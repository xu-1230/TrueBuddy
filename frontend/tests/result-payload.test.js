/**
 * 批改结果回写 payload 组装的行为用例（Node 内置 `node:test`）。
 *
 * 锁三条不能靠肉眼看出来的：
 * ①终态与漏批份数**不在前端组装**——服务端从 records 数量对比 fileCount 推导，避免双头真相；
 * ②模型的脏输出（custom 写成字符串、缺分数、null 字段）要归一成服务端能落库的形状；
 * ③执行失败的空载荷也要能组装（收敛终态 4 用）。
 *
 * 运行：cd frontend && node --test tests/result-payload.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { buildResultWritePayload } from '../src/views/homework/utils/result-payload.js'

test('payload：records 逐字段归一（文本归空、分数缺失置 null、custom 归一为对象）', () => {
  const payload = buildResultWritePayload({
    records: [
      {
        student: ' 张三 ',
        score: '88',
        grade: '良',
        custom: {
          deductions: '字迹潦草 −2：卷面',
          comment: '整体完整，第三题失分',
        },
        readFiles: ['电子技术基础-第3次作业/20230101-张三/第三章习题.docx'],
      },
      { student: null, score: null, custom: '{"comment":"模型写成字符串"}', readFiles: null },
    ],
    statistics: { average: 81.5 },
    conversationId: 123,
  })

  assert.equal(payload.conversationId, '123')
  assert.deepEqual(payload.statistics, { average: 81.5 })

  const [zhang, blank] = payload.records
  assert.equal(zhang.student, '张三')
  assert.equal(zhang.score, 88)
  assert.deepEqual(zhang.custom, {
    deductions: '字迹潦草 −2：卷面',
    comment: '整体完整，第三题失分',
  })
  assert.equal('comment' in zhang, false, '旧固定字段不再进 payload')
  assert.equal('deductions' in zhang, false)
  assert.equal(zhang.readFiles.length, 1)

  assert.equal(blank.student, '')
  assert.equal(blank.score, null)
  assert.equal(blank.grade, '')
  assert.deepEqual(blank.custom, { comment: '模型写成字符串' })
  assert.deepEqual(blank.readFiles, [])
})

test('payload：无效分数（非有限数）置 null，不编造 0 分', () => {
  const payload = buildResultWritePayload({
    records: [{ student: '张三', score: '很好' }],
  })
  assert.equal(payload.records[0].score, null)
  assert.deepEqual(payload.records[0].custom, {})
})

test('payload：执行失败的空载荷也能组装（收敛终态 4 用）', () => {
  const payload = buildResultWritePayload({ records: [], statistics: null, conversationId: '' })
  assert.deepEqual(payload, {
    conversationId: '',
    statistics: null,
    records: [],
  })
})

test('payload：records 缺省当空列表，conversationId 缺省当空串', () => {
  const payload = buildResultWritePayload({})
  assert.deepEqual(payload.records, [])
  assert.equal(payload.conversationId, '')
  assert.equal(payload.statistics, null)
})
