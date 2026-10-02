<script setup>
/**
 * 画布左侧浮层里的竖向工具栏（workflow2 版），与节点库面板并排停靠（定位由 WorkflowView 的
 * `.wf-left-rail` 负责，本组件不再自己定位）。
 *
 * 只负责「用户意图」：把按钮点击转成事件发给 WorkflowView，真正的行为由画布页决定 ——
 * 画布页会把当前画布提交给后端执行，并在执行期间把 `running` 置为 true 禁用按钮。
 *
 * 容器 pointer-events: none、只有按钮自己可交互，这样工具栏不会吃掉画布的拖拽 / 框选手势。
 */
import { VideoPlay } from '@element-plus/icons-vue'

defineOptions({ name: 'ToolsWrap' })

defineProps({
  /** 是否正在执行：执行中禁用按钮并改文案，避免重复提交 */
  running: { type: Boolean, default: false },
})

defineEmits(['run'])
</script>

<template>
  <div class="tools-wrap">
    <button
      class="tools-wrap__btn tools-wrap__btn--run"
      type="button"
      :disabled="running"
      :aria-label="running ? '执行中' : '运行'"
      @click="$emit('run')"
    >
      <el-icon><VideoPlay /></el-icon>
      <span class="tools-wrap__btn-text">{{ running ? '执行中' : '运行' }}</span>
    </button>
  </div>
</template>

<style scoped>
/* ===== 竖向工具条：由画布页的左侧浮层容器（.wf-left-rail）负责定位与排布 ===== */
.tools-wrap {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  gap: 4px;
  padding: 4px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
  pointer-events: none;
}

.tools-wrap__btn {
  display: flex;
  height: 36px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 12px;
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  cursor: pointer;
  pointer-events: auto;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out),
    transform var(--tb-dur-2) var(--tb-ease-out);
}

.tools-wrap__btn:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  transform: translateY(-1px);
}

/* 运行是这一条工具栏里的主操作：主色图标 + 主色文字，hover 用主色软底 */
.tools-wrap__btn--run {
  color: var(--tb-primary);
}

.tools-wrap__btn--run:hover {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

/* 执行中：禁用并降饱和，避免重复提交 */
.tools-wrap__btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.tools-wrap__btn:disabled:hover {
  background: transparent;
  transform: none;
}

.tools-wrap__btn-text {
  font-weight: 500;
}

@media (prefers-reduced-motion: reduce) {
  .tools-wrap__btn {
    transition: none;
  }

  .tools-wrap__btn:hover {
    transform: none;
  }
}
</style>
