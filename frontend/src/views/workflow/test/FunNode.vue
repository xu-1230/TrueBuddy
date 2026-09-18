<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { InfoFilled, RefreshRight } from '@element-plus/icons-vue'

/**
 * 循环体节点（对标扣子 Coze 工作流的循环节点形态）
 *
 * 扣子里循环是一个整体容器：上面的循环节点卡片（ForNode）承载配置，
 * 下面这块就是「循环体」——一块内嵌的浅色子画布：
 *   - 循环体内没有固定的开始 / 结束节点，步骤按连线从上到下串成链
 *   - 多轮之间严格串行，每轮从上到下执行完才进入下一轮
 *   - 内置变量 item / index 仅循环体内可用
 *   - 专属节点（设置变量 / 继续循环 / 停止循环）只能放在循环体内
 */
const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
  selected: { type: Boolean, default: false },
})

const LOOP_BODY_TIP =
  '循环体是循环节点的内部子画布：没有固定的开始/结束节点，步骤按连线从上到下依次执行，多轮严格串行；' +
  '内置变量 item / index 仅循环体内可用，设置变量、继续循环、停止循环节点只能放在循环体内。'

/** 循环体最小尺寸（与循环节点卡片宽度对齐） */
const MIN_WIDTH = 260
const MIN_HEIGHT = 240

/** 扣子风格的步骤图标配色（按节点类型） */
const TONE_MAP = {
  llm: '#4d53e8',
  code: '#00b2a9',
  plugin: '#165dff',
  start: '#f59e0b',
  setting: '#e07a1f',
  continue: '#00b2a9',
  stop: '#f04438',
}

const { findNode, viewport, updateNode } = useVueFlow()

const zoomOf = () => viewport.value?.zoom || 1

const selfNode = computed(() => findNode(props.id))
const parentId = computed(() => selfNode.value?.parentNode ?? '')
const parentNode = computed(() => (parentId.value ? findNode(parentId.value) : null))

/** 循环体内的步骤（优先 data.blocks，回退节点级 blocks） */
const steps = computed(() => {
  const blocks = props.data?.blocks ?? selfNode.value?.blocks ?? []
  return blocks.map((block) => ({
    id: block?.id,
    type: block?.type || '',
    label: block?.data?.label || '未命名节点',
    tone: TONE_MAP[block?.type] || '#6c7cff',
  }))
})

const stepCount = computed(() => steps.value.length)

/* ---------- 尺寸（右下角拖拽调整，宽度变化后与循环节点中线对齐） ---------- */

const size = ref({
  width: props.data?.size?.width ?? 260,
  height: props.data?.size?.height ?? 280,
})

const rootStyle = computed(() => ({
  width: `${size.value.width}px`,
  height: `${size.value.height}px`,
}))

const resizing = ref(false)
let resizeStart = null

function startResize(event) {
  if (event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()

  resizeStart = {
    x: event.clientX,
    y: event.clientY,
    width: size.value.width,
    height: size.value.height,
    centerX: (selfNode.value?.position?.x ?? 0) + size.value.width / 2,
    zoom: zoomOf(),
  }

  resizing.value = true
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', stopResize)
}

function handleResizeMove(event) {
  if (!resizeStart) return

  const width = Math.max(
    MIN_WIDTH,
    resizeStart.width + (event.clientX - resizeStart.x) / resizeStart.zoom,
  )
  const height = Math.max(
    MIN_HEIGHT,
    resizeStart.height + (event.clientY - resizeStart.y) / resizeStart.zoom,
  )
  size.value = { width, height }

  const x = Math.round(resizeStart.centerX - width / 2)
  const self = selfNode.value
  if (self && x !== self.position.x) {
    updateNode(props.id, { position: { x, y: self.position.y } })
  }
}

function stopResize() {
  resizeStart = null
  resizing.value = false
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', handleResizeMove)
  document.removeEventListener('mouseup', stopResize)
}

/* ---------- 头部拖拽：整体移动（循环节点卡片 + 循环体） ---------- */

const moving = ref(false)
let moveStart = null

function startMove(event) {
  const parent = parentNode.value
  if (!parent || event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()

  moveStart = {
    x: event.clientX,
    y: event.clientY,
    position: { x: parent.position.x, y: parent.position.y },
    zoom: zoomOf(),
  }

  moving.value = true
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleMove)
  document.addEventListener('mouseup', stopMove)
}

function handleMove(event) {
  if (!moveStart) return

  updateNode(parentId.value, {
    position: {
      x: moveStart.position.x + (event.clientX - moveStart.x) / moveStart.zoom,
      y: moveStart.position.y + (event.clientY - moveStart.y) / moveStart.zoom,
    },
  })
}

function stopMove() {
  if (!moveStart) return
  moveStart = null
  moving.value = false
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', handleMove)
  document.removeEventListener('mouseup', stopMove)
}

onBeforeUnmount(() => {
  stopResize()
  stopMove()
})
</script>

<template>
  <div
    class="loop-body"
    :class="{ 'is-selected': selected, 'is-resizing': resizing, 'is-moving': moving }"
    :style="rootStyle"
  >
    <!-- 循环入口：接收循环节点卡片接出来的连线 -->
    <Handle type="target" id="loop-top" :position="Position.Top" />

    <!-- 循环输出：全部轮次完成后从这里出去 -->
    <Handle type="source" :position="Position.Right" />

    <!-- 标题栏：拖动可整体移动循环 -->
    <header
      class="loop-body__header nodrag"
      title="拖动可整体移动循环"
      @mousedown.stop="startMove"
      @click.stop
    >
      <span class="loop-body__title">循环体</span>
      <span class="loop-body__count">{{ stepCount }} 个节点</span>
      <el-tooltip :content="LOOP_BODY_TIP" placement="top" :show-after="200">
        <el-icon class="loop-body__tip"><InfoFilled /></el-icon>
      </el-tooltip>
    </header>

    <!-- 内嵌子画布：步骤按连线从上到下串成链，多轮严格串行 -->
    <div class="loop-body__canvas">
      <div v-if="steps.length" class="loop-body__chain">
        <template v-for="(step, index) in steps" :key="step.id ?? index">
          <div v-if="index > 0" class="loop-body__connector" />
          <div class="loop-body__node">
            <span class="loop-body__node-icon" :style="{ backgroundColor: step.tone }">
              <el-icon><RefreshRight /></el-icon>
            </span>
            <span class="loop-body__node-label">{{ step.label }}</span>
          </div>
        </template>
      </div>

      <!-- 空态：扣子风格「拖入节点」引导（选中循环体后才能向其中添加节点） -->
      <div v-else class="loop-body__empty">
        <button class="loop-body__add" type="button" title="选中循环体后，从节点面板拖入节点">
          <span class="loop-body__add-plus">＋</span>
          <span>拖入节点到循环体</span>
        </button>
        <p class="loop-body__empty-hint">
          循环体内无需开始/结束节点，步骤将按连线顺序在每轮中执行
        </p>
      </div>
    </div>

    <footer class="loop-body__footer">每轮从上到下执行 · 多轮严格串行</footer>

    <!-- 右下角调整大小手柄 -->
    <div
      class="loop-body__grip nodrag"
      title="拖拽调整循环体大小"
      @mousedown="startResize"
    >
      <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path
          d="M5.19 11.84C8.264 10.62 10.714 8.166 11.93 5.089"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
        />
      </svg>
    </div>
  </div>
</template>

<style scoped>
.loop-body {
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border: 1px solid #dfe1e6;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 6px rgba(29, 33, 41, 0.06);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.loop-body:hover {
  border-color: #b8bce0;
}

.loop-body.is-selected,
.loop-body.is-resizing {
  border-color: #4d53e8;
  box-shadow: 0 0 0 3px rgba(77, 83, 232, 0.12);
}

/* 标题栏 */
.loop-body__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  box-sizing: border-box;
  height: 40px;
  padding: 0 12px;
  border-bottom: 1px solid #eceef1;
  border-radius: 11px 11px 0 0;
  cursor: move;
}

.loop-body__title {
  color: #1d2129;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
}

.loop-body__count {
  flex: 1;
  min-width: 0;
  color: #86909c;
  font-size: 12px;
  line-height: 16px;
}

.loop-body__tip {
  flex: 0 0 auto;
  color: #a9aeb8;
  font-size: 14px;
  cursor: help;
}

/* 内嵌子画布：浅色底、无点阵（与主画布区分） */
.loop-body__canvas {
  display: flex;
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
  margin: 8px 8px 0;
  border-radius: 8px;
  background: #f5f6f8;
}

/* 步骤链：纵向居中排布，步骤间用带箭头的连线串联 */
.loop-body__chain {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 12px;
  overflow: auto;
}

.loop-body__node {
  display: flex;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
  max-width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(29, 33, 41, 0.04);
}

.loop-body__node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 5px;
  color: #fff;
  font-size: 12px;
}

.loop-body__node-label {
  min-width: 0;
  overflow: hidden;
  color: #1d2129;
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 步骤之间的连线（带箭头，模拟扣子循环体内的执行连线） */
.loop-body__connector {
  position: relative;
  flex: 0 0 auto;
  width: 1.5px;
  height: 18px;
  background: #c9cdd4;
}

.loop-body__connector::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  border: 4px solid transparent;
  border-top-color: #c9cdd4;
  border-bottom: none;
  transform: translateX(-50%);
}

/* 空态：居中「拖入节点」引导 */
.loop-body__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 12px;
  text-align: center;
}

.loop-body__add {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: 1px dashed #b8bce0;
  border-radius: 8px;
  background: transparent;
  color: #4d53e8;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease;
}

.loop-body__add:hover {
  border-color: #4d53e8;
  background: rgba(77, 83, 232, 0.04);
}

.loop-body__add-plus {
  font-size: 14px;
  font-weight: 600;
}

.loop-body__empty-hint {
  max-width: 200px;
  margin: 0;
  color: #a9aeb8;
  font-size: 11px;
  line-height: 16px;
}

/* 底部说明条 */
.loop-body__footer {
  flex: 0 0 auto;
  padding: 6px 12px 8px;
  color: #a9aeb8;
  font-size: 11px;
  line-height: 14px;
  text-align: center;
  user-select: none;
}

/* 右下角 resize 手柄：悬停 / 选中 / 拖拽中显现 */
.loop-body__grip {
  position: absolute;
  right: 4px;
  bottom: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  color: #a9aeb8;
  opacity: 0;
  pointer-events: none;
  cursor: nwse-resize;
  transition: opacity 0.16s ease, color 0.16s ease;
}

.loop-body:hover .loop-body__grip,
.loop-body.is-selected .loop-body__grip,
.loop-body.is-resizing .loop-body__grip {
  opacity: 1;
  pointer-events: auto;
}

.loop-body__grip:hover {
  color: #4d53e8;
}

/* 连接点：小圆点，hover 转主色 */
.loop-body :deep(.vue-flow__handle) {
  box-sizing: border-box;
  width: 9px;
  height: 9px;
  min-width: 0;
  min-height: 0;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #c9cdd4;
  transition: background 0.16s ease, scale 0.16s ease;
}

.loop-body :deep(.vue-flow__handle:hover) {
  background: #4d53e8;
  scale: 1.25;
}
</style>
