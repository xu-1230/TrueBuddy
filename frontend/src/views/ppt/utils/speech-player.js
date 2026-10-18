/**
 * 讲稿 PCM 播放器：把后端 SSE 送来的 48kHz / 单声道 / 16bit PCM 边收边播，并顺手产出响度给口型。
 *
 * 为什么不用现成的播放器包：这三件事必须长在同一个 AudioContext 上——
 * 1. 音频的终点就是**这一页**：它正被屏幕共享，学生听到的声音是从这里出去的（docs/16 §5.7）；
 * 2. 口型必须由**这一页正在播的音频**驱动，否则嘴和声音对不上；
 * 3. 「停止演讲」必须让声音立刻消失：`stop()` 掐掉已排期与队列里的全部 buffer。
 *
 * 三个来自实现细节的口径：
 * - 后端按厂商 SDK 的分片推流，分片边界可能落在一个 16bit 采样的中间，因此留一个字节进位；
 * - `AudioContext` 懒建且整页复用：没播之前不建，避免白拿一个被浏览器挂起的上下文；
 * - 播放需要页面已被激活（老师在本页点过任意位置）。未激活时 `resume()` 会被挂起，
 *   这里只尝试、只记录，不阻断播报——提示交给调用方。
 */

/** 与后端 `DigitalHumanServiceImpl` 的出口口径一致（CosyVoice 的 PCM_48000HZ_MONO_16BIT） */
export const PCM_SAMPLE_RATE = 48000

/** 播放起始的提前量（秒）：给解码与排期留余量，避免首块抖一下 */
const START_LEAD_SECONDS = 0.05
/** 噪声门限：低于此值当作没说话，避免底噪让嘴一直微动 */
const RMS_FLOOR = 0.005
/** 线性增益：正常播报的 RMS 约 0.05–0.15，×4 后落在 0.2–0.6 的开合区间 */
const RMS_GAIN = 4

/**
 * 瞬时响度 → 口型开合度（0–1）。
 *
 * @param {number} rms 一帧的均方根
 * @returns {number} 开合度，NaN/负数/低于门限都归 0
 */
export function mouthLevel(rms) {
  if (!Number.isFinite(rms) || rms <= RMS_FLOOR) return 0
  return Math.min(1, rms * RMS_GAIN)
}

/** 一帧 Float32 采样的均方根 */
export function rmsOf(samples) {
  if (!samples?.length) return 0
  let sum = 0
  for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i]
  return Math.sqrt(sum / samples.length)
}

/** base64 → 字节：chunk 走 base64 是为了让 SSE 的 data 行里不出现二进制 */
export function decodeBase64(text) {
  const binary = globalThis.atob(text)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

/** 小端 16bit → [-1,1] 浮点（Web Audio 只认浮点） */
export function int16ToFloat32(bytes) {
  const count = Math.floor(bytes.length / 2)
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const floats = new Float32Array(count)
  for (let i = 0; i < count; i++) floats[i] = view.getInt16(i * 2, true) / 32768
  return floats
}

/**
 * 建一个播放器。
 *
 * @param {object} [options]
 * @param {number} [options.sampleRate] 采样率，默认与后端一致
 * @param {(level: number) => void} [options.onLevel] 每次算出新开合度时回调（驱动口型）
 * @param {() => AudioContext} [options.createContext] AudioContext 工厂（注入以便离线测试）
 * @param {(cb: FrameRequestCallback) => number} [options.requestFrame] RAF 注入点
 * @returns {{ feed: (payload: string|Uint8Array) => void, stop: () => void, destroy: () => Promise<void>, readonly level: number }}
 */
export function createSpeechPlayer({ sampleRate = PCM_SAMPLE_RATE, onLevel, createContext, requestFrame } = {}) {
  let context = null
  let gain = null
  let analyser = null
  let samples = null
  /** 已排期的音源：`stop()` 要把它们全部掐掉，否则「停止」之后还会接着响 */
  let scheduled = []
  /** 下一段音频的排期时刻（AudioContext 时钟） */
  let nextStartTime = 0
  /** 上一片落单的高位字节（分片切在采样中间时用） */
  let carry = 0
  let frameId = null
  /** 取帧循环是否在跑：`stop()` 后即使还有一个在途回调，也不能再改口型 */
  let running = false
  let level = 0

  function ensureGraph() {
    if (context) return context
    context = (createContext ?? (() => new AudioContext({ sampleRate })))()
    gain = context.createGain()
    analyser = context.createAnalyser()
    analyser.fftSize = 1024
    samples = new Float32Array(analyser.fftSize)
    gain.connect(analyser)
    analyser.connect(context.destination)
    return context
  }

  function tick() {
    if (!running || !context) return
    analyser.getFloatTimeDomainData(samples)
    level = mouthLevel(rmsOf(samples))
    onLevel?.(level)
    frameId = (requestFrame ?? ((cb) => requestAnimationFrame(cb)))(tick)
  }

  /** 把奇数长度的分片补成偶数：整数个 16bit 采样才能解成浮点 */
  function withCarry(bytes) {
    if (carry && bytes.length) {
      const merged = new Uint8Array(bytes.length + 1)
      merged[0] = carry
      merged.set(bytes, 1)
      carry = 0
      return merged
    }
    if (bytes.length % 2) {
      carry = bytes[bytes.length - 1]
      return bytes.subarray(0, bytes.length - 1)
    }
    return bytes
  }

  function feed(payload) {
    const raw = typeof payload === 'string' ? decodeBase64(payload) : payload
    if (!raw?.length) return
    const bytes = withCarry(raw)
    if (!bytes.length) return

    const floats = int16ToFloat32(bytes)
    const ctx = ensureGraph()
    // 页面没被激活时这里会保持挂起：不阻断，调用方按需提示
    ctx.resume?.().catch(() => {})

    const buffer = ctx.createBuffer(1, floats.length, sampleRate)
    buffer.copyToChannel(floats, 0)

    const source = ctx.createBufferSource()
    source.buffer = buffer
    source.connect(gain)
    // 无缝接续：首块留提前量，之后紧接上一块的结束时刻
    const startAt = Math.max(ctx.currentTime + START_LEAD_SECONDS, nextStartTime)
    source.start(startAt)
    nextStartTime = startAt + buffer.duration
    scheduled.push(source)
    source.onended = () => {
      scheduled = scheduled.filter((item) => item !== source)
    }

    if (!running) {
      running = true
      tick()
    }
  }

  /** 立刻安静：掐掉已排期音源、丢掉进位与排期时刻，并把口型归零 */
  function stop() {
    scheduled.forEach((source) => {
      try {
        source.onended = null
        source.stop()
      } catch {
        // 已经自然播完的音源 stop 不了，也没有影响
      }
    })
    scheduled = []
    nextStartTime = 0
    carry = 0
    level = 0
    running = false
    onLevel?.(0)
    if (frameId != null) {
      globalThis.cancelAnimationFrame?.(frameId)
      frameId = null
    }
  }

  /** 收起整条链路（离开页面时调用）：先静音再关上下文 */
  async function destroy() {
    stop()
    const closing = context
    context = null
    gain = null
    analyser = null
    samples = null
    await closing?.close?.()
  }

  return {
    feed,
    stop,
    destroy,
    get level() {
      return level
    },
  }
}
