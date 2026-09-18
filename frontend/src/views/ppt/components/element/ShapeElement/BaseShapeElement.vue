<template>
  <div
    class="base-element-shape"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <div
        class="element-content"
        :style="{
          opacity: elementInfo.opacity,
          filter: shadowStyle ? `drop-shadow(${shadowStyle})` : '',
          transform: flipStyle,
          color: shapeText.defaultColor,
          fontFamily: shapeText.defaultFontName,
        }"
      >
        <svg overflow="visible" :width="elementInfo.width" :height="elementInfo.height">
          <defs v-if="elementInfo.gradient">
            <linearGradient
              v-if="gradientType !== 'radial'"
              :id="gradientId"
              x1="0%"
              y1="0%"
              x2="100%"
              y2="0%"
              :gradientTransform="`rotate(${gradientRotate}, 0.5, 0.5)`"
            >
              <stop
                v-for="(stop, si) in gradientStops"
                :key="'gs-' + si"
                :offset="stop.offset"
                :stop-color="stop.color"
              />
            </linearGradient>
            <radialGradient v-else :id="gradientId">
              <stop
                v-for="(stop, si) in gradientStops"
                :key="'gs-' + si"
                :offset="stop.offset"
                :stop-color="stop.color"
              />
            </radialGradient>
          </defs>
          <pattern
            v-if="shapePattern"
            :id="patternId"
            patternUnits="objectBoundingBox"
            width="100%"
            height="100%"
          >
            <image
              :href="shapePattern"
              x="0"
              y="0"
              width="1"
              height="1"
              preserveAspectRatio="xMidYMid slice"
            />
          </pattern>
          <g :transform="scaleTransform">
            <path
              vector-effect="non-scaling-stroke"
              stroke-linecap="butt"
              stroke-miterlimit="8"
              :d="elementInfo.path"
              :fill="shapeFill"
              :stroke="outlineColor"
              :stroke-width="outlineWidth"
              :stroke-dasharray="outlineStyle === 'dashed' ? '10 5' : '0 0'"
            ></path>
          </g>
        </svg>

        <div
          class="shape-text"
          :class="shapeText.align"
          v-if="shapeText.content"
          v-html="shapeText.content"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import useElementOutline from '../hooks/useElementOutline'
import useElementShadow from '../hooks/useElementShadow'
import useElementFlip from '../hooks/useElementFlip'
import { generateId } from '../../../utils/element'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})

const outline = computed(() => props.elementInfo.outline)
const { outlineWidth, outlineStyle, outlineColor } = useElementOutline(outline)

const shadow = computed(() => props.elementInfo.shadow)
const { shadowStyle } = useElementShadow(shadow)

const flipH = computed(() => props.elementInfo.flipH)
const flipV = computed(() => props.elementInfo.flipV)
const { flipStyle } = useElementFlip(flipH, flipV)

// 与参考实现一致：path 按元素宽高 / viewBox 等比拉伸
const viewBox = computed(() => props.elementInfo.viewBox || [200, 200])
const scaleTransform = computed(
  () => `scale(${props.elementInfo.width / viewBox.value[0]}, ${props.elementInfo.height / viewBox.value[1]})`,
)

// 形状内文字（默认值与 PPTist 一致）
const shapeText = computed(() => {
  const text = props.elementInfo.text || {}
  return {
    content: text.content || '',
    defaultFontName: text.defaultFontName || '微软雅黑',
    defaultColor: text.defaultColor || '#000',
    align: ['top', 'middle', 'bottom'].includes(text.align) ? text.align : 'middle',
  }
})

// 渐变（兼容 colors: [{color,pos}] 与 color: [c1,c2] 两种结构）
const gradientType = computed(() => props.elementInfo.gradient?.type)
const gradientRotate = computed(() => props.elementInfo.gradient?.rotate || 0)
const gradientStops = computed(() => {
  const gradient = props.elementInfo.gradient
  if (!gradient) return []
  if (Array.isArray(gradient.colors) && gradient.colors.length) {
    return gradient.colors.map(item => {
      let pos = Number(item.pos)
      if (Number.isNaN(pos)) pos = 0
      return { offset: pos + '%', color: item.color }
    })
  }
  if (Array.isArray(gradient.color) && gradient.color.length) {
    return gradient.color.map((c, index) => ({
      offset: (index === 0 ? 0 : 100) + '%',
      color: c,
    }))
  }
  return []
})

// 图片（图案）填充，来自 pptx 导录的 shape.pattern（base64 图片）
const shapePattern = computed(() => props.elementInfo.pattern || '')

const gradientId = `shape-gradient-${generateId(10)}`
const patternId = `shape-pattern-${generateId(10)}`

// 填充优先级：渐变 > 图片填充 > 纯色（无填充时透明，避免 SVG 默认黑色）
const shapeFill = computed(() => {
  if (props.elementInfo.gradient) return `url(#${gradientId})`
  if (shapePattern.value) return `url(#${patternId})`
  return props.elementInfo.fill || 'transparent'
})
</script>

<style lang="css" scoped>
.base-element-shape {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
  transform-origin: center;
}
.element-content {
  width: 100%;
  height: 100%;
  position: relative;
}
.element-content svg {
  transform-origin: 0 0;
  overflow: visible;
}
.shape-text {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  padding: 10px;
  line-height: 1.2;
  word-break: break-word;
  pointer-events: none;
  overflow: hidden;
}
.shape-text.top {
  justify-content: flex-start;
}
.shape-text.middle {
  justify-content: center;
}
.shape-text.bottom {
  justify-content: flex-end;
}
</style>