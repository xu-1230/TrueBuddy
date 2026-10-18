<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import LoginForm from '@/components/LoginForm.vue'
import {
  enableAdminPreview,
  exitAdminSession,
  verifyAdminSession,
} from '@/views/admin/hooks/useAdminSession'

const route = useRoute()
const router = useRouter()

const isDev = import.meta.env.DEV

/** 只接受指向管理端的回跳地址，避免登录后被带到任意页面 */
const redirect = computed(() => {
  const target = route.query.redirect
  return typeof target === 'string' && target.startsWith('/admin') ? target : '/admin/dashboard'
})

async function handleLoginSuccess() {
  exitAdminSession()
  const status = await verifyAdminSession({ force: true })

  if (status === 'denied') {
    router.replace({ name: 'forbidden', query: { from: redirect.value } })
    return
  }

  router.replace(redirect.value)
}

/** 开发环境预览：不请求后端，页面使用内置演示数据 */
function enterPreview() {
  if (enableAdminPreview()) router.replace(redirect.value)
}
</script>

<template>
  <main class="admin-login">
    <section class="admin-login__card">
      <RouterLink class="admin-login__back" to="/index">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        <span>返回用户端</span>
      </RouterLink>

      <header class="admin-login__head">
        <p class="admin-login__eyebrow">TrueBuddy Admin</p>
        <h1 class="admin-login__title">管理端登录</h1>
        <p class="admin-login__desc">
          管理端只对管理员账号开放。请使用已在 <code>system_user_role</code> 中绑定
          <code>admin</code> 角色的账号登录。
        </p>
      </header>

      <LoginForm @login-success="handleLoginSuccess" @go-forgot="() => {}" />

      <div v-if="isDev" class="admin-login__preview">
        <el-divider>或</el-divider>
        <el-button class="admin-login__preview-btn" @click="enterPreview">以演示数据预览</el-button>
        <p class="admin-login__preview-hint">
          仅开发环境可见：不请求后端，页面使用内置演示数据，顶部会标注「演示数据」。
        </p>
      </div>

      <footer class="admin-login__foot">
        <span>普通用户 / 学生请从</span>
        <RouterLink class="admin-login__link" to="/login">用户登录页</RouterLink>
        <span>进入。</span>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.admin-login {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--tb-size-5);
  background-color: var(--tb-paper-bg);
  background-image:
    linear-gradient(90deg, var(--tb-paper-line) 1px, transparent 1px),
    linear-gradient(var(--tb-paper-line) 1px, transparent 1px);
  background-size: var(--tb-paper-size) var(--tb-paper-size);
}

.admin-login__card {
  position: relative;
  width: min(440px, 100%);
  padding: var(--tb-size-6);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
}

.admin-login__back {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  margin-bottom: var(--tb-size-5);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  text-decoration: none;
}

.admin-login__back:hover {
  color: var(--tb-primary-text);
}

.admin-login__head {
  margin-bottom: var(--tb-size-5);
}

.admin-login__eyebrow {
  margin: 0 0 var(--tb-size-2);
  color: var(--tb-primary-text);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  text-transform: uppercase;
}

.admin-login__title {
  margin: 0;
  color: var(--tb-text-primary);
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-2xl);
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-tight);
}

.admin-login__desc {
  margin: var(--tb-size-2) 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
}

.admin-login__desc code {
  padding: 1px 4px;
  border-radius: var(--tb-radius-xs);
  background: var(--tb-bg-raised);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
}

.admin-login__foot {
  margin-top: var(--tb-size-5);
  padding-top: var(--tb-size-4);
  border-top: var(--tb-border-width) solid var(--tb-border);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  text-align: center;
}

.admin-login__preview {
  margin-top: var(--tb-size-4);
  text-align: center;
}

.admin-login__preview :deep(.el-divider) {
  margin: var(--tb-size-4) 0 var(--tb-size-3);
}

.admin-login__preview :deep(.el-divider__text) {
  background: var(--tb-bg-surface);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.admin-login__preview-btn {
  width: 100%;
}

.admin-login__preview-hint {
  margin: var(--tb-size-2) 0 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-snug);
}

.admin-login__link {
  margin: 0 var(--tb-size-1);
  color: var(--tb-primary-text);
  text-decoration: none;
}

.admin-login__link:hover {
  text-decoration: underline;
}

/* 登录表单在窄卡片里去掉大间距，避免首屏被顶出可视区 */
.admin-login :deep(.el-form-item) {
  margin-bottom: var(--tb-size-4);
}
</style>
