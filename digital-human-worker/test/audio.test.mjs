import { test } from 'node:test'
import assert from 'node:assert/strict'

import { CHANNELS, FRAME_MS, FrameSlicer, SAMPLE_RATE, frameBytes, frameSamples } from '../src/audio.mjs'

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
