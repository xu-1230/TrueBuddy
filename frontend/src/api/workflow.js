/**
 * 工作流接口层（workflow2 画布页专用）。
 *
 * 执行与人工确认恢复都以 `text/event-stream` 返回，axios 无法流式读取，
 * 因此这里只负责接口地址与请求体构造，真正的读流在 `@/utils/workflow-sse`。
 */
import service from './index'
import { collapseIterations } from '@/views/workflow2/utils/iteration'

/**
 * SSE 接口地址（由 `@/utils/workflow-sse` 用原生 `fetch` 读取）。
 *
 * 这里**带** context-path 前缀 `/api`：`fetch` 不经过 axios 实例，不会自动补 baseURL。
 */
export const WORKFLOW_API = {
  /** 执行：请求体为画布定义，响应 text/event-stream */
  run: '/api/workflow/run',
  /** 人工确认后继续：query 参数 run_id / content，响应 text/event-stream */
  resume: '/api/workflow/resume',
}

/**
 * 工作流定义的 REST 接口路径（走 axios 实例）。
 *
 * 这里**不带** `/api` 前缀：axios 实例的 `baseURL` 已经是 `/api`，
 * 若重复拼接会请求到 `/api/api/workflow/*`。
 */
export const WORKFLOW_REST_API = {
  /** 保存定义：id 空=新建、非空=更新；返回 String 类型的雪花 id */
  save: '/workflow/save',
  /** 定义详情（query 参数 `workflow_id`），含 schemaJson */
  detail: '/workflow/detail',
  /** 定义列表（不含 schemaJson，按更新时间倒序） */
  list: '/workflow/list',
}

/** 节点上允许提交给后端的字段（Vue Flow 注入的运行时字段一律剔除） */
const NODE_FIELDS = ['id', 'type', 'position']

/** 连线上允许提交给后端的字段（后端只认 source / target，id 必填） */
const EDGE_FIELDS = ['id', 'source', 'target']

/** 迭代体内部连线：写回后端时的字段（与后端 WorkflowEdge 的 @JsonAlias 一致） */
const INNER_EDGE_FIELDS = ['id', 'sourceNodeID', 'targetNodeID']

/** 画布运行态字段前缀：仅前端使用，不提交给后端 */
const RUNTIME_PREFIX = '__'

/** 取白名单字段，未定义的字段直接省略（保持请求体与定义规范一致） */
const pickFields = (source, fields) => {
  const target = {}
  fields.forEach((field) => {
    if (source?.[field] !== undefined) target[field] = source[field]
  })
  return target
}

/** 节点业务数据：剔除运行态字段（`__` 前缀），避免把展示用状态带回后端 */
const pickNodeData = (data) => {
  const target = {}
  Object.keys(data ?? {}).forEach((key) => {
    if (!key.startsWith(RUNTIME_PREFIX)) target[key] = data[key]
  })
  return target
}

/**
 * 把画布数据转成后端工作流定义（与 `workflow` 表 `schema_json` 同构）。
 *
 * 提交前先把迭代容器折叠回后端结构：挂在容器下的子节点（`parentNode`）写回所属迭代节点的 `blocks`，
 * 内部连线写回节点级 `edges`，顶层只保留没有 `parentNode` 的节点，避免子块被当成游离节点上传。
 *
 * @param {Array} nodes 画布节点（v-model 绑定的那一份）
 * @param {Array} edges 画布连线
 * @returns {{ nodes: Array, edges: Array }} 可直接作为 `POST /api/workflow/run` 的请求体
 */
export function buildRunDefinition(nodes = [], edges = []) {
  const collapsed = collapseIterations(nodes, edges)
  return {
    nodes: collapsed.nodes.map(toBackendNode),
    edges: collapsed.edges.map((edge) => pickFields(edge, EDGE_FIELDS)),
  }
}

/** 节点 → 后端定义：迭代体子节点与内部连线递归折叠，普通节点只取白名单字段 */
const toBackendNode = (node) => {
  const target = { ...pickFields(node, NODE_FIELDS), data: pickNodeData(node.data) }
  const blocks = Array.isArray(node.blocks) ? node.blocks : []
  if (blocks.length) target.blocks = blocks.map(toBackendNode)
  const innerEdges = Array.isArray(node.edges) ? node.edges : []
  if (innerEdges.length) target.edges = innerEdges.map((edge) => pickFields(edge, INNER_EDGE_FIELDS))
  return target
}

/**
 * 人工确认恢复的地址：`run_id` / `content` 走 query 参数（后端为 `@RequestParam`）。
 *
 * @param {string} runId 执行标识，取自 `run` 事件
 * @param {string} content 人工确认内容
 */
export function buildResumeUrl(runId, content) {
  const query = new URLSearchParams({ run_id: runId ?? '', content: content ?? '' })
  return `${WORKFLOW_API.resume}?${query.toString()}`
}

/**
 * 画布 → 后端定义 JSON 字符串（保存用）。
 *
 * 与「运行」共用同一份折叠口径（{@link buildRunDefinition}），因此库里存的定义与
 * 运行时提交的定义天然同构——打开时再反向展开成画布即可。
 *
 * @param {Array} nodes 画布节点
 * @param {Array} edges 画布连线
 * @returns {string} 可直接作为 `schemaJson` 提交的 JSON 字符串
 */
export function buildWorkflowSchema(nodes = [], edges = []) {
  return JSON.stringify(buildRunDefinition(nodes, edges))
}

/**
 * 保存工作流定义：`id` 为空表示新建，非空表示更新该记录。
 *
 * @param {{ id?: string, name: string, description?: string, schemaJson: string }} payload
 * @returns {Promise<string>} 工作流 id（雪花 ID 字符串）
 */
export function saveWorkflow(payload) {
  return service.post(WORKFLOW_REST_API.save, payload)
}

/**
 * 工作流详情（含画布定义 JSON 字符串）。
 *
 * @param {string} workflowId 工作流 id（雪花 ID 字符串）
 * @returns {Promise<object>} 详情：{ id, name, description, status, builtin, stepCount, schemaJson, updateTime }
 */
export function getWorkflowDetail(workflowId) {
  return service.get(WORKFLOW_REST_API.detail, { params: { workflow_id: workflowId } })
}

/**
 * 工作流列表（不含画布定义，按更新时间倒序）。
 *
 * @returns {Promise<Array>} 列表项：{ id, name, description, status, builtin, stepCount, updateTime }
 */
export function listWorkflows() {
  return service.get(WORKFLOW_REST_API.list)
}
