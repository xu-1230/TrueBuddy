/**
 * 会议共享状态：会议信息、在线成员、消息、共享与连接状态。
 *
 * 老师控制台与学生会议室两个页面共用同一份状态结构（不同页面只挂不同组件），
 * 实时事件统一由 `applyEvent` 落到这里，避免两个页面各写一套事件分支。
 *
 * 成员只有一份：`roomAttendees` = LiveKit 房间事件得出的在线成员，是「在线」的唯一真相源
 * （docs/adr/0002）。REST `detail` / `state` 返回的 `attendees` 是累计入会台账，
 * 前端**不消费**它 —— 它含「尚未显式离开」的崩溃残留，一旦被当作名单就是误导。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { selectMemberPanel } from '@/views/meeting/utils/attendees'
import { isOwnMessage } from '@/views/meeting/utils/chat'

export const useMeetingStore = defineStore('meeting', () => {
  /** 会议信息：{ id, meetingNo, title, status, startTime, endTime, attendeeCount } */
  const meeting = ref(null)
  /** 在线成员（来自 LiveKit 房间事件） */
  const roomAttendees = ref([])
  /** 消息列表（按时间正序） */
  const messages = ref([])
  /** 是否正在共享屏幕 */
  const sharing = ref(false)
  /** 连接状态：idle / connecting / connected / reconnecting / disconnected */
  const connection = ref('idle')
  /** 会议是否已结束（结束提示由页面渲染） */
  const ended = ref(false)
  /**
   * 当前端上身份：判定「这条消息是不是我发的」的唯一依据。
   * 由页面在入会 / 初始化时注入，避免展示层拿 `attendeeId === selfId` 猜
   * （老师发言在库里 attendeeId 恒为 null，比对会碰巧成立，见 utils/chat.js）。
   */
  const selfIdentity = ref({ selfId: null, isHost: false })

  /** 房间是否已连上 —— 只有连上了才持有「在线」的真相 */
  const connected = computed(() => connection.value === 'connected')
  /** 名单区该展示什么（降级时为空名单 + 不给人数） */
  const memberPanel = computed(() =>
    selectMemberPanel({ attendees: roomAttendees.value, connected: connected.value }),
  )
  const displayAttendees = computed(() => memberPanel.value.attendees)
  const degraded = computed(() => memberPanel.value.degraded)
  /** 在线人数；降级时为 null（没有真相源就不报数） */
  const onlineCount = computed(() => memberPanel.value.onlineCount)

  function setConnection(status) {
    connection.value = status
  }

  function setMeeting(info) {
    meeting.value = info ?? null
  }

  function setRoomAttendees(list) {
    roomAttendees.value = Array.isArray(list) ? list : []
  }

  function setSelf(identity) {
    selfIdentity.value = {
      selfId: identity?.selfId ?? null,
      isHost: identity?.isHost === true,
    }
  }

  /** 落库/回显进来的消息统一补上 self 标记，展示层只管渲染 */
  function decorate(message) {
    return { ...message, self: isOwnMessage(message, selfIdentity.value) }
  }

  function setMessages(list) {
    messages.value = (Array.isArray(list) ? list : []).map(decorate)
  }

  function appendMessage(message) {
    if (message?.id != null && messages.value.some((item) => item.id === message.id)) return
    messages.value.push(decorate(message))
  }

  /**
   * 应用一条服务端事件。
   *
   * @param {object} payload 事件体（`type` 区分）
   */
  function applyEvent(payload) {
    switch (payload?.type) {
      case 'attendee.joined':
      case 'attendee.left':
        setRoomAttendees(payload.attendees)
        break
      case 'chat.message':
        appendMessage({
          id: payload.id,
          attendeeId: payload.attendeeId,
          nickname: payload.nickname,
          role: payload.role,
          content: payload.content,
          kind: payload.kind,
          createTime: payload.createTime,
          self: payload.self === true,
        })
        break
      case 'screen.start':
        sharing.value = true
        break
      case 'screen.stop':
        sharing.value = false
        break
      case 'meeting.closed':
        ended.value = true
        sharing.value = false
        break
      default:
        break
    }
  }

  /** 重置为初始态（离开页面时调用，避免上一次会议的数据串场） */
  function reset() {
    meeting.value = null
    roomAttendees.value = []
    messages.value = []
    sharing.value = false
    connection.value = 'idle'
    ended.value = false
    selfIdentity.value = { selfId: null, isHost: false }
  }

  return {
    meeting,
    roomAttendees,
    messages,
    sharing,
    connection,
    ended,
    connected,
    displayAttendees,
    degraded,
    onlineCount,
    setConnection,
    setMeeting,
    setRoomAttendees,
    setSelf,
    setMessages,
    appendMessage,
    applyEvent,
    reset,
  }
})
