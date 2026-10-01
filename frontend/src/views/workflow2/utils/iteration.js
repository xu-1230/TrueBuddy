/**
 * 迭代容器（type = 4）在画布上的展开 / 折叠与布局。
 *
 * 后端把迭代体（`blocks` + 节点级 `edges`）内嵌在迭代节点里，且边以迭代节点自身 id 作虚拟入口 / 出口
 * （`迭代节点id → 子块` 等于子图 START → 子块，`子块 → 迭代节点id` 等于子块 → END）；
 * 画布则把迭代体物化成真正挂在容器下的子节点（`parentNode` + 相对坐标）与普通连线。
 * 本模块负责两种表示之间的等价转换，以及「卡片 + 画布区」节点尺寸与子块坐标的计算，
 * 画布页 / 节点组件只调用导出函数，不自行估算尺寸。
 * 「迭代开始 / 结束」标记与内侧连接点不在本模块口径内：组件把它们锚定在画布区的左 / 右侧、
 * 与首 / 末子块**上下居中**（同一水平线，跟随子块实际位置），拖拽改尺寸时自动跟随。
 *
 * 已知限制：{@link expandIteration} 只展开**一层**——迭代体里再放一个迭代节点时，内层迭代体的 `blocks`
 * 原样留在子块定义里（提交后端不丢数据），但内层子块不在画布上展开；组件会在内层卡片上标出「嵌套 N」提示。
 */
import { MarkerType } from '@vue-flow/core'

/** 迭代节点的类型编码 */
export const ITERATE_NODE_TYPE = '4'

/** 迭代体入口 / 出口连在容器上的 handle id（与 IterateNode.vue 中的 Handle 一一对应） */
export const ITERATE_ENTRY_HANDLE = 'iterate-in'
export const ITERATE_EXIT_HANDLE = 'iterate-out'

/**
 * 节点布局口径：纵向为「卡片顶部内容区（上内边距 + header + 变量行）→ canvasInset → 子节点画布区
 * → 卡片底部内边距」；画布区内横向为「迭代开始标记 → 子块区 → 迭代结束标记」，
 * 三者**上下居中**（同一水平线，内部连线水平进出子块）。
 * 运行状态与汇总产出不在画布区内，展示在卡片正下方（与其它节点的结果面板一致）。
 *
 * 画布区**嵌在卡片内部**：左右与底部各内缩 {@link ITERATION_LAYOUT.canvasInset}，
 * 顶部偏移由顶部内容区高度推导（见 {@link cardTopAreaHeightOf} / {@link canvasTopOf}），
 * 因此调整卡片内容（变量行数）会自动带动外侧连接点、画布区与子块的纵向位置。
 */
export const ITERATION_LAYOUT = {
  /** 节点宽度 = 卡片宽度：只有一两个子块时也保持可见的内部区域 */
  minWidth: 400,
  /** 画布区最小高度 */
  canvasMinHeight: 240,
  /** 画布区相对卡片的内缩（左右 / 底部）与「变量行 → 画布区」之间的间距 */
  canvasInset: 12,
  /** 画布区上下内边距 */
  canvasPaddingY: 16,
  /** 画布区左右内边距与子块间距 */
  paddingX: 24,
  childGap: 24,
  /** 子块尺寸预估：用于算画布区高度（子块实际高度由卡片内容决定，这里只求区域够大不裁切） */
  childWidth: 272,
  childHeight: 160,
  /**
   * 迭代开始 / 结束标记：图标胶囊（只有图标），宽高贴合图标（14 + 内边距 12 + 边框 2 = 28）。
   * 该常量同时决定横向口径：containerWidthOf（节点宽度）/ spaceBetweenMarkersOf / childPositionOf。
   */
  markerWidth: 28,
  markerHeight: 28,
  /** 标记与子块之间的间距（以及画布区内边距的组成） */
  markerGap: 12,
}

/**
 * 卡片口径：`.node-card` 就是整个节点（白底 + 12px 圆角 + 边框 + 投影），画布区嵌在它内部。
 * 顶部内容区（上内边距 + header + 变量行）的高度按变量行数推导，**组件用同一个
 * {@link cardTopAreaHeightOf} 显式设定卡片高度**，保证外侧连接点、画布区（及其中的标记 / 子块）
 * 的位置与卡片的视觉高度严格一致（不依赖内容撑开）。
 */
export const CARD_LAYOUT = {
  /** 卡片上下内边距（与 .node-header 的 padding 一致） */
  paddingY: 12,
  /** header 行高（28px 图标 + 上下留白） */
  headerHeight: 28,
  /** 单个变量区的高度：VariableTags 一行（line-height 22 + 行距 8） */
  variableRowHeight: 30,
  /** 末行变量区的额外留白（VariableTags 的 last 行 padding-bottom 比普通行多 4px） */
  tailPadding: 4,
}

/**
 * 迭代体的层叠顺序（Vue Flow 的 `zIndex`）：父卡片（默认 0）< 内部连线 < 子节点卡片。
 *
 * 必须是这个固定顺序：父卡片底不透明，内部连线要高过它才看得见；而子节点又必须高过内部连线，
 * 否则连线会画在子块卡片上面。画布页同时关闭了 `elevateNodesOnSelect`，
 * 避免选中父节点时它被抬到 1000、把子节点和内部连线反压回去。
 */
export const ITERATION_LAYER = {
  /** 迭代体内部连线 */
  edge: 1,
  /** 迭代体子块（画布子节点） */
  child: 2,
}

/**
 * 迭代体内部连线样式：与画布顶层 CANVAS_EDGE_STYLE 保持一致，两种来源的线看起来完全一样。
 *
 * `zIndex` 是必需的：Vue Flow 默认把连线渲染在节点层**之下**，而迭代节点本身就是一张不透明卡片，
 * 内部连线会被卡片底色整段盖住、完全看不见（层叠顺序见 {@link ITERATION_LAYER}）。
 */
export const ITERATION_EDGE_STYLE = {
  zIndex: ITERATION_LAYER.edge,
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: MarkerType.Arrow, color: 'var(--tb-primary)', width: 15, height: 15 },
}

/** 后端节点字段（与 WorkflowNode 对齐）：折叠回 blocks 时只保留这些，剔除画布运行时字段 */
const BACKEND_NODE_FIELDS = ['id', 'type', 'position', 'data', 'blocks', 'edges']

/** 已声明的变量个数：字符串写法与对象写法都算，空名忽略（与 VariableTags 的过滤口径一致） */
const declaredCountOf = (list) => (Array.isArray(list)
  ? list.filter((item) => (typeof item === 'string' ? item : item?.name)).length
  : 0)

/** 顶部卡片里的变量行数：输入 + 迭代变量 + 输出 */
export function cardRowCountOf(data) {
  return declaredCountOf(data?.inputs)
    + declaredCountOf(data?.variableParameters)
    + declaredCountOf(data?.outputs)
}

/** 卡片顶部内容区高度：上下内边距 + header 行 + 变量行（含末行留白），也就是画布区开始之前的那一段 */
export function cardTopAreaHeightOf(rowCount = 0) {
  const rows = Math.max(rowCount, 0)
  return CARD_LAYOUT.paddingY * 2
    + CARD_LAYOUT.headerHeight
    + (rows ? rows * CARD_LAYOUT.variableRowHeight + CARD_LAYOUT.tailPadding : 0)
}

/** 外侧连接点（in / out）相对节点根的 y：贴在卡片左右边缘，与顶部内容区中线对齐 */
export function cardAnchorYOf(rowCount = 0) {
  return cardTopAreaHeightOf(rowCount) / 2
}

/** 画布区顶部偏移（相对节点根）：顶部内容区 + 与画布区之间的内缩间距 */
export function canvasTopOf(rowCount = 0) {
  return cardTopAreaHeightOf(rowCount) + ITERATION_LAYOUT.canvasInset
}

/**
 * 子块区起点（画布区内相对值）：子块区在画布区内**垂直居中**（上下留白均分，不小于上内边距）。
 * 「迭代开始 / 结束」标记固定在画布区的垂直中点（组件用 CSS 锚定），不随子块拖动而移动。
 *
 * @param {number} childCount 子块个数
 * @returns {number} 单位 px
 */
export function childrenTopOf(childCount) {
  return Math.max(
    ITERATION_LAYOUT.canvasPaddingY,
    Math.round((canvasHeightOf(childCount) - childrenBlockHeightOf(childCount)) / 2),
  )
}

/** 子块区高度：childCount 个子块纵向排完（空容器也按 1 个子块占位，两个标记之间不留空） */
function childrenBlockHeightOf(childCount) {
  const count = Math.max(childCount, 1)
  return count * ITERATION_LAYOUT.childHeight + (count - 1) * ITERATION_LAYOUT.childGap
}

/**
 * 画布区高度（画布区自身口径）：上下内边距 + 子块区（标记在左右两侧，不占纵向空间），
 * 不小于 {@link ITERATION_LAYOUT.canvasMinHeight}。
 * 运行状态与汇总产出在卡片正下方（与其它节点的结果面板一致），不占用画布区高度。
 *
 * @param {number} childCount 子块个数
 * @returns {number} 单位 px
 */
export function canvasHeightOf(childCount) {
  return Math.max(
    ITERATION_LAYOUT.canvasMinHeight,
    ITERATION_LAYOUT.canvasPaddingY * 2 + childrenBlockHeightOf(childCount),
  )
}

/**
 * 第 index 个子块相对**节点根**的坐标（Vue Flow 的 `parentNode` 语义）：纵向排列，
 * 横向放在「迭代开始」与「迭代结束」两个标记之间（空间富余时在两者之间居中），
 * 整个子块区在画布区内**垂直居中**（与固定在垂直中点的标记同一水平线）。
 *
 * @param {number} index 子块下标
 * @param {number} childCount 子块总数（决定子块区的垂直居中偏移）
 * @param {number} rowCount 顶部卡片的变量行数（决定画布区顶部偏移）
 * @returns {{ x: number, y: number }} 相对节点盒左上角的坐标
 */
export function childPositionOf(index, childCount, rowCount = 0) {
  return {
    x: childXOf(),
    y: canvasTopOf(rowCount)
      + childrenTopOf(childCount)
      + index * (ITERATION_LAYOUT.childHeight + ITERATION_LAYOUT.childGap),
  }
}

/**
 * 子块在画布区内的横向坐标（相对节点根，**x 方向的唯一来源**）：
 * 画布区在卡片内左右内缩，子块夹在两个标记之间（左标记 + 间距之后，剩余空间内居中）。
 *
 * @param {number} canvasWidth 画布区宽度；默认取设计宽度口径，传**真实宽度**时可用于
 *   「节点被拖宽 / 拖窄后子块重新居中」（见 {@link childXOfNodeWidth}）
 * @returns {number} 相对节点盒左上角的 x
 */
export function childXOf(canvasWidth = canvasWidthOf()) {
  const spaceBetween = canvasWidth
    - (ITERATION_LAYOUT.paddingX + ITERATION_LAYOUT.markerWidth + ITERATION_LAYOUT.markerGap) * 2
  return ITERATION_LAYOUT.canvasInset
    + ITERATION_LAYOUT.paddingX
    + ITERATION_LAYOUT.markerWidth
    + ITERATION_LAYOUT.markerGap
    + Math.max(0, Math.round((spaceBetween - ITERATION_LAYOUT.childWidth) / 2))
}

/**
 * 节点盒宽度 → 子块横坐标：节点被拖拽改宽度后，画布区比设计宽度更宽 / 更窄，
 * 子块要跟着重新在两个标记之间居中（否则会一直停在设计宽度的位置上）。
 *
 * @param {number} nodeWidth 节点盒的当前宽度（px）
 * @returns {number} 相对节点盒左上角的 x
 */
export function childXOfNodeWidth(nodeWidth) {
  const canvasWidth = Math.max(
    ITERATION_LAYOUT.paddingX * 2 + ITERATION_LAYOUT.childWidth,
    (Number(nodeWidth) || 0) - ITERATION_LAYOUT.canvasInset * 2,
  )
  return childXOf(canvasWidth)
}

/** 节点宽度 = 卡片宽度：要放得下「开始标记 → 子块 → 结束标记」的横向排列（含内缩），不小于 minWidth */
function containerWidthOf() {
  return Math.max(
    ITERATION_LAYOUT.minWidth,
    ITERATION_LAYOUT.canvasInset * 2
      + (ITERATION_LAYOUT.paddingX + ITERATION_LAYOUT.markerWidth + ITERATION_LAYOUT.markerGap) * 2
      + ITERATION_LAYOUT.childWidth,
  )
}

/** 画布区宽度：卡片宽度左右各内缩 canvasInset */
function canvasWidthOf() {
  return containerWidthOf() - ITERATION_LAYOUT.canvasInset * 2
}

/**
 * 拖拽改尺寸的下限：宽度要放得下子块与画布区内边距，高度要装得下顶部内容区 + 画布区 + 底部内边距，
 * 而画布区高度又必须装得下**当前子块**（与初始尺寸 {@link containerSizeOf} 同源）。
 * <p>旧实现的高度恒用 `canvasMinHeight`、与子块数量无关，子块一多就能缩到「子块溢出画布区」。
 *
 * @param {number} rowCount 顶部卡片的变量行数
 * @param {number} childCount 子块个数
 * @returns {{ width: number, height: number }} 单位 px
 */
export function minNodeSizeOf(rowCount = 0, childCount = 0) {
  return containerSizeOf(childCount, rowCount)
}

/**
 * 节点尺寸（`expandIteration` 写入的**初始**尺寸）：纵向依次为「卡片顶部内容区 → canvasInset →
 * 画布区（内含横向的「开始标记 → 子块 → 结束标记」）→ 卡片底部内边距」。
 * 用户可拖拽卡片右下角改变尺寸（组件经 `updateNode` 更新节点 style，画布区与标记随 CSS 锚定伸缩）；
 * 结果区在卡片正下方，不计入节点盒声明高度（与其它节点的结果面板一样溢出在盒外）。
 *
 * @param {number} childCount 子块个数
 * @param {number} rowCount 顶部卡片的变量行数
 * @returns {{ width: number, height: number }} 单位 px
 */
export function containerSizeOf(childCount, rowCount = 0) {
  return {
    width: containerWidthOf(),
    height: canvasTopOf(rowCount)
      + canvasHeightOf(childCount)
      + ITERATION_LAYOUT.canvasInset,
  }
}

/**
 * 迭代节点 → 画布表示：`blocks` 物化为挂在容器下的子节点，节点级 `edges` 转成内部连线。
 * <p>只展开一层：子块本身若是迭代节点，它的 `blocks` / `edges` 原样保留在定义里（不递归展开）。
 *
 * @param {object} node 后端结构的迭代节点（含 blocks / edges）
 * @returns {{ nodes: Array, edges: Array }} 可直接并入画布 nodes / edges 的定义
 */
export function expandIteration(node) {
  const id = node?.id
  if (!id) return { nodes: [], edges: [] }

  const blocks = Array.isArray(node.blocks) ? node.blocks : []
  const innerEdges = Array.isArray(node.edges) ? node.edges : []
  // 卡片顶部内容区（header + 变量行）占掉节点上部一段高度：变量行数决定它，进而决定画布区与子块的起点
  const rowCount = cardRowCountOf(node.data)
  const { width, height } = containerSizeOf(blocks.length, rowCount)

  const container = {
    ...node,
    blocks: [],
    edges: null,
    style: { width: `${width}px`, height: `${height}px` },
  }
  const children = blocks.map((block, index) => ({
    ...pickBackendNode(block),
    parentNode: id,
    extent: 'parent',
    // 子块要高于父卡片与内部连线（见 ITERATION_LAYER），否则会被卡片底色或连线盖住
    zIndex: ITERATION_LAYER.child,
    position: childPositionOf(index, blocks.length, rowCount),
  }))
  const edges = innerEdges.map((edge) => toCanvasInnerEdge(edge, id))

  return { nodes: [container, ...children], edges }
}

/**
 * 画布表示 → 后端定义：`parentNode` 子节点折叠回所属迭代节点的 `blocks`，内部连线折叠回节点级 `edges`。
 * 没有迭代容器时原样返回，调用方可无脑先折叠再提交。
 *
 * @param {Array} nodes 画布节点
 * @param {Array} edges 画布连线
 * @returns {{ nodes: Array, edges: Array }} 只有顶层节点 / 顶层连线的定义
 */
export function collapseIterations(nodes = [], edges = []) {
  const containers = nodes.filter((node) => node?.type === ITERATE_NODE_TYPE)
  if (!containers.length) return { nodes, edges }

  // 每个容器的子块集合：一条边只要任一端落在某个容器的「自身 / 子块」上，就属于那个迭代体
  const childIdsOf = new Map(
    containers.map((container) => [
      container.id,
      new Set(nodes.filter((node) => node.parentNode === container.id).map((node) => node.id)),
    ]),
  )

  const innerEdges = []
  const outerEdges = []
  edges.forEach((edge) => {
    const owner = containers.find((container) =>
      belongsToContainer(edge, container.id, childIdsOf.get(container.id)))
    if (owner) innerEdges.push({ ownerId: owner.id, edge })
    else outerEdges.push(edge)
  })

  const containerIds = new Set(containers.map((container) => container.id))
  /** 顶层节点：没有 parentNode，或 parentNode 指向**不存在的容器**（画布残留数据，不能静默丢弃） */
  const isTopLevel = (node) => !node.parentNode || !containerIds.has(node.parentNode)

  const collapsed = nodes
    .filter(isTopLevel)
    .map((node) => {
      if (node.type !== ITERATE_NODE_TYPE) return node

      const children = nodes.filter((child) => child.parentNode === node.id)
      const nodeEdges = innerEdges
        .filter((item) => item.ownerId === node.id)
        .map((item) => ({
          id: item.edge.id,
          sourceNodeID: item.edge.source,
          targetNodeID: item.edge.target,
        }))

      return {
        ...node,
        blocks: children.map((child) => toBackendNode(child)),
        edges: nodeEdges,
      }
    })

  return { nodes: collapsed, edges: outerEdges }
}

/**
 * 重算子块相对坐标与容器尺寸：子块增删或排布变化后调用，返回新的节点数组。
 *
 * @param {Array} nodes 画布节点
 * @param {string} containerId 迭代节点 id
 * @returns {Array} 新的节点数组（未命中的节点原样返回）
 */
export function layoutIteration(nodes, containerId) {
  const children = nodes.filter((node) => node.parentNode === containerId)
  const container = nodes.find((node) => node.id === containerId)
  const rowCount = cardRowCountOf(container?.data)
  const { width, height } = containerSizeOf(children.length, rowCount)

  return nodes.map((node) => {
    if (node.id === containerId) {
      return {
        ...node,
        style: { ...(node.style ?? {}), width: `${width}px`, height: `${height}px` },
      }
    }
    const index = children.findIndex((child) => child.id === node.id)
    if (index < 0) return node
    return { ...node, position: childPositionOf(index, children.length, rowCount) }
  })
}

/**
 * 内部边：**两端都落在该迭代体范围内**（容器自身或其子块）。
 * 只有一端落在容器上的边是「主链路连入 / 连出」，属于顶层边，不能折进迭代体。
 */
function belongsToContainer(edge, containerId, childIds) {
  const sourceInside = edge.source === containerId || childIds.has(edge.source)
  const targetInside = edge.target === containerId || childIds.has(edge.target)
  return sourceInside && targetInside
}

/**
 * 后端内部边 → 画布连线。
 * 连到容器自身的那一端要显式指定 handle，否则会默认落到容器外侧的入 / 出 handle 上。
 */
function toCanvasInnerEdge(edge, containerId) {
  const source = edge?.sourceNodeID ?? edge?.source
  const target = edge?.targetNodeID ?? edge?.target
  const canvasEdge = {
    id: edge?.id ?? `e-${source}-${target}`,
    source,
    target,
    ...ITERATION_EDGE_STYLE,
  }
  if (source === containerId) canvasEdge.sourceHandle = ITERATE_ENTRY_HANDLE
  if (target === containerId) canvasEdge.targetHandle = ITERATE_EXIT_HANDLE
  return canvasEdge
}

/** 画布子块 → 后端节点：只保留后端字段，去掉 parentNode / extent / dimensions 等画布运行时字段 */
function toBackendNode(child) {
  const node = {}
  BACKEND_NODE_FIELDS.forEach((field) => {
    if (child?.[field] !== undefined) node[field] = child[field]
  })
  node.position = { x: child?.position?.x ?? 0, y: child?.position?.y ?? 0 }
  node.blocks = Array.isArray(child?.blocks) ? child.blocks : []
  node.edges = Array.isArray(child?.edges) ? child.edges : null
  return node
}

/** 取后端节点字段（供 expand 复用，避免把画布运行时字段带进子节点） */
function pickBackendNode(node) {
  const result = {}
  BACKEND_NODE_FIELDS.forEach((field) => {
    if (node?.[field] !== undefined) result[field] = node[field]
  })
  return result
}
