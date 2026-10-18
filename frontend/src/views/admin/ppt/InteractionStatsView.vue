<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import BarChart from '@/views/admin/components/BarChart.vue'
import { formatNumber } from '@/views/admin/utils/format'
import { getInteractionStats } from '@/api/admin/ppt'
import { mockInteractionStats } from '@/views/admin/mock/ppt'

const loading = ref(false)
const usingMock = ref(false)
const data = ref(mockInteractionStats)

const summary = computed(() => data.value?.summary || [])
const series = computed(() => data.value?.series || [])
const byClass = computed(() => data.value?.byClass || [])

const messageBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.messages })),
)
const questionBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.questions })),
)

function isValid(value) {
  return Boolean(value && Array.isArray(value.summary) && Array.isArray(value.series))
}

async function load() {
  loading.value = true
  try {
    const result = await getInteractionStats()
    if (!isValid(result)) throw new Error('互动统计格式不符合预期')
    data.value = result
    usingMock.value = false
  } catch {
    data.value = mockInteractionStats
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
      title="课堂互动统计"
      description="课堂内的消息、提问与举手。互动数据来自会议消息与房间事件，按周汇总。"
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
          <h2 class="admin-panel__title">每周互动消息</h2>
          <span class="admin-muted">近 6 周</span>
        </header>
        <BarChart :items="messageBars" tone="primary" value-suffix=" 条" />
      </article>

      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">每周提问次数</h2>
          <span class="admin-muted">近 6 周</span>
        </header>
        <BarChart :items="questionBars" tone="warning" value-suffix=" 次" />
      </article>
    </section>

    <section class="admin-panel admin-panel--flush">
      <div class="chart-head chart-head--padded">
        <h2 class="admin-panel__title">按班级</h2>
      </div>
      <el-table :data="byClass" size="small">
        <el-table-column prop="className" label="班级" min-width="160" />
        <el-table-column prop="messages" label="互动消息" width="120" align="center" />
        <el-table-column prop="questions" label="提问次数" width="120" align="center" />
        <el-table-column prop="handRaises" label="举手次数" width="120" align="center" />
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
  margin-bottom: var(--tb-size-3);
}

.chart-head--padded {
  padding: var(--tb-size-3) var(--tb-size-4);
  margin-bottom: 0;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}
</style>
