<script setup>
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { SwitchButton } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_STATE_KEY } from '../utils/run-inject'
import { nodeRuntimeOf } from '../utils/run-state'
import { unboundNamesOf } from '../utils/variable-sources'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

/** 输入变量声明（归一化与可见性由 VariableTags 统一处理） */
const inputs = computed(() => props.data?.inputs ?? [])

/**
 * 未绑定取值的输入变量名（交给 VariableTags 打告警色）：结束节点的输入要指向产出方，
 * 运行时才组装得出最终结果；口径与其余卡片、运行前校验共用 `unboundNamesOf`。
 */
const unboundInputNames = computed(() => unboundNamesOf(inputs.value))

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
            <SwitchButton />
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


      <!-- 输入变量：结束节点把上游变量绑定成最终结果；未绑定取值的用告警色提示「尚未定义」 -->
      <VariableTags label="输入" :variables="inputs" :undefined-names="unboundInputNames" />

      <!-- 目标 Handle（左侧）：结束节点只接收上游连线 -->
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
  background: var(--tb-success);
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

/* ===== 变量行：标签行样式全部收在 VariableTags 里（五张卡片共用一份） ===== */

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
