/**
 * 会议聊天连续消息分组的行为用例
 *
 * 锁住：
 *   - 同一发送者连续消息合并、组首标记；
 *   - 5 分钟时间窗、时间缺失、发送者切换的边界；
 *   - self / 老师 / 数字人的身份键与展示标记。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  CHAT_GROUP_GAP_MS,
  groupMeetingMessages,
  senderKeyOf,
} from '../src/views/meeting/utils/chat-groups.js'

const at = (minute) => `2026-11-18T10:${String(minute).padStart(2, '0')}:00`

test('空列表 / 非数组安全', () => {
  assert.deepEqual(groupMeetingMessages(), [])
  assert.deepEqual(groupMeetingMessages(null), [])
  assert.deepEqual(groupMeetingMessages('oops'), [])
})

test('同一发送者连续消息：首条 groupStart，后续合并', () => {
  const list = [
    { id: 1, attendeeId: 7, nickname: '学生', role: 2, content: 'A', createTime: at(1) },
    { id: 2, attendeeId: 7, nickname: '学生', role: 2, content: 'B', createTime: at(2) },
  ]

  const result = groupMeetingMessages(list)

  assert.deepEqual(result.map((item) => item.groupStart), [true, false])
  assert.deepEqual(result.map((item) => item.content), ['A', 'B'])
  assert.equal(result[0].senderKey, 'attendee:7')
})

test('发送者切换：新起一组', () => {
  const list = [
    { id: 1, attendeeId: 7, nickname: '学生', role: 2, content: 'A', createTime: at(1) },
    { id: 2, attendeeId: 8, nickname: '另一学生', role: 2, content: 'B', createTime: at(1) },
  ]

  assert.deepEqual(
    groupMeetingMessages(list).map((item) => item.groupStart),
    [true, true],
  )
})

test('超过 5 分钟：同一发送者也新起一组', () => {
  const list = [
    { id: 1, attendeeId: 7, nickname: '学生', role: 2, content: 'A', createTime: at(1) },
    { id: 2, attendeeId: 7, nickname: '学生', role: 2, content: 'B', createTime: at(7) },
  ]

  assert.deepEqual(
    groupMeetingMessages(list).map((item) => item.groupStart),
    [true, true],
  )
  assert.equal(CHAT_GROUP_GAP_MS, 5 * 60 * 1000)
})

test('时间缺失：同一发送者仍合并，不按时间窗拆', () => {
  const list = [
    { id: 1, attendeeId: 7, nickname: '学生', role: 2, content: 'A' },
    { id: 2, attendeeId: 7, nickname: '学生', role: 2, content: 'B' },
  ]

  assert.deepEqual(
    groupMeetingMessages(list).map((item) => item.groupStart),
    [true, false],
  )
})

test('老师 / 数字人无 attendeeId：按 role+kind+nickname 归并', () => {
  const list = [
    { id: 1, attendeeId: null, nickname: '王老师', role: 1, content: 'A', createTime: at(1) },
    { id: 2, attendeeId: null, nickname: '王老师', role: 1, content: 'B', createTime: at(2) },
    { id: 3, attendeeId: null, nickname: '数字人', role: 3, kind: 2, content: 'C', createTime: at(3) },
  ]

  const result = groupMeetingMessages(list)

  assert.equal(result[0].senderKey, 'role:1::王老师')
  assert.equal(result[0].host, true)
  assert.equal(result[1].groupStart, false)
  assert.equal(result[2].senderKey, 'role:3:2:数字人')
  assert.equal(result[2].digital, true)
})

test('自己的消息统一为 self，即使 attendeeId 不同', () => {
  const list = [
    { id: 1, attendeeId: null, nickname: '我', role: 1, self: true, content: 'A' },
    { id: 2, attendeeId: 99, nickname: '我', role: 2, self: true, content: 'B' },
  ]

  const result = groupMeetingMessages(list)

  assert.equal(result[0].senderKey, 'self')
  assert.equal(result[1].senderKey, 'self')
  assert.deepEqual(result.map((item) => item.self), [true, true])
  assert.equal(result[1].groupStart, false)
})

test('senderKeyOf：空消息返回空串', () => {
  assert.equal(senderKeyOf(null), '')
})
