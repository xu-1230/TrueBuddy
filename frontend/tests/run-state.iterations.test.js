/**
 * 迭代进度归约的行为用例（Node 内置 `node:test`）。
 *
 * 约定：后端每轮会把**每个子块**的事件各转发一次，并在事件里带上本轮的权威轮次下标
 * `state[迭代节点id].index`（后端 `IterateNodeTest.forwardedEventsCarryRoundIndex` 断言了这一点）。
 * 前端必须按这个下标取「已完成轮次」，否则含 N 个子块时会把轮次算成 N 倍。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyRunEvent,
  beginRun,
  createRunState,
  iterationProgressOf,
} from '../src/views/workflow2/utils/run-state.js'

const CONTAINER_ID = '132996'
const SOURCE_ID = '100001'
const CHILD_A = '200001'
const CHILD_B = '200002'

/** 画布节点：1 个迭代容器（含 2 个子块）+ 上游迭代源节点 */
const canvasNodes = () => [
  {
    id: CONTAINER_ID,
    type: '4',
    data: {
      inputs: [{ name: 'items', type: 'list', input: { blockID: SOURCE_ID, name: 'description', type: 'list' } }],
    },
  },
  { id: CHILD_A, type: '3', parentNode: CONTAINER_ID },
  { id: CHILD_B, type: '3', parentNode: CONTAINER_ID },
  { id: SOURCE_ID, type: '3' },
]

/** 迭代源产出到达 → 总轮数 = 迭代源数组长度 */
const withTotal = (state, length) => applyRunEvent(state, 'node', {
  node: SOURCE_ID,
  state: { [SOURCE_ID]: { description: Array.from({ length }, (_, i) => `第 ${i + 1} 项`) } },
})

/** 一轮里两个子块的事件（各自带本轮轮次下标 startIndex） */
const feedRound = (state, roundIndex) => {
  const payloadState = { [CONTAINER_ID]: { index: roundIndex, item: `第 ${roundIndex + 1} 项` } }
  return [CHILD_A, CHILD_B].reduce(
    (acc, nodeId) => applyRunEvent(acc, 'node', { node: nodeId, state: { ...payloadState, [nodeId]: { result: '模拟文本1' } } }),
    state,
  )
}

test('轮次按事件携带的下标取值：2 个子块跑 2 轮显示 2/2 轮', () => {
  let state = beginRun(createRunState(), canvasNodes())
  state = withTotal(state, 2)
  assert.equal(iterationProgressOf(state, CONTAINER_ID).total, 2)

  state = feedRound(state, 0)
  assert.equal(iterationProgressOf(state, CONTAINER_ID).done, 1, '一轮里的两个子块事件只算一轮')

  state = feedRound(state, 1)
  const progress = iterationProgressOf(state, CONTAINER_ID)
  assert.equal(progress.done, 2, '同一轮多次收到子块事件也不会重复计数（取 max）')
  assert.equal(progress.total, 2)
  assert.ok(progress.done <= progress.total, '完成轮次不会超过总轮数')
})

test('兜底：事件里没有轮次下标时退回按子块事件累加', () => {
  let state = beginRun(createRunState(), canvasNodes())
  state = withTotal(state, 2)

  // 老后端（不带 state[容器id].index）的事件
  for (const nodeId of [CHILD_A, CHILD_B]) {
    state = applyRunEvent(state, 'node', { node: nodeId, state: { [nodeId]: { result: '模拟文本1' } } })
  }

  assert.equal(iterationProgressOf(state, CONTAINER_ID).done, 2)
})
