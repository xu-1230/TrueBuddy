<script setup>
/**
 * 人工确认节点卡片（type = 5）。
 *
 * 与其它卡片一样只做展示：变量声明走 VariableTags，运行态由画布页 provide；
 * 差别是执行到这里后端会中断，因此等待确认时卡片下方多一个输入框与「确认继续」按钮，
 * 确认动作交回画布页发起 `/workflow/resume`。
 */
import { computed, inject, ref } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { User } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_ACTION_KEY, RUN_STATE_KEY } from '../utils/run-inject'
import { nodeRuntimeOf, RUN_STATUS } from '../utils/run-state'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ name: 'HumanNode', inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

const inputs = computed(() => props.data?.inputs ?? [])
const outputs = computed(() => props.data?.outputs ?? [])

/** 运行态与运行动作由画布页 provide；卡片可能在无运行态的上下文复用，故允许缺省 */
const runState = inject(RUN_STATE_KEY, null)
const runActions = inject(RUN_ACTION_KEY, null)
const runtime = computed(() => (runState ? nodeRuntimeOf(runState.value, props.id) : null))
const waiting = computed(() => runtime.value?.status === RUN_STATUS.waiting)

/** 待确认内容：确认后写入后端的 confirmedPlan */
const content = ref('')

const confirm = () => {
  runActions?.confirm?.(props.id, content.value)
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
      <VariableTags label="输入" :variables="inputs" :last="false" />

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

      <!-- 等待确认区不属于节点本体：终止 click 冒泡，避免点输入框 / 「确认继续」时打开节点抽屉 -->
      <div v-if="waiting" class="node-confirm nodrag" @click.stop>
        <input
          v-model="content"
          class="node-confirm__input nodrag"
          type="text"
          placeholder="填写确认内容，例如：确认按名单批改"
          @keyup.enter="confirm"
        />
        <button class="node-confirm__btn nodrag" type="button" @click="confirm">确认继续</button>
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

.node-confirm__input {
  min-width: 0;
  flex-grow: 1;
  padding: 4px 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
  font-size: 13px;
  color: var(--tb-text-primary);
  outline: none;
  transition: border-color var(--tb-dur-2) var(--tb-ease-out);
}

.node-confirm__input:focus {
  border-color: var(--tb-primary);
  background: var(--tb-bg-surface);
}

.node-confirm__input::placeholder {
  color: var(--tb-text-tertiary);
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
