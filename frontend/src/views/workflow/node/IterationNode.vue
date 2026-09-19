<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { ElMessage } from 'element-plus'
import { DataLine, MoreFilled, Refresh } from '@element-plus/icons-vue'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
  /** 可添加的节点类型，由 WorkflowView 传入 */
  catalog: { type: Array, default: () => [] },
})

const emit = defineEmits(['add-node'])

/* ===== 添加节点：节点类型选择面板 ===== */
const pickerVisible = ref(false)
const pickerAnchor = ref(null)
const pickerPlacement = ref('bottom')

/** 用 virtual-ref 让三个「添加节点」入口共用同一个面板 */
const togglePicker = async (event, placement = 'bottom') => {
  const anchor = event.currentTarget
  const isSameAnchor = pickerAnchor.value === anchor
  const wasVisible = pickerVisible.value
  pickerVisible.value = false
  // 再次点击同一个入口 = 收起
  if (wasVisible && isSameAnchor) return
  // 先关再开，保证切换入口时面板重新定位
  await nextTick()
  pickerAnchor.value = anchor
  pickerPlacement.value = placement
  pickerVisible.value = true
}

const handleDocumentMousedown = (event) => {
  if (pickerAnchor.value?.contains(event.target)) return
  if (event.target?.closest?.('.workflow-node-picker')) return
  pickerVisible.value = false
}

watch(pickerVisible, (visible) => {
  if (visible) {
    document.addEventListener('mousedown', handleDocumentMousedown, true)
  } else {
    document.removeEventListener('mousedown', handleDocumentMousedown, true)
  }
})

/** 选择节点类型后关闭面板，由外层负责创建节点 */
const pickNode = (item) => {
  pickerVisible.value = false
  emit('add-node', { parentId: props.id, type: item.type })
}

const { getNodes, getEdges, updateNode, updateNodeData, updateNodeInternals, viewport } = useVueFlow()

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
const ITERATION_BOUNDARY_TYPES = ['iteration-start', 'iteration-end']

/** 当前迭代节点内部的全部子节点（含自带的起止节点） */
const childNodes = computed(() => getNodes.value.filter((item) => item.parentNode === props.id))

/** 用户添加的子节点，用于判断空状态 */
const contentChildren = computed(() =>
  childNodes.value.filter((item) => !ITERATION_BOUNDARY_TYPES.includes(item.type)),
)

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
 * 迭代节点的子流程数据：内部子节点本身是 vue-flow 的 parentNode 嵌套结构，
 * 这里把它们镜像到节点 data.children / data.childEdges 上，便于查看与序列化。
 * 子节点的 position 保持相对父节点，与 vue-flow 内部一致。
 */
const collectIterationData = (iterationId) => {
  const innerNodes = getNodes.value.filter((item) => item.parentNode === iterationId)
  const childIds = new Set(innerNodes.map((item) => item.id))

  return {
    children: innerNodes.map((item) => ({
      id: item.id,
      type: item.type,
      position: { x: item.position.x, y: item.position.y },
      width: item.width,
      height: item.height,
      zIndex: item.zIndex,
      data: item.data,
    })),
    childEdges: getEdges.value
      .filter((edge) => childIds.has(edge.source) && childIds.has(edge.target))
      .map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        sourceHandle: edge.sourceHandle,
        targetHandle: edge.targetHandle,
        type: edge.type,
      })),
  }
}

/** 把子流程同步到节点 data 上 */
const syncIterationData = () => {
  const { children: childNodes, childEdges } = collectIterationData(props.id)
  updateNodeData(props.id, { children: childNodes, childEdges })
}

/** 子节点集合 / 位置的签名，变化时重新同步 data.children / data.childEdges */
const childrenSignature = computed(() =>
  childNodes.value
    .map((item) => `${item.id}@${Math.round(item.position.x)},${Math.round(item.position.y)}`)
    .join('|'),
)

watch(childrenSignature, syncIterationData, { immediate: true })

/**
 * 生成用于查看 / 提交的图数据：迭代节点内部的子节点、子连线收进该节点的
 * data.children / data.childEdges，不再出现在外层的 nodes / edges 里。
 * 注意：vue-flow 渲染时必须把它们放在 nodes 数组中（靠 parentNode 定位），
 * 所以只在快照这一层做收拢。
 */
const buildWorkflowSnapshot = () => {
  const list = getNodes.value
  const childIds = new Set(list.filter((item) => item.parentNode).map((item) => item.id))

  const snapshotNodes = list
    .filter((item) => !item.parentNode)
    .map((item) => {
      const snapshot = {
        id: item.id,
        type: item.type,
        position: { x: item.position.x, y: item.position.y },
        width: item.width,
        height: item.height,
        zIndex: item.zIndex,
        data: item.data,
      }
      if (item.type === 'iteration') {
        const { children: childNodes, childEdges } = collectIterationData(item.id)
        snapshot.data = { ...item.data, children: childNodes, childEdges }
      }
      return snapshot
    })

  const snapshotEdges = getEdges.value
    .filter((edge) => !childIds.has(edge.source) && !childIds.has(edge.target))
    .map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle,
      type: edge.type,
    }))

  return { nodes: snapshotNodes, edges: snapshotEdges }
}

/** JSON 序列化兜底：内部字段若有循环引用也不影响控制台输出 */
const stringifySafe = (value) => {
  try {
    return JSON.stringify(value, null, 2)
  } catch (error) {
    return `JSON 序列化失败：${error.message}`
  }
}

/** 调试用：打印「全部」nodes / edges，迭代节点内部的子节点 / 子连线只出现在 data.children 下 */
const handleLogData = () => {
  console.group('[workflow] 输出数据')
  try {
    syncIterationData()
    const graph = buildWorkflowSnapshot()

    console.log(`nodes(${graph.nodes.length})`, graph.nodes)
    console.log(`edges(${graph.edges.length})`, graph.edges)
    console.log('JSON', stringifySafe(graph))

    ElMessage.success(`已输出 ${graph.nodes.length} 个节点 / ${graph.edges.length} 条连线`)
  } catch (error) {
    console.error('输出数据失败', error)
    ElMessage.error('输出数据失败，详见控制台')
  } finally {
    console.groupEnd()
  }
}

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
    const width = Math.max(MIN_SIZE.width, startSize.width + (moveEvent.clientX - startX) / zoom)
    const height = Math.max(MIN_SIZE.height, startSize.height + (moveEvent.clientY - startY) / zoom)
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
  document.removeEventListener('mousedown', handleDocumentMousedown, true)
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

      <!-- hover 操作栏 -->
      <div class="node-actionbar">
        <div class="node-actionbar-inner">
          <button
            class="node-more-btn nodrag nopan nowheel"
            type="button"
            aria-label="输出 node / edge 数据"
            @click="handleLogData"
          >
            <el-icon><DataLine /></el-icon>
          </button>
          <button class="node-more-btn nodrag nopan nowheel" type="button" aria-label="更多">
            <el-icon><MoreFilled /></el-icon>
          </button>
        </div>
      </div>

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
        <div class="node-canvas">
          <!-- 没有子节点时，「添加节点」跟在起点节点后面 -->
          <button
            v-if="!contentChildren.length"
            class="iteration-add iteration-add--inline nodrag nopan"
            type="button"
            aria-haspopup="dialog"
            @click.stop="togglePicker($event, 'top')"
          >
            <span class="iteration-add-plus">+</span>
            添加节点
          </button>

          <!-- 已有子节点时，按钮沉底 -->
          <div v-else class="iteration-slot">
            <button
              class="iteration-add nodrag nopan"
              type="button"
              aria-haspopup="dialog"
              @click.stop="togglePicker($event, 'top')"
            >
              <span class="iteration-add-plus">+</span>
              添加节点
            </button>
          </div>
        </div>
      </div>

      <!-- 添加节点：节点类型选择面板（用 virtual-ref 定位） -->
      <el-popover
        :visible="pickerVisible"
        :virtual-ref="pickerAnchor"
        virtual-triggering
        :placement="pickerPlacement"
        :popper-style="{ width: '200px', padding: '4px' }"
        popper-class="workflow-node-picker"
      >
        <div class="node-picker">
          <div class="node-picker-title">添加节点</div>
          <button
            v-for="item in catalog"
            :key="item.type"
            class="node-picker-item"
            type="button"
            @click="pickNode(item)"
          >
            <span class="node-picker-icon" :style="{ backgroundColor: item.color }">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="node-picker-label">{{ item.label }}</span>
          </button>
        </div>
      </el-popover>
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
  padding: 12px 12px 8px;
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
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
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

/* ===== 添加节点按钮 ===== */
.iteration-slot {
  display: flex;
  justify-content: center;
  width: 100%;
  margin-top: auto;
}

/* 空状态：按钮跟在起点节点后面（对齐子节点落点 / 起点节点中心） */
.iteration-add--inline {
  position: absolute;
  left: 100px;
  top: 78px;
  transform: translateY(-50%);
}

.iteration-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  font-size: 14px;
  color: #1d2129;
  cursor: pointer;
  transition: box-shadow 0.2s, color 0.2s;
}

.iteration-add:hover {
  color: #2563eb;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.iteration-add-plus {
  font-size: 16px;
  line-height: 1;
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

/* ===== hover 操作栏 ===== */
.node-actionbar {
  position: absolute;
  top: -28px;
  right: 0;
  display: flex;
  height: 28px;
  padding-bottom: 4px;
  visibility: hidden;
}

.node-card:hover .node-actionbar {
  visibility: visible;
}

.node-actionbar-inner {
  display: flex;
  align-items: center;
  height: 24px;
  padding: 0 2px;
  border-radius: 8px;
  border: 0.5px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(5px);
  color: #9aa1ad;
}

.node-more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.node-more-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #4e5969;
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
