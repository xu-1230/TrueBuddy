/**
 * 「迭代子块拖回容器」两处缺陷的**复现 → 回归锁定**用例（Node 内置 node:test，零新依赖）。
 *
 * 这些用例最初用来复现并确认问题真实存在（修复前有 4 条断言失败，失败信息见对话记录）；
 * 修复后转为回归锁定：用例序列完全保留，只把期望改成正确行为。任何一处退化都会在这里变红。
 *
 * 运行：cd frontend && npm test
 *      或：node --test tests/repro-iteration-reattach.test.js
 *
 * ===== 两条链路与各自的修复点 =====
 *
 * 1. **不按 Ctrl 也能把子块拖出容器**（修复前现象：拖回容器确实成为子块，但 `extent` 没生效）。
 *    真机序列：`Ctrl 拖子块出容器 → 松手 → 把节点拖回容器（成为子块）→ 不按 Ctrl 再拖它 → 仍能拖出去`。
 *
 *    修复前根因：修饰键状态由**全局** `keydown` / `keyup` 维护，而 `keyup` 事件在 Ctrl 抬起时
 *    `ctrlKey` 已经是 `false`，于是「松开即复位」的守卫永远不成立、状态恒为 `true`。残留的 `true`
 *    让随后**任意一次落在子块上的 pointerdown**（不按 Ctrl 也会）走进 `handleCanvasPointerDown` 的
 *    解约束分支：清 `parentNode` / `extent`、position 转绝对坐标。而 Vue Flow 的夹取范围是在**拖拽开始时**
 *    快照 `node.extent` 的（`@vue-flow/core/dist/vue-flow-core.mjs:4158-4182` 的 `getDragItems`），
 *    `extent` 已被清掉 → `getExtent`（`:4227-4258`）退回全局 `nodeExtent`（本应用未设置）→ 完全不夹取。
 *
 *    修复：删掉那份全局状态，`handleCanvasPointerDown` 直接读本次 `pointerdown` 事件自带的
 *    `ctrlKey` / `metaKey`（原生 PointerEvent 上就是这一下手势的真实状态，见 `isModifierEvent`）。
 *    本文件用同一份判定复刻该分支，跑「Ctrl 拖出 → 拖回 → 不按 Ctrl 按下」整条序列。
 *
 * 2. **拖回容器后连线看不见 / 看着断了**（修复前现象：线还在 store 里，但被不透明容器卡片盖住，
 *    只在卡片外露出两小截）。
 *
 *    修复前根因：`settleNodeLevel` 在「拖入 / 拖回容器」时只写节点、只删跨层边，而「属于迭代体内部」
 *    这个身份是迁移**之后**才成立的——那些原来在顶层画的线不会自己长出 `zIndex`。它又不会被清理
 *    （`isCrossLevelEdge` 刻意认为「容器 ↔ 它自己的子块」合法，折叠后等价于子图的 START / END），
 *    于是一条 `zIndex` 缺省的边留在 store 里：Vue Flow 1.48.2 里**每条边是一个独立的
 *    `<svg class="vue-flow__edges" style="z-index: …">`**（`vue-flow-core.mjs:9363-9373`，
 *    `getEdgeZIndex:4333-4345` 只在 `typeof edge.zIndex === 'number'` 时才采用），而节点层
 *    `.vue-flow__nodes` 在 DOM 中更靠后（`@vue/core/dist/style.css:64-67 / 135-138`）→ 同 z-index
 *    时节点覆盖边 → 整段被卡片盖住。
 *
 *    修复：`utils/iteration` 新增 `internalEdgePatchOf(nodes, edge)`（内部边口径的唯一来源，
 *    与 `expandIteration` 的展开共用 `innerEdgeFieldsOf`），画布页在迁移结算后调用
 *    `normalizeIterationEdges()` 写回；本文件复刻的就是这一步。
 *
 * ===== 另外几项是「排除性证据」 =====
 * 迁移算式 `planMigration`（utils/iteration-membership.js:287-329）本身是完整的（入容器带齐
 * `parentNode / extent / zIndex`、坐标夹进可见画布区），所以这两处缺陷都不在迁移算式里。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  ITERATE_ENTRY_HANDLE,
  ITERATE_EXIT_HANDLE,
  ITERATION_EDGE_STYLE,
  ITERATION_LAYER,
  canvasTopOf,
  estimatedTopAreaOf,
  expandIteration,
  internalEdgePatchOf,
  iterationOwnerOf,
} from '../src/views/workflow2/utils/iteration.js'
import {
  absolutePositionOf,
  canvasRectOf,
  invalidEdgesOf,
  isPointInRect,
  planMigration,
} from '../src/views/workflow2/utils/iteration-membership.js'

/* ===== 夹具（默认流程 default-flow.js 的真实数据） ===== */

/**
 * 默认流程里的关键 id：**从定义派生**。
 *
 * id 是数据、不是契约 —— 上一版写死在这里，替换默认流程时本文件的用例集体染红，
 * 而它们真正关心的是「子块脱离 / 回到容器时连线的归一化」这类与具体 id 无关的行为。
 */
const iterateNode = () => DEFAULT_FLOW_DEFINITION.nodes.find((node) => String(node.type) === '4')
const idsOfType = (type) => DEFAULT_FLOW_DEFINITION.nodes
  .filter((node) => String(node.type) === type)
  .map((node) => node.id)
/** 迭代容器（type 4） */
const CONTAINER_ID = iterateNode().id
/** 迭代体首个子块 */
const CHILD_ID = iterateNode().blocks[0].id
/** 迭代体全部子块 id（用来把「内部边」与「主链路连入」区分开） */
const CHILD_IDS = iterateNode().blocks.map((block) => block.id)
/** 顶层节点（规划）：问题2 里「顶层状态」的参照物 */
const [UPSTREAM_ID] = idsOfType('3')

/**
 * 默认流程的画布表示：迭代节点用 `expandIteration` 展开成「容器 + 子块 + 内部边」，
 * 顶层边只是补上 vue-flow 的 `source / target` 字段名（与画布页 `toCanvasFlow` 同口径）。
 */
const canvasOfDefaultFlow = () => {
  const nodes = []
  const edges = []
  for (const node of DEFAULT_FLOW_DEFINITION.nodes) {
    if (String(node.type) === '4') {
      const expanded = expandIteration(node)
      nodes.push(...expanded.nodes)
      edges.push(...expanded.edges)
      continue
    }
    nodes.push(node)
  }
  edges.push(...DEFAULT_FLOW_DEFINITION.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeID,
    target: edge.targetNodeID,
  })))
  return { nodes, edges }
}

const nodeOf = (nodes, id) => nodes.find((node) => node?.id === id)

/** 写回节点：等价于画布页 `useVueFlow().updateNode(id, patch)`（`Object.assign` 语义） */
const applyPatch = (nodes, id, patch) => nodes.map((node) => (
  node?.id === id ? { ...node, ...patch } : node
))

/** 读取组件源码（源码契约断言用；路径相对本测试文件） */
const readSource = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf8')

/* ===== 问题1：修饰键判定（与 WorkflowView.vue 的 isModifierEvent 逐字一致） ===== */

/** 与画布页 `isModifierEvent` 逐字一致；文件末尾的「源码契约」用例用正则守住这段拷贝 */
const isModifierEvent = (event) => Boolean(event?.ctrlKey || event?.metaKey)

/**
 * 原生 PointerEvent 的形态：`ctrlKey` / `metaKey` 就是这一下手势的修饰键状态。
 *
 * 对比「曾经的坑」：Ctrl 的 `keyup` 事件上 `ctrlKey` 已经是 `false`，用它做复位守卫会让状态
 * 永远复位不了 —— 所以现在不再维护任何全局键盘状态，只认当前手势。
 */
const POINTER_DOWN_WITH_CTRL = { type: 'pointerdown', ctrlKey: true, metaKey: false }
const POINTER_DOWN_WITH_META = { type: 'pointerdown', ctrlKey: false, metaKey: true }
const POINTER_DOWN_PLAIN = { type: 'pointerdown', ctrlKey: false, metaKey: false }

/**
 * 复刻 `handleCanvasPointerDown` 的「解约束」决策（逐条对应源码）：
 * 1. `if (!isModifierEvent(event)) return` —— 本次按下没带修饰键就什么都不做；
 * 2. 命中的必须是**子块**（`node.parentNode` 为真，且能拿到 `computedPosition`）；
 * 3. 写入 `{ parentNode: undefined, extent: undefined, zIndex: 0, position: 绝对坐标 }`
 *    （源码用 `node.computedPosition`，它本来就是绝对坐标；这里用 `absolutePositionOf`，
 *     两者对结构完整的子块等价，见 tests/iteration-membership.test.js 的坐标换算用例）。
 *
 * @param {Array} nodes 画布节点
 * @param {string} nodeId 被按下的节点 id
 * @param {object} event 本次 pointerdown 事件（取 ctrlKey / metaKey）
 * @returns {Array} 新的节点数组（与源码一样不可变地写回）
 */
const pointerDownOnNode = (nodes, nodeId, event) => {
  if (!isModifierEvent(event)) return nodes
  const node = nodeOf(nodes, nodeId)
  if (!node?.parentNode) return nodes
  const absolute = absolutePositionOf(nodes, node)
  return applyPatch(nodes, nodeId, {
    parentNode: undefined,
    extent: undefined,
    zIndex: 0,
    position: { x: Math.round(absolute.x), y: Math.round(absolute.y) },
  })
}

/**
 * 「下一次拖拽是否会被 `extent` 夹回容器画布区」的判据建模。
 *
 * 口径抄自 `@vue-flow/core/dist/vue-flow-core.mjs:4227-4258` 的 `getExtent`：
 * `extent` 生效（按容器画布区夹取）当且仅当 `extent.range === 'parent'` 且 `node.parentNode` 存在、
 * 父节点可解析、`node.dimensions` 非零；任一条件不满足时会 `triggerError(NODE_EXTENT_INVALID)` 并退回
 * 全局 `nodeExtent`（本应用未设置 → 等于**不夹取**）。这里只判「前置条件」，具体坐标由
 * `planMigration` / `clampChildPosition` 负责（已在 tests/iteration-membership.test.js 里断言过）。
 */
const extentTakesEffect = (node, parentResolvable = true, dimensions = { width: 272, height: 160 }) => {
  const extent = node?.extent
  const supportsParentRange = extent === 'parent'
    || (!Array.isArray(extent) && extent?.range === 'parent')
  if (!supportsParentRange) return false
  return Boolean(node.parentNode && parentResolvable && dimensions.width && dimensions.height)
}

test('问题1 回归：按住 Ctrl 的按下仍然能把子块解约束（手势修饰键仍然有效）', () => {
  const { nodes } = canvasOfDefaultFlow()
  assert.equal(isModifierEvent(POINTER_DOWN_WITH_CTRL), true, 'Ctrl 按下的手势要判为修饰键手势')
  assert.equal(isModifierEvent(POINTER_DOWN_WITH_META), true, 'Meta（macOS 的 Command）同理')
  assert.equal(isModifierEvent(POINTER_DOWN_PLAIN), false, '不按修饰键的普通按下不能判为修饰键手势')

  const afterCtrlDown = pointerDownOnNode(nodes, CHILD_ID, POINTER_DOWN_WITH_CTRL)
  const child = nodeOf(afterCtrlDown, CHILD_ID)
  assert.equal(child.parentNode, undefined, 'Ctrl 按下即解约束（早于 Vue Flow 的拖拽快照）')
  assert.equal(child.extent, undefined, '解约束必须清掉 extent，否则这一整段拖拽仍被夹在容器里')
  assert.equal(child.zIndex, 0, '解约束后层级归 0')
  assert.equal(
    child.position.x,
    Math.round(nodeOf(nodes, CONTAINER_ID).position.x + nodeOf(nodes, CHILD_ID).position.x),
    'position 换算成绝对坐标，节点不会跳回原点',
  )
})

test('问题1 回归：Ctrl 拖出 → 拖回容器 → 不按 Ctrl 再拖，子块仍被 extent 夹在容器内', () => {
  const { nodes: initial } = canvasOfDefaultFlow()

  // 第 1 步：Ctrl 把子块拖出容器（解约束 + 落到容器外）—— 用户主动按了 Ctrl，行为正确
  const detached = pointerDownOnNode(initial, CHILD_ID, POINTER_DOWN_WITH_CTRL)
  const droppedOutside = applyPatch(
    detached,
    CHILD_ID,
    planMigration(detached, CHILD_ID, null, { detachAt: { x: 1500, y: 820 } }),
  )
  assert.equal(nodeOf(droppedOutside, CHILD_ID).parentNode, undefined, '（前置）拖出后它是顶层节点')

  // 第 2 步：把它拖回容器画布区 → 松手结算，确实成为子块
  const reattached = applyPatch(
    droppedOutside,
    CHILD_ID,
    planMigration(droppedOutside, CHILD_ID, CONTAINER_ID),
  )
  const childAfterAttach = nodeOf(reattached, CHILD_ID)
  assert.equal(childAfterAttach.parentNode, CONTAINER_ID, '拖回容器确实成为子块')
  assert.equal(childAfterAttach.extent?.range, 'parent', '入容器时 extent 已补齐')
  assert.equal(
    extentTakesEffect(childAfterAttach),
    true,
    '成为子块后 extent 生效：下一次拖拽会被夹回画布区',
  )

  // 第 3 步：用户【不按 Ctrl】再按下该子块 —— 这次按下不能解约束
  // （修复前：全局状态残留为 true，这一步会把 extent 清掉，于是不按 Ctrl 也能把子块拖出容器）
  const afterPlainPointerDown = pointerDownOnNode(reattached, CHILD_ID, POINTER_DOWN_PLAIN)
  const childAfterPointerDown = nodeOf(afterPlainPointerDown, CHILD_ID)
  assert.equal(
    childAfterPointerDown.parentNode,
    CONTAINER_ID,
    '不按 Ctrl 的按下不能把子块解约束（parentNode 必须还在）',
  )
  assert.equal(
    childAfterPointerDown.extent?.range,
    'parent',
    '不按 Ctrl 的按下也不能清掉 extent（清掉 = 不按 Ctrl 也能把子块拖出容器）',
  )
  assert.equal(
    extentTakesEffect(childAfterPointerDown),
    true,
    '不按 Ctrl 的拖拽仍必须被 extent 夹在容器画布区内',
  )
})

test('问题1 回归：解约束只作用于子块，顶层节点不受任何按下影响', () => {
  const { nodes } = canvasOfDefaultFlow()
  const untouched = pointerDownOnNode(nodes, UPSTREAM_ID, POINTER_DOWN_PLAIN)
  assert.deepEqual(untouched, nodes, '普通按下对顶层节点没有任何副作用')

  // 反向对照：顶层节点即使带了 Ctrl 也不需要解约束（它本来就没有 parentNode / extent）
  const withCtrl = pointerDownOnNode(nodes, UPSTREAM_ID, POINTER_DOWN_WITH_CTRL)
  assert.deepEqual(withCtrl, nodes, 'Ctrl 按下对顶层节点同样没有副作用')
})

/* ===== 问题2：拖回容器后连线的内部边归一化 ===== */

/**
 * 「Ctrl 拖出 → 顶层状态下连到容器 → 再拖回容器 → 归一化」之后的画布状态。
 *
 * 每一步都复用画布页真实调用的那份纯函数 / 同一套清理与归一化口径，不另抄算式：
 * 1. Ctrl 拖出：`planMigration(..., null, { detachAt })`（= 解约束 + 出容器换算）；
 *    同时按画布页 `cleanupCrossLevelEdges(..., { detach: true })` 的口径删掉「子块 ↔ 原容器」的内部边
 *    （这就是用户拖出后不得不重新连线的原因）；
 * 2. 顶层状态下新建「子块 → 容器」的连线：此时 `iterationOwnerOf` 为 null，`handleConnect` 走
 *    `CANVAS_EDGE_STYLE`（没有 `zIndex`、handle 是外侧 `out / in`）；
 * 3. 再拖回容器：`settleNodeLevel` 写节点并删跨层边，随后 `normalizeIterationEdges()`
 *    （= 本文件复刻的那一步：对每条边取 `internalEdgePatchOf` 并写回）把内部边口径补齐。
 */
const reattachedState = () => {
  const { nodes: withChild, edges: innerEdges } = canvasOfDefaultFlow()

  const detachedNodes = applyPatch(
    withChild,
    CHILD_ID,
    planMigration(withChild, CHILD_ID, null, { detachAt: { x: 1500, y: 820 } }),
  )
  const isChildToContainer = (edge) => (edge.source === CHILD_ID && edge.target === CONTAINER_ID)
    || (edge.target === CHILD_ID && edge.source === CONTAINER_ID)
  const childToContainerEdges = innerEdges.filter(isChildToContainer)
  // 首个子块只与容器之间有一条内部边（入口）；出口边挂在末尾子块上
  assert.equal(
    childToContainerEdges.length,
    1,
    '（前置）首个子块与容器之间的内部边只有入口那一条',
  )
  const keptEdges = innerEdges.filter((edge) => !isChildToContainer(edge))
  assert.equal(
    keptEdges.length,
    innerEdges.length - childToContainerEdges.length,
    '（前置）脱离容器时这些内部边被清理',
  )

  // 顶层状态下的重新连线：两端是外侧 handle，且没有 zIndex（= CANVAS_EDGE_STYLE）
  const redrawnEdge = {
    id: `e-${CHILD_ID}-${CONTAINER_ID}`,
    source: CHILD_ID,
    target: CONTAINER_ID,
    sourceHandle: 'out',
    targetHandle: 'in',
    animated: true,
    style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  }

  const plan = planMigration(detachedNodes, CHILD_ID, CONTAINER_ID)
  const nodes = applyPatch(detachedNodes, CHILD_ID, plan)

  // 画布页迁移结算后的那一步：把「新变为内部」的连线补成内部边口径
  const patch = internalEdgePatchOf(nodes, redrawnEdge)
  const normalizedEdge = patch ? { ...redrawnEdge, ...patch } : redrawnEdge
  return {
    nodes,
    edges: [...keptEdges, normalizedEdge],
    edge: normalizedEdge,
    redrawnEdge,
    patch,
    plan,
  }
}

test('问题2 回归：拖回容器后，留下的连线被归一到内部边口径（zIndex + 内侧 handle）', () => {
  const { nodes, edges, edge, redrawnEdge, patch, plan } = reattachedState()

  // 排除性证据：迁移算式本身完整 —— 「成为子块」这部分从来不是问题
  assert.equal(plan.parentNode, CONTAINER_ID, '入容器要写回 parentNode')
  assert.equal(plan.extent?.range, 'parent', '入容器要补 extent（拖拽夹取靠它）')
  assert.equal(plan.zIndex, ITERATION_LAYER.child, '入容器要抬高子块层级')
  const container = nodeOf(nodes, CONTAINER_ID)
  const topArea = estimatedTopAreaOf(container.data)
  const rect = canvasRectOf(container, topArea)
  const absolute = absolutePositionOf(nodes, nodeOf(nodes, CHILD_ID))
  assert.ok(
    isPointInRect(absolute, rect),
    `入容器后子块的绝对坐标必须落在可见画布区内（画布区顶边 y=${container.position.y + canvasTopOf(topArea)}）`,
  )

  // 这条边不会被清理 —— 「容器 ↔ 它自己的子块」被刻意判为合法（折叠后等价于子图 START / END），
  // 所以线确实还在 store 里，问题从来不是「线被删了」而是「线看不见」
  assert.deepEqual(
    invalidEdgesOf(nodes, edges).map((item) => item.id),
    [],
    '迁移后这条边不算跨层，不会被清理',
  )
  assert.equal(
    iterationOwnerOf(nodes, CHILD_ID, CONTAINER_ID),
    CONTAINER_ID,
    '拖回容器后它就是迭代体内部边（折叠回后端会落进容器的 edges 里）',
  )

  // 归一化补丁：必须给这条边补上内部边口径
  assert.ok(patch, '迁移后这条边需要归一化（修复前补丁为 null ⇒ 线一直带着顶层样式被卡片盖住）')
  assert.equal(redrawnEdge.zIndex, undefined, '（前置）顶层样式没有 zIndex')
  assert.equal(
    edge.zIndex,
    ITERATION_LAYER.edge,
    '归一化后必须带 ITERATION_EDGE_STYLE 的 zIndex，连线才会压过不透明的容器卡片',
  )
  assert.equal(
    edge.targetHandle,
    ITERATE_EXIT_HANDLE,
    '连到容器的那一端要改用内侧 handle（否则路径从容器外侧的主链路 handle 进出、横穿卡片）',
  )
  assert.equal(edge.sourceHandle, 'out', '子块那一端保持自己的出连接点（内部边本来就是这个形态）')
  assert.equal(
    edge.animated,
    ITERATION_EDGE_STYLE.animated,
    '内部边的动画 / 描边等口径与其它来源的内部边保持一致',
  )

  // 幂等：已经合规的边不再产生补丁（所以画布页可以无脑整表过一遍）
  assert.equal(internalEdgePatchOf(nodes, edge), null, '已合规的内部边不应再产生补丁')
})

test('问题2 回归：顶层连线不会被归一化（口径只作用于迭代体内部）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 主链路连入：指向容器、且不是来自迭代体内部子块的那条
  const outerEdge = edges.find((edge) => (
    edge.target === CONTAINER_ID && !CHILD_IDS.includes(edge.source)
  ))
  assert.ok(outerEdge, '默认流程里存在「顶层节点 → 容器」的主链路连线')
  assert.equal(
    internalEdgePatchOf(nodes, outerEdge),
    null,
    '顶层 → 容器（主链路连入）不是迭代体内部边，不能被套上内部边样式',
  )
  assert.equal(internalEdgePatchOf(nodes, { ...outerEdge, id: 'x', source: '100001', target: '100002' }), null)
  assert.equal(internalEdgePatchOf(nodes, { id: 'no-endpoints' }), null, '缺端点的脏数据不报错、返回 null')
})

/* ===== 问题2：层叠顺序（可见性结论） ===== */

/** 节点自身的 zIndex（`vue-flow-core.mjs:9478`：`Number(node.zIndex ?? style.zIndex ?? 0)`） */
const nodeZIndexOf = (node) => Number(node?.zIndex ?? node?.style?.zIndex ?? 0)

/**
 * 节点在画布上的实际 z（`vue-flow-core.mjs:3854-3860` 的 `getXYZPos` + `:9525-9547` 的 watcher）：
 * 有父节点时是 `max(父 z, 自身 zIndex) + 1`，顶层节点就是自身 zIndex。
 */
const computedZOf = (node, parent = null) => (
  parent ? Math.max(computedZOf(parent), nodeZIndexOf(node)) + 1 : nodeZIndexOf(node)
)

/** 边的 z（`vue-flow-core.mjs:4333-4345` 的 `getEdgeZIndex`：只有 `typeof edge.zIndex === 'number'` 才采用） */
const edgeZOf = (edge) => (typeof edge?.zIndex === 'number' ? edge.zIndex : 0)

/**
 * 绘制顺序模型（口径对齐 Vue Flow 1.48.2）：
 * - 边：每条边是一个独立的 `<svg class="vue-flow__edges" style="z-index: …">`（`:9363-9373`）；
 * - 节点：渲染在 `.vue-flow__nodes` 里，DOM 顺序在 edges 层**之后**（`dist/style.css:64-67 / 135-138`）；
 * - 同 `z-index` 时，DOM 靠后的层画在上面。
 * `layer` 取 0 = edges 层、1 = nodes 层。
 */
const paintOrder = (items) => [...items].sort((a, b) => (a.z - b.z) || (a.layer - b.layer))

test('问题2 回归：归一化之后，内部连线画在容器卡片之上（不会再被盖住）', () => {
  const { nodes, edge, redrawnEdge } = reattachedState()
  const containerZ = computedZOf(nodeOf(nodes, CONTAINER_ID))

  const paintedAfterContainer = (edgeZ) => {
    const stack = paintOrder([
      { id: `edge:${edge.id}`, layer: 0, z: edgeZ },
      { id: `node:${CONTAINER_ID}`, layer: 1, z: containerZ },
    ])
    return stack.findIndex((item) => item.id === `edge:${edge.id}`)
      > stack.findIndex((item) => item.id === `node:${CONTAINER_ID}`)
  }

  // 机制对照（修复前就是这样）：没有 zIndex 的边与容器卡片同为 0，而 edges 层在 DOM 里更靠前 → 被盖住
  assert.equal(
    paintedAfterContainer(edgeZOf(redrawnEdge)),
    false,
    `顶层样式的 zIndex=${edgeZOf(redrawnEdge)} 会被容器卡片盖住（只剩卡片外两小截）`,
  )

  // 修复后：内部边口径把它抬到卡片之上，同时仍低于子块卡片（否则线会画在子块上面）
  assert.equal(
    paintedAfterContainer(edgeZOf(edge)),
    true,
    `zIndex = ITERATION_LAYER.edge(${ITERATION_LAYER.edge}) 时内部连线画在容器卡片之上`,
  )
  assert.ok(
    ITERATION_LAYER.edge < ITERATION_LAYER.child,
    '内部连线要低于子块，否则线会画在子块卡片上面',
  )
})

/* ===== 源码契约：守住复刻，并锁定两处修复不能被改回去 ===== */

test('源码契约：修饰键取自当前手势（无全局状态）', () => {
  const view = readSource('../src/views/workflow2/WorkflowView.vue')

  // 判定本身与用例里的复刻一致（只看 ctrlKey / metaKey）
  assert.match(
    view,
    /const isModifierEvent = \(event\) => Boolean\(event\?\.ctrlKey \|\| event\?\.metaKey\)/,
    'isModifierEvent 与用例里的复刻一致',
  )

  // ★ 修复点 1：解约束分支读「本次 pointerdown 事件」，不再依赖全局键盘状态
  assert.match(
    view,
    /if \(!isModifierEvent\(event\)\) return/,
    '解约束分支以当前事件的修饰键为唯一判据',
  )
  assert.equal(
    view.includes('modifierActive'),
    false,
    '不再维护全局修饰键状态（那份状态会在 keyup 上永远复位不了）',
  )
  assert.equal(
    view.includes("'keyup'"),
    false,
    '不再监听键盘 keyup（修饰键的 keyup 事件里 ctrlKey 已是 false，做不了复位守卫）',
  )

  // 写回字段与用例里的 pointerDownOnNode 一致
  assert.match(
    view,
    /updateNode\(node\.id, \{[\s\S]{0,200}parentNode: undefined,[\s\S]{0,140}extent: undefined,[\s\S]{0,140}zIndex: 0,[\s\S]{0,200}position: \{ x: Math\.round\(node\.computedPosition\.x\)/,
    '解约束写回 parentNode / extent / zIndex / 绝对坐标',
  )

  // 每次按下都从干净状态开始：否则「Ctrl + 点击」（没有拖拽、不派发 node-drag-stop）留下的标记
  // 会被下一轮拖拽当成「脱离容器」，去删与本轮无关的连线
  assert.match(
    view,
    /const handleCanvasPointerDown = \(event\) => \{[\s\S]{0,400}detachArmed\.value = false[\s\S]{0,80}detachFrom\.value = null/,
    'handleCanvasPointerDown 先清上一轮的解约束标记，再判本次手势',
  )
})

test('源码契约：迁移结算后会归一化内部连线，且口径只有一处实现', () => {
  const view = readSource('../src/views/workflow2/WorkflowView.vue')
  const iteration = readSource('../src/views/workflow2/utils/iteration.js')

  // 新建边时按 iterationOwnerOf 选内部边样式（这条一直是合规的来源）
  assert.match(
    view,
    /iterationOwnerOf\(getNodes\.value, source, target\)\s*\n\s*\? ITERATION_EDGE_STYLE\s*\n\s*: CANVAS_EDGE_STYLE/,
    'handleConnect 只负责新建边的样式',
  )

  // ★ 修复点 2：迁移路径（settleNodeLevel → cleanupCrossLevelEdges → normalizeIterationEdges）
  // 调用归一化；但内部边口径不在视图层内联，收在 utils/iteration 里
  const settleStart = view.indexOf('const settleNodeLevel = (node, event, fromContainer = null)')
  const settleEnd = view.indexOf('const handleNodeDragStop')
  assert.ok(settleStart > 0 && settleEnd > settleStart, '能定位 settleNodeLevel → handleNodeDragStop 这段实现')
  const migrationPath = view.slice(settleStart, settleEnd)
  assert.match(migrationPath, /normalizeIterationEdges\(\)/, '迁移结算后必须归一化内部连线')
  assert.equal(
    migrationPath.includes('ITERATION_EDGE_STYLE'),
    false,
    '迁移路径不内联内部边样式（口径只在 utils/iteration 里）',
  )
  assert.equal(migrationPath.includes('sourceHandle'), false, '迁移路径不内联 handle 改写')
  assert.equal(migrationPath.includes('targetHandle'), false, '迁移路径不内联 handle 改写')

  // 内部边字段口径只有一处实现：展开定义（toCanvasInnerEdge）与迁移归一化（internalEdgePatchOf）共用它
  assert.match(iteration, /function innerEdgeFieldsOf\(edge, containerId\)/, '内部边字段口径有唯一实现')
  assert.match(
    iteration,
    /\.\.\.innerEdgeFieldsOf\(\{ source, target \}, containerId\)/,
    'toCanvasInnerEdge 用同一份口径',
  )
  assert.match(
    iteration,
    /const fields = innerEdgeFieldsOf\(edge, ownerId\)/,
    'internalEdgePatchOf 用同一份口径',
  )
  assert.match(
    iteration,
    /export function internalEdgePatchOf\(nodes, edge\)/,
    '归一化补丁函数对外可用（画布页据此写回）',
  )
  assert.match(
    iteration,
    /if \(edge\?\.source === containerId\) fields\.sourceHandle = ITERATE_ENTRY_HANDLE/,
    '容器那一端改用内侧入口 handle',
  )
  assert.match(
    iteration,
    /if \(edge\?\.target === containerId\) fields\.targetHandle = ITERATE_EXIT_HANDLE/,
    '容器那一端改用内侧出口 handle',
  )
})

test('源码契约：夹具前提（顶层节点无 parentNode / 子块带 extent）', () => {
  const { nodes } = canvasOfDefaultFlow()
  assert.equal(nodeOf(nodes, UPSTREAM_ID)?.parentNode, undefined, `夹具前提：${UPSTREAM_ID} 是顶层节点`)
  assert.equal(nodeOf(nodes, CHILD_ID)?.parentNode, CONTAINER_ID, `夹具前提：${CHILD_ID} 是容器子块`)
  assert.equal(nodeOf(nodes, CHILD_ID)?.extent?.range, 'parent', '夹具前提：子块带画布区 extent')
  assert.equal(ITERATE_ENTRY_HANDLE, 'iterate-in', '内侧入口 handle id 与组件一致')
  assert.equal(ITERATE_EXIT_HANDLE, 'iterate-out', '内侧出口 handle id 与组件一致')
})
