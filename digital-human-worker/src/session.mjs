/**
 * 与 LiveKit 房间的唯一接触点：一条常驻音频轨 + 把 PCM 帧喂进去。
 *
 * <p>本文件是 worker 里唯一 import rtc-node 的地方，因此它的行为只能靠真实环境验证
 * （spike 结论见 docs/17 §13）；路由与分帧逻辑都在可离线测试的模块里。
 */
import {
  AudioFrame,
  AudioSource,
  LocalAudioTrack,
  Room,
  RoomEvent,
  TrackPublishOptions,
  TrackSource,
  dispose,
} from '@livekit/rtc-node'

import { CHANNELS, FrameSlicer, SAMPLE_RATE, frameBytes } from './audio.mjs'

const FRAME_SIZE_BYTES = frameBytes(SAMPLE_RATE, CHANNELS)
const SAMPLES_PER_FRAME = FRAME_SIZE_BYTES / 2

/** Buffer 切片 → Int16Array：必须带 byteOffset，Node 的 Buffer 可能来自共享内存池 */
function toInt16(frame) {
  return new Int16Array(frame.buffer, frame.byteOffset, SAMPLES_PER_FRAME)
}

/** 一个房间一个数字人会话 */
export class AgentSession {
  constructor({ roomName, url, token, log = console }) {
    this.roomName = roomName
    this.url = url
    this.token = token
    this.log = log
    this.source = null
    this.room = null
    this.trackSid = null
  }

  get connected() {
    return this.room !== null
  }

  async start() {
    this.source = new AudioSource(SAMPLE_RATE, CHANNELS)
    this.room = new Room({ autoSubscribe: false, dynacast: false })
    this.room.on(RoomEvent.Disconnected, () => {
      this.log.warn?.(`[session][房间 ${this.roomName} 断开]`)
      this.room = null
    })
    await this.room.connect(this.url, this.token)

    const track = LocalAudioTrack.createAudioTrack('digital-human-audio', this.source)
    const options = new TrackPublishOptions()
    options.name = 'digital-human-audio'
    options.source = TrackSource.SOURCE_MICROPHONE
    options.simulcast = false
    options.dtx = false
    const publication = await this.room.localParticipant.publishTrack(track, options)
    this.trackSid = publication.sid
    this.log.info?.(`[session][数字人已进房 room=${this.roomName} sid=${this.trackSid}]`)
  }

  /** 喂一帧（20ms）；AudioSource 自带队列与背压，await 即可 */
  async feed(frame) {
    await this.source.captureFrame(new AudioFrame(toInt16(frame), SAMPLE_RATE, CHANNELS, SAMPLES_PER_FRAME))
  }

  /** 当前排队未播的时长（毫秒），用于日志与「是否播完」判断 */
  get queuedMs() {
    return this.source ? this.source.queuedDuration : 0
  }

  async waitForPlayout() {
    if (this.source) {
      await this.source.waitForPlayout()
    }
  }

  /** 打断：丢掉还没播出去的音频（合成侧的取消由后端负责，见 docs/17 §6） */
  interrupt() {
    this.source?.clearQueue()
  }

  async stop() {
    const { source, room } = this
    this.source = null
    this.room = null
    try {
      await source?.close()
    } finally {
      await room?.disconnect()
    }
  }
}

/**
 * 会话与播报任务的登记表。
 *
 * <p>{@code streamProvider} 是注入点：真实环境传请求体流，测试里传一个内存异步迭代器，
 * 这样「分帧 → 打断 → 收尾」这套状态机能离线测。
 */
export class SessionRegistry {
  constructor({ log = console } = {}) {
    this.log = log
    this.sessions = new Map()
    this.says = new Map()
  }

  list() {
    return [...this.sessions.keys()]
  }

  /** 同一房间重复调用视为幂等：已连接就只回原会话，不重复进房占席位 */
  async open({ room, url, token }) {
    const existing = this.sessions.get(room)
    if (existing?.connected) {
      return existing
    }
    if (existing) {
      await this.close(room)
    }
    const session = new AgentSession({ roomName: room, url, token, log: this.log })
    this.sessions.set(room, session)
    try {
      await session.start()
    } catch (error) {
      this.sessions.delete(room)
      await session.stop().catch(() => {})
      throw error
    }
    return session
  }

  async close(room) {
    const session = this.sessions.get(room)
    if (!session) {
      return false
    }
    this.sessions.delete(room)
    await session.stop()
    this.log.info?.(`[session][数字人离房 room=${room}]`)
    return true
  }

  async closeAll() {
    await Promise.all([...this.sessions.keys()].map((room) => this.close(room)))
  }

  /**
   * 收一条播报：边收流边分帧喂进会话；被 cancel 就停下并清空未播队列。
   *
   * @returns 播报任务的状态对象
   */
  async say({ room, requestId, iterable }) {
    const session = this.sessions.get(room)
    if (!session) {
      throw new Error(`会话不存在: ${room}`)
    }
    const entry = { room, requestId, state: 'receiving', frames: 0, cancelled: false }
    this.says.set(requestId, entry)

    const slicer = new FrameSlicer(FRAME_SIZE_BYTES)
    try {
      for await (const chunk of iterable) {
        if (entry.cancelled) {
          break
        }
        for (const frame of slicer.push(chunk)) {
          await session.feed(frame)
          entry.frames += 1
        }
      }
      if (!entry.cancelled) {
        const tail = slicer.flush()
        if (tail) {
          await session.feed(tail)
          entry.frames += 1
        }
        entry.state = 'playing'
        await session.waitForPlayout()
        entry.state = 'done'
      } else {
        entry.state = 'cancelled'
        session.interrupt()
      }
    } catch (error) {
      entry.state = 'failed'
      entry.error = error?.message ?? String(error)
      throw error
    } finally {
      this.log.info?.(`[say][requestId=${requestId} state=${entry.state} frames=${entry.frames}]`)
    }
    return entry
  }

  /** 幂等判定：同一 requestId 重复投递时不重播 */
  hasSay(requestId) {
    return this.says.has(requestId)
  }

  cancel(requestId) {
    const entry = this.says.get(requestId)
    if (!entry) {
      return false
    }
    entry.cancelled = true
    return true
  }

  state(requestId) {
    return this.says.get(requestId) ?? null
  }

  /** 释放 rtc 原生资源：不调用它进程不会退出（docs/17 §13.3 第 1 条实测） */
  async shutdown() {
    await this.closeAll()
    await dispose()
  }
}
