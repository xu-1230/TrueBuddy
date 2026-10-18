<script setup>
defineProps({
  items: { type: Array, default: () => [] },
})
</script>

<template>
  <el-breadcrumb class="admin-breadcrumb" separator="/">
    <el-breadcrumb-item
      v-for="(item, index) in items"
      :key="`${item.path}-${index}`"
      :to="index < items.length - 1 ? item.path : undefined"
    >
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<style scoped>
.admin-breadcrumb {
  min-width: 0;
}

.admin-breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-sm);
  font-weight: 400;
}

.admin-breadcrumb :deep(.el-breadcrumb__inner.is-link) {
  color: var(--tb-text-secondary);
}

.admin-breadcrumb :deep(.el-breadcrumb__inner.is-link:hover) {
  color: var(--tb-primary-text);
}

.admin-breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--tb-text-primary);
  font-weight: 600;
}
</style>
