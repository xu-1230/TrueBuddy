<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ParticleMorph } from '@/lib/particleMorph'
import { useAppearance } from '@/composables/useAppearance'

/**
 * 封面右侧的点阵粒子形变
 *
 * 形状取参考工程的 karot 同款 SVG（见 public/shapes/）：
 * 学术符号剪影 → 书架线稿，**只形变一次、不循环**。
 * 形变结束后鼠标快速划过仍会把粒子推开，随后回落到线稿上。
 * 引擎本体见 src/lib/particleMorph.js（Canvas 2D，零依赖）。
 */

/** 形状表：0 = 书架线稿，1 = 学术符号剪影；素材为透明底 + 橙色填充，采样只取 alpha */
const SHAPES = ['/shapes/karot-shelf.svg', '/shapes/karot-silhouettes.svg']
/** 形变方向：剪影 → 线稿 */
const MORPH_FROM_TO = [SHAPES[1], SHAPES[0]]

const hostRef = ref(null)
const { isDark } = useAppearance()

/** 尊重系统「减少动效」：只静态展示起始形状，不做形变 */
const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

let fx = null

/** 粒子颜色跟随主题：Token 里的 light-dark() 只有经元素解析后才是 canvas 能用的颜色值 */
function themeColor() {
  const el = hostRef.value
  return el ? getComputedStyle(el).color : undefined
}

/** 切到后台就停下，回来再继续（省电，也避免回来后一帧跨度过大） */
function onVisibility() {
  if (!fx || fx.destroyed || reduceMotion) return
  if (document.hidden) fx.pause()
  else fx.start()
}

onMounted(async () => {
  const narrow = window.innerWidth < 768

  fx = new ParticleMorph(hostRef.value, {
    // 采样间隔与点径要配成一对：点径 × 2 ≈ 间隔时点与点刚好相接、连成整片，
    // 实心区不会露出网格空洞（原来的 3.2 / 1 在实心剪影上会留下明显白点阵），
    // 间隔收紧到 2.6 也保证线稿的细线不会被采样网格跨过而断成虚线。
    spacing: narrow ? 3 : 2.6,
    pointRadius: 1.3,
    // 抗锯齿的半透明边缘也算形状：否则轮廓会被吃掉一圈，看起来「缺粒子」
    alphaCut: 64,
    // 素材自带留白较多，放大 1.4 让主体更饱满（超出画布的部分自然裁掉）
    fit: 1.4,
    // 上限留足余量：一旦触顶，引擎会加大采样间隔，形状反而会被稀释出空洞
    maxParticles: narrow ? 9000 : 16000,
    speed: 0.05,
    startDelay: 700,
    autoStart: !reduceMotion,
  })

  document.addEventListener('visibilitychange', onVisibility)

  fx.setColor(themeColor())
  await fx.setShapes(MORPH_FROM_TO)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  fx?.destroy()
  fx = null
})

watch(isDark, () => fx?.setColor(themeColor()))
</script>

<template>
  <div ref="hostRef" class="particle-morph" aria-hidden="true"></div>
</template>

<style scoped>
.particle-morph {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  /* 粒子取色即此处解析后的 color（随明暗态切换）。
     用 primary-text 而非 primary：亮色下是更深的靛蓝，落在米白纸面上对比足、
     不会像浅蓝那样"发飘"；暗色下自动变为浅蓝，同样清晰。 */
  color: var(--tb-primary-text);
}
</style>
