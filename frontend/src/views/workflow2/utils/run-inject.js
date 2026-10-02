/**
 * 画布运行态的注入键。
 *
 * 画布页 `provide`，节点卡片 `inject`：卡片可能在无运行态的上下文被复用（例如别处渲染同一张卡片），
 * 因此消费端必须允许缺省，缺省时退化为「不渲染结果面板」。
 */

/** 运行态（只读 ref：`{ runId, streaming, nodes }`） */
export const RUN_STATE_KEY = Symbol('workflowRunState')

/** 运行动作（`{ confirm: (nodeId, content) => void }`） */
export const RUN_ACTION_KEY = Symbol('workflowRunActions')

/**
 * 当前拖拽命中的迭代容器 id（只读 ref：`string | null`）。
 *
 * 画布页在 `node-drag` 期间按落点算出「松手会进哪个容器」，容器卡片 inject 它来高亮自己，
 * 让用户在松手**之前**就知道节点会落到哪里。只在拖拽期间有值，松手 / 取消即清空。
 */
export const ITERATION_DROP_TARGET_KEY = Symbol('workflowIterationDropTarget')
