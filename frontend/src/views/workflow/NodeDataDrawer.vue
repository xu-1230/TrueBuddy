<script setup>
/**
 * 节点数据抽屉。
 *
 * 画布上点中任意节点后打开，按节点自身的 data 结构布局出可编辑的表单：
 * 基本信息（id / 类型 / 名称 / 坐标）、变量分组（inputs / outputs / blocks）、
 * 迭代节点的内部节点列表、以及只读的原始 JSON。
 *
 * 数据流向刻意保持单一：抽屉只持有「草稿」，点保存时通过 updateNode 写回 Vue Flow store，
 * 节点卡片与连线会随 store 自动更新（绘制数据请一律以 store 为准）。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useVueFlow } from '@vue-flow/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Delete, Plus, RefreshLeft } from '@element-plus/icons-vue'

defineOptions({ name: 'NodeDataDrawer' })

const props = defineProps({
  visible: { type: Boolean, default: false },
  nodeId: { type: String, default: null },
})

const emit = defineEmits(['update:visible', 'select'])

const { getNodes, updateNode, removeNodes } = useVueFlow()

/* ===== 常量 ===== */

/** 节点类型 → 中文名 */
const NODE_TYPE_LABELS = {
  start: '开始节点',
  input: '输入节点',
  output: '结束节点',
  llm: '大模型节点',
  iteration: '迭代节点',
  'iteration-start': '迭代起点',
  'iteration-end': '迭代终点',
}

/** 变量类型（与后端 schema 里的 type 取值一致） */
const TYPE_LABELS = {
  string: '字符串',
  integer: '整数',
  number: '数字',
  boolean: '布尔',
  list: '列表',
  object: '对象',
  file: '文件',
}

const TYPE_OPTIONS = Object.keys(TYPE_LABELS)

/** 节点 data 里可编辑的变量分组 */
const VARIABLE_GROUPS = [
  { key: 'inputs', label: '输入变量' },
  { key: 'outputs', label: '输出变量' },
  { key: 'blocks', label: '数据块' },
]

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

const nodeLabel = computed(() => node.value?.data?.label || node.value?.id || '未选择节点')

const nodeTypeName = computed(() => NODE_TYPE_LABELS[node.value?.type] ?? node.value?.type ?? '未知类型')

const isIteration = computed(() => node.value?.type === 'iteration')

/** 迭代节点内部的子节点（含自带的起止节点） */
const childNodes = computed(() => {
  if (!isIteration.value || !liveNode.value) return []
  return getNodes.value.filter((item) => item.parentNode === liveNode.value.id)
})

/** 画布绝对坐标 */
const canvasPosition = computed(() => {
  const position = node.value?.computedPosition ?? node.value?.position ?? { x: 0, y: 0 }
  return { x: Math.round(position.x), y: Math.round(position.y) }
})

/** 当前节点 data 里真实存在的变量分组 */
const groups = computed(() =>
  VARIABLE_GROUPS.filter((group) => Array.isArray(node.value?.data?.[group.key])),
)

const rawJson = computed(() => {
  const current = node.value
  if (!current) return ''
  const { id, type, position, parentNode, width, height, zIndex, data } = current
  return JSON.stringify({ id, type, position, parentNode, width, height, zIndex, data }, null, 2)
})

/* ===== 草稿 ===== */

const draft = reactive({
  label: '',
  x: 0,
  y: 0,
  /** { inputs: [], outputs: [], blocks: [] } */
  groups: {},
})

/** 上一次保存 / 初始化时的快照，用来判断是否有未保存的修改 */
const cleanSnapshot = ref('')

const serializeDraft = () =>
  JSON.stringify({ label: draft.label, x: draft.x, y: draft.y, groups: draft.groups })

const dirty = computed(() => Boolean(node.value) && serializeDraft() !== cleanSnapshot.value)

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

const initDraft = () => {
  const current = liveNode.value
  draft.groups = {}
  for (const group of VARIABLE_GROUPS) {
    const list = current?.data?.[group.key]
    draft.groups[group.key] = Array.isArray(list) ? list.map(cloneVariable) : []
  }
  if (!current) {
    draft.label = ''
    draft.x = 0
    draft.y = 0
    cleanSnapshot.value = serializeDraft()
    return
  }
  draft.label = current.data?.label ?? ''
  draft.x = Math.round(current.position?.x ?? 0)
  draft.y = Math.round(current.position?.y ?? 0)
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

// 节点被拖走后同步坐标（抽屉是模态的，正常不会发生，这里只做兜底）
watch(
  () => [liveNode.value?.position?.x, liveNode.value?.position?.y],
  ([x, y]) => {
    if (!liveNode.value) return
    if (typeof x === 'number') draft.x = Math.round(x)
    if (typeof y === 'number') draft.y = Math.round(y)
  },
)

/* ===== 变量编辑 ===== */

const addVariable = (key) => {
  draft.groups[key].push({ name: '', type: 'string', description: '' })
}

const removeVariable = (key, index) => {
  draft.groups[key].splice(index, 1)
}

/** 切到 list 时补一个元素 schema，切走时清掉，避免脏数据 */
const handleTypeChange = (item) => {
  if (item.type === 'list') {
    if (!item.schema) item.schema = { type: 'string', description: '' }
  } else {
    delete item.schema
  }
}

/* ===== 保存 / 删除 ===== */

const handleSave = () => {
  const current = liveNode.value
  if (!current) return

  const label = draft.label.trim()
  const data = { ...current.data, label }
  let dropped = 0

  for (const group of VARIABLE_GROUPS) {
    const existed = Array.isArray(current.data?.[group.key])
    const list = draft.groups[group.key] ?? []
    if (!existed && !list.length) continue

    const items = []
    for (const item of list) {
      const name = (item.name ?? '').trim()
      // 名称为空的变量在节点上是不可见的，保存时直接丢掉
      if (!name) {
        dropped += 1
        continue
      }
      const next = { ...item, name, description: item.description ?? '' }
      next.type = next.type || 'string'
      if (next.type === 'list') {
        next.schema = {
          type: next.schema?.type || 'string',
          description: next.schema?.description ?? '',
        }
      } else {
        delete next.schema
      }
      items.push(next)
    }
    draft.groups[group.key] = items
    data[group.key] = items.map(cloneVariable)
  }

  draft.label = label
  const position = { x: Math.round(draft.x), y: Math.round(draft.y) }
  const patch = { data }
  const currentPosition = {
    x: Math.round(current.position?.x ?? 0),
    y: Math.round(current.position?.y ?? 0),
  }
  if (position.x !== currentPosition.x || position.y !== currentPosition.y) {
    patch.position = position
  }

  updateNode(current.id, patch)
  cleanSnapshot.value = serializeDraft()
  ElMessage.success(
    dropped ? `已保存（${dropped} 个未命名变量被忽略）` : `已保存「${label || current.id}」`,
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
    size="460px"
    append-to-body
    class="node-drawer"
    @update:model-value="handleVisibleChange"
  >
    <template #header>
      <div class="node-drawer__header">
        <span class="node-drawer__badge">{{ nodeTypeName }}</span>
        <span class="node-drawer__title" :title="nodeLabel">{{ nodeLabel }}</span>
        <span v-if="dirty" class="node-drawer__dirty">未保存</span>
      </div>
    </template>

    <div v-if="!node" class="node-drawer__empty">节点已不存在</div>

    <div v-else class="node-drawer__body">
      <!-- 基本信息 -->
      <section class="panel">
        <h4 class="panel__title">基本信息</h4>

        <div class="field field--row">
          <div class="field__item">
            <label class="field__label">节点 ID</label>
            <el-input :model-value="node.id" disabled />
          </div>
          <div class="field__item">
            <label class="field__label">节点类型</label>
            <el-input :model-value="nodeTypeName" disabled />
          </div>
        </div>

        <div class="field">
          <label class="field__label">节点名称</label>
          <el-input
            v-model="draft.label"
            maxlength="40"
            show-word-limit
            placeholder="请输入节点名称"
          />
        </div>

        <div class="field field--row">
          <div class="field__item">
            <label class="field__label">坐标 X</label>
            <el-input-number v-model="draft.x" :step="10" controls-position="right" />
          </div>
          <div class="field__item">
            <label class="field__label">坐标 Y</label>
            <el-input-number v-model="draft.y" :step="10" controls-position="right" />
          </div>
        </div>

        <p class="field__hint">
          {{
            node.parentNode
              ? '该节点位于父节点内部，坐标是相对父节点的偏移量。'
              : `画布坐标：X ${canvasPosition.x} · Y ${canvasPosition.y}`
          }}
        </p>
      </section>

      <!-- 变量分组：inputs / outputs / blocks -->
      <section v-for="group in groups" :key="group.key" class="panel">
        <div class="panel__head">
          <h4 class="panel__title">
            {{ group.label }}
            <span class="panel__count">{{ draft.groups[group.key].length }}</span>
          </h4>
          <el-button text type="primary" :icon="Plus" @click="addVariable(group.key)">添加</el-button>
        </div>

        <p v-if="!draft.groups[group.key].length" class="panel__empty">
          暂无{{ group.label }}，点击右上角「添加」新增一项。
        </p>

        <div
          v-for="(item, index) in draft.groups[group.key]"
          :key="`${group.key}-${index}`"
          class="var-card"
        >
          <div class="var-card__head">
            <span class="var-card__name">{{ group.label }} {{ index + 1 }}</span>
            <el-button text type="danger" :icon="Delete" @click="removeVariable(group.key, index)" />
          </div>

          <div class="field field--row">
            <div class="field__item">
              <label class="field__label">名称</label>
              <el-input v-model="item.name" placeholder="变量名，如 USER_INPUT" />
            </div>
            <div class="field__item">
              <label class="field__label">类型</label>
              <el-select v-model="item.type" @change="handleTypeChange(item)">
                <el-option
                  v-for="option in TYPE_OPTIONS"
                  :key="option"
                  :label="`${TYPE_LABELS[option]}（${option}）`"
                  :value="option"
                />
              </el-select>
            </div>
          </div>

          <div class="field">
            <label class="field__label">描述</label>
            <el-input
              v-model="item.description"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="变量说明，会显示在节点提示里"
            />
          </div>

          <div v-if="item.type === 'list' && item.schema" class="field field--row var-card__schema">
            <div class="field__item">
              <label class="field__label">元素类型</label>
              <el-select v-model="item.schema.type">
                <el-option
                  v-for="option in TYPE_OPTIONS"
                  :key="option"
                  :label="TYPE_LABELS[option]"
                  :value="option"
                />
              </el-select>
            </div>
            <div class="field__item">
              <label class="field__label">元素描述</label>
              <el-input v-model="item.schema.description" placeholder="列表元素的说明" />
            </div>
          </div>
        </div>
      </section>

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

      <!-- 原始数据 -->
      <section class="panel">
        <el-collapse>
          <el-collapse-item title="原始数据（JSON）" name="raw">
            <pre class="raw-json">{{ rawJson }}</pre>
          </el-collapse-item>
        </el-collapse>
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
/* ===== 抽屉头部 ===== */
.node-drawer__header {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.node-drawer__badge {
  flex-shrink: 0;
  padding: 0 8px;
  border-radius: 6px;
  background: var(--tb-bg-sunken);
  font-size: 12px;
  line-height: 20px;
  color: var(--tb-text-secondary);
}

.node-drawer__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  color: var(--tb-text-primary);
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
  gap: 12px;
}

.node-drawer__empty {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

.panel {
  padding: 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 10px;
  background: var(--tb-bg-surface);
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
  padding: 8px;
  border: var(--tb-border-width) dashed var(--tb-border);
  border-radius: 8px;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* ===== 字段 ===== */
.field + .field,
.field + .field--row,
.var-card + .var-card {
  margin-top: 10px;
}

.field--row {
  display: flex;
  gap: 8px;
}

.field__item {
  flex: 1;
  min-width: 0;
}

.field__label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--tb-text-secondary);
}

.field__hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}

.field__item :deep(.el-input-number),
.field__item :deep(.el-select) {
  width: 100%;
}

/* ===== 变量卡片 ===== */
.var-card {
  padding: 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 8px;
  background: var(--tb-bg-raised);
}

.var-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.var-card__name {
  font-size: 12px;
  color: var(--tb-text-secondary);
}

.var-card__schema {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--tb-border);
}

/* ===== 内部节点 ===== */
.child-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 6px;
  padding: 6px 10px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 8px;
  background: var(--tb-bg-raised);
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.child-row:last-child {
  margin-bottom: 0;
}

.child-row:hover {
  border-color: var(--tb-primary);
  background: var(--tb-primary-soft);
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

/* ===== 原始数据 ===== */
.raw-json {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  padding: 8px;
  border-radius: 6px;
  background: var(--tb-bg-sunken);
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-secondary);
}

:deep(.el-collapse),
:deep(.el-collapse-item__header),
:deep(.el-collapse-item__wrap) {
  border: none;
}

/* ===== 抽屉底部 ===== */
.node-drawer__footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-drawer__spacer {
  flex: 1;
}
</style>
