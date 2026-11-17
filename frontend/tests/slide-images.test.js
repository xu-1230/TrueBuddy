/**
 * PPT 图片选图口径（Node 内置 `node:test`）。
 *
 * 锁住 docs/specs/2026-11-18-PPT讲稿多模态图片理解.md 的 D1–D4：
 * 只取图片元素、最多 3 张、按面积降序、过滤小于 1% 的装饰图、同 src 去重、
 * EMF/WMF 与远程 URL 不送模型；裁剪范围按百分比换算成源图矩形。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_IMAGES_PER_PAGE,
  computeClipSourceRect,
  selectSlideImageElements,
} from '../src/views/ppt/utils/slide-images.js'

const DATA_URL = 'data:image/png;base64,AAAA'
const PAGE_RATIO = 0.5625 // 1000 × 562.5，1% = 5625

function image(width, height, src = DATA_URL, extra = {}) {
  return { type: 'image', id: `img-${width}-${height}`, src, width, height, left: 0, top: 0, ...extra }
}

test('选图：只取图片元素，按面积降序，过滤小于页面 1% 的小图', () => {
  const slide = {
    elements: [
      image(20, 20, 'data:image/png;base64,SMALL'), // 400 < 5625，装饰图标
      image(400, 300, 'data:image/png;base64,BIG'),
      image(200, 200, 'data:image/png;base64,MEDIUM'),
      { type: 'text', content: '正文', width: 800, height: 100 },
    ],
  }

  const picked = selectSlideImageElements(slide, PAGE_RATIO)
  assert.deepEqual(picked.map(el => el.src), [
    'data:image/png;base64,BIG',
    'data:image/png;base64,MEDIUM',
  ])
})

test('选图：最多 3 张，同 src 去重', () => {
  const slide = {
    elements: [
      image(400, 400, 'data:image/png;base64,A'),
      image(400, 400, 'data:image/png;base64,A'),
      image(300, 300, 'data:image/png;base64,B'),
      image(250, 250, 'data:image/png;base64,C'),
      image(200, 200, 'data:image/png;base64,D'),
    ],
  }

  const picked = selectSlideImageElements(slide, PAGE_RATIO)
  assert.equal(picked.length, MAX_IMAGES_PER_PAGE)
  assert.deepEqual(picked.map(el => el.src), [
    'data:image/png;base64,A',
    'data:image/png;base64,B',
    'data:image/png;base64,C',
  ])
})

test('选图：EMF/WMF、远程 URL、非 data URL 与无尺寸图片都不送模型', () => {
  const slide = {
    elements: [
      image(400, 300, 'data:image/x-emf;base64,AAAA'),
      image(400, 300, 'data:image/x-wmf;base64,AAAA'),
      image(400, 300, 'https://example.com/a.png'),
      image(400, 300, 'blob:http://localhost/abc'),
      { type: 'image', src: DATA_URL }, // 没有 width/height
    ],
  }

  assert.deepEqual(selectSlideImageElements(slide, PAGE_RATIO), [])
})

test('裁剪：clip.range 的百分比换算为源图矩形；无裁剪时取整图', () => {
  assert.deepEqual(
    computeClipSourceRect({ clip: { range: [[25, 10], [75, 90]] } }, 1000, 500),
    { sx: 250, sy: 50, sw: 500, sh: 400 },
  )
  assert.deepEqual(
    computeClipSourceRect({}, 1000, 500),
    { sx: 0, sy: 0, sw: 1000, sh: 500 },
  )
  // 退化 range 回退整图，不能压出一张 1×1 的无效图
  assert.deepEqual(
    computeClipSourceRect({ clip: { range: [[80, 80], [20, 20]] } }, 1000, 500),
    { sx: 0, sy: 0, sw: 1000, sh: 500 },
  )
})
