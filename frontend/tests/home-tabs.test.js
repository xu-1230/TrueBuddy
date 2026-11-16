/**
 * 首页 Zone C 标签页双行为模型的配置用例（Node 内置 `node:test`）。
 *
 * 锁三件事：
 * ①六个 tab 的顺序与文案（US1）——综合 / 文档 / 批改 / 会议 / 知识库 / 工作流；
 * ②filter 型 tab 只过滤资产、必须没有 to（防止误配成可跳转）；
 * ③jump 型 tab 必须带落点路由（防止点击后静默无效）。
 *
 * 运行：cd frontend && node --test tests/home-tabs.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { HOME_TABS, KIND_META } from '../src/views/system/constants.js'

test('六个 tab 按序展示：综合 / 文档 / 批改 / 会议 / 知识库 / 工作流', () => {
  assert.deepEqual(
    HOME_TABS.map((tab) => tab.label),
    ['综合', '文档', '批改', '会议', '知识库', '工作流'],
  )
})

test('filter 型 tab 只过滤资产，不携带跳转落点', () => {
  const filterTabs = HOME_TABS.filter((tab) => tab.type === 'filter')

  assert.deepEqual(
    filterTabs.map((tab) => tab.key),
    ['all', 'knowledge', 'workflow'],
  )
  filterTabs.forEach((tab) => {
    assert.equal(tab.to, undefined, `过滤型 tab「${tab.label}」不该配置 to`)
  })
})

test('jump 型 tab 必须带落点路由，点击直接跳页不占过滤态', () => {
  const jumpTabs = HOME_TABS.filter((tab) => tab.type === 'jump')

  assert.deepEqual(
    jumpTabs.map((tab) => tab.key),
    ['doc', 'grading', 'meeting'],
  )
  jumpTabs.forEach((tab) => {
    assert.match(tab.to, /^\/index\//, `跳转型 tab「${tab.label}」的落点应是站内路由`)
  })
  assert.deepEqual(
    jumpTabs.map((tab) => tab.to),
    ['/index/doc-template', '/index/homework-grading', '/index/meeting'],
  )
})

test('chat kind 元数据已清退，会话不再出现在资源类型里', () => {
  assert.equal(KIND_META.chat, undefined)
  assert.deepEqual(Object.keys(KIND_META), ['knowledge', 'workflow', 'doc'])
})
