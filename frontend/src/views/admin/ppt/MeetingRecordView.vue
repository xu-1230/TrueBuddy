<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { MEETING_STATUS_META } from '@/views/admin/constants'
import { formatDateTime, formatDuration } from '@/views/admin/utils/format'
import { listPptMeetings } from '@/api/admin/ppt'
import { queryPptMeetings } from '@/views/admin/mock/ppt'

const filters = reactive({ keyword: '', status: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listPptMeetings,
  fallback: queryPptMeetings,
  filters: () => ({ keyword: filters.keyword.trim(), status: filters.status }),
})

const statusOptions = Object.entries(MEETING_STATUS_META).map(([value, meta]) => ({
  value: Number(value),
  label: meta.label,
}))

function statusMeta(status) {
  return MEETING_STATUS_META[status] || { label: '未知', tone: 'info' }
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  search()
}

async function copyLink(row) {
  const link = `${window.location.origin}/meeting/${row.meetingNo}`
  try {
    await navigator.clipboard.writeText(link)
    ElMessage.success('入会链接已复制')
  } catch {
    ElMessage.warning(link)
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="会议记录查询"
      description="PPT 智慧课堂的会议台账：参会人数为入会记录数，不是实时在线人数——在线状态只认 LiveKit 房间事件（ADR-0002）。"
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
          placeholder="会议号 / 标题 / 主持人"
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
      <el-table-column label="会议号" width="90">
        <template #default="{ row }">
          <span class="admin-code">{{ row.meetingNo }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="title" label="会议标题" min-width="160" show-overflow-tooltip />
      <el-table-column prop="host" label="主持人" width="80" />
      <el-table-column prop="attendeeCount" label="参会人数" width="80" align="center" />

      <el-table-column label="时长" width="90">
        <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
      </el-table-column>

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="开始时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
      </el-table-column>

      <el-table-column label="结束时间" width="130">
        <template #default="{ row }">{{ formatDateTime(row.endedAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="row.status !== 1" @click="copyLink(row)">
            复制入会链接
          </el-button>
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
