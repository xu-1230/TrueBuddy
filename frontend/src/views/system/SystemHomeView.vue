<template>
  <div class="home">
    <HomeHero />

    <!-- Zone B 可用工具：与 Zone D 共用列宽，每张卡占 2 格 -->
    <section class="band" aria-labelledby="band-modules">
      <div class="rail">
        <ShowcaseCard
          v-for="card in moduleCards"
          :key="card.id"
          v-bind="card"
          variant="entry"
          @open="go(card.to)"
        />
      </div>
    </section>

    <!-- Zone C + D：标签页 / 排序 + 我的资产网格 -->
    <section class="band" aria-labelledby="band-assets">
      <header class="band__head band__head--tabs">
        <h2 id="band-assets" class="band__title band__title--hidden">我的资产</h2>
        <el-tabs v-model="activeTab" class="tabs">
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
          <ShowcaseCard
            v-for="card in assetCards"
            :key="card.key"
            v-bind="card"
            variant="asset"
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { getGenerationHistory } from '@/api/doctemplate'
import { getSpaceList } from '@/api/knowledgebase'
import { listConversations, listWorkflows } from '@/api/workflow'
import HomeHero from './components/HomeHero.vue'
import ShowcaseCard from './components/ShowcaseCard.vue'
import { HOME_SORTS, HOME_TABS, KIND_META, MODULE_CARDS } from './constants'
import {
  buildFeed,
  buildModuleStats,
  filterFeed,
  formatCount,
  formatRelative,
  moduleBadge,
  sortFeed,
} from './utils/feed'

const router = useRouter()

/** 四个来源的原始返回，请求失败留 null，不阻塞其余来源渲染 */
const sources = ref({ spaces: null, workflows: null, generations: null, conversations: null })
const loading = ref(false)
const failedCount = ref(0)

const activeTab = ref('all')
const sortKey = ref('recommended')

/** 相对时间以「本次加载」为基准，避免每秒重算导致文案跳动 */
const loadedAt = ref(Date.now())

const feedItems = computed(() => buildFeed(sources.value, { now: loadedAt.value }))
const moduleStats = computed(() => buildModuleStats(feedItems.value))

const assetCards = computed(() =>
  sortFeed(filterFeed(feedItems.value, activeTab.value), sortKey.value).map((item) => {
    const meta = KIND_META[item.kind] || {}
    return {
      key: item.key,
      title: item.name,
      description: item.description,
      icon: meta.icon,
      pattern: meta.pattern,
      kindLabel: meta.label,
      badge: item.badge,
      metric: item.metric === null ? '' : formatCount(item.metric),
      metricLabel: item.metricLabel,
      state: item.state,
      stateTone: item.stateTone,
      timeLabel: formatRelative(item.updatedAt, loadedAt.value),
      to: item.to,
    }
  }),
)

const moduleCards = computed(() =>
  MODULE_CARDS.map((card) => {
    const stat = card.source ? moduleStats.value[card.source] : null
    return {
      id: card.id,
      title: card.title,
      description: card.description,
      icon: card.icon,
      pattern: card.pattern,
      image: card.image,
      kindLabel: '',
      badge: moduleBadge(stat, card.hotAt),
      metric: stat ? formatCount(stat.count) : '',
      metricLabel: stat ? card.unit : '',
      to: card.to,
    }
  }),
)

const railSummary = computed(() => {
  const total = feedItems.value.length
  if (!total) return '暂无可统计的资产，卡片上的数字会在创建后自动出现'
  if (failedCount.value) return `已汇总 ${total} 项资产 · ${failedCount.value} 个来源未加载完成`
  return `已汇总 ${total} 项资产 · 数字来自各模块的真实列表`
})

const activeSortLabel = computed(
  () => HOME_SORTS.find((option) => option.key === sortKey.value)?.label || '推荐',
)

const emptyDescription = computed(() =>
  feedItems.value.length ? '该分类下还没有内容' : '还没有任何资产，先从知识库开始',
)

function go(path) {
  router.push(path)
}

function pickSort(key) {
  sortKey.value = key
}

/**
 * 四个来源用 allSettled 并发：任一接口挂掉只影响它自己那一类卡片，
 * 首页不该因为一个模块的故障整页空白。
 */
const FEED_SOURCES = [
  { key: 'spaces', label: '知识库空间', request: getSpaceList },
  { key: 'workflows', label: '工作流', request: listWorkflows },
  { key: 'generations', label: '文档生成历史', request: () => getGenerationHistory(24) },
  { key: 'conversations', label: '会话', request: listConversations },
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
.assets {
  display: flex;
  flex-direction: column;
  min-height: 240px;
}

.assets__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
  gap: var(--tb-size-3);
}

@media (max-width: 1024px) {
  .home {
    gap: var(--tb-size-5);
  }

  .assets__grid {
    grid-template-columns: repeat(auto-fill, minmax(208px, 1fr));
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

  .assets__grid {
    grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  }

  /* 与 HomeHero 同一档位收成一栏 */
  .rail {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
