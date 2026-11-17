/**
 * 评分区间与自定义导出列的纯函数（零依赖，可被 Node 用例直接引用）。
 *
 * 口径与后端 `HomeworkServiceImpl` 保持一致：
 * - 三个评分输入（score_min / score_max / pass_score）是老师在面板里直接填的值；
 * - 自定义导出列的「变量类型」直接复用工作流的类型字典（string / integer / number /
 *   boolean / time / object / list / file，list / file 带元素类型），全部由模型按描述产出；
 * - 列名 1–20 字且唯一，每列必须有描述；没有默认值 / 静态列。
 */
import {
  VARIABLE_TYPE_OPTIONS,
  VARIABLE_TYPE_TREE,
  isContainerType,
  variableTypeCode,
} from '../../workflow2/utils/variable-types.js'

/** 评分配置默认值（与工作流 startnode 的 default、后端常量一致） */
export const SCORE_RANGE_DEFAULT = Object.freeze({ min: 0, max: 100, pass: 60 })

/** 快捷预设：点一次填三个值，之后仍可改 */
export const SCORE_PRESETS = Object.freeze([
  { label: '0–100', min: 0, max: 100, pass: 60 },
  { label: '0–150', min: 0, max: 150, pass: 90 },
  { label: '0–20', min: 0, max: 20, pass: 12 },
  { label: '60–100', min: 60, max: 100, pass: 60 },
])

/** 三个评分输入的显示名（变量名与 startnode 的 key 一致） */
export const SCORE_INPUTS = Object.freeze([
  { key: 'score_min', label: '最低分' },
  { key: 'score_max', label: '最高分' },
  { key: 'pass_score', label: '及格线' },
])

/** 自定义列可用的完整类型树（与 NodeDataDrawer 输出变量同一套短码） */
export const CUSTOM_FIELD_TYPES = VARIABLE_TYPE_TREE
export const CUSTOM_FIELD_TYPE_VALUES = VARIABLE_TYPE_OPTIONS.map((option) => option.value)

/** 预置列（新建任务 / 旧数据迁移 / 工作流 startnode default 三处同源） */
const PRESET_FIELDS = [
  {
    key: 'deductions',
    description: '扣分明细：逐项列出失分项与扣分，每行一条：失分项 −扣分：依据（尽量引用原文片段）。',
    type: 'string',
  },
  {
    key: 'comment',
    description: 'AI 评语：先肯定具体优点，再指出具体问题与改进方向，与扣分明细一一对应。',
    type: 'string',
  },
]

/** 新建一份默认字段列表（每次返回新对象，避免面板之间共享引用） */
export function defaultCustomFields() {
  return PRESET_FIELDS.map((field) => ({ ...field }))
}

/** 类型归一：兼容早期 text / float 与短码，其余原样 */
export const normalizeCustomFieldType = (type) => {
  const aliases = {
    text: 'string',
    str: 'string',
    int: 'integer',
    num: 'number',
    bool: 'boolean',
    arr: 'list',
    obj: 'object',
  }
  const normalized = aliases[type] ?? type
  return normalized === 'float' ? 'number' : normalized
}

/** 类型展示短码（str / int / num / bool / time / [str] / obj / file） */
export const customTypeCode = (field) => variableTypeCode(
  normalizeCustomFieldType(field?.type),
  field?.schema,
)

/** 新增一列：变量名（即 key）留空由老师填写，默认文本类型 */
export function newCustomField() {
  return { key: '', description: '', type: 'string' }
}

const textOf = (value) => (value == null ? '' : String(value).trim())

/** 变量名（key）格式与保留字段，与后端 HomeworkServiceImpl 保持一致 */
const CUSTOM_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/
const RESERVED_KEYS = new Set(['student', 'score', 'grade', 'readFiles', 'custom'])
const finiteOf = (value) => {
  if (value == null || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

/** custom 值归一：对象直接用，模型偶尔写成 JSON 字符串时解析一次，其余归空对象 */
export function customValuesOf(values) {
  if (values == null) return {}
  if (typeof values === 'object' && !Array.isArray(values)) return values
  if (typeof values === 'string') {
    try {
      const parsed = JSON.parse(values)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
    } catch {
      return {}
    }
  }
  return {}
}

/** 区间校验；返回错误文案或空串 */
export function validateScoreRange(range = {}) {
  const min = finiteOf(range.min)
  const max = finiteOf(range.max)
  const pass = finiteOf(range.pass)
  if (min == null || max == null || pass == null) return '请填写完整的评分区间与及格线'
  if (min >= max) return '最低分必须小于最高分'
  if (pass < min || pass > max) return '及格线必须落在评分区间内'
  return ''
}

/** 容器类型的合法元素类型（list / file 各自不同，直接取字典里的 children） */
const elementTypesOf = (type) =>
  VARIABLE_TYPE_OPTIONS.find((option) => option.value === type)?.children?.map((child) => child.value) ?? []

/** 自定义列校验；返回错误文案或空串 */
export function validateCustomFields(fields = []) {
  if (!Array.isArray(fields)) return '自定义导出列格式不正确'
  const keys = new Set()
  for (const [index, field] of fields.entries()) {
    const key = textOf(field?.key)
    const description = textOf(field?.description)
    const type = normalizeCustomFieldType(textOf(field?.type))
    const at = `第 ${index + 1} 列`

    if (!key) return `${at}：请填写变量名`
    if (!CUSTOM_KEY_PATTERN.test(key)) return `${at}：变量名只能是字母、数字、下划线且不能以数字开头`
    if (RESERVED_KEYS.has(key)) return `${at}：变量名不能使用保留字段「${key}」`
    if (key.length > 40) return `${at}：变量名不能超过 40 个字符`
    if (keys.has(key)) return `变量名重复：${key}`
    keys.add(key)
    if (!CUSTOM_FIELD_TYPE_VALUES.includes(type)) return `${at}：不支持的类型 ${type}`
    if (isContainerType(type)) {
      const elementType = textOf(field?.schema?.type)
      const allowed = elementTypesOf(type)
      if (!allowed.includes(elementType)) {
        return `${at}：${type} 需要选择元素类型（${allowed.join(' / ')}）`
      }
    }
    if (!description) return `${at}：请填写描述，供模型按列产出内容`
    if (description.length > 120) return `${at}：描述不能超过 120 个字符`
  }
  return ''
}

/**
 * 成绩卡 / 导出用的字段值：按定义顺序返回 [{...field, kind, value}]。
 * kind 决定渲染方式：text / number / boolean / list / object。
 */
export function customRows(fields = [], values = {}) {
  const source = customValuesOf(values)
  return (Array.isArray(fields) ? fields : [])
    .filter((field) => field?.key)
    .map((field) => {
      const type = normalizeCustomFieldType(field.type)
      const raw = source[field.key]
      const kind = ['integer', 'number'].includes(type)
        ? 'number'
        : type === 'boolean'
          ? 'boolean'
          : type === 'list'
            ? 'list'
            : type === 'object'
              ? 'object'
              : 'text'
      return {
        key: field.key,
        label: textOf(field.key),
        type,
        schema: field.schema ?? null,
        typeCode: variableTypeCode(type, field.schema),
        kind,
        value: raw == null ? '' : raw,
      }
    })
}

/** 回写 payload 用的 custom 对象：只保留定义里的 key，缺失补空串 */
export function buildCustomValues(fields = [], values = {}) {
  const source = customValuesOf(values)
  const result = {}
  for (const field of Array.isArray(fields) ? fields : []) {
    const key = textOf(field?.key)
    if (!key) continue
    const raw = source[key]
    result[key] = raw == null ? '' : raw
  }
  return result
}
