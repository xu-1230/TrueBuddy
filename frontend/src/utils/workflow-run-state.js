import { WORKFLOW_EXE_STATUS, WORKFLOW_MESSAGE_TYPE } from '@/api/workflow'

/**
 * 工作流执行状态的**增量拼装**（纯函数、无副作用，供画布页与作业批改实测页共用）。
 *
 * 后端以 SSE 推送事件，前端把事件累积成与 `WorkflowProcessRespVO` **同构**的快照：
 * 这样模板渲染、诊断弹窗、终态落地都只需面对一种数据结构，也便于 SSE 断开后由轮询接口
 * 直接覆盖（`get_process` 返回的正是同一种结构）。
 *
 * 本模块与 `workflow-sse.js`（读流）分工明确：读流负责「把字节流切成事件」，本模块负责
 * 「把事件累积成状态」。
 */

/** 是否终态（成功 / 失败）；「等待用户输入」不是终态，需继续等事件或轮询 */
export const isTerminalStatus = (status) =>
  status === WORKFLOW_EXE_STATUS.FINISHED || status === WORKFLOW_EXE_STATUS.FAILED

/** `created` 事件负载 → 初始执行状态快照（节点均为「未执行」） */
export const buildCreatedProcess = (data) => ({
  executeId: data?.executeId || '',
  workFlowId: data?.workflowId ?? null,
  exeHistoryStatus: WORKFLOW_EXE_STATUS.RUNNING,
  executeStatus: 1,
  exeCostMs: 0,
  workflowExeCost: '0.000s',
  nodeResults: data?.nodeResults || [],
  message: '正在执行，进度实时推送',
  result: null,
})

/** 用最新节点结果替换快照中的对应节点，返回新快照（节点不在列表中时追加） */
export const withNodeResult = (process, nodeId, nodeResult) => {
  if (!nodeId || !nodeResult) return process
  const list = [...(process?.nodeResults || [])]
  const index = list.findIndex((item) => item?.nodeId === nodeId)
  if (index >= 0) list[index] = nodeResult
  else list.push(nodeResult)
  return { ...process, nodeResults: list }
}

/**
 * 处理一条 `message` 事件。
 *
 * @returns {{ process: Object, pendingConfirm: Object|null, liveRecord: Object|null }}
 *   `pendingConfirm` 仅在 `requires_action` 时有值；`liveRecord` 为 `{ index, total, record }`
 */
export const applyMessageEvent = (process, message) => {
  if (!message) return { process, pendingConfirm: null, liveRecord: null }
  switch (message.type) {
    case WORKFLOW_MESSAGE_TYPE.NODE_STARTED:
    case WORKFLOW_MESSAGE_TYPE.NODE_FINISHED:
      return {
        process: withNodeResult(process, message.nodeId, message.nodeResult),
        pendingConfirm: null,
        liveRecord: null,
      }
    case WORKFLOW_MESSAGE_TYPE.RECORD:
      return {
        process,
        pendingConfirm: null,
        liveRecord: { index: message.index, total: message.total, record: message.record },
      }
    case WORKFLOW_MESSAGE_TYPE.REQUIRES_ACTION:
      return {
        process: {
          ...process,
          exeHistoryStatus: WORKFLOW_EXE_STATUS.WAITING,
          message: '等待确认批改清单',
        },
        pendingConfirm: {
          nodeId: message.nodeId,
          nodeName: message.nodeName,
          plan: message.plan || [],
          defaultPlan: message.defaultPlan || [],
        },
        liveRecord: null,
      }
    default:
      return { process, pendingConfirm: null, liveRecord: null }
  }
}

/** 循环流式记录（按序号存放）→ 按序号排序的结果数组（并发迭代时可能乱序到达） */
export const sortLiveRecords = (liveRecords) =>
  Object.entries(liveRecords || {})
    .sort((a, b) => Number(a[0]) - Number(b[0]))
    .map(([, record]) => record)

/** 追加 / 覆盖一条流式记录，返回新对象 */
export const withLiveRecord = (liveRecords, index, record) => ({
  ...liveRecords,
  [index]: record,
})
