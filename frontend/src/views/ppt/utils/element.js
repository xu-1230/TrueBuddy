// 生成随机元素 ID（替代 nanoid）
export const generateId = (length = 10) => {
  const CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let id = ''
  for (let i = 0; i < length; i++) {
    id += CHARS.charAt(Math.floor(Math.random() * CHARS.length))
  }
  return id
}

// hex 颜色转 rgba（替代 tinycolor）
export const hexToRgba = (hex, alpha) => {
  let _hex = hex.replace('#', '')
  if (_hex.length === 3) {
    _hex = _hex.split('').map(c => c + c).join('')
  }
  const r = parseInt(_hex.substring(0, 2), 16)
  const g = parseInt(_hex.substring(2, 4), 16)
  const b = parseInt(_hex.substring(4, 6), 16)
  if ([r, g, b].some(v => Number.isNaN(v))) return hex
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

/**
 * 根据表格的主题色，获取对应用于配色的子颜色
 * @param themeColor 主题色
 */
export const getTableSubThemeColor = (themeColor) => {
  return [
    hexToRgba(themeColor, 0.3),
    hexToRgba(themeColor, 0.1),
  ]
}

/**
 * 获取线条元素路径字符串
 * @param element 线条元素
 */
export const getLineElementPath = (element) => {
  const start = element.start.join(',')
  const end = element.end.join(',')
  if (element.broken) {
    const mid = element.broken.join(',')
    return `M${start} L${mid} L${end}`
  }
  else if (element.curve) {
    const mid = element.curve.join(',')
    return `M${start} Q${mid} ${end}`
  }
  else if (element.cubic) {
    const [c1, c2] = element.cubic
    const p1 = c1.join(',')
    const p2 = c2.join(',')
    return `M${start} C${p1} ${p2} ${end}`
  }
  return `M${start} L${end}`
}