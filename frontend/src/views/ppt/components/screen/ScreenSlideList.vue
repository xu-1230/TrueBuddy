<template>
  <div class="screen-slide-list">
    <div
      :class="[
        'slide-item',
        `turning-mode-${slide.turningMode || 'slideY'}`,
        {
          'current': index === slideIndex,
          'before': index < slideIndex,
          'after': index > slideIndex,
          'hide': (index === slideIndex - 1 || index === slideIndex + 1) && slide.turningMode !== currentSlide?.turningMode,
        }
      ]"
      v-for="(slide, index) in slides"
      :key="slide.id"
    >
      <div
        class="slide-content"
        :style="{
          width: slideWidth + 'px',
          height: slideHeight + 'px',
        }"
        v-if="Math.abs(slideIndex - index) < 2 || slide.animations?.length"
      >
        <ScreenSlide
          :slide="slide"
          :scale="scale"
          :animationIndex="animationIndex"
          :turnSlideToId="turnSlideToId"
          :manualExitFullscreen="manualExitFullscreen"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSlidesStore } from '../../stores/slides'
import { VIEWPORT_SIZE } from '../../configs/canvas'

import ScreenSlide from './ScreenSlide.vue'

const props = defineProps({
  slideWidth: {
    type: Number,
    required: true,
  },
  slideHeight: {
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
const { slides, slideIndex, currentSlide } = storeToRefs(slidesStore)

const scale = computed(() => props.slideWidth / VIEWPORT_SIZE)
</script>

<style lang="css" scoped>
.screen-slide-list {
  background: var(--tb-neutral-950);
  position: relative;
  width: 100%;
  height: 100%;
}
.slide-item {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}
.slide-item.current {
  z-index: 2;
}
.slide-item.hide {
  opacity: 0;
}
.slide-item.turning-mode-no.before {
  transform: translateY(-100%);
}
.slide-item.turning-mode-no.after {
  transform: translateY(100%);
}
.slide-item.turning-mode-fade {
  transition: opacity 0.75s;
}
.slide-item.turning-mode-fade.before {
  pointer-events: none;
  opacity: 0;
}
.slide-item.turning-mode-fade.after {
  pointer-events: none;
  opacity: 0;
}
.slide-item.turning-mode-slideX {
  transition: transform 0.35s;
}
.slide-item.turning-mode-slideX.before {
  transform: translateX(-100%);
}
.slide-item.turning-mode-slideX.after {
  transform: translateX(100%);
}
.slide-item.turning-mode-slideY {
  transition: transform 0.35s;
}
.slide-item.turning-mode-slideY.before {
  transform: translateY(-100%);
}
.slide-item.turning-mode-slideY.after {
  transform: translateY(100%);
}
.slide-content {
  background-color: var(--tb-bg-surface);
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>