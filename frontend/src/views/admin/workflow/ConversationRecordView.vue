<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { CONVERSATION_STATUS_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listWorkflowConversations } from '@/api/admin/workflow'
import { queryWorkflowConversations } from '@/views/admin/mock/workflow'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listWorkflowConversations,
  fallback: queryWorkflowConversations,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(CONVERSATION_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return CONVERSATION_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`会话 ${row.id} 的消息记录待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="会话记录查看"
      description="工作流会话的消息数与状态。这里只看会话级摘要，逐条消息在会话详情里查看。"
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
          placeholder="会话 ID / 工作流 / 用户"
          clearable
          style="width: 240px"
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
      <el-table-column label="会话 ID" width="120">
        <template #default="{ row }">
          <span class="admin-code">{{ row.id }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="workflow" label="工作流" min-width="180" show-overflow-tooltip />
      <el-table-column prop="user" label="用户" width="80" />
      <el-table-column prop="messageCount" label="消息数" width="80" align="center" />

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="开始时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>

      <el-table-column label="更新时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看会话</el-button>
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
