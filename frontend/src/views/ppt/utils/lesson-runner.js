/**
 * 讲课工作项（Lesson Ticket）的播报队列与推进编排。
 *
 * 两种模式（控制台两个入口，lesson-start 信号携带 mode）：
 * - verbatim 照读：manual 跟随——老师翻页（onPageChanged）是唯一推进 Trigger，翻到哪页念哪页
 *   remark，播完即止（不自动翻页）。
 * - ai 自主：老师只点一次开始——取页面信息 → narrate（AI 现场讲词）→ 切段串行播 → onDrained →
 *   缓冲 → advance() 自动翻页（翻页后仍由 onPageChanged 驱动下一页，与老师手动翻页走同一通路：
 *   后到者生效，无隐藏状态）→ 尾页播完 lesson-done。翻页决策是确定性的（decideAfterPage），
 *   模型只决定「这一段说什么」——dsh 铁律：授权给规则，不给模型。
 *
 * 可靠性：整页 = 多段串行（先入队后发送，段身份 page-{页}-seg-{序}）；同段播报失败中止本页；
 * ai 模式 narrate 失败静默重试 1 次 → 仍失败上浮 page-failed(recoverable) 挂起，等老师
 * resolve('retry'|'skip')；预取：讲第 N 页时预生成第 N+1 讲词（失败不阻塞，到页再生成）；
 * 能停：stop() 作废一切并调 stopSpeaking()（掐请求 = 后端合成取消 + 静音）。
 *
 * 依赖注入使编排与浏览器解耦：node --test 用假件覆盖全部行为；bufferMs 可注入（测试用 1ms）。
 */

import { splitIntoSegments } from './lesson-segments.js'

/**
 * 播完一页后的推进决策（确定性 gate，纯函数）。
 * @param {number} page 刚播完的页（0 基）
 * @param {number} pageCount 总页数
 * @returns {'advance' | 'end'}
 */
export function decideAfterPage(page, pageCount) {
  return page + 1 < pageCount ? 'advance' : 'end'
}

/**
 * 建一个讲课工作项 runner。
 *
 * @param {object} deps 依赖注入
 * @param {(segment: {id: string, page: number, index: number, text: string}) => Promise<void>} deps.speakSegment
 *   播报一段：resolve = 该段 SSE 流正常结束；reject = 失败或被取消。
 * @param {() => void} [deps.stopSpeaking] 立刻安静（掐在途请求 + 播放器静音）
 * @param {(page: number) => string} [deps.getPageText] 某页讲稿纯文本（verbatim）
 * @param {(event: object) => void} [deps.onEvent] 工作项事件
 * @param {(text: string) => string[]} [deps.segmentText] 切段注入点
 * @param {(page: number) => object|null|Promise<object|null>} [deps.getPageInfo] ai：某页
 *   { index(1基), title, text, remark, images? }；可返回 Promise（图片压缩是异步的）
 * @param {(payload: object) => Promise<{ speechText: string }>} [deps.generateSpeech] ai：narrate 端点
 * @param {() => void} [deps.advance] ai：驱动翻页（execNext 循环），结果由 onPageChanged 驱动
 * @param {() => number} [deps.getPageCount] ai：总页数
 * @param {number} [bufferMs] 播完到 advance 的缓冲（默认 1500ms，测试注入 1）
 */
export function createLessonRunner({
  speakSegment, stopSpeaking, getPageText, onEvent, segmentText = splitIntoSegments,
  getPageInfo, generateSpeech, advance, getPageCount,
  bufferMs = 1500,
} = {}) {
  let active = false
  /** 当前模式；null = 未运行 */
  let mode = null
  /** 回合号：start/翻页/stop 递增；旧回合的异步回调以 myRun !== run 自我作废 */
  let run = 0
  /** 当前页（0 基）：缓冲定时器到点时校验「老师是否已经自己翻过去了」 */
  let currentPage = null
  /** ai 挂起等待老师 resolve 的一页：{ page, myRun }；非空 = person/failed 阶段 */
  let pendingResolve = null
  let bufferTimer = null
  /** 预取的讲词：page → Promise<string|null>（失败置 null，到页后现场再生成） */
  const prefetch = new Map()
  let startedAtMs = 0

  const emit = (event) => onEvent?.(event)
  const isCurrent = (myRun) => myRun === run && active
  /** 已讲分钟数（至少 1：提示词里「已讲 0 分钟」没有意义） */
  const elapsedMinutes = () => Math.max(1, Math.round((Date.now() - startedAtMs) / 60000))

  // ========== verbatim 照读（manual 跟随，P0 行为） ==========

  function speakPage(page) {
    const myRun = ++run
    currentPage = page
    const raw = (getPageText?.(page) ?? '').trim()
    startSpeaking(myRun, page, raw)
  }

  // ========== 共用：切段 + 串行播报 ==========

  /** 切段串行播完；空文本报 page-skipped；播报失败（合成层）报 page-failed 并中止本页。 */
  function startSpeaking(myRun, page, text) {
    const texts = segmentText(text ?? '')
    if (!texts.length) {
      emit({ type: 'page-skipped', page })
      // verbatim：停在原地等老师；ai：跳过后继续推进（下一页/结束）
      if (mode === 'ai') afterPage(myRun, page)
      return
    }
    // 先入队：整页的段对象在发送前就全部定型（身份 + 顺序），发送只是逐个取出
    const queue = texts.map((t, index) => ({ id: `page-${page}-seg-${index}`, page, index, text: t }))
    emit({ type: 'page-start', page, segments: queue.length })

    ;(async () => {
      for (const segment of queue) {
        if (!isCurrent(myRun)) return
        try {
          await speakSegment?.(segment)
        } catch (error) {
          // 回合已作废的 reject 是「被停止/翻页掐断」，不是失败
          if (!isCurrent(myRun)) return
          emit({ type: 'page-failed', page, segmentId: segment.id, message: error?.message ?? String(error) })
          return
        }
        if (!isCurrent(myRun)) return
      }
      if (!isCurrent(myRun)) return
      emit({ type: 'page-done', page, segments: queue.length })
    })()
  }

  // ========== ai 自主（narrate + 自动推进） ==========

  /** 单次 narrate（重试策略归 getAiSpeech）。页面无内容返回 null。 */
  async function generateOnce(page) {
    const info = (await getPageInfo?.(page)) ?? null
    // 图片也是页面内容：纯图片页（无正文/备注）必须继续走 narrate，不能在这里被跳过
    if (!info || (!info.remark?.trim() && !info.text?.trim() && !info.images?.length)) return null
    const payload = {
      ...info,
      context: { pageCount: getPageCount?.() ?? null, elapsedMin: elapsedMinutes() },
    }
    const result = await generateSpeech?.(payload)
    return result?.speechText?.trim() ?? null
  }

  /** 取某页讲词：预取命中先用（失败降级现场生成），否则静默重试 1 次；两次失败抛错上浮。 */
  async function getAiSpeech(myRun, page) {
    const prefetched = prefetch.get(page)
    if (prefetched) {
      prefetch.delete(page)
      const text = await prefetched
      if (text) return text
    }
    let lastError = null
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const text = await generateOnce(page)
        if (text === null) return null
        if (text) return text
        lastError = new Error('讲词为空')
      } catch (error) {
        if (!isCurrent(myRun)) throw error
        lastError = error
      }
    }
    throw lastError ?? new Error('讲词生成失败')
  }

  /** ai 模式讲一页：narrate → 切段播报；两次失败上浮 Attention 并挂起等 resolve。 */
  function prepareAiPage(page) {
    const myRun = ++run
    currentPage = page
    ;(async () => {
      let speech
      try {
        speech = await getAiSpeech(myRun, page)
      } catch (error) {
        if (!isCurrent(myRun)) return
        emit({ type: 'page-failed', page, recoverable: true, message: error?.message ?? String(error) })
        pendingResolve = { page, myRun }
        return
      }
      if (speech === null) {
        // 无内容页：明确跳过（不是失败），ai 模式继续推进
        emit({ type: 'page-skipped', page })
        afterPage(myRun, page)
        return
      }
      if (!isCurrent(myRun)) return
      // 讲本页的同时预取下一页讲词（失败置 null，到页后现场再生成）
      if (page + 1 < (getPageCount?.() ?? page + 2)) {
        prefetch.set(page + 1, generateOnce(page + 1).catch(() => null))
      }
      startSpeaking(myRun, page, speech ?? '')
    })()
  }

  /**
   * 一页讲完后的推进（确定性 gate）：还有下一页就缓冲后 advance（老师缓冲期内手动翻走则
   * 页码已变，本次 advance 自动丢弃），尾页则工作项 ended（lesson-done）。
   */
  function afterPage(myRun, page) {
    if (!isCurrent(myRun)) return
    const decision = decideAfterPage(page, getPageCount?.() ?? page + 1)
    if (decision === 'end') {
      active = false
      mode = null
      currentPage = null
      ++run
      emit({ type: 'lesson-done' })
      return
    }
    bufferTimer = setTimeout(() => {
      bufferTimer = null
      if (!isCurrent(myRun) || mode !== 'ai' || currentPage !== page) return
      emit({ type: 'advance-request', from: page })
      advance?.()
    }, bufferMs)
  }

  return {
    start(page, { mode: m = 'verbatim' } = {}) {
      active = true
      mode = m
      startedAtMs = Date.now()
      prefetch.clear()
      pendingResolve = null
      if (m === 'ai') prepareAiPage(page)
      else speakPage(page)
    },
    onPageChanged(page) {
      if (!active) return
      if (bufferTimer) {
        clearTimeout(bufferTimer)
        bufferTimer = null
      }
      pendingResolve = null
      stopSpeaking?.()
      if (mode === 'ai') prepareAiPage(page)
      else speakPage(page)
    },
    notifyDrained() {
      if (!active) return
      emit({ type: 'page-drained' })
      if (mode !== 'ai' || pendingResolve) return
      afterPage(run, currentPage)
    },
    /** Attention 裁决（仅 ai 挂起时有效）：retry 重做本页；skip 跳过推进。 */
    resolve(resolution) {
      if (!active || mode !== 'ai' || !pendingResolve) return
      const { page, myRun } = pendingResolve
      pendingResolve = null
      if (!isCurrent(myRun)) return
      if (resolution === 'skip') {
        afterPage(myRun, page)
        return
      }
      prepareAiPage(page)
    },
    stop() {
      if (!active) return
      active = false
      mode = null
      pendingResolve = null
      if (bufferTimer) {
        clearTimeout(bufferTimer)
        bufferTimer = null
      }
      ++run
      stopSpeaking?.()
      emit({ type: 'stopped' })
    },
    get active() { return active },
    get mode() { return mode },
    get awaitingResolve() { return pendingResolve != null },
  }
}
