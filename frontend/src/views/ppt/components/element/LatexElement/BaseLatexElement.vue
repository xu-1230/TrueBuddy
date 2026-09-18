<template>
  <div
    class="base-element-latex"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <svg :viewBox="viewBox" width="100%" height="100%">
        <path
          :d="elementInfo.path"
          :fill="elementInfo.color"
          :stroke="elementInfo.color"
          :stroke-width="elementInfo.strokeWidth"
        />
      </svg>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})

const viewBox = computed(() => {
  const vb = props.elementInfo.viewBox || [200, 200]
  return `0 0 ${vb[0]} ${vb[1]}`
})
</script>

<style lang="css" scoped>
.base-element-latex {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
  transform-origin: center;
}
svg {
  display: block;
}
</style>