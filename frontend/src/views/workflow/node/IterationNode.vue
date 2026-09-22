<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { Refresh } from '@element-plus/icons-vue'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

const { getNodes, updateNode, updateNodeInternals, viewport } = useVueFlow()

/** 节点默认尺寸 */
const DEFAULT_SIZE = { width: 480, height: 280 }
/** 拖拽调整尺寸的下限：宽度要够放下「起点节点 + 一个子节点 + 终点节点」 */
const MIN_SIZE = { width: 448, height: 170 }

const node = computed(() => getNodes.value.find((item) => item.id === props.id))

const size = computed(() => ({
  width: node.value?.width ?? DEFAULT_SIZE.width,
  height: node.value?.height ?? DEFAULT_SIZE.height,
}))

/** 画布四周内缩（.node-body 4px + .node-canvas 4px）与起止节点尺寸，需与 WorkflowView 保持一致 */
const ITERATION_CANVAS_INSET = 8
const ITERATION_BOUNDARY_SIZE = 52
/** 起止节点与子节点之间的间距，需与 WorkflowView 的 ITERATION_BOUNDARY_GAP 保持一致 */
const ITERATION_BOUNDARY_GAP = 12
/** 内容下方额外留白，需与 WorkflowView 的 growIterationToFit 保持一致 */
const ITERATION_CONTENT_BOTTOM_GAP = 16

/** 当前迭代节点内部的全部子节点（含自带的起止节点） */
const childNodes = computed(() => getNodes.value.filter((item) => item.parentNode === props.id))

/**
 * 容纳现有子节点所需的最小尺寸（终点节点的位置由容器宽度决定，不参与计算）。
 * 缩小时不能低于它：一旦 extent 变成 min > max 的反转区间，子节点会先挂在画布外，
 * 之后只要一拖动就会被 clamp 到 header 上（实测 y 会从 134 直接跳到 24）。
 * 公式与 WorkflowView 的 growIterationToFit 一致，保证「撑大」和「缩到最小」是两个互补的口径。
 */
const contentMinSize = computed(() => {
  const parentPosition = node.value?.computedPosition ?? node.value?.position ?? { x: 0, y: 0 }
  let contentRight = 0
  let contentBottom = 0
  for (const child of childNodes.value) {
    if (child.type === 'iteration-end') continue
    // 子节点的 computedPosition 是画布绝对坐标，减去父节点才是容器内的局部坐标
    const position = child.computedPosition ?? child.position
    contentRight = Math.max(contentRight, position.x - parentPosition.x + (child.dimensions?.width || 0))
    contentBottom = Math.max(contentBottom, position.y - parentPosition.y + (child.dimensions?.height || 0))
  }
  return {
    width:
      contentRight +
      ITERATION_CANVAS_INSET +
      ITERATION_BOUNDARY_SIZE +
      ITERATION_BOUNDARY_GAP + // 右侧给终点节点留位
      ITERATION_BOUNDARY_GAP, // 再留一个间距，和 growIterationToFit 对齐
    height: contentBottom + ITERATION_CONTENT_BOTTOM_GAP,
  }
})

/** 终点节点贴着画布右内侧，容器尺寸变化时跟着挪 */
const syncEndNodePosition = () => {
  const target = childNodes.value.find((item) => item.type === 'iteration-end')
  if (!target) return
  const nextX = Math.max(
    ITERATION_CANVAS_INSET,
    size.value.width - ITERATION_CANVAS_INSET - ITERATION_BOUNDARY_SIZE,
  )
  if (Math.round(target.position.x) === Math.round(nextX)) return
  updateNode(target.id, { position: { ...target.position, x: nextX } })
}

watch(() => size.value.width, syncEndNodePosition, { immediate: true })

/**
 * 句柄的尺寸 / 位置变了（例如换了一套 Handle 样式）后，需要让 vue-flow 重新测量，
 * 否则连线仍然接在旧位置。
 */
onMounted(async () => {
  await nextTick()
  updateNodeInternals([props.id])
})

/* ===== 调整尺寸 ===== */
const resizing = ref(false)
let stopResize = null

const handleResizeStart = (event) => {
  if (event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()

  const startX = event.clientX
  const startY = event.clientY
  const startSize = { ...size.value }
  resizing.value = true

  const handleMove = (moveEvent) => {
    const zoom = viewport.value?.zoom || 1
    // 不能缩到装不下现有子节点，否则 Vue Flow 的 extent 会反转（min > max）
    const minContent = contentMinSize.value
    const width = Math.max(
      MIN_SIZE.width,
      minContent.width,
      startSize.width + (moveEvent.clientX - startX) / zoom,
    )
    const height = Math.max(
      MIN_SIZE.height,
      minContent.height,
      startSize.height + (moveEvent.clientY - startY) / zoom,
    )
    updateNode(props.id, { width: Math.round(width), height: Math.round(height) })
  }

  const handleUp = () => {
    resizing.value = false
    window.removeEventListener('pointermove', handleMove)
    window.removeEventListener('pointerup', handleUp)
    stopResize = null
    // 尺寸变了要重新上报，保证连线端点跟着句柄走
    updateNodeInternals([props.id])
  }

  stopResize = handleUp
  window.addEventListener('pointermove', handleMove)
  window.addEventListener('pointerup', handleUp)
}

onBeforeUnmount(() => {
  stopResize?.()
})
</script>

<template>
  <div class="node-wrapper">
    <div class="node-card" :class="{ 'node-card--resizing': resizing }">
      <!-- 调整尺寸（悬停显示） -->
      <div class="node-resize">
        <button
          class="node-resize-btn nodrag nopan"
          type="button"
          aria-label="调整节点尺寸"
          @pointerdown="handleResizeStart"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path
              d="M5.19 11.84C8.264 10.62 10.714 8.166 11.93 5.089"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>

      <!-- 目标 Handle（左侧） -->
      <Handle id="in" type="target" :position="Position.Left" class="node-handle node-handle-target" />

      <!-- 源 Handle（右侧） -->
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" />

      <!-- 节点头部 -->
      <div class="node-header">
        <button class="node-title-btn" type="button" :aria-label="data.label">
          <div class="node-icon">
            <el-icon class="node-icon-svg"><Refresh /></el-icon>
          </div>
          <div class="node-label-wrap">
            <div class="node-label" :title="data.label">{{ data.label }}</div>
          </div>
        </button>
      </div>

      <!-- 内层画布：起止节点与子节点都是真实节点，由 Vue Flow 渲染在这块区域之上 -->
      <div class="node-body">
        <div class="node-canvas"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 节点容器 ===== */
.node-wrapper {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  border-radius: 16px;
  border: 1px solid transparent;
}

/* ===== 节点卡片 ===== */
.node-card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding-bottom: 4px;
  border-radius: 15px;
  border: 1px solid #e5e7eb;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.node-card:hover {
  border-color: #c9cdd4;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
}

/* 调整尺寸时禁用过渡，避免卡片抖动 */
.node-card--resizing {
  transition: none;
  user-select: none;
}

/* ===== 节点头部 ===== */
.node-header {
  display: flex;
  align-items: center;
  padding: 12px;
}

.node-title-btn {
  display: flex;
  min-width: 0;
  flex-grow: 1;
  align-items: center;
  margin-right: 4px;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.node-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-right: 8px;
  border-radius: 8px;
  border: 0.5px solid rgba(255, 255, 255, 0.02);
  background: #06aed4;
  color: #fff;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
}

.node-icon-svg {
  font-size: 14px;
}

.node-label-wrap {
  display: flex;
  min-width: 0;
  flex-grow: 1;
  align-items: center;
}

.node-label {
  min-width: 0;
  flex-grow: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
}

/* ===== 内层点阵画布 ===== */
.node-body {
  flex-grow: 1;
  min-height: 0;
  padding: 0 4px 4px;
}

.node-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 240px;
  min-height: 90px;
  padding: 4px;
  border-radius: 16px;
  background-color: #f7f8fa;
  background-image: radial-gradient(circle, #dcdfe6 1px, transparent 1px);
  background-size: 14px 14px;
  background-position: -1px -1px;
}

/* ===== Handle（与 InputNode 等其他节点保持一致） ===== */
.node-handle {
  width: 8px !important;
  height: 8px !important;
  border: 1px solid #c9cdd4 !important;
  background: #fff !important;
  opacity: 0;
  transition: opacity 0.2s;
}

.node-card:hover .node-handle {
  opacity: 1;
}

/* ===== 调整尺寸按钮 ===== */
.node-resize {
  position: absolute;
  right: 1px;
  bottom: 1px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s;
}

.node-card:hover .node-resize,
.node-card--resizing .node-resize {
  opacity: 1;
  pointer-events: auto;
}

.node-resize-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 2px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: rgba(0, 0, 0, 0.16);
  cursor: nwse-resize;
}

.node-resize-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #4e5969;
}
</style>
