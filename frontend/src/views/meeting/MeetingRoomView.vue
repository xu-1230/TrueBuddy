<template>
  <div class="meeting-studio meeting-room">
    <StudioBackdrop />

    <div class="meeting-room__inner">
      <MeetingTopBar
        :title="store.meeting?.title || '模拟会议'"
        :meeting-no="meetingNo"
        sub-note="学生会议室"
        :icon="Monitor"
        :online-count="store.onlineCount"
        :connected="store.connected"
        :ended="store.ended"
        :connection="store.connection"
        :sharing="store.sharing"
        :start-time="store.meeting?.startTime || ''"
        :end-time="store.meeting?.endTime || ''"
      />

      <div v-if="stateError || digitalUnavailable" class="meeting-room__notices">
        <!-- 状态同步失败不能静默：否则表现为「聊天没更新 / 会议结束没提示」 -->
        <StatusNotice v-if="stateError" tone="warn" title="会议状态同步失败">
          {{ stateError }}；聊天仍可继续，但「会议已结束」的兜底检测会失效，刷新页面可重试。
        </StatusNotice>

        <!-- 开了数字人但语音合成没配好：明确说出来，不能表现为「老师没声音」 -->
        <StatusNotice v-if="digitalUnavailable" tone="warn" title="语音未配置">
          老师端的语音合成未配置（DashScope Key 为空），本场开会时可能没有讲解声音。
        </StatusNotice>
      </div>

      <main class="room-body" :data-panel="activePanel">
        <section class="room-body__stage">
          <ScreenStage
            ref="stageRef"
            :sharing="store.sharing"
            :hint="stageHint"
            placeholder="等待主持人共享画面"
          />
          <!-- 数字人只存在于老师共享的画面里，学生端不单独渲染形象 -->
        </section>

        <aside class="room-body__side mt-rail">
          <AttendeeList :attendees="store.displayAttendees" :degraded="store.degraded" />
          <ChatPanel
            class="room-body__chat"
            :messages="store.messages"
            :disabled="store.ended"
            :loading="loading"
            @send="handleSend"
          />
        </aside>
      </main>

      <!-- 小屏只留一块面板：画面与「名单+聊天」切换 -->
      <nav class="room-switcher">
        <el-segmented v-model="activePanel" :options="panelOptions" aria-label="切换画面与成员聊天" />
      </nav>
    </div>

    <Transition name="curtain">
      <div v-if="store.ended" class="room-ended" role="status" aria-live="assertive">
        <span class="room-ended__wash" aria-hidden="true" />
        <div class="room-ended__inner">
          <p class="mt-eyebrow">Off air / 会议结束</p>
          <h2 class="room-ended__title">ENDED</h2>
          <p class="room-ended__sub mt-mono">会议号 {{ formattedMeetingNo }}</p>
          <p class="room-ended__hint">感谢参与，可以关闭本页面了。</p>
          <span class="room-ended__rule" aria-hidden="true" />
        </div>
      </div>
    </Transition>

    <JoinLobby
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
import { Monitor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDigitalStatus, getMeetingState, joinMeeting, leaveMeeting } from '@/api/meeting'
import AttendeeList from './components/AttendeeList.vue'
import ChatPanel from './components/ChatPanel.vue'
import JoinLobby from './components/JoinLobby.vue'
import MeetingTopBar from './components/MeetingTopBar.vue'
import ScreenStage from './components/ScreenStage.vue'
import StatusNotice from './components/StatusNotice.vue'
import StudioBackdrop from './components/StudioBackdrop.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom, describeChatSend } from './hooks/useLiveKitRoom'
import { formatMeetingNo } from './utils/meeting-time'
import './styles/meeting.css'

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
/** 小屏分段切换的面板；宽屏两块同时显示，此项不起作用 */
const activePanel = ref('stage')

const panelOptions = [
  { label: '画面', value: 'stage' },
  { label: '成员与聊天', value: 'chat' },
]

const meetingNo = String(route.params.meetingNo ?? '')
const formattedMeetingNo = computed(() => formatMeetingNo(meetingNo))
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

/** 后端下发的语音可用性：{ enabled, available } */
const digital = ref(null)
/** 开了语音但合成没配好：必须显式说出来，不能表现为「点了没声音」 */
const digitalUnavailable = computed(() => Boolean(digital.value?.enabled) && !digital.value?.available)

async function refreshDigitalStatus() {
  try {
    digital.value = await getDigitalStatus()
  } catch {
    // 取不到状态就当不可用：宁可多提示一次，也不要静默
    digital.value = null
  }
}

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

/** 身份注入 store：聊天消息靠它判定「是不是我发的」，不在展示层比 id */
function applySelf(session) {
  selfId.value = session?.attendeeId ?? null
  selfNickname.value = session?.nickname ?? ''
  store.setSelf({ selfId: selfId.value })
}

async function handleJoin(nickname) {
  joining.value = true
  try {
    const result = await joinMeeting(meetingNo, nickname)
    const session = { attendeeToken: result.attendeeToken, attendeeId: result.attendeeId, nickname }
    writeSession(session)
    applySelf(session)
    store.setMeeting(result.meeting)
    store.setMessages(result.messages)
    dialogVisible.value = false
    await connectRoom(result.realtime)
    startStatePoll()
  } catch (error) {
    // 会议不存在 / 已结束 / 实时通道未配置，错误码由全局拦截器抛出
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
 * @param {{ seed?: boolean }} [options] seed=true 表示首次进入 / 刷新恢复，用库里的消息铺底。
 * @returns {Promise<object|null>} 状态；失败返回 null（原因记进 stateError）
 */
async function refreshState({ seed = false } = {}) {
  const session = readSession()
  if (!session) return null
  applySelf(session)
  if (seed) loading.value = true
  try {
    const state = await getMeetingState(meetingNo, session.attendeeToken)
    store.setMeeting(state.meeting)
    // 铺底时机：首次进入 / 刷新恢复，或者**实时通道不在线**（此时轮询降级为聊天通道）。
    // 通道在线时绝不覆盖 —— 整体覆盖会把刚收到的实时消息打回旧值，页面表现就是「聊天不更新」。
    // 成员名单不在这里覆盖：它只认房间事件（docs/adr/0002），库里的 attendees 是台账、含崩溃残留。
    // 轮询同时不碰 loading，否则聊天面板每 10s 闪一次骨架。
    if (seed || store.connection !== 'connected') {
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
  // 包一层：轮询只做「会议是否已结束」的兜底，不带 seed；数字人可用性搭这趟车，不另开定时器
  refreshDigitalStatus()
  statePollTimer = window.setInterval(() => {
    refreshState()
    refreshDigitalStatus()
  }, STATE_POLL_INTERVAL_MS)
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
  // 刷新恢复：这一次要铺底（消息来自库），此后的轮询只兜底会议结束
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
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  height: 100dvh;
  padding: clamp(12px, 1.6vw, 24px);
  overflow: hidden;
  background: var(--mt-bg);
}

.meeting-room__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: clamp(10px, 1.2vw, 16px);
  height: 100%;
  min-height: 0;
}

.meeting-room__notices {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
}

.room-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) clamp(300px, 24vw, 380px);
  gap: clamp(10px, 1.2vw, 16px);
  min-height: 0;
}

.room-body__stage {
  display: flex;
  min-width: 0;
  min-height: 0;
}

.room-body__side {
  min-height: 0;
  padding: 12px;
  gap: 10px;
}

.room-body__chat {
  flex: 1;
  min-height: 0;
  padding-top: 10px;
  border-top: 1px solid var(--mt-line);
}

/* 小屏切换器默认不占位（宽屏两块同时显示） */
.room-switcher {
  display: none;
  flex: none;
}

/* ================= 结束幕 ================= */
.room-ended {
  color-scheme: dark;
  position: fixed;
  inset: 0;
  z-index: var(--mt-z-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-neutral-950) 88%, transparent);
  color: var(--mt-text);
  backdrop-filter: blur(18px) saturate(1.2);
}

.room-ended__wash {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(42% 46% at 36% 40%, color-mix(in oklab, var(--mt-signal) 12%, transparent), transparent 70%),
    radial-gradient(38% 42% at 68% 64%, color-mix(in oklab, var(--mt-amber) 8%, transparent), transparent 72%);
  filter: blur(30px);
  pointer-events: none;
}

.room-ended__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--tb-size-6);
  text-align: center;
}

.room-ended__title {
  margin: var(--tb-size-3) 0 0;
  color: var(--mt-text);
  font-family: var(--mt-font-display);
  font-size: clamp(3.5rem, 12vw, 9rem);
  font-weight: 500;
  letter-spacing: -0.05em;
  line-height: 0.86;
}

.room-ended__sub {
  margin: var(--tb-size-4) 0 0;
  color: var(--mt-text-2);
  font-size: var(--tb-text-sm);
  letter-spacing: 0.18em;
}

.room-ended__hint {
  margin: var(--tb-size-2) 0 0;
  color: var(--mt-text-3);
  font-size: var(--tb-text-sm);
}

.room-ended__rule {
  display: block;
  width: 64px;
  height: 1px;
  margin-top: var(--tb-size-5);
  background: var(--mt-signal);
}

.curtain-enter-active {
  transition: opacity var(--mt-dur-5) var(--mt-ease);
}

.curtain-leave-active {
  transition: opacity var(--mt-dur-3) var(--mt-ease);
}

.curtain-enter-from,
.curtain-leave-to {
  opacity: 0;
}

.curtain-enter-active .room-ended__inner {
  animation: mt-rise-in 0.8s var(--mt-ease);
}

@media (max-width: 1080px) {
  .room-body {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 1024px) {
  .room-body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
  }

  .room-switcher {
    display: block;
  }

  .room-body > * {
    display: none;
  }

  .room-body[data-panel='stage'] .room-body__stage {
    display: flex;
  }

  .room-body[data-panel='chat'] .room-body__side {
    display: flex;
  }
}

@media (max-width: 820px) {
  .meeting-room {
    padding: 10px;
  }

  .meeting-room__inner {
    gap: 10px;
  }

  .room-body__side {
    padding: 10px;
  }

  .room-switcher :deep(.el-segmented) {
    width: 100%;
  }
}
</style>
