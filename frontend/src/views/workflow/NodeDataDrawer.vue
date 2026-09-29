<script setup>
/**
 * 节点数据抽屉。
 *
 * 画布上点中任意节点后打开，按节点自身的 data 结构布局出可编辑的表单：
 * 节点信息卡（图标 + 名称 + 类型说明）、名称编辑、变量分组（展示哪几组由 groupPlanOf 决定）、
 * 迭代节点的内部节点列表、以及只读的原始 JSON。
 *
 * 位置不在这里编辑：节点坐标由画布拖拽决定，抽屉暴露坐标只会与拖拽结果互相打架，
 * 需要时可在「原始数据」里查看。
 *
 * 数据流向刻意保持单一：抽屉只持有「草稿」，点保存时通过 updateNode 写回 Vue Flow store，
 * 节点卡片与连线会随 store 自动更新（绘制数据请一律以 store 为准）。
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, FullScreen, Plus, RefreshLeft } from '@element-plus/icons-vue'
import { resolveNodeIcon } from './utils/node-icons'
import { getScopeIndex } from './utils/workflow-scope'
import { shortTypeCodeOf } from './utils/variable-type'
import VariablePicker from './VariablePicker.vue'
import VariableTypeSelect from './VariableTypeSelect.vue'

defineOptions({ name: 'NodeDataDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  nodeId: { type: String, default: null },
})

const emit = defineEmits(['update:visible', 'select'])

const { getNodes, getEdges, updateNode, removeNodes } = useVueFlow()

/* ===== 常量 ===== */

/** 节点类型 → 中文名 */
const NODE_TYPE_LABELS = {
  start: '开始节点',
  input: '开始节点',
  output: '结束节点',
  llm: '大模型节点',
  human: '人工确认节点',
  iteration: '迭代节点',
  'iteration-start': '迭代起点',
  'iteration-end': '迭代终点',
}

/** 节点类型 → 作用说明（展示在信息卡里，与节点库卡片的 hint 同源口径） */
const NODE_TYPE_DESCRIPTIONS = {
  start: '工作流的起始节点，声明启动工作流所需的输入；这些输入变量同时作为开始节点的输出，供后续节点引用',
  input: '工作流的起始节点，声明启动工作流所需的输入；这些输入变量同时作为开始节点的输出，供后续节点引用',
  output: '工作流的结束节点，声明工作流最终的输出；这些输出变量以本节点输入的方式选择上游变量的取值',
  end: '工作流的结束节点，声明工作流最终的输出；这些输出变量以本节点输入的方式选择上游变量的取值',
  llm: '调用大语言模型，根据输入消息与提示词生成回复',
  human: '执行到此节点会中断，等待人工确认清单后继续执行',
  iteration: '对列表中的每一项重复执行内部的子节点',
  'iteration-start': '迭代节点内部的循环起点',
  'iteration-end': '迭代节点内部的循环终点',
}

/**
 * 节点要展示哪些变量分组，以及每组的数据落在 data 的哪个键、要不要「取值」列。
 *
 * **展示名按用户视角，数据落点按变量在功能上的实际用途** —— 两者刻意错位，这正是
 * 开始 / 结束节点的语义（与画布卡片同源：InputNode 读 `data.outputs` 却标「输入」，
 * OutputNode 读 `data.outputs` 却标「输出」）：
 * - 开始节点（input / start）：只展示「输入变量」。声明落在 `data.outputs`，因为它在功能上
 *   是本节点的输出（会被下游引用），所以按纯声明样式渲染（变量名 | 类型 | 描述，无取值列）；
 * - 结束节点（output / end）：只展示「输出变量」。声明同样落在 `data.outputs`，但它在功能上是
 *   本节点的输入（执行时由上游节点的输出提供），因此按取值样式渲染 —— 每行都选上游变量，
 *   取值写回 `backendInputs.inputParameters`；
 * - 其余节点：输入 / 输出两组都展示，输入组带取值列（引用上游变量 / 固定值）。
 *
 * 字段含义：
 * - key：草稿与折叠面板的键（模板里 `draft.groups[key]`、`expandedGroups` 都用它）；
 * - label：分组标题（用户视角的展示名）；
 * - dataKey：声明实际落库的 data 键 —— 开始节点是 outputs 而非 inputs，映射就体现在这里；
 * - value：该组是否需要「取值」列（VariablePicker + 写回 backendInputs）；
 * - hint：标题下的一句话说明，把「展示名 ↔ 实际用途」的映射讲清楚。
 *
 * 分组一律全部列出，**不按 data 里是否已存在过滤**：即使是还没有任何变量的空白节点，
 * 也要能看到完整框架 —— 分组标题、计数与「添加」按钮，否则用户无从声明第一个变量。
 * 保存时仍遵守「原本没有该分组且没填内容就不写入」的规则，不会往 data 里塞空数组。
 *
 * 只有 inputs / outputs 两种分组：曾经的第三组「数据块」（data.blocks）没有生产者也没有
 * 消费者（后端 NodeDataDef 无该字段、执行不读，节点卡片仅在 outputs / inputs 都为空时兜底
 * 读到），属命名撞上后端「循环子节点 blocks」的遗留，已整体移除。
 */
const groupPlanOf = (type) => {
  if (type === 'input' || type === 'start') {
    return [
      {
        key: 'inputs',
        label: '输入变量',
        dataKey: 'outputs',
        value: false,
        hint: '开始节点声明的输入变量同时是工作流的输出，供后续节点引用。',
      },
    ]
  }
  if (type === 'output' || type === 'end') {
    return [
      {
        key: 'outputs',
        label: '输出变量',
        dataKey: 'outputs',
        value: true,
        hint: '结束节点声明的输出变量以「选择上游变量」的方式取值，功能上作为本节点的输入。',
      },
    ]
  }
  /*
   * 迭代容器自身不再配置参数：三段参数分别落在它的起止节点上（见 ownerNode 的说明）。
   * 容器抽屉只保留「内部节点」列表，避免同一份数据出现两个编辑入口。
   */
  if (type === 'iteration') return []
  if (type === 'iteration-start') {
    return [
      {
        key: 'loopArray',
        label: '循环数组',
        dataKey: 'inputs',
        value: true,
        // 只有一行、名字锁死 item：执行侧就是拿这一项当迭代来源
        arrayOnly: true,
        hint: '迭代来源。只能引用上游的数组变量；循环体内用 item 取到当前元素。',
      },
      {
        key: 'inputs',
        label: '中间变量',
        dataKey: 'inputs',
        value: true,
        // 与循环数组共用 inputParameters，这里剔掉 item，避免同一行出现两次
        withoutArrayOnly: true,
        hint: '循环体要用到的其它变量，声明的每一项都会注入循环体，循环内的节点可直接引用。',
      },
    ]
  }
  if (type === 'iteration-end') {
    return [
      {
        key: 'outputs',
        label: '输出',
        dataKey: 'outputs',
        value: false,
        // 第一项还要指定「每轮结果取自哪个内部节点的输出」
        aggregate: true,
        hint: '迭代结果按轮次汇总成列表；每轮的取值取自下面指定的内部节点输出。',
      },
    ]
  }
  return [
    { key: 'inputs', label: '输入变量', dataKey: 'inputs', value: true },
    { key: 'outputs', label: '输出变量', dataKey: 'outputs', value: false },
  ]
}

/* ===== 当前节点 ===== */

/** 选中的节点：只接收 id，节点对象实时从 store 取，避免拿到过期引用 */
const liveNode = computed(() =>
  props.nodeId ? getNodes.value.find((item) => item.id === props.nodeId) ?? null : null,
)

/**
 * 抽屉关闭动画期间节点可能已经被删掉，用最后一次的节点兜底，
 * 否则关闭过程中内容会闪一下空状态。
 */
const lastNode = ref(null)
watch(
  liveNode,
  (value) => {
    if (value) lastNode.value = value
  },
  { immediate: true },
)

const node = computed(() => liveNode.value ?? lastNode.value)

/* ===== 迭代的起止节点：只是入口，参数落在父容器 ===== */

/** 迭代容器的起止节点类型（画布专用，后端图上不存在） */
const ITERATION_BOUNDARY_TYPES = ['iteration-start', 'iteration-end']

/** 被点的是迭代的起点 / 终点 */
const isIterationBoundary = computed(() => ITERATION_BOUNDARY_TYPES.includes(node.value?.type))

/**
 * 数据宿主：草稿读写的真正对象。
 *
 * 普通节点就是自己；迭代的起止节点是**父容器** —— 它们在执行侧根本不存在
 * （后端只认 1/2/3/21/30，`workflow-schema.js` 的类型白名单也不含它们），
 * 参数写在它们自己身上就是死数据：既不落库也不执行。
 *
 * 所以三段参数全部写在容器上，且与 `LoopNodeAction` 的读法严格对齐：
 * - 循环数组 / 中间变量 → 容器的 `backendInputs.inputParameters`；
 * - 输出 → 容器的 `data.outputs`（第一项的 `input` 声明每轮结果的聚合来源）。
 */
const ownerNode = computed(() => {
  const current = node.value
  if (!current) return null
  if (!isIterationBoundary.value) return current
  return getNodes.value.find((item) => item.id === current.parentNode) ?? current
})

/** 被点的是起点还是终点：决定三段里展示哪几段 */
const boundarySide = computed(() =>
  node.value?.type === 'iteration-start' ? 'start' : node.value?.type === 'iteration-end' ? 'end' : null,
)

/** 循环数组那一行在容器 inputParameters 里的固定名字（执行侧按名字认数组，不允许改） */
const LOOP_ITEM_KEY = 'item'

/**
 * 展示用名称：编辑期间优先取草稿值，
 * 这样双击改完回车就能立刻在标题上看到，不用等「保存」才回显（写库仍然要等保存）。
 *
 * 起止节点的名字是画布语义（迭代起点 / 迭代终点），不做重命名，也不显示容器的名字。
 */
const nodeLabel = computed(() => {
  if (isIterationBoundary.value) return NODE_TYPE_LABELS[node.value?.type] ?? node.value?.id ?? ''
  return draft.label || node.value?.data?.label || node.value?.id || '未选择节点'
})

const isIteration = computed(() => node.value?.type === 'iteration')

/** 迭代节点内部的子节点（含自带的起止节点） */
const childNodes = computed(() => {
  if (!isIteration.value || !liveNode.value) return []
  return getNodes.value.filter((item) => item.parentNode === liveNode.value.id)
})

/** 信息卡图标：与节点库拖拽卡片、画布上的节点图标同源 */
const nodeIcon = computed(() => resolveNodeIcon(node.value?.type))

/** 信息卡描述：说明该节点的作用 */
const nodeDescription = computed(
  () => NODE_TYPE_DESCRIPTIONS[node.value?.type] ?? '该节点的配置信息',
)

/* ===== 草稿 ===== */

const draft = reactive({
  label: '',
  /** { inputs: [], outputs: [] } */
  groups: {},
})

/** 上一次保存 / 初始化时的快照，用来判断是否有未保存的修改 */
const cleanSnapshot = ref('')

const serializeDraft = () => JSON.stringify({ label: draft.label, groups: draft.groups })

const dirty = computed(() => Boolean(node.value) && serializeDraft() !== cleanSnapshot.value)

/* ===== 变量分组的折叠状态 ===== */

/** 展开的分组 key；空数组表示全部收起（默认状态） */
const expandedGroups = ref([])

/** 变量项：字符串写法补成完整对象，对象写法深拷贝（保留 schema 等未知字段） */
const cloneVariable = (item) => {
  if (typeof item === 'string') return { name: item, type: 'string', description: '' }
  if (!item || typeof item !== 'object') return { name: '', type: 'string', description: '' }
  try {
    return JSON.parse(JSON.stringify(item))
  } catch {
    return { name: '', type: 'string', description: '' }
  }
}

/* ===== 迭代输出：每轮结果取自哪个内部节点 ===== */

/**
 * 「结果取自」的下拉值，形如 `{内部子节点id}.{输出名}`；空串表示自动。
 *
 * 候选全部本地推导：起止节点不在后端执行图中，而循环体内部子节点的输出**就在画布上**，
 * 本地推导即可，生成的引用与执行侧写状态的键一致
 * （`MockLlmNodeAction`：`{nodeId}.{声明的输出名}`）。
 */
const aggregateRefKey = ref('')

/** 从容器 outputs[0].input 里回读「结果取自」（后端结构是 input.value.content） */
const buildAggregateKey = (input) => {
  const content = input?.value?.content
  return content?.blockID && content?.name ? `${content.blockID}.${content.name}` : ''
}

/** 容器内部子节点（排除起止节点）自己声明的输出 */
const innerOutputOptions = computed(() => {
  const container = ownerNode.value
  if (!container) return []
  const options = []
  for (const child of getNodes.value) {
    if (child.parentNode !== container.id) continue
    if (ITERATION_BOUNDARY_TYPES.includes(child.type)) continue
    for (const output of child.data?.outputs ?? []) {
      const name = typeof output === 'string' ? output : output?.name
      if (!name) continue
      options.push({
        refKey: `${child.id}.${name}`,
        label: `${child.data?.label || child.id} · ${name}`,
      })
    }
  }
  return options
})

/* ===== 重命名 ===== */

/** 是否处于标题编辑态 */
const renaming = ref(false)
/** 编辑中的名称（草稿外的临时值，回车 / 失焦才写进 draft） */
const renameValue = ref('')
/** 编辑框实例，用它把光标送进去并全选 */
const renameInput = ref(null)

const startRename = async () => {
  // 起止节点不做重命名：名字是画布语义，改了也不落库
  if (!liveNode.value || renaming.value || isIterationBoundary.value) return
  renameValue.value = draft.label
  renaming.value = true
  await nextTick()
  renameInput.value?.focus()
  renameInput.value?.select()
}

/** 提交：只写草稿，与变量编辑保持一致 —— 落盘统一交给底部「保存」 */
const commitRename = () => {
  if (!renaming.value) return
  draft.label = renameValue.value.trim()
  renaming.value = false
}

const cancelRename = () => {
  renaming.value = false
  renameValue.value = ''
}

/* ===== 输入取值：上游变量选择器（Coze 风格） ===== */

/**
 * 候选集由本地「作用域索引」同步给出（见 `utils/workflow-scope.js`），**不请求后端**。
 *
 * 索引复用 `workflow-reference.js` 的 `visibleUpstreamNodeIds`，再补上循环体局部变量
 * （`item` + 循环入参），因此选择器里选得出的变量与执行侧同口径，不会触发
 * 「引用必须来自上游」的校验失败。
 *
 * 全图索引在图不变时复用（`getScopeIndex` 内部按结构签名 memo），所以切换节点只是一次
 * Map 查找 —— 这既是「打开抽屉零后端请求」的来源，也是拖动节点不会反复重算的原因。
 */
const scopeIndex = computed(() => getScopeIndex(getNodes.value, getEdges.value))

/** 本地计算没有异步过程，恒为 false（保留 VariablePicker 的 loading 语义） */
const upstreamLoading = ref(false)

/** 基础类型 → 画布 rawMeta.type（与后端 mock 画布的取值约定一致） */
const RAW_META_TYPE = {
  string: 1,
  integer: 2,
  boolean: 3,
  float: 4,
  number: 4,
  list: 99,
  object: 99,
  file: 99,
}

/** 当前节点要展示的变量分组：开始 / 结束只展示一组，其余节点两组都展示 */
const visibleGroups = computed(() => groupPlanOf(node.value?.type))

/** 该分组是否需要「取值」列（引用上游变量 / 固定值） */
const isValueGroup = (key) => visibleGroups.value.some((group) => group.key === key && group.value)

/** 当前节点是否有带取值的分组：没有（开始节点，它声明的是工作流输入、没有上游）就不必去算上游候选变量 */
const hasValueGroup = computed(() => visibleGroups.value.some((group) => group.value))

/**
 * 上游可见变量候选：直接读全图作用域索引，同步返回、无网络请求。
 *
 * 按**数据宿主**（`ownerNode`）取候选：起止节点的取值写在父容器上，而它们在执行侧根本
 * 不存在（后端类型白名单不含它们），拿它们的 id 只能查到空；容器的上游可见变量，恰好
 * 就是循环数组能引用的范围。
 */
const upstreamOptions = computed(() => {
  const owner = ownerNode.value
  if (!owner || !hasValueGroup.value) return []
  return scopeIndex.value.get(owner.id)?.variables ?? []
})

/** 本节点不参与执行（类型不支持 / 迭代容器为空）时的提示，无错为空串 */
const upstreamError = computed(() => {
  const owner = ownerNode.value
  if (!owner || !hasValueGroup.value) return ''
  return scopeIndex.value.get(owner.id)?.error ?? ''
})

/** 行栅格类：取值组 = 变量名 | 变量值 | 删除；纯声明组 = 变量名 | 变量类型 | 展开 | 删除 */
const gridClassOf = (key) => (isValueGroup(key) ? 'var-grid--value' : 'var-grid--plain')

/** 该分组是否是「循环数组」段（只有一行、名字与类型锁死、只能引用数组） */
const isLoopArrayGroup = (key) =>
  visibleGroups.value.some((group) => group.key === key && group.arrayOnly)

/**
 * 该分组的候选变量。
 * 循环数组只列数组类型的候选：选不出标量，就不会发生「选完才发现类型不匹配」。
 */
const optionsForGroup = (key) =>
  isLoopArrayGroup(key)
    ? upstreamOptions.value.filter((item) => item.type === 'list')
    : upstreamOptions.value

/**
 * 该行的取值是否由 `backendInputs` 里的**专用槽位**提供（如结束节点的 `content` 内容模板）。
 *
 * 这类槽位不是 `inputParameters` 里的普通引用 —— `content` 的取值是一段 `{{results}}` 模板，
 * 「选择上游变量」控件表示不了它，但它的取值确实已经配好，所以只用来让 `inputErrorOf`
 * 不要把它误判成「变量值不可为空」（同时把取值区置为提示文案，而不是让用户以为漏配）。
 */
const isSlotProvided = (item) => {
  const name = String(item?.name ?? '').trim()
  if (!name) return false
  // 专用槽位与取值一样落在数据宿主上（起止节点看的是父容器的 backendInputs）
  const backendInputs = ownerNode.value?.data?.backendInputs
  return Boolean(backendInputs && typeof backendInputs === 'object' && backendInputs[name] != null)
}

/**
 * 输入取值的即时校验：返回该行的错误文案，无错则为空串。
 *
 * - 没有选变量（取值区为空）→ 「变量值不可为空」：节点执行时取不到值，属于必须补上的配置缺口；
 * - 引用了上游已不存在的变量 → 「引用变量不存在」：来源节点被删 / 断线 / 改名。
 *
 * 只依赖当前画布拓扑与草稿，不请求后端，因此增删连线后错误会立即出现或消失。
 * 三种「不报错」的例外各有原因，不要随手去掉：
 * 1. 变量名还没填 —— 刚点「添加」出来的空行还不算一个变量，一出生就报红会误导；
 * 2. 候选集还在算 / 算失败 —— 此时无法断言「没选」或「引用失效」，报了就是假阳性；
 * 3. 取值由 backendInputs 的专用槽位提供（如结束节点的 content 模板）—— 取值本就配好了，
 *    只是「选择上游变量」这个控件表示不了它。
 */
const inputErrorOf = (item) => {
  const binding = item?.binding
  if (!binding) return ''
  if (!String(item?.name ?? '').trim()) return ''
  if (isSlotProvided(item)) return ''
  if (upstreamLoading.value || upstreamError.value) return ''
  if (binding.mode === 'ref' && binding.refKey) {
    const known = upstreamOptions.value.some((entry) => entry.refKey === binding.refKey)
    return known ? '' : '引用变量不存在'
  }
  return '变量值不可为空'
}

/* ===== 描述展开（纯 UI 状态，不进草稿快照，避免把「未保存」误置为脏） ===== */

/** 展开描述的行：`{groupId}-{行号}` */
const expandedDesc = ref([])

const descKeyOf = (groupKey, index) => `${groupKey}-${index}`

const isDescExpanded = (groupKey, index) => expandedDesc.value.includes(descKeyOf(groupKey, index))

const toggleDesc = (groupKey, index) => {
  const key = descKeyOf(groupKey, index)
  const at = expandedDesc.value.indexOf(key)
  if (at >= 0) {
    expandedDesc.value.splice(at, 1)
  } else {
    expandedDesc.value.push(key)
  }
}

/** 空取值：固定值且无内容 */
const emptyBinding = () => ({ mode: 'literal', refKey: null, literal: '' })

/** 后端 inputParameters 项 → 取值草稿 */
const readBinding = (param) => {
  const value = param?.input?.value ?? {}
  if (value.type === 'ref' && value.content && typeof value.content === 'object') {
    const { blockID, name } = value.content
    if (blockID && name) return { mode: 'ref', refKey: `${blockID}.${name}`, literal: '' }
    return emptyBinding()
  }
  if (value.content == null) return emptyBinding()
  return { mode: 'literal', refKey: null, literal: String(value.content) }
}

/**
 * 取值行草稿 = `data[declarationKey]` 声明（保持原有顺序）∪ `backendInputs.inputParameters`。
 * 取并集是为了不丢已有取值：画布自建节点的声明可能为空，而 ref 都在 `backendInputs` 里。
 *
 * @param {Object} current 当前节点
 * @param {string} declarationKey 声明所在的 data 键（开始节点是 outputs，其余带取值的是 inputs）
 */
const buildInputDrafts = (current, declarationKey = 'inputs') => {
  const declarations = Array.isArray(current?.data?.[declarationKey])
    ? current.data[declarationKey].map(cloneVariable)
    : []
  const params = Array.isArray(current?.data?.backendInputs?.inputParameters)
    ? current.data.backendInputs.inputParameters
    : []

  const bindingByName = new Map()
  const requiredByName = new Map()
  for (const param of params) {
    const name = typeof param?.name === 'string' ? param.name.trim() : ''
    if (!name) continue
    bindingByName.set(name, readBinding(param))
    requiredByName.set(name, param?.input?.required === true)
  }

  const rows = declarations.map((item) => {
    const row = { ...item, binding: bindingByName.get(item.name) ?? emptyBinding() }
    // required 在变量声明与取值槽位两处都可能出现，任一为 true 即视为必填（取值校验不依赖它）
    if (row.required !== true && requiredByName.get(item.name) === true) row.required = true
    return row
  })
  const declared = new Set(rows.map((item) => item.name))
  for (const param of params) {
    const name = typeof param?.name === 'string' ? param.name.trim() : ''
    if (!name || declared.has(name)) continue
    rows.push({
      name,
      type: param?.input?.type || 'string',
      description: param?.input?.description ?? '',
      // required 只从槽位回读：画布不编辑它，但执行侧要用，丢一次就再也回不去
      ...(param?.input?.required === true ? { required: true } : {}),
      ...(param?.input?.schema ? { schema: { ...param.input.schema } } : {}),
      binding: readBinding(param),
    })
  }
  return rows
}

/**
 * 取值草稿 → 后端 `InputSlotDef`；返回 null 表示该行没有可写入的取值。
 *
 * 空固定值不写入：保留「未配置取值」的语义，让 `workflow-schema.js` 的取值兜底逻辑继续生效
 * （一旦写入空的 inputParameters，兜底会被整体跳过）。
 */
const buildInputSlot = (declaration, binding) => {
  const type = declaration.type || 'string'
  const rawMeta = { type: RAW_META_TYPE[type] ?? 1 }
  const slot = { type }
  if (type === 'list') slot.schema = { type: declaration.schema?.type || 'string' }
  // 声明侧的元信息随取值一起回写，否则保存一轮就会丢掉
  if (declaration.description) slot.description = declaration.description
  if (declaration.required === true) slot.required = true

  if (binding?.mode === 'ref' && binding.refKey) {
    const index = binding.refKey.indexOf('.')
    if (index <= 0 || index === binding.refKey.length - 1) return null
    slot.value = {
      type: 'ref',
      content: {
        blockID: binding.refKey.slice(0, index),
        name: binding.refKey.slice(index + 1),
        source: 'block-output',
      },
      rawMeta,
    }
    return slot
  }

  const literal = binding?.literal ?? ''
  if (!String(literal).trim()) return null
  slot.value = { type: 'literal', content: literal, rawMeta }
  return slot
}

const initDraft = () => {
  const current = liveNode.value
  // 切节点 / 重新打开时退出编辑态，避免编辑框残留上一个节点的值
  renaming.value = false
  renameValue.value = ''
  // 折叠面板回到默认状态：全部收起
  expandedGroups.value = []
  // 描述展开是纯 UI 状态，切节点时一并收起
  expandedDesc.value = []
  draft.groups = {}
  // 起止节点的参数写在父容器上：草稿的读写对象统一走 owner（见 ownerNode）
  const owner = ownerNode.value
  for (const group of visibleGroups.value) {
    if (group.value) {
      // 取值行额外带取值草稿（引用上游变量 / 固定值），来源是 backendInputs
      const rows = owner ? buildInputDrafts(owner, group.dataKey) : []
      if (group.arrayOnly) {
        // 循环数组固定一行、名字锁死 item：执行侧按名字认数组，缺了就补一行空的等用户选
        const declared = rows.find((row) => row.name === LOOP_ITEM_KEY)
        draft.groups[group.key] = [
          {
            ...(declared ?? { description: '' }),
            name: LOOP_ITEM_KEY,
            type: 'list',
            schema: { type: declared?.schema?.type || 'string' },
            binding: declared?.binding ?? emptyBinding(),
          },
        ]
        continue
      }
      // 中间变量与循环数组共用 inputParameters：剔掉数组那一行，免得同一份数据渲染两遍
      draft.groups[group.key] = group.withoutArrayOnly
        ? rows.filter((row) => row.name !== LOOP_ITEM_KEY)
        : rows
      continue
    }
    const list = owner?.data?.[group.dataKey]
    draft.groups[group.key] = Array.isArray(list) ? list.map(cloneVariable) : []
  }
  // 迭代输出：把容器上已配好的「结果取自」回读进下拉
  aggregateRefKey.value =
    boundarySide.value === 'end' ? buildAggregateKey(owner?.data?.outputs?.[0]?.input) : ''
  // 候选集由本地作用域索引同步给出（见 workflow-scope.js），不请求后端。
  if (!current || !owner) {
    draft.label = ''
    cleanSnapshot.value = serializeDraft()
    return
  }
  draft.label = owner.data?.label ?? ''
  cleanSnapshot.value = serializeDraft()
}

// 打开抽屉 / 切换到另一个节点时，用 store 里的最新数据重建草稿
watch(
  () => [props.visible, props.nodeId],
  ([visible]) => {
    if (visible) initDraft()
  },
  { immediate: true },
)

/* ===== 变量编辑 ===== */

/** 新增变量：description 仍保留在数据结构里（后端 schema 需要），只是抽屉不提供编辑入口 */
const addVariable = (key) => {
  const item = { name: '', type: 'string', description: '' }
  // 取值行的取值草稿：默认固定值，选变量后由 VariablePicker 改为引用
  if (isValueGroup(key)) item.binding = emptyBinding()
  draft.groups[key].push(item)
}

const removeVariable = (key, index) => {
  draft.groups[key].splice(index, 1)
}

/**
 * 切到 list 时补一个元素 schema（可带上类型菜单选定的元素类型），切走时清掉，避免脏数据。
 * 输出行的类型下拉只改类型、不带元素类型，因此 itemType 可缺省。
 */
const handleTypeChange = (item, itemType) => {
  if (item.type !== 'list') {
    delete item.schema
    return
  }
  if (!item.schema) {
    item.schema = { type: itemType || 'string', description: '' }
  } else if (itemType) {
    item.schema.type = itemType
  }
}

/** 类型菜单回写：类型与元素类型一次定下（子菜单里选 Array 元素时走 list 分支） */
const pickType = (item, picked) => {
  item.type = picked.type
  handleTypeChange(item, picked.itemType)
}

/** 「元素类型」直达入口：只改 schema.type，不动外层类型（leafOnly 保证不会选到 Array） */
const pickElementType = (item, picked) => {
  if (item.schema) item.schema.type = picked.type
}

/* ===== 保存 / 删除 ===== */

const handleSave = () => {
  const current = liveNode.value
  const owner = ownerNode.value
  if (!current || !owner) return

  // 起止节点写的是容器的 data；重命名只对普通节点开放，起止节点不能把容器名字改掉
  const isBoundary = isIterationBoundary.value
  const base = owner.data ?? {}
  const label = isBoundary ? '' : draft.label.trim()
  const data = isBoundary ? { ...base } : { ...base, label }
  let dropped = 0
  const slots = []
  /*
   * 按 dataKey 汇总后再写入：迭代起点的「循环数组」与「中间变量」共用 inputs，
   * 逐组直接赋值会让后一组把前一组的声明覆盖掉（item 那一行会凭空消失）。
   */
  const byDataKey = new Map()
  const valueGroupSeen = new Set()

  for (const group of visibleGroups.value) {
    const list = draft.groups[group.key] ?? []
    const entries = []
    for (const item of list) {
      const name = (item.name ?? '').trim()
      // 名称为空的变量在节点上是不可见的，保存时直接丢掉
      if (!name) {
        dropped += 1
        continue
      }
      // description 可在输出变量行展开编辑：这里统一从草稿写回，未编辑时即原值
      const declaration = { ...item, name, description: item.description ?? '' }
      declaration.type = declaration.type || 'string'
      if (declaration.type === 'list') {
        // 元素描述同理，只保留原值
        declaration.schema = {
          type: declaration.schema?.type || 'string',
          description: declaration.schema?.description ?? '',
        }
      } else {
        delete declaration.schema
      }
      const binding = item.binding
      // 取值只进 backendInputs，不写进变量声明
      delete declaration.binding

      if (group.value) {
        if (group.arrayOnly) {
          // 循环数组是执行侧按「名字 item + 类型 list」认出来的，落库一律锁回去
          declaration.name = LOOP_ITEM_KEY
          declaration.type = 'list'
          declaration.schema = { type: declaration.schema?.type || 'string' }
        }
        const slot = buildInputSlot(declaration, binding)
        if (slot) slots.push({ name: declaration.name, input: slot })
      }
      entries.push({ declaration, binding })
    }

    const bucket = byDataKey.get(group.dataKey) ?? {
      existed: Array.isArray(base[group.dataKey]),
      entries: [],
    }
    bucket.entries.push(...entries)
    byDataKey.set(group.dataKey, bucket)
    if (group.value) valueGroupSeen.add(group.key)

    // 草稿保留取值（保存后选择器要继续显示已选变量），落库的声明则不含它
    draft.groups[group.key] = entries.map(({ declaration, binding }) =>
      binding ? { ...declaration, binding } : { ...declaration },
    )
  }

  for (const [dataKey, bucket] of byDataKey) {
    if (bucket.existed || bucket.entries.length) {
      data[dataKey] = bucket.entries.map(({ declaration }) => cloneVariable(declaration))
    }
  }

  // 取值写回 backendInputs.inputParameters；一条都没有时清掉该键，
  // 保留「未配置取值」的语义（否则 workflow-schema.js 的取值兜底会被整体跳过）
  if (valueGroupSeen.size) {
    const rest = { ...base.backendInputs }
    delete rest.inputParameters
    if (slots.length) {
      data.backendInputs = { ...rest, inputParameters: slots }
    } else if (Object.keys(rest).length) {
      data.backendInputs = rest
    } else {
      delete data.backendInputs
    }
  }

  /*
   * 迭代输出：把「结果取自」写回 outputs[0].input。
   * 保留该槽位上既有的 type / schema 等字段（它们由旧数据带回，执行侧 resolveSlot 要用），
   * 只换 value；清空选择即回到「自动」——后端会退化为取循环体唯一的非空输出。
   */
  if (boundarySide.value === 'end' && Array.isArray(data.outputs) && data.outputs.length) {
    const [first, ...others] = data.outputs
    if (aggregateRefKey.value) {
      const index = aggregateRefKey.value.indexOf('.')
      const blockID = index > 0 ? aggregateRefKey.value.slice(0, index) : ''
      const name = index > 0 ? aggregateRefKey.value.slice(index + 1) : ''
      if (blockID && name) {
        first.input = {
          ...first.input,
          value: { type: 'ref', content: { blockID, name, source: 'block-output' } },
        }
      }
    } else if (first.input) {
      const kept = {}
      for (const [key, value] of Object.entries(first.input)) {
        if (key !== 'value') kept[key] = value
      }
      if (Object.keys(kept).length) first.input = kept
      else delete first.input
    }
    data.outputs = [first, ...others]
  }

  if (!isBoundary) draft.label = label
  updateNode(owner.id, { data })
  cleanSnapshot.value = serializeDraft()
  ElMessage.success(
    dropped
      ? `已保存（${dropped} 个未命名变量被忽略）`
      : `已保存「${label || base.label || owner.id}」`,
  )
}

const handleRemove = async () => {
  const current = liveNode.value
  if (!current) return
  try {
    await ElMessageBox.confirm(`删除后无法恢复，确定删除「${current.data?.label || current.id}」吗？`, '删除节点', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  removeNodes([current.id])
  emit('update:visible', false)
  ElMessage.success('节点已删除')
}

/* ===== 交互 ===== */

const handleVisibleChange = (value) => {
  emit('update:visible', value)
}

/** 点内部节点：把抽屉切到该子节点上 */
const selectNode = (nodeId) => {
  emit('select', nodeId)
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="560px"
    append-to-body
    class="node-drawer"
    :close-on-press-escape="!renaming"
    @update:model-value="handleVisibleChange"
  >
    <!-- 抽屉头部兼节点信息卡：图标 + 名称 + 作用说明，省掉一个独立区块 -->
    <template #header>
      <div class="node-drawer__header">
        <span class="node-drawer__icon">
          <el-icon><component :is="nodeIcon" /></el-icon>
        </span>
        <div class="node-drawer__heading">
          <!-- 重命名：双击标题就地编辑，回车 / 失焦提交，Esc 放弃 -->
          <el-input
            v-if="renaming"
            ref="renameInput"
            v-model="renameValue"
            class="node-drawer__rename"
            size="small"
            maxlength="40"
            @blur="commitRename"
            @keydown.enter.stop="commitRename"
            @keydown.esc.stop="cancelRename"
          />
          <h3
            v-else
            class="node-drawer__title"
            :title="`${nodeLabel}（双击重命名）`"
            @dblclick="startRename"
          >
            {{ nodeLabel }}
          </h3>
          <p class="node-drawer__desc" :title="nodeDescription">{{ nodeDescription }}</p>
        </div>
        <span v-if="dirty" class="node-drawer__dirty">未保存</span>
      </div>
    </template>

    <div v-if="!node" class="node-drawer__empty">节点已不存在</div>

    <div v-else class="node-drawer__body">
      <!--
        变量分组：折叠面板，默认全部收起。
        展示哪几组由 groupPlanOf 决定 —— 开始 / 结束节点只展示一组（开始 = 输入变量、
        结束 = 输出变量），且「展示名按用户视角、数据落点按功能用途」：两者都写在 data.outputs，
        与画布卡片 InputNode / OutputNode 的口径一致。
        每组是否带「取值」也由 groupPlanOf 决定：普通节点的输入组、结束节点的输出组都要选上游变量
        （值区可直接填固定值，点右侧按钮从上游变量里挑，Coze 风格的引用）；开始节点的输入组
        声明的是工作流输入、没有上游，故只做纯声明。
      -->
      <el-collapse v-if="visibleGroups.length" v-model="expandedGroups" class="var-collapse">
        <el-collapse-item style="padding: 12px;border-bottom: var(--tb-border-width) solid var(--tb-border);" v-for="group in visibleGroups" :key="group.key" :name="group.key">
          <template #title>
            <span class="var-group__head">
              <span class="var-group__name">
                {{ group.label }}
                <span class="panel__count">{{ draft.groups[group.key].length }}</span>
              </span>
              <!--
                添加：软底圆角方块 + 主色加号（对齐参考图）。
                不再带「添加」文字：这是高频入口，文字会把标题行右半边占满。
              -->
              <button
                v-if="!group.arrayOnly"
                type="button"
                class="var-group__add"
                :aria-label="`添加${group.label}`"
                :title="`添加${group.label}`"
                @click.stop="addVariable(group.key)"
              >
                <el-icon><Plus /></el-icon>
              </button>
            </span>
          </template>
          <!-- 分组说明：把「展示名 ↔ 变量实际用途」的映射写在标题下（开始 / 结束节点才有） -->
          <p v-if="group.hint" class="var-group__hint">{{ group.hint }}</p>

          <!--
            迭代输出：每轮的结果取自哪个内部节点的输出。
            候选不走接口（起止节点在后端图上不存在，问不到），由画布内部子节点自己声明的输出推导。
          -->
          <div v-if="group.aggregate" class="var-row__sub">
            <span class="var-row__sub-label">结果取自</span>
            <el-select
              v-model="aggregateRefKey"
              class="var-row__sub-control"
              clearable
              placeholder="自动：取循环体唯一的输出"
            >
              <el-option
                v-for="option in innerOutputOptions"
                :key="option.refKey"
                :label="option.label"
                :value="option.refKey"
              />
            </el-select>
          </div>

          <p v-if="!draft.groups[group.key].length" class="panel__empty">
            暂无{{ group.label }}，点击右上角「＋」新增一项。
          </p>
          <!--
            列表式布局：表头 + 数据行共用同一套栅格（.var-grid--*），保证列对齐。
            取值组 = 变量名 | 变量值 | 删除；纯声明组（输出组与开始 / 结束节点的分组）=
            变量名 | 变量类型 | 展开 | 删除。
          -->
          <div v-else class="var-list">
            <div class="var-head" :class="gridClassOf(group.key)">
              <span>变量名</span>
              <span>{{ isValueGroup(group.key) ? '变量值' : '变量类型' }}</span>
              <span v-if="!isValueGroup(group.key)"></span>
              <span></span>
            </div>

            <div
              v-for="(item, index) in draft.groups[group.key]"
              :key="`${group.key}-${index}`"
              class="var-row"
            >
              <div class="var-row__main" :class="gridClassOf(group.key)">
                <!-- 循环数组那一行名字锁死：执行侧按名字 item 认出迭代来源，改名就等于没配 -->
                <span
                  v-if="group.arrayOnly"
                  class="var-row__locked"
                  title="迭代来源固定用 item 这个名字"
                >
                  {{ item.name }}
                </span>
                <el-input
                  v-else
                  v-model="item.name"
                  class="var-row__name"
                  placeholder="变量名，如 USER_INPUT"
                />

                <!-- 输入组：变量值 = 类型菜单 + 取值级联选择器（类型仍在 item.type / item.schema 上） -->
                <template v-if="isValueGroup(group.key)">
                  <!-- 取值与它的校验文案同属第 2 列，文案才好在红框正下方对齐 -->
                  <div class="var-row__value-cell">
                    <div class="var-row__value">
                      <!-- 类型菜单：一级选完整类型名，Array 带元素类型子菜单 -->
                      <!-- 循环数组那一行类型锁死 list：只读回显，元素类型在下方子行里改 -->
                      <span
                        v-if="group.arrayOnly"
                        class="var-row__type var-row__locked-type"
                        title="迭代来源必须是数组"
                      >
                        {{ shortTypeCodeOf(item) }}
                      </span>
                      <VariableTypeSelect
                        v-else
                        class="var-row__type"
                        :type="item.type"
                        :item-type="item.schema?.type"
                        @pick="pickType(item, $event)"
                      />
                      <VariablePicker
                        v-if="item.binding"
                        v-model="item.binding"
                        class="var-row__picker"
                        :options="optionsForGroup(group.key)"
                        :loading="upstreamLoading"
                        :error="Boolean(inputErrorOf(item))"
                        :placeholder="isSlotProvided(item) ? '已有内置取值' : '选择变量'"
                      />
                    </div>

                    <!-- 校验文案：缩进到取值字段（红框）左缘的正下方 -->
                    <p v-if="inputErrorOf(item)" class="var-row__error">{{ inputErrorOf(item) }}</p>
                  </div>
                </template>

                <!-- 输出组 / 开始节点的输入组：变量类型菜单 + 描述展开 -->
                <template v-else>
                  <VariableTypeSelect
                    class="var-row__type"
                    bordered
                    :type="item.type"
                    :item-type="item.schema?.type"
                    @pick="pickType(item, $event)"
                  />
                  <el-button
                    class="var-row__expand"
                    text
                    :class="{ 'is-active': isDescExpanded(group.key, index) }"
                    :icon="FullScreen"
                    :aria-label="isDescExpanded(group.key, index) ? '收起描述' : '展开描述'"
                    :title="isDescExpanded(group.key, index) ? '收起描述' : '编辑描述'"
                    @click="toggleDesc(group.key, index)"
                  />
                </template>

                <!-- 循环数组那一行不能删：删了迭代就没有来源，需要清空时直接清掉取值 -->
                <el-button
                  v-if="!group.arrayOnly"
                  class="var-row__remove"
                  text
                  :icon="Delete"
                  aria-label="删除该变量"
                  title="删除该变量"
                  @click="removeVariable(group.key, index)"
                />
              </div>

              <!-- 列表类型的元素类型：也能在类型菜单的 Array 子级里改，这里留一个直达入口 -->
              <div v-if="item.type === 'list' && item.schema" class="var-row__sub">
                <span class="var-row__sub-label">元素类型</span>
                <VariableTypeSelect
                  class="var-row__sub-control"
                  bordered
                  leaf-only
                  :type="item.schema.type"
                  @pick="pickElementType(item, $event)"
                />
              </div>

              <!-- 描述：输出组（与开始节点的输入组）展开后编辑，保存时写回 data -->
              <div v-if="isDescExpanded(group.key, index)" class="var-row__sub var-row__sub--stack">
                <span class="var-row__sub-label">描述</span>
                <el-input
                  v-model="item.description"
                  class="var-row__sub-control"
                  maxlength="200"
                  placeholder="帮助大模型准确了解参数的作用"
                />
              </div>
            </div>
          </div>

          <p v-if="isValueGroup(group.key) && upstreamError" class="var-row__hint">
            {{ upstreamError }}
          </p>
        </el-collapse-item>
      </el-collapse>

      <!--
        迭代容器的参数不在自己身上：三段参数分别落在它的起止节点上 ——
        后端图里只有容器，起止节点是画布专有节点，参数写在自己身上就是死数据（不落库也不执行）。
      -->
      <p v-if="isIteration" class="var-group__hint">
        迭代的「循环数组 / 中间变量」在内部的起点节点上配置，输出在终点节点上配置；点下面的起止节点即可打开。
      </p>

      <!-- 迭代节点：内部节点 -->
      <section v-if="isIteration" class="panel">
        <div class="panel__head">
          <h4 class="panel__title">
            内部节点
            <span class="panel__count">{{ childNodes.length }}</span>
          </h4>
        </div>

        <p v-if="!childNodes.length" class="panel__empty">还没有节点被拖进这个迭代节点。</p>

        <button
          v-for="child in childNodes"
          :key="child.id"
          type="button"
          class="child-row"
          @click="selectNode(child.id)"
        >
          <span class="child-row__name">{{ child.data?.label || child.id }}</span>
          <span class="child-row__type">{{ NODE_TYPE_LABELS[child.type] ?? child.type }}</span>
        </button>
      </section>


    </div>

    <template #footer>
      <div class="node-drawer__footer">
        <el-button
          v-if="node && node.deletable !== false"
          type="danger"
          plain
          :icon="Delete"
          @click="handleRemove"
        >
          删除节点
        </el-button>
        <span class="node-drawer__spacer"></span>
        <el-button :icon="RefreshLeft" :disabled="!dirty" @click="initDraft">重置</el-button>
        <el-button type="primary" :icon="Check" :disabled="!dirty" @click="handleSave">保存</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
/*
 * 注意：抽屉外壳（.el-drawer / __header / __body / __footer）的样式**不能写在这个 scoped 块里**。
 * 抽屉是 append-to-body 的 Teleport，这几个元素由 Element 渲染、不带本组件的 scope id，
 * 所以 `:deep(.el-drawer__body)` 与 `.el-drawer__body` 都匹配不到（是死规则）。
 * 它们的样式统一放在文件末尾的非 scoped 块里，用抽屉自己的 class `.node-drawer` 做作用域。
 */

.node-drawer__header {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.node-drawer__icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-primary);
  color: #fff;
  font-size: 18px;
}

.node-drawer__heading {
  flex: 1;
  min-width: 0;
}

.node-drawer__title {
  margin: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--tb-text-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
  /* 双击进入编辑态，禁掉选中以免反光标 */
  cursor: text;
  user-select: none;
}

.node-drawer__rename {
  width: 100%;
  max-width: 280px;
}

.node-drawer__desc {
  margin: 2px 0 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: var(--tb-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-drawer__dirty {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--tb-warning-soft);
  font-size: 12px;
  line-height: 18px;
  color: var(--tb-warning);
}

/* ===== 抽屉主体 ===== */
.node-drawer__body {
  display: flex;
  flex-direction: column;
  /* gap: 12px; */
  /*
   * 横向留白的唯一来源。
   * 抽屉外壳（.el-drawer__body）的 padding 已清零，所以抽屉内所有「要通到两端」的元素
   * 都靠它算负边距 —— 只有这一个值，改它全抽屉跟着走，不会再出现
   * 「容器没有内边距、标题行还挂着 -16px，结果顶到抽屉外面」这种脱节。
   */
  /* --var-gutter: 16px;
  padding: 0 var(--var-gutter) 16px; */
}

.node-drawer__empty {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

/*
 * 分组容器不做卡片：不画边框、不铺背景、不加内边距，
 * 分组之间靠 .node-drawer__body 的 gap 与标题本身区分，保持无边界感。
 */
.panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--tb-text-primary);
}

.panel__head .panel__title {
  margin: 0;
}

.panel__count {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--tb-bg-sunken);
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: var(--tb-text-secondary);
}

.panel__empty {
  margin: 0;
  padding: 2px 0;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/*
 * 分组说明：把「展示名 ↔ 变量实际用途」的映射写在标题下。
 * 只在开始 / 结束节点这类「展示名与数据落点刻意错位」的分组上出现，
 * 与空状态同一字号与色阶，属于提示信息、不抢列表的视线。
 */
.var-group__hint {
  margin: 0 0 8px;
  padding: 2px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

/* ===== 变量分组：折叠面板 ===== */
/*
 * 沿用「无边框」风格：抹掉 el-collapse 自带的分隔线与内容区边距，
 * 分组之间靠标题行的留白与 hover 反馈区分。
 */
:deep(.el-collapse) {
  border: none;
}

:deep(.el-collapse-item__header) {
  display: flex;
  height: auto;
  /*
   * 底边线与 hover 底色要通到抽屉两端（对齐参考图）：用 --var-gutter 的负值吃掉
   * .node-drawer__body 的同名内边距，再用等量内边距把文字放回原位。
   * 两者必须取自同一个变量 —— 各写各的就会出现「容器改了留白、标题行还按旧值出血」
   * 从而顶到抽屉外面去。
   *
   * 这里**不给回退值**是有意的：变量一旦消失，calc 整条声明失效，margin/padding
   * 退回各自初始值（0），最坏也只是贴边，绝不会再顶出抽屉。
   */
  margin: 0 calc(-1 * var(--var-gutter));
  padding: 10px var(--var-gutter);
  border: none;
  /* 分组标题下的分隔线：收起时浅灰实线，悬停转主色，展开后换成主色渐变 */

  background: transparent;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--tb-text-primary);
}

/*
 * 整行都是点击区（role=button），悬停 / 键盘聚焦时给与 .child-row 相同的反馈。
 * 只改 background-color 不用 background 简写：展开态的分隔线是 background-image 画的渐变，
 * 简写会把它重置成 none，悬停展开中的分组时线会整条消失。
 */
:deep(.el-collapse-item__header:hover),
:deep(.el-collapse-item__header:focus-visible) {
  outline: none;
  border-bottom-color: var(--tb-primary);
  color: var(--tb-primary-text);
}

/*
 * 展开态用渐变线：主色渐隐到透明。
 * 渐变画在背景上而不是 border-image —— border-image 会连 1px 的粗细一起拉伸变形。
 */
:deep(.el-collapse-item__header.is-active) {
  border-bottom-color: transparent;
}

:deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

:deep(.el-collapse-item__content) {
  padding: 4px 0 8px;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
}

/*
 * 折叠箭头放左侧：默认它在标题之后、且 margin-left: auto 会顶到最右，
 * 这里用 order 提到最前，并把自动外边距换回右侧固定间距。
 */
:deep(.el-collapse-item__arrow) {
  order: -1;
  margin: 0 8px 0 0;
  color: var(--tb-text-secondary);
}

.var-group__head {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.var-group__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

/*
 * 「添加」：折叠标题右侧的图标按钮。
 * 参考图里它是软底圆角方块（主色淡底 + 主色图标），不用文字按钮 ——
 * 方块点击面积够大且有底色，比一行「+ 添加」更省横向空间。
 * 点击已 stop，不会连带折叠。
 */
.var-group__add {
  display: inline-flex;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: var(--tb-primary-soft);
  font-size: 14px;
  color: var(--tb-primary);
  cursor: pointer;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.var-group__add:hover {
  background: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.var-group__add:focus-visible {
  outline: 2px solid var(--tb-ring);
  outline-offset: 1px;
}

/* ===== 变量列表：表头与数据行共用同一套栅格，保证列对齐 ===== */
.var-list {
  display: flex;
  flex-direction: column;
}

/*
 * 栅格模板按分组切换：
 * - 输入组：变量名 | 变量值（类型下拉 + 取值） | 删除
 *   （没有独立的「变量类型」列：类型就在取值区的类型下拉里，再回显一列是重复信息）
 * - 输出组（与开始节点的输入组）：变量名 | 变量类型 | 描述展开 | 删除
 */
.var-grid--value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr) 28px;
  /* 用 start 而不是 center：第 2 列比别的高（多一行校验文案），居中会把控件整体压下去，
     跟变量名输入框错开一整行；对齐各自的第一行，控件就始终齐平（三列首行都是 32px）。 */
  align-items: start;
  gap: 8px;
}

.var-grid--plain {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) 28px 28px;
  align-items: center;
  gap: 8px;
}

/* 表头：只做列名提示，轻到不抢视线 */
.var-head {
  /* 与 .var-row 同样做负边距抵消，否则列名会比下面的字段右移 8px */
  margin: 0 -8px;
  padding: 2px 8px 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.var-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 左右负外边距抵消内边距：内容位置不变，但 hover 底色能铺满整行宽度。
     出血量必须小于等于 --var-gutter，否则会像标题行那样顶出抽屉。 */
  margin: 0 -8px;
  padding: 10px 8px;
  /* 行底用虚线，与分组标题的实线区分开 */
  border-bottom: var(--tb-border-width) dashed var(--tb-border);
  border-radius: var(--tb-radius-md);
  transition:
    border-color var(--tb-dur-2) var(--tb-ease-out),
    background-color var(--tb-dur-2) var(--tb-ease-out);
}

/* 悬停整行或行内表单获得焦点时浮出一层浅底；底线保持灰色，避免与主色描边抢视线 */
/* .var-row:hover,
.var-row:focus-within {
  background: var(--tb-bg-raised);
} */

/* 末行不画线，免得与下一个分组标题的底线叠成双线 */
.var-row:last-child {
  border-bottom-color: transparent;
}

/*
 * 「变量值」整格：分段控件 + 它的校验文案。
 * 类型段宽度抽成变量，是为了让下面的红字能正好缩进到取值字段左缘（与红框对齐），
 * 窄屏断点只需要改这一个值，两处不会走偏。
 */
.var-row__value-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  /* 文案属于这个字段本身，间距比行内其他元素(8px)更紧 */
  gap: 4px;
  --var-type-width: 74px;
}

/* 变量值：类型段 + 取值段合成一个分段控件（对齐参考图），外层只画一道描边 */
.var-row__value {
  display: flex;
  min-width: 0;
  /*
   * 含边框正好 32px，与变量名输入框（Element 默认 32px）齐平。
   * 必须 border-box：内部两段是 30px，content-box 下再加 1px×2 边框会变成 34px，
   * 就会比同行其他控件高出 2px。对齐口径是「外边距盒 32px」。
   */
  box-sizing: border-box;
  min-height: 32px;
  align-items: stretch;
  overflow: hidden;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  transition:
    border-color var(--tb-dur-2) var(--tb-ease-out),
    box-shadow var(--tb-dur-2) var(--tb-ease-out);
}

/* 悬停只做「可交互」的轻提示；主色留给聚焦，静态浏览时不会到处是彩色描边 */
.var-row__value:hover {
  border-color: var(--tb-border-strong);
}

.var-row__value:focus-within {
  border-color: var(--tb-primary);
  box-shadow: 0 0 0 2px color-mix(in oklab, var(--tb-primary) 16%, transparent);
}

/*
 * 校验失败的红框不在这里：它落在「变量值」那一格自己身上（见 VariablePicker 的 is-error），
 * 因为错误说的是取值，左侧类型段不该被一起染红。分段控件本身只保持正常的悬停 / 聚焦反馈。
 */

/* 类型段：贴左边，右侧一条竖线把两段分开（触发器自身的描边在 VariableTypeSelect 里已剥离） */
.var-row__value .var-row__type {
  flex: 0 0 var(--var-type-width);
  border-right: var(--tb-border-width) solid var(--tb-border);
}

.var-row__picker {
  flex: 1;
  min-width: 0;
}

/* 取值校验文案：缩进到取值字段（红框）左缘，正落在它下面 */
.var-row__error {
  margin: 0;
  padding-left: var(--var-type-width, 74px);
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-danger);
}

/* 描述展开按钮：展开态用主色高亮 */
.var-row__expand {
  padding: 0;
  color: var(--tb-text-tertiary);
}

.var-row__expand.is-active,
.var-row__expand:hover {
  color: var(--tb-primary);
}

.var-row__remove {
  /* 与首行控件同高：整行按 start 对齐后，靠这个高度让图标落在控件的中线上 */
  height: 32px;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: var(--tb-text-tertiary);
}

.var-row__remove:hover {
  color: var(--tb-danger, #f53f3f);
}

/* 次级行：元素类型是一行内联，描述是一行「标签 + 控件」堆叠 */
.var-row__sub {
  display: flex;
  align-items: center;
  gap: 8px;
}

.var-row__sub--stack {
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}

/*
 * 锁定单元格（循环数组那一行）：名字与类型不可编辑。
 * 用与输入框同高的只读块顶替，视觉上「看得出是固定值」，而不是一个坏掉的输入框。
 */
.var-row__locked {
  display: flex;
  height: 32px;
  min-width: 0;
  align-items: center;
  padding: 0 8px;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
  font-size: 12px;
  color: var(--tb-text-primary);
}

/* 类型段里的锁定回显：宽高与类型菜单一致（由 .var-row__value .var-row__type 提供），只是不可点 */
.var-row__locked-type {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  font-size: 12px;
  color: var(--tb-text-secondary);
  cursor: default;
}

.var-row__sub-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

.var-row__sub-control {
  flex: 1;
  min-width: 0;
}

/* 上游变量获取失败的提示（例如节点当前不参与执行） */
.var-row__hint {
  margin: 0;
  padding: 2px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-danger, #f53f3f);
}

/* ===== 内部节点 ===== */
.child-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 靠父级 flex 的 stretch 撑满宽度，配合负外边距让底边线通到两端 */
  margin: 0 -8px;
  padding: 8px;
  border: none;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: transparent;
  cursor: pointer;
  transition:
    border-color var(--tb-dur-2) var(--tb-ease-out),
    background-color var(--tb-dur-2) var(--tb-ease-out);
}

.child-row:hover,
.child-row:focus-visible {
  outline: none;
  border-bottom-color: var(--tb-primary);
  background: var(--tb-bg-raised);
}

.child-row:last-child {
  border-bottom-color: transparent;
}

.child-row__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--tb-text-primary);
}

.child-row__type {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 12px;
  color: var(--tb-text-secondary);
}

/* ===== 抽屉底部 ===== */
/* 操作区的分隔线画在自身顶边：贴抽屉底边的话会被视作边界而看不出来 */
.node-drawer__footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.node-drawer__spacer {
  flex: 1;
}

/*
 * 响应式一致性：
 * 1. 线条粗细统一走 --tb-border-width，缩放或高 DPR 下都不会变虚、变粗；
 * 2. 窄视口收紧行内边距并压缩类型列，避免名称列被下拉挤到不可用；
 * 3. 尊重系统「减少动态效果」，直接关掉过渡。
 */
@media (max-width: 480px) {
  /* 窄视口下压缩取值列，保证变量名列可用 */
  .var-grid--value {
    grid-template-columns: minmax(0, 1fr) minmax(0, 2fr) 22px;
    gap: 6px;
  }

  .var-grid--plain {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr) 22px 22px;
    gap: 6px;
  }

  /* 类型段窄屏再收 6px（校验文案的缩进跟着一起收）；再小就会把「[bool] + 箭头」挤到溢出 */
  .var-row__value-cell {
    --var-type-width: 68px;
  }

  .var-row {
    gap: 6px;
    margin: 0 -6px;
    padding: 8px 6px;
  }

  /* 窄屏行内边距收到 6px，表头跟着收，与字段保持同一条对齐线 */
  .var-head {
    margin: 0 -6px;
    padding: 2px 6px 6px;
  }

  .child-row {
    margin: 0 -6px;
    padding: 8px 6px;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.el-collapse-item__header),
  .var-group__add,
  .var-row,
  .child-row {
    transition: none;
  }
}
</style>

<!--
  抽屉外壳的内边距（.el-drawer__header / __body / __footer）。

  必须写在非 scoped 块里：抽屉是 append-to-body 的 Teleport，这几个元素由 Element 渲染、
  不带本组件的 scope id —— scoped 块里无论 `:deep(.el-drawer__body)` 还是 `.el-drawer__body`
  都匹配不到（之前那两处就是这么失效的，看着像「写了没效果」）。
  作用域改用抽屉自己的 class `.node-drawer`：Element 用 mergeProps 把 $attrs 并到 `.el-drawer` 上，
  所以它正是这三个元素的祖先。
-->
<style>
/* Element 默认是 20px（--el-drawer-padding-primary）；三区统一 16px 内缩 */
.node-drawer .el-drawer__header {
  /* header 默认还有 32px 的 margin-bottom：信息卡占两行高，留太多会把下面的表单整体推下去 */
  margin-bottom: 12px;
  padding: 16px 16px 0;
}

/* 主体清零：四周留白全部交给 .node-drawer__body 自己控制 */
.node-drawer .el-drawer__body {
  padding: 0;
}

/* 底部留白只补左右与下方；分隔线画在 .node-drawer__footer 的顶边 */
.node-drawer .el-drawer__footer {
  padding: 0 16px 16px;
}
</style>
