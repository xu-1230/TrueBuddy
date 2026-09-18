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
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-tap-highlight-color: transparent;
  /* touch-action: none; */
}

table{ border-collapse: collapse; }
table th, table td{ border: 1px solid #000; padding: 8px 12px; text-align: left; }

/* 重置 Element Plus 表格，避免全局样式影响 */
.el-table table { border-collapse: separate; }
.el-table th, .el-table td { border: initial; padding: initial; text-align: initial; }

</style>
