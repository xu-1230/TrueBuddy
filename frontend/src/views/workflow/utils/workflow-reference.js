/**
 * 输入引用的「必须来自上游」校验（**纯前端**）。
 *
 * 为什么在前端做：后端不做这项校验（它只依赖 spring-ai-alibaba-graph 建图时的结构校验），
 * 而非上游引用在执行时必然取到空值——典型表现是「配置看着没问题，跑出来是空清单」。
 * 因此在试运行前由画布自查一次，把问题挡在请求之前。
 *
 * 判定口径与执行侧一致：只有**一定能先于目标节点执行**的节点所声明的输出才是合法引用。
 * 输入是 `toWorkflowSchema` 产出的「执行格式」：
 * - 迭代子节点挂在容器的 `blocks` 里，内部顺序即数组顺序（执行侧同样只认声明顺序）；
 * - 顶层 `edges` 只保留跨顶层节点的连线（迭代内部连线不进执行图）。
 *
 * 作用域规则与执行侧对齐：
 * - 顶层节点：可见其反向可达闭包；
 * - 循环体节点：额外可见容器自身（`item` 等局部变量的引用目标）、容器的外层祖先，
 *   以及同体声明在前的兄弟；容器自身的聚合输出在体内尚未产出，故不作为来源。
 */

/** 节点类型编码（与后端 WorkflowNodeType 一致） */
const START_TYPE = '1'
const END_TYPE = '2'
const LOOP_TYPE = '21'

/** 可执行节点的编码：开始 / 结束不注册为图节点，只映射为图的 START / END */
const EXECUTABLE_CODES = new Set(['3', LOOP_TYPE, '30'])

const listOf = (value) => (Array.isArray(value) ? value : [])

/**
 * 节点及其（递归的）内部子节点，键为节点 id。
 *
 * 导出供 `workflow-scope.js` 复用：作用域索引需要按 id 取到任意层级的节点
 * （迭代内部子节点挂在容器的 `blocks` 里，不在顶层数组上）。
 */
export const indexSchemaNodes = (nodes, index = new Map()) => {
  for (const node of listOf(nodes)) {
    if (!node?.id) continue
    index.set(node.id, node)
    indexSchemaNodes(node.blocks, index)
  }
  return index
}

/** 节点展示名：优先标题，缺失时退回 id */
export const nodeTitleOf = (node) => node?.data?.nodeMeta?.title || node?.id || ''

/** target → [sources] 的入边索引（extraIncoming 用于表达「循环体依附容器」这类隐式依赖） */
const indexIncoming = (edges, extraIncoming) => {
  const incoming = new Map()
  const push = (target, source) => {
    if (!target || !source) return
    if (!incoming.has(target)) incoming.set(target, [])
    incoming.get(target).push(source)
  }
  for (const edge of listOf(edges)) {
    push(edge?.targetNodeID ?? edge?.target, edge?.sourceNodeID ?? edge?.source)
  }
  if (extraIncoming) {
    for (const [target, sources] of extraIncoming) {
      for (const source of listOf(sources)) push(target, source)
    }
  }
  return incoming
}

/** 反向 BFS：所有能经连线到达 targetId 的节点（不含自身） */
const reverseClosure = (targetId, incoming) => {
  const visited = new Set()
  const queue = [targetId]
  while (queue.length) {
    const current = queue.shift()
    for (const source of incoming.get(current) ?? []) {
      if (!visited.has(source)) {
        visited.add(source)
        queue.push(source)
      }
    }
  }
  return visited
}

const blocksOf = (node) => listOf(node?.blocks)

/** nodeId 是否（递归地）落在容器的内部子节点中 */
const containsDeep = (container, nodeId) =>
  blocksOf(container).some((block) => block?.id === nodeId || containsDeep(block, nodeId))

/** 由外到内收集包含链（调用方负责反转成由内到外） */
const collectLoopChain = (container, nodeId, chain) => {
  chain.push(container)
  for (const block of blocksOf(container)) {
    if (block?.type === LOOP_TYPE && containsDeep(block, nodeId)) {
      collectLoopChain(block, nodeId, chain)
      return
    }
  }
}

/** 包含该节点的迭代容器，由内到外（嵌套循环时层层都在链上） */
export function containingLoops(schemaNodes, nodeId) {
  const chain = []
  for (const node of listOf(schemaNodes)) {
    if (node?.type === LOOP_TYPE && containsDeep(node, nodeId)) {
      collectLoopChain(node, nodeId, chain)
      break
    }
  }
  return chain.reverse()
}

/** 容器内声明在 nodeId 之前的兄弟节点（nodeId 落在更深层容器时同样适用） */
const precedingBlockIds = (container, nodeId) => {
  const ids = []
  for (const block of blocksOf(container)) {
    if (block?.id === nodeId || containsDeep(block, nodeId)) break
    ids.push(block.id)
  }
  return ids
}

/**
 * 目标节点的可见上游节点 id 集合。
 *
 * 结果**包含**所属循环容器（容器 id 是局部变量 `{containerId}.item` 的引用目标），
 * 因此它可以直接用于「引用是否合法」的判定。
 */
export function visibleUpstreamNodeIds(schemaNodes, schemaEdges, nodeId) {
  const extraIncoming = new Map()
  let currentId = nodeId
  for (const container of containingLoops(schemaNodes, nodeId)) {
    extraIncoming.set(currentId, [container.id, ...precedingBlockIds(container, currentId)])
    currentId = container.id
  }
  return reverseClosure(nodeId, indexIncoming(schemaEdges, extraIncoming))
}

/** 取槽位的 ref 目标节点 id；非 ref 取值方式返回 null */
const refBlockId = (slot) => {
  const value = slot?.value
  if (value?.type !== 'ref' || !value.content || typeof value.content !== 'object') return null
  const blockId = value.content.blockID
  return blockId == null || String(blockId).trim() === '' ? null : String(blockId)
}

/** 节点输入侧的命名参数（业务输入 / 大模型参数 / 循环内部临时变量 / 循环次数 / 结束内容） */
const namedSlots = (inputs) => {
  if (!inputs || typeof inputs !== 'object') return []
  const slots = []
  for (const key of ['inputParameters', 'llmParam', 'variableParameters']) {
    for (const param of listOf(inputs[key])) {
      if (param?.name) slots.push({ name: param.name, slot: param.input })
    }
  }
  slots.push({ name: 'loopCount', slot: inputs.loopCount })
  slots.push({ name: 'content', slot: inputs.content })
  return slots
}

/**
 * 补齐后端建图时会自动加的首尾连线。
 *
 * 后端 `SchemaGraphBuilder.registerEdges` 在「没有以开始节点为源的连线」时补
 * `START → 首个可执行节点`，在「没有指向结束节点的连线」时补 `末个可执行节点 → END`。
 * 校验必须用与执行同一张图，否则会把「引用开始节点声明的输入」误判成非上游。
 */
const withImplicitTerminalEdges = (schemaNodes, schemaEdges) => {
  const edges = [...listOf(schemaEdges)]
  const startId = listOf(schemaNodes).find((node) => node?.type === START_TYPE)?.id
  const endId = listOf(schemaNodes).find((node) => node?.type === END_TYPE)?.id
  const executableIds = listOf(schemaNodes)
    .filter((node) => EXECUTABLE_CODES.has(node?.type))
    .map((node) => node.id)
  if (startId && executableIds.length && !edges.some((edge) => edge?.sourceNodeID === startId)) {
    edges.push({ sourceNodeID: startId, targetNodeID: executableIds[0] })
  }
  if (endId && executableIds.length && !edges.some((edge) => edge?.targetNodeID === endId)) {
    edges.push({
      sourceNodeID: executableIds[executableIds.length - 1],
      targetNodeID: endId,
    })
  }
  return edges
}

/**
 * 从开始节点正向可达的节点 id；没有开始节点时返回 null（表示不做可达性过滤）。
 *
 * 不可达节点在后端执行时会被跳过（`warnUnreachable` 告警），它们的引用不影响运行结果，
 * 因此不该因为它们拦下试运行。
 */
const reachableFromStart = (schemaNodes, edges) => {
  const startId = listOf(schemaNodes).find((node) => node?.type === START_TYPE)?.id
  if (!startId) return null
  const outgoing = new Map()
  for (const edge of edges) {
    if (!edge?.sourceNodeID) continue
    if (!outgoing.has(edge.sourceNodeID)) outgoing.set(edge.sourceNodeID, [])
    outgoing.get(edge.sourceNodeID).push(edge.targetNodeID)
  }
  const seen = new Set()
  const queue = [startId]
  while (queue.length) {
    const current = queue.shift()
    if (!current || seen.has(current)) continue
    seen.add(current)
    for (const next of outgoing.get(current) ?? []) queue.push(next)
  }
  return seen
}

/**
 * 校验所有节点的输入引用是否来自上游。
 *
 * 只校验**输入侧**：输出声明的 `input`（如循环节点的结果聚合）指向容器内部，
 * 属于节点自身的内聚关系，不是「引用上游变量」。
 *
 * @param {Array} schemaNodes `toWorkflowSchema` 产出的节点（含 blocks 嵌套）
 * @param {Array} schemaEdges `toWorkflowSchema` 产出的顶层连线
 * @returns {Array<{nodeId: string, nodeTitle: string, paramName: string, refNodeId: string,
 *   refNodeTitle: string, reason: 'not-found'|'not-upstream'}>} 违规项，空数组表示通过
 */
export function collectReferenceIssues(schemaNodes, schemaEdges) {
  const nodeIndex = indexSchemaNodes(schemaNodes)
  // 判定依据是后端实际执行的那张图：先补齐首尾隐式连线，再做闭包计算
  const edges = withImplicitTerminalEdges(schemaNodes, schemaEdges)
  const reachable = reachableFromStart(schemaNodes, edges)
  const issues = []

  const checkNode = (node, enclosingReachable) => {
    const inScope = !reachable || enclosingReachable || reachable.has(node?.id)
    // 开始节点声明的是工作流输入，没有上游可引用；不可达节点执行时会被跳过，无需校验
    if (inScope && node?.id && node.type !== START_TYPE) {
      const visible = visibleUpstreamNodeIds(schemaNodes, edges, node.id)
      for (const { name, slot } of namedSlots(node?.data?.inputs)) {
        const refNodeId = refBlockId(slot)
        if (!refNodeId) continue
        const base = {
          nodeId: node.id,
          nodeTitle: nodeTitleOf(node),
          paramName: name,
          refNodeId,
          refNodeTitle: nodeTitleOf(nodeIndex.get(refNodeId)),
        }
        if (!nodeIndex.has(refNodeId)) {
          issues.push({ ...base, reason: 'not-found' })
        } else if (!visible.has(refNodeId)) {
          issues.push({ ...base, reason: 'not-upstream' })
        }
      }
    }
    for (const block of blocksOf(node)) checkNode(block, inScope)
  }

  for (const node of listOf(schemaNodes)) checkNode(node, false)
  return issues
}
