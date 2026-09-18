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

/** 当前选中的 DOM 元素（不参与响应式，仅用于增删高亮 class） */
let pickedEl = null

function clearPicked(notify = false) {
  if (!pickedEl) return
  pickedEl.classList.remove('docx-picked')
  pickedEl = null
  if (notify) emit('pick', null)
}

function describeElement(el) {
  const tag = el.tagName.toLowerCase()
  const name = TAG_LABELS[tag] || tag
  if (tag === 'img') {
    const alt = el.getAttribute('alt') || '未命名图片'
    return { tag, name, label: `${name} · ${alt}`, text: alt }
  }
  if (tag === 'table') {
    const rows = el.rows?.length ?? 0
    const cols = el.rows?.[0]?.cells?.length ?? 0
    return { tag, name, label: `${name} · ${rows} 行 × ${cols} 列`, text: '' }
  }
  const text = (el.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 30)
  return { tag, name, label: text ? `${name} · ${text}` : name, text }
}

function handlePick(event) {
  if (!props.picking) return
  // 选择模式下阻止文档内的超链接跳转等默认行为
  event.preventDefault()
  const target = event.target
  if (!(target instanceof HTMLElement)) return
  const el = target.closest(PICKABLE_SELECTOR)
  if (!el || !bodyRef.value?.contains(el)) return
  clearPicked()
  el.classList.add('docx-picked')
  pickedEl = el
  emit('pick', describeElement(el))
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
  if (!container || !src) return

  const token = (renderToken += 1)
  loading.value = true
  error.value = ''
  // 重新渲染会替换整棵 DOM，旧的选择结果随之失效
  clearPicked(true)
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
  } catch (err) {
    if (token !== renderToken) return
    console.error('[DocxPreview] 渲染文档失败：', err)
    error.value = err?.message || '未知错误'
  } finally {
    if (token === renderToken) loading.value = false
  }
}

watch(() => props.src, render)

// 退出选择模式时丢弃当前选中项，避免残留高亮
watch(
  () => props.picking,
  (value) => {
    if (!value) clearPicked(true)
  },
)

onMounted(render)

onBeforeUnmount(() => {
  // 作废未完成的渲染任务并清空容器，避免残留 DOM 与内存泄漏
  renderToken += 1
  clearPicked()
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
  background: #eef0f4;
}

.docx-preview__body {
  flex: 1;
  min-height: 0;
  padding: 1.25rem;
  overflow: auto;
  background-color: #fbfbfc;
  background-image: radial-gradient(#dfe2e8 1px, transparent 1px);
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
  border-radius: 4px;
  box-shadow: 0 10px 26px rgba(17, 24, 39, 0.12);
}

/* ---------- 元素选择模式 ---------- */
.docx-preview--picking .docx-preview__body {
  cursor: crosshair;
  user-select: none;
}

/* 悬浮预览：浅靛蓝描边（已选中的元素不参与） */
.docx-preview--picking
  .docx-preview__body
  :deep(:is(p, h1, h2, h3, h4, h5, h6, table, tr, td, th, li, img):hover:not(.docx-picked)) {
  outline: 2px solid #a5b4fc;
  outline-offset: 1px;
  background-color: rgba(99, 102, 241, 0.06);
}

/* 已选中：实心靛蓝描边 + 淡色底 */
.docx-preview__body :deep(.docx-picked) {
  outline: 2px solid #6366f1;
  outline-offset: 1px;
  background-color: rgba(99, 102, 241, 0.1);
  border-radius: 2px;
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
  background: rgba(251, 251, 252, 0.92);
  backdrop-filter: blur(2px);
}

.docx-preview__spinner {
  width: 30px;
  height: 30px;
  margin-bottom: 0.35rem;
  border-radius: 50%;
  border: 3px solid #e0e1fd;
  border-top-color: var(--primary, #6366f1);
  animation: docx-spin 0.8s linear infinite;
}

.docx-preview__overlay-title {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
}

.docx-preview__overlay-desc {
  margin: 0;
  max-width: 320px;
  font-size: 0.78rem;
  color: #9ca3af;
  word-break: break-all;
}

.docx-preview__overlay-icon {
  font-size: 38px;
  color: #f59e0b;
}

.docx-preview__retry {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.6rem;
  height: 32px;
  padding: 0 0.9rem;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.docx-preview__retry:hover {
  background: #e0e7ff;
  border-color: #a5b4fc;
}

.docx-preview__body::-webkit-scrollbar {
  width: 8px;
}

.docx-preview__body::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #d8dade;
}

@keyframes docx-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
