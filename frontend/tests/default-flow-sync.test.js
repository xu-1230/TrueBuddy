/**
 * 前端默认画布 ↔ 后端内置种子的一致性用例。
 *
 * `default-flow.js` 与 `standard-grading.json` 是同一份内置定义的两个落点：
 * 前者用于「无 workflow_id 的空白画布」，后者由启动初始化器写入 workflow 表并真正执行。
 * 两边不同步的表现是「画布上看到的一套、跑的是另一套」——只能靠用例钉住。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'

const seed = JSON.parse(readFileSync(
  new URL('../../backend/tb-module-workflow/src/main/resources/workflow/standard-grading.json', import.meta.url),
  'utf8',
))

const nodeOf = (definition, id) => definition.nodes.find((node) => node.id === id)

test('默认画布与后端种子逐字段一致（防止两份定义漂移）', () => {
  assert.deepEqual(DEFAULT_FLOW_DEFINITION, seed)
})

test('开始节点：评分区间与自定义列都有 default', () => {
  const start = nodeOf(seed, '100001')
  const outputOf = (name) => start.data.outputs.find((field) => field.name === name)

  assert.equal(outputOf('score_min').default, 0)
  assert.equal(outputOf('score_max').default, 100)
  assert.equal(outputOf('pass_score').default, 60)
  assert.equal(outputOf('custom_fields').required, false)
  assert.deepEqual(
    outputOf('custom_fields').default.map((field) => field.key),
    ['deductions', 'comment'],
  )
})

test('迭代体：评分 → 输出，产出含 custom、不再有固定 comment/deductions', () => {
  const iterate = nodeOf(seed, '100004')
  assert.deepEqual(iterate.blocks.map((block) => block.id), ['100005', '100008'])
  assert.deepEqual(iterate.data.outputs.map((field) => field.name), [
    'student', 'score', 'grade', 'custom', 'readFiles',
  ])

  const output = iterate.blocks.find((block) => block.id === '100008')
  assert.deepEqual(
    output.data.inputs.map((field) => field.name),
    iterate.data.outputs.map((field) => field.name),
    '迭代 outputs 必须与出口子块 inputs 同名，否则聚合恒为空',
  )
})

test('汇总节点不再吃 comment，只用 pass_score 判不及格', () => {
  const summary = nodeOf(seed, '100007')
  const inputNames = summary.data.inputs.map((field) => field.name)
  assert.equal(inputNames.includes('comment'), false)
  assert.equal(inputNames.includes('pass_score'), true)

  const gradeSheet = summary.data.outputs.find((field) => field.name === 'grade_sheet')
  assert.deepEqual(gradeSheet.schema.required, ['student', 'score', 'grade'])
})
