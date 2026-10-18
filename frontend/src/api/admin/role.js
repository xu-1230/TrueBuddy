import adminRequest from './index'

/** 角色列表（管理端角色数量很少，不做分页） */
export const listRoles = () => adminRequest({ url: '/role/list', method: 'get' })

export const createRole = (data) => adminRequest({ url: '/role/create', method: 'post', data })

export const updateRole = (data) => adminRequest({ url: '/role/update', method: 'put', data })

export const deleteRole = (id) => adminRequest({ url: `/role/${id}`, method: 'delete' })

/** 某角色已勾选的菜单 id */
export const getRoleMenuIds = (roleId) =>
  adminRequest({ url: '/role/menu-ids', method: 'get', params: { roleId } })

/** 保存角色的菜单权限 */
export const assignRoleMenus = (roleId, menuIds) =>
  adminRequest({ url: '/role/assign-menus', method: 'put', data: { roleId, menuIds } })
