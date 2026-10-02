<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Delete, FullScreen, Plus } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useVueFlow } from '@vue-flow/core'
import { injectOutputsOf, iterationOutputWarningsOf, upstreamOutputsOf } from '../utils/variable-sources'
import { metaOf } from '../utils/node-meta'
const { getNodes, getEdges, removeNodes } = useVueFlow()
const props = defineProps({
  visible: { type: Boolean, default: false },
  nodeId: { type: String, default: null },
})

const options = [
  {
    value: 'string',
    label: 'String',
  },
  {
    value: 'integer',
    label: 'Integer',
  },
  {
    value: 'number',
    label: 'Number',
  },
  {
    value: 'boolean',
    label: 'Boolean',
  },
  {
    value: 'time',
    label: 'Time',
  },
  {
    value: 'object',
    label: 'Object',
  },
  {
    value: 'list',
    label: 'List',
    children: [
      {
        value: 'string',
        label: 'String',
      },
      {
        value: 'integer',
        label: 'Integer',
      },
      {
        value: 'number',
        label: 'Number',
      },
      {
        value: 'boolean',
        label: 'Boolean',
      },
      {
        value: 'object',
        label: 'Object',
      },
    ],
  },
  {
    value: 'file',
    label: 'File',
    children: [
      {
        value: 'string',
        label: 'String',
      },
      {
        value: 'integer',
        label: 'Integer',
      },
      {
        value: 'number',
        label: 'Number',
      },
      {
        value: 'boolean',
        label: 'Boolean',
      },
      {
        value: 'object',
        label: 'Object',
      },
    ],
  },
]



const node = computed(() => getNodes.value.find((item) => item?.id === props.nodeId) ?? null)

const meta = computed(() => metaOf(node.value?.type))

/** 当前节点所属的迭代容器（顶层节点为 null） */
const containerOf = computed(() => {
  const parentId = node.value?.parentNode
  if (!parentId) return null
  return getNodes.value.find((item) => item?.id === parentId) ?? null
})

/** 当前节点可选的变量：抽屉打开/切换节点时刷新，直接喂给变量取值选择器 */
const upstreamOutputs = ref([])

/**
 * 候选来源（纯函数在 `utils/variable-sources` 里，可脱离 Vue 单独验证）：
 * - 上游变量按「同一个 parentNode」过滤，迭代体内外不能互相引用（后端一定取不到值）；
 * - 迭代体内的子块额外带上容器「每轮注入」的变量（index / item / 迭代变量 / 当前项别名），
 *   它们的 `blockID` 是容器 id，因此与同层上游自然分成不同的分组。
 */
const refreshUpstreamOutputs = () => {
  const current = node.value
  if (!props.visible || !current) {
    upstreamOutputs.value = []
    return
  }

  const nodes = getNodes.value
  const inject = injectOutputsOf(nodes, containerOf.value, current.id)
  // 注入变量排在前面：分组按出现顺序生成，容器的「每轮注入」组因此稳定排在其它分组之前
  upstreamOutputs.value = [...inject, ...upstreamOutputsOf(nodes, getEdges.value ?? [], current.id)]
}

/**
 * 候选列表的依赖指纹：节点的变量声明与连线的两端，任何变化都要让候选刷新。
 * 旧实现只监听边的**数量**，改输出变量名（边数量不变）时列表不会更新。
 */
const sourcesFingerprint = computed(() => {
  const nodes = getNodes.value
    .map((item) => `${item?.id}:${(item?.data?.outputs ?? [])
      .map((output) => `${output?.name ?? output}:${output?.type ?? ''}`)
      .join(',')}`)
    .join('|')
  const edges = (getEdges.value ?? []).map((edge) => `${edge?.source}>${edge?.target}`).join('|')
  return `${props.visible}|${props.nodeId}|${nodes}|${edges}`
})

watch(sourcesFingerprint, refreshUpstreamOutputs, { immediate: true })

/** 类型短码：与节点卡片上变量胶囊的读法保持同一套 */
const SHORT_TYPES = {
  string: 'str',
  integer: 'int',
  number: 'num',
  float: 'num',
  boolean: 'bool',
  time: 'time',
  list: 'arr',
  object: 'obj',
  file: 'file',
}

/**
 * 输入行当前取值的类型短码：
 * 数组（type === 'list'）读作 `[obj]` / `[str]`，其余直接读成 `str` / `int`。
 * 判据必须是 type 而不是「有没有 schema」：schema 是引用过数组变量后可能留在数据里的
 * 存量字段，拿它当判据的话，从数组类型改成标量类型后方括号会一直挂着去不掉。
 */
const typeCodeOf = (row) => {
  const type = row?.input?.type ?? row?.type ?? 'string'
  const schema = row?.schema ?? row?.input?.schema
  const code = SHORT_TYPES[type] ?? SHORT_TYPES.string
  if (type !== 'list') return code
  // 数组的元素类型取自 schema.type，缺失时按 string 兜底，避免渲染出空方括号
  return `[${SHORT_TYPES[schema?.type] ?? SHORT_TYPES.string}]`
}

/**
 * 取值选择器的选项：把上游变量按「来源节点」分组，做成二级 cascader。
 * 一级 value 是来源节点 id，二级 value 是变量名 —— 选中的结果就是 [blockID, name]，
 * 可以直接回填到 input 上，不用再反查。
 */
const upstreamOptions = computed(() => {
  const groups = new Map()

  for (const item of upstreamOutputs.value) {
    if (!groups.has(item.blockID)) {
      groups.set(item.blockID, {
        value: item.blockID,
        label: item.nodeLabel || item.blockID,
        children: [],
      })
    }
    groups.get(item.blockID).children.push({
      value: item.name,
      label: `${SHORT_TYPES[item.type] ?? SHORT_TYPES.string} ${item.name}`,
    })
  }

  return [...groups.values()]
})

/** 取值选择器的行为参数统一在这里配，模板里不用重复写字面量 */
const SOURCE_CASCADER_PROPS = { expandTrigger: 'hover' }

/** 已绑定取值的行回显成 [blockID, name]；常量行没有这两个字段，回显为空即「未绑定」 */
const sourcePathOf = (row) => {
  const { blockID, name } = row?.input ?? {}
  return blockID && name ? [blockID, name] : []
}

/**
 * 选中 / 清空上游变量：把来源写回 input（blockID + name），并同步取值类型。
 * 类型跟着来源一起改，才不会出现「引用了一个数组变量、显示却还是 str」这种对不上的数据；
 * 清空只解绑取值，保留原类型，用户仍可把它当常量用。
 */
const handleSourceChange = (row, path) => {
  if (!row) return
  // 新加的行还没有取值槽，这里先兜一个：否则选了上游变量也会被下面的分支直接丢掉
  if (!row.input) row.input = {}

  const [blockID, name] = Array.isArray(path) ? path : []

  if (!blockID || !name) {
    delete row.input.blockID
    delete row.input.name
    return
  }

  const target = upstreamOutputs.value.find((item) => item.blockID === blockID && item.name === name)
  if (!target) return

  row.input.blockID = blockID
  row.input.name = name
  if (target.type) row.input.type = target.type

  if (target.schema) {
    // 数组：结构跟随来源变量
    row.input.schema = { ...target.schema }
    // 行上本来就声明了 schema 时一并跟随，否则读到的还是旧结构
    if (row.schema !== undefined) row.schema = { ...target.schema }
  } else {
    // 标量：把上一次引用数组留下的 schema 清掉，不然方括号会一直挂在显示上
    delete row.input.schema
    if (row.schema !== undefined) delete row.schema
  }
}

/* ===== 变量行：输入 / 输出共用 ===== */

/** 折叠面板展开的分组：name 与模板里的折叠项一致（3 输入 / 4 输出），默认全展开 */
const activeGroups = ref(['3', '4'])

/**
 * 变量行的稳定 key（输入 / 输出共用）：行对象直接就是 node.data 里的元素，数据里没有 id 字段，
 * 所以用 WeakMap 惰性发号 —— 不往数据里塞 UI 字段，行被移除后 key 也随之回收。
 */
const rowKeys = new WeakMap()
let rowSeq = 0

const rowKeyOf = (row) => {
  if (!row) return ''
  if (!rowKeys.has(row)) rowKeys.set(row, `row-${(rowSeq += 1)}`)
  return rowKeys.get(row)
}

/* ===== 输出变量 ===== */

/** 类型下拉的显示树：把 options 的完整类型名换成短码，数组子项读作 `[obj]`，与输入段的读法同一套 */
const typeOptions = computed(() =>
  options.map((option) => ({
    ...option,
    label: SHORT_TYPES[option.value] ?? option.label,
    children: option.children?.map((child) => ({
      ...child,
      label: `[${SHORT_TYPES[child.value] ?? child.label}]`,
    })),
  })),
)

/** 类型下拉的回显路径：只有带子项的容器类型（list / file）走二级，其余即使有残留 schema 也只回显一级 */
const typePathOf = (row) => {
  const type = row?.type ?? 'string'
  const isContainer = Boolean(options.find((option) => option.value === type)?.children?.length)
  return isContainer && row?.schema?.type ? [type, row.schema.type] : [type]
}

/**
 * 类型下拉选中 → 写回声明：容器类型落成 `{ type, schema: { type } }`，标量清掉残留 schema。
 * schema 里 type 之外的字段（数组元素的 properties 等）原样保留，改元素类型不丢结构。
 */
const handleOutputTypeChange = (row, path) => {
  const [type, element] = Array.isArray(path) ? path : [path]
  if (!type) return
  if (element) row.schema = { ...row.schema, type: element }
  else delete row.schema
  row.type = type
}

/** 展开了描述的输出行（key = rowKeyOf(row)） */
const expandedDescs = reactive(new Set())

const isDescExpanded = (key) => expandedDescs.has(key)

const toggleDesc = (key) => {
  if (expandedDescs.has(key)) expandedDescs.delete(key)
  else expandedDescs.add(key)
}

/** 输出数组：节点数据里没有就先建一个，保证「＋」在结束节点这类没声明 outputs 的节点上也能用 */
const outputListOf = () => {
  const target = node.value
  if (!target?.data) return null
  if (!Array.isArray(target.data.outputs)) target.data.outputs = []
  return target.data.outputs
}

const addOutputRow = () => {
  const list = outputListOf()
  if (!list) return
  list.push({ name: '', type: 'string', description: '' })
  // 新增后要保证输出分组可见，否则会出现「点了＋却看不到新行」
  if (!activeGroups.value.includes('4')) activeGroups.value = [...activeGroups.value, '4']
}

const removeOutputRow = (index) => {
  const list = outputListOf()
  if (!list) return
  list.splice(index, 1)
}

/* ===== 输入变量 ===== */

/** 输入数组：与 outputListOf 对称，节点数据里没有就先建一个（开始节点就没有 inputs 字段） */
const inputListOf = () => {
  const target = node.value
  if (!target?.data) return null
  if (!Array.isArray(target.data.inputs)) target.data.inputs = []
  return target.data.inputs
}

/**
 * 新增输入变量：只落 `{ name, type }`，**不写取值槽** ——
 * input 缺失即「未指定取值」，节点卡片会给这一行打告警色，等用户选了上游变量再补上。
 */
const addInputRow = () => {
  const list = inputListOf()
  if (!list) return
  list.push({ name: '', type: 'string' })
  // 新增后要保证输入分组可见，否则会出现「点了＋却看不到新行」
  if (!activeGroups.value.includes('3')) activeGroups.value = [...activeGroups.value, '3']
}

const removeInputRow = (index) => {
  const list = inputListOf()
  if (!list) return
  list.splice(index, 1)
}

/* ===== 迭代变量（仅迭代节点）：迭代体每轮都能读到的中间变量 ===== */

/** 迭代变量数组：与 inputListOf 对称，落点是 `data.variableParameters` */
const paramListOf = () => {
  const target = node.value
  if (!target?.data) return null
  if (!Array.isArray(target.data.variableParameters)) target.data.variableParameters = []
  return target.data.variableParameters
}

/** 新增迭代变量：行结构与输入行一致（变量名 + 类型 + 上游取值），取值槽等用户选完来源再补 */
const addParamRow = () => {
  const list = paramListOf()
  if (!list) return
  list.push({ name: '', type: 'string' })
  // 新增后要保证分组可见，否则会出现「点了＋却看不到新行」
  if (!activeGroups.value.includes('2')) activeGroups.value = [...activeGroups.value, '2']
}

const removeParamRow = (index) => {
  const list = paramListOf()
  if (!list) return
  list.splice(index, 1)
}

/* ===== 迭代体的同名校验 ===== */

/** 迭代体子块：画布上 `parentNode` 指向当前节点的节点 */
const childNodes = computed(() => getNodes.value.filter((item) => item?.parentNode === props.nodeId))

/**
 * 输出变量里与迭代体子块产出**不同名**的那些。
 * 后端 `IterateNode#aggregate` 按同名把每轮子块的产出聚合成列表，名字对不上就汇总为空列表
 * （表现为「流程成功但没有结果」），这里只做提示、不阻断编辑。
 */
const outputWarnings = computed(() =>
  node.value?.type === '4' ? iterationOutputWarningsOf(node.value, childNodes.value) : new Set(),
)

const isOutputWarned = (name) => Boolean(name) && outputWarnings.value.has(name)

const emit = defineEmits(['update:visible'])

const handleVisibleChange = (value) => {
  emit('update:visible', value)
}

/**
 * 删除当前节点（含迭代容器内的子块与相关连线）。
 *
 * 必须显式级联：Vue Flow 的默认删除只删节点本身、不删挂在它下面的子块，
 * 容器被删后子块会变成悬空节点，再次运行时被当作顶层节点提交执行。
 */
const removeCurrentNode = async () => {
  const current = node.value
  if (!current) return
  try {
    await ElMessageBox.confirm(
      `删除节点「${current.data?.label || current.id}」？迭代容器内的子块与相关连线会一并删除。`,
      '删除节点',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消：ElMessageBox 以 reject 表示「取消」，静默返回即可
    return
  }
  removeNodes([current], true, true)
  emit('update:visible', false)
}

// const activeNames = ref(['1'])

</script>

<template>
 <el-drawer class="nd-drawer" :model-value="visible" @update:model-value="handleVisibleChange">
    <template #header>
      <div class="nd-head">
        <span class="nd-head__icon">
          <el-icon><component :is="meta.icon" /></el-icon>
        </span>
        <div class="nd-head__heading">
          <h3 class="nd-head__title">
            {{ node?.data?.label }}
          </h3>
          <p class="nd-head__desc" :title="meta.desc">{{ meta?.desc }}</p>
        </div>
        <!-- 删除节点：容器连同迭代体子块一并删除，二次确认避免误触 -->
        <button
          type="button"
          class="nd-head__remove"
          aria-label="删除节点"
          title="删除节点"
          @click.stop="removeCurrentNode"
        >
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </template>
  <!-- node 为 null = 当前节点已被删除（例如画布上按删除键删掉的正是抽屉在编辑的节点）：
       整块内容不渲染，避免模板里读 `node.data` 直接抛 TypeError -->
  <div v-if="node" class="demo-collapse">
    <el-collapse v-model="activeGroups" expand-icon-position="left" >

      <el-collapse-item name="3">
        <template #title>
          <span class="nd-group__head">
            <span class="nd-group__name">
              输入
              <span class="nd-panel__count">{{ node.data?.inputs?.length ?? 0 }}</span>
            </span>
            <button
              type="button"
              class="nd-group__add"
              aria-label="添加输入变量"
              title="添加输入变量"
              @click.stop="addInputRow"
            >
              <el-icon><Plus /></el-icon>
            </button>
          </span>
        </template>

        <p v-if="!node.data?.inputs?.length" class="nd-panel__empty">
          暂无输入变量，点击右上角「＋」新增一项。
        </p>
        <div v-else class="nd-list">
          <div class="nd-head-row nd-grid--value">
            <span>变量名</span>
            <span>变量值</span>
            <span></span>
          </div>
          <div
            v-for="(row, index) in node.data.inputs"
            :key="rowKeyOf(row)"
            class="nd-row nd-row--item"
          >
            <div class="nd-row__main nd-grid--value">
              <el-input
                size="small"
                style="color: #000;"
                v-model="row.name"
                class="nd-row__name"
                placeholder="变量名，如 question"
              />
              <!-- 变量值：左边只读类型短码（跟着所选来源同步），右边从上游可达节点的 output 里选 -->
              <div class="nd-row__value">
                <div class="nd-row__type" :title="row?.description">{{ typeCodeOf(row) }}</div>
                <el-cascader
                  style="width: 100%;"
                  size="small"
                  :model-value="sourcePathOf(row)"
                  :options="upstreamOptions"
                  :props="SOURCE_CASCADER_PROPS"
                  :disabled="!upstreamOptions.length"
                  clearable
                  filterable
                  :placeholder="upstreamOptions.length ? '引用上游变量' : '无上游变量'"
                  @update:model-value="(path) => handleSourceChange(row, path)"
                />
              </div>

              <el-button
                class="nd-row__remove"
                text
                :icon="Delete"
                aria-label="删除该变量"
                title="删除该变量"
                @click="removeInputRow(index)"
              />
            </div>
          </div>
        </div>
      </el-collapse-item>
      <!-- 迭代变量：只有迭代节点有；迭代体内的子块每一轮都能读到它们（后端按 variableParameters 注入） -->
      <el-collapse-item v-if="meta.flags.variableParams" name="2">
        <template #title>
          <span class="nd-group__head">
            <span class="nd-group__name">
              迭代变量
              <span class="nd-panel__count">{{ node.data?.variableParameters?.length ?? 0 }}</span>
            </span>
            <button
              type="button"
              class="nd-group__add"
              aria-label="添加迭代变量"
              title="添加迭代变量"
              @click.stop="addParamRow"
            >
              <el-icon><Plus /></el-icon>
            </button>
          </span>
        </template>

        <p v-if="!node.data?.variableParameters?.length" class="nd-panel__empty">
          暂无迭代变量，点击右上角「＋」新增一项；迭代体内的子块每轮都能读到它们。
        </p>
        <div v-else class="nd-list">
          <div class="nd-head-row nd-grid--value">
            <span>变量名</span>
            <span>变量值</span>
            <span></span>
          </div>
          <div
            v-for="(row, index) in node.data.variableParameters"
            :key="rowKeyOf(row)"
            class="nd-row nd-row--item"
          >
            <div class="nd-row__main nd-grid--value">
              <el-input
                size="small"
                v-model="row.name"
                class="nd-row__name"
                placeholder="变量名，如 jj"
              />
              <!-- 变量值：左边只读类型短码，右边从同层上游节点的 output 里选 -->
              <div class="nd-row__value">
                <div class="nd-row__type" :title="row?.description">{{ typeCodeOf(row) }}</div>
                <el-cascader
                  style="width: 100%;"
                  size="small"
                  :model-value="sourcePathOf(row)"
                  :options="upstreamOptions"
                  :props="SOURCE_CASCADER_PROPS"
                  :disabled="!upstreamOptions.length"
                  clearable
                  filterable
                  :placeholder="upstreamOptions.length ? '引用上游变量' : '无上游变量'"
                  @update:model-value="(path) => handleSourceChange(row, path)"
                />
              </div>

              <el-button
                class="nd-row__remove"
                text
                :icon="Delete"
                aria-label="删除该变量"
                title="删除该变量"
                @click="removeParamRow(index)"
              />
            </div>
          </div>
        </div>
      </el-collapse-item>
      <el-collapse-item name="4">
        <template #title>
          <span class="nd-group__head">
            <span class="nd-group__name">
              输出
              <span class="nd-panel__count">{{ node.data?.outputs?.length ?? 0 }}</span>
              <!-- 迭代节点的输出要按同名聚合各轮子块的产出，名字对不上会静默汇总为空列表 -->
              <span
                v-if="outputWarnings.size"
                class="nd-group__warn"
                :title="`须与迭代体子块的产出同名，否则汇总为空：${[...outputWarnings].join('、')}`"
              >{{ outputWarnings.size }} 个与子块产出不同名</span>
            </span>
            <button
              type="button"
              class="nd-group__add"
              aria-label="添加输出变量"
              title="添加输出变量"
              @click.stop="addOutputRow"
            >
              <el-icon><Plus /></el-icon>
            </button>
          </span>
        </template>

        <p v-if="!node.data?.outputs?.length" class="nd-panel__empty">
          暂无输出变量，点击右上角「＋」新增一项。
        </p>
        <div v-else class="nd-list">
          <div class="nd-head-row nd-grid--plain">
            <span>变量名</span>
            <span>变量类型</span>
            <span></span>
            <span></span>
          </div>
          <div
            v-for="(row, index) in node.data.outputs"
            :key="rowKeyOf(row)"
            class="nd-row nd-row--item"
            :class="{ 'nd-row--warn': isOutputWarned(row.name) }"
            :title="isOutputWarned(row.name) ? '须与迭代体子块的产出同名，否则汇总为空' : ''"
          >
            <div class="nd-row__main nd-grid--plain">
              <el-input
                size="small"
                style="color: #000;"
                v-model="row.name"
                class="nd-row__name"
                placeholder="变量名，如 summary"
              />
              <!-- 变量类型：复用 options（list / file 走二级），选中后落成 { type, schema:{ type } } -->
              <el-cascader
                size="small"
                :model-value="typePathOf(row)"
                :options="typeOptions"
                :props="SOURCE_CASCADER_PROPS"
                :show-all-levels="false"
                @update:model-value="(path) => handleOutputTypeChange(row, path)"
              />
              <el-button
                class="nd-row__expand"
                text
                :class="{ 'is-active': isDescExpanded(rowKeyOf(row)) }"
                :icon="FullScreen"
                :aria-label="isDescExpanded(rowKeyOf(row)) ? '收起描述' : '编辑描述'"
                :title="isDescExpanded(rowKeyOf(row)) ? '收起描述' : '编辑描述'"
                @click="toggleDesc(rowKeyOf(row))"
              />
              <el-button
                class="nd-row__remove"
                text
                :icon="Delete"
                aria-label="删除该变量"
                title="删除该变量"
                @click="removeOutputRow(index)"
              />
            </div>

            <div v-if="isDescExpanded(rowKeyOf(row))" class="nd-sub nd-sub--stack">
              <span class="nd-sub__label">描述</span>
              <el-input
                size="small"
                v-model="row.description"
                maxlength="200"
                placeholder="帮助大模型准确了解参数的作用"
              />
            </div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
    <!-- {{ meta }} -->
 </el-drawer>
</template>

<style scoped>

/* :deep(.el-cascader ) {
  width: 60px;
} */

:deep(.el-collapse-item__content) {
  padding:0px 12px 12px 12px;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
}

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

/* 删除节点：危险动作默认低调，悬停才转危险色，避免误点 */
.nd-head__remove {
  display: flex;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  font-size: 15px;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    background-color var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.nd-head__remove:hover {
  background: var(--tb-bg-sunken);
  color: var(--tb-danger);
}

:deep(.el-collapse-item__header) {
  padding: 0 12px;
}

.nd-panel__empty {
  margin: 0;
  padding: 2px 0;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

.nd-list {
  display: flex;
  flex-direction: column;
}

.nd-head-row {
  margin: 0 -8px;
  padding: 2px 8px 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.nd-grid--value {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr) 28px;
  align-items: center;
  gap: 8px;
}

.nd-row__name {
  min-width: 0;
}

/* 变量值列：上边类型短码，下边取值选择器 */
.nd-row__value {
  display: flex;
  min-width: 0;
  gap: 2px;
}

/* 类型短码：只读文字，不属于可编辑控件，所以不给边框/底色 */
.nd-row__type {
  text-align: center;
  width: 30px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-secondary);
}

/* ===== 输出段栅格：变量名 | 变量类型 | 描述展开 | 删除 ===== */
.nd-grid--plain {
  display: grid;
  /*
   * 类型列固定宽度：它只放一个短码（`str` / `[obj]`），再宽也读不出更多信息，
   * 却会把变量名列挤到显示不全（`student_categories` 这种长名很常见）。
   */
  grid-template-columns: minmax(0, 1fr) 92px 28px 28px;
  align-items: center;
  gap: 8px;
}

/* 变量行（输入 / 输出共用）：栅格下面还可能挂一行描述，所以整行纵向排列 */
.nd-row--item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  /* 左右负外边距抵消内边距：内容位置不变，hover 底色却能铺满整行 */
  margin: 0 -8px;
  padding: 6px 8px;
  border-bottom: var(--tb-border-width) dashed var(--tb-border);
  border-radius: var(--tb-radius-md);
  transition: background-color var(--tb-dur-2) var(--tb-ease-out);
}

.nd-row--item:hover,
.nd-row--item:focus-within {
  background: var(--tb-bg-raised);
}

/* 末行不画线，免得与分组底边线叠成双线 */
.nd-row--item:last-child {
  border-bottom-color: transparent;
}

/* 与迭代体子块产出不同名的输出行：与卡片上的告警色保持一套语义 */
.nd-row--warn {
  background: var(--tb-warning-soft);
}

/* 行内图标按钮：高度对齐同行的 small 输入框，图标才落在控件中线上 */
.nd-row__expand,
.nd-row__remove {
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

.nd-row__remove:hover {
  color: var(--tb-danger);
}

/* ===== 次级行：输出变量的描述 ===== */
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

/* ===== 分组标题：名称 + 数量胶囊 + 「＋」新增 ===== */
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

.nd-panel__count {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--tb-bg-sunken);
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: var(--tb-text-secondary);
}

/* 迭代节点的「输出与迭代体子块产出不同名」告警：只提示，不阻断编辑 */
.nd-group__warn {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--tb-warning-soft);
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  color: var(--tb-warning);
}

/* 「添加」：软底圆角方块 + 主色加号；高频入口用图标方块比「＋ 添加」省横向空间 */
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

.nd-group__add:hover {
  background: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.nd-group__add:focus-visible {
  outline: 2px solid var(--tb-ring);
  outline-offset: 1px;
}

</style>

<!--
  el-drawer 内部用 Teleport 挂到 body，父组件的 data-v-xxx 作用域属性不会落在抽屉 DOM 上，
  所以 scoped 里的 :deep() 选不中 .el-drawer__header，这里用非 scoped 样式 + 自定义类名限定范围。
-->
<style>
.nd-drawer .el-drawer__header {
  margin-bottom: 0px;
  padding: 12px;

}

.nd-drawer .el-drawer__body {
  padding: 0px;
}
</style>
