<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { RUN_STATUS_META } from '@/views/admin/constants'
import { formatDateTime, formatLatency } from '@/views/admin/utils/format'
import { listWorkflowRuns } from '@/api/admin/workflow'
import { queryWorkflowRuns } from '@/views/admin/mock/workflow'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listWorkflowRuns,
  fallback: queryWorkflowRuns,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(RUN_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return RUN_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`运行 ${row.id} 的节点日志待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="运行记录查询"
      description="每次工作流执行的台账：状态、耗时与起止时间。失败与取消的运行可到「执行异常排查」看原因。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input
          v-model="filters.keyword"
          placeholder="运行 ID / 工作流 / 触发人"
          clearable
          style="width: 250px"
        />
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
      <el-table-column label="运行 ID" width="170">
        <template #default="{ row }">
          <span class="admin-code">{{ row.id }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="workflow" label="工作流" min-width="160" show-overflow-tooltip />
      <el-table-column prop="user" label="触发人" width="80" />

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="耗时" width="80" align="right">
        <template #default="{ row }">{{ formatLatency(row.durationMs) }}</template>
      </el-table-column>

      <el-table-column label="开始时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>

      <el-table-column label="结束时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看日志</el-button>
        </template>
      </el-table-column>
    </AdminTable>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}
</style>
