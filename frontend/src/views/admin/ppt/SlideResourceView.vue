<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { RESOURCE_STATUS_META } from '@/views/admin/constants'
import { formatBytes, formatDateTime } from '@/views/admin/utils/format'
import { listSlideResources } from '@/api/admin/ppt'
import { querySlideResources } from '@/views/admin/mock/ppt'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listSlideResources,
  fallback: querySlideResources,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(RESOURCE_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return RESOURCE_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handlePreview(row) {
  ElMessage.info(`课件「${row.name}」预览待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="课件资源管理"
      description="用户上传与 AI 生成的 PPT 课件。处理中的课件暂时不能进入放映，失败的可重新导入。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="课件名 / 所有者" clearable style="width: 220px" />
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
      <el-table-column prop="name" label="课件名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="owner" label="所有者" width="110" />
      <el-table-column prop="slideCount" label="页数" width="80" align="center" />

      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">{{ formatBytes(row.size) }}</template>
      </el-table-column>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
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
