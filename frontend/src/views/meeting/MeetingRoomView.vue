<template>
  <div class="meeting-room">
    <header class="room-bar">
      <div class="room-bar__info">
        <p class="room-bar__title">{{ store.meeting?.title || '模拟会议' }}</p>
        <p class="room-bar__sub">
          会议号 <strong>{{ meetingNo }}</strong>
          <span v-if="store.ended"> · 已结束</span>
        </p>
      </div>

      <div class="room-bar__actions">
        <el-tag size="small" effect="plain" type="info">{{ store.onlineCount }} 人在线</el-tag>
        <el-tag size="small" effect="plain" :type="connectionTagType">{{ connectionText }}</el-tag>
      </div>
    </header>

    <main class="room-body">
      <div class="room-body__stage">
        <ScreenStage ref="stageRef" :sharing="store.sharing" :hint="stageHint" />
      </div>

      <aside class="room-body__side">
        <AttendeeList :attendees="store.attendees" :online-count="store.onlineCount" />
        <el-divider class="room-body__divider" />
        <ChatPanel
          :messages="store.messages"
          :self-id="selfId"
          :disabled="store.ended"
          :loading="loading"
          @send="handleSend"
        />
      </aside>
    </main>

    <div v-if="store.ended" class="room-ended">
      <el-result icon="info" title="会议已结束" sub-title="感谢参与，可以关闭本页面了" />
    </div>

    <NicknameDialog
      v-model="dialogVisible"
      :meeting-no="meetingNo"
      :title="store.meeting?.title"
      :loading="joining"
      @submit="handleJoin"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMeetingState, joinMeeting } from '@/api/meeting'
import AttendeeList from './components/AttendeeList.vue'
import ChatPanel from './components/ChatPanel.vue'
import NicknameDialog from './components/NicknameDialog.vue'
import ScreenStage from './components/ScreenStage.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom } from './hooks/useLiveKitRoom'

/** 参会会话存 sessionStorage：刷新页面后免二次输入名字，并用于重新取令牌 */
const STORAGE_PREFIX = 'tb-meeting-attendee-'
/** 会议状态轮询间隔：实时能力已交给 LiveKit，这里只兜底「会议已结束」 */
const STATE_POLL_INTERVAL_MS = 10000

const route = useRoute()
const store = useMeetingStore()
const stageRef = ref(null)
const dialogVisible = ref(false)
const joining = ref(false)
const loading = ref(false)

const meetingNo = String(route.params.meetingNo ?? '')
const selfId = ref(null)
const selfNickname = ref('')
let statePollTimer = null

const room = useLiveKitRoom({
  getVideoElement: () => stageRef.value?.videoRef,
  onEvent: (payload) => store.applyEvent(payload),
  onConnectionChange: (status) => store.setConnection(status),
})

const connectionText = computed(() => {
  const map = {
    idle: '未连接',
    connecting: '连接中',
    connected: '已连接',
    reconnecting: '重连中',
    disconnected: '连接已断开',
  }
  return map[store.connection] ?? '未知'
})

const connectionTagType = computed(() => {
  if (store.connection === 'connected') return 'success'
  if (store.connection === 'connecting' || store.connection === 'reconnecting') return 'warning'
  return 'danger'
})

const stageHint = computed(() =>
  store.connection === 'connected' ? '等待主持人开始共享' : '正在连接会议…',
)

function sessionKey() {
  return `${STORAGE_PREFIX}${meetingNo}`
}

/** 读取参会会话：{ attendeeToken, attendeeId, nickname }；兼容早期只存 token 字符串的格式 */
function readSession() {
  const raw = window.sessionStorage.getItem(sessionKey())
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed?.attendeeToken ? parsed : null
  } catch {
    return { attendeeToken: raw, attendeeId: null, nickname: '' }
  }
}

function writeSession(session) {
  window.sessionStorage.setItem(sessionKey(), JSON.stringify(session))
}

async function handleJoin(nickname) {
  joining.value = true
  try {
    const result = await joinMeeting(meetingNo, nickname)
    writeSession({ attendeeToken: result.attendeeToken, attendeeId: result.attendeeId, nickname })
    selfId.value = result.attendeeId
    selfNickname.value = nickname
    store.setMeeting(result.meeting)
    store.setAttendees(result.attendees)
    store.setMessages(result.messages)
    dialogVisible.value = false
    await connectRoom(result.realtime)
    startStatePoll()
  } catch (error) {
    // 会议不存在 / 已结束 / 人数已满 / 实时通道未配置，错误码由全局拦截器抛出
    ElMessage.error(error?.message || '入会失败')
    dialogVisible.value = true
  } finally {
    joining.value = false
  }
}

/** 连接 LiveKit 房间：学生的令牌只能订阅画面与发 DataChannel（不可发布） */
async function connectRoom(realtime) {
  if (!realtime?.livekitUrl || !realtime?.livekitToken) {
    ElMessage.error('实时通道未就绪：请确认 LiveKit 已启动，且后端已配置 LIVEKIT_API_KEY / LIVEKIT_API_SECRET')
    return
  }
  try {
    await room.connect(realtime)
  } catch (error) {
    ElMessage.error(`实时通道连接失败：${error?.message ?? error}`)
  }
}

/**
 * 拉会议状态：刷新、重连与轮询共用；返回体里带**新的访问令牌**，因此刷新后仍能连上房间。
 *
 * @returns {Promise<object|null>} 状态；失败返回 null
 */
async function refreshState() {
  const session = readSession()
  if (!session) return null
  if (session.nickname) selfNickname.value = session.nickname
  if (session.attendeeId) selfId.value = session.attendeeId
  loading.value = true
  try {
    const state = await getMeetingState(meetingNo, session.attendeeToken)
    store.setMeeting(state.meeting)
    store.setAttendees(state.attendees)
    store.setMessages(state.messages)
    if (state.meeting?.status === 2) markEnded()
    return state
  } catch {
    // 凭据失效（多为会议已结束）：保留当前画面，等轮询给出结论
    return null
  } finally {
    loading.value = false
  }
}

function markEnded() {
  store.ended = true
  stopStatePoll()
}

function startStatePoll() {
  stopStatePoll()
  statePollTimer = window.setInterval(refreshState, STATE_POLL_INTERVAL_MS)
}

function stopStatePoll() {
  if (statePollTimer) window.clearInterval(statePollTimer)
  statePollTimer = null
}

/** 发言：DataChannel 实时广播 + 后端落库（学生用参会凭据） */
async function handleSend(content) {
  const session = readSession()
  const persisted = await room.sendChat(content, {
    meetingNo,
    attendeeToken: session?.attendeeToken,
    self: { attendeeId: selfId.value, nickname: selfNickname.value || '我', role: 2 },
  })
  if (!persisted) ElMessage.warning('消息已发出，但未写入历史（刷新后可能看不到）')
}

async function restore() {
  if (!readSession()) {
    dialogVisible.value = true
    return
  }
  const state = await refreshState()
  if (!state || store.ended) return
  await connectRoom(state.realtime)
  startStatePoll()
}

onMounted(restore)

onBeforeUnmount(() => {
  stopStatePoll()
  room.disconnect()
  store.reset()
})
</script>

<style scoped>
.meeting-room {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100vh;
  padding: 1rem;
  background: var(--tb-bg-page);
}

.room-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.75rem 1rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.room-bar__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.room-bar__sub {
  margin: 0.125rem 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.room-bar__sub strong {
  color: var(--tb-text-primary);
  letter-spacing: 0.08em;
}

.room-bar__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.room-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 1rem;
  min-height: 0;
}

.room-body__stage {
  min-width: 0;
}

.room-body__side {
  display: flex;
  flex-direction: column;
  padding: 1rem;
  min-height: 0;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.room-body__divider {
  margin: 0.75rem 0;
}

.room-ended {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in oklab, var(--tb-bg-page) 82%, transparent);
  backdrop-filter: blur(6px);
}

@media (max-width: 1080px) {
  .room-body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
