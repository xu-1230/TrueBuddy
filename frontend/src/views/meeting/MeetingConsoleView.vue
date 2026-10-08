<template>
  <div class="meeting-console">
    <header class="console-bar">
      <div class="console-bar__info">
        <el-icon class="console-bar__icon"><VideoCamera /></el-icon>
        <div>
          <p class="console-bar__title">{{ store.meeting?.title || '模拟会议' }}</p>
          <p class="console-bar__sub">
            会议号 <strong>{{ session?.meetingNo || '—' }}</strong>
            <span v-if="session"> · {{ store.onlineCount }} 人在线</span>
          </p>
        </div>
      </div>

      <div class="console-bar__actions">
        <el-tag size="small" effect="plain" :type="connectionTagType">{{ connectionText }}</el-tag>
        <template v-if="session">
          <el-button @click="handleCopyLink">复制入会链接</el-button>
          <el-button v-if="!store.sharing" type="primary" @click="handleStartShare">开始共享</el-button>
          <el-button v-else @click="handleStopShare">停止共享</el-button>
          <el-button type="danger" :disabled="store.ended" @click="handleClose">结束会议</el-button>
        </template>
      </div>
    </header>

    <el-alert
      v-if="store.ended"
      type="info"
      :closable="false"
      show-icon
      title="会议已结束"
      description="学生端已收到结束提示，实时通道已断开。"
    >
      <div class="console-missing__action">
        <el-button size="small" type="primary" @click="handleForgetMeetId">新建会议</el-button>
      </div>
    </el-alert>

    <!-- 正在按地址栏的 ?meetid= 恢复（避免先闪一下建会表单） -->
    <section v-if="restoring" class="console-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在恢复会议 {{ meetId }}…</span>
    </section>

    <!-- 地址栏带了会议号，但本浏览器没有这场会的主持人凭据：给出口而不是报错 -->
    <el-alert
      v-else-if="missingCredential"
      type="warning"
      :closable="false"
      show-icon
      :title="`本浏览器没有会议 ${meetId} 的主持人凭据`"
    >
      <p>主持人凭据只存在创建这场会议的浏览器里：可能换了机器、清了存储，或这场会议已经结束。</p>
      <div class="console-missing__action">
        <el-button size="small" type="primary" @click="handleForgetMeetId">新建会议</el-button>
      </div>
    </el-alert>

    <section v-else-if="!session" class="console-create">
      <el-form :model="form" label-width="80px" @submit.prevent>
        <el-form-item label="会议标题">
          <el-input
            v-model="form.title"
            placeholder="如：企业级应用开发综合实践 第 3 讲"
            maxlength="100"
            show-word-limit
            @keyup.enter="handleCreate"
          />
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="creating" @click="handleCreate">创建会议</el-button>
      <p class="console-create__tip">
        创建后地址栏会带上会议号（<code>?meetid=</code>）：刷新、复制、另开标签页都回到这一场。
        把入会链接发给学生，学生打开链接输入名字即可参会（免登录）。
        老师端是控制台，不作为参会成员出现在名单里。
      </p>

      <!-- 本机还留着凭据的会议：不给入口的话，摘掉地址栏会议号后就再也回不去 -->
      <div v-if="knownMeetings.length" class="console-create__known">
        <p class="console-create__known-title">本机还能回到这些会议：</p>
        <el-tag
          v-for="item in knownMeetings"
          :key="item.meetingNo"
          class="console-create__known-item"
          type="info"
          effect="plain"
          @click="handleEnterMeeting(item.meetingNo)"
        >
          {{ item.meetingNo }} · {{ item.title }}
        </el-tag>
      </div>
    </section>

    <section v-else class="console-body">
      <div class="console-body__stage">
        <ScreenStage
          ref="stageRef"
          :sharing="store.sharing"
          placeholder="尚未共享画面"
          hint="点击右上角「开始共享」，选中自动打开的「PPT 放映页」标签页"
        />
      </div>

      <aside class="console-body__side">
        <AttendeeList :attendees="store.attendees" :online-count="store.onlineCount" />
        <el-divider class="console-body__divider" />
        <ChatPanel :messages="store.messages" :disabled="store.ended" @send="handleSend" />
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeMeeting, createMeeting, getMeetingDetail } from '@/api/meeting'
import AttendeeList from './components/AttendeeList.vue'
import ChatPanel from './components/ChatPanel.vue'
import ScreenStage from './components/ScreenStage.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom, describeChatSend } from './hooks/useLiveKitRoom'

/**
 * 主持人会话存储：键按会议号切分，放在 `localStorage`。
 *
 * <p>为什么不用原来的「单个 `sessionStorage` 键」：地址栏的 `?meetid={会议号}` 必须能**在任意标签页**恢复
 * （复制链接到新标签页、多开两个控制台各管一场会），而 `sessionStorage` 逐标签隔离、单个键也区分不了会议。
 * 代价是主持人凭据会留在本机浏览器里，因此**结束会议时立刻删除该键**。
 */
const SESSION_PREFIX = 'tb-meeting-host:'
/** 改造前的会话键（单个 sessionStorage）：读到就迁移一次，迁移后不再使用 */
const LEGACY_SESSION_KEY = 'tb-meeting-host'
/** 老师发言时的昵称与角色（与后端落库口径一致） */
const HOST_NICKNAME = '老师'
const ROLE_HOST = 1
/** 放映页路由：用相对路径，自动跟随当前 origin（localhost 或热点下的局域网 IP 都能打开） */
const SCREENING_PATH = '/index/ppt'

const store = useMeetingStore()
const route = useRoute()
const router = useRouter()
const stageRef = ref(null)
const creating = ref(false)
const form = reactive({ title: '' })

/** 主持人会话：{ meetingId, meetingNo, joinUrl, hostToken, title } */
const session = ref(null)
/** 放映页标签页句柄：同一浏览器会话内复用，避免每建一次会就多开一个页签 */
let screeningTab = null

/**
 * 地址栏会议号（`?meetid=`）。
 *
 * <p>`/index/meeting` = 未指定会议（建会态）；`/index/meeting?meetid={会议号}` = 管理这一场。
 * 取值就是会议号本身（与 `lecture_meeting.meeting_no`、学生端 `/meeting/:meetingNo` 同一个值）。
 */
const meetId = computed(() => String(route.query.meetid ?? '').trim())
/** 正在按地址栏恢复（避免先闪一下建会表单） */
const restoring = ref(false)
/** 地址栏带了会议号，但本浏览器没有这场会的主持人凭据 */
const missingCredential = ref(false)
/** 本机还留着主持人凭据的会议：建会态下列出来，作为「回到某一场」的入口 */
const knownMeetings = ref([])

const room = useLiveKitRoom({
  getLocalPreviewElement: () => stageRef.value?.videoRef,
  onEvent: (payload) => store.applyEvent(payload),
  onConnectionChange: handleConnectionChange,
})

/** 共享状态以房间为准（浏览器共享栏点「停止」也会同步过来），再镜像给展示层 */
watch(
  () => room.sharing.value,
  (sharing) => {
    store.sharing = sharing
  },
)

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
  return 'info'
})

async function handleConnectionChange(status) {
  store.setConnection(status)
  // 断开时确认一次会议状态：房间被结束（含被别处结束）就给出结束提示
  if (status === 'disconnected' && !store.ended) await syncEndedState()
}

async function syncEndedState() {
  if (!session.value) return
  try {
    const detail = await getMeetingDetail(session.value.meetingNo, session.value.hostToken)
    if (detail.meeting?.status === 2) store.ended = true
  } catch {
    // 凭据失效或会议已删除：保持当前界面，不额外打扰
  }
}

/**
 * 同步占位一个空白标签页。
 *
 * <p>必须在**点击的同步流程里**打开：`await` 之后再 `window.open` 会被浏览器弹窗拦截器拦掉。
 * 已存在放映页时直接复用，避免每建一次会就多开一个页签。
 */
function reserveScreeningTab() {
  if (screeningTab && !screeningTab.closed) return screeningTab
  screeningTab = window.open('about:blank', '_blank')
  return screeningTab
}

/** 把放映页地址交给已占位的标签页；`?screen=1` 让 PPT 页加载后直接进入放映状态 */
function openScreeningTab(tab) {
  const url = `${SCREENING_PATH}?screen=1`
  if (!tab) {
    ElMessage.warning(`浏览器拦截了新标签页：请允许本站弹出窗口，或手动打开 ${url}`)
    return
  }
  tab.location.replace(url)
  tab.focus?.()
}

async function handleCreate() {
  const title = form.title.trim()
  if (!title) {
    ElMessage.warning('请先填写会议标题')
    return
  }
  // 先占位再请求：见 reserveScreeningTab 的注释（弹窗拦截）
  const tab = reserveScreeningTab()
  creating.value = true
  try {
    const created = await createMeeting(title)
    // 上一场会议的数据不要串到这一场（`ended` 尤其要清，否则新会议一上来就是「已结束」态）
    clearCurrentMeeting()
    // 标题随会话一起存：create 响应不含 title，刷新恢复时先用本地值顶上，再以详情接口为准
    session.value = { ...created, title }
    writeHostSession(session.value)
    // 地址栏立刻带上会议号：刷新 / 复制到新标签页都指向这一场（watch 认出同一场会直接跳过，不会重复拉详情）
    await router.replace({ query: { meetid: created.meetingNo } })
    store.setMeeting({ title, meetingNo: created.meetingNo, status: 1 })
    ElMessage.success(`会议已创建，会议号 ${created.meetingNo}`)
    // 详情接口才会下发可发布的访问令牌（create 只返回会议号与主持人凭据）
    await loadDetail()
    // 控制台负责「讲」，放映页负责「放」：建会成功后把放映页打开成放映状态
    openScreeningTab(tab)
    // 顺手把放映页共享出去（浏览器要求用户手势，被拒绝会退化成提示）
    await tryAutoShare()
  } catch (error) {
    // 建会失败就别留一个空白页签
    tab?.close()
    ElMessage.error(error?.message || '创建会议失败')
  } finally {
    creating.value = false
  }
}

/** 拉详情：补齐成员与历史，并用其中的 realtime 连接房间 */
async function loadDetail() {
  if (!session.value) return null
  const detail = await getMeetingDetail(session.value.meetingNo, session.value.hostToken)
  store.setMeeting(detail.meeting)
  store.setAttendees(detail.attendees)
  store.setMessages(detail.messages)
  if (detail.meeting?.status === 2) {
    store.ended = true
    return detail
  }
  await connectRoom(detail.realtime)
  return detail
}

/** 连接 LiveKit 房间：老师拿到的令牌可发布（能共享屏幕） */
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

async function handleCopyLink() {
  try {
    await navigator.clipboard.writeText(session.value.joinUrl)
    ElMessage.success('入会链接已复制，发给学生即可')
  } catch {
    ElMessage.info(session.value.joinUrl)
  }
}

/**
 * 建会后自动把放映页共享出去。
 *
 * <p>浏览器规定 `getDisplayMedia` 必须由**用户手势**直接触发，而建会流程已经用掉一次手势
 * （`window.open` 占位放映页），所以这次自动尝试多半会被拒绝：成功则老师零操作，
 * 失败就退化为提示，由老师点「开始共享」补一次真实手势。
 * 无论哪条路径，共享目标都是那个放映页标签页（见 `startScreenShare` 的候选排除）。
 */
async function tryAutoShare() {
  try {
    await room.startScreenShare()
    ElMessage.success('已开始共享放映页，学生端能看到画面')
  } catch {
    // 被浏览器拒绝（无用户手势）或老师在共享选择框里点了取消，都引导到手动按钮
    ElMessage.info('请在右上角点「开始共享」，在弹出的选择框里选中「PPT 放映页」标签页')
  }
}

async function handleStartShare() {
  try {
    await room.startScreenShare()
    ElMessage.success('已开始共享，学生端应能看到画面')
  } catch (error) {
    // 多为用户在浏览器授权弹窗里点了取消；也可能是页面不是安全上下文（需 localhost / HTTPS）
    ElMessage.info(`未开始共享：${error?.message ?? error}`)
  }
}

async function handleStopShare() {
  await room.stopScreenShare()
}

async function handleClose() {
  const confirmed = await ElMessageBox.confirm('结束后学生端将立即收到提示，且不可再入会。', '结束会议', {
    type: 'warning',
    confirmButtonText: '结束会议',
    cancelButtonText: '取消',
  })
    .then(() => true)
    .catch(() => false)
  if (!confirmed || !session.value) return

  try {
    // 后端会把 LiveKit 房间删掉：房间内所有学生立刻收到 Disconnected，这就是「会议已结束」的通知
    await closeMeeting(session.value.meetingNo, session.value.hostToken)
    // 这一场到此为止：删掉本机凭据、摘掉地址栏会议号（watch 会清掉界面状态），控制台回到建会态
    clearHostSession(session.value.meetingNo)
    await router.replace({ query: {} })
    await room.disconnect()
    ElMessage.success('会议已结束，学生端已断开，可以创建下一场')
  } catch (error) {
    ElMessage.error(error?.message || '结束会议失败')
  }
}

/** 发言：DataChannel 实时广播 + 后端落库（老师用主持人凭据） */
async function handleSend(content) {
  const result = await room.sendChat(content, {
    meetingNo: session.value?.meetingNo,
    hostToken: session.value?.hostToken,
    self: { attendeeId: null, nickname: HOST_NICKNAME, role: ROLE_HOST },
  })
  const tip = describeChatSend(result)
  if (tip) ElMessage.warning(tip)
}

// ========== 主持人会话的本地存取（按会议号切分） ==========

function sessionKey(meetingNo) {
  return `${SESSION_PREFIX}${meetingNo}`
}

function clearHostSession(meetingNo) {
  window.localStorage.removeItem(sessionKey(meetingNo))
}

function writeHostSession(value) {
  window.localStorage.setItem(sessionKey(value.meetingNo), JSON.stringify(value))
}

/** 解析会话 JSON；结构不完整就删掉坏值并返回 null */
function parseHostSession(raw, meetingNo) {
  try {
    const saved = JSON.parse(raw)
    if (!saved?.meetingNo || !saved?.hostToken) throw new Error('invalid session')
    return saved
  } catch {
    clearHostSession(meetingNo)
    return null
  }
}

function readHostSession(meetingNo) {
  const raw = window.localStorage.getItem(sessionKey(meetingNo))
  return raw ? parseHostSession(raw, meetingNo) : null
}

/** 扫一遍本机存着的会议会话（先收键名再读，避免读的过程中删键导致漏项） */
function loadKnownMeetings() {
  const keys = []
  for (let i = 0; i < window.localStorage.length; i += 1) {
    const key = window.localStorage.key(i)
    if (key?.startsWith(SESSION_PREFIX)) keys.push(key)
  }
  const list = keys
    .map((key) => readHostSession(key.slice(SESSION_PREFIX.length)))
    .filter(Boolean)
    .map((saved) => ({ meetingNo: saved.meetingNo, title: saved.title || '未命名会议' }))
  knownMeetings.value = list.sort((a, b) => a.meetingNo.localeCompare(b.meetingNo))
}

/** 清掉当前这一场的界面状态（换会议号时用：数据串场比多点一次更糟） */
function clearCurrentMeeting() {
  session.value = null
  store.reset()
}

/**
 * 兼容改造前的会话形态（单个 `sessionStorage` 键）→ 按会议号切分的 `localStorage` 键，一次性迁移。
 *
 * @returns {string|null} 迁移后的会议号（调用方据此补地址栏），没有可迁移的会话时返回 null
 */
function migrateLegacySession() {
  const raw = window.sessionStorage.getItem(LEGACY_SESSION_KEY)
  if (!raw) return null
  window.sessionStorage.removeItem(LEGACY_SESSION_KEY)
  try {
    const saved = JSON.parse(raw)
    if (!saved?.meetingNo || !saved?.hostToken) return null
    writeHostSession(saved)
    return saved.meetingNo
  } catch {
    return null
  }
}

/**
 * 按地址栏恢复：`?meetid={会议号}` 指向哪一场就恢复哪一场。
 *
 * <p>主持人凭据只存在创建会议的那个浏览器里，因此「地址栏带会议号但本地没凭据」是正常情况
 * （换机器 / 清了存储 / 会议已结束）：不报错，只提示并给「新建会议」这个出口。
 */
async function restoreFromQuery() {
  const meetingNo = meetId.value
  if (!meetingNo) {
    clearCurrentMeeting()
    missingCredential.value = false
    // 回到建会态：顺手列出本机还能回到的会议
    loadKnownMeetings()
    return
  }
  // 地址栏刚由本页写入（建会成功后 `router.replace`）：这一场已拉过详情、也连上房间，不必再恢复一遍
  if (session.value?.meetingNo === meetingNo) return

  const saved = readHostSession(meetingNo)
  if (!saved) {
    clearCurrentMeeting()
    missingCredential.value = true
    return
  }

  missingCredential.value = false
  restoring.value = true
  clearCurrentMeeting()
  session.value = saved
  form.title = saved.title ?? ''
  try {
    await loadDetail()
  } catch {
    // 凭据失效（会议被删 / 换了库）或详情接口报错：清掉本地凭据，落到提示态
    clearHostSession(meetingNo)
    session.value = null
    store.reset()
    missingCredential.value = true
  } finally {
    restoring.value = false
  }
}

/** 唯一入口：地址栏变化（含首次进入）都走这里，`immediate` 保证首屏也恢复一次 */
watch(meetId, restoreFromQuery, { immediate: true })

onMounted(async () => {
  if (meetId.value) return
  // 改造期兼容：把旧键里的会话搬进新键，并把会议号补进地址栏（上面的 watch 会接着恢复）
  const adopted = migrateLegacySession()
  if (adopted) await router.replace({ query: { meetid: adopted } })
})

/** 从建会态回到某一已存在的会议（地址栏带上会议号，`watch` 接手恢复） */
function handleEnterMeeting(meetingNo) {
  router.push({ query: { meetid: meetingNo } })
}

/** 「新建会议」出口：摘掉地址栏会议号、断开当前房间，回到建会态 */
async function handleForgetMeetId() {
  missingCredential.value = false
  await router.replace({ query: {} })
  await room.disconnect()
}

onBeforeUnmount(() => {
  room.disconnect()
  store.reset()
})
</script>

<style scoped>
.meeting-console {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100%;
}

.console-bar {
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

.console-bar__info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.console-bar__icon {
  font-size: 1.5rem;
  color: var(--tb-primary);
}

.console-bar__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.console-bar__sub {
  margin: 0.125rem 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.console-bar__sub strong {
  color: var(--tb-text-primary);
  letter-spacing: 0.08em;
}

.console-bar__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.console-create {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 1.5rem;
  max-width: 560px;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.console-create__tip {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}

.console-create__tip code {
  padding: 0 0.25rem;
  background: light-dark(rgba(15, 23, 42, 0.06), rgba(148, 163, 184, 0.16));
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.9em;
}

/* 按地址栏恢复中的占位：顶掉建会表单，避免先闪一下「创建会议」 */
.console-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem 1.25rem;
  max-width: 560px;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.console-missing__action {
  margin-top: 0.5rem;
}

/* 建会态下列出的「本机还能回到的会议」 */
.console-create__known {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  width: 100%;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px dashed var(--tb-border-strong);
}

.console-create__known-title {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.console-create__known-item {
  cursor: pointer;
}

.console-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 1rem;
  min-height: 420px;
}

.console-body__stage {
  min-width: 0;
}

.console-body__side {
  display: flex;
  flex-direction: column;
  padding: 1rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.console-body__divider {
  margin: 0.75rem 0;
}

@media (max-width: 1080px) {
  .console-body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
