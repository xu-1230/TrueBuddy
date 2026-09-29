<script setup>
/**
 * 画布左侧竖向工具栏（tools-wrap）。
 *
 * 只负责「用户意图」：把按钮点击转成事件发给 WorkflowView，把节点卡片拖拽时的类型写进
 * dataTransfer（drop 与落点换算由画布页处理，因为只有它持有 Vue Flow 的坐标换算能力）。
 *
 * 浮层刻意不用 el-popover：它自带「外部点击关闭」，与原生拖拽（dragstart 会引起 focus 变化）
 * 组合时容易在拖动过程中被收起，这里改为按钮切换 + 右上角关闭按钮。
 */
import { computed, ref } from 'vue'
import {
  Close,
  DocumentAdd,
  FolderOpened,
  FullScreen,
  Loading,
  Plus,
  RefreshLeft,
  Upload,
  VideoPlay,
  View,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { NODE_CATALOG, WORKFLOW_NODE_DND_TYPE } from './utils/node-catalog'
import { NODE_ICONS } from './utils/node-icons'

defineOptions({ name: 'ToolsWrap' })

const props = defineProps({
  /** 试运行是否进行中（进行中禁用按钮并显示旋转指示） */
  running: { type: Boolean, default: false },
  /** 是否在画布上展示执行结果 */
  resultsVisible: { type: Boolean, default: true },
  /**
   * 当前展开的浮层：nodes（节点库）/ save（保存）/ open（打开）/ 空字符串（都不展开）。
   * 由父组件统一持有，保证三个浮层互斥、不会叠在一起。
   */
  activePanel: { type: String, default: '' },
})

const emit = defineEmits([
  'run',
  'new-workflow',
  'toggle-results',
  'toggle-panel',
  'fit-view',
  'zoom-in',
  'zoom-out',
  'reset-zoom',
])

/** 节点类型 → 图标（与节点数据抽屉共用同一套映射） */

/** 节点库浮层是否展开（由父组件的 activePanel 统一控制，与其它浮层互斥） */
const nodesPanelVisible = computed(() => props.activePanel === 'nodes')

/** 正在被拖拽的节点类型（仅用于卡片透明度反馈） */
const draggingKind = ref(null)

const handleDragStart = (event, item) => {
  if (!item.addable) {
    event.preventDefault()
    return
  }
  draggingKind.value = item.kind
  if (!event.dataTransfer) return
  event.dataTransfer.setData(WORKFLOW_NODE_DND_TYPE, item.kind)
  event.dataTransfer.effectAllowed = 'move'
}

const handleDragEnd = () => {
  draggingKind.value = null
}
</script>

<template>
  <div class="tools-wrap">
    <!-- 添加节点 / 试运行 -->
    <div class="tools-wrap__group">
      <el-tooltip content="添加节点" placement="right" :show-after="200">
        <button
          class="tools-wrap__btn"
          :class="{ 'is-active': nodesPanelVisible }"
          type="button"
          aria-label="添加节点"
          :aria-expanded="nodesPanelVisible"
          @click="emit('toggle-panel', 'nodes')"
        >
          <el-icon><Plus /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip :content="running ? '执行中…' : '试运行'" placement="right" :show-after="200">
        <button
          class="tools-wrap__btn tools-wrap__btn--run"
          type="button"
          aria-label="试运行"
          :disabled="running"
          @click="emit('run')"
        >
          <el-icon :class="{ 'is-spin': running }">
            <Loading v-if="running" />
            <VideoPlay v-else />
          </el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="新建工作流" placement="right" :show-after="200">
        <button
          class="tools-wrap__btn"
          type="button"
          aria-label="新建工作流"
          @click="emit('new-workflow')"
        >
          <el-icon><DocumentAdd /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="保存工作流" placement="right" :show-after="200">
        <button
          class="tools-wrap__btn"
          :class="{ 'is-active': activePanel === 'save' }"
          type="button"
          aria-label="保存工作流"
          :aria-expanded="activePanel === 'save'"
          @click="emit('toggle-panel', 'save')"
        >
          <el-icon><Upload /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="打开工作流" placement="right" :show-after="200">
        <button
          class="tools-wrap__btn"
          :class="{ 'is-active': activePanel === 'open' }"
          type="button"
          aria-label="打开工作流"
          :aria-expanded="activePanel === 'open'"
          @click="emit('toggle-panel', 'open')"
        >
          <el-icon><FolderOpened /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip
        :content="resultsVisible ? '隐藏执行结果' : '显示执行结果'"
        placement="right"
        :show-after="200"
      >
        <button
          class="tools-wrap__btn"
          :class="{ 'is-active': resultsVisible }"
          type="button"
          aria-label="执行结果"
          :aria-pressed="resultsVisible"
          @click="emit('toggle-results')"
        >
          <el-icon><View /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <span class="tools-wrap__divider" aria-hidden="true" />

    <!-- 视图控制 -->
    <div class="tools-wrap__group">
      <el-tooltip content="适应视图" placement="right" :show-after="200">
        <button class="tools-wrap__btn" type="button" aria-label="适应视图" @click="emit('fit-view')">
          <el-icon><FullScreen /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="放大" placement="right" :show-after="200">
        <button class="tools-wrap__btn" type="button" aria-label="放大" @click="emit('zoom-in')">
          <el-icon><ZoomIn /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="缩小" placement="right" :show-after="200">
        <button class="tools-wrap__btn" type="button" aria-label="缩小" @click="emit('zoom-out')">
          <el-icon><ZoomOut /></el-icon>
        </button>
      </el-tooltip>

      <el-tooltip content="重置缩放" placement="right" :show-after="200">
        <button class="tools-wrap__btn" type="button" aria-label="重置缩放" @click="emit('reset-zoom')">
          <el-icon><RefreshLeft /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <!-- 节点库 -->
    <section v-show="nodesPanelVisible" class="node-library" aria-label="节点库">
      <header class="node-library__head">
        <span class="node-library__title">节点库</span>
        <button
          class="node-library__close"
          type="button"
          aria-label="收起节点库"
          @click="emit('toggle-panel', 'nodes')"
        >
          <el-icon><Close /></el-icon>
        </button>
      </header>
      <p class="node-library__hint">拖拽卡片到画布任意位置放下即可添加</p>

      <div class="node-library__list">
        <div
          v-for="item in NODE_CATALOG"
          :key="item.kind"
          class="node-card"
          :class="{ 'is-disabled': !item.addable, 'is-dragging': draggingKind === item.kind }"
          :draggable="item.addable"
          :title="item.hint"
          @dragstart="handleDragStart($event, item)"
          @dragend="handleDragEnd"
        >
          <span class="node-card__icon">
            <el-icon><component :is="NODE_ICONS[item.kind]" /></el-icon>
          </span>
          <span class="node-card__text">
            <span class="node-card__label">{{ item.label }}</span>
            <span class="node-card__hint">{{ item.hint }}</span>
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ===== 竖向工具条 ===== */
.tools-wrap {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 4px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  /* 容器不拦截画布事件，只有按钮与浮层可交互 */
  pointer-events: none;
}

.tools-wrap__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tools-wrap__divider {
  height: var(--tb-border-width);
  margin: 2px 4px;
  background: var(--tb-border);
}

.tools-wrap__btn {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-secondary);
  font-size: 18px;
  cursor: pointer;
  pointer-events: auto;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out),
    transform var(--tb-dur-2) var(--tb-ease-out);
}

.tools-wrap__btn:hover:not(:disabled) {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  transform: translateY(-1px);
}

.tools-wrap__btn.is-active {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.tools-wrap__btn:disabled {
  color: var(--tb-text-tertiary);
  cursor: not-allowed;
}

.tools-wrap__btn--run {
  color: var(--tb-primary);
}

.tools-wrap__btn .is-spin {
  animation: tools-wrap-spin 900ms linear infinite;
}

@keyframes tools-wrap-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ===== 节点库浮层 ===== */
.node-library {
  position: absolute;
  top: 0;
  left: calc(100% + 8px);
  width: 240px;
  padding: 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
  pointer-events: auto;
}

.node-library__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.node-library__title {
  font-size: var(--tb-text-sm);
  font-weight: 600;
  color: var(--tb-text-primary);
}

.node-library__close {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.node-library__close:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

.node-library__hint {
  margin: 4px 0 12px;
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
  color: var(--tb-text-tertiary);
}

.node-library__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: var(--tb-radius-md);
  cursor: grab;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-ease-out);
}

.node-card:hover {
  background: var(--tb-bg-raised);
}

.node-card.is-dragging {
  opacity: 0.6;
  cursor: grabbing;
}

.node-card.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.node-card.is-disabled:hover {
  background: transparent;
}

.node-card__icon {
  display: flex;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
  font-size: 16px;
}

.node-card__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.node-card__label {
  font-size: var(--tb-text-sm);
  font-weight: 500;
  color: var(--tb-text-primary);
}

.node-card__hint {
  overflow: hidden;
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 窄屏与动效偏好 ===== */
@media (max-width: 768px) {
  .node-library {
    width: 200px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tools-wrap__btn,
  .node-library__close,
  .node-card {
    transition: none;
  }

  .tools-wrap__btn:hover:not(:disabled) {
    transform: none;
  }
}
</style>
