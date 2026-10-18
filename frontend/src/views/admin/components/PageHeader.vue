<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  demo: { type: Boolean, default: false },
})
</script>

<template>
  <div class="page-header">
    <div class="page-header__main">
      <div class="page-header__title-row">
        <h1 class="page-header__title">{{ title }}</h1>
        <el-tag v-if="demo" type="warning" size="small" effect="plain">演示数据</el-tag>
      </div>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--tb-size-4);
  flex-wrap: wrap;
}

.page-header__main {
  min-width: 0;
}

.page-header__title-row {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
}

.page-header__title {
  margin: 0;
  color: var(--tb-text-primary);
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-2xl);
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-tight);
  line-height: var(--tb-leading-tight);
}

.page-header__desc {
  margin: var(--tb-size-2) 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .page-header__title {
    font-size: var(--tb-text-xl);
  }
}
</style>
