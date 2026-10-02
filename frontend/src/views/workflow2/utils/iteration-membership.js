/**
 * 节点在「顶层」与「迭代容器内部」之间的从属关系：判定、命中与迁移计划（纯函数，无 Vue 依赖）。
 *
 * 画布把迭代体物化成「容器 + `parentNode` 子块」，于是一个节点要么是顶层节点、要么是某个容器的
 * 子块。拖拽改变这个从属关系时，有三件事必须按同一套口径算，否则会出现「连线在、节点不在同一层」
 * 这种提交后端就失败的状态：
 * 1. **层级判据**（{@link isCrossLevelEdge}）：与画布连线拦截（`WorkflowView#handleConnect`）同源——
 *    跨层的边折叠成 `blocks` 后，其中一端已不在同一张图里，后端建图会直接失败；
 * 2. **画布区几何**（{@link canvasRectOf}）：子块的落点是否「进到了容器里」，判据就是可见画布区；
 * 3. **坐标语义**（{@link planMigration}）：子块的 `position` 是**相对容器**的，Vue Flow 在
 *    `parentNode` 变化时**不会**自动换算，必须由调用方把绝对坐标与相对坐标来回折算。
 */
import {
  CARD_LAYOUT,
  ITERATE_NODE_TYPE,
  ITERATION_LAYER,
  ITERATION_LAYOUT,
  canvasTopOf,
  childXOfNodeWidth,
  containerSizeOf,
  estimatedTopAreaOf,
  iterationChildExtent,
} from './iteration.js'

/** 默认的顶部内容区高度取值：实测值在 IterateNode 组件里，拿不到时用估算值（上界，恒安全） */
const defaultTopAreaHeightOf = (container) => estimatedTopAreaOf(container?.data)

/** 解析像素值（`'448px'` → 448）；解析不出来返回 null */
const toFinite = (value) => {
  const parsed = parseFloat(value)
  return Number.isFinite(parsed) ? parsed : null
}

/* ===== 从属关系 ===== */

/**
 * 节点所属的容器 id；顶层节点返回 null。
 *
 * @param {Array} nodes 画布节点
 * @param {string} nodeId 节点 id
 * @returns {string|null}
 */
export function containerIdOf(nodes = [], nodeId) {
  return (nodes ?? []).find((node) => node?.id === nodeId)?.parentNode ?? null
}

/**
 * 两个节点之间的连线是否**跨层**（折叠成 `blocks` 之后其中一端不在同一张图里）。
 *
 * 判据与 `WorkflowView#handleConnect` 完全一致：同层合法；「容器 ↔ 它自己的子块」也合法——
 * 容器在父图、子块在子图，折叠后这条边等价于子图的 START / END，是迭代体的入口 / 出口。
 *
 * @param {Array} nodes 画布节点
 * @param {string} source 起点节点 id
 * @param {string} target 终点节点 id
 * @returns {boolean}
 */
export function isCrossLevelEdge(nodes = [], source, target) {
  const sourceContainer = containerIdOf(nodes, source)
  const targetContainer = containerIdOf(nodes, target)
  if (sourceContainer === targetContainer) return false
  return !(sourceContainer === target || targetContainer === source)
}

/**
 * 全图上所有跨层的连线。
 *
 * 迁移一个节点只会影响与它相连的边，但整表扫一遍的代价可以忽略（节点数很小），
 * 换来的是「无论迁移路径怎样都不会漏删」——漏一条就是提交后端时建图失败。
 *
 * @param {Array} nodes 画布节点
 * @param {Array} edges 画布连线
 * @returns {Array} 跨层的边（可直接交给 `removeEdges`）
 */
export function invalidEdgesOf(nodes = [], edges = []) {
  return (edges ?? []).filter((edge) => isCrossLevelEdge(nodes, edge?.source, edge?.target))
}

/**
 * 某节点的全部后代 id（画布只展开一层迭代体，但这里按通用祖先链实现，多一层也不会算错）。
 *
 * @param {Array} nodes 画布节点
 * @param {string} rootId 根节点 id
 * @returns {Set<string>} 后代 id 集合（不含 rootId 自身）
 */
export function descendantIdsOf(nodes = [], rootId) {
  const descendants = new Set()
  let grew = true
  while (grew) {
    grew = false
    for (const node of nodes ?? []) {
      const parentId = node?.parentNode
      if (!parentId) continue
      if (descendants.has(node.id)) continue
      if (parentId === rootId || descendants.has(parentId)) {
        descendants.add(node.id)
        grew = true
      }
    }
  }
  return descendants
}

/* ===== 几何 ===== */

/**
 * 容器节点盒的尺寸：优先读节点 `style`（`expandIteration` 与节点工厂都会写入），
 * 缺失时退回 `containerSizeOf` 的初始口径（此时按「还没有子块」算，只作兜底）。
 *
 * @param {object} container 迭代容器节点
 * @param {number} topAreaHeight 顶部内容区高度
 * @returns {{width: number, height: number}} 单位 px
 */
export function containerBoxSizeOf(container, topAreaHeight = 0) {
  const width = toFinite(container?.style?.width)
  const height = toFinite(container?.style?.height)
  if (width !== null && height !== null) return { width, height }
  const fallback = containerSizeOf(0, topAreaHeight)
  return { width: width ?? fallback.width, height: height ?? fallback.height }
}

/**
 * 容器的**可见画布区**在画布坐标系下的矩形。
 *
 * 内缩口径与 {@link iterationChildExtent} 严格一致：基准是容器的 border box，左右 / 底部各内缩
 * `canvasInset + 边框`，顶部再让出「顶部内容区 + `canvasInset` + 边框」。
 * （CSS 里 `.iterate-canvas` 锚定在 card 的 padding box 内，所以要各加一个边框宽度。）
 *
 * @param {object} container 迭代容器节点
 * @param {number} topAreaHeight 顶部内容区高度
 * @returns {{x: number, y: number, width: number, height: number}} 画布坐标下的矩形
 */
export function canvasRectOf(container, topAreaHeight = 0) {
  const { width, height } = containerBoxSizeOf(container, topAreaHeight)
  const inset = ITERATION_LAYOUT.canvasInset + CARD_LAYOUT.borderWidth
  const top = canvasTopOf(topAreaHeight) + CARD_LAYOUT.borderWidth
  return {
    x: (Number(container?.position?.x) || 0) + inset,
    y: (Number(container?.position?.y) || 0) + top,
    width: Math.max(0, width - inset * 2),
    height: Math.max(0, height - top - inset),
  }
}

/** 点是否落在矩形内（含边界） */
export function isPointInRect(point, rect) {
  return point.x >= rect.x
    && point.x <= rect.x + rect.width
    && point.y >= rect.y
    && point.y <= rect.y + rect.height
}

/** 节点的绝对坐标（顶层节点本身就是绝对坐标；子块要加上容器的坐标） */
export function absolutePositionOf(nodes = [], node) {
  const parent = node?.parentNode
    ? (nodes ?? []).find((item) => item?.id === node.parentNode)
    : null
  return {
    x: (Number(node?.position?.x) || 0) + (Number(parent?.position?.x) || 0),
    y: (Number(node?.position?.y) || 0) + (Number(parent?.position?.y) || 0),
  }
}

/**
 * 节点的中心点（绝对画布坐标）：优先用 Vue Flow 已渲染的 `computedPosition`（拖拽时它就是当前
 * 位置），退回 `position` + 实测尺寸。给不了落点坐标的调用方（如触屏事件）兜底用。
 *
 * @param {object} node 画布节点
 * @returns {{x: number, y: number}}
 */
export function nodeCenterOf(node) {
  const x = Number(node?.computedPosition?.x ?? node?.position?.x) || 0
  const y = Number(node?.computedPosition?.y ?? node?.position?.y) || 0
  return {
    x: x + (Number(node?.dimensions?.width) || 0) / 2,
    y: y + (Number(node?.dimensions?.height) || 0) / 2,
  }
}

/**
 * 落点命中的迭代容器 id。
 *
 * 取**最上层**（节点数组里靠后、渲染层级更高）的命中容器：容器互相重叠时，视觉上压在上面的那个
 * 才是用户想放进去的。`exclude` 用来排掉「不能作为宿主」的容器——把容器拖进它自己或它的后代里
 * 会形成环。
 *
 * @param {Array} nodes 画布节点
 * @param {{x: number, y: number}} point 画布坐标（一般取指针位置）
 * @param {object} [options] 选项
 * @param {(container: object) => number} [options.topAreaHeightOf] 取容器顶部内容区高度
 * @param {Iterable<string>} [options.exclude] 需要排除的容器 id
 * @param {string} [options.excludeId] 单个需要排除的容器 id（等价于 exclude 只有一个元素）
 * @returns {string|null} 容器 id；没命中任何容器时返回 null（= 落在顶层）
 */
export function containerAtPoint(nodes = [], point, options = {}) {
  const { topAreaHeightOf = defaultTopAreaHeightOf, exclude = [], excludeId = null } = options
  const excluded = new Set(exclude)
  if (excludeId) excluded.add(excludeId)

  const containers = (nodes ?? []).filter(
    (node) => node?.type === ITERATE_NODE_TYPE && !excluded.has(node.id),
  )
  for (let index = containers.length - 1; index >= 0; index -= 1) {
    const container = containers[index]
    if (isPointInRect(point, canvasRectOf(container, topAreaHeightOf(container)))) {
      return container.id
    }
  }
  return null
}

/* ===== 迁移 ===== */

/** 有效的高度：优先实测（`dimensions`），其次节点 style，最后退回布局口径的预估值 */
const heightOf = (node) => {
  const measured = Number(node?.dimensions?.height)
  if (Number.isFinite(measured) && measured > 0) return measured
  const styled = toFinite(node?.style?.height)
  if (styled !== null && styled > 0) return styled
  return ITERATION_LAYOUT.childHeight
}

/**
 * 把子块的落点夹进容器的可见画布区（相对容器根）：横向是自动轴，纵向保留用户摆放。
 *
 * 横向必须强制（与 `childXOfNodeWidth` 一致）：画布区宽度刚好等于「标记 + 间距 + 子块」，
 * 横向上没有可摆的余地；而且容器的宽度变化 watcher 只在**宽度变化时**才重新居中，
 * 新插入的子块如果带着指针的 x，会在那里一直停着，与其它子块对不齐。
 *
 * @param {object} container 迭代容器节点
 * @param {{x: number, y: number}} relative 相对容器根的落点
 * @param {number} childHeight 子块高度（用于纵向夹取的上界）
 * @param {number} topAreaHeight 顶部内容区高度
 * @returns {{x: number, y: number}} 相对容器根的坐标
 */
export function clampChildPosition(container, relative, childHeight, topAreaHeight = 0) {
  const { width, height } = containerBoxSizeOf(container, topAreaHeight)
  const inset = ITERATION_LAYOUT.canvasInset + CARD_LAYOUT.borderWidth
  const top = canvasTopOf(topAreaHeight) + CARD_LAYOUT.borderWidth
  const bottom = height - inset
  const maxY = Math.max(top, bottom - Math.max(childHeight, 0))
  return {
    x: childXOfNodeWidth(width),
    y: Math.round(Math.min(Math.max(Number(relative?.y) || 0, top), maxY)),
  }
}

/**
 * 把节点的**中心**摆到指定点上（返回节点左上角应处的坐标）。
 *
 * 用于「脱离容器」：子块被 `extent` 夹在容器内，它的当前坐标不是用户想要的落点——
 * 真正的意图是「松手时指针在哪里」，所以以指针为节点的中心反推左上角。
 *
 * @param {object} node 画布节点（用 Vue Flow 的实测 `dimensions`，拿不到时按零尺寸处理）
 * @param {{x: number, y: number}} point 画布坐标（通常是指针位置）
 * @returns {{x: number, y: number}}
 */
export function centeredPositionOf(node, point) {
  return {
    x: Math.round((Number(point?.x) || 0) - (Number(node?.dimensions?.width) || 0) / 2),
    y: Math.round((Number(point?.y) || 0) - (Number(node?.dimensions?.height) || 0) / 2),
  }
}

/**
 * 迁移计划：把 `nodeId` 迁到 `targetContainerId`（传 null 表示迁到顶层）。
 *
 * 只**计算**要写回 Vue Flow 的字段，不改任何数据——画布页拿到后自己 `updateNode`，
 * 这样迁移逻辑可以脱离 Vue Flow 单测。
 *
 * 挂载 / 脱离各自的关键点：
 * - **入容器**：`position` 换成相对坐标（绝对坐标 − 容器坐标）并夹进画布区；同时必须带上
 *   `extent`（约束在可见画布区内）与 `zIndex`（否则子块会被不透明的容器卡片盖住）；
 * - **出容器**：`position` 换成绝对坐标（容器坐标 + 相对坐标），并清掉 `parentNode` / `extent` /
 *   `zIndex`——漏掉 `extent` 的话，节点看着已经出来了，下一次拖拽又会被夹回容器里。
 *
 * @param {Array} nodes 画布节点
 * @param {string} nodeId 被迁移的节点 id
 * @param {string|null} targetContainerId 目标容器 id；null = 顶层
 * @param {object} [options] 选项
 * @param {(container: object) => number} [options.topAreaHeightOf] 取容器顶部内容区高度
 * @param {{x: number, y: number}} [options.detachAt] **脱离容器时**的绝对落点。子块被 `extent`
 *   夹在容器内，其当前坐标不是用户想要的落点（它想放到的位置在容器外），所以由调用方给指针位置
 * @returns {{parentNode: (string|undefined), extent: (object|undefined), zIndex: number, position: {x: number, y: number}}|null}
 *   返回 null 表示「不需要迁移」（节点不存在、目标非法、已经在目标层、或要把容器拖进自己 / 后代里）
 */
export function planMigration(nodes = [], nodeId, targetContainerId, options = {}) {
  const { topAreaHeightOf = defaultTopAreaHeightOf, detachAt = null } = options
  const list = nodes ?? []
  const node = list.find((item) => item?.id === nodeId)
  if (!node) return null

  const currentParent = node.parentNode ?? null
  const nextParent = targetContainerId ?? null
  if (currentParent === nextParent) return null

  const container = nextParent
    ? list.find((item) => item?.id === nextParent && item.type === ITERATE_NODE_TYPE)
    : null
  if (nextParent && !container) return null
  // 自嵌套（含把容器拖进自己的后代）会形成环，直接拒绝
  if (container && (container.id === nodeId || descendantIdsOf(list, nodeId).has(container.id))) {
    return null
  }

  const absolute = absolutePositionOf(list, node)
  if (!container) {
    const position = detachAt ?? absolute
    return {
      parentNode: undefined,
      extent: undefined,
      zIndex: 0,
      position: { x: Math.round(position.x), y: Math.round(position.y) },
    }
  }

  const topAreaHeight = topAreaHeightOf(container)
  return {
    parentNode: container.id,
    extent: iterationChildExtent(topAreaHeight),
    zIndex: ITERATION_LAYER.child,
    position: clampChildPosition(
      container,
      { x: absolute.x - (Number(container.position?.x) || 0), y: absolute.y - (Number(container.position?.y) || 0) },
      heightOf(node),
      topAreaHeight,
    ),
  }
}
