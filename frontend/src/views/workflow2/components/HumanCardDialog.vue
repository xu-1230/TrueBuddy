<script setup>
/**
 * 人工确认弹窗：执行到人工确认节点中断时由画布页弹出。
 *
 * 数据全部来自后端的卡片帧（`conversation.message.completed`，`content_type = card`）：
 * - `card.data`   只读回显的**待确认数据**（中断节点 `inputs` 引用的上游产出）；
 * - `card.fields` 待用户填写的**表单字段**（中断节点 `outputs` 声明）。
 *
 * 字段清单只在卡片帧里下发（不像 Coze 散在模板与变量里），因此这里按 `type` 直接渲染控件即可：
 * `string` / `text` → 输入框；`list` → 文件清单（一期用模拟数据，见 `utils/mock-card-values`）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { prettyJson, summarizeValue } from '../utils/run-state'
import { isFileListField, kindOf, mockFileItemsOf } from '../utils/mock-card-values'

const props = defineProps({
  /** 是否显示 */
  modelValue: { type: Boolean, default: false },
  /** 卡片载荷：{ node_id, card_type, title, data, fields } */
  card: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const title = computed(() => props.card?.title || '人工确认')
const dataItems = computed(() => props.card?.data ?? [])
const fields = computed(() => props.card?.fields ?? [])

/** 表单取值：字段名 → 取值 */
const values = ref({})

/** 展开查看完整 JSON 的字段名集合 */
const expanded = ref(new Set())

/** 按字段声明初始化取值：文本类空串、列表类空数组 */
const resetValues = () => {
  const next = {}
  fields.value.forEach((field) => {
    next[field?.name] = kindOf(field) === 'text' ? '' : []
  })
  values.value = next
  expanded.value = new Set()
}

// 打开弹窗时（重新）初始化：中断卡片可能来自另一轮执行，不能沿用上一轮填过的值
watch(() => props.modelValue, (open) => { if (open) resetValues() })
watch(() => props.card?.node_id, () => { if (props.modelValue) resetValues() })

const toggleExpand = (name) => {
  const next = new Set(expanded.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  expanded.value = next
}

/** 一期：文件清单用模拟数据填充（不真上传） */
const fillMockFiles = (field) => {
  values.value = { ...values.value, [field.name]: mockFileItemsOf() }
}

const clearField = (field) => {
  values.value = { ...values.value, [field.name]: kindOf(field) === 'text' ? '' : [] }
}

const isEmptyValue = (value) => {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return !value.trim()
  if (Array.isArray(value)) return value.length === 0
  return false
}

const submit = () => {
  const missing = fields.value.filter((field) => field?.required && isEmptyValue(values.value[field?.name]))
  if (missing.length) {
    ElMessage.warning(`请先填写：${missing.map((field) => field.label || field.name).join('、')}`)
    return
  }
  emit('submit', { nodeId: props.card?.node_id, values: { ...values.value } })
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="560px"
    append-to-body
    class="human-card-dialog"
  >
    <!-- 待确认数据：只读回显 -->
    <div v-if="dataItems.length" class="card-section">
      <div class="card-section__title">待确认数据</div>
      <div v-for="item in dataItems" :key="item.name" class="card-data-row">
        <div class="card-data-row__head">
          <span class="card-data-row__label">{{ item.label || item.name }}</span>
          <button class="card-data-row__toggle" type="button" @click="toggleExpand(item.name)">
            {{ expanded.has(item.name) ? '收起' : '展开' }}
          </button>
        </div>
        <div v-if="expanded.has(item.name)" class="card-data-row__json">
          <pre>{{ prettyJson(item.value) }}</pre>
        </div>
        <div v-else class="card-data-row__summary">{{ summarizeValue(item.value) }}</div>
      </div>
    </div>

    <!-- 待填写字段 -->
    <div class="card-section">
      <div class="card-section__title">填写确认内容</div>
      <div v-for="field in fields" :key="field.name" class="card-field">
        <div class="card-field__head">
          <span class="card-field__label">
            {{ field.label || field.name }}
            <span v-if="field.required" class="card-field__required">*</span>
          </span>
          <span class="card-field__type">{{ field.type || 'string' }}</span>
        </div>

        <template v-if="kindOf(field) === 'text'">
          <el-input
            v-model="values[field.name]"
            type="textarea"
            :rows="3"
            :placeholder="`请输入${field.label || field.name}`"
          />
        </template>

        <template v-else>
          <div class="card-field__actions">
            <el-button size="small" @click="fillMockFiles(field)">
              {{ isFileListField(field) ? '选择作业文件（模拟）' : '填充模拟数据' }}
            </el-button>
            <el-button size="small" text @click="clearField(field)">清空</el-button>
            <span class="card-field__count">已选 {{ (values[field.name] ?? []).length }} 条</span>
          </div>
          <ul v-if="(values[field.name] ?? []).length" class="card-field__list">
            <li v-for="(item, index) in values[field.name]" :key="index">
              {{ item?.path || item?.name || summarizeValue(item) }}
            </li>
          </ul>
        </template>
      </div>

      <div v-if="!fields.length" class="card-section__empty">
        该中断节点没有声明待填字段（`outputs` 为空）。
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.card-section + .card-section {
  margin-top: 16px;
}

.card-section__title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.card-section__empty {
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

/* ===== 待确认数据 ===== */
.card-data-row {
  padding: 8px 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
}

.card-data-row + .card-data-row {
  margin-top: 8px;
}

.card-data-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-data-row__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--tb-text-primary);
}

.card-data-row__toggle {
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--tb-primary);
  cursor: pointer;
}

.card-data-row__summary {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tb-text-tertiary);
  word-break: break-all;
}

.card-data-row__json pre {
  max-height: 200px;
  margin: 6px 0 0;
  overflow: auto;
  font-size: 12px;
  color: var(--tb-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

/* ===== 待填字段 ===== */
.card-field + .card-field {
  margin-top: 12px;
}

.card-field__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.card-field__label {
  font-size: 13px;
  color: var(--tb-text-primary);
}

.card-field__required {
  margin-left: 2px;
  color: var(--tb-danger);
}

.card-field__type {
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

.card-field__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-field__count {
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

.card-field__list {
  max-height: 160px;
  margin: 8px 0 0;
  padding-left: 18px;
  overflow: auto;
  font-size: 12px;
  color: var(--tb-text-secondary);
  word-break: break-all;
}
</style>
