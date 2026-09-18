<template>
  <div
    class="base-element-image"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <div class="element-content" :style="contentStyle">
        <div class="image-content" :style="{ clipPath: clipShape.style }">
          <img
            :src="elementInfo.src"
            draggable="false"
            :style="{ ...imgPosition, filter: filterStyle }"
          />
        </div>
        <ElementOutline :width="elementInfo.width" :height="elementInfo.height" :outline="elementInfo.outline" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import useElementShadow from '../hooks/useElementShadow'
import useElementFlip from '../hooks/useElementFlip'
import { getClipInfo } from '../../../configs/imageClip'

import ElementOutline from './../ElementOutline.vue'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})

const shadow = computed(() => props.elementInfo.shadow)
const { shadowStyle } = useElementShadow(shadow)

const flipH = computed(() => props.elementInfo.flipH)
const flipV = computed(() => props.elementInfo.flipV)
const { flipStyle } = useElementFlip(flipH, flipV)

// 图片裁剪：生成裁剪形状样式与图片位置（无裁剪时为完整展示）
const clip = computed(() => props.elementInfo.clip)
const { clipShape, imgPosition } = getClipInfo(clip.value)

// 图片样式
const contentStyle = computed(() => ({
  opacity: props.elementInfo.opacity,
  filter: shadowStyle.value ? `drop-shadow(${shadowStyle.value})` : undefined,
  transform: flipStyle.value || undefined,
}))

// 图片滤镜
const filterStyle = computed(() => {
  const f = props.elementInfo.filters
  if (!f) return undefined
  const filters = []
  if (f.blur) filters.push(`blur(${f.blur}px)`)
  if (f.brightness && f.brightness !== 100) filters.push(`brightness(${f.brightness}%)`)
  if (f.contrast && f.contrast !== 100) filters.push(`contrast(${f.contrast}%)`)
  if (f.grayscale) filters.push(`grayscale(${f.grayscale}%)`)
  if (f.hue && f.hue !== 0) filters.push(`hue-rotate(${f.hue}deg)`)
  if (f.opacity && f.opacity !== 100) filters.push(`opacity(${f.opacity}%)`)
  if (f.saturate && f.saturate !== 100) filters.push(`saturate(${f.saturate}%)`)
  if (f.sepia) filters.push(`sepia(${f.sepia}%)`)
  return filters.length ? filters.join(' ') : undefined
})
</script>

<style lang="css" scoped>
.base-element-image {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
}
.element-content {
  width: 100%;
  height: 100%;
  position: relative;
}
.image-content {
  width: 100%;
  height: 100%;
  overflow: hidden;
  position: relative;
}
img {
  position: absolute;
  -webkit-user-drag: none;
}
</style>