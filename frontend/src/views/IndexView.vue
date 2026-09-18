<template>
  <div class="index-layout">
    <aside class="sidebar" :class="{ 'sidebar--collapsed': collapsed }">
      <div class="sidebar__header">
        <a v-if="!collapsed" class="sidebar__brand" href="/index" aria-label="TrueBuddy 首页">
          <svg class="sidebar__logo" viewBox="0 0 120 120" aria-hidden="true">
            <rect x="24" y="37" width="16" height="62" fill="#111" />
            <line x1="24" y1="44" x2="40" y2="44" stroke="#fff" stroke-width="3" />
            <rect x="46" y="22" width="16" height="74" fill="none" stroke="#111" stroke-width="6" />
            <rect x="68" y="47" width="16" height="52" fill="#111" />
            <line x1="67" y1="55" x2="85" y2="55" stroke="#fff" stroke-width="3" />
            <rect x="90" y="28" width="16" height="68" fill="none" stroke="#111" stroke-width="6" />
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
        <button v-if="!collapsed" type="button" class="sidebar__setting" aria-label="设置">
          <el-icon><Setting /></el-icon>
        </button>
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
  Setting,
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

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
  background: #f9fafb;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  flex: 0 0 auto;
  background: #f3f4f6;
  border-right: 1px solid #e5e7eb;
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
  color: inherit;
  min-width: 0;
}

.sidebar__logo {
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
}

.sidebar__name {
  margin-left: 0.5rem;
  font-size: 1.1rem;
  font-weight: 700;
  color: #111827;
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
    color: #6b7280;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s ease;
}

.sidebar__collapse:hover {
  background: #e5e7eb;
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
  border-radius: 8px;
  color: #4b5563;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.menu-item:hover {
  background: #e5e7eb;
  color: #111827;
}

.menu-item--active {
  background: #e5e7eb;
  color: #111827;
  font-weight: 600;
}

.menu-item__icon {
  flex: 0 0 auto;
  color: #6b7280;
}

.menu-item--active .menu-item__icon {
  color: #111827;
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
  border-top: 1px solid #e5e7eb;
  margin-top: 0.75rem;
}

.sidebar__user {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.sidebar__avatar {
  background: #6366f1;
  color: #fff;
  font-size: 0.875rem;
  flex: 0 0 auto;
}

.sidebar__user-info {
  min-width: 0;
}

.sidebar__username {
  font-size: 0.875rem;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
}

.sidebar__role {
  font-size: 0.75rem;
  color: #9ca3af;
  white-space: nowrap;
}

.sidebar__setting {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  border: none;
  background: transparent;
  color: #6b7280;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.sidebar__setting:hover {
  background: #e5e7eb;
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
</style>