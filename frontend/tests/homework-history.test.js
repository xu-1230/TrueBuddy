/**
 * 批改历史抽屉的两个纯函数 seam：
 * ①状态映射——列表徽标按 homework_task.status 的五档口径取文案与配色，未知值不炸；
 * ②文件树构建——任务详情的平铺 files（按 relative_path 升序）还原出目录层级，
 *   空输入与无 URL 的文件都不能让画布回看挂掉。
 *
 * 运行：cd frontend && node --test tests/homework-history.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { statusTextOf, statusToneOf } from '../src/views/homework/utils/status.js'
import { buildFileTree } from '../src/views/homework/utils/file-tree.js'
import { formatDateTime } from '../src/views/homework/utils/format.js'

test('状态映射：五档口径 + 未知值兜底', () => {
  assert.equal(statusTextOf(0), '待批改')
  assert.equal(statusTextOf(1), '批改中')
  assert.equal(statusTextOf(2), '成功')
  assert.equal(statusTextOf(3), '部分成功')
  assert.equal(statusTextOf(4), '失败')
  assert.equal(statusTextOf(null), '未知')
  assert.equal(statusTextOf(9), '未知')
})

test('状态配色：终态才有成败色，进行中一律中性', () => {
  assert.equal(statusToneOf(2), 'success')
  assert.equal(statusToneOf(3), 'success')
  assert.equal(statusToneOf(4), 'failed')
  assert.equal(statusToneOf(0), 'pending')
  assert.equal(statusToneOf(1), 'pending')
  assert.equal(statusToneOf(null), 'pending')
})

test('文件树：平铺路径还原目录层级，文件节点带大小与 URL', () => {
  const tree = buildFileTree([
    { path: '电子技术基础-第3次作业/20230101-张三/第三章习题.docx', name: '第三章习题.docx', size: 512, url: 'https://minio/a' },
    { path: '电子技术基础-第3次作业/20230101-李四/第三章习题.pdf', name: '第三章习题.pdf', size: 256 },
  ])

  assert.equal(tree.length, 1)
  const root = tree[0]
  assert.equal(root.label, '电子技术基础-第3次作业')
  assert.equal(root.children.length, 2)

  const zhang = root.children[0]
  assert.equal(zhang.label, '20230101-张三')
  assert.equal(zhang.file, undefined)
  const zhangFile = zhang.children[0]
  assert.equal(zhangFile.label, '第三章习题.docx')
  assert.equal(zhangFile.file, true)
  assert.equal(zhangFile.size, 512)
  assert.equal(zhangFile.url, 'https://minio/a')

  const liFile = root.children[1].children[0]
  assert.equal(liFile.url, '')
})

test('文件树：同目录文件按出现顺序归入同一节点，不重复建目录', () => {
  const tree = buildFileTree([
    { path: '作业/学生A/1.docx', name: '1.docx', size: 1 },
    { path: '作业/学生A/2.docx', name: '2.docx', size: 2 },
    { path: '作业/学生B/1.docx', name: '1.docx', size: 3 },
  ])

  assert.equal(tree.length, 1)
  assert.equal(tree[0].label, '作业')
  assert.equal(tree[0].children.length, 2)
  assert.equal(tree[0].children[0].children.length, 2)
})

test('文件树：空输入与无路径行都安全', () => {
  assert.deepEqual(buildFileTree(undefined), [])
  assert.deepEqual(buildFileTree([]), [])
  assert.deepEqual(buildFileTree([{ path: '', name: 'x' }]), [])
  assert.deepEqual(buildFileTree([{ path: null, name: 'x', size: 1 }]), [])
})

test('formatDateTime：吃下 yudao 空格分隔格式，空值回落空串', () => {
  assert.equal(formatDateTime('2026-11-16 14:05:00'), '2026-11-16 14:05')
  assert.equal(formatDateTime('2026-11-16T14:05:00'), '2026-11-16 14:05')
  assert.equal(formatDateTime(null), '')
  assert.equal(formatDateTime('不是时间'), '')
})
