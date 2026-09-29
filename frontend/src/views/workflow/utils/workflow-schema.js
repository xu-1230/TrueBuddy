/**
 * 画布（Vue Flow store）→ 后端工作流 schema 的序列化。
 *
 * 后端 `tb-module-workflow` 的 `SchemaNormalizer.readDetail` 同时支持「外层详情对象」与
 * 「裸 schema」（顶层直接 `{ nodes, edges }`）两种写法，因此这里直接输出裸 schema。
 * 节点类均标注 `@JsonIgnoreProperties(ignoreUnknown = true)`，对多余字段宽容，但有三处硬约束：
 *
 * 1. `data.inputs` 必须是**对象**（`NodeInputsDef`）：画布自身的 `data.inputs` 是变量声明**数组**，
 *    直接透传会导致反序列化失败并抛 `WORKFLOW_SCHEMA_INVALID`，所以只映射 `data.nodeMeta`
 *    与 `data.outputs`；打开工作流时读回的快照会把原始 inputs（ref 结构）暂存在 `data.backendInputs`，
 *    这一份会原样回写到 `data.inputs` —— 人工确认节点的待确认清单、循环节点的迭代配置都依赖它，
 *    丢了它就只剩「结构可执行」而参数为空（画布自建节点即属这种情况，属已知限制）。
 * 2. `type` 必须在 `WorkflowNodeType` 白名单内（1/start/input、2/end/output、3/llm、
 *    21/iteration/loop、30/human/confirm/question）：`iteration-start` / `iteration-end`
 *    会抛 `WORKFLOW_NODE_TYPE_UNSUPPORTED`。迭代起止节点天然带 `parentNode`，因此不会进入顶层；
 *    写入 `blocks` 时也会再按类型过滤一遍。
 * 3. 顶层 `edges` 不能包含迭代内部连线：`normalizeEdges` 的 `nodeIndex` 连 `blocks` 里的子节点
 *    一起索引，内部连线若留在顶层会被当成真实图边、破坏执行拓扑。故顶层只保留两端都在顶层
 *    节点集合内的连线。
 *
 * 另一个执行侧约束：循环节点内部不能为空（`SchemaGraphBuilder.buildLoopAction` 会抛
 * `WORKFLOW_GRAPH_BUILD_FAILED`），所以没有合法子节点的迭代节点会被跳过，并在 `skipped` 中返回。
 */
import { collectReferenceIssues } from './workflow-reference'

/** 前端语义类型 → 后端 WorkflowNodeType 规范编码（仅供本模块内部映射） */
const BACKEND_NODE_TYPE = {
  start: '1',
  input: '1',
  end: '2',
  output: '2',
  llm: '3',
  iteration: '21',
  human: '30',
}

/**
 * 节点数据里的输出声明：字符串写法补成对象，字段对齐后端 OutputParamDef。
 *
 * 注意保留 `input`（循环节点的输出用它声明「结果聚合来源」，例如 results ← 子节点的 output）与
 * `required` / `defaultValue`（开始节点的入参声明）：这些字段画布不编辑，但执行时必须原样回传，
 * 否则循环节点取不到聚合来源，只能退回「唯一非空输出」，把整份结果 map 拼成字符串返回，
 * 表现为批改记录解析不出总分与评语。
 */
const normalizeOutputs = (outputs) => {
  if (!Array.isArray(outputs)) return []
  return outputs
    .map((item) => (typeof item === 'string' ? { name: item } : item))
    .filter((item) => item?.name)
    .map((item) => {
      const output = { name: item.name, type: item.type || 'string' }
      if (item.description) output.description = item.description
      if (item.required != null) output.required = item.required
      if (item.defaultValue != null) output.defaultValue = item.defaultValue
      if (item.schema && typeof item.schema === 'object') output.schema = item.schema
      if (item.input && typeof item.input === 'object') output.input = item.input
      return output
    })
}

/** 可执行节点类型：后端会为它们注册图节点，开始 / 结束仅映射为图的 START / END */
const EXECUTABLE_TYPES = new Set(['llm', 'iteration', 'human'])

/** 迭代节点的内部子节点（只保留类型合法的部分） */
const collectBlocks = (node, nodes) =>
  nodes.filter((child) => child?.parentNode === node.id && BACKEND_NODE_TYPE[child.type])

/** 画布顺序：先 x 后 y，得到「从左到右、从上到下」的直觉执行顺序 */
const byCanvasOrder = (a, b) => {
  const ax = a.position?.x ?? 0
  const bx = b.position?.x ?? 0
  if (ax !== bx) return ax - bx
  return (a.position?.y ?? 0) - (b.position?.y ?? 0)
}

/**
 * node 的上游节点（沿入边反向 BFS，由近到远）。
 *
 * **只认真正的上游**：曾经用「画布上第一个大模型节点」兜底，当它与本节点没有通路时会造出
 * 一条非上游引用——运行时必然取到空值，也会被引用校验拦下。宁可返回空，也不造非法引用。
 */
const upstreamNodesOf = (nodeId, nodes, edges) => {
  const incoming = new Map()
  for (const edge of Array.isArray(edges) ? edges : []) {
    if (!edge?.source || !edge?.target) continue
    if (!incoming.has(edge.target)) incoming.set(edge.target, [])
    incoming.get(edge.target).push(edge.source)
  }
  const seen = new Set()
  const ordered = []
  const queue = [nodeId]
  while (queue.length) {
    for (const source of incoming.get(queue.shift()) ?? []) {
      if (seen.has(source)) continue
      seen.add(source)
      const found = nodes.find((item) => item?.id === source)
      if (found) ordered.push(found)
      queue.push(source)
    }
  }
  return ordered
}

/**
 * 画布自建的人工确认节点：生成 `plan` 输入引用。
 *
 * 后端 `HumanConfirmNodeAction` 从 `inputParameters` 里名为 `plan` 的 ref 读取待确认清单，
 * 而画布上的 `data.inputs` 只是变量声明数组、没有 ref 语义，因此这里按画布连线推断：
 * 取「上游中最近的一个、且声明了输出的大模型节点」，引用其第一个输出。
 * 找不到就保持无输入（执行时清单为空，确认卡片会提示「无可批改项」）。
 *
 * @param {Object} node 人工确认节点
 * @param {Array} nodes 画布节点
 * @param {Array} edges 画布连线
 * @returns {Object|null} `data.inputs` 结构；无法推断时返回 null
 */
const buildPlanInput = (node, nodes, edges) => {
  const upstream = upstreamNodesOf(node.id, nodes, edges).find(
    (item) => item.type === 'llm' && normalizeOutputs(item.data?.outputs).length > 0,
  )
  if (!upstream) return null

  const outputName = normalizeOutputs(upstream.data.outputs)[0].name
  return {
    inputParameters: [
      {
        name: 'plan',
        input: {
          type: 'string',
          value: {
            type: 'ref',
            // 与后端 schema 中的 ref 结构保持一致
            content: { blockID: upstream.id, name: outputName, source: 'block-output' },
            rawMeta: { type: 1 },
          },
        },
      },
    ],
  }
}

/** 开始节点声明的文件树输出名：后端 mock 的「规划」语义靠它产出待批改清单 */
const FILE_TREE_NAME = 'fileTree'

/**
 * 画布自建的大模型节点：开始节点声明了 `fileTree` 输出时，自动补一个同名输入引用。
 *
 * 后端 `MockLlmEngine` 的规划语义（标题含「规划」/「清单」）会遍历 `fileTree` 生成清单，
 * 而画布缺少「节点输入绑定」的编辑能力，自建节点序列化后没有任何输入，规划结果只能是空清单。
 * 这里按约定引用开始节点声明的 fileTree；开始节点没有声明该输出（例如新建的空白画布）时不注入，
 * 避免凭空造数据。
 *
 * @param {Array} nodes 画布节点
 * @returns {Object|null} `data.inputs` 结构；无 fileTree 声明时返回 null
 */
const buildFileTreeInput = (nodes) => {
  const start = nodes.find(
    (item) =>
      item?.type === 'input' &&
      normalizeOutputs(item.data?.outputs).some((output) => output.name === FILE_TREE_NAME),
  )
  if (!start) return null
  return {
    inputParameters: [
      {
        name: FILE_TREE_NAME,
        input: {
          type: 'list',
          schema: { type: 'string' },
          value: {
            type: 'ref',
            content: { blockID: start.id, name: FILE_TREE_NAME, source: 'block-output' },
            rawMeta: { type: 99 },
          },
        },
      },
    ],
  }
}

/**
 * 单个画布节点 → schema 节点（递归处理迭代内部子节点）。
 * @param {Object} node Vue Flow 节点
 * @param {Array} nodes 全量节点（用于查找迭代子节点）
 * @param {Array} edges 画布连线（用于推断人工确认节点的清单来源）
 */
const toSchemaNode = (node, nodes, edges) => {
  const data = node.data ?? {}
  const position = node.position ?? { x: 0, y: 0 }
  const schemaData = {
    nodeMeta: {
      title: data.label ?? node.id,
      subTitle: '',
      description: '',
    },
    // 画布自身的 data.inputs 是变量声明数组，与后端 NodeInputsDef 的对象结构不兼容，故不输出；
    // 但从后端加载的画布会把原始 inputs（含 ref 结构）暂存在 backendInputs，执行时必须原样回传，
    // 否则人工确认节点拿不到待确认清单、循环节点拿不到迭代配置。
    outputs: normalizeOutputs(data.outputs),
  }
  if (data.backendInputs && typeof data.backendInputs === 'object' && !Array.isArray(data.backendInputs)) {
    schemaData.inputs = data.backendInputs
  } else if (node.type === 'llm') {
    // 画布自建的大模型节点：补上开始节点的文件树引用（后端规划语义依赖它）
    const fileTreeInput = buildFileTreeInput(nodes)
    if (fileTreeInput) schemaData.inputs = fileTreeInput
  } else if (node.type === 'human') {
    // 画布自建的人工确认节点：按连线推断清单来源，避免中断后清单为空而无法确认
    const planInput = buildPlanInput(node, nodes, edges)
    if (planInput) schemaData.inputs = planInput
  }
  const schemaNode = {
    id: node.id,
    type: BACKEND_NODE_TYPE[node.type],
    data: schemaData,
    blocks: [],
    edges: [],
    // 画布坐标仅供展示与排障，后端执行时不使用
    meta: { position: { x: Math.round(position.x ?? 0), y: Math.round(position.y ?? 0) } },
  }
  if (node.type === 'iteration') {
    schemaNode.blocks = collectBlocks(node, nodes).map((child) => toSchemaNode(child, nodes, edges))
  }
  return schemaNode
}

/**
 * 画布 → 后端工作流 schema（纯函数，不改动入参）。
 *
 * 关于自动串接：后端图编译器只在「缺少首尾连线」时自动补 `START→首个可执行节点` 与
 * `末个可执行节点→END`；若画布完全没连线且可执行节点不止一个，中间没有边，
 * 执行时会因边指向未知节点报 `edge with sourceId: 'xxx' doesn't exist!`。
 * 因此完全没有连线时，这里按画布位置顺序自动串成一条链（首尾仍交给后端补齐）。
 *
 * @param {Array} nodes 画布节点（`useVueFlow().getNodes.value`）
 * @param {Array} edges 画布连线（`useVueFlow().getEdges.value`）
 * @returns {{ nodes: Array, edges: Array, versions: Object, skipped: Array, autoLinked: boolean,
 *   referenceIssues: Array }}
 *   `skipped` 为未参与执行的节点（原因：类型不支持 / 循环节点缺少内部子节点）；
 *   `autoLinked` 为 true 表示边是按节点位置自动串接的，调用方可据此提示用户；
 *   `referenceIssues` 为「输入引用必须来自上游」的校验结果（空数组表示通过），
 *   前端据此在试运行前拦截——后端不做这项校验。
 */
export function toWorkflowSchema(nodes = [], edges = []) {
  const list = Array.isArray(nodes) ? nodes : []
  const topNodes = list.filter((node) => node && !node.parentNode)
  const topIds = new Set()
  const executables = []
  const schemaNodes = []
  const skipped = []

  for (const node of topNodes) {
    if (!BACKEND_NODE_TYPE[node.type]) {
      skipped.push({ id: node.id, label: node.data?.label, reason: '类型不支持' })
      continue
    }
    if (node.type === 'iteration' && collectBlocks(node, list).length === 0) {
      skipped.push({ id: node.id, label: node.data?.label, reason: '迭代节点缺少内部子节点' })
      continue
    }
    topIds.add(node.id)
    if (EXECUTABLE_TYPES.has(node.type)) {
      executables.push({ id: node.id, position: node.position })
    }
    schemaNodes.push(toSchemaNode(node, list, edges))
  }

  let schemaEdges = (Array.isArray(edges) ? edges : [])
    .filter((edge) => edge && topIds.has(edge.source) && topIds.has(edge.target))
    .map((edge) => ({ sourceNodeID: edge.source, targetNodeID: edge.target }))

  let autoLinked = false
  if (!schemaEdges.length && executables.length > 1) {
    const chain = [...executables].sort(byCanvasOrder)
    schemaEdges = chain.slice(1).map((node, index) => ({
      sourceNodeID: chain[index].id,
      targetNodeID: node.id,
    }))
    autoLinked = true
  }

  // 引用校验必须放在自动串接之后：判定依据是最终要执行的那张图
  // （隐式首尾连线与可达性过滤由校验器内部按后端建图规则补齐）
  const referenceIssues = collectReferenceIssues(schemaNodes, schemaEdges)

  return {
    nodes: schemaNodes,
    edges: schemaEdges,
    versions: { loop: 'v2' },
    skipped,
    autoLinked,
    referenceIssues,
  }
}
