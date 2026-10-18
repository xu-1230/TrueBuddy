/**
 * 管理端访问判定的行为用例（Node 内置 node:test）
 *
 * 锁三件事：
 *   ① 未登录只跳登录，不跳 403（两者语义不同）；
 *   ② 「身份未知 / 离线」不能当成拒绝，否则后端没起时管理端整片打不开；
 *   ③ 只有 admin 角色才算管理员，会员 / 普通用户一律 403。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ADMIN_PERMISSION,
  buildLoginLocation,
  hasPermission,
  hasRole,
  isAdmin,
  isSafeRedirect,
  mergeRouteMeta,
  normalizeList,
  resolveAccess,
  resolveLoginDestination,
} from '../src/utils/access.js'

test('normalizeList 把数组 / 逗号串 / 空值收敛为字符串数组', () => {
  assert.deepEqual(normalizeList(['admin', ' member ']), ['admin', 'member'])
  assert.deepEqual(normalizeList('admin,member'), ['admin', 'member'])
  assert.deepEqual(normalizeList(null), [])
  assert.deepEqual(normalizeList(undefined), [])
})

test('hasRole：未声明所需角色即不限制', () => {
  assert.equal(hasRole([], []), true)
  assert.equal(hasRole([], null), true)
  assert.equal(hasRole(['admin'], ['admin']), true)
  assert.equal(hasRole(['member'], ['admin']), false)
  assert.equal(hasRole(['member', 'admin'], ['admin']), true)
})

test('isAdmin 只认 admin 角色编码，不认会员 / 普通用户', () => {
  assert.equal(isAdmin(['admin']), true)
  assert.equal(isAdmin('admin'), true)
  assert.equal(isAdmin(['member']), false)
  assert.equal(isAdmin(null), false)
})

test('hasPermission：精确命中、* 与模块通配', () => {
  assert.equal(hasPermission(['system:user:list'], 'system:user:list'), true)
  assert.equal(hasPermission(['system:user:list'], 'system:user:update'), false)
  assert.equal(hasPermission(['*'], 'anything:at:all'), true)
  assert.equal(hasPermission(['system:*'], 'system:user:list'), true)
  assert.equal(hasPermission(['system:*'], 'kb:document:list'), false)
  assert.equal(hasPermission([], ''), true, '没有要求权限码时一律放行')
})

test('mergeRouteMeta：子路由覆盖父路由的同名键', () => {
  const meta = mergeRouteMeta([
    { meta: { requiresAuth: true, requiresAdmin: true } },
    { meta: { title: '用户管理' } },
  ])
  assert.deepEqual(meta, { requiresAuth: true, requiresAdmin: true, title: '用户管理' })
})

test('未登录访问受保护页 → login', () => {
  assert.equal(resolveAccess({ requiresAuth: true }, { hasToken: false }), 'login')
})

test('登录了但没有管理员身份 → forbidden', () => {
  assert.equal(
    resolveAccess({ requiresAuth: true, requiresAdmin: true }, { hasToken: true, roleStatus: 'denied' }),
    'forbidden',
  )
})

test('身份未知 / 离线先放行，由布局里的校验兜底', () => {
  assert.equal(
    resolveAccess({ requiresAuth: true, requiresAdmin: true }, { hasToken: true, roleStatus: 'unknown' }),
    'ok',
  )
  assert.equal(
    resolveAccess({ requiresAuth: true, requiresAdmin: true }, { hasToken: true, roleStatus: 'offline' }),
    'ok',
  )
})

test('已确认身份：管理端入口只认 admin:dashboard 权限', () => {
  assert.equal(
    resolveAccess(
      { requiresAuth: true, requiresAdmin: true },
      { hasToken: true, roleStatus: 'granted', roles: ['member'], permissions: ['system:user:list'] },
    ),
    'forbidden',
  )
  assert.equal(
    resolveAccess(
      { requiresAuth: true, requiresAdmin: true },
      { hasToken: true, roleStatus: 'granted', roles: ['admin'], permissions: [ADMIN_PERMISSION] },
    ),
    'ok',
  )
})

test('审核员拥有 admin:dashboard 即可进入管理端（只读）', () => {
  assert.equal(
    resolveAccess(
      { requiresAuth: true, requiresAdmin: true },
      {
        hasToken: true,
        roleStatus: 'granted',
        roles: ['auditor'],
        permissions: [ADMIN_PERMISSION, 'system:user:list', 'system:log:list'],
      },
    ),
    'ok',
  )
})

test('公开页不因为没 Token 就被拦', () => {
  assert.equal(resolveAccess({}, { hasToken: false }), 'ok')
  assert.equal(resolveAccess({ requiresAuth: false }, { hasToken: false }), 'ok')
})

test('登录跳转只回跳站内绝对路径，挡住开放重定向', () => {
  assert.deepEqual(buildLoginLocation('/admin/user'), {
    path: '/login',
    query: { redirect: '/admin/user' },
  })
  assert.deepEqual(buildLoginLocation('//evil.com'), { path: '/login' })
  assert.deepEqual(buildLoginLocation('https://evil.com'), { path: '/login' })
  assert.deepEqual(buildLoginLocation(undefined), { path: '/login' })
})

test('isSafeRedirect 只认站内绝对路径', () => {
  assert.equal(isSafeRedirect('/index/home'), true)
  assert.equal(isSafeRedirect('/admin/dashboard'), true)
  assert.equal(isSafeRedirect('//evil.com'), false)
  assert.equal(isSafeRedirect('https://evil.com'), false)
  assert.equal(isSafeRedirect('javascript:alert(1)'), false)
  assert.equal(isSafeRedirect(''), false)
  assert.equal(isSafeRedirect(null), false)
})

test('登录分流：管理员进管理端，普通用户进用户端', () => {
  assert.deepEqual(resolveLoginDestination({ permissions: [ADMIN_PERMISSION] }), {
    path: '/admin/dashboard',
  })
  assert.deepEqual(resolveLoginDestination({ permissions: [] }), { path: '/index' })
  assert.deepEqual(resolveLoginDestination({ permissions: ['system:user:list'] }), { path: '/index' })
})

test('登录分流：安全 redirect 优先，管理员也可回到用户端目标', () => {
  assert.deepEqual(
    resolveLoginDestination({ permissions: [ADMIN_PERMISSION], redirect: '/admin/user/list' }),
    { path: '/admin/user/list' },
  )
  assert.deepEqual(
    resolveLoginDestination({ permissions: [ADMIN_PERMISSION], redirect: '/index/workflow' }),
    { path: '/index/workflow' },
  )
  assert.deepEqual(
    resolveLoginDestination({ permissions: [], redirect: '/index/homework-grading' }),
    { path: '/index/homework-grading' },
  )
})

test('登录分流：普通用户回跳管理端地址进 403，非法 redirect 被丢弃', () => {
  assert.deepEqual(
    resolveLoginDestination({ permissions: [], redirect: '/admin/user/list' }),
    { name: 'forbidden', query: { from: '/admin/user/list' } },
  )
  assert.deepEqual(resolveLoginDestination({ permissions: [], redirect: '//evil.com' }), {
    path: '/index',
  })
  assert.deepEqual(
    resolveLoginDestination({ permissions: [ADMIN_PERMISSION], redirect: 'https://evil.com' }),
    { path: '/admin/dashboard' },
  )
})
