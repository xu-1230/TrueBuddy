<script setup>
/**
 * 人工确认节点卡片（type = 5）。
 *
 * 与其它卡片一样只做展示：变量声明走 VariableTags，运行态由画布页 provide。
 * 执行到这里后端会**中断**并下发一张表单卡片，填写与提交都在画布页弹出的弹窗里完成
 * （见 `components/HumanCardDialog.vue`）；卡片上只留一个「填写」入口，弹窗关掉后还能再打开。
 */
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { User } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_ACTION_KEY, RUN_STATE_KEY } from '../utils/run-inject'
import { nodeRuntimeOf, RUN_STATUS } from '../utils/run-state'
import { unboundNamesOf } from '../utils/variable-sources'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ name: 'HumanNode', inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

const inputs = computed(() => props.data?.inputs ?? [])
const outputs = computed(() => props.data?.outputs ?? [])

/**
 * 未绑定取值的输入变量名（交给 VariableTags 打告警色）：口径与大模型 / 迭代卡片、
 * 运行前校验共用 `unboundNamesOf`。输出是本节点产出的，不存在「没取值」。
 */
const unboundInputNames = computed(() => unboundNamesOf(inputs.value))

/** 运行态与运行动作由画布页 provide；卡片可能在无运行态的上下文复用，故允许缺省 */
const runState = inject(RUN_STATE_KEY, null)
const runActions = inject(RUN_ACTION_KEY, null)
const runtime = computed(() => (runState ? nodeRuntimeOf(runState.value, props.id) : null))
const waiting = computed(() => runtime.value?.status === RUN_STATUS.waiting)

/** 重新打开表单弹窗（中断时由画布页自动弹出，这里只是「关掉后还能再开」的入口） */
const openCard = () => {
  runActions?.openCard?.(props.id)
}
</script>

<template>
  <div class="node-wrapper">
    <div class="node-card">
      <!-- 节点头部 -->
      <div class="node-header">
        <div class="node-icon">
          <el-icon class="node-icon-svg">
            <User />
          </el-icon>
        </div>
        <div class="node-label-wrap">
          <div class="node-label" :title="data.label">{{ data.label }}</div>
        </div>
      </div>

      <!-- 输入变量：人工确认的待确认内容来源 -->
      <VariableTags label="输入" :variables="inputs" :undefined-names="unboundInputNames" :last="false" />

      <!-- 输出变量 -->
      <VariableTags label="输出" :variables="outputs" />

      <!-- 源 Handle（右侧） -->
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" />

      <!-- 目标 Handle（左侧） -->
      <Handle id="in" type="target" :position="Position.Left" class="node-handle node-handle-target" />
    </div>

    <!-- 运行结果与人工确认：贴在卡片正下方；节点在迭代容器内时结果面板自行跳过 -->
    <template v-if="runtime">
      <NodeResultPanel :runtime="runtime" :node-id="id" />

      <!-- 等待确认区不属于节点本体：终止 click 冒泡，避免点按钮时打开节点抽屉 -->
      <div v-if="waiting" class="node-confirm nodrag" @click.stop>
        <span class="node-confirm__hint">{{ runtime.waitLabel || '等待人工确认' }}</span>
        <button class="node-confirm__btn nodrag" type="button" @click="openCard">填写</button>
      </div>
    </template>
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

.node-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--tb-warning);
  color: #fff;
}

.node-icon-svg {
  font-size: 16px;
}

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

/* ===== 人工确认输入区 ===== */
.node-confirm {
  display: flex;
  width: 17rem;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  border: var(--tb-border-width) solid var(--tb-warning);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
}

.node-confirm__hint {
  min-width: 0;
  flex-grow: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--tb-text-secondary);
}

.node-confirm__btn {
  flex-shrink: 0;
  padding: 4px 10px;
  border: none;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-primary);
  font-size: 13px;
  font-weight: 500;
  color: var(--tb-on-primary);
  cursor: pointer;
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.node-confirm__btn:hover {
  background: var(--tb-primary-active);
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
