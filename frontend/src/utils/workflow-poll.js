import { getProcess, WORKFLOW_EXE_STATUS } from '@/api/workflow'

/** 轮询间隔（毫秒）：后端进度为节点级，2s 足够且请求量可控 */
export const WORKFLOW_POLL_INTERVAL = 2000

/** 连续失败的最大重试次数（网络抖动容错；会话失效等业务错误不重试，直接结束） */
export const WORKFLOW_POLL_MAX_RETRY = 3

/** 会话不存在 / 已过期：重试也不会恢复，直接结束轮询 */
const SESSION_NOT_FOUND_CODE = 1005000005

/**
 * 轮询工作流执行状态（SSE 的兜底通道：断线重连 / 页面刷新后续上进度）。
 *
 * 行为：立即请求一次，之后每 `interval` 毫秒请求一次；**只有 2（成功）/ 3（失败）才是终态**，
 * 1（执行中）与 4（等待用户输入）都继续轮询（后者额外回调 `onWaiting`，供页面弹出确认清单）；
 * 请求异常按 `WORKFLOW_POLL_MAX_RETRY` 重试，超出上限或遇到「会话不存在」时回调 `onError` 并停止。
 *
 * 使用方必须在组件卸载 / 重新提交前调用 `stop()`，避免残留定时器。
 *
 * @param {string} workflowId 执行会话 id（SSE 的 `created` 事件下发）
 * @param {{
 *   onProcess?: (process: import('@/api/workflow').WorkflowProcessRespVO) => void,
 *   onWaiting?: (process: import('@/api/workflow').WorkflowProcessRespVO) => void,
 *   onFinish?: (process: import('@/api/workflow').WorkflowProcessRespVO) => void,
 *   onError?: (error: Error) => void,
 *   interval?: number,
 * }} [handlers]
 * @returns {{ stop: () => void, done: Promise<object|undefined> }} stop 立即停止轮询；done 在结束时 settle
 */
export function pollWorkflowProcess(workflowId, handlers = {}) {
  const { onProcess, onWaiting, onFinish, onError, interval = WORKFLOW_POLL_INTERVAL } = handlers
  let timer = null
  let stopped = false
  let errorCount = 0
  let settle
  const done = new Promise((resolve) => {
    settle = resolve
  })

  const stop = () => {
    stopped = true
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const finish = (process) => {
    stop()
    onFinish?.(process)
    settle(process)
  }

  const fail = (error) => {
    stop()
    onError?.(error)
    settle(undefined)
  }

  const tick = async () => {
    if (stopped) return
    try {
      const process = await getProcess(workflowId)
      if (stopped) return
      errorCount = 0
      onProcess?.(process)
      if (process?.exeHistoryStatus === WORKFLOW_EXE_STATUS.WAITING) {
        // 等待用户输入：非终态，继续轮询；调用方据 pendingConfirm 弹出确认清单
        onWaiting?.(process)
      }
      // 只有成功 / 失败才是终态；等待态（4）若误判为结束，会导致确认后拿不到后续进度
      if (process?.exeHistoryStatus !== WORKFLOW_EXE_STATUS.FINISHED
        && process?.exeHistoryStatus !== WORKFLOW_EXE_STATUS.FAILED) {
        timer = setTimeout(tick, interval)
        return
      }
      finish(process)
    } catch (error) {
      if (stopped) return
      if (error?.code === SESSION_NOT_FOUND_CODE || errorCount >= WORKFLOW_POLL_MAX_RETRY) {
        fail(error)
        return
      }
      errorCount += 1
      timer = setTimeout(tick, interval)
    }
  }

  tick()
  return { stop, done }
}
