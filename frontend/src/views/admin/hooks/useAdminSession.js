import { computed, reactive, readonly } from 'vue'
import { getAdminProfile, getAdminPermissions } from '@/api/admin/auth'
import { removeToken } from '@/utils/auth'
import { ADMIN_PERMISSION, ADMIN_ROLE, PERMISSION_ALL, hasPermission } from '@/utils/access'
import { SESSION_ERROR, classifySessionError } from '../utils/session-state'

/**
 * 管理端会话（模块级单例）
 *
 * 身份与权限只认服务端：初始化调用 `GET /admin/auth/profile`，
 * 由后端按 RBAC 解析角色与权限码；前端不再保存或伪造身份快照。
 * 三种结果必须分开处理，不能都当失败：
 *   granted —— 拥有 admin:dashboard 权限（管理员或审核员），进入管理端；
 *   denied  —— 已登录但没有管理端权限，跳 403；
 *   offline —— 未登录 / 后端不可达，允许以演示数据浏览，
 *              否则「只启前端不启后端」时管理端会整片打不开，无法联调 UI。
 *
 * 为什么不用 Pinia：这里只有一份会话数据，模块级 reactive 足够；
 * 用户端的 store 也都就近放在模块内（views/ppt/stores、views/meeting/stores）。
 */

const ROLE_LABELS = {
  admin: '管理员',
  auditor: '审核员',
  member: '会员',
  normal: '普通用户',
}

export const ADMIN_SESSION_STATUS = {
  UNKNOWN: 'unknown',
  CHECKING: 'checking',
  GRANTED: 'granted',
  DENIED: 'denied',
  OFFLINE: 'offline',
}

/**
 * 开发环境预览开关：只写 sessionStorage，且只在 import.meta.env.DEV 下生效。
 * 生产构建里这段判断会被整体摇掉，不会留下绕过登录的入口。
 */
export const ADMIN_PREVIEW_KEY = 'tb-admin-preview'

export function isAdminPreview() {
  if (!import.meta.env.DEV) return false
  try {
    return sessionStorage.getItem(ADMIN_PREVIEW_KEY) === '1'
  } catch {
    return false
  }
}

export function enableAdminPreview() {
  if (!import.meta.env.DEV) return false
  try {
    sessionStorage.setItem(ADMIN_PREVIEW_KEY, '1')
  } catch {
    return false
  }
  resetAdminSession()
  return true
}

function disableAdminPreview() {
  try {
    sessionStorage.removeItem(ADMIN_PREVIEW_KEY)
  } catch {
    /* 隐私模式下忽略 */
  }
}

/** 退出登录 / 退出预览：清空会话并移除预览标记 */
export function exitAdminSession() {
  disableAdminPreview()
  resetAdminSession()
}

const DEFAULT_PROFILE = {
  id: null,
  username: 'admin',
  nickname: '管理员',
  initial: '管',
  roleLabel: '超级管理员',
}

const state = reactive({
  status: ADMIN_SESSION_STATUS.UNKNOWN,
  roles: [],
  permissions: [],
  profile: { ...DEFAULT_PROFILE },
  message: '',
  verifiedAt: 0,
})

/** 并发调用（守卫 + 布局挂载）只发一次请求 */
let inflight = null

/** 把服务端资料写入会话状态；roles 只认服务端返回值 */
function applyProfile(profile) {
  if (!profile || typeof profile !== 'object') {
    return
  }
  const roles = Array.isArray(profile.roles) ? profile.roles.filter(Boolean) : []
  const nickname = profile.nickname || profile.name || DEFAULT_PROFILE.nickname
  state.roles = roles
  state.profile = {
    id: profile.userId ?? profile.id ?? DEFAULT_PROFILE.id,
    username: profile.username || DEFAULT_PROFILE.username,
    nickname,
    initial: String(nickname).slice(0, 1) || DEFAULT_PROFILE.initial,
    roleLabel:
      profile.roleLabel ||
      profile.roleName ||
      roles.map((role) => ROLE_LABELS[role] || role).join(' / ') ||
      DEFAULT_PROFILE.roleLabel,
  }
}

/** profile 已带权限时不再多发一次请求；缺失才回退到 /permissions */
async function resolvePermissions(profile) {
  if (Array.isArray(profile?.permissions)) {
    return profile.permissions
  }
  const permissions = await getAdminPermissions()
  return Array.isArray(permissions) ? permissions : []
}

/**
 * 校验当前账号是否有管理端入口权限（admin:dashboard）。
 * @param {{ force?: boolean }} options force = true 时忽略已通过的缓存重新校验
 * @returns {Promise<string>} ADMIN_SESSION_STATUS
 */
export async function verifyAdminSession({ force = false } = {}) {
  if (!force && state.status === ADMIN_SESSION_STATUS.GRANTED) return state.status
  if (inflight) return inflight

  // 开发预览：不请求后端，直接进入「演示数据」状态（顶部会标注演示数据）
  if (isAdminPreview()) {
    state.status = ADMIN_SESSION_STATUS.OFFLINE
    state.roles = [ADMIN_ROLE]
    state.permissions = [PERMISSION_ALL]
    state.profile = { ...DEFAULT_PROFILE }
    state.message = '预览模式：使用内置演示数据，未连接后端'
    state.verifiedAt = Date.now()
    return state.status
  }

  state.status = ADMIN_SESSION_STATUS.CHECKING

  inflight = (async () => {
    try {
      const profile = await getAdminProfile()
      applyProfile(profile)
      state.permissions = await resolvePermissions(profile)

      // 服务端已返回 200，但账号没有管理端入口权限：明确拒绝
      if (!hasPermission(state.permissions, ADMIN_PERMISSION)) {
        state.status = ADMIN_SESSION_STATUS.DENIED
        state.message = '当前账号没有管理端访问权限'
        state.verifiedAt = Date.now()
        return state.status
      }

      state.status = ADMIN_SESSION_STATUS.GRANTED
      state.message = ''
      state.verifiedAt = Date.now()
      return state.status
    } catch (error) {
      const kind = classifySessionError(error)
      state.roles = []
      state.permissions = []
      state.profile = { ...DEFAULT_PROFILE }

      if (kind === SESSION_ERROR.EXPIRED) {
        // Token 已失效：清掉，下一次导航由守卫送回登录页
        removeToken()
        state.status = ADMIN_SESSION_STATUS.OFFLINE
        state.message = '登录已过期，请重新登录'
      } else if (kind === SESSION_ERROR.DENIED) {
        state.status = ADMIN_SESSION_STATUS.DENIED
        state.message = error?.message || '当前账号没有管理端访问权限'
      } else {
        state.status = ADMIN_SESSION_STATUS.OFFLINE
        state.message = '后端服务未连接，管理端暂时以演示数据运行'
      }
      state.verifiedAt = Date.now()
      return state.status
    } finally {
      inflight = null
    }
  })()

  return inflight
}

/** 只清空会话状态，不动预览标记（预览标记由 exitAdminSession 负责） */
export function resetAdminSession() {
  state.status = ADMIN_SESSION_STATUS.UNKNOWN
  state.roles = []
  state.permissions = []
  state.profile = { ...DEFAULT_PROFILE }
  state.message = ''
  state.verifiedAt = 0
}

export function useAdminSession() {
  const status = computed(() => state.status)
  const roles = computed(() => state.roles)
  const permissions = computed(() => state.permissions)
  const profile = computed(() => state.profile)

  const isChecking = computed(() => state.status === ADMIN_SESSION_STATUS.CHECKING)
  const isDenied = computed(() => state.status === ADMIN_SESSION_STATUS.DENIED)
  const isOffline = computed(() => state.status === ADMIN_SESSION_STATUS.OFFLINE)
  const isVerified = computed(() => state.status === ADMIN_SESSION_STATUS.GRANTED)

  /** 守卫只拦「明确拒绝」：检查中 / 离线都先放行，由布局里的校验兜底 */
  const canEnterAdmin = computed(() => state.status !== ADMIN_SESSION_STATUS.DENIED)

  const notice = computed(() => (state.status === ADMIN_SESSION_STATUS.OFFLINE ? state.message : ''))

  return {
    state: readonly(state),
    status,
    roles,
    permissions,
    profile,
    isChecking,
    isDenied,
    isOffline,
    isVerified,
    canEnterAdmin,
    notice,
    verifyAdminSession,
    resetAdminSession,
  }
}
