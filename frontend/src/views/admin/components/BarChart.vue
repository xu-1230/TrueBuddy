<script setup>
import { computed } from 'vue'
import { formatNumber } from '@/views/admin/utils/format'

/**
 * 轻量条形图（纯 CSS，不引入图表库）
 *
 * 统计页只需要「同一量纲横向比较」，用条形比饼图更易读；不引 echarts
 * 是为了避免为几张管理端统计图再加一个几百 KB 的依赖。
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  /** primary | success | warning | info | danger */
  tone: { type: String, default: 'primary' },
  valueSuffix: { type: String, default: '' },
  formatValue: { type: Function, default: null },
})

const maxValue = computed(() => {
  const values = props.items.map((item) => Number(item.value) || 0)
  return Math.max(1, ...values)
})

function percent(value) {
  return `${Math.max(2, (Number(value) || 0) / maxValue.value * 100)}%`
}

function displayValue(value) {
  return props.formatValue ? props.formatValue(value) : formatNumber(value)
}
</script>

<template>
  <div class="bar-chart" :class="`is-${tone}`">
    <div v-for="item in items" :key="item.label" class="bar-chart__row">
      <span class="bar-chart__label" :title="item.label">{{ item.label }}</span>
      <span class="bar-chart__track">
        <span class="bar-chart__fill" :style="{ width: percent(item.value) }" />
      </span>
      <span class="bar-chart__value">{{ displayValue(item.value) }}{{ valueSuffix }}</span>
    </div>
    <el-empty v-if="items.length === 0" description="暂无数据" :image-size="56" />
  </div>
</template>

<style scoped>
.bar-chart {
  --bar-tone: var(--tb-primary);

  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
}

.bar-chart.is-success {
  --bar-tone: var(--tb-success);
}

.bar-chart.is-warning {
  --bar-tone: var(--tb-warning);
}

.bar-chart.is-info {
  --bar-tone: var(--tb-info);
}

.bar-chart.is-danger {
  --bar-tone: var(--tb-danger);
}

.bar-chart__row {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr) 86px;
  align-items: center;
  gap: var(--tb-size-3);
}

.bar-chart__label {
  overflow: hidden;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bar-chart__track {
  display: block;
  height: 10px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-bg-raised);
  overflow: hidden;
}

.bar-chart__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: color-mix(in oklab, var(--bar-tone) 72%, var(--tb-bg-surface));
  transition: width var(--tb-dur-4) var(--tb-ease-out);
}

.bar-chart__value {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xs);
  font-variant-numeric: tabular-nums;
  text-align: right;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .bar-chart__row {
    grid-template-columns: 64px minmax(0, 1fr) 76px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bar-chart__fill {
    transition: none;
  }
}
</style>
