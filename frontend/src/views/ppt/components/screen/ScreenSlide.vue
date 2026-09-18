<template>
  <div
    class="screen-slide"
    :style="{
      width: VIEWPORT_SIZE + 'px',
      height: VIEWPORT_SIZE * viewportRatio + 'px',
      transform: `scale(${scale})`,
    }"
  >
    <div class="background" :style="{ ...backgroundStyle }"></div>
    <ScreenElement
      v-for="(element, index) in slide.elements"
      :key="element.id"
      :elementInfo="element"
      :elementIndex="index + 1"
      :animationIndex="animationIndex"
      :turnSlideToId="turnSlideToId"
      :manualExitFullscreen="manualExitFullscreen"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSlidesStore } from '../../stores/slides'
import { VIEWPORT_SIZE } from '../../configs/canvas'
import useSlideBackgroundStyle from '../../hooks/useSlideBackgroundStyle'

import ScreenElement from './ScreenElement.vue'

const props = defineProps({
  slide: {
    type: Object,
    required: true,
  },
  scale: {
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
const { viewportRatio } = storeToRefs(slidesStore)

const background = computed(() => props.slide.background)
const { backgroundStyle } = useSlideBackgroundStyle(background)
</script>

<style lang="css" scoped>
.screen-slide {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  overflow: hidden;
}
.background {
  width: 100%;
  height: 100%;
  background-position: center;
  position: absolute;
}
</style>