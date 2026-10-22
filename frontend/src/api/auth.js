import service from './index'

/** 账号密码登录 */
export const login = (username, password) =>
  service({ url: '/system/auth/login', method: 'post', data: { username, password } })

/**
 * 发送邮箱验证码
 * @param {string} email
 * @param {'register'|'reset'|'login'} scene
 */
export const sendCode = (email, scene) =>
  service({ url: '/system/auth/send-code', method: 'post', data: { email, scene } })

/** 邮箱验证码注册（成功后直接返回登录令牌） */
export const register = (data) =>
  service({ url: '/system/auth/register', method: 'post', data })

/** 邮箱验证码登录 */
export const loginByCode = (email, code) =>
  service({ url: '/system/auth/login/code', method: 'post', data: { email, code } })

/** 邮箱验证码重置密码 */
export const resetPassword = (data) =>
  service({ url: '/system/auth/reset-password', method: 'post', data })
