/**
 * 「某一轮迭代刚完成的那一份」的提取口径 —— 批改页**流式上牌**的唯一依据。
 *
 * 后端每轮迭代把子块产出写进**该轮的局部状态**，所以迭代体出口子块（结果契约里的
 * `outputNodeId`）那一帧里：
 * - `state[outputNodeId]` 就是**这一轮那一个学生**的完整记录；
 * - `state[迭代节点id].index` 是轮次下标（0 起，与名单顺序一致）。
 *
 * 于是「每完成一份学生就上一张卡」只要把回调挂在这一帧上就行，不必等整轮跑完。
 * 迭代节点的**汇总帧**（`state[迭代节点id]` 是各列数组）里没有出口子块的产出，
 * 因此天然不会被当成"又完成了一份"；人工确认卡片帧、非节点帧同理。
 *
 * 单独成文件（而不是写在 `grading-run.js` 里）的理由：那边要 import api 层，
 * 而这段是纯函数，得能在 `node:test` 里直接验。
 */
import { nodePayloadOf } from '@/views/workflow2/utils/run-state'

/**
 * @param {object} contract `resultContractOf` 的结果
 * @param {object} payload SSE 事件载荷
 * @returns {{record: object, index: number|null}|null} 不是"某轮刚完成"时返回 null
 */
export const roundRecordOf = (contract, payload) => {
  // 非迭代工作流只有一次产出，不参与流式上牌：那种定义的卡片由跑完后的最终结果统一补齐
  if (!contract?.iteration) return null

  const data = nodePayloadOf(payload)
  if (!data || data.node !== contract.outputNodeId) return null

  const record = data.state?.[data.node]
  // 数组/标量都不是一份成绩：前者是聚合列，后者是"模型返回没解析成对象"的退化形态
  if (!record || typeof record !== 'object' || Array.isArray(record)) return null

  const index = Number(data.state?.[contract.sourceNodeId]?.index)
  return { record, index: Number.isInteger(index) && index >= 0 ? index : null }
}
