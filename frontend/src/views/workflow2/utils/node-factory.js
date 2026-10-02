/**
 * 新建节点工厂（纯函数，无 Vue 依赖，可脱离画布单测）。
 *
 * 画布页只负责编排（取 store 实时节点 → 调本模块 → `addNodes`），节点该长什么样、
 * 落在哪里、叫什么名字全部收敛在这里，避免画布页膨胀、也避免规则散落在组件里漂移。
 *
 * 三条后端约定决定了这里的实现（见 docs/12-工作流规范.md）：
 * 1. **id 是全图唯一的字符串**，新版约定为数字串（§3.2），且顶层边与迭代体内部边共用同一
 *    命名空间（§3.3）—— 因此取「全图（含迭代体子块）数字 id 最大值 + 1」，结果可预测、无碰撞。
 * 2. **`type` 只认数字编码字符串** `'1'`~`'5'`（§4.1），与画布 `nodeTypes` 的字符串 key 一致。
 * 3. **普通节点 `blocks` 写 `[]`、`edges` 写 `null`**（§8.1 各节点示例），字段形态必须与定义同构。
 */
import {
  CARD_LAYOUT,
  ITERATE_NODE_TYPE,
  cardTopAreaHeightOf,
  containerSizeOf,
  estimatedTopAreaOf,
} from './iteration.js'
import { NODE_META } from './node-meta.js'

/** 普通卡片宽度：与 `node/*.vue` 里 `.node-card` 的 `17rem` 一致（1rem = 16px） */
const NODE_WIDTH = 272

/** 既有卡片的高度估算：参与「与既有节点群纵向居中」的落点计算，不写入节点数据 */
const NODE_HEIGHT = 140

/**
 * 新建普通卡片的高度：只有头部、没有任何变量行。
 *
 * 按 `CARD_LAYOUT` 的口径推导而不是写死一个数字——改卡片内边距 / 头部高度时这里自动跟随。
 * 与 {@link NODE_HEIGHT} 不同：那个估的是**既有**卡片（带变量行，明显更高），
 * 混用会让新建节点在画布上摆不正（按 140 折算会比实际卡片高出几十像素）。
 */
const NEW_CARD_HEIGHT = cardTopAreaHeightOf(0) + CARD_LAYOUT.borderWidth * 2

/** 新节点与既有节点群右边缘之间的横向间隔 */
const NODE_GAP_X = 240

/** id 自增的基准值（画布为空时的起点），与 `default-flow.js` 的编号量级保持一致 */
const ID_BASE = 100001

/** 可参与自增的最大安全 id：再大就会越过 `Number.MAX_SAFE_INTEGER` 出现精度丢失 */
const ID_MAX = Number.MAX_SAFE_INTEGER - 1

/** 空画布时的落点：与 `default-flow.js` 首个节点的位置一致 */
const EMPTY_CANVAS_POSITION = { x: 40, y: 240 }

/** 数字串判据：后端约定 id 为数字串，语义化写法（旧版）不参与自增 */
const NUMERIC_ID = /^\d+$/

/** 解析样式里的像素值（`'272px'` → 272）；解析不出来返回 null */
const toFinite = (value) => {
  const parsed = parseFloat(value)
  return Number.isFinite(parsed) ? parsed : null
}

/**
 * 全图已占用的 id 集合。
 *
 * 语义化 id 同样计入：它们不参与自增，但**必须**参与唯一性判断，否则会生成一个已被占用的数字 id。
 */
const usedIdsOf = (nodes) => new Set((nodes ?? []).map((node) => node?.id).filter(Boolean))

/**
 * 下一个可用的节点 id（数字串）。
 *
 * 取全图（含迭代体子块，画布上子块与容器在同一个数组里）数字 id 的最大值 +1；
 * 空画布从 {@link ID_BASE} 起。最大值已触及安全整数上限时退回「从基准值线性找第一个空位」，
 * 保证返回值永远是安全范围内、且未被占用的数字串。
 *
 * @param {Array} nodes 画布节点（`getNodes()` 的实时数据，含 `parentNode` 子块）
 * @returns {string} 可直接写入节点 `id` 的数字串
 */
export function nextNodeId(nodes = []) {
  const used = usedIdsOf(nodes)
  const numericIds = [...used]
    .filter((id) => typeof id === 'string' && NUMERIC_ID.test(id))
    .map(Number)
    .filter(Number.isSafeInteger)
  if (!numericIds.length) return String(ID_BASE)

  const max = Math.max(...numericIds)
  if (max < ID_MAX) return String(max + 1)

  let candidate = ID_BASE
  while (used.has(String(candidate))) candidate += 1
  return String(candidate)
}

/**
 * 默认节点名：与画布上已有名称重名时追加序号。
 *
 * 「大模型」→「大模型 2」→「大模型 3」，并复用中间被腾出的空位（已有「大模型」与「大模型 3」时
 * 返回「大模型 2」），避免名称越加越长。同名节点在画布上无法分辨，必须在这里挡住。
 *
 * @param {Array} nodes 画布节点
 * @param {string} base 基础名（通常取 {@link NODE_META} 的 `name`）
 * @returns {string} 未与既有节点重名的名称
 */
export function uniqueLabelOf(nodes = [], base) {
  const name = String(base ?? '').trim()
  if (!name) return name

  const used = new Set((nodes ?? []).map((node) => node?.data?.label).filter(Boolean))
  if (!used.has(name)) return name

  let index = 2
  while (used.has(`${name} ${index}`)) index += 1
  return `${name} ${index}`
}

/**
 * 单个节点在画布上的占位尺寸。
 *
 * 优先读节点 `style`（迭代节点由 `expandIteration` / 工厂写入显式宽高），其次按类型取口径：
 * 迭代容器用 {@link containerSizeOf}（与卡片实际尺寸同源），其余用卡片标称值。
 * 这是纯几何估算，只服务于落点计算，不写回节点。
 *
 * @param {object} node 画布节点
 * @returns {{ width: number, height: number }}
 */
function sizeOf(node) {
  const styleWidth = toFinite(node?.style?.width)
  const styleHeight = toFinite(node?.style?.height)
  if (styleWidth !== null && styleHeight !== null) {
    return { width: styleWidth, height: styleHeight }
  }

  const fallback = String(node?.type) === ITERATE_NODE_TYPE
    ? containerSizeOf(0, estimatedTopAreaOf(node?.data))
    : { width: NODE_WIDTH, height: NODE_HEIGHT }

  return {
    width: styleWidth ?? fallback.width,
    height: styleHeight ?? fallback.height,
  }
}

/**
 * 新节点的默认落点：既有节点群的右侧、纵向上与节点群居中对齐。
 *
 * 只统计**顶层节点**（没有 `parentNode` 的）：迭代体子块的坐标是相对容器的，混进来会把包围盒
 * 拉小到容器内部。计算结果不依赖 DOM 测量，同样的画布一定得到同样的落点，便于复现与测试。
 *
 * 点击添加默认落在画布可视区域正中（见 `WorkflowView` 的 `viewportCenterNodePosition`），
 * 本函数是**拿不到画布尺寸时**的兜底口径（画布还没挂载、尺寸为 0 等场合）。
 *
 * @param {Array} nodes 画布节点
 * @returns {{ x: number, y: number }} 画布坐标（节点左上角）
 */
export function nextNodePosition(nodes = []) {
  const topLevel = (nodes ?? []).filter((node) => !node?.parentNode && node?.position)
  if (!topLevel.length) return { ...EMPTY_CANVAS_POSITION }

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const node of topLevel) {
    const { width, height } = sizeOf(node)
    const x = Number(node.position.x) || 0
    const y = Number(node.position.y) || 0
    minX = Math.min(minX, x)
    minY = Math.min(minY, y)
    maxX = Math.max(maxX, x + width)
    maxY = Math.max(maxY, y + height)
  }

  return {
    x: Math.round(maxX + NODE_GAP_X),
    // 让新卡片的**中点**落在既有节点群的纵向中线上（按标称高度折算，不引入 DOM 测量）
    y: Math.round((minY + maxY) / 2 - NODE_HEIGHT / 2),
  }
}

/**
 * 新建节点的**标称**尺寸。
 *
 * 用途是「把节点摆正在某个点上」（例如点击添加时落在画布可视区域正中）：节点的 `position` 是左上角，
 * 要整体居中就得先知道它多大。取的是**刚创建、还没有任何变量声明**时的尺寸——普通卡片只有头部
 * （见 {@link NEW_CARD_HEIGHT}），迭代容器按 {@link containerSizeOf} 的真实口径（0 个子块、0 行变量）。
 *
 * @param {string} type 节点类型编码
 * @returns {{ width: number, height: number }} 单位 px
 */
export function nominalSizeOf(type) {
  if (String(type) === ITERATE_NODE_TYPE) {
    return containerSizeOf(0, estimatedTopAreaOf({}))
  }
  return { width: NODE_WIDTH, height: NEW_CARD_HEIGHT }
}

/**
 * 各类型节点的默认 `data`（字段与 docs/12-工作流规范.md §8.1 的节点示例同构）。
 *
 * 输入 / 输出一律留空数组：抽屉里的「＋」与变量取值选择器负责填充，新建节点不预置任何
 * 变量（预置假变量会让卡片立刻出现「未绑定取值」的告警色）。
 */
const DATA_TEMPLATES = {
  '1': (label) => ({ label, outputs: [] }),
  '2': (label) => ({ label, inputs: [] }),
  '3': (label) => ({ label, system: '', user: '', inputs: [], outputs: [] }),
  '4': (label) => ({ label, inputs: [], variableParameters: [], outputs: [] }),
  '5': (label) => ({ label, inputs: [], outputs: [] }),
}

/**
 * 按类型生成一个可直接交给 `addNodes` 的节点对象。
 *
 * @param {string} type 节点类型编码（`'1'`~`'5'`）
 * @param {object} [options] 选项
 * @param {string} options.id 节点 id（必须已由 {@link nextNodeId} 保证全图唯一）
 * @param {string} [options.label] 节点名称，缺省取 {@link NODE_META} 的 `name`
 * @param {{x: number, y: number}} [options.position] 画布坐标，缺省为 `{ x: 0, y: 0 }`
 * @returns {object} 画布节点
 * @throws {Error} 类型不在 {@link NODE_META} 内，或缺少 `id`
 */
export function createWorkflowNode(type, { id, label, position } = {}) {
  const meta = NODE_META[type]
  if (!meta) throw new Error(`不支持的节点类型: ${type}`)
  if (!id) throw new Error('创建节点必须提供 id')

  const finalLabel = label || meta.name
  const data = DATA_TEMPLATES[type](finalLabel)
  const node = {
    id: String(id),
    type: String(type),
    position: {
      x: Number(position?.x) || 0,
      y: Number(position?.y) || 0,
    },
    data,
    // 普通节点与容器的字段形态见 docs/12 §8.1：blocks 写 []、edges 写 null
    blocks: [],
    edges: null,
  }

  // 迭代容器的卡片尺寸由画布区推导，缺 style 会渲染异常（口径与 expandIteration 完全一致）。
  // style 属于 Vue Flow 运行时字段，提交后端时被 buildRunDefinition 的字段白名单自动剔除。
  if (String(type) === ITERATE_NODE_TYPE) {
    const { width, height } = nominalSizeOf(type)
    node.style = { width: `${width}px`, height: `${height}px` }
  }

  return node
}
