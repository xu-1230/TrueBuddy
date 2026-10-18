<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { HEALTH_STATUS_META } from '@/views/admin/constants'
import { formatDateTime, formatLatency } from '@/views/admin/utils/format'
import { getServiceHealth } from '@/api/admin/system'
import { mockServiceHealth } from '@/views/admin/mock/data'

const loading = ref(false)
const usingMock = ref(false)
const overview = ref(mockServiceHealth)

const services = computed(() => overview.value?.services || [])

const counts = computed(() => {
  const result = { healthy: 0, degraded: 0, down: 0 }
  for (const service of services.value) {
    if (result[service.status] !== undefined) result[service.status] += 1
  }
  return result
})

const summary = computed(() => [
  {
    key: 'total',
    label: '检查项',
    value: services.value.length,
    icon: 'health',
    tone: 'primary',
    hint: '依赖服务与外部组件',
  },
  {
    key: 'healthy',
    label: '正常',
    value: counts.value.healthy,
    icon: 'shield',
    tone: 'success',
    hint: '最近一次探测通过',
  },
  {
    key: 'degraded',
    label: '降级',
    value: counts.value.degraded,
    icon: 'log',
    tone: 'warning',
    hint: '可降级使用，需关注',
  },
  {
    key: 'down',
    label: '不可用',
    value: counts.value.down,
    icon: 'key',
    tone: 'danger',
    hint: '相关功能不可用',
  },
])

function statusMeta(status) {
  return HEALTH_STATUS_META[status] || { label: '未知', tone: 'info' }
}

async function load() {
  loading.value = true
  try {
    const data = await getServiceHealth()
    if (!data || !Array.isArray(data.services)) throw new Error('健康检查数据格式不符合预期')
    overview.value = data
    usingMock.value = false
  } catch {
    overview.value = mockServiceHealth
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

async function copyDiagnostics() {
  const lines = services.value.map(
    (service) => `${service.name}\t${statusMeta(service.status).label}\t${formatLatency(service.latencyMs)}\t${service.detail}`,
  )
  const text = [`检查时间：${overview.value?.checkedAt || '—'}`, ...lines].join('\n')

  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('诊断信息已复制')
  } catch {
    ElMessage.warning('浏览器未授权剪贴板，请手动复制')
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="服务健康"
      description="管理端依赖服务的探测结果。这里是只读快照，真实可用性以各服务自身监控为准。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">重新检查</el-button>
        <el-button @click="copyDiagnostics">复制诊断信息</el-button>
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

    <section v-loading="loading" class="health-grid">
      <article
        v-for="service in services"
        :key="service.key"
        class="health-card"
        :class="`is-${service.status}`"
      >
        <header class="health-card__head">
          <h2 class="health-card__name">{{ service.name }}</h2>
          <StatusTag :label="statusMeta(service.status).label" :tone="statusMeta(service.status).tone" />
        </header>

        <p class="health-card__detail">{{ service.detail }}</p>

        <dl class="health-card__meta">
          <div class="health-card__meta-item">
            <dt>响应</dt>
            <dd>{{ formatLatency(service.latencyMs) }}</dd>
          </div>
          <div class="health-card__meta-item">
            <dt>版本</dt>
            <dd>{{ service.version || '—' }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <p class="health-footnote">
      检查时间：{{ formatDateTime(overview?.checkedAt) }}
      <span class="admin-muted">（服务端返回的快照时间，不是页面刷新时间）</span>
    </p>
  </div>
</template>

<style scoped>
.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--tb-size-4);
}

.health-card {
  --health-tone: var(--tb-success);

  padding: var(--tb-size-4);
  border: var(--tb-border-width) solid var(--tb-border);
  border-left: 3px solid var(--health-tone);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
}

.health-card.is-degraded {
  --health-tone: var(--tb-warning);
}

.health-card.is-down {
  --health-tone: var(--tb-danger);
}

.health-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-2);
}

.health-card__name {
  margin: 0;
  color: var(--tb-text-primary);
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-md);
  font-weight: 600;
}

.health-card__detail {
  margin: var(--tb-size-3) 0 0;
  min-height: 2.6em;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
}

.health-card__meta {
  display: flex;
  gap: var(--tb-size-5);
  margin: var(--tb-size-3) 0 0;
  padding-top: var(--tb-size-3);
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.health-card__meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.health-card__meta-item dt {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.health-card__meta-item dd {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
  font-variant-numeric: tabular-nums;
}

.health-footnote {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}
</style>
