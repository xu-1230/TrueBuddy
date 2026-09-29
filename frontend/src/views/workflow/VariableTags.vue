<script setup>
/**
 * 节点卡片上的变量标签行（输入 / 输出共用）。
 *
 * 四种卡片（开始 / 结束 / 大模型 / 人工确认）的变量声明都走这一份实现，标签统一是
 * 「[类型短码] 变量名」的胶囊：不再各组件各写一套 `node-var-tag` 样式，也就不会出现
 * 「同一个变量在开始节点是纯文字、在大模型节点是胶囊」这类不一致 —— 这正是本组件存在的理由。
 *
 * 类型短码与抽屉里的类型选择器同源（`shortTypeCodeOf`）：数组统一写成 `[str]` 这种带方括号的读法。
 *
 * 告警态（`undefinedNames`）只对「声明 ≠ 能取到值」的节点有意义：大模型节点的输入要
 * backendInputs 里绑定了取值才算定义好，其余节点没有这个概念，不传即全部按正常态渲染。
 */
import { computed } from 'vue'
import { shortTypeCodeOf } from './utils/variable-type'

defineOptions({ name: 'VariableTags' })

const props = defineProps({
  /** 行首标签（输入 / 输出） */
  label: { type: String, default: '' },
  /** 变量声明数组：兼容字符串写法与 { name, type, schema?, description? } 对象 */
  variables: { type: Array, default: () => [] },
  /** 未绑定取值的变量名集合（`Set<string>`）：命中的胶囊用告警色提示「尚未指定取值」 */
  undefinedNames: { type: Object, default: () => new Set() },
  /**
   * 是否为本节点变量区的最后一行。
   *
   * 卡片底边留 12px、行与行之间只留 8px —— 只有末行会用到 12px。抽成组件后没法再用
   * `.node-vars:last-of-type` 判断（是不是末行只有父组件知道），因此由调用方显式指定。
   */
  last: { type: Boolean, default: true },
})

/** 变量列表：字符串写法补成对象；名称为空的声明在卡片上不可见，直接过滤 */
const items = computed(() =>
  (props.variables ?? [])
    .map((item) => (typeof item === 'string' ? { name: item } : item))
    .filter((item) => item?.name),
)

const isUndefined = (name) => props.undefinedNames.has(name)

/** 悬浮提示：描述优先；告警态补一句原因，否则光标停上去是个空提示 */
const titleOf = (item) =>
  item.description || (isUndefined(item.name) ? '尚未指定取值，运行时取不到值' : '')
</script>

<template>
  <div v-if="items.length" class="node-inputs" :class="{ 'node-inputs--last': last }">
    <span class="node-inputs-label">{{ label }}</span>
    <div class="node-input-tags">
      <span
        v-for="item in items"
        :key="item.name"
        class="node-input-tag"
        :class="{ 'is-undefined': isUndefined(item.name) }"
        :title="titleOf(item)"
      >
        <span class="node-input-tag__type">{{ shortTypeCodeOf(item) }}</span>
        <span class="node-input-tag__name">{{ item.name }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
/* ===== 变量行：行首标签 + 可换行的胶囊列表 ===== */
.node-inputs {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  /* 非末行 8px 行距；末行由 .node-inputs--last 补足卡片底边留白 */
  padding: 0 12px 8px;
}

.node-inputs--last {
  padding-bottom: 12px;
}

.node-inputs-label {
  flex-shrink: 0;
  font-size: 13px;
  line-height: 22px;
  color: var(--tb-text-tertiary);
}

.node-input-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* ===== 变量胶囊：[类型短码] 变量名 ===== */
.node-input-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 6px;
  background: var(--tb-bg-sunken);
  font-size: 13px;
  line-height: 22px;
  color: var(--tb-text-primary);
}

/* 类型短码比变量名更轻，形成「[str] input」的主次关系 */
.node-input-tag__type {
  font-size: 11px;
  font-weight: 400;
  color: var(--tb-text-tertiary);
}

.node-input-tag__name {
  font-weight: 500;
}

/* 未绑定取值：橙底 + 橙字，与抽屉里「未定义」的调性保持一致 */
.node-input-tag.is-undefined {
  border-color: var(--tb-warning);
  background: var(--tb-warning-soft);
  color: var(--tb-warning);
}

.node-input-tag.is-undefined .node-input-tag__type {
  color: var(--tb-warning);
  opacity: 0.75;
}
</style>
