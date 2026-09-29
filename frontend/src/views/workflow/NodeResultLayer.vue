<script setup>
/**
 * 节点执行结果层：在画布上把执行状态、节点输出与**人工确认卡片**直接画到对应节点上
 * （类似扣子的调试视图），不使用任何弹窗。
 *
 * 实现要点：不改动 7 个节点组件，而是叠一层「与视口同步变换」的覆盖层——
 * 外层用与 `.vue-flow__viewport` 相同的 `translate + scale` 跟随平移 / 缩放，
 * 内层用节点的 `computedPosition`（画布绝对坐标，迭代子节点也已换算）与 `dimensions` 定位，
 * 因此拖拽、缩放、展开迭代容器都能自动跟随。
 *
 * 覆盖层本身 `pointer-events: none`，不拦截画布交互；只有卡片可交互。
 *
 * 人工确认卡片：执行暂停在「人工确认」节点时，在该节点**正下方**渲染勾选清单 + 提交按钮。
 * 勾选状态由父组件持有（受控），本组件只负责渲染与派发事件。
 */
import { computed, ref } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import { MAX_OUTPUT_ROWS } from './utils/execution-result'

defineOptions({ name: 'NodeResultLayer' })

/** 人工确认卡片的最小宽度（节点卡片 15rem 放不下勾选列表时适当放宽） */
const CONFIRM_CARD_MIN_WIDTH = 320

const props = defineProps({
  /** 节点 id → 输出列表（[{ name, text }]） */
  outputs: { type: Object, default: () => ({}) },
  /** 节点 id → 执行状态：running / success / failed / skipped */
  statuses: { type: Object, default: () => ({}) },
  /** 等待用户输入的人工确认数据：{ nodeId, nodeName, plan, defaultPlan }；null 表示当前无暂停节点 */
  pending: { type: Object, default: null },
  /** 已勾选的文件路径（受控，与父组件 v-model:selected 双向绑定） */
  selected: { type: Array, default: () => [] },
  /** 是否正在提交确认清单（提交中禁用按钮） */
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:selected', 'submit', 'cancel'])

/** 状态 → 卡片标题（默认即「执行输出」） */
const STATUS_TITLES = {
  running: '执行中',
  failed: '执行失败',
}

const { viewport, getNodes } = useVueFlow()

/** 与画布视口保持同一套变换，保证结果与节点严丝合缝 */
const layerStyle = computed(() => {
  const { x = 0, y = 0, zoom = 1 } = viewport.value ?? {}
  return { transform: `translate(${x}px, ${y}px) scale(${zoom})` }
})

/* ===== 输出卡片：点击展开完整内容 ===== */

const expandedIds = ref(new Set())

const toggleExpanded = (nodeId) => {
  const next = new Set(expandedIds.value)
  if (next.has(nodeId)) next.delete(nodeId)
  else next.add(nodeId)
  expandedIds.value = next
}

/* ===== 定位 ===== */

const items = computed(() =>
  getNodes.value
    .map((node) => {
      const status = props.statuses[node.id]
      const outputs = props.outputs[node.id] ?? []
      if (!status && !outputs.length) return null
      const position = node.computedPosition ?? node.position ?? { x: 0, y: 0 }
      const width = node.dimensions?.width ?? 0
      const height = node.dimensions?.height ?? 0
      return {
        id: node.id,
        status,
        expanded: expandedIds.value.has(node.id),
        title: STATUS_TITLES[status] ?? '执行输出',
        outputs: outputs.slice(0, MAX_OUTPUT_ROWS),
        more: Math.max(0, outputs.length - MAX_OUTPUT_ROWS),
        outlineStyle: {
          transform: `translate(${position.x}px, ${position.y}px)`,
          width: `${width}px`,
          height: `${height}px`,
        },
        cardStyle: {
          transform: `translate(${position.x}px, ${position.y + height + 8}px)`,
          width: `${width}px`,
        },
      }
    })
    .filter(Boolean),
)

/* ===== 人工确认卡片（挂在暂停节点正下方） ===== */

/** 可勾选的清单项（过滤掉没有路径的条目） */
const confirmOptions = computed(() =>
  (Array.isArray(props.pending?.plan) ? props.pending.plan : []).filter((item) => item?.filePath),
)

const confirmStyle = computed(() => {
  const pending = props.pending
  if (!pending?.nodeId) return null
  const node = getNodes.value.find((item) => item.id === pending.nodeId)
  if (!node) return null
  const position = node.computedPosition ?? node.position ?? { x: 0, y: 0 }
  const height = node.dimensions?.height ?? 0
  const width = Math.max(node.dimensions?.width ?? 0, CONFIRM_CARD_MIN_WIDTH)
  return {
    transform: `translate(${position.x}px, ${position.y + height + 8}px)`,
    width: `${width}px`,
  }
})

const confirmAllSelected = computed(
  () => confirmOptions.value.length > 0 && props.selected.length === confirmOptions.value.length,
)

const setSelected = (paths) => emit('update:selected', paths)

/** 全选 / 再点一次取消全选 */
const toggleAllSelected = () => {
  setSelected(confirmAllSelected.value ? [] : confirmOptions.value.map((item) => item.filePath))
}

const invertSelected = () => {
  const chosen = new Set(props.selected)
  setSelected(confirmOptions.value.map((item) => item.filePath).filter((path) => !chosen.has(path)))
}
</script>

<template>
  <div class="result-layer">
    <div class="result-layer__canvas" :style="layerStyle">
      <template v-for="item in items" :key="item.id">
        <!-- 执行状态：节点外描一圈 -->
        <div
          v-if="item.status"
          class="result-layer__outline"
          :class="`is-${item.status}`"
          :style="item.outlineStyle"
        />

        <!-- 节点下方的结果卡片（点击展开完整内容） -->
        <div
          v-if="item.outputs.length"
          class="result-card"
          :class="[`is-${item.status || 'idle'}`, { 'is-expanded': item.expanded }]"
          :style="item.cardStyle"
        >
          <div
            class="result-card__head"
            role="button"
            tabindex="0"
            @click="toggleExpanded(item.id)"
            @keydown.enter="toggleExpanded(item.id)"
          >
            <span class="result-card__title">{{ item.title }}</span>
            <span class="result-card__more">
              {{ item.expanded ? '收起' : `${item.outputs.length + item.more} 项` }}
            </span>
          </div>
          <div v-for="output in item.outputs" :key="output.name" class="result-card__row">
            <span class="result-card__name" :title="output.name">{{ output.name }}</span>
            <span class="result-card__value">{{ output.text }}</span>
          </div>
          <p v-if="item.more && !item.expanded" class="result-card__more-hint">
            还有 {{ item.more }} 项，点击展开
          </p>
        </div>
      </template>

      <!--
        人工确认卡片：执行暂停在「人工确认」节点时，挂在该节点正下方。
        勾选清单 → 「提交确认并继续」后同一条事件流继续推送后续节点。
      -->
      <div v-if="confirmStyle" class="confirm-card" :style="confirmStyle">
        <div class="confirm-card__head">
          <span class="confirm-card__title">人工确认 · {{ pending.nodeName || pending.nodeId }}</span>
          <span class="confirm-card__count">
            {{ confirmOptions.length ? `已选 ${selected.length} / ${confirmOptions.length}` : '无可批改项' }}
          </span>
        </div>
        <p class="confirm-card__hint">勾选本次需要批改的作业，提交后继续执行。</p>
        <div class="confirm-card__tools">
          <button type="button" class="confirm-card__chip" @click="toggleAllSelected">
            {{ confirmAllSelected ? '取消全选' : '全选' }}
          </button>
          <button type="button" class="confirm-card__chip" @click="invertSelected">反选</button>
        </div>
        <el-checkbox-group
          class="confirm-card__list"
          :model-value="selected"
          @update:model-value="setSelected"
        >
          <el-checkbox
            v-for="item in confirmOptions"
            :key="item.filePath"
            :value="item.filePath"
            class="confirm-card__item"
          >
            <span class="confirm-card__path" :title="item.filePath">{{ item.filePath }}</span>
            <span v-if="item.reason" class="confirm-card__reason">{{ item.reason }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div class="confirm-card__actions">
          <button
            type="button"
            class="confirm-card__btn"
            :disabled="submitting"
            @click="emit('cancel')"
          >
            停止接收
          </button>
          <button
            type="button"
            class="confirm-card__btn confirm-card__btn--primary"
            :disabled="submitting || !selected.length"
            @click="emit('submit')"
          >
            {{ submitting ? '提交中…' : '提交确认并继续' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-layer {
  position: absolute;
  inset: 0;
  z-index: 4;
  overflow: hidden;
  /* 覆盖层不拦截画布交互，只有卡片可交互 */
  pointer-events: none;
}

/* 0 尺寸容器 + 绝对定位子元素：不参与布局，只承担视口变换 */
.result-layer__canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 0;
  transform-origin: 0 0;
}

/* ===== 状态描边 ===== */
.result-layer__outline {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: 12px;
}

.result-layer__outline.is-success {
  outline: 2px solid var(--tb-success);
  outline-offset: 2px;
}

.result-layer__outline.is-failed {
  outline: 2px solid var(--tb-danger);
  outline-offset: 2px;
}

.result-layer__outline.is-running {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  animation: result-layer-pulse 1.2s var(--tb-ease-inout) infinite;
}

/* 未执行到的节点：虚线灰框，不抢视觉 */
.result-layer__outline.is-skipped {
  outline: 1px dashed var(--tb-border-strong);
  outline-offset: 2px;
}

@keyframes result-layer-pulse {
  50% {
    outline-color: var(--tb-border);
  }
}

/* ===== 结果卡片 ===== */
.result-card {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  max-height: 132px;
  flex-direction: column;
  gap: 2px;
  overflow: auto;
  padding: 8px 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  pointer-events: auto;
}

.result-card.is-expanded {
  max-height: 320px;
}

.result-card.is-success {
  border-color: var(--tb-success);
}

.result-card.is-failed {
  border-color: var(--tb-danger);
}

.result-card.is-running {
  border-color: var(--tb-primary);
}

.result-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.result-card__head[role="button"] {
  cursor: pointer;
}

.result-card__title {
  font-weight: 600;
}

.result-card__more {
  flex-shrink: 0;
}

.result-card__row {
  display: flex;
  min-width: 0;
  gap: 6px;
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
}

.result-card__name {
  flex-shrink: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--tb-text-secondary);
}

.result-card__value {
  min-width: 0;
  overflow: hidden;
  color: var(--tb-text-primary);
  word-break: break-word;
}

/* 展开后完整展示，不再截断 */
.result-card.is-expanded .result-card__value {
  white-space: pre-wrap;
}

.result-card__more-hint {
  margin: 0;
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
}

/* ===== 人工确认卡片（节点正下方） ===== */
.confirm-card {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  border: var(--tb-border-width) solid var(--tb-warning);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
  pointer-events: auto;
}

.confirm-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.confirm-card__title {
  min-width: 0;
  overflow: hidden;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confirm-card__count {
  flex-shrink: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.confirm-card__hint {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
}

.confirm-card__tools {
  display: flex;
  gap: 10px;
}

.confirm-card__chip {
  padding: 0;
  border: none;
  background: none;
  color: var(--tb-primary-text);
  font-size: var(--tb-text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.confirm-card__list {
  display: flex;
  max-height: 168px;
  flex-direction: column;
  gap: 2px;
  overflow: auto;
}

.confirm-card__item {
  height: auto;
  margin-right: 0;
}

.confirm-card__path {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-2xs);
}

.confirm-card__reason {
  margin-left: 6px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.confirm-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.confirm-card__btn {
  padding: 3px 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xs);
  background: transparent;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  cursor: pointer;
}

.confirm-card__btn:hover:not(:disabled) {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

.confirm-card__btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.confirm-card__btn--primary {
  border-color: var(--tb-primary);
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-weight: 600;
}

.confirm-card__btn--primary:hover:not(:disabled) {
  background: var(--tb-primary);
  color: var(--tb-primary-text);
}

@media (prefers-reduced-motion: reduce) {
  .result-layer__outline.is-running {
    animation: none;
  }
}
</style>
