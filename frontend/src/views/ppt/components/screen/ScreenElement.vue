<template>
  <div
    class="screen-element"
    :class="{ 'link': elementInfo.link }"
    :id="`screen-element-${elementInfo.id}`"
    :style="{
      zIndex: elementIndex,
      color: theme.fontColor,
      fontFamily: theme.fontName,
      visibility: needWaitAnimation ? 'hidden' : 'visible',
    }"
    :title="elementInfo.link?.target || ''"
    @click="openLink()"
  >
    <component :is="currentElementComponent" :elementInfo="elementInfo"></component>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSlidesStore } from '../../stores/slides'

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
  animationIndex: {
    type: Number,
    required: true,
  },
  turnSlideToId: {
    type: Function,
    required: true,
  },
  manualExitFullscreen: {
    type: Function,
    required: true,
  },
})

const slidesStore = useSlidesStore()
const { formatedAnimations, theme } = storeToRefs(slidesStore)

// 元素类型 → 渲染组件
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

// 判断元素是否需要等待执行入场动画：等待执行入场的元素需要先隐藏
const needWaitAnimation = computed(() => {
  // 该元素在本页动画序列中的位置
  const elementIndexInAnimation = formatedAnimations.value.findIndex(item => {
    const elIds = item.animations.map(item => item.elId)
    return elIds.includes(props.elementInfo.id)
  })

  // 该元素未设置过动画
  if (elementIndexInAnimation === -1) return false

  // 若该元素已执行过动画，都无须隐藏
  if (elementIndexInAnimation < props.animationIndex) return false

  // 若该元素未执行过动画，获取其将要执行的第一个动画
  // 若将要执行的第一个动画为入场，则需要隐藏，否则无须隐藏
  const firstAnimation = formatedAnimations.value[elementIndexInAnimation].animations.find(item => item.elId === props.elementInfo.id)
  if (firstAnimation?.type === 'in') return true
  return false
})

// 打开元素绑定的超链接
const openLink = () => {
  const link = props.elementInfo.link
  if (!link) return

  if (link.type === 'web') {
    props.manualExitFullscreen()
    window.open(link.target)
  }
  else if (link.type === 'slide') {
    props.turnSlideToId(link.target)
  }
}
</script>

<style lang="css" scoped>
.link {
  cursor: pointer;
}
.screen-element {
  position: absolute;
}
</style>