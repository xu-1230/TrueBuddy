/**
 * 「每完成一份学生上一张卡」的提取口径用例。
 *
 * 帧结构照抄真实抓包（`.repro/hw-e2e/run-phase2.sse` 里那一轮）：
 * 一轮的帧顺序是 `100005 → 100006 → 100008`，出口子块（`100008`）那一帧的
 * `state['100008']` 是这一轮那一个学生的记录、`state['100004'].index` 是轮次下标；
 * 整轮跑完后才有迭代节点的**汇总帧**（各列数组）。
 *
 * 运行：cd frontend && npm test
 */
import { register } from 'node:module'
import test from 'node:test'
import assert from 'node:assert/strict'

// 被测模块 import 了 `@/views/workflow2/utils/run-state`（别名 + 省略扩展名），
// 必须走 tests/support 的解析钩子；钩子只对 register() 之后的**动态 import** 生效。
register(new URL('./support/alias-hooks.mjs', import.meta.url))
const { roundRecordOf } = await import('../src/views/homework/utils/round-record.js')

/** 结果契约：迭代工作流，出口子块 100008，迭代节点 100004 */
const ITERATION_CONTRACT = {
  iteration: true,
  outputNodeId: '100008',
  sourceNodeId: '100004',
  summaryNodeId: '100007',
}

/** 节点产出帧（`nodePayloadOf` 读的就是 `content.data`） */
const nodeFrame = (node, state) => ({
  content_type: 'verbose',
  content: { msg_type: 'node_finished', node_id: node, data: { node, state } },
})

/** 一轮里"轮次已完成"的那一帧 */
const roundFrame = (index, record) => nodeFrame('100008', {
  100004: { index },
  100005: { student: record.student, score: record.score, grade: record.grade, deductions: [], readFiles: [] },
  100006: { record },
  100008: record,
})

const TIANCAI = { student: '天才', score: 80, grade: '良好', comment: '结构完整' }
const HUAJISHU = { student: '华技术', score: 70, grade: '中等', comment: '原理展开不足' }

test('出口子块的帧 → 取出这一轮的学生记录与轮次下标', () => {
  const hit = roundRecordOf(ITERATION_CONTRACT, roundFrame(0, TIANCAI))
  assert.ok(hit, '应能识别出"某轮刚完成"')
  assert.equal(hit.index, 0)
  assert.deepEqual(hit.record, TIANCAI)
})

test('同轮更早的子块帧（评分 / 评语）不算完成了一份', () => {
  const stage = nodeFrame('100005', { 100004: { index: 0 }, 100005: { student: '天才' } })
  assert.equal(roundRecordOf(ITERATION_CONTRACT, stage), null)
})

test('迭代节点的汇总帧不算"又完成了一份"（否则每轮会被上两次牌）', () => {
  const aggregate = nodeFrame('100004', {
    100004: { student: ['天才', '华技术'], score: [80, 70] },
    100008: HUAJISHU,
  })
  assert.equal(roundRecordOf(ITERATION_CONTRACT, aggregate), null)
})

test('人工确认卡片帧与非节点帧都不是产出', () => {
  assert.equal(roundRecordOf(ITERATION_CONTRACT, {
    content_type: 'card',
    content: { node_id: '100003', title: '确认批改列表', data: [], fields: [] },
  }), null)
  assert.equal(roundRecordOf(ITERATION_CONTRACT, { content_type: 'verbose', content: {} }), null)
  assert.equal(roundRecordOf(ITERATION_CONTRACT, null), null)
})

test('非迭代工作流不参与流式上牌（卡片由跑完后的最终结果补齐）', () => {
  const contract = { ...ITERATION_CONTRACT, iteration: false }
  assert.equal(roundRecordOf(contract, roundFrame(0, TIANCAI)), null)
})

test('产出退化形态不当成一份成绩：数组 / 标量 / 空值都返回 null', () => {
  // 模型返回的 JSON 没解析成功时，单输出变量会退化成裸文本（见 LlmNode 的兜底分支）
  assert.equal(roundRecordOf(ITERATION_CONTRACT, nodeFrame('100008', {
    100004: { index: 1 },
    100008: '{"student":"许小齐"—— 一段没解析成功的文本',
  })), null)
  assert.equal(roundRecordOf(ITERATION_CONTRACT, nodeFrame('100008', {
    100004: { index: 1 },
    100008: [TIANCAI],
  })), null)
  assert.equal(roundRecordOf(ITERATION_CONTRACT, nodeFrame('100008', {
    100004: { index: 1 },
  })), null)
})

test('轮次下标缺失时照常给出记录，下标退回 null（调用方按顺序兜底去重）', () => {
  const hit = roundRecordOf(ITERATION_CONTRACT, nodeFrame('100008', { 100008: TIANCAI }))
  assert.ok(hit)
  assert.equal(hit.index, null)
  assert.deepEqual(hit.record, TIANCAI)
})

test('按真实帧序列走一遍：两轮产出两份、顺序与名单一致', () => {
  const frames = [
    nodeFrame('100003', { 100002: { student_list: [{ student: '天才' }, { student: '华技术' }] } }),
    nodeFrame('100005', { 100004: { index: 0 }, 100005: { student: '天才' } }),
    nodeFrame('100006', { 100004: { index: 0 }, 100006: { record: TIANCAI } }),
    roundFrame(0, TIANCAI),
    nodeFrame('100005', { 100004: { index: 1 }, 100005: { student: '华技术' } }),
    nodeFrame('100006', { 100004: { index: 1 }, 100006: { record: HUAJISHU } }),
    roundFrame(1, HUAJISHU),
    nodeFrame('100004', { 100004: { student: ['天才', '华技术'] } }),
    nodeFrame('100007', { 100007: { average_score: 75 } }),
  ]

  const emitted = frames
    .map((frame) => roundRecordOf(ITERATION_CONTRACT, frame))
    .filter(Boolean)

  assert.equal(emitted.length, 2, '两轮各上一张，汇总帧不重复上牌')
  assert.deepEqual(emitted.map((hit) => hit.index), [0, 1])
  assert.deepEqual(emitted.map((hit) => hit.record.student), ['天才', '华技术'])
})
