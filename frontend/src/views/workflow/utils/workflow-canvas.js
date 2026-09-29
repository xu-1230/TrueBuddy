/**
 * 画布快照 ↔ 后端文档（`workflow.schema_json`）的序列化 / 反序列化。
 *
 * **只认「画布快照格式」一种**，也就是本前端 `toCanvasSnapshot()` 存进 `workflow` 表的那套：
 * - 节点 `type` 用语义名（input / output / llm / iteration / human）；
 * - 标题在 `data.label`，变量声明 `data.inputs` / `data.outputs` / `data.blocks` 全是**数组**；
 * - 坐标直接是节点上的 `position`，迭代子节点平铺在 `nodes` 里并用 `parentNode` 指向容器；
 * - 执行要用的后端 ref 结构存在 `data.backendInputs`，由 `workflow-schema.js` 原样写回。
 *
 * 刻意**不**兼容后端 / Coze 导出的编码格式（`type` 为 1/2/3/21/30、`data.nodeMeta`、
 * `inputs` 为 ref 对象、子节点嵌套在 `blocks`）：那套格式的 `data.inputs` 是**对象**，
 * 与画布的变量声明**数组**语义冲突，两套混着按结构猜会让「输入变量」在保存往返后静默消失。
 * 命中该格式时由 `assertCanvasSnapshot` 显式抛错；将来若真要导入，另写一个转换器即可。
 *
 * 注意「保存格式」与「执行格式」是两回事：执行格式由 `workflow-schema.js` 的
 * `toWorkflowSchema` 在试运行前临时生成，不落库。
 */
import { MarkerType } from '@vue-flow/core'

/** 画布语义类型 → 画布节点类型（一一对应，用于校验快照里的节点类型是否认识，仅本模块内部使用） */
const CANVAS_NODE_TYPE = {
  input: 'input',
  output: 'output',
  llm: 'llm',
  iteration: 'iteration',
  human: 'human',
}

/** 后端 WorkflowNodeType 的编码值：只用来识别「不是画布快照格式」并给出明确报错 */
const BACKEND_TYPE_CODES = new Set(['1', '2', '3', '21', '30'])

/** 画布连线统一样式：新建连线与打开工作流共用，保证两种来源的线完全一致 */
export const CANVAS_EDGE_STYLE = {
  type: 'default',
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: MarkerType.Arrow, color: 'var(--tb-primary)', width: 15, height: 15 },
}

/**
 * 迭代节点内部连线的层级：节点层默认在连线层之上，且选中节点会 +1000，
 * 因此内部连线必须高过 1000 才不会被容器卡片盖掉。
 */
export const INNER_EDGE_Z_INDEX = 1100

/* ===== 反序列化时的几何常量 =====
 * 与 WorkflowView.vue 的 ITERATION_* 常量、IterationNode.vue 的 DEFAULT_SIZE 保持一致，
 * 改动其中一处需同步其余处（与既有的「DEFAULT_SIZE 双份定义」保持一致的做法）。
 */
/** 迭代容器默认尺寸 */
const ITERATION_SIZE = { width: 480, height: 280 }
/** 迭代节点内层点阵画布的四周内缩 */
const ITERATION_CANVAS_INSET = 8
/** 迭代子节点落点：贴点阵画布内容区（header 48px + 画布内缩 8px） */
const ITERATION_CHILD_ORIGIN = { x: 100, y: 56 }
/** 子节点横向间距 */
const ITERATION_CHILD_GAP = 40
/** 子节点兜底尺寸（画布卡片宽 17rem） */
const CHILD_SIZE = { width: 272, height: 120 }
/** 迭代起止节点尺寸与右侧留位 */
const BOUNDARY_SIZE = 52
const BOUNDARY_GAP = 12
/** 迭代子节点可移动范围与层级（与 WorkflowView 中的同名常量对应） */
const ITERATION_CHILD_EXTENT = { range: 'parent', padding: [50, 6, 10, 6] }
const ITERATION_CHILD_Z_INDEX = 1

/** 读取节点坐标：画布快照直接把坐标放在节点上 */
const readPosition = (raw) => {
  const value = raw?.position ?? {}
  return {
    x: Number.isFinite(Number(value?.x)) ? Number(value.x) : 0,
    y: Number.isFinite(Number(value?.y)) ? Number(value.y) : 0,
  }
}

/**
 * 变量声明数组（inputs / outputs）：原样保留。
 * 空数组不写入 data，避免在抽屉里留下一个「有分组却没内容」的残留状态。
 */
const assignVariableGroup = (data, raw, key) => {
  const list = raw?.data?.[key]
  if (Array.isArray(list) && list.length) data[key] = list
}

/**
 * 节点 data → 画布 data（只认画布快照格式）。
 * `backendInputs` 是执行时才用得到的后端 ref 结构，跟着节点走，不被画布编辑。
 */
const buildNodeData = (raw) => {
  const data = { label: raw?.data?.label ?? raw?.id ?? '' }
  for (const key of ['inputs', 'outputs']) assignVariableGroup(data, raw, key)
  const backendInputs = raw?.data?.backendInputs
  if (backendInputs && typeof backendInputs === 'object' && !Array.isArray(backendInputs)) {
    data.backendInputs = backendInputs
  }
  return data
}

/**
 * 校验是不是「画布快照格式」。
 *
 * 命中后端 / Coze 导出的特征时直接抛错：宁可给一条清晰提示，
 * 也不要用两套语义去猜 `data.inputs` 是数组还是对象，从而静默丢数据。
 */
const assertCanvasSnapshot = (rawNodes) => {
  const foreign = rawNodes.find(
    (raw) =>
      BACKEND_TYPE_CODES.has(String(raw?.type ?? '')) ||
      raw?.data?.nodeMeta ||
      (Array.isArray(raw?.blocks) && raw.blocks.length) ||
      raw?.meta?.position,
  )
  if (!foreign) return
  throw new Error(
    `画布格式不支持：节点「${foreign.id}」像是后端 / Coze 导出的 schema` +
      '（type 编码、nodeMeta 或 blocks 结构）。画布只读写自身保存的快照格式，' +
      '请重新保存一次，或先用导入工具转换成快照格式。',
  )
}

/** 迭代容器内部的起止节点不参与子节点处理 */
const BOUNDARY_TYPES = new Set(['iteration-start', 'iteration-end'])

/** 迭代容器的直接子节点：快照里平铺在 nodes 中，用 parentNode 指回容器 */
const collectChildren = (raw, all) =>
  (Array.isArray(all) ? all : []).filter(
    (item) =>
      item?.parentNode === raw.id &&
      CANVAS_NODE_TYPE[item?.type] &&
      !BOUNDARY_TYPES.has(item?.type),
  )

/**
 * 把平铺的连线按「所属迭代容器」分组，供内部连线还原。
 *
 * 判定依据是端点的 `parentNode`：两端同属一个容器，这条线就是该容器的内部连线；
 * 嵌套场景下各归各自的内层容器，不会重复。顶层连线（两端都没有父容器）不在此列。
 *
 * @returns {Map<string, Array<{id: (string|undefined), source: string, target: string}>>}
 */
const groupEdgesByContainer = (rawNodes, rawEdges) => {
  const parentOf = new Map()
  const iterationIds = new Set()
  for (const raw of rawNodes) {
    if (!raw?.id) continue
    if (raw.type === 'iteration') iterationIds.add(raw.id)
    if (raw.parentNode) parentOf.set(raw.id, raw.parentNode)
  }

  /**
   * 端点所属容器：优先看 parentNode；
   * 快照里若缺了 `-start` / `-end` 边界节点条目，再按 id 约定兜底（并确认前缀确实是迭代容器）。
   */
  const containerOf = (id) => {
    const parent = parentOf.get(id)
    if (parent) return parent
    const match = /^(.+)-(?:start|end)$/.exec(String(id ?? ''))
    return match && iterationIds.has(match[1]) ? match[1] : null
  }

  const grouped = new Map()
  for (const edge of rawEdges) {
    const source = edge?.source
    const target = edge?.target
    if (!source || !target) continue
    const container = containerOf(source)
    if (!container || container !== containerOf(target)) continue
    if (!grouped.has(container)) grouped.set(container, [])
    grouped.get(container).push({ id: edge?.id, source, target })
  }
  return grouped
}

/**
 * 展开迭代节点的内部结构：子节点 + 起点 / 终点 + 内部连线，并回写容器尺寸。
 *
 * @param {Object} container 画布容器节点（会被就地修改 width / height）
 * @param {Object} raw 快照里的迭代节点
 * @param {{nodes: Array, edges: Array, skipped: Array}} out 输出收集器
 * @param {Array} all 全部原始节点
 * @param {Map<string, Array>} edgesByContainer 按容器分组后的内部连线
 */
const expandIteration = (container, raw, out, all, edgesByContainer) => {
  const startId = `${raw.id}-start`
  const endId = `${raw.id}-end`
  const children = collectChildren(raw, all)

  let cursorX = ITERATION_CHILD_ORIGIN.x
  const childIds = []
  for (const child of children) {
    if (!child?.id) continue
    const type = CANVAS_NODE_TYPE[child.type]
    // 快照自带位置 / extent / zIndex，优先沿用；缺失时才按默认布局排布
    const position = Number.isFinite(Number(child?.position?.x))
      ? child.position
      : { x: cursorX, y: ITERATION_CHILD_ORIGIN.y }
    const node = {
      id: child.id,
      type,
      data: buildNodeData(child),
      parentNode: raw.id,
      extent: child.extent ?? ITERATION_CHILD_EXTENT,
      zIndex: child.zIndex ?? ITERATION_CHILD_Z_INDEX,
      position,
      ...(child.draggable === false ? { draggable: false } : {}),
      ...(child.deletable === false ? { deletable: false } : {}),
    }
    out.nodes.push(node)
    childIds.push(node.id)
    if (type === 'iteration') {
      expandIteration(node, child, out, all, edgesByContainer)
    }
    // 嵌套迭代的宽度是撑开后的实际宽度，按它推进才不会让后续子节点压上去
    cursorX = position.x + (node.width || child.width || CHILD_SIZE.width) + ITERATION_CHILD_GAP
  }

  // 起止节点由容器自带的工厂语义维护：起点只出线、终点只入线，不可拖动 / 删除
  // （终点位置由 IterationNode 按容器宽度自动贴着右内侧，这里给的只是初值）
  out.nodes.push(
    {
      id: startId,
      type: 'iteration-start',
      parentNode: raw.id,
      zIndex: ITERATION_CHILD_Z_INDEX,
      position: { x: ITERATION_CANVAS_INSET, y: ITERATION_CHILD_ORIGIN.y },
      draggable: false,
      deletable: false,
    },
    {
      id: endId,
      type: 'iteration-end',
      parentNode: raw.id,
      zIndex: ITERATION_CHILD_Z_INDEX,
      position: { x: cursorX, y: ITERATION_CHILD_ORIGIN.y },
      draggable: false,
      deletable: false,
    },
  )

  // 容器尺寸：装得下「起点 + 子节点 + 终点」，宽度不足时子节点会压到终点上
  const requiredWidth = cursorX + ITERATION_CHILD_GAP + BOUNDARY_SIZE + BOUNDARY_GAP + 16
  const requiredHeight = ITERATION_CHILD_ORIGIN.y + CHILD_SIZE.height + 24
  // 快照自带尺寸时优先沿用（只向上取大，保证装得下子节点）
  container.width = Math.max(container.width ?? 0, ITERATION_SIZE.width, Math.ceil(requiredWidth))
  container.height = Math.max(container.height ?? 0, ITERATION_SIZE.height, Math.ceil(requiredHeight))

  // 内部连线：两端都落在「起点 / 子节点 / 终点」范围内才认，其余（脏数据）忽略
  const known = new Set([startId, endId, ...childIds])
  const inner = (edgesByContainer.get(raw.id) ?? []).filter(
    (edge) => edge.source !== edge.target && known.has(edge.source) && known.has(edge.target),
  )

  if (!inner.length) {
    // 容器内还没连线时按子节点顺序串成一条链，保证循环体有完整的出入口
    const chain = [startId, ...childIds, endId]
    for (let index = 1; index < chain.length; index += 1) {
      inner.push({ source: chain[index - 1], target: chain[index] })
    }
  }

  const seen = new Set()
  for (const edge of inner) {
    const key = `${edge.source}->${edge.target}`
    if (seen.has(key)) continue
    seen.add(key)
    out.edges.push({
      id: edge.id ?? `e-${edge.source}-${edge.target}`,
      source: edge.source,
      target: edge.target,
      ...CANVAS_EDGE_STYLE,
      zIndex: INNER_EDGE_Z_INDEX,
    })
  }
}

/**
 * 画布快照 → 画布节点 / 连线。
 *
 * @param {Object} schema 已 `JSON.parse` 的 `schema_json`（`{ nodes, edges }`）
 * @returns {{ nodes: Array, edges: Array, skipped: Array }}
 *   `skipped` 为类型不认识的节点（只跳过该节点，不影响其余内容），供调用方提示用户
 * @throws {Error} schema 不是画布快照格式时（见 `assertCanvasSnapshot`）
 */
export function fromWorkflowSchema(schema) {
  const rawNodes = Array.isArray(schema?.nodes) ? schema.nodes : []
  const rawEdges = Array.isArray(schema?.edges) ? schema.edges : []
  assertCanvasSnapshot(rawNodes)

  const out = { nodes: [], edges: [], skipped: [], ids: new Set() }
  const edgesByContainer = groupEdgesByContainer(rawNodes, rawEdges)

  for (const raw of rawNodes) {
    if (!raw?.id) continue
    // 子节点平铺在 nodes 里并带 parentNode，统一交给父容器处理
    if (raw.parentNode) continue
    const type = CANVAS_NODE_TYPE[raw.type]
    if (!type) {
      out.skipped.push({ id: raw.id, label: raw?.data?.label, reason: '类型不支持' })
      continue
    }
    const node = {
      id: raw.id,
      type,
      data: buildNodeData(raw),
      position: readPosition(raw),
    }
    if (type === 'input' || type === 'output') {
      // 画布上的开始 / 结束是唯一实例，不允许删除
      node.deletable = false
      node.sourcePosition = 'right'
      node.targetPosition = 'left'
    }
    if (type === 'iteration' && Number.isFinite(Number(raw.width))) {
      // 快照自带容器尺寸，沿用（expandIteration 只会在此基础上向上取大）
      node.width = Number(raw.width)
      node.height = Number.isFinite(Number(raw.height)) ? Number(raw.height) : undefined
    }
    out.nodes.push(node)
    out.ids.add(node.id)
    if (type === 'iteration') {
      expandIteration(node, raw, out, rawNodes, edgesByContainer)
    }
  }

  for (const raw of rawEdges) {
    const source = raw?.source
    const target = raw?.target
    // 迭代内部连线的两端不是顶层节点，已由 expandIteration 单独生成
    if (!out.ids.has(source) || !out.ids.has(target)) continue
    out.edges.push({
      id: raw?.id ?? `e-${source}-${target}`,
      source,
      target,
      ...CANVAS_EDGE_STYLE,
    })
  }

  return { nodes: out.nodes, edges: out.edges, skipped: out.skipped }
}

/**
 * 画布 → 存储快照（保存到 workflow 表用）。
 *
 * 与 `toWorkflowSchema`（画布 → 后端执行格式）是两套结构，**保存必须用这一套**：
 * 执行格式的 type 是后端编码（1/2/3/21/30）、data 是 `{nodeMeta, outputs}`，
 * 丢失了画布坐标、`parentNode` 与 `data.backendInputs`（人工确认清单的 ref 来源），
 * 拿它保存会导致重开后人工确认节点中断时清单为空。
 * 这里只剔除 vue-flow 的运行时字段，其余原样保留。
 *
 * @param {Array} nodes 画布节点（`getNodes.value`）
 * @param {Array} edges 画布连线（`getEdges.value`）
 * @returns {{ nodes: Array, edges: Array }}
 */
export function toCanvasSnapshot(nodes = [], edges = []) {
  /** 需要落库的节点字段（vue-flow 的 computedPosition / dimensions / selected 等均为运行时派生，不存） */
  const NODE_KEYS = [
    'id',
    'type',
    'position',
    'data',
    'parentNode',
    'width',
    'height',
    'extent',
    'zIndex',
    'draggable',
    'deletable',
    'sourcePosition',
    'targetPosition',
  ]
  const EDGE_KEYS = [
    'id',
    'source',
    'target',
    'sourceHandle',
    'targetHandle',
    'type',
    'style',
    'animated',
    'markerEnd',
    'zIndex',
  ]

  const pick = (source, keys) => {
    const result = {}
    for (const key of keys) {
      if (source[key] !== undefined) result[key] = source[key]
    }
    return result
  }

  return {
    nodes: (Array.isArray(nodes) ? nodes : []).filter(Boolean).map((node) => pick(node, NODE_KEYS)),
    edges: (Array.isArray(edges) ? edges : []).filter(Boolean).map((edge) => pick(edge, EDGE_KEYS)),
  }
}
