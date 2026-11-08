<template>
  <div class="ppt-page">
    <!-- 工具栏 -->
    <div class="ppt-toolbar">
      <div class="ppt-title">
        <el-icon :size="20"><Monitor /></el-icon>
        <span>PPT 演示</span>
      </div>
      <div class="ppt-toolbar-actions">
        <el-button type="primary" plain @click="openFileDialog">
          <el-icon class="btn-icon"><UploadFilled /></el-icon>
          导入 PPT 数据
        </el-button>
        <el-button type="success" plain @click="enterScreeningFromStart()">
          <el-icon class="btn-icon"><VideoPlay /></el-icon>
          从第一页放映
        </el-button>
        <el-button type="success" @click="enterScreening()">
          <el-icon class="btn-icon"><VideoPlay /></el-icon>
          从当前页放映
        </el-button>
        <!-- 创建会议：本页就是被共享的那一屏，控制台随后自动开成另一个标签页（docs/16 §5.6） -->
        <el-button type="primary" :disabled="screening" @click="openMeetingDialog">
          <el-icon class="btn-icon"><VideoCamera /></el-icon>
          创建会议
        </el-button>
        <!-- 试听本页：彩排时没有控制台，播报入口就在这一页（声音从这一页出去，共享后学生才听得到） -->
        <el-button :type="speaking ? 'danger' : 'default'" @click="speaking ? stopSpeaking() : speakCurrentPage()">
          <el-icon class="btn-icon"><Microphone /></el-icon>
          {{ speaking ? '停止' : '试听本页' }}
        </el-button>
        <el-tooltip content="用 AI 把全部页的备注改写成演讲稿" placement="bottom" :show-after="300">
          <el-button type="warning" plain :loading="aiRunning" @click="optimizeAllRemarks">
            <AiSparkIcon class="btn-icon" :size="14" />
            AI 优化全部备注
          </el-button>
        </el-tooltip>
      </div>
      <input
        ref="fileInputRef"
        type="file"
        accept=".pptx,.json"
        class="file-input"
        @change="onFileChange"
      />
    </div>

    <!-- AI 采纳后可一键撤销：改写备注是不可逆操作，必须留退路（关掉这个提示就放弃撤销入口） -->
    <el-alert
      v-if="aiUndo"
      class="ppt-ai-undo"
      type="success"
      :closable="true"
      show-icon
      title="已应用 AI 讲稿"
      @close="aiUndo = null"
    >
      <div class="ppt-ai-undo__actions">
        <el-button size="small" @click="undoAiRemarks">撤销本次优化（还原原备注）</el-button>
      </div>
    </el-alert>

    <!-- 主体区域 -->
    <div class="ppt-body">
      <!-- 左侧幻灯片缩略图列表 -->
      <div class="ppt-thumbnails">
        <div
          v-for="(slide, index) in slides"
          :key="slide.id"
          class="thumb-item"
          :class="{ 'active': index === slideIndex }"
          :title="`第 ${index + 1} 页`"
          @click="updateSlideIndex(index)"
        >
          <ThumbnailSlide :slide="slide" :size="160" />
          <span class="thumb-index">{{ index + 1 }}</span>
        </div>
        <el-empty v-if="!slides.length" description="暂无幻灯片，请导入 PPT 数据" :image-size="60" />
      </div>

      <div class="layout-content-center">
        <div class="canvas-tool center-top">
          <div class="left-handler">
            <el-icon
              class="handler-item"
              :class="{ 'disable': slideIndex === 0 }"
              title="上一页"
              @click="turnPrevSlide"
            >
              <CaretLeft />
            </el-icon>
            <span class="page-text">{{ slideIndex + 1 }} / {{ slides.length }}</span>
            <el-icon
              class="handler-item"
              :class="{ 'disable': slideIndex === slides.length - 1 }"
              title="下一页"
              @click="turnNextSlide"
            >
              <CaretRight />
            </el-icon>
          </div>

          <div class="right-handler">
            <el-icon class="handler-item viewport-size" title="缩小" @click="scaleCanvas('-')">
              <Minus />
            </el-icon>
            <el-popover
              v-model:visible="canvasScaleVisible"
              placement="bottom"
              trigger="click"
              :width="70"
            >
              <template #reference>
                <span class="text" @click="canvasScaleVisible = true">{{ canvasScalePercentage }}</span>
              </template>
              <div class="viewport-size-preset">
                <div
                  class="preset-item"
                  v-for="item in canvasScalePresetList"
                  :key="item"
                  @click="applyCanvasPresetScale(item)"
                >{{ item }}%</div>
              </div>
            </el-popover>
            <el-icon class="handler-item viewport-size" title="放大" @click="scaleCanvas('+')">
              <Plus />
            </el-icon>
            <el-icon class="handler-item viewport-size-adaptation" title="适应屏幕" @click="resetCanvas()">
              <FullScreen />
            </el-icon>
          </div>
        </div>

        <div class="canvas center-body" ref="canvasWrapRef" @wheel="handleMousewheelCanvas">
          <el-empty
            v-if="!currentSlide"
            description="暂无幻灯片，请导入 PPT 数据"
            :image-size="60"
          />
          <div
            v-else
            class="viewport-wrapper"
            :style="{
              width: viewportWidth + 'px',
              height: viewportHeight + 'px',
              left: viewportLeft + 'px',
              top: viewportTop + 'px',
            }"
          >
            <ScreenSlide
              :slide="currentSlide"
              :scale="canvasScale"
              :animation-index="Number.MAX_SAFE_INTEGER"
              :turn-slide-to-id="turnSlideToId"
              :manual-exit-fullscreen="noop"
            />
          </div>
        </div>

        <!-- 备注区：center-bottom（参考 PPTist Remark，可拖拽调整高度，编辑后点击保存写回当前页） -->
        <div class="remark center-bottom" :style="{ height: remarkHeight + 'px' }">
          <div class="resize-handler" @mousedown="resizeRemark($event)"></div>
          <!-- AI 优化本页备注：常显在备注区右上角（「保存」只在有改动时出现，两者互不挤占） -->
          <el-tooltip content="AI 优化本页备注（生成演讲稿）" placement="top" :show-after="300">
            <button
              type="button"
              class="remark-ai"
              :class="{ 'is-running': aiRunning }"
              :disabled="aiRunning || !currentSlide"
              @click="optimizeCurrentRemark"
            >
              <AiSparkIcon :size="16" />
            </button>
          </el-tooltip>
          <textarea
            v-model="remarkDraft"
            :placeholder="currentSlide ? '暂无演讲者备注，可在此编辑后保存' : ''"
            :disabled="!currentSlide"
          ></textarea>
          <Transition name="el-fade-in">
            <div v-if="remarkChanged" class="remark-actions">
              <el-button
                type="primary"
                size="small"
                @click="saveRemark"
              >
                <el-icon class="btn-icon"><DocumentChecked /></el-icon>
                保存
              </el-button>
            </div>
          </Transition>
        </div>
      </div>

      <!-- 右侧：数字人展示页面占位（参考 PPTist 布局：layout-content-right） -->
      <div class="toolbar layout-content-right" :style="{ width: sidebarWidth + 'px' }">
        <!-- <div class="resize-handler" @mousedown="resizeSidebar($event)"></div> -->
        <div
          class="digital-human-placeholder"
          :class="{ 'screening-mode': screening, 'is-dragging': draggingAvatar }"
          @pointerdown="startAvatarDrag"
          @dblclick="resetAvatarDrag"
        >
          <!-- 位移只加在 canvas 上：拖动的是数字人本身，切换按钮留在原位 -->
          <canvas
            ref="canvasRef"
            class="digital-human-canvas"
            :style="screening ? { transform: `translate3d(${avatarDragOffset.x}px, ${avatarDragOffset.y}px, 0)` } : null"
          />

          <!-- 形象切换：点一下按登记表顺序换下一个，加载不出来的自动跳过 -->
          <button
            type="button"
            class="avatar-switch"
            :class="{ 'is-loading': switchingAvatar }"
            :disabled="switchingAvatar"
            :title="`当前：${currentAvatarLabel}，点击切换下一个`"
            @click="switchAvatar"
          >
            <el-icon class="avatar-switch__icon"><Refresh /></el-icon>
            <span class="avatar-switch__label">{{ currentAvatarLabel }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 放映模式 -->
    <ScreenView v-if="screening" />

    <!-- AI 讲稿对比弹窗：单页（备注区星形按钮）/ 整篇（工具栏按钮） -->
    <AiRemarkDialog
      v-model="aiSingle.visible"
      :page-label="`第 ${aiSingle.pageIndex} 页`"
      :original="aiSingle.original"
      :remark="aiSingle.remark"
      :chars="aiSingle.chars"
      :seconds="aiSingle.seconds"
      :regenerating="aiSingle.regenerating"
      @apply="applyCurrentRemark"
      @regenerate="regenerateCurrentRemark"
    />
    <AiRemarkBatchDialog
      v-model="aiBatch.visible"
      :pages="aiBatch.pages"
      :retrying="aiBatch.retrying"
      @apply="applyBatchRemarks"
      @retry-failed="retryFailedRemarks"
    />

    <!-- 创建会议：确认后本页立刻进放映（被共享的就是这一屏），控制台另开一个标签页 -->
    <el-dialog v-model="meeting.visible" title="创建会议" width="480px">
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="会议标题">
          <el-input
            v-model="meeting.title"
            placeholder="如：企业级应用开发综合实践 第 3 讲"
            maxlength="100"
            show-word-limit
            @keyup.enter="handleCreateMeeting"
          />
        </el-form-item>
      </el-form>
      <!-- 没有讲稿不阻断建会（无闸门），只把页码列出来：讲稿就是备注，会议的画面是这份 PPT 本身 -->
      <p v-if="pagesWithoutRemark.length" class="meeting-dialog__hint">
        第 {{ pagesWithoutRemark.slice(0, 8).join('、') }} 页还没有讲稿（备注）<template v-if="pagesWithoutRemark.length > 8">等 {{ pagesWithoutRemark.length }} 页</template>：不影响开会，建议先补上。
      </p>
      <template #footer>
        <el-button @click="meeting.visible = false">取消</el-button>
        <el-button type="primary" :loading="meeting.creating" @click="handleCreateMeeting">
          创建并开始
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CaretLeft, CaretRight, DocumentChecked, FullScreen, Microphone, Minus, Monitor, Plus, Refresh, UploadFilled, VideoCamera, VideoPlay,
} from '@element-plus/icons-vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'
import { convertPPTXToPPTist } from 'pptx-to-pptist'
import { useSlidesStore } from './stores/slides'
import { useScreenStore } from './stores/screen'
import { useRoute, useRouter } from 'vue-router'
import { VIEWPORT_SIZE } from './configs/canvas'
import { AVATAR_MODEL_IDS, DEFAULT_AVATAR_MODEL, FALLBACK_AVATAR_MODEL, applyFraming, getAvatarModelConfig, loadAvatarModel, resolveMouthParamId } from './configs/avatar'
import useScreening from './hooks/useScreening'

import './styles/prosemirror.css'
import './styles/animation.css'

import { optimizeSpeech } from '@/api/ppt'
import { createMeeting } from '@/api/meeting'
import { readHostSession, writeHostSession } from '@/utils/hostSession'
import { readEventStream } from '@/utils/event-stream'
import { createLessonRunner } from './utils/lesson-runner'
import { createSpeechPlayer } from './utils/speech-player'
import { buildSpeechPages, extractSlideTitle, htmlToPlainText } from './utils/slide-text'
import ThumbnailSlide from './components/thumbnail/ThumbnailSlide.vue'
import ScreenSlide from './components/screen/ScreenSlide.vue'
import ScreenView from './screen/ScreenView.vue'
import AiSparkIcon from './components/ai/AiSparkIcon.vue'
import AiRemarkDialog from './components/ai/AiRemarkDialog.vue'
import AiRemarkBatchDialog from './components/ai/AiRemarkBatchDialog.vue'

if (typeof window !== 'undefined' && typeof window.Buffer === 'undefined') {
  window.Buffer = { isBuffer: () => false }
}

const slidesStore = useSlidesStore()
const screenStore = useScreenStore()
const { slides, slideIndex, viewportRatio, currentSlide } = storeToRefs(slidesStore)
const { screening } = storeToRefs(screenStore)
const { enterScreening, enterScreeningFromStart } = useScreening()
const route = useRoute()
const router = useRouter()

/**
 * 由路由参数进入放映：`/index/ppt?screen=1`
 *
 * <p>为「创建会议后自动打开的放映页」准备：老师在这个窗口放映，学生通过会议实时通道看画面。
 * 进会议总是从头讲，因此固定从第一页开始；首屏没有用户手势，浏览器的全屏请求会被拒绝
 * （`enterFullscreen` 已吞掉该 rejection），这里只保证进入放映状态本身。
 */
const enterScreeningByQuery = () => {
  if (route.query.screen !== '1') return
  // 这个窗口同时是「被共享的页面」：标题写清楚，浏览器共享选择框里才认得出它
  document.title = 'PPT 放映页'
  enterScreeningFromStart()
}

onMounted(enterScreeningByQuery)
// 同一个标签页被再次导航到 `?screen=1` 时组件不会重新挂载，因此同时监听参数变化
watch(() => route.query.screen, enterScreeningByQuery)

// ===== 讲稿播报（本页出声，声音随共享轨到学生端）=====
// 声音的终点就是这一页：它正被屏幕共享，学生听到的就是这里放出来的 PCM（docs/16 §5.7）。
// 因此播报能力长在放映页，触发有两条路：
// ① 控制台点「开始演讲」→ 经 BroadcastChannel 把文本送过来（按钮在另一个标签页，中间必须有条通道）；
// ② 本页「试听本页」：彩排时没有控制台，直接念当前页的讲稿。
// 「停止」= 掐断请求：后端合成随之取消（写不出去即停），播放器也立刻安静。

/** 播报接口：与讲稿生成同口径（`/ppt/**` 免登录，故带 `/api` 前缀走 Vite 代理） */
const SPEAK_PATH = '/api/ppt/speech/speak'
/** 播报总线：一个会议一条频道，与主持人凭据的存储键同形（`tb-meeting-host:{会议号}`） */
const CHANNEL_PREFIX = 'tb-meeting:'

/** 本页是否正在念讲稿（按钮文案与防重复点击用） */
const speaking = ref(false)
/** 数字人口型开合度 0–1：由**本页正在播的音频**算出（不是远端哪条轨） */
const mouthLevel = ref(0)

const speechPlayer = createSpeechPlayer({
  onLevel: (level) => {
    mouthLevel.value = level
  },
  onDrained: () => lessonRunner.notifyDrained(),
})

/** 当前播报的中断句柄：停止演讲 / 念下一段 / 离开页面都靠它取消合成 */
let speakAbort = null
/** 正在监听的总线（换会议号要换频道） */
let bus = null

/** 本页所属的会议号：由创建会议或 `?meetid=` 带进来；没有就是纯彩排 */
const presentingMeetingNo = computed(() => String(route.query.meetid ?? '').trim())

/** 停止播报：掐请求 + 立刻安静（播放器会把已排期与队列里的音频一起丢掉） */
function stopSpeaking() {
  speakAbort?.abort()
  speakAbort = null
  speechPlayer.stop()
  speaking.value = false
}

/**
 * 核心流式播报：发 `POST /ppt/speech/speak`，把 SSE 的 PCM 喂给播放器。
 *
 * <p>`keepAudio=false`（替换模式，试听 / 控制台念一段用）：先停掉上一段——两段声音不叠；
 * `keepAudio=true`（接续模式，播报队列用）：不停上一段直接排期，speechPlayer 的排期时钟
 * 把新段无缝接在上一段之后（前提：上一段的流已正常结束——队列的串行保证）。
 *
 * <p>resolve = 流正常结束（合成完成、音频已排期）；reject = 失败或被取消。
 * 会议号与主持人凭据都给齐时才落库成「数字人发言」——彩排试听不该出现在会议记录里。
 *
 * @param {string} text 要念的文本
 * @param {{ keepAudio?: boolean }} [options]
 */
async function speakStream(text, { keepAudio = false } = {}) {
  const content = (text ?? '').trim()
  if (!content) return
  // 替换模式才掐上一段；接续模式由排期时钟无缝续上（队列保证上一段流已结束）
  if (!keepAudio) stopSpeaking()
  const controller = new AbortController()
  speakAbort = controller
  speaking.value = true

  const meetingNo = presentingMeetingNo.value
  const hostToken = meetingNo ? readHostSession(meetingNo)?.hostToken : ''
  try {
    await readEventStream(SPEAK_PATH, {
      body: { text: content, meetingNo: meetingNo || undefined, hostToken: hostToken || undefined },
      signal: controller.signal,
      onEvent: (event, payload) => {
        if (event === 'chunk') {
          speechPlayer.feed(payload)
          return
        }
        if (event === 'error') {
          // 后端明确的失败：统一走 catch（提示 + 上抛给调用方）
          throw new Error(payload?.message || '语音合成失败')
        }
      },
    })
    // 流正常读完 = 这一段的合成全部结束：onDrained 双条件的另一半就位
    speechPlayer.markStreamDone()
  } catch (error) {
    // 自己取消的（停止演讲 / 换一段 / 关页面）不是错误，别报给老师
    if (!controller.signal.aborted) ElMessage.error(error?.message || '播报失败')
    throw error
  } finally {
    if (speakAbort === controller) {
      speakAbort = null
      speaking.value = false
    }
  }
}

/**
 * 念一段讲稿（试听 / 控制台「念一段」用，替换模式）。
 * 空文本的提示拦在这里——speakStream 面向队列，空段在队列侧已过滤。
 */
async function speak(text) {
  const content = (text ?? '').trim()
  if (!content) {
    ElMessage.warning('这一页还没有讲稿：先写备注，或用备注区右上角的 AI 生成')
    return
  }
  try {
    await speakStream(content)
  } catch {
    // 失败已在 speakStream 里提示过；被取消的静默
  }
}

// ===== 讲课工作项（P0：manual 跟随照读 + 能停）=====
const lessonRunner = createLessonRunner({
  // 段播报走接续模式：runner 保证串行（上一段流结束才发下一段），speechPlayer 无缝排期
  speakSegment: (segment) => speakStream(segment.text, { keepAudio: true }),
  stopSpeaking,
  // 照读的文本与备注区同一口径：pptx 解析出的 HTML 统一转纯文本
  getPageText: (page) => htmlToPlainText(slides.value[page]?.remark ?? '').trim(),
  onEvent: (event) => {
    if (event.type === 'page-start') console.info('[lesson] 开始讲第 ' + (event.page + 1) + ' 页（共 ' + event.segments + ' 段）')
    if (event.type === 'page-done') console.info('[lesson] 第 ' + (event.page + 1) + ' 页讲完')
    if (event.type === 'page-failed') console.warn('[lesson] 第 ' + (event.page + 1) + ' 页播报受阻：' + event.segmentId + '（' + event.message + '）')
    if (event.type === 'page-skipped') console.info('[lesson] 第 ' + (event.page + 1) + ' 页没有讲稿，跳过')
  },
})

/** 试听本页（彩排用，不需要会议）：读的是备注区草稿，未保存的改动也能先听一遍 */
function speakCurrentPage() {
  speak(remarkDraft.value)
}

/**
 * 接住控制台的播报指令。
 *
 * <p>为什么用总线而不是接口：声音只能在**这一页**播（只有它被共享），而按钮在控制台那个标签页。
 * 指令只做单向（控制台 → 本页）；页码反向由本页广播（postPageToConsole：页面真相在本页，说给控制台听）。
 * 换会议号就换频道，上一场会的指令打不进来。
 */
function watchConsole() {
  bus?.close()
  bus = null
  const meetingNo = presentingMeetingNo.value
  if (!meetingNo || typeof BroadcastChannel === 'undefined') return
  bus = new BroadcastChannel(`${CHANNEL_PREFIX}${meetingNo}`)
  bus.onmessage = (event) => {
    const message = event?.data ?? {}
    if (message.action === 'speak') speak(message.text)
    if (message.action === 'stop') stopSpeaking()
    // 讲课工作项（P0）：启动 = 跟随老师翻页照读当前页 remark；停止 = 作废一切并立刻安静。
    // 既有 speak/stop 原样保留（协议向后兼容：手输播报与试听路径不变）
    if (message.action === 'lesson-start') lessonRunner.start(slideIndex.value)
    if (message.action === 'lesson-stop') {
      // 工作项与「念一段」一起停：手输播报的「停止演讲」也走 lesson-stop，runner 未运行时 stop 是空操作
      lessonRunner.stop()
      stopSpeaking()
    }
  }
}

/**
 * 页码上行（页面真相）：翻页即向总线广播，控制台才知道「现在讲第几页」（补 G2）。
 * 广播沿用「每条消息一个短命频道」的惯例；放映页自己的 bus 也会收到这条消息，
 * onmessage 没有 page 分支，天然忽略。
 */
function postPageToConsole(page) {
  const meetingNo = presentingMeetingNo.value
  if (!meetingNo || typeof BroadcastChannel === 'undefined') return
  const channel = new BroadcastChannel(CHANNEL_PREFIX + meetingNo)
  channel.postMessage({ action: 'page', page, pageCount: slides.value.length })
  channel.close()
}

// 翻页的两件事：页码上行 + 工作项跟随换页（未启动时 runner 自己忽略）
watch(slideIndex, (page) => {
  postPageToConsole(page)
  lessonRunner.onPageChanged(page)
})

// `immediate` 让「带着 ?meetid= 刷新」也能挂上这一场的总线
watch(presentingMeetingNo, watchConsole, { immediate: true })

// ===== 创建会议 =====
// 建会后**本页就是被共享的那一屏**：控制台是「名单 + 聊天」，而 `.ppt-screen-view` 是
// `z-index:9999` 的全屏覆盖层，侧栏放在这里一定会被学生看到，所以控制台只能另开一个标签页。

/** 控制台路由：相对路径，自动跟随当前 origin（localhost 或热点下的局域网 IP 都能打开） */
const CONSOLE_PATH = '/index/meeting'

const meeting = reactive({ visible: false, title: '', creating: false })
/** 控制台标签页句柄：同一浏览器会话内复用，避免每建一次会就多开一个页签 */
let consoleTab = null

/** 还没有讲稿（备注）的页码，1 基；只作建会前的提示，**不阻断**（见 docs/16 §5.6） */
const pagesWithoutRemark = computed(() => slides.value
  .map((slide, index) => (htmlToPlainText(slide.remark ?? '').trim() ? 0 : index + 1))
  .filter(Boolean))

function openMeetingDialog() {
  meeting.visible = true
}

/** 控制台地址：`credential=1` 让控制台提示一次「凭据只此一次」，提示完它自己会把参数摘掉 */
function consoleUrl(meetingNo) {
  return `${CONSOLE_PATH}?meetid=${meetingNo}&credential=1`
}

/**
 * 创建会议并开始讲。
 *
 * <p>顺序不能变：`window.open` 必须在**点击的同步流程里**执行（过了 `await` 会被弹窗拦截器拦掉）。
 * 拿不到控制台标签页就整件事失败，**绝不能先进放映**——否则老师已经全屏讲义，
 * 而提示被那个覆盖层压在下面，什么也看不到。
 *
 * <p>共享仍由控制台点「开始共享」发起（`selfBrowserSurface: 'exclude'` 会把控制台自己排除在候选外，
 * 老师只能选到本页）。这里只把本页切进放映、并把标题改成「PPT 放映页」——共享选择框里靠它认页面。
 */
async function handleCreateMeeting() {
  const title = meeting.title.trim()
  if (!title) {
    ElMessage.warning('请先填写会议标题')
    return
  }
  if (!consoleTab || consoleTab.closed) consoleTab = window.open('about:blank', '_blank')
  meeting.creating = true
  try {
    const created = await createMeeting(title)
    // 凭据只随 create 下发一次（ADR-0003）：先落本机，控制台标签页靠它恢复这一场
    writeHostSession({ ...created, title })
    const url = consoleUrl(created.meetingNo)
    if (!consoleTab || consoleTab.closed) {
      throw new Error(`浏览器拦截了新标签页：请允许本站弹出窗口，或手动打开 ${url}`)
    }
    meeting.visible = false
    // 地址带上会议号：刷新 / 重新打开这条链接时，放映页才知道自己在哪一场（会议号不是秘密，凭据不进 URL）。
    // 这次 replace 会命中 `enterScreeningByQuery`（进放映 + 把标题改成「PPT 放映页」，共享选择框里靠它认页面），
    // 同时让本页挂上这一场的播报总线（见 `watchConsole`）。
    await router.replace({ query: { screen: '1', meetid: created.meetingNo } })
    consoleTab.location.replace(url)
    consoleTab.focus?.()
    ElMessage.success(`会议已创建：${created.meetingNo}。到控制台点「开始共享」并选中「PPT 放映页」`)
  } catch (error) {
    // 建会失败（或标签页没开出来）就别留一个空白页签
    consoleTab?.close?.()
    ElMessage.error(error?.message || '创建会议失败')
  } finally {
    meeting.creating = false
  }
}

const fileInputRef = ref(null)

// 缩略图点击切换页面
const updateSlideIndex = (index) => {
  slidesStore.updateSlideIndex(index)
}

// 展示区翻页
const turnPrevSlide = () => {
  if (slideIndex.value > 0) slidesStore.updateSlideIndex(slideIndex.value - 1)
}
const turnNextSlide = () => {
  if (slideIndex.value < slides.value.length - 1) slidesStore.updateSlideIndex(slideIndex.value + 1)
}

// 非放映状态下无全屏概念，提供无操作占位函数
const noop = () => {}

// ===== 画布缩放与视口定位（参考 PPTist useViewportSize / useScaleCanvas）=====
const canvasWrapRef = ref(null)
const canvasPercentage = ref(90) // 画布可视区域百分比（PPTist 默认值）
const canvasScale = ref(1)
const viewportLeft = ref(0)
const viewportTop = ref(0)
const canvasScaleVisible = ref(false)
const canvasScalePresetList = [200, 150, 100, 80, 50]

// 根据画布区域尺寸与百分比计算缩放比例及视口居中位置
const updateViewport = () => {
  if (!canvasWrapRef.value) return
  const canvasWidth = canvasWrapRef.value.clientWidth
  const canvasHeight = canvasWrapRef.value.clientHeight
  if (!canvasWidth || !canvasHeight) return

  if (canvasHeight / canvasWidth > viewportRatio.value) {
    const viewportActualWidth = canvasWidth * (canvasPercentage.value / 100)
    canvasScale.value = viewportActualWidth / VIEWPORT_SIZE
    viewportLeft.value = (canvasWidth - viewportActualWidth) / 2
    viewportTop.value = (canvasHeight - viewportActualWidth * viewportRatio.value) / 2
  }
  else {
    const viewportActualHeight = canvasHeight * (canvasPercentage.value / 100)
    canvasScale.value = viewportActualHeight / (VIEWPORT_SIZE * viewportRatio.value)
    viewportLeft.value = (canvasWidth - viewportActualHeight / viewportRatio.value) / 2
    viewportTop.value = (canvasHeight - viewportActualHeight) / 2
  }
}

watch([canvasPercentage, viewportRatio], updateViewport)

let canvasResizeObserver = null

onMounted(() => {
  updateViewport()
  canvasResizeObserver = new ResizeObserver(updateViewport)
  if (canvasWrapRef.value) canvasResizeObserver.observe(canvasWrapRef.value)
  initLive2D()
})
// 放在 beforeUnmount：此时 canvas 仍在 DOM 中，PIXI 可安全释放渲染器与 WebGL 上下文
onBeforeUnmount(() => {
  endAvatarDrag()
  destroyLive2D()
  // 离开 PPT 页就别再念了：掐断请求即取消后端合成，随后收掉播放器与总线
  stopSpeaking()
  lessonRunner.stop()
  bus?.close()
  speechPlayer.destroy()
  // 离开 PPT 页就别再等讲稿了（后端仍会把当前批次跑完，这是无状态接口的固有代价）
  aiAbortController?.abort()
})
onUnmounted(() => canvasResizeObserver && canvasResizeObserver.disconnect())

const viewportWidth = computed(() => VIEWPORT_SIZE * canvasScale.value)
const viewportHeight = computed(() => VIEWPORT_SIZE * viewportRatio.value * canvasScale.value)
const canvasScalePercentage = computed(() => Math.round(canvasScale.value * 100) + '%')

// 缩放画布百分比（参考 PPTist）
const scaleCanvas = (command) => {
  let percentage = canvasPercentage.value
  const step = 5
  const max = 200
  const min = 30
  if (command === '+' && percentage <= max) percentage += step
  if (command === '-' && percentage >= min) percentage -= step
  canvasPercentage.value = percentage
}

// 设置目标画布缩放比例（通过换算画布可视区域百分比实现，参考 PPTist）
const applyCanvasPresetScale = (value) => {
  canvasPercentage.value = Math.round(value / canvasScale.value * canvasPercentage.value) / 100
  canvasScaleVisible.value = false
}

// 重置画布缩放（适应屏幕）
const resetCanvas = () => {
  canvasPercentage.value = 90
}

// 鼠标滚轮：Ctrl + 滚轮缩放画布，普通滚轮切换页面（参考 PPTist Canvas 交互）
let wheelTurnTimer = null
const handleMousewheelCanvas = (e) => {
  e.preventDefault()
  if (e.ctrlKey) {
    if (e.deltaY > 0) scaleCanvas('-')
    else if (e.deltaY < 0) scaleCanvas('+')
    return
  }
  if (wheelTurnTimer) return
  if (e.deltaY > 0) turnNextSlide()
  else if (e.deltaY < 0) turnPrevSlide()
  wheelTurnTimer = setTimeout(() => { wheelTurnTimer = null }, 300)
}

// ===== 备注区（参考 PPTist Remark，可编辑当前页备注、拖拽调整高度）=====
const remarkHeight = ref(100)

// 备注编辑草稿：随当前页切换自动同步；内容有变化时才显示「保存」按钮，保存后按钮消失
const remarkDraft = ref('') // 正在编辑的内容
const remarkOriginal = ref('') // 当前页已保存的内容
const remarkChanged = computed(() => remarkDraft.value !== remarkOriginal.value)

watch(() => currentSlide.value?.id, () => {
  // pptx 解析出的备注是 HTML 段落（如 <p style="text-align:left;">…</p>），统一转纯文本
  // （与正文提取共用 utils/slide-text.js 的同一份实现）
  const text = htmlToPlainText(currentSlide.value?.remark)
  remarkDraft.value = text
  remarkOriginal.value = text
}, { immediate: true })

const saveRemark = () => {
  if (!currentSlide.value || !remarkChanged.value) return
  slidesStore.updateSlide({ remark: remarkDraft.value })
  remarkOriginal.value = remarkDraft.value
  ElMessage.success('备注已保存')
}

const resizeRemark = (e) => {
  let isMouseDown = true
  const startPageY = e.pageY
  const originHeight = remarkHeight.value

  document.onmousemove = (ev) => {
    if (!isMouseDown) return
    const moveY = ev.pageY - startPageY
    let newHeight = -moveY + originHeight
    if (newHeight < 40) newHeight = 40
    if (newHeight > 120) newHeight = 120
    remarkHeight.value = newHeight
  }
  document.onmouseup = () => {
    isMouseDown = false
    document.onmousemove = null
    document.onmouseup = null
  }
}

// ===== AI 讲稿优化（备注区星形按钮 = 本页；工具栏按钮 = 全部）=====
/** 本场时长（分钟）：不再做成输入项，按此固定值均摊成每页目标字数（后端按 4.0 字/秒换算） */
const AI_TARGET_MINUTES = 8
/** 跑批中：两个按钮 disabled（并发跑批在演示里只会有害） */
const aiRunning = ref(false)
/** 最近一次 AI 应用的可撤销快照：采纳前把原备注留在内存，采纳后可一键还原 */
const aiUndo = ref(null)

const aiSingle = reactive({
  visible: false, pageIndex: 0, slideId: '', original: '', remark: '', chars: 0, seconds: 0, regenerating: false,
})
const aiBatch = reactive({ visible: false, pages: [], retrying: false })
/** 当前请求的中止句柄：离开 PPT 页 / 切路由时中止等待（后端仍会把当前批次跑完，见 docs/16 风险表） */
let aiAbortController = null

/** 目标总时长（秒）：非法或非正数就不传，后端按「不约束篇幅」处理 */
const aiTargetSeconds = () => {
  const minutes = Number(AI_TARGET_MINUTES)
  return Number.isFinite(minutes) && minutes > 0 ? Math.round(minutes * 60) : undefined
}

/**
 * 请求前的快照：页码 → { slideId, title, original }。
 *
 * 回填一律按 `slideId`（不按「当前页」），因此老师在等待期间翻页、甚至切到别的页，也不会写错页。
 */
const snapshotOf = (entries) => entries.map(({ index, slide }) => ({
  index,
  slideId: slide.id,
  title: extractSlideTitle(slide) || `第 ${index} 页`,
  original: htmlToPlainText(slide.remark),
}))

async function requestSpeech(pages) {
  aiAbortController?.abort()
  aiAbortController = new AbortController()
  return optimizeSpeech({ targetSeconds: aiTargetSeconds(), pages }, aiAbortController.signal)
}

/** 主动中止不算失败：老师切页/离开时必然发生一次，不该弹红字 */
const isAborted = (error) => error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED'

/** 生成一页讲稿：单页按钮、整篇里的补生成、单页「重新生成」都走这里，保证输入口径一致 */
async function generatePageScript(slide, index) {
  const [page] = buildSpeechPages([{ index, slide }])
  const result = await requestSpeech([page])
  return result?.pages?.[0] ?? null
}

/** 当前页被改写时同步备注区草稿与「已保存」基准（否则面板上还是旧备注） */
function syncRemarkDraft(slideId, remark) {
  if (slideId !== currentSlide.value?.id) return
  remarkDraft.value = remark
  remarkOriginal.value = remark
}

/** 备注区星形按钮：优化当前页备注 */
const optimizeCurrentRemark = async () => {
  const slide = currentSlide.value
  if (!slide) {
    ElMessage.warning('请先导入 PPT 数据')
    return
  }
  const index = slideIndex.value + 1
  const original = htmlToPlainText(slide.remark)
  aiRunning.value = true
  try {
    const item = await generatePageScript(slide, index)
    if (!item?.remark) {
      ElMessage.warning('这一页没能生成讲稿，可以再点一次试试')
      return
    }
    Object.assign(aiSingle, {
      visible: true, pageIndex: index, slideId: slide.id, original,
      remark: item.remark, chars: item.chars, seconds: item.seconds, regenerating: false,
    })
  } catch (error) {
    if (!isAborted(error)) ElMessage.error(error?.message || '讲稿生成失败')
  } finally {
    aiRunning.value = false
  }
}

/** 单页弹窗的「重新生成」：仍以**原备注**为输入，避免一轮轮把 AI 的措辞越改越远 */
const regenerateCurrentRemark = async () => {
  const slide = slides.value.find(item => item.id === aiSingle.slideId)
  if (!slide) return
  aiSingle.regenerating = true
  try {
    const item = await generatePageScript(slide, aiSingle.pageIndex)
    if (item?.remark) {
      Object.assign(aiSingle, { remark: item.remark, chars: item.chars, seconds: item.seconds })
      ElMessage.success('已重新生成')
    } else {
      ElMessage.warning('这次没有生成成功，可以再试一次')
    }
  } catch (error) {
    if (!isAborted(error)) ElMessage.error(error?.message || '讲稿生成失败')
  } finally {
    aiSingle.regenerating = false
  }
}

/** 采纳单页讲稿：按 slideId 写回 */
const applyCurrentRemark = (remark) => {
  slidesStore.updateSlideById(aiSingle.slideId, { remark })
  syncRemarkDraft(aiSingle.slideId, remark)
  aiUndo.value = { pages: [{ slideId: aiSingle.slideId, original: aiSingle.original }] }
  aiSingle.visible = false
  ElMessage.success('已应用 AI 讲稿')
}

/** 工具栏按钮：优化全部页备注（一次请求，后端内部按批跑） */
const optimizeAllRemarks = async () => {
  const entries = slides.value.map((slide, i) => ({ index: i + 1, slide }))
  if (!entries.length) {
    ElMessage.warning('请先导入 PPT 数据')
    return
  }
  // 跑之前确认一次：整篇会改写所有页的备注；覆盖前留快照，采纳后可一键撤销
  const confirmed = await ElMessageBox.confirm(
    `将对全部 ${entries.length} 页生成讲稿，AI 会改写这些页现有的备注。生成完成后可以逐页挑选，也可以一键撤销。`,
    'AI 优化全部备注',
    { type: 'warning', confirmButtonText: '开始生成', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  const snapshot = snapshotOf(entries)
  aiRunning.value = true
  try {
    const result = await requestSpeech(buildSpeechPages(entries))
    const byIndex = new Map((result?.pages ?? []).map(item => [item.index, item]))
    aiBatch.pages = snapshot.map(item => {
      const generated = byIndex.get(item.index)
      return {
        index: item.index,
        slideId: item.slideId,
        title: item.title,
        original: item.original,
        remark: generated?.remark ?? '',
        chars: generated?.chars ?? 0,
        seconds: generated?.seconds ?? 0,
        failed: !generated?.remark,
      }
    })
    aiBatch.visible = true
  } catch (error) {
    if (!isAborted(error)) ElMessage.error(error?.message || '讲稿生成失败')
  } finally {
    aiRunning.value = false
  }
}

/** 采纳整篇里勾选的页 */
const applyBatchRemarks = (indexes) => {
  const selected = new Set(indexes)
  const applied = aiBatch.pages.filter(page => selected.has(page.index) && !page.failed)
  applied.forEach(page => {
    slidesStore.updateSlideById(page.slideId, { remark: page.remark })
    syncRemarkDraft(page.slideId, page.remark)
  })
  aiUndo.value = { pages: applied.map(page => ({ slideId: page.slideId, original: page.original })) }
  aiBatch.visible = false
  ElMessage.success(`已应用 ${applied.length} 页 AI 讲稿`)
}

/** 只重试失败的页：不重跑全篇，避免又等一两分钟 */
const retryFailedRemarks = async () => {
  const entries = aiBatch.pages
    .filter(page => page.failed)
    .map(page => ({ index: page.index, slide: slides.value.find(item => item.id === page.slideId) }))
    .filter(entry => entry.slide)
  if (!entries.length) return
  aiBatch.retrying = true
  try {
    const result = await requestSpeech(buildSpeechPages(entries))
    const byIndex = new Map((result?.pages ?? []).map(item => [item.index, item]))
    let recovered = 0
    aiBatch.pages = aiBatch.pages.map(page => {
      const generated = byIndex.get(page.index)
      if (!generated?.remark) return page
      recovered += 1
      return { ...page, remark: generated.remark, chars: generated.chars, seconds: generated.seconds, failed: false }
    })
    if (recovered) ElMessage.success(`已补生成 ${recovered} 页`)
    else ElMessage.warning('补生成仍未成功，可以稍后再试')
  } catch (error) {
    if (!isAborted(error)) ElMessage.error(error?.message || '重试失败')
  } finally {
    aiBatch.retrying = false
  }
}

/** 一键撤销：只回滚被采纳过的那几页 */
const undoAiRemarks = () => {
  const pages = aiUndo.value?.pages ?? []
  pages.forEach(page => {
    slidesStore.updateSlideById(page.slideId, { remark: page.original })
    syncRemarkDraft(page.slideId, page.original)
  })
  aiUndo.value = null
  ElMessage.success('已还原到 AI 优化前的备注')
}

// 右侧工具栏宽度
const sidebarWidth = ref(400)

// 幻灯片内跳转链接
const turnSlideToId = (id) => {
  const index = slides.value.findIndex(item => item.id === id)
  if (index >= 0) slidesStore.updateSlideIndex(index)
}

// 导入 PPT 数据（导录）：支持 .pptx 文件与 PPTist JSON 两种格式
const openFileDialog = () => {
  fileInputRef.value?.click()
}

const onFileChange = (e) => {
  const file = e.target.files?.[0]
  if (file) parseFile(file)
  e.target.value = ''
}

const parseFile = (file) => {
  const fileName = file.name.toLowerCase()
  const isPPTX = fileName.endsWith('.pptx')
  const isJSON = fileName.endsWith('.json') || file.type === 'application/json'

  if (!isPPTX && !isJSON) {
    ElMessage.warning('仅支持导入 .pptx 或 JSON 格式的 PPT 数据')
    return
  }

  // .pptx：解析为 PPTist JSON 数据后导入
  if (isPPTX) {
    const reader = new FileReader()
    reader.onload = async (e) => {
      try {
        const result = await convertPPTXToPPTist(e.target.result, {
          fixedViewport: true,
          viewportWidth: VIEWPORT_SIZE,
        })
        importSlideData(result)
      }
      catch {
        ElMessage.error('PPT 文件解析失败，请选择有效的 .pptx 文件')
      }
    }
    reader.readAsArrayBuffer(file)
    return
  }

  // JSON：直接读取导入
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = JSON.parse(e.target.result)
      importSlideData(data)
    }
    catch {
      ElMessage.error('文件解析失败，请选择有效的 PPT JSON 数据文件')
    }
  }
  reader.readAsText(file)
}

// 归一化导入的幻灯片数据，兼容 pptx-to-pptist 转换结果与旧版 JSON 数据的差异
const normalizeSlideData = (slides) => {
  return slides.map(slide => ({
    ...slide,
    // 页面切换不播放过渡动画（PPTist turning-mode-no 为瞬间切换）
    turningMode: 'no',
    elements: (slide.elements || []).map(el => {
      // 线条元素：pptx-to-pptist 输出的 start/end 为画布绝对坐标（已按 viewport 缩放一次），
      // left/top 亦为缩放后的包围盒坐标。旧版数据中 start/end 为相对坐标（端点
      // 必位于包围盒角上，故最小值恒为 0），据此区分并转换为相对坐标
      if (el.type !== 'line' || !Array.isArray(el.start) || !Array.isArray(el.end)) return el
      const [sx, sy] = el.start
      const [ex, ey] = el.end
      const isRelative = Math.min(sx, ex) === 0 && Math.min(sy, ey) === 0
      if (isRelative) return el
      const left = Math.min(sx, ex)
      const top = Math.min(sy, ey)
      const round2 = n => Math.round(n * 100) / 100
      return {
        ...el,
        left: round2(left),
        top: round2(top),
        start: [round2(sx - left), round2(sy - top)],
        end: [round2(ex - left), round2(ey - top)],
      }
    }),
  }))
}

// 解析并写入幻灯片数据（兼容 ppt-master 导出 JSON / pptx-to-pptist 转换结果）
const importSlideData = (data) => {
  if (!data || !Array.isArray(data.slides) || !data.slides.length) {
    ElMessage.error('导入数据无效：缺少 slides 数组')
    return
  }

  if (data.theme) slidesStore.setTheme(data.theme)

  // 视口比例：优先根据 width/height 计算，其次使用 viewportRatio 字段
  const ratioFromSize = data.width && data.height ? +(data.height / data.width).toFixed(4) : 0
  const ratio = ratioFromSize || data.viewportRatio
  if (ratio) slidesStore.setViewportRatio(ratio)

  slidesStore.setSlides(normalizeSlideData(data.slides))
  ElMessage.success(`导入成功，共 ${data.slides.length} 页幻灯片`)
}



// ===== 数字人（Live2D）=====
// pixi-live2d-display 依赖全局 PIXI 注册 Ticker，必须在任何 Live2DModel 使用前赋值
window.PIXI = PIXI

const canvasRef = ref(null)
let app = null // PIXI.Application 单实例
let model = null // Live2DModel 单实例
let live2dDisposed = false // 卸载标记：阻断「组件已卸载后才 resolve」的异步回调写入已销毁实例
let mouthParamId = null // 当前模型真正的嘴部参数 ID，加载后探测一次并复用

const currentAvatarId = ref(DEFAULT_AVATAR_MODEL) // 当前挂在舞台上的形象
const switchingAvatar = ref(false) // 切换进行中：防止连点把加载流程叠起来
const currentAvatarLabel = computed(() => getAvatarModelConfig(currentAvatarId.value).label)

// 放映时数字人的拖拽位移，相对「贴右 + 垂直居中」的默认位置；编辑模式下不生效
const avatarDragOffset = ref({ x: 0, y: 0 })
const draggingAvatar = ref(false)
let avatarDragState = null // { pointerX, pointerY, originX, originY, left, top, width, height }

/** 加载器：把 URL 变成 Live2DModel，交给 configs/avatar 决定先试哪个地址 */
const createLive2DModel = (url) => Live2DModel.from(url, {
  autoInteract: true,
  // 兜底地址在 jsDelivr，贴图属于跨域资源；不显式带 CORS，纹理进不了 WebGL 会直接黑屏
  crossOrigin: 'anonymous',
})

/** 大肥鱼作者留的「水印消除」开关参数，写 1 时模型内部把水印部件透明度压到 0 */
const WATERMARK_OFF_PARAM = 'Paramcol_3'

/**
 * 关掉模型自带的宣传水印。
 *
 * 大肥鱼身上印着「免费公皮请勿盗用售卖 / 作者@Mao绒绒家园 / UID xxx」，
 * 投到大屏上整页红字，讲课没法看。作者把水印做成了两个独立部件
 * （Part45 水印2.png / Part46 水印.png），并留了 Paramcol_3 这个开关和
 * 同名表情，这里走的是作者自己提供的关闭方式，不是抠图改素材。
 *
 * 不用 model.expression('13 水印消除')：表情默认有 1 秒淡入，
 * 起播那一下水印还清清楚楚地挂着，看起来就是「闪一下才消失」。
 * 改成挂在 beforeModelUpdate 上（coreModel.update() 之前最后一站）逐帧写，
 * 第一帧就是干净的。只写一次不行 —— Cubism 每帧会把参数还原，下一帧水印会回来。
 *
 * 调用方必须在 stage.addChild 之前调用，否则第一帧已经画出去了。
 *
 * 关的只是画面上的字，署名义务不因此消失 —— 答辩致谢与 NOTICE 里
 * 仍要写明 @Mao绒绒家园，商用另需作者授权。
 *
 * @param {any} target 已加载的 Live2DModel
 * @param {string} id 形象 id
 */
const applyWatermarkPolicy = (target, id) => {
  if (id !== 'DeepSeekDaFeiyu') return

  const internal = target.internalModel
  const coreModel = internal?.coreModel
  if (!coreModel) return

  const hide = () => {
    try {
      coreModel.setParameterValueById(WATERMARK_OFF_PARAM, 1)
    }
    catch {
      // 参数不存在时 Cubism 本来就不报错，这里只是兜住接口缺失的极端情况
    }
  }

  internal.on('beforeModelUpdate', hide)
  hide()
}

/**
 * 把口型开合度写给本页这份 Live2D（本页唯一会动嘴的入口）。
 *
 * <p>Cubism 4 才有 `setParameterValueById`；缺了就静默不动，不能让渲染循环抛错。
 * <p>参数名不能写死：鲸鱼娘（c_0120）的 model3.json 里 LipSync 组是空数组，
 * 硬写 `ParamMouthOpenY` 有可能是在给一个不存在的参数赋值，嘴不会动也不报错。
 *
 * @param {number} level 开合度 0–1，来自**本页正在播的音频**的逐帧响度
 */
const writeMouthOpen = (level) => {
  if (!mouthParamId) return
  const coreModel = model?.internalModel?.coreModel
  coreModel?.setParameterValueById?.(mouthParamId, Math.max(0, Math.min(1, level)))
}

/** 数字人一开口，这一屏的嘴就要跟着动 —— 学生看到的就是这一屏，听到的声音也从这里出去 */
watch(mouthLevel, writeMouthOpen)

// 完整释放：停渲染循环 → 销毁模型 → 销毁渲染器
// 路由 /index/ppt 每次进入都是新实例，若此处不销毁，旧 Ticker 会持续对已被浏览器回收的
// WebGL 上下文发起 bindBuffer/bindTexture，产生 INVALID_OPERATION 刷屏
const destroyLive2D = () => {
  live2dDisposed = true
  mouthParamId = null

  // 先停循环，避免销毁过程中 Ticker 仍在绘制
  if (app?.ticker) app.ticker.stop()

  if (model) {
    model.removeAllListeners()
    // 水印开关挂在 internalModel 的 beforeModelUpdate 上，这里一并摘掉
    model.internalModel?.removeAllListeners()
    model.destroy({ children: true, texture: true, baseTexture: true })
    model = null
  }

  if (app) {
    // removeView 传 false：canvas 由 Vue 托管，不能由 PIXI 从 DOM 中移除
    app.destroy(false, { children: true, texture: true, baseTexture: true })
    app = null
  }
}

/**
 * 按「首选形象 → 兜底形象」的顺序找一只能加载的模型。
 *
 * 首选是 DeepSeek 鲸鱼娘：先读 public/live2d/deepseek-whale（跑过 fetch 脚本才有），
 * 没有再走 jsDelivr。两处都拿不到就退回仓库里一定有的 Haru，
 * 保证放映现场不出现一块空白 —— 断网时至少还有个人站在那儿念稿。
 */
const loadPreferredAvatar = async () => {
  try {
    return await loadAvatarModel(createLive2DModel, DEFAULT_AVATAR_MODEL)
  }
  catch (e) {
    console.error(`[数字人] ${DEFAULT_AVATAR_MODEL} 加载失败，回退 ${FALLBACK_AVATAR_MODEL}`, e)
  }

  try {
    return await loadAvatarModel(createLive2DModel, FALLBACK_AVATAR_MODEL)
  }
  catch (e) {
    console.error('[数字人] 兜底形象也加载失败，右侧面板保持空白', e)
    return null
  }
}

const initLive2D = async () => {
  if (live2dDisposed || app || !canvasRef.value) return

  app = new PIXI.Application({
    view: canvasRef.value,
    width: 400,
    height: 800,
    // resizeTo: window,
    backgroundAlpha: 0,
    resolution: window.devicePixelRatio || 1,
    autoDensity: true,
  })

  const loaded = await loadPreferredAvatar()

  // 加载期间组件可能已卸载：此时 app 已销毁，丢弃模型而不是挂到失效实例上
  if (!loaded || live2dDisposed || !app) {
    loaded?.model.destroy({ children: true, texture: true, baseTexture: true })
    return
  }

  model = loaded.model
  currentAvatarId.value = loaded.id
  if (loaded.id !== DEFAULT_AVATAR_MODEL) {
    console.warn(`[数字人] 当前显示的是兜底形象 ${loaded.id}，DeepSeek 鲸鱼娘未能加载`)
  }
  // 取景按模型原始宽高算，换形象不需要再回来改数字
  applyFraming(
    model,
    { width: app.screen.width, height: app.screen.height },
    getAvatarModelConfig(loaded.id).framing,
  )
  // 必须赶在 addChild 之前：一旦上舞台就开始出帧，第一帧就会把水印画出去
  applyWatermarkPolicy(model, loaded.id)
  app.stage.addChild(model)

  mouthParamId = resolveMouthParamId(model.internalModel)
  if (!mouthParamId) console.warn('[数字人] 该模型没有嘴部开合参数，口型同步将被跳过')

  model.on('hit', () => {
    //  model.expression(4)
  })
}

/**
 * 把指定形象挂到舞台上，成功返回 true。
 *
 * 顺序是「先挂新的、再拆旧的」：新模型解析完之前旧模型留在原地，
 * 切换过程中右侧不会闪一下空白；代价是同一时刻短暂持两份贴图，可接受。
 *
 * @param {string} id 形象 id
 * @returns {Promise<boolean>} 是否挂载成功
 */
const mountAvatar = async (id) => {
  if (!app || live2dDisposed) return false

  const loaded = await loadAvatarModel(createLive2DModel, id)

  // 加载期间组件被卸载：app 已经没了，把刚拿到的模型直接丢掉
  if (!app || live2dDisposed) {
    loaded.model.destroy({ children: true, texture: true, baseTexture: true })
    return false
  }

  const previous = model
  model = loaded.model
  applyFraming(
    model,
    { width: app.screen.width, height: app.screen.height },
    getAvatarModelConfig(id).framing,
  )
  // 同上：先关水印再上舞台，避免切换瞬间闪一下
  applyWatermarkPolicy(model, id)
  app.stage.addChild(model)
  mouthParamId = resolveMouthParamId(model.internalModel)

  if (previous) {
    app.stage.removeChild(previous)
    previous.removeAllListeners()
    previous.internalModel?.removeAllListeners()
    previous.destroy({ children: true, texture: true, baseTexture: true })
  }

  return true
}

/**
 * 按登记表顺序切到下一个形象。
 *
 * 用一个形象要过「本地文件 → CDN」两关，任何一关拿到模型就算数；
 * 两关都失败的（例如没跑过取模型脚本）直接跳过，最多转一圈回到原点，
 * 免得用户点了按钮却像没反应。
 */
const switchAvatar = async () => {
  if (switchingAvatar.value || !app) return
  switchingAvatar.value = true

  const start = AVATAR_MODEL_IDS.indexOf(currentAvatarId.value)
  try {
    for (let step = 1; step <= AVATAR_MODEL_IDS.length; step++) {
      const id = AVATAR_MODEL_IDS[(start + step) % AVATAR_MODEL_IDS.length]
      try {
        if (await mountAvatar(id)) {
          currentAvatarId.value = id
          return
        }
      }
      catch (e) {
        console.warn(`[数字人] ${id} 加载失败，跳过`, e)
      }
    }
    ElMessage.warning('没有可以加载的形象，请确认 public/live2d 下的模型文件是否完整')
  }
  finally {
    switchingAvatar.value = false
  }
}

/** 拖动时至少留这么多像素留在视口内，避免把数字人拖丢找不回来 */
const AVATAR_DRAG_KEEP = 100

const clampValue = (value, min, max) => Math.min(Math.max(value, min), max)

/**
 * 开始拖动数字人（仅放映模式生效）。
 *
 * 事件挂在容器上，但真正能命中的只有 canvas：放映时整个面板是
 * `pointer-events: none`（为了不挡住翻页和工具栏），下面只把 canvas
 * 那一块收回 auto，四周的透明留白照样穿透。
 */
const startAvatarDrag = (e) => {
  if (!screening.value || !canvasRef.value) return
  // 只认左键，右键留给浏览器菜单
  if (e.pointerType === 'mouse' && e.button !== 0) return

  const rect = canvasRef.value.getBoundingClientRect()
  avatarDragState = {
    pointerX: e.clientX,
    pointerY: e.clientY,
    originX: avatarDragOffset.value.x,
    originY: avatarDragOffset.value.y,
    left: rect.left,
    top: rect.top,
    width: rect.width,
    height: rect.height,
  }
  draggingAvatar.value = true

  // 监听挂 window：鼠标拖快了会跑出 canvas，只绑在元素上会中途断掉
  window.addEventListener('pointermove', moveAvatarDrag)
  window.addEventListener('pointerup', endAvatarDrag)
  window.addEventListener('pointercancel', endAvatarDrag)
}

const moveAvatarDrag = (e) => {
  if (!avatarDragState) return

  const state = avatarDragState
  const nextLeft = state.left + (e.clientX - state.pointerX)
  const nextTop = state.top + (e.clientY - state.pointerY)

  // 两端各留一点在视口里
  const left = clampValue(nextLeft, AVATAR_DRAG_KEEP - state.width, window.innerWidth - AVATAR_DRAG_KEEP)
  const top = clampValue(nextTop, AVATAR_DRAG_KEEP - state.height, window.innerHeight - AVATAR_DRAG_KEEP)

  avatarDragOffset.value = {
    x: state.originX + (left - state.left),
    y: state.originY + (top - state.top),
  }
}

const endAvatarDrag = () => {
  draggingAvatar.value = false
  avatarDragState = null
  window.removeEventListener('pointermove', moveAvatarDrag)
  window.removeEventListener('pointerup', endAvatarDrag)
  window.removeEventListener('pointercancel', endAvatarDrag)
}

/** 双击回到默认位置：贴右、垂直居中 */
const resetAvatarDrag = () => {
  if (!screening.value) return
  avatarDragOffset.value = { x: 0, y: 0 }
}
</script>

<style lang="css" scoped>
.ppt-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--tb-bg-page);
  border: var(--tb-border-width) solid var(--tb-border);

  overflow: hidden;
}

/* 工具栏 */
.ppt-toolbar {
  height: 52px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--tb-bg-surface);
  border-bottom: 1px solid var(--tb-border);
}
.ppt-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--tb-text-primary);
}
.ppt-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.btn-icon {
  margin-right: 4px;
}
/* AI 应用后的撤销入口（关掉即放弃撤销） */
.ppt-ai-undo {
  margin: 0 0 0.5rem;
}
.ppt-ai-undo__actions {
  margin-top: 0.25rem;
}
/* 创建会议弹窗里「哪些页还没有讲稿」的提示：只列页码、不阻断建会 */
.meeting-dialog__hint {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}
.file-input {
  display: none;
}

/* 主体 */
.ppt-body {
  flex: 1;
  min-height: 0;
  display: flex;
}

/* 左侧缩略图 */
.ppt-thumbnails {
  width: 210px;
  flex: 0 0 auto;
  overflow-y: auto;
  background: var(--tb-bg-sunken);
  padding: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

/* 滚动条：默认 15px 常显灰条与深色画布不搭，收成 6px（与全站其它滚动区一致）
   注意：这里只用 ::-webkit-scrollbar，不写 scrollbar-width ——
   标准属性一旦声明，Chrome 会忽略 webkit 伪元素的样式（含 hover 态） */
.ppt-thumbnails::-webkit-scrollbar {
  width: 6px;
}

.ppt-thumbnails::-webkit-scrollbar-track {
  background: transparent;
}

/* 常态克制、悬停才显眼，避免常驻滚动条抢缩略图的视觉焦点 */
.ppt-thumbnails::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-text-tertiary);
  transition: background var(--tb-dur-2) var(--tb-motion-ease);
}

.ppt-thumbnails::-webkit-scrollbar-thumb:hover {
  background: var(--tb-text-secondary);
}

/* Firefox 不支持 ::-webkit-scrollbar，用标准属性给等价效果（Chrome 不会命中此分支） */
@supports not selector(::-webkit-scrollbar) {
  .ppt-thumbnails {
    scrollbar-width: thin;
    scrollbar-color: var(--tb-text-tertiary) transparent;
  }
}
.thumb-item {
  position: relative;
  width: 165px;
  flex: 0 0 auto;
  border: 2px solid transparent;
  border-radius: 4px;
  box-shadow: var(--tb-shadow-1);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s;
}
.thumb-item:hover {
  border-color: var(--tb-border-strong);
}
.thumb-item.active {
  border-color: var(--tb-primary);
}
.thumb-index {
  position: absolute;
  bottom: 4px;
  right: 6px;
  width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--tb-on-primary);
  background: var(--tb-overlay);
  border-radius: 50%;
}

/* 中部：canvas-tool + canvas（参考 PPTist） */
.layout-content-center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.canvas-tool {
  height: 40px;
  flex: 0 0 auto;
  position: relative;
  z-index: 5;
  border-bottom: 1px solid var(--tb-border);
  background-color: var(--tb-bg-surface);
  display: flex;
  justify-content: space-between;
  padding: 0 10px;
  font-size: 13px;
  user-select: none;
}
.left-handler,
.right-handler {
  display: flex;
  align-items: center;
}
.page-text {
  margin: 0 4px;
  color: var(--tb-text-secondary);
  cursor: default;
}
.handler-item {
  margin: 0 10px;
  font-size: 14px;
  cursor: pointer;

  &.disable {
    opacity: 0.4;
    cursor: not-allowed;
  }
}
.right-handler .text {
  width: 40px;
  text-align: center;
  cursor: pointer;
}
.right-handler .viewport-size {
  font-size: 13px;
}
.preset-item {
  padding: 8px 20px;
  text-align: center;
  cursor: pointer;

  &:hover {
    color: var(--tb-danger);
  }
}
.canvas {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
  background-color: var(--tb-bg-raised);

  .el-empty {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    margin: 0;
  }
}
.viewport-wrapper {
  position: absolute;
  box-shadow: var(--tb-shadow-3);
}
/* 备注区（参考 PPTist Remark） */
.remark {
  position: relative;
  flex: 0 0 auto;
  border-top: 1px solid var(--tb-border);
  background-color: var(--tb-bg-raised);
  line-height: 1.5;

  textarea {
    width: 100%;
    height: 100%;
    overflow-y: auto;
    resize: none;
    border: 0;
    outline: 0;
    padding: 8px;
    font-size: 12px;
    background-color: transparent;
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    padding-bottom: 34px;
    /* 给右上角的 AI 按钮留位（textarea 是绝对定位铺满的，不留位会被盖住） */
    padding-right: 38px;
    color: var(--tb-text-secondary);
  }
}
.remark-actions {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 3;
}
/* AI 优化本页备注：备注区右上角常显（「保存」只在有改动时出现，两者互不挤占） */
.remark-ai {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--tb-primary);
  background: var(--tb-bg-surface);
  border: 1px solid var(--tb-border);
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.remark-ai:hover:not(:disabled) {
  color: var(--tb-bg-surface);
  background: var(--tb-primary);
  border-color: var(--tb-primary);
}
.remark-ai:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
/* 生成中：呼吸提示（单页也要等十几秒，光靠 disabled 太安静） */
.remark-ai.is-running {
  animation: remark-ai-pulse 1.2s ease-in-out infinite;
}
@keyframes remark-ai-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.4;
  }
}
.resize-handler {
  height: 7px;
  position: absolute;
  top: -3px;
  left: 0;
  right: 0;
  cursor: n-resize;
  z-index: 2;
}

/* 右侧工具栏（参考 PPTist layout-content-right） */
.layout-content-right {
  flex: 0 0 auto;
  position: relative;
  background-color: var(--tb-bg-surface);
  border-left: 1px solid var(--tb-border);
  overflow: hidden;
}
.layout-content-right .resize-handler {
  width: 7px;
  position: absolute;
  left: -3px;
  top: 0;
  bottom: 0;
  cursor: e-resize;
  z-index: 2;
}
.digital-human-placeholder {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 20px;

  color: var(--tb-text-tertiary);
  font-size: 14px;
  text-align: center;
  line-height: 1.6;
}

/* 画布尺寸由 PIXI autoDensity 以内联样式写入，此处只消除行内元素基线间隙 */
.digital-human-canvas {
  display: block;
}

/* 形象切换：贴在数字人面板底部 */
.avatar-switch {
  position: absolute;
  left: 50%;
  /* canvas 固定 800 高，比面板高时底部会溢出视口，按钮得往上让一点 */
  bottom: 28px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: calc(100% - 32px);
  padding: 7px 14px;
  border: none;
  border-radius: 999px;
  /* 面板底色是浅色，浅底浅边框的按钮完全看不见，直接用深色实心胶囊 */
  background: color-mix(in oklab, var(--tb-text-primary) 82%, transparent);
  backdrop-filter: blur(6px);
  box-shadow: 0 2px 12px rgb(0 0 0 / 20%);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.18s, transform 0.18s;

  /* 放映模式下父级 pointer-events: none，这里必须自己收回来，否则点不到 */
  pointer-events: auto;
}

.avatar-switch:hover:not(:disabled) {
  background: var(--tb-primary);
  transform: translateX(-50%) translateY(-1px);
}

.avatar-switch:disabled {
  cursor: progress;
}

.avatar-switch__icon {
  font-size: 14px;
}

/* 加载中让图标转起来，比只改鼠标样式更能说明「正在换」 */
.avatar-switch.is-loading .avatar-switch__icon {
  animation: avatar-switch-spin 1s linear infinite;
}

@keyframes avatar-switch-spin {
  to {
    transform: rotate(360deg);
  }
}

.avatar-switch__label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 放映模式下：数字人浮于放映覆盖层之上，随放映一起展示 */
.digital-human-placeholder.screening-mode {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 10000;
  margin: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  /* 不拦截鼠标事件，避免遮挡放映翻页/工具栏操作 */
  pointer-events: none;
}

/* 放映时只有 canvas 这一块收回指针事件，用来拖动；四周留白仍然穿透。
   整块面板若设成 auto 会挡住右侧 400px 的翻页与工具栏。 */
.digital-human-placeholder.screening-mode .digital-human-canvas {
  pointer-events: auto;
  cursor: grab;
  touch-action: none;
}

.digital-human-placeholder.screening-mode.is-dragging .digital-human-canvas {
  cursor: grabbing;
  will-change: transform;
}

/* 放映时只让数字人上屏：切换按钮属于编辑期控件，不给观众看到 */
.digital-human-placeholder.screening-mode .avatar-switch {
  display: none;
}
</style>