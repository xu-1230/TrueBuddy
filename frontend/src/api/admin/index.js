import service from '../index'

/**
 * 管理端接口前缀
 *
 * 约定：后端 context-path 为 /api，管理端 Controller 统一挂在 /admin 下，
 * 因此完整地址形如 /api/admin/user/page。这里只拼 /admin，/api 由 axios baseURL 负责，
 * 两处都不重复写。
 */
export const ADMIN_API = '/admin'

/**
 * 管理端请求：复用全局 axios 实例（拦截器 / Token / CommonResult 解包都不重复实现）
 *
 * @param {{ url: string, method?: string, params?: object, data?: object }} config
 * @returns {Promise<any>}
 */
export function adminRequest(config) {
  return service({ ...config, url: `${ADMIN_API}${config.url}` })
}

export default adminRequest
