<script setup>
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_STATE_KEY } from '../utils/run-inject'
import { nodeRuntimeOf } from '../utils/run-state'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

/** 运行态由画布页 provide；卡片可能在无运行态的上下文复用，故允许缺省 */
const runState = inject(RUN_STATE_KEY, null)
const runtime = computed(() => (runState ? nodeRuntimeOf(runState.value, props.id) : null))

</script>

<template>
  <div class="node-wrapper">
    <div class="node-card">
      <!-- 节点头部 -->
      <div class="node-header">
        <button class="node-title-btn" type="button" aria-label="输出 2">
          <div class="node-icon">
            <svg
              width="14"
              height="14"
              viewBox="0 0 14 14"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              class="node-icon-svg"
              data-icon="End"
              aria-hidden="true"
            >
              <g id="icons/end">
                <path
                  id="Vector (Stroke)"
                  fill-rule="evenodd"
                  clip-rule="evenodd"
                  d="M6.67315 1.18094C6.87691 1.0639 7.12769 1.06475 7.33067 1.18315L10.8307 3.22481C11.0323 3.34242 11.1562 3.55826 11.1562 3.79167C11.1562 4.02507 11.0323 4.24091 10.8307 4.35852L7.65625 6.21026V9.91667C7.65625 10.2791 7.36244 10.5729 7 10.5729C6.63756 10.5729 6.34375 10.2791 6.34375 9.91667V5.84577C6.34361 5.83788 6.34361 5.83 6.34375 5.82213V1.75C6.34375 1.51502 6.46939 1.29797 6.67315 1.18094ZM7.65625 4.69078L9.19758 3.79167L7.65625 2.89256V4.69078ZM5.31099 8.25466C5.37977 8.61051 5.14704 8.95473 4.79119 9.0235C3.97285 9.18165 3.32667 9.41764 2.90374 9.67762C2.45323 9.95454 2.40625 10.1564 2.40625 10.2086C2.40625 10.2448 2.42254 10.3508 2.60674 10.5202C2.79151 10.6901 3.09509 10.8732 3.52555 11.0406C4.38229 11.3738 5.61047 11.594 7 11.594C8.38954 11.594 9.61773 11.3738 10.4745 11.0406C10.9049 10.8732 11.2085 10.6901 11.3933 10.5202C11.5775 10.3508 11.5938 10.2448 11.5938 10.2086C11.5938 10.1564 11.5468 9.95454 11.0963 9.67762C10.6733 9.41764 10.0271 9.18165 9.20881 9.0235C8.85296 8.95473 8.62023 8.61051 8.68901 8.25465C8.75778 7.8988 9.102 7.66608 9.45786 7.73485C10.3682 7.91077 11.1803 8.18867 11.7836 8.55947C12.3592 8.91331 12.9062 9.45912 12.9062 10.2086C12.9062 10.7361 12.6287 11.1672 12.2816 11.4864C11.935 11.805 11.4698 12.0618 10.9502 12.2639C9.90679 12.6696 8.50997 12.9065 7 12.9065C5.49004 12.9065 4.09322 12.6696 3.04983 12.2639C2.53023 12.0618 2.06497 11.805 1.7184 11.4864C1.37128 11.1672 1.09375 10.7361 1.09375 10.2086C1.09375 9.45913 1.64077 8.91332 2.21642 8.55947C2.81966 8.18867 3.63181 7.91077 4.54215 7.73485C4.898 7.66608 5.24222 7.8988 5.31099 8.25466Z"
                  fill="currentColor"
                />
              </g>
            </svg>
          </div>
          <div class="node-label-wrap">
            <div class="node-label" :title="data.label">
              {{ data.label }}
            </div>
          </div>
        </button>
      </div>

      <VariableTags label="输入" :variables="data.outputs" />

      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-target" />
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
  border-radius: 1rem;
  border: 1px solid transparent;
  width: auto;
}

/* ===== 节点卡片 ===== */
.node-card {
  position: relative;
  padding-bottom: 0.25rem;
  border-radius: 15px;
  border: 1px solid rgba(68,83,130, 0.25);
  width: 17rem; /* w-60 = 15rem */
  background: white;
  box-shadow: var(--tb-shadow-1);
  transition: box-shadow 0.2s;
}

.node-card:hover {
  border: 1px solid rebeccapurple;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
}

/* ===== 节点头部 ===== */
.node-header {
  display: flex;
  align-items: center;
  border-radius: 1rem 1rem 0 0;
  padding: 12px;
}

/* ===== 标题按钮 ===== */
.node-title-btn {
  margin-right: 0.25rem;
  display: flex;
  min-width: 0;
  flex-grow: 1;
  appearance: none;
  align-items: center;
  border-radius: 0.375rem;
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
}



/* ===== 图标 ===== */
.node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0.5px solid rgba(255, 255, 255, 0.02);
  color: #fff;
  width: 1.5rem;
  height: 1.5rem;
  border-radius: 0.5rem;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
  background: var(--util-colors-warning-warning-500, var(--tb-accent));
  margin-right: 0.5rem;
  flex-shrink: 0;
}

.node-icon-svg {
  width: 1rem;
  height: 1rem;
}

/* ===== 标签 ===== */
.node-label-wrap {
  display: flex;
  min-width: 0;
  flex-grow: 1;
  align-items: center;
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #000;
}

.node-label {
  min-width: 0;
  flex-grow: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 操作按钮区域 ===== */
.node-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
}

/* ===== 自定义 Handle 基础 ===== */
.custom-handle {
  position: absolute;
  width: 1rem !important;
  height: 1rem !important;
  border-radius: 0 !important;
  border: none !important;
  background: transparent !important;
  outline: none !important;
  z-index: 1;
  transition: transform 0.2s;
  top: 1rem !important;
  transform: translateY(0) !important;
}

.custom-handle:hover {
  transform: translateY(0) scale(1.25) !important;
}

/* Handle 装饰线（默认透明） */
.custom-handle::after {
  content: '';
  position: absolute;
  top: 0.25rem;
  height: 0.5rem;
  width: 0.125rem;
  background: var(--workflow-link-line-handle, var(--tb-border-strong));
  opacity: 0;
  transition: opacity 0.2s;
}

/* ===== 目标 Handle（左侧） ===== */
.custom-handle-target {
  left: -9px !important;
}

.custom-handle-target::after {
  left: 0.375rem;
  right: auto;
}

/* ===== 源 Handle（右侧） ===== */
.custom-handle-source {
  right: -9px !important;
}

.custom-handle-source::after {
  right: 0.375rem;
  left: auto;
}

/* Handle 悬停时显示装饰线 */
.custom-handle:hover::after {
  opacity: 1;
}

/* ===== 添加按钮 ===== */
.handle-add-btn {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 10;
  display: inline-flex;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  width: 1rem;
  height: 1rem;
  padding: 0;
  border-radius: 9999px;
  border: none;
  background: var(--components-button-primary-bg, var(--tb-primary));
  color: var(--components-button-primary-text, var(--tb-on-primary));
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1), inset 0 0 0 0.5px var(--components-button-primary-border, var(--tb-border));
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.handle-add-btn:hover {
  background: var(--components-button-primary-bg-hover, var(--tb-primary-active));
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1), 0 1px 3px rgba(0, 0, 0, 0.1);
  inset: 0 0 0 0.5px var(--components-button-primary-border-hover, var(--tb-border-strong));
}

.handle-add-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--state-accent-solid, var(--tb-primary));
}

/* 按钮可见状态 */
.handle-add-btn--visible {
  opacity: 1;
  pointer-events: auto;
}

/* 加号图标 */
.handle-plus-icon {
  display: block;
  width: 0.625rem;
  height: 0.625rem;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='none'%3E%3Cpath d='M8 3v10M3 8h10' stroke='white' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
  background-position: center;
}

/* ===== 工具提示 ===== */
.handle-tooltip {
  position: absolute;
  top: -0.25rem;
  left: 50%;
  display: none;
  transform: translate(-50%, -100%);
  border-radius: 0.5rem;
  border: 0.5px solid var(--components-panel-border, var(--tb-border));
  background: var(--components-tooltip-bg, var(--tb-bg-raised));
  padding: 0.375rem;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
  white-space: nowrap;
  pointer-events: none;
}

/* 悬停 Handle 时显示提示 */
.custom-handle:hover .handle-tooltip {
  display: block;
}

.tooltip-content {
  font-size: 0.75rem;
  line-height: 1.4;
  color: var(--text-tertiary, var(--tb-text-tertiary));
}

.tooltip-line {
  white-space: nowrap;
}

.tooltip-key {
  font-weight: 500;
  color: var(--text-secondary, var(--tb-text-secondary));
}

/* ===== 节点头部更多按钮 ===== */
.node-more-btn {
  display: flex;
  width: 1.25rem;
  height: 1.25rem;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  border-radius: 0.375rem;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--text-tertiary, var(--tb-text-tertiary));
}

.node-more-btn:hover {
  background: var(--state-base-hover, var(--tb-bg-raised));
}

.node-more-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 1px var(--components-input-border-hover, var(--tb-primary));
}

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