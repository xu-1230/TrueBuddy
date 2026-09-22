<template>
  <div class="markdown-viewer">
    <Viewer :value="markdown" :plugins="plugins" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Viewer } from '@bytemd/vue-next'
import gfm from '@bytemd/plugin-gfm'
import highlight from '@bytemd/plugin-highlight'
import mermaid from '@bytemd/plugin-mermaid'
import 'bytemd/dist/index.css'

/**
 * Markdown 只读渲染组件（bytemd Viewer）
 *
 * 用法：
 *     <MarkdownViewer :value="chunk.content" />
 *
 * 内置插件：GFM（表格 / 任务列表 / 删除线）、代码高亮、Mermaid 流程图。
 */
const props = defineProps({
  /** Markdown 原文 */
  value: { type: String, default: '' },
  /** 自定义插件列表，不传时使用内置插件 */
  plugins: { type: Array, default: null },
})

const markdown = computed(() => props.value ?? '')

// 插件实例只需初始化一次，避免每次渲染重建
const defaultPlugins = [gfm(), highlight(), mermaid()]

const plugins = computed(() => props.plugins ?? defaultPlugins)
</script>

<!--
  非 scoped：Viewer 内部通过 innerHTML 渲染，作用域属性不会挂到标题、段落等子元素上，
  因此统一以 .markdown-viewer 作为命名空间限定样式作用范围。
-->
<style>
.markdown-viewer {
  /* 代码高亮语义色：色相沿用 GitHub 浅色主题，暗色态给出对应变体。
     这是「内容语义色」而非品牌色，改色相会损失可辨识度，故只做明暗双态（09-样式规范.md §3.2 第 3 条） */
  --md-hl-comment: light-dark(#6a737d, #8b949e);
  --md-hl-keyword: light-dark(#d73a49, #ff7b72);
  --md-hl-string: light-dark(#032f62, #a5d6ff);
  --md-hl-number: light-dark(#005cc5, #79c0ff);
  --md-hl-title: light-dark(#6f42c1, #d2a8ff);
  --md-hl-name: light-dark(#22863a, #7ee787);
  --md-hl-type: light-dark(#e36209, #ffa657);
  --md-hl-meta: light-dark(#b31d28, #ff9492);
}

.markdown-viewer .markdown-body {
  font-family: var(--font-sans);
  font-size: inherit;
  line-height: 1.75;
  color: inherit;
  word-break: break-word;
  background: transparent;
}

.markdown-viewer .markdown-body > :first-child {
  margin-top: 0;
}

.markdown-viewer .markdown-body > :last-child {
  margin-bottom: 0;
}

.markdown-viewer .markdown-body h1,
.markdown-viewer .markdown-body h2,
.markdown-viewer .markdown-body h3,
.markdown-viewer .markdown-body h4,
.markdown-viewer .markdown-body h5,
.markdown-viewer .markdown-body h6 {
  margin: 1em 0 0.5em;
  font-weight: 600;
  line-height: 1.4;
  color: var(--tb-text-primary);
}

.markdown-viewer .markdown-body h1 {
  font-size: 1.25em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--tb-border);
}

.markdown-viewer .markdown-body h2 {
  font-size: 1.15em;
}

.markdown-viewer .markdown-body h3 {
  font-size: 1.05em;
}

.markdown-viewer .markdown-body h4,
.markdown-viewer .markdown-body h5,
.markdown-viewer .markdown-body h6 {
  font-size: 1em;
}

.markdown-viewer .markdown-body p {
  margin: 0.6em 0;
}

.markdown-viewer .markdown-body ul,
.markdown-viewer .markdown-body ol {
  margin: 0.6em 0;
  padding-left: 1.4em;
}

.markdown-viewer .markdown-body li + li {
  margin-top: 0.25em;
}

.markdown-viewer .markdown-body blockquote {
  margin: 0.6em 0;
  padding: 0.2em 0.9em;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border-left: 3px solid var(--tb-border-strong);
}

.markdown-viewer .markdown-body a {
  color: var(--tb-primary-text);
  text-decoration: none;
}

.markdown-viewer .markdown-body a:hover {
  text-decoration: underline;
}

.markdown-viewer .markdown-body hr {
  margin: 1.2em 0;
  border: none;
  border-top: 1px solid var(--tb-border);
}

.markdown-viewer .markdown-body img {
  max-width: 100%;
}

.markdown-viewer .markdown-body code {
  padding: 0.15em 0.35em;
  font-family: var(--font-mono);
  font-size: 0.9em;
  color: var(--md-hl-meta);
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-xs);
}

.markdown-viewer .markdown-body pre {
  margin: 0.75em 0;
  padding: 0.75em 1em;
  background: var(--tb-bg-sunken);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  overflow-x: auto;
}

.markdown-viewer .markdown-body pre code {
  padding: 0;
  font-size: 0.85em;
  line-height: 1.6;
  color: var(--tb-text-primary);
  background: transparent;
}

.markdown-viewer .markdown-body table {
  display: block;
  width: 100%;
  margin: 0.75em 0;
  border-collapse: collapse;
  overflow-x: auto;
}

.markdown-viewer .markdown-body th,
.markdown-viewer .markdown-body td {
  padding: 0.4em 0.7em;
  border: 1px solid var(--tb-border);
}

.markdown-viewer .markdown-body th {
  font-weight: 600;
  background: var(--tb-bg-raised);
}

.markdown-viewer .markdown-body input[type='checkbox'] {
  margin-right: 0.35em;
}

/* 代码高亮（highlight.js 类名，明暗双态，取值见文件顶部 --md-hl-*） */
.markdown-viewer .hljs-comment,
.markdown-viewer .hljs-quote {
  color: var(--md-hl-comment);
}

.markdown-viewer .hljs-keyword,
.markdown-viewer .hljs-selector-tag,
.markdown-viewer .hljs-literal {
  color: var(--md-hl-keyword);
}

.markdown-viewer .hljs-string,
.markdown-viewer .hljs-doctag,
.markdown-viewer .hljs-regexp {
  color: var(--md-hl-string);
}

.markdown-viewer .hljs-number,
.markdown-viewer .hljs-variable,
.markdown-viewer .hljs-template-variable,
.markdown-viewer .hljs-attr {
  color: var(--md-hl-number);
}

.markdown-viewer .hljs-title,
.markdown-viewer .hljs-section,
.markdown-viewer .hljs-selector-id {
  color: var(--md-hl-title);
}

.markdown-viewer .hljs-name,
.markdown-viewer .hljs-tag,
.markdown-viewer .hljs-attribute {
  color: var(--md-hl-name);
}

.markdown-viewer .hljs-type,
.markdown-viewer .hljs-built_in,
.markdown-viewer .hljs-builtin-name {
  color: var(--md-hl-type);
}

.markdown-viewer .hljs-meta {
  color: var(--md-hl-meta);
}

.markdown-viewer .hljs-emphasis {
  font-style: italic;
}

.markdown-viewer .hljs-strong {
  font-weight: 700;
}
</style>
