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
