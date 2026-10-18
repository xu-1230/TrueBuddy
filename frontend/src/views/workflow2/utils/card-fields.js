/**
 * 人工确认卡片的**取值口径**（纯函数，无 Vue / DOM 依赖，可脱离组件单测）。
 *
 * 为什么捏在这里而不是内联进弹窗：这条规则不是 UI 细节，而是**引擎契约的一部分** ——
 * 后端 `HumanNode#apply` 只从本次提交的 `values` 回写 `state[中断节点id]`，空提交直接返回
 * `Map.of()`（该节点的产出在状态里压根不存在）。于是"哪些字段必须回填、回填什么"写错的表现是
 * **流程照样成功、下游变量却全是空**（实测过：汇总节点的提示词里满分与及格线变成空串），
 * 这种错必须能被单测钉住，不能只靠肉眼看弹窗。
 *
 * 卡片帧的两组数据（口径由后端 `HumanNode#interrupt` 决定）：
 * - `card.data`   只读回显的**待确认数据**：按中断节点 `data.inputs` 的引用逐个取值，
 *                 每项 `{name, type, label, value}`；
 * - `card.fields` 待用户填写的**表单字段**：按中断节点 `data.outputs` 声明生成，
 *                 每项 `{name, type, required, label, schema}`。
 *
 * 本模块把两者按**变量名**对齐，分成三类：
 *
 * | 分类 | 判据 | 界面 | 提交 |
 * | --- | --- | --- | --- |
 * | `readonly` | 在 `fields` 里、且 `data` 里有同名项 | 只读（`data` 那一段已经在展示它，不重复渲染） | **原值回填**：不提交它，下游就拿不到值 |
 * | `editable` | 在 `fields` 里、`data` 里没有同名项、且是文本类 | 输入框，用户填 | 填什么提交什么 |
 * | `unfillable` | 在 `fields` 里、`data` 里没有同名项、且非文本类 | 只读提示「需上游节点产出」 | 不提交，且**不参与必填拦截**（否则会出现填不了又过不去的死锁） |
 *
 * `unfillable` 这一类是刻意的：列表类的录入控件（选择作业文件 / 结构化名单编辑器）当前不存在，
 * 而"人工手填列表"在本系统里也没有真实场景 —— 作业文件清单由 `POST /homework/upload` +
 * 开始节点提供。真要做靠的是结构化编辑器，不是这里。
 */

/**
 * 字段类型的归一化：只把列表与对象挑出来，其余（`string` / `text` / 缺省）一律当文本。
 *
 * @param {object} field 卡片字段声明
 * @returns {'text'|'list'|'object'} 归一化后的类型
 */
export const kindOf = (field) => {
  const type = String(field?.type ?? '').toLowerCase()
  if (type === 'list' || type === 'array') return 'list'
  if (type === 'object') return 'object'
  return 'text'
}

/** 展示用标签：字段说明优先，缺省回落变量名（与后端 `HumanNode#labelOf` 同一口径） */
export const labelOf = (item) => item?.label || item?.name || ''

/**
 * 值是否"空"：`null` / `undefined` / 空串 / 空数组都算。
 *
 * 注意 `false` 与 `0` **不算空** —— 满分填 0、布尔开关关掉都是合法取值。
 *
 * @param {unknown} value
 * @returns {boolean}
 */
export const isEmptyValue = (value) => {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return !value.trim()
  if (Array.isArray(value)) return value.length === 0
  return false
}

/**
 * 把卡片拆成「只读回填 / 待填写 / 填不了」三组，并给出提交用的初始取值。
 *
 * @param {object|null} card 卡片载荷 `{node_id, card_type, title, data, fields}`
 * @returns {{
 *   values: Record<string, unknown>,
 *   readonly: Array<object>,
 *   editable: Array<object>,
 *   unfillable: Array<object>,
 * }} `values` 是提交取值（只读项已回填上游原值，待填项为空）；
 *   `readonly` 项带 `value`，`editable` / `unfillable` 项是原字段声明。
 */
export function resolveCard(card) {
  const dataItems = Array.isArray(card?.data) ? card.data : []
  const fields = Array.isArray(card?.fields) ? card.fields : []
  const dataByName = new Map(dataItems.map((item) => [item?.name, item]))

  const values = {}
  const readonly = []
  const editable = []
  const unfillable = []

  for (const field of fields) {
    const name = field?.name
    if (!name) continue
    const source = dataByName.get(name)
    if (source) {
      // 同名：上游已经产出过，老师不该为"我已经确认过的内容"再负一次录入责任，
      // 这里是引擎唯一能收到值的通道（见模块头），所以必须原值回填
      values[name] = source.value
      readonly.push({ ...field, value: source.value })
      continue
    }
    if (kindOf(field) === 'text') {
      values[name] = ''
      editable.push(field)
      continue
    }
    // 列表 / 对象类且没有上游来源：当前没有可用控件，也没有真实场景（见模块头）
    unfillable.push(field)
  }

  return { values, readonly, editable, unfillable }
}

/**
 * 提交前的拦截项，分成两种文案（两者的处置方式完全不同，不能混为一谈）：
 *
 * - `sourceMissing`：「上游未提供取值」—— `readonly` 项必填、但上游那一步没产出（取到 `undefined`）。
 *   这是**状态缺失**，挡下来是对的：继续跑下去下游会静默拿到空串。
 * - `unfilled`：「请先填写」—— `editable` 项必填、用户还没填。
 *
 * `unfillable` 项**不参与拦截**（没有控件可填，拦了就是死锁）。
 *
 * @param {object|null} card 卡片载荷
 * @param {Record<string, unknown>} values 当前表单取值
 * @returns {{sourceMissing: string[], unfilled: string[]}} 需要展示的字段标签清单
 */
export function blockingIssuesOf(card, values = {}) {
  const { readonly, editable } = resolveCard(card)
  return {
    sourceMissing: readonly
      .filter((item) => item.required && isEmptyValue(item.value))
      .map(labelOf),
    unfilled: editable
      .filter((field) => field.required && isEmptyValue(values[field.name]))
      .map(labelOf),
  }
}
