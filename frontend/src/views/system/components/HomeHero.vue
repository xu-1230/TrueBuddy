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

    <!-- A3 运营轮播 -->
    <span
      class="hero__carousel"
      @mouseenter="paused = true"
      @mouseleave="paused = false"
    >
      <transition name="hero-fade">
        <span :key="active" class="hero__slide" :class="`is-${slides[active].pattern}`">
          <span class="hero__slide-body">
            <span class="hero__eyebrow">{{ slides[active].eyebrow }}</span>
            <span class="hero__slide-title">{{ slides[active].title }}</span>
            <span class="hero__slide-desc">{{ slides[active].description }}</span>
            <span class="hero__cta">
              <span>{{ slides[active].cta }}</span>
              <el-icon :size="14"><ArrowRight /></el-icon>
            </span>
          </span>
          <el-icon v-if="slides[active].icon" class="hero__slide-mark" :size="96">
            <component :is="slides[active].icon" />
          </el-icon>
        </span>
      </transition>

      <span class="hero__dots" role="tablist" aria-label="运营位切换">
        <button
          v-for="(slide, index) in slides"
          :id="`hero-dot-${slide.id}`"
          :key="slide.id"
          class="hero__dot"
          :class="{ 'is-active': index === active }"
          type="button"
          role="tab"
          :aria-selected="index === active"
          :aria-label="slide.title"
          @click="active = index"
        ></button>
      </span>
    </span>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { CAROUSEL_INTERVAL, CAROUSEL_SLIDES, PROMO_ENTRIES, PROMO_MAIN } from '../constants'

const router = useRouter()
const slides = CAROUSEL_SLIDES
const active = ref(0)
const paused = ref(false)

let timer = null

/** 轮播是「自动播放的内容切换」，无障碍上必须可暂停，且尊重系统减弱动效偏好 */
const reduceMotion = () =>
  typeof window !== 'undefined' &&
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true

function go(path) {
  router.push(path)
}

function advance() {
  if (paused.value || reduceMotion()) return
  active.value = (active.value + 1) % slides.length
}

onMounted(() => {
  timer = window.setInterval(advance, CAROUSEL_INTERVAL)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  timer = null
})
</script>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.7fr);
  gap: var(--tb-size-3);
  min-height: 226px;
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

.hero__entry {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  min-height: 0;
  padding: var(--tb-size-4);
  color: var(--tb-text-primary);
  background:
    radial-gradient(circle at 50% 118%, var(--tb-primary-soft), transparent 62%),
    var(--tb-bg-surface);
}

.hero__entry-text {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
  min-width: 0;
}

.hero__entry-title {
  font-size: var(--tb-text-lg);
  font-weight: 600;
  line-height: var(--tb-leading-snug);
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
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-full);
}

.hero__pill span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.hero__thumb {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: var(--tb-primary-text);
  background: linear-gradient(150deg, var(--tb-bg-sunken), var(--tb-primary-soft));
  border: var(--tb-border-width) solid var(--tb-primary-border);
  border-radius: var(--tb-radius-lg);
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

.hero__entry:hover .hero__thumb {
  transform: scale(1.06);
}

/* ========== A3 轮播 ========== */
.hero__carousel {
  /* 轮播本身不响应点击（点子在圆点上），因此不用 button 语义 */
  cursor: default;
  background: var(--tb-bg-sunken);
}

.hero__slide {
  position: relative;
  display: flex;
  flex: 1;
  align-items: flex-end;
  min-height: 0;
  overflow: hidden;
  color: var(--tb-text-primary);
}

.hero__slide::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.55;
}

.hero__slide.is-glow::before {
  background:
    radial-gradient(circle at 78% 26%, var(--tb-primary-border), transparent 52%),
    radial-gradient(circle at 26% 88%, var(--tb-primary-soft), transparent 58%),
    linear-gradient(120deg, var(--tb-bg-sunken), var(--tb-bg-raised));
  opacity: 1;
}

.hero__slide.is-beam::before {
  background:
    repeating-linear-gradient(112deg, var(--tb-primary-border) 0 3px, transparent 3px 30px),
    linear-gradient(120deg, var(--tb-bg-sunken), var(--tb-primary-soft));
}

.hero__slide.is-lines::before {
  background:
    repeating-linear-gradient(180deg, var(--tb-primary-border) 0 1px, transparent 1px 14px),
    linear-gradient(120deg, var(--tb-bg-sunken), var(--tb-bg-raised));
  opacity: 0.8;
}

.hero__slide-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-1);
  padding: var(--tb-size-5);
  max-width: 68%;
}

.hero__slide-title {
  font-size: var(--tb-text-2xl);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  line-height: var(--tb-leading-tight);
  color: var(--tb-text-primary);
}

.hero__slide-desc {
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-snug);
  color: var(--tb-text-secondary);
}

.hero__cta {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  align-self: flex-start;
  margin-top: var(--tb-size-2);
  padding: var(--tb-size-1) var(--tb-size-3);
  font-size: var(--tb-text-sm);
  font-weight: 600;
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  border-radius: var(--tb-radius-full);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

/* 按钮不跟着平移，只把底色推亮一档 */
.hero__slide:hover .hero__cta {
  background: var(--tb-primary-hover);
}

.hero__slide-mark {
  position: absolute;
  right: var(--tb-size-5);
  top: 50%;
  z-index: 0;
  color: var(--tb-primary);
  opacity: 0.16;
  transform: translateY(-50%);
}

.hero__dots {
  position: absolute;
  right: var(--tb-size-4);
  bottom: var(--tb-size-3);
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
}

.hero__dot {
  width: 6px;
  height: 6px;
  padding: 0;
  cursor: pointer;
  background: var(--tb-text-tertiary);
  border: none;
  border-radius: var(--tb-radius-full);
  transition:
    width var(--tb-dur-2) var(--tb-ease-out),
    background var(--tb-dur-2) var(--tb-ease-out);
}

.hero__dot.is-active {
  width: 18px;
  background: var(--tb-primary);
}

/* 只动 opacity，符合 docs/09 §6.3 */
.hero-fade-enter-active,
.hero-fade-leave-active {
  transition: opacity var(--tb-dur-3) var(--tb-ease-out);
}

.hero-fade-enter-from,
.hero-fade-leave-to {
  opacity: 0;
}

.hero-fade-leave-active {
  position: absolute;
  inset: 0;
}

@media (prefers-reduced-motion: reduce) {
  .hero-fade-enter-active,
  .hero-fade-leave-active {
    transition: none;
  }
}

/* ========== 响应式：多栏 → 单栏 ========== */
@media (max-width: 1024px) {
  .hero {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .hero__carousel {
    grid-column: 1 / -1;
    min-height: 180px;
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

  .hero__slide-body {
    max-width: 100%;
  }
}
</style>
