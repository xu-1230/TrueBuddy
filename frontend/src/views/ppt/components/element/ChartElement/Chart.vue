<template>
  <svg
    class="chart"
    :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
    preserveAspectRatio="xMidYMid meet"
    :style="chartStyle"
  >
    <!-- 背景 -->
    <rect
      v-if="options.bgColor && options.bgColor !== 'transparent'"
      :fill="options.bgColor"
      :width="chartWidth"
      :height="chartHeight"
    />

    <!-- 竖向柱状图（column） -->
    <template v-if="chartKind === 'column'">
      <!-- 网格线 -->
      <g v-for="(y, i) in gridLines" :key="'grid-' + i">
        <line :x1="plotLeft" :x2="plotRight" :y1="y" :y2="y" :stroke="gridColor" stroke-width="1" />
      </g>
      <g v-for="(series, si) in data.series" :key="'bar-series-' + si">
        <rect
          v-for="(value, vi) in series"
          :key="'bar-' + si + '-' + vi"
          :x="barX(si, vi)"
          :y="barY(si, vi)"
          :width="barWidth(si)"
          :height="barHeight(si, vi)"
          :fill="seriesColor(si)"
          rx="2"
        />
      </g>
      <!-- X 轴标签 -->
      <text
        v-for="(label, li) in data.labels"
        :key="'xlabel-' + li"
        :x="labelX(li)"
        :y="chartHeight - 8"
        text-anchor="middle"
        :fill="fontColor"
        font-size="12"
      >{{ label }}</text>
    </template>

    <!-- 横向条形图（bar） -->
    <template v-else-if="chartKind === 'bar'">
      <g v-for="(y, i) in gridLines" :key="'grid-' + i">
        <line :x1="plotLeft" :x2="plotRight" :y1="y" :y2="y" :stroke="gridColor" stroke-width="1" />
      </g>
      <g v-for="(series, si) in data.series" :key="'hbar-series-' + si">
        <rect
          v-for="(value, vi) in series"
          :key="'hbar-' + si + '-' + vi"
          :x="plotLeft"
          :y="hbarY(si, vi)"
          :width="hbarWidth(value)"
          :height="hbarHeight"
          :fill="seriesColor(si)"
          rx="2"
        />
      </g>
      <!-- Y 轴标签 -->
      <text
        v-for="(label, li) in data.labels"
        :key="'ylabel-' + li"
        :x="plotLeft - 6"
        :y="hbarY(0, li) + hbarHeight / 2 + 4"
        text-anchor="end"
        :fill="fontColor"
        font-size="12"
      >{{ label }}</text>
    </template>

    <!-- 折线图 / 雷达图（radar 降级为折线） -->
    <template v-else-if="chartKind === 'line'">
      <g v-for="(y, i) in gridLines" :key="'grid-' + i">
        <line :x1="plotLeft" :x2="plotRight" :y1="y" :y2="y" :stroke="gridColor" stroke-width="1" />
      </g>
      <g v-for="(series, si) in data.series" :key="'line-series-' + si">
        <polyline
          :points="linePoints(series)"
          :stroke="seriesColor(si)"
          stroke-width="3"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <circle
          v-for="(value, vi) in series"
          :key="'dot-' + si + '-' + vi"
          :cx="pointX(vi)"
          :cy="pointY(value)"
          :fill="seriesColor(si)"
          r="4"
        />
      </g>
      <text
        v-for="(label, li) in data.labels"
        :key="'xlabel-' + li"
        :x="pointX(li)"
        :y="chartHeight - 8"
        text-anchor="middle"
        :fill="fontColor"
        font-size="12"
      >{{ label }}</text>
    </template>

    <!-- 面积图（area） -->
    <template v-else-if="chartKind === 'area'">
      <g v-for="(y, i) in gridLines" :key="'grid-' + i">
        <line :x1="plotLeft" :x2="plotRight" :y1="y" :y2="y" :stroke="gridColor" stroke-width="1" />
      </g>
      <g v-for="(series, si) in data.series" :key="'area-series-' + si">
        <polygon
          :points="areaPoints(series)"
          :fill="seriesColor(si)"
          fill-opacity="0.25"
        />
        <polyline
          :points="linePoints(series)"
          :stroke="seriesColor(si)"
          stroke-width="2.5"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </g>
      <text
        v-for="(label, li) in data.labels"
        :key="'xlabel-' + li"
        :x="pointX(li)"
        :y="chartHeight - 8"
        text-anchor="middle"
        :fill="fontColor"
        font-size="12"
      >{{ label }}</text>
    </template>

    <!-- 饼图 / 环形图 -->
    <template v-else-if="chartKind === 'pie'">
      <g v-if="!isDonut">
        <path
          v-for="(slice, si) in pieSlices"
          :key="'pie-' + si"
          :d="describeArc(pieCenterX, pieCenterY, pieRadius, slice.start, slice.end)"
          :fill="slice.color"
          stroke="#fff"
          stroke-width="1"
        />
      </g>
      <g v-else>
        <circle
          v-for="(slice, si) in pieSlices"
          :key="'donut-' + si"
          :cx="pieCenterX"
          :cy="pieCenterY"
          :r="pieRadius"
          fill="none"
          :stroke="slice.color"
          :stroke-width="pieRadius * (options.pieDonutFactor || 0.5)"
          :stroke-dasharray="`${slice.fraction * donutCircumference} ${donutCircumference}`"
          :transform="`rotate(${slice.start} ${pieCenterX} ${pieCenterY})`"
        />
      </g>
    </template>

    <!-- 散点图（降级：显示占位提示） -->
    <template v-else>
      <text
        :x="chartWidth / 2"
        :y="chartHeight / 2"
        text-anchor="middle"
        :fill="fontColor"
        font-size="14"
      >散点图暂不支持</text>
    </template>

    <!-- 图例 -->
    <g v-if="showLegend && data.legends.length" transform="translate(8, 8)">
      <g v-for="(name, i) in data.legends" :key="'legend-' + i" :transform="`translate(0, ${i * 18})`">
        <rect :x="0" :y="2" width="12" height="12" :fill="seriesColor(i)" rx="2" />
        <text :x="18" :y="12" :fill="fontColor" font-size="12">{{ name }}</text>
      </g>
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    required: true,
  },
  data: {
    type: Object,
    required: true,
  },
  // 兼容：pptx 导入为 themeColors，旧数据为 themeColor
  themeColors: {
    type: Array,
    default: () => [],
  },
  themeColor: {
    type: Array,
    default: () => ['#5b9bd5', '#ed7d31', '#70ad47', '#ffc000', '#7030a0'],
  },
  gridColor: {
    type: String,
    default: '#ccc',
  },
  legend: {
    type: Boolean,
    default: true,
  },
  options: {
    type: Object,
    default: () => ({}),
  },
})

// 图表类型归一化：radar 降级为 line
const chartKind = computed(() => {
  if (props.type === 'radar') return 'line'
  return props.type
})

const isDonut = computed(() => props.type === 'ring' || !!props.options.pieDonut)

const showLegend = computed(() =>
  props.legend !== undefined && props.legend !== null ? props.legend : true,
)

const chartWidth = computed(() => props.options.width || 400)
const chartHeight = computed(() => props.options.height || 250)
const padding = computed(() => props.options.padding || 40)

const plotLeft = computed(() => padding.value)
const plotTop = computed(() => padding.value)
const plotRight = computed(() => chartWidth.value - padding.value)
const plotBottom = computed(() => chartHeight.value - padding.value - 5)
const plotHeight = computed(() => plotBottom.value - plotTop.value)
const plotWidth = computed(() => plotRight.value - plotLeft.value)

const fontColor = computed(() => props.options.fontColor || '#333')

const chartStyle = computed(() => ({
  width: '100%',
  height: 'auto',
  display: 'block',
}))

const seriesColor = (si) => {
  const colors = props.themeColors.length ? props.themeColors : props.themeColor
  return colors[si % colors.length]
}

// 统一数值：series 元素可能是对象 {y: xx}
const toNumber = (value) => {
  if (typeof value === 'number') return value
  return Number(value?.y ?? value) || 0
}

// 竖向柱状图布局
const maxValue = computed(() => {
  const all = props.data.series.flat().map(toNumber)
  return Math.max(...all, 1)
})

const groupWidth = computed(() => plotWidth.value / Math.max(props.data.labels.length, 1))
const seriesCount = computed(() => Math.max(props.data.series.length, 1))

const barWidth = () => {
  const totalBar = groupWidth.value * 0.62
  return totalBar / seriesCount.value
}
const barX = (si, vi) => {
  const groupCenter = plotLeft.value + groupWidth.value * vi + groupWidth.value / 2
  const totalBar = groupWidth.value * 0.62
  const startX = groupCenter - totalBar / 2
  return startX + barWidth(si) * si
}
const barH = (value) => (toNumber(value) / maxValue.value) * plotHeight.value
const barY = (si, vi) => plotBottom.value - barH(props.data.series[si][vi])
const barHeight = (si, vi) => barH(props.data.series[si][vi])

// 横向条形图布局
const rowH = computed(() => plotHeight.value / Math.max(props.data.labels.length, 1))
const hbarHeight = computed(() => (rowH.value * 0.6) / Math.max(props.data.series.length, 1))
const hbarY = (si, vi) =>
  plotTop.value + rowH.value * vi + rowH.value * 0.2 + hbarHeight.value * si
const hbarWidth = (value) => (toNumber(value) / maxValue.value) * plotWidth.value

// 网格线（4等分）
const gridLines = computed(() => {
  const lines = []
  for (let i = 0; i < 4; i++) {
    lines.push(plotTop.value + (plotHeight.value / 4) * i)
  }
  return lines
})

// X 轴标签位置（柱状图）
const labelX = (li) => plotLeft.value + groupWidth.value * li + groupWidth.value / 2

// 折线图
const pointX = (vi) => plotLeft.value + groupWidth.value * vi + groupWidth.value / 2
const pointY = (value) => plotBottom.value - (toNumber(value) / maxValue.value) * plotHeight.value
const linePoints = (series) => {
  return series.map((value, vi) => `${pointX(vi)},${pointY(value)}`).join(' ')
}
// 面积图：折线 + 底部闭合
const areaPoints = (series) => {
  const pts = series.map((value, vi) => `${pointX(vi)},${pointY(value)}`)
  pts.push(`${plotLeft.value + plotWidth.value},${plotBottom.value}`)
  pts.push(`${plotLeft.value},${plotBottom.value}`)
  return pts.join(' ')
}

// 饼图
const pieCenterX = computed(() => chartWidth.value / 2)
const pieCenterY = computed(() => chartHeight.value / 2)
const pieRadius = computed(() =>
  Math.min(chartWidth.value, chartHeight.value) / 2 - (isDonut.value ? 10 : 30),
)

const pieSlices = computed(() => {
  const values = (props.data.series[0] || []).map(toNumber)
  const total = values.reduce((a, b) => a + b, 0) || 1
  let angle = -90
  return values.map((value, i) => {
    const start = angle
    const end = angle + (value / total) * 360
    angle = end
    return {
      value,
      start,
      end,
      fraction: value / total,
      color: seriesColor(i),
    }
  })
})

const donutCircumference = computed(() => 2 * Math.PI * pieRadius.value)

const polarToCartesian = (cx, cy, r, angleDeg) => {
  const angleRad = ((angleDeg - 90) * Math.PI) / 180
  return {
    x: cx + r * Math.cos(angleRad),
    y: cy + r * Math.sin(angleRad),
  }
}

const describeArc = (cx, cy, r, startAngle, endAngle) => {
  const start = polarToCartesian(cx, cy, r, endAngle)
  const end = polarToCartesian(cx, cy, r, startAngle)
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1'
  return `M ${cx} ${cy} L ${start.x} ${start.y} A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y} Z`
}
</script>