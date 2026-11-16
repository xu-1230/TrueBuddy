<template>
  <div class="mt-ambient" aria-hidden="true">
    <!-- 入口屏：复用会议主视觉图，按主页 Hero 的「染色 + 蒙层 + 颗粒」处理 -->
    <div v-if="poster" class="mt-poster" :class="{ 'mt-poster--entry': animate }">
      <img class="mt-poster__img" :src="image" alt="" decoding="async" draggable="false" data-mt-poster />
      <div class="mt-poster__tint" />
      <div class="mt-poster__scrim" />
      <div class="mt-poster__grain" />
    </div>

    <div class="mt-ambient__aurora" />
    <div class="mt-ambient__beam" />
    <div class="mt-ambient__grid" />
    <div class="mt-ambient__scan" />
    <div v-if="scanline" class="mt-ambient__scanline" />
    <div class="mt-ambient__grain" />
  </div>
</template>

<script setup>
defineProps({
  /** 是否铺会议主视觉图（仅入口屏） */
  poster: { type: Boolean, default: false },
  /** 海报图路径 */
  image: { type: String, default: '/hero/hero-meeting.webp' },
  /** 入口屏海报是否做一次性缓慢推近 */
  animate: { type: Boolean, default: false },
  /** 是否显示缓慢下移的扫描线 */
  scanline: { type: Boolean, default: true },
})
</script>
