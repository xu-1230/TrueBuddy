import adminRequest from './index'

/** 当前管理员资料（昵称 / 头像 / 角色），接口未就绪时页面回落到本地默认值 */
export const getAdminProfile = () => adminRequest({ url: '/auth/profile', method: 'get' })

/** 当前管理员的权限码集合，供菜单与按钮级显隐使用 */
export const getAdminPermissions = () => adminRequest({ url: '/auth/permissions', method: 'get' })
