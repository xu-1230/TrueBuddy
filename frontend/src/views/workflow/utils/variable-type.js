/**
 * 变量类型的显示口径（纯函数模块，不引入 Vue 与 Element Plus）。
 *
 * 抽屉与画布节点卡片共用同一份映射，避免两处各写一套导致显示不一致：
 * - 类型短码：类型选择器触发器与节点卡片胶囊的紧凑写法（标量 `str`、数组 `[str]`）；
 * - 完整类型名：类型级联面板的选项文案与悬浮提示（`String` / `Array<String>`）。
 *
 * 短码与完整类型名都从**变量声明**（`{ type, schema }`）出发，所以同一份声明在抽屉里
 * 与卡片胶囊上的读法完全一致 —— 卡片上的 `[str]` 就是抽屉触发器上的 `[str]`。
 */

/** 类型短码（与后端 schema 的 type 取值一一对应） */
const SHORT_TYPES = {
  string: 'str',
  integer: 'int',
  number: 'num',
  float: 'num',
  boolean: 'bool',
  list: 'arr',
  object: 'obj',
  file: 'file',
}

/** 完整类型名（Coze 写法的首字母大写形式） */
const FULL_TYPES = {
  string: 'String',
  integer: 'Integer',
  number: 'Number',
  float: 'Number',
  boolean: 'Boolean',
  list: 'Array',
  object: 'Object',
  file: 'File',
}

/** 未知类型按字符串处理，保证画布上的历史数据也能正常显示 */
const FALLBACK_TYPE = 'string'

/**
 * 类型短码：类型选择器触发器与节点卡片胶囊的紧凑写法。
 *
 * 只产出类型名本身的短码；数组的方括号由调用方补（`list` 渲染成 `[元素类型短码]`），
 * 因为「括号 = 数组」是展示层的约定，不该混进映射表。
 *
 * @param {string} type 后端类型：string|integer|number|float|boolean|list|object|file
 * @returns {string} str | int | num | bool | arr | obj | file
 */
export function shortTypeOf(type) {
  return SHORT_TYPES[type] ?? SHORT_TYPES[FALLBACK_TYPE]
}

/**
 * 紧凑类型短码（含数组方括号）：类型选择器触发器与**节点卡片胶囊**共用。
 *
 * 方括号只标记「数组」这一维：标量直接写短码（`str` / `int` / `bool` …），
 * 数组写成 `[元素类型短码]`（`[str]` 即 `Array<String>`）。
 * 卡片胶囊过去写的是裸 `arr`，与抽屉触发器对不上，故统一收敛到这里。
 *
 * @param {{ type?: string, schema?: { type?: string } }} declaration 变量声明
 * @returns {string} str | int | num | bool | obj | file | [str] | [int] | …
 */
export function shortTypeCodeOf(declaration) {
  const type = declaration?.type ?? FALLBACK_TYPE
  if (type !== 'list') {
    return shortTypeOf(type)
  }
  return `[${shortTypeOf(declaration?.schema?.type ?? FALLBACK_TYPE)}]`
}

/**
 * 完整类型名：类型级联面板的选项文案与悬浮提示使用。
 *
 * 列表类型读取元素类型：`{ type:'list', schema:{ type:'string' } }` → `Array<String>`；
 * 元素类型缺失时退化为 `Array<String>`，与后端 coerce 的默认口径一致。
 *
 * @param {{ type?: string, schema?: { type?: string } }} declaration 变量声明
 * @returns {string} String | Integer | Number | Boolean | Object | File | Array<String>
 */
export function displayTypeOf(declaration) {
  const type = declaration?.type ?? FALLBACK_TYPE
  if (type !== 'list') {
    return FULL_TYPES[type] ?? FULL_TYPES[FALLBACK_TYPE]
  }
  const itemType = declaration?.schema?.type ?? FALLBACK_TYPE
  return `Array<${FULL_TYPES[itemType] ?? FULL_TYPES[FALLBACK_TYPE]}>`
}

/**
 * 后端取值槽（`InputSlotDef.value`）是否已绑定到值。
 *
 * 面向节点数据里原始的 `input.value`：画布节点卡片用它判断某个输入是否「尚未定义」
 * （未定义的输入会以告警色显示）。抽屉里的取值草稿不在此校验范围，
 * 那边由 `NodeDataDrawer.inputErrorOf` 直接按引用是否有效判定。
 *
 * @param {{ type?: string, content?: unknown }|null|undefined} slotValue
 * @returns {boolean}
 */
export function hasBoundValue(slotValue) {
  if (slotValue?.type === 'ref') {
    const content = slotValue.content
    return Boolean(content && typeof content === 'object' && content.blockID && content.name)
  }
  return Boolean(String(slotValue?.content ?? '').trim())
}
