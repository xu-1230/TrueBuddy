<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Monitor,
  MoonNight,
  Odometer,
  Setting,
  Sunny,
  SwitchButton,
  Tickets,
  Tools,
  User,
} from '@element-plus/icons-vue'
import AdminBreadcrumb from './AdminBreadcrumb.vue'
import { useAppearance } from '@/composables/useAppearance'
import { ADMIN_SESSION_STATUS } from '@/views/admin/hooks/useAdminSession'

const props = defineProps({
  profile: { type: Object, default: () => ({}) },
  status: { type: String, default: ADMIN_SESSION_STATUS.UNKNOWN },
  notice: { type: String, default: '' },
})

const emit = defineEmits(['logout'])

const route = useRoute()
const router = useRouter()
const { isDark, toggle } = useAppearance()

/** 面包屑：管理端固定根节点 + 当前匹配链上带 title 的路由 */
const breadcrumbItems = computed(() => {
  const trail = []
  for (const record of route.matched) {
    const title = record.meta?.title
    if (!title) continue
    trail.push({ title, path: record.path })
  }
  return [{ title: '管理端', path: '/admin/dashboard' }, ...trail]
})

const isOffline = computed(() => props.status === ADMIN_SESSION_STATUS.OFFLINE)
const isChecking = computed(() => props.status === ADMIN_SESSION_STATUS.CHECKING)
const initial = computed(() => props.profile?.initial || '管')
const nickname = computed(() => props.profile?.nickname || '管理员')
const roleLabel = computed(() => props.profile?.roleLabel || '管理员')

function handleCommand(command) {
  if (command === 'profile') {
    ElMessage.info('个人资料页待接入')
    return
  }
  if (command === 'workspace') {
    router.push('/index')
    return
  }
  if (command === 'logout') {
    emit('logout')
    return
  }
  // 平台运维不在功能模块图内，从顶栏齿轮进入
  if (command.startsWith('system-')) {
    router.push(`/admin/system/${command.slice('system-'.length)}`)
  }
}
</script>

<template>
  <header class="admin-header">
    <div class="admin-header__left">
      <AdminBreadcrumb :items="breadcrumbItems" />
    </div>

    <div class="admin-header__right">
      <el-tooltip v-if="isOffline" :content="notice" placement="bottom" :show-after="120">
        <el-tag type="warning" size="small" effect="plain">演示数据</el-tag>
      </el-tooltip>
      <el-tag v-else-if="isChecking" type="info" size="small" effect="plain">身份校验中</el-tag>

      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button type="button" class="admin-header__icon" aria-label="系统运维">
          <el-icon :size="17"><Setting /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="system-setting" :icon="Tools">系统配置</el-dropdown-item>
            <el-dropdown-item command="system-log" :icon="Tickets">操作日志</el-dropdown-item>
            <el-dropdown-item command="system-health" :icon="Odometer">服务健康</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <button
        type="button"
        class="admin-header__icon"
        :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
        :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
        @click="toggle"
      >
        <el-icon :size="17">
          <Sunny v-if="isDark" />
          <MoonNight v-else />
        </el-icon>
      </button>

      <RouterLink class="admin-header__workspace" to="/index">
        <el-icon :size="15"><Monitor /></el-icon>
        <span>用户端</span>
      </RouterLink>

      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button type="button" class="admin-user" aria-label="账号菜单">
          <el-avatar :size="28" class="admin-user__avatar">{{ initial }}</el-avatar>
          <span class="admin-user__info">
            <span class="admin-user__name">{{ nickname }}</span>
            <span class="admin-user__role">{{ roleLabel }}</span>
          </span>
          <el-icon :size="12" class="admin-user__arrow"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile" :icon="User">个人资料</el-dropdown-item>
            <el-dropdown-item command="workspace" :icon="Monitor">返回用户端</el-dropdown-item>
            <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-4);
  flex: 0 0 auto;
  height: 60px;
  padding: 0 var(--tb-size-6);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.admin-header__left {
  min-width: 0;
  overflow: hidden;
}

.admin-header__right {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  flex: 0 0 auto;
}

.admin-header__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: var(--tb-border-width) solid transparent;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-header__icon:hover {
  color: var(--tb-text-primary);
  background: var(--tb-bg-raised);
  border-color: var(--tb-border);
}

.admin-header__icon:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.admin-header__workspace {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  height: 32px;
  padding: 0 var(--tb-size-3);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  text-decoration: none;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-header__workspace:hover {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
}

.admin-header__workspace:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  height: 40px;
  padding: 0 var(--tb-size-2) 0 var(--tb-size-1);
  border: var(--tb-border-width) solid transparent;
  border-radius: var(--tb-radius-md);
  background: transparent;
  font-family: inherit;
  cursor: pointer;
  transition:
    background var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-user:hover {
  background: var(--tb-bg-raised);
  border-color: var(--tb-border);
}

.admin-user:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
}

.admin-user__avatar {
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  font-size: var(--tb-text-xs);
}

.admin-user__info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  line-height: 1.2;
}

.admin-user__name {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.admin-user__role {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  white-space: nowrap;
}

.admin-user__arrow {
  color: var(--tb-text-tertiary);
}

@media (max-width: 768px) {
  .admin-header {
    padding: 0 var(--tb-size-4);
  }

  .admin-user__info,
  .admin-header__workspace span {
    display: none;
  }

  .admin-header__workspace {
    padding: 0 var(--tb-size-2);
  }
}

@media (prefers-reduced-motion: reduce) {
  .admin-header__icon,
  .admin-header__workspace,
  .admin-user {
    transition: none;
  }
}
</style>
