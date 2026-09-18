<template>
  <div
    class="base-element"
    :style="{ zIndex: elementIndex }"
  >
    <component :is="currentElementComponent" :elementInfo="elementInfo"></component>
  </div>
</template>

<script setup>
import { computed } from 'vue'

import BaseTextElement from '../element/TextElement/BaseTextElement.vue'
import BaseImageElement from '../element/ImageElement/BaseImageElement.vue'
import BaseShapeElement from '../element/ShapeElement/BaseShapeElement.vue'
import BaseLineElement from '../element/LineElement/BaseLineElement.vue'
import ScreenChartElement from '../element/ChartElement/ScreenChartElement.vue'
import BaseTableElement from '../element/TableElement/BaseTableElement.vue'
import BaseLatexElement from '../element/LatexElement/BaseLatexElement.vue'
import ScreenVideoElement from '../element/VideoElement/ScreenVideoElement.vue'
import ScreenAudioElement from '../element/AudioElement/ScreenAudioElement.vue'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
  elementIndex: {
    type: Number,
    required: true,
  },
})

// 元素类型 → 渲染组件（缩略图中不执行动画，元素始终完整显示）
const currentElementComponent = computed(() => {
  const elementTypeMap = {
    image: BaseImageElement,
    text: BaseTextElement,
    shape: BaseShapeElement,
    line: BaseLineElement,
    chart: ScreenChartElement,
    table: BaseTableElement,
    latex: BaseLatexElement,
    video: ScreenVideoElement,
    audio: ScreenAudioElement,
  }
  return elementTypeMap[props.elementInfo.type] || null
})
</script>

<style lang="css" scoped>
.base-element {
  position: absolute;
}
</style>