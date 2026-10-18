<script setup>
import AdminIcon from './AdminIcon.vue'

defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '—' },
  hint: { type: String, default: '' },
  icon: { type: String, default: 'dot' },
  /** primary | success | warning | info | danger */
  tone: { type: String, default: 'primary' },
})
</script>

<template>
  <article class="stat-card" :class="`is-${tone}`">
    <span class="stat-card__icon">
      <AdminIcon :name="icon" :size="20" />
    </span>
    <div class="stat-card__body">
      <p class="stat-card__label">{{ label }}</p>
      <p class="stat-card__value">{{ value }}</p>
      <p v-if="hint" class="stat-card__hint">{{ hint }}</p>
    </div>
  </article>
</template>

<style scoped>
.stat-card {
  --stat-tone: var(--tb-primary);

  display: flex;
  align-items: flex-start;
  gap: var(--tb-size-3);
  min-width: 0;
  padding: var(--tb-size-4);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
}

.stat-card.is-success {
  --stat-tone: var(--tb-success);
}

.stat-card.is-warning {
  --stat-tone: var(--tb-warning);
}

.stat-card.is-info {
  --stat-tone: var(--tb-info);
}

.stat-card.is-danger {
  --stat-tone: var(--tb-danger);
}

.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  border-radius: var(--tb-radius-md);
  background: color-mix(in oklab, var(--stat-tone) 14%, var(--tb-bg-surface));
  color: var(--stat-tone);
}

.stat-card__body {
  min-width: 0;
}

.stat-card__label {
  margin: 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  white-space: nowrap;
}

.stat-card__value {
  margin: var(--tb-size-1) 0 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-2xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: var(--tb-leading-tight);
}

.stat-card__hint {
  margin: var(--tb-size-2) 0 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
}
</style>
