import { computed } from 'vue'

/**
 * 幻灯片背景样式
 * 兼容两种数据结构：
 * - 新版（pptx 导录输出）：{ type, image: { src, size } } / { type, gradient: { type, colors: [{color,pos}], rotate } }
 * - 旧版：{ type, image, imageSize } / { type, gradientColor, gradientType, gradientRotate }
 * @param background 背景数据（ref/computed）
 */
export default (background) => {
  // 背景样式
  const backgroundStyle = computed(() => {
    if (!background.value) return {}

    if (background.value.type === 'solid') {
      return { backgroundColor: background.value.color }
    }
    else if (background.value.type === 'image') {
      const image = background.value.image
      const src = typeof image === 'string' ? image : (image && image.src) || ''
      const size = (image && image.size) || background.value.imageSize || 'cover'
      return {
        backgroundImage: `url(${src})`,
        backgroundRepeat: size === 'cover' ? 'no-repeat' : 'repeat',
        backgroundSize: size,
      }
    }
    else if (background.value.type === 'gradient') {
      // 新版：gradient 对象（含 colors 数组）
      if (background.value.gradient) {
        const { type, colors, rotate } = background.value.gradient
        if (!Array.isArray(colors) || !colors.length) return {}
        const colorStops = colors.map(item => `${item.color} ${Number(item.pos) || 0}%`).join(', ')
        if (type === 'radial') {
          return { backgroundImage: `radial-gradient(${colorStops})` }
        }
        return { backgroundImage: `linear-gradient(${rotate || 90}deg, ${colorStops})` }
      }
      // 旧版：gradientColor / gradientType / gradientRotate
      const colors = background.value.gradientColor || []
      if (!colors.length) return {}
      const rotate = background.value.gradientRotate || 90
      if (background.value.gradientType === 'radial') {
        return { backgroundImage: `radial-gradient(${colors.join(', ')})` }
      }
      return { backgroundImage: `linear-gradient(${rotate}deg, ${colors.join(', ')})` }
    }
    return {}
  })

  return {
    backgroundStyle,
  }
}