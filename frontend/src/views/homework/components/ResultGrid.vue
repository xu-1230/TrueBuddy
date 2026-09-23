<template>
  <!-- 批改中且暂无结果：整屏进度 -->
  <div v-if="grading && !records.length" class="result-state">
    <span class="result-state__spinner"></span>
    <p class="result-state__title">{{ uploading ? `正在上传作业文件 ${uploadPercent}%` : '正在批改作业…' }}</p>
    <p class="result-state__desc">
      {{ stageLabel || `共 ${totalCount} 份作业，正在逐份解析与评分，请勿关闭页面` }}
    </p>
  </div>

  <!-- 有结果：统计条 + 卡片网格（批改中同样展示，卡片逐份出现） -->
  <div v-else-if="records.length" class="result-wrap">
    <div v-if="grading" class="running">
      <span class="running__spinner"></span>
      <span class="running__text">
        {{ uploading ? `正在上传作业文件 ${uploadPercent}%` : `正在批改 ${doneCount}/${totalCount} 份` }}
      </span>
      <span v-if="stageLabel && !uploading" class="running__stage" :title="stageTitle">
        {{ stageLabel }}
      </span>
      <span class="running__track">
        <span class="running__bar" :style="{ width: `${runningPercent}%` }"></span>
      </span>
    </div>

    <div class="stats">
      <div class="stat">
        <span class="stat__value">{{ records.length }}</span>
        <span class="stat__label">批改份数</span>
      </div>
      <div class="stat">
        <span class="stat__value">{{ averageScore }}</span>
        <span class="stat__label">平均分</span>
      </div>
      <div class="stat">
        <span class="stat__value">{{ passRate }}</span>
        <span class="stat__label">及格率</span>
      </div>
      <div class="stat">
        <span class="stat__value stat__value--warn">{{ failedCount }}</span>
        <span class="stat__label">未完成</span>
      </div>
    </div>

    <div class="result-grid">
      <article
        v-for="record in records"
        :key="record.id"
        class="result-card"
        :class="`result-card--${scoreTone(record.score)}`"
        @click="emit('open', record)"
      >
        <div class="result-card__head">
          <span class="result-card__name" :title="record.studentName">
            {{ record.studentName || '未知学生' }}
          </span>
          <span class="result-card__badge">{{ record.grade || '未批改' }}</span>
        </div>

        <div class="result-card__score">
          <span class="result-card__score-value">
            {{ Number.isFinite(record.score) ? record.score : '--' }}
          </span>
          <span class="result-card__score-unit">分</span>
        </div>

        <p class="result-card__file" :title="record.fileName">{{ record.fileName }}</p>
        <p class="result-card__comment">{{ record.comment }}</p>

        <div class="result-card__foot">
          <span class="result-card__meta">{{ metaText(record) }}</span>
          <button type="button" class="result-card__view" @click.stop="emit('open', record)">
            <el-icon><View /></el-icon>
            <span>查看</span>
          </button>
        </div>
      </article>
    </div>
  </div>

  <!-- 空态 -->
  <div v-else class="display-empty">
    <el-icon class="display-empty__icon"><Files /></el-icon>
    <p class="display-empty__title">批改结果将在这里展示</p>
    <p class="display-empty__desc">
      选择作业文件夹、填写批改要求后点击左下角「开始批改」，将逐份给出得分与评语
    </p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Files, View } from '@element-plus/icons-vue'
import { formatFileSize, scoreTone } from '@/views/homework/utils/format'

const props = defineProps({
  /** 逐份批改结果（批改中会逐份追加） */
  records: { type: Array, default: () => [] },
  /** 批改进行中 */
  grading: { type: Boolean, default: false },
  /** 是否处于上传阶段 */
  uploading: { type: Boolean, default: false },
  /** 上传进度（0-100） */
  uploadPercent: { type: Number, default: 0 },
  /** 已完成的批改份数 */
  doneCount: { type: Number, default: 0 },
  /** 提交的作业总份数 */
  totalCount: { type: Number, default: 0 },
  /** 当前批改阶段（由所选工作流的节点驱动）：{ label, index, total, file, model?, kbName?, iteration? } */
  stage: { type: Object, default: null },
})

const emit = defineEmits(['open'])

const averageScore = computed(() => {
  const scores = props.records.filter((item) => Number.isFinite(item.score)).map((item) => item.score)
  if (!scores.length) return '--'
  return (scores.reduce((sum, score) => sum + score, 0) / scores.length).toFixed(1)
})

const failedCount = computed(() => props.records.filter((item) => item.status !== 1).length)

const passRate = computed(() => {
  const scored = props.records.filter((item) => Number.isFinite(item.score))
  if (!scored.length) return '--'
  const passed = scored.filter((item) => item.score >= 60).length
  return `${Math.round((passed / scored.length) * 100)}%`
})

/** 进行中进度条百分比（上传阶段按上传进度，批改阶段按完成份数） */
const runningPercent = computed(() => {
  if (props.uploading) return props.uploadPercent
  if (!props.totalCount) return 0
  return Math.round((props.doneCount / props.totalCount) * 100)
})

/** 当前工作流阶段的文案（迭代步骤回显迭代计数，LLM 步骤回显模型与上下文） */
const stageLabel = computed(() => {
  if (!props.stage) return ''
  const { index, total, label, model, kbName, iteration } = props.stage
  // 迭代体内直接展示「迭代 2/5 · 步骤名」，与 Dify 的运行日志一致
  const head = iteration
    ? `迭代 ${iteration.index}/${iteration.total} · ${label}`
    : `第 ${index}/${total} 步 · ${label}`
  const parts = [head]
  if (props.stage.model) parts.push(`模型 ${model}`)
  if (props.stage.kbName) parts.push(`上下文 ${kbName}`)
  return parts.join(' · ')
})

/** 悬浮提示补全当前处理的文件，列表里只放得下阶段文案 */
const stageTitle = computed(() => {
  if (!props.stage) return ''
  return props.stage.file ? `${stageLabel.value}（${props.stage.file}）` : stageLabel.value
})

function metaText(record) {
  const parts = []
  if (record.status !== 1) parts.push('批改未完成')
  if (Number.isFinite(record.size) && record.size > 0) parts.push(formatFileSize(record.size))
  return parts.join(' · ') || '已完成'
}
</script>

<style scoped>
/* ---------- 批改中（无结果） ---------- */
.result-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
  text-align: center;
}

.result-state__spinner {
  width: 34px;
  height: 34px;
  margin-bottom: 0.35rem;
  border-radius: 50%;
  border: 3px solid var(--tb-primary-border);
  border-top-color: var(--tb-primary);
  animation: homework-spin 0.8s linear infinite;
}

.result-state__title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.result-state__desc {
  margin: 0;
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
}

@keyframes homework-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------- 结果区 ---------- */
.result-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  height: 100%;
  min-height: 0;
}

.running {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0;
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

.running__spinner {
  flex: 0 0 auto;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--tb-primary-border);
  border-top-color: var(--tb-primary);
  animation: homework-spin 0.8s linear infinite;
}

.running__text {
  flex: 0 0 auto;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--tb-primary-text);
}

.running__stage {
  flex: 0 0 auto;
  max-width: 240px;
  padding: 1px 8px;
  border-radius: 0;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border: var(--tb-border-width) solid var(--tb-warning-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.running__track {
  flex: 1;
  min-width: 40px;
  height: 6px;
  border-radius: 0;
  background: var(--tb-bg-sunken);
  overflow: hidden;
}

.running__bar {
  display: block;
  height: 100%;
  border-radius: 0;
  background: var(--tb-primary);
  transition: width 0.3s ease;
}

.stats {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.6rem;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0.5rem 0.7rem;
  border-radius: 0;
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

.stat__value {
  font-size: 1.05rem;
  font-weight: 800;
  color: var(--tb-primary-text);
  letter-spacing: 0.3px;
}

.stat__value--warn {
  color: var(--tb-warning);
}

.stat__label {
  font-size: 0.66rem;
  color: var(--tb-text-tertiary);
}

.result-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(238px, 1fr));
  align-content: start;
  gap: 0.9rem;
  overflow-y: auto;
  padding-right: 2px;
}

.result-grid::-webkit-scrollbar {
  width: 6px;
}

.result-grid::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--tb-border-strong);
}

/* ---------- 结果卡片 ---------- */
.result-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-self: start;
  padding: 0.75rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  cursor: pointer;
  animation: card-in 0.28s ease both;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-card:hover {
  transform: translateY(-3px);
  border-color: var(--tb-primary-border);
  box-shadow: var(--tb-shadow-2);
}

.result-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.result-card__name {
  min-width: 0;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-card__badge {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 0;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

.result-card__score {
  display: flex;
  align-items: baseline;
  gap: 4px;
  line-height: 1;
}

.result-card__score-value {
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: var(--tb-primary-text);
}

.result-card__score-unit {
  font-size: 0.7rem;
  color: var(--tb-text-tertiary);
}

.result-card__file {
  margin: 0;
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-card__comment {
  margin: 0;
  font-size: 0.72rem;
  line-height: 1.6;
  color: var(--tb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.15rem;
  padding-top: 0.5rem;
  border-top: 1px dashed var(--tb-border);
}

.result-card__meta {
  font-size: 0.66rem;
  color: var(--tb-text-tertiary);
}

.result-card__view {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  height: 26px;
  padding: 0 0.6rem;
  border: none;
  border-radius: 0;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.result-card__view:hover {
  transform: translateY(-1px);
  box-shadow: var(--tb-shadow-brand);
}

/* 分数档位配色 */
.result-card--excellent .result-card__score-value {
  color: var(--tb-success);
}

.result-card--excellent .result-card__badge {
  color: var(--tb-success);
  background: var(--tb-success-soft);
  border-color: var(--tb-success-soft);
}

.result-card--good .result-card__score-value {
  color: var(--tb-primary-text);
}

.result-card--pass .result-card__score-value {
  color: var(--tb-warning);
}

.result-card--pass .result-card__badge {
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border-color: var(--tb-warning-soft);
}

.result-card--weak .result-card__score-value {
  color: var(--tb-danger);
}

.result-card--weak .result-card__badge {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: var(--tb-danger-soft);
}

.result-card--pending .result-card__score-value {
  color: var(--tb-text-tertiary);
}

/* ---------- 空态 ---------- */
.display-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  text-align: center;
}

.display-empty__icon {
  font-size: 46px;
  color: var(--tb-primary);
}

.display-empty__title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.display-empty__desc {
  margin: 0;
  max-width: 26rem;
  font-size: 0.78rem;
  line-height: 1.7;
  color: var(--tb-text-tertiary);
}
</style>
