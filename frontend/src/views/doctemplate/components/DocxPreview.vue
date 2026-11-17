<template>
  <div class="docx-preview" :class="{ 'docx-preview--picking': picking }">
    <!-- 渲染容器：docx-preview 会把内容与样式直接写入该节点 -->
    <div ref="bodyRef" class="docx-preview__body" @click="handlePick"></div>

    <!-- 加载态 -->
    <div v-if="loading" class="docx-preview__overlay">
      <span class="docx-preview__spinner"></span>
      <p class="docx-preview__overlay-title">正在渲染参考文档…</p>
      <p class="docx-preview__overlay-desc">{{ fileName }}</p>
    </div>

    <!-- 错误态 -->
    <div v-else-if="error" class="docx-preview__overlay docx-preview__overlay--error">
      <el-icon class="docx-preview__overlay-icon"><WarningFilled /></el-icon>
      <p class="docx-preview__overlay-title">参考文档渲染失败</p>
      <p class="docx-preview__overlay-desc">{{ error }}</p>
      <button type="button" class="docx-preview__retry" @click="render">
        <el-icon><Refresh /></el-icon>
        <span>重新加载</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { renderAsync } from 'docx-preview'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  /** 参考文档地址（public 下的静态资源或后端返回的文件 URL） */
  src: { type: String, default: '' },
  /** 是否开启「元素选择」模式：开启后点击文档内容即可选中对应元素 */
  picking: { type: Boolean, default: false },
  /** 当前已选中的 docx 定位列表（p:N / t:N / t:N/r:R/c:C），用于高亮同步 */
  selectedLocators: { type: Array, default: () => [] },
})

const emit = defineEmits(['pick'])

const bodyRef = ref(null)
const loading = ref(false)
const error = ref('')

/** 已下载的 Blob 缓存，避免同一文档重复请求 */
const blobCache = new Map()
/** 渲染令牌：切换文档时让旧的渲染任务结果作废，避免竞态覆盖 */
let renderToken = 0

/* ---------- 元素选择 ---------- */
/** 可被选中的文档元素（docx-preview 渲染出的语义标签） */
const PICKABLE_SELECTOR = 'p, h1, h2, h3, h4, h5, h6, table, tr, td, th, img, li, ol, ul'
const TAG_LABELS = {
  p: '段落',
  h1: '标题 1',
  h2: '标题 2',
  h3: '标题 3',
  h4: '标题 4',
  h5: '标题 5',
  h6: '标题 6',
  table: '表格',
  tr: '表格行',
  td: '单元格',
  th: '表头',
  img: '图片',
  li: '列表项',
  ul: '无序列表',
  ol: '有序列表',
}

/** locator → DOM 元素；渲染后重建，用于按父组件选中的 locator 同步高亮 */
const locatorEls = new Map()

function describeElement(el) {
  const tag = el.tagName.toLowerCase()
  const name = TAG_LABELS[tag] || tag
  const locator = el.dataset.docxLoc || ''
  // 合并单元格在 docx-preview DOM 里的行列坐标与 docx-mcp 逻辑网格可能不同：
  // 前端仍用 DOM locator 做高亮，送给 LLM 时降级为表格级定位 + 单元格文本。
  const table = el.closest('table[data-docx-loc]')
  const hintLocator = table?.dataset.docxLoc || locator
  if (tag === 'img') {
    const alt = el.getAttribute('alt') || '未命名图片'
    return { tag, name, locator, hintLocator, label: `${name} · ${alt}`, text: alt }
  }
  if (tag === 'table') {
    const rows = el.rows?.length ?? 0
    const cols = el.rows?.[0]?.cells?.length ?? 0
    return {
      tag,
      name,
      locator,
      hintLocator,
      label: `${name} · ${rows} 行 × ${cols} 列`,
      text: '',
    }
  }
  const text = (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 30)
  return { tag, name, locator, hintLocator, label: text ? `${name} · ${text}` : name, text }
}

function handlePick(event) {
  if (!props.picking) return
  // 选择模式下阻止文档内的超链接跳转等默认行为
  event.preventDefault()
  const target = event.target
  if (!(target instanceof HTMLElement)) return
  const picked = target.closest(PICKABLE_SELECTOR)
  if (!picked || !bodyRef.value?.contains(picked)) return
  // 以最近的有 docx 定位的元素为准：点在单元格/表格边缘时也能落到可编辑位置
  const el = picked.closest('[data-docx-loc]')
  if (!el) return
  emit('pick', describeElement(el))
}

/** 父组件的选中列表是唯一真相源：按 locator 同步 docx-picked 高亮 */
function applyHighlights() {
  const selected = new Set(props.selectedLocators || [])
  locatorEls.forEach((el, locator) => {
    el.classList.toggle('docx-picked', selected.has(locator))
  })
}

/**
 * 给 docx-preview 渲染出的正文标 docx-mcp 定位：
 * - 正文段落 p:N（只数 article 直属 p，跨页按顺序累加）
 * - 表格 t:N（深度优先全局编号，含嵌套表）
 * - 单元格 t:N/r:R/c:C，单元格内段落 .../p:P
 * 页眉页脚不参与；footer 是 article 的兄弟节点，不会被遍历。
 */
function annotateLocators(container) {
  locatorEls.clear()
  container.querySelectorAll('[data-docx-loc]').forEach((el) => el.removeAttribute('data-docx-loc'))

  let paragraphIndex = 0
  let tableIndex = 0

  const setLoc = (el, locator) => {
    el.dataset.docxLoc = locator
    locatorEls.set(locator, el)
  }

  const annotateCell = (cell, locator) => {
    setLoc(cell, locator)
    let cellParagraph = 0
    for (const child of cell.children) {
      if (child.tagName === 'P') {
        setLoc(child, `${locator}/p:${cellParagraph++}`)
      } else if (child.tagName === 'TABLE') {
        annotateTable(child)
      }
    }
  }

  /** 只取本表的直接行；table.rows 可能把嵌套表的行也带出来 */
  const directRows = (table) => {
    const rows = []
    for (const child of table.children) {
      if (child.tagName === 'TR') {
        rows.push(child)
      } else if (child.tagName === 'THEAD' || child.tagName === 'TBODY' || child.tagName === 'TFOOT') {
        for (const row of child.children) {
          if (row.tagName === 'TR') rows.push(row)
        }
      }
    }
    return rows
  }

  const annotateTable = (table) => {
    const tableLocator = `t:${tableIndex++}`
    setLoc(table, tableLocator)
    const occupied = new Set()
    let rowIndex = 0
    for (const row of directRows(table)) {
      let column = 0
      for (const cell of row.cells) {
        while (occupied.has(`${rowIndex}:${column}`)) column += 1
        const rowSpan = Math.max(1, cell.rowSpan || 1)
        const colSpan = Math.max(1, cell.colSpan || 1)
        for (let r = 0; r < rowSpan; r += 1) {
          for (let c = 0; c < colSpan; c += 1) {
            occupied.add(`${rowIndex + r}:${column + c}`)
          }
        }
        annotateCell(cell, `${tableLocator}/r:${rowIndex}/c:${column}`)
        column += colSpan
      }
      rowIndex += 1
    }
  }

  const sections = container.querySelectorAll('.docx-wrapper > section.docx')
  for (const section of sections) {
    const article = section.querySelector(':scope > article')
    if (!article) continue
    for (const child of article.children) {
      if (child.tagName === 'P') {
        setLoc(child, `p:${paragraphIndex++}`)
      } else if (child.tagName === 'TABLE') {
        annotateTable(child)
      }
    }
  }

  applyHighlights()
}

const fileName = computed(() => {
  if (!props.src) return ''
  const segments = props.src.split('/')
  return decodeURIComponent(segments[segments.length - 1] || props.src)
})

async function loadBlob(src) {
  if (blobCache.has(src)) return blobCache.get(src)
  const response = await fetch(src)
  if (!response.ok) throw new Error(`请求失败（HTTP ${response.status}）`)
  const blob = await response.blob()
  blobCache.set(src, blob)
  return blob
}

async function render() {
  const container = bodyRef.value
  const src = props.src
  if (!container || !src) {
    locatorEls.clear()
    return
  }

  const token = (renderToken += 1)
  loading.value = true
  error.value = ''
  locatorEls.clear()
  container.innerHTML = ''

  try {
    const blob = await loadBlob(src)
    if (token !== renderToken) return
    await renderAsync(blob, container, undefined, {
      className: 'docx',
      inWrapper: true,
      breakPages: true,
      renderHeaders: true,
      renderFooters: true,
      experimental: true,
      ignoreLastRenderedPageBreak: false,
    })
    if (token !== renderToken) return
    annotateLocators(container)
  } catch (err) {
    if (token !== renderToken) return
    console.error('[DocxPreview] 渲染文档失败：', err)
    error.value = err?.message || '未知错误'
  } finally {
    if (token === renderToken) loading.value = false
  }
}

watch(() => props.src, render)
watch(() => props.selectedLocators, applyHighlights, { deep: true })

onMounted(render)

onBeforeUnmount(() => {
  // 作废未完成的渲染任务并清空容器，避免残留 DOM 与内存泄漏
  renderToken += 1
  locatorEls.clear()
  if (bodyRef.value) bodyRef.value.innerHTML = ''
})
</script>

<style scoped>
.docx-preview {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--tb-bg-sunken);
}

.docx-preview__body {
  flex: 1;
  min-height: 0;
  padding: 1.25rem;
  overflow: auto;
  background-color: var(--tb-bg-sunken);
  background-image: radial-gradient(var(--tb-border) 1px, transparent 1px);
  background-size: 22px 22px;
}

/* docx-preview 生成的是真实 DOM，需用 :deep 覆盖默认样式 */
.docx-preview__body :deep(.docx-wrapper) {
  padding: 0;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.25rem;
}

.docx-preview__body :deep(.docx-wrapper > section.docx) {
  margin: 0;
  border-radius: 0;
  box-shadow: var(--tb-shadow-3);
}

/* ---------- 元素选择模式 ---------- */
.docx-preview--picking .docx-preview__body {
  cursor: crosshair;
  user-select: none;
}

/* 悬浮预览：浅橙红描边（已选中的元素不参与） */
.docx-preview--picking
  .docx-preview__body
  :deep(:is(p, h1, h2, h3, h4, h5, h6, table, tr, td, th, li, img):hover:not(.docx-picked)) {
  outline: 2px solid var(--tb-primary-border);
  outline-offset: 1px;
  background-color: var(--tb-primary-soft);
}

/* 已选中：实心橙红描边 + 淡色底 */
.docx-preview__body :deep(.docx-picked) {
  outline: 2px solid var(--tb-primary);
  outline-offset: 1px;
  background-color: var(--tb-primary-soft);
  border-radius: 0;
}

.docx-preview__overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1.5rem;
  text-align: center;
  background: color-mix(in oklab, var(--tb-bg-page) 92%, transparent);
  backdrop-filter: blur(2px);
}

.docx-preview__spinner {
  width: 30px;
  height: 30px;
  margin-bottom: 0.35rem;
  border-radius: 50%;
  border: 3px solid var(--tb-primary-border);
  border-top-color: var(--tb-primary);
  animation: docx-spin 0.8s linear infinite;
}

.docx-preview__overlay-title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--tb-text-primary);
}

.docx-preview__overlay-desc {
  margin: 0;
  max-width: 320px;
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
  word-break: break-all;
}

.docx-preview__overlay-icon {
  font-size: 38px;
  color: var(--tb-accent);
}

.docx-preview__retry {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.6rem;
  height: 32px;
  padding: 0 0.9rem;
  border: var(--tb-border-width) solid var(--tb-primary-border);
  border-radius: 0;
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.docx-preview__retry:hover {
  background: var(--tb-primary-border);
  border-color: var(--tb-primary-border);
}

.docx-preview__body::-webkit-scrollbar {
  width: 8px;
}

.docx-preview__body::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--tb-border-strong);
}

@keyframes docx-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
