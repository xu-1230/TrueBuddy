/**
 * 知识库页 URL 契约的行为用例
 *
 * 锁住：
 *   - /index/knowledge-base?space= 的解析与规范化；
 *   - 非法 space、重复 space 的边界；
 *   - 规范 URL 的拼接（无 space = 空间列表）。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  knowledgeBaseLocation,
  normalizeSpaceId,
  parseKnowledgeBaseRoute,
} from '../src/views/knowledgebase/utils/route.js'

test('空间列表：无 space 参数', () => {
  assert.deepEqual(parseKnowledgeBaseRoute({}), {
    spaceId: null,
    hasInvalidSpace: false,
  })
})

test('空间页：合法 space 解析为 number', () => {
  assert.deepEqual(parseKnowledgeBaseRoute({ space: '12' }), {
    spaceId: 12,
    hasInvalidSpace: false,
  })
})

test('space 前后空白会被规范化', () => {
  assert.equal(parseKnowledgeBaseRoute({ space: ' 12 ' }).spaceId, 12)
})

test('非法 space 标记 hasInvalidSpace 并丢弃 spaceId', () => {
  for (const space of ['abc', '0', '-1', '1.5', '12x', {}, '9007199254740992']) {
    const state = parseKnowledgeBaseRoute({ space })
    assert.equal(state.spaceId, null, `space=${String(space)}`)
    assert.equal(state.hasInvalidSpace, true, `space=${String(space)}`)
  }
})

test('space 空值视为未携带，不报非法', () => {
  const state = parseKnowledgeBaseRoute({ space: '' })
  assert.equal(state.spaceId, null)
  assert.equal(state.hasInvalidSpace, false)
})

test('重复 space 取第一个', () => {
  assert.equal(parseKnowledgeBaseRoute({ space: ['5', '6'] }).spaceId, 5)
})

test('重复 space 中第一个非法即视为非法', () => {
  const state = parseKnowledgeBaseRoute({ space: ['abc', '6'] })
  assert.equal(state.spaceId, null)
  assert.equal(state.hasInvalidSpace, true)
})

test('生成规范 URL：空间列表 / 空间页', () => {
  assert.deepEqual(knowledgeBaseLocation(), {
    name: 'knowledge-base',
    query: {},
  })
  assert.deepEqual(knowledgeBaseLocation(12), {
    name: 'knowledge-base',
    query: { space: '12' },
  })
  assert.deepEqual(knowledgeBaseLocation('12'), {
    name: 'knowledge-base',
    query: { space: '12' },
  })
})

test('生成规范 URL：非法 id 被丢弃回列表', () => {
  assert.deepEqual(knowledgeBaseLocation('abc'), {
    name: 'knowledge-base',
    query: {},
  })
  assert.deepEqual(knowledgeBaseLocation(0), {
    name: 'knowledge-base',
    query: {},
  })
})

test('normalizeSpaceId 接受正整数数字与字符串', () => {
  assert.equal(normalizeSpaceId(12), 12)
  assert.equal(normalizeSpaceId('12'), 12)
  assert.equal(normalizeSpaceId('0012'), 12)
  assert.equal(normalizeSpaceId(0), null)
  assert.equal(normalizeSpaceId(-1), null)
  assert.equal(normalizeSpaceId(''), null)
  assert.equal(normalizeSpaceId(null), null)
})
