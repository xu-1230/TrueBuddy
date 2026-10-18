/**
 * 管理端菜单生成与菜单树操作的行为用例
 *
 * 菜单不单独维护，由 admin.routes.js 的 meta 生成；这里锁住：
 *   - menu:false 的路由（登录页）不进菜单；
 *   - 分组按路由声明顺序；
 *   - 权限码不命中时该菜单项消失。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { register } from 'node:module'

// menu.js 内部用 @/ 别名引用 utils/access，node:test 需要先注册解析钩子
register(new URL('./support/alias-hooks.mjs', import.meta.url))

const { buildMenu, buildMenuGroups } = await import('../src/views/admin/utils/menu.js')
const { countMenuNodes, findMenuNode, insertMenuNode, removeMenuNode, updateMenuNode } = await import(
  '../src/views/admin/utils/menu-tree.js'
)

const routeFixture = [
  {
    path: '/admin',
    name: 'admin',
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        meta: { title: '概览', icon: 'dashboard', group: '概览', permission: 'admin:dashboard' },
      },
      {
        path: 'user',
        name: 'admin-user',
        meta: { title: '用户管理', icon: 'users', group: '账号与权限', permission: 'system:user:list' },
      },
      {
        path: 'role',
        name: 'admin-role',
        meta: { title: '角色管理', icon: 'shield', group: '账号与权限', permission: 'system:role:list' },
      },
      {
        path: 'system/log',
        name: 'admin-system-log',
        meta: { title: '操作日志', icon: 'log', group: '系统', permission: 'system:log:list' },
      },
    ],
  },
]

test('菜单按 meta.group 分组，组顺序 = 路由声明顺序', () => {
  const groups = buildMenuGroups(routeFixture, { permissions: ['*'] })

  assert.deepEqual(groups.map((group) => group.label), ['概览', '账号与权限', '系统'])
  assert.deepEqual(groups[1].items.map((item) => item.title), ['用户管理', '角色管理'])
})

test('菜单路径由 /admin 与子路由 path 拼成，name 原样传递', () => {
  const groups = buildMenuGroups(routeFixture, { permissions: ['*'] })
  const dashboard = groups[0].items[0]

  assert.equal(dashboard.path, '/admin/dashboard')
  assert.equal(dashboard.name, 'admin-dashboard')
  assert.equal(dashboard.icon, 'dashboard')
})

test('menu:false 的路由不进菜单', () => {
  const groups = buildMenuGroups(routeFixture, { permissions: ['*'] })
  const titles = groups.flatMap((group) => group.items.map((item) => item.title))

  assert.equal(titles.includes('管理端登录'), false)
})

test('权限码不命中的菜单项被过滤，整组为空则不出现', () => {
  const groups = buildMenuGroups(routeFixture, { permissions: ['system:user:list'] })

  assert.deepEqual(groups.map((group) => group.label), ['账号与权限'])
  assert.deepEqual(groups[0].items.map((item) => item.title), ['用户管理'])
})

test('超级权限 * 保留全部菜单', () => {
  const groups = buildMenuGroups(routeFixture, { permissions: ['*'] })

  assert.equal(groups.flatMap((group) => group.items).length, 4)
})

test('路由表里没有 admin 根节点时返回空菜单，不抛错', () => {
  assert.deepEqual(buildMenuGroups([], { permissions: ['*'] }), [])
  assert.deepEqual(buildMenuGroups([{ name: 'guides', children: [] }], {}), [])
})

test('pinned 路由进固定区，不参与功能模块分组', () => {
  const routes = [
    {
      name: 'admin',
      children: [
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          meta: { title: '概览', permission: 'admin:dashboard', pinned: true },
        },
        {
          path: 'user/list',
          name: 'admin-user-list',
          meta: { title: '用户列表', group: '用户与权限管理', permission: 'system:user:list' },
        },
      ],
    },
  ]

  const menu = buildMenu(routes, { permissions: ['*'] })

  assert.deepEqual(menu.pinned.map((item) => item.title), ['概览'])
  assert.deepEqual(menu.groups.map((group) => group.label), ['用户与权限管理'])
  assert.deepEqual(menu.groups[0].items.map((item) => item.title), ['用户列表'])
})

test('系统运维页 menu:false 时只在路由里存在，不进菜单', () => {
  const routes = [
    {
      name: 'admin',
      children: [
        {
          path: 'system/setting',
          name: 'admin-system-setting',
          meta: { title: '系统配置', menu: false, permission: 'system:config:list' },
        },
      ],
    },
  ]

  const menu = buildMenu(routes, { permissions: ['*'] })

  assert.deepEqual(menu.pinned, [])
  assert.deepEqual(menu.groups, [])
})

/* ---------------- 菜单树操作 ---------------- */

const treeFixture = () => [
  {
    id: 1,
    name: '账号与权限',
    type: 1,
    children: [
      { id: 11, name: '用户管理', type: 2, children: [{ id: 111, name: '新增用户', type: 3 }] },
      { id: 12, name: '角色管理', type: 2, children: [] },
    ],
  },
  { id: 2, name: '系统', type: 1, children: [] },
]

test('countMenuNodes 统计含按钮在内的全部节点', () => {
  assert.equal(countMenuNodes(treeFixture()), 5)
})

test('findMenuNode 能查到任意层级', () => {
  assert.equal(findMenuNode(treeFixture(), 111)?.name, '新增用户')
  assert.equal(findMenuNode(treeFixture(), 999), null)
})

test('insertMenuNode：parentId 为空挂根，否则挂到父节点下，且不改入参', () => {
  const tree = treeFixture()
  const withRoot = insertMenuNode(tree, null, { id: 3, name: '内容管理', type: 1 })
  const withChild = insertMenuNode(tree, 12, { id: 121, name: '分配权限', type: 3 })

  assert.equal(withRoot.length, 3)
  assert.equal(findMenuNode(withChild, 121)?.name, '分配权限')
  assert.equal(tree.length, 2, '入参树不能被原地修改')
  assert.equal(findMenuNode(tree, 121), null)
})

test('updateMenuNode 只改目标节点，子树保留', () => {
  const next = updateMenuNode(treeFixture(), 11, { name: '账号管理' })

  assert.equal(findMenuNode(next, 11)?.name, '账号管理')
  assert.equal(findMenuNode(next, 111)?.name, '新增用户')
})

test('removeMenuNode 连同子树一起删除', () => {
  const next = removeMenuNode(treeFixture(), 1)

  assert.equal(countMenuNodes(next), 1)
  assert.equal(findMenuNode(next, 111), null)
})
