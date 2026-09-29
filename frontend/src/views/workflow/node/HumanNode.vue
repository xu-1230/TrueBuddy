<script setup>
/**
 * 人工确认节点（后端 type = 30 / human）。
 *
 * 执行到此会真实中断，等待用户确认清单后从断点恢复。
 * 该节点的「待确认清单」来自后端下发的 `data.inputs.plan` 引用（原样暂存在 data.backendInputs），
 * 画布上不展示输入变量，只展示节点名称与声明的输出（如 confirmedPlan）。
 *
 * 目前只由「加载后端画布」产生：画布自建该节点缺少 plan 输入配置，中断时清单会为空。
 */
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { QuestionFilled } from '@element-plus/icons-vue'
import VariableTags from '../VariableTags.vue'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

/** 变量声明：归一化（字符串写法 / 过滤空名）由 VariableTags 统一处理 */
const outputs = computed(() => props.data?.outputs ?? [])
</script>

<template>
  <div class="node-wrapper">
    <div class="node-card">
      <!-- 节点头部 -->
      <div class="node-header">
        <div class="node-icon">
          <el-icon class="node-icon-svg">
            <QuestionFilled />
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

      <!-- 停顿提示 -->
      <div class="node-hint">执行到此会中断，等待确认后继续</div>

      <!-- 输出变量 -->
      <VariableTags label="输出" :variables="outputs" />

      <!-- 源 Handle（右侧） -->
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" />

      <!-- 目标 Handle（左侧） -->
      <Handle id="in" type="target" :position="Position.Left" class="node-handle node-handle-target" />
    </div>
  </div>
</template>

<style scoped>
/* ===== 节点容器 ===== */
.node-wrapper {
  position: relative;
  display: flex;
  width: auto;
}

/* ===== 节点卡片 ===== */
.node-card {
  position: relative;
  width: 15rem;
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

/* ===== 图标：借用 warning 状态色表达「中断 / 等待确认」语义 ===== */
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

/* ===== 停顿提示 ===== */
.node-hint {
  margin: 0 12px 8px;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--tb-warning-soft);
  font-size: 12px;
  line-height: 16px;
  color: var(--tb-text-secondary);
}

/* ===== 输出变量：标签行样式全部收在 VariableTags 里（四张卡片共用一份） ===== */

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
