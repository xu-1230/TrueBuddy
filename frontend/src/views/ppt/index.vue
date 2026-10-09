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
        <!-- 本场时长：AI 按「总时长 ÷ 页数 × 4.0 字/秒」给每页定篇幅（换算在后端，只此一处） -->
        <span class="ppt-toolbar-field">
          本场时长
          <el-input-number
            v-model="aiTargetMinutes"
            class="ppt-target-minutes"
            :min="1"
            :max="300"
            :step="1"
            size="small"
            :disabled="aiRunning"
            controls-position="right"
          />
          分钟
        </span>
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
        <div class="digital-human-placeholder" :class="{ 'screening-mode': screening }">
          <canvas ref="canvasRef" class="digital-human-canvas" />
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CaretLeft, CaretRight, DocumentChecked, FullScreen, Minus, Monitor, Plus, UploadFilled, VideoPlay,
} from '@element-plus/icons-vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'
import { convertPPTXToPPTist } from 'pptx-to-pptist'
import { useSlidesStore } from './stores/slides'
import { useScreenStore } from './stores/screen'
import { useRoute } from 'vue-router'
import { VIEWPORT_SIZE } from './configs/canvas'
import useScreening from './hooks/useScreening'

import './styles/prosemirror.css'
import './styles/animation.css'

import { optimizeSpeech } from '@/api/ppt'
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
// 命中已存在的放映页签时（window.open 复用同名窗口）组件不会重新挂载，因此同时监听参数变化
watch(() => route.query.screen, enterScreeningByQuery)

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
  destroyLive2D()
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
/** 本场默认时长（分钟）：用于把总时长均摊成每页目标字数（后端按 4.0 字/秒换算） */
const aiTargetMinutes = ref(8)
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
  const minutes = Number(aiTargetMinutes.value)
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

const LIVE2D_MODEL_URL = '/live2d/Haru/Haru.model3.json'

const canvasRef = ref(null)
let app = null // PIXI.Application 单实例
let model = null // Live2DModel 单实例
let live2dDisposed = false // 卸载标记：阻断「组件已卸载后才 resolve」的异步回调写入已销毁实例

// 完整释放：停渲染循环 → 销毁模型 → 销毁渲染器
// 路由 /index/ppt 每次进入都是新实例，若此处不销毁，旧 Ticker 会持续对已被浏览器回收的
// WebGL 上下文发起 bindBuffer/bindTexture，产生 INVALID_OPERATION 刷屏
const destroyLive2D = () => {
  live2dDisposed = true

  // 先停循环，避免销毁过程中 Ticker 仍在绘制
  if (app?.ticker) app.ticker.stop()

  if (model) {
    model.removeAllListeners()
    model.destroy({ children: true, texture: true, baseTexture: true })
    model = null
  }

  if (app) {
    // removeView 传 false：canvas 由 Vue 托管，不能由 PIXI 从 DOM 中移除
    app.destroy(false, { children: true, texture: true, baseTexture: true })
    app = null
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

  try {
    const loadedModel = await Live2DModel.from(LIVE2D_MODEL_URL, { autoInteract: true })

    // 加载期间组件可能已卸载：此时 app 已销毁，丢弃模型而不是挂到失效实例上
    if (live2dDisposed || !app) {
      loadedModel.destroy({ children: true, texture: true, baseTexture: true })
      return
    }

    model = loadedModel
    model.scale.set(0.2)
    model.position.set(-30, -46)
    app.stage.addChild(model)

    model.on('hit', () => {
      //  model.expression(4)
    })
  }
  catch (e) {
    console.error('Live2D 模型加载失败:', e)
  }
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
/* 工具栏里的「本场时长」输入（AI 按总时长 ÷ 页数给每页定篇幅） */
.ppt-toolbar-field {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  white-space: nowrap;
}
.ppt-target-minutes {
  width: 96px;
}
/* AI 应用后的撤销入口（关掉即放弃撤销） */
.ppt-ai-undo {
  margin: 0 0 0.5rem;
}
.ppt-ai-undo__actions {
  margin-top: 0.25rem;
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
</style>