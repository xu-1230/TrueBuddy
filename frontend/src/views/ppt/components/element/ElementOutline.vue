<template>
  <svg
    class="element-outline"
    v-if="showOutline"
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    style="position: absolute; left: 0; top: 0; pointer-events: none;"
  >
    <rect
      :x="outlineWidth / 2 + 1"
      :y="outlineWidth / 2 + 1"
      :width="width - outlineWidth - 2"
      :height="height - outlineWidth - 2"
      :rx="cornerRadius"
      :ry="cornerRadius"
      fill="none"
      :stroke="outlineColor"
      :stroke-width="outlineWidth"
      stroke-linecap="round"
      :stroke-dasharray="outlineStyle === 'dashed' ? '10, 5' : undefined"
    />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import useElementOutline from './hooks/useElementOutline'

const props = defineProps({
  width: {
    type: Number,
    required: true,
  },
  height: {
    type: Number,
    required: true,
  },
  outline: {
    type: Object,
    default: () => ({}),
  },
  cornerRadius: {
    type: Number,
    default: 0,
  },
})

const outline = computed(() => props.outline)
const { outlineWidth, outlineStyle, outlineColor, showOutline } = useElementOutline(outline)
</script>