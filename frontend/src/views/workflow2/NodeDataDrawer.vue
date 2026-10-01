<script setup>
/**
 * 节点数据抽屉（workflow2）。
 *
 * 打开时机由画布决定：WorkflowView 收到节点点击（含卡片右上角「···」）后把 `node-id` 传进来，
 * 点画布空白处关闭。本组件不自己监听画布事件。
 *
 * 双向同步：抽屉里的每一次编辑（输入、下拉切换、增删变量行）都立即写回画布上的节点数据，
 * 卡片当场跟着变 —— 没有「保存」这一步，也就没有「未保存」这个状态。
 *
 * 覆盖 workflow2 现有的五类节点：1 开始 / 2 结束 / 3 大模型 / 4 迭代 / 5 人工确认。变量按「分组」呈现（折叠面板，
 * 与旧版抽屉同一套观感）：输入组是「变量名 | 变量值 | 删除」，取值段由类型段 + 取值选择器
 * 拼成分段控件；输出组是「变量名 | 变量类型 | 描述 | 删除」，描述折叠在行内展开。
 *
 * 数据落点（就是 `data` 本身）：
 *   data.inputs  = [{ name, type, schema?, description?, input? }]
 *   data.inputs[].input = { type, blockID, name }   取值＝引用上游变量（`blockID.name` 即引用键）
 *   data.inputs[].input = { type, content }         取值＝字面量常量
 *   data.outputs = [{ name, type, schema?, description? }]
 * `input` 缺失即「未指定取值」，节点卡片上的对应变量会打告警色（见 LlmNode）。
 */
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CloseBold, Delete, FullScreen, Plus, MagicStick, RefreshRight, SwitchButton, User, VideoPlay } from '@element-plus/icons-vue'
import { useVueFlow } from '@vue-flow/core'

defineOptions({ name: 'NodeDataDrawer' })

const props = defineProps({
  /** 受控显隐，配 `v-model:visible` */
  visible: { type: Boolean, default: false },
  /** 当前编辑的节点 id：只存 id，节点对象每次实时从画布取，避免拿到过期引用 */
  nodeId: { type: String, default: null },
})

const emit = defineEmits(['update:visible'])

const { getNodes, getEdges, updateNode, removeNodes } = useVueFlow()

/* ===== 节点类型档案 ===== */

/** 三类节点的文案与可编辑区块；`flags` 决定抽屉里出现哪些分组 */
const NODE_META = {
  '1': {
    name: '开始',
    icon: VideoPlay,
    desc: '工作流入口：定义初始输出变量，供下游节点引用',
    flags: { prompt: false, inputs: false, outputs: true },
  },
  '2': {
    name: '结束',
    icon: SwitchButton,
    desc: '工作流出口：把上游变量绑定成最终结果',
    flags: { prompt: false, inputs: true, outputs: false },
  },
  '3': {
    name: '大模型',
    icon: MagicStick,
    desc: '按提示词调用大模型：读取输入变量，产出输出变量',
    flags: { prompt: true, inputs: true, outputs: true },
  },
  '4': {
    name: '迭代',
    icon: RefreshRight,
    desc: '迭代容器：对列表输入逐项执行容器内的子步骤',
    flags: { prompt: false, inputs: true, outputs: true },
  },
  '5': {
    name: '人工确认',
    icon: User,
    desc: '人工确认：执行到此暂停，确认后从断点继续',
    flags: { prompt: false, inputs: true, outputs: true },
  },
}

const UNKNOWN_META = {
  name: '节点',
  icon: MagicStick,
  desc: '该节点暂不支持在抽屉里编辑',
  flags: { prompt: false, inputs: false, outputs: false },
}

const VARIABLE_SYNTAX = '{{变量名}}'

/* ===== 变量类型 ===== */

/** 完整类型名：类型下拉里的次级文案 */
const TYPE_LABEL = {
  string: 'String',
  integer: 'Integer',
  number: 'Number',
  boolean: 'Boolean',
  object: 'Object',
  file: 'File',
}

/** 类型短码：取值段里那个紧凑的段（与节点卡片胶囊同一套读法，方括号只标记数组这一维） */
const TYPE_CODE = {
  string: 'str',
  integer: 'int',
  number: 'num',
  boolean: 'bool',
  object: 'obj',
  file: 'file',
}

/**
 * 类型下拉的候选：标量 + 数组。
 * 数组在数据里落成 `{ type:'list', schema:{ type } }`，下拉值写成 `list:元素类型` 一个字符串。
 */
const TYPE_OPTIONS = [
  ...Object.keys(TYPE_LABEL).map((type) => ({ value: type, code: TYPE_CODE[type], label: TYPE_LABEL[type] })),
  ...Object.keys(TYPE_LABEL).map((type) => ({
    value: `list:${type}`,
    code: `[${TYPE_CODE[type]}]`,
    label: `Array<${TYPE_LABEL[type]}>`,
  })),
]

/** 类型下拉值：标量是类型名本身，数组是 `list:元素类型` */
const typeKeyOf = (declaration) => {
  const type = declaration?.type ?? 'string'
  return type === 'list' ? `list:${declaration?.schema?.type ?? 'string'}` : type
}

/** 类型下拉值 → 变量声明片段（会覆盖旧的 type / schema） */
const typeDeclOf = (typeKey) => {
  if (typeof typeKey === 'string' && typeKey.startsWith('list:')) {
    return { type: 'list', schema: { type: typeKey.slice(5) } }
  }
  return { type: typeKey || 'string' }
}

/** 完整类型名：取值选择器的候选项里用它做次级说明 */
const typeLabelOf = (typeKey) =>
  typeof typeKey === 'string' && typeKey.startsWith('list:')
    ? `Array<${TYPE_LABEL[typeKey.slice(5)] ?? 'String'}>`
    : TYPE_LABEL[typeKey] ?? 'String'

/** 变量声明数组：字符串写法补成对象；名称为空的声明在画布上不可见，直接过滤 */
const declarationList = (variables) =>
  (Array.isArray(variables) ? variables : [])
    .map((item) => (typeof item === 'string' ? { name: item } : item))
    .filter((item) => item?.name)

/** 取值槽 → 引用键 `blockID.name`（空串表示没引用） */
const refKeyOfSlot = (slot) => (slot?.blockID && slot?.name ? `${slot.blockID}.${slot.name}` : '')

/** 引用键 → 取值槽坐标；`blockID` 本身不含点号，按第一个点切开即可 */
const slotOfRefKey = (refKey) => {
  const index = typeof refKey === 'string' ? refKey.indexOf('.') : -1
  if (index <= 0) return null
  return { blockID: refKey.slice(0, index), name: refKey.slice(index + 1) }
}

/** 引用键里的来源节点 id */
const refBlockIdOf = (refKey) => {
  const index = typeof refKey === 'string' ? refKey.indexOf('.') : -1
  return index > 0 ? refKey.slice(0, index) : ''
}

/** 把类型写回声明：数组只带 schema.type，标量则清掉残留的 schema */
const applyType = (declaration, typeKey) => {
  const { type, schema } = typeDeclOf(typeKey)
  declaration.type = type
  if (schema) declaration.schema = { ...declaration.schema, ...schema }
  else delete declaration.schema
}

/* ===== 当前节点 ===== */

const node = computed(() => getNodes.value.find((item) => item?.id === props.nodeId) ?? null)

const meta = computed(() => NODE_META[node.value?.type] ?? UNKNOWN_META)

/**
 * 上游可达节点（沿入边反向 BFS，由近到远）。
 *
 * 只认真正的上游：引用必须来自能走到的前驱，否则运行时取到空值。
 * 图里没有环（连线时已拦自连与重复边），因此不需要额外的去环处理。
 */
const upstreamNodes = computed(() => {
  const start = props.nodeId
  if (!start) return []
  const incoming = new Map()
  for (const edge of getEdges.value) {
    if (!edge?.source || !edge?.target) continue
    if (!incoming.has(edge.target)) incoming.set(edge.target, [])
    incoming.get(edge.target).push(edge.source)
  }
  const seen = new Set()
  const ordered = []
  const queue = [start]
  while (queue.length) {
    for (const source of incoming.get(queue.shift()) ?? []) {
      if (seen.has(source) || source === start) continue
      seen.add(source)
      const found = getNodes.value.find((item) => item?.id === source)
      if (found) ordered.push(found)
      queue.push(source)
    }
  }
  return ordered
})

/** 取值选择器的候选树：一级来源节点，二级该节点声明的输出变量，叶子值就是引用键 */
const upstreamTree = computed(() =>
  upstreamNodes.value
    .map((item) => ({
      value: item.id,
      label: item.data?.label || item.id,
      children: declarationList(item.data?.outputs).map((declaration) => ({
        value: `${item.id}.${declaration.name}`,
        label: declaration.name,
        hint: typeLabelOf(typeKeyOf(declaration)),
      })),
    }))
    .filter((group) => group.children.length),
)

/** 叶子引用键是否还在候选树里：不在就是「来源节点被删 / 断线」 */
const refFoundInTree = (refKey) =>
  Boolean(refKey) && upstreamTree.value.some((group) => group.children.some((child) => child.value === refKey))

/** 级联选择器的选中路径：两级都命中才给完整路径，失效引用退化成只定位来源节点 */
const cascaderPathOf = (row) => {
  if (!row.refKey) return []
  const group = upstreamTree.value.find((item) => item.value === refBlockIdOf(row.refKey))
  if (!group) return []
  return refFoundInTree(row.refKey) ? [group.value, row.refKey] : [group.value]
}

/** 取值校验文案：占位为空串表示没有错，调用方直接 v-if */
const refErrorOf = (row) => {
  if (!row.refKey) return ''
  if (refFoundInTree(row.refKey)) return ''
  return '引用的变量不存在：来源节点可能已被删除或断线'
}

/* ===== 变量行 ===== */

/**
 * 抽屉里的编辑态行。
 *
 * 为什么留一层「行」而不是直接 v-model 到 `data` 上：下拉里的类型是 `list:object` 这种
 * 单值写法，落库却是 `{ type, schema:{ type } }` 两段式；取值是「引用键 / 常量」二选一，
 * 落库却是两种不同的槽结构。行这一层把这些 UI 中间态收住，写回时再由 `sync()` 摊平成数据。
 *
 * `src` 指向上一次写回的那份声明对象，用来兜住抽屉不管的字段（`required`、
 * 数组 `schema.properties` 等），保证同步不丢数据。
 */
let rowSeq = 0
const nextRowKey = () => `row-${(rowSeq += 1)}`

const draft = reactive({
  label: '',
  system: '',
  user: '',
  inputs: [],
  outputs: [],
})

/** 变量分组：哪几组出现由节点类型决定 */
const groups = computed(() => {
  const flags = meta.value.flags
  return [
    flags.inputs
      ? { key: 'inputs', label: '输入变量', hint: '取值可引用上游节点的输出；不选引用时填常量。' }
      : null,
    flags.outputs
      ? { key: 'outputs', label: '输出变量', hint: '下游节点可按变量名引用这些输出。' }
      : null,
  ].filter(Boolean)
})

/** 展开的分组：默认全展开，省掉「打开抽屉还要先点一下」 */
const expandedGroups = ref(['inputs', 'outputs'])

/** 展开了描述的输出行（key = 行的 rowKey） */
const expandedDescs = reactive(new Set())

const isDescExpanded = (key) => expandedDescs.has(key)

const toggleDesc = (key) => {
  if (expandedDescs.has(key)) expandedDescs.delete(key)
  else expandedDescs.add(key)
}

/** 从画布上的节点数据重建行；由「换节点 / 重新打开」触发，不参与写回 */
const hydrate = () => {
  const current = node.value
  if (!current) return
  const data = current.data ?? {}
  draft.label = data.label ?? current.id
  draft.system = data.system ?? ''
  draft.user = data.user ?? ''
  expandedDescs.clear()
  draft.inputs = declarationList(data.inputs).map((declaration) => {
    const slot = declaration.input ?? {}
    const refKey = refKeyOfSlot(slot)
    return {
      key: nextRowKey(),
      name: declaration.name,
      // 现有数据把类型写在取值槽上，此处一并兜住
      typeKey: typeKeyOf({ type: declaration.type ?? slot.type, schema: declaration.schema }),
      refKey,
      literal: refKey ? '' : String(slot.content ?? ''),
      src: declaration,
    }
  })
  draft.outputs = declarationList(data.outputs).map((declaration) => ({
    key: nextRowKey(),
    name: declaration.name,
    typeKey: typeKeyOf(declaration),
    description: declaration.description ?? '',
    src: declaration,
  }))
}

/* ===== 写回（每一次编辑都走这里） ===== */

/**
 * 把草稿摊平成节点数据并写回画布。
 *
 * 所有编辑入口（输入框的 `input`、下拉 / 级联的 `change`、增删行、重命名）都以它为落点 ——
 * 这就是「双向同步」的实现方式：抽屉改一点、画布变一点，不需要保存按钮。
 */
const sync = () => {
  const current = node.value
  if (!current) return
  const flags = meta.value.flags
  const data = { ...current.data, label: draft.label }

  if (flags.prompt) {
    data.system = draft.system
    data.user = draft.user
  }

  if (flags.inputs) {
    data.inputs = draft.inputs.map((row) => {
      const declaration = { ...row.src, name: row.name.trim() }
      applyType(declaration, row.typeKey)
      const literal = row.literal.trim()
      const slot = row.refKey ? slotOfRefKey(row.refKey) : null
      if (slot) {
        declaration.input = { type: declaration.type, blockID: slot.blockID, name: slot.name }
      } else if (literal) {
        declaration.input = { type: declaration.type, content: literal }
      } else {
        // 没引用也没常量：清掉取值槽，卡片会回到「尚未指定取值」的告警态
        delete declaration.input
      }
      row.src = declaration
      return declaration
    })
  }

  if (flags.outputs) {
    data.outputs = draft.outputs.map((row) => {
      const declaration = { ...row.src, name: row.name.trim() }
      applyType(declaration, row.typeKey)
      const description = row.description.trim()
      if (description) declaration.description = description
      else delete declaration.description
      row.src = declaration
      return declaration
    })
  }

  updateNode(current.id, { data })
}

/* ===== 变量行增删 ===== */

const addInputRow = () => {
  draft.inputs.push({ key: nextRowKey(), name: '', typeKey: 'string', refKey: '', literal: '', src: null })
  // 新行默认展开
  expandedGroups.value = expandedGroups.value.includes('inputs') ? expandedGroups.value : [...expandedGroups.value, 'inputs']
  sync()
}

const addOutputRow = () => {
  draft.outputs.push({ key: nextRowKey(), name: '', typeKey: 'string', description: '', src: null })
  expandedGroups.value = expandedGroups.value.includes('outputs')
    ? expandedGroups.value
    : [...expandedGroups.value, 'outputs']
  sync()
}

const removeRow = (rows, index) => {
  rows.splice(index, 1)
  sync()
}

/** 级联选中：emitPath 给出完整路径，末位就是引用键 */
const applyRef = (row, path) => {
  const chain = Array.isArray(path) ? path : [path]
  row.refKey = chain.length ? chain[chain.length - 1] : ''
  sync()
}

/** 清除引用：连同旧常量一起清掉，避免留下界面上看不见的取值 */
const clearRef = (row) => {
  row.refKey = ''
  sync()
}

/* ===== 重命名 ===== */

const renaming = ref(false)
const renameValue = ref('')
const renameInput = ref(null)

const startRename = async () => {
  if (!node.value || renaming.value) return
  renameValue.value = draft.label
  renaming.value = true
  await nextTick()
  renameInput.value?.focus()
  renameInput.value?.select()
}

/** 回车 / 失焦提交：写进草稿后立即同步 */
const commitRename = () => {
  if (!renaming.value) return
  draft.label = renameValue.value.trim()
  renaming.value = false
  sync()
}

const cancelRename = () => {
  renaming.value = false
  renameValue.value = ''
}

/* ===== 交互 ===== */

const handleRemove = async () => {
  const current = node.value
  if (!current) return
  try {
    await ElMessageBox.confirm(
      `删除后无法恢复，确定删除「${current.data?.label || current.id}」吗？`,
      '删除节点',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  removeNodes([current.id])
  emit('update:visible', false)
  ElMessage.success('节点已删除')
}

const handleVisibleChange = (value) => {
  emit('update:visible', value)
}

const CASCADER_PROPS = { expandTrigger: 'hover', emitPath: true }

// 换节点或重新打开时按最新的节点数据重建行，保证抽屉里看到的永远是画布上的当前值
watch(
  () => [props.visible, props.nodeId],
  ([visible, nodeId]) => {
    if (visible && nodeId) hydrate()
  },
  { immediate: true },
)
</script>

<template>
  <!--
    非模态抽屉：画布要保持可点、可拖（点击空白处关闭抽屉正是靠画布自己的 onPaneClick）。
    默认的模态遮罩会把画布整个盖住，那条「点空白关闭」的规则就永远触发不到，所以关掉它。
  -->
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="460px"
    append-to-body
    class="node-drawer"
    :close-on-press-escape="!renaming"
    @update:model-value="handleVisibleChange"
  >
    <!-- 抽屉头部兼节点信息卡：类型图标 + 名称（双击重命名）+ 作用说明，省掉一个独立区块 -->
    <template #header>
      <div class="nd-head">
        <span class="nd-head__icon">
          <el-icon><component :is="meta.icon" /></el-icon>
        </span>
        <div class="nd-head__heading">
          <el-input
            v-if="renaming"
            ref="renameInput"
            v-model="renameValue"
            class="nd-head__rename"
            size="small"
            maxlength="40"
            @keydown.enter="commitRename"
            @keydown.esc="cancelRename"
            @blur="commitRename"
          />
          <h3
            v-else
            class="nd-head__title"
            :title="`${draft.label || node?.id || ''}（双击重命名）`"
            @dblclick="startRename"
          >
            {{ draft.label || meta.name }}
          </h3>
          <p class="nd-head__desc" :title="meta.desc">{{ meta.desc }}</p>
        </div>
      </div>
    </template>

    <div v-if="!node" class="nd-empty">节点已不存在</div>

    <div v-else class="nd-body">
      <!-- 提示词：大模型节点专有，放在变量分组之前（它是这类节点的主要内容） -->
      <section v-if="meta.flags.prompt" class="nd-panel">
        <div class="nd-panel__head">
          <h4 class="nd-panel__title">提示词</h4>
          <span class="nd-panel__hint">用 {{ VARIABLE_SYNTAX }} 引用输入变量</span>
        </div>
        <div class="nd-sub nd-sub--stack">
          <span class="nd-sub__label">System</span>
          <el-input
            v-model="draft.system"
            type="textarea"
            :rows="4"
            resize="vertical"
            placeholder="角色与规则说明"
            @input="sync"
          />
        </div>
        <div class="nd-sub nd-sub--stack">
          <span class="nd-sub__label">User</span>
          <el-input
            v-model="draft.user"
            type="textarea"
            :rows="6"
            resize="vertical"
            placeholder="给模型的具体任务"
            @input="sync"
          />
        </div>
      </section>

      <!--
        变量分组：折叠面板。标题行放分组名 + 数量胶囊 + 「＋」软底按钮，
        行内是「表头 + 数据行」的栅格列表，行与行之间用虚线分隔。
      -->
      <el-collapse v-if="groups.length" v-model="expandedGroups" class="nd-collapse">
        <el-collapse-item v-for="group in groups" :key="group.key" :name="group.key">
          <template #title>
            <span class="nd-group__head">
              <span class="nd-group__name">
                {{ group.label }}
                <span class="nd-panel__count">{{ draft[group.key].length }}</span>
              </span>
              <button
                type="button"
                class="nd-group__add"
                :aria-label="`添加${group.label}`"
                :title="`添加${group.label}`"
                @click.stop="group.key === 'inputs' ? addInputRow() : addOutputRow()"
              >
                <el-icon><Plus /></el-icon>
              </button>
            </span>
          </template>

          <p class="nd-group__hint">{{ group.hint }}</p>

          <!-- 输入组：变量名 | 变量值（类型段 + 取值段） | 删除 -->
          <template v-if="group.key === 'inputs'">
            <p v-if="!draft.inputs.length" class="nd-panel__empty">
              暂无输入变量，点击右上角「＋」新增一项。
            </p>

            <div v-else class="nd-list">
              <div class="nd-head-row nd-grid--value">
                <span>变量名</span>
                <span>变量值</span>
                <span></span>
              </div>

              <div v-for="(row, index) in draft.inputs" :key="row.key" class="nd-row">
                <div class="nd-row__main nd-grid--value">
                  <el-input
                    v-model="row.name"
                    class="nd-row__name"
                    placeholder="变量名，如 question"
                    @input="sync"
                  />

                  <!-- 取值与它的校验文案同属第 2 列，红字才能落在取值段正下方 -->
                  <div class="nd-value-cell">
                    <div class="nd-value">
                      <el-select v-model="row.typeKey" class="nd-value__type" @change="sync">
                        <el-option
                          v-for="option in TYPE_OPTIONS"
                          :key="option.value"
                          :label="option.code"
                          :value="option.value"
                        >
                          <span class="nd-type-option">
                            <span class="nd-type-option__code">{{ option.code }}</span>
                            <span class="nd-type-option__name">{{ option.label }}</span>
                          </span>
                        </el-option>
                      </el-select>

                      <!-- 外面这层 div 是样式挂点：el-cascader 的 class 落点不确定，包一层自有元素才压得住它的描边 -->
                      <div class="nd-value__picker" :class="{ 'is-error': Boolean(refErrorOf(row)) }">
                        <el-cascader
                          :model-value="cascaderPathOf(row)"
                          :options="upstreamTree"
                          :props="CASCADER_PROPS"
                          :show-all-levels="false"
                          :clear-icon="CloseBold"
                          clearable
                          placeholder="选择变量"
                          popper-class="nd-cascader__popper"
                          @change="applyRef(row, $event)"
                          @clear="clearRef(row)"
                        >
                          <template #default="{ data }">
                            <span class="nd-cascader__node">
                              <span class="nd-cascader__node-name">{{ data.label }}</span>
                              <span v-if="data.hint" class="nd-cascader__node-type">{{ data.hint }}</span>
                            </span>
                          </template>
                          <template #empty>
                            <p class="nd-cascader__empty">
                              本节点没有可引用的上游变量，请先把上游节点连到本节点。
                            </p>
                          </template>
                        </el-cascader>
                      </div>
                    </div>
                    <p v-if="refErrorOf(row)" class="nd-row__error">{{ refErrorOf(row) }}</p>
                  </div>

                  <el-button
                    class="nd-row__remove"
                    text
                    :icon="Delete"
                    aria-label="删除该变量"
                    title="删除该变量"
                    @click="removeRow(draft.inputs, index)"
                  />
                </div>

                <!-- 没选引用时才有常量：它属于取值这一格，因此缩成一行子行 -->
                <div v-if="!row.refKey" class="nd-sub">
                  <span class="nd-sub__label">常量</span>
                  <el-input
                    v-model="row.literal"
                    class="nd-sub__control"
                    placeholder="未选择变量时使用的固定值"
                    @input="sync"
                  />
                </div>
              </div>
            </div>
          </template>

          <!-- 输出组：变量名 | 变量类型 | 描述 | 删除 -->
          <template v-else>
            <p v-if="!draft.outputs.length" class="nd-panel__empty">
              暂无输出变量，点击右上角「＋」新增一项。
            </p>

            <div v-else class="nd-list">
              <div class="nd-head-row nd-grid--plain">
                <span>变量名</span>
                <span>变量类型</span>
                <span></span>
                <span></span>
              </div>

              <div v-for="(row, index) in draft.outputs" :key="row.key" class="nd-row">
                <div class="nd-row__main nd-grid--plain">
                  <el-input
                    v-model="row.name"
                    class="nd-row__name"
                    placeholder="变量名，如 summary"
                    @input="sync"
                  />

                  <!-- 独立一列的类型选择：短码 + 完整类型名，像 el-select 一样自带描边 -->
                  <el-select v-model="row.typeKey" class="nd-row__type" @change="sync">
                    <el-option
                      v-for="option in TYPE_OPTIONS"
                      :key="option.value"
                      :label="option.code"
                      :value="option.value"
                    >
                      <span class="nd-type-option">
                        <span class="nd-type-option__code">{{ option.code }}</span>
                        <span class="nd-type-option__name">{{ option.label }}</span>
                      </span>
                    </el-option>
                  </el-select>

                  <el-button
                    class="nd-row__expand"
                    text
                    :class="{ 'is-active': isDescExpanded(row.key) }"
                    :icon="FullScreen"
                    :aria-label="isDescExpanded(row.key) ? '收起描述' : '编辑描述'"
                    :title="isDescExpanded(row.key) ? '收起描述' : '编辑描述'"
                    @click="toggleDesc(row.key)"
                  />

                  <el-button
                    class="nd-row__remove"
                    text
                    :icon="Delete"
                    aria-label="删除该变量"
                    title="删除该变量"
                    @click="removeRow(draft.outputs, index)"
                  />
                </div>

                <div v-if="isDescExpanded(row.key)" class="nd-sub nd-sub--stack">
                  <span class="nd-sub__label">描述</span>
                  <el-input
                    v-model="row.description"
                    maxlength="200"
                    placeholder="帮助大模型准确了解参数的作用"
                    @input="sync"
                  />
                </div>
              </div>
            </div>
          </template>


          
        </el-collapse-item>
      </el-collapse>
    </div>

    <template #footer>
      <div class="nd-foot">
        <el-button type="danger" plain :icon="Delete" @click="handleRemove">删除节点</el-button>
        <span class="nd-foot__spacer"></span>
        <span class="nd-foot__hint">修改即时生效</span>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
/*
 * 注意：抽屉外壳（.el-drawer / __header / __body / __footer）的样式不能写在这个 scoped 块里。
 * 抽屉是 append-to-body 的 Teleport，这几个元素由 Element 渲染、不带本组件的 scope id，
 * 所以 :deep(.el-drawer__body) 与 .el-drawer__body 都匹配不到。它们的样式统一放在文件末尾的
 * 非 scoped 块里，用抽屉自己的 class `.node-drawer` 做作用域。
 */

/* ===== 头部：图标 + 名称（双击重命名）+ 说明 ===== */
.nd-head {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.nd-head__icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  font-size: 18px;
}

.nd-head__heading {
  flex: 1;
  min-width: 0;
}

.nd-head__title {
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

.nd-head__rename {
  width: 100%;
  max-width: 280px;
}

.nd-head__desc {
  margin: 2px 0 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: var(--tb-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 主体：横向留白的唯一来源（出血元素都按它算负边距） ===== */
.nd-body {
  --nd-gutter: 16px;
  display: flex;
  flex-direction: column;
}

.nd-empty {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

/* ===== 非折叠的分组（提示词） ===== */
.nd-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 0 12px;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.nd-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nd-panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--tb-text-primary);
}

.nd-panel__hint {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: var(--tb-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nd-panel__count {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--tb-bg-sunken);
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: var(--tb-text-secondary);
}

.nd-panel__empty {
  margin: 0;
  padding: 2px 0;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* ===== 变量分组：折叠面板 ===== */
/* 沿用「无边框」风格：抹掉 el-collapse 自带的分隔线与内容区边距，分组靠自身底边线分隔 */
.nd-collapse {
  border: none;
}

.nd-collapse :deep(.el-collapse) {
  border: none;
}

.nd-collapse :deep(.el-collapse-item) {
  padding-bottom: 12px;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.nd-collapse :deep(.el-collapse-item:last-child) {
  border-bottom: none;
}

/* 标题行：整行可点，底边线 / hover 底色通到抽屉两端（用 gutter 的负值抵消内边距） */
.nd-collapse :deep(.el-collapse-item__header) {
  display: flex;
  height: auto;
  padding: 10px var(--nd-gutter);
  border: none;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  color: var(--tb-text-primary);
  border-top:1px solid red ;
}

.nd-collapse :deep(.el-collapse-item__header:hover),
.nd-collapse :deep(.el-collapse-item__header:focus-visible) {
  outline: none;

  color: var(--tb-primary-text);
}

.nd-collapse :deep(.el-collapse-item__wrap) {
  padding: 0 16px;
  border: none;
  background: transparent;
}

.nd-collapse :deep(.el-collapse-item__content) {
  padding: 4px 0 0;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
}

/* 折叠箭头放左侧：默认它在标题之后、且 margin-left: auto 会顶到最右 */
.nd-collapse :deep(.el-collapse-item__arrow) {
  order: -1;
  margin: 0 8px 0 0;
  color: var(--tb-text-secondary);
}

.nd-group__head {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.nd-group__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

/* 「添加」：软底圆角方块 + 主色加号；高频入口用图标方块比「＋ 添加」省横向空间 */
.nd-group__add {
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

.nd-group__add:hover {
  background: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.nd-group__add:focus-visible {
  outline: 2px solid var(--tb-ring);
  outline-offset: 1px;
}

.nd-group__hint {
  margin: 0 0 8px;
  padding: 2px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

/* ===== 变量列表：表头与数据行共用同一套栅格，保证列对齐 ===== */
.nd-list {
  display: flex;
  flex-direction: column;
}

/*
 * 栅格模板按分组切换：
 * - 输入组：变量名 | 变量值（类型段 + 取值段） | 删除
 * - 输出组：变量名 | 变量类型 | 描述展开 | 删除
 */
.nd-grid--value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr) 28px;
  /* align-items: start：第 2 列比别的高（多一行校验文案），居中会把控件整体压下去 */
  align-items: start;
  gap: 8px;
}

.nd-grid--plain {
  display: grid;
  /*
   * 类型列固定宽度：它只放一个短码（`str` / `[obj]`），再宽也读不出更多信息，
   * 却会把变量名列挤到显示不全（`student_categories` 这种长名很常见）。
   */
  grid-template-columns: minmax(0, 1fr) 132px 28px 28px;
  align-items: center;
  gap: 8px;
}

/* 表头：只做列名提示，轻到不抢视线 */
.nd-head-row {
  margin: 0 -8px;
  padding: 2px 8px 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.nd-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 左右负外边距抵消内边距：内容位置不变，但 hover 底色能铺满整行宽度 */
  margin: 0 -8px;
  padding: 10px 8px;
  border-bottom: var(--tb-border-width) dashed var(--tb-border);
  border-radius: var(--tb-radius-md);
  transition:
    border-color var(--tb-dur-2) var(--tb-ease-out),
    background-color var(--tb-dur-2) var(--tb-ease-out);
}

.nd-row:hover,
.nd-row:focus-within {
  background: var(--tb-bg-raised);
}

/* 末行不画线，免得与分组底边线叠成双线 */
.nd-row:last-child {
  border-bottom-color: transparent;
}

/* 行内控件统一 32px：Element 默认尺寸与它一致，这里只保证栅格内不被拉伸 */
.nd-row__name {
  min-width: 0;
}

/*
 * 「变量值」整格：分段控件 + 它的校验文案。
 * 类型段宽度抽成变量，是为了让下面的红字能正好缩进到取值段左缘。
 */
.nd-value-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  --nd-type-width: 74px;
}

/* 变量值：类型段 + 取值段合成一个分段控件，外层只画一道描边 */
.nd-value {
  display: flex;
  min-width: 0;
  /* border-box 的 32px：内部两段 30px，与同行其他控件齐平 */
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

.nd-value:hover {
  border-color: var(--tb-border-strong);
}

.nd-value:focus-within {
  border-color: var(--tb-primary);
  box-shadow: 0 0 0 2px color-mix(in oklab, var(--tb-primary) 16%, transparent);
}

/* 类型段：贴左边，右侧一条竖线把两段分开（自身的描边全部剥掉） */
.nd-value__type {
  flex: 0 0 var(--nd-type-width);
  min-width: 0;
  border-right: var(--tb-border-width) solid var(--tb-border);
}

.nd-value__type :deep(.el-select__wrapper) {
  min-height: 30px;
  padding: 0 10px;
  background: transparent;
  box-shadow: none;
}

.nd-value__type :deep(.el-select__wrapper:hover),
.nd-value__type :deep(.el-select__wrapper.is-hovering),
.nd-value__type :deep(.el-select__wrapper.is-focused) {
  box-shadow: none;
}

.nd-value__type :deep(.el-select__selected-item) {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.nd-value__type :deep(.el-select__caret) {
  font-size: 11px;
  color: var(--tb-text-tertiary);
}

/* 取值段：级联选择器铺满剩余宽度 */
.nd-value__picker {
  display: flex;
  flex: 1;
  min-width: 0;
}

.nd-value__picker :deep(.el-cascader) {
  flex: 1;
  min-width: 0;
  /* 根元素默认 line-height: 32px 会把行盒撑到 32px，取值段就回不到 30px */
  line-height: normal;
}

/* 嵌在分段控件里：剥掉级联选择器自带的描边与底色 */
.nd-value__picker :deep(.el-input__wrapper) {
  padding: 0 10px;
  background: transparent;
}

/* 逐个状态覆盖：Element 在 hover / focus 会把内嵌描边重新画回来 */
.nd-value__picker:not(.is-error) :deep(.el-input__wrapper),
.nd-value__picker:not(.is-error) :deep(.el-input__wrapper:hover),
.nd-value__picker:not(.is-error) :deep(.el-input__wrapper.is-focus),
.nd-value__picker:not(.is-error) :deep(.el-input__wrapper:focus-within),
.nd-value__picker:not(.is-error) :deep(.el-cascader:hover .el-input__wrapper),
.nd-value__picker:not(.is-error) :deep(.el-cascader .el-input.is-focus .el-input__wrapper) {
  box-shadow: none;
}

/* 引用失效：红框只画在取值段自身，左边类型段不该被一起染红 */
.nd-value__picker.is-error :deep(.el-input__wrapper),
.nd-value__picker.is-error :deep(.el-input__wrapper:hover),
.nd-value__picker.is-error :deep(.el-input__wrapper.is-focus),
.nd-value__picker.is-error :deep(.el-input__wrapper:focus-within),
.nd-value__picker.is-error :deep(.el-cascader:hover .el-input__wrapper),
.nd-value__picker.is-error :deep(.el-cascader .el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--tb-danger) inset;
}

.nd-value__picker :deep(.el-input__inner) {
  font-size: 12px;
}

/* 类型下拉的选项：短码 + 完整类型名 */
.nd-type-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.nd-type-option__code {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-primary);
}

.nd-type-option__name {
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* 取值下拉的叶子：变量名 + 类型标签 */
.nd-cascader__node {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.nd-cascader__node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nd-cascader__node-type {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--tb-bg-sunken);
  font-size: 10px;
  color: var(--tb-text-secondary);
}

.nd-cascader__empty {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

/* 取值校验文案：缩进到取值段左缘，正落在它下面 */
.nd-row__error {
  margin: 0;
  padding-left: var(--nd-type-width, 74px);
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-danger);
}

/* 输出行的类型列：独立一列，自带描边 */
.nd-row__type {
  min-width: 0;
}

.nd-row__type :deep(.el-select__selected-item) {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.nd-row__type :deep(.el-select__caret) {
  font-size: 11px;
  color: var(--tb-text-tertiary);
}

.nd-row__expand,
.nd-row__remove {
  /* 与首行控件同高：整行按 start 对齐后，靠这个高度让图标落在控件的中线上 */
  height: 32px;
  padding: 0;
  font-size: 14px;
  line-height: 1;
  color: var(--tb-text-tertiary);
}

.nd-row__expand.is-active,
.nd-row__expand:hover {
  color: var(--tb-primary);
}

.nd-row__remove:hover {
  color: var(--tb-danger);
}

/* ===== 次级行：取值常量 / 描述，都是「标签 + 控件」 ===== */
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
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.nd-sub__control {
  flex: 1;
  min-width: 0;
}

/* ===== 抽屉底部：分隔线画在自身顶边 ===== */
.nd-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.nd-foot__spacer {
  flex: 1;
}

.nd-foot__hint {
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* 窄视口下压缩取值列与类型段，保证变量名列可用 */
@media (max-width: 520px) {
  .nd-grid--value {
    grid-template-columns: minmax(0, 1fr) minmax(0, 2fr) 22px;
    gap: 6px;
  }

  .nd-grid--plain {
    grid-template-columns: minmax(0, 1fr) 108px 22px 22px;
    gap: 6px;
  }

  .nd-value-cell {
    --nd-type-width: 68px;
  }

  .nd-row {
    gap: 6px;
    margin: 0 -6px;
    padding: 8px 6px;
  }

  .nd-head-row {
    margin: 0 -6px;
    padding: 2px 6px 6px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .nd-collapse :deep(.el-collapse-item__header),
  .nd-group__add,
  .nd-row,
  .nd-value {
    transition: none;
  }
}
</style>

<!--
  抽屉外壳的内边距（.el-drawer__header / __body / __footer）。

  必须写在非 scoped 块里：抽屉是 append-to-body 的 Teleport，这几个元素由 Element 渲染、
  不带本组件的 scope id，scoped 块里无论 :deep() 还是普通选择器都匹配不到。
  作用域改用抽屉自己的 class `.node-drawer` —— Element 用 mergeProps 把 $attrs 并到 `.el-drawer` 上，
  它正是这三个元素的祖先。
-->
<style>
.node-drawer .el-drawer__header {
  /* 默认还有 32px 的 margin-bottom，信息卡只占两行高，留太多会把下面的表单整体推下去 */
  margin-bottom: 12px;
  padding: 16px 16px 0;
}

.node-drawer .el-drawer__body {
  padding: 0px;
}

.node-drawer .el-drawer__footer {
  padding: 0 16px 16px;
}

/* 取值下拉不限高：菜单容器默认写死 204px，变量多时会出现内部滚动条 */
.nd-cascader__popper .el-cascader-menu__wrap.el-scrollbar__wrap {
  height: auto;
  max-height: none;
}
</style>
