<script setup>
import { computed } from 'vue'
import { PAGE_SIZES } from '@/views/admin/constants'

/**
 * 管理端表格容器
 *
 * 统一承担：加载态、空态、错误态、分页、演示数据标记。
 * 页面只写 <el-table-column>，不再各自拼一套 el-table + el-pagination。
 */
const props = defineProps({
  rows: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' },
  usingMock: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  pageSizes: { type: Array, default: () => PAGE_SIZES },
  rowKey: { type: [String, Function], default: 'id' },
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

const metaText = computed(() => `共 ${props.total} 条`)

function handleCurrentChange(next) {
  emit('update:page', next)
  emit('change')
}

function handleSizeChange(next) {
  emit('update:size', next)
  emit('update:page', 1)
  emit('change')
}
</script>

<template>
  <section class="admin-table">
    <el-alert
      v-if="errorMessage"
      class="admin-table__alert"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />

    <el-table
      v-loading="loading"
      class="admin-table__el"
      :data="rows"
      :row-key="rowKey"
      size="default"
      :border="false"
    >
      <slot />
      <template #empty>
        <el-empty :description="loading ? '正在加载' : '暂无数据'" :image-size="64" />
      </template>
    </el-table>

    <footer class="admin-table__footer">
      <div class="admin-table__meta">
        <span>{{ metaText }}</span>
        <el-tag v-if="usingMock" type="warning" size="small" effect="plain">演示数据</el-tag>
      </div>

      <el-pagination
        :current-page="page"
        :page-size="size"
        :page-sizes="pageSizes"
        :total="total"
        background
        layout="sizes, prev, pager, next, jumper"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </footer>
  </section>
</template>

<style scoped>
.admin-table {
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  overflow: hidden;
}

.admin-table__alert {
  border-radius: 0;
}

.admin-table__el {
  width: 100%;
}

.admin-table__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  flex-wrap: wrap;
  padding: var(--tb-size-3) var(--tb-size-4);
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.admin-table__meta {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}
</style>
