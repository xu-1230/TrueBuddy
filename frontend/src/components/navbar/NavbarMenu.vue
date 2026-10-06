<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gsap } from 'gsap'
import { killSprings, springTo } from '@/lib/springTo'
import { MENU_TABS, MENUS } from './menuData'

/** 展开 / 收起时通知外壳（用于临时提升导航层级，避免被页面浮层压住） */
const emit = defineEmits(['open'])

/* ---------- 动效常量 ---------- */
const COLUMN_STAGGER = 0.04
const PANEL_OFFSET = -8
const GRID_OFFSET = 40
const COLUMN_OFFSET = 8
/** 指针穿越「一级项 → 面板」之间空隙的容错时长（ms） */
const CLOSE_DELAY = 140

/** 「减少动效」偏好：开启时所有弹簧退化为直接显隐 */
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const activeId = ref(null)
const direction = ref(0)

const rootRef = ref(null)
const navRef = ref(null)
const indicatorRef = ref(null)
const buttonRefs = []
const chevronRefs = []
const chevronStates = MENU_TABS.map(() => ({ rotate: 0 }))
const indicatorState = { x: 0, y: 0, width: 0, height: 0 }

/** 同一元素同一时刻只跑一个弹簧，新动画打断旧的 */
const handles = new WeakMap()
function runSpring(el, ...args) {
  handles.get(el)?.kill()
  const handle = springTo(...args)
  handles.set(el, handle)
  return handle
}

let previousActiveId = null
let closeTimer = 0

function cancelClose() {
  if (closeTimer) {
    clearTimeout(closeTimer)
    closeTimer = 0
  }
}

/** 延迟收起：让指针有时间从一级项滑入面板 */
function scheduleClose() {
  if (closeTimer || activeId.value === null) return
  closeTimer = setTimeout(() => {
    closeTimer = 0
    close()
  }, CLOSE_DELAY)
}

function setActive(id) {
  if (activeId.value === id) return
  if (activeId.value !== null) {
    const from = MENU_TABS.findIndex((tab) => tab.id === activeId.value)
    const to = MENU_TABS.findIndex((tab) => tab.id === id)
    direction.value = to > from ? 1 : -1
  } else {
    direction.value = 0
  }
  activeId.value = id
  emit('open', true)
}

function close() {
  cancelClose()
  if (activeId.value === null) return
  direction.value = 0
  activeId.value = null
  emit('open', false)
}

/* ---------- 共享指示器：单个胶囊跟随激活项 ---------- */
function applyIndicator() {
  if (!indicatorRef.value) return
  gsap.set(indicatorRef.value, {
    x: indicatorState.x,
    y: indicatorState.y,
    width: indicatorState.width,
    height: indicatorState.height,
  })
}

function syncIndicator(animate) {
  const nav = navRef.value
  if (!nav || !activeId.value) return

  const index = MENU_TABS.findIndex((tab) => tab.id === activeId.value)
  const button = buttonRefs[index]
  if (!button) return

  const navRect = nav.getBoundingClientRect()
  const rect = button.getBoundingClientRect()
  // 内缩 2px：指示器是按钮外一层薄壳，避免与按钮自身边界贴死
  const target = {
    x: rect.left - navRect.left - nav.clientLeft + 2,
    y: rect.top - navRect.top - nav.clientTop + 2,
    width: rect.width - 4,
    height: rect.height - 4,
  }

  if (!animate || reduceMotion) {
    Object.assign(indicatorState, target)
    applyIndicator()
    return
  }

  runSpring(indicatorRef.value, indicatorState, target, {
    stiffness: 500,
    damping: 35,
    onUpdate: applyIndicator,
  })
}

/* ---------- 面板进出场 ---------- */
function onPanelEnter(el, done) {
  if (reduceMotion) {
    gsap.set(el, { opacity: 1, y: 0 })
    done()
    return
  }
  const state = { opacity: 0, y: PANEL_OFFSET }
  gsap.set(el, { opacity: 0, y: PANEL_OFFSET })
  runSpring(el, state, { opacity: 1, y: 0 }, {
    stiffness: 500,
    damping: 35,
    onUpdate: (s) => gsap.set(el, { opacity: s.opacity, y: s.y }),
    onComplete: done,
  })
}

function onPanelLeave(el, done) {
  handles.get(el)?.kill()
  if (reduceMotion) {
    done()
    return
  }
  gsap.to(el, { opacity: 0, y: PANEL_OFFSET, duration: 0.15, ease: 'power1.out', onComplete: done })
}

/* ---------- 网格切换：按切换方向滑入 / 滑出 ---------- */
function onGridEnter(el, done) {
  const dir = direction.value
  const columns = el.querySelectorAll('.mega__column')

  if (reduceMotion) {
    gsap.set([el, ...columns], { opacity: 1, x: 0, y: 0 })
    done()
    return
  }

  const state = { opacity: 0, x: dir ? dir * GRID_OFFSET : 0 }
  gsap.set(el, { opacity: 0, x: state.x })

  runSpring(el, state, { opacity: 1, x: 0 }, {
    stiffness: 300,
    damping: 30,
    onUpdate: (s) => gsap.set(el, { opacity: s.opacity, x: s.x }),
    onComplete: done,
  })

  // 列：首次展开自下方升起并逐列错峰，切换栏目时纯淡入（纯淡入更稳，避免双层位移打架）
  columns.forEach((column, index) => {
    const columnState = { opacity: 0, y: dir ? 0 : COLUMN_OFFSET }
    gsap.set(column, { opacity: 0, y: columnState.y })
    runSpring(column, columnState, { opacity: 1, y: 0 }, {
      stiffness: 400,
      damping: 30,
      delay: index * COLUMN_STAGGER,
      onUpdate: (s) => gsap.set(column, { opacity: s.opacity, y: s.y }),
    })
  })
}

function onGridLeave(el, done) {
  // 面板整体已在退场（activeId 置空）时无需再做网格退场
  if (!activeId.value || reduceMotion) {
    done()
    return
  }

  const dir = direction.value
  const parent = el.parentElement
  const rect = el.getBoundingClientRect()
  const parentRect = parent.getBoundingClientRect()

  // 模拟 popLayout：把退场网格脱离文档流，让新网格立刻占位，避免高度跳动
  gsap.set(el, {
    position: 'absolute',
    top: rect.top - parentRect.top - parent.clientTop,
    left: rect.left - parentRect.left - parent.clientLeft,
    width: rect.width,
    margin: 0,
  })

  const state = { opacity: 1, x: 0 }
  runSpring(el, state, { opacity: 0, x: dir ? dir * -GRID_OFFSET : 0 }, {
    stiffness: 300,
    damping: 30,
    onUpdate: (s) => gsap.set(el, { opacity: s.opacity, x: s.x }),
    onComplete: done,
  })
}

/* ---------- 激活态联动：指示器 + 箭头 ---------- */
watch(
  activeId,
  (id) => {
    const shouldAnimate = previousActiveId !== null && id !== null
    previousActiveId = id

    if (id) syncIndicator(shouldAnimate)

    MENU_TABS.forEach((tab, index) => {
      const svg = chevronRefs[index]
      if (!svg) return
      runSpring(svg, chevronStates[index], { rotate: tab.id === id ? 180 : 0 }, {
        stiffness: 500,
        damping: 30,
        onUpdate: (s) => gsap.set(svg, { rotate: s.rotate }),
      })
    })
  },
  { flush: 'post' },
)

/* ---------- 全局监听 ---------- */
function onResize() {
  syncIndicator(false)
}

/** 点击导航区之外收起（覆盖触屏：触屏没有 hover，靠点击展开 + 点击外部收起） */
function onDocumentPointerDown(event) {
  if (activeId.value === null) return
  if (rootRef.value && !rootRef.value.contains(event.target)) close()
}

function onKeydown(event) {
  if (event.key === 'Escape') close()
}

/** 键盘 Tab 离开整个组件时收起 */
function onFocusOut(event) {
  if (!rootRef.value) return
  const next = event.relatedTarget
  if (next && rootRef.value.contains(next)) return
  close()
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
  // 字体异步加载完成后按真实度量重新对齐，避免按回退字体定位
  document.fonts?.ready.then(() => syncIndicator(false))
})

onBeforeUnmount(() => {
  cancelClose()
  window.removeEventListener('resize', onResize)
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
  killSprings()
})
</script>

<template>
  <div ref="rootRef" class="mega" @mouseenter="cancelClose" @mouseleave="scheduleClose" @focusout="onFocusOut">
    <nav ref="navRef" class="mega__tabs" aria-label="主导航">
      <div v-if="activeId" ref="indicatorRef" class="mega__indicator" aria-hidden="true"></div>

      <button
        v-for="(tab, index) in MENU_TABS"
        :key="tab.id"
        :ref="(el) => (buttonRefs[index] = el)"
        type="button"
        class="mega__tab"
        :class="{ 'is-active': activeId === tab.id }"
        aria-haspopup="true"
        :aria-expanded="activeId === tab.id"
        @mouseenter="setActive(tab.id)"
        @focus="setActive(tab.id)"
        @click="setActive(tab.id)"
      >
        <span class="mega__label">
          {{ tab.label }}
          <svg
            :ref="(el) => (chevronRefs[index] = el)"
            class="mega__chevron"
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
        </span>
      </button>
    </nav>

    <Transition :css="false" @enter="onPanelEnter" @leave="onPanelLeave">
      <div v-if="activeId" class="mega__panel">
        <div class="mega__surface">
          <!-- appear：首次展开时也走一遍网格进场（列错峰自下升起），否则首开无动效 -->
          <Transition :css="false" appear @enter="onGridEnter" @leave="onGridLeave">
            <div :key="activeId" class="mega__grid">
              <div v-for="column in MENUS[activeId]" :key="column.title" class="mega__column">
                <div class="mega__column-title">{{ column.title }}</div>
                <RouterLink
                  v-for="item in column.items"
                  :key="item.label"
                  class="mega__link"
                  :to="item.to"
                  @click="close"
                >
                  {{ item.label }}
                </RouterLink>
              </div>
            </div>
          </Transition>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.mega {
  /* 不设 position：面板的定位基准交给 .navbar__container，使面板宽度随手收窄而不错位 */
  display: flex;
  align-items: center;
  height: 100%;
}

/* ===== 一级项 ===== */
.mega__tabs {
  position: relative;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px;
}

.mega__indicator {
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 0;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-primary-soft);
  border: 1px solid var(--tb-primary-border);
  pointer-events: none;
}

.mega__tab {
  position: relative;
  padding: 0.5rem 0.75rem;
  border: none;
  background: none;
  border-radius: var(--tb-radius-sm);
  font-size: 0.9rem;
  font-weight: 500;
  font-family: inherit;
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.mega__tab:hover,
.mega__tab.is-active {
  color: var(--tb-text-primary);
}

.mega__tab:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.mega__label {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
}

.mega__chevron {
  margin-left: 6px;
  /* 箭头旋转由 GSAP 写 transform，这里只声明颜色继承 */
  color: currentColor;
  flex: 0 0 auto;
}

/* ===== 面板 ===== */
.mega__panel {
  position: absolute;
  top: 100%;
  right: var(--navbar-pad, var(--tb-size-5));
  /* 宽度基准为导航容器，窄屏自动收窄，不会向视口左侧溢出 */
  width: min(720px, calc(100% - 2 * var(--navbar-pad, var(--tb-size-5))));
  /* 透明搭桥：指针可从一级项滑入面板而不脱离 hover 区 */
  padding-top: 10px;
  will-change: transform, opacity;
}

.mega__surface {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
}

.mega__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--tb-size-6);
  padding: var(--tb-size-6);
  will-change: transform, opacity;
}

.mega__column {
  will-change: transform, opacity;
}

.mega__column-title {
  padding: 0 0.625rem 0.75rem;
  font-family: var(--font-mono);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
  text-transform: uppercase;
  color: var(--tb-text-tertiary);
}

.mega__link {
  display: block;
  padding: 0.5rem 0.625rem;
  border-radius: var(--tb-radius-sm);
  font-size: 0.875rem;
  color: var(--tb-text-secondary);
  text-decoration: none;
  transition:
    background var(--tb-dur-2) var(--tb-motion-ease),
    color var(--tb-dur-2) var(--tb-motion-ease);
}

.mega__link:hover,
.mega__link:focus-visible {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.mega__link:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: -2px;
}

@media (max-width: 768px) {
  .mega {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .mega__tab,
  .mega__link {
    transition: none;
  }
}
</style>
