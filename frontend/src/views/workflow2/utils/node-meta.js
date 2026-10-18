/**
 * 节点元数据：**唯一的来源**。
 *
 * 节点配置抽屉（`components/NodeDataDrawer.vue`）与节点库面板（`components/NodePanel.vue`）
 * 共用这里的描述——名称、图标、职责说明与配置分组开关。禁止在别处再复制一份：
 * 两处各写一套时，改了一处就会出现「面板里的说明与抽屉里的说明对不上」。
 *
 * `flags` 决定抽屉里哪些配置分组可见（提示词 / 输入 / 输出 / 迭代变量），搬移 / 新增节点类型
 * 时必须同步维护，不可增删或改名。
 *
 * 节点类型编码与后端 `NodeActionFactory` 的数字编码一一对应（见 docs/12-工作流规范.md §4.1），
 * 因此 key 一律是字符串（`'1'`~`'5'`），与画布 `nodeTypes` 的注册表口径一致。
 */
import {
  MagicStick,
  QuestionFilled,
  RefreshRight,
  SwitchButton,
  Upload,
  User,
  VideoPlay,
} from '@element-plus/icons-vue'

/** 已识别类型的元数据：type（字符串）→ { name, icon, desc, flags } */
export const NODE_META = {
  '1': {
    name: '开始',
    icon: VideoPlay,
    desc: '工作流入口：定义初始输出变量，供下游节点引用',
    flags: { prompt: false, inputs: false, outputs: true, variableParams: false },
  },
  '2': {
    name: '结束',
    icon: SwitchButton,
    desc: '工作流出口：把上游变量绑定成最终结果',
    flags: { prompt: false, inputs: true, outputs: false, variableParams: false },
  },
  '3': {
    name: '大模型',
    icon: MagicStick,
    desc: '按提示词调用大模型：读取输入变量，产出输出变量',
    flags: { prompt: true, inputs: true, outputs: true, variableParams: false },
  },
  '4': {
    name: '迭代',
    icon: RefreshRight,
    desc: '迭代容器：对列表输入逐项执行容器内的子步骤',
    // 只有迭代节点有「迭代变量」（`data.variableParameters`）：迭代体每轮都能读到它们
    flags: { prompt: false, inputs: true, outputs: true, variableParams: true },
  },
  '5': {
    name: '人工确认',
    icon: User,
    desc: '人工确认：执行到此暂停，确认后从断点继续',
    flags: { prompt: false, inputs: true, outputs: true, variableParams: false },
  },
  '6': {
    name: '输出',
    icon: Upload,
    desc: '输出：把上游产出按变量名透传成本节点的产出，声明「要展示的结果」',
    // 只有「输出参数」一栏：它的产出名就是这些 inputs 的变量名，没有独立的 outputs 声明
    flags: { prompt: false, inputs: true, outputs: false, variableParams: false },
  },
}

/** 未识别类型的兜底元数据（所有配置分组都不展示） */
export const UNKNOWN_META = {
  name: '未知节点',
  icon: QuestionFilled,
  desc: '未识别的节点类型，暂不支持配置',
  flags: { prompt: false, inputs: false, outputs: false, variableParams: false },
}

/**
 * 节点库面板的条目列表：按画布上的展示顺序 `1` → `5`。
 *
 * 直接从 {@link NODE_META} 派生，避免「面板漏了某个新类型」——新增节点类型时只改 NODE_META
 * 就会自动出现在面板里。
 */
export const NODE_CATALOG = Object.entries(NODE_META).map(([type, meta]) => ({
  type,
  name: meta.name,
  icon: meta.icon,
  desc: meta.desc,
}))

/**
 * 按类型取元数据，未识别时回落 {@link UNKNOWN_META}。
 *
 * 查表走对象的键语义，因此数字类型（`3`）会命中字符串键 `'3'` —— 这是刻意保留的容忍度：
 * 画布与后端约定类型是字符串编码，旧的脏数据里若写成数字，命中原类型也比显示「未知节点」有用。
 * `undefined` / `null` / 未登记的编码一律回落兜底元数据，调用方不需要自己判空。
 *
 * @param {string|number} type 节点类型编码
 * @returns {typeof UNKNOWN_META} 元数据对象
 */
export function metaOf(type) {
  return NODE_META[type] ?? UNKNOWN_META
}
