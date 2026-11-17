<template>
  <div class="meeting-studio meeting-console">
    <div class="meeting-console__inner">
      <MeetingTopBar
        :title="store.meeting?.title || '会议列表'"
        :meeting-no="session?.meetingNo || ''"
        sub-note="老师控制台"
        :icon="VideoCamera"
        :online-count="store.onlineCount"
        :connected="store.connected"
        :ended="store.ended"
        :connection="store.connection"
        :sharing="store.sharing"
        :start-time="store.meeting?.startTime || ''"
        :end-time="store.meeting?.endTime || ''"
      >
        <template #actions>
          <template v-if="session">
            <button type="button" class="mt-btn mt-btn--quiet" @click="handleCopyLink">
              <el-icon class="mt-btn__icon"><Link /></el-icon>
              复制入会链接
            </button>
            <button
              type="button"
              class="mt-btn mt-btn--danger"
              :disabled="store.ended"
              @click="handleClose"
            >
              <el-icon class="mt-btn__icon"><SwitchButton /></el-icon>
              结束会议
            </button>
          </template>
        </template>
      </MeetingTopBar>

      <!-- 状态提示：结束提示、主持人凭据（只随创建成功下发一次，ADR-0003） -->
      <div v-if="store.ended || (credentialVisible && session)" class="console-notices">
        <StatusNotice v-if="store.ended" tone="info" title="会议已结束">
          <p>学生端已收到结束提示，实时通道已断开。</p>
          <template #actions>
            <button type="button" class="mt-btn mt-btn--primary" @click="goCreateInPpt">
              去 PPT 页创建会议
            </button>
          </template>
        </StatusNotice>

        <StatusNotice
          v-if="credentialVisible && session"
          tone="warn"
          title="请保存主持人凭据"
          closable
          @close="credentialVisible = false"
        >
          <p>凭据只在下发会议号时返回一次，之后任何接口都不会再给。丢失无法找回，只能重新建会。</p>
          <div class="console-credential__row">
            <code class="console-credential__token">{{ session.hostToken }}</code>
            <button type="button" class="mt-btn mt-btn--quiet" @click="handleCopyCredential">
              复制凭据
            </button>
          </div>
        </StatusNotice>
      </div>

      <!-- 正在按地址栏的 ?meetid= 恢复（避免先闪一下建会表单） -->
      <section v-if="restoring" class="console-entry console-entry--loading" role="status">
        <div class="console-entry__inner">
          <span class="console-entry__spinner console-entry__reveal" aria-hidden="true" />
        </div>
      </section>

      <!-- 地址栏带了会议号，但本浏览器没有这场会的主持人凭据 -->
      <section v-else-if="missingCredential" class="console-entry">
        <div class="console-entry__inner">
          <div class="console-entry__actions console-entry__reveal">
            <button type="button" class="mt-btn mt-btn--primary" @click="goCreateInPpt">
              去 PPT 页创建会议
            </button>
          </div>
        </div>
      </section>

      <!-- 控制台不再自己建会：它随会议一起打开（会议在 PPT 页创建） -->
      <section v-else-if="!session" class="console-entry">
        <div class="console-entry__inner">
          <div class="console-entry__actions console-entry__reveal">
            <button type="button" class="mt-btn mt-btn--primary" @click="goCreateInPpt">
              去 PPT 页创建会议
            </button>
          </div>

          <!-- 本机还留着凭据的会议：不给入口的话，摘掉地址栏会议号后就再也回不去 -->
          <div
            v-if="knownMeetings.length"
            class="console-entry__known mt-scroll console-entry__reveal"
          >
            <p class="mt-eyebrow">本机还能回到这些会议</p>
            <button
              v-for="(item, index) in knownMeetings"
              :key="item.meetingNo"
              type="button"
              class="console-entry__known-item"
              @click="handleEnterMeeting(item.meetingNo)"
            >
              <span class="console-entry__known-index mt-mono">
                {{ String(index + 1).padStart(2, '0') }}
              </span>
              <strong class="mt-mono">{{ item.meetingNo }}</strong>
              <span class="console-entry__known-title">{{ item.title }}</span>
              <span class="console-entry__known-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </div>
      </section>

      <template v-else>
        <div class="console-body" :data-panel="activePanel">

          <section class="console-body__stage">
            <ScreenStage
              ref="stageRef"
              :sharing="store.sharing"
              placeholder="尚未共享画面"
              hint="点击下方「开始共享」，在选择框里选中「PPT 放映页」标签页（就是你创建会议的那个 PPT 页）"
            />
          </section>

          <aside class="console-body__side">
            <MeetingChatRail
              :messages="store.messages"
              :attendees="store.displayAttendees"
              :degraded="store.degraded"
              :disabled="store.ended"
              @send="handleSend"
            />
          </aside>
        </div>

        <div class="console-bottom">
          <!-- 讲课工作项 Attention：narrate 失败等需要老师裁决的事 -->
          <StatusNotice
            v-if="lessonAttention"
            tone="danger"
            :title="`第 ${lessonAttention.page + 1} 页讲词生成失败`"
          >
            <p>{{ lessonAttention.message }}。学生端在等待，请裁决：</p>
            <template #actions>
              <button type="button" class="mt-btn mt-btn--quiet" @click="resolveAttention('retry')">
                重试
              </button>
              <button type="button" class="mt-btn mt-btn--primary" @click="resolveAttention('skip')">
                跳过本页
              </button>
            </template>
          </StatusNotice>

          <ControlDock class="console-dock" label="演讲与共享控制">
            <div class="mt-dock__group">
              <!-- 讲课工作项运行中：页码来自放映页上行的 page 事件（页面真相在放映页） -->
              <span v-if="lessonDone" class="mt-chip mt-chip--muted">讲课已完成</span>
              <span v-else-if="lessonActive" class="mt-chip mt-chip--live">
                {{ lessonMode === 'ai' ? 'AI 演讲中' : '照读跟讲中' }}
                {{ presenterPage != null ? ` · 第 ${presenterPage + 1} / ${presenterPageCount ?? '?'} 页` : '' }}
              </span>
              <span v-else class="mt-chip mt-chip--muted">演讲待机</span>
            </div>

            <div class="mt-dock__group">
              <!-- 两种演讲方式：照读 = 跟随翻页念 remark；ai = 自动讲词 + 自动翻页 -->
              <button
                type="button"
                class="mt-btn"
                :disabled="store.ended"
                @click="startLecture('verbatim')"
              >
                <el-icon class="mt-btn__icon"><VideoPlay /></el-icon>
                照读演讲
              </button>
              <button
                type="button"
                class="mt-btn"
                :disabled="store.ended"
                @click="startLecture('ai')"
              >
                <el-icon class="mt-btn__icon"><VideoPlay /></el-icon>
                AI 演讲
              </button>
              <!-- 停止真的停得下来：掐断请求即取消后端合成 -->
              <button
                type="button"
                class="mt-btn mt-btn--quiet"
                :disabled="store.ended"
                @click="stopDigital"
              >
                停止演讲
              </button>
            </div>

            <div class="mt-dock__group">
              <button
                v-if="!store.sharing"
                type="button"
                class="mt-btn mt-btn--primary"
                @click="handleStartShare"
              >
                <el-icon class="mt-btn__icon"><Monitor /></el-icon>
                开始共享
              </button>
              <button
                v-else
                type="button"
                class="mt-btn mt-btn--signal"
                @click="handleStopShare"
              >
                <el-icon class="mt-btn__icon"><Monitor /></el-icon>
                停止共享
              </button>
            </div>

            <div class="mt-dock__group">
              <!-- 手输播报：多源 / 长文本的调试入口，与「开始演讲」信号并存 -->
              <button
                type="button"
                class="mt-btn"
                :disabled="store.ended"
                @click="openDigitalDialog"
              >
                <el-icon class="mt-btn__icon"><Microphone /></el-icon>
                数字人播报
              </button>
            </div>
          </ControlDock>

          <nav class="console-switcher">
            <el-segmented v-model="activePanel" :options="panelOptions" aria-label="切换画面与成员聊天" />
          </nav>
        </div>
      </template>
    </div>

    <el-dialog
      v-model="digitalVisible"
      title="数字人播报"
      width="560px"
      class="mt-tokens mt-dialog mt-dialog--digital"
    >
      <div class="digital-dialog">
        <div class="digital-dialog__state">
          <span class="mt-chip" :class="`mt-chip--${digitalState.tone}`">{{ digitalState.label }}</span>
          <span class="digital-dialog__hint">{{ digitalHint }}</span>
          <button type="button" class="mt-btn mt-btn--quiet" @click="refreshDigitalStatus">
            刷新状态
          </button>
        </div>
        <el-input
          v-model="digitalText"
          type="textarea"
          aria-label="数字人播报内容"
          :rows="5"
          maxlength="1000"
          show-word-limit
          placeholder="要播报的内容（由「PPT 放映页」念出来，声音随屏幕共享到学生端）"
        />
        <div class="digital-dialog__wave" aria-hidden="true">
          <i v-for="n in 7" :key="n" :style="{ '--i': n }" />
        </div>
      </div>
      <template #footer>
        <button type="button" class="mt-btn mt-btn--quiet" @click="digitalVisible = false">关闭</button>
        <button
          type="button"
          class="mt-btn mt-btn--primary"
          :disabled="!digitalText.trim()"
          @click="handleDigitalSay"
        >
          播报
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Link, Microphone, Monitor, SwitchButton, VideoCamera, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { closeMeeting, getDigitalStatus, getMeetingDetail } from '@/api/meeting'
import MeetingChatRail from './components/MeetingChatRail.vue'
import ControlDock from './components/ControlDock.vue'
import MeetingTopBar from './components/MeetingTopBar.vue'
import ScreenStage from './components/ScreenStage.vue'
import StatusNotice from './components/StatusNotice.vue'
import { useMeetingStore } from './stores/meeting'
import { useLiveKitRoom, describeChatSend } from './hooks/useLiveKitRoom'
import {
  clearHostSession,
  listKnownMeetings,
  migrateLegacyHostSession,
  readHostSession,
} from '@/utils/hostSession'
import './styles/meeting.css'

/** 老师发言时的昵称与角色（与后端落库口径一致） */
const HOST_NICKNAME = '老师'
const ROLE_HOST = 1

const store = useMeetingStore()
const route = useRoute()
const router = useRouter()
const stageRef = ref(null)

/** 主持人会话：{ meetingId, meetingNo, joinUrl, hostToken, title } */
const session = ref(null)

/** 小屏切换的面板；宽屏两块同时显示 */
const activePanel = ref('stage')
const panelOptions = [
  { label: '画面', value: 'stage' },
  { label: '成员与聊天', value: 'chat' },
]

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

const digitalState = computed(() => {
  const status = digitalStatus.value
  if (!status) return { label: '状态未知', tone: 'muted' }
  if (!status.enabled) return { label: '未启用', tone: 'muted' }
  if (!status.available) return { label: '语音未配置', tone: 'warn' }
  return { label: '可用', tone: 'live' }
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
 * <p>为什么不是接口：声音必须在**被共享的那一屏**（放映页）播出来，学生才听得到。
 * 总线只做单向（控制台 → 放映页）；翻页与「现在第几页」仍由放映页自己说了算。
 *
 * <p>协议（P0 扩展，向后兼容）：`speak`/`stop` 是「念一段 / 立刻安静」的原始指令；
 * `lesson-start`/`lesson-stop` 启停讲课工作项（跟页照读）；`page` 是放映页反向广播的页码。
 *
 * @param {{ action: 'speak'|'stop'|'lesson-start'|'lesson-stop'|'lesson-resolve', text?: string, mode?: string, resolution?: string }} message
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

/** 停止演讲：工作项作废 + 掐断放映页播报请求，后端合成随即取消 */
function stopDigital() {
  if (!postToPresenter({ action: 'lesson-stop' })) return
  lessonActive.value = false
  lessonMode.value = null
  lessonDone.value = false
  lessonAttention.value = null
  ElMessage.info('已停止讲课工作项与播报')
}

/** Attention 裁决：把老师的决定回传给放映页的工作项（retry 重做本页 / skip 跳过推进） */
function resolveAttention(resolution) {
  if (!postToPresenter({ action: 'lesson-resolve', resolution })) return
  lessonAttention.value = null
}

// ===== 讲课工作项（P0：manual 跟随照读）=====
/** 工作项是否运行中（本端标记：lesson-start 发出即认为在讲，lesson-stop 复位） */
const lessonActive = ref(false)
/** 当前演讲方式：verbatim 照读 / ai 自主（lesson-start 时记录） */
const lessonMode = ref(null)
/** 讲课工作项 Attention：narrate 失败等需要老师裁决的事（放映页经总线上行） */
const lessonAttention = ref(null)
/** 工作项是否已自然讲完（尾页播完） */
const lessonDone = ref(false)
/** 放映页上行的当前页（0 基）与总页数——页面真相在放映页，本页只显示 */
const presenterPage = ref(null)
const presenterPageCount = ref(null)
/** 常驻监听放映页上行的频道：page 事件是持续到来的状态，不能用每条一个的短命频道收 */
let presenterBus = null

/** 按当前会议号挂/换上行的监听频道（与 postToPresenter 的发送频道同一条总线） */
function watchPresenter() {
  presenterBus?.close()
  presenterBus = null
  presenterPage.value = null
  presenterPageCount.value = null
  const meetingNo = session.value?.meetingNo
  if (!meetingNo || typeof BroadcastChannel === 'undefined') return
  presenterBus = new BroadcastChannel(CHANNEL_PREFIX + meetingNo)
  presenterBus.onmessage = (event) => {
    const message = event?.data ?? {}
    if (message.action === 'page') {
      presenterPage.value = message.page
      presenterPageCount.value = message.pageCount
    }
    // 工作项上行事件：narrate 失败 → Attention 卡；讲完 → 完成态
    if (message.action === 'lesson-event') {
      const event = message.event ?? {}
      if (event.type === 'page-failed') lessonAttention.value = { page: event.page, message: event.message }
      if (event.type === 'lesson-done') {
        lessonActive.value = false
        lessonDone.value = true
        lessonAttention.value = null
      }
    }
  }
}
watch(() => session.value?.meetingNo, watchPresenter, { immediate: true })

/**
 * 「开始演讲」：启动讲课工作项 —— 就是注释里预告过的那个升级（docs/18 §5.2）。
 *
 * <p>入口与信号语义都不变（还是这个按钮、还是「开始」），变的是总线上发的是结构化的
 * `lesson-start`：放映页收到后按当前页 remark 照读，并跟随老师的翻页自动换页。
 */
function startLecture(mode) {
  if (!postToPresenter({ action: 'lesson-start', mode })) return
  lessonActive.value = true
  lessonMode.value = mode
  lessonDone.value = false
  lessonAttention.value = null
  ElMessage.success(mode === 'ai' ? 'AI 演讲已开始：自动讲词 + 自动翻页，老师只需随时接管' : '照读演讲已开始：跟随老师翻页念备注')
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
 * <p>`?credential=1` 由 PPT 页建会后写入：控制台是老师第一个看到「凭据只此一次」的地方，
 * 提示一次后立刻从地址栏摘掉，刷新不再重复。
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
 * <p>控制台自己不能建会：甲方案里被共享的那一屏是 PPT 页，建会必须发生在那儿。
 * 这里只负责断开当前房间并把老师送过去。
 */
async function goCreateInPpt() {
  await room.disconnect()
  router.push('/index/ppt')
}

onBeforeUnmount(() => {
  presenterBus?.close()
  room.disconnect()
  store.reset()
})
</script>

<style scoped>
.meeting-console {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: clamp(12px, 1.6vw, 24px);
  overflow: hidden;
}

.meeting-console__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: clamp(10px, 1.2vw, 16px);
  min-height: 0;
}

.console-notices {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--tb-size-2);
}

/* ================= 入口屏 ================= */
/* 整块入口屏不再滚动：内容超出时只让「本机会议」列表自己滚 */
.console-entry {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.console-entry__inner {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  gap: var(--tb-size-3);
  width: min(880px, 100%);
  min-height: 0;
}

.console-entry__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-2);
}

.console-entry__known {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  margin-top: var(--tb-size-3);
  padding-top: var(--tb-size-3);
  overflow-y: auto;
  border-top: 1px solid var(--mt-line);
}

.console-entry__known-item {
  display: grid;
  grid-template-columns: 32px minmax(80px, 0.6fr) minmax(0, 1fr) 20px;
  align-items: center;
  gap: var(--tb-size-3);
  min-height: 48px;
  padding: 0 8px;
  border: 0;
  border-bottom: 1px solid color-mix(in oklab, var(--mt-line) 55%, transparent);
  background: transparent;
  color: var(--mt-text-2);
  text-align: left;
  cursor: pointer;
  transition: background var(--mt-dur-1) var(--mt-ease);
}

.console-entry__known-item:hover {
  background: color-mix(in oklab, var(--mt-text) 4%, transparent);
}

.console-entry__known-index {
  color: var(--mt-text-3);
  font-size: 10px;
}

.console-entry__known-item strong {
  color: var(--mt-text);
  font-size: var(--tb-text-md);
  font-weight: 500;
  letter-spacing: 0.14em;
}

.console-entry__known-title {
  overflow: hidden;
  color: var(--mt-text-2);
  font-size: var(--tb-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-entry__known-arrow {
  color: var(--mt-signal);
  font-family: var(--mt-font-mono);
}

.console-entry__spinner {
  width: 28px;
  height: 28px;
  margin-top: var(--tb-size-2);
  border: 1px solid var(--mt-line-strong);
  border-top-color: var(--mt-signal);
  border-radius: 50%;
  animation: mt-spin 0.9s linear infinite;
}

/* 入口 reveal：reduced-motion 由 meeting.css 统一关闭 */
.console-entry__reveal {
  animation: mt-rise-in 0.7s var(--mt-ease);
}

.console-entry__reveal:nth-child(2) {
  animation-delay: 0.07s;
}

.console-entry__reveal:nth-child(3) {
  animation-delay: 0.13s;
}

.console-entry__reveal:nth-child(4) {
  animation-delay: 0.19s;
}

.console-entry__reveal:nth-child(5) {
  animation-delay: 0.25s;
}

/* ================= 会议中 ================= */
.console-body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, 1fr) clamp(320px, 24vw, 380px);
  gap: clamp(10px, 1.2vw, 16px);
  min-height: 0;
}

.console-body__stage {
  display: flex;
  min-width: 0;
  min-height: 0;
}

.console-body__side {
  display: flex;
  min-width: 0;
  min-height: 0;
  padding: 0;
}

.console-bottom {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: var(--tb-size-2);
}

.console-dock {
  width: 100%;
}

.console-switcher {
  display: none;
  flex: none;
}

/* ================= 凭据 ================= */
.console-credential__row {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin-top: 6px;
}

.console-credential__token {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  overflow-x: auto;
  border: 1px solid var(--mt-line);
  border-radius: var(--mt-radius-xs);
  background: color-mix(in oklab, var(--mt-text) 6%, transparent);
  color: var(--mt-text);
  font-family: var(--mt-font-mono);
  font-size: var(--tb-text-xs);
  white-space: nowrap;
}

/* ================= 数字人 voice booth ================= */
.digital-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
}

.digital-dialog__state {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
}

.digital-dialog__hint {
  flex: 1;
  min-width: 0;
  color: var(--mt-text-2);
  font-size: var(--tb-text-sm);
  line-height: 1.55;
}

.digital-dialog__wave {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 28px;
  opacity: 0.65;
}

.digital-dialog__wave i {
  display: block;
  width: 3px;
  height: 100%;
  background: var(--mt-signal);
  transform-origin: bottom;
  animation: mt-equalize 1.6s var(--mt-ease-inout) infinite;
  animation-delay: calc(var(--i) * -0.16s);
}

/* ================= 响应式 ================= */
@media (max-width: 1080px) {
  .console-body {
    grid-template-columns: minmax(0, 1fr) 300px;
  }
}

@media (max-width: 1024px) {
  .console-body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
  }

  .console-switcher {
    display: block;
  }

  .console-body > * {
    display: none;
  }

  .console-body[data-panel='stage'] .console-body__stage {
    display: flex;
  }

  .console-body[data-panel='chat'] .console-body__side {
    display: flex;
  }
}

@media (max-width: 820px) {
  .meeting-console {
    padding: 10px;
  }

  .meeting-console__inner {
    gap: 10px;
  }

  .console-bottom .mt-btn {
    min-height: 46px;
  }

  .console-switcher :deep(.el-segmented) {
    width: 100%;
  }
}
</style>
