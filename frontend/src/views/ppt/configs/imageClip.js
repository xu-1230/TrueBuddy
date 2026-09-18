// 图片裁剪形状配置（参考 PPTist 的 configs/imageClip.ts）
// style 为 CSS clip-path 值；多边形形状提供 createPath 生成 SVG path

export const CLIPPATHS = {
  rect: {
    name: '矩形',
    type: 'rect',
    radius: '0',
    style: '',
  },
  rect2: {
    name: '矩形2',
    type: 'polygon',
    style: 'polygon(0% 0%, 80% 0%, 100% 20%, 100% 100%, 0 100%)',
    createPath: (width, height) => {
      return `M 0 0 L ${width * 0.8} 0 L ${width} ${height * 0.2} L ${width} ${height} L 0 ${height} Z`
    },
  },
  rect3: {
    name: '矩形3',
    type: 'polygon',
    style: 'polygon(0% 0%, 80% 0%, 100% 20%, 100% 100%, 20% 100%, 0% 80%)',
    createPath: (width, height) => {
      return `M 0 0 L ${width * 0.8} 0 L ${width} ${height * 0.2} L ${width} ${height} L ${width * 0.2} ${height} L 0 ${height * 0.8} Z`
    },
  },
  roundRect: {
    name: '圆角矩形',
    type: 'rect',
    radius: '10%',
    style: 'inset(0 0 0 0 round 10% 10% 10% 10%)',
  },
  ellipse: {
    name: '圆形',
    type: 'ellipse',
    style: 'ellipse(50% 50% at 50% 50%)',
  },
  triangle: {
    name: '三角形',
    type: 'polygon',
    style: 'polygon(50% 0%, 0% 100%, 100% 100%)',
    createPath: (width, height) => {
      return `M ${width * 0.5} 0 L 0 ${height} L ${width} ${height} Z`
    },
  },
  triangle2: {
    name: '三角形2',
    type: 'polygon',
    style: 'polygon(50% 100%, 0% 0%, 100% 0%)',
    createPath: (width, height) => {
      return `M ${width * 0.5} ${height} L 0 0 L ${width} 0 Z`
    },
  },
  triangle3: {
    name: '三角形3',
    type: 'polygon',
    style: 'polygon(0% 0%, 0% 100%, 100% 100%)',
    createPath: (width, height) => {
      return `M 0 0 L 0 ${height} L ${width} ${height} Z`
    },
  },
  rhombus: {
    name: '菱形',
    type: 'polygon',
    style: 'polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%)',
    createPath: (width, height) => {
      return `M ${width * 0.5} 0 L ${width} ${height * 0.5} L ${width * 0.5} ${height} L 0 ${height * 0.5} Z`
    },
  },
  hexagon: {
    name: '六边形',
    type: 'polygon',
    style: 'polygon(20% 0%, 80% 0%, 100% 50%, 80% 100%, 20% 100%, 0% 50%)',
    createPath: (width, height) => {
      return `M ${width * 0.2} 0 L ${width * 0.8} 0 L ${width} ${height * 0.5} L ${width * 0.8} ${height} L ${width * 0.2} ${height} L 0 ${height * 0.5} Z`
    },
  },
  heptagon: {
    name: '七边形',
    type: 'polygon',
    style: 'polygon(50% 0%, 89.07% 18.83%, 98.74% 61.13%, 71.70% 95.07%, 28.30% 95.07%, 1.26% 61.13%, 10.93% 18.83%)',
    createPath: (width, height) => {
      const p = [
        [0.5, 0], [0.8907, 0.1883], [0.9874, 0.6113], [0.717, 0.9507],
        [0.283, 0.9507], [0.0126, 0.6113], [0.1093, 0.1883],
      ]
      return p.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x * width} ${y * height}`).join(' ') + ' Z'
    },
  },
  octagon: {
    name: '八边形',
    type: 'polygon',
    style: 'polygon(96.19% 69.13%, 69.13% 96.19%, 30.87% 96.19%, 3.81% 69.13%, 3.81% 30.87%, 30.87% 3.81%, 69.13% 3.81%, 96.19% 30.87%)',
    createPath: (width, height) => {
      const p = [
        [0.9619, 0.6913], [0.6913, 0.9619], [0.3087, 0.9619], [0.0381, 0.6913],
        [0.0381, 0.3087], [0.3087, 0.0381], [0.6913, 0.0381], [0.9619, 0.3087],
      ]
      return p.map(([x, y], i) => `${i === 0 ? 'M' : 'L'} ${x * width} ${y * height}`).join(' ') + ' Z'
    },
  },
  pentagon: {
    name: '五边形',
    type: 'polygon',
    style: 'polygon(50% 0%, 100% 38%, 82% 100%, 18% 100%, 0% 38%)',
    createPath: (width, height) => {
      return `M ${width * 0.5} 0 L ${width} ${0.38 * height} L ${0.82 * width} ${height} L ${0.18 * width} ${height} L 0 ${0.38 * height} Z`
    },
  },
  parallelogram: {
    name: '平行四边形',
    type: 'polygon',
    style: 'polygon(30% 0%, 100% 0%, 70% 100%, 0% 100%)',
    createPath: (width, height) => {
      return `M ${width * 0.3} 0 L ${width} 0 L ${width * 0.7} ${height} L 0 ${height} Z`
    },
  },
  parallelogram2: {
    name: '平行四边形2',
    type: 'polygon',
    style: 'polygon(30% 100%, 100% 100%, 70% 0%, 0% 0%)',
    createPath: (width, height) => {
      return `M ${width * 0.3} ${height} L ${width} ${height} L ${width * 0.7} 0 L 0 0 Z`
    },
  },
  trapezoid: {
    name: '梯形',
    type: 'polygon',
    style: 'polygon(25% 0%, 75% 0%, 100% 100%, 0% 100%)',
    createPath: (width, height) => {
      return `M ${width * 0.25} 0 L ${width * 0.75} 0 L ${width} ${height} L 0 ${height} Z`
    },
  },
  trapezoid2: {
    name: '梯形2',
    type: 'polygon',
    style: 'polygon(0% 0%, 100% 0%, 75% 100%, 25% 100%)',
    createPath: (width, height) => {
      return `M 0 0 L ${width} 0 L ${width * 0.75} ${height} L ${width * 0.25} ${height} Z`
    },
  },
}

// 根据图片裁剪信息生成裁剪样式与图片位置（参考 PPTist 的 useClipImage）
export const getClipInfo = (clip) => {
  if (!clip) {
    return {
      clipShape: CLIPPATHS.rect,
      imgPosition: { top: '0', left: '0', width: '100%', height: '100%' },
    }
  }

  const shape = clip.shape || 'rect'
  const clipShape = CLIPPATHS[shape] || CLIPPATHS.rect

  const range = Array.isArray(clip.range) && clip.range.length === 2 ? clip.range : [[0, 0], [100, 100]]
  const [start, end] = range

  const widthScale = (end[0] - start[0]) / 100
  const heightScale = (end[1] - start[1]) / 100
  const left = start[0] / widthScale
  const top = start[1] / heightScale

  return {
    clipShape,
    imgPosition: {
      left: -left + '%',
      top: -top + '%',
      width: 100 / widthScale + '%',
      height: 100 / heightScale + '%',
    },
  }
}