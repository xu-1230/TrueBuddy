/**
 * 作业批改模块通用格式化工具
 */

const pad = (value) => String(value).padStart(2, '0')

/** 日期时间 → 「2026-11-16 14:05」（空值回落空串；兼容空格分隔的常见后端格式） */
export function formatDateTime(value) {
  if (value == null || value === '') return ''
  const normalized = typeof value === 'string' ? value.replace(/^(\d{4}-\d{2}-\d{2})\s+/, '$1T') : value
  const date = normalized instanceof Date ? normalized : new Date(normalized)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 字节 → 人类可读大小 */
export function formatFileSize(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 毫秒 → 「1 分 20 秒」/「12 秒」 */
export function formatDuration(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return ''
  const totalSeconds = Math.round(ms / 1000)
  if (totalSeconds < 60) return `${totalSeconds} 秒`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return seconds ? `${minutes} 分 ${seconds} 秒` : `${minutes} 分`
}

/** 评分配置缺省值（与面板 / 工作流 startnode 保持一致） */
const DEFAULT_RANGE = { min: 0, max: 100, pass: 60 }

const rangeOf = (range = {}) => ({
  min: Number.isFinite(Number(range.min)) ? Number(range.min) : DEFAULT_RANGE.min,
  max: Number.isFinite(Number(range.max)) ? Number(range.max) : DEFAULT_RANGE.max,
  pass: Number.isFinite(Number(range.pass)) ? Number(range.pass) : DEFAULT_RANGE.pass,
})

/**
 * 得分 → 配色档位（优秀 / 良好 / 及格 / 待改进）。
 *
 * 以及格线为基线向满分方向两等分：待改进 < pass；及格 ≥ pass；
 * 良好 ≥ pass + 50%×(max−pass)；优秀 ≥ pass + 75%×(max−pass)。
 * 0–100 / 60 时即 80 良好、90 优秀。
 */
export function scoreTone(score, range = DEFAULT_RANGE) {
  if (!Number.isFinite(score)) return 'pending'
  const { max, pass } = rangeOf(range)
  if (score < pass) return 'weak'
  const span = max - pass
  if (span <= 0) return 'pass'
  if (score >= pass + span * 0.75) return 'excellent'
  if (score >= pass + span * 0.5) return 'good'
  return 'pass'
}

/** 得分在评分区间里的比例（0–1），用于刻度光点位置 */
export function scoreRatio(score, range = DEFAULT_RANGE) {
  if (!Number.isFinite(score)) return 0
  const { min, max } = rangeOf(range)
  if (max <= min) return 0
  return Math.min(Math.max((score - min) / (max - min), 0), 1)
}

/** 评分区间的显示档位（用于进度条刻度标签） */
export function scoreScaleOf(range = DEFAULT_RANGE) {
  const { min, max, pass } = rangeOf(range)
  const excellent = max - pass > 0 ? pass + (max - pass) * 0.75 : pass
  return { min, max, pass, excellent }
}
