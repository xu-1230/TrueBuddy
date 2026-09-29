import service from './index'

/**
 * 工作流 API 接入层
 *
 * 后端模块：`tb-module-workflow`（Spring AI Alibaba Graph；大模型与知识库检索仍为模拟实现，执行会话存内存）。
 *
 * 1）执行（**当前可用**，见 `views/homework/HomeworkWorkflowView.vue` 实测页与 `views/workflow/` 画布页）：
 *     POST /workflow/run                      → text/event-stream  提交执行（SSE 实时推送）
 *     POST /workflow/run（带 executeId）      → text/event-stream  提交确认清单，从人工确认断点恢复
 *     GET  /workflow/get_process?workflow_id= → CommonResult<WorkflowProcessRespVO> 轮询兜底（建议每 2s）
 *   SSE 不用 axios（会被 CommonResult 拦截器解包），由 {@link openEventStream}（`utils/workflow-sse`）读取；
 *   断线 / 刷新后改用 {@link pollWorkflowProcess}（`utils/workflow-poll`）续上进度。
 *
 * ### SSE 事件契约（data 为 JSON，即业务数据本身，不经 CommonResult 包装）
 *
 * | 事件      | 时机                     | data 关键字段                                                                 |
 * | --------- | ------------------------ | ----------------------------------------------------------------------------- |
 * | `created` | 会话建立、任务已提交     | `executeId`、`workflowId`、`nodeResults`（全部「未执行」）                     |
 * | `message` | 增量过程事件（见下）     | `type`：`node_started` / `node_finished` / `record` / `requires_action`        |
 * | `completed` | 整体执行完成           | 与 `WorkflowProcessRespVO` 同构（含 `result`、`exeHistoryStatus=2`）           |
 * | `error`   | 校验失败 / 执行异常      | `code`、`message`（其后**必定**补发 `done`）                                   |
 * | `done`    | 流终止（必发，最后一条） | `{}`；恢复请求的应答为 `{ accepted: true }`                                    |
 *
 * `message` 的 `type` 与附加字段：
 * - `node_started` / `node_finished`：`nodeId`、`nodeResult`（该节点最新状态 / 耗时 / 输入输出）；
 * - `record`：`nodeId`、`index`、`total`、`record`（循环内每完成一份批改即回传一份）；
 * - `requires_action`：`nodeId`、`nodeName`、`plan`、`defaultPlan`（**等待用户提交确认清单**，
 *   此时连接保持打开，提交后同一条流继续推后续事件）。
 *
 * 2）画布持久化（**当前可用**，落库到 workflow 表，画布页 `views/workflow/` 已接线）：
 *     GET  /workflow/detail?id={workflowId}   → CommonResult<WorkflowDetailVO>  含 schema_json
 *     POST /workflow/save                     → CommonResult<String>            工作流 id（新建 / 更新）
 *     GET  /workflow/list                     → CommonResult<WorkflowVO[]>       不含画布结构
 *
 * 说明：保存的是「画布渲染格式」（type 为 input/llm/human/iteration，含 position / parentNode /
 * data.backendInputs），与执行用的后端格式不同；保存即覆盖，不做版本管理。
 *
 * 画布 / 状态查询走 axios，其响应拦截器已自动解包 CommonResult.data。
 */

import { openEventStream } from '@/utils/workflow-sse'

/** 接口路径：与后端联调时改这里即可 */
export const WORKFLOW_API = {
  /** 提交执行 / 恢复人工确认（SSE 事件流，双语义） */
  run: '/workflow/run',
  /** 查询执行状态（每 2s 轮询兜底） */
  getProcess: '/workflow/get_process',
  /** 工作流详情（含 schema_json 字符串） */
  detail: '/workflow/detail',
  /** 保存工作流（新建时返回工作流 id） */
  save: '/workflow/save',
  /** 工作流列表（不含画布结构，按更新时间倒序） */
  list: '/workflow/list',
}

// ========== 状态码（与后端约定一致，前端按数字判态） ==========

/** 执行状态：exeHistoryStatus */
export const WORKFLOW_EXE_STATUS = {
  /** 执行中（继续等待事件 / 继续轮询） */
  RUNNING: 1,
  /** 执行成功（终态，结束） */
  FINISHED: 2,
  /** 执行失败（终态，结束） */
  FAILED: 3,
  /** 等待用户输入：人工确认节点已暂停（**非终态**，需提交确认清单后继续） */
  WAITING: 4,
}

/** SSE 事件名（与后端 `WorkflowStreamRegistry` 常量一一对应） */
export const WORKFLOW_STREAM_EVENT = {
  /** 会话已建立（任务已提交） */
  CREATED: 'created',
  /** 增量过程事件（用 data.type 区分：node_started / node_finished / record / requires_action） */
  MESSAGE: 'message',
  /** 整体执行完成 */
  COMPLETED: 'completed',
  /** 校验失败 / 执行异常 */
  ERROR: 'error',
  /** 流终止（必发，最后一条） */
  DONE: 'done',
}

/** `message` 事件的子类型（data.type） */
export const WORKFLOW_MESSAGE_TYPE = {
  /** 节点开始执行 */
  NODE_STARTED: 'node_started',
  /** 节点执行完成 */
  NODE_FINISHED: 'node_finished',
  /** 单份批改完成（循环内逐条回传） */
  RECORD: 'record',
  /** 等待用户输入（人工确认节点已暂停） */
  REQUIRES_ACTION: 'requires_action',
}

/** 执行结果码：executeStatus */
export const WORKFLOW_EXECUTE_STATUS = {
  /** 正常 */
  NORMAL: 1,
  /** 异常 */
  ABNORMAL: 2,
}

/** 节点状态：nodeResults[].nodeStatus */
export const WORKFLOW_NODE_STATUS = {
  /** 未执行 */
  PENDING: 1,
  /** 执行中 */
  RUNNING: 2,
  /** 执行成功 */
  SUCCESS: 3,
  /** 执行失败 */
  FAILED: 4,
}

// ========== 前后端共享 VO 契约 ==========

/**
 * @typedef {Object} PlanItemVO
 * @property {string} filePath      作业文件相对路径
 * @property {string} fileName      作业文件名
 * @property {boolean} needGrading  是否需要批改（非文本类文件为 false）
 * @property {string} reason        判定理由
 */

/**
 * @typedef {Object} GradingDimensionsVO
 * @property {number} accuracy      准确性（0-100）
 * @property {number} completeness  完整性（0-100）
 * @property {number} expression    表达（0-100）
 */

/**
 * @typedef {Object} GradingRecordVO
 * @property {string} fileName                   作业文件名
 * @property {string} filePath                   作业文件相对路径
 * @property {number|null} totalScore            得分（满分由入参 fullScore 决定）
 * @property {string} grade                      等级：优秀 / 良好 / 及格 / 待改进
 * @property {GradingDimensionsVO} dimensions    三维度得分
 * @property {string} comment                    教师评语
 */

/**
 * @typedef {Object} WorkflowRunReqVO
 * @property {string} [schemaJson]       画布 schema（JSON 字符串）；新建执行时必填，为空时后端报「工作流定义为空，无法执行」
 * @property {string} [executeId]        执行会话 id；传值即视为「恢复等待态会话」（需配合 `confirmedPlan`）
 * @property {string[]} [confirmedPlan]  已确认的批改清单（作业文件路径列表）；恢复执行时必填且非空
 * @property {string} [workflowId]       本次执行所用画布对应的工作流定义 id（用于响应中的 workFlowId）
 * @property {Object} [inputs]           开始节点输入（按变量名覆盖）
 * @property {string[]} [fileTree]       文件夹文件树（相对路径列表，目录以 / 结尾）
 * @property {string[]} [knowledgeList]  知识库列表
 * @property {string} [aiDescription]    AI 批改说明
 * @property {number} [fullScore]        本次批改满分
 */

/**
 * 待确认清单（人工确认节点暂停时的交互数据）。
 *
 * 出现位置：SSE 的 `message` 事件（`type = requires_action`）与 `GET /workflow/get_process`
 * 的 `pendingConfirm`（`exeHistoryStatus = 4` 时）。
 *
 * @typedef {Object} WorkflowPendingConfirmVO
 * @property {string} nodeId          暂停节点 id（人工确认节点）
 * @property {string} nodeName        暂停节点标题
 * @property {PlanItemVO[]} plan      规划出的批改清单（全部条目，含无需批改项）
 * @property {string[]} defaultPlan   默认勾选清单（仅 needGrading 为 true 的文件路径）
 */

/**
 * SSE 事件（`utils/workflow-sse` 解析后回调的形状）。
 *
 * @typedef {Object} WorkflowStreamEvent
 * @property {'created'|'message'|'completed'|'error'|'done'} event 事件名，见 `WORKFLOW_STREAM_EVENT`
 * @property {Object} data 事件负载（已 JSON.parse）
 */

/**
 * `message` 事件负载（`type` 决定附加字段）。
 *
 * @typedef {Object} WorkflowMessageEvent
 * @property {'node_started'|'node_finished'|'record'|'requires_action'} type
 * @property {string} [nodeId]           节点 id
 * @property {NodeResultVO} [nodeResult] `node_started` / `node_finished` 时该节点最新状态
 * @property {number} [index]            `record` 时的序号（从 1 开始）
 * @property {number} [total]            `record` 时的总条目数
 * @property {GradingRecordVO} [record]  `record` 时该份批改结果
 * @property {string} [nodeName]         `requires_action` 时暂停节点标题
 * @property {PlanItemVO[]} [plan]       `requires_action` 时的批改清单
 * @property {string[]} [defaultPlan]    `requires_action` 时的默认勾选清单
 */

/**
 * @typedef {Object} WorkflowRunRespVO
 * @property {PlanItemVO[]} plan                 规划出的批改清单（全部条目）
 * @property {string[]} defaultPlan              默认批改清单（仅 needGrading 为 true 的路径）
 * @property {string[]} [confirmedPlan]          本次实际批改的清单（人工确认提交后写入）
 * @property {GradingRecordVO[]} records         逐份批改结果
 * @property {string} [content]                  结束节点渲染出的最终展示内容
 * @property {number} [fullScore]                本次批改满分
 * @property {string[]} nodeTrace                已执行节点顺序
 * @property {Object} state                      最终状态快照
 */

/**
 * @typedef {Object} NodeResultVO
 * @property {string} nodeId              节点 id
 * @property {string} nodeName            节点标题
 * @property {string} nodeType            节点类型：Start / LLM / Loop / HumanConfirm / End
 * @property {number} nodeStatus          节点状态：1=未执行，2=执行中，3=执行成功，4=执行失败
 * @property {string} errorInfo           失败原因（成功为空串）
 * @property {string} errorLevel          错误级别（预留）
 * @property {string} input               节点输入（JSON 字符串）
 * @property {string} output              节点输出（JSON 字符串）
 * @property {string} nodeExeCost         节点耗时（人类可读，如 0.000s / 2s / 1m32s）
 * @property {number} nodeExeCostMs       节点耗时（毫秒）
 * @property {boolean} needAsync          是否需要异步（预留）
 * @property {number} logVersion          日志版本
 */

/**
 * @typedef {Object} TokenAndCostVO
 * @property {string} inputTokens   输入 token 数（本项目固定 0 Tokens）
 * @property {string} outputTokens  输出 token 数
 * @property {string} totalTokens   总 token 数
 * @property {string} inputCost     输入费用
 * @property {string} outputCost    输出费用
 * @property {string} totalCost     总费用
 */

/**
 * @typedef {Object} WorkflowProcessRespVO
 * @property {string} executeId                      执行会话 id（来自 SSE 的 `created` 事件）
 * @property {string|null} workFlowId                画布对应的工作流定义 id（未关联时为 null）
 * @property {number} exeHistoryStatus               执行状态：1=执行中，2=执行成功，3=执行失败，4=等待用户输入（非终态）
 * @property {number} executeStatus                  执行结果码：1=正常，2=异常
 * @property {string} workflowExeCost                总耗时（人类可读）
 * @property {number} exeCostMs                      总耗时（毫秒）
 * @property {Array} nodeEvents                      事件列表（预留，固定空数组）
 * @property {NodeResultVO[]} nodeResults            节点级执行结果（按画布节点顺序）
 * @property {TokenAndCostVO} tokenAndCost           token 与费用
 * @property {string} projectId                      项目 id（固定 "0"）
 * @property {string} message                        提示信息 / 失败原因
 * @property {WorkflowRunRespVO|null} result         业务结果：执行中为 null，终态返回完整结果
 * @property {WorkflowPendingConfirmVO} [pendingConfirm] 待确认清单：仅 exeHistoryStatus=4 时有值
 */

/**
 * @typedef {Object} WorkflowVO
 * @property {string} workflow_id     工作流 id
 * @property {string} name            工作流名称
 * @property {string} desc            工作流说明
 * @property {string} schema_json     工作流结构（**JSON 字符串**，需 JSON.parse 后再用）
 * @property {number} status          状态（0 = 草稿，1 = 已发布）
 * @property {number} create_time     创建时间（秒级时间戳）
 * @property {number} update_time     更新时间（秒级时间戳）
 */

/**
 * @typedef {Object} WorkflowDetailVO
 * @property {WorkflowVO} workflow    工作流主体
 */

/**
 * @typedef {Object} WorkflowSaveReqVO
 * @property {string} [id]            工作流 id（空 = 新建）
 * @property {string} [name]          工作流名称（为空时后端兜底为「未命名工作流」）
 * @property {string} [desc]          工作流说明
 * @property {string} schema_json     画布结构（JSON 字符串）
 * @property {number} [status]        状态：0 = 草稿，1 = 已发布
 */

// ========== 接口定义 ==========

/**
 * 提交工作流执行：以 SSE 实时接收执行事件。
 * POST /workflow/run
 *
 * `schemaJson` 必填（为空 / 非法时后端也走事件流，先推 `error` 再推 `done`）；其余字段可缺省，
 * 缺省时后端使用模拟输入数据。
 *
 * 事件与负载见模块头注释的事件契约表。执行到人工确认节点时会收到 `message`
 * （`type = requires_action`），此时应弹出确认清单，用户提交后调 {@link resumeWorkflow}
 * ——提交后**同一条流**会继续推后续事件。
 *
 * @param {WorkflowRunReqVO} [payload]
 * @param {{
 *   onEvent?: (event: string, data: any) => void,
 *   onError?: (error: Error) => void,
 *   onDone?: (reason: 'done'|'closed'|'abort') => void,
 * }} [handlers]
 * @returns {{ abort: () => void, done: Promise<void> }} `abort()` 主动中止（页面卸载 / 重新执行）
 */
export function runWorkflowStream(payload = {}, handlers = {}) {
  return openEventStream(WORKFLOW_API.run, payload, handlers)
}

/**
 * 提交人工确认清单，从断点恢复执行。
 * POST /workflow/run（带 `executeId` + `confirmedPlan`）
 *
 * 恢复后的事件继续推回**原来那条流**（`runWorkflowStream` 的回调仍在接收），本次响应只作
 * 「已受理」应答，因此本函数在流结束时 resolve。
 *
 * @param {{executeId: string, confirmedPlan: string[]}} payload `executeId` 为等待态会话 id
 * @returns {Promise<boolean>} 已受理返回 true；「会话不在等待态」「确认清单为空」等业务错误会 reject
 */
export function resumeWorkflow(payload) {
  return new Promise((resolve, reject) => {
    openEventStream(WORKFLOW_API.run, payload, {
      onEvent: (event, data) => {
        if (event === WORKFLOW_STREAM_EVENT.ERROR) {
          const error = new Error(data?.message || '提交确认清单失败')
          if (data?.code !== undefined) {
            error.code = data.code
          }
          reject(error)
        }
      },
      onError: (error) => reject(error),
      onDone: () => resolve(true),
    })
  })
}

/**
 * 查询工作流执行状态（SSE 的轮询兜底；用 `utils/workflow-poll` 的 `pollWorkflowProcess` 驱动）。
 * GET /workflow/get_process?workflow_id={workflowId}
 *
 * 注意：**只有 2（成功）/ 3（失败）是终态**；4（等待用户输入）时继续轮询，
 * 并据 `pendingConfirm` 弹出确认清单。
 *
 * @param {string} workflowId 执行会话 id（SSE 的 `created` 事件下发）
 * @returns {Promise<WorkflowProcessRespVO>}
 */
export function getProcess(workflowId) {
  return service({
    url: WORKFLOW_API.getProcess,
    method: 'get',
    params: { workflow_id: workflowId },
  })
}

/**
 * 查询工作流详情（含画布结构）
 * GET /workflow/detail?id={workflowId}
 * @param {string} workflowId 工作流 id
 * @returns {Promise<WorkflowDetailVO>}
 */
export function getWorkflowDetail(workflowId) {
  return service({ url: WORKFLOW_API.detail, method: 'get', params: { id: workflowId } })
}

/**
 * 保存工作流：入参 id 为空时新建（返回新 id），非空时更新（返回原 id）。
 * 保存即覆盖当前记录，**不做版本管理**。
 * POST /workflow/save
 * @param {WorkflowSaveReqVO} payload
 * @returns {Promise<string>} 工作流 id
 */
export function saveWorkflow(payload) {
  return service({ url: WORKFLOW_API.save, method: 'post', data: payload })
}

/**
 * 工作流列表（不含画布结构，按更新时间倒序）
 * GET /workflow/list
 * @returns {Promise<WorkflowVO[]>}
 */
export function listWorkflows() {
  return service({ url: WORKFLOW_API.list, method: 'get' })
}
