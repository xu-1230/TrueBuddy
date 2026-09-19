<script setup>
import { markRaw, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Refresh } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, MarkerType } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import './styles/nodePicker.css'

import StartNode from './node/StartNode.vue'
import InputNode from './node/InputNode.vue'
import OutputNode from './node/OutputNode.vue'
import LlmNode from './node/LlmNode.vue'
import IterationNode from './node/IterationNode.vue'
import IterationStartNode from './node/IterationStartNode.vue'
import IterationEndNode from './node/IterationEndNode.vue'


const {
  addNodes,
  addEdges,
  removeNodes,
  updateNode,
  getNodes,
  getEdges,
  getIntersectingNodes,
  onNodesChange,
  onNodeDragStart,
  onNodeDrag,
  onNodeDragStop,
  onNodeClick,
} = useVueFlow()

/** 内层点阵画布的四周内缩：.node-body 4px + .node-canvas 4px */
const ITERATION_CANVAS_INSET = 8
/** 迭代起止节点尺寸 */
const ITERATION_BOUNDARY_SIZE = 52
/** 起止节点与子节点之间的间距 */
const ITERATION_BOUNDARY_GAP = 12
/** 起点节点在迭代节点内部的落点（纵向 44px header + 4px 内边距） */
const ITERATION_START_POSITION = { x: ITERATION_CANVAS_INSET, y: 52 }
/**
 * 迭代节点内部子节点的落点：纵向贴画布内容区顶部，
 * 横向排在起点节点之后（画布内缩 8 + 起点 52 + 间距 40）。
 */
const ITERATION_CHILD_ORIGIN = { x: 100, y: 52 }
/**
 * 迭代节点内部子节点的可移动范围 = 内层点阵画布，[top, right, bottom, left] 对应
 * IterationNode.vue 的样式：header 44px、.node-body 左右 4px / 下 4px、.node-card 下 4px；
 * 右侧额外留出终点节点 + 间距，避免子节点压在终点上。
 * 用 { range: 'parent', padding } 的形式，容器尺寸变化时会自动跟随，无需再同步。
 */
const ITERATION_RIGHT_RESERVE = ITERATION_CANVAS_INSET + ITERATION_BOUNDARY_SIZE + ITERATION_BOUNDARY_GAP
const ITERATION_CHILD_EXTENT = { range: 'parent', padding: [44, ITERATION_RIGHT_RESERVE, 8, 4] }
/** 迭代节点内部子节点的层级，需高于容器本身，避免被子节点被容器覆盖 */
const ITERATION_CHILD_Z_INDEX = 1
/**
 * 迭代节点内部连线的层级。
 * 节点层默认在连线层之上，所以要抬高；另外 vue-flow 的 elevateNodesOnSelect 默认开启，
 * 选中的节点会额外 +1000（`nodeZIndex + (selected ? 1e3 : 0)`），
 * 点一下迭代节点就会把整张卡片抬到 1000，因此内部连线必须高过 1000 才不会被盖掉。
 */
const INNER_EDGE_Z_INDEX = 1100
/** 迭代节点内部的起点 / 终点节点类型 */
const ITERATION_BOUNDARY_TYPES = ['iteration-start', 'iteration-end']

/** 迭代节点内部的起点 / 终点节点（不可拖动、不可删除） */
const isIterationBoundaryNode = (node) => ITERATION_BOUNDARY_TYPES.includes(node?.type)

/**
 * 迭代节点自带的起止节点：起点只出线、终点只入线，用来串起内部子流程并闭环。
 * 不设 extent，位置由 IterationNode 按画布尺寸维护。
 */
const createIterationBoundaryNodes = (iterationId) => [
  {
    id: `${iterationId}-start`,
    type: 'iteration-start',
    parentNode: iterationId,
    zIndex: ITERATION_CHILD_Z_INDEX,
    position: { ...ITERATION_START_POSITION },
    draggable: false,
    deletable: false,
  },
  {
    id: `${iterationId}-end`,
    type: 'iteration-end',
    parentNode: iterationId,
    zIndex: ITERATION_CHILD_Z_INDEX,
    position: {
      x: ITERATION_START_POSITION.x + ITERATION_BOUNDARY_SIZE + 240,
      y: ITERATION_START_POSITION.y,
    },
    draggable: false,
    deletable: false,
  },
]
/** 迭代节点内部子节点的兜底高度 */
const ITERATION_CHILD_HEIGHT = 120

/**
 * 初始图数据。
 * 这里刻意用单向绑定（:nodes / :edges）而不是 v-model：vue-flow 只在
 * 「数组引用或长度变化」时把 store 同步回 props，而拖动、改 parentNode / extent / position
 * 都是原地修改、不会回写，用 v-model 会让人误以为它就是最新数据。
 * 运行时读图请一律走 useVueFlow() 的 getNodes() / getEdges()（store 才是唯一数据源）。
 */
const initialNodes = ref([
  {
    id: "100001",
    type: 'input',
    data: {
      label: '开始',
      outputs: [
        {
          name: 'USER_INPUT',
          type: 'string',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
        },
        {
          name: 'CONVERSATION_NAME',
          type: 'string',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
        },
        {
          name: 'test',
          type: 'list',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
          schema: {
            type: 'string',
            description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
          },
        },
      ],
    },
    position: { x: 100, y: 100 },
    deletable: false,
    sourcePosition: 'right',
    targetPosition: 'left',
    blocks: [],
  },
  { id:"4", type: 'start', data: { label: '测试6666666666666666'}, position: { x: 100, y: 100 }},
  { id:"6", type: 'start', data: { label: '测试6666666666666666'}, position: { x: -20, y: 180 }},

  {
    id: 'llm-1',
    type: 'llm',
    data: {
      label: 'deepseek 大模型',
      inputs: [
        {
          name: 'message',
          type: 'string',
          description: '用户消息，作为本次对话的输入内容。',
        },
        {
          name: 'prompt',
          type: 'string',
          description: '系统提示词，用于约束大模型的回答风格。',
        },
        {
          name: 'model',
          type: 'string',
          description: '本次调用使用的大模型名称。',
        },
        {
          name: 'mcpServers',
          type: 'list',
          description: '本次调用可挂载的 MCP 服务列表。',
          schema: {
            type: 'string',
            description: 'MCP 服务名称。',
          },
        },
      ],
      outputs: [
        {
          name: 'text',
          type: 'string',
          description: '大模型返回的文本结果。',
        },
      ],
    },
    position: { x: 360, y: 100 },
  },

  {
    id: '100002',
    type: 'iteration',
    data: { label: '迭代 2', children: [], childEdges: [] },
    position: { x: 120, y: 340 },
    width: 480,
    height: 280,
  },
  ...createIterationBoundaryNodes('100002'),

  {
    id: "__END__",
    type: 'output',
    data: {
      label: '结束',
      outputs: [
        {
          name: 'USER_INPUT',
          type: 'string',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
        },
        {
          name: 'CONVERSATION_NAME',
          type: 'string',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
        },
        {
          name: 'test',
          type: 'list',
          description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
          schema: {
            type: 'string',
            description: '本次请求绑定的会话，会自动写入消息、会从该会话读对话历史。',
          },
        },
      ],
    },
    position: { x: 100, y: 100 },
    deletable: false,
    sourcePosition: 'right',
    targetPosition: 'left',
    blocks: [],
  },
])

const initialEdges = ref([])

const nodeTypes = {
  start: markRaw(StartNode),
  input: markRaw(InputNode),
  output: markRaw(OutputNode),
  llm: markRaw(LlmNode),
  'iteration-start': markRaw(IterationStartNode),
  'iteration-end': markRaw(IterationEndNode),
}

const isIterationNode = (node) => node?.type === 'iteration'

/** Ctrl / Cmd 是否按下 */
const isModifierPressed = (event) => Boolean(event && (event.ctrlKey || event.metaKey))

const clampValue = (value, min, max) => Math.min(Math.max(value, min), Math.max(min, max))

/**
 * 子节点在迭代节点内部的落点。
 * 横向收敛在点阵画布内（容器不横向扩容）；纵向允许超出，随后由 growIterationToFit
 * 把容器连同点阵画布一起撑高，保证落点不会被 vue-flow 二次校正而产生跳动。
 */
const toChildPosition = (node, parent) => {
  const parentPosition = parent.computedPosition ?? parent.position
  const [top, right, , left] = ITERATION_CHILD_EXTENT.padding
  const parentSize = parent.dimensions ?? {}
  const nodeSize = node.dimensions ?? {}
  return {
    x: clampValue(
      node.position.x - parentPosition.x,
      Math.max(left, ITERATION_CHILD_ORIGIN.x),
      (parentSize.width ?? 0) - right - (nodeSize.width || 0),
    ),
    y: Math.max(top, ITERATION_CHILD_ORIGIN.y, node.position.y - parentPosition.y),
  }
}

/** 内容超出容器时自动放大迭代节点（宽高任一不够才写回） */
const growIterationToFit = (parent, contentBottom, contentRight) => {
  const patch = {}
  const requiredHeight = Math.ceil(contentBottom + 16)
  // 右侧要给终点节点留位置，宽度不够子节点会被压到终点上
  const requiredWidth = Math.ceil(contentRight + ITERATION_RIGHT_RESERVE + ITERATION_BOUNDARY_GAP)
  if ((parent.height ?? 0) < requiredHeight) patch.height = requiredHeight
  if ((parent.width ?? 0) < requiredWidth) patch.width = requiredWidth
  if (Object.keys(patch).length) {
    updateNode(parent.id, patch)
  }
}

/** 把画布上的节点挂进迭代节点内部 */
const nestIntoIteration = (node, parent) => {
  const position = toChildPosition(node, parent)
  updateNode(node.id, {
    parentNode: parent.id,
    extent: ITERATION_CHILD_EXTENT,
    zIndex: ITERATION_CHILD_Z_INDEX,
    position,
  })
  growIterationToFit(
    parent,
    position.y + (node.dimensions?.height || ITERATION_CHILD_HEIGHT),
    position.x + (node.dimensions?.width || 0),
  )
}

/** 把节点从迭代节点内部取出，坐标换算回画布坐标系 */
const detachFromIteration = (node) => {
  const parent = getNodes.value.find((item) => item.id === node.parentNode)
  if (!parent) return
  const parentPosition = parent.computedPosition ?? parent.position
  updateNode(node.id, {
    parentNode: undefined,
    extent: undefined,
    zIndex: undefined,
    position: {
      x: parentPosition.x + node.position.x,
      y: parentPosition.y + node.position.y,
    },
  })
  ElMessage.info(`已从「${parent.data?.label ?? '迭代节点'}」取出`)
}

/** 本次 Ctrl / Cmd 取出动作对应的节点，拖拽结束时不再把它自动挂回迭代节点 */
let modifierDetachedNodeId = null

/**
 * Ctrl / Cmd + 点击（拖拽）子节点：把节点从迭代节点中取出，之后即可自由拖到画布任意位置。
 * vue-flow 默认 nodeDragThreshold 为 1，按住 Ctrl 点击时只要指针有 1px 以上位移就会走拖拽流程，
 * 且 multiSelectionActive 为 true 时不会再派发 nodeClick，所以三个入口都要接。
 */
const detachWithModifier = ({ event, node }) => {
  // 起止节点是迭代节点的一部分，不允许被取出
  if (!node?.parentNode || isIterationBoundaryNode(node)) return
  if (!isModifierPressed(event)) return
  detachFromIteration(node)
  modifierDetachedNodeId = node.id
}

// ① Ctrl + 点击（指针几乎不动，走原生 click）
onNodeClick(detachWithModifier)
// ② Ctrl + 拖拽时按下（指针位移超过阈值，vue-flow 走拖拽流程）
onNodeDragStart((payload) => {
  modifierDetachedNodeId = null
  detachWithModifier(payload)
})
// ③ 拖拽过程中才按下 Ctrl
onNodeDrag(detachWithModifier)

// 拖拽结束：与某个迭代节点相交则自动挂入其内部
onNodeDragStop(({ node }) => {
  if (!node || isIterationNode(node)) return
  // 刚刚被 Ctrl / Cmd 取出的节点：本次交互是“取出”，不再自动挂回
  if (node.id === modifierDetachedNodeId) {
    modifierDetachedNodeId = null
    return
  }
  // 已在迭代节点内部的不重复挂载
  if (node.parentNode) return
  const intersections = getIntersectingNodes({ id: node.id })
  const target = getNodes.value.find(
    (item) =>
      isIterationNode(item) && item.id !== node.id && intersections.some((hit) => hit.id === item.id),
  )
  if (target) {
    nestIntoIteration(node, target)
  }
})

/** 递归收集某个节点下面的所有后代 id（迭代节点的子节点、起止节点、嵌套迭代节点…） */
const collectDescendantIds = (nodeId, list) => {
  const ids = []
  const queue = [nodeId]
  while (queue.length) {
    const current = queue.shift()
    for (const item of list) {
      if (item.parentNode === current) {
        ids.push(item.id)
        queue.push(item.id)
      }
    }
  }
  return ids
}

/**
 * 删除迭代节点时级联清理它的子节点。
 * vue-flow 按 Backspace 删除走的是 `removeNodes(selectedNodes)`，而 removeNodes 的
 * `removeChildren` 默认为 false，子节点会留下来变成孤儿（parentNode 指向已不存在的节点，
 * extent 又一直是 { range: 'parent' }，会持续报 NODE_EXTENT_INVALID 且位置错乱）。
 */
onNodesChange((changes) => {
  const removedIds = changes.filter((change) => change.type === 'remove').map((change) => change.id)
  if (!removedIds.length) return

  const list = getNodes.value
  const descendantIds = new Set()
  for (const id of removedIds) {
    for (const descendantId of collectDescendantIds(id, list)) {
      descendantIds.add(descendantId)
    }
  }
  if (!descendantIds.size) return

  // 放到下一帧再删：避免在 nodesChange 回调里重入
  nextTick(() => {
    const pending = [...descendantIds].filter((id) => getNodes.value.some((item) => item.id === id))
    if (!pending.length) return
    // 起止节点带 deletable: false（防止被单独删掉），级联删除时先解除限制再删，
    // removeNodes 会把挂在这些节点上的连线一并清掉
    for (const id of pending) {
      updateNode(id, { deletable: true })
    }
    removeNodes(pending)
  })
})

/**
 * 连线：vue-flow 的 autoConnect 默认为 false，不接这个事件的话拖完连线会被丢掉，
 * 所以这里自己把连接写进 edges。
 *
 * 另外做两件校验：
 * 1. 不允许节点连到自己；
 * 2. 同一对句柄之间不允许重复连线（否则会叠出多条完全重合的线）。
 *
 * 注意：节点层在连线层之上，迭代节点内部的连线会被容器卡片（白底 + 点阵画布）盖住，
 * 因此只要连线涉及迭代节点内部的节点，就把它抬到节点层之上。
 */
const handleConnect = (connection) => {
  const { source, target, sourceHandle = null, targetHandle = null } = connection

  if (source === target) {
    ElMessage.warning('不能把节点连到它自己')
    return
  }

  const duplicated = getEdges.value.some(
    (edge) =>
      edge.source === source &&
      edge.target === target &&
      (edge.sourceHandle ?? null) === sourceHandle &&
      (edge.targetHandle ?? null) === targetHandle,
  )
  if (duplicated) {
    ElMessage.warning('这两个节点之间已经有连线了')
    return
  }

  const innerNodeIds = new Set(
    getNodes.value.filter((item) => item.parentNode).map((item) => item.id),
  )
  const isInnerEdge = innerNodeIds.has(source) || innerNodeIds.has(target)

  addEdges([
    {
      ...connection,
      type: 'smoothstep',
      markerEnd: MarkerType.ArrowClosed,
      ...(isInnerEdge ? { zIndex: INNER_EDGE_Z_INDEX } : {}),
    },
  ])
}

/** 迭代节点内的「添加节点」面板可选类型 */
const nodeCatalog = [
  { type: 'llm', label: '大模型', color: '#12b76a', icon: Box },
  { type: 'iteration', label: '迭代', color: '#06aed4', icon: Refresh },
]

let nodeSequence = 2

/** 生成要新增的子节点，不同类型带各自的默认数据与尺寸 */
const createChildNode = (type, index, parentId) => {
  const base = {
    id: `${type}-${index}`,
    type,
    parentNode: parentId,
    extent: ITERATION_CHILD_EXTENT,
    zIndex: ITERATION_CHILD_Z_INDEX,
    position: { x: ITERATION_CHILD_ORIGIN.x, y: 0 },
  }
  if (type === 'iteration') {
    return {
      ...base,
      // 迭代节点自身也带子流程字段，方便嵌套时继续往外层迭代节点挂
      width: 480,
      height: 280,
      data: { label: `迭代 ${index}`, children: [], childEdges: [] },
    }
  }
  return {
    ...base,
    data: {
      label: `LLM ${index}`,
      inputs: [{ name: 'prompt', type: 'string', description: '节点提示词。' }],
      outputs: [{ name: 'text', type: 'string', description: '大模型返回的文本结果。' }],
    },
  }
}

// 迭代节点内的“添加节点”：按所选类型在已有子节点下方追加节点，并把容器撑高
const handleAddNode = ({ parentId, type }) => {
  const parent = getNodes.value.find((item) => item.id === parentId)
  if (!parent) return
  const siblings = getNodes.value.filter((item) => item.parentNode === parentId)
  const nextY = siblings.reduce(
    (max, item) =>
      Math.max(max, item.position.y + (item.dimensions?.height || ITERATION_CHILD_HEIGHT) + 12),
    ITERATION_CHILD_ORIGIN.y,
  )
  const child = createChildNode(type, nodeSequence++, parentId)
  child.position = { ...child.position, y: nextY }
  // 嵌套迭代节点时，一并带上它自己的起止节点
  const added = type === 'iteration' ? [child, ...createIterationBoundaryNodes(child.id)] : [child]
  addNodes(added)
  growIterationToFit(
    parent,
    nextY + (child.height || ITERATION_CHILD_HEIGHT),
    ITERATION_CHILD_ORIGIN.x + (child.width || 0),
  )
}

</script>

<template>
<VueFlow
  :nodes="initialNodes"
  :edges="initialEdges"
  :node-types="nodeTypes"
  @connect="handleConnect"
>
  <!-- 迭代节点用插槽渲染：便于接住组件内部抛出的「添加节点」事件 -->
  <template #node-iteration="iterationProps">
    <IterationNode v-bind="iterationProps" :catalog="nodeCatalog" @add-node="handleAddNode" />
  </template>
</VueFlow>

</template>



<style scoped>
/*
 * 自定义 input / output 节点后，需覆盖 vue-flow 默认主题（theme-default.css）给
 * 内置 .vue-flow__node-input / .vue-flow__node-output 加的 padding / border /
 * 固定宽度等样式，否则自定义卡片外会多出一层描边和内边距。
 */
:deep(.vue-flow__node-input),
:deep(.vue-flow__node-output) {
  padding: 0;
  width: auto;
  border: none;
  border-radius: 0;
  background: transparent;
  text-align: left;
  box-shadow: none;
}

:deep(.vue-flow__node-input.selectable:hover),
:deep(.vue-flow__node-input.selected),
:deep(.vue-flow__node-input.selected:hover),
:deep(.vue-flow__node-input:focus),
:deep(.vue-flow__node-input:focus-visible),
:deep(.vue-flow__node-output.selectable:hover),
:deep(.vue-flow__node-output.selected),
:deep(.vue-flow__node-output.selected:hover),
:deep(.vue-flow__node-output:focus),
:deep(.vue-flow__node-output:focus-visible) {
  border: none;
  box-shadow: none;
}
</style>
