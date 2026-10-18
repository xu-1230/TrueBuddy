/**
 * 管理端格式化工具（纯函数）
 *
 * 统一放在这里的原因：表格列、详情抽屉、健康卡都要按同一格式显示，
 * 分散写会出现「同一天有的显示 10-18 有的显示 2026/10/18」。
 */

const EMPTY = '—'

function toDate(value) {
  if (value === null || value === undefined || value === '') return null
  // 兼容「2026-10-18 09:00」这种后端常见格式：Safari 不接受空格分隔
  const normalized = typeof value === 'string' ? value.replace(/^(\d{4}-\d{2}-\d{2})\s+/, '$1T') : value
  const date = normalized instanceof Date ? normalized : new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

const pad = (value) => String(value).padStart(2, '0')

/**
 * 日期时间：2026-10-18 14:05
 * @param {string|number|Date|null} value
 * @returns {string}
 */
export function formatDateTime(value) {
  const date = toDate(value)
  if (!date) return EMPTY
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * 日期：2026-10-18
 * @param {string|number|Date|null} value
 * @returns {string}
 */
export function formatDate(value) {
  const date = toDate(value)
  if (!date) return EMPTY
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * 相对时间：刚刚 / 5 分钟前 / 3 小时前 / 2 天前 / 日期
 * @param {string|number|Date|null} value
 * @param {number} now 便于测试注入当前时间
 * @returns {string}
 */
export function formatRelativeTime(value, now = Date.now()) {
  const date = toDate(value)
  if (!date) return EMPTY

  const diff = now - date.getTime()
  if (diff < 0) return formatDateTime(date)

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDate(date)
}

/**
 * 字节数：1.2 MB
 * @param {number|null} bytes
 * @returns {string}
 */
export function formatBytes(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value) || value < 0) return EMPTY
  if (value === 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const exponent = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  const size = value / 1024 ** exponent
  const digits = exponent === 0 || size >= 100 ? 0 : size >= 10 ? 1 : 2
  return `${size.toFixed(digits)} ${units[exponent]}`
}

/**
 * 数字千分位：1,286
 * @param {number|string|null} value
 * @returns {string}
 */
export function formatNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return EMPTY
  return number.toLocaleString('zh-CN')
}

/**
 * 会议时长（秒 → 1 小时 05 分）
 * @param {number|null} seconds
 * @returns {string}
 */
export function formatDuration(seconds) {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value < 0) return EMPTY

  const totalMinutes = Math.floor(value / 60)
  if (totalMinutes < 1) return `${Math.max(0, Math.round(value))} 秒`

  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  if (hours === 0) return `${minutes} 分钟`
  return minutes === 0 ? `${hours} 小时` : `${hours} 小时 ${pad(minutes)} 分`
}

/**
 * 毫秒耗时：820 ms / 1.4 s
 * @param {number|null} ms
 * @returns {string}
 */
export function formatLatency(ms) {
  if (ms === null || ms === undefined || ms === '') return EMPTY
  const value = Number(ms)
  if (!Number.isFinite(value) || value < 0) return EMPTY
  return value >= 1000 ? `${(value / 1000).toFixed(1)} s` : `${Math.round(value)} ms`
}
