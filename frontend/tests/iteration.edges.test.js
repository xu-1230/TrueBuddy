import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ITERATION_EDGE_STYLE,
  ITERATION_LAYER,
  ITERATION_LAYOUT,
  canvasTopOf,
  cardTopAreaHeightOf,
  childPositionOf,
  iterationChildExtent,
  iterationOwnerOf,
} from '../src/views/workflow2/utils/iteration.js'

/**
 * 画布：容器 c1 带子块 a / b，容器 c2 带子块 c，另有顶层 start / end。
 * 与 `collapseIterations` 的折叠口径共用同一份判定（`belongsToContainer`）。
 */
const canvas = () => [
  { id: 'c1', type: '4' },
  { id: 'a', type: '3', parentNode: 'c1' },
  { id: 'b', type: '3', parentNode: 'c1' },
  { id: 'c2', type: '4' },
  { id: 'c', type: '3', parentNode: 'c2' },
  { id: 'start', type: '1' },
  { id: 'end', type: '2' },
]

test('迭代体内部的连线归到所属容器', () => {
  const nodes = canvas()

  assert.equal(iterationOwnerOf(nodes, 'a', 'b'), 'c1', '同一容器的两个子块')
  assert.equal(iterationOwnerOf(nodes, 'c1', 'a'), 'c1', '容器 → 自己的子块（迭代体入口）')
  assert.equal(iterationOwnerOf(nodes, 'a', 'c1'), 'c1', '子块 → 容器（迭代体出口）')
  assert.equal(iterationOwnerOf(nodes, 'c2', 'c'), 'c2', '另一个容器互不干扰')
})

test('跨迭代体边界的连线不算迭代体内部边', () => {
  const nodes = canvas()

  assert.equal(iterationOwnerOf(nodes, 'start', 'end'), null, '顶层 ↔ 顶层')
  assert.equal(iterationOwnerOf(nodes, 'start', 'c1'), null, '顶层 → 容器（主链路连入）')
  assert.equal(iterationOwnerOf(nodes, 'c1', 'end'), null, '容器 → 顶层（主链路连出）')
  assert.equal(iterationOwnerOf(nodes, 'start', 'a'), null, '顶层 → 迭代体子块（跨边界）')
  assert.equal(iterationOwnerOf(nodes, 'a', 'c'), null, '不同容器的子块')
  assert.equal(iterationOwnerOf([], 'a', 'b'), null, '空画布')
})

test('内部连线样式必须带层级，否则会被容器卡片整段盖住', () => {
  assert.equal(ITERATION_EDGE_STYLE.zIndex, ITERATION_LAYER.edge)
  assert.ok(ITERATION_EDGE_STYLE.zIndex > 0, 'zIndex 必须为正，连线才会渲染到节点层之上')
  assert.ok(
    ITERATION_LAYER.edge < ITERATION_LAYER.child,
    '内部连线要低于子块，否则线会画在子块卡片上面',
  )
})

test('标记与子块之间那段内部连线足够长（可点性只能靠长度）', () => {
  const topArea = cardTopAreaHeightOf(1)
  const [first, second] = [childPositionOf(0, 2, topArea), childPositionOf(1, 2, topArea)]
  const entryMarkerRight = ITERATION_LAYOUT.canvasInset + ITERATION_LAYOUT.paddingX + ITERATION_LAYOUT.markerWidth
  const innerEdgeLength = first.x - entryMarkerRight

  assert.equal(innerEdgeLength, ITERATION_LAYOUT.markerGap, '入口标记右缘到子块左缘的距离就是内部连线长度')
  assert.ok(innerEdgeLength >= 24, '连线过短会点不中，再连同一对节点又提示「已有连线」')
  assert.equal(second.x, first.x, '同容器的子块 x 对齐，内部连线保持水平')
  assert.ok(canvasTopOf(topArea) > 0, '画布区起点为正，子块落位在卡片内容区之下')
  assert.ok(
    iterationChildExtent(topArea).padding[0] >= canvasTopOf(topArea),
    '拖拽约束的上边界不能高于画布区起点，否则子块能压到变量行上',
  )
})
