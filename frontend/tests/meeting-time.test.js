/**
 * 会议时间 / 会议号的纯函数用例。
 *
 * 锁住：
 *   - yudao 空格分隔与 ISO 两种时间形态都能解析；
 *   - 没有开始时间时不编造时长；
 *   - 已结束的会议冻结在 endTime；
 *   - 会议号只做视觉分组，不改变原始数字。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  formatElapsed,
  formatMeetingNo,
  parseMeetingTime,
} from '../src/views/meeting/utils/meeting-time.js'

const START = new Date(2026, 10, 16, 10, 0, 0).getTime()

test('parseMeetingTime 吃下空格分隔、ISO 与 Date，非法值返回 null', () => {
  assert.equal(parseMeetingTime('2026-11-16 10:00:00'), START)
  assert.equal(parseMeetingTime('2026-11-16T10:00:00'), START)
  assert.equal(parseMeetingTime(new Date(START)), START)
  assert.equal(parseMeetingTime(null), null)
  assert.equal(parseMeetingTime(''), null)
  assert.equal(parseMeetingTime('不是时间'), null)
})

test('formatElapsed 返回 HH:MM:SS，并按 now 推进', () => {
  assert.equal(formatElapsed('2026-11-16 10:00:00', START), '00:00:00')
  assert.equal(formatElapsed('2026-11-16 10:00:00', START + 65_000), '00:01:05')
  assert.equal(formatElapsed('2026-11-16 10:00:00', START + 3_723_000), '01:02:03')
})

test('formatElapsed 没有开始时间时显示占位，时钟漂移按 0 处理', () => {
  assert.equal(formatElapsed(null, START), '--:--:--')
  assert.equal(formatElapsed('2026-11-16 10:00:00', START - 5_000), '00:00:00')
})

test('formatElapsed 已结束时冻结在 endTime', () => {
  assert.equal(
    formatElapsed('2026-11-16 10:00:00', START + 3_600_000, '2026-11-16 10:05:00'),
    '00:05:00',
  )
})

test('formatMeetingNo 只做三位分组，空值给占位', () => {
  assert.equal(formatMeetingNo('123456'), '123 456')
  assert.equal(formatMeetingNo(123456), '123 456')
  assert.equal(formatMeetingNo(''), '—')
  assert.equal(formatMeetingNo(null), '—')
})
