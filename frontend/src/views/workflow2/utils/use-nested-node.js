/**
 * 判断节点是否渲染在容器（迭代节点）内部。
 *
 * 迭代体里的子块不单独展示运行结果：迭代节点卡片正下方有统一的结果区，
 * 否则每个子块各自挂一块结果面板，会把容器内部撑得杂乱且互相遮挡。
 * Vue Flow 的父子节点在组件树上不是嵌套关系（子节点的 DOM 与容器平级），
 * 因此只能按 `parentNode` 反查，而不能用 provide / inject。
 *
 * 判定口径收在 `utils/iteration` 的 {@link isNestedNode}：**父容器必须仍然存在**。
 * 容器被删除后残留的子块（或加载进来的悬空 `parentNode`）不再算「在迭代体内」，
 * 结果面板照常渲染，避免出现「面板永不出现」的静默现象。
 */
import { computed } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import { isNestedNode } from './iteration'

/**
 * @param {string} nodeId 节点 id
 * @returns {import('vue').ComputedRef<boolean>} 该节点是否挂在某个容器下
 */
export function useNestedNode(nodeId) {
  const { getNodes } = useVueFlow()
  return computed(() => isNestedNode(getNodes.value, nodeId))
}
