<template>
  <div
    class="base-element-line"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
    }"
  >
    <div
      class="element-content"
      :style="{ filter: shadowStyle ? `drop-shadow(${shadowStyle})` : '' }"
    >
      <svg
        overflow="visible"
        :width="svgWidth"
        :height="svgHeight"
      >
        <defs>
          <LinePointMarker
            v-if="startPointType"
            :id="elementInfo.id"
            position="start"
            :type="startPointType"
            :color="elementInfo.color"
            :base-size="elementInfo.width"
          />
          <LinePointMarker
            v-if="endPointType"
            :id="elementInfo.id"
            position="end"
            :type="endPointType"
            :color="elementInfo.color"
            :base-size="elementInfo.width"
          />
        </defs>
        <path
          :d="path"
          :stroke="elementInfo.color"
          :stroke-width="elementInfo.width"
          :stroke-dasharray="lineDashArray"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
          :marker-start="startPointType ? `url(#${elementInfo.id}-${startPointType}-start)` : ''"
          :marker-end="endPointType ? `url(#${elementInfo.id}-${endPointType}-end)` : ''"
        ></path>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getLineElementPath } from '../../../utils/element'
import useElementShadow from '../hooks/useElementShadow'

import LinePointMarker from './LinePointMarker.vue'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})

const shadow = computed(() => props.elementInfo.shadow)
const { shadowStyle } = useElementShadow(shadow)

// 线条包围盒尺寸：取起点终点坐标差值，最小为 24
const svgWidth = computed(() => {
  const width = Math.abs(props.elementInfo.start[0] - props.elementInfo.end[0])
  return width < 24 ? 24 : width
})
const svgHeight = computed(() => {
  const height = Math.abs(props.elementInfo.start[1] - props.elementInfo.end[1])
  return height < 24 ? 24 : height
})

const lineDashArray = computed(() => props.elementInfo.style === 'dashed' ? '10, 5' : '0, 0')

const path = computed(() => getLineElementPath(props.elementInfo))

// 线条端点样式：points 为 [起点样式, 终点样式]，如 ['', 'arrow']
const startPointType = computed(() => props.elementInfo.points?.[0] || '')
const endPointType = computed(() => props.elementInfo.points?.[1] || '')
</script>

<style lang="css" scoped>
.base-element-line {
  position: absolute;
}
.element-content {
  width: 100%;
  height: 100%;
  position: relative;
}
svg {
  transform-origin: 0 0;
  overflow: visible;
}
</style>