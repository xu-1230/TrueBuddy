<template>
  <aside class="config-panel">
    <div class="config-panel__scroll">

    
      <!-- 作业文件夹选择 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">作业文件夹选择</span>
          <span class="section-head__hint">Word / PDF / 文本 / 图片</span>
        </div>
        <FolderUploader v-model="folder" :disabled="grading" />
      </section>

      
      <!-- 知识库选择 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">知识库选择</span>
          <span class="section-head__hint">用于批改时的评分依据检索</span>
        </div>
        <el-select
          v-model="spaceIds"
          class="kb-select"
          popper-class="hw-kb-select-popper"
          placeholder="请选择知识库（可多选）"
          :loading="kbLoading"
          multiple
          collapse-tags
          collapse-tags-tooltip
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
                :class="{ 'kb-option__tile--active': spaceIds.includes(kb.id) }"
              >
                <el-icon><Collection /></el-icon>
              </span>
              <span class="kb-option__main">
                <span class="kb-option__name">{{ kb.name }}</span>
                <span v-if="kb.description" class="kb-option__desc">{{ kb.description }}</span>
              </span>
              <span v-if="kb.isDefault" class="kb-option__tag">默认</span>
              <span v-else class="kb-option__count">{{ kb.documentCount ?? 0 }} 篇</span>
              <el-icon v-if="spaceIds.includes(kb.id)" class="kb-option__check"><Check /></el-icon>
            </span>
          </el-option>
          <template #footer>
            <span>共 {{ knowledgeBases.length }} 个知识库</span>
          </template>
        </el-select>
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
          placeholder="请说明本次作业的评分维度、扣分规则与评语要求，例如：重点考查需求分析的完整性与数据流图的规范性；概念性错误每个扣 5 分，缺少图例扣 3 分。满分与及格线已在上方「评分与导出设置」中固定。"
        />
      </section>

      <!-- 评分与导出设置：固定评分区间 + 自定义导出列 -->
      <section class="panel-section">
        <div class="section-head">
          <span class="section-head__title">评分与导出设置</span>
          <span class="section-head__hint">{{ scoreRangeHint }}</span>
        </div>

        <!-- 评分设置：输入变量的行式表单（变量名 + 老师自己填的值） -->
        <div class="nd-group__head score-input-head">
          <span class="nd-group__name">
            评分设置
            <span class="nd-panel__count">3</span>
          </span>
        </div>
        <div class="score-range__presets" role="group" aria-label="常用评分区间">
          <button
            v-for="preset in SCORE_PRESETS"
            :key="preset.label"
            type="button"
            class="score-preset"
            :disabled="grading"
            @click="applyPreset(preset)"
          >
            {{ preset.label }}
          </button>
        </div>
        <div class="nd-list score-input-list">
          <div class="nd-head-row nd-grid--value">
            <span>变量名</span>
            <span>变量值</span>
          </div>
          <div class="nd-row nd-row--item score-input-row">
            <div class="nd-row__main nd-grid--value">
              <span class="score-input__name" title="最低分">score_min</span>
              <el-input-number
                v-model="scoreMin"
                size="small"
                :min="-9999"
                :max="9999"
                :step="0.5"
                :controls="false"
                :disabled="grading"
              />
            </div>
          </div>
          <div class="nd-row nd-row--item score-input-row">
            <div class="nd-row__main nd-grid--value">
              <span class="score-input__name" title="最高分">score_max</span>
              <el-input-number
                v-model="scoreMax"
                size="small"
                :min="-9999"
                :max="9999"
                :step="0.5"
                :controls="false"
                :disabled="grading"
              />
            </div>
          </div>
          <div class="nd-row nd-row--item score-input-row">
            <div class="nd-row__main nd-grid--value">
              <span class="score-input__name" title="及格线">pass_score</span>
              <el-input-number
                v-model="passScore"
                size="small"
                :min="-9999"
                :max="9999"
                :step="0.5"
                :controls="false"
                :disabled="grading"
              />
            </div>
          </div>
        </div>
        <p v-if="rangeError" class="field__tip field__tip--error">{{ rangeError }}</p>

        <!-- 自定义导出列：输出变量的行式表单（变量名 + 类型 + 描述） -->
        <div class="nd-group__head custom-group-head">
          <span class="nd-group__name">
            自定义导出列
            <span class="nd-panel__count">{{ customFields.length }}</span>
          </span>
          <button
            type="button"
            class="nd-group__add"
            :disabled="grading"
            aria-label="添加导出列"
            title="添加导出列"
            @click="addField"
          >
            <el-icon><Plus /></el-icon>
          </button>
        </div>
        <p v-if="fieldsError" class="field__tip field__tip--error">{{ fieldsError }}</p>

        <p v-if="!customFields.length" class="nd-panel__empty">
          暂无自定义导出列；点击右上角「＋」新增一列。
        </p>
        <div v-else class="nd-list">
          <div class="nd-head-row custom-grid">
            <span>变量名</span>
            <span>变量类型</span>
            <span></span>
            <span></span>
          </div>
          <div
            v-for="(field, index) in customFields"
            :key="field.key"
            class="nd-row nd-row--item custom-row"
          >
            <div class="nd-row__main custom-grid">
              <el-input
                v-model="field.key"
                size="small"
                maxlength="40"
                placeholder="变量名，如 highlight"
                :disabled="grading"
              />
              <el-cascader
                size="small"
                :model-value="fieldTypePath(field)"
                :options="CUSTOM_FIELD_TYPES"
                :props="{ expandTrigger: 'hover' }"
                :disabled="grading"
                @update:model-value="(path) => handleFieldTypeChange(field, path)"
              />
              <el-button
                class="nd-row__expand"
                text
                :class="{ 'is-active': isFieldDescExpanded(field.key) }"
                :icon="FullScreen"
                :aria-label="isFieldDescExpanded(field.key) ? '收起描述' : '编辑描述'"
                :title="isFieldDescExpanded(field.key) ? '收起描述' : '编辑描述'"
                @click="toggleFieldDesc(field.key)"
              />
              <el-button
                class="nd-row__remove"
                text
                :icon="Delete"
                :disabled="grading"
                :aria-label="`删除第 ${index + 1} 列`"
                title="删除该列"
                @click="removeField(index)"
              />
            </div>

            <div v-if="isFieldDescExpanded(field.key)" class="nd-sub nd-sub--stack">
              <span class="nd-sub__label">描述</span>
              <el-input
                v-model="field.description"
                size="small"
                maxlength="120"
                placeholder="给模型的描述：这一列要写什么"
                :disabled="grading"
              />
            </div>
          </div>
        </div>
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
              :loading="workflowsLoading"
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

          <!-- 原「可识别图片 / 批改并发数量」两个开关只是摆了控件、从不进请求体，已摘掉。
               能力本身仍是规划项（图片识别要多模态模型；并发批改要迭代节点从串行改成并行），
               落地时再接回来 —— 见 docs/specs/2026-10-14-批改作业工作流定义替换.md 的规划项。 -->
          <p class="field__tip">
            图片识别与并发批改属规划能力，当前工作流不支持，因此这里不再提供开关。
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
import { computed, reactive, ref } from 'vue'
import {
  ArrowDown,
  ArrowRight,
  Check,
  Collection,
  Delete,
  EditPen,
  FullScreen,
  Plus,
} from '@element-plus/icons-vue'
import FolderUploader from './FolderUploader.vue'
import SquircleButton from './SquircleButton.vue'
import {
  CUSTOM_FIELD_TYPES,
  SCORE_PRESETS,
  newCustomField,
  normalizeCustomFieldType,
  validateCustomFields,
  validateScoreRange,
} from '@/views/homework/utils/custom-fields'
import { isContainerType } from '@/views/workflow2/utils/variable-types'

/** AI 描述说明字数上限（与后端 DESCRIPTION_MAX 一致） */
const DESC_MAX = 500

const spaceIds = defineModel('spaceIds', { default: () => [] })
const description = defineModel('description', { default: '' })
const workflow = defineModel('workflow', { default: 'standard' })
const folder = defineModel('folder', { default: null })
const scoreMin = defineModel('scoreMin', { default: 0 })
const scoreMax = defineModel('scoreMax', { default: 100 })
const passScore = defineModel('passScore', { default: 60 })
const customFields = defineModel('customFields', { default: () => [] })

const props = defineProps({
  /** 批改工作流列表（批改工作流 + 用户自定义工作流），来自后端 GET /workflow/list */
  workflows: { type: Array, default: () => [] },
  /** 工作流列表是否加载中 */
  workflowsLoading: { type: Boolean, default: false },
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

/** 步骤数：后端已按顶层节点统计好（迭代体子块不计入），前端直接用 */
function stepCount(workflow) {
  return Number(workflow?.stepCount) || 0
}

const advancedOpen = ref(true)

const fileCount = computed(() => folder.value?.files?.length ?? 0)

/** 已展开描述的自定义列 key（沿用 NodeDataDrawer 的展开态方案） */
const expandedFields = reactive(new Set())
const isFieldDescExpanded = (key) => expandedFields.has(key)
function toggleFieldDesc(key) {
  if (expandedFields.has(key)) expandedFields.delete(key)
  else expandedFields.add(key)
}

/** 类型下拉回显：list / file 走二级（[类型, 元素类型]），其余一级 */
function fieldTypePath(field) {
  const type = normalizeCustomFieldType(field?.type)
  return isContainerType(type) && field?.schema?.type ? [type, field.schema.type] : [type]
}

/** 类型下拉选中：容器类型落成 { type, schema:{ type } }，标量清掉 schema */
function handleFieldTypeChange(field, path) {
  const [type, element] = Array.isArray(path) ? path : [path]
  if (!type) return
  field.type = type
  if (element) field.schema = { ...field.schema, type: element }
  else delete field.schema
}

/** 前端即时校验（后端 /homework/grade 会再校验一次） */
const rangeError = computed(() => validateScoreRange({
  min: scoreMin.value,
  max: scoreMax.value,
  pass: passScore.value,
}))
const fieldsError = computed(() => validateCustomFields(customFields.value))

/** 区间摘要：标题右侧直接显示本次评分范围（min–max）与及格线 */
const scoreRangeHint = computed(() => {
  if (rangeError.value) return '待修正'
  return `${scoreMin.value}–${scoreMax.value} 分 · 及格 ${passScore.value}`
})

/** 点预设：只填三个值，之后仍可微调 */
function applyPreset(preset) {
  scoreMin.value = preset.min
  scoreMax.value = preset.max
  passScore.value = preset.pass
}

/** 新增一列：变量名由老师填写（还没有 key，先空着等填） */
function addField() {
  customFields.value = [...customFields.value, newCustomField()]
}

/** 删除一列（预置列也可删） */
function removeField(index) {
  const [removed] = customFields.value.slice(index, index + 1)
  if (removed?.key) expandedFields.delete(removed.key)
  customFields.value = customFields.value.filter((_, itemIndex) => itemIndex !== index)
}
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
  border: var(--tb-border-width) solid var(--tb-border);
  /* box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border); */
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

.field__tip--error {
  color: var(--tb-danger);
}

/* ---------- 评分与导出设置 ---------- */
.score-range__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.score-preset {
  padding: 3px 9px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xs);
  background: var(--tb-bg-surface);
  font-size: 0.68rem;
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition: color 0.16s ease, border-color 0.16s ease, background 0.16s ease;
}

.score-preset:hover:not(:disabled) {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.score-preset:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 评分设置：输入变量的行式表单 */
.score-input-head {
  margin-top: 0.35rem;
}

.score-input-list {
  margin-top: 0.35rem;
}

.nd-grid--value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr);
  align-items: center;
  gap: 6px;
}

.score-input__name {
  min-width: 0;
  font-family: var(--font-mono);
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: var(--tb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-input-row :deep(.el-input-number) {
  flex: 1 1 auto;
  width: 100%;
}

.score-input-row :deep(.el-input__wrapper) {
  min-height: 28px;
  padding: 0 0.45rem;
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
}

.score-input-row :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary) inset;
}

.score-input-row :deep(.el-input__inner) {
  font-size: 0.74rem;
  font-variant-numeric: tabular-nums;
}

/* ---------- 自定义导出列：nd-list 行式表单（对齐 workflow2 NodeDataDrawer） ---------- */
.custom-group-head {
  margin-top: 0.85rem;
}

.nd-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.nd-group__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
}

.nd-panel__count {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--tb-bg-sunken);
  font-size: 0.66rem;
  font-weight: 400;
  line-height: 18px;
  color: var(--tb-text-secondary);
}

.nd-group__add {
  display: inline-flex;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: var(--tb-primary-soft);
  font-size: 14px;
  color: var(--tb-primary);
  cursor: pointer;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.nd-group__add:hover:not(:disabled) {
  background: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.nd-group__add:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.nd-panel__empty {
  margin: 0;
  padding: 2px 0;
  font-size: 0.7rem;
  line-height: 1.7;
  color: var(--tb-text-tertiary);
}

.nd-list {
  display: flex;
  flex-direction: column;
}

.nd-head-row {
  margin: 0 -8px;
  padding: 2px 8px 6px;
  font-size: 0.66rem;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

/* 面板只有 312px 可用：变量名 / 类型 / 展开描述 / 删除 */
.custom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px 22px 22px;
  align-items: center;
  gap: 6px;
}

.custom-row :deep(.el-cascader) {
  width: 100%;
  min-width: 0;
}

.nd-row--item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0 -8px;
  padding: 4px 8px;
  border-bottom: var(--tb-border-width) dashed var(--tb-border);
  border-radius: var(--tb-radius-md);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.nd-row--item:hover,
.nd-row--item:focus-within {
  background: var(--tb-bg-raised);
}

.nd-row--item:last-child {
  border-bottom-color: transparent;
}

.nd-row__expand,
.nd-row__remove {
  width: 22px;
  height: 24px;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: var(--tb-text-tertiary);
}

.nd-row__expand.is-active,
.nd-row__expand:hover {
  color: var(--tb-primary);
}

.nd-row__remove:hover:not(:disabled) {
  color: var(--tb-danger);
}

.nd-row__remove:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.nd-sub {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nd-sub--stack {
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}

.nd-sub__label {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  font-size: 0.66rem;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.custom-row :deep(.el-input__wrapper),
.custom-row :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 0 0.45rem;
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
  font-size: 0.72rem;
}

.custom-row :deep(.el-input__wrapper.is-focus),
.custom-row :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary) inset;
}

.custom-row :deep(.el-select .el-input__wrapper),
.custom-row :deep(.el-select__wrapper) {
  padding-right: 0.25rem;
}

.nd-sub :deep(.el-input__wrapper) {
  min-height: 28px;
  padding: 0 0.45rem;
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
  font-size: 0.72rem;
}

.nd-sub :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary) inset;
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

:deep(.kb-select .el-select__placeholder) {
  color: var(--tb-text-tertiary);
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
  background: linear-gradient(135deg, var(--tb-primary-soft), var(--tb-primary-border));
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
  transition: color 0.18s ease, background 0.18s ease;
}

.kb-option__tile--active {
  color: var(--tb-on-primary);
  background: linear-gradient(135deg, var(--tb-primary-hover), var(--tb-primary));
  box-shadow: none;
}

.kb-option__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
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
  border-radius: 0;
  font-weight: 600;
}

.kb-option__count {
  padding: 1px 8px;
  font-size: 0.66rem;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
  transition: color 0.18s ease, background 0.18s ease;
}

/* 内置默认知识库标记：与模板页一致取 accent 色系 */
.kb-option__tag {
  padding: 1px 7px;
  font-size: 0.64rem;
  color: color-mix(in oklab, var(--tb-accent) 72%, #000000);
  background: var(--tb-accent-soft);
  border: var(--tb-border-width) solid var(--tb-accent-soft);
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
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

.hw-kb-select-popper .el-select-dropdown__item.is-selected .kb-option__count {
  color: var(--tb-primary-text);
  background: var(--tb-bg-surface);
}

.hw-kb-select-popper .el-select-dropdown__item.is-selected .kb-option__tag {
  color: var(--tb-primary-text);
  background: var(--tb-bg-surface);
  border-color: var(--tb-primary-border);
}

.hw-kb-select-popper .el-select-dropdown__empty {
  padding: 18px 0;
  font-size: 0.76rem;
  color: var(--tb-text-tertiary);
}

.hw-kb-select-popper .el-select-dropdown__footer {
  padding: 6px 12px;
  border-top: 1px solid var(--tb-border);
  background: var(--tb-bg-sunken);
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  text-align: right;
}
</style>
