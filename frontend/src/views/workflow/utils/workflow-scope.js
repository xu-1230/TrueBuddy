/**
 * 前端「作用域链」：把「上游可见变量」从**每次现算的查询**改成**随图缓存的结构**。
 *
 * 参考字节开源 FlowGram 变量引擎的思路（MIT 许可）：编辑器把可见性物化成 scope 链
 * （`scope.depScopes` / `scope.coverScopes`），查询退化为集合查找。
 * 本模块用同样的思路，但**不引入 FlowGram**（它是 React 生态，见
 * `@flowgram.ai/variable-core` 的 `peerDependencies: react >=16.8`）。
 *
 * **本模块是变量候选集的唯一实现**：后端对应的 `listNodeVariables` 接口已下线，
 * 候选集不再向后端索取。产出项与执行侧的口径保持一致：
 * - `visibleSourceNodes` ← `visibleUpstreamNodeIds()` 减去 `containingLoops()`；
 * - `declaredOutputs`    ← 节点 `data.outputs`；
 * - `loopLocals`         ← `loopLocals()`（`item` + 循环节点声明的输入参数）；
 * - `elementType` / `buildVariable` / `sourceRank` 同名字段直接产出。
 *
 * 刻意**不加隐式首尾连线**（`workflow-reference.js` 的 `withImplicitTerminalEdges`）：
 * 后端 `SchemaNormalizer.normalize` 只做 `normalizeEdges(原始 edges)`，不补 START→首个 /
 * 末个→END，因此这里也必须用原始连线。副作用是「完全没连线」的画布上
 * 第一个可执行节点候选为空。
 *
 * 与引用校验的关系：本模块与 `collectReferenceIssues` 共用同一个
 * `visibleUpstreamNodeIds`，所以「选择器里能选出来的」必然通过校验，两者结构上不可能矛盾。
 */
import {
  containingLoops,
  indexSchemaNodes,
  nodeTitleOf,
  visibleUpstreamNodeIds,
} from './workflow-reference'
import { toWorkflowSchema } from './workflow-schema'

/* ===== 常量（对齐后端 WorkflowServiceImpl / LoopNodeAction） ===== */

/** 变量来源：工作流输入（开始节点输出） */
const SOURCE_INPUT = 'input'
/** 变量来源：上游节点输出 */
const SOURCE_NODE = 'node'
/** 变量来源：循环体局部变量（由循环节点在运行时注入） */
const SOURCE_LOCAL = 'local'
/** 循环体当前元素变量名（与 `LoopNodeAction` 的绑定约定一致） */
const ITEM_VARIABLE = 'item'
/** 开始节点的后端类型编码 */
const START_TYPE = '1'

/** 候选来源排序权重：工作流输入最常用，循环局部变量最特殊放最后 */
const sourceRank = (source) => {
  if (source === SOURCE_INPUT) return 0
  return source === SOURCE_NODE ? 1 : 2
}

/* ===== 变量构造（对齐 buildVariable） ===== */

/**
 * 组装单个候选变量；字段口径与执行侧的引用键约定一致（`{nodeId}.{name}`）。
 *
 * @param {string} source 来源：input / node / local
 * @param {Object} node 变量所属节点
 * @param {{name: string, type?: string, schema?: Object, description?: string}} declaration 声明
 */
const buildVariable = (source, node, { name, type, schema, description }) => {
  const finalType = String(type ?? '').trim() || 'string'
  const title = nodeTitleOf(node) || node.id
  return {
    source,
    nodeId: node.id,
    nodeTitle: nodeTitleOf(node),
    name,
    type: finalType,
    // 仅 list 有元素类型；与后端 buildVariable 的取值口径一致
    itemType:
      finalType.toLowerCase() === 'list' && schema?.type != null ? String(schema.type) : null,
    description: description ?? '',
    // 状态键约定：{nodeId}.{name}（对齐 RefResolver.stateKey）
    refKey: `${node.id}.${name}`,
    label: `${title} · ${name}`,
  }
}

/** 节点声明的输出（过滤掉无名项） */
const declaredOutputs = (node) =>
  (Array.isArray(node?.data?.outputs) ? node.data.outputs : []).filter((output) =>
    String(output?.name ?? '').trim(),
  )

/**
 * 取元素类型：循环体内 `item` 是单个元素，而非容器声明的整个列表。
 * 与后端 `elementType` 一致：list 取 `schema.type`，否则取 `slot.type`，兜底 `string`。
 */
const elementType = (slot) => {
  if (!slot) return 'string'
  if (String(slot.type ?? '').toLowerCase() === 'list' && slot.schema?.type) {
    return String(slot.schema.type)
  }
  return String(slot.type ?? '').trim() || 'string'
}

/**
 * 循环体的局部变量：`item` 与循环节点声明的输入参数。
 *
 * `LoopNodeAction#execute` 会把这两类以 `{loopId}.{name}` 绑定进循环体，
 * 因此循环体内的节点可以引用它们；画布上并不存在这些声明，只能在此合成。
 */
const loopLocals = (container) => {
  const locals = []
  const params = container?.data?.inputs?.inputParameters
  if (Array.isArray(params)) {
    for (const param of params) {
      const name = String(param?.name ?? '').trim()
      if (!name) continue
      const slot = param?.input
      locals.push(
        buildVariable(SOURCE_LOCAL, container, {
          name,
          type: elementType(slot),
          schema: slot?.schema,
          description: name === ITEM_VARIABLE ? '当前迭代项' : '循环输入参数（由循环节点注入）',
        }),
      )
    }
  }
  // `item` 由执行器无条件注入，容器没声明它时也要列出
  if (!locals.some((item) => item.name === ITEM_VARIABLE)) {
    locals.push(
      buildVariable(SOURCE_LOCAL, container, {
        name: ITEM_VARIABLE,
        type: 'string',
        description: '当前迭代项',
      }),
    )
  }
  return locals
}

/* ===== 单节点可见变量 ===== */

/**
 * 某个节点的上游可见变量。
 *
 * @param {{nodes: Array, edges: Array, nodeIndex: Map}} ctx 执行格式画布与节点索引
 * @param {string} nodeId 目标节点 id
 * @returns {{variables: Array, error: string}} `error` 非空表示无法计算（节点不在图中）
 */
const variablesOfNode = (ctx, nodeId) => {
  if (!ctx.nodeIndex.has(nodeId)) {
    return { variables: [], error: '节点不在当前执行图中' }
  }

  const containers = containingLoops(ctx.nodes, nodeId)
  const containerIds = new Set(containers.map((item) => item.id))

  const variables = []
  for (const sourceId of visibleUpstreamNodeIds(ctx.nodes, ctx.edges, nodeId)) {
    // 容器自身的聚合输出在循环体内尚未产出，故不作为来源
    if (containerIds.has(sourceId)) continue
    const sourceNode = ctx.nodeIndex.get(sourceId)
    if (!sourceNode) continue
    const isStart = sourceNode.type === START_TYPE
    for (const output of declaredOutputs(sourceNode)) {
      variables.push(
        buildVariable(isStart ? SOURCE_INPUT : SOURCE_NODE, sourceNode, {
          name: output.name,
          type: output.type,
          schema: output.schema,
          description: output.description,
        }),
      )
    }
  }
  for (const container of containers) {
    variables.push(...loopLocals(container))
  }

  // 稳定排序：工作流输入 → 上游节点输出 → 循环局部变量；组内保持反向 BFS 的由近到远顺序
  variables.sort((a, b) => sourceRank(a.source) - sourceRank(b.source))
  return { variables, error: '' }
}

/* ===== 全图索引 + memo（等价于 FlowGram 的 scope chain） ===== */

/** 取值为数组时返回自身，否则空数组（签名与遍历共用，避免到处写 Array.isArray） */
const listOf = (value) => (Array.isArray(value) ? value : [])

/**
 * 图结构签名：只保留**候选集会读到的输入**，因此拖动节点、选中、缩放都不会触发重建。
 *
 * 覆盖范围与 `toWorkflowSchema` → `variablesOfNode` 实际读取的字段一一对应：
 * - 节点 id / 类型 / 所属容器，且保持**数组顺序**（`blocks` 的顺序参与「同体前置兄弟」判定）；
 * - `data.outputs` 的 name / type / schema.type / description（候选变量的元信息）；
 * - `data.backendInputs.inputParameters` 的 name / type / schema.type（循环体局部变量）；
 * - 连线两端；
 * - **完全没有连线**时的节点坐标 —— 此时 `toWorkflowSchema` 会按坐标自动串接，
 *   坐标同样影响候选集。
 *
 * 注意：以后若新增会进入候选集的字段，**必须同步加进这里**，否则会拿到过期索引。
 */
const signatureOf = (nodes, edges) => {
  const list = listOf(nodes)
  const edgeList = listOf(edges)
  return JSON.stringify({
    n: list.map((node) => [
      node.id,
      node.type,
      node.parentNode ?? null,
      node.data?.label ?? '',
      listOf(node.data?.outputs).map((output) => [
        output?.name,
        output?.type,
        output?.schema?.type ?? null,
        output?.description ?? '',
      ]),
      listOf(node.data?.backendInputs?.inputParameters).map((param) => [
        param?.name,
        param?.input?.type,
        param?.input?.schema?.type ?? null,
      ]),
    ]),
    e: edgeList.map((edge) => [edge.source, edge.target]),
    p: edgeList.length ? null : list.map((node) => [node.id, node.position?.x ?? 0, node.position?.y ?? 0]),
  })
}

let memoKey = ''
let memoIndex = null

/**
 * 全图作用域索引：`Map<nodeId, { variables, error }>`。
 *
 * 图结构不变时直接复用上次结果，所以「切换节点」只是一次 Map 查找 ——
 * 这正是「打开抽屉不再请求后端」的来源，也是拖动节点不会反复重算的原因。
 *
 * @param {Array} nodes 画布节点（`getNodes.value`）
 * @param {Array} edges 画布连线（`getEdges.value`）
 * @returns {Map<string, {variables: Array, error: string}>}
 */
export function getScopeIndex(nodes, edges) {
  const key = signatureOf(nodes, edges)
  if (key === memoKey && memoIndex) return memoIndex

  const { nodes: schemaNodes, edges: schemaEdges, skipped } = toWorkflowSchema(nodes, edges)
  const ctx = {
    nodes: schemaNodes,
    edges: schemaEdges,
    nodeIndex: indexSchemaNodes(schemaNodes),
  }

  const index = new Map()
  // 不参与执行的节点（类型不支持 / 迭代容器为空）：提示口径与抽屉原有文案一致
  for (const item of skipped ?? []) {
    index.set(item.id, {
      variables: [],
      error: `本节点当前不参与执行（${item.reason}），无法计算上游变量`,
    })
  }
  // nodeIndex 含迭代内部子节点（挂在容器的 blocks 上），一次遍历即覆盖全部层级
  for (const node of ctx.nodeIndex.values()) {
    index.set(node.id, variablesOfNode(ctx, node.id))
  }

  memoKey = key
  memoIndex = index
  return index
}
