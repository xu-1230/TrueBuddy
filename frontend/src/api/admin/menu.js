import adminRequest from './index'

/** 菜单 / 权限树（system_menu） */
export const getMenuTree = () => adminRequest({ url: '/menu/tree', method: 'get' })

export const createMenu = (data) => adminRequest({ url: '/menu/create', method: 'post', data })

export const updateMenu = (data) => adminRequest({ url: '/menu/update', method: 'put', data })

export const deleteMenu = (id) => adminRequest({ url: `/menu/${id}`, method: 'delete' })
