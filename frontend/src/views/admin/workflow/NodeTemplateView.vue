<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { NODE_CATEGORY_OPTIONS, RESOURCE_STATUS_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listNodeTemplates } from '@/api/admin/workflow'
import { queryNodeTemplates } from '@/views/admin/mock/workflow'

const filters = reactive({ keyword: '', category: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listNodeTemplates,
  fallback: queryNodeTemplates,
  filters: () => ({
    keyword: filters.keyword.trim(),
    category: filters.category,
    status: filters.status,
  }),
})

const categoryLabels = computed(() =>
  Object.fromEntries(NODE_CATEGORY_OPTIONS.map((item) => [item.value, item.label])),
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

function handleView(row) {
  ElMessage.info(`节点模板「${row.name}」的配置页待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="节点模板维护"
      description="工作流可复用节点的定义：节点类型、字段数与启停状态。停用后用户端节点面板不再出现该节点。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="节点名称" clearable style="width: 220px" />
      </el-form-item>

      <el-form-item label="分类" class="query-item">
        <el-select v-model="filters.category" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="item in NODE_CATEGORY_OPTIONS"
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
      <el-table-column label="节点名称" min-width="200">
        <template #default="{ row }">
          <div class="node-cell">
            <span class="node-cell__name">{{ row.name }}</span>
            <span class="node-cell__meta">type = {{ row.type }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>

      <el-table-column prop="fieldCount" label="字段数" width="90" align="center" />

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

.node-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-cell__name {
  color: var(--tb-text-primary);
  font-weight: 600;
}

.node-cell__meta {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  font-family: var(--font-mono);
}
</style>
