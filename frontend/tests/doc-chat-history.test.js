/**
 * 文档聊天记录映射的行为用例
 *
 * 锁住：
 *   - 服务端 id 加 `db-` 前缀，与本地消息 id 不冲突；
 *   - 助手 blocks 优先，缺失时回退段落；
 *   - 用户消息带 selections / delivered，system 不显示时间；
 *   - 非法条目被过滤，顺序保持不变。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import { toChatMessage, toChatMessages } from '../src/views/doctemplate/utils/chat-history.js'

test('用户消息：文本、选区、时间与 db 前缀 id', () => {
  const message = toChatMessage({
    id: 7,
    role: 'user',
    content: '把标题改短',
    selections: [{ locator: 'p:1', label: '段落 · 标题' }],
    createTime: '2026-11-17T10:30:00',
  })

  assert.equal(message.id, 'db-7')
  assert.equal(message.role, 'user')
  assert.equal(message.content, '把标题改短')
  assert.equal(message.delivered, true)
  assert.deepEqual(message.selections, [{ locator: 'p:1', label: '段落 · 标题' }])
  assert.equal(typeof message.at, 'number')
})

test('助手消息：优先使用结构化 blocks', () => {
  const blocks = [
    { type: 'p', text: '已修改 2 处。' },
    { type: 'ul', items: ['标题改为系统分析', '删除重复段落'] },
  ]
  const message = toChatMessage({
    id: 8,
    role: 'assistant',
    content: '已修改 2 处。',
    blocks,
    versionId: 12,
    createTime: '2026-11-17T10:31:00',
  })

  assert.deepEqual(message.content, blocks)
  assert.equal(message.versionId, 12)
  assert.equal(message.superseded, false)
})

test('助手消息：blocks 缺失时回退为单段落', () => {
  const message = toChatMessage({
    id: 9,
    role: 'assistant',
    content: '未修改文档。',
    blocks: null,
    createTime: '2026-11-17T10:32:00',
  })

  assert.deepEqual(message.content, [{ type: 'p', text: '未修改文档。' }])
  assert.equal(message.versionId, null)
})

test('失败回复与失效状态：原样映射', () => {
  const message = toChatMessage({
    id: 10,
    role: 'assistant',
    content: '修改失败：模型超时',
    error: true,
    superseded: true,
    createTime: '2026-11-17T10:33:00',
  })

  assert.equal(message.error, true)
  assert.equal(message.superseded, true)
})

test('system 提示：只保留文本，不显示时间', () => {
  const message = toChatMessage({
    id: 11,
    role: 'system',
    content: '已撤回到上一版。',
    createTime: '2026-11-17T10:34:00',
  })

  assert.equal(message.role, 'system')
  assert.equal(message.content, '已撤回到上一版。')
  assert.equal(message.at, null)
})

test('非法条目被过滤，合法条目顺序不变', () => {
  const list = toChatMessages([
    { id: 1, role: 'user', content: 'A', createTime: '2026-11-17T10:00:00' },
    null,
    { id: 2, role: 'unknown', content: 'B' },
    { id: 3, role: 'assistant', content: 'C', createTime: '2026-11-17T10:01:00' },
  ])

  assert.deepEqual(
    list.map((item) => item.id),
    ['db-1', 'db-3'],
  )
})

test('非数组输入返回空列表', () => {
  assert.deepEqual(toChatMessages(null), [])
  assert.deepEqual(toChatMessages(undefined), [])
})
