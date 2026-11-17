/**
 * 工作流页 URL 契约与全幅路径匹配的行为用例（ADR 0007）
 *
 * 锁住：
 *   - 雪花 id 全程按字符串承载（不转 Number）；
 *   - 列表 / 新画布 / 画布三种 location 的拼接；
 *   - `/index/workflow`（列表）与它的子路径（画布 / new）在全幅判定上的差异。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  normalizeWorkflowId,
  workflowCanvasLocation,
  workflowListLocation,
  workflowNewLocation,
} from '../src/views/workflow2/utils/route.js'
import { isChildPath } from '../src/utils/route.js'

test('normalizeWorkflowId 保留雪花 id 的字符串精度', () => {
  const snowflake = '1798123456789012345'
  assert.equal(normalizeWorkflowId(snowflake), snowflake)
  assert.equal(normalizeWorkflowId(' 201 '), '201')
})

test('normalizeWorkflowId 拒绝非纯数字', () => {
  for (const value of ['', 'abc', '12x', '1.5', '-1', 'new', null, undefined, 201]) {
    assert.equal(normalizeWorkflowId(value), null, `value=${String(value)}`)
  }
})

test('画布 location：合法 id 用路径参数', () => {
  assert.deepEqual(workflowCanvasLocation('1798123456789012345'), {
    name: 'workflow-canvas',
    params: { id: '1798123456789012345' },
  })
})

test('画布 location：非法 id 回列表，避免带一个打不开的地址', () => {
  assert.deepEqual(workflowCanvasLocation('abc'), { name: 'workflow' })
  assert.deepEqual(workflowCanvasLocation(''), { name: 'workflow' })
})

test('新画布 / 列表 location', () => {
  assert.deepEqual(workflowNewLocation(), { name: 'workflow-new' })
  assert.deepEqual(workflowListLocation(), { name: 'workflow' })
})

test('全幅判定：列表不算子路径，画布 / new 算', () => {
  assert.equal(isChildPath('/index/workflow', '/index/workflow'), false)
  assert.equal(isChildPath('/index/workflow/123', '/index/workflow'), true)
  assert.equal(isChildPath('/index/workflow/new', '/index/workflow'), true)
})

test('全幅判定：相近路径不误命中', () => {
  assert.equal(isChildPath('/index/workflow-x', '/index/workflow'), false)
  assert.equal(isChildPath('/index/workflowx/1', '/index/workflow'), false)
  assert.equal(isChildPath('', '/index/workflow'), false)
})
