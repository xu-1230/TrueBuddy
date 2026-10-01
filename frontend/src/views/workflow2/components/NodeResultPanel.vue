<script setup>
/**
 * 节点运行结果面板：贴在节点卡片正下方，展示执行状态与本次产出的变量。
 *
 * 纯展示组件：运行态由画布页 provide、节点卡片 inject 后按 `props.id` 取出传进来，
 * 面板自己不关心事件流，也不写回画布数据。
 */
import { computed, ref } from 'vue'
import { prettyJson, resultEntriesOf, RUN_STATUS, statusTextOf } from '../utils/run-state'
import { useNestedNode } from '../utils/use-nested-node'

defineOptions({ name: 'NodeResultPanel' })

const props = defineProps({
  /** 该节点的运行态：`{ status, result, waitLabel }` */
  runtime: { type: Object, required: true },
  /** 节点 id：判断是否挂在迭代容器内部（容器内的节点不单独展示结果） */
  nodeId: { type: String, default: '' },
})

const status = computed(() => props.runtime?.status ?? RUN_STATUS.idle)
const entries = computed(() => resultEntriesOf(props.runtime?.result))

/** 挂在迭代容器内部时不渲染：结果由迭代节点卡片正下方的统一结果区展示，避免子块各自挂面板 */
const nested = useNestedNode(props.nodeId)

/** 展开查看完整 JSON 的变量名（同一时刻只展开一行） */
const expandedName = ref('')

const toggleJson = (name) => {
  expandedName.value = expandedName.value === name ? '' : name
}
</script>

<template>
  <div
    v-if="!nested"
    class="node-result"
    :class="`node-result--${status}`"
  >
    <div class="node-result__status">
      <span class="node-result__dot" />
      <span class="node-result__status-text">{{ statusTextOf(status) }}</span>
      <span v-if="runtime.waitLabel" class="node-result__wait-label">{{ runtime.waitLabel }}</span>
    </div>

    <p v-if="!entries.length" class="node-result__empty">本次无产出变量</p>

    <ul v-else class="node-result__list">
      <li v-for="item in entries" :key="item.name" class="node-result__item">
        <button class="node-result__row" type="button" @click="toggleJson(item.name)">
          <span class="node-result__name">{{ item.name }}</span>
          <span class="node-result__value">{{ item.summary }}</span>
        </button>
        <pre v-if="expandedName === item.name" class="node-result__json">{{ prettyJson(item.raw) }}</pre>
      </li>
    </ul>
  </div>
</template>

<style scoped>
/* ===== 面板容器：与节点卡片同宽，贴在卡片正下方 ===== */
.node-result {
  width: 17rem;
  margin-top: 8px;
  padding: 8px 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  animation: node-result-in 120ms var(--tb-ease-out);
}

/* ===== 状态行 ===== */
.node-result__status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-result__dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  border-radius: 9999px;
  background: var(--tb-text-tertiary);
}

.node-result__status-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--tb-text-secondary);
  transition: color var(--tb-dur-2) var(--tb-ease-out);
}

.node-result__wait-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* ===== 产出变量 ===== */
.node-result__list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.node-result__item + .node-result__item {
  margin-top: 2px;
}

.node-result__row {
  display: flex;
  width: 100%;
  align-items: baseline;
  gap: 8px;
  padding: 2px 4px;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.node-result__row:hover {
  background: var(--tb-bg-raised);
}

.node-result__name {
  flex-shrink: 0;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--tb-text-secondary);
}

.node-result__value {
  flex-grow: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--tb-text-primary);
}

.node-result__json {
  max-height: 160px;
  margin: 4px 0 0;
  padding: 6px 8px;
  overflow: auto;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
  color: var(--tb-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.node-result__empty {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

/* ===== 状态语义色 ===== */
.node-result--running .node-result__dot {
  background: var(--tb-primary);
  animation: node-result-pulse 1.2s ease-in-out infinite;
}

.node-result--running .node-result__status-text {
  color: var(--tb-primary);
}

.node-result--done .node-result__dot {
  background: var(--tb-success);
}

.node-result--done .node-result__status-text {
  color: var(--tb-success);
}

.node-result--waiting {
  border-color: var(--tb-warning);
}

.node-result--waiting .node-result__dot {
  background: var(--tb-warning);
}

.node-result--waiting .node-result__status-text {
  color: var(--tb-warning);
}

.node-result--failed {
  border-color: var(--tb-danger);
}

.node-result--failed .node-result__dot {
  background: var(--tb-danger);
}

.node-result--failed .node-result__status-text {
  color: var(--tb-danger);
}

/* ===== 动效 ===== */
@keyframes node-result-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes node-result-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .node-result {
    animation: none;
  }

  .node-result--running .node-result__dot {
    animation: none;
  }
}
</style>
