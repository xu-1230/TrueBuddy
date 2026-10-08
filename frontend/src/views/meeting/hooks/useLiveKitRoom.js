/**
 * 会议房间客户端（LiveKit）。
 *
 * 老师控制台与学生会议室共用这一个组合式函数，差异只在令牌：后端给老师签发**可发布**令牌
 * （能共享屏幕），给学生签发**只读**令牌（只能订阅画面），权限由 LiveKit 服务端强制。
 *
 * 职责边界：
 * - 屏幕共享：老师 `setScreenShareEnabled(true)`（内部走 `getDisplayMedia`）；学生订阅到共享轨后 attach 到 `<video>`
 * - 聊天：走 DataChannel（RELIABLE，保证送达与顺序），**LiveKit 不落库**，所以发送方额外调一次
 *   `POST /meeting/message` 写历史（失败只影响历史，不影响实时）
 * - 成员与在线数：以房间内的 participant 事件为准（DB 的 `online` 字段已退化为历史）
 *
 * 对外事件名与旧自建 WebSocket 通道**保持一致**（`chat.message` / `attendee.joined` / `attendee.left` /
 * `screen.start` / `screen.stop`），因此 `stores/meeting.js` 的 `applyEvent` 与两个展示组件都不用改。
 */
import { ref, shallowRef } from 'vue'
import { Room, RoomEvent, Track } from 'livekit-client'
import { sendMeetingMessage } from '@/api/meeting'

/** 角色：老师（与后端 role 口径一致） */
const ROLE_HOST = 1
/** 角色：学生 */
const ROLE_STUDENT = 2

/**
 * 创建房间客户端。
 *
 * @param {object} options
 * @param {() => HTMLVideoElement|undefined} options.getVideoElement 远端画面元素（学生端用）
 * @param {() => HTMLVideoElement|undefined} [options.getLocalPreviewElement] 本地预览元素（老师端用）
 * @param {(payload: object) => void} [options.onEvent] 归一化事件回调（喂给 meeting store）
 * @param {(status: string) => void} [options.onConnectionChange] 连接状态变化
 */
export function useLiveKitRoom({ getVideoElement, getLocalPreviewElement, onEvent, onConnectionChange } = {}) {
  /** LiveKit 的 Room 实例（不是响应式对象，用 shallowRef 避免被深度代理） */
  const room = shallowRef(null)
  /** 本端是否正在共享屏幕（浏览器共享栏点「停止」也会回到 false） */
  const sharing = ref(false)

  /** 从 participant 的 metadata 里取角色；metadata 缺失/非法时按学生处理 */
  function roleOf(participant) {
    try {
      const metadata = JSON.parse(participant?.metadata || '{}')
      return metadata.role === ROLE_HOST ? ROLE_HOST : ROLE_STUDENT
    } catch {
      return ROLE_STUDENT
    }
  }

  function attendeeOf(participant) {
    return {
      id: participant.identity,
      nickname: participant.name || participant.identity,
      role: roleOf(participant),
      online: true,
    }
  }

  /** 当前房间成员：本端 + 所有远端（顺序稳定，便于列表渲染） */
  function attendeeList() {
    if (!room.value) return []
    const remote = [...room.value.remoteParticipants.values()]
    return [room.value.localParticipant, ...remote].map(attendeeOf)
  }

  /**
   * 连接房间。
   *
   * @param {{ livekitUrl: string, livekitToken: string }} realtime 后端下发的接入信息
   */
  async function connect(realtime) {
    const { livekitUrl, livekitToken } = realtime ?? {}
    if (!livekitUrl || !livekitToken) throw new Error('缺少 LiveKit 接入信息')
    await disconnect()

    const instance = new Room({ adaptiveStream: true, dynacast: true })
    room.value = instance
    onConnectionChange?.('connecting')

    instance.on(RoomEvent.Connected, () => {
      onConnectionChange?.('connected')
      onEvent?.({ type: 'attendee.joined', attendees: attendeeList() })
    })
    instance.on(RoomEvent.Reconnecting, () => onConnectionChange?.('reconnecting'))
    instance.on(RoomEvent.Reconnected, () => onConnectionChange?.('connected'))
    instance.on(RoomEvent.Disconnected, () => onConnectionChange?.('disconnected'))
    instance.on(RoomEvent.ParticipantConnected, () =>
      onEvent?.({ type: 'attendee.joined', attendees: attendeeList() }),
    )
    instance.on(RoomEvent.ParticipantDisconnected, () =>
      onEvent?.({ type: 'attendee.left', attendees: attendeeList() }),
    )
    instance.on(RoomEvent.ParticipantMetadataChanged, () =>
      onEvent?.({ type: 'attendee.joined', attendees: attendeeList() }),
    )

    // 远端屏幕共享轨：订阅到就贴到画面上，取消订阅就摘掉
    instance.on(RoomEvent.TrackSubscribed, (track, publication) => {
      if (publication.source !== Track.Source.ScreenShare || track.kind !== Track.Kind.Video) return
      const video = getVideoElement?.()
      if (video) {
        track.attach(video)
        video.play?.().catch(() => {})
      }
      onEvent?.({ type: 'screen.start' })
    })
    instance.on(RoomEvent.TrackUnsubscribed, (track, publication) => {
      if (publication.source !== Track.Source.ScreenShare) return
      track.detach()
      onEvent?.({ type: 'screen.stop' })
    })

    // 本端共享轨：给自己的预览用（学生端不会有这两条事件）
    instance.on(RoomEvent.LocalTrackPublished, (publication) => {
      if (publication.source !== Track.Source.ScreenShare) return
      sharing.value = true
      const preview = getLocalPreviewElement?.()
      if (preview) {
        publication.videoTrack?.attach(preview)
        preview.play?.().catch(() => {})
      }
    })
    instance.on(RoomEvent.LocalTrackUnpublished, (publication) => {
      if (publication.source !== Track.Source.ScreenShare) return
      sharing.value = false
      getLocalPreviewElement?.()?.removeAttribute('src')
    })

    // 聊天：发送方不会收到自己的 DataChannel 回包，因此发送端本地先渲染
    instance.on(RoomEvent.DataReceived, (payload, participant) => {
      let body
      try {
        body = JSON.parse(new TextDecoder().decode(payload))
      } catch {
        return
      }
      if (body?.type !== 'chat' || typeof body.content !== 'string') return
      onEvent?.({
        type: 'chat.message',
        id: body.clientId,
        attendeeId: participant?.identity ?? null,
        nickname: participant?.name || participant?.identity || '匿名',
        role: roleOf(participant),
        content: body.content,
        kind: 1,
        createTime: new Date().toISOString(),
      })
    })

    try {
      await instance.connect(livekitUrl, livekitToken, { autoSubscribe: true })
    } catch (error) {
      onConnectionChange?.('disconnected')
      throw error
    }
  }

  /**
   * 发送聊天消息：DataChannel 广播 + 后端落库（双写）。
   *
   * <p>两条路各自记账、互不牵连。特别是**实时广播失败不再被吞掉**：以前房间没连上（或数据通道未就绪）
   * 时直接 `return false`，消息既没广播也没落库，而调用方只提示「未写入历史」——
   * 在页面上就表现为「聊天没有更新」这种查不出原因的现象。
   *
   * @param {string} content 正文
   * @param {{ meetingNo: string, attendeeToken?: string, hostToken?: string, self: object }} context 身份上下文
   * @returns {Promise<{ delivered: boolean, persisted: boolean }>} 实时是否广播成功、是否写入历史
   */
  async function sendChat(content, { meetingNo, attendeeToken, hostToken, self } = {}) {
    const text = (content ?? '').trim()
    if (!text) return { delivered: false, persisted: false }

    let delivered = false
    if (room.value) {
      try {
        const clientId = globalThis.crypto?.randomUUID?.() ?? `local-${Date.now()}`
        const payload = new TextEncoder().encode(JSON.stringify({ type: 'chat', clientId, content: text }))
        await room.value.localParticipant.publishData(payload, { reliable: true })
        delivered = true

        // DataChannel 不会把数据回给自己，发送端本地先渲染
        onEvent?.({
          type: 'chat.message',
          id: clientId,
          attendeeId: self?.attendeeId ?? null,
          nickname: self?.nickname || '我',
          role: self?.role ?? ROLE_STUDENT,
          content: text,
          kind: 1,
          createTime: new Date().toISOString(),
        })
      } catch (error) {
        // 让调用方据此如实提示；这里不能抛，否则连落库的机会都没了
        console.warn('[sendChat][实时广播失败，改为只写历史]', error)
      }
    }

    const body = { meetingNo, content: text, attendeeToken, hostToken }
    try {
      await sendMeetingMessage(body)
      return { delivered, persisted: true }
    } catch {
      // 重试一次：实时已经发出去了，历史缺一条是最需要补救的情况
      try {
        await sendMeetingMessage(body)
        return { delivered, persisted: true }
      } catch {
        return { delivered, persisted: false }
      }
    }
  }

  /**
   * 老师端：开始共享屏幕（内部走 getDisplayMedia；用户取消会抛错）。
   *
   * <p>共享目标固定为「建会时自动打开的那个放映页」：
   * - `selfBrowserSurface: 'exclude'` 把**当前标签页（控制台）**从候选列表里去掉，避免老师误共享控制台
   * - `surfaceSwitching: 'include'` 让浏览器共享栏留一个「切换共享内容」的退路（选错了能换）
   *
   * @param {object} [options] 额外的 DisplayMediaStreamOptions（会被合并覆盖）
   */
  async function startScreenShare(options = {}) {
    if (!room.value) throw new Error('房间未连接')
    await room.value.localParticipant.setScreenShareEnabled(true, {
      audio: false,
      selfBrowserSurface: 'exclude',
      surfaceSwitching: 'include',
      ...options,
    })
  }

  /** 老师端：停止共享 */
  async function stopScreenShare() {
    await room.value?.localParticipant.setScreenShareEnabled(false)
  }

  /** 断开并清理（幂等） */
  async function disconnect() {
    const instance = room.value
    room.value = null
    sharing.value = false
    if (!instance) return
    instance.removeAllListeners()
    await instance.disconnect().catch(() => {})
  }

  return {
    room,
    sharing,
    connect,
    disconnect,
    sendChat,
    startScreenShare,
    stopScreenShare,
  }
}

/**
 * 把 {@link useLiveKitRoom} 的 `sendChat` 结果翻成一句提示（两个页面共用，避免口径不一致）。
 *
 * @param {{ delivered: boolean, persisted: boolean }} result 发送结果
 * @returns {string} 需要提示时返回文案，一切正常时返回空串
 */
export function describeChatSend({ delivered, persisted } = {}) {
  if (delivered && persisted) return ''
  if (!delivered && !persisted) return '消息未发送：实时通道未连接，历史也写入失败'
  if (!delivered) return '实时通道未连接：消息只写入了历史，其他人刷新后才能看到'
  return '消息已广播，但未写入历史（刷新后可能看不到）'
}
