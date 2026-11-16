<template>
  <section class="status-notice" :class="`status-notice--${tone}`" :role="tone === 'danger' ? 'alert' : 'status'">
    <span class="status-notice__mark" aria-hidden="true" />
    <div class="status-notice__body">
      <p class="status-notice__title">{{ title }}</p>
      <div v-if="$slots.default" class="status-notice__content">
        <slot />
      </div>
      <div v-if="$slots.actions" class="status-notice__actions">
        <slot name="actions" />
      </div>
    </div>
    <button
      v-if="closable"
      type="button"
      class="status-notice__close"
      aria-label="关闭提示"
      @click="emit('close')"
    >
      <el-icon><Close /></el-icon>
    </button>
  </section>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'

defineProps({
  /** info / warn / danger / live */
  tone: { type: String, default: 'info' },
  title: { type: String, required: true },
  closable: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
</script>

<style scoped>
.status-notice {
  position: relative;
  display: flex;
  gap: var(--tb-size-3);
  padding: 12px 14px 12px 16px;
  border: 1px solid var(--mt-line);
  border-radius: var(--mt-radius-sm);
  background: color-mix(in oklab, var(--mt-bg-elevated) 78%, transparent);
  color: var(--mt-text-2);
  overflow: hidden;
}

/* 左侧信号条：颜色只表达语义，不做大面积底色 */
.status-notice__mark {
  flex: none;
  width: 3px;
  align-self: stretch;
  border-radius: var(--mt-radius-pill);
  background: var(--mt-text-3);
}

.status-notice--live .status-notice__mark {
  background: var(--mt-signal);
}

.status-notice--warn .status-notice__mark {
  background: var(--mt-amber);
}

.status-notice--danger .status-notice__mark {
  background: var(--mt-danger);
}

.status-notice--live {
  border-color: color-mix(in oklab, var(--mt-signal) 26%, transparent);
}

.status-notice--warn {
  border-color: color-mix(in oklab, var(--mt-amber) 26%, transparent);
}

.status-notice--danger {
  border-color: color-mix(in oklab, var(--mt-danger) 28%, transparent);
}

.status-notice__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.status-notice__title {
  margin: 0;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  font-weight: 600;
  line-height: 1.45;
}

.status-notice__content {
  color: var(--mt-text-2);
  font-size: var(--tb-text-sm);
  line-height: 1.65;
}

.status-notice__content :deep(p) {
  margin: 0;
}

.status-notice__content :deep(code) {
  display: inline-block;
  max-width: 100%;
  padding: 2px 6px;
  border-radius: var(--mt-radius-xs);
  background: color-mix(in oklab, var(--mt-text) 7%, transparent);
  color: var(--mt-text);
  font-family: var(--mt-font-mono);
  font-size: 0.92em;
  overflow-wrap: anywhere;
}

.status-notice__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
  margin-top: 2px;
}

.status-notice__close {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--mt-radius-sm);
  background: transparent;
  color: var(--mt-text-3);
  cursor: pointer;
  transition:
    color var(--mt-dur-2) var(--mt-ease),
    border-color var(--mt-dur-2) var(--mt-ease),
    background var(--mt-dur-2) var(--mt-ease);
}

.status-notice__close:hover {
  color: var(--mt-text);
  border-color: var(--mt-line);
  background: color-mix(in oklab, var(--mt-text) 6%, transparent);
}
</style>
