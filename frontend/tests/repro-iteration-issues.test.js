/**
 * 迭代节点头三个问题的**最小复现 + 回归锁定**用例。
 *
 * 这些用例最初用来复现并确认问题真实存在（复现结论见对话记录），修复后转为回归锁定：
 *
 * 1. 子块移动范围：`extent` 必须与**可见画布区**一致（原来是 `'parent'` = 整张卡片盒，
 *    子块能被拖到卡片最顶部压在变量行上、也能贴到卡片边缘）；
 * 2. 内部连线删除：画布上移除内部边后，折叠回后端的 `node.edges` 不应再包含它
 *    （这条一直通过 —— 前端删除链路本身正确，删除「没有效果」来自后端兜底补边 +
 *    画布无持久化，按约定留给后续的「提交前前端校验」处理）；
 * 3. 连接点一致性：内侧（迭代开始 / 结束标记上的）handle 与普通节点同款类名、
 *    同款显隐规则、不再单独配色。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  CARD_LAYOUT,
  ITERATION_LAYOUT,
  canvasTopOf,
  cardRowCountOf,
  cardTopAreaHeightOf,
  childPositionOf,
  collapseIterations,
  containerSizeOf,
  expandIteration,
  iterationChildExtent,
} from '../src/views/workflow2/utils/iteration.js'

/** 默认定义里的迭代节点 */
const ITERATE_NODE = DEFAULT_FLOW_DEFINITION.nodes.find((node) => String(node.type) === '4')
const CONTAINER_ID = ITERATE_NODE.id
/** 迭代体首个子块（id 从定义派生：写死会在替换默认流程时染红） */
const CHILD_ID = ITERATE_NODE.blocks[0].id
/** 迭代体子块个数：节点初始尺寸与子块坐标都按它算 */
const CHILD_COUNT = ITERATE_NODE.blocks.length
/** 顶部卡片的变量个数（用于估算行数上界） */
const ROW_COUNT = cardRowCountOf(ITERATE_NODE.data)

/**
 * 顶部内容区高度（布局口径的输入）。无 DOM 环境取估算值 —— 它是实测值的上界，
 * 卡片挂载后会由 IterateNode 的 ResizeObserver 覆盖成真实高度。
 */
const TOP_AREA = cardTopAreaHeightOf(ROW_COUNT)

/** 读取组件源码（声明层断言用；路径相对本测试文件） */
const readSource = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf8')

test('问题 1：子块的拖拽约束与可见画布区重合（不再压到卡片头部 / 贴卡片边缘）', () => {
  const { nodes } = expandIteration(ITERATE_NODE)
  const child = nodes.find((node) => node.parentNode === CONTAINER_ID)

  const extent = iterationChildExtent(TOP_AREA)
  assert.deepEqual(child.extent, extent, '子块的 extent 就是画布区口径')
  assert.equal(extent.range, 'parent', '仍然相对父节点夹取，只是把范围收进画布区')

  // extent 的 padding 顺序同 CSS 四值：上 右 下 左
  const [top, right, bottom, left] = extent.padding
  const border = CARD_LAYOUT.borderWidth
  const box = containerSizeOf(CHILD_COUNT, TOP_AREA)

  // 约束矩形（相对父节点盒）= 画布区在卡片内的实际锚定矩形：
  // 画布区相对 `.node-card` 的 padding box 内缩 canvasInset，而父节点盒是 border box，故各加一个边框宽
  assert.deepEqual(
    { left, top, right: box.width - right, bottom: box.height - bottom },
    {
      left: border + ITERATION_LAYOUT.canvasInset,
      top: border + canvasTopOf(TOP_AREA),
      right: box.width - border - ITERATION_LAYOUT.canvasInset,
      bottom: box.height - border - ITERATION_LAYOUT.canvasInset,
    },
  )

  // 四向都内缩：不再是「整张卡片盒」（回归保护：改回 'parent' 会被这里挡住）
  assert.ok(top > 0 && left > 0 && right > 0 && bottom > 0, '四向都内缩，子块不会贴到卡片边缘')
  assert.equal(top, TOP_AREA + ITERATION_LAYOUT.canvasInset + border,
    '约束上边界 = 顶部内容区 + 内缩（+ 边框）：子块不可能停到头部 / 变量行上')

  // 对照：初始位置仍在画布区内
  const initial = childPositionOf(0, CHILD_COUNT, TOP_AREA)
  assert.ok(initial.y >= top - border, '初始 y 在画布区内')
  assert.ok(initial.x >= left - border && initial.x < box.width - right, '初始 x 在画布区内')
})

test('问题 2：画布上移除内部边后，折叠回后端的 node.edges 里不应再有它', () => {
  const { nodes, edges } = expandIteration(ITERATE_NODE)
  // 「子块 → 容器」是迭代体的虚拟出口，只挂在**没有出边的那个子块**上（不一定是首个子块）
  const target = edges.find((edge) => edge.target === CONTAINER_ID)
  assert.ok(target, '默认定义里存在「子块 → 容器」的内部边')

  const remaining = edges.filter((edge) => edge.id !== target.id)
  const collapsed = collapseIterations(nodes, remaining)
  const container = collapsed.nodes.find((node) => node.id === CONTAINER_ID)

  assert.equal(container.edges.length, edges.length - 1, '内部边数量随之减少')
  assert.equal(
    container.edges.some((edge) => edge.id === target.id),
    false,
    '被移除的内部边不应再写回 node.edges',
  )
})

test('问题 3：内侧 handle 与普通节点同款类名、同款显隐规则、不再单独配色', () => {
  const iterate = readSource('../src/views/workflow2/node/IterateNode.vue')
  const llm = readSource('../src/views/workflow2/node/LlmNode.vue')

  // 内侧 handle：与普通节点同款类名（按 type 带 -source / -target），不再有专属的 iterate-handle
  assert.match(iterate, /:id="ITERATE_ENTRY_HANDLE"[\s\S]{0,200}class="node-handle node-handle-source"/)
  assert.match(iterate, /:id="ITERATE_EXIT_HANDLE"[\s\S]{0,200}class="node-handle node-handle-target"/)
  assert.equal(iterate.includes('iterate-handle'), false, '内侧专属类与主色配色已移除')

  // 外侧 handle 同样补齐修饰类
  assert.match(iterate, /\bid="in"[\s\S]{0,200}class="node-handle node-handle-target"/)
  assert.match(iterate, /\bid="out"[\s\S]{0,200}class="node-handle node-handle-source"/)

  // 显隐规则与普通节点一致：悬停卡片（不再把卡片下方的结果区也算作触发范围）
  assert.equal(iterate.includes('.node-wrapper:hover .node-handle'), false, '旧的 wrapper 级悬停规则已移除')
  assert.match(iterate, /\.node-card:hover \.node-handle/)
  assert.match(llm, /\.node-card:hover \.node-handle/, '普通节点用的就是这条规则')

  // 连接点沿用 .node-handle 的白底 + 灰边，没有单独的颜色覆盖
  assert.match(iterate, /\.node-handle \{[\s\S]{0,260}background: var\(--tb-bg-surface\) !important/)
})
