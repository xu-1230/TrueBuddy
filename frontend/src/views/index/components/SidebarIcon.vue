<script setup>
/**
 * 侧边栏双色调（Dual-tone）图标集
 *
 * 为何自研：Element Plus 图标只有单一线性风格，视觉存在感弱，无法表达
 * 「浅色底 + 主色图形」的层次；本组件用两层结构实现双色调：
 *   ① .tb-icon__plate  —— 浅色底板（currentColor + 低透明度）
 *   ② .tb-icon__shape  —— 主色实心图形（内部细节以 evenodd 挖空，露出底板）
 * 颜色全部继承 currentColor，随父级（菜单项）的悬停 / 选中态自动联动，
 * 属内联 SVG 组件而非字体图标方案。
 */
defineProps({
  name: { type: String, required: true },
})
</script>

<template>
  <svg class="tb-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <rect class="tb-icon__plate" x="1.6" y="1.6" width="20.8" height="20.8" rx="6.4" />

    <!-- 工作流：连线作为浅色层，先绘制再压上节点 -->
    <path
      v-if="name === 'workflow'"
      class="tb-icon__link"
      d="M7.4 10.6h4.6a2 2 0 0 1 2 2v1.2"
      stroke-width="1.6"
      stroke-linecap="round"
    />

    <!-- 首页 -->
    <g v-if="name === 'home'" class="tb-icon__shape" fill-rule="evenodd">
      <path
        d="M12 4.4 4.6 11.2v6.6a1.8 1.8 0 0 0 1.8 1.8h11.2a1.8 1.8 0 0 0 1.8-1.8v-6.6L12 4.4Zm-1.7 9.4h3.4v5.8h-3.4v-5.8Z"
      />
    </g>

    <!-- 文档模板生成：纸张 + 折角 + 文本行 -->
    <g v-else-if="name === 'doc'" class="tb-icon__shape" fill-rule="evenodd">
      <path
        d="M7.4 4.4h6.6l4 4v11.8H7.4V4.4Zm6.6 1 3 3h-3v-3ZM10 11h6.4v1.4H10V11Zm0 3h6.4v1.4H10V14Zm0 3h4.2v1.4H10V17Z"
      />
    </g>

    <!-- 作业批改：试卷 + 对勾 -->
    <g v-else-if="name === 'grading'" class="tb-icon__shape" fill-rule="evenodd">
      <path
        d="M7.6 4.4h8.8v15.8H7.6V4.4Zm2.2 9.2 2.4 2.4 3.7-3.7-1.1-1.1-2.6 2.7-1.3-1.4-1.1 1.1Z"
      />
    </g>

    <!-- 知识库模块：打开的书 -->
    <g v-else-if="name === 'knowledge'" class="tb-icon__shape" fill-rule="evenodd">
      <path
        d="M12 7.6C10.4 6.3 8.1 5.5 5.6 5.5v10.4c2.5 0 4.8.8 6.4 2.1 1.6-1.3 3.9-2.1 6.4-2.1V5.5c-2.5 0-4.8.8-6.4 2.1Zm-.7 1.3h1.4v8.9h-1.4V8.9Z"
      />
    </g>

    <!-- PPT 演示：屏幕 + 柱状图 + 支架 -->
    <g v-else-if="name === 'slide'" class="tb-icon__shape" fill-rule="evenodd">
      <path
        d="M4.6 5.4h14.8v9.8H4.6V5.4Zm3.4 4.2h1.8v3.4H8V9.6Zm3.1-2h1.8v5.4h-1.8V7.6Zm3.1 3.4h1.8v2h-1.8v-2ZM11.1 15.2h1.8v2.4h-1.8v-2.4ZM9 17.6h6v1.6H9v-1.6Z"
      />
    </g>

    <!-- 模拟会议：屏幕 + 两位参会者 -->
    <g v-else-if="name === 'meeting'" class="tb-icon__shape">
      <path
        d="M4.6 5h14.8v10.4H4.6V5Zm1.6 1.6v7.2h11.6V6.6H6.2Z"
        fill-rule="evenodd"
      />
      <circle cx="9.6" cy="9.2" r="1.5" />
      <path d="M7.1 13.2c0-1.3 1.1-2.3 2.5-2.3s2.5 1 2.5 2.3H7.1Z" />
      <circle cx="14.7" cy="9.4" r="1.2" />
      <path d="M12.8 13.2c0-1.1.9-1.9 1.9-1.9s1.9.8 1.9 1.9h-3.8Z" />
      <path d="M11.1 16.6h1.8v2.2h-1.8v-2.2Z" />
    </g>

    <!-- 工作流编排：两个节点 + 连线 -->
    <g v-else-if="name === 'workflow'" class="tb-icon__shape">
      <rect x="4.4" y="4.4" width="6.4" height="6.4" rx="2.2" />
      <rect x="13.2" y="13.2" width="6.4" height="6.4" rx="2.2" />
    </g>

    <!-- 系统设置：滑杆 -->
    <g v-else-if="name === 'settings'" class="tb-icon__shape">
      <path
        d="M4.6 7h2.4v1.6H4.6V7Zm7.2 0h7.6v1.6h-7.6V7ZM4.6 11.2h7.6v1.6H4.6v-1.6Zm13 0h2.2v1.6h-2.2v-1.6ZM4.6 15.4h1v1.6h-1v-1.6Zm5.8 0h9v1.6h-9v-1.6Z"
      />
      <circle cx="9.4" cy="7.8" r="2.2" />
      <circle cx="14.8" cy="12" r="2.2" />
      <circle cx="8" cy="16.2" r="2.2" />
    </g>

    <!-- 帮助与反馈：问号 -->
    <g v-else-if="name === 'help'" class="tb-icon__shape">
      <path
        d="M9.6 9.8a2.6 2.6 0 1 1 4.2 2.1c-.8.6-1.4 1-1.4 1.9"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
      />
      <circle cx="12" cy="17.2" r="1.2" />
    </g>
  </svg>
</template>

<style scoped>
.tb-icon {
  display: block;
  width: 20px;
  height: 20px;
}

/* 浅色底板：与主色图形叠出双色调 */
.tb-icon__plate {
  fill: currentColor;
  opacity: 0.18;
}

.tb-icon__shape {
  fill: currentColor;
}

.tb-icon__link {
  fill: none;
  stroke: currentColor;
  opacity: 0.42;
}
</style>
