import adminRequest from './index'

/** 用户分页列表：params = { page, size, keyword, status, role } */
export const listUsers = (params) => adminRequest({ url: '/user/page', method: 'get', params })

/** 新增用户 */
export const createUser = (data) => adminRequest({ url: '/user/create', method: 'post', data })

/** 修改用户 */
export const updateUser = (data) => adminRequest({ url: '/user/update', method: 'put', data })

/** 启用 / 禁用 */
export const updateUserStatus = (id, status) =>
  adminRequest({ url: '/user/status', method: 'put', data: { id, status } })

/** 重置密码：后端生成或校验新密码，前端不落地明文 */
export const resetUserPassword = (id) =>
  adminRequest({ url: '/user/reset-password', method: 'put', data: { id } })

/** 删除用户（软删） */
export const deleteUser = (id) => adminRequest({ url: `/user/${id}`, method: 'delete' })
