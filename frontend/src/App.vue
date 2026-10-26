<template>

  <RouterView />
</template>

<script setup>

</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  /* 正文与 UI 统一使用 Inter（见 src/assets/fonts.css），中文回退到系统字体 */
  font-family: var(--font-sans);
  -webkit-tap-highlight-color: transparent;
  /* touch-action: none; */
}

h1,
h2 {
  /* 大标题走 --tb-font-display（Ink 下是衬线，与"教案/文档"的书卷气质一致），
     与小标题拉开层级；h3 及以下保持无衬线，避免小字号下发虚 */
  font-family: var(--tb-font-display);
  letter-spacing: var(--tb-tracking-display);
}

h3 {
  letter-spacing: -0.01em;
}

table{ border-collapse: collapse; }
table th, table td{ border: 1px solid var(--tb-border-strong); padding: 8px 12px; text-align: left; }

/* 重置 Element Plus 表格，避免全局样式影响 */
.el-table table { border-collapse: separate; }
.el-table th, .el-table td { border: initial; padding: initial; text-align: initial; }

</style>
