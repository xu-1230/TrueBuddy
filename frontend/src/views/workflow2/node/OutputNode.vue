<script setup>
/**
 * 输出节点卡片（type = '6'）。
 *
 * 它**只声明 `inputs`**：这些变量的名字就是它的产出名（后端 `OutputNode#apply` 把引用到的值原样透传
 * 成自己的产出），所以卡片上那一行标成「输出」而不是「输入」—— 与结束节点同一呈现口径。
 *
 * 与结束节点的差别只有一个：输出节点**有出边**（迭代体内它是「评分 → 评语 → 输出」的最后一个，
 * 再由它指回容器作为迭代体的虚拟出口），所以它同时带入口与出口两个连接点。
 */
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Upload } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_STATE_KEY } from '../utils/run-inject'
import { nodeRuntimeOf } from '../utils/run-state'
import { unboundNamesOf } from '../utils/variable-sources'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

/** 输出参数声明（取值来源逐一指向产出方） */
const outputs = computed(() => props.data?.inputs ?? [])

/** 未绑定取值的输出参数名（交给 VariableTags 打告警色），口径与运行前校验共用 `unboundNamesOf` */
const unboundNames = computed(() => unboundNamesOf(outputs.value))

/** 运行态由画布页 provide；卡片可能在无运行态的上下文复用，故允许缺省 */
const runState = inject(RUN_STATE_KEY, null)
const runtime = computed(() => (runState ? nodeRuntimeOf(runState.value, props.id) : null))
</script>

<template>
  <div class="node-wrapper">
    <div class="node-card">
      <!-- 节点头部 -->
      <div class="node-header">
        <div class="node-icon">
          <el-icon class="node-icon-svg">
            <Upload />
          </el-icon>
        </div>
        <div class="node-label-wrap">
          <div class="node-label" :title="data.label">{{ data.label }}</div>
        </div>
        <button class="node-more-btn nodrag" type="button" aria-label="更多操作">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="3" cy="8" r="1.3" fill="currentColor" />
            <circle cx="8" cy="8" r="1.3" fill="currentColor" />
            <circle cx="13" cy="8" r="1.3" fill="currentColor" />
          </svg>
        </button>
      </div>

      <!-- 输出参数：名字即产出名，取值来源指向产出方；未绑定取值的用告警色提示 -->
      <VariableTags label="输出" :variables="outputs" :undefined-names="unboundNames" />

      <!-- 出口 / 入口两个连接点：它既是迭代体链路的终点，又要接回容器 -->
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" />
      <Handle id="in" type="target" :position="Position.Left" class="node-handle node-handle-target" />
    </div>

    <!-- 运行结果：贴在卡片正下方；节点在迭代容器内时由结果面板自行跳过（容器统一展示） -->
    <NodeResultPanel v-if="runtime" :runtime="runtime" :node-id="id" />
  </div>
</template>

<style scoped>
/* ===== 节点容器：卡片在上，运行结果在下 ===== */
.node-wrapper {
  position: relative;
  display: flex;
  flex-direction: column;
  width: auto;
}

/* ===== 节点卡片 ===== */
.node-card {
  position: relative;
  width: 17rem;
  border-radius: 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.node-card:hover {
  border-color: var(--tb-border-strong);
  box-shadow: var(--tb-shadow-2);
}

/* ===== 节点头部 ===== */
.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
}

/* ===== 图标 ===== */
.node-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--tb-primary);
  color: #fff;
}

.node-icon-svg {
  font-size: 16px;
}

/* ===== 标签 ===== */
.node-label-wrap {
  display: flex;
  min-width: 0;
  flex-grow: 1;
  align-items: center;
}

.node-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  color: var(--tb-text-primary);
}

/* ===== 更多按钮 ===== */
.node-more-btn {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
}

.node-more-btn:hover {
  background: var(--tb-bg-sunken);
  color: var(--tb-text-secondary);
}

/* ===== Handle ===== */
.node-handle {
  width: 8px !important;
  height: 8px !important;
  border: var(--tb-border-width) solid var(--tb-border-strong) !important;
  background: var(--tb-bg-surface) !important;
  opacity: 0;
  transition: opacity 0.2s;
}

.node-card:hover .node-handle {
  opacity: 1;
}
</style>
