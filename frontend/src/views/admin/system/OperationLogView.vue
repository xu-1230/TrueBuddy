<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { LOG_MODULE_OPTIONS, LOG_RESULT_META } from '@/views/admin/constants'
import { formatDateTime, formatLatency } from '@/views/admin/utils/format'
import { listOperationLogs } from '@/api/admin/system'
import { queryOperationLogs } from '@/views/admin/mock/data'

const filters = reactive({ keyword: '', module: '', result: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listOperationLogs,
  fallback: queryOperationLogs,
  filters: () => ({
    keyword: filters.keyword.trim(),
    module: filters.module,
    result: filters.result,
  }),
})

const moduleLabels = computed(() =>
  Object.fromEntries(LOG_MODULE_OPTIONS.map((item) => [item.value, item.label])),
)

const resultOptions = Object.entries(LOG_RESULT_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

function resultMeta(result) {
  return LOG_RESULT_META[result] || { label: result || '—', tone: 'info' }
}

function moduleLabel(module) {
  return moduleLabels.value[module] || module || '—'
}

function resetFilters() {
  filters.keyword = ''
  filters.module = ''
  filters.result = ''
  search()
}

function exportLogs() {
  ElMessage.info('日志导出接口待接入')
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="操作日志"
      description="管理端与用户端的关键操作留痕。日志只追加不修改，便于回溯权限与内容变更。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="Download" @click="exportLogs">导出</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input
          v-model="filters.keyword"
          placeholder="操作人 / 动作 / 对象 / IP"
          clearable
          style="width: 240px"
        />
      </el-form-item>

      <el-form-item label="模块" class="query-item">
        <el-select v-model="filters.module" placeholder="全部" clearable style="width: 130px">
          <el-option
            v-for="item in LOG_MODULE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="结果" class="query-item">
        <el-select v-model="filters.result" placeholder="全部" clearable style="width: 110px">
          <el-option
            v-for="item in resultOptions"
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
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ formatDateTime(row.time) }}</template>
      </el-table-column>

      <el-table-column prop="operator" label="操作人" width="110" />
      <el-table-column label="模块" width="100">
        <template #default="{ row }">{{ moduleLabel(row.module) }}</template>
      </el-table-column>

      <el-table-column prop="action" label="动作" min-width="140" show-overflow-tooltip />
      <el-table-column prop="target" label="对象" min-width="160" show-overflow-tooltip />

      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <StatusTag :label="resultMeta(row.result).label" :tone="resultMeta(row.result).tone" />
        </template>
      </el-table-column>

      <el-table-column label="IP" width="120">
        <template #default="{ row }">
          <span class="admin-code">{{ row.ip }}</span>
        </template>
      </el-table-column>

      <el-table-column label="耗时" width="80" align="right">
        <template #default="{ row }">{{ formatLatency(row.durationMs) }}</template>
      </el-table-column>
    </AdminTable>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}
</style>
