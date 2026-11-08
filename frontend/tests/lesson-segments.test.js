/**
 * 讲稿切段的可测约束（Node 内置 `node:test`）。
 *
 * 锁四条来自方案口径的规则：
 * ① 全空白返回空数组——放映页不会为「没有讲稿」发一次注定失败的请求；
 * ② 段边界落在句读符后：TTS 的停顿由标点给，绝不在词中间断；
 * ③ 单句超长按上限硬切：整页 1500 字、单条 1000 字的上限约束必须被满足；
 * ④ 段长口径与后端一致（UTF-16 `String.length`），硬切按码点不切碎 emoji。
 *
 * 运行：cd frontend && node --test tests/lesson-segments.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { SEGMENT_MAX_CHARS, splitIntoSegments } from '../src/views/ppt/utils/lesson-segments.js'

test('空文本与全空白：返回空数组，不产生任何段', () => {
  assert.deepEqual(splitIntoSegments(''), [])
  assert.deepEqual(splitIntoSegments('   \n  \r\n  '), [])
  assert.deepEqual(splitIntoSegments(null), [])
  assert.deepEqual(splitIntoSegments(undefined), [])
})

test('无句读的短文本：整段合并为一段', () => {
  assert.deepEqual(splitIntoSegments('这是没有句读的一句话'), ['这是没有句读的一句话'])
})

test('按句读断句 + 贪心合并：塞得下就并段，塞不下才分段', () => {
  // 上限 10：第 1 句 5 字 + 第 2 句 5 字 = 10 恰好塞进同一段；第 3 句再并就超了
  const text = '第一句话。第二句话。第三句话。'
  assert.deepEqual(splitIntoSegments(text, 10), ['第一句话。第二句话。', '第三句话。'])
})

test('句读符必须留在句尾：停顿点不丢失', () => {
  const segments = splitIntoSegments('好的。开始。', 4)
  assert.deepEqual(segments, ['好的。', '开始。'])
  assert.ok(segments.every((seg) => /[。！？；]$/.test(seg)), '每段以句读符收尾')
})

test('单句超长：按上限硬切，句读符跟在切点之后的那段', () => {
  // 「ABCDEFG」7 字无句读，上限 3 → 三段；末尾句读符并进最后一段
  assert.deepEqual(splitIntoSegments('ABCDEFG。', 3), ['ABC', 'DEF', 'G。'])
})

test('恰好等于上限的句子不触发硬切', () => {
  const text = '一二三四五'
  assert.deepEqual(splitIntoSegments(text, 5), ['一二三四五'])
})

test('换行按句读处理：两行合不进一段时分段，换行符不进段', () => {
  assert.deepEqual(splitIntoSegments('第一行\n第二行', 4), ['第一行', '第二行'])
  // 塞得下时合并，且不含换行
  assert.deepEqual(splitIntoSegments('第一行\n第二行', 10), ['第一行第二行'])
})

test('emoji 代理对不被硬切拆坏', () => {
  // 一句 = 5 个 emoji（每个占 2 个 UTF-16 单元）+ 句号，共 6 个码点、length=11；上限 3 按码点切
  const sentence = '😀😁😂🤣😃。'
  const segments = splitIntoSegments(sentence, 3)
  assert.deepEqual(segments, ['😀😁😂', '🤣😃。'])
})

test('非法上限回落默认 1000', () => {
  assert.equal(splitIntoSegments('一。')[0].length <= SEGMENT_MAX_CHARS, true)
  assert.deepEqual(splitIntoSegments('一。', 0), ['一。'])
  assert.deepEqual(splitIntoSegments('一。', -3), ['一。'])
  assert.deepEqual(splitIntoSegments('一。', NaN), ['一。'])
})

test('默认上限为 1000：两句合计超限被切成两段，每段都不超上限', () => {
  // 第一句 991 字（含句号），第二句 21 字：合计 1012 > 1000，贪心合并放不下第二句
  const text = `${'一'.repeat(990)}。${'二'.repeat(20)}。`
  const segments = splitIntoSegments(text)
  assert.equal(segments.length, 2)
  assert.ok(segments.every((seg) => seg.length <= 1000))
})
