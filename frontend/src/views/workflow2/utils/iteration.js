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
  /**
   * 标记与子块之间的间距（同时参与画布区内边距与节点宽度的推导）。
   *
   * 这个间距就是「迭代开始 / 结束」标记与子块之间那段内部连线的长度：太短（曾用 12px，
   * 屏幕上只有几个像素）会让连线既看不清、也点不中——用户想删掉它时总是点到子块卡片上
   * （卡片层级更高），于是「删不掉内部连线」，再连接时又被告知「已有连线」。
   * 24px 是「看得清 + 点得中」与「不把节点撑得太宽」之间的取值。
   */
  markerGap: 24,
}

/**
 * 卡片口径：`.node-card` 就是整个节点（白底 + 12px 圆角 + 边框 + 投影），画布区嵌在它内部。
 * 顶部内容区（上内边距 + header + 变量行）的高度按变量行数推导，**组件用同一个
 * {@link cardTopAreaHeightOf} 显式设定卡片高度**，保证外侧连接点、画布区（及其中的标记 / 子块）
 * 的位置与卡片的视觉高度严格一致（不依赖内容撑开）。
 */
export const CARD_LAYOUT = {
  /** 头部上下内边距（与 `.node-header` 的 padding 一致） */
  paddingY: 12,
  /** 头部图标尺寸（`.node-icon`）：头部内容高度取「图标」与「标题两行」的较大者 */
  iconHeight: 28,
  /**
   * 头部标题区高度：`.node-label` 行（20px）+ 行距（2px）+ `.node-subtitle` 行（18px）。
   *
   * 迭代卡片比其它卡片多一行副标题（「迭代」标识 + 遍历对象），只按图标高度（28px）算会把顶部
   * 内容区算矮十几像素——画布区（不透明底 + 边框的绝对定位块）会盖住最后一行变量胶囊。
   * 这个常量与 `node/IterateNode.vue` 里给 `.node-label` / `.node-subtitle` 写的显式 `line-height`
   * 一一对应，改样式时必须同步改这里（`tests/iteration.layout.test.js` 有契约用例兜底）。
   */
  titleContentHeight: 40,
  /** 单个变量区的高度：VariableTags 一行（胶囊 line-height 22 + 上下边框 2 + 行距 8） */
  variableRowHeight: 32,
  /** 末行变量区的额外留白（VariableTags 的 last 行 padding-bottom 比普通行多 4px） */
  tailPadding: 4,
  /** 卡片边框宽度（`.node-card` 的 border）：画布区锚定在 padding box 内，算画布区高度时要扣掉两侧 */
  borderWidth: 1,
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
  /**
   * 这里**刻意不设** `interactionWidth`（保持 Vue Flow 的默认值）。
   *
   * Vue Flow 会为每条边画一条同路径的透明粗线来接指针事件，它的两端**就是**迭代节点上的两个
   * 内侧 handle（`iterate-in` / `iterate-out`）；而内部连线的层级（`zIndex: ITERATION_LAYER.edge`）
   * 必须高于节点卡片，所以命中区一旦覆盖到端点，这两个连接点就再也收不到 pointerdown——
   * 表现为「从迭代开始 / 结束拖不出任何连线」（实测：handle 中心点的 elementFromPoint 命中的是
   * `path.vue-flow__edge-interaction`，而 handle 本身不可达）。
   *
   * 因此内部连线的可点性只能靠**它本身的长度**（见 {@link ITERATION_LAYOUT.markerGap}），
   * 不能靠加宽命中区——两个诉求在几何上是互斥的。
   */
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

/**
 * 卡片顶部内容区高度的**估算值**：头部上下内边距 + 头部内容（图标与标题两行取大）+
 * 变量行（含末行留白）。参数是**变量个数**，也就是「每个变量独占一行」时的行数上界。
 *
 * 实际渲染的变量胶囊是 flex-wrap（一行能并排多个，见 components/VariableTags.vue），
 * 所以这个估算值恒 ≥ 真实高度：它适合当**首帧兜底**与**尺寸下限**，但不能当最终值 ——
 * 拿它当最终值会让卡片在「同一行内新增变量」时凭空长高，画布区高度随之漂移
 * （本模块历史上就是这么错的）。真实高度必须测量（见 {@link estimatedTopAreaOf} 与
 * node/IterateNode.vue 的 ResizeObserver）。
 */
export function cardTopAreaHeightOf(rowCount = 0) {
  const rows = Math.max(rowCount, 0)
  return CARD_LAYOUT.paddingY * 2
    + Math.max(CARD_LAYOUT.iconHeight, CARD_LAYOUT.titleContentHeight)
    + (rows ? rows * CARD_LAYOUT.variableRowHeight + CARD_LAYOUT.tailPadding : 0)
}

/**
 * 由变量声明**估算**顶部内容区高度：只用在「还没测到真实高度」的场合 ——
 * `expandIteration` 写初始尺寸、`layoutIteration` 的调用方没传实测值、以及卡片首帧兜底。
 *
 * 它是实测值的上界（见 {@link cardTopAreaHeightOf}），因此拿它当尺寸下限永远安全
 * （不会出现装不下子块）；卡片挂载后会被 ResizeObserver 的实测值覆盖。
 *
 * @param {object} data 节点 data（含 inputs / variableParameters / outputs）
 * @returns {number} 单位 px
 */
export function estimatedTopAreaOf(data) {
  return cardTopAreaHeightOf(cardRowCountOf(data))
}

/**
 * 外侧连接点（in / out）相对节点根的 y：贴在卡片左右边缘，与顶部内容区中线对齐。
 *
 * @param {number} topAreaHeight 顶部内容区高度（优先传**实测值**，见 node/IterateNode.vue）
 */
export function cardAnchorYOf(topAreaHeight = 0) {
  return Math.max(Number(topAreaHeight) || 0, 0) / 2
}

/**
 * 画布区顶部偏移（相对节点根）：顶部内容区 + 与画布区之间的内缩间距。
 *
 * @param {number} topAreaHeight 顶部内容区高度（优先传**实测值**，见 node/IterateNode.vue）
 */
export function canvasTopOf(topAreaHeight = 0) {
  return Math.max(Number(topAreaHeight) || 0, 0) + ITERATION_LAYOUT.canvasInset
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
 * 画布区高度的**唯一口径**：卡片盒高 − 上下边框 − 顶部内容区高 − 底部内缩。
 *
 * 与 CSS 的锚定方式严格对应：`.iterate-canvas` 是相对 `.node-card` 的 **padding box** 做
 * `top: canvasTopOf(顶部高)` / `bottom: canvasInset` 的绝对定位，所以
 * `画布区高度 = borderBox 高度 − 2×borderWidth − top − bottom`。
 * 组件与测试都必须经由这一个函数换算，避免再出现「各处各算一套、相差 2px」的偏差。
 *
 * @param {number} cardHeight 节点盒（border box）高度
 * @param {number} topAreaHeight 顶部内容区高度
 * @returns {number} 单位 px
 */
export function canvasHeightOfCard(cardHeight, topAreaHeight) {
  return (Number(cardHeight) || 0)
    - CARD_LAYOUT.borderWidth * 2
    - canvasTopOf(topAreaHeight)
    - ITERATION_LAYOUT.canvasInset
}

/**
 * 顶部内容区高度变化后，卡片应有的高度：按**实测 delta** 平移当前高度，并不低于下限。
 *
 * 这是「画布区高度守恒」的实现方式 —— 顶部内容区长高多少，卡片盒就长高多少，
 * 于是 `画布区高度 = 卡片高 − 边框 − 顶部高 − 内缩` 保持不变。
 * delta 允许为负（真实内容比首帧估算值矮），此时卡片**回缩**，这是正确行为：
 * 同一行内新增变量不会让卡片变高，删到换行消失时卡片会缩回去。
 *
 * @param {number} currentHeight 当前卡片盒高
 * @param {number} prevTopArea 变化前的顶部内容区高度
 * @param {number} nextTopArea 变化后的顶部内容区高度
 * @param {number} minHeight 下限（见 {@link minNodeSizeOf}）
 * @returns {number} 单位 px
 */
export function cardHeightAfterContentChange(currentHeight, prevTopArea, nextTopArea, minHeight) {
  const delta = (Number(nextTopArea) || 0) - (Number(prevTopArea) || 0)
  return Math.max(Number(minHeight) || 0, Math.round((Number(currentHeight) || 0) + delta))
}

/**
 * 第 index 个子块相对**节点根**的坐标（Vue Flow 的 `parentNode` 语义）：纵向排列，
 * 横向放在「迭代开始」与「迭代结束」两个标记之间（空间富余时在两者之间居中），
 * 整个子块区在画布区内**垂直居中**（与固定在垂直中点的标记同一水平线）。
 *
 * @param {number} index 子块下标
 * @param {number} childCount 子块总数（决定子块区的垂直居中偏移）
 * @param {number} topAreaHeight 顶部内容区高度（决定画布区顶部偏移；实测值优先）
 * @returns {{ x: number, y: number }} 相对节点盒左上角的坐标
 */
export function childPositionOf(index, childCount, topAreaHeight = 0) {
  return {
    x: childXOf(),
    y: canvasTopOf(topAreaHeight)
      + childrenTopOf(childCount)
      + index * (ITERATION_LAYOUT.childHeight + ITERATION_LAYOUT.childGap),
  }
}

/**
 * 子块的拖拽约束（Vue Flow 的 `extent`）：与**可见画布区**一致。
 *
 * `extent: 'parent'` 的约束基准是整张卡片盒，而画布区在卡片内还有一圈内缩
 * （左右 / 底部各 {@link ITERATION_LAYOUT.canvasInset}，顶部还要让出顶部内容区），两者不是同一个矩形——
 * 只用 `'parent'` 时子块能被拖到卡片最顶部压在变量行上，也能贴到卡片边缘。
 * 这里按画布区给出四向 padding（顺序同 CSS 的 `[上, 右, 下, 左]`），并各加上卡片边框宽度：
 * extent 的基准是父节点的 border box，而画布区锚定在它的 padding box 内。
 *
 * 顶部内容区高度会带动 {@link canvasTopOf}，所以该约束必须在它变化时同步刷新
 * （见 `node/IterateNode.vue` 对实测顶部高度的 watch）。
 *
 * @param {number} topAreaHeight 顶部内容区高度（优先传**实测值**）
 * @returns {{ range: string, padding: number[] }} 可直接作为节点 `extent` 的值
 */
export function iterationChildExtent(topAreaHeight = 0) {
  const inset = ITERATION_LAYOUT.canvasInset + CARD_LAYOUT.borderWidth
  return {
    range: 'parent',
    padding: [canvasTopOf(topAreaHeight) + CARD_LAYOUT.borderWidth, inset, inset, inset],
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
 * @param {number} topAreaHeight 顶部内容区高度（优先传**实测值**）
 * @param {number} childCount 子块个数
 * @returns {{ width: number, height: number }} 单位 px
 */
export function minNodeSizeOf(topAreaHeight = 0, childCount = 0) {
  return containerSizeOf(childCount, topAreaHeight)
}

/**
 * 节点尺寸（`expandIteration` 写入的**初始**尺寸）：纵向依次为「卡片上下边框 → 顶部内容区
 * → canvasInset → 画布区（内含横向的「开始标记 → 子块 → 结束标记」）→ canvasInset → 卡片下边框」。
 * 用户可拖拽卡片右下角改变尺寸（组件经 `updateNode` 更新节点 style，画布区与标记随 CSS 锚定伸缩）；
 * 结果区在卡片正下方，不计入节点盒声明高度（与其它节点的结果面板一样溢出在盒外）。
 *
 * <p>高度含 `borderWidth * 2`：返回值是**节点盒（border box）**高度，而画布区锚定在卡片的
 * padding box 内（见 {@link canvasHeightOfCard}）。漏掉边框会让初始尺寸算出的画布区比
 * {@link canvasHeightOf} 少 2px，子块居中基准随之整体偏 1px。
 *
 * @param {number} childCount 子块个数
 * @param {number} topAreaHeight 顶部内容区高度（估算值或实测值）
 * @returns {{ width: number, height: number }} 单位 px
 */
export function containerSizeOf(childCount, topAreaHeight = 0) {
  return {
    width: containerWidthOf(),
    height: CARD_LAYOUT.borderWidth * 2
      + canvasTopOf(topAreaHeight)
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
  // 卡片顶部内容区（header + 变量行）占掉节点上部一段高度，决定画布区与子块的起点。
  // 这里没有 DOM 可测，只能用 {@link estimatedTopAreaOf} 的估算值（实测值的上界，恒安全）；
  // 卡片挂载后由 IterateNode 的 ResizeObserver 修正为真实值。
  const topAreaHeight = estimatedTopAreaOf(node.data)
  const { width, height } = containerSizeOf(blocks.length, topAreaHeight)

  const container = {
    ...node,
    blocks: [],
    edges: null,
    style: { width: `${width}px`, height: `${height}px` },
  }
  const children = blocks.map((block, index) => ({
    ...pickBackendNode(block),
    parentNode: id,
    // 拖拽约束 = 可见画布区（见 iterationChildExtent）：子块不能压到卡片头部 / 变量行，也不能贴卡片边缘
    extent: iterationChildExtent(topAreaHeight),
    // 子块要高于父卡片与内部连线（见 ITERATION_LAYER），否则会被卡片底色或连线盖住
    zIndex: ITERATION_LAYER.child,
    position: childPositionOf(index, blocks.length, topAreaHeight),
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
 * 节点是否渲染在**仍然存在**的迭代容器内部。
 *
 * 判据必须是「父容器还在」，不能只看 `parentNode` 是否存在：容器被删除后子块会短暂（或永久）
 * 残留成悬空节点，此时它已经不是迭代体子块了——结果面板要照常渲染，而不能被永久隐藏
 * （见 `components/NodeResultPanel.vue` 与 `utils/use-nested-node`）。
 *
 * @param {Array} nodes 画布节点
 * @param {string} nodeId 节点 id
 * @returns {boolean}
 */
export function isNestedNode(nodes = [], nodeId) {
  const list = nodes ?? []
  const parentId = list.find((node) => node?.id === nodeId)?.parentNode
  if (!parentId) return false
  return list.some((node) => node?.id === parentId)
}

/**
 * 一条连线所属的迭代容器：两端都落在同一个迭代体范围内（容器自身 ∪ 它的画布子块）时返回容器 id。
 *
 * 判定复用 {@link collapseIterations} 折叠内部边用的同一份口径（{@link belongsToContainer}），
 * 避免两处规则漂移。用途是给画布上**新建**的连线挑样式：迭代体内部的连线必须套
 * {@link ITERATION_EDGE_STYLE}，关键是其中的 `zIndex` —— Vue Flow 默认把连线渲染在节点层
 * **之下**，而迭代容器本身就是一张不透明卡片，漏掉 `zIndex` 的新建内部连线会被卡片底色整段
 * 盖住，表现为「连线已经建好了（store 里有）、画布上却看不见」，再连同一对节点又提示「已有连线」。
 *
 * @param {Array} nodes 画布节点
 * @param {string} source 连线起点节点 id
 * @param {string} target 连线终点节点 id
 * @returns {string|null} 所属迭代容器 id；不属于任何迭代体时为 null（顶层连线）
 */
export function iterationOwnerOf(nodes = [], source, target) {
  const list = (nodes ?? []).filter(
    (node) => node && typeof node !== 'string' && node.type === ITERATE_NODE_TYPE,
  )
  for (const container of list) {
    const childIds = new Set(
      (nodes ?? [])
        .filter((node) => node?.parentNode === container.id)
        .map((node) => node.id),
    )
    if (belongsToContainer({ source, target }, container.id, childIds)) return container.id
  }
  return null
}

/**
 * 重算子块相对坐标与容器尺寸：子块增删或排布变化后调用，返回新的节点数组。
 *
 * <p>尺寸策略是**只增不减**：目标尺寸取「按当前子块数与变量行数算出的初始尺寸」与「容器当前尺寸」
 * 的较大者。用户可以把容器拖大、也可以拖小（下限由 {@link minNodeSizeOf} 保证装得下子块），
 * 重算只能把装不下的情况补上，不能反过来把用户拖出来的尺寸改掉——否则删掉一个子块就会把用户
 * 手动放大的容器弹回初始大小。
 *
 * @param {Array} nodes 画布节点
 * @param {string} containerId 迭代节点 id
 * @param {number|null} topAreaHeight 顶部内容区高度（**实测值**；传 null 时退回估算值）
 * @returns {Array} 新的节点数组（未命中的节点原样返回）
 */
export function layoutIteration(nodes, containerId, topAreaHeight = null) {
  const list = nodes ?? []
  const children = list.filter((node) => node?.parentNode === containerId)
  const container = list.find((node) => node?.id === containerId)
  // 调用方有实测顶部高度就传进来；没有则退回估算值（上界）——算出的下限只会偏大，
  // 不会把「装不下当前子块」的尺寸放过去，所以这个兜底是安全的。
  const topArea = topAreaHeight ?? estimatedTopAreaOf(container?.data)
  const { width, height } = containerSizeOf(children.length, topArea)
  const nextWidth = Math.max(width, parseFloat(container?.style?.width) || 0)
  const nextHeight = Math.max(height, parseFloat(container?.style?.height) || 0)

  return list.map((node) => {
    if (node?.id === containerId) {
      return {
        ...node,
        style: { ...node.style, width: `${nextWidth}px`, height: `${nextHeight}px` },
      }
    }
    const index = children.findIndex((child) => child.id === node?.id)
    if (index < 0) return node
    return { ...node, position: childPositionOf(index, children.length, topArea) }
  })
}

/**
 * 子块纵向位置的**重叠消解**：只把压在前一个子块上的往后（往下）顺延，不重排、不垂直居中。
 *
 * 与 `layoutIteration` 的分工：那个负责**容器尺寸**（只增不减）与「子块增删后的标准排布」；
 * 本函数的判据则是**用户已经摆好的位置优先**——拖进容器的子块必须停在指针落点上，
 * 不能被「按 index 重排 + 垂直居中」那一套弹回队列里。只有真发生了纵向重叠才动它，
 * 因此默认流程（`expandIteration` 已按 {@link childPositionOf} 排好）走一遍结果不变。
 *
 * 上下界由调用方给出（画布区顶边 / 底边）：装不下时优先保证子块不被推到画布区之外，
 * 而不是让它压到卡片头部或溢出卡片。
 *
 * @param {Array} items 子块位置项 `{ id, y, height }`（height 用真实渲染高度，避免用预估值误判重叠）
 * @param {object} [options] 选项
 * @param {number} [options.gap] 子块之间的最小间距，默认 {@link ITERATION_LAYOUT.childGap}
 * @param {number} [options.top] y 的下界（画布区顶边，相对节点根）
 * @param {number} [options.bottom] y + height 的上界（画布区底边，相对节点根）
 * @returns {Array<{id: string, y: number}>} 每个子块应有的 y（整数）；与入参一一对应
 */
export function restackChildPositions(items = [], options = {}) {
  const {
    gap = ITERATION_LAYOUT.childGap,
    top = 0,
    bottom = Number.POSITIVE_INFINITY,
  } = options
  const minY = Number(top) || 0
  const maxBottom = Number.isFinite(bottom) ? Number(bottom) : Number.POSITIVE_INFINITY

  const list = (items ?? [])
    .filter((item) => item && item.id !== undefined && item.id !== null)
    .map((item) => ({
      id: item.id,
      y: Number(item.y) || 0,
      height: Math.max(Number(item.height) || 0, 0),
    }))
    // 按当前 y 自上而下处理；y 相同时用 id 兜底，保证同样的输入得到同样的输出
    .sort((a, b) => (a.y - b.y) || String(a.id).localeCompare(String(b.id)))

  const result = []
  let cursor = null
  for (const item of list) {
    let y = cursor === null ? item.y : Math.max(item.y, cursor)
    y = Math.max(y, minY)
    if (maxBottom !== Number.POSITIVE_INFINITY) {
      // 越界时往上收，但不得低于 minY（画布区装不下时以「不进到卡片头部」为准）
      y = Math.min(y, Math.max(minY, maxBottom - item.height))
    }
    result.push({ id: item.id, y: Math.round(y) })
    cursor = y + item.height + gap
  }
  return result
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
