/**
 * 作业批改 —— 批改工作流定义与本地存储
 *
 * 工作流 = 一张节点图，节点即批改步骤，
 * 系统内置「标准批改作业」等模板，用户可在此基础上新建自己的工作流。
 * 演示模式下自定义工作流存放在 localStorage，刷新不丢。
 */

import { markRaw } from 'vue'
import { UserFilled, CircleCheck, MagicStick, RefreshRight } from '@element-plus/icons-vue'

/** 本地存储键 */
const STORAGE_KEY = 'tb-homework-workflows'

/** 内置工作流 id 前缀 */
const BUILTIN_PREFIX = 'builtin-'

/**
 * 可用节点类型（节点面板使用）
 * duration 为演示模式下的单步耗时（毫秒）
 */
export const NODE_KINDS = [
  {
    kind: 'start',
    label: '开始',
    hint: '工作流入口',
    tag: 'START',
    tone: 'start',
    icon: markRaw(UserFilled),
    duration: 120,
  },
  {
    kind: 'llm',
    label: 'LLM',
    hint: '调用大模型评分与写评语',
    tag: 'LLM',
    tone: 'llm',
    icon: markRaw(MagicStick),
    duration: 440,
    hasLlmConfig: true,
  },
  {
    kind: 'iteration',
    label: '迭代',
    hint: '对每一份作业重复执行容器内的步骤',
    tag: 'ITERATION',
    tone: 'iteration',
    icon: markRaw(RefreshRight),
    duration: 200,
    hasIterationConfig: true,
  },
  {
    kind: 'end',
    label: '结束',
    hint: '工作流出口，汇总本次批改',
    tag: 'END',
    tone: 'end',
    icon: markRaw(CircleCheck),
    duration: 140,
  },
]

/** 节点卡片尺寸与间距（拖拽新增 / 链路排布 / 防重叠共用） */
export const NODE_WIDTH = 212
export const NODE_HEIGHT = 104
export const NODE_GAP = 252
/** 纵向错落的步长（内置链路排布与「整理布局」共用） */
export const NODE_ROW_GAP = 56

/** 迭代容器尺寸与内部子节点的排布参数 */
export const ITERATION_WIDTH = 400
export const ITERATION_HEIGHT = 240
export const ITERATION_PADDING_X = 24
export const ITERATION_PADDING_Y = 64
/** 迭代体入口（「迭代开始」节点）与首个子步骤之间的纵向间距 */
export const ITERATION_START_GAP = 52
export const ITERATION_CHILD_GAP = 24

export const NODE_KIND_MAP = Object.fromEntries(NODE_KINDS.map((item) => [item.kind, item]))

/** 不在节点库里出现、但作为内置结构存在的节点元信息（如迭代体的「迭代开始」入口） */
const EXTRA_NODE_META = {
  'iteration-start': {
    kind: 'iteration-start',
    label: '迭代开始',
    hint: '迭代体的入口，从这里连出容器内的每一步',
    tag: 'START',
    tone: 'iteration',
    icon: markRaw(RefreshRight),
    duration: 60,
  },
}

/** LLM 节点可选模型（value 与后端模型标识保持一致） */
export const LLM_MODEL_OPTIONS = [
  { value: 'gpt-4o', label: 'gpt-4o', provider: 'OpenAI', tag: 'CHAT' },
  { value: 'gpt-4o-mini', label: 'gpt-4o-mini', provider: 'OpenAI', tag: 'CHAT' },
  { value: 'qwen-max', label: 'qwen-max', provider: '通义千问', tag: 'CHAT' },
  { value: 'qwen-plus', label: 'qwen-plus', provider: '通义千问', tag: 'CHAT' },
  { value: 'ernie-4.0', label: 'ernie-4.0', provider: '文心一言', tag: 'CHAT' },
  { value: 'glm-4', label: 'glm-4', provider: '智谱 AI', tag: 'CHAT' },
]

/** 提示词可插入的变量（点击后以 {{变量}} 形式写入提示词） */
export const PROMPT_VARIABLES = [
  { key: '作业原文', token: '{{作业原文}}', desc: '当前作业的解析文本' },
  { key: '批改要求', token: '{{批改要求}}', desc: '本次批改的全局要求' },
  { key: '满分', token: '{{满分}}', desc: '总分，默认 100' },
  { key: '学生姓名', token: '{{学生姓名}}', desc: '从文件名识别出的学生' },
]

/** LLM 节点默认配置：新建节点与旧数据补默认值共用 */
export const LLM_DEFAULTS = {
  model: 'gpt-4o',
  temperature: 0.2,
  maxTokens: 2048,
  kbEnabled: false,
  kbId: 'default',
  prompt: '',
}

/** 模型 value → 展示名（节点卡片与批改回显使用） */
export function modelLabel(value) {
  return LLM_MODEL_OPTIONS.find((item) => item.value === value)?.label ?? value ?? '未选择模型'
}

/** 模型 value → 服务商（面板展示） */
export function modelProvider(value) {
  return LLM_MODEL_OPTIONS.find((item) => item.value === value)?.provider ?? ''
}

/** 迭代节点默认配置：新建节点与旧数据补默认值共用 */
export const ITERATION_DEFAULTS = {
  parallel: false,
  concurrency: 3,
}

/** 迭代节点输出变量（面板只读展示） */
export const ITERATION_OUTPUTS = [
  { name: 'item', type: 'object', desc: '当前迭代项（一份作业）' },
  { name: 'index', type: 'number', desc: '当前迭代序号，从 0 开始' },
  { name: 'result', type: 'array', desc: '全部迭代结果组成的数组' },
]

/**
 * 补齐 LLM 节点缺失的配置项
 * 内置模板与历史自定义工作流可能没有新字段，读取时合并默认值，
 * 不直接写盘，避免打开页面就误判「未保存」。
 */
export function withLlmDefaults(node) {
  if (node?.data?.kind !== 'llm') return node
  return { ...node, data: { ...LLM_DEFAULTS, ...node.data } }
}

/** 补齐迭代节点缺失的配置项（与 LLM 同机制） */
export function withIterationDefaults(node) {
  if (node?.data?.kind !== 'iteration') return node
  return { ...node, data: { ...ITERATION_DEFAULTS, ...node.data } }
}

/** 节点配置兜底统一入口：LLM 与迭代节点各自补齐自己的字段 */
export function withNodeDefaults(node) {
  return withIterationDefaults(withLlmDefaults(node))
}

/** 节点 → vue-flow 节点类型（只有迭代容器用独立组件） */
export function nodeTypeOf(kind) {
  return kind === 'iteration' ? 'iteration' : 'workflow'
}

/** 取迭代容器内部的子节点（按纵向位置排序） */
export function childrenOf(nodes, parentId) {
  return (nodes ?? [])
    .filter((node) => node.parentNode === parentId)
    .sort((a, b) => (a.position?.y ?? 0) - (b.position?.y ?? 0))
}

/**
 * 内置的批改工作流：系统只提供一个标准模板，用户在此基础上复制或新建自己的工作流。
 * 链路：开始 → 迭代（内部含 LLM 评分与评语）→ 结束
 * chain[].children 为迭代容器内部的子步骤
 */
const BUILTIN_WORKFLOWS = [
  {
    id: 'builtin-standard',
    name: '标准批改作业',
    description: '系统内置的批改工作流：开始 → 逐份迭代（LLM 评分与评语）→ 结束',
    chain: [
      { kind: 'start', label: '开始' },
      {
        kind: 'iteration',
        label: '逐份迭代',
        note: '对文件夹中的每一份作业重复执行容器内的步骤',
        parallel: false,
        concurrency: 3,
        children: [
          {
            kind: 'llm',
            label: 'LLM 评分与评语',
            note: '按准确性 / 完整性 / 表达三维度打分（满分 100），并给出亮点与问题',
            model: 'gpt-4o',
            temperature: 0.2,
            maxTokens: 2048,
            kbEnabled: false,
            kbId: 'default',
            prompt: '你是资深课程助教，请结合「{{批改要求}}」审阅「{{作业原文}}」。\n'
              + '按准确性 / 完整性 / 表达三个维度打分（满分 {{满分}}），先给出总分，再分条列出亮点与改进建议。',
          },
        ],
      },
      { kind: 'end', label: '结束', note: '汇总本次批改结果并输出成绩表' },
    ],
  },
]

/** 工作流是否为内置模板 */
export function isBuiltin(id) {
  return typeof id === 'string' && id.startsWith(BUILTIN_PREFIX)
}

/** 读取全部工作流：批改工作流（内置，仅一个）+ 用户自定义工作流 */
export function loadWorkflows() {
  const custom = readCustom()
  return [...custom, ...BUILTIN_WORKFLOWS.map(buildBuiltin)]
}

/** 按 id 取工作流 */
export function getWorkflow(id) {
  return loadWorkflows().find((item) => item.id === id) ?? null
}

/** 默认工作流（标准批改作业） */
export function defaultWorkflowId() {
  return BUILTIN_WORKFLOWS[0].id
}

/** 新增或更新自定义工作流 */
export function saveWorkflow(workflow) {
  const list = readCustom()
  const now = formatNow()
  const record = {
    ...workflow,
    id: workflow.id || `wf-${Date.now()}`,
    builtin: false,
    updatedAt: now,
    createTime: workflow.createTime || now,
  }
  const index = list.findIndex((item) => item.id === record.id)
  if (index >= 0) list.splice(index, 1, record)
  else list.unshift(record)
  writeCustom(list)
  return record
}

/** 删除自定义工作流（内置模板不可删） */
export function removeWorkflow(id) {
  if (isBuiltin(id)) return false
  const list = readCustom().filter((item) => item.id !== id)
  writeCustom(list)
  return true
}

/** 复制一份工作流（内置模板复制后即为可编辑的自定义工作流） */
export function duplicateWorkflow(id, name) {
  const source = getWorkflow(id)
  if (!source) return null
  return saveWorkflow({
    name: name || `${source.name} 副本`,
    description: source.description,
    nodes: cloneNodes(source.nodes),
    edges: source.edges.map((edge) => ({ ...edge, id: edge.id })),
  })
}

/**
 * 从节点链路推导批改特征，供演示模式的批改执行使用
 * - LLM 节点决定使用的模型与是否启用知识库上下文
 * @returns {{ kinds: string[], hasLlm: boolean, model: string, kbEnabled: boolean, kbId: string, prompt: string }}
 */
export function traitsOf(workflow) {
  const nodes = (workflow?.nodes ?? []).filter((node) => !node.parentNode)
  const kinds = nodes.map((node) => node.data?.kind)
  const llmNode = withLlmDefaults(nodes.find((node) => node.data?.kind === 'llm'))
  const iterationNode = withIterationDefaults(nodes.find((node) => node.data?.kind === 'iteration'))

  return {
    kinds,
    hasLlm: kinds.includes('llm'),
    hasIteration: kinds.includes('iteration'),
    model: llmNode?.data?.model ?? '',
    kbEnabled: llmNode?.data?.kbEnabled === true,
    kbId: llmNode?.data?.kbId ?? '',
    prompt: llmNode?.data?.prompt ?? '',
    parallel: iterationNode?.data?.parallel === true,
    concurrency: iterationNode?.data?.concurrency ?? ITERATION_DEFAULTS.concurrency,
  }
}

/**
 * 按批改顺序返回节点：沿连线从起始节点依次向下游遍历（节点位置不影响顺序），
 * 未连线的孤立节点按横坐标追加在末尾。
 * 迭代容器内部的子节点属于「迭代体」，不参与主链路遍历。
 */
export function orderedNodes(workflow) {
  const nodes = (workflow?.nodes ?? []).filter((node) => !node.parentNode)
  const edges = workflow?.edges ?? []
  if (!nodes.length) return []

  const byId = new Map(nodes.map((node) => [node.id, node]))
  const incoming = new Set(edges.map((edge) => edge.target))
  const nextOf = new Map()
  edges.forEach((edge) => {
    if (!nextOf.has(edge.source)) nextOf.set(edge.source, edge.target)
  })

  const ordered = []
  const visited = new Set()
  let cursor = nodes.find((node) => !incoming.has(node.id)) ?? nodes[0]
  while (cursor && !visited.has(cursor.id)) {
    visited.add(cursor.id)
    ordered.push(cursor)
    cursor = byId.get(nextOf.get(cursor.id))
  }

  nodes
    .filter((node) => !visited.has(node.id))
    .sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0))
    .forEach((node) => ordered.push(node))
  return ordered
}

/** 默认节点样式标签（生成节点时用） */
export function nodeMeta(kind) {
  return NODE_KIND_MAP[kind] ?? EXTRA_NODE_META[kind] ?? { kind, label: kind, hint: '', tag: kind, tone: 'llm', duration: 240 }
}

/* ========== 内部方法 ========== */

/**
 * 由内置链路定义生成可编辑的节点图
 * 顶层节点按 n1 / n2 / … 编号，迭代容器的子节点为 n{父序号}-c{子序号}
 */
function buildBuiltin(def) {
  const nodes = []
  def.chain.forEach((step, index) => {
    const id = `n${index + 1}`
    nodes.push(createNode(step, index, id))
    // 迭代容器内部先放一个「迭代开始」入口节点，再放容器内的子步骤
    if (step.kind === 'iteration') {
      nodes.push(createStartNode(id))
      ;(step.children ?? []).forEach((child, childIndex) => {
        nodes.push(createChildNode(child, id, childIndex))
      })
    }
  })

  // 主链路连线：顶层节点顺序相连（容器内部由迭代语义驱动，不额外连线）
  const edges = def.chain.slice(0, -1).map((_, index) => ({
    id: `${def.id}-e${index + 1}`,
    source: `n${index + 1}`,
    target: `n${index + 2}`,
    type: 'smoothstep',
  }))

  // 迭代体的入口连到容器内第一个子步骤（与 Dify 一致：从「迭代开始」出发）
  def.chain.forEach((step, index) => {
    if (step.kind === 'iteration' && (step.children ?? []).length) {
      edges.push({
        id: `${def.id}-ie${index + 1}`,
        source: `n${index + 1}-start`,
        target: `n${index + 1}-c1`,
        type: 'smoothstep',
      })
    }
  })

  return {
    id: def.id,
    name: def.name,
    description: def.description,
    builtin: true,
    nodes,
    edges,
  }
}

/** 生成一个步骤节点（横向排布，y 交错避免节点重叠） */
function createNode(step, index, id = `n${index + 1}`) {
  const meta = nodeMeta(step.kind)
  return {
    id,
    type: nodeTypeOf(step.kind),
    position: { x: 40 + index * NODE_GAP, y: 80 + (index % 2) * NODE_ROW_GAP },
    ...(step.kind === 'iteration' ? iterationShape() : null),
    data: {
      kind: step.kind,
      label: step.label || meta.label,
      hint: meta.hint,
      note: step.note ?? '',
      order: index + 1,
      ...(step.kind === 'llm' ? llmFields(step) : null),
      ...(step.kind === 'iteration' ? iterationFields(step) : null),
    },
  }
}

/** 生成一个迭代容器内部的子节点（坐标相对父容器，排在「迭代开始」节点下方） */
function createChildNode(step, parentId, index) {
  return {
    ...createNode(step, index, `${parentId}-c${index + 1}`),
    parentNode: parentId,
    extent: 'parent',
    expandParent: true,
    position: {
      x: ITERATION_PADDING_X,
      y: ITERATION_PADDING_Y + ITERATION_START_GAP + index * (NODE_HEIGHT + ITERATION_CHILD_GAP),
    },
  }
}

/** 迭代容器的尺寸（子节点拖入时会自动撑开） */
function iterationShape() {
  return { style: { width: `${ITERATION_WIDTH}px`, height: `${ITERATION_HEIGHT}px` } }
}

/** 迭代容器的内置入口节点「迭代开始」（kind = iteration-start，仅作迭代体起点） */
function createStartNode(parentId) {
  return {
    id: `${parentId}-start`,
    type: 'iteration-start',
    parentNode: parentId,
    extent: 'parent',
    expandParent: true,
    position: { x: ITERATION_PADDING_X, y: ITERATION_PADDING_Y },
    data: { kind: 'iteration-start', label: '迭代开始', order: '0' },
  }
}

/** 取迭代节点的配置项（缺省项用 ITERATION_DEFAULTS 兜底） */
function iterationFields(step) {
  return {
    parallel: step.parallel === true,
    concurrency: Number.isFinite(step.concurrency)
      ? Math.min(5, Math.max(1, step.concurrency))
      : ITERATION_DEFAULTS.concurrency,
  }
}

/** 取 LLM 节点的配置项（缺省项用 LLM_DEFAULTS 兜底） */
function llmFields(step) {
  return {
    model: step.model || LLM_DEFAULTS.model,
    temperature: Number.isFinite(step.temperature) ? step.temperature : LLM_DEFAULTS.temperature,
    maxTokens: Number.isFinite(step.maxTokens) ? step.maxTokens : LLM_DEFAULTS.maxTokens,
    kbEnabled: step.kbEnabled === true,
    kbId: step.kbId || LLM_DEFAULTS.kbId,
    prompt: step.prompt ?? LLM_DEFAULTS.prompt,
  }
}

function cloneNodes(nodes) {
  return nodes.map((node) => ({ ...node, position: { ...node.position }, data: { ...node.data } }))
}

function readCustom() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : null
    return Array.isArray(parsed) ? parsed.filter((item) => item && item.id && !isBuiltin(item.id)) : []
  } catch {
    return []
  }
}

function writeCustom(list) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    // 存储不可用时不影响页面使用
  }
}

function formatNow() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} `
    + `${pad(now.getHours())}:${pad(now.getMinutes())}`
}
