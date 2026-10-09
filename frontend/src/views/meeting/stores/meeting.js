/**
 * 会议共享状态：会议信息、成员、消息、共享与连接状态。
 *
 * 老师控制台与学生会议室两个页面共用同一份状态结构（不同页面只挂不同组件），
 * 实时事件统一由 `applyEvent` 落到这里，避免两个页面各写一套事件分支。
 *
 * 成员分两份，互不覆盖（这是本 store 最重要的一条边界，理由见 `utils/attendees.js`）：
 * - `roomAttendees`：LiveKit 房间事件得出的**在线成员**，是"在线"的唯一真相源
 * - `joinLedger`：REST `detail` / `state` 返回的**累计入会台账**，只在房间不可用时降级展示
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { pickDisplayAttendees } from '@/views/meeting/utils/attendees'

export const useMeetingStore = defineStore('meeting', () => {
  /** 会议信息：{ id, meetingNo, title, status, startTime, endTime, attendeeCount } */
  const meeting = ref(null)
  /** 在线成员（来自 LiveKit 房间事件） */
  const roomAttendees = ref([])
  /** 累计入会台账（来自 REST，含已离开与崩溃残留的行） */
  const joinLedger = ref([])
  /** 消息列表（按时间正序） */
  const messages = ref([])
  /** 是否正在共享屏幕 */
  const sharing = ref(false)
  /** 连接状态：idle / connecting / connected / reconnecting / disconnected */
  const connection = ref('idle')
  /** 会议是否已结束（结束提示由页面渲染） */
  const ended = ref(false)

  /** 名单区实际要展示的那一份，以及是否处于降级态 */
  const display = computed(() =>
    pickDisplayAttendees({
      roomAttendees: roomAttendees.value,
      joinLedger: joinLedger.value,
      connection: connection.value,
    }),
  )
  const displayAttendees = computed(() => display.value.attendees)
  const degraded = computed(() => display.value.degraded)
  /** 在线人数；降级时为 null（没有真相源时不编一个数字出来） */
  const onlineCount = computed(() => display.value.onlineCount)

  function setConnection(status) {
    connection.value = status
  }

  function setMeeting(info) {
    meeting.value = info ?? null
  }

  /** REST 返回的是台账，不是在线名单 */
  function setJoinLedger(list) {
    joinLedger.value = Array.isArray(list) ? list : []
  }

  function setRoomAttendees(list) {
    roomAttendees.value = Array.isArray(list) ? list : []
  }

  function setMessages(list) {
    messages.value = Array.isArray(list) ? list : []
  }

  function appendMessage(message) {
    if (message?.id != null && messages.value.some((item) => item.id === message.id)) return
    messages.value.push(message)
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
    joinLedger.value = []
    messages.value = []
    sharing.value = false
    connection.value = 'idle'
    ended.value = false
  }

  return {
    meeting,
    roomAttendees,
    joinLedger,
    messages,
    sharing,
    connection,
    ended,
    displayAttendees,
    degraded,
    onlineCount,
    setConnection,
    setMeeting,
    setJoinLedger,
    setRoomAttendees,
    setMessages,
    appendMessage,
    applyEvent,
    reset,
  }
})
