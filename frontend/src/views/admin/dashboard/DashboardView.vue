<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatCard from '@/views/admin/components/StatCard.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import AdminIcon from '@/views/admin/components/AdminIcon.vue'
import { getDashboardOverview } from '@/api/admin/dashboard'
import { mockDashboardOverview } from '@/views/admin/mock/data'
import { HEALTH_STATUS_META, LOG_RESULT_META } from '@/views/admin/constants'
import { formatLatency, formatNumber, formatRelativeTime } from '@/views/admin/utils/format'

const loading = ref(false)
const usingMock = ref(false)
const overview = ref(null)

const stats = computed(() => overview.value?.stats || [])
const todos = computed(() => overview.value?.todos || [])
const activities = computed(() => overview.value?.activities || [])
const services = computed(() => overview.value?.services || [])

const quickLinks = [
  { title: '用户列表', description: '账号、角色与启停状态', path: '/admin/user/list', icon: 'users' },
  { title: '模板资源列表', description: '模板库与使用次数', path: '/admin/doc-template/templates', icon: 'doc' },
  { title: '批改记录查看', description: '作业提交与平均分', path: '/admin/homework/records', icon: 'grading' },
  { title: '用户空间列表', description: '知识库空间与用量', path: '/admin/knowledge-base/spaces', icon: 'book' },
  { title: '会议记录查询', description: '课堂台账与时长', path: '/admin/ppt/meetings', icon: 'meeting' },
  { title: '执行异常排查', description: '失败节点与错误码', path: '/admin/workflow/exceptions', icon: 'health' },
]

function isOverviewValid(data) {
  return Boolean(data && Array.isArray(data.stats) && data.stats.length > 0)
}

async function load() {
  loading.value = true
  try {
    const data = await getDashboardOverview()
    if (!isOverviewValid(data)) throw new Error('概览数据格式不符合预期')
    overview.value = data
    usingMock.value = false
  } catch {
    overview.value = mockDashboardOverview
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

function healthMeta(status) {
  return HEALTH_STATUS_META[status] || { label: '未知', tone: 'info' }
}

function resultMeta(result) {
  return LOG_RESULT_META[result] || { label: result || '—', tone: 'info' }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="概览"
      description="系统运行状态、待处理事项与最近操作。数据来自管理端接口，接口未就绪时展示演示数据。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <section class="admin-grid admin-grid--stats">
      <StatCard
        v-for="item in stats"
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
        <header class="dashboard-panel__head">
          <h2 class="admin-panel__title">待处理事项</h2>
          <span class="admin-muted">{{ todos.length }} 项</span>
        </header>
        <ul class="admin-list">
          <li v-for="todo in todos" :key="todo.id" class="admin-list__item">
            <span class="admin-list__dot" :class="`is-${todo.level}`" aria-hidden="true" />
            <div class="admin-list__main">
              <p class="admin-list__title">{{ todo.title }}</p>
              <p class="admin-list__meta">{{ todo.meta }}</p>
            </div>
          </li>
        </ul>
        <el-empty v-if="todos.length === 0 && !loading" description="没有待处理事项" :image-size="56" />
      </article>

      <article class="admin-panel">
        <header class="dashboard-panel__head">
          <h2 class="admin-panel__title">最近操作</h2>
          <RouterLink class="dashboard-panel__more" to="/admin/system/log">全部日志</RouterLink>
        </header>
        <ul class="admin-list">
          <li v-for="item in activities" :key="item.id" class="admin-list__item">
            <div class="admin-list__main">
              <p class="admin-list__title">
                <span class="admin-strong">{{ item.operator }}</span>
                {{ item.action }}
                <span class="admin-strong">{{ item.target }}</span>
              </p>
              <p class="admin-list__meta">{{ formatRelativeTime(item.time) }}</p>
            </div>
            <StatusTag :label="resultMeta(item.result).label" :tone="resultMeta(item.result).tone" />
          </li>
        </ul>
        <el-empty v-if="activities.length === 0 && !loading" description="暂无操作记录" :image-size="56" />
      </article>
    </section>

    <section class="admin-grid admin-grid--2">
      <article class="admin-panel">
        <header class="dashboard-panel__head">
          <h2 class="admin-panel__title">依赖服务</h2>
          <RouterLink class="dashboard-panel__more" to="/admin/system/health">查看详情</RouterLink>
        </header>
        <ul class="service-list">
          <li v-for="service in services" :key="service.key" class="service-list__item">
            <div class="service-list__main">
              <p class="admin-list__title">{{ service.name }}</p>
              <p class="admin-list__meta">响应 {{ formatLatency(service.latencyMs) }}</p>
            </div>
            <StatusTag :label="healthMeta(service.status).label" :tone="healthMeta(service.status).tone" />
          </li>
        </ul>
      </article>

      <article class="admin-panel">
        <header class="dashboard-panel__head">
          <h2 class="admin-panel__title">快捷入口</h2>
        </header>
        <div class="quick-grid">
          <RouterLink v-for="link in quickLinks" :key="link.path" class="quick-link" :to="link.path">
            <span class="quick-link__icon"><AdminIcon :name="link.icon" :size="18" /></span>
            <span class="quick-link__body">
              <span class="quick-link__title">{{ link.title }}</span>
              <span class="quick-link__desc">{{ link.description }}</span>
            </span>
          </RouterLink>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.dashboard-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-2);
}

.dashboard-panel__more {
  color: var(--tb-primary-text);
  font-size: var(--tb-text-xs);
  text-decoration: none;
}

.dashboard-panel__more:hover {
  text-decoration: underline;
}

.service-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.service-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding: var(--tb-size-3) 0;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.service-list__item:last-child {
  border-bottom: 0;
}

.service-list__main {
  min-width: 0;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--tb-size-2);
}

.quick-link {
  display: flex;
  align-items: flex-start;
  gap: var(--tb-size-2);
  padding: var(--tb-size-3);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  color: var(--tb-text-primary);
  text-decoration: none;
  transition:
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.quick-link:hover {
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.quick-link:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.quick-link__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
}

.quick-link:hover .quick-link__icon {
  color: var(--tb-primary-text);
}

.quick-link__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.quick-link__title {
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.quick-link__desc {
  margin-top: 2px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
}
</style>
