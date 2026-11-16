/**
 * 会议时间与会议号的纯函数（无 Vue 依赖，便于 Node 单测）。
 *
 * 后端 `startTime` / `endTime` 是 LocalDateTime，序列化形态可能是
 * `yyyy-MM-dd HH:mm:ss` 或 ISO；这里做宽容解析，解析不出来就显示占位符，
 * 不在展示层猜时间。
 */

/**
 * 宽容解析会议时间。
 *
 * @param {string|number|Date|null|undefined} value
 * @returns {number|null} 毫秒时间戳
 */
export function parseMeetingTime(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value.getTime()

  const matched = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(
    String(value).trim(),
  )
  if (matched) {
    const [, year, month, day, hour, minute, second] = matched
    return new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hour),
      Number(minute),
      Number(second || 0),
    ).getTime()
  }

  const fallback = Date.parse(value)
  return Number.isNaN(fallback) ? null : fallback
}

/**
 * 开讲时长：HH:MM:SS。
 *
 * - 没有开始时间：`--:--:--`
 * - 已结束：冻结在 endTime
 * - 负值（时钟漂移）按 0 处理
 *
 * @param {string|number|Date|null|undefined} startTime
 * @param {number} [now]
 * @param {string|number|Date|null|undefined} [endTime]
 * @returns {string}
 */
export function formatElapsed(startTime, now = Date.now(), endTime = null) {
  const start = parseMeetingTime(startTime)
  if (start === null) return '--:--:--'
  const end = parseMeetingTime(endTime) ?? now
  const total = Math.max(0, Math.floor((end - start) / 1000))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  return [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':')
}

/**
 * 会议号分组显示：`123456` → `123 456`。
 * 只用于视觉分组；复制/请求仍使用原始会议号。
 *
 * @param {string|number|null|undefined} value
 * @returns {string}
 */
export function formatMeetingNo(value) {
  const digits = String(value ?? '').replace(/\D/g, '')
  if (!digits) return '—'
  return digits.replace(/(\d{3})(?=\d)/g, '$1 ').trim()
}
