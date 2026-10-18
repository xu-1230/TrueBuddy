<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { RESOURCE_STATUS_META, TEMPLATE_CATEGORY_OPTIONS } from '@/views/admin/constants'
import { formatBytes, formatDateTime } from '@/views/admin/utils/format'
import { listDocTemplates } from '@/api/admin/doctemplate'
import { queryDocTemplates } from '@/views/admin/mock/doctemplate'

const filters = reactive({ keyword: '', category: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listDocTemplates,
  fallback: queryDocTemplates,
  filters: () => ({
    keyword: filters.keyword.trim(),
    category: filters.category,
    status: filters.status,
  }),
})

const categoryLabels = computed(() =>
  Object.fromEntries(TEMPLATE_CATEGORY_OPTIONS.map((item) => [item.value, item.label])),
)

const statusOptions = Object.entries(RESOURCE_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function categoryLabel(value) {
  return categoryLabels.value[value] || value || '—'
}

function statusMeta(status) {
  return RESOURCE_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.category = ''
  filters.status = ''
  search()
}

function handlePreview(row) {
  ElMessage.info(`模板「${row.name}」预览待接入`)
}

function handleDownload(row) {
  ElMessage.info(`模板「${row.name}」下载待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="模板资源列表"
      description="文档模板库：分类、版本与使用次数。停用或草稿模板不会出现在用户端的模板选择里。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="模板名 / 维护人" clearable style="width: 220px" />
      </el-form-item>

      <el-form-item label="分类" class="query-item">
        <el-select v-model="filters.category" placeholder="全部" clearable style="width: 130px">
          <el-option
            v-for="item in TEMPLATE_CATEGORY_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="状态" class="query-item">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </QueryBar>

    <AdminTable
      v-model:page="page"
      v-model:size="size"
      :rows="rows"
      :total="total"
      :loading="loading"
      :error-message="errorMessage"
      :using-mock="usingMock"
      @change="load"
    >
      <el-table-column label="模板名称" min-width="220">
        <template #default="{ row }">
          <div class="resource-cell">
            <span class="resource-cell__name">{{ row.name }}</span>
            <span class="resource-cell__meta">维护人：{{ row.owner }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>

      <el-table-column prop="version" label="版本" width="80" />

      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">{{ formatBytes(row.size) }}</template>
      </el-table-column>

      <el-table-column prop="usageCount" label="使用次数" width="90" align="center" />

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <div class="admin-inline">
            <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
            <el-button link @click="handleDownload(row)">下载</el-button>
          </div>
        </template>
      </el-table-column>
    </AdminTable>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}

.resource-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.resource-cell__name {
  color: var(--tb-text-primary);
  font-weight: 600;
}

.resource-cell__meta {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}
</style>
