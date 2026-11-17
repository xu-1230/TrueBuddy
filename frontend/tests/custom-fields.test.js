/**
 * 评分区间 / 自定义导出列的纯函数用例（Node 内置 `node:test`）。
 *
 * 锁住与后端 `HomeworkServiceImpl` 相同的口径：
 * 区间 min < max、pass ∈ [min,max]；变量名（即 key）/类型/描述（没有 label / 默认值 / 静态列）；
 * 以及成绩卡档位色「以及格线为基线两等分」。
 */
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildCustomValues,
  customRows,
  customTypeCode,
  defaultCustomFields,
  newCustomField,
  normalizeCustomFieldType,
  validateCustomFields,
  validateScoreRange,
} from '../src/views/homework/utils/custom-fields.js'
import { scoreRatio, scoreScaleOf, scoreTone } from '../src/views/homework/utils/format.js'

/* ===== 区间 ===== */

test('区间校验：默认 0–100/60 通过，min ≥ max 与及格线越界被拦', () => {
  assert.equal(validateScoreRange({ min: 0, max: 100, pass: 60 }), '')
  assert.match(validateScoreRange({ min: 100, max: 100, pass: 60 }), /最低分必须小于最高分/)
  assert.match(validateScoreRange({ min: 0, max: 20, pass: 30 }), /及格线必须落在评分区间内/)
  assert.match(validateScoreRange({ min: 0, max: 20, pass: -1 }), /及格线必须落在评分区间内/)
  assert.match(validateScoreRange({ min: '', max: 20, pass: 12 }), /完整的评分区间/)
})

test('档位色：以 pass 为基线两等分；0–100/60 仍是 80/90', () => {
  const range = { min: 0, max: 100, pass: 60 }
  assert.equal(scoreTone(92, range), 'excellent')
  assert.equal(scoreTone(90, range), 'excellent')
  assert.equal(scoreTone(85, range), 'good')
  assert.equal(scoreTone(80, range), 'good')
  assert.equal(scoreTone(70, range), 'pass')
  assert.equal(scoreTone(60, range), 'pass')
  assert.equal(scoreTone(59, range), 'weak')
  assert.equal(scoreTone(null, range), 'pending')

  const small = { min: 0, max: 20, pass: 12 }
  assert.equal(scoreTone(18, small), 'excellent')
  assert.equal(scoreTone(16, small), 'good')
  assert.equal(scoreTone(12, small), 'pass')
  assert.equal(scoreTone(11, small), 'weak')

  const shifted = { min: 60, max: 100, pass: 60 }
  assert.equal(scoreTone(90, shifted), 'excellent')
  assert.equal(scoreTone(80, shifted), 'good')
  assert.equal(scoreTone(60, shifted), 'pass')
})

test('刻度：得分在区间内的比例与阈值位置', () => {
  assert.equal(scoreRatio(0, { min: 0, max: 100, pass: 60 }), 0)
  assert.equal(scoreRatio(50, { min: 0, max: 100, pass: 60 }), 0.5)
  assert.equal(scoreRatio(100, { min: 0, max: 100, pass: 60 }), 1)
  assert.equal(scoreRatio(10, { min: 60, max: 100, pass: 60 }), 0)
  assert.equal(scoreRatio(80, { min: 60, max: 100, pass: 60 }), 0.5)

  const scale = scoreScaleOf({ min: 0, max: 20, pass: 12 })
  assert.equal(scale.pass, 12)
  assert.equal(scale.excellent, 18)
})

/* ===== 自定义列 ===== */

test('预置列：扣分明细在前、AI 评语在后，均为带描述的文本列（没有 default）', () => {
  const fields = defaultCustomFields()
  assert.deepEqual(fields.map((field) => field.key), ['deductions', 'comment'])
  assert.equal(fields.every((field) => field.type === 'string'), true)
  assert.equal(fields.every((field) => Boolean(field.description)), true)
  assert.equal(fields.every((field) => !('default' in field)), true)
  assert.equal(fields.every((field) => !('label' in field)), true)
  // 每次返回新对象，面板之间不共享引用
  assert.notEqual(defaultCustomFields()[0], defaultCustomFields()[0])
})

test('新增列：变量名留空由老师填写，无 label / default', () => {
  const field = newCustomField()
  assert.equal(field.key, '')
  assert.equal(field.type, 'string')
  assert.equal('label' in field, false)
  assert.equal('default' in field, false)
})

test('变量名校验：合法通过；重复 / 缺描述 / 类型非法被拦', () => {
  assert.equal(validateCustomFields(defaultCustomFields()), '')

  const duplicate = [
    { key: 'col_1', description: '写亮点', type: 'string' },
    { key: 'col_1', description: '再写亮点', type: 'string' },
  ]
  assert.match(validateCustomFields(duplicate), /变量名重复/)

  const noDescription = [{ key: 'col_1', description: '', type: 'string' }]
  assert.match(validateCustomFields(noDescription), /请填写描述/)

  const badType = [{ key: 'col_1', description: '写亮点', type: 'unknown' }]
  assert.match(validateCustomFields(badType), /不支持的类型/)

  const numberField = [{ key: 'col_1', description: '0–20 的分数', type: 'number' }]
  assert.equal(validateCustomFields(numberField), '')

  const objectField = [{ key: 'col_1', description: '输出结构化结果', type: 'object' }]
  assert.equal(validateCustomFields(objectField), '')

  const booleanField = [{ key: 'col_1', description: '给出 true / false', type: 'boolean' }]
  assert.equal(validateCustomFields(booleanField), '')

  const listMissingElement = [{ key: 'col_1', description: '逐条列出', type: 'list' }]
  assert.match(validateCustomFields(listMissingElement), /需要选择元素类型/)

  const listField = [{
    key: 'col_1',
    description: '逐条列出',
    type: 'list',
    schema: { type: 'string' },
  }]
  assert.equal(validateCustomFields(listField), '')
})

test('类型归一与短码：text → string，integer 保持整数，list 读作 [str]', () => {
  assert.equal(normalizeCustomFieldType('text'), 'string')
  assert.equal(normalizeCustomFieldType('integer'), 'integer')
  assert.equal(customTypeCode({ type: 'string' }), 'str')
  assert.equal(customTypeCode({ type: 'integer' }), 'int')
  assert.equal(customTypeCode({ type: 'number' }), 'num')
  assert.equal(customTypeCode({ type: 'boolean' }), 'bool')
  assert.equal(customTypeCode({ type: 'object' }), 'obj')
  assert.equal(customTypeCode({ type: 'list', schema: { type: 'string' } }), '[str]')
  assert.equal(customTypeCode({ type: 'file', schema: { type: 'string' } }), 'file')
})

test('类型渲染：number/integer → number，boolean/list/object/string 各自 kind', () => {
  const fields = [
    { key: 'score2', type: 'integer' },
    { key: 'pass', type: 'boolean' },
    { key: 'tags', type: 'list', schema: { type: 'string' } },
    { key: 'meta', type: 'object' },
    { key: 'note', type: 'string' },
  ]
  const rows = customRows(fields, {
    score2: 18,
    pass: true,
    tags: ['思路清楚', '引用准确'],
    meta: { level: 'A' },
    note: '继续保持',
  })
  assert.deepEqual(rows.map((row) => row.kind), ['number', 'boolean', 'list', 'object', 'text'])
  assert.deepEqual(rows.map((row) => row.typeCode), ['int', 'bool', '[str]', 'obj', 'str'])
  assert.deepEqual(rows[2].value, ['思路清楚', '引用准确'])
  assert.deepEqual(rows[3].value, { level: 'A' })
})

test('取值：按定义顺序返回，缺失为空串，未定义 key 丢弃', () => {
  const fields = defaultCustomFields()
  const rows = customRows(fields, {
    deductions: '文本细读 −4：缺少原文',
    comment: '整体不错',
    unexpected: '不该出现',
  })
  assert.deepEqual(rows.map((row) => row.key), ['deductions', 'comment'])
  assert.deepEqual(rows.map((row) => row.value), ['文本细读 −4：缺少原文', '整体不错'])
  assert.equal(rows.some((row) => 'static' in row), false)

  assert.deepEqual(buildCustomValues(fields, { comment: '整体不错' }), {
    deductions: '',
    comment: '整体不错',
  })
  assert.deepEqual(buildCustomValues(fields, '{"comment":"模型写成字符串"}'), {
    deductions: '',
    comment: '模型写成字符串',
  })
})
