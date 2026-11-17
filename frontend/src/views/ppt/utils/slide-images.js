/**
 * 幻灯片图片提取与压缩：把一页里值得给多模态模型看的图片压成 JPEG data URL。
 *
 * 口径（见 docs/specs/2026-11-18-PPT讲稿多模态图片理解.md）：
 * - 只取 `type = image` 且 `src` 是 `data:image/*` 的元素；背景图与形状图片填充不发；
 * - 每页最多 3 张，按元素面积降序，面积小于页面 1% 的图标/装饰图过滤，同 src 去重；
 * - 最长边 1280px、JPEG 0.82、单张 data URL 不超过 420KB；有 clip.range 时按可见区域裁剪；
 * - EMF/WMF 等浏览器解不了的格式静默跳过（返回 null），不阻断其他图片。
 *
 * 纯函数（选图 / 裁剪坐标）与浏览器能力（Image / canvas）分开导出，便于 node:test 锁定口径。
 */

import { VIEWPORT_SIZE } from '../configs/canvas.js'

/** 每页最多送模型的图片数 */
export const MAX_IMAGES_PER_PAGE = 3

/** 小于页面面积 1% 的图片视为图标/装饰，不送模型 */
export const MIN_IMAGE_AREA_RATIO = 0.01

/** 压缩后最长边（像素） */
export const MAX_IMAGE_EDGE = 1280

/** JPEG 编码质量 */
export const JPEG_QUALITY = 0.82

/** 单张 data URL 的字符上限（约 300KB 二进制） */
export const MAX_IMAGE_DATA_URL_LENGTH = 420 * 1024

/** 浏览器确定无法解码的矢量格式：直接跳过，连 <img> 都不尝试 */
const UNSUPPORTED_DATA_URL = /^data:image\/(x-emf|x-wmf)[;,]/i

/** 压缩结果缓存：key 含裁剪范围，同一张图被多次引用时只压一次 */
const cache = new Map()

/** 导入新 PPT 时清空缓存，避免上一份文档的 data URL 长期占内存 */
export function clearSlideImageCache() {
  cache.clear()
}

/** 压缩缓存 key：同一 src 不同裁剪范围要分别压 */
function cacheKeyOf(element) {
  const range = element?.clip?.range
  return `${element?.src || ''}|${Array.isArray(range) ? JSON.stringify(range) : ''}`
}

/**
 * 选出一页里要送模型的图片元素（纯函数）。
 *
 * @param {object} slide PPTist 幻灯片
 * @param {number} viewportRatio 高 / 宽（默认 16:9 = 0.5625）
 * @returns {object[]} 按面积降序、最多 3 个图片元素
 */
export function selectSlideImageElements(slide, viewportRatio = 0.5625) {
  const ratio = Number(viewportRatio) > 0 ? Number(viewportRatio) : 0.5625
  const pageArea = VIEWPORT_SIZE * VIEWPORT_SIZE * ratio
  const minArea = pageArea * MIN_IMAGE_AREA_RATIO
  const seen = new Set()

  return (Array.isArray(slide?.elements) ? slide.elements : [])
    .filter(element => {
      const src = element?.src
      return element?.type === 'image'
        && typeof src === 'string'
        && src.startsWith('data:image/')
        && !UNSUPPORTED_DATA_URL.test(src)
    })
    .map(element => ({
      element,
      area: Math.max(0, Number(element.width) || 0) * Math.max(0, Number(element.height) || 0),
    }))
    .filter(({ area }) => area >= minArea)
    .sort((a, b) => b.area - a.area)
    .filter(({ element }) => {
      if (seen.has(element.src)) return false
      seen.add(element.src)
      return true
    })
    .slice(0, MAX_IMAGES_PER_PAGE)
    .map(({ element }) => element)
}

/**
 * 计算 crop 的源矩形（纯函数）。`clip.range` 是 [[左, 上], [右, 下]] 的百分比。
 *
 * @param {object} element 图片元素
 * @param {number} naturalWidth 原图宽
 * @param {number} naturalHeight 原图高
 * @returns {{ sx: number, sy: number, sw: number, sh: number }}
 */
export function computeClipSourceRect(element, naturalWidth, naturalHeight) {
  const width = Math.max(1, Number(naturalWidth) || 1)
  const height = Math.max(1, Number(naturalHeight) || 1)
  const range = element?.clip?.range

  if (!Array.isArray(range) || range.length !== 2 || !Array.isArray(range[0]) || !Array.isArray(range[1])) {
    return { sx: 0, sy: 0, sw: width, sh: height }
  }

  const clamp = value => Math.min(100, Math.max(0, Number(value) || 0))
  const left = clamp(range[0][0])
  const top = clamp(range[0][1])
  const right = clamp(range[1][0])
  const bottom = clamp(range[1][1])

  // 退化范围（右≤左 / 下≤上）按「未裁剪」处理，避免压出一张 1×1 的无效图
  if (right <= left || bottom <= top) {
    return { sx: 0, sy: 0, sw: width, sh: height }
  }

  const sx = (width * left) / 100
  const sy = (height * top) / 100
  const sw = Math.max(1, (width * (right - left)) / 100)
  const sh = Math.max(1, (height * (bottom - top)) / 100)
  return { sx, sy, sw, sh }
}

/** 浏览器里加载一张 data URL 图片；失败（EMF/WMF、损坏数据）返回 null */
function loadImage(src) {
  if (typeof window === 'undefined' || typeof window.Image !== 'function') return Promise.resolve(null)
  return new Promise(resolve => {
    const image = new window.Image()
    image.onload = () => resolve(image)
    image.onerror = () => resolve(null)
    image.src = src
  })
}

/** 把源图按裁剪范围画到目标尺寸，返回 JPEG data URL；编码失败返回 null */
function drawToDataUrl(image, rect, width, height, quality) {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) return null

  // 透明图转 JPEG 会变黑：先铺白底，与课件/演示的默认底色一致
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, width, height)
  context.drawImage(image, rect.sx, rect.sy, rect.sw, rect.sh, 0, 0, width, height)
  return canvas.toDataURL('image/jpeg', quality)
}

/** 最长边限制到 MAX_IMAGE_EDGE，保持宽高比 */
function fitSize(rect) {
  const longest = Math.max(rect.sw, rect.sh)
  const scale = longest > MAX_IMAGE_EDGE ? MAX_IMAGE_EDGE / longest : 1
  return {
    width: Math.max(1, Math.round(rect.sw * scale)),
    height: Math.max(1, Math.round(rect.sh * scale)),
  }
}

/**
 * 压缩单个图片元素（浏览器能力）。
 *
 * @param {object} element 图片元素
 * @returns {Promise<string|null>} JPEG data URL；无法解码/超限时 null
 */
export async function compressSlideImage(element) {
  const src = element?.src
  if (typeof src !== 'string' || !src.startsWith('data:image/') || UNSUPPORTED_DATA_URL.test(src)) {
    return null
  }

  const key = cacheKeyOf(element)
  if (cache.has(key)) return cache.get(key)

  const task = (async () => {
    try {
      const image = await loadImage(src)
      if (!image?.naturalWidth || !image?.naturalHeight) return null

      const rect = computeClipSourceRect(element, image.naturalWidth, image.naturalHeight)
      let { width, height } = fitSize(rect)
      let quality = JPEG_QUALITY

      // 先降质量、再降尺寸：优先保住文字/图表的清晰度，实在超限才缩边
      for (let attempt = 0; attempt < 8; attempt += 1) {
        const dataUrl = drawToDataUrl(image, rect, width, height, quality)
        if (dataUrl && dataUrl.length <= MAX_IMAGE_DATA_URL_LENGTH) return dataUrl

        if (quality > 0.6) {
          quality = Math.max(0.6, quality - 0.08)
        } else {
          width = Math.max(1, Math.round(width * 0.8))
          height = Math.max(1, Math.round(height * 0.8))
        }
      }
      return null
    } catch {
      // SVG 外链导致的 taint、canvas 内存不足等：一张图失败不能拖垮整页
      return null
    }
  })()

  cache.set(key, task)
  return task
}

/**
 * 一页要送模型的图片 data URL 列表（按面积降序，最多 3 张）。
 *
 * @param {object} slide PPTist 幻灯片
 * @param {number} viewportRatio 高 / 宽
 * @returns {Promise<string[]>}
 */
export async function buildSlideImages(slide, viewportRatio = 0.5625) {
  const elements = selectSlideImageElements(slide, viewportRatio)
  if (!elements.length) return []
  if (typeof document === 'undefined') return []

  const images = await Promise.all(elements.map(element => compressSlideImage(element)))
  return images.filter(Boolean)
}
