<script setup>
/**
 * 自定义连线预览：拖拽连线时跟随指针的那条虚线。
 *
 * 通过 VueFlow 的 `#connection-line` 插槽接入，插槽除了 sourceX/sourceY/targetX/targetY
 * 还会传入 sourceNode、markerEnd、connectionStatus 等一堆额外属性。这里不透传给根 <g>，
 * 否则对象属性会被字符串化后写成非法 DOM 属性（与各 node 组件的处理保持一致）。
 */
defineOptions({ inheritAttrs: false })

defineProps({
  sourceX: {
    type: Number,
    required: true,
  },
  sourceY: {
    type: Number,
    required: true,
  },
  targetX: {
    type: Number,
    required: true,
  },
  targetY: {
    type: Number,
    required: true,
  },
})
</script>

<template>
  <g>
    <path
      class="vue-flow__connection animated"
      fill="none"
      stroke="var(--tb-primary)"
      :stroke-width="1.5"
      :d="`M${sourceX},${sourceY} C ${sourceX} ${targetY} ${sourceX} ${targetY} ${targetX},${targetY}`"
    />

    <circle :cx="targetX" :cy="targetY" fill="#fff" :r="4" stroke="var(--tb-primary)" :stroke-width="1.5" />
  </g>
</template>
