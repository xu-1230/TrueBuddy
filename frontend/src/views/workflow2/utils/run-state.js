/**
 * 画布运行态：把后端 `/chat` 的 SSE 事件归约为「每个节点的状态 + 本次产出 + 待确认卡片」。
 *
 * 全部是纯函数（入参不变、返回新对象），画布页只负责喂事件、节点卡片只负责渲染，
 * 这样状态流转可以脱离 Vue 单独验证，也不依赖 Vue Flow 内部的节点更新 API。
 *
 * 事件协议（对齐 Coze 的 `conversation.*` 命名，见后端 `ChatProtocol`）：
 * - `conversation.chat.created`      → 拿到 conversation_id / execute_id / chat_id
 * - `conversation.chat.in_progress`  → 开始执行
 * - `conversation.message.completed` → 节点产出（content_type=verbose）或表单卡片（content_type=card）
 * - `conversation.chat.requires_action` / `.completed` / `.failed` → 终态
 * - `done`                           → 流关闭（**与成败无关**，不参与状态归约）
 */

/** 后端事件名（与 ChatProtocol 一一对应） */
export const RUN_EVENT = {
  created: 'conversation.chat.created',
  inProgress: 'conversation.chat.in_progress',
  messageCompleted: 'conversation.message.completed',
  requiresAction: 'conversation.chat.requires_action',
  completed: 'conversation.chat.completed',
  failed: 'conversation.chat.failed',
  done: 'done',
}

/** 消息维度：节点产出走 verbose 帧，表单卡片走 card 帧 */
const CONTENT_TYPE_VERBOSE = 'verbose'
const CONTENT_TYPE_CARD = 'card'
const MSG_TYPE_NODE_FINISHED = 'node_finished'

/** 节点执行状态 */
export const RUN_STATUS = {
  /** 待运行：本次执行没有走到该节点 */
  idle: 'idle',
  /** 执行中 */
  running: 'running',
  /** 完成：已收到该节点的产出帧 */
  done: 'done',
  /** 等待确认：人工确认节点中断，等待用户在弹窗里填写并提交 */
  waiting: 'waiting',
  /** 失败：流异常结束时仍未完成 */
  failed: 'failed',
}

/** 状态文案（界面统一取词） */
const STATUS_TEXT = {
  idle: '待运行',
  running: '执行中',
  done: '完成',
  waiting: '等待确认',
  failed: '失败',
}

/** 单节点运行态的初始值；card 只在人工确认节点上出现 */
const IDLE_RUNTIME = { status: RUN_STATUS.idle, result: null, waitLabel: '', card: null }

/** 状态中文文案 */
export const statusTextOf = (status) => STATUS_TEXT[status] ?? STATUS_TEXT.idle

/** 迭代节点类型编码（与 `utils/iteration` 的 ITERATE_NODE_TYPE 一致；此处用字面量避免引入 @vue-flow 依赖） */
const ITERATE_TYPE = '4'

/** 节点 id：兼容传入节点对象或裸 id 字符串（旧调用方传的是 id 数组） */
const idOf = (node) => (typeof node === 'string' ? node : node?.id)

/** 迭代源引用：inputs 里声明为 list 的那一项的 `input`（形如 `{ blockID, name }`） */
const listSourceOf = (inputs = []) => {
  const listInput = inputs.find((item) => item?.type === 'list' || item?.input?.type === 'list')
  const ref = (listInput ?? inputs[0])?.input
  return ref?.blockID && ref?.name ? { blockID: ref.blockID, name: ref.name } : null
}

/**
 * 迭代项个数：与后端 `NodeStateReader#toList` 同口径（见 12-工作流规范 §4.5）。
 *
 * 后端把迭代源归一化成一个列表：`List` / 数组原样使用，`null` 返回空列表（一轮都不跑），
 * 其余标量 / 对象按**单项列表**处理（跑 1 轮）。前端据此推断总轮数，不能只认数组——
 * 否则迭代源是标量时总轮数恒为 0，界面上只显示「1 轮」且进度条永远停在 0%。
 *
 * @param {*} value 迭代源节点的产出
 * @returns {number} 轮数
 */
export const toListLength = (value) => {
  if (Array.isArray(value)) return value.length
  if (value === null || value === undefined) return 0
  return 1
}

/** 已完成轮次不会超过总轮数；总轮数未知（0）时不设上限（迭代源产出还没到） */
const clampDone = (done, total) => (total > 0 ? Math.min(done, total) : Math.max(done, 0))

/**
 * 迭代进度建档：从画布节点推导「容器 id → 子块集合 + 迭代源引用」。
 * 总轮数取迭代源数组的长度；已完成轮次取后端事件里携带的权威轮次下标
 * （`state[容器id].index`，见 {@link roundOfEvent}），拿不到时才退回「按子块事件累加」。
 */
const iterationPlanOf = (nodes = []) => {
  const list = (nodes ?? []).filter((node) => node && typeof node !== 'string')
  const plan = {}
  for (const node of list) {
    if (String(node.type) !== ITERATE_TYPE) continue
    /** 迭代体内的子块 id 集合：命中一次即完成一轮 */
    const children = new Set(list.filter((child) => child.parentNode === node.id).map((child) => child.id))
    // 画布只展开一层迭代体：内层嵌套迭代节点在画布上没有子块，收不到任何子块事件。
    // 登记它只会让卡片挂上一个永远不动的「0/N 轮」，因此跳过（进度查询返回 null，
    // 徽标退回「产出变量个数」，内层的存在感由卡片上的「嵌套 N」徽标承担）。
    if (!children.size) continue
    plan[node.id] = {
      children,
      /** 迭代源引用：其产出到达后据列表长度得出总轮数 */
      source: listSourceOf(node.data?.inputs),
      done: 0,
      total: 0,
    }
  }
  return plan
}

/**
 * 事件里的**权威轮次**（1 起）：迭代节点每轮执行时会把轮次下标写进 `state[迭代节点id].index`
 * （后端 `IterateNode#buildRoundInput`），子块事件转发出来时也带着它，因此前端直接取用即可。
 *
 * @returns {number} 轮次（1 起）；事件里没有该下标时返回 0
 */
const roundOfEvent = (eventState, containerId) => {
  const index = eventState?.[containerId]?.index
  return Number.isInteger(index) && index >= 0 ? index + 1 : 0
}

/** 节点产出帧 → 迭代进度：按事件里的权威轮次取已完成轮次，迭代源的产出到达时补总轮数 */
const withIterationProgress = (state, nodeId, eventState) => {
  const plan = state.iterations
  if (!plan || !Object.keys(plan).length) return state

  let changed = false
  const iterations = { ...plan }
  for (const [containerId, info] of Object.entries(plan)) {
    let next = info
    if (info.children.has(nodeId)) {
      const round = roundOfEvent(eventState, containerId)
      // 取事件自带的轮次（幂等：同轮多个子块事件都取到同一个轮次，不会重复计数）；
      // 兜底分支按子块事件累加，含多个子块时会多算成 N 倍——用总轮数钳住，不让文案出现「4/2 轮」
      const done = round ? Math.max(next.done, round) : next.done + 1
      next = { ...next, done: clampDone(done, next.total) }
    }
    if (info.source?.blockID === nodeId) {
      // 总轮数与后端 `NodeStateReader#toList` 同口径：标量源也算 1 轮
      const total = toListLength(eventState?.[nodeId]?.[info.source.name])
      if (total !== next.total) next = { ...next, total, done: clampDone(next.done, total) }
    }
    if (next !== info) {
      iterations[containerId] = next
      changed = true
    }
  }
  return changed ? { ...state, iterations } : state
}

/**
 * 一次运行的空状态。
 *
 * `conversationId` / `executeId` / `chatId` 由后端第一帧下发：会话编号跨轮不变、执行编号跨轮复用
 * （同一行 workflow_run）、轮次编号每轮新生成；`failed` 记录本轮是否以失败终态收尾，
 * 供 {@link finishRun} 在流正常关闭时区分「跑完了」与「失败了」。
 */
export const createRunState = () => ({
  conversationId: null,
  executeId: null,
  chatId: null,
  status: null,
  failed: false,
  streaming: false,
  nodes: {},
  iterations: {},
})

/**
 * 提交执行：把画布上的全部节点标记为「执行中」，清空上一次的产出，并按画布结构建立迭代进度。
 *
 * @param {object} state 当前运行态
 * @param {Array<object|string>} nodes 画布节点（传裸 id 数组时也能工作，只是拿不到迭代进度）
 */
export const beginRun = (state, nodes = []) => ({
  conversationId: state.conversationId,
  executeId: null,
  chatId: null,
  status: null,
  failed: false,
  streaming: true,
  nodes: Object.fromEntries(nodes.map((node) => [idOf(node), { ...IDLE_RUNTIME, status: RUN_STATUS.running }])),
  iterations: iterationPlanOf(nodes),
})

/** 写回单个节点的运行态（不可变） */
const withNode = (state, nodeId, patch) => ({
  ...state,
  nodes: { ...state.nodes, [nodeId]: { ...(state.nodes[nodeId] ?? IDLE_RUNTIME), ...patch } },
})

/** 从节点产出帧里取出 `{ node, state }`：载荷在同形约束下改挂到了 `content.data` 上 */
const nodePayloadOf = (payload) => {
  const data = payload?.content?.data
  if (!data?.node) return null
  return { node: data.node, state: data.state ?? {} }
}

/**
 * 归约一个 SSE 事件。
 *
 * @param {object} state 当前运行态
 * @param {string} event 事件名，见 {@link RUN_EVENT}
 * @param {object} payload 事件载荷
 */
export const applyRunEvent = (state, event, payload) => {
  if (event === RUN_EVENT.created) {
    return {
      ...state,
      conversationId: payload?.conversation_id ?? state.conversationId,
      executeId: payload?.execute_id ?? state.executeId,
      chatId: payload?.chat_id ?? state.chatId,
      status: payload?.status ?? state.status,
      streaming: true,
    }
  }
  if (event === RUN_EVENT.inProgress) {
    return { ...state, status: payload?.status ?? state.status, streaming: true }
  }
  if (event === RUN_EVENT.messageCompleted) {
    const contentType = payload?.content_type
    if (contentType === CONTENT_TYPE_CARD) {
      // 人工确认中断：记下弹窗数据，状态置 waiting（弹窗由画布页按 status=waiting 打开）
      const nodeId = payload?.content?.node_id
      if (!nodeId) return state
      return withNode(state, nodeId, {
        status: RUN_STATUS.waiting,
        waitLabel: payload?.content?.title ?? '',
        card: payload.content,
      })
    }
    if (contentType === CONTENT_TYPE_VERBOSE && payload?.content?.msg_type === MSG_TYPE_NODE_FINISHED) {
      const data = nodePayloadOf(payload)
      if (!data) return state
      // 节点产出：state[节点id]；迭代体子块的事件同样按各自的 id 落表（画布上没有对应卡片时不展示）
      const next = withNode(state, data.node, {
        status: RUN_STATUS.done,
        result: data.state?.[data.node] ?? null,
        waitLabel: '',
        card: null,
      })
      // 迭代体子块事件按事件里携带的轮次下标累计迭代进度（见 roundOfEvent）
      return withIterationProgress(next, data.node, data.state)
    }
    return state
  }
  if (event === RUN_EVENT.requiresAction) {
    return { ...state, status: payload?.status ?? 'requires_action' }
  }
  if (event === RUN_EVENT.completed) {
    return { ...state, status: payload?.status ?? 'completed' }
  }
  if (event === RUN_EVENT.failed) {
    return { ...state, status: payload?.status ?? 'failed', failed: true }
  }
  return state
}

/**
 * 收尾：流关闭时，仍处于「执行中」的节点回落为「待运行」（本次没走到）；失败时标记失败。
 *
 * @param {object} state 当前运行态
 * @param {boolean} [failed] 是否失败；省略时取本轮的终态帧结论（chat.failed → true）
 */
export const finishRun = (state, failed) => {
  const isFailed = typeof failed === 'boolean' ? failed : state.failed === true
  return {
    ...state,
    streaming: false,
    failed: isFailed,
    nodes: Object.fromEntries(
      Object.entries(state.nodes).map(([id, runtime]) => [
        id,
        runtime.status === RUN_STATUS.running
          ? { ...runtime, status: isFailed ? RUN_STATUS.failed : RUN_STATUS.idle }
          : runtime,
      ]),
    ),
  }
}

/**
 * 人工确认后继续：保留已有产出，仅把流重新标记为进行中。
 *
 * 上一轮的卡片已被这次提交**消费掉**，因此要清掉卡片并把该节点从「等待确认」拉回「执行中」：
 * 否则弹窗关闭后卡片仍挂在运行态里，节点上的「填写」按钮还能再打开一张已经作废的表单。
 */
export const resumeRun = (state) => ({
  ...state,
  streaming: true,
  failed: false,
  nodes: Object.fromEntries(
    Object.entries(state.nodes ?? {}).map(([id, runtime]) => (
      runtime.card
        ? [id, { ...runtime, status: RUN_STATUS.running, waitLabel: '', card: null }]
        : [id, runtime]
    )),
  ),
})

/** 读取某节点的运行态；从未参与运行（含未登记）时返回 null，卡片据此决定是否渲染结果面板 */
export const nodeRuntimeOf = (state, nodeId) => state?.nodes?.[nodeId] ?? null

/**
 * 当前待用户填写的表单卡片（可能同时只有一个：同一会话不允许并发执行）。
 *
 * @returns {object|null} 卡片载荷 `{node_id, card_type, title, data, fields}`；没有等待确认时返回 null
 */
export const pendingCardOf = (state) => {
  const entries = Object.entries(state?.nodes ?? {})
  const hit = entries.find(([, runtime]) => runtime.status === RUN_STATUS.waiting && runtime.card)
  return hit ? hit[1].card : null
}

/**
 * 读取某个迭代容器的进度：`{ done, total }`；未登记（非迭代节点或未运行）时返回 null。
 * `total` 为 0 表示迭代源的产出还没到达，界面上只显示已完成轮次。
 */
export const iterationProgressOf = (state, containerId) =>
  state?.iterations?.[containerId] ?? null

/** 单行取值的最大字符数，超出截断 */
const MAX_SCALAR_LENGTH = 60

/** 单个标量的展示文本：超长截断，对象/数组折叠为摘要 */
const summarizeScalar = (value) => {
  if (value === null || value === undefined) return '空值'
  if (Array.isArray(value)) return `[${value.length} 项]`
  if (typeof value === 'object') return `{${Object.keys(value).length} 个字段}`
  const text = String(value)
  return text.length > MAX_SCALAR_LENGTH ? `${text.slice(0, MAX_SCALAR_LENGTH)}…` : text
}

/** 单个取值的摘要：字符串截断、数组给出条数与预览、对象给出字段数 */
export const summarizeValue = (value) => {
  if (Array.isArray(value)) {
    const preview = value.slice(0, 2).map(summarizeScalar).join('、')
    return preview ? `共 ${value.length} 项：${preview}` : '共 0 项'
  }
  if (value !== null && typeof value === 'object') {
    const keys = Object.keys(value)
    return keys.length ? `共 ${keys.length} 个字段：${keys.slice(0, 3).join('、')}` : '空对象'
  }
  return summarizeScalar(value)
}

/**
 * 把节点产出摊平成可渲染的行：[{ name, summary, raw }]。
 * 产出约定是「变量名 -> 取值」的 Map；异常形态（数组 / 标量）也给出可读的一行。
 */
export const resultEntriesOf = (result) => {
  if (result === null || result === undefined) return []
  if (Array.isArray(result)) {
    return result.map((item, index) => ({ name: `item ${index}`, summary: summarizeValue(item), raw: item }))
  }
  if (typeof result !== 'object') {
    return [{ name: 'value', summary: summarizeValue(result), raw: result }]
  }
  return Object.entries(result).map(([name, value]) => ({ name, summary: summarizeValue(value), raw: value }))
}

/** 完整 JSON 文本（点击变量行时展开查看） */
export const prettyJson = (value) => {
  const text = JSON.stringify(value, null, 2)
  return text === undefined ? String(value) : text
}
