<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MoonNight, Right, Sunny } from '@element-plus/icons-vue'
import LoginForm from '@/components/LoginForm.vue'
import RegisterForm from '@/components/RegisterForm.vue'
import ForgetPasswordForm from '@/components/ForgetPasswordForm.vue'
import LoginShowcase from '@/components/LoginShowcase.vue'
import { useAppearance } from '@/composables/useAppearance'
import { isSafeRedirect, resolveLoginDestination } from '@/utils/access'
import { enableAdminPreview, resetAdminSession } from '@/views/admin/hooks/useAdminSession'

const route = useRoute()
const router = useRouter()

/** 开发环境才显示「演示数据预览」入口 */
const isDev = import.meta.env.DEV

/** 明暗态：本页不使用内页外壳，故独立提供切换入口 */
const { isDark, toggle } = useAppearance()

const FORM_MAP = {
  login: LoginForm,
  register: RegisterForm,
  forgot: ForgetPasswordForm,
}

/** 引导语文案与表单态联动，避免注册态仍挂着「HELLO.」 */
const COPY = {
  login: {
    eyebrow: 'Sign in · 登录',
    title: 'HELLO.',
    lede: '欢迎回来，接着上次的备课与批改继续。',
  },
  register: {
    eyebrow: 'Sign up · 注册',
    title: 'JOIN US.',
    lede: '用邮箱开一个账号，把模板、批改、知识库与演讲收进同一张工作台。',
  },
  forgot: {
    eyebrow: 'Reset · 重置密码',
    title: 'RESET.',
    lede: '用邮箱验证码重新设置登录密码，两步即可回到工作台。',
  },
}

const FORM_KEYS = Object.keys(FORM_MAP)

/** /register 复用本页，初始态跟随路由名；页内切换不回写地址，避免路由与状态两份真相 */
const initialKey = FORM_KEYS.includes(route.name) ? route.name : 'login'
const formType = ref(initialKey)
/** 找回密码成功后带回的账号，用于登录表单预填 */
const prefillUsername = ref('')

const currentComponent = computed(() => FORM_MAP[formType.value])
const copy = computed(() => COPY[formType.value])
/** 忘记密码态由表单自带的「返回登录」收尾；登录/注册共用底部切换 */
const showSwitch = computed(() => formType.value !== 'forgot')

function switchForm(key) {
  if (FORM_KEYS.includes(key)) formType.value = key
}

function goForgot() {
  formType.value = 'forgot'
}

/** 找回密码成功或注册页点「去登录」：回登录态，有账号时预填 */
function onGoLogin(username) {
  prefillUsername.value = username || ''
  formType.value = 'login'
}

/**
 * 管理端与用户端共用本页：按登录响应里的权限决定落地页。
 * 管理员 / 审核员拥有 admin:dashboard，进管理端；其余进用户端。
 */
function onLoginSuccess(data) {
  // 账号可能已切换：清掉上一个账号残留的管理端会话缓存
  resetAdminSession()
  const target = resolveLoginDestination({
    permissions: Array.isArray(data?.permissions) ? data.permissions : [],
    redirect: route.query.redirect,
  })
  router.replace(target)
}

/** 开发预览：不请求后端，以演示数据进入管理端 */
function enterPreview() {
  if (!isDev || !enableAdminPreview()) return
  const redirect = isSafeRedirect(route.query.redirect) && route.query.redirect.startsWith('/admin')
    ? route.query.redirect
    : '/admin/dashboard'
  router.replace(redirect)
}
</script>

<template>
  <div class="login">
    <main class="login__frame">
      <section class="side">
        <header class="side__bar">
          <p class="brand">
            <span class="brand__mark" aria-hidden="true" />
            <span class="brand__word">TrueBuddy</span>
            <span class="brand__sub">教学智能体工作台</span>
          </p>

          <button
            type="button"
            class="page-appearance"
            role="switch"
            :aria-checked="isDark"
            :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
            :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
            @click="toggle"
          >
            <span class="page-appearance__icon" :class="{ 'is-shown': !isDark }">
              <el-icon><Sunny /></el-icon>
            </span>
            <span class="page-appearance__icon" :class="{ 'is-shown': isDark }">
              <el-icon><MoonNight /></el-icon>
            </span>
          </button>
        </header>

        <div class="side__intro" aria-live="polite">
          <p class="intro__eyebrow">{{ copy.eyebrow }}</p>
          <h2 class="intro__title">{{ copy.title }}</h2>
          <p class="intro__lede">{{ copy.lede }}</p>
        </div>

        <!-- 表单落在 1fr 行里：卡片总高由展台锚定，切换三态不再顶开版面 -->
        <div class="side__form" :class="{ 'side__form--register': formType === 'register' }">
          <div class="side__form__scroll">
            <Transition name="form-swap" mode="out-in">
              <component
                :is="currentComponent"
                :key="formType"
                v-bind="formType === 'login' ? { initialUsername: prefillUsername } : {}"
                @go-forgot="goForgot"
                @go-login="onGoLogin"
                @login-success="onLoginSuccess"
              />
            </Transition>
          </div>

          <button
            v-if="showSwitch"
            type="button"
            class="mode-switch"
            @click="switchForm(formType === 'register' ? 'login' : 'register')"
          >
            <span>{{ formType === 'register' ? '已经开过账号？直接登录' : '还没有账号？注册一个' }}</span>
            <el-icon><Right /></el-icon>
          </button>
        </div>

        <footer class="side__foot">
          <p v-if="isDev" class="preview-entry">
            <a role="button" tabindex="0" @click="enterPreview" @keyup.enter="enterPreview">
              以演示数据预览管理端
            </a>
          </p>
          <p class="side__meta">© 2026 TrueBuddy · 师范生教学练一体化平台</p>
        </footer>
      </section>

      <aside class="stage">
        <LoginShowcase />
      </aside>
    </main>
  </div>
</template>

<style scoped>
/* —— 环境层：与首页同一套纸面网格语言，卡片浮在其上 —— */
.login {
  --login-pad: clamp(var(--tb-size-3), 2.6vw, var(--tb-size-6));
  display: grid;
  place-items: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: var(--login-pad);
  background-color: var(--tb-paper-bg);
  background-image:
    linear-gradient(90deg, var(--tb-paper-line) 1px, transparent 1px),
    linear-gradient(var(--tb-paper-line) 1px, transparent 1px);
  background-size: var(--tb-paper-size) var(--tb-paper-size);
}

/* 一次性入场：只做透明度与极小位移，不遮挡首屏可读性（docs/09-样式规范.md §6.2 例外见 §6.4） */
.login__frame {
  display: grid;
  grid-template-columns: minmax(0, 46fr) minmax(0, 54fr);
  width: min(1800px, 100%);
  height: clamp(600px, calc(100dvh - var(--login-pad) * 2), 780px);
  overflow: hidden;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  background-color: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
  animation: frame-in var(--tb-dur-4) var(--tb-ease-out) both;
}

@keyframes frame-in {
  from {
    opacity: 0;
    transform: translate3d(0, 10px, 0);
  }
}

/* —— 左栏：四行网格，表单行吃掉剩余高度 —— */
.side {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: clamp(var(--tb-size-4), 4.2vh, var(--tb-size-6));
  min-width: 0;
  min-height: 0;
  padding: clamp(var(--tb-size-5), 3.4vw, var(--tb-size-7)) clamp(var(--tb-size-5), 3vw, var(--tb-size-7));
  background-color: var(--tb-bg-surface);
}

.side__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-4);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin: 0;
  min-width: 0;
}

.brand__mark {
  position: relative;
  flex: 0 0 auto;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--tb-brand-400), var(--tb-primary));
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--tb-primary) 16%, transparent);
}

.brand__word {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-lg);
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-tight);
  color: var(--tb-text-primary);
}

.brand__sub {
  padding-left: var(--tb-size-2);
  border-left: var(--tb-border-width) solid var(--tb-border);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  white-space: nowrap;
}

/* 明暗切换：随 NavbarItem 的既有语言，位置从「固定右上角」改为品牌行内，
   不再压在深色展台上，浅色态下也不会出现白块浮在暗底 */
.page-appearance {
  position: relative;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--tb-control-md);
  height: var(--tb-control-md);
  padding: 0;
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  cursor: pointer;
  /* hover 只换底色与描边，不做位移（与首页卡片同一套反馈语言） */
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease),
    color var(--tb-motion-dur) var(--tb-motion-ease);
}

.page-appearance:hover {
  background: var(--tb-primary-soft);
  border-color: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.page-appearance:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.page-appearance__icon {
  position: absolute;
  display: inline-flex;
  opacity: 0;
  transform: rotate(-35deg) scale(0.7);
  transition:
    opacity var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-motion-dur) var(--tb-ease-spring);
}

.page-appearance__icon.is-shown {
  opacity: 1;
  transform: rotate(0) scale(1);
}

/* —— 引导语 —— */
.intro__eyebrow {
  margin: 0 0 var(--tb-size-2);
  color: var(--tb-primary-text);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  text-transform: uppercase;
}

.intro__title {
  margin: 0;
  font-family: var(--tb-font-display);
  font-size: clamp(var(--tb-text-2xl), 3.4vw, var(--tb-text-3xl));
  font-weight: var(--tb-weight-display);
  line-height: var(--tb-leading-tight);
  letter-spacing: var(--tb-tracking-tight);
  color: var(--tb-text-primary);
}

.intro__lede {
  margin: var(--tb-size-3) 0 0;
  max-width: 30ch;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}

/* —— 表单槽 —— */
.side__form {
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-width: 440px;
}

/* 仅表单内容滚动，底部切换按钮始终保持可见 */
.side__form__scroll {
  flex: 0 1 auto;
  min-height: 0;
  overflow: auto;
  scrollbar-width: thin;
}

/* 注册表单字段更多，双列布局需要更宽的容器；登录/找回保持原宽度 */
.side__form--register {
  max-width: 560px;
}

/* 三态表单共用一套字段语言：标签在上、行距收紧、控件对齐刻度。
   子组件只保留各自的结构件，避免同一份排版在三个文件里各写一遍。 */
.side__form :deep(.el-form-item) {
  margin-bottom: var(--tb-size-4);
}

.side__form :deep(.el-form-item__label) {
  height: auto;
  padding: 0;
  margin-bottom: var(--tb-size-1);
  line-height: var(--tb-leading-snug);
  font-size: var(--tb-text-xs);
  font-weight: 500;
  letter-spacing: var(--tb-tracking-normal);
  color: var(--tb-text-secondary);
}

.side__form :deep(.el-input__wrapper) {
  border-radius: var(--tb-radius-md);
  background-color: var(--tb-bg-surface);
  box-shadow: 0 0 0 1px var(--tb-border-strong) inset;
  transition: box-shadow var(--tb-motion-dur) var(--tb-motion-ease);
}

.side__form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--tb-primary-border) inset;
}

.side__form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--tb-primary) inset,
    0 0 0 4px var(--tb-ring);
}

.side__form :deep(.el-input__inner) {
  font-size: var(--tb-text-md);
}

.side__form :deep(.el-input__prefix),
.side__form :deep(.el-input__suffix) {
  color: var(--tb-text-tertiary);
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.side__form :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--tb-primary-text);
}

.side__form :deep(.el-form-item__error) {
  /* EP 默认 line-height 让错误文案吃掉 15px，与下一个标签只剩 1px 缝；
     压到 1 并去掉 padding-top，让错误紧贴自己的字段，又不顶到下一个标签 */
  padding-top: 0;
  font-size: var(--tb-text-2xs);
  line-height: 1;
}

.side__form :deep(.el-checkbox__label) {
  font-size: var(--tb-text-sm);
  color: var(--tb-text-secondary);
}

.side__form :deep(.el-button) {
  border-radius: var(--tb-radius-md);
  font-weight: 500;
  letter-spacing: var(--tb-tracking-wide);
  transition:
    background-color var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease),
    box-shadow var(--tb-motion-dur) var(--tb-motion-ease);
}

.side__form :deep(.el-button--large) {
  min-height: var(--tb-control-lg);
}

/* 主 CTA 用品牌色投影抬高一级，hover 只把投影压深，不做位移 */
.side__form :deep(.el-button--primary:not(.is-link)) {
  box-shadow: var(--tb-shadow-brand);
}

.side__form :deep(.el-button--primary:not(.is-link):hover) {
  box-shadow: 0 12px 28px color-mix(in oklab, var(--tb-brand-600) 34%, transparent);
}

.form-swap-enter-active,
.form-swap-leave-active {
  transition:
    opacity var(--tb-dur-2) var(--tb-motion-ease),
    transform var(--tb-dur-3) var(--tb-ease-out);
}

.form-swap-enter-from,
.form-swap-leave-to {
  opacity: 0;
  transform: translate3d(0, 8px, 0);
}

/* —— 页脚 —— */
.side__foot {
  display: grid;
  gap: var(--tb-size-2);
}

.side__form > .mode-switch {
  flex: 0 0 auto;
  margin-top: var(--tb-size-1);
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  padding: var(--tb-size-1) 0;
  border: 0;
  background: none;
  color: var(--tb-text-secondary);
  font-family: inherit;
  font-size: var(--tb-text-sm);
  cursor: pointer;
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.mode-switch:hover {
  color: var(--tb-primary-text);
}

.mode-switch:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 3px;
  border-radius: var(--tb-radius-xs);
}

.preview-entry {
  margin: 0;
  font-size: var(--tb-text-2xs);
}

.preview-entry a {
  color: var(--tb-text-tertiary);
  cursor: pointer;
  text-decoration: none;
  border-bottom: 1px dashed var(--tb-border-strong);
  transition:
    color var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease);
}

.preview-entry a:hover {
  color: var(--tb-primary-text);
  border-bottom-color: var(--tb-primary-border);
}

.preview-entry a:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  border-radius: var(--tb-radius-xs);
}

.side__meta {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
}

/* —— 右栏：展台 —— */
.stage {
  /* 展台底色由页面按「全局」明暗决定：亮色主题走暖纸面 + 极淡墨蓝光晕，
     不再压一块夜蓝（Ink 主题下夜蓝与暖纸并列会显得突兀）；
     暗色主题收到深墨蓝，与页面底色连成一片。
     底色必须画在 .stage 上而不是 .showcase 上——后者起了 color-scheme: dark，
     light-dark() 在使用处解析，传进去的表达式会恒取暗支（实测踩到）。 */
  background-color: light-dark(var(--tb-paper-bg), #12141a);
  background-image:
    radial-gradient(
      120% 85% at 80% -15%,
      light-dark(color-mix(in oklab, var(--tb-primary) 9%, var(--tb-paper-bg)), #1f232d),
      transparent 62%
    ),
    linear-gradient(
      180deg,
      transparent 52%,
      light-dark(
        color-mix(in oklab, var(--tb-primary) 6%, #eae4d8),
        color-mix(in oklab, #12141a 70%, transparent)
      )
    );

  min-width: 0;
  min-height: 0;
}

/* —— 响应式：断点沿用项目既有取值（1024 / 768） —— */
@media (max-width: 1024px) {
  .login__frame {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    max-height: none;
  }

  /* 单栏时把左栏收成一条居中的阅读列，避免表单贴着 440px 上限、右边留大片空白 */
  .side {
    width: min(100%, 620px);
    margin-inline: auto;
    gap: var(--tb-size-5);
    padding: var(--tb-size-6);
  }

  .side__form {
    max-width: none;
    overflow: visible;
  }

  .side__form__scroll {
    overflow: visible;
  }
}

@media (max-width: 768px) {
  .login {
    --login-pad: var(--tb-size-3);
    align-items: start;
  }

  .login__frame {
    border-radius: var(--tb-radius-lg);
  }

  .side {
    padding: var(--tb-size-5) var(--tb-size-4);
    gap: var(--tb-size-4);
  }

  .brand__sub {
    display: none;
  }

  .intro__lede {
    font-size: var(--tb-text-md);
  }

  .page-appearance {
    /* 触摸目标放大到 --tb-control-lg */
    width: var(--tb-control-lg);
    height: var(--tb-control-lg);
  }

  .mode-switch {
    font-size: var(--tb-text-md);
  }

  /* 触摸优先：控件抬到 --tb-control-lg，输入字号到 16px 以免 iOS 聚焦时自动放大页面 */
  .side__form {
    max-width: none;
  }

  .side__form :deep(.el-input__wrapper) {
    min-height: var(--tb-control-lg);
  }

  .side__form :deep(.el-input__inner) {
    font-size: var(--tb-text-lg);
  }

  .side__form :deep(.el-form-item) {
    margin-bottom: var(--tb-size-5);
  }
}

/* 矮视口：先让位给表单，装饰与大字收敛 */
@media (max-height: 780px) and (min-width: 1025px) {
  .intro__lede {
    display: none;
  }

  .side {
    gap: var(--tb-size-4);
  }
}

@media (prefers-reduced-motion: reduce) {
  .login__frame {
    animation: none;
  }

  .page-appearance__icon,
  .page-appearance,
  .mode-switch {
    transition: none;
  }

  .form-swap-enter-active,
  .form-swap-leave-active {
    transition: opacity var(--tb-dur-1) linear;
  }

  .form-swap-enter-from,
  .form-swap-leave-to {
    transform: none;
  }
}
</style>
