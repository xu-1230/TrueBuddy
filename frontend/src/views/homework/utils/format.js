/**
 * 作业批改模块通用格式化工具
 */

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

/** 得分 → 配色档位（优秀 / 良好 / 及格 / 待改进） */
export function scoreTone(score) {
  if (!Number.isFinite(score)) return 'pending'
  if (score >= 90) return 'excellent'
  if (score >= 80) return 'good'
  if (score >= 60) return 'pass'
  return 'weak'
}
