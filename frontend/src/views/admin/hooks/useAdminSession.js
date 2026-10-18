import { computed, reactive, readonly } from 'vue'
import { getUserType } from '@/utils/auth'
import { getAdminProfile, getAdminPermissions } from '@/api/admin/auth'
import { ADMIN_ROLE, PERMISSION_ALL } from '@/utils/access'

/**
 * 管理端会话（模块级单例）
 *
 * 身份判断只认登录响应里的 `userType`（0 普通用户 / 1 会员 / 2 管理员）：
 * 登录时由 `LoginForm` 写入本地，会话校验时直接读，不再请求身份接口。
 * 三种结果必须分开处理，不能都当失败：
 *   granted —— 身份为管理员，进入管理端；
 *   denied  —— 已登录但非管理员，跳 403；
 *   offline —— 没有登录态（或未连后端），允许以演示数据浏览，
 *              否则「只启前端不启后端」时管理端会整片打不开，无法联调 UI。
 *
 * 为什么不用 Pinia：这里只有一份会话数据，模块级 reactive 足够；
 * 用户端的 store 也都就近放在模块内（views/ppt/stores、views/meeting/stores）。
 */

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
  permissions: [PERMISSION_ALL],
  profile: { ...DEFAULT_PROFILE },
  message: '',
  verifiedAt: 0,
})

/** 并发调用（守卫 + 布局挂载）只发一次请求 */
let inflight = null

/** 资料与权限码是「锦上添花」，接口没就绪时保留默认值，不影响进入管理端 */
async function loadProfileAndPermissions() {
  const [profileResult, permissionResult] = await Promise.allSettled([
    getAdminProfile(),
    getAdminPermissions(),
  ])

  if (profileResult.status === 'fulfilled' && profileResult.value && typeof profileResult.value === 'object') {
    const profile = profileResult.value
    const nickname = profile.nickname || profile.name || DEFAULT_PROFILE.nickname
    state.profile = {
      id: profile.id ?? DEFAULT_PROFILE.id,
      username: profile.username || DEFAULT_PROFILE.username,
      nickname,
      initial: String(nickname).slice(0, 1) || DEFAULT_PROFILE.initial,
      roleLabel: profile.roleLabel || profile.roleName || DEFAULT_PROFILE.roleLabel,
    }
    if (Array.isArray(profile.roles) && profile.roles.length > 0) state.roles = profile.roles
  }

  if (permissionResult.status === 'fulfilled' && Array.isArray(permissionResult.value)) {
    state.permissions = permissionResult.value
  }
}

/**
 * 校验当前账号的管理员身份。
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
      const userType = getUserType()
      // 没有登录信息：按「未连后端」处理，保留以演示数据浏览的能力
      if (userType === null) {
        state.status = ADMIN_SESSION_STATUS.OFFLINE
        state.message = '未检测到登录信息，管理端暂时以演示数据运行'
        state.verifiedAt = Date.now()
        return state.status
      }
      // 已登录但不是管理员：明确拒绝，跳 403
      if (userType !== 2) {
        state.status = ADMIN_SESSION_STATUS.DENIED
        state.message = '当前账号不是管理员'
        state.verifiedAt = Date.now()
        return state.status
      }

      state.status = ADMIN_SESSION_STATUS.GRANTED
      state.message = ''
      state.verifiedAt = Date.now()
      state.roles = [ADMIN_ROLE]
      await loadProfileAndPermissions()
      if (!state.roles.includes(ADMIN_ROLE)) state.roles = [ADMIN_ROLE, ...state.roles]
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
  state.permissions = [PERMISSION_ALL]
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
