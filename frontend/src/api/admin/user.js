import adminRequest from './index'

/** 用户分页列表：params = { page, size, keyword, status, role } */
export const listUsers = (params) => adminRequest({ url: '/user/page', method: 'get', params })

/** 新增用户：账号、邮箱唯一，roles 非空时绑定角色 */
export const createUser = (data) => adminRequest({ url: '/user/create', method: 'post', data })

/** 修改用户：账号不可改；roles 非空时全量重绑 */
export const updateUser = (data) => adminRequest({ url: '/user/update', method: 'put', data })

/** 删除用户（物理删除，级联清理角色绑定；不可删除当前登录账号） */
export const deleteUser = (id) => adminRequest({ url: `/user/${id}`, method: 'delete' })
