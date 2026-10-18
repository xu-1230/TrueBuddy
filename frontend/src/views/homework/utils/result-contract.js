/**
 * 作业批改页面的**结果契约**：从工作流定义里读出「要展示什么」与「数据从哪来」。
 *
 * 纯函数（无 Vue / 无 axios / 无其它模块依赖），可脱离页面单测 —— 因为这条契约错了的表现是
 * 「流程成功、页面却什么都不显示」，属于必须在提交前发现的错误。
 *
 * 刻意**不 import 任何 `@/` 模块**（连读运行态的辅助函数也不放这里）：`node:test` 原生不解析
 * `@/` 别名，一旦引入依赖，本模块的用例就得先注册 loader 钩子再动态 import（见
 * `tests/support/alias-hooks.mjs` 的说明），白白牺牲可测性。读运行态那两行放在调用方。
 *
 * 契约的由来（见 docs/12-工作流规范.md §4.6）：
 * - **输出节点（`type === '6'`）声明"要展示的结果"**：它的 `inputs` 的变量名即展示字段名，
 *   `description` 作字段标签。输出节点只有 `inputs`，没有 `outputs` 声明；
 * - 输出节点可以出现在**迭代体内**（声明"单份要展示什么"）或**顶层**（声明"整批要展示什么"）。
 *   迭代体内那份的产出**不会**出现在父状态的 `state[输出节点id]` 里（子图状态独立），
 *   它被 `IterateNode` 按同名聚合到**迭代节点**上，所以数据要从迭代节点的产出里取；
 * - 于是「数据来源节点」可能是迭代节点（体内输出节点，每个字段是一个列表、下标即学生），
 *   也可能是输出节点自己（顶层输出节点，值是标量）。
 */

/** 输出节点的类型编码（与后端 `NodeActionFactory` 一致） */
export const OUTPUT_NODE_TYPE = '6'

/** 迭代节点的类型编码 */
const ITERATE_NODE_TYPE = '4'

const typeOf = (node) => String(node?.type ?? '')

/**
 * 解析画布定义：接受 JSON 字符串或已解析对象，结构不是 `{nodes, edges}` 时返回 null。
 *
 * @param {string|object|null} schemaJson 工作流详情里的 `schemaJson`
 */
export function parseDefinition(schemaJson) {
  if (schemaJson == null) return null
  if (typeof schemaJson === 'object') {
    return Array.isArray(schemaJson.nodes) ? schemaJson : null
  }
  try {
    const parsed = JSON.parse(schemaJson)
    return parsed && Array.isArray(parsed.nodes) ? parsed : null
  } catch {
    return null
  }
}

/**
 * 找输出节点：**先看顶层**（整批结构），再进迭代体的 `blocks`（单份结构）。
 *
 * 允许多个输出节点，这里取"最外层优先"的第一个 —— 页面要展示的是整批结果，
 * 顶层的那个比体内的更贴近页面；定义里没有顶层输出节点时（当前内置定义即如此）
 * 才退到体内的那个。
 *
 * @returns {{node: object, container: object|null}|null} `container` 非空表示输出节点在迭代体内
 */
export function findOutputNode(definition) {
  const nodes = definition?.nodes ?? []
  for (const node of nodes) {
    if (typeOf(node) === OUTPUT_NODE_TYPE) {
      return { node, container: null }
    }
  }
  for (const node of nodes) {
    if (typeOf(node) !== ITERATE_NODE_TYPE) continue
    const block = (node.blocks ?? []).find((item) => typeOf(item) === OUTPUT_NODE_TYPE)
    if (block) return { node: block, container: node }
  }
  return null
}

/** 一条连线的起点 / 终点（兼容 `source` 与 `sourceNodeID` 两种写法） */
const endpointsOf = (edge) => ({
  source: edge?.sourceNodeID ?? edge?.source ?? null,
  target: edge?.targetNodeID ?? edge?.target ?? null,
})

/**
 * 结果契约。返回 null 表示"这个工作流没有输出节点" —— 页面据此拦下本次执行并提示用户。
 *
 * @param {string|object|null} schemaJson 工作流详情里的 `schemaJson`
 * @returns {{
 *   outputNodeId: string,
 *   sourceNodeId: string,
 *   iteration: boolean,
 *   fields: Array<{name: string, label: string, type: string}>,
 *   summaryNodeId: string|null,
 * }|null}
 *   `sourceNodeId` 是**运行态里取数据的节点**；`iteration` 为真时每个字段是一个列表，
 *   下标即学生；`summaryNodeId` 是汇总节点（迭代节点的直接下游），拿不到时为 null。
 */
export function resultContractOf(schemaJson) {
  const definition = parseDefinition(schemaJson)
  const found = definition ? findOutputNode(definition) : null
  if (!found) return null

  const fields = (found.node.data?.inputs ?? [])
    .filter((field) => field?.name)
    .map((field) => ({
      name: field.name,
      label: field.description || field.name,
      type: field.type ?? field.input?.type ?? 'string',
    }))

  const sourceNodeId = found.container ? found.container.id : found.node.id
  // 迭代节点的下游顶层节点即汇总节点（"班级统计"的来源）；顶层输出节点时无汇总
  const summaryEdge = found.container
    ? (definition.edges ?? []).map(endpointsOf).find((edge) => edge.source === sourceNodeId)
    : null

  return {
    outputNodeId: found.node.id,
    sourceNodeId,
    iteration: Boolean(found.container),
    fields,
    summaryNodeId: summaryEdge?.target ?? null,
  }
}

/** 归一化成数组：`null` / `undefined` 当空，标量当单项（与后端 `NodeStateReader#toList` 同口径） */
const toArray = (value) => {
  if (value == null) return []
  return Array.isArray(value) ? value : [value]
}

/**
 * 把运行态里的产出按契约拼成**逐条记录**（一个学生一条）。
 *
 * 每个字段在迭代聚合后都是一个列表，**下标相同即同一个学生**；长度不一致时按**最长的**对齐、
 * 短的补 `null`（取最短会静默丢学生，宁可让页面显示一条带空缺的卡片）。
 *
 * @param {object|null} result 数据来源节点的产出（`nodeRuntimeOf(state, sourceNodeId).result`）
 * @param {Array<{name: string}>} fields 契约里的字段
 * @returns {Array<Record<string, unknown>>} 每条记录的字段名即 `fields[].name`
 */
export function recordsOf(result, fields = []) {
  const columns = fields.map((field) => toArray(result?.[field?.name]))
  const length = columns.reduce((max, column) => Math.max(max, column.length), 0)

  const records = []
  for (let index = 0; index < length; index += 1) {
    const record = {}
    fields.forEach((field, column) => {
      const values = columns[column]
      record[field.name] = index < values.length ? values[index] : null
    })
    records.push(record)
  }
  return records
}
