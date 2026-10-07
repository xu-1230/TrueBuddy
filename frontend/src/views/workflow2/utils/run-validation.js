/**
 * 运行前的画布校验：**复刻 flowgram 运行时的 TaskValidate 思路**（先校验再运行，见其官方文档
 * 「运行时 API · 使用示例 4」的 `safeRunWorkflow`）。
 *
 * flowgram 在启动任务前用 `TaskValidateAPI({schema, inputs})` 跑一组校验器
 * （`schemaFormat` / `cycleDetection` / `edgeSourceTargetExist` / `startEndNode`），逐个 try/catch
 * 收集错误、以 `{valid, errors[]}` 表达失败，页面拿到错误列表后拦住本次运行。这里把同一套思路
 * 落到本项目的画布数据上，**只保留五类真正可达、且不拦会出问题的检查**：
 *
 * 1. **循环连接**（对应 `cycleDetection`）：后端图引擎允许成环（`spring-ai-alibaba-graph-core` 的
 *    `Errors` 枚举里没有任何环相关错误），画布上连出回边后要跑到递归上限才失败，属于典型
 *    「运行前就该发现」的配置问题；
 * 2. **开始 / 结束节点缺失**（对应 `startEndNode` 的「必须存在」部分）：后端给「无入边 / 无出边」的
 *    节点自动接 `START` / `END`，缺这两个节点不会让建图失败，但流程语义已经不完整。只认**顶层**的
 *    开始 / 结束（迭代容器内的同类型节点不算入口 / 出口），与 flowgram 只统计顶层 `nodes` 的口径一致。
 * 3. **顶层断链**（flowgram 没有对应校验器，是本项目按后端装配规则补的一条）：后端把「无入边」的
 *    节点自动接 `START`，漏连线的节点因此变成**并行起点**——运行不报错，只在结果面板上表现为
 *    「失败 / 本次无产出变量」。只查顶层非「开始」节点，理由见 {@link runValidationErrors} 第 3 项；
 * 4. **变量引用取不到值**（同样是为后端「取不到值就静默替换成空串」补的）：引用的来源节点已删 / 跨了
 *    迭代体边界 / 不在上游 / 没声明该变量时，运行不会失败，但提示词里对应那一段会变成空串。判定直接
 *    复用数据抽屉的候选口径，理由见 {@link runValidationErrors} 第 4 项；
 * 5. **变量还没有绑定取值**（同上，是第 4 项的互补面）：抽屉里新增的变量行只写了名字、没选取值来源
 *    （或只写了常量——后端压根不读 `content`）时，运行时同样取不到值。画布上只有告警色、运行不拦，
 *    于是「变量一个都没配」也能跑通，理由见 {@link runValidationErrors} 第 5 项。
 *
 * 不在这里重复的检查（各有更权威或更便宜的把关处，按「不处理实际不会发生的错误」略过；每条都注明
 * 它对应 flowgram 的哪个校验器）：
 * - 节点类型与 schema 结构（`schemaFormat`）：请求体由后端 DTO 反序列化，节点只能由面板白名单
 *   （`'1'`~`'5'`）创建；
 * - 连线 id 非空且唯一（`schemaFormat` 对边的必填字段）：后端 `WorkflowProcessLoader.validate` 已校验，
 *   前端新建连线由 Vue Flow 生成 id；
 * - 边两端必须存在（`edgeSourceTargetExist`）：删除节点会连带删掉相关连线（`removeNodes(..., true, ...)`，
 *   Vue Flow 的 `createChildrenRemovalChanges` 内部同样会清掉挂在子块上的边）；
 * - 开始 / 结束「多于一个」（`startEndNode` 的「只能有一个」部分）：节点库面板已把这两个类型做成全图
 *   唯一（`NodePanel` 的 `UNIQUE_TYPES`）；
 * - 迭代体内部的 block-start / block-end（`startEndNode` 对 `blocks` 层的递归要求）：本 schema 里没有
 *   这两种节点，迭代体的入口 / 出口是「容器自身 id」的虚拟边（后端接成 `START` / `END`），没有对应物；
 * - 空的迭代容器：后端 `IterateNode` 对没有子块的迭代体返回空汇总，不是错误；
 * - 输入参数（flowgram 在 schema 校验通过后用 start 节点 `outputs` 对 `inputs` 做的 JSON Schema 校验）：
 *   `/chat` 首轮不接受业务输入（开始节点的产出是模拟数据）。
 *
 * ⚠️ **唯一会被未来功能推翻的前提**：上面「结构类检查不可达」全都成立在「画布数据只来自本地」——
 * 默认定义（`default-flow.js`）、面板白名单创建、拖拽迁移三处（`WorkflowView.vue` 的 `defaultCanvas`）。
 * 「打开工作流」（从后端读回定义填进画布）已经实现（画布页按路由 `workflow_id` 载入），
 * 数据形态已不再完全由前端保证，届时必须补上三项：`schemaFormat`（节点 / 边的必填字段）、
 * `edgeSourceTargetExist`（边端点存在性）、节点类型白名单。
 */
import { ITERATE_NODE_TYPE, isNestedNode } from './iteration.js'
import { metaOf } from './node-meta.js'
import { injectOutputsOf, unboundNamesOf, upstreamOutputsOf } from './variable-sources.js'

/** 开始 / 结束节点的类型编码（与 `NodePanel` 的 `UNIQUE_TYPES` 同一口径） */
const START_TYPE = '1'
const END_TYPE = '2'

/** 错误信息里的节点名：节点名称优先，回落节点 id（用户要能一眼定位到具体节点） */
const nameOf = (node) => node?.data?.label || node?.id || '未命名节点'

/** 一条错误里最多点名的条目数：再多的折叠成「等 N 个 / 处」，避免弹窗刷出一长串 */
const LISTED_LIMIT = 3

/**
 * 断链错误里的节点名清单：`「结束」` / `「A」「B」「C」等 5 个节点`。
 *
 * @param {Array} nodes 断链的节点（按画布顺序）
 */
const namesOf = (nodes) => {
  const shown = nodes.slice(0, LISTED_LIMIT).map((node) => `「${nameOf(node)}」`).join('')
  return nodes.length > LISTED_LIMIT ? `${shown}等 ${nodes.length} 个节点` : shown
}

/**
 * 问题清单 → 一句话：`甲；乙` / `甲；乙；丙；等 5 处`（超出 {@link LISTED_LIMIT} 的折叠掉）。
 *
 * @param {Array<string>} problems 每条问题的描述
 */
const problemsTextOf = (problems) => {
  const shown = problems.slice(0, LISTED_LIMIT).join('；')
  return problems.length > LISTED_LIMIT ? `${shown}；等 ${problems.length} 处` : shown
}

/**
 * 节点类型编码：统一转成字符串再比较。
 *
 * 代码库对「数字类型」是明确容忍的（见 `utils/node-meta.js` 的 `metaOf`：旧的脏数据里若写成数字，
 * 命中原类型也比显示「未知节点」有用），校验必须用同一口径——否则一个 `type: 1` 的开始节点会被
 * 判成「没有开始节点」，把用户的运行直接拦在门外。
 */
const typeOf = (node) => String(node?.type ?? '')

/**
 * 一条边是否属于某一层：**两端都落在该层的节点集合内**。
 *
 * 与「折叠成 `blocks`」的口径一致：这样「迭代容器 ↔ 它自己的子块」这类边不会被算进容器内部那层——
 * 它是子图的虚拟入口 / 出口（后端接成 `START` / `END`），若算进去，「入口 + 出口」两条边会立刻
 * 构成一个假环。
 */
const edgeWithin = (edge, ids) => ids.has(edge?.source) && ids.has(edge?.target)

/**
 * 某一层内是否存在环：三色 DFS，判据与 flowgram 的 `cycleDetection` 相同
 * （遇到处于「正在访问」状态的邻居即为回边；每个节点只走一遍）。
 *
 * @param {Set<string>} ids 该层的节点 id 集合
 * @param {Array} edges 该层的边（两端都在 `ids` 内）
 * @returns {boolean}
 */
const hasCycle = (ids, edges) => {
  const adjacency = new Map([...ids].map((id) => [id, []]))
  edges.forEach((edge) => { adjacency.get(edge.source)?.push(edge.target) })

  const visiting = new Set()
  const visited = new Set()
  const visit = (id) => {
    visiting.add(id)
    for (const next of adjacency.get(id) ?? []) {
      if (visiting.has(next)) return true
      if (!visited.has(next) && visit(next)) return true
    }
    visiting.delete(id)
    visited.add(id)
    return false
  }

  return [...ids].some((id) => !visited.has(id) && visit(id))
}

/**
 * 画布上的校验层级：**顶层** + **每个迭代容器的直接子块**。
 *
 * 分层口径与**画布的展开口径**一致：子块与顶层由 `parentNode` 区分（容器被删后的悬空子块按顶层算，
 * 与提交给后端的口径相同），容器类型走 {@link typeOf} 的字符串比较（与 `toCanvasFlow` /
 * `metaOf` 相同，容忍数字类型的脏数据）；容器互相嵌套时各自成层，因此这里不需要递归。
 * 「有多少层」用 `ids.size` 兜掉空层——空迭代体不是错误，也没必要参与环检测。
 *
 * @param {Array} nodes 画布节点
 * @returns {Array<{scope: string, subject: string, ids: Set<string>}>} 每层的范围描述与节点集合
 */
const layersOf = (nodes = []) => {
  const list = nodes ?? []
  const layers = [{
    scope: '工作流',
    subject: '节点',
    ids: new Set(list.filter((node) => node?.id && !isNestedNode(list, node.id)).map((node) => node.id)),
  }]
  list
    .filter((node) => typeOf(node) === ITERATE_NODE_TYPE)
    .forEach((container) => {
      const ids = new Set(list.filter((node) => node?.parentNode === container.id).map((node) => node.id))
      if (ids.size) {
        layers.push({ scope: `迭代容器「${nameOf(container)}」内部`, subject: '子块', ids })
      }
    })
  return layers
}

/**
 * 节点声明里「按引用取值」的槽位：`data.inputs[]` 与迭代容器的 `data.variableParameters[]`。
 *
 * 只认 `input.blockID` 存在的槽位（`{blockID, name}` 形式）：写常量的槽位（`{content}`）、以及只声明了
 * 变量名还没选来源的槽位都不算引用——后者画布上已用告警色提示，不属于「引用取不到值」。
 *
 * @param {object} node 画布节点
 * @returns {Array<{field: object, ref: object, where: string}>} `where` 用于错误文案（输入变量 / 迭代变量）
 */
const refsOf = (node) => {
  const slots = []
  const collect = (declared, where) => {
    if (!Array.isArray(declared)) return
    declared.forEach((field) => {
      if (field && typeof field === 'object' && field.input?.blockID) {
        slots.push({ field, ref: field.input, where })
      }
    })
  }
  collect(node?.data?.inputs, '输入变量')
  collect(node?.data?.variableParameters, '迭代变量')
  return slots
}

/**
 * 一个取不到值的引用**为什么**取不到值：按「来源在不在 → 是否同层 → 是否可达 → 变量名是否声明」的顺序
 * 判定，与抽屉候选项的过滤顺序一致，因此每一条都能落到一个可操作的修复动作上。
 *
 * @param {Array} list 画布节点
 * @param {object} node 引用所在的节点
 * @param {object} ref 引用（`{blockID, name}`）
 * @param {Set<string>} upstreamIds 该节点能取到值的来源节点 id 集合
 * @returns {string} 追加在「节点「X」的输入变量「y」：」之后的原因描述
 */
const refProblemOf = (list, node, ref, upstreamIds) => {
  if (!ref.name) return '没有指定来源变量名'
  const source = list.find((item) => item?.id === ref.blockID)
  if (!source) return `来源节点（${ref.blockID}）不在画布上`
  if ((source.parentNode ?? null) !== (node.parentNode ?? null)) {
    return `来源「${nameOf(source)}」在另一层，跨迭代体边界取不到值`
  }
  if (!upstreamIds.has(ref.blockID)) return `来源「${nameOf(source)}」没有连到本节点（不在上游）`
  return `来源「${nameOf(source)}」没有声明产出变量「${ref.name}」`
}

/**
 * 校验画布定义，返回**错误列表**（空数组 = 可以运行）。
 *
 * @param {Array} nodes 画布节点（Vue Flow 的实时节点：靠 `parentNode` 区分顶层与迭代体子块）
 * @param {Array} edges 画布连线（`source` / `target`）
 * @returns {string[]} 错误信息，每条都可直接展示给用户
 */
export function runValidationErrors(nodes = [], edges = []) {
  const list = nodes ?? []
  const links = edges ?? []
  const layers = layersOf(list)
  const errors = []

  // 1) 循环连接：逐层检查，只报第一处（与 flowgram「一个校验器最多贡献一条错误」的口径一致）
  const cyclic = layers.find((layer) => hasCycle(layer.ids, links.filter((edge) => edgeWithin(edge, layer.ids))))
  if (cyclic) {
    errors.push(`${cyclic.scope}存在循环连接：${cyclic.subject}首尾相连形成回路`)
  }

  // 2) 开始 / 结束节点：只看顶层 —— 迭代体子块不是流程的入口 / 出口
  //
  // 文案统一带上「顶层」限定：开始 / 结束被**拖进迭代容器**时（`planMigration` 不限类型，这是可达的），
  // 节点其实还在画布上，只说「缺少」会让人以为被删了。口径与 flowgram 的 `startEndNode` 一致——它只
  // 统计顶层 `nodes` 里的 `Start` / `End`，嵌在 `blocks` 里的同类型节点不算工作流的入口 / 出口。
  const [topLevel] = layers
  const startName = metaOf(START_TYPE).name
  const endName = metaOf(END_TYPE).name
  const hasStart = list.some((node) => topLevel.ids.has(node?.id) && typeOf(node) === START_TYPE)
  const hasEnd = list.some((node) => topLevel.ids.has(node?.id) && typeOf(node) === END_TYPE)
  if (!hasStart && !hasEnd) {
    errors.push(`工作流缺少顶层的「${startName}」和「${endName}」节点`)
  } else if (!hasStart) {
    errors.push(`工作流缺少顶层的「${startName}」节点`)
  } else if (!hasEnd) {
    errors.push(`工作流缺少顶层的「${endName}」节点`)
  }

  // 3) 断链：顶层除「开始」以外的节点都必须有**连入顶层的连线**
  //
  // 后端装配图时给「无入边」的节点自动接 `StateGraph.START`（见 `WorkflowController#createGraph` 与
  // docs/12-工作流规范.md §3.1），所以漏连一条线**不会**让建图失败：节点被悄悄变成**并行起点**，拿不到
  // 上游数据，直到运行后才发现「失败 / 本次无产出变量」——「看着能跑、跑出来全是失败」多半就是它，
  // 属于典型的「运行前就该发现」的配置问题（与 flowgram 把结构问题拦在运行之前同一动机）。
  //
  // 只查顶层、且只在「开始」存在时查：
  // - 顶层只有「开始」一个合法入口，其余节点没有入边必然是漏连线（用户最容易漏的就是「中间节点 → 结束」）；
  // - 迭代体**不查**：子块的入口 / 出口后端有「按入度 / 出度兜底补齐」的规则（docs/12 §4.5：单子块即
  //   `START → 子块 → END`），把一个新节点拖进容器、还没来得及连线是正常中间态，拦下来会把正常形态误判；
  // - 「开始」本身缺失时不再逐节点报断链：缺失已单独报出，此时每个节点都没有上游，报出来只会淹没信息。
  if (hasStart) {
    const linkedTargets = new Set(
      links.filter((edge) => edgeWithin(edge, topLevel.ids)).map((edge) => edge.target),
    )
    const dangling = list.filter((node) => (
      node?.id !== undefined
      && topLevel.ids.has(node.id)
      && typeOf(node) !== START_TYPE
      && !linkedTargets.has(node.id)
    ))
    if (dangling.length) {
      errors.push(`工作流存在断链：${namesOf(dangling)}没有连入的连线，运行时会作为独立起点执行、拿不到上游数据`)
    }
  }

  // 4) 变量引用：引用的来源必须真的能取到值
  //
  // 后端取值失败是**静默降级**：`NodeStateReader#read` 取不到时返回 `null`，`LlmNode#renderPrompt` 再把
  // 提示词里对应的占位符替换成**空串**（其注释原文：「取不到值（含模板里写了未声明的变量）时替换为空串」）。
  // 于是引用断了既不会让运行失败、也不会在结果面板上报错——提示词里悄悄少一段，模型照样返回结果，
  // 联调期极难发现，同样属于「运行前就该发现」的配置问题。
  //
  // 判定口径**直接复用数据抽屉的候选项**（`utils/variable-sources`）：抽屉里能选到的集合，就是运行时
  // 能取到值的集合，两边不会漂移。候选项 = 同层反向可达的上游产出（`upstreamOutputsOf`）+ 迭代体每轮
  // 注入的变量（`injectOutputsOf`：index / item / 容器的 variableParameters / 子块自己声明的当前项别名）。
  const brokenRefs = []
  list.forEach((node) => {
    const slots = refsOf(node)
    if (!slots.length || !node?.id) return
    const container = list.find((item) => item?.id === node.parentNode) ?? null
    const candidates = [
      ...upstreamOutputsOf(list, links, node.id),
      ...injectOutputsOf(list, container, node.id),
    ]
    const upstreamIds = new Set(candidates.map((item) => item.blockID))
    slots.forEach(({ field, ref, where }) => {
      if (candidates.some((item) => item.blockID === ref.blockID && item.name === ref.name)) return
      const problem = refProblemOf(list, node, ref, upstreamIds)
      brokenRefs.push(`节点「${nameOf(node)}」的${where}「${field.name || ref.name}」：${problem}`)
    })
  })
  if (brokenRefs.length) {
    errors.push(`变量引用有问题：${problemsTextOf(brokenRefs)}`)
  }

  // 5) 未绑定取值：声明出来的输入变量必须有取值来源
  //
  // 抽屉里点「＋」新增一行变量，只是**声明**：变量名写好了、取值槽还是空的（画布上用告警色提示）。
  // 这种变量后端拿不到值——`LlmNode` 遍历 inputs 时 `NodeFieldRef == null` 直接跳过，提示词占位符替换成
  // 空串；迭代容器没绑迭代源则 `readItems` 返回空列表、一轮都不跑（只产出空汇总）。两边都不会让运行
  // 失败，于是「变量一个都没配」也能一路跑通。告警色只画在节点卡片上，运行入口不拦，所以这里补上。
  //
  // 与第 4 项的分工：第 4 项管「引用了但取不到值」，这一项管「压根没引用」（含只写了常量，
  // 口径见 variable-sources 的 `unboundNamesOf`，卡片上的告警色用的是同一份实现）。
  const unbound = []
  list.forEach((node) => {
    if (!node?.id) return
    const collect = (declared, where) => {
      // 口径与卡片告警色同源（`unboundNamesOf`），不在这里再写一份判定
      unboundNamesOf(declared).forEach((name) => {
        unbound.push(`节点「${nameOf(node)}」的${where}「${name}」`)
      })
    }
    collect(node.data?.inputs, '输入变量')
    collect(node.data?.variableParameters, '迭代变量')
  })
  if (unbound.length) {
    errors.push(`变量还没有绑定取值：${problemsTextOf(unbound)}`)
  }

  return errors
}
