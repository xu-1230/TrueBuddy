<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { HOMEWORK_RESOURCE_TYPE_OPTIONS, RESOURCE_STATUS_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listHomeworkResources } from '@/api/admin/homework'
import { queryHomeworkResources } from '@/views/admin/mock/homework'

const filters = reactive({ keyword: '', type: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listHomeworkResources,
  fallback: queryHomeworkResources,
  filters: () => ({
    keyword: filters.keyword.trim(),
    type: filters.type,
    status: filters.status,
  }),
})

const typeLabels = computed(() =>
  Object.fromEntries(HOMEWORK_RESOURCE_TYPE_OPTIONS.map((item) => [item.value, item.label])),
)

const statusOptions = Object.entries(RESOURCE_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function typeLabel(value) {
  return typeLabels.value[value] || value || '—'
}

function statusMeta(status) {
  return RESOURCE_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.type = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`批改资源「${row.name}」详情页待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="批改资源列表"
      description="评分标准、提示词与批改工作流。停用资源不会出现在批改任务的配置项里。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="资源名 / 维护人" clearable style="width: 220px" />
      </el-form-item>

      <el-form-item label="类型" class="query-item">
        <el-select v-model="filters.type" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="item in HOMEWORK_RESOURCE_TYPE_OPTIONS"
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
      <el-table-column label="资源名称" min-width="240">
        <template #default="{ row }">
          <div class="resource-cell">
            <span class="resource-cell__name">{{ row.name }}</span>
            <span class="resource-cell__meta">维护人：{{ row.owner }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>

      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="usageCount" label="使用次数" width="90" align="center" />

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看</el-button>
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
