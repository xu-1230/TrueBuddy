/**
 * 工作流列表分组的纯函数用例
 *
 * 锁住：
 *   - 内置模板单独成组、用户自建保持后端返回顺序；
 *   - 空 / 脏数据不产生空卡片；
 *   - 可见性切换目标只有 0 / 1。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import { groupMyWorkflows, visibilityTarget } from '../src/views/workflow2/utils/workflow-list.js'

test('分组：builtin 与自建分开，各自保持原顺序', () => {
  const list = [
    { id: '1', name: '标准批改作业', builtin: true },
    { id: '2', name: '我的流程 A' },
    { id: '3', name: '我的流程 B', builtin: false },
  ]

  const { builtin, mine } = groupMyWorkflows(list)

  assert.deepEqual(builtin.map((item) => item.id), ['1'])
  assert.deepEqual(mine.map((item) => item.id), ['2', '3'])
})

test('分组：空列表 / 非数组都安全', () => {
  assert.deepEqual(groupMyWorkflows(), { builtin: [], mine: [] })
  assert.deepEqual(groupMyWorkflows(null), { builtin: [], mine: [] })
  assert.deepEqual(groupMyWorkflows('oops'), { builtin: [], mine: [] })
})

test('分组：跳过空项，不产生空卡片', () => {
  const { builtin, mine } = groupMyWorkflows([null, undefined, { id: '1' }])
  assert.deepEqual(builtin, [])
  assert.deepEqual(mine.map((item) => item.id), ['1'])
})

test('可见性目标：公有 → 私有，其余 → 公有', () => {
  assert.equal(visibilityTarget(1), 0)
  assert.equal(visibilityTarget(0), 1)
  assert.equal(visibilityTarget(null), 1)
  assert.equal(visibilityTarget(undefined), 1)
})
