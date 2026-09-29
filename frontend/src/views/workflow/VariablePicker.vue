<script setup>
/**
 * 取值选择：Element Plus 级联选择器（一级 = 来源节点，二级 = 该节点的输出变量）。
 *
 * 直接用 `el-cascader`，不再自造触发器与弹层：
 * - **不可编辑**：不设 `filterable`，输入框只读 —— 取值只能从上游变量里选，不能手敲；
 * - **可清除**：`clearable` + `CloseBold` 图标，清除即回到空态；
 * - 悬停展开子级，叶子值就是 `refKey`（`{nodeId}.{变量名}`）。
 *
 * 候选集由后端按「反向可达闭包 + 循环作用域」给出，即「一定能先于本节点执行」的节点所声明的输出，
 * 所以这里选出来的变量不会触发执行侧的「引用必须来自上游」校验。
 *
 * 取值模型与抽屉的既有约定保持一致：
 * - `{ mode: 'ref', refKey }` 选中某个上游变量；
 * - `{ mode: 'literal', refKey: null, literal: '' }` 空态（清除选择后的状态）。
 */
import { computed } from 'vue'
import { CloseBold } from '@element-plus/icons-vue'
import { displayTypeOf } from './utils/variable-type'

defineOptions({ name: 'VariablePicker' })

const props = defineProps({
  /** { mode: 'ref' | 'literal', refKey: string | null, literal: string } */
  modelValue: {
    type: Object,
    default: () => ({ mode: 'literal', refKey: null, literal: '' }),
  },
  /** 上游可见变量（由本地作用域索引产出） */
  options: { type: Array, default: () => [] },
  /** 候选集加载中：加载完成前不给可选项，避免误选到还没算出来的变量 */
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '选择变量' },
  /**
   * 取值校验失败（未选变量 / 引用失效）：红框画在取值字段**自身**上。
   * 校验口径由抽屉的 `inputErrorOf` 给出，这里只负责呈现 —— 因为 Element 在 hover / focus
   * 时会把 `.el-input__wrapper` 的内嵌描边重新画回来，只有在本组件内才能按状态压住它。
   */
  error: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

/** 悬停即展开子级；emitPath 保留完整路径，叶子值就是 refKey */
const PANEL_PROPS = { expandTrigger: 'hover', emitPath: true }

/** 级联树：一级来源节点，二级该节点声明的输出变量 */
const optionTree = computed(() => {
  if (props.loading) return []
  const nodes = new Map()
  for (const item of props.options) {
    if (!nodes.has(item.nodeId)) {
      nodes.set(item.nodeId, {
        value: item.nodeId,
        label: item.nodeTitle || item.nodeId || '未命名节点',
        children: [],
      })
    }
    nodes.get(item.nodeId).children.push({
      value: item.refKey,
      label: item.name,
      /** 叶子上的类型：面板右侧的类型标签用它 */
      varType: item.type,
    })
  }
  return [...nodes.values()]
})

/**
 * 当前选中路径：已解析的引用取「来源节点 + 变量」；
 * 失效引用（来源节点被删 / 断线 / 改名）退化到只定位来源节点，配合抽屉的红边与「引用变量不存在」。
 */
const panelValue = computed(() => {
  const refKey = props.modelValue?.mode === 'ref' ? props.modelValue.refKey : ''
  if (!refKey) return []
  const option = props.options.find((item) => item.refKey === refKey)
  if (option) return [option.nodeId, option.refKey]
  const nodeId = refKey.slice(0, refKey.indexOf('.'))
  return nodeId ? [nodeId] : []
})

/** 选中叶子即回写引用；路径末位就是 refKey */
const handleChange = (value) => {
  const path = Array.isArray(value) ? value : [value]
  const refKey = path[path.length - 1]
  if (!props.options.some((item) => item.refKey === refKey)) return
  emit('update:modelValue', { mode: 'ref', refKey, literal: '' })
}

/** 清除选择：回到空态，同时清掉旧固定值，避免留下界面上看不见的取值 */
const handleClear = () => {
  emit('update:modelValue', { mode: 'literal', refKey: null, literal: '' })
}
</script>

<template>
  <!--
    外面这层 div 是必需的样式挂点，不是装饰：
    `el-cascader` 的根节点是它内部的 ElTooltip，真正带 `.el-cascader` 的元素由插槽渲染，
    父组件的 scope id 只落到「子组件根节点」，所以 `.var-cascader :deep(...)` 会整体失效，
    抽屉传进来的 class（flex 布局）也没有确定的落点。包一层自有元素即可两者兼顾。
  -->
  <div class="var-cascader" :class="{ 'is-error': error }">
    <el-cascader
      :model-value="panelValue"
      :options="optionTree"
      :props="PANEL_PROPS"
      :disabled="disabled"
      :placeholder="loading ? '正在计算上游可见变量…' : placeholder"
      popper-class="var-cascader__popper"
      clearable
      :clear-icon="CloseBold"
      @change="handleChange"
      @clear="handleClear"
    >
      <template #default="{ data }">
        <span class="var-cascader__node">
          <span class="var-cascader__node-name">{{ data.label }}</span>
          <span v-if="data.varType" class="var-cascader__node-type">
            {{ displayTypeOf({ type: data.varType }) }}
          </span>
        </span>
      </template>
      <template #empty>
        <p class="var-cascader__empty">
          {{
            loading
              ? '正在计算上游可见变量…'
              : options.length
                ? '没有可引用的上游变量。'
                : '本节点没有可引用的上游变量，请先把上游节点连到本节点。'
          }}
        </p>
      </template>
    </el-cascader>
  </div>
</template>

<style scoped>
/* 样式挂点：同时承担「分段控件里的一段」的弹性宽度 */
.var-cascader {
  display: flex;
  flex: 1;
  min-width: 0;
}

/* flex 布局靠 :deep 落到 el-cascader 根上，不依赖 attrs 透传的落点 */
.var-cascader :deep(.el-cascader) {
  flex: 1;
  min-width: 0;
  /* 根元素 Element 默认 line-height: 32px，会把行盒撑到 32px，取值段就回到 32px 而不是 30px */
  line-height: normal;
}

/* 嵌在分段控件里：剥掉 el-cascader 自带的描边与底色，只留取值区本身 */
.var-cascader :deep(.el-input__wrapper) {
  /*
   * 去掉 Element 默认的 1px 上下内边距 —— 取值段要正好 30px：
   * 外层分段控件是 border-box 的 32px，1px×2 边框之后内容区只剩 30px。
   */
  padding: 0 10px;
  background: transparent;
}

/*
 * 无错时内部全程不画描边。必须逐个状态覆盖：Element 在 hover / focus 会把内嵌描边重新画回来，
 * 且权重比基础态高一级（(0,4,0) vs (0,3,0)）：
 *   .el-cascader:not(.is-disabled):hover .el-input__wrapper
 *   .el-cascader .el-input.is-focus .el-input__wrapper
 * 取值段的悬停 / 聚焦反馈统一交给外层分段控件（.var-row__value 的描边与聚焦光环）。
 * 用 :not(.is-error) 把错误态排除在外，避免与下面的红框规则互相压制。
 */
.var-cascader:not(.is-error) :deep(.el-input__wrapper),
.var-cascader:not(.is-error) :deep(.el-input__wrapper:hover),
.var-cascader:not(.is-error) :deep(.el-input__wrapper.is-focus),
.var-cascader:not(.is-error) :deep(.el-input__wrapper:focus-within),
.var-cascader:not(.is-error) :deep(.el-cascader:hover .el-input__wrapper),
.var-cascader:not(.is-error) :deep(.el-cascader .el-input.is-focus .el-input__wrapper) {
  box-shadow: none;
}

/*
 * 校验失败：红框画在取值字段**自身**上 —— 错误说的是「取值」，左侧类型段不该被一起染红。
 * 同样列全各状态，保证悬停 / 聚焦时红框不会被打回默认描边。
 */
.var-cascader.is-error :deep(.el-input__wrapper),
.var-cascader.is-error :deep(.el-input__wrapper:hover),
.var-cascader.is-error :deep(.el-input__wrapper.is-focus),
.var-cascader.is-error :deep(.el-input__wrapper:focus-within),
.var-cascader.is-error :deep(.el-cascader:hover .el-input__wrapper),
.var-cascader.is-error :deep(.el-cascader .el-input.is-focus .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--tb-danger) inset;
}

.var-cascader :deep(.el-input__inner) {
  font-size: 12px;
}

/* 下拉叶子：变量名 + 类型标签（节点层只有名字） */
.var-cascader__node {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.var-cascader__node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.var-cascader__node-type {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 3px;
  background: var(--tb-bg-sunken);
  font-size: 10px;
  color: var(--tb-text-secondary);
}

.var-cascader__empty {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--tb-text-tertiary);
}
</style>

<style>
/* 下拉不限高：菜单容器默认写死 height: 204px，变量多时会出现内部滚动条 */
.var-cascader__popper .el-cascader-menu__wrap.el-scrollbar__wrap {
  height: auto;
  max-height: none;
}
</style>
