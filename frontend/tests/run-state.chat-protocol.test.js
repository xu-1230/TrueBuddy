/**
 * `/chat` 事件协议的归约用例（Node 内置 `node:test`）。
 *
 * 锁住三件事：
 * 1. 三个标识的分工：`conversation_id` 跨轮不变、`execute_id` 跨轮复用（同一行 workflow_run）、
 *    `chat_id` 每轮新生成；
 * 2. 节点产出与表单卡片**都走** `conversation.message.completed`，靠 `content_type` 分流
 *    （verbose → 节点完成；card → 等待确认 + 弹窗数据）；
 * 3. 失败终态由 `conversation.chat.failed` 帧表达——`done` 只是「流关闭」，**流正常关闭不等于成功**。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  applyRunEvent,
  beginRun,
  createRunState,
  finishRun,
  nodeRuntimeOf,
  pendingCardOf,
  resumeRun,
} from '../src/views/workflow2/utils/run-state.js'

const CONVERSATION_ID = 'conversation-1'
const EXECUTE_ID = 'execute-1'
const HUMAN_ID = '100003'
const START_ID = '100001'

/** 画布节点：一个开始节点 + 一个人工确认节点 */
const canvasNodes = () => [
  { id: START_ID, type: '1' },
  { id: HUMAN_ID, type: '5' },
]

/** 一轮开始（首轮或续跑）：created → in_progress */
const startRound = (state, chatId) => {
  const created = applyRunEvent(state, 'conversation.chat.created', {
    status: 'created',
    conversation_id: CONVERSATION_ID,
    execute_id: EXECUTE_ID,
    chat_id: chatId,
  })
  return applyRunEvent(created, 'conversation.chat.in_progress', { status: 'in_progress' })
}

/** 节点产出帧 */
const nodeFinished = (state, nodeId, payloadState) => applyRunEvent(state, 'conversation.message.completed', {
  content_type: 'verbose',
  content: { msg_type: 'node_finished', data: { node: nodeId, state: payloadState } },
})

/** 中断卡片帧 */
const card = (nodeId = HUMAN_ID) => ({
  content_type: 'card',
  content: {
    card_type: 'INPUT',
    node_id: nodeId,
    title: '人工确认',
    data: [{ name: 'plan', type: 'list', label: '待确认清单', value: ['张三'] }],
    fields: [{ name: 'files', type: 'list', required: true, label: '作业文件', schema: { type: 'object' } }],
  },
})

test('三个标识的分工：会话跨轮不变、执行跨轮复用、轮次每轮新生成', () => {
  let state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  assert.equal(state.conversationId, CONVERSATION_ID)
  assert.equal(state.executeId, EXECUTE_ID)
  assert.equal(state.chatId, 'chat-1')

  // 续跑：后端复用同一条执行记录（同一个 execute_id），但轮次编号是新的
  state = startRound(resumeRun(state), 'chat-2')
  assert.equal(state.conversationId, CONVERSATION_ID, '会话编号跨轮不变')
  assert.equal(state.executeId, EXECUTE_ID, '执行编号跨轮复用')
  assert.equal(state.chatId, 'chat-2', '轮次编号每轮新生成')
})

test('节点产出帧：节点置为完成，产出取 state[节点id]', () => {
  let state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  state = nodeFinished(state, START_ID, { [START_ID]: { file_tree: ['第三章习题.docx'] } })

  const runtime = nodeRuntimeOf(state, START_ID)
  assert.equal(runtime.status, 'done')
  assert.deepEqual(runtime.result, { file_tree: ['第三章习题.docx'] })
})

test('中断帧：卡片落到等待确认的节点上，data / fields 原样保留', () => {
  let state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  state = applyRunEvent(state, 'conversation.message.completed', card())
  state = applyRunEvent(state, 'conversation.chat.requires_action', { status: 'requires_action' })

  const runtime = nodeRuntimeOf(state, HUMAN_ID)
  assert.equal(runtime.status, 'waiting')
  assert.equal(runtime.waitLabel, '人工确认', '节点卡片上的文案取卡片标题')

  const pending = pendingCardOf(state)
  assert.equal(pending.node_id, HUMAN_ID)
  assert.equal(pending.fields[0].name, 'files')
  assert.deepEqual(pending.data[0].value, ['张三'])

  // 收尾：waiting 的节点保持 waiting——它不是「本次没走到」，不能回落 idle
  const finished = finishRun(state)
  assert.equal(finished.streaming, false)
  assert.equal(nodeRuntimeOf(finished, HUMAN_ID).status, 'waiting')
  assert.equal(pendingCardOf(finished).node_id, HUMAN_ID, '弹窗数据在流关闭后仍可取到')
})

test('续跑把等待确认的节点接着跑完，卡片随之清空', () => {
  let state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  state = applyRunEvent(state, 'conversation.message.completed', card())
  state = finishRun(applyRunEvent(state, 'conversation.chat.requires_action', { status: 'requires_action' }))

  // 第二轮：同一个会话、同一条执行记录，人工确认节点这次真的产出了
  state = startRound(resumeRun(state), 'chat-2')
  assert.equal(pendingCardOf(state), null, '续跑开始时上一轮的卡片已被提交消费掉')
  assert.equal(nodeRuntimeOf(state, HUMAN_ID).status, 'running', '等待确认的节点被拉回执行中')
  state = nodeFinished(state, HUMAN_ID, { [HUMAN_ID]: { files: [{ name: '第三章习题.docx' }] } })
  state = finishRun(applyRunEvent(state, 'conversation.chat.completed', { status: 'completed' }))

  assert.equal(state.status, 'completed')
  assert.equal(nodeRuntimeOf(state, HUMAN_ID).status, 'done')
  assert.equal(pendingCardOf(state), null, '确认完成后不该再有待填卡片')
})

test('失败终态：chat.failed 记失败，流正常关闭时未完成的节点标为失败', () => {
  let state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  state = applyRunEvent(state, 'conversation.chat.failed', {
    status: 'failed',
    last_error: { code: 500, msg: '炸了' },
  })
  const finished = finishRun(state)

  assert.equal(finished.status, 'failed')
  assert.equal(finished.failed, true, '流照常关闭（done），失败只能从 chat.failed 帧读出来')
  assert.equal(nodeRuntimeOf(finished, START_ID).status, 'failed')
})

test('done 帧不参与归约：它只表示流关闭', () => {
  const state = startRound(beginRun(createRunState(), canvasNodes()), 'chat-1')
  assert.equal(applyRunEvent(state, 'done', { conversation_id: CONVERSATION_ID }), state)
})
