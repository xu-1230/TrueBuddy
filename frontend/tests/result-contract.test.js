/**
 * 作业批改页面「结果契约」用例（Node 内置 `node:test`，零新依赖）。
 *
 * 钉住的是"页面凭什么知道要展示什么"：
 * 1. 输出节点（`type === '6'`）的 `inputs` 就是展示字段，`description` 就是字段标签；
 * 2. 输出节点在迭代体内时，数据要从**迭代节点的聚合产出**里取（子图状态独立，
 *    `state[输出节点id]` 在父状态里根本不存在）—— 取错节点的表现是"流程成功、页面空白"；
 * 3. 聚合成的是**平行列表**，页面按**下标**对齐，短的补 null（不能静默丢学生）。
 *
 * 后两条用例直接对着 `default-flow.js` 的真实定义断言，把"定义怎么声明"与"页面怎么读"绑在一起。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  OUTPUT_NODE_TYPE,
  findOutputNode,
  parseDefinition,
  recordsOf,
  resultContractOf,
} from '../src/views/homework/utils/result-contract.js'

/* ===== 解析与定位 ===== */

test('parseDefinition：接受 JSON 字符串与对象，结构不对时返回 null', () => {
  const json = JSON.stringify(DEFAULT_FLOW_DEFINITION)
  assert.deepEqual(parseDefinition(json).nodes.length, DEFAULT_FLOW_DEFINITION.nodes.length)
  assert.equal(parseDefinition(DEFAULT_FLOW_DEFINITION)?.nodes?.length, DEFAULT_FLOW_DEFINITION.nodes.length)
  assert.equal(parseDefinition('{ 坏 JSON'), null)
  assert.equal(parseDefinition('{"nodes":"不是数组"}'), null)
  assert.equal(parseDefinition(null), null)
})

test('findOutputNode：顶层优先于迭代体内', () => {
  const body = { id: 'O1', type: OUTPUT_NODE_TYPE, data: { inputs: [] } }
  const top = { id: 'O2', type: OUTPUT_NODE_TYPE, data: { inputs: [] } }
  const iterate = { id: 'I', type: '4', blocks: [body], data: {} }

  assert.equal(findOutputNode({ nodes: [iterate], edges: [] }).node.id, 'O1')
  assert.equal(
    findOutputNode({ nodes: [iterate, top], edges: [] }).node.id,
    'O2',
    '顶层那个才是"整批结构"，优先它',
  )
  assert.deepEqual(findOutputNode({ nodes: [{ id: 'S', type: '1' }], edges: [] }), null)
})

test('resultContractOf：没有输出节点时返回 null（页面据此拦下执行）', () => {
  assert.equal(resultContractOf({ nodes: [{ id: 'S', type: '1' }], edges: [] }), null)
  assert.equal(resultContractOf(null), null)
})

/* ===== 真实定义：字段与数据来源 ===== */

/** 内置定义里那个输出节点（在迭代体内） */
const outputNodeOfDefaultFlow = () => {
  const iterate = DEFAULT_FLOW_DEFINITION.nodes.find((node) => String(node.type) === '4')
  return { iterate, node: iterate.blocks.find((block) => String(block.type) === OUTPUT_NODE_TYPE) }
}

test('真实定义：输出节点在迭代体内 → 数据从迭代节点的聚合产出取', () => {
  const { iterate, node } = outputNodeOfDefaultFlow()
  const contract = resultContractOf(DEFAULT_FLOW_DEFINITION)

  assert.equal(contract.outputNodeId, node.id)
  assert.equal(
    contract.sourceNodeId,
    iterate.id,
    '体内输出节点的产出只在子图里，父状态只有迭代节点按同名聚合出来的列表',
  )
  assert.equal(contract.iteration, true)
  assert.deepEqual(
    contract.fields.map((field) => field.name),
    node.data.inputs.map((field) => field.name),
    '展示字段就是输出节点声明的 inputs（顺序也一致）',
  )
})

test('真实定义：输出节点没有 outputs 声明（产出名就是 inputs 的变量名）', () => {
  const { node, iterate } = outputNodeOfDefaultFlow()
  assert.equal(node.data.outputs, undefined, '输出节点只声明 inputs')
  assert.deepEqual(
    node.data.inputs.map((field) => field.name).sort(),
    iterate.data.outputs.map((field) => field.name).sort(),
    '迭代节点的 outputs 必须与出口子块（输出节点）同名，否则聚合恒为空',
  )
})

test('真实定义：汇总节点取迭代节点的直接下游（班级统计的来源）', () => {
  const { iterate } = outputNodeOfDefaultFlow()
  const contract = resultContractOf(DEFAULT_FLOW_DEFINITION)
  const next = DEFAULT_FLOW_DEFINITION.edges
    .find((edge) => edge.sourceNodeID === iterate.id).targetNodeID

  assert.equal(contract.summaryNodeId, next)
  const summary = DEFAULT_FLOW_DEFINITION.nodes.find((node) => node.id === next)
  assert.ok(
    summary.data.outputs.some((field) => field.name === 'average_score'),
    '下游那个节点确实是汇总节点（产出班级统计）',
  )
})

/* ===== 平行列表 → 逐条记录 ===== */

test('recordsOf：按下标对齐多个平行列表', () => {
  const fields = [{ name: 'student' }, { name: 'score' }]
  assert.deepEqual(
    recordsOf({ student: ['天才', '许小齐'], score: [67, 64] }, fields),
    [{ student: '天才', score: 67 }, { student: '许小齐', score: 64 }],
  )
})

test('recordsOf：长度不一致时按最长的对齐、短的补 null（不静默丢学生）', () => {
  const fields = [{ name: 'student' }, { name: 'score' }, { name: 'comment' }]
  const records = recordsOf({ student: ['A', 'B', 'C'], score: [1, 2], comment: '整段文本' }, fields)

  assert.equal(records.length, 3, '取最长（3 个学生），不是取最短（2）')
  assert.equal(records[2].student, 'C')
  assert.equal(records[2].score, null, '这一列没有值 → 补 null，由页面显示为空缺')
  assert.equal(records[0].comment, '整段文本', '标量列当单项处理')
  assert.equal(records[1].comment, null)
})

test('recordsOf：全空 / 缺列都不抛错', () => {
  assert.deepEqual(recordsOf(null, [{ name: 'a' }]), [])
  assert.deepEqual(recordsOf({}, [{ name: 'a' }]), [])
  assert.deepEqual(recordsOf({ a: [] }, [{ name: 'a' }]), [])
  assert.deepEqual(recordsOf({ a: [1] }, []), [])
})
