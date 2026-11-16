/**
 * 首页信息流派生逻辑的行为用例（Node 内置 `node:test`）。
 *
 * 锁三条不能靠肉眼看出来的：
 * ①**卡片上的数字只能是真实字段的推导**——后端没有播放量、没有热度接口，
 *   规模计数缺失就传 null，不补 0、不编造数字；
 * ②**任一接口挂掉不能拖垮整页**——buildFeed 对 null 来源要照常出结果；
 * ③时间格式要能吃下 yudao 的 `yyyy-MM-dd HH:mm:ss`，`new Date()` 直接解它会返回 Invalid Date。
 *
 * 运行：cd frontend && node --test tests/home-feed.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildFeed,
  buildModuleStats,
  filterFeed,
  formatCount,
  formatRelative,
  parseTime,
  sortFeed,
} from '../src/views/system/utils/feed.js'

const NOW = new Date(2026, 9, 9, 12, 0, 0).getTime()
const DAY = 86400000

/** 把「N 天前」写成本地时间字符串，与后端 yudao 的序列化格式一致 */
const daysAgo = (n) => {
  const date = new Date(NOW - n * DAY)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

test('parseTime 吃下 yudao 的空格分隔格式，ISO 与非法值都不炸', () => {
  assert.equal(parseTime(daysAgo(0)), new Date(2026, 9, 9, 12, 0, 0).getTime())
  assert.equal(parseTime('2026-10-09T12:00:00'), new Date(2026, 9, 9, 12, 0, 0).getTime())
  assert.equal(parseTime(null), null)
  assert.equal(parseTime(''), null)
  assert.equal(parseTime('不是时间'), null)
})

test('三个来源合成一条流，按更新时间倒序', () => {
  const items = buildFeed(
    {
      spaces: [{ id: 1, name: '课件库', documentCount: 2, createTime: daysAgo(20) }],
      workflows: [{ id: 2, name: '批改流', stepCount: 6, updateTime: daysAgo(1) }],
      generations: [{ id: 3, title: '教案复刻', kb: '课件库', docCount: 4, status: '成功', time: daysAgo(2) }],
    },
  )

  assert.deepEqual(
    items.map((item) => item.key),
    ['workflow-2', 'doc-3', 'knowledge-1'],
  )
  assert.deepEqual(
    items.map((item) => item.kind),
    ['workflow', 'doc', 'knowledge'],
  )
})

test('会话来源已彻底移除：误传 conversations 也不产生任何卡片', () => {
  const items = buildFeed(
    {
      conversations: [{ conversationId: 4, title: '会话 A', workflowName: '批改流', createTime: daysAgo(5) }],
    },
  )

  assert.deepEqual(items, [], '会话卡片与 chat kind 已下线，不该有任何兜底残留')
  assert.ok(items.every((item) => item.kind !== 'chat'))
})

test('来源缺失时不编造数字：null 与 undefined 都出空流', () => {
  assert.deepEqual(buildFeed({}), [])
  assert.deepEqual(buildFeed({ spaces: null, workflows: undefined }), [])
  assert.deepEqual(buildFeed({ spaces: [{ id: 1, name: '空空间', createTime: daysAgo(1) }] })
    .map((item) => item.metric), [null], 'documentCount 缺失时不能补 0，0 会被读成「确实没有文档」')
})

test('生成历史把状态文案带成语义色档位', () => {
  const items = buildFeed(
    { generations: [
      { id: 1, title: '成功任务', status: '成功', docCount: 1, time: daysAgo(1) },
      { id: 2, title: '部分成功任务', status: '部分成功', docCount: 1, time: daysAgo(1) },
      { id: 3, title: '失败任务', status: '失败', docCount: 1, time: daysAgo(1) },
      { id: 4, title: '没状态', docCount: 1, time: daysAgo(1) },
    ] },
  )

  assert.deepEqual(
    items.map((item) => item.stateTone),
    ['success', 'warning', 'danger', 'neutral'],
    '四条时间相同，倒序后由稳定排序保持原顺序',
  )
})

test('过滤与推荐排序：规模优先，同规模按更新时间倒序', () => {
  const items = buildFeed(
    { workflows: [
      { id: 1, name: '老流程', updateTime: daysAgo(30) },
      { id: 2, name: '新流程', updateTime: daysAgo(1) },
    ], spaces: [{ id: 3, name: '老空间', documentCount: 9, createTime: daysAgo(30) }] },
  )

  assert.deepEqual(filterFeed(items, 'workflow').map((item) => item.id), [2, 1])
  assert.equal(filterFeed(items, 'all').length, 3)
  assert.deepEqual(
    sortFeed(items, 'recommended').map((item) => item.id),
    [3, 2, 1],
    '空间有 9 份文档排最前；无计数的工作流按更新时间先后',
  )
  assert.deepEqual(sortFeed(items, 'created').map((item) => item.id), [2, 3, 1], '1 与 3 同为 30 天前，稳定排序保留入流顺序')
})

test('能力目录卡：没有来源的模块不给统计', () => {
  const items = buildFeed(
    { workflows: [
      { id: 1, name: 'A', updateTime: daysAgo(1) },
      { id: 2, name: 'B', updateTime: daysAgo(9) },
      { id: 3, name: 'C', updateTime: daysAgo(9) },
      { id: 4, name: 'D', updateTime: daysAgo(9) },
    ] },
  )
  const stats = buildModuleStats(items)

  assert.deepEqual(stats.workflow, { count: 4 })
  assert.equal(stats.knowledge, undefined, '知识库一条都没有，不该出现卡片统计')
})

test('计数缩写与相对时间', () => {
  assert.equal(formatCount(999), '999')
  assert.equal(formatCount(12345), '1.2万')
  assert.equal(formatCount(20000), '2万')
  assert.equal(formatCount(150000), '15万')
  assert.equal(formatCount(null), '')

  assert.equal(formatRelative(parseTime(daysAgo(0)), NOW), '刚刚')
  assert.equal(formatRelative(parseTime(daysAgo(1)), NOW), '昨天')
  assert.equal(formatRelative(parseTime(daysAgo(10)), NOW), '10 天前')
  assert.equal(formatRelative(null, NOW), '时间未知')
})
