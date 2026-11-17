/**
 * 「AI 优化全部」的分片与合并。
 *
 * 为什么要分片：整篇一次 HTTP 请求会把所有页的图片 data URL 一起塞进 JSON，
 * 30 页 × 3 张 × 约 300KB 就是几十 MB。模型分批是后端内部的事，兜不住请求体积；
 * 因此前端按 6 页一片发请求，后端仍然无状态。
 *
 * 每片必须携带全篇 `totalPages` 与 `outline`：后端按它们均摊时长、给模型全篇结构；
 * 只用本片 6 页会把这 6 页误当成整篇。
 */

import { extractSlideTitle } from './slide-text.js'

/** 单次 optimize 请求最多携带的页数 */
export const OPTIMIZE_CHUNK_SIZE = 6

/**
 * 按页数切片，保持原顺序。
 *
 * @param {Array<{ index: number, slide: object }>} entries
 * @param {number} size
 * @returns {Array<Array<{ index: number, slide: object }>>}
 */
export function chunkEntries(entries, size = OPTIMIZE_CHUNK_SIZE) {
  const list = Array.isArray(entries) ? entries : []
  const step = Number(size) > 0 ? Math.floor(size) : OPTIMIZE_CHUNK_SIZE
  const chunks = []
  for (let start = 0; start < list.length; start += step) {
    chunks.push(list.slice(start, start + step))
  }
  return chunks
}

/**
 * 全篇大纲：每页一行标题，取不到标题退化为「第 N 页」。
 *
 * @param {Array<{ index: number, slide: object }>} entries
 * @returns {string[]}
 */
export function buildDeckOutline(entries) {
  return (Array.isArray(entries) ? entries : []).map(({ index, slide }) => {
    const title = extractSlideTitle(slide).trim()
    return title || `第 ${index} 页`
  })
}

/**
 * 合并多个分片的 optimize 响应。
 *
 * @param {Array<{ pages?: Array<{ index: number }>, failedIndexes?: number[] }>} results
 * @returns {{ pages: Array<object>, failedIndexes: number[] }} 按页码升序
 */
export function mergeSpeechResults(results) {
  const pages = []
  const failed = new Set()

  for (const result of Array.isArray(results) ? results : []) {
    for (const page of result?.pages ?? []) {
      if (page?.index != null) pages.push(page)
    }
    for (const index of result?.failedIndexes ?? []) {
      failed.add(index)
    }
  }

  // 分片边界理论上不会重复；万一重复，后到的结果覆盖先到的
  const byIndex = new Map(pages.map(page => [page.index, page]))
  return {
    pages: [...byIndex.values()].sort((a, b) => a.index - b.index),
    failedIndexes: [...failed].sort((a, b) => a - b),
  }
}
