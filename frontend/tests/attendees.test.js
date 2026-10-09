/**
 * 「在线成员 vs 入会台账」裁决逻辑的行为用例（Node 内置 `node:test`）。
 *
 * 锁的行为：在线的唯一真相源是 LiveKit 房间事件，REST 台账不得冒充在线名单
 * （docs/adr/0002、docs/specs/2026-10-09-会议生命周期修复.md 故事 4/5/6/13）。
 * 之所以单独测这个纯函数：曾经的实现是房间事件与 10s 轮询写**同一个** attendees 数组，
 * 谁后写谁赢，页面表现就是在线名单被库里的脏数据盖掉——这类"谁覆盖谁"的裁决只能靠行为用例钉住。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { fromRoomParticipants, pickDisplayAttendees, roleOfParticipant } from '../src/views/meeting/utils/attendees.js'

/** 房间里的 participant：本端老师 + 远端一个学生 */
const host = { identity: 'host', name: '老师', metadata: '{"role":1,"nickname":"老师"}' }
const student = { identity: 'attendee-7', name: '小明', metadata: '{"role":2,"nickname":"小明"}' }

/** 台账行：一个尚未显式离开、一个已离开 */
const pendingRow = { id: 7, nickname: '小明', role: 2, joinTime: '2026-10-09T01:00:00', leaveTime: null }
const leftRow = { id: 8, nickname: '小红', role: 2, joinTime: '2026-10-09T01:01:00', leaveTime: '2026-10-09T01:30:00' }

test('房间成员映射不再携带 online 布尔', () => {
  const list = fromRoomParticipants([host, student])

  assert.equal(list.length, 2)
  assert.deepEqual(list[0], { id: 'host', nickname: '老师', role: 1 })
  assert.equal('online' in list[1], false, '成员项里不该再有 online 字段等着被误用')
})

test('participant 没有 name 时回落到 identity，metadata 非法时按学生处理', () => {
  const anonymous = { identity: 'attendee-9', metadata: 'not-json' }

  assert.deepEqual(fromRoomParticipants([anonymous]), [{ id: 'attendee-9', nickname: 'attendee-9', role: 2 }])
  assert.equal(roleOfParticipant({ identity: 'x' }), 2, '缺 metadata 不能把人升级成老师')
})

test('房间已连接：后到的 REST 台账不覆盖在线名单', () => {
  const roomAttendees = fromRoomParticipants([host, student])
  // 台账里有 20 条崩溃残留的「尚未显式离开」行——它们不该出现在在线名单里
  const joinLedger = [pendingRow, leftRow, pendingRow, pendingRow]

  const display = pickDisplayAttendees({ roomAttendees, joinLedger, connection: 'connected' })

  assert.equal(display.degraded, false)
  assert.equal(display.attendees.length, 2, '在线名单只认房间成员，与台账行数无关')
  assert.equal(display.onlineCount, 2)
})

test('房间断开：降级展示台账，且不给出在线人数', () => {
  const display = pickDisplayAttendees({
    roomAttendees: fromRoomParticipants([host]),
    joinLedger: [pendingRow, leftRow],
    connection: 'reconnecting',
  })

  assert.equal(display.degraded, true)
  assert.equal(display.onlineCount, null, '没有真相源时不能编一个在线数字出来')
  assert.deepEqual(display.attendees.map((item) => item.id), [7], '已离开的行不进降级名单')
})

test('重连恢复：连接态一变回 connected，在线名单立刻回到房间口径', () => {
  const roomAttendees = fromRoomParticipants([host, student])
  const joinLedger = [pendingRow, leftRow]

  const down = pickDisplayAttendees({ roomAttendees, joinLedger, connection: 'disconnected' })
  const up = pickDisplayAttendees({ roomAttendees, joinLedger, connection: 'connected' })

  assert.equal(down.degraded, true)
  assert.equal(up.degraded, false)
  assert.equal(up.onlineCount, roomAttendees.length)
})
