/**
 * 管理端会话错误分类用例
 *
 * 401 与 403 的处置完全不同：前者要清 Token 重新登录，后者应直接 403；
 * 网络错误必须保留“演示数据可浏览”的能力。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { SESSION_ERROR, classifySessionError } from '../src/views/admin/utils/session-state.js'

test('401 → expired（登录态失效）', () => {
  assert.equal(classifySessionError({ response: { status: 401 } }), SESSION_ERROR.EXPIRED)
})

test('403 → denied（无管理端权限）', () => {
  assert.equal(classifySessionError({ response: { status: 403 } }), SESSION_ERROR.DENIED)
})

test('响应拦截器抛出的业务错误码 → denied', () => {
  assert.equal(classifySessionError({ code: 1002000000, message: '用户不存在' }), SESSION_ERROR.DENIED)
})

test('网络错误 → offline（允许演示数据）', () => {
  assert.equal(classifySessionError({ code: 'ERR_NETWORK', message: 'Network Error' }), SESSION_ERROR.OFFLINE)
  assert.equal(classifySessionError(new Error('timeout')), SESSION_ERROR.OFFLINE)
})
