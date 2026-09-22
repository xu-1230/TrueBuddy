<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

/** 变量列表：兼容字符串数组与 { name, type, description, schema? } 对象数组 */
const variables = computed(() => {
  const list = props.data?.outputs ?? props.data?.inputs ?? props.data?.blocks ?? []
  return list
    .map((item) => (typeof item === 'string' ? { name: item } : item))
    .filter((item) => item?.name)
})
</script>

<template>
  <div class="node-wrapper">
    <div class="node-card">
      <!-- 节点头部 -->
      <div class="node-header">
        <div class="node-icon">
          <svg
            width="16"
            height="16"
            viewBox="0 0 16 16"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            class="node-icon-svg"
            aria-hidden="true"
          >
            <path
              d="M4.25 2.25a.75.75 0 0 1 .75.75v10a.75.75 0 0 1-1.5 0V3a.75.75 0 0 1 .75-.75Z"
              fill="currentColor"
            />
            <path
              d="M5 3.5h6.05a.5.5 0 0 1 .42.77L10.2 6.5l1.27 2.23a.5.5 0 0 1-.42.77H5V3.5Z"
              fill="currentColor"
            />
          </svg>
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

      <!-- 输入变量 -->
      <div v-if="variables.length" class="node-inputs">
        <span class="node-inputs-label">输入</span>
        <div class="node-input-tags">
          <span
            v-for="item in variables"
            :key="item.name"
            class="node-input-tag"
            :title="item.description"
          >
            {{ item.name }}
          </span>
        </div>
      </div>

      <!-- 源 Handle（右侧） -->
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" />

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
  width: 16px;
  height: 16px;
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

/* ===== 输入变量 ===== */
.node-inputs {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 0 12px 12px;
}

.node-inputs-label {
  flex-shrink: 0;
  font-size: 13px;
  line-height: 22px;
  color: var(--tb-text-tertiary);
}

.node-input-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.node-input-tag {
  padding: 0 8px;
  border-radius: 6px;
  background: var(--tb-bg-sunken);
  font-size: 13px;
  line-height: 22px;
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
