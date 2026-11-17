<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { WORKFLOW_STATUS_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listWorkflows } from '@/api/admin/workflow'
import { queryWorkflows } from '@/views/admin/mock/workflow'

const router = useRouter()

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listWorkflows,
  fallback: queryWorkflows,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(WORKFLOW_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return WORKFLOW_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function openCanvas() {
  router.push('/index/workflow')
}

function handleView(row) {
  ElMessage.info(`工作流「${row.name}」的详情页待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="工作流列表"
      description="批改工作流的定义台账。列表与画布编辑在用户端 /index/workflow 完成，这里只做总览与入口。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="openCanvas">工作流列表</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="名称 / 描述" clearable style="width: 220px" />
      </el-form-item>

      <el-form-item label="状态" class="query-item">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px">
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
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column prop="nodeCount" label="节点数" width="80" align="center" />

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column prop="updatedBy" label="更新人" width="100" />

      <el-table-column label="更新时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <div class="admin-inline">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link @click="openCanvas">打开画布</el-button>
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
</style>
