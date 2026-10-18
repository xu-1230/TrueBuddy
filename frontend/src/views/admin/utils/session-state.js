/**
 * 管理端会话错误分类（纯函数，便于单测）
 *
 * 三种结果必须分开处理：
 *   denied  —— 已登录但无管理端权限（403 / 业务拒绝），跳 403；
 *   expired —— 登录态失效（401），清 Token 后重新登录；
 *   offline —— 请求没到达后端（网络错误），允许以演示数据浏览。
 */

export const SESSION_ERROR = {
  DENIED: 'denied',
  EXPIRED: 'expired',
  OFFLINE: 'offline',
}

/**
 * @param {unknown} error axios 错误或响应拦截器抛出的业务 Error
 * @returns {'denied'|'expired'|'offline'}
 */
export function classifySessionError(error) {
  const httpStatus = error?.response?.status
  if (httpStatus === 401) return SESSION_ERROR.EXPIRED
  if (httpStatus === 403) return SESSION_ERROR.DENIED
  // 响应拦截器对业务失败会挂数字 code；axios 的网络错误 code 是字符串
  if (typeof error?.code === 'number') return SESSION_ERROR.DENIED
  return SESSION_ERROR.OFFLINE
}
