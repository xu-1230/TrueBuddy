/**
 * 讲稿播放器与口型的可测部分（Node 内置 `node:test`）。
 *
 * 锁三条来自实现与实机的约束：
 * ① 分片可能切在采样中间：留一个字节进位，否则每片都会丢掉/错位半个采样；
 * ② 口型用**逐帧瞬时响度**（累计平均在流停后仍会动），底噪与异常值必须归零；
 * ③ `stop()` 必须让声音立刻消失：已排期的音源全部掐掉、口型归零 —— 这是「停止演讲」的全部含义。
 * ④ `onDrained` 是「播完了」的双条件确认：流正常结束（`markStreamDone`）**且**已排期音频播完
 *    才触发一次——网络抖动掐断不是播完，`stop` 取消也不是播完（P1 自动推进的第一前置）。
 *
 * 运行：cd frontend && node --test tests/speech-player.test.js
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { createSpeechPlayer, decodeBase64, int16ToFloat32, mouthLevel, rmsOf } from '../src/views/ppt/utils/speech-player.js'

/** 小端 16bit：0x4000 = 16384 → 0.5 */
const chunkOf = (...samples) => {
  const bytes = new Uint8Array(samples.length * 2)
  const view = new DataView(bytes.buffer)
  samples.forEach((value, index) => view.setInt16(index * 2, value, true))
  return bytes
}

/** 假 AudioContext：记录每次排期的音源、交给播放器的采样数，以及每帧给出的响度 */
function fakeContext(state) {
  return {
    currentTime: 0,
    destination: { id: 'destination' },
    resume() {
      state.resumes += 1
      return Promise.resolve()
    },
    createGain: () => ({ connect() {} }),
    createAnalyser: () => ({
      fftSize: 0,
      getFloatTimeDomainData(buffer) {
        state.frames += 1
        buffer.fill(state.amplitude ?? 0)
      },
      connect() {},
    }),
    createBuffer: (channels, length, sampleRate) => ({
      duration: length / sampleRate,
      samples: length,
      copyToChannel() {},
    }),
    createBufferSource() {
      const source = {
        buffer: null,
        connect() {},
        start(at) {
          source.startedAt = at
          state.started.push(source)
        },
        stop() {
          source.stopped = true
        },
        onended: null,
      }
      state.sources.push(source)
      return source
    },
    close() {
      state.closed += 1
      return Promise.resolve()
    },
  }
}

function setup() {
  const state = { contexts: 0, resumes: 0, frames: 0, closed: 0, sources: [], started: [], amplitude: 0 }
  /** 待跑的 RAF 帧：真实浏览器里它是连续的，这里由用例逐帧驱动，断言才可预期 */
  const pending = []
  let context = null
  const levels = []
  /** onDrained 触发记录：双条件回调的用例断言用 */
  const drained = []
  const player = createSpeechPlayer({
    createContext: () => {
      state.contexts += 1
      context ??= fakeContext(state)
      return context
    },
    onLevel: (level) => levels.push(level),
    onDrained: () => drained.push(1),
    requestFrame: (cb) => {
      pending.push(cb)
      return pending.length
    },
  })
  return {
    player,
    levels,
    state,
    pending,
    drained,
    /** 跑下一帧，并指定这一帧测到的瞬时响度 */
    frame: (amplitude) => {
      state.amplitude = amplitude
      pending.shift()?.(0)
    },
  }
}

test('mouthLevel：门限以下、异常值都归零，正常区间线性且封顶 1', () => {
  assert.equal(mouthLevel(0), 0)
  assert.equal(mouthLevel(0.004), 0, '低于 0.005 的门限当底噪')
  assert.equal(mouthLevel(0.05), 0.2)
  assert.equal(mouthLevel(0.15), 0.6)
  assert.equal(mouthLevel(0.9), 1, '不能超出 Live2D 参数区间')
  for (const bad of [NaN, -1, undefined, null]) assert.equal(mouthLevel(bad), 0)
})

test('rmsOf：常数信号等于自身，全零与空输入为 0', () => {
  // Float32 存不下精确的 0.1 / 0.5，比较一律带容差
  const close = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-6, `${actual} ≠ ${expected}`)
  close(rmsOf(new Float32Array([0.5, 0.5, 0.5, 0.5])), 0.5)
  assert.equal(rmsOf(new Float32Array([0, 0, 0])), 0)
  close(rmsOf(new Float32Array([0.1, -0.1])), 0.1)
  assert.equal(rmsOf(new Float32Array(0)), 0)
  assert.equal(rmsOf(null), 0)
})

test('int16ToFloat32：按小端解码到 [-1,1]', () => {
  const floats = int16ToFloat32(chunkOf(16384, -32768, 32767))

  assert.equal(floats.length, 3)
  assert.ok(Math.abs(floats[0] - 0.5) < 1e-6, '0x4000 → 0.5')
  assert.equal(floats[1], -1, '-32768 是整型下限')
  assert.ok(floats[2] > 0.999 && floats[2] < 1, '32767 顶不到 1')
})

test('decodeBase64：chunk 载荷解回原始字节', () => {
  assert.deepEqual([...decodeBase64('AQID')], [1, 2, 3])
  assert.deepEqual([...decodeBase64('')], [])
})

test('分片切在采样中间：留一个字节进位，凑成整数个采样再播', () => {
  const { player, state } = setup()

  // 3 字节 = 1 个完整采样 + 半个；第二个分片补上那半个
  player.feed(new Uint8Array([1, 0, 2]))
  player.feed(new Uint8Array([0]))

  assert.deepEqual(state.started.map((source) => source.buffer.samples), [1, 1],
    '两片各自只该播出一个采样：半个采样不能单独成块')
  assert.equal(state.contexts, 1, 'AudioContext 整页只建一次并复用')
})

test('逐帧瞬时：响度变化时口型立刻跟着变，不做累计平均', () => {
  const { player, levels, frame } = setup()

  // feed 自身先跑一帧（此时音频还没排上，读到的是静音），随后才是真正有声的三帧
  player.feed(chunkOf(16384))
  assert.equal(levels[0], 0, '首帧在读排期之前的分析器，必然是静音')

  // 三帧分别是大声明、底噪、中等：若是累计平均，第二帧不会掉回 0
  frame(0.3)
  frame(0.001)
  frame(0.05)

  const [, loud, floor, medium] = levels
  assert.equal(loud, 1, '0.3×4 超过 1，必须封顶')
  assert.equal(floor, 0, '底噪帧要归零')
  // Float32 存不下精确的 0.05，比较带容差
  assert.ok(Math.abs(medium - 0.2) < 1e-6, `第三帧应约等于 0.2，实际 ${medium}`)
})

test('stop：掐掉已排期音源、口型归零、停掉取帧，并允许接着念下一段', () => {
  const { player, levels, state, pending, frame } = setup()

  player.feed(chunkOf(16384))
  frame(0.2)
  const first = state.started[0]
  const before = levels.length

  player.stop()

  assert.equal(first.stopped, true, '已排期的音源必须真的停下来')
  assert.equal(player.level, 0)
  assert.equal(levels.at(-1), 0, 'stop 要回调一次 0，否则嘴会停在最后一帧的开度')

  // 即使在途还有一个取帧回调（浏览器里会被 cancelAnimationFrame 掐掉），它也不许再改口型
  const leftover = pending.shift()
  leftover?.(0)
  assert.equal(levels.length, before + 1, 'stop 之后不该再产出新的开合度')

  // 停完还能继续念：排期时刻归零，下一段从头排
  player.feed(chunkOf(16384))
  assert.equal(state.sources.length, 2)
})

test('destroy：先静音再关掉 AudioContext', async () => {
  const { player, state, frame } = setup()

  player.feed(chunkOf(16384))
  frame(0.2)
  await player.destroy()

  assert.equal(state.sources[0].stopped, true)
  assert.equal(state.closed, 1, 'destroy 才真正释放 AudioContext')
})

// ===== onDrained：播完确认（双条件）=====

test('onDrained：流正常结束且音频播完才触发，且恰好一次', () => {
  const { player, state, drained } = setup()

  player.feed(chunkOf(16384))
  player.markStreamDone()
  assert.equal(drained.length, 0, '音频还在播：只满足「流结束」不算播完')

  state.sources[0].onended()
  assert.equal(drained.length, 1, '双条件同时满足：播完了')

  // 触发后 streamDone 已复位：重复的 onended 回调不许再触发
  state.sources[0].onended()
  assert.equal(drained.length, 1)
})

test('onDrained：只有音频播完、流没正常结束（网络抖动掐断）不触发', () => {
  const { player, state, drained } = setup()

  player.feed(chunkOf(16384))
  state.sources[0].onended()
  assert.equal(drained.length, 0, '缺「流正常结束」这一半：静音不等于播完')
})

test('onDrained：段间无缝排期下不误报——上一段播完时下一段已在路上', () => {
  const { player, state, drained } = setup()

  // 段1：流结束（音频仍在播）→ 段2 的 chunk 已开始 feed → 段1 播完
  player.feed(chunkOf(16384))
  player.markStreamDone()
  player.feed(chunkOf(16384))
  state.sources[0].onended()
  assert.equal(drained.length, 0, '段2 还在路：段1 播完不是整页播完')

  // 段2 流结束 + 播完：才轮到 drained
  player.markStreamDone()
  state.sources[1].onended()
  assert.equal(drained.length, 1)
})

test('onDrained：stop 之后不算播完（取消是取消，不是播完）', () => {
  const { player, state, drained } = setup()

  player.feed(chunkOf(16384))
  player.stop()
  assert.equal(state.sources[0].stopped, true)
  assert.equal(drained.length, 0, '被掐断的播报不许报「播完」')
})

test('onDrained：零音频的空流直接结束视为播完（空段兜底）', () => {
  const { player, drained } = setup()

  player.markStreamDone()
  assert.equal(drained.length, 1, '没有音频要播 + 流已结束 = 播完了（虽然什么都没播）')
})
