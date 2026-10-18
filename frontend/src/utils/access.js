/**
 * 角色与权限判断（纯函数，用户端 / 管理端共用）
 *
 * 为什么单独成文件：
 *   路由守卫、侧栏菜单、按钮级显隐是三处独立调用，但必须共用同一套口径——
 *   「谁是管理员」「某权限码是否命中」只要出现第二份实现，迟早会出现
 *   「菜单能点、接口 403」这种前后端 / 两处判断打架的情况。
 *
 * 口径（与后端 RBAC 对齐）：
 *   - 角色编码来自 system_role.code（普通用户 normal / 会员 member / 管理员 admin）；
 *   - 权限标识来自 system_menu.permission，形如 system:user:list；
 *   - 超级权限用 `*` 表示，`system:*` 表示该模块全量命中。
 *
 * 注意：这里的判断只负责「界面可达性」，真正的安全边界在后端。
 */

/** 管理员角色编码：与后端 system_role.code = 'admin' 一致 */
export const ADMIN_ROLE = 'admin'

/** 管理端入口权限：与后端 system_menu.permission 一致（管理员与审核员拥有） */
export const ADMIN_PERMISSION = 'admin:dashboard'

/** 超级权限通配符（仅开发预览使用） */
export const PERMISSION_ALL = '*'

/**
 * 把角色 / 权限统一收敛成字符串数组。
 * 入参允许数组、逗号分隔字符串、null / undefined，避免调用方各自判空。
 *
 * @param {string[]|string|null|undefined} values
 * @returns {string[]}
 */
export function normalizeList(values) {
  if (!values) return []
  const list = Array.isArray(values) ? values : String(values).split(',')
  return list.map((item) => String(item).trim()).filter(Boolean)
}

/**
 * 是否具备任一所需角色。
 * 未声明所需角色 = 不限制（返回 true），与路由 meta 的缺省语义一致。
 *
 * @param {string[]|string|null} granted 已具备的角色
 * @param {string[]|string|null} required 所需角色
 * @returns {boolean}
 */
export function hasRole(granted, required) {
  const need = normalizeList(required)
  if (need.length === 0) return true
  const owned = normalizeList(granted)
  return need.some((role) => owned.includes(role))
}

/**
 * 是否为管理员（只看角色编码）
 *
 * @param {string[]|string|null} roles
 * @returns {boolean}
 */
export function isAdmin(roles) {
  return normalizeList(roles).includes(ADMIN_ROLE)
}

/**
 * 权限码是否命中。支持 `*` 与 `模块:*` 两种通配写法。
 *
 * @param {string[]|string|null} granted 已授予的权限码
 * @param {string|null} required 当前要求的权限码
 * @returns {boolean}
 */
export function hasPermission(granted, required) {
  if (!required) return true
  const owned = normalizeList(granted)
  if (owned.includes(PERMISSION_ALL)) return true
  if (owned.includes(required)) return true
  return owned.some((code) => code.endsWith(':*') && required.startsWith(code.slice(0, -1)))
}

/**
 * 合并父子路由的 meta：子路由覆盖父路由同名键。
 * 父级写 requiresAuth / requiresAdmin，子级只关心自己的 title 与 permission。
 *
 * @param {Array<{ meta?: object }>} matched
 * @returns {object}
 */
export function mergeRouteMeta(matched = []) {
  return matched.reduce((acc, record) => Object.assign(acc, record.meta), {})
}

/**
 * 判定一次导航的访问结果。
 *
 * @param {object} meta 合并后的路由 meta
 * @param {{ hasToken?: boolean, roleStatus?: string, roles?: string[], permissions?: string[] }} session
 *   roleStatus: unknown | checking | granted | denied | offline
 * @returns {'ok'|'login'|'forbidden'}
 */
export function resolveAccess(meta = {}, session = {}) {
  const { hasToken = false, roleStatus = 'unknown', permissions = [] } = session

  if (meta.requiresAuth && !hasToken) return 'login'

  if (meta.requiresAdmin) {
    // 后端明确拒绝：直接 403，不再给任意角色放行
    if (roleStatus === 'denied') return 'forbidden'
    // 管理端入口按权限码判定：管理员与审核员拥有 admin:dashboard，普通用户 / 会员被拒绝
    if (roleStatus === 'granted' && !hasPermission(permissions, ADMIN_PERMISSION)) return 'forbidden'
  }

  return 'ok'
}

/**
 * 生成登录跳转地址，并带上回跳路径。
 * 只接受站内绝对路径，避免 `//evil.com` 这类开放重定向。
 *
 * @param {string} fullPath 当前路由完整路径
 * @param {string} loginPath 登录页路径
 * @returns {{ path: string, query?: { redirect: string } }}
 */
export function buildLoginLocation(fullPath, loginPath = '/login') {
  const redirect = typeof fullPath === 'string' && fullPath.startsWith('/') && !fullPath.startsWith('//') ? fullPath : ''
  return redirect ? { path: loginPath, query: { redirect } } : { path: loginPath }
}
