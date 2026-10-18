<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { SPACE_STATUS_META, SPACE_VISIBILITY_META } from '@/views/admin/constants'
import { formatBytes, formatDateTime } from '@/views/admin/utils/format'
import { listKnowledgeSpaces } from '@/api/admin/knowledgebase'
import { queryKnowledgeSpaces } from '@/views/admin/mock/knowledgebase'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listKnowledgeSpaces,
  fallback: queryKnowledgeSpaces,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = computed(() =>
  Object.entries(SPACE_STATUS_META).map(([value, meta]) => ({ value, label: meta.label })),
)

function statusMeta(status) {
  return SPACE_STATUS_META[status] || { label: status || '未知', tone: 'info' }
}

function visibilityMeta(visibility) {
  return SPACE_VISIBILITY_META[visibility] || { label: visibility || '—', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

function handleView(row) {
  ElMessage.info(`空间「${row.name}」的文档详情页待接入`)
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="用户空间列表"
      description="所有用户的知识库空间与用量。展开行可查看该空间最近的文档，完整文档清单以空间详情页为准。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input v-model="filters.keyword" placeholder="空间名 / 所有者" clearable style="width: 220px" />
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
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="space-docs">
            <p class="space-docs__title">最近文档</p>
            <el-table v-if="row.documents && row.documents.length" :data="row.documents" size="small">
              <el-table-column prop="name" label="文档名称" min-width="220" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="80" />
              <el-table-column label="大小" width="100" align="right">
                <template #default="{ row: doc }">{{ formatBytes(doc.size) }}</template>
              </el-table-column>
              <el-table-column prop="chunks" label="分片数" width="80" align="center" />
              <el-table-column label="更新时间" width="150">
                <template #default="{ row: doc }">{{ formatDateTime(doc.updatedAt) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该空间暂无文档" :image-size="48" />
          </div>
        </template>
      </el-table-column>

      <el-table-column label="空间名称" min-width="140">
        <template #default="{ row }">
          <span class="admin-strong">{{ row.name }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="owner" label="所有者" width="100" />

      <el-table-column label="可见性" width="80">
        <template #default="{ row }">
          <StatusTag
            :label="visibilityMeta(row.visibility).label"
            :tone="visibilityMeta(row.visibility).tone"
          />
        </template>
      </el-table-column>

      <el-table-column prop="docCount" label="文档数" width="70" align="center" />
      <el-table-column prop="chunkCount" label="分片数" width="70" align="center" />

      <el-table-column label="存储" width="90" align="right">
        <template #default="{ row }">{{ formatBytes(row.storageBytes) }}</template>
      </el-table-column>

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="80" fixed="right">
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

.space-docs {
  padding: var(--tb-size-3) var(--tb-size-4);
  background: var(--tb-bg-raised);
}

.space-docs__title {
  margin: 0 0 var(--tb-size-2);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
}
</style>
