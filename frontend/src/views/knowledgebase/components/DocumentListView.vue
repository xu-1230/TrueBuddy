<template>
  <div class="document-list-view">
    <div class="kb-toolbar">
      <div class="kb-toolbar__left">
        <el-button text @click="$emit('back')">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <el-divider direction="vertical" />
        <el-icon class="kb-toolbar__folder-icon"><FolderOpened /></el-icon>
        <span class="kb-toolbar__title" :title="space?.name">{{ space?.name }}</span>
        <el-tag v-if="readonly" size="small" type="info" class="kb-toolbar__readonly-tag">
          公有 · 只读
        </el-tag>
      </div>
    </div>

    <!-- 左右分栏：左侧文档列表，右侧分片详情 -->
    <div class="doc-workspace">
      <aside class="doc-sidebar">
        <div class="doc-sidebar__header">
          <el-icon><Document /></el-icon>
          <span>文档列表</span>
          <el-tooltip v-if="!readonly" content="添加文档" placement="top">
            <el-icon class="doc-sidebar__add" @click="$emit('add-document')">
              <Plus />
            </el-icon>
          </el-tooltip>
        </div>
        <ul
          v-loading="loading && documents.length > 0"
          class="doc-list"
          :aria-busy="loading"
        >
          <template v-if="loading && !documents.length">
            <li
              v-for="n in DOC_SKELETON_COUNT"
              :key="n"
              class="doc-list__item doc-list__item--skeleton"
              aria-hidden="true"
            >
              <span class="doc-skeleton__block doc-skeleton__icon"></span>
              <span
                class="doc-skeleton__block doc-skeleton__line"
                :class="`is-${((n - 1) % 3) + 1}`"
              ></span>
            </li>
          </template>
          <template v-else>
            <li
              v-for="doc in documents"
              :key="doc.id"
              class="doc-list__item"
              :class="{ 'doc-list__item--active': isActive(doc) }"
              @click="$emit('select-document', doc)"
            >
              <span
                class="doc-list__icon"
                :class="`doc-list__icon--${doc.type.toLowerCase()}`"
                :title="doc.type"
              >
                <el-icon><component :is="docIcon(doc.type)" /></el-icon>
              </span>
              <span class="doc-list__name" :title="doc.name">{{ doc.name }}</span>
            </li>
          </template>
        </ul>
        <el-empty
          v-if="!loading && !documents.length"
          :image-size="72"
          :description="readonly ? '该空间暂无文档' : '暂无文档，点击添加'"
        />
        <p v-if="loading" class="kb-sr-only" role="status">正在加载文档列表…</p>
      </aside>

      <section class="doc-detail">
        <template v-if="activeDocument">
          <header class="doc-detail__header">
            <div class="doc-detail__title">
              <span class="doc-detail__label">文档：</span>
              <span class="doc-detail__name" :title="activeDocument.name">
                {{ activeDocument.name }}
              </span>
            </div>
            <div class="doc-detail__meta">
              <el-tag size="small" :type="statusMeta(activeDocument).tagType">
                {{ statusMeta(activeDocument).label }}
              </el-tag>
              <span class="doc-detail__divider">|</span>
              <span>分片数：{{ chunkLoading ? '—' : chunks.length }}</span>
              <el-icon
                v-if="!readonly"
                class="doc-detail__delete"
                title="删除文档"
                @click="onDelete(activeDocument)"
              >
                <Delete />
              </el-icon>
            </div>
          </header>

          <el-alert
            v-if="isPendingConfirm(activeDocument)"
            class="doc-detail__hint"
            title="该文档已解析出分片，但尚未确认向量化，暂不可被知识库检索"
            type="warning"
            :closable="false"
            show-icon
          />

          <div
            v-loading="chunkLoading && chunks.length > 0"
            class="chunk-list"
            :aria-busy="chunkLoading"
          >
            <template v-if="chunkLoading && !chunks.length">
              <div
                v-for="n in CHUNK_SKELETON_COUNT"
                :key="n"
                class="chunk-card chunk-card--skeleton"
                aria-hidden="true"
              >
                <span
                  v-for="line in 4"
                  :key="line"
                  class="chunk-skeleton__block chunk-skeleton__line"
                  :class="`is-${line}`"
                ></span>
              </div>
            </template>
            <template v-else>
              <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
                <MarkdownViewer :value="chunk.content" class="chunk-card__content" />
              </div>
            </template>
            <el-empty
              v-if="!chunkLoading && !chunks.length"
              :image-size="90"
              description="该文档暂无分片数据"
            />
            <p v-if="chunkLoading" class="kb-sr-only" role="status">正在加载文档分片…</p>
          </div>
        </template>
        <el-empty v-else :image-size="110" description="请选择左侧文档查看分片内容" />
      </section>
    </div>
  </div>
</template>

<script setup>
import {
  ArrowLeft,
  Delete,
  Document,
  Files,
  FolderOpened,
  Notebook,
  Picture,
  Plus,
  Tickets,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import { DOC_STATUS, docStatusMeta } from '../constants'

/** 文档类型 → 图标（与 DocTemplateView 保持一致） */
const DOC_ICON_MAP = {
  pdf: Tickets,
  word: Document,
  doc: Document,
  docx: Document,
  markdown: Notebook,
  md: Notebook,
  ppt: Picture,
  pptx: Picture,
  txt: Files,
}

function docIcon(type) {
  return DOC_ICON_MAP[String(type).toLowerCase()] ?? Files
}

const props = defineProps({
  space: { type: Object, default: () => ({}) },
  documents: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  activeDocument: { type: Object, default: null },
  chunks: { type: Array, default: () => [] },
  chunkLoading: { type: Boolean, default: false },
  /** 公有空间只读浏览：隐藏添加 / 删除入口 */
  readonly: { type: Boolean, default: false },
})

const emit = defineEmits(['back', 'select-document', 'delete-document', 'add-document'])

/** 文档列表 / 分片首屏骨架数量 */
const DOC_SKELETON_COUNT = 6
const CHUNK_SKELETON_COUNT = 3

function isActive(doc) {
  return props.activeDocument && props.activeDocument.id === doc.id
}

/** 文档状态展示元数据（文案 / 标签色 / 悬浮提示） */
function statusMeta(doc) {
  return docStatusMeta(doc?.status)
}

/** 是否处于「已解析未向量化」的待确认状态 */
function isPendingConfirm(doc) {
  return doc?.status === DOC_STATUS.PENDING_CONFIRM
}

function onDelete(doc) {
  ElMessageBox.confirm(`确定删除文档「${doc.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
    .then(() => {
      emit('delete-document', doc)
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}
</script>

<style scoped>
.document-list-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.kb-toolbar__left {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.kb-toolbar__folder-icon {
  color: var(--tb-accent);
  font-size: 18px;
}

.kb-toolbar__title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--tb-text-primary);
  margin-left: 0.25rem;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-toolbar__readonly-tag {
  margin-left: 0.5rem;
}

/* ========== 左右分栏 ========== */
.doc-workspace {
  display: flex;
  align-items: stretch;
  gap: 1rem;
  height: calc(100vh - 6.75rem);
  min-height: 420px;
}

/* ========== 左侧文档列表 ========== */
.doc-sidebar {
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
  overflow: hidden;
}

.doc-sidebar__header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: 0.95rem;
  font-weight: 700;
  border-bottom: 1px solid var(--tb-border);
}

.doc-sidebar__add {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 16px;
  color: var(--tb-primary-text);
  background: var(--tb-primary-border);
  border-radius: var(--tb-radius-md);
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.doc-sidebar__add:hover {
  color: var(--tb-on-primary);
  background: var(--tb-primary-active);
}

.doc-list {
  flex: 1;
  min-height: 80px;
  margin: 0;
  padding: 0.5rem;
  list-style: none;
  overflow-y: auto;
}

.doc-list__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.6rem;
  border-radius: var(--tb-radius-md);
  cursor: pointer;
  transition: background 0.2s ease;
}

.doc-list__item:hover {
  background: var(--tb-bg-sunken);
}

.doc-list__item--active {
  background: var(--tb-primary-soft);
}

.doc-list__item--active .doc-list__name {
  color: var(--tb-primary-text);
  font-weight: 600;
}

.doc-list__icon {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 16px;
  color: var(--tb-on-primary);
  background: var(--tb-text-tertiary);
  border-radius: var(--tb-radius-md);
}

.doc-list__icon--pdf {
  background: var(--tb-danger);
}

.doc-list__icon--word,
.doc-list__icon--doc,
.doc-list__icon--docx {
  background: var(--tb-info);
}

.doc-list__icon--markdown,
.doc-list__icon--md {
  background: #8b5cf6;
}

.doc-list__icon--ppt,
.doc-list__icon--pptx {
  background: var(--tb-warning);
}

.doc-list__icon--txt {
  background: var(--tb-text-secondary);
}

.doc-list__name {
  flex: 1;
  min-width: 0;
  font-size: 0.85rem;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 右侧分片详情 ========== */
.doc-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  border: var(--tb-border-width) solid var(--tb-border);
  box-shadow: var(--tb-shadow-1);
  overflow: hidden;
}

.doc-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--tb-border);
}

.doc-detail__title {
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.doc-detail__label {
  flex: 0 0 auto;
  font-size: 0.85rem;
  color: var(--tb-text-secondary);
}

.doc-detail__name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-detail__meta {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--tb-text-secondary);
}

.doc-detail__divider {
  color: #d1d5db;
}

.doc-detail__delete {
  font-size: 16px;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--tb-radius-sm);
  transition: color 0.2s, background 0.2s;
}

.doc-detail__delete:hover {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

/* 待确认状态提示：仅在「已解析未向量化」时出现 */
.doc-detail__hint {
  margin: 12px 16px 0;
}

.chunk-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  overflow-y: auto;
  background: var(--tb-bg-raised);
}

.chunk-card {
  padding: 0.9rem 1rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.chunk-card:hover {
  border-color: var(--tb-primary-border);
  box-shadow: var(--tb-shadow-1);
}



/* 分片正文：Markdown 渲染容器（字号 / 颜色由 MarkdownViewer 内的 .markdown-body 继承） */
.chunk-card__content {
  font-size: 0.85rem;
  line-height: 1.7;
  color: var(--tb-text-secondary);
  word-break: break-word;
}

/* ========== 加载骨架 ========== */
.kb-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  white-space: nowrap;
  border: 0;
  clip-path: inset(50%);
}

.doc-list__item--skeleton {
  pointer-events: none;
}

.doc-skeleton__block,
.chunk-skeleton__block {
  position: relative;
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-text-primary) 8%, var(--tb-bg-surface));
  border-radius: var(--tb-radius-xs);
}

/* 文档列表行：30px 图标块 + 一条名称线，行高与真实文档项一致 */
.doc-skeleton__icon {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: var(--tb-radius-md);
}

.doc-skeleton__line {
  flex: 1;
  max-width: 78%;
  height: 11px;
}

.doc-skeleton__line.is-2 {
  max-width: 58%;
}

.doc-skeleton__line.is-3 {
  max-width: 68%;
}

/* 分片卡：四行文本占位，卡壳沿用 chunk-card */
.chunk-card--skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.chunk-card--skeleton:hover {
  border-color: var(--tb-border);
  box-shadow: none;
}

.chunk-skeleton__line {
  height: 10px;
}

.chunk-skeleton__line.is-1 {
  width: 100%;
}

.chunk-skeleton__line.is-2 {
  width: 94%;
}

.chunk-skeleton__line.is-3 {
  width: 88%;
}

.chunk-skeleton__line.is-4 {
  width: 56%;
}

/* 流光只做 transform 位移（doc-ui §6.3） */
.doc-skeleton__block::after,
.chunk-skeleton__block::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 24%,
    light-dark(rgb(255 255 255 / 0.65), rgb(255 255 255 / 0.09)) 50%,
    transparent 76%
  );
  animation: kb-doc-shimmer 1.4s var(--tb-ease-linear) infinite;
  transform: translateX(-100%);
}

@keyframes kb-doc-shimmer {
  to {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .doc-skeleton__block::after,
  .chunk-skeleton__block::after {
    animation: none;
    opacity: 0;
  }
}
</style>
