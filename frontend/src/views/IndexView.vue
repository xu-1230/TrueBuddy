<template>
  <div class="index-layout">
    <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
      <div class="sidebar__header">
        <a v-if="!collapsed" class="sidebar__brand" href="/index" aria-label="TrueBuddy 首页">
          <svg class="sidebar__logo" viewBox="0 0 120 120" aria-hidden="true">
            <rect x="24" y="37" width="16" height="62" fill="currentColor" />
            <line x1="24" y1="44" x2="40" y2="44" stroke="var(--tb-bg-raised)" stroke-width="3" />
            <rect x="46" y="22" width="16" height="74" fill="none" stroke="currentColor" stroke-width="6" />
            <rect x="68" y="47" width="16" height="52" fill="currentColor" />
            <line x1="67" y1="55" x2="85" y2="55" stroke="var(--tb-bg-raised)" stroke-width="3" />
            <rect x="90" y="28" width="16" height="68" fill="none" stroke="currentColor" stroke-width="6" />
          </svg>
          <span v-if="!collapsed" class="sidebar__name">TrueBuddy</span>
        </a>
        <button
          type="button"
          class="sidebar__collapse"
          :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="collapsed = !collapsed"
        >
          <el-icon>
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
        </button>
      </div>

      <nav class="sidebar__menu">
        <button
          v-for="item in menuItems"
          :key="item.path"
          type="button"
          class="menu-item"
          :class="{ 'menu-item--active': isActive(item) }"
          @click="go(item)"
        >
          <el-icon class="menu-item__icon"><component :is="item.icon" /></el-icon>
          <span v-if="!collapsed" class="menu-item__label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar__footer">
        <div class="sidebar__user">
          <el-avatar :size="32" class="sidebar__avatar">{{ user.initial }}</el-avatar>
          <div v-if="!collapsed" class="sidebar__user-info">
            <div class="sidebar__username">{{ user.name }}</div>
            <div class="sidebar__role">{{ user.role }}</div>
          </div>
        </div>
        <div class="sidebar__actions">
          <button
            type="button"
            class="sidebar__setting"
            role="switch"
            :aria-checked="isDark"
            :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
            :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
            @click="toggle"
          >
            <span class="sidebar__theme-icon" :class="{ 'is-shown': !isDark }">
              <el-icon><Sunny /></el-icon>
            </span>
            <span class="sidebar__theme-icon" :class="{ 'is-shown': isDark }">
              <el-icon><MoonNight /></el-icon>
            </span>
          </button>
          <button v-if="!collapsed" type="button" class="sidebar__setting" aria-label="设置">
            <el-icon><Setting /></el-icon>
          </button>
        </div>
      </div>
    </aside>

    <main class="index-main" :class="{ 'index-main--flush': isFullBleed }">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Collection,
  Connection,
  Document,
  EditPen,
  Expand,
  Fold,
  HomeFilled,
  Monitor,
  MoonNight,
  Setting,
  Sunny,
} from '@element-plus/icons-vue'
import { useAppearance } from '@/composables/useAppearance'

const route = useRoute()
const router = useRouter()

/** 明暗态：写入 html[data-appearance] 与 html.dark，全站 Token 与 Element Plus 随之切换 */
const { isDark, toggle } = useAppearance()

const collapsed = ref(false)

/** 工作流编排是全屏画布，需要去掉主区内边距与滚动 */
const isFullBleed = computed(() => route.path === '/index/workflow')

const user = reactive({
  name: '张三',
  role: '免费用户',
  initial: '张',
})

const menuItems = [

  { path: '/index/home', label: '首页', icon: HomeFilled },
  { path: '/index/doc-template', label: '文档模板生成', icon: Document },
  { path: '/index/homework-grading', label: '作业批改', icon: EditPen },
  // { path: '/',label:'作业生成',icon: EditPen},
  { path: '/index/knowledge-base', label: '知识库模块', icon: Collection },
  { path: '/index/ppt', label: 'PPT 演示', icon: Monitor },
  { path: '/index/workflow', label: '工作流编排', icon: Connection },

]

function go(item) {
  router.push(item.path)
}

function isActive(item) {
  return route.path === item.path
}
</script>

<style scoped>
.index-layout {
  display: flex;
  height: 100vh;
  background: var(--tb-bg-page);
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  flex: 0 0 auto;
  background: var(--tb-bg-raised);
  border-right: 1px solid var(--tb-border);
  padding: 1rem 0.75rem;
  transition: width 0.2s ease;
  overflow: hidden;
}

.sidebar--collapsed {
  width: 76px;
}

.sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0.25rem;
  margin-bottom: 1rem;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  text-decoration: none;
  /* logo 使用 currentColor，随主题文字色变化 */
  color: var(--tb-text-primary);
  min-width: 0;
}

.sidebar__logo {
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
}

.sidebar__name {
  margin-left: 0.5rem;
  font-family: var(--tb-font-display);
  font-size: 1.25rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
  white-space: nowrap;
}

.sidebar__collapse {
  font-size: 25px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4rem;
  height: 2rem;
  border: none;
  background: transparent;
  color: var(--tb-text-secondary);
  border-radius: var(--tb-radius-md);
  cursor: pointer;
  transition: background var(--tb-motion-dur) var(--tb-motion-ease);
}

.sidebar__collapse:hover {
  background: var(--tb-bg-sunken);
}

.sidebar__menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  overflow-y: auto;
  min-height: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.5rem;
  border: none;
  background: transparent;
  border-radius: var(--tb-radius-md);
  color: var(--tb-text-secondary);
  font-size: 0.9rem;
  cursor: pointer;
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    color var(--tb-motion-dur) var(--tb-motion-ease);
}

.menu-item:hover {
  background: var(--tb-bg-sunken);
  color: var(--tb-text-primary);
}

.menu-item--active {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-weight: 600;
}

.menu-item__icon {
  flex: 0 0 auto;
  color: var(--tb-text-tertiary);
}

.menu-item--active .menu-item__icon {
  color: var(--tb-primary-text);
}

.menu-item__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar--collapsed .menu-item {
  justify-content: center;
  padding: 0.6rem 0;
}

.sidebar__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 0.25rem 0;
  border-top: 1px solid var(--tb-border);
  margin-top: 0.75rem;
}

/* 折叠态竖向排列，避免页脚操作区溢出 76px 侧栏 */
.sidebar--collapsed .sidebar__footer {
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.sidebar__user {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.sidebar__avatar {
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  font-size: 0.875rem;
  flex: 0 0 auto;
}

.sidebar__user-info {
  min-width: 0;
}

.sidebar__username {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  white-space: nowrap;
}

.sidebar__role {
  font-size: 0.75rem;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.sidebar__actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex: 0 0 auto;
}

.sidebar__setting {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  background: transparent;
  color: var(--tb-text-secondary);
  border-radius: var(--tb-radius-md);
  cursor: pointer;
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    color var(--tb-motion-dur) var(--tb-motion-ease);
}

.sidebar__setting:hover {
  background: var(--tb-bg-sunken);
  color: var(--tb-primary-text);
}

/* 明暗切换：两枚图标叠放交叉淡入，按钮宽度不跳动 */
.sidebar__theme-icon {
  position: absolute;
  display: inline-flex;
  opacity: 0;
  transform: rotate(-35deg) scale(0.7);
  transition:
    opacity var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-motion-dur) var(--tb-ease-spring);
}

.sidebar__theme-icon.is-shown {
  opacity: 1;
  transform: rotate(0) scale(1);
}

.index-main {
  flex: 1;
  padding: 1rem;
  overflow-y: auto;
}

.index-main--flush {
  padding: 0;
  overflow: hidden;
}

@media (prefers-reduced-motion: reduce) {
  .sidebar__theme-icon {
    transition: none;
  }
}
</style>