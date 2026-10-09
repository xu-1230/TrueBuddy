/**
 * 数字人口型驱动的可测部分（Node 内置 `node:test`）。
 *
 * 锁三条实测/设计出来的约束（docs/17 §13.3）：
 * ① AudioContext 整页只建一次并复用 —— 每次订阅都新建时第一次订阅会长时间拿不到帧；
 * ② 口型用**逐帧瞬时响度** —— 累计平均在流停后仍会动；
 * ③ 底噪与异常值必须归零，否则嘴会一直微张。
 *
 * 运行：cd frontend && node --test tests/agent-audio.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { createAgentAudioMeter, mouthLevel, rmsOf } from '../src/views/meeting/utils/agentAudio.js'

/** 造一个假 AudioContext：每帧按 patterns 依次给出采样值 */
function fakeContext(patterns, state) {
  let frame = 0
  return {
    state: 'suspended',
    resume() {
      this.state = 'running'
      state.contextResumes += 1
      return Promise.resolve()
    },
    createMediaStreamSource() {
      state.sources += 1
      return { connect() {}, disconnect() {} }
    },
    createAnalyser() {
      return {
        fftSize: 0,
        getFloatTimeDomainData(buffer) {
          const amplitude = patterns[Math.min(frame, patterns.length - 1)] ?? 0
          frame += 1
          state.frames += 1
          buffer.fill(amplitude)
        },
        disconnect() {},
      }
    },
    close() {
      state.closed += 1
      return Promise.resolve()
    },
  }
}

function setup(patterns, { ticks = patterns.length } = {}) {
  const state = { contexts: 0, contextResumes: 0, sources: 0, frames: 0, closed: 0 }
  let context = null
  const levels = []
  let budget = ticks
  const meter = createAgentAudioMeter({
    createContext: () => {
      state.contexts += 1
      context ??= fakeContext(patterns, state)
      return context
    },
    onLevel: (level) => levels.push(level),
    // 同步驱动 RAF，让每一帧都可预期；预算用完就停，避免无限递归
    requestFrame: (cb) => {
      if (budget > 0) {
        budget -= 1
        cb(0)
      }
      return 1
    },
  })
  return { meter, levels, state }
}

test('mouthLevel：门限以下、异常值都归零，正常区间线性且封顶 1', () => {
  assert.equal(mouthLevel(0), 0)
  assert.equal(mouthLevel(0.004), 0, '低于 0.005 的门限当底噪')
  assert.equal(mouthLevel(0.05), 0.2)
  assert.equal(mouthLevel(0.15), 0.6)
  assert.equal(mouthLevel(0.9), 1, '不能超出 Live2D 参数区间')
  for (const bad of [NaN, -1, undefined, null]) assert.equal(mouthLevel(bad), 0)
})

test('rmsOf：常数信号等于自身，全零为 0，空输入为 0', () => {
  // Float32 存不下精确的 0.1 / 0.5，比较一律带容差
  const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-6, `${actual} ≠ ${expected}`)
  close(rmsOf(new Float32Array([0.5, 0.5, 0.5, 0.5])), 0.5)
  assert.equal(rmsOf(new Float32Array([0, 0, 0])), 0)
  close(rmsOf(new Float32Array([0.1, -0.1])), 0.1)
  assert.equal(rmsOf(new Float32Array(0)), 0)
  assert.equal(rmsOf(null), 0)
})

test('逐帧瞬时：响度变化时口型立刻跟着变，不做累计平均', () => {
  const levels = []
  // start() 自身就跑一帧，再续两帧 = 三帧
  let budget = 2
  const meter = createAgentAudioMeter({
    createContext: () =>
      fakeContext([0.3, 0.001, 0.05], { contexts: 0, contextResumes: 0, sources: 0, frames: 0, closed: 0 }),
    onLevel: (level) => levels.push(level),
    requestFrame: (cb) => {
      if (budget > 0) {
        budget -= 1
        cb(0)
      }
      return 1
    },
  })
  meter.start({ id: 'stream' })

  // 三帧分别是大声、底噪、中等：若是累计平均，第二帧不会掉回 0
  assert.equal(levels.length, 3)
  assert.equal(levels[0], 1, '0.3×4 超过 1，必须封顶')
  assert.equal(levels[1], 0, '底噪帧要归零')
  assert.ok(Math.abs(levels[2] - 0.2) < 1e-6, `第三帧应约等于 0.2，实际 ${levels[2]}`)
})

test('AudioContext 只建一次：第二次订阅复用同一个 context 并重新 resume', () => {
  const { meter, state } = setup([0.1, 0.1])
  meter.start({ id: 'first' })
  meter.stop()
  meter.start({ id: 'second' })
  assert.equal(state.contexts, 1, '两次订阅只能有一个 AudioContext')
  assert.equal(state.contextResumes, 2, '每次订阅都要 resume（浏览器可能自动挂起）')
  assert.equal(state.sources, 2, '每条轨要各自的 source 节点')
  meter.stop()
})

test('stop 归零并停止产帧；dispose 才关 context', async () => {
  const { meter, levels, state } = setup([0.2, 0.2, 0.2], { ticks: 2 })
  meter.start({ id: 'stream' })
  const before = levels.length
  meter.stop()
  assert.equal(meter.level, 0)
  assert.equal(levels.at(-1), 0, 'stop 要回调一次 0，否则 UI 会停在最后一帧的开度')
  assert.equal(state.closed, 0, 'stop 不关 context')

  meter.start({ id: 'again' })
  assert.equal(levels.length > before, true)
  await meter.dispose()
  assert.equal(state.closed, 1, 'dispose 才真正释放 AudioContext')
})
