<template>
  <!-- AI 星形图标（四角星 + 两颗小星）：项目里没有现成的 sparkle 图标，按 SidebarIcon 的做法内联 SVG -->
  <svg
    class="ai-spark-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="currentColor"
    aria-hidden="true"
  >
    <path d="M11 2.5 L13.6 8.9 L20 11.5 L13.6 14.1 L11 20.5 L8.4 14.1 L2 11.5 L8.4 8.9 Z" />
    <path d="M19 2.3 L20 4.5 L22.2 5.5 L20 6.5 L19 8.7 L18 6.5 L15.8 5.5 L18 4.5 Z" />
    <path d="M5.5 16.4 L6.3 18.2 L8.1 19 L6.3 19.8 L5.5 21.6 L4.7 19.8 L2.9 19 L4.7 18.2 Z" />
  </svg>
</template>

<script setup>
defineProps({
  /** 图标边长（像素） */
  size: { type: [Number, String], default: 16 },
})
</script>

<style scoped>
.ai-spark-icon {
  display: block;
}
</style>
