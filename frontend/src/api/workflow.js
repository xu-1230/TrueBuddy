import service from './index'

/**
 * 工作流编排 API 接入层
 *
 * 对接后端工作流模块的 Coze 风格契约（Controller 路径待确认，见 `WORKFLOW_API`）：
 *     GET  /workflow/detail?id={workflowId}   → CommonResult<WorkflowDetailVO>
 *     POST /workflow/save                     → CommonResult<Boolean>
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 *
 * 现状：页面默认读取内置 mock（`views/workflow/mock/workflowDetail.json`，
 * 与后端返回体同构）；后端启动后把 `WorkflowView.vue` 的 `loadDocument()`
 * 换成 `getWorkflowDetail(workflowId)` 即可，`saveDocument()` 换成 `saveWorkflow()`。
 */

/** 接口路径：与后端联调时改这里即可 */
export const WORKFLOW_API = {
  /** 工作流详情（返回 schema_json 字符串） */
  detail: '/workflow/detail',
  /** 保存工作流（请求体与详情返回同构） */
  save: '/workflow/save',
}

// ========== 前后端共享 VO 契约 ==========

/**
 * @typedef {Object} WorkflowVO
 * @property {string} workflow_id     工作流 id
 * @property {string} name            工作流名称
 * @property {string} desc            工作流说明
 * @property {string} schema_json     工作流结构（**JSON 字符串**，需 JSON.parse 后再用）
 * @property {number} schema_type     schema 版本类型（当前为 1）
 * @property {number} type            工作流类型（0 = 低代码工作流）
 * @property {number} status          状态（1 = 已发布）
 * @property {string} space_id        所属空间 id
 * @property {number} create_time     创建时间（秒级时间戳）
 * @property {number} update_time     更新时间（秒级时间戳）
 */

/**
 * @typedef {Object} WorkflowDetailVO
 * @property {WorkflowVO} workflow    工作流主体
 * @property {string} workflow_version 工作流版本号，如 v0.0.1
 */

/**
 * @typedef {Object} WorkflowSaveDTO
 * @property {WorkflowVO} workflow    工作流主体（`schema_json` 为序列化后的字符串）
 * @property {string} workflow_version 工作流版本号
 */

// ========== 接口定义 ==========

/**
 * 查询工作流详情
 * GET /workflow/detail?id={workflowId}
 * @param {string} workflowId 工作流 id
 * @returns {Promise<WorkflowDetailVO>}
 */
export function getWorkflowDetail(workflowId) {
  return service({ url: WORKFLOW_API.detail, method: 'get', params: { id: workflowId } })
}

/**
 * 保存工作流（画布编辑结果序列化回 schema_json）
 * POST /workflow/save
 * @param {WorkflowSaveDTO} payload
 * @returns {Promise<boolean>}
 */
export function saveWorkflow(payload) {
  return service({ url: WORKFLOW_API.save, method: 'post', data: payload })
}
