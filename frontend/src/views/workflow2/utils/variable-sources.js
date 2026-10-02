/**
 * 抽屉「变量值」候选的来源归约（纯函数，可脱离 Vue 单独验证）。
 *
 * 后端的取值语义决定了「哪些变量真的能取到值」，本模块把它落成两条规则：
 *
 * 1. **同层**（{@link upstreamOutputsOf}）：迭代体是容器的内部结构。容器被折叠成 `blocks` 后，
 *    容器外节点的产出只存在于父图状态 `state[节点id]`，迭代体子图里读不到；子块的产出同样只
 *    活在子图里，不会冒泡到父图。因此两者之间的引用一定取不到值（后端 `NodeStateReader#read`
 *    返回 `null`，提示词占位符被替换成空串），候选列表必须按「同一个 parentNode」双向隔离。
 * 2. **每轮注入**（{@link injectOutputsOf}）：迭代体内的子块真正能用的是容器每轮注入的
 *    `index` / `item` / 容器的 `variableParameters` / 子块自己声明的当前项别名
 *    （后端 `IterateNode#buildRoundInput` 与 `#collectItemVarNames` 写入）。这些变量不在任何
 *    节点的 `data.outputs` 里，光靠遍历上游节点是拿不到的，需要单独补出来。
 *
 * 候选项的形状与抽屉原有的 `upstreamOutputs` 保持一致：
 * `{ name, type, schema?, description?, blockID, nodeLabel, nodeType }`，
 * 其中 `blockID` 就是回填给 `input.blockID` 的来源节点 id。
 */

/** 变量名（兼容字符串写法与对象写法；空名视为未声明） */
const nameOf = (item) => (typeof item === 'string' ? item : item?.name) || ''

/** 声明数组 → 有名字的声明 */
const declaredOf = (list) => (Array.isArray(list) ? list.filter((item) => nameOf(item)) : [])

/** 节点 id → 其父容器 id（顶层节点返回 null） */
const parentIdOf = (node) => node?.parentNode ?? null

/** 单个节点的产出声明 → 候选项（附加来源信息） */
const outputsOf = (node) =>
  declaredOf(node?.data?.outputs).map((output) => ({
    ...output,
    name: nameOf(output),
    blockID: node.id,
    nodeLabel: node.data?.label ?? '',
    nodeType: node.type,
  }))

/**
 * 与起点**同层**的可达上游变量候选项。
 *
 * 从 `nodeId` 出发沿连线反向遍历（边 `target → source`），只接受「父容器与起点相同」的源节点：
 *
 * - 起点是顶层节点（`parentNode` 为空）时，只收同为顶层节点的产出——迭代体子块的产出被自然排除，
 *   它们只存在于迭代体子图里，父图取不到；
 * - 起点是迭代体子块时，只收同一容器内其它子块的产出——容器外的节点与容器自身的聚合产出都被排除，
 *   迭代体每轮能拿到的注入变量交给 {@link injectOutputsOf}。
 *
 * 用 `visited` 去重（起点先入集合），因此迭代体那种「子块 ↔ 容器」的环形连线不会死循环。
 *
 * @param {Array} nodes 画布节点
 * @param {Array} edges 画布连线（含迭代体内部边）
 * @param {string} nodeId 起点节点 id
 * @returns {Array} 候选项数组
 */
export function upstreamOutputsOf(nodes = [], edges = [], nodeId) {
  const list = nodes ?? []
  const nodeMap = new Map(list.map((node) => [node?.id, node]))
  const start = nodeMap.get(nodeId)
  if (!start) return []

  const startParent = parentIdOf(start)
  const visited = new Set([nodeId])
  const queue = [nodeId]
  const upstream = []

  while (queue.length) {
    const currentId = queue.shift()
    for (const edge of edges ?? []) {
      if (edge?.target !== currentId) continue
      const sourceId = edge.source
      if (visited.has(sourceId)) continue
      const sourceNode = nodeMap.get(sourceId)
      if (!sourceNode) continue
      // 同层过滤：跨迭代体边界的引用后端一定取不到值
      if (parentIdOf(sourceNode) !== startParent) continue
      visited.add(sourceId)
      upstream.push(sourceNode)
      queue.push(sourceId)
    }
  }

  return upstream.flatMap(outputsOf)
}

/**
 * 迭代体内子块可用的「每轮注入」变量候选项（`blockID` 统一为容器 id）。
 *
 * 与后端口径对齐：
 * - `index`：轮次下标（`IterateNode#buildRoundInput` 的 `INDEX_VAR`）；
 * - `item`：当前迭代项（`ITEM_VAR`）；
 * - 容器的 `variableParameters` 声明的中间变量；
 * - 子块把某个输入指向容器自身时声明的别名（`IterateNode#collectItemVarNames` 会把它按当前项注入）。
 *
 * 只有确实挂在容器下的子块才有注入变量；顶层节点调用时返回空数组。
 *
 * @param {Array} nodes 画布节点
 * @param {object} container 迭代容器节点
 * @param {string} nodeId 子块 id
 * @returns {Array} 候选项数组
 */
export function injectOutputsOf(nodes = [], container = null, nodeId) {
  const containerId = container?.id
  if (!containerId) return []

  const node = (nodes ?? []).find((item) => item?.id === nodeId)
  if (parentIdOf(node) !== containerId) return []

  // 变量名 → 候选项（同名只留一份，先后顺序即展示顺序）
  const byName = new Map([
    ['index', { name: 'index', type: 'integer', description: '当前轮次下标（从 0 开始）' }],
    ['item', { name: 'item', type: 'object', description: '当前迭代项' }],
  ])

  for (const parameter of declaredOf(container.data?.variableParameters)) {
    byName.set(nameOf(parameter), {
      ...parameter,
      name: nameOf(parameter),
      type: parameter.type ?? parameter.input?.type ?? 'string',
    })
  }

  // 子块已声明的当前项别名：用户能看到它并把取值指向同一个来源
  for (const field of declaredOf(node.data?.inputs)) {
    const ref = field.input
    if (ref?.blockID !== containerId || !ref?.name) continue
    if (byName.has(ref.name)) continue
    byName.set(ref.name, {
      name: ref.name,
      type: field.type ?? ref.type ?? 'object',
      description: '当前迭代项（本节点声明的别名）',
    })
  }

  return [...byName.values()].map((item) => ({
    ...item,
    blockID: containerId,
    nodeLabel: `${container.data?.label ?? containerId}（每轮注入）`,
    nodeType: container.type,
  }))
}

/**
 * 迭代节点 `outputs` 与迭代体子块产出名不一致的变量名集合。
 *
 * 后端 `IterateNode#aggregate` 按**同名**把每轮子块的产出聚合成列表（见 12-工作流规范 §4.5），
 * 名字对不上时汇总为空列表，表现为「流程成功但没有结果」。这里只做提示，不阻断编辑
 * （改名过程中必然出现短暂的不一致）。
 *
 * 子块没有任何产出声明时返回空集合：那是「还没配好」的中间态，不该报错。
 *
 * @param {object} node 迭代节点
 * @param {Array} childrenNodes 迭代体子块（画布上 `parentNode` 指向该节点）
 * @returns {Set<string>} 不一致的输出变量名
 */
export function iterationOutputWarningsOf(node, childrenNodes = []) {
  const outputs = declaredOf(node?.data?.outputs).map(nameOf)
  if (!outputs.length) return new Set()

  const produced = new Set()
  for (const child of childrenNodes ?? []) {
    declaredOf(child?.data?.outputs).forEach((output) => produced.add(nameOf(output)))
  }
  if (!produced.size) return new Set()

  return new Set(outputs.filter((name) => !produced.has(name)))
}
