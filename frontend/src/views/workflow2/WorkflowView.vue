<script setup>
import { computed, h, markRaw, onMounted, onUnmounted, provide, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { MarkerType, VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import StartNode from './node/StartNode.vue'
import EndNode from './node/EndNode.vue'
import LlmNode from './node/LlmNode.vue'
import IterateNode from './node/IterateNode.vue'
import HumanNode from './node/HumanNode.vue'
import ConnectionLine from './ConnectionLine.vue'
import HumanCardDialog from './components/HumanCardDialog.vue'
import NodeDataDrawer from './components/NodeDataDrawer.vue'
import NodePanel from './components/NodePanel.vue'
import ToolsWrap from './components/ToolsWrap.vue'
import {
  createWorkflowNode,
  nextNodeId,
  nextNodePosition,
  nominalSizeOf,
  uniqueLabelOf,
} from './utils/node-factory'
import { hasNodeDragPayload, readNodeDragPayload } from './utils/node-drag'
import { NODE_META, metaOf } from './utils/node-meta'
import { runValidationErrors } from './utils/run-validation'
import {
  centeredPositionOf,
  containerAtPoint,
  descendantIdsOf,
  invalidEdgesOf,
  isCrossLevelEdge,
  nodeCenterOf,
  planMigration,
} from './utils/iteration-membership'
import {
  buildCardSubmitBody,
  buildChatBody,
  buildRunDefinition,
  buildWorkflowSchema,
  createConversation,
  getWorkflowDetail,
  saveWorkflow,
  CHAT_API,
} from '@/api/workflow'
import { readWorkflowStream } from '@/utils/workflow-sse'
import {
  ITERATE_NODE_TYPE,
  ITERATION_EDGE_STYLE,
  expandIteration,
  internalEdgePatchOf,
  iterationOwnerOf,
} from './utils/iteration'
import { ITERATION_DROP_TARGET_KEY, RUN_ACTION_KEY, RUN_STATE_KEY } from './utils/run-inject'
import { DEFAULT_FLOW_DEFINITION } from './default-flow'
import { applyRunEvent, beginRun, createRunState, finishRun, pendingCardOf, resumeRun } from './utils/run-state'

const {
  addEdges,
  addNodes,
  getEdges,
  getNodes,
  getSelectedEdges,
  getSelectedNodes,
  removeEdges,
  removeNodes,
  screenToFlowCoordinate,
  setEdges,
  setNodes,
  updateNode,
} = useVueFlow()

/**
 * 节点类型注册表：与后端 NodeActionFactory 的数字编码一一对应。
 * key 必须是字符串，节点的 `type` 也统一写字符串，如 `'4'`。
 */
const nodeTypes = {
  "1": markRaw(StartNode),
  "2": markRaw(EndNode),
  "3": markRaw(LlmNode),
  "4": markRaw(IterateNode),
  "5": markRaw(HumanNode),
}

/** 运行态：状态与产出由 run-state 归约，节点卡片按节点 id 读取 */
const runState = ref(createRunState())
provide(RUN_STATE_KEY, computed(() => runState.value))
provide(RUN_ACTION_KEY, { openCard: () => openCard() })

/** 拖拽命中的迭代容器：容器卡片据此高亮自己，让用户在松手前知道节点会落到哪 */
const iterationDropTarget = ref(null)
provide(ITERATION_DROP_TARGET_KEY, iterationDropTarget)



/** 连线统一样式：新建连线与初始化连线共用，保证两种来源的线完全一致 */
const CANVAS_EDGE_STYLE = {
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: MarkerType.Arrow, color: 'var(--tb-primary)', width: 15, height: 15 },
}

/** 外侧连接点的 handle id：所有节点卡片的入 / 出连接点都用这两个 id（迭代容器另有内侧 handle） */
const CANVAS_INPUT_HANDLE = 'in'
const CANVAS_OUTPUT_HANDLE = 'out'

const handleConnect = (connection) => {
  const { source, target } = connection

  if (source === target) {
    ElMessage.warning('不能把节点连到它自己')
    return
  }

  // 迭代体是容器的内部结构：子块只能和同一容器内的节点（含容器自身）连线。
  // 跨越边界的边在折叠成 blocks 之后，其中一端已不在同一张图里，后端建图会直接失败。
  // 判据收在 utils/iteration-membership 里：拖拽迁移节点后要用**同一套规则**清理跨层边，
  // 两处各写一份必然漂移。
  if (isCrossLevelEdge(getNodes.value, source, target)) {
    ElMessage.warning('迭代体内部的节点只能和同一迭代容器内的节点连线')
    return
  }

  /**
   * 同一对节点之间只允许一条连线：按「节点对」判重，**刻意忽略 handle**。
   *
   * 迭代体内部连线来自定义时两端不带 handle（`容器 → 子块` 就等于子图的 START → 子块），
   * 而用户在同一对节点上手连时带的是内侧 handle（`iterate-in` / `in`）。只按 handle 比较会
   * 把它们当成两条不同的边，于是画布上叠加出语义完全相同的重复连线——折叠后两条都会写进
   * 容器的 `edges` 提交给后端（子图里会对同一对节点重复 addEdge）。
   */
  const duplicated = getEdges.value.some((edge) => edge.source === source && edge.target === target)
  if (duplicated) {
    ElMessage.warning('这两个节点之间已经有连线了；点选画布上那条连线再按 Delete 即可删除')
    return
  }

  /**
   * 迭代体**内部**的新连线必须套内部边样式，关键是 `zIndex`：Vue Flow 默认把连线渲染在节点层
   * 之下，而迭代容器本身就是一张不透明卡片，漏掉 `zIndex` 的新建内部连线会被卡片底色整段盖住——
   * 表现为「连线已经建好了（store 里有）、画布上却看不见」，再连同一对节点又提示「已有连线」。
   * 顶层连线沿用画布样式，两者除 `zIndex` 外完全一致（见 utils/iteration 的 ITERATION_EDGE_STYLE）。
   */
  const edgeStyle = iterationOwnerOf(getNodes.value, source, target)
    ? ITERATION_EDGE_STYLE
    : CANVAS_EDGE_STYLE
  addEdges([{ ...connection, ...edgeStyle }])
}

/** 触发删除的按键：Windows / Linux 习惯的 Delete 与 macOS 习惯的 Backspace */
const DELETE_KEYS = new Set(['Backspace', 'Delete'])

/** 输入类元素：在这些元素里按删除键只编辑文本，不能删节点 */
const INPUT_TAGS = new Set(['INPUT', 'TEXTAREA', 'SELECT'])

const isEditingElement = (target) => {
  if (!target || typeof target !== 'object') return false
  if (INPUT_TAGS.has(target.tagName)) return true
  return target.isContentEditable === true
}

/**
 * 画布删除键（统一接管 `Backspace` / `Delete`）。
 *
 * 必须自己接管：Vue Flow 的默认删除只删「节点本身」，**不删挂在它下面的迭代体子块**
 * （`removeNodes` 的 `removeChildren` 默认 `false`）。容器被删后子块会变成悬空节点，
 * 再次运行时被当成顶层节点提交、由后端自动接 `START`/`END` 真实执行。
 * 因此画布显式关闭默认删除键（模板里的 `:delete-key-code="null"`），由这里级联删除子块。
 */
const handleDeleteKey = (event) => {
  if (!DELETE_KEYS.has(event.key)) return
  // 抽屉与节点内的输入框（如人工确认）里按删除键属于文本编辑
  if (isEditingElement(event.target)) return

  const selectedNodes = getSelectedNodes.value
  const selectedEdges = getSelectedEdges.value
  if (!selectedNodes.length && !selectedEdges.length) return

  event.preventDefault()
  if (selectedEdges.length) removeEdges(selectedEdges)
  if (selectedNodes.length) {
    // 第三个参数：连同容器内的子块一起删（Vue Flow 会递归删除全部后代并清理它们的连线）
    removeNodes(selectedNodes, true, true)
    // 抽屉正在编辑的节点（或它的子块）被一并删掉时关掉抽屉，避免抽屉继续引用一个已不存在的节点
    if (activeNodeId.value && !getNodes.value.some((node) => node.id === activeNodeId.value)) {
      drawerVisible.value = false
    }
  }
}

/**
 * 清理悬空的迭代体子块：`parentNode` 指向的容器已不存在时，把该节点一并删掉。
 *
 * 作为「任何删除路径产生的残留」的兜底（例如容器是被外部数据、旧版本代码或键盘以外的途径移除的），
 * 保证提交给后端的定义里不会出现孤儿子块。
 */
const cleanOrphanChildren = () => {
  const ids = new Set(getNodes.value.map((node) => node.id))
  const orphans = getNodes.value.filter((node) => node.parentNode && !ids.has(node.parentNode))
  if (!orphans.length) return
  removeNodes(orphans, true, true)
}

/**
 * 节点数据抽屉：点击节点即打开（卡片右上角「···」在节点内部，点击同样会冒泡到节点上），
 * 点画布空白处关闭。抽屉只编辑草稿，保存时写回 Vue Flow store，卡片随之刷新。
 */
const drawerVisible = ref(false)
const activeNodeId = ref(null)


function onNodeClick({ node }) {
  activeNodeId.value = node.id
  drawerVisible.value = true
}

/**
 * 画布上已存在的节点类型集合，喂给节点库面板做「开始 / 结束」的唯一性拦截。
 *
 * 只读每个节点的 `type`：Vue Flow 拖动节点时改的是 `position`，不会命中这里依赖的属性，
 * 因此拖拽不会带着面板一起重算（面板是纯展示块，没必要跟着画布每帧刷新）。
 */
const existingTypes = computed(() => new Set(getNodes.value.map((node) => node?.type)))

/** 画布根容器：点击添加时用它量出可视区域，把新节点摆到正中 */
const canvasRef = ref(null)

/**
 * 点击添加的默认落点：画布可视区域的正中（画布坐标，节点左上角）。
 *
 * 把画布 DOM 的中心点换算成画布坐标，再按节点标称尺寸折算成左上角——`position` 是左上角，
 * 不减半个尺寸的话节点会整体偏向右下。这样新节点无论当前视口停在哪里、缩放多少，
 * 都出现在用户正在看的那块区域中间（拖拽添加仍按指针落位，不走这里）。
 *
 * @param {string} type 节点类型编码
 * @returns {{x: number, y: number}|null} 量不出画布尺寸时返回 null，由调用方回落工厂的默认落点
 */
const viewportCenterNodePosition = (type) => {
  const canvas = canvasRef.value
  if (!canvas) return null
  const rect = canvas.getBoundingClientRect()
  if (!rect.width || !rect.height) return null

  const center = screenToFlowCoordinate({
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2,
  })
  const { width, height } = nominalSizeOf(type)
  return { x: Math.round(center.x - width / 2), y: Math.round(center.y - height / 2) }
}

/**
 * 添加节点到画布：合成节点对象后写入 Vue Flow store。
 *
 * id / 名称 / 默认结构全部由 `utils/node-factory` 的纯函数决定（口径见该模块注释），
 * 这里只负责把 store 的实时数据喂进去。类型先做一次白名单校验，避免脏类型进了画布后
 * 渲染成「未知节点」还带着一个占用的 id。
 *
 * @param {string} type 节点类型编码（面板条目来自 NODE_CATALOG，正常情况下必然合法）
 * @param {{x: number, y: number}} [position] 指定落点（拖拽添加传入指针位置）；
 *   缺省落在画布可视区域正中，量不出画布尺寸时回落工厂推算的默认落点
 */
const addNodeAt = (type, position) => {
  if (!NODE_META[type]) {
    ElMessage.warning(`不支持的节点类型：${type}`)
    return
  }
  const current = getNodes.value
  const node = createWorkflowNode(type, {
    id: nextNodeId(current),
    label: uniqueLabelOf(current, metaOf(type).name),
    position: position ?? viewportCenterNodePosition(type) ?? nextNodePosition(current),
  })
  addNodes([node])
  attachIfInIteration(node)
  ElMessage.success(`已添加「${metaOf(type).name}」节点`)
}

/**
 * 新建节点落在迭代容器的画布区上时，直接把它挂成该容器的子块。
 *
 * 复用与「拖拽已有节点入容器」完全同一套迁移计划（{@link planMigration}）：
 * 相对坐标换算、画布区夹取、`extent` / `zIndex` 的补齐都在那一处，这里不再写第二份。
 *
 * @param {object} node 刚写入 store 的节点
 */
const attachIfInIteration = (node) => {
  if (!node?.id || !node.position) return
  const target = containerAtPoint(getNodes.value, node.position)
  if (!target) return
  const plan = planMigration(getNodes.value, node.id, target)
  if (plan) updateNode(node.id, plan)
}

/** 是否正有节点从面板被拖到画布上方：仅用于画布边缘的落点反馈 */
const nodeDropActive = ref(false)

/**
 * 拖拽经过画布：只有携带节点库载荷时才声明「接受放置」。
 *
 * 必须 `preventDefault()`，否则浏览器把这里当成不可放置区域，`drop` 永远不会触发；
 * 其它来源的拖拽（文本、链接）不拦截，保持浏览器默认行为。
 */
const handleNodeDragOver = (event) => {
  if (!hasNodeDragPayload(event.dataTransfer)) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
  nodeDropActive.value = true
}

/**
 * 拖拽离开画布：收起落点反馈。
 *
 * 指针在画布内的子元素之间移动时也会触发 `dragleave`，用 `contains(relatedTarget)` 过滤掉，
 * 否则反馈会随每一次跨元素移动闪烁。
 */
const handleNodeDragLeave = (event) => {
  if (event.currentTarget?.contains(event.relatedTarget)) return
  nodeDropActive.value = false
}

/**
 * 拖拽结束（含按 Esc 取消、以及放到画布外）：无论有没有落位都收起反馈。
 *
 * `dragend` 在拖拽源（面板条目，位于 `.wf-canvas` 子树内）上触发并冒泡到这里，
 * 是唯一能覆盖「取消拖拽」这条路径的时机——只看 `dragleave` 会让虚线残留。
 */
const handleNodeDragEnd = () => {
  nodeDropActive.value = false
}

/** 松手落位：把指针位置换算成画布坐标，节点就落在用户放开指针的地方 */
const handleNodeDrop = (event) => {
  nodeDropActive.value = false
  if (!hasNodeDragPayload(event.dataTransfer)) return
  event.preventDefault()
  const type = readNodeDragPayload(event.dataTransfer)
  if (!NODE_META[type]) {
    ElMessage.warning(`不支持的节点类型：${type}`)
    return
  }
  addNodeAt(type, screenToFlowCoordinate({ x: event.clientX, y: event.clientY }))
}

/* ===== 拖拽节点改层级：进迭代体 / 移出迭代体 ===== */

/**
 * 指针在画布坐标系下的落点。
 *
 * 取**指针**而不是被拖节点的中心：节点是握着头（或任意一处）拖的，用中心判定时「想移出容器」
 * 得把节点推出去一大半才成立，体感很别扭；指针位置则与用户的意图直接对应。触屏等没有坐标的
 * 事件退回节点中心（{@link nodeCenterOf}）。
 */
const dropPointOf = (node, event) => {
  const clientX = Number(event?.clientX)
  const clientY = Number(event?.clientY)
  if (Number.isFinite(clientX) && Number.isFinite(clientY)) {
    return screenToFlowCoordinate({ x: clientX, y: clientY })
  }
  return nodeCenterOf(node)
}

/**
 * 清掉迁移之后变跨层的连线。
 *
 * 迭代体是容器的内部结构：折叠成 `blocks` 之后，跨层的边有一端不在同一张图里，后端建图会直接失败。
 * 删掉是**唯一**的可行解（保留会让「拖动一下节点」变成「运行时报错」），条数回给调用方提示用户。
 *
 * @returns {number} 被删掉的连线条数
 */
const removeCrossLevelEdges = () => {
  const invalid = invalidEdgesOf(getNodes.value, getEdges.value)
  if (invalid.length) removeEdges(invalid)
  return invalid.length
}

/**
 * 「按住 Ctrl / Meta 把迭代子块拖出容器」的判据：直接读**当前手势**的 `pointerdown` 事件。
 *
 * 时机：节点拖拽的夹取范围在 `pointerdown` 之后「超过拖拽阈值」那一刻就被 Vue Flow 快照固定
 * （`getDragItems` 取 `node.extent`），拖拽中途改 store 解不开夹取。所以必须在快照生成之前——
 * 即 `pointerdown` 捕获阶段——若节点是迭代子块且按住修饰键，就把它即时提为顶层，让这次拖拽全程自由。
 *
 * 为什么用事件自带的修饰键、而不是维护一份全局键盘状态：`pointerdown` 是原生 PointerEvent，
 * 其 `ctrlKey` / `metaKey` 就是这一下手势的真实状态；而全局 `keydown` / `keyup` 维护的状态机一定会
 * **长期失准**——`keyup` 事件在修饰键抬起时 `ctrlKey` / `metaKey` 已经是 `false`，
 * `Boolean(event.ctrlKey || event.metaKey)` 这种守卫永远进不了复位分支。于是「Ctrl 拖出去过一次」
 * 之后，任何一次落在子块上的按下都会被误判成 Ctrl 解约束：子块看着还在容器里，却不再被 `extent`
 * 夹住，不按 Ctrl 也能拖出容器（回归用例见 tests/repro-iteration-reattach.test.js）。
 *
 * 注意 Windows 上 Vue Flow 默认把 Ctrl 当多选键（`multiSelectionKeyCode` 默认 `"Control"`），
 * 画布页已把它改成 Alt（见模板），所以 Ctrl 在这里只承担「解约束」这一个语义。
 */
const isModifierEvent = (event) => Boolean(event?.ctrlKey || event?.metaKey)

/** 这次拖拽是否因按住修饰键而在 `pointerdown` 时解除了子块约束（松手结算时用） */
const detachArmed = ref(false)
/** 解除约束前子块所属的容器 id（脱离后用于清掉它与原容器的内部连线） */
const detachFrom = ref(null)

/**
 * `pointerdown` 捕获阶段：若落在迭代子块上且**本次按下**按住修饰键，立即把子块提为顶层节点。
 *
 * 修饰键取本事件自带的 `ctrlKey` / `metaKey`（判定见 {@link isModifierEvent}），不依赖任何全局状态。
 * 时机比 Vue Flow 的拖拽快照（超过拖拽阈值后才取 `getDragItems`）更早，因此快照会捕获到
 * 「已解约束」的状态，整段拖拽都自由——子块得以被拖到容器外。具体改动：清掉 `parentNode`
 * 与 `extent`、把层级设回 0，并把 `position` 从「相对容器」换算成「绝对坐标」（用当前
 * `computedPosition`，它本来就是绝对坐标），否则解约束瞬间节点会跳回原点。
 */
const handleCanvasPointerDown = (event) => {
  // 每次按下都是一次全新的手势：先清掉上一轮的解约束标记。
  // 必须清——「按住 Ctrl 点一下子块」不会产生拖拽，Vue Flow 也就不会派发 `node-drag-stop`
  // （`getDragItems` 为空时不触发 onStop），标记会一直挂到下一次拖拽：那时若松手落在空白，
  // 就会被当成「脱离容器」，把「被拖节点 ↔ 上一轮那个容器」这条与本轮无关的连线删掉。
  detachArmed.value = false
  detachFrom.value = null
  if (!isModifierEvent(event)) return
  const nodeEl = event?.target?.closest?.('.vue-flow__node')
  const nodeId = nodeEl?.getAttribute?.('data-id')
  if (!nodeId) return
  const node = getNodes.value.find((item) => item?.id === nodeId)
  if (!node?.parentNode || !node?.computedPosition) return

  detachArmed.value = true
  detachFrom.value = node.parentNode
  updateNode(node.id, {
    parentNode: undefined,
    extent: undefined,
    zIndex: 0,
    position: { x: Math.round(node.computedPosition.x), y: Math.round(node.computedPosition.y) },
  })
}

/**
 * 拖拽进行中：实时算出「松手会落进哪个容器」，喂给容器卡片高亮。
 *
 * 每次指针移动都做一次命中检测（节点数很小，代价可忽略）；排除被拖节点自己与它的后代，
 * 与松手时的口径完全一致，所以高亮的容器就是最终结果，不会出现「看着高亮、松手却没进去」。
 */
const handleNodeDrag = ({ node, event }) => {
  if (!node?.id) {
    iterationDropTarget.value = null
    return
  }
  const nodes = getNodes.value
  iterationDropTarget.value = containerAtPoint(nodes, dropPointOf(node, event), {
    exclude: descendantIdsOf(nodes, node.id),
  })
}

/**
 * 拖拽结束：按落点结算节点属于哪一层。
 *
 * 子块若是在 `pointerdown` 时按住修饰键被即时提为顶层（见 {@link handleCanvasPointerDown}），
 * 那么它已经脱离了容器、整段拖拽都是自由的；这里只需决定「松手时落在哪」：
 * - **落在某容器画布区** → 通过 {@link planMigration} 挂进该容器（跨容器移动也走这条）；
 * - **落在空白** → 已是顶层，什么也不用做，只清一遍跨层线；
 * - **普通拖拽出界**（没按修饰键）→ 维持原状，留在原容器。
 *
 * 不再需要「松手后写 `computedPosition` 补丁」：解约束发生在 `pointerdown`、早于 Vue Flow 的
 * 拖拽快照，松手时节点已经是顶层自由状态，Vue Flow 收尾写回的就是正确坐标。
 *
 * @param {object} node 被拖拽的节点（Vue Flow 的实时节点）
 * @param {Event} event 拖拽结束事件（取指针位置）
 */
const settleNodeLevel = (node, event, fromContainer = null) => {
  if (!node?.id) return
  const nodes = getNodes.value
  const currentParent = node.parentNode ?? null
  const point = dropPointOf(node, event)

  // 排掉自己与自己的后代：容器拖进自己里会形成环（画布只展开一层，后代最多是当前子块）
  const target = containerAtPoint(nodes, point, { exclude: descendantIdsOf(nodes, node.id) })

  // 真正脱离容器（按住修饰键拖到空白）：节点已在 pointerdown 提为顶层，这里只清跨层线 + 与原容器的内部线
  if (!target && detachArmed.value) {
    setTimeout(() => cleanupCrossLevelEdges(node.id, { detach: true, fromContainer }), 0)
    return
  }

  // 落回原容器（非脱离情形）：无需迁移，但仍清一遍跨层线
  if (target === currentParent) return cleanupCrossLevelEdges(node.id)

  // 拖到空白但没按修饰键：普通拖拽出界，维持原状（子块本就被 extent 夹在容器内）
  if (!target && currentParent) return cleanupCrossLevelEdges(node.id)

  const plan = planMigration(nodes, node.id, target, {
    // 脱离容器时节点已被提为顶层，其当前绝对坐标就是用户想要的落点；
    // detachAt 仅在「起手没解约束、松手却要求脱离」的兜底路径使用（节点居中于指针）
    detachAt: target ? undefined : centeredPositionOf(node, point),
  })

  // 跨容器挂入需要延迟到 Vue Flow 拖拽收尾之后，避免被它回写覆盖；
  // 已顶层的脱离路径无需补丁，这里统一用 setTimeout 收口即可
  setTimeout(() => {
    const latest = getNodes.value.find((item) => item?.id === node.id)
    if (!latest) return
    if (plan) updateNode(latest.id, plan)
    cleanupCrossLevelEdges(node.id, { detach: !target })
    // 「迭代体内部」这个身份是迁移之后才成立的：先删掉跨层的边，再把它新带来的内部连线
    // 补成内部边口径，否则它会带着顶层样式被不透明的容器卡片盖住（见 normalizeIterationEdges）
    normalizeIterationEdges()
  }, 0)
}

/**
 * 清理迁移后变跨层的连线，并提示用户。
 *
 * `detach` 为真（子块真正脱离容器、落到空白）时，除了常规跨层边，还要清掉它与**原容器**之间的
 * 内部连线（`容器 ↔ 子块` 按 {@link isCrossLevelEdge} 不算跨层、不会被前一步删掉，但它已经不是
 * 迭代体的一部分了，留着会变成「顶层节点直接连到迭代容器」这条语义错乱的线）。重新挂入另一容器
 * （`detach` 为假）则不动这些线。
 *
 * @param {string} nodeId 被迁移的节点 id
 * @param {object} [options]
 * @param {boolean} [options.detach] 是否本次为「脱离容器」
 */
const cleanupCrossLevelEdges = (nodeId, { detach = false, fromContainer = null } = {}) => {
  let removed = removeCrossLevelEdges()
  if (detach && nodeId && fromContainer) {
    const cid = fromContainer
    const internal = getEdges.value.filter(
      (edge) => (edge.source === nodeId && edge.target === cid)
        || (edge.target === nodeId && edge.source === cid),
    )
    if (internal.length) {
      removeEdges(internal)
      removed += internal.length
    }
  }
  if (removed) {
    ElMessage.warning(`已删除 ${removed} 条跨层连线（迭代体内外不能直接相连）`)
  }
}

/**
 * 把「迁移之后才变成迭代体内部」的连线归一到内部边口径（迭代体样式 + 内侧 handle）。
 *
 * 为什么需要：入容器（含跨容器移动）只写回节点与删掉跨层的边，而「属于某个迭代体内部」这个身份
 * 是迁移**之后**才成立的——那些原来在顶层画的线不会自己长出 `zIndex`，于是被不透明的容器卡片
 * 整段盖住（线在 store 里、画布上却看不见），handle 还挂在外侧则让路径从卡片边缘进出。
 * 口径本身收在 `utils/iteration` 的 `internalEdgePatchOf` 里，这里只负责写回。
 *
 * 写回用 `setEdges` 整表替换：Vue Flow 没有「按字段更新单条边」的动作（`updateEdge` 是「改接」
 * 语义、`applyEdgeChanges` 只认 select / remove 之类），而未命中的边原样引用回写、不会引起重渲染。
 * 已合规的边拿不到补丁，所以整表过一遍的代价可以忽略（节点数与连线数都很小）。
 *
 * @returns {number} 本次被写回的连线条数
 */
const normalizeIterationEdges = () => {
  const nodes = getNodes.value
  const patches = new Map()
  getEdges.value.forEach((edge) => {
    const patch = internalEdgePatchOf(nodes, edge)
    if (patch) patches.set(edge.id, patch)
  })
  if (!patches.size) return 0
  setEdges(getEdges.value.map((edge) => (
    patches.has(edge.id) ? { ...edge, ...patches.get(edge.id) } : edge
  )))
  return patches.size
}

/** Vue Flow 节点拖拽结束：先按落点结算层级，再清掉拖拽高亮与本轮解约束标记 */
const handleNodeDragStop = ({ node, event }) => {
  const fromContainer = detachFrom.value
  settleNodeLevel(node, event, fromContainer)
  iterationDropTarget.value = null
  detachArmed.value = false
  detachFrom.value = null
}



/** 当前事件流的控制器：重新执行或离开页面时用它中断旧连接 */
const runAbort = ref(null)

/** 中断当前事件流 */
const abortRun = () => {
  runAbort.value?.abort()
  runAbort.value = null
}

/** 人工确认弹窗：卡片数据由后端的中断帧下发，画布页只负责「弹出来」与「提交回去」 */
const cardVisible = ref(false)
const cardData = ref(null)

/** 打开当前待确认的表单卡片（中断时自动打开；节点卡片上的「填写」按钮也能再打开） */
const openCard = () => {
  const card = pendingCardOf(runState.value)
  if (!card) {
    ElMessage.warning('当前没有等待确认的人工确认节点')
    return
  }
  cardData.value = card
  cardVisible.value = true
}

// 收到中断帧（节点进入 waiting 且带卡片）就自动弹出，用户不需要再点一次
watch(
  () => pendingCardOf(runState.value)?.node_id ?? '',
  (nodeId) => { if (nodeId) openCard() },
)

/**
 * 提交一轮交互并消费事件流：事件交给 run-state 归约，节点卡片按 id 渲染状态与产出。
 *
 * @param {object} options 选项
 * @param {object} options.body 请求体（首轮 = 会话 + 工作流；续跑 = 会话 + 表单填写值）
 * @param {boolean} [options.resume] 是否续跑；续跑保留已有产出，首轮重置全部节点状态
 */
const execute = async ({ body, resume = false }) => {
  abortRun()
  const controller = new AbortController()
  runAbort.value = controller
  // 首轮把整份画布节点交给归约：除了给每个节点建运行态，还用它推导迭代进度（容器 → 子块、迭代源）。
  // 取 store 的实时节点（getNodes）而不是 v-model 绑定的 ref：ref 由 Vue Flow 在下一个 tick 才回写，
  // 刚做完删除/清理就点运行时会拿到旧数据，导致残留节点仍被当作顶层节点提交执行。
  runState.value = resume ? resumeRun(runState.value) : beginRun(runState.value, getNodes.value)

  try {
    await readWorkflowStream(CHAT_API.chat, {
      body,
      signal: controller.signal,
      onEvent: (event, payload) => {
        runState.value = applyRunEvent(runState.value, event, payload)
      },
    })
    // 失败终态由 chat.failed 帧表达（流本身仍正常关闭），故这里不传 failed，交给 finishRun 读本轮结论
    runState.value = finishRun(runState.value)
  } catch (error) {
    // 主动中断（重新运行 / 离开页面）不算失败
    if (controller.signal.aborted) return
    runState.value = finishRun(runState.value, true)
    console.error('[workflow] 执行失败:', error)
    ElMessage.error(error?.message || '工作流执行失败')
  } finally {
    if (runAbort.value === controller) runAbort.value = null
  }
}

/**
 * 运行前校验未通过：把全部原因一次列给用户（对应 flowgram 示例 4 里 `result.errors?.forEach` 的提示）。
 *
 * 用 vnode 列表而不是 HTML 字符串：错误信息里会带上用户填写的节点名称，不能当 HTML 渲染。
 */
const showRunValidationErrors = (errors) => {
  // `alert` 返回的 Promise 在用户点右上角关闭时以 `'cancel'` reject，这里必须吞掉，
  // 否则控制台会出现未捕获的 promise rejection（与 NodeDataDrawer 里 try/catch 吞掉「取消」同因）
  ElMessageBox.alert(
    h('div', errors.map((message) => h('div', message))),
    '运行前校验未通过',
    { type: 'warning', confirmButtonText: '知道了' },
  ).catch(() => {})
}

/**
 * 工具栏「运行」：把**已保存的**工作流提交给后端执行，节点进度与产出通过 SSE 回传。
 *
 * `/chat` 只接受 `workflow_id`、不接受内联画布定义，所以未保存的画布必须先保存
 * （地址栏带不上 `workflow_id` 时直接拦住，不做静默保存——那会覆盖已发布的定义）。
 * 每次运行新建一个会话（画布页不做会话列表），中断后的续跑复用同一个会话。
 *
 * 提交前先做一次**运行前校验**（复刻 flowgram 运行时的 TaskValidate：先校验再运行，见
 * utils/run-validation）：不通过就拦住本次运行并把原因全部列出，不进入执行。
 */
const handleRun = async () => {
  if (runState.value.streaming) {
    ElMessage.info('正在执行中，请等待本次执行结束')
    return
  }
  const workflowId = currentWorkflowId.value
  if (!workflowId) {
    ElMessage.warning('请先保存工作流再运行（执行按 workflow_id 读取已落库的定义）')
    return
  }
  // 提交前清掉悬空的迭代体子块，避免它们作为顶层节点被提交（定义取自 store 的实时数据）
  cleanOrphanChildren()
  const definition = buildRunDefinition(getNodes.value, getEdges.value)
  if (!definition.nodes.length) {
    ElMessage.warning('画布上还没有节点，先添加节点再运行')
    return
  }
  // 校验读的是与提交同一份 store 实时数据（刚做过悬空子块清理），不会出现「校验的是旧画布」
  const errors = runValidationErrors(getNodes.value, getEdges.value)
  // 校验通过时页面不会有任何反馈（只有失败才弹窗），开发期留一条日志，便于确认「这次校验到底跑没跑」
  if (import.meta.env.DEV) console.debug('[workflow] 运行前校验', errors.length ? errors : '通过')
  if (errors.length) {
    showRunValidationErrors(errors)
    return
  }
  try {
    // 会话在建的那一刻绑定工作流；执行入口按会话查最新一条执行实例来决定「新建 / 续跑」
    const conversation = await createConversation(workflowId)
    runState.value = { ...runState.value, conversationId: conversation.conversationId }
  } catch (error) {
    ElMessage.error(error?.message || '会话创建失败')
    return
  }
  execute({ body: buildChatBody(runState.value.conversationId, workflowId) })
}

/**
 * 人工确认弹窗的「确定」：把表单填写值**结构化**回传，从暂停处继续执行（新起一条事件流）。
 *
 * 回传的是 `values`（字段名 → 取值）而不是一段文本：后端按 `state[节点id][变量名]` 回写状态，
 * 下游节点通过 `blockID + name` 就能引用到人工填写的内容。
 *
 * @param {object} payload 弹窗提交内容
 * @param {string} payload.nodeId 等待确认的节点 id
 * @param {object} payload.values 字段名 → 取值
 */
function submitCard({ nodeId, values }) {
  const conversationId = runState.value.conversationId
  if (!conversationId) {
    ElMessage.warning('没有正在等待确认的执行')
    return
  }
  cardVisible.value = false
  execute({ body: buildCardSubmitBody(conversationId, nodeId, values), resume: true })
}

// 删除键挂在 window 上：画布本身不保证持有焦点（点空白处后焦点会落回 body），
// 挂在画布容器上会出现「选中节点后按删除键没反应」。输入框里的按键由 isEditingElement 过滤。
onMounted(() => {
  window.addEventListener('keydown', handleDeleteKey)
  // 带 workflow_id 进入时载入已保存的定义；无该参数则保留 default-flow 的默认画布
  loadWorkflowFromRoute()
})
onUnmounted(() => window.removeEventListener('keydown', handleDeleteKey))

onUnmounted(abortRun)


/**
 * 把后端结构的定义摊平成画布表示：迭代节点用 `expandIteration` 展开为「容器 + 子块 + 内部边」
 * （子块带 `parentNode`），顶层边统一套上画布连线样式并换成 vue-flow 的字段名。
 *
 * 顶层边显式指定外侧 handle（`out` → `in`）：vue-flow 在边没写 handle 时会取「候选列表第一个」
 * （loose 模式下候选会把 source / target 两边的 handle 合并），而迭代容器有 4 个 handle，
 * 不指定就会连到内侧的「迭代开始 / 迭代结束」上。已有 handle 的边保持原值。
 */
const toCanvasFlow = (definition) => {
  const canvasNodes = []
  const canvasEdges = []
  for (const node of definition.nodes) {
    if (String(node.type) === ITERATE_NODE_TYPE) {
      const expanded = expandIteration(node)
      canvasNodes.push(...expanded.nodes)
      canvasEdges.push(...expanded.edges)
      continue
    }
    canvasNodes.push(node)
  }
  for (const edge of definition.edges) {
    canvasEdges.push({
      id: edge.id,
      source: edge.sourceNodeID ?? edge.source,
      target: edge.targetNodeID ?? edge.target,
      sourceHandle: edge.sourceHandle ?? CANVAS_OUTPUT_HANDLE,
      targetHandle: edge.targetHandle ?? CANVAS_INPUT_HANDLE,
      ...CANVAS_EDGE_STYLE,
    })
  }
  return { nodes: canvasNodes, edges: canvasEdges }
}

/**
 * 画布数据：默认加载 `default-flow.js` 的链路（开始 → 大模型 → 迭代 → 结束），
 * 后续的节点库 / 打开工作流会在此之上增删。
 * 迭代节点的迭代体（blocks）以 `parentNode` 子节点的形式挂在容器下。
 */
const defaultCanvas = toCanvasFlow(DEFAULT_FLOW_DEFINITION)

const nodes = ref(defaultCanvas.nodes)

// 画布上的边用 vue-flow 的字段名：source / target（不是后端 schema 的 sourceNodeID / targetNodeID）
// handle 由 toCanvasFlow 显式补齐（外侧 out / in）：多 handle 的节点（如迭代容器）必须显式指定，
// 否则 vue-flow 会按「候选列表第一个」兜底，把主链路连到内侧的「迭代开始 / 迭代结束」上
const edges = ref(defaultCanvas.edges)

/* ===== 工作流定义持久化：按 workflow_id 载入、保存回库 ===== */

const route = useRoute()
const router = useRouter()

/** 当前正在编辑的工作流 id（空 = 尚未保存过，保存时走新建） */
const currentWorkflowId = ref(String(route.query.workflow_id ?? '').trim())

/** 当前工作流的名称 / 说明：保存对话框回填与顶部标识共用 */
const currentWorkflowName = ref('')
const currentWorkflowDescription = ref('')

/** 保存对话框状态 */
const saveDialogVisible = ref(false)
const saving = ref(false)
const saveForm = ref({ name: '', description: '' })

/** 名称 / 说明长度上限（与后端 workflow 表的 varchar(100) / varchar(500) 对齐） */
const WORKFLOW_NAME_MAX = 100
const WORKFLOW_DESC_MAX = 500

/** 解析画布定义 JSON；内容损坏时返回 null，由调用方决定如何提示 */
function parseSchemaJson(schemaJson) {
  try {
    return JSON.parse(schemaJson ?? '')
  } catch {
    return null
  }
}

/**
 * 用后端详情覆盖画布：`schemaJson` 解析出的定义经 `toCanvasFlow` 展开（迭代体还原成
 * 「容器 + 子块 + 内部边」），随后回填名称与说明。
 *
 * 解析失败或结构为空时**不改动画布**——宁可停在原画布，也不要清掉用户正在编辑的内容。
 */
function applyWorkflowDetail(detail) {
  const definition = parseSchemaJson(detail?.schemaJson)
  if (!definition?.nodes?.length) {
    ElMessage.error('该工作流的画布定义无法解析，已保留当前画布')
    return
  }
  const canvas = toCanvasFlow(definition)
  setNodes(canvas.nodes)
  setEdges(canvas.edges)
  currentWorkflowId.value = String(detail.id ?? '')
  currentWorkflowName.value = detail.name ?? ''
  currentWorkflowDescription.value = detail.description ?? ''
}

/** 按路由 `workflow_id` 载入定义；没有该参数时保持默认画布 */
async function loadWorkflowFromRoute() {
  const workflowId = String(route.query.workflow_id ?? '').trim()
  if (!workflowId) return
  try {
    applyWorkflowDetail(await getWorkflowDetail(workflowId))
  } catch (error) {
    ElMessage.error(error?.message || '工作流载入失败')
  }
}

/**
 * 工具栏「保存」：先过一遍与运行同一套的运行前校验（避免把跑不起来的画布存进库），
 * 通过后打开对话框填写名称 / 说明；已有名称时回填，便于「打开后另存」。
 */
function handleSave() {
  if (runState.value.streaming) {
    ElMessage.info('正在执行中，请等待本次执行结束')
    return
  }
  // 与运行同一口径：先清掉悬空的迭代体子块，避免它们作为顶层节点被存进定义
  cleanOrphanChildren()
  const definition = buildRunDefinition(getNodes.value, getEdges.value)
  if (!definition.nodes.length) {
    ElMessage.warning('画布上还没有节点，先添加节点再保存')
    return
  }
  const errors = runValidationErrors(getNodes.value, getEdges.value)
  if (errors.length) {
    showRunValidationErrors(errors)
    return
  }
  saveForm.value = { name: currentWorkflowName.value, description: currentWorkflowDescription.value }
  saveDialogVisible.value = true
}

/**
 * 对话框「保存」：提交画布定义（与运行共用同一份折叠口径）。
 *
 * 新建成功后把返回的雪花 id 写回地址栏——刷新或分享该链接即可直接打开这份定义。
 * id 全程按字符串处理：19 位雪花 ID 超出 JS 安全整数范围，转成数值会丢精度。
 */
async function submitSave() {
  const name = saveForm.value.name.trim()
  if (!name) {
    ElMessage.warning('请填写工作流名称')
    return
  }
  if (name.length > WORKFLOW_NAME_MAX) {
    ElMessage.warning(`工作流名称不能超过 ${WORKFLOW_NAME_MAX} 个字符`)
    return
  }
  const description = saveForm.value.description.trim()
  if (description.length > WORKFLOW_DESC_MAX) {
    ElMessage.warning(`工作流说明不能超过 ${WORKFLOW_DESC_MAX} 个字符`)
    return
  }
  saving.value = true
  try {
    const id = await saveWorkflow({
      id: currentWorkflowId.value,
      name,
      description,
      schemaJson: buildWorkflowSchema(getNodes.value, getEdges.value),
    })
    currentWorkflowId.value = String(id ?? '')
    currentWorkflowName.value = name
    currentWorkflowDescription.value = description
    saveDialogVisible.value = false
    router.replace({ query: { ...route.query, workflow_id: currentWorkflowId.value } })
    ElMessage.success('工作流已保存')
  } catch (error) {
    ElMessage.error(error?.message || '工作流保存失败')
  } finally {
    saving.value = false
  }
}

</script>

<template>
  <div
    ref="canvasRef"
    class="wf-canvas"
    :class="{ 'wf-canvas--dropping': nodeDropActive }"
    @dragover="handleNodeDragOver"
    @dragleave="handleNodeDragLeave"
    @dragend="handleNodeDragEnd"
    @drop="handleNodeDrop"
    @pointerdown.capture="handleCanvasPointerDown"
  >
    <!-- elevate-nodes-on-select 关闭：迭代节点是不透明卡片，子块与内部连线靠固定层级（见 utils/iteration 的
         ITERATION_LAYER）压在卡片之上；若保留「选中即抬到 1000」的默认行为，选中迭代节点时会把这两者反压回去 -->
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :node-types="nodeTypes"
      :elevate-nodes-on-select="false"
      :delete-key-code="null"
      :multi-selection-key-code="['Alt']"
      fit-view-on-init
      @connect="handleConnect"
      @node-click="onNodeClick"
      @node-drag="handleNodeDrag"
      @node-drag-stop="handleNodeDragStop"
    >
      <Background :gap="22" :size="1.4" />

      <!-- 自定义连线预览：拖拽连线时跟随指针的紫色虚线 + 终点圆点 -->
      <template #connection-line="connectionLineProps">
        <ConnectionLine v-bind="connectionLineProps" />
      </template>
    </VueFlow>

    <!-- 左侧浮层：节点库面板与工具栏并排停靠在左上角。
         容器整体 pointer-events: none，只有各自的按钮 / 输入框可交互，不能吃掉画布的拖拽与框选 -->
    <div class="wf-left-rail">
      <NodePanel :existing-types="existingTypes" @add-node="addNodeAt" />
      <ToolsWrap
        :running="runState.streaming"
        :saving="saving"
        @run="handleRun"
        @save="handleSave"
      />
    </div>

    <!-- 当前工作流标识：已打开 / 已保存过才显示，便于确认正在编辑哪一份定义 -->
    <div
      v-if="currentWorkflowName"
      class="wf-workflow-badge"
      :title="currentWorkflowDescription || currentWorkflowName"
    >
      {{ currentWorkflowName }}
    </div>

    <!-- 节点数据抽屉：编辑当前选中节点的变量与提示词 -->
    <NodeDataDrawer v-model:visible="drawerVisible" :node-id="activeNodeId" />

    <!-- 保存工作流：名称必填、说明可选；打开已有工作流时回填原值 -->
    <el-dialog
      v-model="saveDialogVisible"
      title="保存工作流"
      width="420px"
      :close-on-click-modal="false"
    >
      <div class="wf-save-form">
        <span class="wf-save-form__label">工作流名称</span>
        <el-input
          v-model="saveForm.name"
          :maxlength="WORKFLOW_NAME_MAX"
          show-word-limit
          placeholder="如：标准批改作业"
        />
        <span class="wf-save-form__label">说明（可选）</span>
        <el-input
          v-model="saveForm.description"
          type="textarea"
          :rows="3"
          resize="none"
          :maxlength="WORKFLOW_DESC_MAX"
          show-word-limit
          placeholder="这份工作流做什么、适用于什么场景"
        />
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="saveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 人工确认弹窗：执行到人工确认节点中断时自动弹出，填写值经 /chat 续跑回传 -->
    <HumanCardDialog v-model="cardVisible" :card="cardData" @submit="submitCard" />
  </div>
</template>

<style scoped>
.wf-canvas {
  /* relative：左侧浮层（.wf-left-rail）是绝对定位的浮层，靠它定位 */
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--tb-bg-page);
}

/*
  当前工作流标识：画布顶部居中，与左侧的节点库 / 工具栏浮层错开。
  pointer-events: none：它只是提示，不能挡住画布的框选与拖拽。
*/
.wf-workflow-badge {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  max-width: 40%;
  padding: 4px 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  font-size: var(--tb-text-sm);
  color: var(--tb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}

/* 保存对话框表单：标签在上、控件在下 */
.wf-save-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wf-save-form__label {
  margin-top: 6px;
  font-size: var(--tb-text-sm);
  color: var(--tb-text-secondary);
}

.wf-save-form__label:first-child {
  margin-top: 0;
}

/*
  左侧浮层容器：节点库面板 + 工具栏横向并排停靠。
  bottom: 16px 让容器占满可用高度，面板的 max-height: 100% 才有意义（节点多时列表内部滚动）。
  pointer-events: none：容器自身与两块浮层之间的空隙不吃画布手势，可交互元素各自声明 auto。
*/
.wf-left-rail {
  position: absolute;
  top: 16px;
  bottom: 16px;
  left: 16px;
  z-index: 10;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  pointer-events: none;
}

/* 从节点库面板拖拽经过画布时的落点反馈：内缘一圈主色虚线。
   pointer-events: none —— 它只是提示层，不能挡住 dragover / drop 事件 */
.wf-canvas--dropping::after {
  position: absolute;
  inset: 8px;
  z-index: 9;
  border: 2px dashed var(--tb-primary);
  border-radius: var(--tb-radius-lg);
  content: '';
  pointer-events: none;
}

.wf-canvas :deep(.vue-flow__background circle) {
  fill: var(--tb-border-strong);
}
</style>

<!--
  连线相关样式（Vue Flow 把边渲染在画布 viewport 里，不属于本组件模板的 DOM 子树，scoped 选不中）：

  悬停 / 选中时把线加粗 —— 让「点中的是哪条线」可见（行内 stroke-width 需要 !important 覆盖）。

  注意**不要**给 `.vue-flow__edge-interaction`（Vue Flow 渲染的透明命中线）加 `stroke-linecap: round`：
  它会让命中区沿路径两端各外扩半个宽度，而迭代体内部连线的两端正好是迭代节点上的两个内侧
  handle（`iterate-in` / `iterate-out`），且连线层级高于节点卡片 —— 结果就是这两个连接点
  再也收不到 pointerdown（拖不出任何连线）。复现脚本见 `frontend/.repro/repro-inner-handle-blocked.ps1`。
-->
<style>
.vue-flow__edge:hover .vue-flow__edge-path,
.vue-flow__edge.selected .vue-flow__edge-path {
  stroke-width: 3 !important;
}
</style>
