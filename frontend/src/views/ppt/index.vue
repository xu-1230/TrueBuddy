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
      </div>
      <input
        ref="fileInputRef"
        type="file"
        accept=".pptx,.json"
        class="file-input"
        @change="onFileChange"
      />
    </div>

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
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
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

import ThumbnailSlide from './components/thumbnail/ThumbnailSlide.vue'
import ScreenSlide from './components/screen/ScreenSlide.vue'
import ScreenView from './screen/ScreenView.vue'

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
onBeforeUnmount(() => destroyLive2D())
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

// pptx 解析出的备注为 HTML 段落（如 <p style="text-align:left;">…</p>），转换为纯文本展示
const remarkToPlainText = (remark) => {
  if (!remark) return ''
  // 已是纯文本（用户编辑后的内容）则原样返回，避免被再次处理导致换行/空格丢失
  if (!/<[a-z][^>]*>/i.test(remark)) return remark
  return remark
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|li)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{2,}/g, '\n')
    .trim()
}
// 备注编辑草稿：随当前页切换自动同步；内容有变化时才显示「保存」按钮，保存后按钮消失
const remarkDraft = ref('') // 正在编辑的内容
const remarkOriginal = ref('') // 当前页已保存的内容
const remarkChanged = computed(() => remarkDraft.value !== remarkOriginal.value)

watch(() => currentSlide.value?.id, () => {
  const text = remarkToPlainText(currentSlide.value?.remark)
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
    color: var(--tb-text-secondary);
  }
}
.remark-actions {
  position: absolute;
  right: 8px;
  bottom: 8px;
  z-index: 3;
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