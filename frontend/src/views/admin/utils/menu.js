import { hasPermission } from '@/utils/access'

/**
 * 由路由表生成侧栏菜单
 *
 * 为什么不再维护一份 menuData：菜单项与路由是一对一关系，两处各写一份迟早会漂移
 * （改了路由忘了菜单、或菜单点进去 404）。这里把 admin.routes.js 当作唯一来源：
 *   - meta.menu === false 的路由不进菜单（登录页、系统运维页、附属页）；
 *   - meta.pinned === true 的路由放进固定区（概览），不属于任何功能模块；
 *   - meta.group 决定分组，分组顺序 = 路由声明顺序；
 *   - meta.permission 决定该账号是否可见。
 *
 * @param {Array} routes 路由数组（含 name === 'admin' 的父路由）
 * @param {{ permissions?: string[] }} options
 * @returns {{
 *   pinned: Array<{ name: string, path: string, title: string, icon: string, permission: string }>,
 *   groups: Array<{ label: string, items: Array<{ name: string, path: string, title: string, icon: string, permission: string }> }>
 * }}
 */
export function buildMenu(routes, { permissions = ['*'] } = {}) {
  const root = (routes || []).find((route) => route?.name === 'admin')
  if (!root || !Array.isArray(root.children)) return { pinned: [], groups: [] }

  const pinned = []
  const groups = []
  const groupIndex = new Map()

  for (const child of root.children) {
    const meta = child?.meta || {}
    if (meta.menu === false || !meta.title) continue
    if (!hasPermission(permissions, meta.permission)) continue

    const item = {
      name: child.name,
      path: `/admin/${String(child.path || '').replace(/^\/+/, '')}`,
      title: meta.title,
      icon: meta.icon || 'dot',
      permission: meta.permission || '',
    }

    if (meta.pinned) {
      pinned.push(item)
      continue
    }

    const label = meta.group || '其他'
    let group = groups[groupIndex.get(label)]
    if (!group) {
      group = { label, items: [] }
      groupIndex.set(label, groups.length)
      groups.push(group)
    }
    group.items.push(item)
  }

  return { pinned, groups }
}

/** 兼容旧调用：只要分组时用它 */
export function buildMenuGroups(routes, options) {
  return buildMenu(routes, options).groups
}
