/**
 * 迭代节点布局 / 折叠口径的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 覆盖修复后的期望行为：尺寸下限装得下子块、节点拉宽后子块重新居中、悬空 parentNode 不再丢失、
 * 以及「嵌套迭代体只展开一层但数据不丢」这条已知限制。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ITERATION_LAYOUT,
  canvasTopOf,
  childPositionOf,
  childXOf,
  childXOfNodeWidth,
  collapseIterations,
  containerSizeOf,
  expandIteration,
  minNodeSizeOf,
} from '../src/views/workflow2/utils/iteration.js'

/** 默认画布的卡片变量行数：输入 1 + 迭代变量 1 + 输出 1 */
const ROW_COUNT = 3

/** 设计宽度下的节点宽度（子块 + 两个标记 + 内边距与内缩） */
const DESIGN_WIDTH = containerSizeOf(1, ROW_COUNT).width

test('尺寸下限装得下全部子块（与初始尺寸同源）', () => {
  const min = minNodeSizeOf(ROW_COUNT, 3)
  const initial = containerSizeOf(3, ROW_COUNT)

  assert.deepEqual(min, initial)

  // 画布区高度必须装得下 3 个子块 + 上下内边距，而不是恒等于 canvasMinHeight
  const canvasHeightAtMin = min.height - canvasTopOf(ROW_COUNT) - ITERATION_LAYOUT.canvasInset
  const needed = ITERATION_LAYOUT.canvasPaddingY * 2
    + 3 * ITERATION_LAYOUT.childHeight
    + 2 * ITERATION_LAYOUT.childGap
  assert.equal(canvasHeightAtMin, needed)
  assert.ok(canvasHeightAtMin > ITERATION_LAYOUT.canvasMinHeight)
})

test('节点拉宽 / 缩窄后子块横向重新居中（x 是自动轴）', () => {
  assert.equal(childXOf(), 76, '设计口径下的子块 x = 内缩 + 内边距 + 标记宽 + 间距')
  assert.equal(childXOfNodeWidth(DESIGN_WIDTH), 76, '设计宽度下与初始布局一致')

  const widened = childXOfNodeWidth(800)
  assert.equal(widened, ITERATION_LAYOUT.canvasInset
    + (800 - ITERATION_LAYOUT.canvasInset * 2 - ITERATION_LAYOUT.childWidth) / 2)
  assert.ok(widened > 76, '拉宽后子块跟着往右走（不再固定在设计宽度的位置）')

  const narrowed = childXOfNodeWidth(DESIGN_WIDTH + 20)
  assert.ok(narrowed > 76 && narrowed < widened, '宽度变化时 x 单调跟随')
})

test('纵向口径：子块区在画布区内垂直居中', () => {
  const childCount = 3
  const canvasTop = canvasTopOf(ROW_COUNT)
  const canvasHeight = containerSizeOf(childCount, ROW_COUNT).height - canvasTop - ITERATION_LAYOUT.canvasInset
  const blockHeight = childCount * ITERATION_LAYOUT.childHeight
    + (childCount - 1) * ITERATION_LAYOUT.childGap
  const paddingTop = childPositionOf(0, childCount, ROW_COUNT).y - canvasTop
  const paddingBottom = canvasHeight - paddingTop - blockHeight

  assert.ok(paddingTop >= ITERATION_LAYOUT.canvasPaddingY, '上方留白不低于画布区内边距')
  assert.equal(paddingTop, paddingBottom, '上下留白相等（垂直居中）')
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
