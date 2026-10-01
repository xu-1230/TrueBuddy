/**
 * 一次性脚本：生成「画布快照格式」的完整工作流 schema 样例。
 *
 * 数据来源分两部分：
 * - 结构（节点 / 连线 / 坐标 / 布局）按画布约定手写；
 * - 执行侧的后端 ref 结构（data.backendInputs）从本地画布
 *   `frontend/src/views/homework/mock/workflow-canvas.json` 原样取出，
 *   只把 ref 里的 blockID 换成本样例的节点 id。
 *
 * 用法：node _gen-canvas-sample.cjs
 */
const fs = require('fs')
const path = require('path')

const ROOT = 'd:/c桌面/毕业设计'
const CANVAS = path.join(ROOT, 'frontend/src/views/homework/mock/workflow-canvas.json')
const OUT = path.join(ROOT, 'testfile/workflow-canvas-sample.json')

const schema = JSON.parse(fs.readFileSync(CANVAS, 'utf8'))
const backendNode = Object.fromEntries(schema.nodes.map((node) => [node.id, node]))
const backendBlock = Object.fromEntries(
  schema.nodes.flatMap((node) => (node.blocks ?? []).map((block) => [block.id, block])),
)

/** 后端 ref 结构里的 blockID（指向后端节点）→ 样例里的画布节点 id */
const ID_MAP = {
  100001: '100001', // 开始
  100002: 'llm-plan', // 作业规划
  100003: 'human-confirm', // 人工确认
  100004: 'iter-grading', // 迭代批改
  900001: '__END__', // 结束
}

/** 取后端节点的 data.inputs（ref 结构），递归把 blockID 换成画布节点 id */
const backendInputsOf = (node) => {
  const inputs = node?.data?.inputs
  if (!inputs) return undefined
  const cloned = JSON.parse(
    JSON.stringify(inputs, (key, value) =>
      key === 'blockID' && ID_MAP[value] ? ID_MAP[value] : value,
    ),
  )
  // 去掉循环配置里与画布无关的空壳字段，保持样例可读
  if (cloned.fcParamVar && !Object.keys(cloned.fcParamVar).length) delete cloned.fcParamVar
  return cloned
}

/** 与 workflow-canvas.js 的 CANVAS_EDGE_STYLE 保持一致（markerEnd.type 序列化为 'arrow'） */
const EDGE_STYLE = {
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: 'arrow', color: 'var(--tb-primary)', width: 15, height: 15 },
}

/** 迭代子节点的可移动范围，与 WorkflowView 的 ITERATION_CHILD_EXTENT 一致 */
const CHILD_EXTENT = { range: 'parent', padding: [50, 6, 10, 6] }
/** 迭代内部节点层级 / 内部连线层级，与前端常量一致 */
const CHILD_Z_INDEX = 1
const INNER_EDGE_Z_INDEX = 1100

const nodes = [
  {
    id: '100001',
    type: 'input',
    position: { x: 100, y: 120 },
    sourcePosition: 'right',
    targetPosition: 'left',
    data: {
      label: '开始',
      outputs: [
        {
          name: 'fileTree',
          type: 'list',
          required: true,
          schema: { type: 'string' },
          description: '文件夹的文件树结构（相对路径列表）',
        },
        {
          name: 'aiDescription',
          type: 'string',
          required: true,
          description: 'AI 批改说明（本次批改的全局要求）',
        },
        {
          name: 'fullScore',
          type: 'integer',
          required: false,
          defaultValue: '100',
          description: '本次批改满分',
        },
      ],
    },
  },
  {
    id: 'llm-plan',
    type: 'llm',
    position: { x: 440, y: 120 },
    data: {
      label: '作业规划',
      inputs: [
        {
          name: 'fileTree',
          type: 'list',
          schema: { type: 'string' },
          description: '待批改的文件树（由开始节点声明，执行时自动引用）',
        },
        { name: 'aiDescription', type: 'string', description: 'AI 批改说明' },
      ],
      outputs: [
        { name: 'output', type: 'string', description: '规划结果：待批改清单（JSON 文本）' },
        { name: 'reasoning_content', type: 'string', description: '规划的思考过程' },
      ],
    },
  },
  {
    id: 'human-confirm',
    type: 'human',
    position: { x: 780, y: 120 },
    data: {
      label: '人工确认',
      inputs: [{ name: 'plan', type: 'string', description: '待教师确认的批改清单' }],
      outputs: [
        {
          name: 'confirmedPlan',
          type: 'list',
          required: true,
          schema: { type: 'string' },
          description: '教师确认后的批改清单',
        },
      ],
      // 执行时才用得到：ref 指向「作业规划」的输出，丢了它中断后清单会是空的
      backendInputs: backendInputsOf(backendNode['100003']),
    },
  },
  {
    id: 'iter-grading',
    type: 'iteration',
    position: { x: 1120, y: 120 },
    width: 760,
    height: 320,
    data: {
      label: '逐份批改',
      inputs: [
        {
          name: 'confirmedPlan',
          type: 'list',
          schema: { type: 'string' },
          description: '待批改的作业清单（循环变量）',
        },
      ],
      outputs: [
        {
          name: 'results',
          type: 'list',
          schema: { type: 'string' },
          description: '逐份批改结果汇总',
        },
      ],
      backendInputs: backendInputsOf(backendNode['100004']),
    },
  },
  {
    id: 'llm-grade',
    type: 'llm',
    parentNode: 'iter-grading',
    position: { x: 100, y: 56 },
    extent: CHILD_EXTENT,
    zIndex: CHILD_Z_INDEX,
    data: {
      label: '逐份批改',
      inputs: [
        { name: 'item', type: 'string', description: '当前迭代到的作业路径（循环注入）' },
        { name: 'aiDescription', type: 'string', description: 'AI 批改说明' },
        { name: 'fullScore', type: 'integer', description: '本次批改满分' },
      ],
      outputs: [{ name: 'result', type: 'string', description: '单份作业的批改结果' }],
      backendInputs: backendInputsOf(backendBlock['100004-1']),
    },
  },
  {
    id: 'iter-grading-start',
    type: 'iteration-start',
    parentNode: 'iter-grading',
    position: { x: 8, y: 56 },
    zIndex: CHILD_Z_INDEX,
    draggable: false,
  },
  {
    id: 'iter-grading-end',
    type: 'iteration-end',
    parentNode: 'iter-grading',
    position: { x: 560, y: 56 },
    zIndex: CHILD_Z_INDEX,
    draggable: false,
  },
  {
    id: '__END__',
    type: 'output',
    position: { x: 1960, y: 120 },
    sourcePosition: 'right',
    targetPosition: 'left',
    data: {
      label: '结束',
      outputs: [
        { name: 'content', type: 'string', description: '渲染给用户看的最终内容' },
        { name: 'results', type: 'list', schema: { type: 'string' }, description: '逐份批改结果' },
      ],
      backendInputs: backendInputsOf(backendNode['900001']),
    },
  },
]

/** 顶层连线：一条主线串起全部可执行节点 */
const topEdges = [
  ['100001', 'llm-plan'],
  ['llm-plan', 'human-confirm'],
  ['human-confirm', 'iter-grading'],
  ['iter-grading', '__END__'],
].map(([source, target]) => ({
  id: `e-${source}-${target}`,
  source,
  target,
  ...EDGE_STYLE,
}))

/** 迭代内部连线：起点 → 子节点 → 终点（zIndex 抬高，否则会被容器卡片盖住） */
const innerEdges = [
  ['iter-grading-start', 'llm-grade'],
  ['llm-grade', 'iter-grading-end'],
].map(([source, target]) => ({
  id: `e-${source}-${target}`,
  source,
  target,
  ...EDGE_STYLE,
  zIndex: INNER_EDGE_Z_INDEX,
}))

const payload = {
  _readme:
    '画布快照格式（workflow 表的 schema_json）完整样例：8 个节点 / 6 条连线（顶层 4 + 迭代内部 2）。' +
    '顶层 nodes / edges 就是画布保存的内容，去掉 _readme 后可直接经 POST /workflow/save 存库，' +
    '再在画布页「打开工作流」里查看。data.backendInputs 取自本地画布 mock/workflow-canvas.json，' +
    '只把 ref 里的 blockID 换成了本样例的节点 id（执行时用于人工确认清单、循环配置与结束节点输出）。',
  nodes,
  edges: [...topEdges, ...innerEdges],
}

fs.writeFileSync(OUT, `${JSON.stringify(payload, null, 2)}\n`, 'utf8')
console.log(
  `written ${OUT}\n  nodes=${nodes.length} edges=${payload.edges.length}` +
    ` (top=${topEdges.length}, inner=${innerEdges.length})`,
)
