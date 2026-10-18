<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import BarChart from '@/views/admin/components/BarChart.vue'
import { formatDuration, formatNumber } from '@/views/admin/utils/format'
import { getClassStats } from '@/api/admin/ppt'
import { mockClassStats } from '@/views/admin/mock/ppt'

const loading = ref(false)
const usingMock = ref(false)
const data = ref(mockClassStats)

const summary = computed(() => data.value?.summary || [])
const series = computed(() => data.value?.series || [])
const byClass = computed(() => data.value?.byClass || [])

const sessionBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.sessions })),
)
const attendanceBars = computed(() =>
  series.value.map((item) => ({ label: item.label, value: item.attendance })),
)

function isValid(value) {
  return Boolean(value && Array.isArray(value.summary) && Array.isArray(value.series))
}

async function load() {
  loading.value = true
  try {
    const result = await getClassStats()
    if (!isValid(result)) throw new Error('课堂统计格式不符合预期')
    data.value = result
    usingMock.value = false
  } catch {
    data.value = mockClassStats
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
      title="课堂数据统计"
      description="按周统计开课场次、累计时长与到课人次。到课取会议入会记录，不是实时在线人数。"
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
          <h2 class="admin-panel__title">每周开课场次</h2>
          <span class="admin-muted">近 6 周</span>
        </header>
        <BarChart :items="sessionBars" tone="primary" value-suffix=" 场" />
      </article>

      <article class="admin-panel">
        <header class="chart-head">
          <h2 class="admin-panel__title">每周到课人次</h2>
          <span class="admin-muted">近 6 周</span>
        </header>
        <BarChart :items="attendanceBars" tone="success" value-suffix=" 人" />
      </article>
    </section>

    <section class="admin-panel admin-panel--flush">
      <div class="chart-head chart-head--padded">
        <h2 class="admin-panel__title">按班级</h2>
      </div>
      <el-table :data="byClass" size="small">
        <el-table-column prop="className" label="班级" min-width="160" />
        <el-table-column prop="sessions" label="开课数" width="100" align="center" />
        <el-table-column prop="avgAttendance" label="平均到课" width="120" align="center" />
        <el-table-column label="累计时长" width="140" align="right">
          <template #default="{ row }">{{ formatDuration(row.minutes * 60) }}</template>
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
  margin-bottom: var(--tb-size-3);
}

.chart-head--padded {
  padding: var(--tb-size-3) var(--tb-size-4);
  margin-bottom: 0;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}
</style>
