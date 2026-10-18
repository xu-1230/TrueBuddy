/**
 * 人工确认卡片的取值口径用例（Node 内置 `node:test`，零新依赖）。
 *
 * 钉住的是一条**引擎契约**：后端 `HumanNode#apply` 只从本次提交的 `values` 回写
 * `state[中断节点id]`，空提交直接返回 `Map.of()`。于是"卡片该回填什么"写错的表现是
 * **流程照样成功、下游变量却全是空**（实测：汇总节点的「满分 / 及格线」被渲染成空串，
 * 模型在 summary 里自己写了「未提供满分与及格线信息」）。这种错只能靠用例钉住。
 *
 * 后两条用例直接对着 `default-flow.js` 的真实定义断言，把"定义怎么声明"与
 * "卡片怎么呈现"绑在一起 —— 以后谁给人机确认节点加一个没有上游来源的 `outputs`，
 * 会先在这里红掉，而不是等到某次批量批改跑完才发现没人能填。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  blockingIssuesOf,
  isEmptyValue,
  kindOf,
  resolveCard,
} from '../src/views/workflow2/utils/card-fields.js'

/** 造一张卡片：`data` 是只读回显项，`fields` 是 outputs 声明 */
const cardOf = (data, fields) => ({ node_id: 'H', card_type: 'INPUT', title: '人工确认', data, fields })

/* ===== 三组字段的划分 ===== */

test('同名即只读回填：fields 与 data 同名时进 readonly，并把上游原值预填进提交取值', () => {
  const card = cardOf(
    [{ name: 'student_list', type: 'list', label: '学生名单', value: ['天才', '许小齐'] }],
    [{ name: 'student_list', type: 'list', required: true, label: '确认后生效的学生名单' }],
  )
  const { values, readonly, editable, unfillable } = resolveCard(card)

  assert.deepEqual(values, { student_list: ['天才', '许小齐'] })
  assert.equal(readonly.length, 1)
  assert.deepEqual(readonly[0].value, ['天才', '许小齐'])
  assert.equal(editable.length, 0, '有上游来源的字段不该再要求人填一遍')
  assert.equal(unfillable.length, 0)
})

test('无同名 + 文本类 → 进 editable，初始为空串（这才需要人填）', () => {
  const card = cardOf(
    [{ name: 'plan', type: 'string', label: '批改方案', value: '按维度打分' }],
    [
      { name: 'plan', type: 'string', required: true, label: '确认后生效的批改方案' },
      { name: 'extra_note', type: 'string', required: false, label: '补充说明' },
    ],
  )
  const { values, readonly, editable, unfillable } = resolveCard(card)

  assert.equal(readonly.length, 1, 'plan 有来源 → 只读回填')
  assert.deepEqual(values, { plan: '按维度打分', extra_note: '' })
  assert.deepEqual(editable.map((f) => f.name), ['extra_note'])
  assert.equal(unfillable.length, 0)
})

test('无同名 + 列表 / 对象类 → 进 unfillable，且不占用提交取值', () => {
  const card = cardOf([], [
    { name: 'files', type: 'list', required: true, label: '作业文件' },
    { name: 'meta', type: 'object', required: true, label: '元信息' },
  ])
  const { values, editable, unfillable } = resolveCard(card)

  assert.deepEqual(values, {}, '没有控件可填的字段不该出现在提交取值里')
  assert.equal(editable.length, 0)
  assert.deepEqual(unfillable.map((f) => f.name), ['files', 'meta'])
})

test('没有 name 的声明跳过；空卡片不抛异常', () => {
  assert.deepEqual(resolveCard(null), { values: {}, readonly: [], editable: [], unfillable: [] })
  assert.deepEqual(resolveCard(undefined).values, {})
  const { values, editable } = resolveCard(cardOf([], [{ type: 'string', required: true }, null]))
  assert.deepEqual(values, {})
  assert.equal(editable.length, 0)
})

/* ===== 提交前的拦截 ===== */

test('阻断项分两种：上游没给到值 vs 用户没填', () => {
  const card = cardOf(
    [{ name: 'rubric', type: 'string', label: '评分细则', value: undefined }],
    [
      { name: 'rubric', type: 'string', required: true, label: '确认后生效的评分细则' },
      { name: 'note', type: 'string', required: true, label: '补充说明' },
    ],
  )

  assert.deepEqual(blockingIssuesOf(card, { rubric: undefined, note: '' }), {
    sourceMissing: ['确认后生效的评分细则'],
    unfilled: ['补充说明'],
  })
  // 填上可编辑项之后只剩"上游没给到值"这一种
  assert.deepEqual(blockingIssuesOf(card, { rubric: undefined, note: '已核对' }), {
    sourceMissing: ['确认后生效的评分细则'],
    unfilled: [],
  })
})

test('unfillable 的必填字段不参与拦截（否则会出现填不了又过不去的死锁）', () => {
  const card = cardOf([], [{ name: 'files', type: 'list', required: true, label: '作业文件' }])
  assert.deepEqual(blockingIssuesOf(card, {}), { sourceMissing: [], unfilled: [] })
})

test('空值判定：null / undefined / 空串 / 空数组算空；0、false 与 [0] 不算空', () => {
  assert.equal(isEmptyValue(null), true)
  assert.equal(isEmptyValue(undefined), true)
  assert.equal(isEmptyValue('   '), true)
  assert.equal(isEmptyValue([]), true)
  assert.equal(isEmptyValue(0), false, '满分填 0 是合法取值')
  assert.equal(isEmptyValue(false), false)
  assert.equal(isEmptyValue([0]), false)
})

test('类型归一：list / array / object 之外一律当文本', () => {
  assert.equal(kindOf({ type: 'list' }), 'list')
  assert.equal(kindOf({ type: 'ARRAY' }), 'list')
  assert.equal(kindOf({ type: 'object' }), 'object')
  assert.equal(kindOf({ type: 'text' }), 'text')
  assert.equal(kindOf({}), 'text')
  assert.equal(kindOf(null), 'text')
})

/* ===== 与真实定义的契约 ===== */

/** 默认流程里的人工确认节点（type = 5） */
const humanNode = () => DEFAULT_FLOW_DEFINITION.nodes.find((node) => String(node.type) === '5')

/** 演示取值：按变量名给一份样例（定义换变量名时这里跟着改，用例本身不依赖具体名字） */
const SAMPLE = {
  student_list: [{ student: '天才', files: [{ path: 'a.docx', fileId: 122 }] }],
  plan: '满分 100、及格线 60；主题契合 25 / 内容深度 30 / 呈现效果 25 / 规范性 20',
}

/** 按后端 `HumanNode` 的口径把节点声明拼成卡片：data 来自 inputs，fields 来自 outputs */
const cardOfHumanNode = () => {
  const node = humanNode()
  return {
    node_id: node.id,
    card_type: 'INPUT',
    title: node.data.label,
    data: node.data.inputs.map((field) => ({
      name: field.name,
      type: field.type ?? field.input?.type,
      label: field.description,
      value: SAMPLE[field.name],
    })),
    fields: node.data.outputs.map((field) => ({
      name: field.name,
      type: field.type,
      required: field.required,
      label: field.description,
      schema: field.schema,
    })),
  }
}

test('真实定义：人工确认节点的每个 outputs 都有同名上游来源（否则卡片上没人能填）', () => {
  const node = humanNode()
  const sourceNames = new Set(node.data.inputs.map((field) => field.name))
  const orphan = node.data.outputs
    .map((field) => field.name)
    .filter((name) => !sourceNames.has(name))

  assert.deepEqual(orphan, [], '没有上游来源的 outputs 会渲染成填不了的控件，必须同时补进 inputs')
})

test('真实定义：卡片每个字段都只读回填，可编辑控件数为 0（卡片只剩「待确认数据 + 确定」）', () => {
  const node = humanNode()
  const card = cardOfHumanNode()
  const { values, readonly, editable, unfillable } = resolveCard(card)

  // 逐字段按定义断言：名单与批改方案都必须真的被回填 —— 它们丢了就会让下游拿到空值
  assert.deepEqual(readonly.map((item) => item.name), node.data.outputs.map((field) => field.name))
  assert.equal(editable.length, 0, '有上游来源的字段不该再要求人填一遍')
  assert.equal(unfillable.length, 0)
  assert.deepEqual(values.student_list, SAMPLE.student_list)
  assert.equal(values.plan, SAMPLE.plan)
  assert.deepEqual(blockingIssuesOf(card, values), { sourceMissing: [], unfilled: [] })
})

test('真实定义：上游没产出取值时，拦下来的理由是「上游未提供取值」而不是「请先填写」', () => {
  const card = cardOfHumanNode()
  card.data = card.data.map((item) => ({ ...item, value: undefined }))

  const issues = blockingIssuesOf(card, resolveCard(card).values)
  assert.equal(issues.unfilled.length, 0, '这些字段没有可编辑控件，不该提示"请先填写"')
  assert.deepEqual(issues.sourceMissing, card.fields.map((field) => field.label))
})
