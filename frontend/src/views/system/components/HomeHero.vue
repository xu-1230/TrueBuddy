<template>
  <section class="hero" aria-label="平台入口">
    <!-- A1 主推卡 -->
    <button class="hero__feature" type="button" @click="go(PROMO_MAIN.to)">
      <span class="hero__feature-top">
        <span>
          <span class="hero__eyebrow">{{ PROMO_MAIN.eyebrow }}</span>
          <span class="hero__feature-title">{{ PROMO_MAIN.title }}</span>
          <span class="hero__feature-desc">{{ PROMO_MAIN.description }}</span>
        </span>
        <span class="hero__round">
          <el-icon :size="18"><ArrowRight /></el-icon>
        </span>
      </span>

      <!-- 拼贴：三张倾斜小卡，与 ShowcaseCard 共用同一套版式语言 -->
      <span class="hero__collage">
        <span class="hero__chip hero__chip--a"></span>
        <span class="hero__chip hero__chip--b"></span>
        <span class="hero__chip hero__chip--c"></span>
      </span>
    </button>

    <!-- A2 双入口 -->
    <span class="hero__entries">
      <button
        v-for="entry in PROMO_ENTRIES"
        :key="entry.id"
        class="hero__entry"
        type="button"
        @click="go(entry.to)"
      >
        <!-- 位图打底：与 A3 轮播同一套「图—染—压」三层处理，文字压左侧留白区 -->
        <span class="hero__entry-media" aria-hidden="true">
          <img class="hero__entry-img" :src="entry.image" alt="" decoding="async" />
          <span class="hero__entry-tint"></span>
          <span class="hero__entry-scrim"></span>
          <span class="hero__entry-grain"></span>
        </span>

        <span class="hero__entry-text">
          <span class="hero__entry-title">{{ entry.title }}</span>
          <span class="hero__pill">
            <el-icon :size="14"><ArrowRight /></el-icon>
            <span>{{ entry.description }}</span>
          </span>
        </span>
        <span class="hero__thumb" :class="`is-${entry.pattern}`">
          <el-icon :size="22"><component :is="entry.icon" /></el-icon>
        </span>
      </button>
    </span>

    <!-- A3 运营轮播：位图打底、文字压左侧、底部分段进度 -->
    <span
      ref="carouselEl"
      class="hero__carousel"
      role="region"
      aria-roledescription="轮播"
      aria-label="平台运营位"
      @mouseenter="hovering = true"
      @mouseleave="hovering = false"
      @focusin="onFocusIn"
      @focusout="onFocusOut"
      @pointermove="trackPointer"
      @pointerleave="resetPointer"
      @click="go(current.to)"
    >
      <!-- 三张图常驻叠放：靠 is-active 交叉淡入，切图不重建 DOM，推镜不会半途归位 -->
      <span class="hero__media" aria-hidden="true">
        <span
          v-for="(slide, index) in slides"
          :key="slide.id"
          class="hero__frame"
          :class="{ 'is-active': index === active }"
        >
          <img class="hero__img" :src="slide.image" alt="" decoding="async" />
        </span>
        <span class="hero__tint"></span>
        <span class="hero__scrim"></span>
        <span class="hero__grain"></span>
      </span>

      <!-- 序号：等宽数字，切换时不抖动 -->
      <span class="hero__count" aria-hidden="true">
        <span class="hero__count-now">{{ pad(active + 1) }}</span>
        <span class="hero__count-rule"></span>
        <span class="hero__count-all">{{ pad(slides.length) }}</span>
      </span>

      <!-- 文案：换 id 即重挂元素，逐行上浮的入场动画随之重播 -->
      <span
        id="hero-panel"
        :key="current.id"
        class="hero__slide-body"
        role="tabpanel"
        :aria-labelledby="`hero-dot-${current.id}`"
      >
        <span class="hero__eyebrow">{{ current.eyebrow }}</span>
        <span class="hero__slide-title">{{ current.title }}</span>
        <span class="hero__slide-desc">{{ current.description }}</span>
        <button class="hero__cta" type="button" @click.stop="go(current.to)">
          <span>{{ current.cta }}</span>
          <el-icon :size="14"><ArrowRight /></el-icon>
        </button>
      </span>

      <!-- 分段进度：轨道靠 transform 撑满（不改 width），与自动切换共用同一份时长 -->
      <span class="hero__rail" role="tablist" aria-label="运营位切换">
        <button
          v-for="(slide, index) in slides"
          :id="`hero-dot-${slide.id}`"
          :key="slide.id"
          class="hero__tab"
          :class="{ 'is-active': index === active }"
          type="button"
          role="tab"
          :aria-selected="index === active"
          aria-controls="hero-panel"
          :tabindex="index === active ? 0 : -1"
          :aria-label="`${slide.eyebrow}：${slide.title}`"
          @click.stop="select(index)"
          @keydown="onTabKeydown($event, index)"
        >
          <span class="hero__tab-no">{{ pad(index + 1) }}</span>
          <span class="hero__tab-track">
            <i :key="lap" :class="{ 'is-run': !prefersReduced && !paused }"></i>
          </span>
        </button>
      </span>
    </span>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { CAROUSEL_INTERVAL, CAROUSEL_SLIDES, PROMO_ENTRIES, PROMO_MAIN } from '../constants'

const router = useRouter()
const slides = CAROUSEL_SLIDES
const active = ref(0)
const carouselEl = ref(null)

/** 三个暂停来源分开记：鼠标移出不该解除键盘焦点的暂停，反之亦然 */
const hovering = ref(false)
const focusing = ref(false)
const pageHidden = ref(false)

/** 每次「重新计时」都自增，进度条靠它重建元素来从头播放，与 setTimeout 共用一个起点 */
const lap = ref(0)

const current = computed(() => slides[active.value])
/** 自动轮播必须可暂停（WCAG 2.2.2），悬停、焦点、切后台都算 */
const paused = computed(() => hovering.value || focusing.value || pageHidden.value)

const prefersReduced =
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true

const finePointer =
  typeof window !== 'undefined' && window.matchMedia?.('(pointer: fine)').matches === true

/** 进度条与推镜的时长直接由 CAROUSEL_INTERVAL 注入，避免脚本与样式各写一份 */
const intervalCss = `${CAROUSEL_INTERVAL}ms`
const driftCss = `${CAROUSEL_INTERVAL + 1400}ms`

let timer = 0

function pad(value) {
  return String(value).padStart(2, '0')
}

function go(path) {
  router.push(path)
}

function stopTimer() {
  if (timer) window.clearTimeout(timer)
  timer = 0
}

function startTimer() {
  stopTimer()
  if (paused.value || prefersReduced) return
  timer = window.setTimeout(() => {
    active.value = (active.value + 1) % slides.length
    startTimer()
  }, CAROUSEL_INTERVAL)
}

function select(index) {
  if (index !== active.value) active.value = index
  lap.value += 1
  startTimer()
}

/** 暂停即把进度归零：恢复后进度条与计时器一定从同一刻重新开始 */
watch(paused, (value) => {
  lap.value += 1
  if (value) {
    stopTimer()
    return
  }
  startTimer()
})

/** 键盘只给左右与首尾，选中后焦点跟着走 */
function onTabKeydown(event, index) {
  let to = -1
  if (event.key === 'ArrowRight') to = Math.min(index + 1, slides.length - 1)
  if (event.key === 'ArrowLeft') to = Math.max(index - 1, 0)
  if (event.key === 'Home') to = 0
  if (event.key === 'End') to = slides.length - 1
  if (to < 0) return
  event.preventDefault()
  select(to)
  event.currentTarget.parentNode?.children?.[to]?.focus()
}

/** 环境光随指针游移：直接写自定义属性，不进响应式，避免每帧重渲染 */
function trackPointer(event) {
  const el = carouselEl.value
  if (!el || !finePointer) return
  const rect = el.getBoundingClientRect()
  el.style.setProperty('--px', ((event.clientX - rect.left) / rect.width - 0.5).toFixed(3))
  el.style.setProperty('--py', ((event.clientY - rect.top) / rect.height - 0.5).toFixed(3))
}

function resetPointer() {
  if (!carouselEl.value) return
  carouselEl.value.style.setProperty('--px', '0')
  carouselEl.value.style.setProperty('--py', '0')
}

/**
 * 只有键盘把焦点送进来才暂停：鼠标点进度条同样会 focus，
 * 若一并暂停，用户点过一次就再也不会自动播放了。
 */
function onFocusIn(event) {
  const target = event.target
  if (target instanceof HTMLElement && target.matches(':focus-visible')) focusing.value = true
}

/** 焦点在容器内部挪动时不算移出，否则会反复重置进度 */
function onFocusOut(event) {
  const next = event.relatedTarget
  if (!(next instanceof Node) || !event.currentTarget.contains(next)) focusing.value = false
}

/** 切到后台时浏览器会冻结定时器，不如主动停表，回来再整轮重来 */
function onVisibility() {
  pageHidden.value = document.hidden
}

onMounted(() => {
  startTimer()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  stopTimer()
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.7fr);
  gap: var(--tb-size-3);
  /* 比 A1/A2 两格的 226px 略高：轮播要留出「图—文—进度条」三层 */
  min-height: 258px;
}

/* 三块共用：抹掉 button 默认外观，内容一律纵向排布 */
.hero__feature,
.hero__entry,
.hero__carousel {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  font: inherit;
  text-align: left;
  color: inherit;
  cursor: pointer;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  /* hover 不做位移：与 ShowcaseCard 统一，只给阴影与描边反馈 */
  transition:
    box-shadow var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}

.hero__feature:hover,
.hero__entry:hover,
.hero__carousel:hover {
  box-shadow: var(--tb-shadow-3);
  border-color: var(--tb-primary-border);
}

.hero__feature:focus-visible,
.hero__entry:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--tb-ring);
}

/* ========== A1 主推卡 ========== */
.hero__feature {
  justify-content: space-between;
  /* 品牌面：文字取 --tb-on-primary，两态都是白，不随明暗反相 */
  color: var(--tb-on-primary);
  background:
    radial-gradient(circle at 82% 12%, color-mix(in oklab, var(--tb-primary-hover) 70%, transparent), transparent 58%),
    linear-gradient(140deg, var(--tb-primary-active) 0%, var(--tb-primary) 100%);
  border-color: color-mix(in oklab, var(--tb-on-primary) 22%, transparent);
}

.hero__feature-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding: var(--tb-size-4);
}

.hero__eyebrow {
  display: block;
  margin-bottom: var(--tb-size-2);
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
  opacity: 0.78;
}

.hero__feature-title {
  display: block;
  font-size: var(--tb-text-2xl);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  line-height: var(--tb-leading-tight);
}

.hero__feature-desc {
  display: block;
  margin-top: var(--tb-size-2);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-snug);
  opacity: 0.82;
}

.hero__round {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--tb-control-md);
  height: var(--tb-control-md);
  color: var(--tb-on-primary);
  background: color-mix(in oklab, var(--tb-on-primary) 18%, transparent);
  border: var(--tb-border-width) solid color-mix(in oklab, var(--tb-on-primary) 34%, transparent);
  border-radius: var(--tb-radius-full);
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}

/* 圆钮不跟着挪位，只把底与描边点亮 */
.hero__feature:hover .hero__round {
  background: color-mix(in oklab, var(--tb-on-primary) 30%, transparent);
  border-color: color-mix(in oklab, var(--tb-on-primary) 55%, transparent);
}

.hero__collage {
  position: relative;
  display: block;
  height: 96px;
  margin: 0 var(--tb-size-4) var(--tb-size-4);
}

.hero__chip {
  position: absolute;
  bottom: 0;
  width: 62px;
  height: 78px;
  background: color-mix(in oklab, var(--tb-on-primary) 16%, transparent);
  border: var(--tb-border-width) solid color-mix(in oklab, var(--tb-on-primary) 30%, transparent);
  border-radius: var(--tb-radius-lg);
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}

.hero__chip--a {
  right: 96px;
  transform: rotate(-11deg);
}

.hero__chip--b {
  right: 48px;
  transform: rotate(-2deg) translateY(-6px);
}

.hero__chip--c {
  right: 0;
  transform: rotate(9deg);
}

/* 三张纸片保持静态扇形角度，hover 只提亮底与描边，不再改角度或位置 */
.hero__feature:hover .hero__chip {
  background: color-mix(in oklab, var(--tb-on-primary) 26%, transparent);
  border-color: color-mix(in oklab, var(--tb-on-primary) 48%, transparent);
}

/* ========== A2 双入口 ========== */
.hero__entries {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: var(--tb-size-3);
  min-width: 0;
}

/**
 * 双入口卡与 A3 同一套「深色画报」处理：位图恒暗、文字恒白，
 * 墨色与白字就地定义、不随明暗反相（反相后白字压浅色照片会失效），其余中性色仍走 Token。
 */
.hero__entry {
  --entry-shade: 26 44 78;
  --entry-on-media: #ffffff;

  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  min-height: 0;
  padding: var(--tb-size-4);
  color: var(--entry-on-media);
  /* 兜底底色与 A1 同一支主色，图片未解码时不会闪白 */
  background: linear-gradient(150deg, var(--tb-primary) 0%, rgb(var(--entry-shade)) 100%);
  isolation: isolate;
}

/* —— 图 —— */
.hero__entry-media {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.hero__entry-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 两张图的主体都在右半，左侧留白供文字落位 */
  object-position: 72% 50%;
  filter: light-dark(brightness(1.12) saturate(1.02), brightness(1.05));
}

/* 品牌染色：把整帧色相拉到主色上、只留明暗，与 A1/A3 归到同一色系 */
.hero__entry-tint {
  position: absolute;
  inset: 0;
  background: linear-gradient(140deg, var(--tb-primary-active) 0%, var(--tb-primary) 100%);
  mix-blend-mode: color;
  opacity: 0.66;
}

/* 压暗层：左侧保文字对比度，顶部与右上角收一次高光纸面 */
.hero__entry-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(
      96deg,
      rgb(var(--entry-shade) / 0.92) 0%,
      rgb(var(--entry-shade) / 0.72) 34%,
      rgb(var(--entry-shade) / 0.24) 68%,
      rgb(var(--entry-shade) / 0.12) 100%
    ),
    linear-gradient(180deg, rgb(var(--entry-shade) / 0.42) 0%, transparent 42%),
    radial-gradient(90% 120% at 100% 0%, rgb(var(--entry-shade) / 0.5) 0%, transparent 58%);
}

.hero__entry-grain {
  position: absolute;
  inset: 0;
  background-image: var(--tb-noise);
  opacity: calc(var(--tb-grain) * 0.6);
  mix-blend-mode: overlay;
}

.hero__entry-text {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
  min-width: 0;
}

.hero__entry-title {
  font-size: var(--tb-text-lg);
  font-weight: 600;
  line-height: var(--tb-leading-snug);
  color: var(--entry-on-media);
  text-shadow: 0 1px 10px rgb(var(--entry-shade) / 0.45);
}

.hero__pill {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  align-self: flex-start;
  max-width: 100%;
  height: var(--tb-control-sm);
  padding: 0 var(--tb-size-3);
  font-size: var(--tb-text-xs);
  color: rgb(255 255 255 / 0.82);
  background: color-mix(in oklab, var(--entry-on-media) 12%, transparent);
  border: var(--tb-border-width) solid color-mix(in oklab, var(--entry-on-media) 26%, transparent);
  border-radius: var(--tb-radius-full);
  backdrop-filter: blur(6px);
}

.hero__pill span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.hero__thumb {
  position: relative;
  z-index: 1;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: var(--entry-on-media);
  background: color-mix(in oklab, var(--entry-on-media) 14%, transparent);
  border: var(--tb-border-width) solid color-mix(in oklab, var(--entry-on-media) 32%, transparent);
  border-radius: var(--tb-radius-lg);
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

.hero__entry:hover .hero__thumb {
  transform: scale(1.06);
}

/* ========== A3 轮播 ========== */
/**
 * 这一格是全页唯一的位图媒体位：底图恒暗、文字恒白，两套主题共用同一种「深色画报」气质，
 * 因此墨色与白字在此就地定义、刻意不随明暗反相 —— 反相后白字压在浅色照片上会直接失效。
 * 其余中性色仍走 Token。
 */
.hero__carousel {
  /* 压暗基色取墨蓝而不是纯黑：这一格要与 A1 的 --tb-primary 渐变并排读成同一支颜色 */
  --hero-shade: 26 44 78;
  --hero-on-media: #ffffff;
  --hero-cta-bg: #eef2f9;
  --px: 0;
  --py: 0;

  justify-content: flex-start;
  gap: var(--tb-size-2);
  padding: var(--tb-size-4) var(--tb-size-5);
  color: var(--hero-on-media);
  /* 兜底底色与 A1 同一支主色，图片未解码时也不会露出一块纯黑 */
  background: linear-gradient(150deg, var(--tb-primary) 0%, rgb(var(--hero-shade)) 100%);
  isolation: isolate;
}

/* —— 图：三帧叠放，只有 is-active 那帧可见 —— */
.hero__media {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.hero__frame {
  position: absolute;
  inset: -10px;
  opacity: 0;
  /* 指针视差：位移量由脚本写进 --px/--py，作用在外框，不干扰内层推镜 */
  transform: translate3d(calc(var(--px) * -14px), calc(var(--py) * -10px), 0);
  transition:
    opacity var(--tb-dur-5) var(--tb-ease-out),
    transform var(--tb-dur-3) var(--tb-ease-out);
}

.hero__frame.is-active {
  opacity: 1;
}

.hero__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 三张图的视觉重心都在右半，裁切时优先保住主体 */
  object-position: 68% 50%;
  transform: scale(1.04);
  /* 亮色页面是暖白，底图稍提亮才不会像一块黑洞；暗色只补一点点，避免破坏夜景 */
  filter: light-dark(brightness(1.14) saturate(1.02), brightness(1.05));
}

/* 推镜：停留期间极缓慢拉近，静图也有了呼吸感；只动 transform，符合 docs/09 §6.3 */
.hero__frame.is-active .hero__img {
  animation: hero-drift v-bind(driftCss) var(--tb-ease-linear) both;
}

@keyframes hero-drift {
  from {
    transform: scale(1.14) translate3d(0.9%, 0.7%, 0);
  }

  to {
    transform: scale(1.03) translate3d(-0.5%, -0.4%, 0);
  }
}

/**
 * 品牌染色：位图原色偏青黑，与 A1 主推卡的墨蓝主色放在一起像是另一套配色。
 * 用 color 混合把整帧的色相/饱和度拉到主色上、只留原图的明暗，两格才归到同一色系；
 * 取值随主题换算，亮色下染成 --tb-primary，暗色下自动提亮到 --tb-primary（暗色档更亮）。
 */
.hero__tint {
  position: absolute;
  inset: 0;
  background: linear-gradient(140deg, var(--tb-primary-active) 0%, var(--tb-primary) 100%);
  mix-blend-mode: color;
  opacity: 0.72;
}

/* 压暗层：左侧保证文字对比度，底部托住进度条，顶部托住序号 */
.hero__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(
      96deg,
      rgb(var(--hero-shade) / 0.95) 0%,
      rgb(var(--hero-shade) / 0.8) 32%,
      rgb(var(--hero-shade) / 0.34) 64%,
      rgb(var(--hero-shade) / 0.06) 100%
    ),
    linear-gradient(0deg, rgb(var(--hero-shade) / 0.88) 0%, rgb(var(--hero-shade) / 0.18) 42%, transparent 70%),
    linear-gradient(180deg, rgb(var(--hero-shade) / 0.5) 0%, transparent 32%),
    /* 右上角单独收一次：第三帧的纸叠正好顶在序号下面 */
    radial-gradient(90% 90% at 100% 0%, rgb(var(--hero-shade) / 0.62) 0%, transparent 58%);
}

/* 颗粒：与卡片封面同源 Token，给数字渲染图补一层纸感 */
.hero__grain {
  position: absolute;
  inset: 0;
  background-image: var(--tb-noise);
  opacity: calc(var(--tb-grain) * 0.75);
  mix-blend-mode: overlay;
}

/* —— 序号 —— */
.hero__count {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  align-self: flex-end;
  gap: var(--tb-size-2);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tb-tracking-wide);
  color: rgb(255 255 255 / 0.58);
  /* 序号可能落在高光纸面上，压一层极浅的投影兜底 */
  text-shadow: 0 1px 10px rgb(var(--hero-shade) / 0.55);
}

.hero__count-now {
  color: var(--hero-on-media);
}

.hero__count-rule {
  width: 20px;
  height: 1px;
  background: currentColor;
}

/* —— 文案 —— */
.hero__slide-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--tb-size-2);
  max-width: min(72%, 38ch);
  /* 顶住序号、贴住进度条：内容整体压在左下 */
  margin-top: auto;
}

/* 逐行上浮：元素是随激活项重挂的，:key 换值即重播 */
.hero__slide-body > * {
  animation: hero-line-in var(--tb-dur-5) var(--tb-ease-out) both;
}

.hero__slide-body > :nth-child(1) {
  animation-delay: 40ms;
}

.hero__slide-body > :nth-child(2) {
  animation-delay: 110ms;
}

.hero__slide-body > :nth-child(3) {
  animation-delay: 180ms;
}

.hero__slide-body > :nth-child(4) {
  animation-delay: 250ms;
}

@keyframes hero-line-in {
  from {
    opacity: 0;
    transform: translate3d(0, 16px, 0);
  }
}

.hero__slide-body .hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin: 0;
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  color: rgb(255 255 255 / 0.72);
}

.hero__slide-body .hero__eyebrow::before {
  content: '';
  width: 16px;
  height: 1px;
  background: currentColor;
}

.hero__slide-title {
  font-size: clamp(1.375rem, 1.9vw, var(--tb-text-2xl));
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  line-height: var(--tb-leading-tight);
  color: var(--hero-on-media);
}

.hero__slide-desc {
  overflow: hidden;
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-snug);
  color: rgb(255 255 255 / 0.74);
  /* 两行封顶；pretty 让末行不至于只剩一个孤字（不支持的内核退化为普通折行） */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  text-wrap: pretty;
}

.hero__cta {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-2);
  padding: var(--tb-size-2) var(--tb-size-4);
  font: inherit;
  font-size: var(--tb-text-sm);
  font-weight: 600;
  line-height: 1.2;
  color: #101725;
  background: var(--hero-cta-bg);
  border: 0;
  border-radius: var(--tb-radius-sm);
  cursor: pointer;
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.hero__cta:focus-visible {
  outline: 2px solid var(--hero-on-media);
  outline-offset: 3px;
}

/* 箭头是唯一的「可点」暗示：悬停整块卡时向右挪一点 */
.hero__cta .el-icon {
  transition: transform var(--tb-dur-2) var(--tb-ease-out);
}

.hero__carousel:hover .hero__cta {
  background: var(--hero-on-media);
}

.hero__carousel:hover .hero__cta .el-icon {
  transform: translateX(3px);
}

/* —— 底部分段进度 —— */
.hero__rail {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--tb-size-4);
  margin-top: var(--tb-size-3);
}

.hero__tab {
  display: grid;
  gap: var(--tb-size-1);
  padding: 0;
  font: inherit;
  text-align: left;
  color: rgb(255 255 255 / 0.46);
  background: none;
  border: 0;
  cursor: pointer;
  transition: color var(--tb-dur-2) var(--tb-ease-out);
}

.hero__tab:hover,
.hero__tab.is-active {
  color: var(--hero-on-media);
}

.hero__tab:focus-visible {
  outline: 2px solid var(--hero-on-media);
  outline-offset: 4px;
  border-radius: var(--tb-radius-xs);
}

.hero__tab-no {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tb-tracking-wide);
}

.hero__tab-track {
  display: block;
  height: 2px;
  overflow: hidden;
  background: rgb(255 255 255 / 0.22);
}

/* 进度靠 scaleX 推进，宽度始终写死（docs/09 §6.3） */
.hero__tab-track i {
  display: block;
  height: 100%;
  background: currentColor;
  transform: scaleX(0);
  transform-origin: left center;
}

.hero__tab-track i.is-run {
  animation: hero-progress v-bind(intervalCss) linear forwards;
}

@keyframes hero-progress {
  to {
    transform: scaleX(1);
  }
}

/* 减弱动效：推镜、逐行入场与进度条全部停用，轮播本身也不再自动前进（见脚本 prefersReduced） */
@media (prefers-reduced-motion: reduce) {
  .hero__frame {
    transition: none;
  }

  .hero__frame.is-active .hero__img,
  .hero__slide-body > *,
  .hero__tab-track i.is-run {
    animation: none;
  }
}

/* ========== 响应式：多栏 → 单栏 ========== */
@media (max-width: 1024px) {
  .hero {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  /* 这一档轮播独占整行，横宽比拉到 5:1，需抬高才不至于压成一条缝 */
  .hero__carousel {
    grid-column: 1 / -1;
    min-height: 234px;
  }

  .hero__slide-body {
    max-width: min(58%, 36ch);
  }
}

@media (max-width: 768px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    min-height: 0;
  }

  .hero__entries {
    grid-template-rows: none;
    grid-template-columns: minmax(0, 1fr);
  }

  .hero__carousel {
    min-height: 248px;
    padding: var(--tb-size-4);
  }

  /* 窄屏改用左侧裁切，把右半的主体尽量留在画面里 */
  .hero__img {
    object-position: 62% 50%;
  }

  .hero__slide-body {
    max-width: 100%;
  }

  .hero__rail {
    gap: var(--tb-size-3);
  }
}
</style>
