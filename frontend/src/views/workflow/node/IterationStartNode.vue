<script setup>
import { Handle, Position } from '@vue-flow/core'
import { HomeFilled } from '@element-plus/icons-vue'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ inheritAttrs: false })

defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})
</script>

<template>
  <div class="boundary-node">
    <span class="boundary-dot boundary-dot--start">
      <el-icon><HomeFilled /></el-icon>
    </span>

    <!-- 迭代起点：只作为连线的起点 -->
    <Handle id="out" type="source" :position="Position.Right" class="node-handle" />
  </div>
</template>

<style scoped>
.boundary-node {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: var(--tb-bg-surface);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.boundary-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: #fff;
  font-size: 14px;
}

.boundary-dot--start {
  background: #2563eb;
}

/* ===== Handle（与 InputNode 等节点保持一致，起点常显便于连线） ===== */
.node-handle {
  width: 8px !important;
  height: 8px !important;
  border: var(--tb-border-width) solid var(--tb-border-strong) !important;
  background: var(--tb-bg-surface) !important;
  transition: transform 0.2s, border-color 0.2s;
}

.node-handle:hover {
  border-color: #2563eb !important;
  transform: translate(50%, -50%) scale(1.25) !important;
}
</style>
