/**
 * 节点类型 → 图标组件。
 *
 * 节点库卡片（ToolsWrap）与节点数据抽屉（NodeDataDrawer）共用同一套映射，
 * 保证「从节点库拖出来的节点」与「抽屉里看到的节点」图标一致。
 *
 * 注：`utils/node-catalog.js` 刻意保持纯数据、不引入组件，所以图标集中放在这里。
 */
import {
  Flag,
  Grid,
  MagicStick,
  QuestionFilled,
  Refresh,
  SwitchButton,
} from '@element-plus/icons-vue'

export const NODE_ICONS = {
  input: Flag,
  start: Flag,
  output: SwitchButton,
  end: SwitchButton,
  llm: MagicStick,
  human: QuestionFilled,
  iteration: Refresh,
  'iteration-start': Grid,
  'iteration-end': Grid,
}

/** 取图标；未知类型回退到通用图标，避免渲染空白 */
export const resolveNodeIcon = (type) => NODE_ICONS[type] ?? Grid
