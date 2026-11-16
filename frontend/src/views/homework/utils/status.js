/**
 * 批改任务状态映射（与后端 homework_task.status 注释保持一致）
 *
 * 0=已上传待批改，1=批改中，2=成功，3=部分成功，4=失败
 */

export const HOMEWORK_STATUS = {
  UPLOAD: 0,
  GRADING: 1,
  SUCCESS: 2,
  PARTIAL: 3,
  FAILED: 4,
}

const STATUS_TEXT = {
  0: '待批改',
  1: '批改中',
  2: '成功',
  3: '部分成功',
  4: '失败',
}

/**
 * 状态值 → 文案（未知值回落「未知」）
 * @param {number|null} status
 * @returns {string}
 */
export function statusTextOf(status) {
  return STATUS_TEXT[status] ?? '未知'
}

/**
 * 状态值 → 徽标配色档位：
 * 终态成功（2/3）绿色、终态失败（4）红色、进行中的状态（0/1/未知）中性
 * @param {number|null} status
 * @returns {'success'|'failed'|'pending'}
 */
export function statusToneOf(status) {
  if (status === HOMEWORK_STATUS.SUCCESS || status === HOMEWORK_STATUS.PARTIAL) return 'success'
  if (status === HOMEWORK_STATUS.FAILED) return 'failed'
  return 'pending'
}
