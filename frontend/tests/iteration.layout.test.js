/**
 * 迭代节点布局 / 折叠口径的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 覆盖修复后的期望行为：尺寸下限装得下子块、**画布区高度守恒**（增删变量不改变画布区高度）、
 * 节点拉宽后子块重新居中、悬空 parentNode 不再丢失、以及「嵌套迭代体只展开一层但数据不丢」。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  CARD_LAYOUT,
  ITERATION_LAYOUT,
  canvasHeightOf,
  canvasHeightOfCard,
  canvasTopOf,
  cardHeightAfterContentChange,
  cardTopAreaHeightOf,
  childPositionOf,
  childXOf,
  childXOfNodeWidth,
  collapseIterations,
  containerSizeOf,
  estimatedTopAreaOf,
  expandIteration,
  isNestedNode,
  layoutIteration,
  minNodeSizeOf,
  restackChildPositions,
} from '../src/views/workflow2/utils/iteration.js'

/** 默认画布的卡片变量个数：输入 1 + 迭代变量 1 + 输出 1 */
const ROW_COUNT = 3

/**
 * 顶部内容区高度（布局口径的输入）。
 * 无 DOM 环境下取 `cardTopAreaHeightOf` 的估算值 —— 它是实测值的**上界**
 * （实际渲染时变量胶囊是 flex-wrap，一行可并排多个，行数只会更少）。
 */
const TOP_AREA = cardTopAreaHeightOf(ROW_COUNT)

/** 设计宽度下的节点宽度（子块 + 两个标记 + 内边距与内缩；与顶部高度无关） */
const DESIGN_WIDTH = containerSizeOf(1, TOP_AREA).width

test('尺寸下限装得下全部子块（与初始尺寸同源）', () => {
  const min = minNodeSizeOf(TOP_AREA, 3)
  const initial = containerSizeOf(3, TOP_AREA)

  assert.deepEqual(min, initial)

  // 画布区高度必须装得下 3 个子块 + 上下内边距，而不是恒等于 canvasMinHeight
  const needed = ITERATION_LAYOUT.canvasPaddingY * 2
    + 3 * ITERATION_LAYOUT.childHeight
    + 2 * ITERATION_LAYOUT.childGap
  assert.equal(canvasHeightOfCard(min.height, TOP_AREA), needed)
  assert.ok(needed > ITERATION_LAYOUT.canvasMinHeight)
})

test('初始尺寸下画布区高度恰好等于画布区口径（不再有 2px 边框偏差）', () => {
  for (const childCount of [0, 1, 3]) {
    assert.equal(
      canvasHeightOfCard(containerSizeOf(childCount, TOP_AREA).height, TOP_AREA),
      canvasHeightOf(childCount),
      `子块数 ${childCount}：初始尺寸换算出的画布区高度必须正好是 canvasHeightOf`,
    )
  }
})

test('顶部内容区高度变化时画布区高度守恒', () => {
  const cardHeight = containerSizeOf(1, TOP_AREA).height
  const grownArea = TOP_AREA + CARD_LAYOUT.variableRowHeight
  const shrunkArea = TOP_AREA - CARD_LAYOUT.variableRowHeight
  const baseline = canvasHeightOfCard(cardHeight, TOP_AREA)

  // 下限必须按**变化后**的顶部高度算（组件的用法）：沿用旧下限会把「内容变矮后的回缩」挡住
  const grown = cardHeightAfterContentChange(
    cardHeight, TOP_AREA, grownArea, minNodeSizeOf(grownArea, 1).height,
  )
  assert.equal(canvasHeightOfCard(grown, grownArea), baseline, '内容变高多少卡片就长高多少，画布区高度不变')

  const shrunk = cardHeightAfterContentChange(
    grown, grownArea, shrunkArea, minNodeSizeOf(shrunkArea, 1).height,
  )
  assert.equal(
    canvasHeightOfCard(shrunk, shrunkArea),
    baseline,
    '内容变矮（首帧估算偏大后的收敛）时卡片回缩，画布区高度同样不变',
  )
})

test('内容高度没变时卡片高度一动不动（同一行内新增变量的场景）', () => {
  const cardHeight = containerSizeOf(1, TOP_AREA).height

  assert.equal(
    cardHeightAfterContentChange(cardHeight, TOP_AREA, TOP_AREA, 0),
    cardHeight,
    '实测 delta 为 0：卡片高度必须保持不变，否则画布区会被撑大',
  )
})

test('高度补偿不会低于「装得下子块」的下限', () => {
  const minHeight = minNodeSizeOf(TOP_AREA, 2).height

  assert.equal(
    cardHeightAfterContentChange(10, TOP_AREA, 0, minHeight),
    minHeight,
    '补偿结果低于下限时取下限',
  )
  assert.ok(minHeight > ITERATION_LAYOUT.canvasMinHeight, '多子块的下限高于画布区最小高度')
})

test('节点拉宽 / 缩窄后子块横向重新居中（x 是自动轴）', () => {
  // 设计口径下的子块 x = 内缩 + 内边距 + 标记宽 + 标记与子块的间距（后者决定内部连线的长度，不能为 0）
  const designChildX = ITERATION_LAYOUT.canvasInset
    + ITERATION_LAYOUT.paddingX
    + ITERATION_LAYOUT.markerWidth
    + ITERATION_LAYOUT.markerGap

  assert.equal(childXOf(), designChildX, '设计口径下的子块 x = 内缩 + 内边距 + 标记宽 + 间距')
  assert.equal(childXOfNodeWidth(DESIGN_WIDTH), designChildX, '设计宽度下与初始布局一致')
  assert.ok(
    ITERATION_LAYOUT.markerGap >= 20,
    '标记与子块的间距就是内部连线的长度：太短会让连线看不清、也点不中（删不掉）',
  )

  const widened = childXOfNodeWidth(800)
  assert.equal(widened, ITERATION_LAYOUT.canvasInset
    + (800 - ITERATION_LAYOUT.canvasInset * 2 - ITERATION_LAYOUT.childWidth) / 2)
  assert.ok(widened > designChildX, '拉宽后子块跟着往右走（不再固定在设计宽度的位置）')

  const narrowed = childXOfNodeWidth(DESIGN_WIDTH + 20)
  assert.ok(narrowed > designChildX && narrowed < widened, '宽度变化时 x 单调跟随')
})

test('纵向口径：子块区在画布区内垂直居中', () => {
  const childCount = 3
  const canvasTop = canvasTopOf(TOP_AREA)
  const canvasHeight = canvasHeightOfCard(containerSizeOf(childCount, TOP_AREA).height, TOP_AREA)
  const blockHeight = childCount * ITERATION_LAYOUT.childHeight
    + (childCount - 1) * ITERATION_LAYOUT.childGap
  const paddingTop = childPositionOf(0, childCount, TOP_AREA).y - canvasTop
  const paddingBottom = canvasHeight - paddingTop - blockHeight

  assert.ok(paddingTop >= ITERATION_LAYOUT.canvasPaddingY, '上方留白不低于画布区内边距')
  assert.equal(paddingTop, paddingBottom, '上下留白相等（垂直居中）')
})

test('顶部高度变化 delta 时子块 y 同步平移同一个 delta（组件据此平移画布上的子块）', () => {
  const delta = CARD_LAYOUT.variableRowHeight
  const before = childPositionOf(0, 2, TOP_AREA)
  const after = childPositionOf(0, 2, TOP_AREA + delta)

  assert.equal(after.y - before.y, delta, '子块 y 与画布区顶部同步平移，在画布区内的相对位置不变')
  assert.equal(after.x, before.x, 'x 不受顶部高度影响')
})

test('悬空 parentNode 的节点在折叠后保留为顶层节点（不再被静默丢弃）', () => {
  const nodes = [
    { id: 'c', type: '4', data: { label: '迭代' }, blocks: [], edges: [], position: { x: 0, y: 0 } },
    { id: 'orphan', type: '3', data: { label: '孤儿子块' }, parentNode: 'ghost', position: { x: 10, y: 10 } },
  ]

  const { nodes: collapsed } = collapseIterations(nodes, [])

  assert.equal(collapsed.some((node) => node.id === 'orphan'), true, '孤儿子块回到顶层，不会被丢掉')
  assert.deepEqual(collapsed.find((node) => node.id === 'c').blocks, [])
})

test('嵌套迭代体只展开一层，但内层定义在往返中不丢', () => {
  const inner = {
    id: 'inner',
    type: '4',
    data: { label: '内层迭代' },
    blocks: [{ id: 'deep', type: '3', data: { label: '内层子块' } }],
    edges: [{ id: 'e-inner-deep', sourceNodeID: 'inner', targetNodeID: 'deep' }],
  }
  const outer = {
    id: 'outer',
    type: '4',
    data: { label: '外层迭代' },
    blocks: [inner],
    edges: [
      { id: 'e-outer-inner', sourceNodeID: 'outer', targetNodeID: 'inner' },
      { id: 'e-inner-outer', sourceNodeID: 'inner', targetNodeID: 'outer' },
    ],
  }

  const { nodes, edges } = expandIteration(outer)

  // 只展开一层：画布上是「外层容器 + 内层容器」，内层子块不出现在画布上（卡片上会标「嵌套 N」）
  assert.equal(nodes.length, 2)
  assert.equal(nodes.some((node) => node.id === 'deep'), false)
  assert.equal(nodes.find((node) => node.id === 'inner').parentNode, 'outer')

  // 往返回来：内层 blocks / edges 原样保留（提交后端不丢数据）
  const { nodes: back, edges: outerEdges } = collapseIterations(nodes, edges)
  const outerBack = back.find((node) => node.id === 'outer')
  assert.equal(back.length, 1, '顶层只有外层迭代节点')
  assert.equal(outerBack.blocks.length, 1)
  assert.equal(outerBack.blocks[0].id, 'inner')
  assert.deepEqual(outerBack.blocks[0].blocks, [inner.blocks[0]])
  assert.deepEqual(outerBack.blocks[0].edges, [inner.edges[0]])
  assert.deepEqual(outerBack.edges, [
    { id: 'e-outer-inner', sourceNodeID: 'outer', targetNodeID: 'inner' },
    { id: 'e-inner-outer', sourceNodeID: 'inner', targetNodeID: 'outer' },
  ])
  assert.deepEqual(outerEdges, [], '内部边不落到顶层')
})

test('顶部内容区估算口径：与图标/标题对齐，且是「按变量个数」的上界', () => {
  const headerHeight = Math.max(CARD_LAYOUT.iconHeight, CARD_LAYOUT.titleContentHeight)

  assert.equal(cardTopAreaHeightOf(0), CARD_LAYOUT.paddingY * 2 + headerHeight)
  assert.ok(
    CARD_LAYOUT.titleContentHeight > CARD_LAYOUT.iconHeight,
    '迭代卡片的标题区（label + 副标题两行）比图标高，头部内容高度必须按标题区算',
  )
  assert.equal(
    cardTopAreaHeightOf(3) - cardTopAreaHeightOf(2),
    CARD_LAYOUT.variableRowHeight,
    '每多一个变量，估算增量等于 VariableTags 的一行高度',
  )
  assert.equal(
    cardTopAreaHeightOf(1) - cardTopAreaHeightOf(0),
    CARD_LAYOUT.variableRowHeight + CARD_LAYOUT.tailPadding,
    '首个变量还要带上「末行留白」的口径',
  )
  assert.equal(
    estimatedTopAreaOf({ inputs: [{ name: 'a' }], outputs: [{ name: 'b' }] }),
    cardTopAreaHeightOf(2),
    '估算值按变量个数（= 行数上界）推导；真实高度由 IterateNode 的 ResizeObserver 测出来',
  )
})

test('子块增删时容器尺寸只增不减', () => {
  const data = { label: '迭代', inputs: [{ name: 'items', type: 'list' }] }
  const topArea = estimatedTopAreaOf(data)
  const initial = containerSizeOf(0, topArea)
  const container = {
    id: 'c',
    type: '4',
    data,
    style: { width: `${initial.width}px`, height: `${initial.height}px` },
    blocks: [],
    edges: null,
  }

  const grown = layoutIteration([container, { id: 'a', type: '3', parentNode: 'c', data: {} }], 'c')
    .find((node) => node.id === 'c')
  assert.equal(grown.style.width, container.style.width, '宽度不因增加子块而改变（子块纵向排列）')
  assert.ok(parseFloat(grown.style.height) >= initial.height, '容器高度不缩小')

  const custom = { ...container, style: { width: '900px', height: '800px' } }
  const kept = layoutIteration([custom], 'c').find((node) => node.id === 'c')
  assert.equal(kept.style.width, '900px', '不把用户拖出来的宽度弹回初始值')
  assert.equal(kept.style.height, '800px', '不把用户拖出来的高度弹回初始值')

  const min = minNodeSizeOf(topArea, 0)
  const belowMin = layoutIteration(
    [{ ...container, style: { width: '10px', height: '10px' } }],
    'c',
  ).find((node) => node.id === 'c')
  assert.equal(belowMin.style.width, `${min.width}px`, '小于下限时补到下限')
  assert.equal(belowMin.style.height, `${min.height}px`, '小于下限时补到下限')
})

test('layoutIteration 接受实测顶部高度：用它算出的下限不会比实测高度更大', () => {
  const data = { label: '迭代', inputs: [{ name: 'items', type: 'list' }] }
  const estimated = estimatedTopAreaOf(data)
  const measured = estimated - CARD_LAYOUT.variableRowHeight
  const nodes = [
    { id: 'c', type: '4', data, style: { width: '424px', height: '410px' } },
    { id: 'a', type: '3', parentNode: 'c', data: {} },
  ]

  const byMeasured = layoutIteration(nodes, 'c', measured).find((node) => node.id === 'c')
  const byEstimated = layoutIteration(nodes, 'c').find((node) => node.id === 'c')

  assert.ok(
    parseFloat(byEstimated.style.height) >= parseFloat(byMeasured.style.height),
    '不传实测值时用估算值（上界），算出的下限只会更保守，不会把卡片压小',
  )
  assert.equal(byMeasured.style.height, '410px', '实测值下的下限仍低于用户已拖出来的高度，故保持不变')
})

test('isNestedNode 只认「父容器仍然存在」的节点', () => {
  const nodes = [
    { id: 'c', type: '4' },
    { id: 'child', type: '3', parentNode: 'c' },
    { id: 'orphan', type: '3', parentNode: 'ghost' },
    { id: 'top', type: '3' },
  ]

  assert.equal(isNestedNode(nodes, 'child'), true)
  assert.equal(isNestedNode(nodes, 'orphan'), false, '容器已删除时不再算迭代体内节点')
  assert.equal(isNestedNode(nodes, 'top'), false)
  assert.equal(isNestedNode(nodes, 'missing'), false)
})

/* ===== restackChildPositions：只消解重叠，保留用户摆放 ===== */

test('restackChildPositions：没有重叠时一个都不动（默认流程的排布保持原样）', () => {
  const items = [
    { id: 'a', y: 100, height: 160 },
    { id: 'b', y: 284, height: 160 },
    { id: 'c', y: 468, height: 160 },
  ]
  assert.deepEqual(restackChildPositions(items), [
    { id: 'a', y: 100 },
    { id: 'b', y: 284 },
    { id: 'c', y: 468 },
  ])
})

test('restackChildPositions：纵向重叠时后一个顺延「高度 + 间距」', () => {
  const items = [
    { id: 'a', y: 0, height: 160 },
    { id: 'b', y: 40, height: 160 },
  ]
  assert.deepEqual(restackChildPositions(items), [
    { id: 'a', y: 0 },
    { id: 'b', y: 160 + ITERATION_LAYOUT.childGap },
  ])
})

test('restackChildPositions：按当前 y 排序处理，与入参顺序无关', () => {
  const items = [
    { id: 'b', y: 300, height: 160 },
    { id: 'a', y: 100, height: 160 },
  ]
  assert.deepEqual(restackChildPositions(items), [
    { id: 'a', y: 100 },
    { id: 'b', y: 300 },
  ])
})

test('restackChildPositions：被拖到画布区上方时收到上界，不进入卡片头部', () => {
  const items = [{ id: 'a', y: -500, height: 160 }]
  assert.deepEqual(restackChildPositions(items, { top: 176 }), [{ id: 'a', y: 176 }])
})

test('restackChildPositions：越出画布区底边时向上收，装不下也不越过上界', () => {
  // 底边 400、子块高 160 → 最高只能到 240
  assert.deepEqual(
    restackChildPositions([{ id: 'a', y: 999, height: 160 }], { top: 176, bottom: 400 }),
    [{ id: 'a', y: 240 }],
  )
  // 画布区比子块还矮时以「不进到卡片头部」为准
  assert.deepEqual(
    restackChildPositions([{ id: 'a', y: 999, height: 160 }], { top: 176, bottom: 200 }),
    [{ id: 'a', y: 176 }],
  )
})

test('restackChildPositions：空列表与脏数据不抛错', () => {
  assert.deepEqual(restackChildPositions(), [])
  assert.deepEqual(restackChildPositions([null, undefined, { y: 10 }]), [])
})

