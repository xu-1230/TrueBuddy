import assert from 'node:assert/strict'
import test from 'node:test'
import {
  ITERATE_ENTRY_HANDLE,
  ITERATE_EXIT_HANDLE,
  ITERATION_EDGE_STYLE,
  ITERATION_LAYER,
  ITERATION_LAYOUT,
  canvasTopOf,
  cardTopAreaHeightOf,
  childPositionOf,
  internalEdgePatchOf,
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

test('迁移后变为迭代体内部的连线：归一化补丁补齐 zIndex 与内侧 handle', () => {
  const nodes = canvas()
  // 「顶层状态下画的、迁移后才变成内部」的连线：没有 zIndex，handle 用的是外侧的 out / in
  const topLevelEdge = {
    id: 'e-a-c1',
    source: 'a',
    target: 'c1',
    sourceHandle: 'out',
    targetHandle: 'in',
    style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  }

  const patch = internalEdgePatchOf(nodes, topLevelEdge)
  assert.ok(patch, '两端都落在迭代体内的连线必须被归一化')
  assert.equal(patch.zIndex, ITERATION_EDGE_STYLE.zIndex, '补齐 zIndex（否则被容器卡片整段盖住）')
  assert.equal(patch.targetHandle, ITERATE_EXIT_HANDLE, '连到容器的那一端改用内侧 handle')
  assert.equal(patch.sourceHandle, undefined, '子块那一端保持它自己的出连接点')

  // 归一化之后与另一个来源（展开定义）口径一致；再取补丁为空 —— 所以画布页可以无脑整表过一遍
  const normalized = { ...topLevelEdge, ...patch }
  assert.deepEqual(
    {
      zIndex: normalized.zIndex,
      animated: normalized.animated,
      style: normalized.style,
      markerEnd: normalized.markerEnd,
    },
    {
      zIndex: ITERATION_EDGE_STYLE.zIndex,
      animated: ITERATION_EDGE_STYLE.animated,
      style: ITERATION_EDGE_STYLE.style,
      markerEnd: ITERATION_EDGE_STYLE.markerEnd,
    },
  )
  assert.equal(internalEdgePatchOf(nodes, normalized), null, '已合规的内部边不再产生补丁')

  // 容器在起点一侧：改的是 sourceHandle
  const entryPatch = internalEdgePatchOf(nodes, { id: 'e-c1-a', source: 'c1', target: 'a' })
  assert.equal(entryPatch.sourceHandle, ITERATE_ENTRY_HANDLE)

  // 同容器的两个子块之间：只需补内部边样式，不动 handle
  const siblingPatch = internalEdgePatchOf(nodes, { id: 'e-a-b', source: 'a', target: 'b' })
  assert.deepEqual(siblingPatch, {
    zIndex: ITERATION_EDGE_STYLE.zIndex,
    style: ITERATION_EDGE_STYLE.style,
    animated: ITERATION_EDGE_STYLE.animated,
    markerEnd: ITERATION_EDGE_STYLE.markerEnd,
  })

  // 顶层连线与脏数据：不处理
  assert.equal(internalEdgePatchOf(nodes, { id: 'e-start-end', source: 'start', target: 'end' }), null)
  assert.equal(
    internalEdgePatchOf(nodes, { id: 'e-start-c1', source: 'start', target: 'c1' }),
    null,
    '主链路连入 / 连出容器不算内部边，不能被套上内部边样式',
  )
  assert.equal(internalEdgePatchOf(nodes, null), null)
  assert.equal(internalEdgePatchOf([], { id: 'e-a-b', source: 'a', target: 'b' }), null, '空画布不报错')
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
