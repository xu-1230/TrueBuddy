/**
 * 「AI 优化全部」分片口径（Node 内置 `node:test`）。
 *
 * 锁住：每 6 页一片、全篇大纲按整篇生成（不是按片）、分片结果按页码合并去重。
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  OPTIMIZE_CHUNK_SIZE,
  buildDeckOutline,
  chunkEntries,
  mergeSpeechResults,
} from '../src/views/ppt/utils/speech-chunks.js'

const entries = count => Array.from({ length: count }, (_, index) => ({
  index: index + 1,
  slide: { id: `slide-${index + 1}`, elements: [] },
}))

test('分片：每 6 页一片，最后一片允许不足', () => {
  const chunks = chunkEntries(entries(13))
  assert.deepEqual(chunks.map(chunk => chunk.length), [OPTIMIZE_CHUNK_SIZE, OPTIMIZE_CHUNK_SIZE, 1])
  assert.deepEqual(chunks.flat().map(entry => entry.index), entries(13).map(entry => entry.index))
})

test('分片：空清单 / 非法片大小回退默认值', () => {
  assert.deepEqual(chunkEntries([]), [])
  assert.deepEqual(chunkEntries(null), [])
  assert.deepEqual(chunkEntries(entries(7), 0).map(chunk => chunk.length), [6, 1])
})

test('全篇大纲：取每页标题，取不到退化为第 N 页', () => {
  const list = [
    { index: 1, slide: { elements: [{ type: 'text', content: '封面', top: 0, left: 0 }] } },
    { index: 2, slide: { elements: [{ type: 'image', src: 'data:image/png;base64,AAAA' }] } },
  ]
  assert.deepEqual(buildDeckOutline(list), ['封面', '第 2 页'])
  assert.deepEqual(buildDeckOutline([]), [])
})

test('合并分片结果：按页码升序，失败页码取并集', () => {
  const merged = mergeSpeechResults([
    { pages: [{ index: 3, remark: 'c' }, { index: 1, remark: 'a' }], failedIndexes: [2] },
    { pages: [{ index: 2, remark: 'b' }], failedIndexes: [4, 2] },
    null,
  ])

  assert.deepEqual(merged.pages.map(page => page.index), [1, 2, 3])
  assert.deepEqual(merged.failedIndexes, [2, 4])
})
