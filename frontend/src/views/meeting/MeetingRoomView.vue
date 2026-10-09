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
        <el-tag v-if="!store.degraded" size="small" effect="plain" type="info">{{ store.onlineCount }} 人在线</el-tag>
        <!-- 房间连不上时只剩台账，而台账行数是「尚未显式离开」，不能当成人数展示出去：只说不可用 -->
        <el-tag v-else size="small" effect="plain" type="warning">实时连接不可用</el-tag>
        <el-tag size="small" effect="plain" :type="connectionTagType">{{ connectionText }}</el-tag>
      </div>
    </header>

    <!-- 状态同步失败不能静默：否则表现为「聊天没更新 / 会议结束没提示」 -->
    <el-alert
      v-if="stateError"
      type="warning"
      :closable="false"
      show-icon
      title="会议状态同步失败"
      :description="`${stateError}；聊天仍可继续，但「会议已结束」的兜底检测会失效，刷新页面可重试。`"
    />

    <main class="room-body">
      <div class="room-body__stage">
        <ScreenStage ref="stageRef" :sharing="store.sharing" :hint="stageHint" />
      </div>

      <aside class="room-body__side">
        <AttendeeList
          :attendees="store.displayAttendees"
          :online-count="store.onlineCount"
          :degraded="store.degraded"
        />
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
import { getMeetingState, joinMeeting, leaveMeeting } from '@/api/meeting'
import AttendeeList from './components/AttendeeList.vue'
import ChatPanel from './components/ChatPanel.vue'
import NicknameDialog from './components/NicknameDialog.vue'
import ScreenStage from './components/ScreenStage.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom, describeChatSend } from './hooks/useLiveKitRoom'

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
/** 状态同步失败的原因：不能静默（它是聊天历史铺底与「会议已结束」兜底的来源） */
const stateError = ref('')
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
    store.setJoinLedger(result.attendees)
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
 * @param {{ seed?: boolean }} [options] seed=true 表示首次进入 / 刷新恢复，用库里的成员与消息铺底。
 * @returns {Promise<object|null>} 状态；失败返回 null（原因记进 stateError）
 */
async function refreshState({ seed = false } = {}) {
  const session = readSession()
  if (!session) return null
  if (session.nickname) selfNickname.value = session.nickname
  if (session.attendeeId) selfId.value = session.attendeeId
  if (seed) loading.value = true
  try {
    const state = await getMeetingState(meetingNo, session.attendeeToken)
    store.setMeeting(state.meeting)
    // 铺底时机：首次进入 / 刷新恢复，或者**实时通道不在线**（此时轮询降级为聊天通道）。
    // 通道在线时绝不覆盖 —— 每 10s 用库里的数据整体覆盖一次，会把刚收到的实时消息
    // 与以 LiveKit 为准的成员状态打回旧值，页面表现就是「聊天不更新」。
    // 轮询同时不碰 loading，否则聊天面板每 10s 闪一次骨架。
    if (seed || store.connection !== 'connected') {
      store.setJoinLedger(state.attendees)
      store.setMessages(state.messages)
    }
    stateError.value = ''
    if (state.meeting?.status === 2) markEnded()
    return state
  } catch (error) {
    // 以前这里静默返回 null：轮询若一直失败（凭据失效 / 会议被删 / 后端异常），
    // 页面上只会表现为「什么都不再更新」，查不到原因，因此这里把原因留下来展示
    stateError.value = error?.message ?? String(error)
    return null
  } finally {
    if (seed) loading.value = false
  }
}

function markEnded() {
  store.ended = true
  stopStatePoll()
}

function startStatePoll() {
  stopStatePoll()
  // 包一层：轮询只做「会议是否已结束」的兜底，不带 seed
  statePollTimer = window.setInterval(() => refreshState(), STATE_POLL_INTERVAL_MS)
}

function stopStatePoll() {
  if (statePollTimer) window.clearInterval(statePollTimer)
  statePollTimer = null
}

/** 发言：DataChannel 实时广播 + 后端落库（学生用参会凭据） */
async function handleSend(content) {
  const session = readSession()
  const result = await room.sendChat(content, {
    meetingNo,
    attendeeToken: session?.attendeeToken,
    self: { attendeeId: selfId.value, nickname: selfNickname.value || '我', role: 2 },
  })
  const tip = describeChatSend(result)
  if (tip) ElMessage.warning(tip)
}

async function restore() {
  if (!readSession()) {
    dialogVisible.value = true
    return
  }
  // 刷新恢复：这一次要铺底（消息与成员来自库），此后的轮询只兜底会议结束
  const state = await refreshState({ seed: true })
  if (!state || store.ended) return
  await connectRoom(state.realtime)
  startStatePoll()
}

onMounted(restore)

onBeforeUnmount(() => {
  stopStatePoll()
  // 路由内离开会议室 = 显式离会，给台账补一笔 leave_time。
  // 只在这一条路径发：刷新走的是 pagehide，而刷新后 sessionStorage 里的凭据会继续用、
  // 不会重新 join —— 若那时也发 leave，台账会记下一条「已离开但其实还在」的假事实，
  // 比留着不动更糟。关标签页 / 崩溃留下的「尚未显式离开」才是 CONTEXT.md 认定的口径。
  const session = readSession()
  if (session?.attendeeToken) leaveMeeting(meetingNo, session.attendeeToken)
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
