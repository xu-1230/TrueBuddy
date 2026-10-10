<template>
  <div class="meeting-console">
    <MeetingTopBar
      :title="store.meeting?.title || '模拟会议'"
      :meeting-no="session?.meetingNo || ''"
      sub-note="老师控制台"
      :icon="VideoCamera"
      :online-count="store.onlineCount"
      :connected="store.connected"
      :ended="store.ended"
      :connection="store.connection"
    >
      <template #actions>
        <template v-if="session">
          <!-- 开始演讲 = 开始信号：让「PPT 放映页」念一段（声音只能在被共享的那一屏出声，见 speakDigital） -->
          <el-button type="primary" :disabled="store.ended" @click="startLecture">
            <el-icon class="btn-icon"><VideoPlay /></el-icon>
            开始演讲
          </el-button>
          <!-- 停止真的停得下来：掐断请求即取消后端合成（旧口径要等它念完） -->
          <el-button :disabled="store.ended" @click="stopDigital">停止演讲</el-button>
          <el-button @click="handleCopyLink">复制入会链接</el-button>
          <el-button v-if="!store.sharing" type="primary" @click="handleStartShare">开始共享</el-button>
          <el-button v-else @click="handleStopShare">停止共享</el-button>
          <!-- 手输播报：多源 / 长文本的调试入口，与「开始演讲」信号并存 -->
          <el-button :disabled="store.ended" @click="openDigitalDialog">数字人播报</el-button>
          <el-button type="danger" :disabled="store.ended" @click="handleClose">结束会议</el-button>
        </template>
      </template>
    </MeetingTopBar>

    <el-alert
      v-if="store.ended"
      type="info"
      :closable="false"
      show-icon
      title="会议已结束"
      description="学生端已收到结束提示，实时通道已断开。"
    >
      <div class="console-missing__action">
        <el-button size="small" type="primary" @click="goCreateInPpt">去 PPT 页创建会议</el-button>
      </div>
    </el-alert>

    <!-- 主持人凭据只随「创建成功」这一次响应下发：不给复制入口，老师清一次存储就只能重开会（ADR-0003） -->
    <el-alert
      v-if="credentialVisible && session"
      class="console-credential"
      type="warning"
      show-icon
      :closable="true"
      title="请保存主持人凭据"
      @close="credentialVisible = false"
    >
      <p>凭据只在下发会议号时返回一次，之后任何接口都不会再给。丢失无法找回，只能重新建会。</p>
      <div class="console-credential__row">
        <code class="console-credential__token">{{ session.hostToken }}</code>
        <el-button size="small" @click="handleCopyCredential">复制凭据</el-button>
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
        <el-button size="small" type="primary" @click="goCreateInPpt">去 PPT 页创建会议</el-button>
      </div>
    </el-alert>

    <!-- 控制台不再自己建会：它随会议一起打开（会议在 PPT 页创建，见 docs/16 §5.6） -->
    <section v-else-if="!session" class="console-empty">
      <h3 class="console-empty__title">会议在 PPT 页创建</h3>
      <p class="console-empty__tip">
        本页是老师控制台，地址栏带 <code>?meetid={会议号}</code> 时管理这一场。
        开会请到「PPT 演示」页：导入 PPT → 写好讲稿 → 点「创建会议」，
        控制台会自动带着这场会的凭据开在这里。
      </p>
      <el-button type="primary" @click="goCreateInPpt">去 PPT 页创建会议</el-button>

      <!-- 本机还留着凭据的会议：不给入口的话，摘掉地址栏会议号后就再也回不去 -->
      <div v-if="knownMeetings.length" class="console-empty__known">
        <p class="console-empty__known-title">本机还能回到这些会议：</p>
        <div class="console-empty__known-list">
          <button
            v-for="item in knownMeetings"
            :key="item.meetingNo"
            type="button"
            class="console-empty__known-item"
            @click="handleEnterMeeting(item.meetingNo)"
          >
            <strong>{{ item.meetingNo }}</strong>
            <span>{{ item.title }}</span>
          </button>
        </div>
      </div>
    </section>

    <section v-else class="console-body">
      <div class="console-body__stage">
        <ScreenStage
          ref="stageRef"
          :sharing="store.sharing"
          placeholder="尚未共享画面"
          hint="点击右上角「开始共享」，在选择框里选中「PPT 放映页」标签页（就是你创建会议的那个 PPT 页）"
        />
      </div>

      <aside class="console-body__side">
        <AttendeeList :attendees="store.displayAttendees" :degraded="store.degraded" />
        <!-- 分隔不用 el-divider：1px 分隔线由样式给（docs/09 §7「分隔线恒 1px」） -->
        <ChatPanel class="console-body__chat" :messages="store.messages" :disabled="store.ended" @send="handleSend" />
      </aside>
    </section>
    <el-dialog v-model="digitalVisible" title="数字人播报" width="520px">
      <p class="digital-dialog__state">
        <el-tag v-if="!digitalStatus" type="info" size="small">状态未知</el-tag>
        <el-tag v-else-if="!digitalStatus.enabled" type="info" size="small">未启用</el-tag>
        <el-tag v-else-if="!digitalStatus.available" type="warning" size="small">
          已启用但语音未配置
        </el-tag>
        <el-tag v-else type="success" size="small">可用</el-tag>
        <span class="digital-dialog__hint">{{ digitalHint }}</span>
        <el-button link type="primary" size="small" @click="refreshDigitalStatus">刷新状态</el-button>
      </p>
      <el-input
        v-model="digitalText"
        type="textarea"
        :rows="4"
        maxlength="1000"
        show-word-limit
        placeholder="要播报的内容（由「PPT 放映页」念出来，声音随屏幕共享到学生端）"
      />
      <template #footer>
        <el-button @click="digitalVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!digitalText.trim()" @click="handleDigitalSay">
          播报
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, VideoCamera, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeMeeting, getDigitalStatus, getMeetingDetail } from '@/api/meeting'
import AttendeeList from './components/AttendeeList.vue'
import ChatPanel from './components/ChatPanel.vue'
import MeetingTopBar from './components/MeetingTopBar.vue'
import ScreenStage from './components/ScreenStage.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom, describeChatSend } from './hooks/useLiveKitRoom'
import {
  clearHostSession,
  listKnownMeetings,
  migrateLegacyHostSession,
  readHostSession,
} from '@/utils/hostSession'

/** 老师发言时的昵称与角色（与后端落库口径一致） */
const HOST_NICKNAME = '老师'
const ROLE_HOST = 1

const store = useMeetingStore()
const route = useRoute()
const router = useRouter()
const stageRef = ref(null)

/** 主持人会话：{ meetingId, meetingNo, joinUrl, hostToken, title } */
const session = ref(null)

// ===== 数字人播报（讲稿由「PPT 放映页」念，见 docs/16 §5.7）=====
const digitalVisible = ref(false)
const digitalText = ref('')
const digitalStatus = ref(null)

const digitalHint = computed(() => {
  const status = digitalStatus.value
  if (!status) return '点「刷新状态」重新探测。'
  if (!status.enabled) return '后端未开启语音（tb.lecture.digital-human.enabled=false）。'
  if (!status.available) return '后端未配置语音合成（DashScope Key 为空），配好并重启后再刷新。'
  return '声音在「PPT 放映页」播放，随屏幕共享到学生端。'
})

async function refreshDigitalStatus() {
  try {
    digitalStatus.value = await getDigitalStatus()
  } catch {
    digitalStatus.value = null
  }
}

function openDigitalDialog() {
  digitalVisible.value = true
  refreshDigitalStatus()
}

/** 播报总线：一个会议一条频道，与主持人凭据的存储键同形（`tb-meeting-host:{会议号}`） */
const CHANNEL_PREFIX = 'tb-meeting:'

/**
 * 给放映页发一条指令。
 *
 * <p>为什么不是接口：声音必须在**被共享的那一屏**（放映页）播出来，学生才听得到，
 * 而那一页也是唯一能顺手驱动口型的地方（口型跟的是它正在播的音频）。总线只做单向
 * （控制台 → 放映页）：翻页与「现在第几页」仍由放映页自己说了算。
 * 每条指令开一个短命频道：控制台不需要长期订阅，也就没有要清理的连接。
 *
 * @param {{ action: 'speak'|'stop', text?: string }} message 指令
 * @returns {boolean} 是否已发出（没有会议号或浏览器不支持时为 false）
 */
function postToPresenter(message) {
  if (!session.value?.meetingNo || typeof BroadcastChannel === 'undefined') return false
  const channel = new BroadcastChannel(`${CHANNEL_PREFIX}${session.value.meetingNo}`)
  channel.postMessage(message)
  channel.close()
  return true
}

/**
 * 把一段文本交给放映页播报。
 *
 * <p>本端先回显一条「数字人发言」：放映页收到后会以数字人身份落库（彩排试听不落库），
 * 这里只是让老师当场看见自己说了什么，与聊天的本端回显同一口径。
 *
 * @param {string} text 要播报的文本
 * @returns {boolean} 是否已发出指令
 */
function speakDigital(text) {
  const content = (text ?? '').trim()
  if (!content || !postToPresenter({ action: 'speak', text: content })) return false
  store.applyEvent({
    type: 'chat.message',
    id: globalThis.crypto?.randomUUID?.() ?? `local-${Date.now()}`,
    attendeeId: null,
    nickname: '数字人',
    role: 3,
    content,
    kind: 2,
    createTime: new Date().toISOString(),
    self: false,
  })
  ElMessage.success('已交给「PPT 放映页」播报：声音从那一页出去，随屏幕共享到学生端')
  return true
}

/** 停止播报：掐断放映页那条请求，后端合成随即取消（旧口径只能等它念完） */
function stopDigital() {
  if (!postToPresenter({ action: 'stop' })) return
  ElMessage.info('已让「PPT 放映页」停止播报')
}

/** 本轮「开始演讲」的固定内容：真正的「讲什么」要等当前页讲稿 / AI 现场讲词落地（docs/18 A/B 段） */
const MOCK_LECTURE_TEXT = '你好'

/**
 * 「开始演讲」：一个**开始信号** —— 表示数字人可以从这里开始讲了。
 *
 * <p>它不是"把文本框里的字发出去"：本轮还没有按页取讲稿，所以内容固定为 {@link MOCK_LECTURE_TEXT}。
 * 下一轮把取文本那一步换成「放映页念当前页 `remark` / AI 现场讲词」时，**信号本身不变**（docs/18 §5.2）。
 */
function startLecture() {
  speakDigital(MOCK_LECTURE_TEXT)
}

/** 【数字人播报】手输框：多源 / 长文本的调试入口，与「开始演讲」信号并存 */
function handleDigitalSay() {
  const text = digitalText.value.trim()
  if (!text) return
  if (speakDigital(text)) digitalText.value = ''
}

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
/** 建会成功后展示主持人凭据的提示条（关掉就不再出现——凭据本身仍在本地会话里） */
const credentialVisible = ref(false)
/** 本机还留着主持人凭据的会议：建会态下列出来，作为「回到某一场」的入口 */
const knownMeetings = ref([])

const room = useLiveKitRoom({
  getLocalPreviewElement: () => stageRef.value?.videoRef,
  onEvent: (payload) => store.applyEvent(payload),
  onConnectionChange: handleConnectionChange,
  // 老师是控制台、不是参会席位：名单与人数都不算自己（docs/16 Q22）
  omitHost: true,
})

// 身份显式注入：老师发言在库里 attendeeId 恒为 null，靠比对 id 会「碰巧」判对自己
store.setSelf({ isHost: true })

/** 共享状态以房间为准（浏览器共享栏点「停止」也会同步过来），再镜像给展示层 */
watch(
  () => room.sharing.value,
  (sharing) => {
    store.sharing = sharing
  },
)

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

/** 拉详情：补齐成员与历史，并用其中的 realtime 连接房间 */
async function loadDetail() {
  if (!session.value) return null
  const detail = await getMeetingDetail(session.value.meetingNo, session.value.hostToken)
  store.setMeeting(detail.meeting)
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

/** 复制主持人凭据：拿不到剪贴板时至少把凭据本身显示出来，别让人空手丢一场会 */
async function handleCopyCredential() {
  const token = session.value?.hostToken
  if (!token) return
  try {
    await navigator.clipboard.writeText(token)
    ElMessage.success('主持人凭据已复制，请自行保存')
  } catch {
    ElMessage.info(token)
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
    // 这一场到此为止：删掉本机凭据、摘掉地址栏会议号（watch 会清掉界面状态），控制台回到空态
    clearHostSession(session.value.meetingNo)
    credentialVisible.value = false
    await router.replace({ query: {} })
    await room.disconnect()
    ElMessage.success('会议已结束，学生端已断开；回到「PPT 放映页」标签页按 ESC 退出放映即可开下一场')
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

/** 清掉当前这一场的界面状态（换会议号时用：数据串场比多点一次更糟） */
function clearCurrentMeeting() {
  session.value = null
  store.reset()
}

/**
 * 按地址栏恢复：`?meetid={会议号}` 指向哪一场就恢复哪一场。
 *
 * <p>主持人凭据只存在创建会议的那个浏览器里，因此「地址栏带会议号但本地没凭据」是正常情况
 * （换机器 / 清了存储 / 会议已结束）：不报错，只提示并给「去 PPT 页创建会议」这个出口。
 *
 * <p>`?credential=1` 由 PPT 页建会后写入：控制台是老师第一个看到「凭据只此一次」的地方
 * （PPT 页建会即进放映，全屏覆盖层会把弹窗挡住），提示一次后立刻从地址栏摘掉，刷新不再重复。
 */
async function restoreFromQuery() {
  const meetingNo = meetId.value
  if (!meetingNo) {
    clearCurrentMeeting()
    missingCredential.value = false
    credentialVisible.value = false
    // 没有会议号 = 空态（会议在 PPT 页创建）：顺手列出本机还能回到的会议
    knownMeetings.value = listKnownMeetings()
    return
  }
  // 地址栏刚由本页写入（`handleEnterMeeting`）：这一场已拉过详情、也连上房间，不必再恢复一遍
  if (session.value?.meetingNo === meetingNo) return

  const saved = readHostSession(meetingNo)
  if (!saved) {
    clearCurrentMeeting()
    missingCredential.value = true
    return
  }

  const justCreated = route.query.credential === '1'
  missingCredential.value = false
  restoring.value = true
  clearCurrentMeeting()
  session.value = saved
  credentialVisible.value = justCreated
  try {
    await loadDetail()
    if (justCreated) await router.replace({ query: { meetid: meetingNo } })
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
  const adopted = migrateLegacyHostSession()
  if (adopted) await router.replace({ query: { meetid: adopted } })
})

/** 从空态回到某一已存在的会议（地址栏带上会议号，`watch` 接手恢复） */
function handleEnterMeeting(meetingNo) {
  router.push({ query: { meetid: meetingNo } })
}

/**
 * 「去 PPT 页创建会议」出口。
 *
 * <p>控制台自己不能建会：甲方案里被共享的那一屏是 PPT 页，建会必须发生在那儿
 * （见 docs/16 §5.6）。这里只负责断开当前房间并把老师送过去。
 */
async function goCreateInPpt() {
  await room.disconnect()
  router.push('/index/ppt')
}

onBeforeUnmount(() => {
  room.disconnect()
  store.reset()
})
</script>

<style scoped>
.digital-dialog__state {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.75rem;
  font-size: var(--tb-text-sm, 13px);
}

.digital-dialog__hint {
  color: var(--tb-text-secondary);
}

.meeting-console {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  height: 100%;
}

.console-empty {
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

.console-empty__title {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-lg);
}

.console-empty__tip {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}

.console-empty__tip code {
  padding: 0 0.25rem;
  background: var(--tb-bg-raised);
  border-radius: var(--tb-radius-xs);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.9em;
}

/* 按地址栏恢复中的占位：顶掉空态，避免先闪一下「会议在 PPT 页创建」 */
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

.console-credential__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.console-credential__token {
  flex: 1;
  overflow-x: auto;
  padding: 0.25rem 0.5rem;
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
  white-space: nowrap;
}

/* 空态下列出的「本机还能回到的会议」 */
.console-empty__known {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  width: 100%;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px dashed var(--tb-border-strong);
}

.console-empty__known-title {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.console-empty__known-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* 可点击的会议入口必须是真按钮：el-tag 挂 click 拿不到 focus 与键盘可达性 */
.console-empty__known-item {
  display: inline-flex;
  align-items: baseline;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background: var(--tb-bg-raised);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  cursor: pointer;
  transition: border-color var(--tb-dur-1) var(--tb-ease-out), background var(--tb-dur-1) var(--tb-ease-out);
}

.console-empty__known-item:hover {
  background: var(--tb-primary-soft);
  border-color: var(--tb-primary-border);
}

.console-empty__known-item strong {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  letter-spacing: 0.08em;
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
  /* 没有这一行，聊天列表在矮窗口下会把面板撑破、内部滚动失效（学生端一直有，控制台漏了） */
  min-height: 0;
  padding: 1rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-2);
}

/* 名单与聊天是同一面板里的两个子区：用 1px 分隔线分区，不再靠 el-divider 撑出空隙 */
.console-body__chat {
  flex: 1;
  min-height: 0;
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--tb-border);
}

@media (max-width: 1080px) {
  .console-body {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
