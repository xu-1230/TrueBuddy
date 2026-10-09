import { test } from 'node:test'
import assert from 'node:assert/strict'

import { CHANNELS, FRAME_MS, FrameSlicer, SAMPLE_RATE, frameBytes, frameSamples, toInt16Frame } from '../src/audio.mjs'

/**
 * rtc-node 的 AudioFrame.protoInfo() 用 `data.buffer` 取指针、**忽略 byteOffset**，
 * 所以交给它的 Int16Array 必须自己独占一段 ArrayBuffer。这条不变量就是这个 bug 的回归锁。
 */
function assertOwnsItsBuffer(int16, label) {
  assert.equal(int16.byteOffset, 0, `${label}: byteOffset 必须为 0`)
  assert.equal(int16.buffer.byteLength, int16.byteLength, `${label}: 底层 buffer 不能含其它数据`)
}

test('采样率与声道与后端成对（docs/17 §6）', () => {
  assert.equal(SAMPLE_RATE, 48000)
  assert.equal(CHANNELS, 1)
  assert.equal(FRAME_MS, 20)
  assert.equal(frameSamples(SAMPLE_RATE), 960)
  // 20ms × 48kHz × 单声道 × 2 字节
  assert.equal(frameBytes(SAMPLE_RATE, CHANNELS), 1920)
})

test('整块正好是若干帧：不产生余数', () => {
  const slicer = new FrameSlicer(4)
  const frames = slicer.push(Buffer.from([1, 2, 3, 4, 5, 6, 7, 8]))
  assert.equal(frames.length, 2)
  assert.deepEqual([...frames[0]], [1, 2, 3, 4])
  assert.deepEqual([...frames[1]], [5, 6, 7, 8])
  assert.equal(slicer.flush(), null)
})

test('跨块的非整数帧：余数留到下一块，不丢字节', () => {
  const slicer = new FrameSlicer(4)
  assert.deepEqual(slicer.push(Buffer.from([1, 2, 3, 4, 5])).map((f) => [...f]), [[1, 2, 3, 4]])
  // 余数 [5] 单独凑不出帧
  assert.deepEqual(slicer.push(Buffer.from([6, 7])).map((f) => [...f]), [])
  // [5,6,7] + [8,9,10,11,12] = 8 字节，恰好两帧，且没有余数
  assert.deepEqual(slicer.push(Buffer.from([8, 9, 10, 11, 12])).map((f) => [...f]), [[5, 6, 7, 8], [9, 10, 11, 12]])
  assert.equal(slicer.flush(), null)
  assert.equal(slicer.produced, 3)
})

test('flush 把尾巴补静音凑成一帧：播报结尾不被丢掉', () => {
  const slicer = new FrameSlicer(4)
  slicer.push(Buffer.from([1, 2, 3]))
  const tail = slicer.flush()
  assert.deepEqual([...tail], [1, 2, 3, 0])
  assert.equal(slicer.flush(), null, '连续 flush 不应产出第二帧')
})

test('空块与零长流：不产出帧也不报错', () => {
  const slicer = new FrameSlicer(1920)
  assert.deepEqual(slicer.push(Buffer.alloc(0)), [])
  assert.equal(slicer.flush(), null)
  assert.equal(slicer.produced, 0)
})

test('帧长非法直接抛：避免静默死循环', () => {
  assert.throws(() => new FrameSlicer(0), TypeError)
  assert.throws(() => new FrameSlicer(1.5), TypeError)
})

test('一帧 20ms：5 秒音频恰好 250 帧', () => {
  const size = frameBytes(SAMPLE_RATE, CHANNELS)
  const slicer = new FrameSlicer(size)
  const fiveSeconds = Buffer.alloc(size * 250, 7)
  assert.equal(slicer.push(fiveSeconds).length, 250)
  assert.equal(slicer.produced * size, fiveSeconds.length)
})

test('toInt16Frame：值正确且独占 ArrayBuffer（电音 bug 的回归锁）', () => {
  // 造一个「大池子 + 偏移视图」的场景：这正是 HTTP 分块经 FrameSlicer 产出的形态
  const pool = Buffer.alloc(1920 * 3)
  for (let i = 0; i < pool.length; i++) pool[i] = (i * 37) % 251
  const view = pool.subarray(1920, 1920 * 2)

  const int16 = toInt16Frame(view)
  assertOwnsItsBuffer(int16, '视图入参')
  assert.equal(int16.length, 960)
  // 与手工按小端解析同一段字节的结果一致
  const manual = new Int16Array(view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength))
  assert.deepEqual([...int16], [...manual])

  // 反例说明：直接建视图虽然 JS 侧读值正确，但 byteOffset ≠ 0，交给 FFI 就会读错位置
  const aliased = new Int16Array(view.buffer, view.byteOffset, 960)
  assert.notEqual(aliased.byteOffset, 0, '这条用例的前提：视图确实带偏移')
})

test('整段真实 PCM 经「分帧 → toInt16Frame」后逐样本无损', () => {
  const size = frameBytes(SAMPLE_RATE, CHANNELS)
  const original = Buffer.alloc(size * 8)
  for (let i = 0; i < original.length; i++) original[i] = (i * 7919) % 256

  const slicer = new FrameSlicer(size)
  const out = []
  // 用奇数分块打乱帧边界，逼出跨块余数路径
  for (let offset = 0; offset < original.length; offset += 1023) {
    for (const frame of slicer.push(original.subarray(offset, Math.min(offset + 1023, original.length)))) {
      assertOwnsItsBuffer(toInt16Frame(frame), '分帧产物')
      out.push(...toInt16Frame(frame))
    }
  }
  const tail = slicer.flush()
  if (tail) out.push(...toInt16Frame(tail))

  const expected = new Int16Array(original.buffer.slice(original.byteOffset, original.byteOffset + original.byteLength))
  assert.deepEqual(out, [...expected], '一帧都不能错位')
})
