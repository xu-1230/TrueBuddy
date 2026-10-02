/**
 * 运行前校验的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 校验口径见 `src/views/workflow2/utils/run-validation.js`：复刻 flowgram 运行时 TaskValidate
 * 的「先校验再运行」，只做五类真正可达的检查——循环连接（逐层）、**顶层**开始 / 结束节点是否缺失
 * （迭代容器内的同类型节点不算入口 / 出口，口径同 flowgram 只统计顶层 `nodes`）、顶层断链（除
 * 「开始」外的顶层节点必须有连入的连线，否则后端会把它当并行起点、运行后才表现为「无产出变量」）、
 * 变量引用是否能真的取到值、以及变量是否绑定了取值来源（后两项都源于后端「取不到值就静默把提示词
 * 占位符替换成空串」）。
 *
 * 关键用例是**防误报**：默认流程的迭代体形态就是「容器 → 子块」+「子块 → 容器」（子图的虚拟
 * 入口 / 出口），它们不能被算成环，否则默认流程一开就是一运行就被拦。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import { expandIteration } from '../src/views/workflow2/utils/iteration.js'
import { metaOf } from '../src/views/workflow2/utils/node-meta.js'
import { runValidationErrors } from '../src/views/workflow2/utils/run-validation.js'

/* ===== 夹具（默认流程 default-flow.js 的真实数据） ===== */

const START_ID = '100001'
const UPSTREAM_ID = '100002'
const CONTAINER_ID = '132996'
const CHILD_ID = '173594'
const END_ID = '900001'

/** 默认流程的画布表示：迭代节点展开成「容器 + 子块 + 内部边」（与画布页 toCanvasFlow 同口径） */
const canvasOfDefaultFlow = () => {
  // 深拷贝定义：`DEFAULT_FLOW_DEFINITION` 是模块级常量，浅引用会让某条用例的就地修改污染后续用例
  const definition = structuredClone(DEFAULT_FLOW_DEFINITION)
  const nodes = []
  const edges = []
  for (const node of definition.nodes) {
    if (String(node.type) === '4') {
      const expanded = expandIteration(node)
      nodes.push(...expanded.nodes)
      edges.push(...expanded.edges)
      continue
    }
    nodes.push(node)
  }
  edges.push(...definition.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeID,
    target: edge.targetNodeID,
  })))
  return { nodes, edges }
}

/** 一条画布连线（校验只用 source / target） */
const link = (id, source, target) => ({ id, source, target })

const withoutNode = (nodes, id) => nodes.filter((node) => node?.id !== id)

/** 删掉一条连线（模拟用户在画布上删连线：节点还在，链路断开） */
const withoutEdge = (edges, id) => edges.filter((edge) => edge?.id !== id)

/**
 * 把节点的输入 / 迭代变量声明整体剥离（连带其中的取值引用）。
 *
 * 只给「结构类」用例用（环 / 开始结束 / 断链）：删掉一个节点后，原本引用它的变量会连带报出
 * 「变量引用有问题」或「变量还没有绑定取值」，那是另外两条检查的真实行为；这里把输入整体剥掉，
 * 保证每条用例只断言自己那一件事。引用 / 绑定检查本身的用例见「变量引用」段。
 */
const withoutInputs = (node) => (node?.data ? {
  ...node,
  data: { ...node.data, inputs: [], variableParameters: [] },
} : node)

/** 整批剥离输入声明（见 {@link withoutInputs}） */
const plainNodes = (nodes) => nodes.map(withoutInputs)

// 文案带「顶层」限定：开始 / 结束被拖进迭代容器时节点仍在画布上，只说「缺少」会让人以为被删了
const START_MISSING = `工作流缺少顶层的「${metaOf('1').name}」节点`
const END_MISSING = `工作流缺少顶层的「${metaOf('2').name}」节点`
const BOTH_MISSING = `工作流缺少顶层的「${metaOf('1').name}」和「${metaOf('2').name}」节点`
const TOP_CYCLE = '工作流存在循环连接：节点首尾相连形成回路'

test('默认流程：开始 / 结束齐全、无环 → 校验通过（不误报迭代体的虚拟出入口边）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 默认流程的迭代体里就有「容器 → 子块」与「子块 → 容器」两条边：它们是子图的入口 / 出口，
  // 只有「两端都在同一层」的边才参与环检测，所以这两条不会被算成环
  assert.deepEqual(runValidationErrors(nodes, edges), [])
})

test('迭代容器内部：只有虚拟出入口边时不算环（防误报）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const onlyContainer = plainNodes(nodes.filter((node) => (
    node.id === CONTAINER_ID || node.id === CHILD_ID
  )))
  const onlyInnerEdges = edges.filter((edge) => (
    (edge.source === CONTAINER_ID && edge.target === CHILD_ID)
    || (edge.source === CHILD_ID && edge.target === CONTAINER_ID)
  ))
  assert.equal(onlyInnerEdges.length, 2, '（前置）默认流程的迭代体确实有入口 / 出口两条边')

  // 只剩「容器 + 子块」两层节点：环检测必须安静，只报缺少开始 / 结束
  assert.deepEqual(
    runValidationErrors(onlyContainer, onlyInnerEdges),
    [BOTH_MISSING],
    '容器 ↔ 自己的子块是子图的入口 / 出口，不能当成环',
  )
})

test('缺少开始节点 / 缺少结束节点 / 两者都缺', () => {
  // 用去引用的副本：删掉节点后，原本引用它的变量会连带报「变量引用有问题」，那是另一条检查的事
  const { nodes, edges } = canvasOfDefaultFlow()
  const plain = plainNodes(nodes)

  assert.deepEqual(runValidationErrors(withoutNode(plain, START_ID), edges), [START_MISSING])
  assert.deepEqual(runValidationErrors(withoutNode(plain, END_ID), edges), [END_MISSING])
  assert.deepEqual(
    runValidationErrors(withoutNode(withoutNode(plain, START_ID), END_ID), edges),
    [BOTH_MISSING],
    '两者都缺只报一条合并错误',
  )
})

/**
 * 把某个节点拖进迭代容器（`parentNode` 指向容器 + 相对坐标，与 `planMigration` 出参同形态）。
 *
 * 拖拽迁移不限节点类型（`planMigration` 只看落点），所以「开始 / 结束」也可能被放进迭代体：这是**可达**场景。
 */
const movedIntoContainer = (nodes, id, y) => nodes.map((node) => (
  node.id === id ? { ...node, parentNode: CONTAINER_ID, position: { x: 24, y } } : node
))

test('开始节点被拖进迭代容器 → 报「缺少顶层的开始节点」（节点还在画布上，且不误报为环）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 口径与 flowgram 的 `startEndNode` 一致——只统计顶层节点，容器内的同类型节点不算工作流入口 / 出口；
  // 文案必须点明「顶层」，否则用户看到画布上明明有「开始」却被说「缺少」
  assert.deepEqual(
    runValidationErrors(movedIntoContainer(plainNodes(nodes), START_ID, 360), edges),
    [START_MISSING],
    '开始节点进了容器：仍要报缺失，但文案是「顶层的开始节点」；同时不能把容器边误报成环',
  )
})

test('结束节点被拖进迭代容器 → 同样报「缺少顶层的结束节点」；两个都进容器时合并成一条', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const plain = plainNodes(nodes)
  assert.deepEqual(
    runValidationErrors(movedIntoContainer(plain, END_ID, 480), edges),
    [END_MISSING],
    '结束节点同理',
  )

  // 两个都在同一个容器里：合并成一条错误，仍然不报环
  assert.deepEqual(
    runValidationErrors(
      movedIntoContainer(movedIntoContainer(plain, START_ID, 360), END_ID, 480),
      edges,
    ),
    [BOTH_MISSING],
    '两个都进容器时合并成一条（与「两个都被删」同一文案，描述的是「顶层没有」这件事）',
  )
})

/* ===== 断链：顶层除「开始」以外的节点必须有连入的连线 ===== */

/** 断链错误文案（节点名清单的折叠规则见 run-validation.js 的 namesOf） */
const dangle = (names) => `工作流存在断链：${names}没有连入的连线，运行时会作为独立起点执行、拿不到上游数据`

/** 变量引用错误文案（逐条问题的格式见 run-validation.js 的 refProblemOf） */
const refSlot = (node, where, field, problem) => `节点「${node}」的${where}「${field}」：${problem}`
const refIssue = (...problems) => `变量引用有问题：${problems.join('；')}`

test('断链：删掉「迭代 → 结束」的连线 → 点名「结束」（后端会把它当并行起点）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 这是最常见的漏连：中间的线都连了，最后一截「→ 结束」没连。后端 `createGraph` 会给「无入边」的节点
  // 自动接 `StateGraph.START`，于是「结束」与主流程并行开跑、拿不到迭代结果——运行不报错，只在结果面板
  // 上表现为「失败 / 本次无产出变量」，所以必须拦在运行之前
  assert.deepEqual(
    runValidationErrors(nodes, withoutEdge(edges, 'e-132996-900001')),
    [
      dangle('「结束」'),
      // 这条线一断，「结束」声明的取值（引用迭代容器的 result）也同时失去来源，两条错误一并给出
      refIssue(refSlot('结束', '输入变量', 'input', '来源「逐份批改」没有连到本节点（不在上游）')),
    ],
  )
})

test('断链：新加的顶层节点还没连线 → 点名该节点，已连好的节点不误报', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const fresh = { id: '100003', type: '3', position: { x: 0, y: 0 }, data: { label: '新节点' } }
  assert.deepEqual(runValidationErrors([...nodes, fresh], edges), [dangle('「新节点」')])
})

test('断链：同时多个节点断链时点名前 3 个并折叠成「等 N 个节点」', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const extra = ['A', 'B', 'C', 'D'].map((label, index) => ({
    id: `10000${index + 3}`,
    type: '3',
    position: { x: 0, y: 0 },
    data: { label },
  }))
  assert.deepEqual(runValidationErrors([...nodes, ...extra], edges), [dangle('「A」「B」「C」等 4 个节点')])
})

test('断链：「开始」缺失时只报缺失，不逐节点刷断链（此时每个节点都没有上游）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  assert.deepEqual(
    runValidationErrors(withoutNode(plainNodes(nodes), START_ID), edges),
    [START_MISSING],
    '入口缺失已经单独报出，再逐节点报断链只会淹没真正的原因',
  )
})

test('断链：迭代体子块没有入边不报（后端按入度 / 出度兜底补齐，属正常中间态）', () => {
  const { nodes } = canvasOfDefaultFlow()
  // 只留「开始 / 结束 / 容器 / 一个子块」，且**不画**容器的虚拟出入口边：容器与结束都连在顶层链路上，
  // 子块则完全没有内部边——后端对迭代体子块有「按入度 / 出度兜底补齐」的规则（docs/12 §4.5），
  // 前端若把「子块没有入边」也算断链，就把「刚拖进容器还没连线」这一正常中间态误判了
  const keep = [START_ID, END_ID, CONTAINER_ID, CHILD_ID]
  assert.deepEqual(
    runValidationErrors(
      plainNodes(nodes.filter((node) => keep.includes(node.id))),
      [link('e-start', START_ID, CONTAINER_ID), link('e-end', CONTAINER_ID, END_ID)],
    ),
    [],
  )
})

/* ===== 变量引用：引用的来源必须真的能取到值（后端取不到值 = 静默替换成空串） ===== */

/** 一个输入声明（`name` 与引用的变量名同名，便于阅读） */
const refField = (name, blockID) => ({ name, input: { blockID, name } })

test('变量引用：引用的来源节点不在画布上 → 点名「来源节点不在画布上」（删掉容器后「结束」还引用它）', () => {
  // 链路上「开始 → 结束」是连通的（不报断链），问题只出在变量引用指向了已删除的节点
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    { id: END_ID, type: '2', data: { label: '结束', inputs: [refField('input', '132996')] } },
  ]
  assert.deepEqual(
    runValidationErrors(nodes, [link('e', START_ID, END_ID)]),
    [refIssue(refSlot('结束', '输入变量', 'input', '来源节点（132996）不在画布上'))],
  )
})

test('变量引用：引用了迭代体子块的产出 → 报跨层取不到值', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    { id: CONTAINER_ID, type: '4', data: { label: '逐份批改' } },
    {
      id: CHILD_ID,
      type: '3',
      parentNode: CONTAINER_ID,
      data: { label: '单份批改', outputs: [{ name: 'result' }] },
    },
    { id: END_ID, type: '2', data: { label: '结束', inputs: [refField('input', CHILD_ID)] } },
  ]
  // 顶层链路连通：开始 → 容器 → 结束；子块的产出只活在迭代体子图里，父图取不到
  assert.deepEqual(
    runValidationErrors(nodes, [
      link('e-1', START_ID, CONTAINER_ID),
      link('e-2', CONTAINER_ID, END_ID),
    ]),
    [refIssue(refSlot('结束', '输入变量', 'input', '来源「单份批改」在另一层，跨迭代体边界取不到值'))],
  )
})

test('变量引用：来源节点存在但没连到本节点 → 报「不在上游」', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    { id: UPSTREAM_ID, type: '3', data: { label: '旁支', outputs: [{ name: 'x' }] } },
    { id: END_ID, type: '2', data: { label: '结束', inputs: [refField('input', UPSTREAM_ID)] } },
  ]
  // 两条边都在（不报断链）：开始 → 结束、开始 → 旁支；但「旁支」到不了「结束」
  assert.deepEqual(
    runValidationErrors(nodes, [
      link('e-1', START_ID, END_ID),
      link('e-2', START_ID, UPSTREAM_ID),
    ]),
    [refIssue(refSlot('结束', '输入变量', 'input', '来源「旁支」没有连到本节点（不在上游）'))],
  )
})

test('变量引用：来源节点没有声明该变量名 → 报「没有声明产出变量」', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始', outputs: [{ name: 'description' }] } },
    { id: END_ID, type: '2', data: { label: '结束', inputs: [refField('input', START_ID)] } },
  ]
  assert.deepEqual(
    runValidationErrors(nodes, [link('e', START_ID, END_ID)]),
    [refIssue(refSlot('结束', '输入变量', 'input', '来源「开始」没有声明产出变量「input」'))],
  )
})

test('变量引用：默认流程（含子块引用容器的每轮注入变量）不误报', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 子块的 item / jj 引用的是容器 id：那是每轮注入的变量（index / item / variableParameters / 别名），
  // 不是「上游节点的产出」，不能因为容器不是产出方就报错
  assert.deepEqual(runValidationErrors(nodes, edges), [])
})

test('变量引用：问题超过 3 处时折叠成「等 N 处」', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    {
      id: UPSTREAM_ID,
      type: '3',
      data: { label: '大模型', inputs: ['a', 'b', 'c', 'd'].map((name) => refField(name, '404')) },
    },
    { id: END_ID, type: '2', data: { label: '结束' } },
  ]
  const problem = (field) => refSlot('大模型', '输入变量', field, '来源节点（404）不在画布上')
  assert.deepEqual(
    runValidationErrors(nodes, [
      link('e-1', START_ID, UPSTREAM_ID),
      link('e-2', UPSTREAM_ID, END_ID),
    ]),
    [`变量引用有问题：${problem('a')}；${problem('b')}；${problem('c')}；等 4 处`],
  )
})

/* ===== 变量没有绑定取值：抽屉里只声明了名字、没选来源（含只写常量） ===== */

test('变量未绑定：声明了输入变量但没选取值来源 → 拦在运行之前', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始', outputs: [{ name: 'description' }] } },
    {
      id: UPSTREAM_ID,
      type: '3',
      data: { label: '大模型', inputs: [{ name: 'description' }, { name: 'file_tree' }] },
    },
    { id: END_ID, type: '2', data: { label: '结束' } },
  ]
  // 抽屉里「＋」新增的变量行只有名字：后端 `LlmNode` 遇到 `input` 为空直接跳过该变量，提示词占位符被
  // 替换成空串，运行照样「成功」——所以这种「一个变量都没配」的节点必须拦在运行之前
  assert.deepEqual(
    runValidationErrors(nodes, [link('e-1', START_ID, UPSTREAM_ID), link('e-2', UPSTREAM_ID, END_ID)]),
    ['变量还没有绑定取值：节点「大模型」的输入变量「description」；节点「大模型」的输入变量「file_tree」'],
  )
})

test('变量未绑定：只写了常量（content）也算未绑定 —— 后端只认 {blockID, name} 引用', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    {
      id: UPSTREAM_ID,
      type: '3',
      data: { label: '大模型', inputs: [{ name: 'description', input: { content: '固定文案' } }] },
    },
    { id: END_ID, type: '2', data: { label: '结束' } },
  ]
  // `NodeStateReader#read` 只看 `blockID` / `name`（`NodeFieldRef` 里没有 `content` 字段）：常量在运行时
  // 同样取到 `null`、提示词里那一段还是空串。前端告警色把常量算作已绑定，比后端宽松，校验按后端口径
  assert.deepEqual(
    runValidationErrors(nodes, [link('e-1', START_ID, UPSTREAM_ID), link('e-2', UPSTREAM_ID, END_ID)]),
    ['变量还没有绑定取值：节点「大模型」的输入变量「description」'],
  )
})

test('变量未绑定：迭代容器没绑迭代源 / 迭代变量 → 一并点名', () => {
  const nodes = [
    { id: START_ID, type: '1', data: { label: '开始' } },
    {
      id: CONTAINER_ID,
      type: '4',
      data: {
        label: '逐份批改',
        inputs: [{ name: 'list', type: 'list' }],
        variableParameters: [{ name: 'jj' }],
      },
    },
    { id: END_ID, type: '2', data: { label: '结束' } },
  ]
  // 迭代源没绑定：后端 `readItems` 返回空列表、一轮都不跑，只产出空汇总（「流程成功但没有结果」）
  assert.deepEqual(
    runValidationErrors(nodes, [
      link('e-1', START_ID, CONTAINER_ID),
      link('e-2', CONTAINER_ID, END_ID),
    ]),
    ['变量还没有绑定取值：节点「逐份批改」的输入变量「list」；节点「逐份批改」的迭代变量「jj」'],
  )
})

test('顶层回边 → 报循环连接', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 默认链路 100001 → 100002 → 132996 → 900001，补一条 100002 → 100001 即形成回路
  const cyclic = [...edges, link('e-back', UPSTREAM_ID, START_ID)]
  assert.deepEqual(runValidationErrors(nodes, cyclic), [TOP_CYCLE])
})

test('迭代容器内部的子块互连成环 → 报容器内部的循环连接', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const second = {
    id: '173595',
    type: '3',
    parentNode: CONTAINER_ID,
    position: { x: 24, y: 240 },
    data: { label: '子块B' },
  }
  const innerCycle = [...edges, link('e-a-b', CHILD_ID, '173595'), link('e-b-a', '173595', CHILD_ID)]

  assert.deepEqual(
    runValidationErrors([...nodes, second], innerCycle),
    ['迭代容器「逐份批改」内部存在循环连接：子块首尾相连形成回路'],
    '容器内部成环要指出是哪个容器，且不能误报成顶层环',
  )
})

test('多条错误一次性收集（复刻 TaskValidate 的 errors 列表语义）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 删掉开始节点，同时在剩下的顶层节点里连出回路 100002 → 132996 → 900001 → 100002
  const errors = runValidationErrors(
    withoutNode(plainNodes(nodes), START_ID),
    [...edges, link('e-loop', END_ID, UPSTREAM_ID)],
  )
  assert.deepEqual(errors, [TOP_CYCLE, START_MISSING], '两类检查各自独立，错误按序全部返回')
})

test('空画布不抛错，只报缺少开始 / 结束', () => {
  assert.deepEqual(runValidationErrors([], []), [BOTH_MISSING])
  assert.deepEqual(runValidationErrors(), [BOTH_MISSING], '参数缺省同样安全')
})

test('悬空子块（容器已不存在）按顶层节点处理，与提交给后端的口径一致', () => {  const { nodes, edges } = canvasOfDefaultFlow()
  const orphan = {
    id: '173596',
    type: '3',
    parentNode: 'not-exist',
    position: { x: 0, y: 0 },
    data: { label: '悬空子块' },
  }
  const linked = [...edges, link('e-orphan-out', '173596', START_ID), link('e-orphan-in', END_ID, '173596')]
  // 悬空子块算顶层，于是 100001 → … → 900001 → 173596 → 100001 是一条顶层回路；
  // 若把它当成子块（按 parentNode 存在与否判断以外的方式），这条回路检测不到
  assert.deepEqual(runValidationErrors([...nodes, orphan], linked), [TOP_CYCLE])
})

/* ===== 画布页接线（纯函数用例覆盖不到「运行前先校验」这条链路，用源码契约守住） ===== */

test('画布页接线：handleRun 先校验，不通过则展示全部原因并拦住本次运行', () => {
  const view = readFileSync(
    new URL('../src/views/workflow2/WorkflowView.vue', import.meta.url),
    'utf8',
  )
  const runStart = view.indexOf('const handleRun = () => {')
  const runEnd = view.indexOf('function confirmNode', runStart)
  assert.ok(runStart > 0 && runEnd > runStart, '能定位 handleRun 的实现')
  const handleRunBody = view.slice(runStart, runEnd)

  assert.match(
    handleRunBody,
    /const errors = runValidationErrors\(getNodes\.value, getEdges\.value\)/,
    '校验读 store 的实时节点与连线（与提交给后端的同一份数据）',
  )
  assert.match(
    handleRunBody,
    /if \(errors\.length\) \{\s*showRunValidationErrors\(errors\)\s*return\s*\}/,
    '校验不通过时列出全部错误并提前返回',
  )
  assert.ok(
    handleRunBody.indexOf('runValidationErrors(') < handleRunBody.indexOf('execute(WORKFLOW_API.run'),
    '校验必须发生在提交执行之前',
  )
  assert.ok(
    handleRunBody.includes("console.debug('[workflow] 运行前校验'"),
    '开发期要能确认校验跑没跑（通过时页面没有其它反馈）',
  )
  assert.match(view, /import \{ runValidationErrors \} from '\.\/utils\/run-validation'/)
  assert.match(view, /ElMessageBox\s*\.\s*alert\(/, '错误列表用弹窗展示')
  assert.match(
    view,
    /\.catch\(\(\) => \{\}\)/,
    'ElMessageBox 的 Promise 在用户点右上角关闭时以 cancel reject，必须吞掉（否则控制台报未捕获的 promise）',
  )
  assert.equal(
    view.includes('dangerouslyUseHTMLString'),
    false,
    '错误信息含用户填写的节点名称，不能当 HTML 渲染',
  )
})

/* ===== 对抗性用例：脏数据形态、自环、跨容器的环、容器套容器 ===== */

test('对抗：节点 type 写成数字（代码库明确容忍的脏数据）时，不能漏判开始 / 结束与迭代容器', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // utils/node-meta.js 的 metaOf 注释写明「旧的脏数据里若写成数字，命中原类型也比显示未知节点有用」，
  // 校验必须用同一口径：严格 === '1' 会把数字类型当成「没有开始节点」，直接把用户拦在门外
  const numericTypes = nodes.map((node) => ({ ...node, type: Number(node.type) }))
  assert.deepEqual(runValidationErrors(numericTypes, edges), [])

  // 容器分层也要认得数字类型的容器：容器内部成环必须报在容器那一层
  const second = {
    id: '173597',
    type: 3,
    parentNode: CONTAINER_ID,
    position: { x: 0, y: 0 },
    data: { label: '子块B' },
  }
  assert.deepEqual(
    runValidationErrors(
      [...numericTypes, second],
      [...edges, link('e-a-b', CHILD_ID, '173597'), link('e-b-a', '173597', CHILD_ID)],
    ),
    ['迭代容器「逐份批改」内部存在循环连接：子块首尾相连形成回路'],
  )
})

test('对抗：自环（节点连到自己）必须报环', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 画布层已拦「连到自己」，但校验器不能依赖上游一定拦住
  assert.deepEqual(
    runValidationErrors(nodes, [...edges, link('e-self', UPSTREAM_ID, UPSTREAM_ID)]),
    [TOP_CYCLE],
  )
})

test('对抗：环经过迭代容器（顶层 大模型 ↔ 容器）也要报出来', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  assert.deepEqual(
    runValidationErrors(nodes, [...edges, link('e-container-back', CONTAINER_ID, UPSTREAM_ID)]),
    [TOP_CYCLE],
    '容器是顶层的一个节点，经过它的回边同样构成顶层环',
  )
})

test('对抗：两个迭代容器互相连成环', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const second = { id: '132997', type: '4', position: { x: 0, y: 600 }, data: { label: '第二个容器' } }
  const errors = runValidationErrors(
    [...nodes, second],
    [...edges, link('e-c1-c2', CONTAINER_ID, '132997'), link('e-c2-c1', '132997', CONTAINER_ID)],
  )
  assert.deepEqual(errors, [TOP_CYCLE])
})

test('对抗：校验不改动画布数据（纯函数，拿到的就是 store 的实时数组）', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  const nodesSnapshot = structuredClone(nodes)
  const edgesSnapshot = structuredClone(edges)

  const first = runValidationErrors(nodes, edges)
  assert.deepEqual(runValidationErrors(nodes, edges), first, '同一输入恒得同一结果（可重复调用）')
  assert.deepEqual(nodes, nodesSnapshot, '节点数组与节点对象都不该被改动')
  assert.deepEqual(edges, edgesSnapshot, '连线数组与连线对象都不该被改动')
})

test('对抗：脏数组（null / 缺字段）不抛错', () => {
  assert.deepEqual(
    runValidationErrors([null, undefined, {}], [null, {}, { source: 'x' }]),
    [BOTH_MISSING],
    '读不到 id / type 的条目一律跳过，判定结果按「空画布」处理',
  )
})

test('对抗：容器套容器时环只报在真正成环的那一层，嵌套的虚拟出入口边不误报', () => {
  const start = { id: '100001', type: '1', position: { x: 0, y: 0 }, data: { label: '开始' } }
  const end = { id: '900001', type: '2', position: { x: 0, y: 0 }, data: { label: '结束' } }
  const outer = { id: 'C1', type: '4', position: { x: 0, y: 0 }, data: { label: '外层容器' } }
  const inner = { id: 'C2', type: '4', parentNode: 'C1', position: { x: 0, y: 0 }, data: { label: '内层容器' } }
  const blockA = { id: 'A', type: '3', parentNode: 'C1', position: { x: 0, y: 0 }, data: { label: '子块A' } }
  const blockX = { id: 'X', type: '3', parentNode: 'C2', position: { x: 0, y: 0 }, data: { label: '子块X' } }
  const nested = [start, end, outer, inner, blockA, blockX]

  // 只有两级各自的「容器 ↔ 子块」虚拟出入口边：一层都不该报环
  assert.deepEqual(runValidationErrors(nested, [
    link('e-in-1', 'C1', 'A'), link('e-out-1', 'A', 'C1'),
    link('e-in-2', 'C2', 'X'), link('e-out-2', 'X', 'C2'),
    link('e-start', '100001', 'C1'), link('e-end', 'C1', '900001'),
  ]), [])

  // 外层两个子块（子块A 与嵌套容器 C2）互连成环 → 只报外层容器这一处
  assert.deepEqual(runValidationErrors(nested, [
    // 顶层链路保持连通（否则会另外触发「断链」，见下面的断链用例），这样断言只聚焦环这一处
    link('e-start', '100001', 'C1'), link('e-end', 'C1', '900001'),
    link('e-a-c2', 'A', 'C2'), link('e-c2-a', 'C2', 'A'),
    link('e-in-2', 'C2', 'X'), link('e-out-2', 'X', 'C2'),
  ]), ['迭代容器「外层容器」内部存在循环连接：子块首尾相连形成回路'])
})
