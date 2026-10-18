<script setup>
import { useRoute } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'
import AdminIcon from './AdminIcon.vue'

defineProps({
  pinned: { type: Array, default: () => [] },
  groups: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle'])

const route = useRoute()

function isActive(path) {
  return route.path === path
}
</script>

<template>
  <aside class="admin-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="admin-sidebar__header">
      <RouterLink class="admin-sidebar__brand" to="/admin/dashboard" aria-label="TrueBuddy 管理端首页">
        <svg class="admin-sidebar__logo" viewBox="0 0 120 120" aria-hidden="true">
          <rect x="24" y="37" width="16" height="62" fill="currentColor" />
          <line x1="24" y1="44" x2="40" y2="44" stroke="var(--tb-bg-surface)" stroke-width="3" />
          <rect x="46" y="22" width="16" height="74" fill="none" stroke="currentColor" stroke-width="6" />
          <rect x="68" y="47" width="16" height="52" fill="currentColor" />
          <line x1="67" y1="55" x2="85" y2="55" stroke="var(--tb-bg-surface)" stroke-width="3" />
          <rect x="90" y="28" width="16" height="68" fill="none" stroke="currentColor" stroke-width="6" />
        </svg>
        <span v-if="!collapsed" class="admin-sidebar__brand-text">
          <span class="admin-sidebar__name">TrueBuddy</span>
          <span class="admin-sidebar__sub">管理控制台</span>
        </span>
      </RouterLink>
    </div>

    <nav class="admin-sidebar__nav" aria-label="管理端导航">
      <div v-if="pinned.length" class="admin-sidebar__pinned">
        <el-tooltip
          v-for="item in pinned"
          :key="item.path"
          :content="item.title"
          placement="right"
          :offset="12"
          :show-after="160"
          :disabled="!collapsed"
        >
          <RouterLink
            class="admin-menu-item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path"
            :aria-current="isActive(item.path) ? 'page' : undefined"
          >
            <AdminIcon class="admin-menu-item__icon" :name="item.icon" :size="18" />
            <span v-if="!collapsed" class="admin-menu-item__label">{{ item.title }}</span>
          </RouterLink>
        </el-tooltip>
      </div>

      <div v-for="group in groups" :key="group.label" class="admin-sidebar__group">
        <p v-if="!collapsed" class="admin-sidebar__group-label">{{ group.label }}</p>

        <el-tooltip
          v-for="item in group.items"
          :key="item.path"
          :content="item.title"
          placement="right"
          :offset="12"
          :show-after="160"
          :disabled="!collapsed"
        >
          <RouterLink
            class="admin-menu-item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path"
            :aria-current="isActive(item.path) ? 'page' : undefined"
          >
            <AdminIcon class="admin-menu-item__icon" :name="item.icon" :size="18" />
            <span v-if="!collapsed" class="admin-menu-item__label">{{ item.title }}</span>
          </RouterLink>
        </el-tooltip>
      </div>
    </nav>

    <div class="admin-sidebar__footer">
      <button
        type="button"
        class="admin-sidebar__collapse"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        :aria-expanded="!collapsed"
        @click="emit('toggle')"
      >
        <el-icon :size="16">
          <Expand v-if="collapsed" />
          <Fold v-else />
        </el-icon>
        <span v-if="!collapsed">收起侧栏</span>
      </button>
      <p v-if="!collapsed" class="admin-sidebar__version">管理端 v0.1 · 内部使用</p>
    </div>
  </aside>
</template>

<style scoped>
.admin-sidebar {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: 248px;
  padding: var(--tb-size-4) var(--tb-size-3);
  border-right: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
  overflow: hidden;
  transition: width var(--tb-dur-3) var(--tb-ease-out);
}

.admin-sidebar.is-collapsed {
  width: 76px;
  align-items: stretch;
}

.admin-sidebar__header {
  padding: 0 var(--tb-size-2);
  margin-bottom: var(--tb-size-5);
}

.admin-sidebar__brand {
  display: flex;
  align-items: center;
  min-width: 0;
  color: var(--tb-text-primary);
  text-decoration: none;
}

.admin-sidebar__brand:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 3px;
  border-radius: var(--tb-radius-sm);
}

.admin-sidebar__logo {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
}

.admin-sidebar__brand-text {
  display: flex;
  flex-direction: column;
  margin-left: var(--tb-size-2);
  min-width: 0;
}

.admin-sidebar__name {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-md);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  white-space: nowrap;
}

.admin-sidebar__sub {
  margin-top: 1px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  white-space: nowrap;
}

.admin-sidebar__nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--tb-border) transparent;
}

.admin-sidebar__group + .admin-sidebar__group {
  margin-top: var(--tb-size-4);
}

/* 固定区（概览）与功能模块之间用分隔线断开：它不是功能图里的一级模块 */
.admin-sidebar__pinned {
  padding-bottom: var(--tb-size-3);
  margin-bottom: var(--tb-size-3);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.admin-sidebar__pinned + .admin-sidebar__group {
  margin-top: 0;
}

.admin-sidebar__group-label {
  height: 24px;
  margin: 0 0 var(--tb-size-1);
  padding: 0 var(--tb-size-3);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
  line-height: 24px;
  white-space: nowrap;
}

.admin-menu-item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 38px;
  margin-bottom: 2px;
  padding: 0 var(--tb-size-3);
  border-radius: var(--tb-radius-md);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  font-weight: 500;
  text-decoration: none;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-menu-item:hover {
  color: var(--tb-text-primary);
  background: color-mix(in oklab, var(--tb-text-primary) 6%, transparent);
}

.admin-menu-item:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.admin-menu-item.is-active {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  font-weight: 600;
}

.admin-menu-item__icon {
  color: var(--tb-text-tertiary);
}

.admin-menu-item.is-active .admin-menu-item__icon {
  color: var(--tb-primary-text);
}

.admin-menu-item__label {
  margin-left: var(--tb-size-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.admin-sidebar.is-collapsed .admin-menu-item {
  justify-content: center;
  padding: 0;
}

.admin-sidebar__footer {
  flex: 0 0 auto;
  margin-top: var(--tb-size-3);
  padding-top: var(--tb-size-3);
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.admin-sidebar__collapse {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  width: 100%;
  height: 32px;
  padding: 0 var(--tb-size-3);
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-tertiary);
  font-family: inherit;
  font-size: var(--tb-text-xs);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-sidebar__collapse:hover {
  color: var(--tb-text-primary);
  background: color-mix(in oklab, var(--tb-text-primary) 6%, transparent);
}

.admin-sidebar__collapse:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.admin-sidebar.is-collapsed .admin-sidebar__collapse {
  justify-content: center;
  padding: 0;
}

.admin-sidebar__version {
  margin: var(--tb-size-2) 0 0;
  padding: 0 var(--tb-size-3);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar,
  .admin-menu-item,
  .admin-sidebar__collapse {
    transition: none;
  }
}
</style>
