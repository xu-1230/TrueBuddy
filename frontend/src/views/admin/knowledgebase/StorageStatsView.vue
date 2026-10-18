<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import BarChart from '@/views/admin/components/BarChart.vue'
import { formatBytes, formatNumber } from '@/views/admin/utils/format'
import { getKnowledgeStorageStats } from '@/api/admin/knowledgebase'
import { mockKnowledgeStorageStats } from '@/views/admin/mock/knowledgebase'

const loading = ref(false)
const usingMock = ref(false)
const data = ref(mockKnowledgeStorageStats)

const totalBytes = computed(() => Number(data.value?.totalBytes) || 0)
const quotaBytes = computed(() => Number(data.value?.quotaBytes) || 0)
const usagePercent = computed(() =>
  quotaBytes.value > 0 ? Math.min(100, Math.round(totalBytes.value / quotaBytes.value * 100)) : 0,
)

const bySpace = computed(() => data.value?.bySpace || [])
const byType = computed(() => data.value?.byType || [])

const spaceBars = computed(() =>
  bySpace.value.map((item) => ({ label: item.space, value: item.bytes })),
)

const summary = computed(() => [
  {
    key: 'used',
    label: '已用存储',
    value: formatBytes(totalBytes.value),
    hint: `共 ${bySpace.value.length} 个空间`,
    icon: 'book',
    tone: 'primary',
  },
  {
    key: 'quota',
    label: '存储配额',
    value: formatBytes(quotaBytes.value),
    hint: '对象存储总量',
    icon: 'log',
    tone: 'info',
  },
  {
    key: 'percent',
    label: '使用率',
    value: `${usagePercent.value}%`,
    hint: usagePercent.value >= 80 ? '接近配额上限' : '余量充足',
    icon: 'health',
    tone: usagePercent.value >= 80 ? 'danger' : 'success',
  },
])

function isValid(value) {
  return Boolean(value && Array.isArray(value.bySpace) && Array.isArray(value.byType))
}

async function load() {
  loading.value = true
  try {
    const result = await getKnowledgeStorageStats()
    if (!isValid(result)) throw new Error('存储用量统计格式不符合预期')
    data.value = result
    usingMock.value = false
  } catch {
    data.value = mockKnowledgeStorageStats
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="存储用量统计"
      description="知识库占用的对象存储空间：按空间与文件类型拆分，用于容量规划与清理。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <section class="admin-grid admin-grid--stats">
      <StatCard
        v-for="item in summary"
        :key="item.key"
        :label="item.label"
        :value="item.value"
        :hint="item.hint"
        :icon="item.icon"
        :tone="item.tone"
      />
    </section>

    <section class="admin-panel">
      <header class="quota-head">
        <h2 class="admin-panel__title">配额使用</h2>
        <span class="admin-muted">{{ formatBytes(totalBytes) }} / {{ formatBytes(quotaBytes) }}</span>
      </header>
      <el-progress
        :percentage="usagePercent"
        :stroke-width="12"
        :status="usagePercent >= 80 ? 'exception' : undefined"
      />
    </section>

    <section class="admin-grid admin-grid--2">
      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">按空间用量</h2>
        </header>
        <BarChart :items="spaceBars" tone="primary" :format-value="formatBytes" />
      </article>

      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">按文件类型</h2>
        </header>
        <el-table :data="byType" size="small">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <span class="admin-code">{{ String(row.type).toUpperCase() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="占用" min-width="120" align="right">
            <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
          </el-table-column>
          <el-table-column label="占比" width="90" align="right">
            <template #default="{ row }">{{ Math.round(row.share * 100) }}%</template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section class="admin-panel admin-panel--flush">
      <div class="chart-head chart-head--padded">
        <h2 class="admin-panel__title">空间明细</h2>
        <span class="admin-muted">共 {{ formatNumber(bySpace.length) }} 个空间</span>
      </div>
      <el-table :data="bySpace" size="small">
        <el-table-column prop="space" label="空间" min-width="160" />
        <el-table-column prop="documents" label="文档数" width="100" align="center" />
        <el-table-column prop="chunks" label="分片数" width="100" align="center" />
        <el-table-column label="存储" width="140" align="right">
          <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.quota-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-3);
}

.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-3);
}

.chart-head--padded {
  padding: var(--tb-size-3) var(--tb-size-4);
  margin-bottom: 0;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}
</style>
