<script setup>
/**
 * 人工确认弹窗：执行到人工确认节点中断时由画布页弹出。
 *
 * 数据全部来自后端的卡片帧（`conversation.message.completed`，`content_type = card`）：
 * - `card.data`   只读回显的**待确认数据**（中断节点 `inputs` 引用的上游产出）；
 * - `card.fields` 待用户填写的**表单字段**（中断节点 `outputs` 声明）。
 *
 * 分组规则（哪一项只读、哪一项要填、提交哪些值）**全部在 `utils/card-fields` 里**，
 * 本组件只负责渲染与抛事件 —— 这条规则漏了会表现为「流程成功但下游变量全是空」，
 * 属于必须被单测钉住的引擎契约，不能内联在这里（见该模块头部的说明）。
 *
 * 一个直接后果：`outputs` 与 `inputs` **同名**的字段是"只读 + 原值回填"，不会渲染成输入框。
 * 于是「填写确认内容」这一段在"所有字段都有上游来源"时整段不渲染 —— 卡片只剩
 * 「待确认数据 + 确定」，这才是"看一眼、点确认"应有的样子。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { prettyJson, summarizeValue } from '../utils/run-state'
import { blockingIssuesOf, kindOf, labelOf, resolveCard } from '../utils/card-fields'

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

/** 卡片的三组字段与提交取值（口径见 utils/card-fields） */
const resolved = computed(() => resolveCard(props.card))
/** 待填写字段（有可编辑控件的那一类） */
const editableFields = computed(() => resolved.value.editable)
/** 声明了但没有来源、也没有控件的字段：只提示，不拦截 */
const unfillableFields = computed(() => resolved.value.unfillable)
/** 是否需要渲染「填写确认内容」这一段 */
const hasFillSection = computed(() => editableFields.value.length > 0 || unfillableFields.value.length > 0)

/** 表单取值：字段名 → 取值（只读项已按上游原值预填） */
const values = ref({})

/** 展开查看完整 JSON 的字段名集合 */
const expanded = ref(new Set())

/** 打开弹窗时（重新）初始化：中断卡片可能来自另一轮执行，不能沿用上一轮填过的值 */
const resetValues = () => {
  values.value = { ...resolved.value.values }
  expanded.value = new Set()
}
watch(() => props.modelValue, (open) => { if (open) resetValues() })
watch(() => props.card?.node_id, () => { if (props.modelValue) resetValues() })

const toggleExpand = (name) => {
  const next = new Set(expanded.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  expanded.value = next
}

const submit = () => {
  const { sourceMissing, unfilled } = blockingIssuesOf(props.card, values.value)
  // 两种问题分开说：前者是"上游没给到值"（状态缺失，改弹窗也解决不了），后者才是"你还没填"
  if (sourceMissing.length) {
    ElMessage.warning(`上游未提供取值，无法确认：${sourceMissing.join('、')}`)
    return
  }
  if (unfilled.length) {
    ElMessage.warning(`请先填写：${unfilled.join('、')}`)
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
          <span class="card-data-row__label">{{ labelOf(item) }}</span>
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

    <!-- 待填写字段：只在这一段真有内容（可填写 / 需提示）时才渲染 -->
    <div v-if="hasFillSection" class="card-section">
      <div class="card-section__title">填写确认内容</div>
      <div v-for="field in editableFields" :key="field.name" class="card-field">
        <div class="card-field__head">
          <span class="card-field__label">
            {{ labelOf(field) }}
            <span v-if="field.required" class="card-field__required">*</span>
          </span>
          <span class="card-field__type">{{ field.type || 'string' }}</span>
        </div>
        <el-input
          v-if="kindOf(field) === 'text'"
          v-model="values[field.name]"
          type="textarea"
          :rows="3"
          :placeholder="`请输入${labelOf(field)}`"
        />
      </div>

      <div v-for="field in unfillableFields" :key="field.name" class="card-field">
        <div class="card-field__head">
          <span class="card-field__label">{{ labelOf(field) }}</span>
          <span class="card-field__type">{{ field.type || 'list' }}</span>
        </div>
        <div class="card-field__empty">
          该字段需由上游节点产出，本卡片不支持填写
        </div>
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

.card-field__empty {
  font-size: 12px;
  color: var(--tb-text-tertiary);
}
</style>
