/**
 * 会议房间客户端（LiveKit）。
 *
 * 老师控制台与学生会议室共用这一个组合式函数，差异只在令牌：后端给老师签发**可发布**令牌
 * （能共享屏幕），给学生签发**只读**令牌（只能订阅画面），权限由 LiveKit 服务端强制。
 *
 * 职责边界：
 * - 屏幕共享：老师 `setScreenShareEnabled(true, { audio: true })`（内部走 `getDisplayMedia`）；
 *   学生订阅到共享轨后画面 attach 到 `<video>`、**共享里的声音** attach 到隐藏的 `<audio>`
 * - 聊天：走 DataChannel（RELIABLE，保证送达与顺序），**LiveKit 不落库**，所以发送方额外调一次
 *   `POST /meeting/message` 写历史（失败只影响历史，不影响实时）
 * - 成员与在线数：以房间内的 participant 事件为准（`lecture_meeting_attendee` 只是累计入会台账，
 *   没有 online 列，也不得有代码把它的行当成在线——见 docs/adr/0002 与 `utils/attendees.js`）
 * - 讲稿播报的声音：**走屏幕共享的音轨**。它由放映页自己播（那一页正是被共享的一屏），
 *   本钩子既不需要「数字人参与者」，也不经手任何音频解码（见 docs/16 §5.7）
 *
 * 对外事件名沿用旧自建 WebSocket 通道的那一套（`chat.message` / `attendee.joined` / `attendee.left` /
 * `screen.start` / `screen.stop`），由 `stores/meeting.js` 的 `applyEvent` 落到 `roomAttendees`。
 */
import { ref, shallowRef } from 'vue'
import { Room, RoomEvent, Track } from 'livekit-client'
import { sendMeetingMessage } from '@/api/meeting'
import { fromRoomParticipants, roleOfParticipant } from '@/views/meeting/utils/attendees'

/** 角色：学生（聊天回显里拿不到角色时的兜底） */
const ROLE_STUDENT = 2

/**
 * 创建房间客户端。
 *
 * @param {object} options
 * @param {() => HTMLVideoElement|undefined} options.getVideoElement 远端画面元素（学生端用）
 * @param {() => HTMLVideoElement|undefined} [options.getLocalPreviewElement] 本地预览元素（老师端用）
 * @param {(payload: object) => void} [options.onEvent] 归一化事件回调（喂给 meeting store）
 * @param {(status: string) => void} [options.onConnectionChange] 连接状态变化
 * @param {boolean} [options.omitHost=false] 名单里去掉老师自己（控制台专用：老师是控制台不是参会席位）
 */
export function useLiveKitRoom({ getVideoElement, getLocalPreviewElement, onEvent, onConnectionChange, omitHost = false } = {}) {
  /** LiveKit 的 Room 实例（不是响应式对象，用 shallowRef 避免被深度代理） */
  const room = shallowRef(null)
  /** 本端是否正在共享屏幕（浏览器共享栏点「停止」也会回到 false） */
  const sharing = ref(false)
  /**
   * 音频播放是否被浏览器自动播放策略拦下（iOS Safari 几乎必拦，桌面 Chrome 偶发）。
   * 由 audioPlaybackChanged 事件镜像 canPlaybackAudio 得出；true 时 UI 应给出
   * 「点此开启声音」入口——startAudio() 必须在用户手势里调用才生效。
   */
  const audioBlocked = ref(false)
  /**
   * 隐藏播放元素：远端的音频轨必须 attach 到媒体元素才会真的出声，
   * 只拿 `mediaStream` 去接 AnalyserNode 的话浏览器既不播、也拿不到有效帧。
   *
   * <p>学生端用它放**屏幕共享里的声音**；老师本机收不到自己发布的轨，
   * 那一路声音由放映页自己播（它就是被共享的那一屏）。
   */
  let screenAudioEl = null
  function ensureScreenAudioElement() {
    if (screenAudioEl) return screenAudioEl
    screenAudioEl = document.createElement('audio')
    screenAudioEl.autoplay = true
    screenAudioEl.setAttribute('data-role', 'screen-share-audio')
    screenAudioEl.style.display = 'none'
    document.body.appendChild(screenAudioEl)
    return screenAudioEl
  }

  /**
   * 当前房间成员：本端 + 所有远端（顺序稳定，便于列表渲染）。
   *
   * 映射逻辑放在 `utils/attendees.js`，因为「房间成员 vs 入会台账」的裁决必须能被单测覆盖。
   */
  function attendeeList() {
    if (!room.value) return []
    return fromRoomParticipants(
      [room.value.localParticipant, ...room.value.remoteParticipants.values()],
      { omitHost },
    )
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
    instance.on(RoomEvent.ParticipantDisconnected, () => {
      onEvent?.({ type: 'attendee.left', attendees: attendeeList() })
    })
    instance.on(RoomEvent.ParticipantMetadataChanged, () =>
      onEvent?.({ type: 'attendee.joined', attendees: attendeeList() }),
    )
    // 浏览器自动播放策略：声音被拦下时 UI 需要一个手势入口来解锁（iOS Safari 必然走到这）
    instance.on(RoomEvent.AudioPlaybackStatusChanged, () => {
      audioBlocked.value = instance ? !instance.canPlaybackAudio : false
    })

    // 远端轨：画面贴到 `<video>`；共享里的声音贴到隐藏的 `<audio>`（老师本机收不到自己的轨，那一份由放映页自己播）
    instance.on(RoomEvent.TrackSubscribed, (track, publication) => {
      if (publication.source === Track.Source.ScreenShareAudio && track.kind === Track.Kind.Audio) {
        const audioEl = ensureScreenAudioElement()
        track.attach(audioEl)
        // autoplay 属性在 iOS 上不算手势；能播就播，播不了的交给 audioBlocked 提示条
        audioEl.play?.().catch(() => {})
        return
      }
      if (publication.source !== Track.Source.ScreenShare || track.kind !== Track.Kind.Video) return
      const video = getVideoElement?.()
      if (video) {
        track.attach(video)
        video.play?.().catch(() => {})
      }
      onEvent?.({ type: 'screen.start' })
    })
    instance.on(RoomEvent.TrackUnsubscribed, (track, publication) => {
      if (publication.source === Track.Source.ScreenShareAudio && track.kind === Track.Kind.Audio) {
        track.detach(ensureScreenAudioElement())
        return
      }
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
        role: roleOfParticipant(participant),
        content: body.content,
        kind: 1,
        createTime: new Date().toISOString(),
        // 收到的一定是别人的：DataChannel 不会把数据回发给发送端自己
        self: false,
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
          // 本端回显：身份由此确定，不需要下游再猜
          self: true,
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
   * <p>共享目标固定为「PPT 放映页」（老师创建会议时进入放映的那个 PPT 标签页）：
   * - `selfBrowserSurface: 'exclude'` 把**当前标签页（控制台）**从候选列表里去掉，避免老师误共享控制台
   * - `surfaceSwitching: 'include'` 让浏览器共享栏留一个「切换共享内容」的退路（选错了能换）
   * - `audio: true` 连**这一页的声音**一起共享：讲稿播报是在放映页里播出来的，
   *   这是学生能听到它的唯一通路（共享框里的音频勾选被取消时，学生只看到画面，得重新共享一次）
   *
   * @param {object} [options] 额外的 DisplayMediaStreamOptions（会被合并覆盖）
   */
  async function startScreenShare(options = {}) {
    if (!room.value) throw new Error('房间未连接')
    await room.value.localParticipant.setScreenShareEnabled(true, {
      audio: true,
      selfBrowserSurface: 'exclude',
      surfaceSwitching: 'include',
      ...options,
    })
  }

  /** 老师端：停止共享 */
  async function stopScreenShare() {
    await room.value?.localParticipant.setScreenShareEnabled(false)
  }

  /**
   * 解锁远端音频播放（浏览器自动播放策略被触发后的恢复入口）。
   *
   * <p>必须在**用户手势**（点击/触摸）的调用栈里 await 它，否则 iOS Safari 仍然拒绝。
   * 成功与否以 audioPlaybackChanged 为准：解锁后 audioBlocked 自动回到 false。
   */
  async function startAudio() {
    if (!room.value) return false
    try {
      await room.value.startAudio()
      return true
    } catch {
      return false
    }
  }

  /** 断开并清理（幂等） */
  async function disconnect() {
    const instance = room.value
    room.value = null
    sharing.value = false
    audioBlocked.value = false
    screenAudioEl?.remove()
    screenAudioEl = null
    if (!instance) return
    instance.removeAllListeners()
    await instance.disconnect().catch(() => {})
  }

  return {
    room,
    sharing,
    audioBlocked,
    connect,
    disconnect,
    sendChat,
    startAudio,
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
