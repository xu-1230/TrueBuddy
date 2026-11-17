/**
 * 批改页的「卡片字段契约」用例（Node 内置 `node:test`，零新依赖）。
 *
 * 钉住的是一次真实事故：`ScoreCard` 改成「一学生一卡」后开始读 `record.files` /
 * `readFiles` / `deductions`（取 `.length` 与 `v-for`），但页面上另一个卡片映射函数没跟着改，
 * 结果那张卡一进 `records` 就在渲染时抛 `TypeError` —— 卡堆是 `v-for` 渲染的，一抛就是
 * **整棵子树不挂载**：页面表现是「卡堆整块消失、只剩它前面已经渲染出来的东西」，
 * 而后端日志显示批改其实全跑完了（3 轮读文件、迭代产出齐全）。
 *
 * 现在的契约：`ScoreCard` 右栏按 `fields` 定义渲染 `record.custom`，固定字段只剩
 * student / score / grade / files / readFiles；`toRecordFromGrading` 必须一次给齐。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf8')

/** `ScoreCard` 模板 / 脚本里读到的 `record.xxx` 字段名 */
const fieldsReadByScoreCard = () => {
  const source = read('../src/views/homework/components/ScoreCard.vue')
  const names = new Set()
  for (const match of source.matchAll(/\brecord\.([A-Za-z_$][\w$]*)/g)) {
    names.add(match[1])
  }
  return [...names].sort()
}

/**
 * 某个映射函数里 `return { … }` 直接给出的字段名。
 *
 * 只认「缩进恰好四个空格 + 标识符 + `:` 或 `,`」的行：函数体里其它行（`const` / `return`）
 * 都是两个空格缩进，不会误命中；`:` 覆盖普通属性、`,` 覆盖简写属性（`studentName,`）。
 */
const fieldsProvidedBy = (source, functionName) => {
  const start = source.indexOf(`function ${functionName}(`)
  assert.ok(start > 0, `能在 HomeworkGradingView.vue 里定位 ${functionName}`)
  const end = source.indexOf('\n}', start)
  const body = source.slice(start, end)
  const names = new Set()
  for (const match of body.matchAll(/^ {4}([A-Za-z_$][\w$]*)(?::|,)/gm)) {
    names.add(match[1])
  }
  return names
}

test('ScoreCard 读到的每个 record 字段，卡片映射函数都必须提供', () => {
  const view = read('../src/views/homework/HomeworkGradingView.vue')
  const needed = fieldsReadByScoreCard()

  // 前置：提取逻辑本身得有效，否则这条用例会变成永远为真的空壳
  assert.ok(needed.length >= 8, `应能从 ScoreCard 提取到字段清单（实际 ${needed.length} 个）`)

  const provided = fieldsProvidedBy(view, 'toRecordFromGrading')
  assert.ok(provided.size >= 8, `能从 toRecordFromGrading 提取到字段清单（实际 ${provided.size} 个）`)

  const missing = needed.filter((name) => !provided.has(name))
  assert.deepEqual(missing, [], `成绩卡（toRecordFromGrading）少了 ScoreCard 会读的字段：${missing.join('、')}`)
})

test('成绩卡提供文件 / 已读 / 自定义输出三个字段（模板会遍历或渲染）', () => {
  const view = read('../src/views/homework/HomeworkGradingView.vue')
  const provided = fieldsProvidedBy(view, 'toRecordFromGrading')

  for (const name of ['files', 'readFiles', 'custom']) {
    assert.ok(provided.has(name), `toRecordFromGrading 必须提供 ${name}（缺了会让卡堆整块不渲染）`)
  }
})

test('ScoreCard 对文件字段做空值兜底，且不再直接读旧 deductions / comment', () => {
  const source = read('../src/views/homework/components/ScoreCard.vue')

  // 模板里读列表长度/遍历时必须带 `?? []` 兜底，不能裸取 .length
  assert.doesNotMatch(source, /record\.files\.length/)
  assert.doesNotMatch(source, /record\.readFiles\.length/)
  assert.match(source, /record\.files \?\? \[\]/)
  assert.match(source, /record\.readFiles \?\? \[\]/)

  // 旧固定字段已迁到 custom（由 customRows 统一兜底），卡片不应再直接读它们
  assert.doesNotMatch(source, /record\.deductions/)
  assert.doesNotMatch(source, /record\.comment/)
  assert.match(source, /record\.custom/)
})
