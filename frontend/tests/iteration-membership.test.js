/**
 * 迭代体「从属关系」判定的行为用例（Node 内置 `node:test`，零新依赖）。
 *
 * 覆盖拖拽迁移节点时依赖的全部口径：
 * 1. 层级判据与画布连线拦截同源（跨层的边提交后端会建图失败，必须删）；
 * 2. 可见画布区的矩形与 `iterationChildExtent` / `canvasHeightOfCard` 严格一致
 *    （差一个边框宽度就会让「进容器」的命中判定与子块的实际约束错位）；
 * 3. 落点命中取最上层容器，且能排掉「容器拖进自己 / 自己的后代」；
 * 4. 挂载 / 脱离的坐标换算（相对 ↔ 绝对）与横向自动轴、纵向夹取；
 * 5. 不需要迁移的场合一律返回 null（画布页据此避免无谓的 `updateNode`）。
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_FLOW_DEFINITION } from '../src/views/workflow2/default-flow.js'
import {
  CARD_LAYOUT,
  ITERATION_LAYER,
  ITERATION_LAYOUT,
  canvasHeightOfCard,
  canvasTopOf,
  childXOfNodeWidth,
  estimatedTopAreaOf,
  expandIteration,
} from '../src/views/workflow2/utils/iteration.js'
import {
  absolutePositionOf,
  canvasRectOf,
  centeredPositionOf,
  clampChildPosition,
  containerAtPoint,
  containerBoxSizeOf,
  containerIdOf,
  descendantIdsOf,
  invalidEdgesOf,
  isCrossLevelEdge,
  isPointInRect,
  planMigration,
} from '../src/views/workflow2/utils/iteration-membership.js'

/** 默认流程的画布表示（迭代节点展开成「容器 + 子块」） */
const canvasOfDefaultFlow = () => {
  const nodes = []
  const edges = []
  for (const node of DEFAULT_FLOW_DEFINITION.nodes) {
    if (String(node.type) === '4') {
      const expanded = expandIteration(node)
      nodes.push(...expanded.nodes)
      edges.push(...expanded.edges)
      continue
    }
    nodes.push(node)
  }
  edges.push(...DEFAULT_FLOW_DEFINITION.edges.map((edge) => ({
    id: edge.id,
    source: edge.sourceNodeID,
    target: edge.targetNodeID,
  })))
  return { nodes, edges }
}

/** 默认流程里的迭代容器与它的子块：从定义派生，id 是数据、不是契约 */
const iterateNode = () => DEFAULT_FLOW_DEFINITION.nodes.find((node) => String(node.type) === '4')
const CONTAINER_ID = iterateNode().id
/** 迭代体首个子块（单个子块的断言用它） */
const CHILD_ID = iterateNode().blocks[0].id
/** 迭代体全部子块（与个数有关的断言用它） */
const CHILD_IDS = iterateNode().blocks.map((block) => block.id)

const containerOf = (nodes) => nodes.find((node) => node.id === CONTAINER_ID)

/* ===== 层级判据 ===== */

test('containerIdOf：子块返回容器 id，顶层节点返回 null', () => {
  const { nodes } = canvasOfDefaultFlow()
  assert.equal(containerIdOf(nodes, CHILD_ID), CONTAINER_ID)
  assert.equal(containerIdOf(nodes, '100001'), null)
  assert.equal(containerIdOf(nodes, '不存在'), null)
})

test('isCrossLevelEdge：同层与「容器 ↔ 自己的子块」都合法', () => {
  const { nodes } = canvasOfDefaultFlow()
  // 顶层 ↔ 顶层
  assert.equal(isCrossLevelEdge(nodes, '100001', '100002'), false)
  // 容器 ↔ 自己的子块：折叠后等价于子图的 START / END
  assert.equal(isCrossLevelEdge(nodes, CONTAINER_ID, CHILD_ID), false)
  assert.equal(isCrossLevelEdge(nodes, CHILD_ID, CONTAINER_ID), false)
})

test('isCrossLevelEdge：顶层 ↔ 别人的子块 判为跨层', () => {
  const { nodes } = canvasOfDefaultFlow()
  assert.equal(isCrossLevelEdge(nodes, '100001', CHILD_ID), true)
  assert.equal(isCrossLevelEdge(nodes, CHILD_ID, '900001'), true)
})

test('invalidEdgesOf：默认流程没有跨层连线', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  assert.deepEqual(invalidEdgesOf(nodes, edges), [])
})

test('invalidEdgesOf：节点入容器后，它连着外部的边变成跨层并被挑出来', () => {
  const { nodes, edges } = canvasOfDefaultFlow()
  // 取「紧邻容器的那个上游顶层节点」拖进容器：它入边来自更上游的顶层节点、出边正好指向容器
  const upstreamOfContainer = DEFAULT_FLOW_DEFINITION.edges
    .find((edge) => edge.targetNodeID === CONTAINER_ID).sourceNodeID
  const beforeIt = DEFAULT_FLOW_DEFINITION.edges
    .find((edge) => edge.targetNodeID === upstreamOfContainer).sourceNodeID
  const moved = nodes.map((node) => (
    node.id === upstreamOfContainer ? { ...node, parentNode: CONTAINER_ID } : node
  ))
  const invalid = invalidEdgesOf(moved, edges)
  // 只剩「顶层上游 → 子块」这条跨层；
  // 「子块 → 容器」此时变成了「容器 ↔ 自己的子块」，折叠后等价于子图的出口，仍然合法
  assert.deepEqual(invalid.map((edge) => edge.id), [`e-${beforeIt}-${upstreamOfContainer}`])
})

test('descendantIdsOf：把容器拖进自己的后代必须被识别出来', () => {
  const { nodes } = canvasOfDefaultFlow()
  assert.deepEqual([...descendantIdsOf(nodes, CONTAINER_ID)], CHILD_IDS)
  assert.deepEqual([...descendantIdsOf(nodes, '100001')], [])
})

/* ===== 画布区几何 ===== */

test('containerBoxSizeOf：读节点 style，缺失时退回 containerSizeOf 口径', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const size = containerBoxSizeOf(container, 0)
  assert.deepEqual(size, {
    width: parseFloat(container.style.width),
    height: parseFloat(container.style.height),
  })
  // 没有 style 时退回初始尺寸（448 是设计宽度）
  assert.equal(containerBoxSizeOf({ position: { x: 0, y: 0 } }, 0).width, 448)
})

test('canvasRectOf：与 iterationChildExtent / canvasHeightOfCard 的口径一致', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const topArea = estimatedTopAreaOf(container.data)
  const rect = canvasRectOf(container, topArea)

  const inset = ITERATION_LAYOUT.canvasInset + CARD_LAYOUT.borderWidth
  assert.equal(rect.x, container.position.x + inset)
  assert.equal(rect.y, container.position.y + canvasTopOf(topArea) + CARD_LAYOUT.borderWidth)
  assert.equal(rect.width, parseFloat(container.style.width) - inset * 2)
  // 画布区高度必须与「盒高 − 边框 − 顶部内容区 − 内缩」这条唯一口径完全相等
  assert.equal(rect.height, canvasHeightOfCard(parseFloat(container.style.height), topArea))
})

test('isPointInRect：含边界', () => {
  const rect = { x: 10, y: 20, width: 100, height: 50 }
  assert.equal(isPointInRect({ x: 10, y: 20 }, rect), true)
  assert.equal(isPointInRect({ x: 110, y: 70 }, rect), true)
  assert.equal(isPointInRect({ x: 9, y: 20 }, rect), false)
  assert.equal(isPointInRect({ x: 10, y: 71 }, rect), false)
})

test('containerAtPoint：画布区内命中，卡片头部 / 外部不命中', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const topArea = estimatedTopAreaOf(container.data)
  const rect = canvasRectOf(container, topArea)
  const center = { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }

  assert.equal(containerAtPoint(nodes, center), CONTAINER_ID)
  // 顶部内容区（变量行）之内不算「进容器」
  assert.equal(containerAtPoint(nodes, { x: center.x, y: container.position.y + 4 }), null)
  // 容器右侧之外
  assert.equal(containerAtPoint(nodes, { x: rect.x + rect.width + 50, y: center.y }), null)
})

test('containerAtPoint：exclude 能排掉自己与自己的后代（防止形成环）', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const rect = canvasRectOf(container, estimatedTopAreaOf(container.data))
  const center = { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }

  assert.equal(containerAtPoint(nodes, center, { excludeId: CONTAINER_ID }), null)
  assert.equal(containerAtPoint(nodes, center, { exclude: [CONTAINER_ID] }), null)
})

/* ===== 坐标换算与夹取 ===== */

test('absolutePositionOf：顶层节点原样，子块加上容器坐标', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const child = nodes.find((node) => node.id === CHILD_ID)

  assert.deepEqual(absolutePositionOf(nodes, container), container.position)
  assert.deepEqual(absolutePositionOf(nodes, child), {
    x: container.position.x + child.position.x,
    y: container.position.y + child.position.y,
  })
})

test('clampChildPosition：横向强制到自动轴，纵向夹进画布区', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const topArea = estimatedTopAreaOf(container.data)
  const rect = canvasRectOf(container, topArea)
  const width = parseFloat(container.style.width)
  // 画布区顶边（相对容器根）要带上卡片边框：extent 与画布区都以 border box 为基准
  const top = canvasTopOf(topArea) + CARD_LAYOUT.borderWidth

  const autoX = childXOfNodeWidth(width)
  // 落点远在容器上方 / 左侧：被夹到画布区左上角
  assert.deepEqual(
    clampChildPosition(container, { x: -999, y: -999 }, 160, topArea),
    { x: autoX, y: top },
  )
  // 落点远在下方：被夹到「画布区底边 − 子块高度」
  const bottomRelative = rect.y + rect.height - container.position.y
  assert.equal(clampChildPosition(container, { x: 0, y: 99999 }, 160, topArea).y, bottomRelative - 160)
  // 正常落点原样保留（只取整）
  assert.deepEqual(
    clampChildPosition(container, { x: 0, y: top + 60 }, 160, topArea),
    { x: autoX, y: top + 60 },
  )
})

/* ===== 迁移计划 ===== */

test('planMigration：顶层节点入容器，带齐 parentNode / extent / zIndex 与夹取后的相对坐标', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const topArea = estimatedTopAreaOf(container.data)
  const width = parseFloat(container.style.width)
  const rect = canvasRectOf(container, topArea)

  // 落点取画布区内部的偏上位置：不会被纵向夹到，便于断言原样保留
  const target = { x: rect.x + 40, y: rect.y + 30 }
  const outer = { id: '900002', type: '3', position: target }
  const plan = planMigration([...nodes, outer], outer.id, CONTAINER_ID, {
    topAreaHeightOf: () => topArea,
  })

  assert.equal(plan.parentNode, CONTAINER_ID)
  assert.equal(plan.zIndex, ITERATION_LAYER.child)
  assert.equal(plan.extent.range, 'parent')
  assert.equal(plan.position.x, childXOfNodeWidth(width))
  assert.equal(
    plan.position.y,
    Math.round(target.y - container.position.y),
    '画布区内部的落点应原样保留（只换算成相对坐标）',
  )
})

test('planMigration：脱离容器时坐标换成绝对，并清掉 extent / zIndex / parentNode', () => {
  const { nodes } = canvasOfDefaultFlow()
  const plan = planMigration(nodes, CHILD_ID, null)
  const container = containerOf(nodes)
  const child = nodes.find((node) => node.id === CHILD_ID)

  assert.equal(plan.parentNode, undefined)
  assert.equal(plan.extent, undefined)
  // zIndex 显式归 0：不清掉会让脱离出来的节点一直压在迭代体内部连线之上
  assert.equal(plan.zIndex, 0)
  assert.deepEqual(plan.position, {
    x: Math.round(container.position.x + child.position.x),
    y: Math.round(container.position.y + child.position.y),
  })
})

test('planMigration：入容器再脱离，纵坐标回到原处（横向回到自动轴）', () => {
  const { nodes } = canvasOfDefaultFlow()
  const container = containerOf(nodes)
  const topArea = estimatedTopAreaOf(container.data)
  const options = { topAreaHeightOf: () => topArea }

  // 落点要落在可见画布区的纵向范围内，否则会被夹到边界上（那样往返本来就会漂移）
  const rect = canvasRectOf(container, topArea)
  const outer = { id: '900002', type: '3', position: { x: 1200, y: rect.y + 30 } }
  const withOuter = [...nodes, outer]
  const attached = planMigration(withOuter, outer.id, CONTAINER_ID, options)
  const attachedNodes = withOuter.map((node) => (
    node.id === outer.id ? { ...node, ...attached } : node
  ))

  const detached = planMigration(attachedNodes, outer.id, null)
  assert.equal(detached.position.y, outer.position.y, '纵向落点不应在往返中漂移')
  assert.equal(detached.position.x, childXOfNodeWidth(parseFloat(container.style.width)) + container.position.x)
})

test('planMigration：不需要迁移的场合一律返回 null', () => {
  const { nodes } = canvasOfDefaultFlow()
  // 节点不存在
  assert.equal(planMigration(nodes, '不存在', CONTAINER_ID), null)
  // 已经在目标层（子块 → 它自己的容器 / 顶层节点 → 顶层）
  assert.equal(planMigration(nodes, CHILD_ID, CONTAINER_ID), null)
  assert.equal(planMigration(nodes, '100001', null), null)
  // 目标不是迭代容器
  assert.equal(planMigration(nodes, '100001', '100002'), null)
  // 容器拖进自己 / 自己的后代
  assert.equal(planMigration(nodes, CONTAINER_ID, CONTAINER_ID), null)
  assert.equal(planMigration(nodes, CONTAINER_ID, CHILD_ID), null)
})

test('planMigration：detachAt 让脱离出来的节点落在指针位置（子块当前坐标被 extent 夹在容器内）', () => {
  const { nodes } = canvasOfDefaultFlow()
  const plan = planMigration(nodes, CHILD_ID, null, { detachAt: { x: 300, y: 500 } })
  assert.deepEqual(plan.position, { x: 300, y: 500 })
  // 不传 detachAt 时维持原口径（容器坐标 + 子块坐标）
  const fallback = planMigration(nodes, CHILD_ID, null)
  assert.notDeepEqual(fallback.position, plan.position)
})

test('centeredPositionOf：按实测尺寸把节点中心摆到落点上', () => {
  const node = { dimensions: { width: 272, height: 120 } }
  assert.deepEqual(centeredPositionOf(node, { x: 300, y: 300 }), { x: 164, y: 240 })
  // 没有实测尺寸时按零尺寸处理（不产生 NaN）
  assert.deepEqual(centeredPositionOf({}, { x: 300, y: 300 }), { x: 300, y: 300 })
  assert.deepEqual(centeredPositionOf(null, { x: 10, y: 20 }), { x: 10, y: 20 })
})
