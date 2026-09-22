<script setup>
import { MoonNight, Sunny } from '@element-plus/icons-vue'
import { useAppearance } from '@/composables/useAppearance'

/** 明暗态：写入 html[data-appearance]，全站 Token（src/assets/theme.css）随之切换 */
const { isDark, toggle } = useAppearance()
</script>

<template>
  <nav class="navbar">
    <div class="navbar__container">
      <div class="navbar__left">
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
        <RouterLink class="navbar__link" to="/docs">文档</RouterLink>
      </div>
      <div class="navbar__right">
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
      </div>
    </div>
  </nav>
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--tb-bg-surface);
  border-bottom: 1px solid var(--tb-border);
}

.navbar__container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
  height: 4rem;
}

.navbar__left {
  display: flex;
  align-items: center;
  gap: 2rem;
}

.navbar__brand {
  display: flex;
  align-items: center;
  text-decoration: none;
  /* logo 图形使用 currentColor，故随主题文字色变化 */
  color: var(--tb-text-primary);
}

.navbar__logo {
  height: 2.5rem;
  width: auto;
  margin-right: 0.5rem;
  flex: 0 0 auto;
}

.navbar__name {
  font-family: var(--tb-font-display);
  font-size: 1.25rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

.navbar__link {
  text-decoration: none;
  color: var(--tb-text-secondary);
  font-size: 0.95rem;
  font-weight: 500;
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.navbar__link:hover {
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
    box-shadow var(--tb-motion-dur) var(--tb-motion-ease);
}

.navbar__login:hover {
  background: var(--tb-primary-hover);
  box-shadow: var(--tb-shadow-brand);
}

@media (prefers-reduced-motion: reduce) {
  .navbar__theme-icon {
    transition: none;
  }
}
</style>
