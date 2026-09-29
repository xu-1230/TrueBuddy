/**
 * 执行结果 → 画布展示的整理逻辑（纯函数，供节点结果层使用）。
 *
 * 后端 `WorkflowProcessRespVO` 提供两份数据：
 * - `result.state`：扁平状态快照，键为 `{nodeId}.{outputName}`，另外还会把开始节点的输出播种为裸变量名；
 * - `nodeResults[]`：节点级执行结果（含节点状态与耗时）。
 *
 * 本模块把前者按 nodeId 归拢成「每个节点自己的输出」，把后者映射为画布上的节点状态。
 */

/** 单个节点最多展示的输出条数（其余折叠为 +N） */
export const MAX_OUTPUT_ROWS = 3

/** 单条输出的最大字符数（卡片内可滚动，这里只做上限保护） */
const MAX_TEXT_LENGTH = 200

const clamp = (text, maxLength) =>
  text.length > maxLength ? `${text.slice(0, maxLength)}…` : text

/** 值 → 单行摘要：数组折叠为「[N 项] 首项」，其余按字符串处理（仅供本模块内部使用） */
const summarizeValue = (value, maxLength = MAX_TEXT_LENGTH) => {
  if (value === null || value === undefined) return '—'
  if (Array.isArray(value)) {
    if (!value.length) return '[]'
    return clamp(`[${value.length} 项] ${summarizeValue(value[0], maxLength - 24)}`, maxLength)
  }
  if (typeof value === 'object') {
    try {
      return clamp(JSON.stringify(value), maxLength)
    } catch {
      return clamp(String(value), maxLength)
    }
  }
  return clamp(String(value), maxLength)
}

/**
 * 状态快照 → `{ [nodeId]: [{ name, text }] }`。
 * 裸变量名（不含 `.` 的键）与内部键（`_` 前缀）不归属任何节点，直接忽略。
 *
 * @param {Object} state `WorkflowRunRespVO.state`
 */
export const collectNodeOutputs = (state) => {
  const result = {}
  if (!state || typeof state !== 'object') return result
  for (const [key, value] of Object.entries(state)) {
    if (key.startsWith('_')) continue
    const index = key.indexOf('.')
    if (index <= 0 || index === key.length - 1) continue
    const nodeId = key.slice(0, index)
    const name = key.slice(index + 1)
    if (!result[nodeId]) result[nodeId] = []
    result[nodeId].push({ name, text: summarizeValue(value) })
  }
  return result
}

/** 迭代节点内部的起止节点不参与状态标注 */
const BOUNDARY_TYPES = new Set(['iteration-start', 'iteration-end'])

/** 后端节点状态（NodeResultVO.nodeStatus）→ 画布状态 */
const NODE_STATUS_TO_CANVAS = {
  2: 'running',
  3: 'success',
  4: 'failed',
}

/**
 * 后端节点级执行结果 → `{ [nodeId]: 'running' | 'success' | 'failed' | 'skipped' }`。
 *
 * 三个细节：
 * 1. `nodeResults` 已包含开始 / 结束节点，且 `nodeId` 就是画布节点 id，无需再做类型映射；
 * 2. 循环内部子节点不在 `nodeResults` 里（后端不做 batch 拆解），因此跟随容器状态；
 * 3. 未执行（`nodeStatus = 1`）的节点：整体已终态时标为「未执行」（虚线灰框），执行中不标色。
 *
 * @param {import('@/api/workflow').NodeResultVO[]} nodeResults 后端 `nodeResults`
 * @param {Array} nodes 画布节点
 * @param {{ finished?: boolean }} [options] `finished` 为 true 时把未执行节点标为 skipped
 */
export const buildNodeStatuses = (nodeResults, nodes = [], options = {}) => {
  const { finished = false } = options
  const byId = {}
  for (const item of Array.isArray(nodeResults) ? nodeResults : []) {
    const status = NODE_STATUS_TO_CANVAS[item?.nodeStatus]
    if (item?.nodeId && status) byId[item.nodeId] = status
  }

  const statuses = {}
  // 先标顶层节点，再让子节点跟随容器
  for (const node of nodes) {
    if (!node?.id || node.parentNode || BOUNDARY_TYPES.has(node.type)) continue
    if (byId[node.id]) statuses[node.id] = byId[node.id]
    else if (finished) statuses[node.id] = 'skipped'
  }

  for (const node of nodes) {
    if (!node?.id || !node.parentNode || BOUNDARY_TYPES.has(node.type)) continue
    const parentStatus = statuses[node.parentNode]
    if (parentStatus) statuses[node.id] = parentStatus
  }
  return statuses
}
