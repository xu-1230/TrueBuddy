/**
 * 首页信息流的归一化与派生逻辑（纯函数，无副作用、无请求，便于单测）。
 *
 * 后端没有任何统计 / 推荐 / 热度接口，因此卡片上的「上新」「热门」与计数
 * 全部由**客户端从真实资产字段推导**：
 *   - 上新：`createTime` 距今 ≤ NEW_BADGE_DAYS 天
 *   - 热门：该类资产的规模字段（文档数 / 节点数 / 产出数）达到 HOT_THRESHOLDS
 * 推导不出来的（作业批改、PPT、模拟会议没有列表接口）就**不显示**，不编造数字。
 *
 * 本文件刻意不 import `constants.js`（它拖着整个图标库），保持可被 Node 直接跑单测。
 */

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** 「上新」的时间窗口 */
export const NEW_BADGE_DAYS = 7

/** 各类资产判定「热门」的规模阈值 */
export const HOT_THRESHOLDS = {
  knowledge: 5,
  workflow: 4,
  doc: 3,
  chat: 3,
}

/** 生成历史的状态文案 → 语义色档位（后端 `HistoryVO.status` 给的就是中文文案） */
export const DOC_STATE_TONE = {
  成功: 'success',
  部分成功: 'warning',
  失败: 'danger',
}

/**
 * 宽容的时间解析。
 *
 * 后端同时存在 `yyyy-MM-dd HH:mm:ss`（yudao 默认序列化）与 ISO 两种形态，
 * 而 `new Date('2026-10-09 12:00')` 在部分内核返回 Invalid Date，
 * 所以先按本地时间手工拆字段，拆不出来才回落到 `Date.parse`。
 *
 * @param {string|number|Date|null|undefined} value
 * @returns {number|null} 毫秒时间戳，无法解析时为 null
 */
export function parseTime(value) {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value.getTime()

  const matched = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/.exec(String(value).trim())
  if (matched) {
    const [, year, month, day, hour, minute, second] = matched
    return new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hour),
      Number(minute),
      Number(second || 0),
    ).getTime()
  }

  const fallback = Date.parse(value)
  return Number.isNaN(fallback) ? null : fallback
}

/**
 * 是否处于「上新」窗口内。时间为空或在未来时不算上新。
 * @param {number|null} timestamp
 * @param {number} now
 * @param {number} [days]
 */
export function isRecent(timestamp, now, days = NEW_BADGE_DAYS) {
  if (!timestamp || timestamp > now) return false
  return now - timestamp <= days * DAY
}

/**
 * 1.2 万这类计数缩写，用于卡片右下角的规模筹码。
 * @param {number|null|undefined} value
 * @returns {string}
 */
export function formatCount(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return ''
  if (value < 10000) return String(value)
  const tenThousand = value / 10000
  return `${tenThousand >= 10 ? Math.round(tenThousand) : tenThousand.toFixed(1).replace(/\.0$/, '')}万`
}

/**
 * 相对时间文案：刚刚 / N 分钟前 / N 小时前 / 昨天 / N 天前 / M月D日。
 * @param {number|null} timestamp
 * @param {number} now
 * @returns {string}
 */
export function formatRelative(timestamp, now) {
  if (!timestamp) return '时间未知'
  const delta = now - timestamp
  if (delta < 0) return '时间未知'
  if (delta < MINUTE) return '刚刚'
  if (delta < HOUR) return `${Math.floor(delta / MINUTE)} 分钟前`
  if (delta < DAY) return `${Math.floor(delta / HOUR)} 小时前`
  if (delta < 2 * DAY) return '昨天'
  if (delta < 30 * DAY) return `${Math.floor(delta / DAY)} 天前`

  const date = new Date(timestamp)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const asArray = (value) => (Array.isArray(value) ? value : [])

const text = (...values) => {
  const hit = values.find((item) => typeof item === 'string' && item.trim())
  return hit ? hit.trim() : ''
}

/** 组装成统一卡片模型；badge 优先级为「上新 > 热门」，与参考图一致（一卡一筹码） */
function toItem(partial, now) {
  const { kind, metric, createdAt, updatedAt } = partial
  const threshold = HOT_THRESHOLDS[kind]
  let badge = null
  if (isRecent(createdAt ?? updatedAt, now)) badge = 'new'
  else if (typeof threshold === 'number' && typeof metric === 'number' && metric >= threshold) badge = 'hot'

  return {
    badge,
    createdAt,
    updatedAt,
    isNew: badge === 'new',
    isHot: badge === 'hot',
    ...partial,
  }
}

/**
 * 把四个接口的返回合并为一条统一信息流。
 *
 * 任一来源请求失败时传 null 即可，其余来源照常渲染。
 *
 * @param {{
 *   spaces?: Array, workflows?: Array, generations?: Array, conversations?: Array
 * }} payload
 * @param {{ now?: number }} [options]
 * @returns {Array<object>} 按 `updatedAt` 倒序的统一卡片模型
 */
export function buildFeed(payload = {}, options = {}) {
  const { spaces, workflows, generations, conversations } = payload
  const now = options.now ?? Date.now()

  const items = [
    ...asArray(spaces).map((space) =>
      toItem(
        {
          key: `knowledge-${space?.id}`,
          id: space?.id,
          kind: 'knowledge',
          name: text(space?.name) || '未命名空间',
          description: text(space?.description) || '暂无空间描述',
          metric: typeof space?.documentCount === 'number' ? space.documentCount : null,
          metricLabel: '文档',
          to: '/index/knowledge-base',
          createdAt: parseTime(space?.createTime),
          updatedAt: parseTime(space?.createTime),
        },
        now,
      ),
    ),
    ...asArray(workflows).map((flow) =>
      toItem(
        {
          key: `workflow-${flow?.id}`,
          id: flow?.id,
          kind: 'workflow',
          name: text(flow?.name) || '未命名工作流',
          description: text(flow?.description) || '暂无描述',
          metric: typeof flow?.stepCount === 'number' ? flow.stepCount : null,
          metricLabel: '节点',
          to: '/index/workflow',
          createdAt: parseTime(flow?.updateTime),
          updatedAt: parseTime(flow?.updateTime),
        },
        now,
      ),
    ),
    ...asArray(generations).map((record) =>
      toItem(
        {
          key: `doc-${record?.id}`,
          id: record?.id,
          kind: 'doc',
          name: text(record?.title) || '未命名生成任务',
          description: text(record?.kb) ? `知识库：${record.kb}` : '暂无来源知识库',
          metric: typeof record?.docCount === 'number' ? record.docCount : null,
          metricLabel: '产出',
          to: '/index/doc-template',
          state: text(record?.status),
          stateTone: DOC_STATE_TONE[text(record?.status)] || 'neutral',
          createdAt: parseTime(record?.time),
          updatedAt: parseTime(record?.time),
        },
        now,
      ),
    ),
    ...asArray(conversations).map((chat) =>
      toItem(
        {
          key: `chat-${chat?.conversationId ?? chat?.id}`,
          id: chat?.conversationId ?? chat?.id,
          kind: 'chat',
          name: text(chat?.title, chat?.workflowName) || '未命名会话',
          description: text(chat?.workflowName) ? `工作流：${chat.workflowName}` : '暂无绑定工作流',
          metric: null,
          metricLabel: '',
          to: '/index/workflow',
          createdAt: parseTime(chat?.createTime),
          updatedAt: parseTime(chat?.updateTime ?? chat?.createTime),
        },
        now,
      ),
    ),
  ]

  return items.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0))
}

/**
 * 按标签页过滤。
 * @param {Array<object>} items
 * @param {string} kind `all` 或具体类型
 */
export function filterFeed(items, kind) {
  if (!kind || kind === 'all') return items
  return items.filter((item) => item.kind === kind)
}

const timeDesc = (field) => (a, b) => (b[field] ?? 0) - (a[field] ?? 0)

/**
 * 排序。「推荐」= 有筹码的（上新 / 热门）排前面，其余按更新时间倒序 ——
 * 这是把「平台想推的」和「最近动过的」合成一个顺序，而不是真的推荐算法。
 * @param {Array<object>} items
 * @param {string} sortKey
 */
export function sortFeed(items, sortKey) {
  const list = [...items]
  if (sortKey === 'created') return list.sort(timeDesc('createdAt'))
  if (sortKey === 'updated') return list.sort(timeDesc('updatedAt'))
  return list.sort((a, b) => {
    const weightA = a.badge ? 1 : 0
    const weightB = b.badge ? 1 : 0
    if (weightA !== weightB) return weightB - weightA
    return (b.updatedAt ?? 0) - (a.updatedAt ?? 0)
  })
}

/**
 * 汇总 Zone B 能力目录卡的统计量。
 * @param {Array<object>} items `buildFeed` 的结果
 * @returns {Record<string, { count: number, recent: boolean }>} 以资源类型为键
 */
export function buildModuleStats(items) {
  const stats = {}
  asArray(items).forEach((item) => {
    if (!item || !item.kind) return
    const bucket = stats[item.kind] || { count: 0, recent: false }
    bucket.count += 1
    if (item.isNew) bucket.recent = true
    stats[item.kind] = bucket
  })
  return stats
}

/**
 * 能力目录卡的筹码：该模块下有 ≤7 天新建的资产即「上新」，否则按规模判「热门」。
 * @param {{ count: number, recent: boolean }|undefined} stat
 * @param {number} threshold
 * @returns {'new'|'hot'|null}
 */
export function moduleBadge(stat, threshold) {
  if (!stat) return null
  if (stat.recent) return 'new'
  if (typeof threshold === 'number' && stat.count >= threshold) return 'hot'
  return null
}
