<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import AdminSidebar from './components/AdminSidebar.vue'
import AdminHeader from './components/AdminHeader.vue'
import { adminRoutes } from '@/router/admin.routes'
import { buildMenu } from './utils/menu'
import { useAdminSession, exitAdminSession } from './hooks/useAdminSession'
import { removeToken } from '@/utils/auth'
import './styles/admin.css'

const router = useRouter()
const { status, profile, permissions, notice, isDenied, verifyAdminSession } = useAdminSession()

const collapsed = ref(false)

/** 菜单由路由 meta 生成：改 admin.routes.js 即同时改侧栏，不存在第二份菜单数据 */
const menu = computed(() => buildMenu(adminRoutes, { permissions: permissions.value }))

onMounted(async () => {
  await verifyAdminSession()
  if (isDenied.value) {
    router.replace({ name: 'forbidden', query: { from: '/admin' } })
  }
})

function handleLogout() {
  removeToken()
  exitAdminSession()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div class="admin-shell" :class="{ 'is-collapsed': collapsed }">
      <AdminSidebar
        :pinned="menu.pinned"
        :groups="menu.groups"
        :collapsed="collapsed"
        @toggle="collapsed = !collapsed"
      />

      <div class="admin-shell__body">
        <AdminHeader :profile="profile" :status="status" :notice="notice" @logout="handleLogout" />

        <main class="admin-main">
          <div class="admin-main__inner">
            <RouterView />
          </div>
        </main>
      </div>
    </div>
  </el-config-provider>
</template>
