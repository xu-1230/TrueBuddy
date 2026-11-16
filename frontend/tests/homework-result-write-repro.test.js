/**
 * 最小复现：内置标准工作流跑完后，前端交给 POST /homework/{taskId}/results 的 payload
 * 是否真的带上了逐学生记录。表 0 行的直接充要条件是 payload.records 为空。
 *
 * 运行：cd frontend && node --test tests/homework-result-write-repro.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  resultContractOf,
  recordsOf,
} from '../src/views/homework/utils/result-contract.js'
import { buildResultWritePayload } from '../src/views/homework/utils/result-payload.js'

const definition = JSON.parse(readFileSync(
  new URL('../../backend/tb-module-workflow/src/main/resources/workflow/standard-grading.json', import.meta.url),
  'utf8',
))

test('内置标准工作流：迭代聚合产出 → 回写 payload 必须带逐学生记录', () => {
  const contract = resultContractOf(definition)
  assert.ok(contract, '标准工作流必须能解析出结果契约')

  // 模拟 IterateNode.aggregate 的产出：每个 output 字段一个平行列表，下标即学生
  const aggregate = {
    student: ['张三', '李四'],
    score: [88, 76],
    grade: ['良', '及格'],
    comment: ['结构完整', '第三题失分'],
    deductions: [[{ item: '第三题', diff: 5, reason: '公式错误' }], []],
    readFiles: [['电子技术基础/张三/第三章.docx'], ['电子技术基础/李四/第三章.docx']],
  }

  const records = recordsOf(aggregate, contract.fields)
  assert.equal(records.length, 2, '平行列表必须还原成两条学生记录')

  const payload = buildResultWritePayload({ records, statistics: null, conversationId: 'conv-repro' })
  assert.equal(payload.records.length, 2, '空 records 会让后端只删不插，表必然 0 行')
  assert.equal(payload.records[0].student, '张三')
  assert.equal(payload.records[0].score, 88)
})
