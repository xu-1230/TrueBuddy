<script setup>
/**
 * 管理端图标（内联 SVG，线性描边）
 *
 * 管理端信息密度更高，用 1.7px 线性图标更克制；用户端侧边栏（IndexView）
 * 也复用本组件，保持两端一致的视觉语言。不跨模块 import 用户端组件，
 * 避免管理端与用户端布局产生依赖。
 */
defineProps({
  name: { type: String, default: 'dot' },
  size: { type: [Number, String], default: 18 },
})
</script>

<template>
  <svg
    class="admin-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <template v-if="name === 'dashboard'">
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.6" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.6" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.6" />
    </template>

    <!-- 首页：屋顶 + 房体 + 门 -->
    <template v-else-if="name === 'home'">
      <path d="M4.4 10.4 12 4.2l7.6 6.2" />
      <path d="M6.4 8.9v9.5c0 .9.7 1.6 1.6 1.6h8c.9 0 1.6-.7 1.6-1.6V8.9" />
      <path d="M10.2 20v-5.2h3.6V20" />
    </template>

    <template v-else-if="name === 'users'">
      <circle cx="9.5" cy="8.5" r="3.2" />
      <path d="M3.8 20c.5-3.4 2.8-5.3 5.7-5.3s5.2 1.9 5.7 5.3" />
      <path d="M16 5.9a3 3 0 0 1 0 5.4" />
      <path d="M17.4 14.9c2 .5 3.2 2.2 3.5 4.5" />
    </template>

    <template v-else-if="name === 'shield'">
      <path d="M12 3.2 19 6v5.4c0 4.6-2.9 7.5-7 9.2-4.1-1.7-7-4.6-7-9.2V6l7-2.8Z" />
      <path d="m9.2 11.8 2 2 3.6-3.8" />
    </template>

    <template v-else-if="name === 'key'">
      <circle cx="8.2" cy="8.2" r="4.2" />
      <path d="m11.2 11.2 8.2 8.2" />
      <path d="m16.2 16.2 2-2" />
      <path d="m18.4 18.4 2-2" />
    </template>

    <!-- knowledge 为用户端菜单使用的别名，与 book 同形 -->
    <template v-else-if="name === 'book' || name === 'knowledge'">
      <path d="M12 6.6C10.3 5.4 8 4.6 5.4 4.6v12.2c2.6 0 4.9.8 6.6 2 1.7-1.2 4-2 6.6-2V4.6c-2.6 0-4.9.8-6.6 2Z" />
      <path d="M12 6.6v12.2" />
    </template>

    <template v-else-if="name === 'doc'">
      <path d="M7 3.6h6.4l3.6 3.6v13.2H7V3.6Z" />
      <path d="M13.2 3.8v3.6h3.6" />
      <path d="M9.6 11h4.8M9.6 14h4.8M9.6 17h3" />
    </template>

    <template v-else-if="name === 'grading'">
      <path d="M7 3.6h10v16.8H7V3.6Z" />
      <path d="m9.4 12 1.7 1.7 3.5-3.7" />
    </template>

    <!-- PPT 演示：幕布 + 支架 -->
    <template v-else-if="name === 'slide'">
      <path d="M3.4 3.8h17.2" />
      <path d="M20.6 3.8v9.8a1.8 1.8 0 0 1-1.8 1.8H5.2a1.8 1.8 0 0 1-1.8-1.8V3.8" />
      <path d="m8.4 20.4 3.6-5 3.6 5" />
    </template>

    <template v-else-if="name === 'meeting'">
      <rect x="3.4" y="6" width="12.4" height="12" rx="2" />
      <path d="m15.8 10.6 4.8-2.6v8l-4.8-2.6" />
    </template>

    <template v-else-if="name === 'workflow'">
      <rect x="3.4" y="3.6" width="6" height="5.4" rx="1.4" />
      <rect x="14.6" y="15" width="6" height="5.4" rx="1.4" />
      <path d="M9.4 6.3h4.2a2.4 2.4 0 0 1 2.4 2.4v6.3" />
      <path d="M13.6 12.6 16 15l2.4-2.4" />
    </template>

    <template v-else-if="name === 'setting'">
      <path d="M4 7h10M18 7h2M4 12h2M10 12h10M4 17h8M16 17h4" />
      <circle cx="16" cy="7" r="2" />
      <circle cx="8" cy="12" r="2" />
      <circle cx="14" cy="17" r="2" />
    </template>

    <template v-else-if="name === 'log'">
      <path d="M5.4 3.8h9.2l4 4v12.4H5.4V3.8Z" />
      <path d="M9 9.4h6M9 13h6M9 16.6h3.6" />
    </template>

    <template v-else-if="name === 'health'">
      <path d="M3 12.4h4l2.2-5.6 3.6 10.4 2.4-6.2 1.6 1.4H21" />
    </template>

    <template v-else>
      <circle cx="12" cy="12" r="4" />
    </template>
  </svg>
</template>

<style scoped>
.admin-icon {
  flex: 0 0 auto;
  display: block;
}
</style>
