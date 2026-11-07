<template>
  <el-dialog
    v-model="visible"
    title="输入数据"
    width="560px"
    :close-on-click-modal="false"
    @open="restore"
  >
    <div class="start-inputs">
      <p class="start-inputs__hint">
        开始节点声明的业务输入：执行前必须显式提供，不再使用内置演示数据。
        上次填写会按工作流记忆，便于重复试跑。
      </p>

      <div v-for="field in outputs" :key="field.name" class="start-inputs__field">
        <div class="start-inputs__head">
          <span class="start-inputs__name">{{ field.name }}</span>
          <span class="start-inputs__type">{{ field.type ?? 'string' }}</span>
          <span v-if="isRequired(field)" class="start-inputs__required">必填</span>
          <span v-if="field.description" class="start-inputs__desc" :title="field.description">
            {{ field.description }}
          </span>
        </div>
        <!-- list / object 用 JSON 编辑（提交时解析校验），string / number 用文本域 -->
        <el-input
          v-model="raw[field.name]"
          type="textarea"
          :rows="isText(field) ? 3 : 6"
          :placeholder="placeholderOf(field)"
          :autosize="{ minRows: isText(field) ? 3 : 6, maxRows: 14 }"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="submit">开始运行</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 画布试运行的「输入数据」弹窗：按开始节点 `data.outputs` 的声明收集业务输入。
 *
 * 后端开始节点（docs/12 §4.4）按声明直读状态顶层同名键、required 缺失直接报错——
 * 所以试运行前必须在这里把值填好；list / object 用 JSON 编辑（提交时解析校验），
 * string / number 用文本域。上次填写按 workflowId 存 localStorage，便于重复试跑。
 * 开始节点没有声明输出时画布页不会打开本弹窗（维持原行为，无需输入）。
 */
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  /** 开始节点声明的输出变量：[{ name, type, required, description }] */
  outputs: { type: Array, default: () => [] },
  /** 当前工作流 id（localStorage 记忆键的一部分） */
  workflowId: { type: String, default: '' },
})

const emit = defineEmits(['submit'])

/** v-model 控制显隐 */
const visible = defineModel({ type: Boolean, default: false })

/** 各变量的原始输入：string / number 直接是文本，list / object 是待解析的 JSON 文本 */
const raw = reactive({})

const STORAGE_PREFIX = 'workflow2:start-inputs'
const storageKey = () => `${STORAGE_PREFIX}:${props.workflowId || 'default'}`

const isText = (field) => {
  const type = String(field?.type ?? 'string').toLowerCase()
  return type === 'string' || type === 'number'
}

const isRequired = (field) => Boolean(field?.required)

const placeholderOf = (field) => {
  if (isText(field)) return field?.type === 'number' ? '例如 100' : '请输入文本'
  const type = String(field?.type ?? '').toLowerCase()
  if (type === 'object') return 'JSON 对象，例如 { "key": "value" }'
  return 'JSON 数组，例如 [{ "fileId": 122, "path": "作业/张三.docx" }]'
}

/** 每次打开都按「声明 × 上次填写」重建表单：声明变更（增删变量）后不会残留旧键 */
function restore() {
  let saved = {}
  try {
    saved = JSON.parse(localStorage.getItem(storageKey()) ?? '{}') ?? {}
  } catch {
    saved = {}
  }
  Object.keys(raw).forEach((key) => delete raw[key])
  props.outputs
    .filter((field) => field?.name)
    .forEach((field) => {
      raw[field.name] = typeof saved[field.name] === 'string' ? saved[field.name] : ''
    })
}

watch(
  () => [props.workflowId, props.outputs],
  () => {
    if (visible.value) restore()
  },
  { deep: true },
)

/** 提交：解析 JSON → required 校验 → 记忆 → 交回父组件发起运行 */
function submit() {
  const inputs = {}
  for (const field of props.outputs) {
    const name = field?.name
    if (!name) continue
    const text = String(raw[name] ?? '').trim()

    if (isText(field)) {
      inputs[name] = field?.type === 'number' && text !== '' ? Number(text) : text
    } else if (text) {
      // 空文本视为未提供，交给下面的 required 校验；非空必须是合法 JSON
      try {
        inputs[name] = JSON.parse(text)
      } catch (error) {
        ElMessage.error(`「${name}」不是合法 JSON：${error?.message ?? '解析失败'}`)
        return
      }
    }
  }

  const missing = props.outputs.filter(
    (field) =>
      field?.name &&
      isRequired(field) &&
      (inputs[field.name] == null ||
        (typeof inputs[field.name] === 'string' && !inputs[field.name].trim()) ||
        (Array.isArray(inputs[field.name]) && !inputs[field.name].length)),
  )
  if (missing.length) {
    ElMessage.warning(`必填变量未提供：${missing.map((field) => field.name).join('、')}`)
    return
  }

  try {
    localStorage.setItem(storageKey(), JSON.stringify(raw))
  } catch {
    // localStorage 不可用（隐私模式 / 配额）不影响运行，只是不记忆
  }

  emit('submit', inputs)
  visible.value = false
}
</script>

<style scoped>
.start-inputs {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.start-inputs__hint {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--tb-text-tertiary, var(--el-text-color-secondary));
}

.start-inputs__field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.start-inputs__head {
  display: flex;
  align-items: baseline;
  gap: 0.45rem;
  min-width: 0;
}

.start-inputs__name {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--tb-text-primary, var(--el-text-color-primary));
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.start-inputs__type {
  font-size: 0.66rem;
  color: var(--tb-info, var(--el-color-info));
}

.start-inputs__required {
  font-size: 0.64rem;
  font-weight: 700;
  color: var(--tb-danger, var(--el-color-danger));
}

.start-inputs__desc {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.68rem;
  color: var(--tb-text-tertiary, var(--el-text-color-secondary));
}
</style>
