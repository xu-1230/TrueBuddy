<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { MENU_TABS, MENUS } from './menuData'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const closeBtnRef = ref(null)
/** 手风琴当前展开的栏目，默认展开第一组 */
const expanded = ref(MENU_TABS[0]?.id ?? null)

function toggleGroup(id) {
  expanded.value = expanded.value === id ? null : id
}

function close() {
  visible.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape') close()
}

/* ---------- 进出场：遮罩淡入 + 面板自右侧滑入 ---------- */
function onEnter(el, done) {
  const mask = el.querySelector('.drawer__mask')
  const panel = el.querySelector('.drawer__panel')

  if (reduceMotion) {
    gsap.set([mask, panel], { opacity: 1, xPercent: 0 })
    done()
    return
  }

  gsap
    .timeline({ onComplete: done })
    .fromTo(mask, { opacity: 0 }, { opacity: 1, duration: 0.25, ease: 'power2.out' }, 0)
    .fromTo(panel, { xPercent: 100 }, { xPercent: 0, duration: 0.4, ease: 'power3.out' }, 0)
}

function onLeave(el, done) {
  const mask = el.querySelector('.drawer__mask')
  const panel = el.querySelector('.drawer__panel')

  if (reduceMotion) {
    done()
    return
  }

  gsap
    .timeline({ onComplete: done })
    .to(panel, { xPercent: 100, duration: 0.28, ease: 'power3.in' }, 0)
    .to(mask, { opacity: 0, duration: 0.24, ease: 'power2.in' }, 0.04)
}

/* ---------- 打开时锁定页面滚动并接管键盘 / 焦点 ---------- */
watch(visible, (open) => {
  if (open) {
    document.addEventListener('keydown', onKeydown)
    // 锁定页面滚动；Lenis 的滚动被不可滚动的文档天然拦住
    document.documentElement.style.overflow = 'hidden'
    nextTick(() => closeBtnRef.value?.focus())
  } else {
    document.removeEventListener('keydown', onKeydown)
    document.documentElement.style.overflow = ''
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.documentElement.style.overflow = ''
})
</script>

<template>
  <!-- Teleport 到 body：脱离 .navbar 的层叠上下文，避免被页面浮层（进度条 / 章节导航）压住 -->
  <Teleport to="body">
    <Transition :css="false" @enter="onEnter" @leave="onLeave">
      <div v-if="visible" class="drawer" role="dialog" aria-modal="true" aria-label="站点导航">
        <div class="drawer__mask" @click="close"></div>

        <!-- data-lenis-prevent：让面板内的滚轮滚动交还浏览器，不被平滑滚动接管 -->
        <div class="drawer__panel" data-lenis-prevent>
          <header class="drawer__head">
            <span class="drawer__brand">TrueBuddy</span>
            <button
              ref="closeBtnRef"
              class="drawer__close"
              type="button"
              aria-label="关闭导航"
              @click="close"
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path
                  d="M3 3l10 10M13 3L3 13"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </header>

          <div class="drawer__body">
            <section v-for="tab in MENU_TABS" :key="tab.id" class="acc">
              <button
                class="acc__head"
                type="button"
                :aria-expanded="expanded === tab.id"
                @click="toggleGroup(tab.id)"
              >
                <span>{{ tab.label }}</span>
                <svg
                  class="acc__chevron"
                  :class="{ 'is-open': expanded === tab.id }"
                  width="10"
                  height="6"
                  viewBox="0 0 10 6"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M1 1l4 4 4-4"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>

              <Transition name="acc">
                <div v-if="expanded === tab.id" class="acc__body">
                  <div v-for="column in MENUS[tab.id]" :key="column.title" class="acc__group">
                    <div class="acc__title">{{ column.title }}</div>
                    <RouterLink
                      v-for="item in column.items"
                      :key="item.label"
                      class="acc__link"
                      :to="item.to"
                      @click="close"
                    >
                      {{ item.label }}
                    </RouterLink>
                  </div>
                </div>
              </Transition>
            </section>
          </div>

          <footer class="drawer__foot">
            <RouterLink class="drawer__btn drawer__btn--primary" to="/login" @click="close">
              登录
            </RouterLink>
            <RouterLink class="drawer__btn" to="/register" @click="close">注册账号</RouterLink>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer {
  position: fixed;
  inset: 0;
  z-index: 400;
}

.drawer__mask {
  position: absolute;
  inset: 0;
  background: var(--tb-overlay);
}

.drawer__panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  width: min(88vw, 360px);
  background: var(--tb-bg-surface);
  border-left: 1px solid var(--tb-border);
  box-shadow: var(--tb-shadow-4);
  will-change: transform;
}

.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  height: 3.5rem;
  padding: 0 1.25rem;
  border-bottom: 1px solid var(--tb-border);
}

.drawer__brand {
  font-family: var(--tb-font-display);
  font-size: 1.1rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

.drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--tb-control-sm);
  height: var(--tb-control-sm);
  padding: 0;
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease);
}

.drawer__close:hover {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
}

.drawer__close:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 面板内滚动区域：允许超长菜单滚动，不撑破视口 */
.drawer__body {
  flex: 1 1 auto;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--tb-size-4) 1.25rem;
}

.acc + .acc {
  border-top: 1px solid var(--tb-border);
}

.acc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.9rem 0;
  border: none;
  background: none;
  font-size: 0.95rem;
  font-weight: 600;
  font-family: inherit;
  color: var(--tb-text-primary);
  cursor: pointer;
}

.acc__chevron {
  color: var(--tb-text-tertiary);
  transition: transform var(--tb-dur-3) var(--tb-ease-out);
}

.acc__chevron.is-open {
  transform: rotate(180deg);
}

.acc__body {
  padding-bottom: var(--tb-size-4);
}

.acc__group + .acc__group {
  margin-top: var(--tb-size-4);
}

.acc__title {
  padding-bottom: var(--tb-size-2);
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
  text-transform: uppercase;
  color: var(--tb-text-tertiary);
}

.acc__link {
  display: block;
  padding: 0.5rem 0;
  font-size: 0.875rem;
  color: var(--tb-text-secondary);
  text-decoration: none;
}

.acc__link:hover,
.acc__link:focus-visible {
  color: var(--tb-primary-text);
}

.acc__link:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  border-radius: var(--tb-radius-xs);
}

.drawer__foot {
  display: flex;
  flex: 0 0 auto;
  gap: var(--tb-size-3);
  padding: 1rem 1.25rem calc(1rem + env(safe-area-inset-bottom));
  border-top: 1px solid var(--tb-border);
}

.drawer__btn {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: var(--tb-control-lg);
  border: 1px solid var(--tb-border-strong);
  border-radius: var(--tb-radius-sm);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  text-decoration: none;
}

.drawer__btn--primary {
  border-color: var(--tb-primary);
  background: var(--tb-primary);
  color: var(--tb-on-primary);
}

.acc-enter-active,
.acc-leave-active {
  transition:
    opacity var(--tb-dur-3) var(--tb-ease-out),
    transform var(--tb-dur-3) var(--tb-ease-out);
}

.acc-enter-from,
.acc-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (prefers-reduced-motion: reduce) {
  .acc__chevron,
  .acc-enter-active,
  .acc-leave-active,
  .drawer__close {
    transition: none;
  }
}
</style>
