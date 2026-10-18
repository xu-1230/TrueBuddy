/**
 * 作业批改页面的**执行链路**：建会话 → `POST /chat` → 读流归约 →（遇人工确认按原值自动通过）
 * → 从「输出节点」声明的契约里取出逐学生结果。
 *
 * 为什么复用画布页那套（`readEventStream` + `run-state`）：执行入口只有一个 `/chat`，
 * 事件流协议、会话/执行/轮次三个标识、中断与续跑的语义都在那边已经实现并被用例钉住，
 * 这里再写一份一定会漂移。
 *
 * 关于**人工确认**：默认把中断卡片交给页面弹窗（`onConfirm`），由用户点「确定 / 取消」决定；
 * 没有 `onConfirm` 时（冒烟脚本、无界面调用）按**只读回填的原值自动通过** —— 这不是跳过闸门，
 * 卡片字段的取值全部来自上游（同名即只读回填，见 `views/workflow2/utils/card-fields.js`），
 * 用户点「确定」提交的就是这份原值。两种情况下一旦出现「需要人现填」或「上游没产出取值」，
 * 都拒绝继续 —— 静默把空值送过去会变成"流程成功、下游全是空"。
 *
 * 关于**流式上牌**：`onRecord` 会在**每轮迭代完成时**被调用一次。后端把子块产出写进该轮的
 * 局部状态，迭代体的出口子块（输出节点）那一帧里 `state[输出节点id]` 就是这一轮那一个学生的
 * 记录、`state[迭代节点id].index` 是轮次下标（0 起，与名单顺序一致）—— 所以"每完成一份上一张"
 * 只需要挂在这一帧上，不必等整轮跑完（见 {@link roundRecordOf}）。
 */
import { CHAT_API, buildCardSubmitBody, buildChatBody, createConversation, getWorkflowDetail } from '@/api/workflow'
import { readEventStream } from '@/utils/event-stream'
import { blockingIssuesOf, resolveCard } from '@/views/workflow2/utils/card-fields'
import {
  applyRunEvent,
  beginRun,
  createRunState,
  finishRun,
  nodeRuntimeOf,
  pendingCardOf,
  resumeRun,
} from '@/views/workflow2/utils/run-state'
import { recordsOf, resultContractOf } from './result-contract'
import { roundRecordOf } from './round-record'

/** 单次批改最多自动通过几轮人工确认（内置定义只有一个，环状定义才会更多） */
const MAX_CONFIRM_ROUNDS = 5

/**
 * 这次人工确认要提交的字段取值。
 *
 * @param {object} card 中断卡片载荷
 * @param {(card: object) => Promise<{values: object}|null>} [onConfirm] 有则交给页面弹窗，返回 null 表示用户取消
 * @returns {Promise<object>} 字段名 → 取值
 */
async function confirmValuesOf(card, onConfirm) {
  const { values, editable } = resolveCard(card)

  // 交给弹窗：取值是否可用由弹窗在提交时校验（上游没产出取值 / 需要人现填都会拦住）
  if (onConfirm) {
    const decision = await onConfirm(card)
    if (!decision) throw new Error('已取消本次批改')
    return decision.values ?? {}
  }

  const { unfilled, sourceMissing } = blockingIssuesOf(card, values)
  if (editable.length || unfilled.length || sourceMissing.length) {
    const parts = []
    if (editable.length) {
      parts.push(`需要人工填写「${editable.map((field) => field.label || field.name).join('、')}」`)
    }
    if (sourceMissing.length) {
      parts.push(`上游没有产出「${sourceMissing.join('、')}」`)
    }
    throw new Error(`工作流的中断节点无法自动通过：${parts.join('，')}；请在画布页试运行该工作流`)
  }
  return values
}

/** 终态事件名 → 结论（终态看帧，不看流是否结束） */
const TERMINAL_OF = {
  'conversation.chat.requires_action': 'requires_action',
  'conversation.chat.completed': 'completed',
  'conversation.chat.failed': 'failed',
}

/**
 * 跑一轮 `/chat` 并把事件归约成运行态。
 *
 * @param {object} options 选项
 * @param {object} options.baseState 起始运行态（首轮用 `beginRun`，续跑用 `resumeRun`）
 * @param {object} options.body 请求体（首轮带 `inputs`，续跑带 `message`）
 * @param {AbortSignal} [options.signal] 取消信号
 * @param {(payload: object) => void} [options.onNode] 每一帧的旁路回调（流式上牌用，见 `roundRecordOf`）
 * @param {(event: string, payload: object) => void} [options.onEvent] 每个事件的旁路回调（进度展示用）
 * @returns {Promise<{state: object, terminal: string|null, lastError: object|null}>}
 */
async function runOnce({ baseState, body, signal, onEvent, onNode }) {
  let state = baseState
  let terminal = null
  let lastError = null

  await readEventStream(CHAT_API.chat, {
    body,
    signal,
    onEvent: (event, payload) => {
      state = applyRunEvent(state, event, payload)
      if (TERMINAL_OF[event]) terminal = TERMINAL_OF[event]
      if (event === 'conversation.chat.failed') lastError = payload?.last_error ?? null
      onNode?.(payload)
      onEvent?.(event, payload)
    },
  })

  return { state: finishRun(state, terminal === 'failed'), terminal, lastError }
}

/**
 * 跑一次批改。
 *
 * @param {object} options 选项
 * @param {string} options.workflowId 批改工作流 id（必须已落库，`/chat` 不接受内联定义）
 * @param {object} options.inputs 首轮业务输入：`{ file_tree, description, know }`，键名与工作流开始节点声明的变量一致
 * @param {string} [options.title] 会话标题
 * @param {AbortSignal} [options.signal] 取消信号
 * @param {(stage: string) => void} [options.onStage] 阶段回调：`conversation` / `grade` / `confirm`
 * @param {(record: object, index: number|null) => void} [options.onRecord] 每完成一份学生就回调一次（流式上牌）
 * @param {(card: object) => Promise<{values: object}|null>} [options.onConfirm] 人工确认交由页面弹窗；返回 null 表示用户取消
 * @returns {Promise<{contract: object, records: Array, statistics: object|null, conversationId: string, runState: object}>}
 *   `records` 是逐学生记录（字段名来自输出节点声明的 `inputs`）；`statistics` 是汇总节点的产出（没有汇总节点时为 null）
 */
export async function runGradingWorkflow({
  workflowId,
  inputs,
  title,
  signal,
  onStage = () => {},
  onRecord,
  onConfirm,
}) {
  if (!workflowId) {
    throw new Error('请先选择批改工作流')
  }

  // 先读定义：拿不到结果契约就没有必要起执行（跑完才发现没处展示是最坏的情况）
  const detail = await getWorkflowDetail(workflowId)
  const contract = resultContractOf(detail?.schemaJson)
  if (!contract) {
    throw new Error('选中的工作流没有「输出」节点，无法展示批改结果；请先在画布页为它加一个输出节点')
  }

  onStage('conversation')
  const conversation = await createConversation(workflowId, title)
  const conversationId = conversation?.conversationId
  if (!conversationId) {
    throw new Error('建立会话失败，请重试')
  }

  // 流式上牌：同一轮只上一次（帧可能重复投递），无轮次下标时按下标顺序兜底去重
  const seenRounds = new Set()
  let fallbackRound = 0
  const emitRound = (payload) => {
    const hit = roundRecordOf(contract, payload)
    if (!hit || !onRecord) return
    const key = hit.index === null ? `seq-${fallbackRound++}` : `round-${hit.index}`
    if (seenRounds.has(key)) return
    seenRounds.add(key)
    onRecord(hit.record, hit.index)
  }

  onStage('grade')
  let round = await runOnce({
    baseState: beginRun(createRunState(), []),
    body: { ...buildChatBody(conversationId, workflowId), inputs },
    signal,
    onNode: emitRound,
  })

  let confirmRounds = 0
  while (round.terminal === 'requires_action') {
    const card = pendingCardOf(round.state)
    if (!card) {
      throw new Error('执行被中断，但没有拿到确认卡片')
    }
    confirmRounds += 1
    if (confirmRounds > MAX_CONFIRM_ROUNDS) {
      throw new Error(`人工确认已连续中断 ${MAX_CONFIRM_ROUNDS} 轮，已中止（工作流可能存在环）`)
    }

    const values = await confirmValuesOf(card, onConfirm)
    onStage('confirm')
    round = await runOnce({
      baseState: resumeRun(round.state),
      body: buildCardSubmitBody(conversationId, card.node_id, values),
      signal,
      onNode: emitRound,
    })
  }

  if (round.terminal !== 'completed') {
    const reason = round.lastError?.msg
    throw new Error(reason || (round.terminal === 'failed' ? '批改执行失败' : '批改执行未正常结束'))
  }

  const sourceResult = nodeRuntimeOf(round.state, contract.sourceNodeId)?.result ?? null
  if (!sourceResult || !Object.keys(sourceResult).length) {
    // 不静默返回空记录：那样页面只会得到一张空画布，谁也说不清是配置问题还是执行问题
    throw new Error(
      `执行已结束，但没有拿到「${contract.sourceNodeId}」的产出`
      + `（本次登记到的节点：${Object.keys(round.state.nodes).join('、') || '无'}），无法展示批改结果`,
    )
  }

  const statistics = contract.summaryNodeId
    ? nodeRuntimeOf(round.state, contract.summaryNodeId)?.result ?? null
    : null

  return {
    contract,
    records: recordsOf(sourceResult, contract.fields),
    statistics,
    conversationId,
    runState: round.state,
  }
}
