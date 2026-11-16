/**
 * 模板生成页 URL 契约与侧边栏匹配的行为用例
 *
 * 锁住：
 *   - /index/doc-template/:id?file= 的解析与规范化；
 *   - 非法 id / file、有 file 无 id、重复 file 的边界；
 *   - 规范 URL 的拼接；
 *   - 侧栏前缀匹配不误命中相近路径。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DOC_TEMPLATE_PATH,
  docTemplateLocation,
  normalizePositiveId,
  parseDocTemplateRoute,
} from '../src/views/doctemplate/utils/route.js'
import { isPathActive } from '../src/utils/route.js'

test('基础页：无 id 无 file', () => {
  assert.deepEqual(parseDocTemplateRoute({}, {}), {
    generationId: null,
    fileId: null,
    hasInvalidId: false,
    hasInvalidFile: false,
    hasFileParam: false,
  })
})

test('详情页：合法 generationId + file', () => {
  assert.deepEqual(parseDocTemplateRoute({ id: '12' }, { file: '5' }), {
    generationId: '12',
    fileId: '5',
    hasInvalidId: false,
    hasInvalidFile: false,
    hasFileParam: true,
  })
})

test('详情页：只有 generationId 时 fileId 为空', () => {
  const state = parseDocTemplateRoute({ id: '12' }, {})
  assert.equal(state.generationId, '12')
  assert.equal(state.fileId, null)
  assert.equal(state.hasFileParam, false)
  assert.equal(state.hasInvalidFile, false)
})

test('非法 generationId 标记 hasInvalidId', () => {
  for (const id of ['abc', '0', '-1', '1.5', ' 12x', {}]) {
    const state = parseDocTemplateRoute({ id }, {})
    assert.equal(state.generationId, null, `id=${String(id)}`)
    assert.equal(state.hasInvalidId, true, `id=${String(id)}`)
  }
})

test('id 前后空白会被规范化', () => {
  assert.equal(parseDocTemplateRoute({ id: ' 12 ' }, {}).generationId, '12')
})

test('file 非法时标记 hasInvalidFile 并丢弃 fileId', () => {
  for (const file of ['abc', '0', '-3', '2.5']) {
    const state = parseDocTemplateRoute({ id: '12' }, { file })
    assert.equal(state.fileId, null, `file=${file}`)
    assert.equal(state.hasInvalidFile, true, `file=${file}`)
  }
})

test('file 空值视为未携带，不报非法', () => {
  const state = parseDocTemplateRoute({ id: '12' }, { file: '' })
  assert.equal(state.fileId, null)
  assert.equal(state.hasFileParam, false)
  assert.equal(state.hasInvalidFile, false)
})

test('有 file 无 id：标记 hasFileParam 供页面剔除畸形地址', () => {
  const state = parseDocTemplateRoute({}, { file: '5' })
  assert.equal(state.generationId, null)
  assert.equal(state.fileId, null)
  assert.equal(state.hasFileParam, true)
  assert.equal(state.hasInvalidFile, false)
})

test('重复 file 取第一个', () => {
  assert.equal(parseDocTemplateRoute({ id: '12' }, { file: ['5', '6'] }).fileId, '5')
})

test('生成规范 URL：基础页 / 详情页 / 文件页', () => {
  assert.deepEqual(docTemplateLocation(), {
    name: 'doc-template',
    params: {},
    query: {},
  })
  assert.deepEqual(docTemplateLocation(12), {
    name: 'doc-template',
    params: { id: '12' },
    query: {},
  })
  assert.deepEqual(docTemplateLocation('12', '5'), {
    name: 'doc-template',
    params: { id: '12' },
    query: { file: '5' },
  })
})

test('生成规范 URL：非法 id / file 被丢弃', () => {
  assert.deepEqual(docTemplateLocation('abc', '5'), {
    name: 'doc-template',
    params: {},
    query: {},
  })
  assert.deepEqual(docTemplateLocation('12', 'abc'), {
    name: 'doc-template',
    params: { id: '12' },
    query: {},
  })
})

test('normalizePositiveId 接受正整数数字与字符串', () => {
  assert.equal(normalizePositiveId(12), '12')
  assert.equal(normalizePositiveId('12'), '12')
  assert.equal(normalizePositiveId('0012'), '12')
  assert.equal(normalizePositiveId(0), null)
  assert.equal(normalizePositiveId(''), null)
  assert.equal(normalizePositiveId(null), null)
})

test('基础路径常量与路由约定一致', () => {
  assert.equal(DOC_TEMPLATE_PATH, '/index/doc-template')
})

test('侧栏匹配：精确与子路径命中', () => {
  assert.equal(isPathActive('/index/doc-template', '/index/doc-template'), true)
  assert.equal(isPathActive('/index/doc-template/12', '/index/doc-template'), true)
  assert.equal(isPathActive('/index/ppt', '/index/ppt'), true)
})

test('侧栏匹配：前缀相近的兄弟路径不误命中', () => {
  assert.equal(isPathActive('/index/homework-grading', '/index/home'), false)
  assert.equal(isPathActive('/index/doc-template-x', '/index/doc-template'), false)
  assert.equal(isPathActive('/index/doc-template', '/index/doc-template/12'), false)
  assert.equal(isPathActive('', '/index/home'), false)
})
