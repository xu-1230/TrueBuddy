/**
 * 工作流页的 URL 契约（URL 是页面状态的唯一真相源）。
 *
 *     /index/workflow            我的工作流 + 公有库 列表
 *     /index/workflow/new        新画布（未落库）
 *     /index/workflow/:id        某个工作流的画布（编辑 / 只读）
 *
 * 雪花 id 全程按字符串承载：19 位数字超出 JS 安全整数范围，转成 Number 会丢精度。
 * 旧的 `?workflow_id=` 兼容路径已废弃（ADR 0007），本模块不再读写该 query。
 */

/** 列表路由 name（与 user.routes.js 保持一致） */
export const WORKFLOW_LIST_ROUTE_NAME = 'workflow'
/** 新画布路由 name */
export const WORKFLOW_NEW_ROUTE_NAME = 'workflow-new'
/** 画布路由 name */
export const WORKFLOW_CANVAS_ROUTE_NAME = 'workflow-canvas'

/**
 * 规范化工作流 id：只接受纯数字字符串，返回去空白后的字符串；非法返回 null。
 * 不转 Number，保持雪花 id 精度。
 */
export function normalizeWorkflowId(value) {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return /^\d+$/.test(trimmed) ? trimmed : null
}

/** 画布 location；id 非法时回列表，避免带一个打不开的地址 */
export function workflowCanvasLocation(id) {
  const normalized = normalizeWorkflowId(id)
  if (!normalized) return { name: WORKFLOW_LIST_ROUTE_NAME }
  return { name: WORKFLOW_CANVAS_ROUTE_NAME, params: { id: normalized } }
}

/** 新画布 location */
export function workflowNewLocation() {
  return { name: WORKFLOW_NEW_ROUTE_NAME }
}

/** 列表 location（无 query） */
export function workflowListLocation() {
  return { name: WORKFLOW_LIST_ROUTE_NAME }
}
