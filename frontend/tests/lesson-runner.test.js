/**
 * 讲课工作项最小队列（LessonRunner）的可测约束（Node 内置 `node:test`）。
 *
 * 锁五条来自方案口径的规则（docs「解决方案-04」§4）：
 * ① 整页 = 多段串行：上一段流结束（resolve）后下一段才发出，段身份稳定（`page-{页}-seg-{序}`）；
 * ② 段失败中止本页：后续段不再发出，事件里说清楚败在哪一段；
 * ③ 「能停」：stop 作废一切 + 注入的 stopSpeaking 被调用，旧段事后 resolve 也不产生 page-done；
 * ④ 翻页即换内容：旧回合作废（含 stopSpeaking），新页从头讲；
 * ⑤ 空页跳过：没有讲稿的页只报 page-skipped，不发出任何请求。
 *
 * 运行：cd frontend && node --test tests/lesson-runner.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { createLessonRunner } from '../src/views/ppt/utils/lesson-runner.js'
import { splitIntoSegments } from '../src/views/ppt/utils/lesson-segments.js'

/**
 * 等段循环落定：async 链的恢复横跨多个微任务 tick，单次 `await Promise.resolve()`
 * 观察到的是中间态；这里用一个宏任务把整条微任务链排空，断言才落在稳态上。
 */
const settle = () => new Promise((resolve) => globalThis.setTimeout(resolve, 0))

/**
 * 假件装配：speakSegment 不真正请求，而是把 resolve/reject 挂进 `pending`，由用例手动放行——
 * 串行、失败、作废的时序才可断言。由于队列串行，同一时刻在途请求最多一个，
 * `pending[0]` 永远指当前段（旧的已被移出？不：旧的 resolve 后 Promise 已定型，
 * 这里用 shift 语义——放行后把队头弹掉，保持 [0] 恒为当前段）。
 */
function setup(texts, limit = 1000) {
  const events = []
  /** 已发出但未结束的段请求：{ seg, resolve, reject } */
  let pending = []
  let stopCount = 0
  const runner = createLessonRunner({
    speakSegment: (seg) =>
      new Promise((resolve, reject) => {
        pending.push({ seg, resolve, reject })
      }),
    stopSpeaking: () => {
      stopCount += 1
      // 模拟真实 abort：挂起的段请求被打挂（AbortError），由 runner 的回合作废分支吞掉
      const hanging = pending
      pending = []
      hanging.forEach(({ reject }) => reject(new Error('aborted')))
    },
    getPageText: (page) => texts[page] ?? '',
    onEvent: (event) => events.push(event),
    segmentText: (text) => splitIntoSegments(text, limit),
  })
  return {
    runner,
    events,
    pending: () => pending,
    stopCount: () => stopCount,
    /** 放行当前（队头）在途请求：正常结束 */
    resolveCurrent: () => {
      const current = pending.shift()
      current?.resolve()
      return current?.seg
    },
    /** 打挂当前在途请求 */
    rejectCurrent: (error) => {
      const current = pending.shift()
      current?.reject(error ?? new Error('合成失败'))
      return current?.seg
    },
  }
}

test('start：整页切段先入队，发送严格串行，讲完报 page-done', async () => {
  const { runner, events, pending, resolveCurrent } = setup(['第一句。第二句。第三句。'], 4)

  runner.start(0)
  await settle()

  assert.equal(runner.active, true)
  assert.deepEqual(events, [{ type: 'page-start', page: 0, segments: 3 }],
    '先入队：page-start 一次报出整页的段数')
  assert.equal(pending().length, 1, '后发送：同一时刻只有一段在途')
  assert.equal(pending()[0].seg.id, 'page-0-seg-0')
  assert.equal(pending()[0].seg.text, '第一句。')

  resolveCurrent()
  await settle()
  assert.equal(pending().length, 1, '上一段结束后下一段才发出')
  assert.equal(pending()[0].seg.id, 'page-0-seg-1')
  assert.equal(pending()[0].seg.text, '第二句。')

  resolveCurrent()
  await settle()
  assert.equal(pending()[0].seg.id, 'page-0-seg-2')

  resolveCurrent()
  await settle()
  assert.equal(pending().length, 0, '全部段结束后没有残留请求')
  assert.deepEqual(events, [
    { type: 'page-start', page: 0, segments: 3 },
    { type: 'page-done', page: 0, segments: 3 },
  ])
})

test('start：段文本按句切好交给 speakSegment', async () => {
  const { runner, pending } = setup(['一。二。'], 2)
  runner.start(0)
  await settle()
  assert.deepEqual(pending().map(({ seg }) => seg.text), ['一。'])
})

test('段失败：中止本页后续段并报 page-failed', async () => {
  const { runner, events, pending, resolveCurrent, rejectCurrent } = setup(['一。二。三。'], 2)

  runner.start(0)
  await settle()
  resolveCurrent()
  await settle()
  assert.equal(pending().length, 1)
  assert.equal(pending()[0].seg.id, 'page-0-seg-1')

  rejectCurrent()
  await settle()
  assert.equal(events.at(-1).type, 'page-failed')
  assert.equal(events.at(-1).segmentId, 'page-0-seg-1')
  assert.equal(pending().length, 0, '第三段不再发出')
  assert.ok(events.every((event) => event.type !== 'page-done'), '失败的页不许报完成')
})

test('stop：立刻安静 + 作废旧回合，旧段事后 resolve 也不产生 page-done', async () => {
  const { runner, events, pending, resolveCurrent, stopCount } = setup(['一。二。'], 2)

  runner.start(0)
  await settle()
  runner.stop()
  assert.equal(stopCount(), 1, 'stop 必须调 stopSpeaking（掐请求 + 播放器静音）')
  assert.equal(runner.active, false)
  assert.equal(events.at(-1).type, 'stopped')

  // 在途段无论 resolve 还是 reject（被 abort 打挂），回合都已作废：不再发段、不报完成、不报失败
  resolveCurrent()
  await settle()
  assert.equal(pending().length, 0)
  assert.ok(!events.some((event) => event.type === 'page-done'), '停止后不许有 page-done')
  assert.ok(!events.some((event) => event.type === 'page-failed'), '被掐断不算失败')

  // 停止后再 stop 是空操作
  const before = events.length
  runner.stop()
  assert.equal(events.length, before)
})

test('翻页：旧回合作废 + stopSpeaking，新页从头讲', async () => {
  const texts = ['旧页一。旧页二。', '新页一。']
  const { runner, events, pending, resolveCurrent, stopCount } = setup(texts, 4)

  runner.start(0)
  await settle()
  assert.equal(pending()[0].seg.page, 0)
  assert.equal(events[0].type, 'page-start')

  runner.onPageChanged(1)
  assert.equal(stopCount(), 1, '翻页先把旧页的声音停掉')
  assert.equal(pending().length, 1, '旧页的在途请求已被 abort 打挂，只剩新页的段')
  assert.equal(pending()[0].seg.page, 1)
  assert.equal(pending()[0].seg.id, 'page-1-seg-0')
  assert.equal(pending()[0].seg.text, '新页一。')
  assert.ok(events.every((event) => event.type !== 'page-failed'), '被翻页掐断的旧段不算失败')

  // 新页讲完：旧页不产生 page-done，新页正常报完成
  resolveCurrent()
  await settle()
  assert.equal(pending().length, 0)
  assert.deepEqual(events.filter((event) => event.type === 'page-done'),
    [{ type: 'page-done', page: 1, segments: 1 }])
})

test('未启动时翻页与 drained 都是空操作', () => {
  const { runner, events, stopCount } = setup(['一。'])
  runner.onPageChanged(0)
  runner.notifyDrained()
  assert.equal(stopCount(), 0)
  assert.deepEqual(events, [])
})

test('空页：只报 page-skipped，不发任何请求', async () => {
  const { runner, events, pending } = setup(['   ', '有讲稿。'])
  runner.start(0)
  await settle()
  assert.deepEqual(events, [{ type: 'page-skipped', page: 0 }])
  assert.equal(pending().length, 0)

  // 翻到有讲稿的页正常讲
  runner.onPageChanged(1)
  await settle()
  assert.equal(pending()[0].seg.id, 'page-1-seg-0')
})

test('notifyDrained：active 时上报 page-drained（P1 自动推进的前置事件）', async () => {
  const { runner, events } = setup(['一。'])
  runner.start(0)
  await settle()
  runner.notifyDrained()
  assert.deepEqual(events.at(-1), { type: 'page-drained' })

  runner.stop()
  runner.notifyDrained()
  assert.equal(events.length, 3, '停止后 drained 不再上报')
})
