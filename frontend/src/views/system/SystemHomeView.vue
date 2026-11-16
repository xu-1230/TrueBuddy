<template>
  <div class="home">
    <HomeHero />

    <!-- Zone B 可用工具：等宽两栏（每张卡占 2 格的节奏） -->
    <section class="band" aria-labelledby="band-modules">
      <div class="rail">
        <ShowcaseCard
          v-for="card in moduleCards"
          :key="card.id"
          v-bind="card"
          @open="go(card.to)"
        />
      </div>
    </section>

    <!-- Zone C + D：标签页 / 排序 + 我的资产网格 -->
    <section class="band" aria-labelledby="band-assets">
      <header class="band__head band__head--tabs">
        <h2 id="band-assets" class="band__title band__title--hidden">我的资产</h2>
        <el-tabs v-model="activeTab" class="tabs" :before-leave="onBeforeTabLeave">
          <el-tab-pane v-for="tab in HOME_TABS" :key="tab.key" :label="tab.label" :name="tab.key" />
        </el-tabs>
        <el-dropdown trigger="click" @command="pickSort">
          <span class="sort">
            <span>{{ activeSortLabel }}</span>
            <el-icon :size="12"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="option in HOME_SORTS"
                :key="option.key"
                :command="option.key"
                :disabled="option.key === sortKey"
              >
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <div v-loading="loading" class="assets">
        <div v-if="assetCards.length" class="assets__grid">
          <AssetCard
            v-for="card in assetCards"
            :key="card.key"
            :uid="card.key"
            :title="card.title"
            :description="card.description"
            :kind="card.kind"
            :kind-label="card.kindLabel"
            :metric="card.metric"
            :metric-label="card.metricLabel"
            :state="card.state"
            :state-tone="card.stateTone"
            :time-label="card.timeLabel"
            :created-at="card.createdAt"
            :updated-at="card.updatedAt"
            :index="card.index"
            @open="go(card.to)"
          />
        </div>
        <el-empty v-else-if="!loading" :description="emptyDescription">
          <el-button type="primary" @click="go('/index/knowledge-base')">去创建知识库</el-button>
        </el-empty>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { getGenerationHistory } from '@/api/doctemplate'
import { getSpaceList } from '@/api/knowledgebase'
import { listWorkflows } from '@/api/workflow'
import AssetCard from './components/AssetCard.vue'
import HomeHero from './components/HomeHero.vue'
import ShowcaseCard from './components/ShowcaseCard.vue'
import { HOME_SORTS, HOME_TABS, KIND_META, MODULE_CARDS } from './constants'
import {
  buildFeed,
  buildModuleStats,
  filterFeed,
  formatCount,
  formatRelative,
  sortFeed,
} from './utils/feed'

const router = useRouter()

/** 三个来源的原始返回，请求失败留 null，不阻塞其余来源渲染 */
const sources = ref({ spaces: null, workflows: null, generations: null })
const loading = ref(false)
const failedCount = ref(0)

/**
 * 过滤态跨页保持：跳转型 tab 跳走再回来仍停在原过滤 tab（US3）。
 * 只存 filter 型 key，兼容旧会话里残留的已下线 tab。
 */
const ACTIVE_TAB_STORAGE_KEY = 'tb-home-active-tab'
const FILTER_TAB_KEYS = HOME_TABS.filter((tab) => tab.type === 'filter').map((tab) => tab.key)
const storedTab = sessionStorage.getItem(ACTIVE_TAB_STORAGE_KEY)
const activeTab = ref(FILTER_TAB_KEYS.includes(storedTab) ? storedTab : 'all')
watch(activeTab, (value) => sessionStorage.setItem(ACTIVE_TAB_STORAGE_KEY, value))

const sortKey = ref('recommended')

/** 相对时间以「本次加载」为基准，避免每秒重算导致文案跳动 */
const loadedAt = ref(Date.now())

const feedItems = computed(() => buildFeed(sources.value, { now: loadedAt.value }))
const moduleStats = computed(() => buildModuleStats(feedItems.value))

/** Zone D 卡片字段：数字与时间交由 AssetCard 呈现，这里只做归一化透传 */
const assetCards = computed(() =>
  sortFeed(filterFeed(feedItems.value, activeTab.value), sortKey.value).map((item, index) => ({
    key: item.key,
    index,
    title: item.name,
    description: item.description,
    kind: item.kind,
    kindLabel: KIND_META[item.kind]?.label || '',
    metric: item.metric,
    metricLabel: item.metricLabel,
    state: item.state,
    stateTone: item.stateTone,
    timeLabel: formatRelative(item.updatedAt, loadedAt.value),
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
    to: item.to,
  })),
)

const moduleCards = computed(() =>
  MODULE_CARDS.map((card) => {
    const stat = card.source ? moduleStats.value[card.source] : null
    return {
      id: card.id,
      title: card.title,
      description: card.description,
      icon: card.icon,
      image: card.image,
      metric: stat ? formatCount(stat.count) : '',
      metricLabel: stat ? card.unit : '',
      to: card.to,
    }
  }),
)

const activeSortLabel = computed(
  () => HOME_SORTS.find((option) => option.key === sortKey.value)?.label || '推荐',
)

const emptyDescription = computed(() =>
  feedItems.value.length ? '该分类下还没有内容' : '还没有任何资产，先从知识库开始',
)

function go(path) {
  router.push(path)
}

/** 跳转型 tab 不占用过滤态：拦截切换，直接路由跳转，activeTab 保持原值 */
function onBeforeTabLeave(activeName) {
  const tab = HOME_TABS.find((item) => item.key === activeName)
  if (tab?.type !== 'jump') return true
  router.push(tab.to)
  return false
}

function pickSort(key) {
  sortKey.value = key
}

/**
 * 三个来源用 allSettled 并发：任一接口挂掉只影响它自己那一类卡片，
 * 首页不该因为一个模块的故障整页空白。
 */
const FEED_SOURCES = [
  { key: 'spaces', label: '知识库空间', request: getSpaceList },
  { key: 'workflows', label: '工作流', request: listWorkflows },
  { key: 'generations', label: '文档生成历史', request: () => getGenerationHistory(24) },
]

async function loadFeed() {
  loading.value = true
  try {
    const settled = await Promise.allSettled(FEED_SOURCES.map((source) => source.request()))
    const payload = {}
    let failed = 0

    settled.forEach((result, index) => {
      const { key, label } = FEED_SOURCES[index]
      if (result.status === 'fulfilled') {
        payload[key] = result.value
        return
      }
      payload[key] = null
      failed += 1
      console.warn(`[home] 信息流来源「${label}」加载失败：`, result.reason?.message || result.reason)
    })

    sources.value = payload
    failedCount.value = failed
    loadedAt.value = Date.now()
    if (failed) ElMessage.warning(`${failed} 个模块数据未加载完成`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadFeed()
})
</script>

<style scoped>
/* 宽度完全由主区决定：侧栏折叠后主区多出的 184px 必须真的落到内容上。
   原先写死 max-width: 1680px，1920 屏上折叠前后只差 52px（1680 - 1628）、
   网格列数也不变，看起来就像折叠没生效；其余业务页同样是流式铺满，这里跟随。 */
.home {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-6);
  min-width: 0;
}

.band {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
  min-width: 0;
}

.band__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--tb-size-3);
}

.band__titles {
  min-width: 0;
}

.band__title {
  margin: 0;
  font-size: var(--tb-text-xl);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  color: var(--tb-text-primary);
}

/* 参考图 Zone C 没有标题，只有一行标签页；标题留给读屏 */
.band__title--hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.band__sub {
  margin: 2px 0 0;
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.band__head--tabs {
  align-items: center;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

/* ========== Zone B 可用工具 ========== */
/* 与 HomeHero 同一套版式语言：按 fr 分的等宽两栏，铺满整行，
   不从 Zone D 的资产网格借列（借列会在 3 列的档位上让第二张卡换行、留下空格） */
.rail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--tb-size-3);
}

/* ========== Zone C 标签页 ========== */
.tabs {
  flex: 1;
  min-width: 0;
}

/* 局部覆盖：去掉 EP 通栏底边与内容区占位，只留文字 + 短下划线 */
.tabs :deep(.el-tabs__header) {
  margin: 0;
}

.tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background: var(--tb-primary);
  border-radius: var(--tb-radius-xs);
}

.tabs :deep(.el-tabs__item) {
  height: var(--tb-control-md);
  font-size: var(--tb-text-md);
  color: var(--tb-text-secondary);
}

.tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
  color: var(--tb-text-primary);
}

.tabs :deep(.el-tabs__content) {
  display: none;
}

.sort {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: var(--tb-size-1);
  height: var(--tb-control-sm);
  padding: 0 var(--tb-size-3);
  font-size: var(--tb-text-sm);
  color: var(--tb-text-secondary);
  cursor: pointer;
  border-radius: var(--tb-radius-sm);
  transition: color var(--tb-dur-1) var(--tb-ease-out);
}

.sort:hover {
  color: var(--tb-primary-text);
}

/* ========== Zone D 网格 ========== */
/* 容器查询基准：列数随主区实际宽度（含侧栏折叠）自动降档，不随视口误判 */
.assets {
  display: flex;
  flex-direction: column;
  min-height: 240px;
  container-type: inline-size;
}

/* 标准 5 列（参考件间距 22px），随宽度逐级 4/3/2/1 */
.assets__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 22px;
}

@container (max-width: 1519px) {
  .assets__grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@container (max-width: 1239px) {
  .assets__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@container (max-width: 979px) {
  .assets__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container (max-width: 679px) {
  .assets__grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }
}

@media (max-width: 1024px) {
  .home {
    gap: var(--tb-size-5);
  }
}

@media (max-width: 768px) {
  .band__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .band__head--tabs {
    flex-direction: row;
    align-items: center;
  }

  /* 与 HomeHero 同一档位收成一栏 */
  .rail {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
