/**
 * 管理端请求失败的判定
 *
 * 页面的兜底策略要区分两种失败，不能一律吞掉：
 *   - 业务失败（响应回来了，CommonResult.code !== 0）：必须提示，不能拿演示数据糊过去；
 *   - 接口不可用（网络错误、404、5xx）：说明后端没起或 /admin/** 未实现，可以切演示数据。
 *
 * @param {any} error axios / 拦截器抛出的错误
 * @returns {boolean}
 */
export function isApiUnavailable(error) {
  if (!error) return true
  // 拦截器对业务失败挂了数字 code（CommonResult.code）；axios 的 'ERR_NETWORK' 不算
  if (typeof error.code === 'number') return false

  const status = error?.response?.status
  if (!status) return true
  return status === 404 || status >= 500
}
