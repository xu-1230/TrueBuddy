<script setup>
/**
 * 画布左侧「节点库面板」：浏览、检索并把节点添加到画布。
 *
 * 只负责「用户意图」：条目点击转成 `add-node` 事件发给画布页，节点长什么样、落在哪里、
 * 叫什么名字由 `utils/node-factory` 决定（见该模块的注释）。面板本身不碰 Vue Flow store。
 *
 * 两条来自后端的约束落在本组件里：
 * 1. **开始 / 结束全图唯一**：后端把「无入边的节点」自动接 `StateGraph.START`、「无出边的节点」
 *    自动接 `StateGraph.END`（docs/12-工作流规范.md §3.1），画布上出现两个入口 / 两个出口时
 *    流程语义不明，因此这两类条目在已存在时置为不可用并说明原因（只在 UI 层拦截，代价低且可逆）。
 * 2. **节点描述只有一个来源**：条目全部来自 `utils/node-meta` 的 `NODE_CATALOG`，与节点配置抽屉
 *    共用同一份名称 / 图标 / 说明，避免两处各写一套而漂移。
 *
 * 容器 `pointer-events: none`、可交互元素 `auto`：面板浮在画布上，不能吃掉画布的拖拽与框选手势。
 * 面板内没有任何元素在画布 DOM 子树里，因此点击条目也不会冒泡成画布的 `node-click`。
 */
import { computed, ref } from 'vue'
import { ArrowLeftBold, CircleClose, Grid, Search } from '@element-plus/icons-vue'
import { NODE_CATALOG } from '../utils/node-meta'
import { writeNodeDragPayload } from '../utils/node-drag'

defineOptions({ name: 'NodePanel' })

const props = defineProps({
  /** 画布上已存在的节点类型集合（`Set`）：开始 / 结束节点全图唯一，命中即禁用 */
  existingTypes: { type: Object, default: () => new Set() },
})

const emit = defineEmits(['add-node'])

/** 全图只能有一个入口 / 出口（见文件头注释）；其余类型可以重复添加 */
const UNIQUE_TYPES = new Set(['1', '2'])

/** 搜索关键字（C4）：匹配节点名称与职责说明，大小写不敏感 */
const keyword = ref('')

/** 窄屏默认折叠：画布宽度本来就紧张，面板不该再挤占（用户可手动展开） */
const collapsed = ref(typeof window !== 'undefined' && window.innerWidth < 768)

const hasKeyword = computed(() => Boolean(keyword.value.trim()))

/** 当前可见条目：无关键字时原样返回 `NODE_CATALOG`，避免每次渲染都新建数组 */
const visibleItems = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return NODE_CATALOG
  return NODE_CATALOG.filter(
    (item) => item.name.toLowerCase().includes(key) || item.desc.toLowerCase().includes(key),
  )
})

/** 该条目是否被「全图唯一」规则拦截 */
const blockedOf = (item) => UNIQUE_TYPES.has(item.type) && props.existingTypes.has(item.type)

/** 条目悬停提示：可添加时看职责说明，不可添加时说清原因 */
const hintOf = (item) => (blockedOf(item) ? `画布上已有「${item.name}」节点` : item.desc)

const clearSearch = () => {
  keyword.value = ''
}

const handleAdd = (item) => {
  if (blockedOf(item)) return
  emit('add-node', item.type)
}

/** 正在被拖拽的条目类型：只用来给条目本身降透明度，不参与落点计算 */
const draggingType = ref(null)

/** 拖拽开始：把节点类型写进 dataTransfer，画布页的 drop 会读它决定落点与节点类型 */
const handleDragStart = (event, item) => {
  if (blockedOf(item)) {
    event.preventDefault()
    return
  }
  writeNodeDragPayload(event.dataTransfer, item.type)
  draggingType.value = item.type
}

const handleDragEnd = () => {
  draggingType.value = null
}
</script>

<template>
  <div class="node-panel">
    <div v-if="!collapsed" class="node-panel__card">
      <div class="node-panel__head">
        <span class="node-panel__title">节点</span>
        <span class="node-panel__count">{{ visibleItems.length }}</span>
        <button
          class="node-panel__icon-btn"
          type="button"
          aria-label="收起节点面板"
          title="收起节点面板"
          @click="collapsed = true"
        >
          <el-icon><ArrowLeftBold /></el-icon>
        </button>
      </div>

      <div class="node-panel__search">
        <el-icon class="node-panel__search-icon"><Search /></el-icon>
        <input
          v-model="keyword"
          class="node-panel__search-input"
          type="text"
          placeholder="搜索节点"
          aria-label="搜索节点"
        />
        <button
          v-if="hasKeyword"
          class="node-panel__search-clear"
          type="button"
          aria-label="清空搜索"
          title="清空搜索"
          @click="clearSearch"
        >
          <el-icon><CircleClose /></el-icon>
        </button>
      </div>

      <ul v-if="visibleItems.length" class="node-panel__list">
        <li v-for="item in visibleItems" :key="item.type">
          <button
            class="node-panel__item"
            :class="{ 'node-panel__item--dragging': draggingType === item.type }"
            type="button"
            draggable="true"
            :disabled="blockedOf(item)"
            :title="hintOf(item)"
            :aria-label="`添加${item.name}节点`"
            @click="handleAdd(item)"
            @dragstart="handleDragStart($event, item)"
            @dragend="handleDragEnd"
          >
            <span class="node-panel__item-icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="node-panel__item-text">
              <span class="node-panel__item-name">{{ item.name }}</span>
              <span class="node-panel__item-desc">{{ item.desc }}</span>
            </span>
          </button>
        </li>
      </ul>

      <div v-else class="node-panel__empty">
        <p class="node-panel__empty-text">没有匹配「{{ keyword }}」的节点</p>
        <button class="node-panel__empty-btn" type="button" @click="clearSearch">清空搜索</button>
      </div>
    </div>

    <button
      v-else
      class="node-panel__rail"
      type="button"
      aria-label="展开节点面板"
      title="展开节点面板"
      @click="collapsed = false"
    >
      <el-icon class="node-panel__rail-icon"><Grid /></el-icon>
    </button>
  </div>
</template>

<style scoped>
/* 浮层容器：不吃画布手势，可交互元素各自声明 pointer-events: auto */
.node-panel {
  display: flex;
  min-height: 0;
  max-height: 100%;
  flex-direction: column;
  pointer-events: none;
}

/* ===== 展开态卡片 ===== */
.node-panel__card {
  display: flex;
  min-height: 0;
  max-height: 100%;
  width: 208px;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  pointer-events: auto;
}

/* ===== 头部条 ===== */
.node-panel__head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0 0 4px;
}

.node-panel__title {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  font-weight: 600;
}

.node-panel__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-bg-sunken);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  font-weight: 500;
  line-height: 18px;
  text-align: center;
}

.node-panel__icon-btn {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  margin-left: auto;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition: background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__icon-btn:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

/* ===== 搜索框（C4） ===== */
.node-panel__search {
  position: relative;
  display: flex;
  align-items: center;
}

.node-panel__search-icon {
  position: absolute;
  left: 8px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  pointer-events: none;
}

.node-panel__search-input {
  width: 100%;
  height: 30px;
  padding: 0 26px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  font-family: var(--font-sans);
  font-size: var(--tb-text-xs);
  outline: none;
  transition: border-color var(--tb-dur-2) var(--tb-ease-out),
    box-shadow var(--tb-dur-2) var(--tb-ease-out),
    background-color var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__search-input::placeholder {
  color: var(--tb-text-tertiary);
}

.node-panel__search-input:focus {
  border-color: var(--tb-primary);
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 3px var(--tb-primary-soft);
}

.node-panel__search-clear {
  position: absolute;
  right: 6px;
  display: flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--tb-radius-full);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  font-size: var(--tb-text-xs);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__search-clear:hover {
  background: var(--tb-bg-sunken);
  color: var(--tb-text-primary);
}

/* ===== 节点列表 ===== */
.node-panel__list {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.node-panel__item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out), transform var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__item:hover:not(:disabled) {
  background: var(--tb-primary-soft);
  color: var(--tb-text-primary);
  transform: translateY(-1px);
}

.node-panel__item:active:not(:disabled) {
  transform: translateY(0);
}

/* 全图唯一性拦截：整体降饱和、不响应点击，悬停由 title 说明原因 */
.node-panel__item:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* 拖拽中：条目半透明，明确「它正在被拖走」 */
.node-panel__item--dragging {
  opacity: 0.45;
  transform: none;
}

.node-panel__item-icon {
  display: flex;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-md);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__item:hover:not(:disabled) .node-panel__item-icon {
  background: var(--tb-bg-surface);
  color: var(--tb-primary);
}

.node-panel__item-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.node-panel__item-name {
  color: inherit;
  font-size: var(--tb-text-xs);
  font-weight: 500;
}

.node-panel__item-desc {
  overflow: hidden;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 搜索空态 ===== */
.node-panel__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
}

.node-panel__empty-text {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: 1.5;
  text-align: center;
  word-break: break-all;
}

.node-panel__empty-btn {
  padding: 4px 10px;
  border: var(--tb-border-width) solid var(--tb-primary-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  cursor: pointer;
  font-family: inherit;
  font-size: var(--tb-text-2xs);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__empty-btn:hover {
  background: var(--tb-bg-raised);
}

/* ===== 折叠态窄栏 ===== */
.node-panel__rail {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  color: var(--tb-text-secondary);
  cursor: pointer;
  pointer-events: auto;
  transition: border-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out), transform var(--tb-dur-2) var(--tb-ease-out);
}

.node-panel__rail:hover {
  border-color: var(--tb-primary-border);
  color: var(--tb-primary);
  transform: translateY(-1px);
}

.node-panel__rail-icon {
  font-size: var(--tb-text-lg);
}

/* ===== 焦点与动效 ===== */
.node-panel button:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

@media (prefers-reduced-motion: reduce) {
  .node-panel__item,
  .node-panel__item-icon,
  .node-panel__icon-btn,
  .node-panel__search-input,
  .node-panel__search-clear,
  .node-panel__empty-btn,
  .node-panel__rail {
    transition: none;
  }

  .node-panel__item:hover:not(:disabled),
  .node-panel__rail:hover {
    transform: none;
  }
}
</style>
