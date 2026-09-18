import { computed } from 'vue'

/**
 * 元素翻转
 * @param flipH 水平翻转
 * @param flipV 垂直翻转
 */
export default (flipH, flipV) => {
  const flipStyle = computed(() => {
    if (flipH.value && flipV.value) return 'rotateY(180deg) rotateX(180deg)'
    if (flipH.value) return 'rotateY(180deg)'
    if (flipV.value) return 'rotateX(180deg)'
    return ''
  })

  return {
    flipStyle,
  }
}