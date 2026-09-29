<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Loading, RefreshRight, Warning } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

import StartNode from './node/StartNode.vue'
import InputNode from './node/InputNode.vue'
import OutputNode from './node/OutputNode.vue'
import LlmNode from './node/LlmNode.vue'
import IterationNode from './node/IterationNode.vue'
import IterationStartNode from './node/IterationStartNode.vue'
import IterationEndNode from './node/IterationEndNode.vue'
import HumanNode from './node/HumanNode.vue'
import ConnectionLine from './ConnectionLine.vue'
import NodeDataDrawer from './NodeDataDrawer.vue'
import ToolsWrap from './ToolsWrap.vue'
import NodeResultLayer from './NodeResultLayer.vue'
import { WORKFLOW_NODE_DND_TYPE, findCatalogItem } from './utils/node-catalog'
import { toWorkflowSchema } from './utils/workflow-schema'
import {
  CANVAS_EDGE_STYLE,
  INNER_EDGE_Z_INDEX,
  fromWorkflowSchema,
  toCanvasSnapshot,
} from './utils/workflow-canvas'
import { buildNodeStatuses, collectNodeOutputs } from './utils/execution-result'
import { pollWorkflowProcess } from '@/utils/workflow-poll'
import {
  applyMessageEvent,
  buildCreatedProcess,
  isTerminalStatus,
  sortLiveRecords,
  withLiveRecord,
} from '@/utils/workflow-run-state'
import {
  getWorkflowDetail,
  listWorkflows,
  resumeWorkflow,
  runWorkflowStream,
  saveWorkflow,
  WORKFLOW_EXE_STATUS,
  WORKFLOW_STREAM_EVENT,
} from '@/api/workflow'


const {
  setNodes,
  setEdges,
  addNodes,
  addEdges,
  removeNodes,
  updateNode,
  getNodes,
  getEdges,
  getIntersectingNodes,
  screenToFlowCoordinate,
  fitView,
  zoomIn,
  zoomOut,
  zoomTo,
  onNodesChange,
  onNodeDragStart,
  onNodeDrag,
  onNodeDragStop,
  onNodeClick,
  onPaneClick,
} = useVueFlow()

/** 开始 / 结束节点的固定 id：画布上是唯一实例，后端建图也按这两个语义入口认 */
const START_NODE_ID = '100001'
const END_NODE_ID = '__END__'

/** 内层点阵画布的四周内缩：.node-body 4px + .node-canvas 4px */
const ITERATION_CANVAS_INSET = 8
/**
 * 节点盒子到点阵画布之间还有两层 1px 描边：.node-wrapper 的透明描边 + .node-card 的灰边框。
 * 算可移动范围时要一并扣掉，否则子节点能压到点阵画布外面（上/左/下各多出 2px）。
 */
const ITERATION_BORDER_INSET = 2
/** 迭代节点头部高度：.node-header 12 + 24 + 12（上下内边距 12px） */
const ITERATION_HEADER_HEIGHT = 48
/** 迭代节点默认尺寸（与 IterationNode.vue 的 DEFAULT_SIZE 保持一致） */
const ITERATION_DEFAULT_SIZE = { width: 480, height: 280 }
/** 迭代起止节点尺寸 */
const ITERATION_BOUNDARY_SIZE = 52
/** 起止节点与子节点之间的间距 */
const ITERATION_BOUNDARY_GAP = 12
/** 起点节点在迭代节点内部的落点（纵向 48px header + 8px 画布内缩） */
const ITERATION_START_POSITION = { x: ITERATION_CANVAS_INSET, y: 56 }
/**
 * 迭代节点内部子节点的落点：纵向贴画布内容区顶部，
 * 横向排在起点节点之后（画布内缩 8 + 起点 52 + 间距 40）。
 */
const ITERATION_CHILD_ORIGIN = { x: 100, y: 56 }
/**
 * 迭代节点内部子节点的可移动范围 = 内层点阵画布，[top, right, bottom, left] 依次对应
 * IterationNode.vue 的样式：描边 2px、header 48px、.node-body 左右 4px / 下 4px、.node-card 下 4px。
 * 这里刻意「整块画布都放开」：子节点能一直拖到点阵画布右边（不会被终点节点那 72px 卡住），
 * 松手时再由 growIterationToFit 把容器撑大、让终点节点让位（见 onNodeDragStop）。
 * 用 { range: 'parent', padding } 的形式，容器尺寸变化时会自动跟随，无需再同步。
 */
const ITERATION_RIGHT_RESERVE = ITERATION_CANVAS_INSET + ITERATION_BOUNDARY_SIZE + ITERATION_BOUNDARY_GAP
const ITERATION_CANVAS_PADDING = [
  ITERATION_BORDER_INSET + ITERATION_HEADER_HEIGHT, // 上：描边 2px + header 48px
  ITERATION_BORDER_INSET + 4, // 右：描边 2px + .node-body 4px
  ITERATION_BORDER_INSET + 8, // 下：描边 2px + .node-body 4px + .node-card 4px
  ITERATION_BORDER_INSET + 4, // 左：描边 2px + .node-body 4px
]
const ITERATION_CHILD_EXTENT = {
  range: 'parent',
  padding: ITERATION_CANVAS_PADDING,
}
/** 迭代节点内部子节点的层级，需高于容器本身，避免被子节点被容器覆盖 */
const ITERATION_CHILD_Z_INDEX = 1
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
 * 画布容器数据（单向绑定）。
 *
 * 刻意用单向绑定（:nodes / :edges）而不是 v-model：vue-flow 只在
 * 「数组引用或长度变化」时把 store 同步回 props，而拖动、改 parentNode / extent / position
 * 都是原地修改、不会回写，用 v-model 会让人误以为它就是最新数据。
 *
 * 两者一律为空数组：画布内容**完全由后端数据驱动**（首屏 list + detail、打开工作流，
 * 或新建时补一对结构节点），全部经 store（setNodes / addNodes）写入；
 * 运行时读图请走 getNodes() / getEdges()（store 才是唯一数据源）。
 */
const initialNodes = ref([])
const initialEdges = ref([])

/**
 * 新建画布时的结构节点：开始 + 结束。
 *
 * 这一对在画布上是唯一实例（不可删除），后端建图也以它们为入口 / 出口，
 * 所以「新建」不是一张全空画布，而是只保留结构节点 —— 不预置任何业务数据，
 * 输入 / 输出变量与其余节点由用户自行添加或从后端加载。
 */
const createBaseNodes = () => [
  {
    id: START_NODE_ID,
    type: 'input',
    data: { label: '开始', outputs: [] },
    position: { x: 100, y: 100 },
    deletable: false,
    sourcePosition: 'right',
    targetPosition: 'left',
  },
  {
    id: END_NODE_ID,
    type: 'output',
    data: { label: '结束', outputs: [] },
    // 不要与开始节点同坐标：坐标相同会让两张卡片完全重叠，开始节点将无法点中
    position: { x: 700, y: 100 },
    deletable: false,
    sourcePosition: 'right',
    targetPosition: 'left',
  },
]

const nodeTypes = {
  start: markRaw(StartNode),
  input: markRaw(InputNode),
  output: markRaw(OutputNode),
  llm: markRaw(LlmNode),
  human: markRaw(HumanNode),
  iteration: markRaw(IterationNode),
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

/**
 * 节点数据抽屉：保存正在查看的节点 id，null 表示没有打开。
 * 抽屉内容与写回都交给 NodeDataDrawer，这里只负责「点哪个节点」。
 */
const activeNodeId = ref(null)
const drawerVisible = ref(false)

const openNodeDrawer = (nodeId) => {
  if (!nodeId) return
  activeNodeId.value = nodeId
  drawerVisible.value = true
}

/**
 * 刚刚拖拽过节点：vue-flow 的 nodeDragThreshold 默认为 1，位移超过 1px 才会派发
 * nodeDragStart，而拖拽松手后浏览器仍会在节点上补一个 click（同一次交互里，
 * 晚于 pointerup 触发）。用标记把这次 click 吃掉，避免「拖完节点就弹出抽屉」；
 * 清标记放到 setTimeout(0)，保证一定晚于这次交互的 click。
 */
let draggingNode = false

/** 点击节点：Ctrl / Cmd 是「取出」，普通点击打开数据抽屉 */
const handleNodeClick = (payload) => {
  if (!payload?.node) return
  if (isModifierPressed(payload.event)) {
    detachWithModifier(payload)
    return
  }
  if (draggingNode) return
  // 迭代的起止节点也允许打开：它们是「循环数组 / 中间变量 / 输出」的编辑入口，
  // 参数写在父容器上（起止节点本身不在后端图里，写自己就是死数据）
  openNodeDrawer(payload.node.id)
}

// ① Ctrl + 点击（指针几乎不动，走原生 click）；普通点击则打开抽屉
onNodeClick(handleNodeClick)
// ② Ctrl + 拖拽时按下（指针位移超过阈值，vue-flow 走拖拽流程）
onNodeDragStart((payload) => {
  modifierDetachedNodeId = null
  draggingNode = true
  detachWithModifier(payload)
})
// ③ 拖拽过程中才按下 Ctrl
onNodeDrag(detachWithModifier)

// 点击画布空白处关闭抽屉
onPaneClick(() => {
  drawerVisible.value = false
})

// 拖拽结束：与某个迭代节点相交则自动挂入其内部
onNodeDragStop(({ node }) => {
  setTimeout(() => {
    draggingNode = false
  }, 0)
  if (!node || isIterationNode(node)) return
  // 刚刚被 Ctrl / Cmd 取出的节点：本次交互是“取出”，不再自动挂回
  if (node.id === modifierDetachedNodeId) {
    modifierDetachedNodeId = null
    return
  }
  const parent = node.parentNode ? getNodes.value.find((item) => item.id === node.parentNode) : null
  // 已在迭代节点内部：子节点能拖到整块点阵画布（包括终点节点所在的位置），
  // 松手时把容器撑到「装得下它 + 给终点节点留位」，终点节点会自动右移让开，不会压在一起
  if (parent) {
    if (isIterationNode(parent)) {
      const childPosition = node.computedPosition ?? node.position
      const parentPosition = parent.computedPosition ?? parent.position
      growIterationToFit(
        parent,
        childPosition.y - parentPosition.y + (node.dimensions?.height || ITERATION_CHILD_HEIGHT),
        childPosition.x - parentPosition.x + (node.dimensions?.width || 0),
      )
    }
    return
  }
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
      ...CANVAS_EDGE_STYLE,
      ...(isInnerEdge ? { zIndex: INNER_EDGE_Z_INDEX } : {}),
    },
  ])
}

/* ===== 工具栏：从节点库拖拽新增节点 ===== */

/** 新节点 id：类型 + 时间戳 + 随机串，保证画布内唯一 */
const createNodeId = (kind) => `${kind}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`

/** 大模型节点：data 结构（变量声明数组 + 输出）与数据抽屉里的可编辑字段保持一致 */
const createLlmNode = (id, position) => ({
  id,
  type: 'llm',
  data: {
    label: '大模型',
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
  position,
})

/** 迭代节点名称：取画布上「迭代 N」的最大序号 + 1，避免与既有节点重名 */
const nextIterationLabel = () => {
  const numbers = getNodes.value
    .map((node) => /^迭代\s*(\d+)$/.exec(node.data?.label ?? '')?.[1])
    .filter(Boolean)
    .map(Number)
  return `迭代 ${numbers.length ? Math.max(...numbers) + 1 : 1}`
}

/**
 * 迭代节点：容器 + 自带的起止节点（与后端加载进来的迭代节点用同一套结构，
 * 起止节点必须一起写入，否则容器内没有出入口，后续也无法级联清理）。
 */
const createIterationNodes = (position) => {
  const id = createNodeId('iteration')
  return [
    {
      id,
      type: 'iteration',
      data: { label: nextIterationLabel() },
      position,
      width: ITERATION_DEFAULT_SIZE.width,
      height: ITERATION_DEFAULT_SIZE.height,
    },
    ...createIterationBoundaryNodes(id),
  ]
}

/**
 * 人工确认节点：试运行执行到此**暂停**，由节点正下方的确认卡片勾选清单，提交后同一条事件流继续。
 * 输出名与后端 HumanConfirmNodeAction 的兜底一致（confirmedPlan）；
 * 待确认清单的输入引用由序列化时按「上游大模型节点」自动生成（见 utils/workflow-schema.js）。
 */
const createHumanNode = (id, position) => ({
  id,
  type: 'human',
  data: {
    label: '人工确认',
    outputs: [
      {
        name: 'confirmedPlan',
        type: 'list',
        description: '用户确认后的清单',
        schema: { type: 'string' },
      },
    ],
  },
  position,
})

/** 按节点类型构建待写入 store 的节点（迭代节点会同时返回自带的起止节点） */
const buildNodesByKind = (kind, position) => {
  if (kind === 'llm') return [createLlmNode(createNodeId('llm'), position)]
  if (kind === 'iteration') return createIterationNodes(position)
  if (kind === 'human') return [createHumanNode(createNodeId('human'), position)]
  return []
}

/** 落点换算：屏幕坐标 → 画布坐标，指针位置即新节点左上角 */
const resolveDropPosition = (event) => {
  const point = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  return { x: Math.round(point.x), y: Math.round(point.y) }
}

/** 拖拽悬停：不 preventDefault 的话浏览器按默认行为处理，不会派发 drop */
const handleDragOver = (event) => {
  if (!event.dataTransfer) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

/** 放下节点库卡片：校验拖拽类型 → 换算落点 → 成批写入 store */
const handleDrop = (event) => {
  const item = findCatalogItem(event.dataTransfer?.getData(WORKFLOW_NODE_DND_TYPE))
  if (!item?.addable) return
  event.preventDefault()
  const nodes = buildNodesByKind(item.kind, resolveDropPosition(event))
  if (!nodes.length) return
  addNodes(nodes)
  ElMessage.success(`已添加「${item.label}」节点`)
}

/* ===== 工具栏：试运行 ===== */

/** HTML 转义：节点标题来自用户输入，拼进提示框前必须先转义 */
const escapeHtml = (value) =>
  String(value ?? '').replace(
    /[&<>"']/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char],
  )

/**
 * 把「输入引用必须来自上游」的校验结果格式化成提示文案。
 *
 * 只校验输入侧：引用的变量必须由**一定能先于本节点执行**的节点声明，否则运行时必然取到空值
 * （典型表现是清单为空、批改记录解析不出分数）。这项校验只在前端做，后端不重复实现。
 */
const formatReferenceIssues = (issues) => {
  const MAX_LINES = 6
  const lines = issues.slice(0, MAX_LINES).map((issue) => {
    const reason =
      issue.reason === 'not-found'
        ? `引用了不存在的节点「${escapeHtml(issue.refNodeId)}」`
        : `引用了非上游节点「${escapeHtml(issue.refNodeTitle || issue.refNodeId)}」，运行时会取到空值`
    return `<li>「${escapeHtml(issue.nodeTitle)}」的输入「${escapeHtml(issue.paramName)}」${reason}</li>`
  })
  if (issues.length > MAX_LINES) {
    lines.push(`<li>…其余 ${issues.length - MAX_LINES} 处略</li>`)
  }
  return [
    '<p>以下节点的输入引用了上游之外的变量，已阻止本次试运行：</p>',
    `<ul style="margin:8px 0 0;padding-left:18px;line-height:1.7">${lines.join('')}</ul>`,
    '<p style="margin:10px 0 0">请在对应节点的「输入变量 → 取值」里重新选上游变量，或先把上游节点连到本节点。</p>',
  ].join('')
}

/** 试运行是否进行中（已提交但未到终态） */
const runLoading = ref(false)
/** 当前执行状态快照（按 SSE 事件增量拼装为 WorkflowProcessRespVO 同构对象） */
const runResult = ref(null)
/** 请求失败时的错误信息（由画布顶部的状态条展示） */
const runError = ref('')
/** 本次执行会话 id（SSE 的 `created` 事件下发） */
const runExecuteId = ref('')
/** SSE 流句柄（`{ abort }`）；`null` 表示当前未订阅实时事件 */
let runStream = null
/** 当前轮询句柄（SSE 断开后的兜底通道） */
let runPoll = null
/** 是否已降级为轮询（仅用于状态条文案） */
const runPollingMode = ref(false)
/** 循环内逐条回传的批改结果，按序号存放（并发迭代时可能乱序到达） */
const runLiveRecords = ref({})
/** 循环总条目数（来自流式记录的 `total`，用于状态条进度文案） */
const runLoopTotal = ref(0)

/** 业务结果：执行中为 null，终态由后端返回 */
const processResult = computed(() => runResult.value?.result || null)

/** 已回传的批改份数（终态取后端结果，执行中取流式增量） */
const gradedCount = computed(() =>
  processResult.value?.records?.length || sortLiveRecords(runLiveRecords.value).length,
)

/** 停止 SSE 流：重新试运行 / 打开或新建工作流 / 页面卸载时必须调用 */
const stopRunStream = () => {
  runStream?.abort()
  runStream = null
}

/** 停止试运行轮询：重新试运行 / 打开或新建工作流 / 页面卸载时必须调用 */
const stopRunPoll = () => {
  runPoll?.stop()
  runPoll = null
}

/** 清空试运行结果、订阅与确认态 */
const resetRunState = () => {
  stopRunStream()
  stopRunPoll()
  runLoading.value = false
  runError.value = ''
  runResult.value = null
  runExecuteId.value = ''
  runPollingMode.value = false
  runLiveRecords.value = {}
  runLoopTotal.value = 0
  closeConfirm()
}

/* ===== 试运行：SSE 事件订阅 ===== */

/** 用补丁更新执行状态快照 */
const patchRunResult = (patch) => {
  runResult.value = { ...runResult.value, ...patch }
}

/** 落地一份完整状态响应（`completed` 事件 / 轮询结果 / 断线重连后使用） */
const applyRunProcess = (process) => {
  if (!process) return
  runResult.value = process
  if (process.exeHistoryStatus === WORKFLOW_EXE_STATUS.WAITING && process.pendingConfirm) {
    openConfirm(process.pendingConfirm)
  }
}

/** `message` 事件分发：把事件累积成状态快照（见 utils/workflow-run-state） */
const applyRunMessage = (message) => {
  const { process, pendingConfirm, liveRecord } = applyMessageEvent(runResult.value, message)
  if (process !== runResult.value) {
    runResult.value = process
  }
  if (liveRecord) {
    runLiveRecords.value = withLiveRecord(runLiveRecords.value, liveRecord.index, liveRecord.record)
    if (liveRecord.total) {
      runLoopTotal.value = liveRecord.total
    }
  }
  if (pendingConfirm) {
    openConfirm(pendingConfirm)
  }
}

/** SSE 事件入口 */
const handleRunEvent = (event, data) => {
  switch (event) {
    case WORKFLOW_STREAM_EVENT.CREATED:
      runExecuteId.value = data?.executeId || ''
      runResult.value = buildCreatedProcess(data)
      break
    case WORKFLOW_STREAM_EVENT.MESSAGE:
      applyRunMessage(data)
      break
    case WORKFLOW_STREAM_EVENT.COMPLETED:
      applyRunProcess(data)
      runLoading.value = false
      ElMessage.success(`执行完成，共执行 ${data?.result?.nodeTrace?.length ?? 0} 个节点`)
      break
    case WORKFLOW_STREAM_EVENT.ERROR:
      runLoading.value = false
      runError.value = data?.message || '工作流执行失败'
      break
    default:
      break
  }
}

/** 降级为轮询：SSE 断开 / 失败后仍能取回后续进度与终态 */
const switchRunToPoll = (tip) => {
  if (!runExecuteId.value || runPoll) return
  stopRunStream()
  runPollingMode.value = true
  ElMessage.warning(tip)
  runPoll = pollWorkflowProcess(runExecuteId.value, {
    onProcess: applyRunProcess,
    onWaiting: (process) => applyRunProcess(process),
    onFinish: (process) => {
      applyRunProcess(process)
      runLoading.value = false
      runPoll = null
      if (process?.exeHistoryStatus === WORKFLOW_EXE_STATUS.FINISHED) {
        ElMessage.success(`执行完成，共执行 ${process?.result?.nodeTrace?.length ?? 0} 个节点`)
      } else {
        runError.value = process?.message || '工作流执行失败'
      }
    },
    onError: (error) => {
      runLoading.value = false
      runPoll = null
      runError.value = error?.message || '查询执行状态失败'
    },
  })
}

/**
 * 试运行：把当前画布序列化成后端 schema，提交给 `POST /workflow/run` 并订阅 SSE 事件流
 * （节点状态与循环内逐份批改结果实时到达）；执行到「人工确认」节点时在节点下方展示确认卡片，
 * 提交后同一条流继续推送。连接断开时自动降级为每 2 秒轮询 `GET /workflow/get_process`。
 *
 * 画布数据一律取自 store（getNodes / getEdges），与页面上的单向绑定无关。
 */
const handleRun = async () => {
  if (runLoading.value) return
  stopRunStream()
  stopRunPoll()
  runLoading.value = true
  runResult.value = null
  runError.value = ''
  runExecuteId.value = ''
  runPollingMode.value = false
  runLiveRecords.value = {}
  runLoopTotal.value = 0
  closeConfirm()
  let payload
  try {
    const { nodes, edges, versions, skipped, autoLinked, referenceIssues } = toWorkflowSchema(
      getNodes.value,
      getEdges.value,
    )
    if (!nodes.length) {
      throw new Error('画布上没有可执行的节点，请先添加节点再试运行')
    }
    // 引用校验：节点输入引用的变量必须来自上游，否则运行时必然取到空值（前端拦在请求之前）
    if (referenceIssues.length) {
      await ElMessageBox.alert(formatReferenceIssues(referenceIssues), '存在非法引用，已阻止试运行', {
        type: 'warning',
        dangerouslyUseHTMLString: true,
        confirmButtonText: '知道了',
      })
      runLoading.value = false
      return
    }
    if (skipped.length) {
      ElMessage.warning(
        `已跳过 ${skipped.length} 个不可执行节点：${skipped
          .map((item) => item.label ?? item.id)
          .join('、')}`,
      )
    }
    if (autoLinked) {
      ElMessage.info('画布未连线，已按节点位置自动串接执行顺序')
    }
    payload = {
      schemaJson: JSON.stringify({ nodes, edges, versions }),
      // 已保存的画布把工作流定义 id 一并回传，让响应里的 workFlowId 有值
      workflowId: currentWorkflowId.value ?? undefined,
    }
  } catch (error) {
    runLoading.value = false
    runError.value = error?.message || '试运行失败，请确认后端服务已启动'
    console.error('[handleRun][试运行失败]', error)
    return
  }
  runStream = runWorkflowStream(payload, {
    onEvent: handleRunEvent,
    onError: (error) => {
      if (!runExecuteId.value) {
        // 提交阶段就被拦下（画布为空 / 非法）：此时尚无会话，直接展示错误
        runLoading.value = false
        runError.value = error?.message || '试运行失败，请确认后端服务已启动'
        console.error('[handleRun][提交失败]', error)
        return
      }
      switchRunToPoll('实时推送失败，已切换为轮询')
    },
    onDone: (reason) => {
      // 等待用户输入时服务端不会发 done（连接保持打开）；连接意外结束则降级轮询
      if (reason === 'closed' && runLoading.value
        && !isTerminalStatus(runResult.value?.exeHistoryStatus)) {
        switchRunToPoll('实时连接已断开，已切换为轮询')
      }
    },
  })
}

/* ===== 试运行：人工确认清单（卡片挂在「人工确认」节点正下方，见 NodeResultLayer） ===== */

/** 等待用户输入的人工确认数据：{ nodeId, nodeName, plan, defaultPlan }；null 表示当前无暂停节点 */
const runPendingConfirm = ref(null)
/** 确认清单已勾选的文件路径（受控传给 NodeResultLayer） */
const confirmSelected = ref([])
/** 是否正在提交确认清单 */
const confirmSubmitting = ref(false)

/** 展示确认卡片：默认勾选后端给出的 defaultPlan（无默认值时留空，由用户自行勾选） */
const openConfirm = (pending) => {
  if (!pending) return
  const paths = (Array.isArray(pending.plan) ? pending.plan : [])
    .map((item) => item.filePath)
    .filter(Boolean)
  const defaults = Array.isArray(pending.defaultPlan) ? pending.defaultPlan : []
  runPendingConfirm.value = pending
  confirmSelected.value = paths.filter((path) => defaults.includes(path))
}

/** 收起确认卡片（提交成功 / 停止接收后调用；再次中断时会重新打开） */
const closeConfirm = () => {
  runPendingConfirm.value = null
  confirmSelected.value = []
}

/** 提交确认清单：恢复后的事件继续从**原事件流**推来 */
const submitConfirm = async () => {
  const sessionId = runResult.value?.executeId || runExecuteId.value
  if (!sessionId) return
  if (!confirmSelected.value.length) {
    ElMessage.warning('请至少勾选一份需要批改的作业')
    return
  }
  confirmSubmitting.value = true
  try {
    await resumeWorkflow({ executeId: sessionId, confirmedPlan: [...confirmSelected.value] })
    closeConfirm()
    patchRunResult({
      exeHistoryStatus: WORKFLOW_EXE_STATUS.RUNNING,
      message: '已提交确认清单，正在执行…',
    })
    ElMessage.success('已提交确认清单，继续执行')
  } catch (error) {
    ElMessage.error(error?.message || '提交确认清单失败')
  } finally {
    confirmSubmitting.value = false
  }
}

/** 停止接收进度：后端无取消接口，仅停止本地订阅（会话仍在等待，超时后自动失效） */
const cancelConfirm = () => {
  closeConfirm()
  stopRunStream()
  stopRunPoll()
  runLoading.value = false
  runError.value = '已停止接收进度（后端会话仍在等待确认，超时后自动失效）'
  ElMessage.info('已停止接收进度')
}

/* ===== 工具栏：视图控制 ===== */

/** 视图过渡时长，与全局动效刻度（--tb-dur-2）保持一致 */
const VIEW_TRANSITION = { duration: 200 }

const handleFitView = () => fitView({ padding: 0.2, ...VIEW_TRANSITION })

/**
 * 等节点渲染并测量完尺寸后再适应视图。
 * vue-flow 要在节点挂载并测出尺寸后才会把位置写进 store，只 await nextTick() 会早于测量，
 * fitView 算出的包围盒不完整（视图只盖住部分节点），因此再等两帧。
 */
const fitViewAfterRender = async () => {
  await nextTick()
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  await fitView({ padding: 0.2, ...VIEW_TRANSITION })
}
const handleZoomIn = () => zoomIn(VIEW_TRANSITION)
const handleZoomOut = () => zoomOut(VIEW_TRANSITION)
const handleResetZoom = () => zoomTo(1, VIEW_TRANSITION)



/* ===== 工具栏：保存 / 打开工作流（落库到 workflow 表） ===== */

/** 当前展开的浮层：nodes / save / open / 空（互斥，由本页统一持有） */
const activePanel = ref('')

/** 当前画布对应的工作流 id（新建后回填，再次保存即更新同一条） */
const currentWorkflowId = ref(null)
/** 保存浮层中的名称输入 */
const workflowName = ref('')
/** 保存中 */
const saving = ref(false)
/** 打开浮层中的工作流列表 */
const workflowList = ref([])

const handleTogglePanel = (panel) => {
  activePanel.value = activePanel.value === panel ? '' : panel
  if (activePanel.value === 'open') {
    refreshWorkflowList()
  }
}

const refreshWorkflowList = async () => {
  try {
    workflowList.value = await listWorkflows()
  } catch (error) {
    ElMessage.error(error?.message || '获取工作流列表失败')
  }
}

/**
 * 保存当前画布：存「画布渲染格式」而不是执行格式——
 * 坐标、parentNode 与 data.backendInputs 都要留住，否则重开后试运行到人工确认节点时拿不到待确认清单。
 */
const handleSaveWorkflow = async () => {
  const snapshot = toCanvasSnapshot(getNodes.value, getEdges.value)
  if (!snapshot.nodes.length) {
    ElMessage.warning('画布为空，没有可保存的内容')
    return
  }
  saving.value = true
  try {
    const id = await saveWorkflow({
      id: currentWorkflowId.value ?? undefined,
      name: workflowName.value,
      desc: '',
      schemaJson: JSON.stringify(snapshot),
      status: 0,
    })
    currentWorkflowId.value = id
    ElMessage.success(`已保存工作流「${workflowName.value || id}」`)
    activePanel.value = ''
    // 列表按更新时间倒序，保存后同步一次，免得「打开」面板里还是旧数据
    refreshWorkflowList()
  } catch (error) {
    ElMessage.error(error?.message || '保存工作流失败')
  } finally {
    saving.value = false
  }
}

/**
 * 打开指定工作流：读回快照并整体替换画布（列表点击与首屏加载共用同一套落点）。
 *
 * @param {string} id 工作流 id
 * @param {{ silent?: boolean }} [options] silent = 首屏静默加载：不弹成功提示、不收起浮层，
 *   失败时抛给调用方，由首屏统一渲染错误提示与重试入口
 */
const handleOpenWorkflow = async (id, options = {}) => {
  const silent = Boolean(options.silent)
  try {
    const detail = await getWorkflowDetail(id)
    const rawSchema = detail?.workflow?.schema_json
    const schema = typeof rawSchema === 'string' ? JSON.parse(rawSchema) : rawSchema
    if (!Array.isArray(schema?.nodes) || !schema.nodes.length) {
      throw new Error('该工作流的画布为空')
    }
    const { nodes, edges, skipped } = fromWorkflowSchema(schema)
    resetRunState()
    currentWorkflowId.value = id
    workflowName.value = detail?.workflow?.name ?? ''
    setNodes(nodes)
    setEdges(edges)
    drawerVisible.value = false
    await fitViewAfterRender()
    if (skipped.length) {
      ElMessage.warning(`已跳过 ${skipped.length} 个不支持的节点`)
    }
    if (!silent) {
      ElMessage.success(`已打开「${detail?.workflow?.name ?? id}」`)
      activePanel.value = ''
    }
  } catch (error) {
    if (silent) throw error
    ElMessage.error(error?.message || '打开工作流失败')
  }
}

/* ===== 首屏：画布数据一律从后端加载 ===== */

/** 首屏是否正在拉取工作流 */
const canvasLoading = ref(true)
/** 首屏加载失败信息（非空时画布上给出重试入口） */
const canvasError = ref('')
/** 错误提示是否已被手动关闭 */
const canvasErrorDismissed = ref(false)

/**
 * 新建画布：清空内容并回到「开始 + 结束」这对结构节点。
 * 只重置前端状态、不落库 —— 保存时才生成一条新的工作流记录。
 */
const resetCanvasToBase = async () => {
  resetRunState()
  currentWorkflowId.value = null
  workflowName.value = ''
  drawerVisible.value = false
  setNodes(createBaseNodes())
  setEdges([])
  await fitViewAfterRender()
}

/** 工具栏：新建工作流 */
const handleNewWorkflow = async () => {
  activePanel.value = ''
  await resetCanvasToBase()
  ElMessage.info('已新建空白画布，保存后会新增一条工作流')
}

/**
 * 首屏加载：取后端最近更新的一条工作流渲染到画布。
 *
 * 列表为空不算错误——停在「新建」状态，用户可直接开始编排；
 * 请求失败时保留一张可用的空画布，并把错误摆在画布上供重试，不阻断后续操作。
 */
const loadInitialWorkflow = async () => {
  canvasLoading.value = true
  canvasError.value = ''
  canvasErrorDismissed.value = false
  try {
    const list = await listWorkflows()
    workflowList.value = Array.isArray(list) ? list : []
    const latest = workflowList.value[0]
    if (!latest?.workflow_id) {
      await resetCanvasToBase()
      return
    }
    await handleOpenWorkflow(latest.workflow_id, { silent: true })
  } catch (error) {
    canvasError.value = error?.message || '加载工作流失败，请确认后端服务已启动'
    console.error('[loadInitialWorkflow][加载工作流失败]', error)
    await resetCanvasToBase()
  } finally {
    canvasLoading.value = false
  }
}

onMounted(loadInitialWorkflow)

/** 列表中的更新时间：后端返回秒级时间戳 */
const formatUpdateTime = (seconds) => {
  if (!seconds) return ''
  const date = new Date(seconds * 1000)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}

/* ===== 画布：执行结果直接落到节点上 ===== */

/** 是否在画布上展示执行结果（工具栏可切换） */
const showResults = ref(true)

/** 画布顶部状态条：执行中 / 失败这类「持续状态」的展示位 */
const statusBarDismissed = ref(false)

watch([runError, runExecuteId, () => runResult.value?.exeHistoryStatus], () => {
  statusBarDismissed.value = false
})

const statusBar = computed(() => {
  if (runPendingConfirm.value) {
    const nodeName = runPendingConfirm.value.nodeName || '人工确认'
    return { tone: 'waiting', text: `已暂停在「${nodeName}」节点，请在节点下方的卡片中勾选并提交` }
  }
  if (runLoading.value) {
    const progress = gradedCount.value
      ? ` · 已批改 ${gradedCount.value}${runLoopTotal.value ? `/${runLoopTotal.value}` : ''}`
      : ''
    return {
      tone: 'running',
      text: runExecuteId.value
        ? `正在执行工作流（${runExecuteId.value}）${runPollingMode.value ? '（轮询兜底）' : ''}${progress}…`
        : '正在提交执行任务…',
    }
  }
  if (runError.value) return { tone: 'error', text: runError.value }
  if (runResult.value?.exeHistoryStatus === WORKFLOW_EXE_STATUS.FAILED) {
    return { tone: 'error', text: runResult.value?.message || '工作流执行失败' }
  }
  return null
})

/** 状态快照 → 各节点自己的输出（后端 state 的键为 `{nodeId}.{outputName}`） */
const nodeOutputs = computed(() => collectNodeOutputs(processResult.value?.state))

/** 后端 nodeResults → 各节点状态（执行中 / 成功 / 失败 / 未执行） */
const nodeStatuses = computed(() =>
  buildNodeStatuses(runResult.value?.nodeResults, getNodes.value, {
    finished: !runLoading.value && Boolean(runResult.value),
  }),
)

const handleToggleResults = () => {
  showResults.value = !showResults.value
}

onBeforeUnmount(() => {
  stopRunStream()
  stopRunPoll()
})

</script>

<template>
<!-- 外壳：为工具栏与浮层提供定位上下文，并承接节点库卡片的 drop -->
<div class="workflow-shell" @dragover="handleDragOver" @drop="handleDrop">
  <VueFlow
    :nodes="initialNodes"
    :edges="initialEdges"
    :node-types="nodeTypes"
    @connect="handleConnect"
  >
    <!-- 自定义连线预览：拖拽连线时跟随指针的紫色虚线 + 终点圆点 -->
    <template #connection-line="connectionLineProps">
      <ConnectionLine v-bind="connectionLineProps" />
    </template>
  </VueFlow>

  <!--
    节点执行结果：状态描边 + 节点下方输出 + 人工确认卡片（全部在画布上完成，无弹窗）。
    人工确认卡片必须始终可见（否则暂停时用户找不到提交入口），因此它不随「执行结果」开关隐藏。
  -->
  <NodeResultLayer
    v-if="showResults || runPendingConfirm"
    :outputs="showResults ? nodeOutputs : {}"
    :statuses="showResults ? nodeStatuses : {}"
    :pending="runPendingConfirm"
    v-model:selected="confirmSelected"
    :submitting="confirmSubmitting"
    @submit="submitConfirm"
    @cancel="cancelConfirm"
  />

  <!-- 执行状态条：执行中 / 等待确认 / 失败这类持续状态的展示位 -->
  <div v-if="statusBar && !statusBarDismissed" class="status-bar" :class="`is-${statusBar.tone}`">
    <el-icon v-if="statusBar.tone === 'running'" class="status-bar__icon is-spin">
      <Loading />
    </el-icon>
    <el-icon v-else-if="statusBar.tone === 'waiting'" class="status-bar__icon">
      <Warning />
    </el-icon>
    <span class="status-bar__text">{{ statusBar.text }}</span>
    <button
      v-if="statusBar.tone === 'error'"
      class="status-bar__close"
      type="button"
      aria-label="关闭提示"
      @click="statusBarDismissed = true"
    >
      <el-icon><Close /></el-icon>
    </button>
  </div>

  <!-- 首屏加载：画布数据全部来自后端，加载期间给出明确反馈 -->
  <div v-if="canvasLoading" class="canvas-state">
    <el-icon class="canvas-state__icon is-spin"><Loading /></el-icon>
    <span class="canvas-state__text">正在加载工作流…</span>
  </div>

  <!-- 首屏加载失败：画布依然可用，给出重试与关闭 -->
  <div
    v-else-if="canvasError && !canvasErrorDismissed"
    class="canvas-state canvas-state--error"
    role="alert"
  >
    <span class="canvas-state__text">{{ canvasError }}</span>
    <el-button size="small" :icon="RefreshRight" @click="loadInitialWorkflow">重试</el-button>
    <button
      class="canvas-state__close"
      type="button"
      aria-label="关闭提示"
      @click="canvasErrorDismissed = true"
    >
      <el-icon><Close /></el-icon>
    </button>
  </div>

  <!-- 左侧工具栏：添加节点（拖拽入画布）/ 试运行 / 新建工作流 / 视图控制 -->
  <ToolsWrap
    :running="runLoading"
    :results-visible="showResults"
    :active-panel="activePanel"
    @run="handleRun"
    @new-workflow="handleNewWorkflow"
    @toggle-results="handleToggleResults"
    @toggle-panel="handleTogglePanel"
    @fit-view="handleFitView"
    @zoom-in="handleZoomIn"
    @zoom-out="handleZoomOut"
    @reset-zoom="handleResetZoom"
  />

  <!-- 保存工作流：画布内联浮层，不使用弹窗 -->
  <section v-show="activePanel === 'save'" class="wf-panel" aria-label="保存工作流">
    <header class="wf-panel__head">
      <span class="wf-panel__title">保存工作流</span>
      <button
        class="wf-panel__close"
        type="button"
        aria-label="收起保存面板"
        @click="handleTogglePanel('save')"
      >
        <el-icon><Close /></el-icon>
      </button>
    </header>
    <input
      v-model="workflowName"
      class="wf-panel__input"
      type="text"
      maxlength="100"
      placeholder="工作流名称"
      @keydown.enter="handleSaveWorkflow"
    />
    <p class="wf-panel__hint">
      {{ currentWorkflowId ? `将更新工作流 ${currentWorkflowId}` : '首次保存会新建一条工作流' }}
    </p>
    <div class="wf-panel__actions">
      <el-button size="small" type="primary" :loading="saving" @click="handleSaveWorkflow">
        保存
      </el-button>
    </div>
  </section>

  <!-- 打开工作流：列表浮层 -->
  <section v-show="activePanel === 'open'" class="wf-panel" aria-label="打开工作流">
    <header class="wf-panel__head">
      <span class="wf-panel__title">打开工作流</span>
      <button
        class="wf-panel__close"
        type="button"
        aria-label="收起打开面板"
        @click="handleTogglePanel('open')"
      >
        <el-icon><Close /></el-icon>
      </button>
    </header>
    <div v-if="workflowList.length" class="wf-panel__list">
      <button
        v-for="item in workflowList"
        :key="item.workflow_id"
        class="wf-panel__item"
        type="button"
        @click="handleOpenWorkflow(item.workflow_id)"
      >
        <span class="wf-panel__name" :title="item.name">{{ item.name }}</span>
        <span class="wf-panel__time">{{ formatUpdateTime(item.update_time) }}</span>
      </button>
    </div>
    <p v-else class="wf-panel__hint">还没有保存过工作流：先在画布上编排，再点「保存」。</p>
  </section>

  <!-- 节点数据抽屉：点击节点打开，编辑名称 / 坐标 / 变量，并写回 Vue Flow store -->
  <NodeDataDrawer
    v-model:visible="drawerVisible"
    :node-id="activeNodeId"
    @select="openNodeDrawer"
  />

</div>
</template>



<style scoped>
/*
 * 画布外壳：为左侧工具栏（ToolsWrap）与节点库浮层提供定位上下文，
 * 同时承接从节点库拖出的卡片 drop。
 * 高度取 100%：父级 .index-main（flex: 1，画布路由下为 --flush）已经确定高度。
 */
.workflow-shell {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/*
 * 首屏状态层：画布数据一律来自后端，加载中 / 加载失败时在画布顶部居中反馈。
 * 与下面的 .status-bar 同风格，一眼可辨是「画布级」提示而非节点内容。
 */
.canvas-state {
  position: absolute;
  top: 16px;
  left: 50%;
  z-index: 10;
  display: flex;
  max-width: min(560px, calc(100% - 160px));
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  transform: translateX(-50%);
}

.canvas-state--error {
  border-color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

.canvas-state__icon {
  flex-shrink: 0;
  color: var(--tb-primary);
  font-size: 16px;
}

.canvas-state .is-spin {
  animation: workflow-spin 900ms linear infinite;
}

.canvas-state__text {
  min-width: 0;
  font-size: var(--tb-text-xs);
  color: var(--tb-text-primary);
  word-break: break-word;
}

.canvas-state--error .canvas-state__text {
  color: var(--tb-danger);
}

.canvas-state__close {
  display: flex;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-secondary);
  cursor: pointer;
}

.canvas-state__close:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

/*
 * 执行状态条：画布顶部居中，用于展示执行中 / 失败这类持续状态。
 * 用浮条而不是弹窗，避免遮挡画布上的节点结果。
 */
.status-bar {
  position: absolute;
  top: 16px;
  left: 50%;
  z-index: 11;
  display: flex;
  max-width: min(560px, calc(100% - 160px));
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  transform: translateX(-50%);
}

.status-bar.is-running {
  border-color: var(--tb-primary-border);
}

.status-bar.is-error {
  border-color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

.status-bar.is-waiting {
  border-color: var(--tb-warning);
  background: var(--tb-warning-soft);
}



.status-bar__icon {
  flex-shrink: 0;
  color: var(--tb-primary);
  font-size: 16px;
}

.status-bar__icon.is-spin {
  animation: workflow-spin 900ms linear infinite;
}

.status-bar__text {
  min-width: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
  word-break: break-word;
}

.status-bar__close {
  display: flex;
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-secondary);
  cursor: pointer;
}

.status-bar__close:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}



@keyframes workflow-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ===== 保存 / 打开工作流浮层：与节点库浮层同一套视觉，不使用弹窗 ===== */
.wf-panel {
  position: absolute;
  top: 16px;
  left: 72px;
  z-index: 10;
  width: 260px;
  padding: 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
}

.wf-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.wf-panel__title {
  font-size: var(--tb-text-sm);
  font-weight: 600;
  color: var(--tb-text-primary);
}

.wf-panel__close {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
}

.wf-panel__close:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

.wf-panel__input {
  width: 100%;
  height: var(--tb-control-sm);
  margin: 8px 0 4px;
  padding: 0 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-page);
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
}

.wf-panel__input:focus {
  border-color: var(--tb-primary);
  outline: none;
}

.wf-panel__hint {
  margin: 0;
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
  color: var(--tb-text-tertiary);
}

.wf-panel__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.wf-panel__list {
  display: flex;
  max-height: 260px;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
  margin-top: 8px;
}

.wf-panel__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.wf-panel__item:hover {
  background: var(--tb-bg-raised);
}

.wf-panel__name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--tb-text-xs);
  color: var(--tb-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-panel__time {
  flex-shrink: 0;
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
}

@media (max-width: 768px) {
  .wf-panel {
    width: 200px;
  }
}

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
