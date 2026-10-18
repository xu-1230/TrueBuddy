<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { TASK_STATUS_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listDocTemplateRecords } from '@/api/admin/doctemplate'
import { queryDocTemplateRecords } from '@/views/admin/mock/doctemplate'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listDocTemplateRecords,
  fallback: queryDocTemplateRecords,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(TASK_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return TASK_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`任务「${row.title}」的生成结果页待接入`)
}

function handleRetry(row) {
  ElMessage.info(`已提交重试：${row.title}（接口待接入）`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="生成记录查看"
      description="每次文档模板生成的记录：进度、耗时与失败原因。失败任务允许单独重试，不影响历史结果。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="任务标题 / 发起人" clearable style="width: 220px" />
      </el-form-item>

      <el-form-item label="状态" class="query-item">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 140px">
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
      <el-table-column prop="title" label="任务标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="template" label="模板" min-width="120" show-overflow-tooltip />
      <el-table-column prop="owner" label="发起人" width="80" />

      <el-table-column label="进度" width="120">
        <template #default="{ row }">
          <div class="progress-cell">
            <el-progress
              :percentage="row.progress"
              :stroke-width="6"
              :status="row.status === 'failed' ? 'exception' : undefined"
              :show-text="false"
            />
            <span class="progress-cell__value">{{ row.progress }}%</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="完成时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <div class="admin-inline">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'failed'" link @click="handleRetry(row)">重试</el-button>
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

.progress-cell {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
}

.progress-cell :deep(.el-progress) {
  flex: 1;
  min-width: 60px;
}

.progress-cell__value {
  flex: 0 0 auto;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  font-variant-numeric: tabular-nums;
}
</style>
