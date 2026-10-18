<script setup>
import { reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { INSPECTION_RESULT_META } from '@/views/admin/constants'
import { formatDateTime } from '@/views/admin/utils/format'
import { listDocTemplateInspections } from '@/api/admin/doctemplate'
import { queryDocTemplateInspections } from '@/views/admin/mock/doctemplate'

const filters = reactive({ keyword: '', result: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listDocTemplateInspections,
  fallback: queryDocTemplateInspections,
  filters: () => ({ keyword: filters.keyword.trim(), result: filters.result }),
})

const resultOptions = Object.entries(INSPECTION_RESULT_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

const detailVisible = ref(false)
const detail = ref(null)

function resultMeta(result) {
  return INSPECTION_RESULT_META[result] || { label: result || '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.result = ''
  search()
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="自检与修复问答记录"
      description="每次生成后的自检轮次：发现了什么、建议怎么改、是否已修复。待处理项可用于回溯生成质量问题。"
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
          placeholder="任务 / 自检发现 / 修复建议"
          clearable
          style="width: 260px"
        />
      </el-form-item>

      <el-form-item label="结果" class="query-item">
        <el-select v-model="filters.result" placeholder="全部" clearable style="width: 120px">
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
      <el-table-column prop="taskTitle" label="关联任务" min-width="170" show-overflow-tooltip />

      <el-table-column label="轮次" width="70" align="center">
        <template #default="{ row }">第 {{ row.round }} 轮</template>
      </el-table-column>

      <el-table-column prop="finding" label="自检发现" min-width="190" show-overflow-tooltip />
      <el-table-column prop="suggestion" label="修复建议" min-width="190" show-overflow-tooltip />

      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <StatusTag :label="resultMeta(row.result).label" :tone="resultMeta(row.result).tone" />
        </template>
      </el-table-column>

      <el-table-column label="时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="70" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </AdminTable>

    <el-dialog v-model="detailVisible" title="自检与修复详情" width="560px">
      <template v-if="detail">
        <div class="inspection-detail">
          <p class="inspection-detail__row">
            <span class="inspection-detail__label">关联任务</span>
            <span>{{ detail.taskTitle }}</span>
          </p>
          <p class="inspection-detail__row">
            <span class="inspection-detail__label">轮次</span>
            <span>第 {{ detail.round }} 轮 · {{ formatDateTime(detail.createdAt) }}</span>
          </p>
          <p class="inspection-detail__row">
            <span class="inspection-detail__label">结果</span>
            <StatusTag :label="resultMeta(detail.result).label" :tone="resultMeta(detail.result).tone" />
          </p>
          <div class="inspection-detail__block">
            <p class="inspection-detail__label">自检发现</p>
            <p class="inspection-detail__text">{{ detail.finding }}</p>
          </div>
          <div class="inspection-detail__block">
            <p class="inspection-detail__label">修复建议</p>
            <p class="inspection-detail__text">{{ detail.suggestion }}</p>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}

.inspection-detail {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
}

.inspection-detail__row {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
}

.inspection-detail__label {
  flex: 0 0 72px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}

.inspection-detail__block {
  padding: var(--tb-size-3);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
}

.inspection-detail__text {
  margin: var(--tb-size-2) 0 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}
</style>
