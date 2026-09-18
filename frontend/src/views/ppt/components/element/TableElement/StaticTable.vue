<template>
  <table class="static-table" :style="tableStyle">
    <colgroup>
      <col
        v-for="(col, index) in elementInfo.colWidths"
        :key="index"
        :style="{
          width: col * elementInfo.width + 'px',
        }"
      />
    </colgroup>
    <tbody>
      <tr v-for="(row, r) in elementInfo.data" :key="r">
        <td
          v-for="(cell, c) in row"
          :key="'cell-' + r + '-' + c"
          :style="cellStyle(r, c)"
          :colspan="cell.colspan"
          :rowspan="cell.rowspan"
          :class="{ 'hidden-cell': hiddenCells.has(r + '-' + c) }"
        >
          <span :style="textStyle(cell)" v-html="formatText(cell.text)"></span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<script setup>
import { computed } from 'vue'
import { getTextStyle, formatText } from './utils'

const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})

// 表格主题色（含行/列头背景等配色）
const subThemeColors = computed(() => {
  const theme = props.elementInfo.theme || {}
  const color = theme.color || '#5b9bd5'
  const hexToRgba = (hex, alpha) => {
    let _hex = String(hex).replace('#', '')
    if (_hex.length === 3) _hex = _hex.split('').map(c => c + c).join('')
    const r = parseInt(_hex.substring(0, 2), 16)
    const g = parseInt(_hex.substring(2, 4), 16)
    const b = parseInt(_hex.substring(4, 6), 16)
    if ([r, g, b].some(v => Number.isNaN(v))) return hex
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
  }
  return [
    color,
    hexToRgba(color, 0.3),
    hexToRgba(color, 0.1),
  ]
})

const tableStyle = computed(() => ({
  width: props.elementInfo.width + 'px',
  borderCollapse: 'collapse',
  fontSize: 14,
  color: '#333',
}))

// 被合并单元格覆盖的坐标（row-col）
const hiddenCells = computed(() => {
  const hidden = new Set()
  const { data } = props.elementInfo
  if (!data) return hidden
  for (let r = 0; r < data.length; r++) {
    const row = data[r]
    if (!row) continue
    for (let c = 0; c < row.length; c++) {
      const cell = row[c]
      if (!cell) continue
      const colspan = cell.colspan || 1
      const rowspan = cell.rowspan || 1
      if (colspan > 1 || rowspan > 1) {
        for (let dr = 0; dr < rowspan; dr++) {
          for (let dc = 0; dc < colspan; dc++) {
            if (dr !== 0 || dc !== 0) hidden.add(`${r + dr}-${c + dc}`)
          }
        }
      }
    }
  }
  return hidden
})

// 单元格样式
const cellStyle = (r, c) => {
  const { data, theme } = props.elementInfo
  const cell = data[r]?.[c]
  if (!cell) return {}

  const { style } = cell
  if (!style) return {}

  const themeColors = subThemeColors.value
  const fontWeight = style.bold === true || style.fontWeight === 'bold' ? 'bold' : undefined
  const textAlign = style.align
  const borderColor = theme ? themeColors[1] : '#e2e6ed'
  const staticBorder = {
    borderLeft: `1px solid ${borderColor}`,
    borderRight: `1px solid ${borderColor}`,
    borderTop: `1px solid ${borderColor}`,
    borderBottom: `1px solid ${borderColor}`,
  }
  if (fontWeight === 'bold') {
    return {
      ...staticBorder,
      fontWeight,
      textAlign,
      backgroundColor: style.backcolor || themeColors[2],
    }
  }
  return {
    ...staticBorder,
    textAlign,
    backgroundColor: style.backcolor || undefined,
  }
}

const textStyle = (cell) => {
  return getTextStyle(cell.style || {})
}
</script>

<style lang="css" scoped>
.static-table {
  border-collapse: collapse;
  table-layout: fixed;
}
.static-table td {
  padding: 5px;
  word-break: break-word;
  overflow: hidden;
}
.static-table .hidden-cell {
  display: none;
}
</style>