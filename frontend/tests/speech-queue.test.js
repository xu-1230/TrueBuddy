/**
 * 跟页播报的纯逻辑用例（`node:test`，零依赖，见 docs/11 §3.1）。
 *
 * 钉住两件事：
 * 1. 切段结果**每一条都在后端单条上限内**，否则整页讲稿会被 `DIGITAL_TEXT_TOO_LONG` 直接拒掉；
 * 2. 切段不能吞字、不能改字（老师写的标点是讲稿的一部分），且翻页时是「先断再播」。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_SEGMENT_CHARS, planPageChange, splitSpeech,
} from '../src/views/meeting/utils/speech-queue.js'

test('空文本与纯空白：一段都不产生（宁可不出声，也不要念一段空气）', () => {
  assert.deepEqual(splitSpeech(''), [])
  assert.deepEqual(splitSpeech('   \n \t '), [])
  assert.deepEqual(splitSpeech(null), [])
  assert.deepEqual(splitSpeech(undefined), [])
})

test('不超上限时原样一段：标点、空格、换行都不改写', () => {
  const text = '同学们好，今天我们讲二次函数。\n先看图像。'
  assert.deepEqual(splitSpeech(text, 200), [text.trim()])
})

test('多句装箱：每条都在上限内，且拼回去与原文一致（不吞字）', () => {
  const sentences = Array.from({ length: 12 }, (_, i) => `第${i}句讲的是这一步的变形要点。`)
  const text = sentences.join('')
  const limit = 60

  const segments = splitSpeech(text, limit)

  assert.ok(segments.length > 1, '应当被切成多段')
  for (const segment of segments) {
    assert.ok(segment.length <= limit, `段超长：${segment.length} > ${limit}`)
    assert.ok(segment.trim().length > 0, '不该出现空段')
  }
  // 装箱只允许在句末切：拼回去必须与原文逐字相同
  assert.equal(segments.join(''), text)
})

test('单句本身就超上限：优先在逗号处切，没有逗号才硬切', () => {
  const withComma = `${'很长的一段话'.repeat(10)}，后面还有内容要说完。`.slice(0, 90)
  const segments = splitSpeech(withComma, 40)
  assert.ok(segments.every((segment) => segment.length <= 40))
  assert.equal(segments.join(''), withComma)

  const noBreak = '无'.repeat(95)
  const hard = splitSpeech(noBreak, 40)
  assert.ok(hard.every((segment) => segment.length <= 40))
  assert.equal(hard.join(''), noBreak, '硬切也不能丢字')
})

test('上限非法时回落到默认值，而不是切成 0 长度死循环', () => {
  const text = `一句话。${'再一句。'.repeat(20)}`
  for (const bad of [0, -5, Number.NaN, undefined, 'x']) {
    const segments = splitSpeech(text, bad)
    assert.ok(segments.length >= 1)
    assert.ok(segments.every((segment) => segment.length <= DEFAULT_SEGMENT_CHARS))
  }
})

test('页变更计划：同页不重播、空页只断不开口、跨页先断再播', () => {
  assert.deepEqual(
    planPageChange({ running: true, index: 3 }, { index: 3, segments: 2 }),
    { cancelFirst: false, play: false, reason: 'same-page' },
  )

  // 空页（这一页没讲稿）：断掉上一段，但不该发起播报
  assert.deepEqual(
    planPageChange({ running: true, index: 3 }, { index: 4, segments: 0 }),
    { cancelFirst: true, play: false, reason: 'empty-page' },
  )

  assert.deepEqual(
    planPageChange({ running: true, index: 3 }, { index: 4, segments: 2 }),
    { cancelFirst: true, play: true, reason: 'advance' },
  )

  // 没在播的时候切页：不需要 cancel，直接播
  assert.deepEqual(
    planPageChange(null, { index: 0, segments: 1 }),
    { cancelFirst: false, play: true, reason: 'advance' },
  )
})
