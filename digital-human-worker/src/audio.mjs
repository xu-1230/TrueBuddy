/**
 * PCM 分帧（纯函数，不依赖 rtc-node，因此可离线测试）。
 *
 * <p>为什么要分帧：LiveKit 的 AudioSource 期望定长小帧（这里 20ms），而后端推来的是
 * CosyVoice 回调产出的任意长度块。不切帧就会出现「一块太大 → 队列瞬间涨到几百毫秒」的抖动。
 */

/** 每帧时长（毫秒）：20ms 是语音链路常用值，再小会被 FFI 调用开销吃掉 */
export const FRAME_MS = 20
/** 16bit PCM 每个采样点占 2 字节 */
export const BYTES_PER_SAMPLE = 2
/**
 * 与后端 CosyVoiceTtsSynth 的输出严格成对（docs/17 §6）：
 * LiveKit 内部工作速率就是 48kHz，选别的采样率会在这一侧多一次重采样。
 */
export const SAMPLE_RATE = 48000
export const CHANNELS = 1

export function frameSamples(sampleRate) {
  return (sampleRate * FRAME_MS) / 1000
}

export function frameBytes(sampleRate, channels) {
  return frameSamples(sampleRate) * channels * BYTES_PER_SAMPLE
}

/**
 * Buffer 切片 → **独立拥有内存**的 Int16Array。
 *
 * 必须拷贝，不能返回 `new Int16Array(buf.buffer, byteOffset, n)` 这种视图：rtc-node 的
 * `AudioFrame.protoInfo()` 取指针时用的是 `data.buffer`（整个底层 ArrayBuffer）而**忽略
 * byteOffset**，视图会让原生层从内存池开头读，送出去的全是错位字节 —— 听感就是重电音。
 * spike 里用的是独立数组（byteOffset 恰好为 0）所以没暴露，接成真链路才坏。
 *
 * @param frame 恰好一帧长的 PCM16 切片
 * @returns {Int16Array} byteOffset 恒为 0、底层 ArrayBuffer 不含其它数据
 */
export function toInt16Frame(frame) {
  const bytes = frame.subarray(0, frame.byteLength)
  return new Int16Array(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength))
}

/**
 * 把任意长度的 PCM 字节流切成定长帧，跨块的余数留到下一次。
 */
export class FrameSlicer {
  constructor(frameSize) {
    if (!Number.isInteger(frameSize) || frameSize <= 0) {
      throw new TypeError(`frameSize 必须是正整数，收到 ${frameSize}`)
    }
    this.frameSize = frameSize
    this.left = Buffer.alloc(0)
    this.produced = 0
  }

  /**
   * @param chunk 任意长度的 PCM 块
   * @returns 本次能凑出的完整帧（可能为空数组）
   */
  push(chunk) {
    const buffer = this.left.length === 0 ? chunk : Buffer.concat([this.left, chunk])
    const frames = []
    let offset = 0
    while (buffer.length - offset >= this.frameSize) {
      frames.push(buffer.subarray(offset, offset + this.frameSize))
      offset += this.frameSize
    }
    this.left = offset === buffer.length ? Buffer.alloc(0) : Buffer.from(buffer.subarray(offset))
    this.produced += frames.length
    return frames
  }

  /**
   * 收尾：把不足一帧的余数补静音凑成一帧，避免播报的最后几十毫秒被丢掉。
   *
   * @returns 补齐后的尾帧；没有余数时返回 null
   */
  flush() {
    if (this.left.length === 0) {
      return null
    }
    const tail = Buffer.alloc(this.frameSize)
    this.left.copy(tail, 0)
    this.left = Buffer.alloc(0)
    this.produced += 1
    return tail
  }
}
