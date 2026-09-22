<template>
  <button
    ref="rootRef"
    type="button"
    class="squircle-btn"
    :class="{ 'squircle-btn--block': block }"
    :style="rootStyle"
    :disabled="disabled"
    @pointerdown="onPointerDown"
    @pointerup="release"
    @pointerleave="release"
    @pointercancel="release"
    @lostpointercapture="release"
    @keydown.enter.prevent="press"
    @keydown.space.prevent="press"
    @keyup.enter.prevent="release"
    @keyup.space.prevent="release"
  >
    <svg
      class="squircle-btn__svg"
      :viewBox="`0 0 ${viewWidth} ${VIEW_HEIGHT}`"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <filter :id="shadowId" x="-100%" y="-100%" width="300%" height="300%">
          <feDropShadow
            :dy="shadowDy"
            :stdDeviation="shadowStd"
            :flood-color="shadowColor"
            :flood-opacity="SHADOW_OPACITY"
          />
        </filter>
        <linearGradient :id="gradientId">
          <stop offset="0" :stop-color="gradientEdge" />
          <stop :offset="gradientStop" :stop-color="gradientMid" />
          <stop :offset="1 - gradientStop" :stop-color="gradientMid" />
          <stop offset="1" :stop-color="gradientEdge" />
        </linearGradient>
      </defs>

      <!-- 落地投影 + 底座 -->
      <path :d="basePath" :fill="baseFill" :filter="`url(#${shadowId})`" />
      <!-- 侧壁主体 -->
      <path :d="basePath" :fill="wallFill" :stroke="wallStroke" stroke-width="1" />
      <!-- 侧壁渐变层：按下的瞬间逐层塌缩 -->
      <path v-for="(wallPath, index) in wallPaths" :key="index" :d="wallPath" :fill="`url(#${gradientId})`" />
      <!-- 顶面 -->
      <path :d="facePath" :fill="faceFill" :stroke="faceStroke" stroke-width="1.5" />
    </svg>

    <span class="squircle-btn__content">
      <slot />
    </span>
  </button>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/* ---------- 几何常量：与原始画布保持一致的 60 单位高度 ---------- */
const VIEW_HEIGHT = 60
const FACE_HEIGHT = 40
const BASE_Y = 12
const RADIUS = 18
const INSET = 5
const SHADOW_OPACITY = 0.3
/** 按下时顶面下沉的画布单位数 */
const PRESS_DEPTH = 5

/* ---------- 实例唯一 id（供 SVG defs 引用） ---------- */
let uidSeed = 0

const props = defineProps({
  /** 顶面颜色 */
  base: { type: String, default: '#3d7ab5' },
  /** 侧壁基色（通常比顶面暗一档） */
  dark: { type: String, default: '#2f5f8f' },
  /** 顶面高度（px），整体元素高度 = 该值 × 1.5 */
  height: { type: Number, default: 42 },
  /** 是否撑满父容器宽度 */
  block: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

const { shadowId, gradientId } = (() => {
  uidSeed += 1
  const uid = `squircle-${uidSeed}`
  return { shadowId: `${uid}-shadow`, gradientId: `${uid}-gradient` }
})()

/* ---------- 尺寸测量 ---------- */
const rootRef = ref(null)
const boxWidth = ref(0)
let observer = null

const scale = computed(() => props.height / FACE_HEIGHT)
/** 换算回画布单位：减去左右各 INSET 的投影留白 */
const unitWidth = computed(() => Math.max(24, boxWidth.value / scale.value - INSET * 2))
const viewWidth = computed(() => unitWidth.value + INSET * 2)

/* ---------- 按压状态：p ∈ {0, 1} ---------- */
const pressed = ref(false)
const depth = computed(() => (pressed.value ? 1 : 0))

const faceY = computed(() => 4 + depth.value * PRESS_DEPTH)
const gradientStop = computed(() => Math.min(0.5, 20 / unitWidth.value))

const basePath = computed(() =>
  squirclePath(unitWidth.value, FACE_HEIGHT, RADIUS, INSET, BASE_Y),
)
const facePath = computed(() =>
  squirclePath(unitWidth.value, FACE_HEIGHT, RADIUS, INSET, faceY.value),
)
const wallPaths = computed(() =>
  Array.from({ length: Math.max(0, BASE_Y - faceY.value) }, (_, index) =>
    squirclePath(unitWidth.value, FACE_HEIGHT, RADIUS, INSET, faceY.value + 1 + index),
  ),
)

/* ---------- 投影：按下时收紧 ---------- */
const shadowDy = computed(() => 4 - depth.value * 2)
const shadowStd = computed(() => 3 - depth.value * 1.5)

/* ---------- 配色：等价于 color-mix(in srgb, …) ---------- */
const shadowColor = computed(() => mixHex(props.dark, 35, 'black'))
const baseFill = computed(() => mixHex(props.dark, 60, 'black'))
const wallFill = computed(() => mixHex(props.dark, 80, 'black'))
const wallStroke = computed(() => mixHex(props.dark, 50, 'black'))
const gradientEdge = computed(() => mixHex(props.dark, 65, 'white'))
const gradientMid = computed(() => mixHex(props.dark, 90, 'white'))
const faceFill = computed(() => props.base)
const faceStroke = computed(() => mixHex(props.base, 70, 'white'))

const rootStyle = computed(() => ({
  height: `${VIEW_HEIGHT * scale.value}px`,
  '--sq-scale': String(scale.value),
  // 顶面下沉量：让文字与顶面同步下移
  '--sq-press': `${depth.value * PRESS_DEPTH * scale.value}px`,
}))

/* ---------- 交互 ---------- */
function press() {
  if (props.disabled) return
  pressed.value = true
}

function release() {
  pressed.value = false
}

function onPointerDown(event) {
  if (props.disabled) return
  press()
  // 捕获指针，保证拖出按钮后松开也能复位
  event.currentTarget?.setPointerCapture?.(event.pointerId)
}

function measure() {
  const el = rootRef.value
  if (!el) return
  const next = el.getBoundingClientRect().width
  if (next && Math.abs(next - boxWidth.value) > 0.5) boxWidth.value = next
}

watch(
  () => props.disabled,
  (value) => {
    if (value) release()
  },
)

onMounted(() => {
  if (!rootRef.value) return
  observer = new ResizeObserver(measure)
  observer.observe(rootRef.value)
  measure()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

/* ---------- 超椭圆（squircle）路径 ---------- */
function squirclePath(width, height, radius, offsetX, offsetY) {
  let path = ''
  for (let corner = 0; corner < 4; corner += 1) {
    for (let step = 0; step < 31; step += 1) {
      const angle = ((corner + step / 30) * Math.PI) / 2
      const cos = Math.cos(angle)
      const sin = Math.sin(angle)
      const pointX =
        offsetX +
        (cos > 0 ? width - radius : radius) +
        Math.sign(cos) * Math.abs(cos) ** 0.6 * radius
      const pointY =
        offsetY +
        (sin > 0 ? height - radius : radius) +
        Math.sign(sin) * Math.abs(sin) ** 0.6 * radius
      path += `${corner || step ? 'L' : 'M'}${pointX.toFixed(2)} ${pointY.toFixed(2)}`
    }
  }
  return `${path}Z`
}

/* ---------- 颜色混合（与 color-mix(in srgb, …) 结果一致） ---------- */
function normalizeHex(hex) {
  const raw = hex.replace('#', '').trim()
  if (raw.length === 3) {
    return raw
      .split('')
      .map((char) => char + char)
      .join('')
  }
  return raw.slice(0, 6)
}

function mixHex(hex, percent, target) {
  const value = Number.parseInt(normalizeHex(hex), 16)
  const base = [(value >> 16) & 255, (value >> 8) & 255, value & 255]
  const other = target === 'white' ? 255 : 0
  const ratio = percent / 100
  const [r, g, b] = base.map((channel) => Math.round(channel * ratio + other * (1 - ratio)))
  return `rgb(${r}, ${g}, ${b})`
}
</script>

<style scoped>
.squircle-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: opacity 0.2s ease, filter 0.2s ease;
}

.squircle-btn--block {
  width: 100%;
}

.squircle-btn__svg {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.squircle-btn__content {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--tb-on-primary);
  /* 顶面中心位于整个画布 40% 高度处，故整体上移 */
  margin-bottom: calc(12px * var(--sq-scale, 1));
  padding: 0 calc(18px * var(--sq-scale, 1));
  /* 按下时文字随顶面一起下沉，避免文字“悬浮”在压下去的顶面之上 */
  transform: translateY(var(--sq-press, 0px));
}

.squircle-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  filter: saturate(0.55);
}

.squircle-btn:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 4px;
  border-radius: var(--tb-radius-xl);
}
</style>
