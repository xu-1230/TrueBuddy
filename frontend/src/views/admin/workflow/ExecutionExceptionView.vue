<script setup>
import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { EXCEPTION_STATUS_META } from '@/views/admin/constants'
import { isApiUnavailable } from '@/views/admin/utils/request'
import { formatDateTime } from '@/views/admin/utils/format'
import { listWorkflowExceptions, resolveWorkflowException } from '@/api/admin/workflow'
import { mockWorkflowExceptions, queryWorkflowExceptions } from '@/views/admin/mock/workflow'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listWorkflowExceptions,
  fallback: queryWorkflowExceptions,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(EXCEPTION_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return EXCEPTION_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

async function handleResolve(row) {
  if (row.status !== 'open') return

  try {
    await ElMessageBox.confirm(
      `确认把「${row.workflow}」的这条异常标记为已解决吗？`,
      '处理异常',
      { type: 'warning', confirmButtonText: '标记已解决', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await resolveWorkflowException(row.id)
    ElMessage.success('已标记为已解决')
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '操作失败')
      return
    }
    const target = mockWorkflowExceptions.find((item) => item.id === row.id)
    if (target) target.status = 'resolved'
    usingMock.value = true
    ElMessage.success('演示模式：已标记为已解决')
    await load()
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="执行异常排查"
      description="工作流执行中的失败与告警：节点、错误码与原因。处理完成后可标记为已解决，记录会保留。"
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
          placeholder="工作流 / 节点 / 错误码 / 原因"
          clearable
          style="width: 260px"
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
      <el-table-column label="时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>

      <el-table-column prop="workflow" label="工作流" min-width="160" show-overflow-tooltip />
      <el-table-column prop="node" label="节点" width="120" show-overflow-tooltip />

      <el-table-column label="错误码" width="130">
        <template #default="{ row }">
          <span class="admin-code">{{ row.errorCode }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="message" label="错误信息" min-width="180" show-overflow-tooltip />

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'open'"
            link
            type="primary"
            @click="handleResolve(row)"
          >
            标记已处理
          </el-button>
          <span v-else class="admin-muted">已处理</span>
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
