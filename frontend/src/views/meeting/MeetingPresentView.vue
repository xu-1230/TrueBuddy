<template>
  <div class="present">
    <!-- 拦路状态：没有凭据 / 会议已结束，此时不进入放映，避免白屏空转 -->
    <div v-if="!ready" class="blocked">
      <div class="blocked-card">
        <p class="blocked-kicker">演讲台</p>
        <h1 class="blocked-title">{{ blockedTitle }}</h1>
        <p class="blocked-text">{{ blockedText }}</p>
        <div class="blocked-actions">
          <el-button type="primary" @click="backToConsole">回控制台</el-button>
          <el-button v-if="credentialMissing" @click="reload">重试</el-button>
        </div>
        <p class="blocked-hint">
          主持人凭据只存在创建会议的那个浏览器里，系统不提供找回（见 docs/adr/0003）。
          换机器或清了存储，请在控制台重新创建一场。
        </p>
      </div>
    </div>

    <template v-else>
      <!-- 放映本体：与 /index/ppt 用的是同一个组件，页码、动画、快捷键全部沿用 -->
      <ScreenView />

      <div class="bar" :class="{ 'bar--active': presenter.state.value !== 'idle' }">
        <div class="bar-meta">
          <span class="bar-title">{{ meetingTitle }}</span>
          <span class="bar-no">会议号 {{ meetingNo }}</span>
          <span class="bar-page">{{ slideIndex + 1 }} / {{ slides.length }}</span>
        </div>

        <div class="bar-controls">
          <el-radio-group v-model="channel" size="small" :disabled="presenter.state.value !== 'idle'">
            <el-radio-button :value="CHANNEL_PREVIEW">本机试听</el-radio-button>
            <el-radio-button :value="CHANNEL_MEETING" :disabled="!meetingChannelReady">会中播报</el-radio-button>
          </el-radio-group>

          <el-button v-if="presenter.state.value === 'idle'" size="small" type="primary" @click="startFollowing">
            跟着页念
          </el-button>
          <el-button v-else size="small" @click="stopFollowing">停止</el-button>

          <span class="bar-state">{{ stateText }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
/**
 * 演讲台（`/meeting/:meetingNo/present`）：放映 + 跟页播报的控制面。
 *
 * 为什么要单独一条路由（docs/18 §5.1）：会议号进到了地址里，本页就能自己按会议号取出
 * 主持人凭据、自己知道在讲哪一页，于是编排层直接长在这一页上——不需要再跨标签页广播页码，
 * 也不存在「控制台以为还在第 3 页、放映页已经翻到第 5 页」这种两份真相。
 *
 * A 段刻意**不连接 LiveKit 房间**：主持人令牌的身份是固定的 `host`（LiveKitTokenService.HOST_IDENTITY），
 * 同一房间同身份后脚进来会把前脚踢掉，控制台和演讲台一起入会会互相顶号。画面仍由控制台共享，
 * 本页只驱动语音（后端 → worker → 房间音频轨）。要把共享权也搬过来，需要先加一类 `presenter` 身份，
 * 那是单独一步（见 docs/18 §5.7）。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSlidesStore } from '../ppt/stores/slides'
import useScreening from '../ppt/hooks/useScreening'
import { htmlToPlainText } from '../ppt/utils/slide-text'
import ScreenView from '../ppt/screen/ScreenView.vue'
import { getDigitalStatus, getMeetingDetail } from '@/api/meeting'
import { usePresenter, CHANNEL_MEETING, CHANNEL_PREVIEW } from './hooks/usePresenter'
import { readHostSession } from './utils/host-session'
import '../ppt/styles/animation.css'

const route = useRoute()
const router = useRouter()
const meetingNo = computed(() => String(route.params.meetingNo ?? '').trim())

const slidesStore = useSlidesStore()
const { slides, slideIndex, currentSlide } = storeToRefs(slidesStore)
const { enterScreeningFromStart } = useScreening()

const ready = ref(false)
const credentialMissing = ref(false)
const meetingTitle = ref('')
const hostToken = ref('')

const channel = ref(CHANNEL_PREVIEW)
const meetingChannelReady = ref(false)

/** 本页此刻要念的稿：pptx 导入的备注是 HTML 段落，统一转纯文本再切段 */
const currentRemark = computed(() => htmlToPlainText(currentSlide.value?.remark))

const presenter = usePresenter({
  identity: () => ({ meetingNo: meetingNo.value, hostToken: hostToken.value }),
  channel: () => channel.value,
  onWarn: (message) => ElMessage.warning(message),
})

const stateText = computed(() => {
  if (presenter.state.value === 'idle') {
    return meetingChannelReady.value
      ? '未在播报。翻页时不会出声。'
      : '未在播报。音频服务未就绪，只能用本机试听。'
  }
  const progress = presenter.progress.value
  if (!progress) return '正在准备音频…'
  return `正在念第 ${progress.index + 1} 页（第 ${progress.segment}/${progress.total} 段）`
})

const blockedTitle = ref('演讲台打不开')
const blockedText = ref('')

function block(title, text) {
  ready.value = false
  blockedTitle.value = title
  blockedText.value = text
}

function backToConsole() {
  router.replace({ path: '/index/meeting', query: { meetid: meetingNo.value } })
}

function reload() {
  window.location.reload()
}

/** 探测会中通路是否真的可用（未启用 / worker 掉线时只允许试听） */
async function probeMeetingChannel() {
  try {
    const status = await getDigitalStatus(meetingNo.value)
    meetingChannelReady.value = Boolean(status?.available)
    if (!meetingChannelReady.value && channel.value === CHANNEL_MEETING) channel.value = CHANNEL_PREVIEW
  } catch {
    meetingChannelReady.value = false
  }
}

async function boot() {
  const saved = readHostSession(meetingNo.value)
  if (!saved) {
    credentialMissing.value = true
    block('这里没有本场会的主持人凭据', `会议号 ${meetingNo.value} 在本机没有找到凭据，请从控制台点「进入演讲台」过来。`)
    return
  }
  credentialMissing.value = false
  hostToken.value = saved.hostToken
  meetingTitle.value = saved.title || '未命名会议'

  try {
    const detail = await getMeetingDetail(meetingNo.value, saved.hostToken)
    if (detail.meeting?.title) meetingTitle.value = detail.meeting.title
    if (detail.meeting?.status === 2) {
      block('这场会议已经结束', `会议号 ${meetingNo.value} 已结束，不能再从这里播报。请在控制台新建一场。`)
      return
    }
  } catch (error) {
    // 详情拉不到时不拦放映：可能是后端暂时不可用，但老师只是想把这一页讲稿试听一遍
    ElMessage.warning(`会议状态未确认：${error?.message ?? error}`)
  }

  ready.value = true
  // 与被共享的标签页对齐：浏览器共享选择框里靠标题认页面（docs/16 §5.6）
  document.title = 'PPT 放映页'
  enterScreeningFromStart()
  await probeMeetingChannel()
}

function startFollowing() {
  presenter.start({ index: slideIndex.value, remark: currentRemark.value })
}

function stopFollowing() {
  presenter.stop()
}

// 跟页：只有正在播报时才动嘴，否则翻页就是普通翻页
watch(slideIndex, (index) => {
  if (presenter.state.value === 'idle') return
  presenter.speakPage(index, currentRemark.value)
})

// 通路与会议号都可能中途变化（换场直接整页重载更干净，这里只重新探测可用性）
watch(channel, async (value) => {
  if (value === CHANNEL_MEETING && !meetingChannelReady.value) await probeMeetingChannel()
})

watch(meetingNo, boot)

onMounted(boot)
</script>

<style scoped>
.present {
  position: relative;
}

/* ===== 拦路状态 ===== */
.blocked {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--tb-size-6);
  background: var(--tb-bg-page);
}
.blocked-card {
  max-width: 520px;
  padding: var(--tb-size-5);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
}
.blocked-kicker {
  margin: 0 0 var(--tb-size-1);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}
.blocked-title {
  margin: 0 0 var(--tb-size-2);
  font-size: var(--tb-text-xl);
  color: var(--tb-text-primary);
}
.blocked-text {
  margin: 0 0 var(--tb-size-4);
  font-size: var(--tb-text-md);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}
.blocked-actions {
  display: flex;
  gap: var(--tb-size-2);
}
.blocked-hint {
  margin: var(--tb-size-4) 0 0;
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-tertiary);
}

/* ===== 演讲台控制条：浮在放映层之上（ScreenView 是 9999） ===== */
.bar {
  position: fixed;
  top: var(--tb-size-3);
  right: var(--tb-size-3);
  z-index: 10001;
  display: flex;
  align-items: center;
  gap: var(--tb-size-4);
  padding: var(--tb-size-2) var(--tb-size-3);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  opacity: 0.45;
  transition: opacity var(--tb-dur-2) var(--tb-ease-out);
}
.bar:hover,
.bar--active {
  opacity: 1;
}
.bar-meta {
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-3);
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}
.bar-title {
  font-size: var(--tb-text-sm);
  color: var(--tb-text-primary);
}
.bar-page {
  font-variant-numeric: tabular-nums;
}
.bar-controls {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
}
.bar-state {
  font-size: var(--tb-text-xs);
  color: var(--tb-text-tertiary);
}
</style>
