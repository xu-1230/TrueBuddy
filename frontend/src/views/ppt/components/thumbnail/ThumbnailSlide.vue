<template>
  <div
    class="thumbnail-slide"
    :style="{
      width: size + 'px',
      height: size * viewportRatio + 'px',
    }"
  >
    <div
      class="elements"
      :style="{
        width: VIEWPORT_SIZE + 'px',
        height: VIEWPORT_SIZE * viewportRatio + 'px',
        transform: `scale(${scale})`,
      }"
    >
      <div class="background" :style="{ ...backgroundStyle }"></div>
      <ThumbnailElement
        v-for="(element, index) in slide.elements"
        :key="element.id"
        :elementInfo="element"
        :elementIndex="index + 1"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSlidesStore } from '../../stores/slides'
import { VIEWPORT_SIZE } from '../../configs/canvas'
import useSlideBackgroundStyle from '../../hooks/useSlideBackgroundStyle'

import ThumbnailElement from './ThumbnailElement.vue'

const props = defineProps({
  slide: {
    type: Object,
    required: true,
  },
  size: {
    type: Number,
    required: true,
  },
})

const slidesStore = useSlidesStore()
const { viewportRatio } = storeToRefs(slidesStore)

const background = computed(() => props.slide.background)
const { backgroundStyle } = useSlideBackgroundStyle(background)

const scale = computed(() => props.size / VIEWPORT_SIZE)
</script>

<style lang="css" scoped>
.thumbnail-slide {
  background-color: #fff;
  overflow: hidden;
}
.elements {
  transform-origin: 0 0;
  position: relative;
}
.background {
  width: 100%;
  height: 100%;
  background-position: center;
  position: absolute;
}
</style>