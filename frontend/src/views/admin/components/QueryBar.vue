<script setup>
defineProps({
  loading: { type: Boolean, default: false },
  searchText: { type: String, default: '查询' },
  resetText: { type: String, default: '重置' },
})

const emit = defineEmits(['search', 'reset'])
</script>

<template>
  <section class="query-bar">
    <el-form class="query-bar__fields" :inline="true" @submit.prevent @keyup.enter="emit('search')">
      <slot />
    </el-form>
    <div class="query-bar__actions">
      <el-button type="primary" :loading="loading" @click="emit('search')">
        {{ searchText }}
      </el-button>
      <el-button @click="emit('reset')">{{ resetText }}</el-button>
    </div>
  </section>
</template>

<style scoped>
.query-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--tb-size-3);
  flex-wrap: wrap;
  padding: var(--tb-size-3) var(--tb-size-4);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
}

.query-bar__fields {
  display: flex;
  align-items: flex-end;
  gap: var(--tb-size-3);
  flex-wrap: wrap;
  min-width: 0;
}

.query-bar__actions {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  flex: 0 0 auto;
}

.query-bar__fields :deep(.el-form-item) {
  margin-right: var(--tb-size-3);
  margin-bottom: 0;
}

@media (max-width: 768px) {
  .query-bar__fields {
    width: 100%;
  }

  .query-bar__fields :deep(.el-form-item) {
    margin-right: 0;
  }

  .query-bar__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
