<template>
  <div class="detail">
    <div class="detail__toolbar">
      <button type="button" class="ghost-chip" @click="emit('back')">
        <el-icon><Back /></el-icon>
        <span>返回结果列表</span>
      </button>
      <span class="detail__title" :title="record.fileName">
        <el-icon><Document /></el-icon>
        {{ record.fileName }}
      </span>
      <span v-if="record.studentName" class="detail__student">{{ record.studentName }}</span>
      <a
        v-if="record.fileUrl"
        class="ghost-chip detail__open"
        :href="record.fileUrl"
        target="_blank"
        rel="noopener"
      >
        <el-icon><TopRight /></el-icon>
        <span>打开原文件</span>
      </a>
    </div>

    <div class="detail__body">
      <!-- 作业原文 -->
      <section class="pane pane--origin">
        <header class="pane__head">
          <span class="pane__title">作业原文</span>
          <span class="pane__hint">由后端解析，可能与原文件排版存在差异</span>
        </header>
        <pre v-if="record.originalText" class="origin">{{ record.originalText }}</pre>
        <div v-else class="origin origin--empty">
          {{ record.errorMsg || '未能解析出作业文本内容' }}
        </div>
      </section>

      <!-- 评分与评语 -->
      <section class="pane pane--score">
        <header class="pane__head">
          <span class="pane__title">批改结果</span>
          <span v-if="workflowLabel" class="pane__hint">{{ workflowLabel }}</span>
        </header>

        <div class="score" :class="`score--${scoreTone(record.score)}`">
          <div class="score__value">
            {{ Number.isFinite(record.score) ? record.score : '--' }}
            <span class="score__unit">分</span>
          </div>
          <span class="score__grade">{{ record.grade || '未批改' }}</span>
        </div>

        <dl class="meta">
          <div class="meta__item">
            <dt>学生</dt>
            <dd>{{ record.studentName || '未知' }}</dd>
          </div>
          <div class="meta__item">
            <dt>文件大小</dt>
            <dd>{{ Number.isFinite(record.size) ? formatFileSize(record.size) : '—' }}</dd>
          </div>
          <div class="meta__item">
            <dt>批改状态</dt>
            <dd :class="{ 'meta__warn': record.status !== 1 }">
              {{ record.status === 1 ? '已完成' : '未完成' }}
            </dd>
          </div>
          <div v-if="record.model" class="meta__item">
            <dt>批改模型</dt>
            <dd>{{ record.model }}</dd>
          </div>
          <div v-if="record.kbName" class="meta__item">
            <dt>知识库上下文</dt>
            <dd>{{ record.kbName }}</dd>
          </div>
        </dl>

        <div class="comment">
          <h4 class="comment__title">AI 评语</h4>
          <p class="comment__body">{{ record.comment || '暂无评语' }}</p>
        </div>

        <div v-if="description" class="requirement">
          <h4 class="requirement__title">本次批改要求</h4>
          <p class="requirement__body">{{ description }}</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { Back, Document, TopRight } from '@element-plus/icons-vue'
import { formatFileSize, scoreTone } from '@/views/homework/utils/format'

defineProps({
  /** 当前查看的批改记录 */
  record: { type: Object, required: true },
  /** 本次批改要求（AI 描述说明） */
  description: { type: String, default: '' },
  /** 工作流文案 */
  workflowLabel: { type: String, default: '' },
})

const emit = defineEmits(['back'])
</script>

<style scoped>
.detail {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  overflow: hidden;
}

.detail__toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid var(--tb-border);
}

.ghost-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  height: 30px;
  padding: 0 0.7rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  font-size: 0.74rem;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.ghost-chip:hover {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.detail__title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail__title .el-icon {
  flex: 0 0 auto;
  color: var(--tb-primary);
}

.detail__student {
  flex: 0 0 auto;
  padding: 2px 9px;
  border-radius: var(--tb-radius-full);
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border: var(--tb-border-width) solid var(--tb-warning-soft);
}

.detail__open {
  margin-left: auto;
}

.detail__body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
  padding: 0.9rem;
}

.pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--tb-radius-lg);
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-raised);
  overflow: hidden;
}

.pane--origin {
  flex: 1.4;
  min-width: 0;
}

.pane--score {
  flex: 1;
  min-width: 0;
  background: var(--tb-bg-surface);
  overflow-y: auto;
  padding-bottom: 0.4rem;
}

.pane__head {
  flex: 0 0 auto;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.55rem 0.8rem;
  border-bottom: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.pane__title {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.pane__hint {
  font-size: 0.66rem;
  color: var(--tb-text-tertiary);
}

.origin {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0.85rem;
  overflow-y: auto;
  font-family: var(--font-sans);
  font-size: 0.76rem;
  line-height: 1.9;
  color: var(--tb-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.origin--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--tb-text-tertiary);
  font-size: 0.76rem;
}

.origin::-webkit-scrollbar,
.pane--score::-webkit-scrollbar {
  width: 6px;
}

.origin::-webkit-scrollbar-thumb,
.pane--score::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-border-strong);
}

/* ---------- 评分面板 ---------- */
.score {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 0.9rem 0.6rem;
}

.score__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 2.6rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -1px;
  color: var(--tb-primary-text);
}

.score__unit {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--tb-text-tertiary);
}

.score__grade {
  padding: 3px 12px;
  border-radius: var(--tb-radius-full);
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

.score--excellent .score__value {
  color: var(--tb-success);
}

.score--excellent .score__grade {
  color: var(--tb-success);
  background: var(--tb-success-soft);
  border-color: var(--tb-success-soft);
}

.score--pass .score__value {
  color: var(--tb-warning);
}

.score--pass .score__grade {
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border-color: var(--tb-warning-soft);
}

.score--weak .score__value {
  color: var(--tb-danger);
}

.score--weak .score__grade {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: var(--tb-danger-soft);
}

.score--pending .score__value {
  color: var(--tb-text-tertiary);
}

.meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.5rem;
  margin: 0;
  padding: 0 0.9rem 0.7rem;
}

.meta__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0.45rem 0.6rem;
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

.meta__item dt {
  font-size: 0.64rem;
  color: var(--tb-text-tertiary);
}

.meta__item dd {
  margin: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta__warn {
  color: var(--tb-warning);
}

.comment,
.requirement {
  margin: 0 0.9rem 0.7rem;
  padding: 0.7rem 0.8rem;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

.requirement {
  background: var(--tb-warning-soft);
  border-color: var(--tb-warning-soft);
}

.comment__title,
.requirement__title {
  margin: 0 0 0.45rem;
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.requirement__title {
  color: var(--tb-warning);
}

.comment__body,
.requirement__body {
  margin: 0;
  font-size: 0.76rem;
  line-height: 1.9;
  color: var(--tb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
