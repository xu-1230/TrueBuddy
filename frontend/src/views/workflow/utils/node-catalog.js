/**
 * 工作流画布的节点目录（纯数据模块，不引入 Vue 与 Element Plus）。
 *
 * 供左侧工具栏的工具条与节点库浮层渲染使用：
 * - `addable: true` 的条目可以拖拽进画布；
 * - `addable: false` 的条目在画布上唯一存在（开始 / 结束），面板中置灰并展示 hint 说明。
 *
 * 画布上的节点 type 与工作流模型一一对应，这里只列「用户可主动创建」的类型：
 * 迭代节点内部的起点 / 终点（iteration-start / iteration-end）由迭代节点自带，不出现在目录中。
 */

/** 拖拽数据类型：dragstart 写入 / drop 读取必须一致 */
export const WORKFLOW_NODE_DND_TYPE = 'application/x-workflow-node'

/** 节点目录（顺序即面板展示顺序） */
export const NODE_CATALOG = [
  {
    kind: 'llm',
    label: '大模型',
    hint: '调用大语言模型生成回复',
    addable: true,
  },
  {
    kind: 'iteration',
    label: '迭代',
    hint: '重复执行一组内部子节点',
    addable: true,
  },
  {
    kind: 'human',
    label: '人工确认',
    hint: '执行到此中断，等待确认后继续',
    addable: true,
  },
  {
    kind: 'input',
    label: '开始',
    hint: '画布唯一实例，已存在',
    addable: false,
  },
  {
    kind: 'output',
    label: '结束',
    hint: '画布唯一实例，已存在',
    addable: false,
  },
]

/** 按 kind 取目录项；未知 kind 返回 null（drop 时用于校验 dataTransfer 内容） */
export function findCatalogItem(kind) {
  if (!kind) return null
  return NODE_CATALOG.find((item) => item.kind === kind) ?? null
}
