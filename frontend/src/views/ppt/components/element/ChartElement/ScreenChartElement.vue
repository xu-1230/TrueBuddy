<template>
  <div
    class="base-element-chart"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <Chart
        class="base-element-chart-content"
        :type="elementInfo.chartType"
        :data="elementInfo.data"
        :theme-colors="elementInfo.themeColors"
        :theme-color="elementInfo.themeColor"
        :grid-color="elementInfo.gridColor"
        :legend="elementInfo.legend"
        :options="{
          ...(elementInfo.options || {}),
          width: elementInfo.width || 400,
          height: elementInfo.height || 250,
          fontColor: elementInfo.textColor,
        }"
      />
    </div>
  </div>
</template>

<script setup>
import Chart from './Chart.vue'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})
</script>

<style lang="css" scoped>
.base-element-chart {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
  transform-origin: center;
}
.base-element-chart-content {
  width: 100%;
  height: 100%;
}
</style>