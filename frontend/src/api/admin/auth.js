import adminRequest from './index'

/** 当前管理端账号资料：昵称 / 头像 / 角色 / 权限（要求 admin:dashboard 权限） */
export const getAdminProfile = () => adminRequest({ url: '/auth/profile', method: 'get' })

/** 当前管理端账号的权限码集合，供菜单与按钮级显隐使用 */
export const getAdminPermissions = () => adminRequest({ url: '/auth/permissions', method: 'get' })
