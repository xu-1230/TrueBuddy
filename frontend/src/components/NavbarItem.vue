<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { MoonNight, Sunny } from '@element-plus/icons-vue'
import { useAppearance } from '@/composables/useAppearance'
import NavbarMenu from './navbar/NavbarMenu.vue'
import NavbarDrawer from './navbar/NavbarDrawer.vue'

/** 明暗态：写入 html[data-appearance]，全站 Token（src/assets/theme.css）随之切换 */
const { isDark, toggle } = useAppearance()

/** 越阈后顶栏收窄并渐显背景 / 边框；留一小段滚动距离，避免回顶瞬间来回抖动 */
const SCROLL_THRESHOLD = 24
/** 移动端断点，与样式中的媒体查询保持一致 */
const MOBILE_BREAKPOINT = 768

const scrolled = ref(false)
const menuOpen = ref(false)
const drawerOpen = ref(false)

/**
 * 滚动只切一个布尔态（越阈才触发一次组件更新），
 * 收窄 / 渐显全部交给 CSS transition，不做逐帧写样式。
 */
let rafId = 0
function onScroll() {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = 0
    scrolled.value = window.scrollY > SCROLL_THRESHOLD
  })
}

/** 放大到桌面宽度后自动收起抽屉，避免汉堡按钮消失而抽屉仍悬在页面上 */
function onResize() {
  if (drawerOpen.value && window.innerWidth > MOBILE_BREAKPOINT) drawerOpen.value = false
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onResize)
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<template>
  <nav class="navbar" :class="{ 'is-scrolled': scrolled, 'is-menu-open': menuOpen }">
    <div class="navbar__container">
      <RouterLink class="navbar__brand" to="/guides" aria-label="TrueBuddy 首页">
        <svg class="navbar__logo" viewBox="0 0 120 120" aria-hidden="true">
          <rect x="24" y="37" width="16" height="62" fill="currentColor" />
          <line x1="24" y1="44" x2="40" y2="44" stroke="var(--tb-bg-surface)" stroke-width="3" />
          <rect x="46" y="22" width="16" height="74" fill="none" stroke="currentColor" stroke-width="6" />
          <rect x="68" y="47" width="16" height="52" fill="currentColor" />
          <line x1="67" y1="55" x2="85" y2="55" stroke="var(--tb-bg-surface)" stroke-width="3" />
          <rect x="90" y="28" width="16" height="68" fill="none" stroke="currentColor" stroke-width="6" />
        </svg>
        <span class="navbar__name">TrueBuddy</span>
      </RouterLink>

      <div class="navbar__right">
        <!-- 一级菜单：悬停展开多列面板（≤768px 自行隐藏） -->
        <NavbarMenu class="navbar__menu" @open="menuOpen = $event" />

        <button
          class="navbar__theme"
          type="button"
          role="switch"
          :aria-checked="isDark"
          :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
          :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
          @click="toggle"
        >
          <span class="navbar__theme-icon" :class="{ 'is-shown': !isDark }">
            <el-icon :size="18"><Sunny /></el-icon>
          </span>
          <span class="navbar__theme-icon" :class="{ 'is-shown': isDark }">
            <el-icon :size="18"><MoonNight /></el-icon>
          </span>
        </button>

        <RouterLink class="navbar__login" to="/login">登录</RouterLink>

        <button
          class="navbar__burger"
          type="button"
          :aria-expanded="drawerOpen"
          aria-label="打开导航菜单"
          @click="drawerOpen = true"
        >
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
    </div>

    <NavbarDrawer v-model="drawerOpen" />
  </nav>
</template>

<style scoped>
.navbar {
  /* 容器内边距：同时作为 Mega 面板的右对齐基准（跨组件经 CSS 变量继承） */
  --navbar-pad: var(--tb-size-5);
  /* fixed 而非 sticky：顶栏不占文档流，首屏装饰背景得以铺到页面顶端，
     否则导航条会在 hero 顶部把背景硬裁一刀，留下一条断开的白带。
     代价：本组件用在首屏非装饰性 hero 的页面时，需自行留出顶部间距。 */
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 100;
  /* 未滚动：完全透明，首屏视觉直达；滚动越阈后才渐显玻璃底与描边 */
  background: transparent;
  border-bottom: 1px solid transparent;
  transition:
    background var(--tb-dur-4) var(--tb-ease-out),
    border-color var(--tb-dur-4) var(--tb-ease-out),
    box-shadow var(--tb-dur-4) var(--tb-ease-out);
}

.navbar.is-scrolled {
  /* 半透明 + 背景虚化：内容从导航条下穿过时保留层次，不遮挡阅读 */
  background: color-mix(in oklab, var(--tb-bg-surface) 86%, transparent);
  -webkit-backdrop-filter: blur(14px) saturate(1.5);
  backdrop-filter: blur(14px) saturate(1.5);
  border-bottom-color: var(--tb-border);
  box-shadow: var(--tb-shadow-1);
}

/* 菜单展开时抬升层级，避免面板被页面右侧章节导航（z-index: 200）压住 */
.navbar.is-menu-open {
  z-index: 300;
}

/* 不支持背景虚化的内核退回不透明底色，避免文字叠字 */
@supports not ((backdrop-filter: blur(2px)) or (-webkit-backdrop-filter: blur(2px))) {
  .navbar.is-scrolled {
    background: var(--tb-bg-surface);
  }
}

.navbar__container {
  position: relative; /* Mega 面板的定位基准：随容器收窄而移动 */
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1700px;
  margin: 0 auto;
  padding: 0 var(--navbar-pad);
  height: 4rem;
  transition:
    max-width var(--tb-dur-4) var(--tb-ease-out),
    height var(--tb-dur-4) var(--tb-ease-out);
}

/* 滚动收窄：一次离散状态切换，由 transition 完成，非逐帧动画 */
.navbar.is-scrolled .navbar__container {
  max-width: 1600px;
  height: 3.5rem;
}

.navbar__brand {
  display: flex;
  align-items: center;
  text-decoration: none;
  /* logo 图形使用 currentColor，故随主题文字色变化 */
  color: var(--tb-text-primary);
  transition: opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.navbar__brand:hover {
  opacity: 0.72;
}

.navbar__brand:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 4px;
  border-radius: var(--tb-radius-sm);
}

.navbar__logo {
  height: 2.5rem;
  width: auto;
  margin-right: 0.5rem;
  flex: 0 0 auto;
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

.navbar__brand:hover .navbar__logo {
  transform: scale(1.06);
}

.navbar__name {
  font-family: var(--tb-font-display);
  font-size: 1.6rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

.navbar__right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

/* 明暗切换：两枚图标叠放交叉淡入，避免按钮宽度跳动 */
.navbar__theme {
  position: relative;
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
  transition:
    color var(--tb-motion-dur) var(--tb-motion-ease),
    background var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease);
}

.navbar__theme:hover {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border-color: var(--tb-primary-border);
}

.navbar__theme:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.navbar__theme-icon {
  position: absolute;
  display: inline-flex;
  opacity: 0;
  transform: rotate(-35deg) scale(0.7);
  transition:
    opacity var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-motion-dur) var(--tb-ease-spring);
}

.navbar__theme-icon.is-shown {
  opacity: 1;
  transform: rotate(0) scale(1);
}

.navbar__login {
  display: inline-flex;
  align-items: center;
  height: var(--tb-control-md);
  text-decoration: none;
  padding: 0 1.25rem;
  border-radius: var(--tb-radius-full);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    box-shadow var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-dur-2) var(--tb-ease-out);
}

.navbar__login:hover {
  background: var(--tb-primary-hover);
  box-shadow: var(--tb-shadow-brand);
  transform: translateY(-1px);
}

.navbar__login:active {
  transform: translateY(0);
}

.navbar__login:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 汉堡按钮：仅移动端出现 */
.navbar__burger {
  display: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: var(--tb-control-md);
  height: var(--tb-control-md);
  padding: 0;
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  cursor: pointer;
  transition:
    border-color var(--tb-motion-dur) var(--tb-motion-ease),
    background var(--tb-motion-dur) var(--tb-motion-ease);
}

.navbar__burger span {
  display: block;
  width: 16px;
  height: 1.5px;
  border-radius: 1px;
  background: var(--tb-text-secondary);
  transition: background var(--tb-motion-dur) var(--tb-motion-ease);
}

.navbar__burger:hover {
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.navbar__burger:hover span {
  background: var(--tb-primary-text);
}

.navbar__burger:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .navbar__login {
    display: none;
  }

  .navbar__burger {
    display: inline-flex;
  }
}

@media (prefers-reduced-motion: reduce) {
  .navbar,
  .navbar__container,
  .navbar__brand,
  .navbar__logo,
  .navbar__theme,
  .navbar__theme-icon,
  .navbar__login,
  .navbar__burger,
  .navbar__burger span {
    transition: none;
  }

  .navbar__login:hover {
    transform: none;
  }
}
</style>
