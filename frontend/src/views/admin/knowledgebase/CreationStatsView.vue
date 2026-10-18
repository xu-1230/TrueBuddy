<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import BarChart from '@/views/admin/components/BarChart.vue'
import { formatNumber } from '@/views/admin/utils/format'
import { getKnowledgeCreationStats } from '@/api/admin/knowledgebase'
import { mockKnowledgeCreationStats } from '@/views/admin/mock/knowledgebase'

const loading = ref(false)
const usingMock = ref(false)
const data = ref(mockKnowledgeCreationStats)

const summary = computed(() => data.value?.summary || [])
const series = computed(() => data.value?.series || [])

const documentBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.documents })),
)
const spaceBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.spaces })),
)

const totalDocuments = computed(() =>
  series.value.reduce((sum, item) => sum + (Number(item.documents) || 0), 0),
)
const totalSpaces = computed(() =>
  series.value.reduce((sum, item) => sum + (Number(item.spaces) || 0), 0),
)

function isValid(value) {
  return Boolean(value && Array.isArray(value.summary) && Array.isArray(value.series))
}

async function load() {
  loading.value = true
  try {
    const result = await getKnowledgeCreationStats()
    if (!isValid(result)) throw new Error('知识库创建统计格式不符合预期')
    data.value = result
    usingMock.value = false
  } catch {
    data.value = mockKnowledgeCreationStats
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
      title="知识库创建统计"
      description="按时间维度看空间与文档的新增量。统计口径为创建时间，不含删除与归档。"
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
        :value="formatNumber(item.value)"
        :hint="item.hint"
        :icon="item.icon"
        :tone="item.tone"
      />
    </section>

    <section class="admin-grid admin-grid--2">
      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">每月新增文档</h2>
          <span class="admin-muted">近 8 个月合计 {{ formatNumber(totalDocuments) }} 份</span>
        </header>
        <BarChart :items="documentBars" tone="success" value-suffix=" 份" />
      </article>

      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">每月新增空间</h2>
          <span class="admin-muted">近 8 个月合计 {{ formatNumber(totalSpaces) }} 个</span>
        </header>
        <BarChart :items="spaceBars" tone="primary" value-suffix=" 个" />
      </article>
    </section>

    <section class="admin-panel admin-panel--flush">
      <div class="chart-head chart-head--padded">
        <h2 class="admin-panel__title">按月明细</h2>
      </div>
      <el-table :data="series" size="small">
        <el-table-column prop="label" label="月份" min-width="120" />
        <el-table-column prop="spaces" label="新增空间" width="120" align="center" />
        <el-table-column prop="documents" label="新增文档" width="120" align="center" />
        <el-table-column label="合计" width="120" align="center">
          <template #default="{ row }">{{ row.spaces + row.documents }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-4);
}

.chart-head--padded {
  padding: var(--tb-size-3) var(--tb-size-4);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}
</style>
