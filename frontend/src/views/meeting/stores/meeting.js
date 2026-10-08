/**
 * 会议共享状态：会议信息、成员、消息、共享与连接状态。
 *
 * 老师控制台与学生会议室两个页面共用同一份状态结构（不同页面只挂不同组件），
 * 实时事件统一由 `applyEvent` 落到这里，避免两个页面各写一套事件分支。
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export const useMeetingStore = defineStore('meeting', () => {
  /** 会议信息：{ id, meetingNo, title, status, startTime, endTime, attendeeCount, onlineCount } */
  const meeting = ref(null)
  /** 参会人列表 */
  const attendees = ref([])
  /** 消息列表（按时间正序） */
  const messages = ref([])
  /** 是否正在共享屏幕 */
  const sharing = ref(false)
  /** 连接状态：idle / connecting / open / closed */
  const connection = ref('idle')
  /** 会议是否已结束（结束提示由页面渲染） */
  const ended = ref(false)

  const onlineCount = computed(() => attendees.value.filter((item) => item.online).length)

  function setConnection(status) {
    connection.value = status
  }

  function setMeeting(info) {
    meeting.value = info ?? null
  }

  function setAttendees(list) {
    attendees.value = Array.isArray(list) ? list : []
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
        setAttendees(payload.attendees)
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
    attendees.value = []
    messages.value = []
    sharing.value = false
    connection.value = 'idle'
    ended.value = false
  }

  return {
    meeting,
    attendees,
    messages,
    sharing,
    connection,
    ended,
    onlineCount,
    setConnection,
    setMeeting,
    setAttendees,
    setMessages,
    appendMessage,
    applyEvent,
    reset,
  }
})
