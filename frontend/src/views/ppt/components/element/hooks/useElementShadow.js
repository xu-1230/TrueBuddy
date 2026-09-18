import { computed } from 'vue'

/**
 * 元素阴影
 * @param shadow 阴影数据 { h, v, blur, color }
 */
export default (shadow) => {
  const shadowStyle = computed(() => {
    if (!shadow.value) return ''
    const { h = 0, v = 0, blur = 0, color = '#000' } = shadow.value
    return `${h}px ${v}px ${blur}px ${color}`
  })

  return {
    shadowStyle,
  }
}