<template>
  <div
    class="base-element-table"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <StaticTable :elementInfo="elementInfo" />
    </div>
  </div>
</template>

<script setup>
import StaticTable from './StaticTable.vue'

defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})
</script>

<style lang="css" scoped>
.base-element-table {
  position: absolute;
}
.rotate-wrapper {
  transform-origin: center;
}
</style>