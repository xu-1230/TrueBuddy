import { computed } from 'vue'

/**
 * 元素描边
 * @param outline 描边数据 { style?: 'dashed'|'solid', width?, color? }
 */
export default (outline) => {
  const outlineWidth = computed(() => outline.value?.width || 0)
  const outlineStyle = computed(() => outline.value?.style || 'solid')
  const outlineColor = computed(() => outline.value?.color || '#d14424')
  const showOutline = computed(() => outlineWidth.value > 0)

  return {
    outlineWidth,
    outlineStyle,
    outlineColor,
    showOutline,
  }
}