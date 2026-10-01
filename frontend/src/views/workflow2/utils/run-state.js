/**
 * 画布运行态：把后端 SSE 事件归约为「每个节点的状态 + 本次产出」。
 *
 * 全部是纯函数（入参不变、返回新对象），画布页只负责喂事件、节点卡片只负责渲染，
 * 这样状态流转可以脱离 Vue 单独验证，也不依赖 Vue Flow 内部的节点更新 API。
 */

/** 节点执行状态 */
export const RUN_STATUS = {
  /** 待运行：本次执行没有走到该节点 */
  idle: 'idle',
  /** 执行中 */
  running: 'running',
  /** 完成：已收到该节点的 node 事件 */
  done: 'done',
  /** 等待确认：人工确认节点中断 */
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

/** 单节点运行态的初始值 */
const IDLE_RUNTIME = { status: RUN_STATUS.idle, result: null, waitLabel: '' }

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
 * 迭代进度建档：从画布节点推导「容器 id → 子块集合 + 迭代源引用」。
 * 轮次靠子块的 node 事件累计（后端每轮把子块事件转发一次），总轮数取迭代源数组的长度。
 */
const iterationPlanOf = (nodes = []) => {
  const list = (nodes ?? []).filter((node) => node && typeof node !== 'string')
  const plan = {}
  for (const node of list) {
    if (String(node.type) !== ITERATE_TYPE) continue
    plan[node.id] = {
      /** 迭代体内的子块 id 集合：命中一次即完成一轮 */
      children: new Set(list.filter((child) => child.parentNode === node.id).map((child) => child.id)),
      /** 迭代源引用：其产出到达后据数组长度得出总轮数 */
      source: listSourceOf(node.data?.inputs),
      done: 0,
      total: 0,
    }
  }
  return plan
}

/** node 事件 → 迭代进度：子块事件累计轮次，迭代源的产出到达时补总轮数 */
const withIterationProgress = (state, nodeId, eventState) => {
  const plan = state.iterations
  if (!plan || !Object.keys(plan).length) return state

  let changed = false
  const iterations = { ...plan }
  for (const [containerId, info] of Object.entries(plan)) {
    let next = info
    if (info.children.has(nodeId)) {
      next = { ...next, done: next.done + 1 }
    }
    if (info.source?.blockID === nodeId) {
      const value = eventState?.[nodeId]?.[info.source.name]
      if (Array.isArray(value)) next = { ...next, total: value.length }
    }
    if (next !== info) {
      iterations[containerId] = next
      changed = true
    }
  }
  return changed ? { ...state, iterations } : state
}

/** 一次运行的空状态 */
export const createRunState = () => ({ runId: null, streaming: false, nodes: {}, iterations: {} })

/**
 * 提交执行：把画布上的全部节点标记为「执行中」，清空上一次的产出，并按画布结构建立迭代进度。
 *
 * @param {object} state 当前运行态
 * @param {Array<object|string>} nodes 画布节点（传裸 id 数组时也能工作，只是拿不到迭代进度）
 */
export const beginRun = (state, nodes = []) => ({
  runId: null,
  streaming: true,
  nodes: Object.fromEntries(nodes.map((node) => [idOf(node), { ...IDLE_RUNTIME, status: RUN_STATUS.running }])),
  iterations: iterationPlanOf(nodes),
})

/** 写回单个节点的运行态（不可变） */
const withNode = (state, nodeId, patch) => ({
  ...state,
  nodes: { ...state.nodes, [nodeId]: { ...(state.nodes[nodeId] ?? IDLE_RUNTIME), ...patch } },
})

/**
 * 归约一个 SSE 事件。
 *
 * @param {object} state 当前运行态
 * @param {string} event 事件名：run / node / interrupt
 * @param {object} payload 事件载荷
 */
export const applyRunEvent = (state, event, payload) => {
  if (event === 'run') {
    return { ...state, runId: payload?.runId ?? state.runId, streaming: true }
  }
  if (event === 'node') {
    const nodeId = payload?.node
    if (!nodeId) return state
    // 节点产出：state[节点id]；迭代体子块的事件同样按各自的 id 落表（画布上没有对应卡片时不展示）
    const next = withNode(state, nodeId, {
      status: RUN_STATUS.done,
      result: payload?.state?.[nodeId] ?? null,
      waitLabel: '',
    })
    // 迭代体每完成一轮就推一次子块事件，据此累计迭代进度
    return withIterationProgress(next, nodeId, payload?.state)
  }
  if (event === 'interrupt') {
    const nodeId = payload?.nodeId
    if (!nodeId) return state
    return withNode(state, nodeId, { status: RUN_STATUS.waiting, waitLabel: payload?.label ?? '' })
  }
  return state
}

/**
 * 收尾：流正常结束时，仍处于「执行中」的节点回落为「待运行」（本次没走到）；异常结束时标记失败。
 *
 * @param {object} state 当前运行态
 * @param {boolean} failed 是否为异常结束
 */
export const finishRun = (state, failed = false) => ({
  ...state,
  streaming: false,
  nodes: Object.fromEntries(
    Object.entries(state.nodes).map(([id, runtime]) => [
      id,
      runtime.status === RUN_STATUS.running
        ? { ...runtime, status: failed ? RUN_STATUS.failed : RUN_STATUS.idle }
        : runtime,
    ]),
  ),
})

/** 人工确认后继续：保留已有产出，仅把流重新标记为进行中 */
export const resumeRun = (state) => ({ ...state, streaming: true })

/** 读取某节点的运行态；从未参与运行（含未登记）时返回 null，卡片据此决定是否渲染结果面板 */
export const nodeRuntimeOf = (state, nodeId) => state?.nodes?.[nodeId] ?? null

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
