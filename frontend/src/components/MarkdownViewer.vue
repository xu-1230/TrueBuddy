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
.markdown-viewer .markdown-body {
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
  color: #111827;
}

.markdown-viewer .markdown-body h1 {
  font-size: 1.25em;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #e5e7eb;
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
  color: #6b7280;
  background: #f9fafb;
  border-left: 3px solid #d1d5db;
}

.markdown-viewer .markdown-body a {
  color: #4f46e5;
  text-decoration: none;
}

.markdown-viewer .markdown-body a:hover {
  text-decoration: underline;
}

.markdown-viewer .markdown-body hr {
  margin: 1.2em 0;
  border: none;
  border-top: 1px solid #e5e7eb;
}

.markdown-viewer .markdown-body img {
  max-width: 100%;
}

.markdown-viewer .markdown-body code {
  padding: 0.15em 0.35em;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 0.9em;
  color: #b31d28;
  background: #f3f4f6;
  border-radius: 4px;
}

.markdown-viewer .markdown-body pre {
  margin: 0.75em 0;
  padding: 0.75em 1em;
  background: #f6f8fa;
  border: 1px solid #e8e9ee;
  border-radius: 8px;
  overflow-x: auto;
}

.markdown-viewer .markdown-body pre code {
  padding: 0;
  font-size: 0.85em;
  line-height: 1.6;
  color: #24292f;
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
  border: 1px solid #e5e7eb;
}

.markdown-viewer .markdown-body th {
  font-weight: 600;
  background: #f9fafb;
}

.markdown-viewer .markdown-body input[type='checkbox'] {
  margin-right: 0.35em;
}

/* 代码高亮（highlight.js 类名，浅色主题） */
.markdown-viewer .hljs-comment,
.markdown-viewer .hljs-quote {
  color: #6a737d;
}

.markdown-viewer .hljs-keyword,
.markdown-viewer .hljs-selector-tag,
.markdown-viewer .hljs-literal {
  color: #d73a49;
}

.markdown-viewer .hljs-string,
.markdown-viewer .hljs-doctag,
.markdown-viewer .hljs-regexp {
  color: #032f62;
}

.markdown-viewer .hljs-number,
.markdown-viewer .hljs-variable,
.markdown-viewer .hljs-template-variable,
.markdown-viewer .hljs-attr {
  color: #005cc5;
}

.markdown-viewer .hljs-title,
.markdown-viewer .hljs-section,
.markdown-viewer .hljs-selector-id {
  color: #6f42c1;
}

.markdown-viewer .hljs-name,
.markdown-viewer .hljs-tag,
.markdown-viewer .hljs-attribute {
  color: #22863a;
}

.markdown-viewer .hljs-type,
.markdown-viewer .hljs-built_in,
.markdown-viewer .hljs-builtin-name {
  color: #e36209;
}

.markdown-viewer .hljs-meta {
  color: #b31d28;
}

.markdown-viewer .hljs-emphasis {
  font-style: italic;
}

.markdown-viewer .hljs-strong {
  font-weight: 700;
}
</style>
