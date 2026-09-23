<template>
  <aside class="config-panel">
    <div class="config-panel__scroll">
      <!-- 知识库选择 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">知识库选择</span>
          <span class="section-head__hint">用于批改时的评分依据检索</span>
        </div>
        <el-select
          v-model="spaceId"
          class="kb-select"
          popper-class="hw-kb-select-popper"
          placeholder="请选择知识库"
          :loading="kbLoading"
          clearable
          no-data-text="暂无更多知识库"
        >
          <template #prefix>
            <el-icon class="kb-select__prefix"><Collection /></el-icon>
          </template>
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          >
            <span class="kb-option">
              <span
                class="kb-option__tile"
                :class="{ 'kb-option__tile--active': spaceId === kb.id }"
              >
                <el-icon><Collection /></el-icon>
              </span>
              <span class="kb-option__main">
                <span class="kb-option__name">{{ kb.name }}</span>
                <span v-if="kb.description" class="kb-option__desc">{{ kb.description }}</span>
              </span>
              <span v-if="kb.isDefault" class="kb-option__tag">默认</span>
              <span v-else class="kb-option__count">{{ kb.documentCount ?? 0 }} 篇</span>
              <el-icon v-if="spaceId === kb.id" class="kb-option__check"><Check /></el-icon>
            </span>
          </el-option>
          <template #footer>
            <span>共 {{ knowledgeBases.length }} 个知识库</span>
          </template>
        </el-select>
      </section>

      <!-- 作业文件夹选择 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">作业文件夹选择</span>
          <span class="section-head__hint">Word / PDF / 文本 / 图片</span>
        </div>
        <FolderUploader v-model="folder" :disabled="grading" />
      </section>

      <!-- AI 描述说明 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">AI 描述说明</span>
          <span class="section-head__count">{{ description.length }}/{{ DESC_MAX }}</span>
        </div>
        <el-input
          v-model="description"
          type="textarea"
          :rows="5"
          :maxlength="DESC_MAX"
          resize="none"
          placeholder="请说明本次作业的批改要求，例如：以满分 100 分评分，重点考查需求分析的完整性与数据流图的规范性；概念性错误每个扣 5 分，缺少图例扣 3 分；评语需给出具体修改建议。"
        />
      </section>

      <!-- 导出结果格式 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">导出结果格式</span>
          <span class="section-head__hint">成绩汇总表</span>
        </div>
        <el-segmented v-model="exportFormat" :options="EXPORT_OPTIONS" size="small" block />
      </section>

      <!-- 高级 -->
      <section class="panel-section">
        <button
          type="button"
          class="section-head section-head--toggle"
          @click="advancedOpen = !advancedOpen"
        >
          <span class="section-head__title">高级</span>
          <el-icon class="section-head__arrow">
            <ArrowDown v-if="advancedOpen" />
            <ArrowRight v-else />
          </el-icon>
        </button>
        <div v-show="advancedOpen" class="panel-section__body">
          <div class="field">
            <span class="field__label">批改工作流</span>
            <el-select
              v-model="workflow"
              class="workflow-select"
              popper-class="hw-kb-select-popper"
              :disabled="grading"
            >
              <el-option-group label="批改工作流">
                <el-option
                  v-for="item in builtinWorkflows"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span class="workflow-option">
                    <span class="workflow-option__label">{{ item.name }}</span>
                    <span class="workflow-option__hint">{{ stepCount(item) }} 个步骤</span>
                  </span>
                </el-option>
              </el-option-group>
              <el-option-group label="用户自定义工作流">
                <el-option
                  v-if="!customWorkflows.length"
                  disabled
                  value="__workflow-empty__"
                  label="暂无自定义工作流，点击下方「新建」创建"
                />
                <el-option
                  v-for="item in customWorkflows"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                >
                  <span class="workflow-option">
                    <span class="workflow-option__label">{{ item.name }}</span>
                    <span class="workflow-option__hint">{{ stepCount(item) }} 个步骤</span>
                  </span>
                </el-option>
              </el-option-group>
            </el-select>
            <div class="workflow-actions">
              <button
                type="button"
                class="workflow-action"
                :disabled="grading"
                @click="emit('create-workflow')"
              >
                <el-icon><Plus /></el-icon>
                <span>新建</span>
              </button>
              <button
                type="button"
                class="workflow-action"
                :disabled="grading || !workflow"
                @click="emit('edit-workflow', workflow)"
              >
                <el-icon><EditPen /></el-icon>
                <span>查看 / 编辑</span>
              </button>
            </div>
          </div>

          <div class="field field--row">
            <span class="field__label">可识别图片</span>
            <el-switch v-model="recognizeImage" :disabled="grading" />
          </div>

          <div class="field field--row">
            <span class="field__label">批改并发数量</span>
            <el-input-number
              v-model="concurrency"
              :min="1"
              :max="MAX_CONCURRENCY"
              :disabled="grading"
              size="small"
              controls-position="right"
            />
          </div>
          <p class="field__tip">
            并发数越高批改越快，但会同步占用大模型额度；图片识别需多模态模型支持，暂不可用时会在评语中提示人工复核。
          </p>
        </div>
      </section>
    </div>

    <footer class="config-panel__footer">
      <SquircleButton
        block
        class="grade-btn"
        :height="42"
        :disabled="!canGrade"
        @click="emit('grade')"
      >
        <span class="grade-btn__inner">
          <span>{{ grading ? '正在批改…' : '开始批改' }}</span>
          <span class="grade-btn__count">{{ fileCount }} 份</span>
        </span>
      </SquircleButton>
    </footer>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  ArrowDown,
  ArrowRight,
  Check,
  Collection,
  EditPen,
  Plus,
} from '@element-plus/icons-vue'
import FolderUploader from './FolderUploader.vue'
import SquircleButton from './SquircleButton.vue'

/** AI 描述说明字数上限（与后端 DESCRIPTION_MAX 一致） */
const DESC_MAX = 500
/** 并发数上限（与后端 tb.homework.max-concurrency 一致） */
const MAX_CONCURRENCY = 5

const EXPORT_OPTIONS = [
  { label: 'Excel', value: 'xlsx' },
  { label: 'CSV', value: 'csv' },
]

const spaceId = defineModel('spaceId', { default: 'default' })
const description = defineModel('description', { default: '' })
const exportFormat = defineModel('exportFormat', { default: 'xlsx' })
const workflow = defineModel('workflow', { default: 'standard' })
const recognizeImage = defineModel('recognizeImage', { default: false })
const concurrency = defineModel('concurrency', { default: 3 })
const folder = defineModel('folder', { default: null })

const props = defineProps({
  /** 批改工作流列表（批改工作流 + 用户自定义工作流） */
  workflows: { type: Array, default: () => [] },
  /** 下拉选项：内置默认知识库 + 接口返回的知识库 */
  knowledgeBases: { type: Array, default: () => [] },
  kbLoading: { type: Boolean, default: false },
  /** 批改进行中 */
  grading: { type: Boolean, default: false },
  /** 是否满足批改条件 */
  canGrade: { type: Boolean, default: false },
})

const emit = defineEmits(['grade', 'create-workflow', 'edit-workflow'])

/** 工作流下拉分组：批改工作流（系统内置） / 用户自定义工作流 */
const builtinWorkflows = computed(() => props.workflows.filter((item) => item.builtin))
const customWorkflows = computed(() => props.workflows.filter((item) => !item.builtin))

/** 步骤数只统计顶层节点，迭代容器内部的迭代体不单独计数 */
function stepCount(workflow) {
  return (workflow?.nodes ?? []).filter((node) => !node.parentNode).length
}

const advancedOpen = ref(true)

const fileCount = computed(() => folder.value?.files?.length ?? 0)
</script>

<style scoped>
.config-panel {
  flex: 0 0 auto;
  width: 344px;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border);
  overflow: hidden;
}

.config-panel__scroll {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
  padding: 1rem;
  overflow-y: auto;
}

.config-panel__scroll::-webkit-scrollbar {
  width: 6px;
}

.config-panel__scroll::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--tb-border-strong);
}

.panel-section {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.panel-section__body {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.section-head--toggle {
  cursor: pointer;
}

.section-head__title {
  display: inline-flex;
  align-items: center;
  font-size: 0.99rem;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--tb-text-primary);
}

/* 分节色条：用品牌色而非状态色（accent 仅用于状态与数据语义） */
.section-head__title::before {
  content: '';
  width: 3px;
  height: 14px;
  margin-right: 6px;
  border-radius: 0;
  background: var(--tb-primary-text);
}

.section-head__hint,
.section-head__count {
  font-size: 0.7rem;
  color: var(--tb-text-tertiary);
}

.section-head__arrow {
  font-size: 14px;
  color: var(--tb-text-tertiary);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.field--row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.field__label {
  font-size: 0.74rem;
  color: var(--tb-text-secondary);
}

.field__tip {
  margin: 0;
  font-size: 0.66rem;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

/* 知识库下拉 */
:deep(.kb-select),
:deep(.workflow-select) {
  width: 100%;
}

:deep(.kb-select .el-select__wrapper),
:deep(.workflow-select .el-select__wrapper) {
  min-height: 40px;
  padding: 0.3rem 0.75rem;
  border-radius: 0;
  background: var(--tb-bg-sunken);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
  font-size: 0.8rem;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

:deep(.kb-select .el-select__wrapper:hover),
:deep(.workflow-select .el-select__wrapper:hover) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border-strong) inset;
}

:deep(.kb-select .el-select__wrapper.is-focused),
:deep(.workflow-select .el-select__wrapper.is-focused) {
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary) inset;
}

.kb-select__prefix {
  flex: 0 0 auto;
  font-size: 15px;
  color: var(--tb-primary);
}

.kb-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 1px 0;
}

.kb-option__tile {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 0;
  font-size: 15px;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
}

.kb-option__tile--active {
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  box-shadow: none;
}

.kb-option__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.kb-option__name {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-option__desc {
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-option__count,
.kb-option__tag {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 0;
  font-size: 0.66rem;
  font-weight: 600;
}

.kb-option__count {
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
}

.kb-option__tag {
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border: var(--tb-border-width) solid var(--tb-warning-soft);
}

.kb-option__check {
  flex: 0 0 auto;
  font-size: 14px;
  color: var(--tb-primary);
}

.workflow-option {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.workflow-option__label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--tb-text-primary);
}

.workflow-option__hint {
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
}

/* 工作流操作：新建 / 查看编辑 */
.workflow-actions {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.workflow-action {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 27px;
  padding: 0 0.55rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  font-size: 0.7rem;
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition: color 0.16s ease, border-color 0.16s ease, background 0.16s ease;
}

.workflow-action:hover:not(:disabled) {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.workflow-action:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 文本域 / 分段控件 / 数字输入 */
:deep(.el-textarea__inner) {
  background: var(--tb-bg-sunken);
  border-radius: 0;
  padding: 0.65rem 0.75rem;
  font-size: 0.8rem;
  line-height: 1.7;
  color: var(--tb-text-primary);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
}

:deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border-strong) inset;
}

:deep(.el-textarea__inner:focus) {
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary) inset;
}

:deep(.el-segmented) {
  --el-segmented-bg-color: var(--tb-bg-sunken);
  --el-segmented-color: var(--tb-text-secondary);
  --el-segmented-item-selected-bg-color: var(--tb-bg-surface);
  --el-segmented-item-selected-color: var(--tb-primary-text);
  --el-segmented-item-hover-bg-color: var(--tb-bg-sunken);
  --el-segmented-item-hover-color: var(--tb-text-primary);
  padding: 3px;
  border-radius: 0;
}

:deep(.el-segmented__item),
:deep(.el-segmented__item-selected) {
  border-radius: 0;
}

:deep(.el-segmented__item) {
  font-size: 0.74rem;
}

:deep(.el-segmented__item-selected) {
  box-shadow: var(--tb-shadow-1);
}

:deep(.el-input-number) {
  width: 108px;
}

:deep(.el-input-number .el-input__wrapper) {
  border-radius: 0;
  background: var(--tb-bg-sunken);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
}

/* 底部主按钮 */
.config-panel__footer {
  flex: 0 0 auto;
  padding: 0.9rem 1rem 1rem;
  border-top: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.grade-btn {
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: var(--tb-on-primary);
}

.grade-btn__inner {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
}

.grade-btn__count {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--tb-radius-full);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--tb-on-primary);
  background: color-mix(in oklab, var(--tb-on-primary) 20%, transparent);
}
</style>

<style>
/* ================= 下拉浮层 =================
   el-select 的下拉默认 teleport 到 body，脱离组件根节点，
   scoped 样式与 :deep() 均无法命中，因此这里使用非 scoped 样式，
   并通过 popper-class="hw-kb-select-popper" 精确限定作用范围。 */
.el-popper.hw-kb-select-popper.hw-kb-select-popper {
  padding: 0;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
  overflow: hidden;
}

.el-popper.hw-kb-select-popper .el-popper__arrow::before {
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.hw-kb-select-popper .el-select-dropdown__list {
  padding: 6px;
}

/* 工作流分组标题：批改工作流 / 用户自定义工作流 */
.hw-kb-select-popper .el-select-group__title {
  padding: 6px 8px 2px;
  font-size: 0.64rem;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--tb-text-tertiary);
}

.hw-kb-select-popper .el-select-group:last-of-type {
  margin-top: 4px;
  border-top: 1px solid var(--tb-border);
}

.hw-kb-select-popper .el-select-dropdown__item {
  height: auto;
  margin: 0 0 2px;
  padding: 6px 8px;
  border-radius: 0;
  line-height: 1.4;
  color: var(--tb-text-primary);
  transition: background 0.18s ease, color 0.18s ease;
}

.hw-kb-select-popper .el-select-dropdown__item:last-child {
  margin-bottom: 0;
}

.hw-kb-select-popper .el-select-dropdown__item.is-hovering,
.hw-kb-select-popper .el-select-dropdown__item:hover {
  background: var(--tb-primary-soft);
}

.hw-kb-select-popper .el-select-dropdown__item.is-selected {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.hw-kb-select-popper .el-select-dropdown__item.is-selected .kb-option__name,
.hw-kb-select-popper .el-select-dropdown__item.is-selected .workflow-option__label {
  color: var(--tb-primary-text);
}

.hw-kb-select-popper .el-select-dropdown__empty {
  padding: 18px 0;
  font-size: 0.76rem;
  color: var(--tb-text-tertiary);
}

.hw-kb-select-popper .el-select-dropdown__footer {
  padding: 6px 12px;
  border-top: 1px solid var(--tb-border);
  background: var(--tb-bg-raised);
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  text-align: right;
}
</style>
