<script setup>
/**
 * 变量类型选择器（Element Plus 级联选择器 `el-cascader-panel`）。
 *
 * 触发器是取值段里的紧凑短码，方括号只用来标记「数组」这一维：
 * 标量直接写短码（`str` / `int` / `bool` …），数组写成 `[元素类型短码]`（`[str]` 即 `Array<String>`）。
 *
 * 展开后是级联面板：一级为完整类型名，`Array` 带一级元素类型 —— 悬停展开、只有叶子可选中，
 * 所以一次点击就同时定下 `type` 与 `schema.type`，不会出现「改了类型忘了改元素类型」。
 *
 * 为什么用 `el-cascader-panel` 而不是 `el-cascader`：取值段是「类型 + 取值 + 选择按钮」的
 * 分段控件，触发器必须由调用方掌控（短码 + 无描边），而 `el-cascader` 自带输入框与标签拼接。
 * 面板本身仍由 Element Plus 提供，交互与原生级联一致。
 *
 * 组件只负责「选了什么」：把结果以 `{ type, itemType }` 交给调用方回写。
 */
import { computed, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { displayTypeOf, shortTypeCodeOf } from './utils/variable-type'

const props = defineProps({
  /** 当前类型（后端口径：string|integer|number|boolean|list|object|file） */
  type: { type: String, default: 'string' },
  /** 列表元素类型（`schema.type`），仅 type === 'list' 时有意义 */
  itemType: { type: String, default: 'string' },
  /** 独立使用（如输出行的类型列）：加描边与底色，观感与 el-select 一致 */
  bordered: { type: Boolean, default: false },
  /**
   * 只允许标量类型。给「列表的元素类型」这类不允许再嵌套的场景用 ——
   * 它本身就是数组的下一层，再出现 Array 会表达成 `Array<Array<…>>`，而 schema 只存一层。
   */
  leafOnly: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['pick'])

/** 叶子类型：与后端 schema 的 type 一一对应；list 不在此列，它由 Array 的子级表达 */
const SCALAR_TYPES = ['string', 'integer', 'number', 'boolean', 'object', 'file']

/** 完整类型名统一走 displayTypeOf，避免这里再抄一份映射 */
const toOption = (value) => ({ value, label: displayTypeOf({ type: value }) })

/** 级联面板选项：leafOnly 时只有标量；否则在末尾追加带元素类型子级的 Array */
const panelOptions = computed(() =>
  props.leafOnly
    ? SCALAR_TYPES.map(toOption)
    : [...SCALAR_TYPES.map(toOption), { value: 'list', label: 'Array', children: SCALAR_TYPES.map(toOption) }],
)

/** 悬停即展开子级；emitPath 保留完整路径，才好区分「标量」与「数组 + 元素」 */
const PANEL_PROPS = { expandTrigger: 'hover', emitPath: true }

const open = ref(false)

/** 已选元素类型；未声明时按 string 处理（与 displayTypeOf 的兜底一致） */
const activeItemType = computed(() => props.itemType || 'string')

/** 面板选中路径：数组是两级，标量一级 */
const panelValue = computed(() =>
  props.type === 'list' ? ['list', activeItemType.value] : [props.type],
)

/** 触发器短码：括号只标记数组，里面写元素类型短码（与节点卡片胶囊同源） */
const code = computed(() =>
  shortTypeCodeOf({ type: props.type, schema: { type: activeItemType.value } }),
)

/** 悬浮提示给完整类型名，把方括号的读法补全 */
const typeTitle = computed(() =>
  displayTypeOf(
    props.type === 'list' ? { type: 'list', schema: { type: activeItemType.value } } : props,
  ),
)

/** 面板回调：路径长度 2 表示选的是数组的元素类型 */
const handleChange = (value) => {
  const path = Array.isArray(value) ? value : [value]
  if (path.length > 1) {
    emit('pick', { type: 'list', itemType: path[path.length - 1] })
  } else {
    emit('pick', { type: path[0] })
  }
  open.value = false
}
</script>

<template>
  <div class="var-type" :class="{ 'var-type--bordered': bordered }">
    <el-popover
      :visible="open"
      trigger="click"
      placement="bottom-start"
      :offset="6"
      :width="260"
      :disabled="disabled"
      popper-class="var-type__popper"
      @update:visible="open = $event"
    >
      <template #reference>
        <button
          type="button"
          class="var-type__trigger"
          :disabled="disabled"
          :aria-label="`变量类型：${typeTitle}`"
          :title="`变量类型：${typeTitle}`"
        >
          <span class="var-type__code">{{ code }}</span>
          <el-icon class="var-type__arrow"><ArrowDown /></el-icon>
        </button>
      </template>

      <!-- 每次展开都重建面板：保证按最新类型重新定位到已选项 -->
      <el-cascader-panel
        v-if="open"
        :model-value="panelValue"
        :options="panelOptions"
        :props="PANEL_PROPS"
        @change="handleChange"
      />
    </el-popover>
  </div>
</template>

<style scoped>
/* ===== 触发器：取值段最左的类型段，高度与取值区对齐 ===== */
.var-type {
  display: flex;
  min-width: 0;
}

.var-type__trigger {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  /* 30px：外层分段控件是 border-box 的 32px，内边 1px 后内容区正好 30px */
  min-height: 30px;
  /* 短码靠左、箭头贴右：与取值段的下拉箭头形成一致的「选择器」观感 */
  justify-content: space-between;
  gap: 6px;
  padding: 0 10px;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  color: var(--tb-text-primary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-ease-out),
    background-color var(--tb-dur-2) var(--tb-ease-out);
}

.var-type__trigger:hover:not(:disabled),
.var-type__trigger:focus-visible {
  background: var(--tb-bg-raised);
  color: var(--tb-primary);
}

.var-type__trigger:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.var-type__code {
  overflow: hidden;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.var-type__arrow {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--tb-text-tertiary);
}

/* ===== 独立使用（输出行的类型列）：像 el-select 一样自带描边 ===== */
.var-type--bordered .var-type__trigger {
  gap: 8px;
  /* 独立使用时邻居是 el-input（32px），这里也要 32px 才齐平 */
  min-height: 32px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
}

.var-type--bordered .var-type__trigger:hover:not(:disabled) {
  border-color: var(--tb-primary);
  color: var(--tb-text-primary);
}

@media (prefers-reduced-motion: reduce) {
  .var-type__trigger {
    transition: none;
  }
}
</style>

<style>
/* 级联面板自己就是一张白色浮层：去掉 popover 的默认留白，并让面板铺满弹层宽度 */
.var-type__popper.el-popover.el-popper {
  min-width: 0;
  padding: 0;
  overflow: hidden;
  border-radius: var(--tb-radius-md);
}

.var-type__popper .el-cascader-panel {
  width: 100%;
}

/* 单列时铺满、双列时均分，避免展开子级后面板宽度跳变 */
.var-type__popper .el-cascader-menu {
  min-width: 0;
  flex: 1;
}

.var-type__popper .el-cascader-menu:last-child {
  border-right: none;
}

/* 下拉不限高：菜单容器默认写死 height: 204px，类型项多了会出现内部滚动条 */
.var-type__popper .el-cascader-menu__wrap.el-scrollbar__wrap {
  height: auto;
  max-height: none;
}
</style>
