/**
 * 节点工厂与节点元数据的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 覆盖「节点库面板」新增节点时依赖的全部口径：
 * 1. id 为全图唯一数字串，取最大值 +1（含迭代体子块，非数字 id 忽略但参与唯一性）；
 * 2. 默认名与画布上已有名称不重复；
 * 3. 落点为「既有节点群右侧、纵向居中」，只统计顶层节点、空画布有兜底起点；
 * 4. 五类节点的默认结构与 docs/12-工作流规范.md §8.1 同构（`blocks: []` / `edges: null`），
 *    迭代节点必须带 `style` 尺寸（口径与 `expandIteration` 一致）；
 * 5. 元数据单一真源：目录顺序、未识别类型兜底。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  CARD_LAYOUT,
  cardTopAreaHeightOf,
  containerSizeOf,
  estimatedTopAreaOf,
  expandIteration,
} from '../src/views/workflow2/utils/iteration.js'
import { NODE_CATALOG, NODE_META, UNKNOWN_META, metaOf } from '../src/views/workflow2/utils/node-meta.js'
import {
  createWorkflowNode,
  nextNodeId,
  nextNodePosition,
  nominalSizeOf,
  uniqueLabelOf,
} from '../src/views/workflow2/utils/node-factory.js'

/** 默认流程的画布表示（迭代节点展开成「容器 + 子块」，与画布页 toCanvasFlow 口径一致） */
const canvasOfDefaultFlow = () => {
  const nodes = []
  for (const node of DEFAULT_FLOW_DEFINITION.nodes) {
    if (String(node.type) === '4') {
      nodes.push(...expandIteration(node).nodes)
      continue
    }
    nodes.push(node)
  }
  return nodes
}

/* ===== nextNodeId ===== */

test('nextNodeId：空画布从基准值起', () => {
  assert.equal(nextNodeId([]), '100001')
  assert.equal(nextNodeId(undefined), '100001')
})

test('nextNodeId：取全图数字 id 最大值 +1（含迭代体子块）', () => {
  const nodes = canvasOfDefaultFlow()
  // 默认流程含子块 173594，最大值仍是结束节点的 900001
  assert.ok(nodes.some((node) => node.id === '173594'))
  assert.equal(nextNodeId(nodes), '900002')
})

test('nextNodeId：语义化 id 不参与自增，但仍参与唯一性', () => {
  assert.equal(nextNodeId([{ id: 'start' }, { id: 'llm-plan' }]), '100001')
  assert.equal(nextNodeId([{ id: 'start' }, { id: '100005' }]), '100006')
})

test('nextNodeId：触及安全整数上限时退回线性找空位', () => {
  const overflowed = String(Number.MAX_SAFE_INTEGER)
  assert.equal(nextNodeId([{ id: overflowed }]), '100001')
  assert.equal(nextNodeId([{ id: overflowed }, { id: '100001' }, { id: '100002' }]), '100003')
})

test('nextNodeId：连续生成不重复', () => {
  const nodes = []
  for (let index = 0; index < 5; index += 1) {
    nodes.push({ id: nextNodeId(nodes) })
  }
  assert.equal(new Set(nodes.map((node) => node.id)).size, 5)
})

/* ===== uniqueLabelOf ===== */

test('uniqueLabelOf：无重名时原样返回', () => {
  assert.equal(uniqueLabelOf([], '大模型'), '大模型')
  assert.equal(uniqueLabelOf([{ data: { label: '开始' } }], '大模型'), '大模型')
})

test('uniqueLabelOf：重名时追加序号', () => {
  assert.equal(uniqueLabelOf([{ data: { label: '大模型' } }], '大模型'), '大模型 2')
  assert.equal(
    uniqueLabelOf([{ data: { label: '大模型' } }, { data: { label: '大模型 2' } }], '大模型'),
    '大模型 3',
  )
})

test('uniqueLabelOf：复用中间被腾出的空位', () => {
  const nodes = [{ data: { label: '大模型' } }, { data: { label: '大模型 3' } }]
  assert.equal(uniqueLabelOf(nodes, '大模型'), '大模型 2')
})

test('uniqueLabelOf：空基础名原样返回（不生成「 2」这类名字）', () => {
  assert.equal(uniqueLabelOf([{ data: { label: '大模型' } }], ''), '')
  assert.equal(uniqueLabelOf([], undefined), '')
})

/* ===== nextNodePosition ===== */

test('nextNodePosition：空画布返回兜底起点', () => {
  assert.deepEqual(nextNodePosition([]), { x: 40, y: 240 })
})

test('nextNodePosition：落在既有节点右侧、纵向居中', () => {
  // 标称 272×140 的卡片：x = 0 + 272 + 240，y = 中线 70 − 半个标称高度 70
  const position = nextNodePosition([{ id: '1', type: '3', position: { x: 0, y: 0 } }])
  assert.deepEqual(position, { x: 512, y: 0 })
})

test('nextNodePosition：多节点取包围盒右边缘，纵向取节点群中线', () => {
  const nodes = [
    { id: '1', type: '3', position: { x: 0, y: 0 } },
    { id: '2', type: '3', position: { x: 600, y: 400 } },
  ]
  // 包围盒：x ∈ [0, 872]，y ∈ [0, 540] → 中线 270 − 70
  assert.deepEqual(nextNodePosition(nodes), { x: 1112, y: 200 })
})

test('nextNodePosition：迭代容器按 style 尺寸参与包围盒', () => {
  const container = {
    id: '132996',
    type: '4',
    position: { x: 0, y: 0 },
    data: { label: '迭代' },
    style: { width: '448px', height: '300px' },
  }
  assert.deepEqual(nextNodePosition([container]), { x: 688, y: 80 })
})

test('nextNodePosition：迭代体子块不参与包围盒（坐标是相对容器的）', () => {
  const container = {
    id: '132996',
    type: '4',
    position: { x: 0, y: 0 },
    style: { width: '448px', height: '300px' },
  }
  const child = { id: '173594', type: '3', parentNode: '132996', position: { x: 9999, y: 9999 } }
  assert.deepEqual(nextNodePosition([container, child]), nextNodePosition([container]))
  assert.deepEqual(nextNodePosition([child]), { x: 40, y: 240 })
})

/* ===== createWorkflowNode ===== */

test('createWorkflowNode：id 与 type 一律为字符串，字段形态与后端定义同构', () => {
  const node = createWorkflowNode('3', { id: '900002', label: '大模型', position: { x: 10, y: 20 } })
  assert.equal(node.id, '900002')
  assert.equal(node.type, '3')
  assert.deepEqual(node.position, { x: 10, y: 20 })
  assert.deepEqual(node.blocks, [])
  assert.equal(node.edges, null)
  assert.deepEqual(node.data, { label: '大模型', system: '', user: '', inputs: [], outputs: [] })
  // 普通节点不写 style（尺寸由卡片内容决定）
  assert.equal(node.style, undefined)
})

test('createWorkflowNode：开始 / 结束 / 人工确认的默认变量组', () => {
  assert.deepEqual(createWorkflowNode('1', { id: '1' }).data.outputs, [])
  assert.deepEqual(createWorkflowNode('2', { id: '2' }).data.inputs, [])
  const human = createWorkflowNode('5', { id: '5' })
  assert.deepEqual(human.data.inputs, [])
  assert.deepEqual(human.data.outputs, [])
  assert.equal(human.data.variableParameters, undefined)
})

test('createWorkflowNode：迭代节点带 variableParameters 与 style 尺寸', () => {
  const node = createWorkflowNode('4', { id: '132997' })
  assert.deepEqual(node.data.variableParameters, [])
  assert.equal(node.edges, null)
  const expected = containerSizeOf(0, estimatedTopAreaOf(node.data))
  assert.deepEqual(node.style, { width: `${expected.width}px`, height: `${expected.height}px` })
})

test('createWorkflowNode：label 缺省取元数据名称', () => {
  assert.equal(createWorkflowNode('3', { id: '1' }).data.label, NODE_META['3'].name)
  assert.equal(createWorkflowNode('4', { id: '2' }).data.label, '迭代')
})

test('createWorkflowNode：position 缺省为零点且非法值不产生 NaN', () => {
  assert.deepEqual(createWorkflowNode('3', { id: '1' }).position, { x: 0, y: 0 })
  assert.deepEqual(
    createWorkflowNode('3', { id: '1', position: { x: undefined, y: 'x' } }).position,
    { x: 0, y: 0 },
  )
})

test('createWorkflowNode：未知类型与缺少 id 直接抛错', () => {
  assert.throws(() => createWorkflowNode('9', { id: '1' }), /不支持的节点类型/)
  assert.throws(() => createWorkflowNode('3', { id: '' }), /必须提供 id/)
})

test('createWorkflowNode：顶层字段只有后端定义字段 + 可被白名单剔除的运行时字段', () => {
  // 提交执行时 buildRunDefinition 只投影 id / type / position / data / blocks / edges，
  // style 是 Vue Flow 的运行时字段（迭代容器尺寸），提交前会被剔除——工厂不得引入别的字段
  const definitionFields = new Set(['id', 'type', 'position', 'data', 'blocks', 'edges'])
  const runtimeFields = new Set(['style'])
  for (const type of ['1', '2', '3', '4', '5']) {
    const node = createWorkflowNode(type, { id: '1' })
    Object.keys(node).forEach((key) => {
      assert.ok(
        definitionFields.has(key) || runtimeFields.has(key),
        `${type} 节点出现了预期外的字段：${key}`,
      )
    })
    // 业务数据里不得出现运行时字段（`__` 前缀会被 pickNodeData 剔除，也不该由工厂产生）
    Object.keys(node.data).forEach((key) => {
      assert.ok(!key.startsWith('__'), `${type} 节点的 data 出现运行时字段：${key}`)
    })
  }
})

/* ===== nominalSizeOf ===== */

test('nominalSizeOf：普通节点取「空卡片」尺寸（宽 272、高 = 头部 + 上下边框）', () => {
  const expected = { width: 272, height: cardTopAreaHeightOf(0) + CARD_LAYOUT.borderWidth * 2 }
  assert.deepEqual(nominalSizeOf('3'), expected)
  // 与既有卡片的估算高度是两回事：拿 140 去折算会让新建节点在画布上偏高
  assert.notEqual(nominalSizeOf('3').height, 140)
})

test('nominalSizeOf：迭代容器取 containerSizeOf 的真实口径，与新建节点写进 style 的尺寸同源', () => {
  const iterate = nominalSizeOf('4')
  assert.deepEqual(iterate, containerSizeOf(0, estimatedTopAreaOf({})))
  assert.deepEqual(iterate, {
    width: parseFloat(createWorkflowNode('4', { id: '1' }).style.width),
    height: parseFloat(createWorkflowNode('4', { id: '1' }).style.height),
  })
})

test('nominalSizeOf：五种类型的尺寸都是有限正数', () => {
  for (const type of ['1', '2', '3', '4', '5']) {
    const size = nominalSizeOf(type)
    assert.ok(Number.isFinite(size.width) && size.width > 0, `${type} 宽度异常`)
    assert.ok(Number.isFinite(size.height) && size.height > 0, `${type} 高度异常`)
  }
})

/* ===== 元数据单一真源 ===== */

test('NODE_CATALOG：按 1→5 顺序覆盖全部可新建类型', () => {
  assert.deepEqual(
    NODE_CATALOG.map((item) => item.type),
    ['1', '2', '3', '4', '5'],
  )
  NODE_CATALOG.forEach((item) => {
    assert.equal(item.name, NODE_META[item.type].name)
    assert.equal(item.desc, NODE_META[item.type].desc)
    assert.ok(item.icon)
  })
})

test('metaOf：未识别类型回落兜底元数据，数字编码按字符串键命中原类型', () => {
  assert.equal(metaOf('3'), NODE_META['3'])
  // 键语义下的容忍：旧数据把类型写成数字时，命中原类型比显示「未知节点」有用
  assert.equal(metaOf(3), NODE_META['3'])
  assert.equal(metaOf('9'), UNKNOWN_META)
  assert.equal(metaOf(undefined), UNKNOWN_META)
  assert.equal(metaOf(null), UNKNOWN_META)
})

test('NODE_META：flags 决定抽屉配置分组，搬移到公共模块后保持原状', () => {
  assert.deepEqual(NODE_META['3'].flags, {
    prompt: true,
    inputs: true,
    outputs: true,
    variableParams: false,
  })
  assert.equal(NODE_META['4'].flags.variableParams, true)
  assert.equal(NODE_META['1'].flags.inputs, false)
})
