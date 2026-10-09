/**
 * 在线名单取值逻辑的行为用例（Node 内置 `node:test`）。
 *
 * 锁两条：①**在线的唯一真相源是 LiveKit 房间事件**，房间没连上就不列名单、不报人数
 * （台账含「尚未显式离开」的崩溃残留，列出来就是误导 —— docs/adr/0002）；
 * ②控制台是主持人的操作台，**老师自己不占参会席位**（docs/16 Q22）。
 *
 * 运行：cd frontend && node --test tests/attendees.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { fromRoomParticipants, roleOfParticipant, selectMemberPanel } from '../src/views/meeting/utils/attendees.js'

/** 房间里的 participant：本端老师 + 远端两个学生 */
const host = { identity: 'host', name: '老师', metadata: '{"role":1,"nickname":"老师"}' }
const studentA = { identity: 'attendee-7', name: '小明', metadata: '{"role":2,"nickname":"小明"}' }
const studentB = { identity: 'attendee-8', name: '小红', metadata: '{"role":2,"nickname":"小红"}' }

test('房间成员映射不再携带 online 布尔', () => {
  const list = fromRoomParticipants([host, studentA])

  assert.equal(list.length, 2)
  assert.deepEqual(list[0], { id: 'host', nickname: '老师', role: 1 })
  assert.equal('online' in list[1], false, '成员项里不该再有 online 字段等着被误用')
})

test('控制台把自己摘出去：omitHost 去掉老师，人数不再虚高 1', () => {
  const everyone = fromRoomParticipants([host, studentA, studentB])
  const forConsole = fromRoomParticipants([host, studentA, studentB], { omitHost: true })

  assert.equal(everyone.length, 3)
  assert.deepEqual(forConsole.map((item) => item.id), ['attendee-7', 'attendee-8'])
  assert.equal(selectMemberPanel({ attendees: forConsole, connected: true }).onlineCount, 2)
})

test('participant 没有 name 时回落到 identity，metadata 非法按学生处理', () => {
  const anonymous = { identity: 'attendee-9', metadata: 'not-json' }

  assert.deepEqual(fromRoomParticipants([anonymous]), [{ id: 'attendee-9', nickname: 'attendee-9', role: 2 }])
  assert.equal(roleOfParticipant({ identity: 'x' }), 2, '缺 metadata 不能把人升级成老师')
})

test('房间已连接：名单与人数都取房间成员', () => {
  const attendees = fromRoomParticipants([host, studentA])

  const panel = selectMemberPanel({ attendees, connected: true })

  assert.equal(panel.degraded, false)
  assert.equal(panel.attendees.length, 2)
  assert.equal(panel.onlineCount, 2)
})

test('房间没连上：不列名单、也不报人数', () => {
  // 库里的台账：两行「尚未显式离开」+ 一行已离开 —— 降级时一行都不该出现在名单里
  const ledger = [
    { id: 7, nickname: '小明', leaveTime: null },
    { id: 8, nickname: '小红', leaveTime: null },
    { id: 9, nickname: '小刚', leaveTime: '2026-10-09T01:30:00' },
  ]

  const panel = selectMemberPanel({ attendees: ledger, connected: false })

  assert.equal(panel.degraded, true)
  assert.deepEqual(panel.attendees, [], '降级时不得把台账当名单展示')
  assert.equal(panel.onlineCount, null, '没有真相源时不能编一个在线数字出来')
})

test('断开再重连：连接态一变回 connected，名单立刻回到房间口径', () => {
  const attendees = fromRoomParticipants([host, studentA])

  const down = selectMemberPanel({ attendees, connected: false })
  const up = selectMemberPanel({ attendees, connected: true })

  assert.equal(down.degraded, true)
  assert.equal(up.degraded, false)
  assert.equal(up.onlineCount, attendees.length)
})
