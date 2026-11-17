/**
 * 工作流变量类型字典：与 NodeDataDrawer 的输出变量类型下拉保持同一套。
 *
 * 自定义导出列的「变量类型」直接复用这份字典（含 list / file 的二级元素类型），
 * 避免面板里出现第二套类型名。
 */
export const VARIABLE_TYPE_OPTIONS = Object.freeze([
  { value: 'string', label: 'String' },
  { value: 'integer', label: 'Integer' },
  { value: 'number', label: 'Number' },
  { value: 'boolean', label: 'Boolean' },
  { value: 'time', label: 'Time' },
  { value: 'object', label: 'Object' },
  {
    value: 'list',
    label: 'List',
    children: [
      { value: 'string', label: 'String' },
      { value: 'integer', label: 'Integer' },
      { value: 'number', label: 'Number' },
      { value: 'boolean', label: 'Boolean' },
      { value: 'object', label: 'Object' },
    ],
  },
  {
    value: 'file',
    label: 'File',
    children: [
      { value: 'string', label: 'String' },
      { value: 'integer', label: 'Integer' },
      { value: 'number', label: 'Number' },
    ],
  },
])

/** 类型短码：与节点卡片上的变量胶囊一致 */
export const VARIABLE_SHORT_TYPES = Object.freeze({
  string: 'str',
  integer: 'int',
  number: 'num',
  float: 'num',
  boolean: 'bool',
  time: 'time',
  list: 'arr',
  object: 'obj',
  file: 'file',
})

/** 允许作为 list / file 元素类型的集合 */
export const CONTAINER_ELEMENT_TYPES = Object.freeze(['string', 'integer', 'number', 'boolean', 'object'])

/** 类型 -> 展示短码；list / file 读作 `[str]` / `file` */
export function variableTypeCode(type, schema) {
  const base = VARIABLE_SHORT_TYPES[type] ?? VARIABLE_SHORT_TYPES.string
  if (type === 'list' || type === 'file') {
    const element = VARIABLE_SHORT_TYPES[schema?.type] ?? VARIABLE_SHORT_TYPES.string
    return type === 'list' ? `[${element}]` : `file`
  }
  return base
}

/** 类型下拉的显示树：完整类型名换成短码，数组子项读作 `[obj]`（与 NodeDataDrawer 同源） */
export const VARIABLE_TYPE_TREE = VARIABLE_TYPE_OPTIONS.map((option) => ({
  ...option,
  label: VARIABLE_SHORT_TYPES[option.value] ?? option.label,
  children: option.children?.map((child) => ({
    ...child,
    label: `[${VARIABLE_SHORT_TYPES[child.value] ?? child.label}]`,
  })),
}))

/** 判断顶层类型是否容器（需要二级元素类型） */
export const isContainerType = (type) => type === 'list' || type === 'file'
