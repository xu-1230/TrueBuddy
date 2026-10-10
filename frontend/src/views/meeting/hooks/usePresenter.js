/**
 * 跟页播报的编排层（docs/18 §6）。
 *
 * 一套机器，两条通路：
 * - `preview`（本机试听）：`POST /ppt/speech/speak` 取回 WAV，用 `Audio` 放，翻页/停止时 `abort` + `pause`；
 * - `meeting`（会中播报）：`POST /meeting/digital/say` 交给 worker 发进房间，取消走 `say/cancel`。
 *
 * 三条不能写错的规矩，都在这一个文件里：
 * 1. **串行**：一个房间只有一条常驻音频轨，两段并发推会交错混播（docs/18 §5.4），所以本页播完才准下一页；
 * 2. **先断后播**：翻页时先掐断在途那段再开口，写反了就是「上一页尾音压住下一页开头」；
 * 3. **世代号**：所有异步回环都比对 `generation`，停止/翻页后旧循环必须自己退出，不能靠标志位猜测。
 */
import { onBeforeUnmount, ref } from 'vue'
import { cancelDigitalSay, sayDigital } from '@/api/meeting'
import { speakPreview } from '@/api/ppt'
import { DEFAULT_SEGMENT_CHARS, planPageChange, splitSpeech } from '../utils/speech-queue'

/** 通路标识（UI 上就叫「试听 / 会中」） */
export const CHANNEL_PREVIEW = 'preview'
export const CHANNEL_MEETING = 'meeting'

const newRequestId = () =>
  globalThis.crypto?.randomUUID?.() ?? `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

/**
 * @param {object} options
 * @param {() => { meetingNo?: string, hostToken?: string }} options.identity 会议通道鉴权所需
 * @param {() => string} [options.channel] 当前通路（切换时不需要重建机器）
 * @param {number} [options.maxSegmentChars] 单段字数上限，须与后端 `max-text-length` 对齐
 * @param {(message: string) => void} [options.onWarn] 失败提示（不弹窗，交给调用方决定形态）
 */
export function usePresenter({ identity, channel, maxSegmentChars = DEFAULT_SEGMENT_CHARS, onWarn } = {}) {
  /** idle | preparing | speaking */
  const state = ref('idle')
  /** 当前正在念的页与段：{ index, segment, total, remarkEmpty } */
  const progress = ref(null)
  /** 最近一次失败的原因（成功时清空） */
  const error = ref('')

  /** 世代号：任何一次停止/翻页都 +1，旧循环在下一次 await 之后发现自己过期就退出 */
  let generation = 0
  /** 在途播报的取消句柄 */
  let controller = null
  /** 试听通路正在放的元素 */
  let audio = null
  /** 当前这一段对应的 requestId（会议通路取消时要用） */
  let currentRequestId = ''
  /** 正在念的页索引；null 表示没在跟随 */
  let following = null
  let running = false

  const currentChannel = () => (typeof channel === 'function' ? channel() : CHANNEL_PREVIEW)

  /** 掐断在途的这一段：先让 worker 清队列，再断 HTTP（顺序反了会留下已经排队的帧） */
  function interruptInflight() {
    generation += 1
    running = false
    following = null

    const ch = currentChannel()
    const { meetingNo, hostToken } = identity?.() ?? {}
    if (ch === CHANNEL_MEETING && currentRequestId && meetingNo) {
      // 不 await：停止按钮不能让网络往返挡住
      cancelDigitalSay({ meetingNo, hostToken, requestId: currentRequestId }).catch(() => {})
    }
    currentRequestId = ''

    controller?.abort()
    controller = null

    if (audio) {
      audio.pause()
      if (audio.src) URL.revokeObjectURL(audio.src)
      audio = null
    }
  }

  /** 试听：取回整段 WAV 再放，`ended` 才算播完 */
  async function playPreview(text, generationAtStart) {
    const blob = await speakPreview(text, controller.signal)
    if (generation !== generationAtStart) return
    const url = URL.createObjectURL(blob)
    await new Promise((resolve) => {
      audio = new Audio(url)
      audio.addEventListener('ended', resolve, { once: true })
      // 放不出去（例如浏览器拦截自动播放）也必须往下走，否则会永远卡在 preparing
      audio.addEventListener('error', resolve, { once: true })
      audio.play().catch(resolve)
    })
    if (audio?.src) URL.revokeObjectURL(audio.src)
    audio = null
  }

  /** 会中：一段一次 say（后端同步返回即代表整段已交给 worker） */
  async function playMeeting(text, generationAtStart) {
    const { meetingNo, hostToken } = identity?.() ?? {}
    if (!meetingNo || !hostToken) throw new Error('缺少会议号或主持人凭据，无法在会中播报')
    currentRequestId = newRequestId()
    await sayDigital({ meetingNo, hostToken, text, requestId: currentRequestId })
    if (generation !== generationAtStart) return
    currentRequestId = ''
  }

  /** 念一页：切段后逐段串行 */
  async function speakPage(index, remark) {
    const segments = splitSpeech(remark, maxSegmentChars)
    const plan = planPageChange(
      running && following !== null ? { running, index: following } : null,
      { index, segments: segments.length },
    )
    if (!plan.play) {
      if (plan.cancelFirst) {
        interruptInflight()
        state.value = 'idle'
        progress.value = null
      }
      return
    }

    // 先断掉上一页，再开这一页
    const generationAtStart = generation + 1
    interruptInflight()
    generation = generationAtStart

    running = true
    following = index
    state.value = 'preparing'
    error.value = ''
    controller = new AbortController()

    for (let position = 0; position < segments.length; position += 1) {
      if (generation !== generationAtStart) return
      progress.value = { index, segment: position + 1, total: segments.length }
      state.value = position === 0 ? 'preparing' : 'speaking'
      try {
        if (currentChannel() === CHANNEL_MEETING) {
          await playMeeting(segments[position], generationAtStart)
        } else {
          await playPreview(segments[position], generationAtStart)
        }
      } catch (caught) {
        if (generation !== generationAtStart) return // 主动取消不是错误
        if (caught?.name === 'AbortError') return
        error.value = caught?.message ?? '播报失败'
        onWarn?.(error.value)
        state.value = 'idle'
        progress.value = null
        running = false
        following = null
        return
      }
      state.value = 'speaking'
    }

    if (generation !== generationAtStart) return
    state.value = 'idle'
    progress.value = null
    running = false
    following = null
  }

  /** 开始跟随：先念当前页，之后由 speakPage 逐页推进 */
  function start(page) {
    running = true
    speakPage(page.index, page.remark)
  }

  /** 停止：掐断在途 + 不再跟随 */
  function stop() {
    interruptInflight()
    state.value = 'idle'
    progress.value = null
    error.value = ''
  }

  onBeforeUnmount(stop)

  return { state, progress, error, start, stop, speakPage }
}
