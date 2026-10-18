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
import { listHomeworkRecords } from '@/api/admin/homework'
import { queryHomeworkRecords } from '@/views/admin/mock/homework'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listHomeworkRecords,
  fallback: queryHomeworkRecords,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(TASK_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function statusMeta(status) {
  return TASK_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function formatScore(score) {
  return score === null || score === undefined ? '—' : Number(score).toFixed(1)
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`作业「${row.name}」的批改结果页待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="批改记录查看"
      description="作业批改记录：提交与完成情况。平均分来自批改结果汇总，运行中的任务不显示分数。"
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
          placeholder="作业名 / 班级 / 发起人"
          clearable
          style="width: 240px"
        />
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
      <el-table-column prop="name" label="作业名称" min-width="190" show-overflow-tooltip />
      <el-table-column prop="className" label="班级" width="110" />
      <el-table-column prop="fileCount" label="文件数" width="70" align="center" />
      <el-table-column prop="owner" label="发起人" width="90" />

      <el-table-column label="平均分" width="80" align="center">
        <template #default="{ row }">
          <span class="score">{{ formatScore(row.averageScore) }}</span>
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

      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">查看结果</el-button>
        </template>
      </el-table-column>
    </AdminTable>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}

.score {
  color: var(--tb-text-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
