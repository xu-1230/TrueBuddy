/**
 * 数字人音频轨的响度测量（口型驱动的唯一数据来源）。
 *
 * 为什么单独成模块：响度 → 口型的换算与 AudioContext 生命周期是这条链路里唯一能被单测覆盖的部分，
 * 而它恰好是最容易在真机上「看起来能动但其实不对」的地方。
 *
 * 两条来自实测（docs/17 §13.3）的硬约束已经写进实现：
 * - AudioContext **整页只建一次并复用**：每次订阅都新建时，第一次订阅会连续十几秒拿不到任何帧
 * - 口型只用**逐帧瞬时响度**：累计平均与按采样数估播放时长在流停之后仍会继续增长
 */

/** 数字人在房间里的身份，与后端 `LiveKitTokenService.AGENT_IDENTITY` 同值 */
export const AGENT_IDENTITY = 'digital-human'

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

/**
 * 建一个响度表。
 *
 * @param {object} options
 * @param {() => AudioContext} options.createContext AudioContext 工厂（注入以便离线测试）
 * @param {(level:number)=>void} [options.onLevel] 每次算出新开合度时回调
 * @param {(cb: FrameRequestCallback) => number} [options.requestFrame] RAF 注入点
 * @param {number} [options.fftSize=1024] 分析窗口
 */
export function createAgentAudioMeter({ createContext, onLevel, requestFrame, fftSize = 1024 } = {}) {
  let context = null
  let analyser = null
  let sourceNode = null
  let samples = null
  let frameId = null
  let running = false
  let level = 0

  function tick() {
    if (!running) return
    analyser.getFloatTimeDomainData(samples)
    level = mouthLevel(rmsOf(samples))
    onLevel?.(level)
    frameId = (requestFrame ?? ((cb) => requestAnimationFrame(cb)))(tick)
  }

  return {
    /** 开始测量一条远端音频轨（传 MediaStream） */
    start(stream) {
      if (!stream) return
      // 只建一次：context 复用是实测出来的必要做法，见文件头
      context ??= createContext()
      context.resume().catch(() => {})
      sourceNode = context.createMediaStreamSource(stream)
      analyser = context.createAnalyser()
      analyser.fftSize = fftSize
      samples = new Float32Array(analyser.fftSize)
      sourceNode.connect(analyser)
      running = true
      tick()
    },
    /** 停止测量并断开节点，但保留 context 供下次订阅复用 */
    stop() {
      running = false
      if (frameId != null) {
        globalThis.cancelAnimationFrame?.(frameId)
        frameId = null
      }
      sourceNode?.disconnect?.()
      analyser?.disconnect?.()
      sourceNode = null
      analyser = null
      level = 0
      onLevel?.(0)
    },
    get level() {
      return level
    },
    /** 彻底释放（页面卸载时调用） */
    async dispose() {
      this.stop()
      await context?.close?.()
      context = null
    },
  }
}
