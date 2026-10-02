<script setup>
/**
 * 迭代节点卡片（type = 4）：就是一张与其它节点同款的普通卡片，卡片内部再嵌一块「子节点画布」。
 *
 * `.node-card` 铺满节点盒（= 整个节点）：图标 + 名称 + 「迭代」标识 + 遍历对象 + 右侧徽标 +
 * 输入 / 迭代变量 / 输出三组变量行；其下是**嵌在卡片里的子节点画布**（`.iterate-canvas`：左右与
 * 底部各内缩 canvasInset，浅色底 + 边框 + 圆角），画布区内横向是「迭代开始」**图标胶囊**
 * （左侧，`HomeFilled`）→ 迭代体子节点 → 「迭代结束」图标胶囊（右侧，`SwitchButton`），
 * 两者边框 / 底色与其它节点卡片一致；三者**上下居中**（同一水平线）。运行状态、
 * 第 n/N 轮进度与汇总产出在**卡片正下方**（与其它节点的结果面板同一形态，面板与卡片同宽）。
 *
 * 三点实现约定：
 * 1. 尺寸是「活的」：初始尺寸由 `expandIteration` 按 `containerSizeOf(子块数, 顶部内容区高度)` 写进节点 style，
 *    用户可拖住卡片右下角把手改变（`updateNode` 更新 style，下限 `minNodeSizeOf(顶部高度, 子块数)`——
 *    缩到装不下子块就没有意义）。卡片 `height: 100%` 跟随节点盒，画布区以 `top/bottom` 锚定在卡片内随之伸缩。
 *    改尺寸**不重排子块**：宽度变化只让子块横向重新居中（x 是自动轴），高度变化保留用户摆放的 y；
 * 2. 「迭代开始 / 结束」是画布区左右两侧的**视觉标记**（不是画布节点），**固定在画布区的垂直中点**
 *    （CSS 锚定，不随子块拖动移动；子块区在子块增删时按真实高度重新垂直居中）；
 *    迭代体内部连线由放在标记元素内部的内侧 handle（`iterate-in` / `iterate-out`）承载
 *    （DOM 锚定），折叠回后端时不会多出节点；
 * 3. 顶部内容区高度**以实际渲染为准**（`ResizeObserver` 量 `.node-content`），不按变量个数推算——
 *    变量胶囊是 flex-wrap，一行能并排多个，「变量个数」不等于「渲染行数」。实测高度变化时按同一个
 *    delta 调整节点盒高度，使画布区高度守恒（见 `utils/iteration` 的 `cardHeightAfterContentChange`），
 *    所以「同一行内新增变量」不会带动画布区变化；子块不各自展示运行结果（见 `utils/use-nested-node`），
 *    迭代体各轮的产出统一由结果区体现。内层嵌套迭代体（自身仍带 `blocks`）画布上不展开，
 *    卡片上以「嵌套 N」徽标标明（数据不丢，提交后端时原样带回）。
 */
import { computed, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { HomeFilled, RefreshRight, SwitchButton } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { ITERATION_DROP_TARGET_KEY, RUN_STATE_KEY } from '../utils/run-inject'
import { iterationProgressOf, nodeRuntimeOf } from '../utils/run-state'
import {
  ITERATE_ENTRY_HANDLE,
  ITERATE_EXIT_HANDLE,
  ITERATION_LAYOUT,
  canvasHeightOf,
  canvasHeightOfCard,
  canvasTopOf,
  cardAnchorYOf,
  cardHeightAfterContentChange,
  childXOfNodeWidth,
  estimatedTopAreaOf,
  iterationChildExtent,
  layoutIteration,
  minNodeSizeOf,
  restackChildPositions,
} from '../utils/iteration'
import { unboundNamesOf } from '../utils/variable-sources'

// 与 Vue Flow 内置节点保持一致：不把 label/selected 等透传属性渲染到 DOM 上
defineOptions({ name: 'IterateNode', inheritAttrs: false })

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, default: () => ({}) },
})

const { getNodes, updateNode, getViewport } = useVueFlow()

/** 运行态由画布页 provide；卡片可能在无运行态的上下文复用，故允许缺省 */
const runState = inject(RUN_STATE_KEY, null)
const runtime = computed(() => (runState ? nodeRuntimeOf(runState.value, props.id) : null))

/**
 * 拖拽命中高亮：画布页在 `node-drag` 期间按落点算出「松手会进哪个容器」。
 *
 * 消费端允许缺省（卡片可能在无注入的上下文被复用），缺省时不高亮。
 */
const dropTargetId = inject(ITERATION_DROP_TARGET_KEY, null)
const isDropTarget = computed(() => Boolean(dropTargetId?.value === props.id))

/** 迭代进度：`{ done, total }`，由子块的 node 事件累计（见 utils/run-state） */
const progress = computed(() => (runState ? iterationProgressOf(runState.value, props.id) : null))

/* ===== 顶部内容区高度：以实际渲染为准，不按变量个数推算 ===== */

/** 标题栏 + 三组变量行所在的**流式容器**（ResizeObserver 的观测对象） */
const contentRef = ref(null)

/**
 * 实测的顶部内容区高度（px）；`null` = 尚未测量，此时退回估算值。
 *
 * 为什么必须测量：变量胶囊是 `flex-wrap`（一行能并排多个，见 components/VariableTags.vue），
 * 「变量个数」≠「实际渲染行数」——按个数推算会让卡片在「同一行内新增变量」时凭空长高，
 * 多出来的高度全部落进画布区，画布区高度随之漂移（这正是本次要修的问题）。
 * 换行位置取决于卡片宽度、字体度量与胶囊文本宽度，纯计算无法与 DOM 严格一致，测量才是可靠来源。
 *
 * 观测对象刻意选**流式内容**：它的高度不受卡片高度影响，所以回调里改卡片高度不会再次触发观测，
 * 天然收敛、不会抖动。
 */
const measuredTopArea = ref(null)

/**
 * 顶部内容区高度：实测值优先。
 *
 * 未测到（首帧）时用 {@link estimatedTopAreaOf} 的估算值兜底 —— 它是实测值的**上界**
 * （按变量个数算行数恒 ≥ 实际行数），所以首帧绝不会出现装不下子块，挂载后最多小幅收敛一次。
 */
const topAreaHeight = computed(() => measuredTopArea.value ?? estimatedTopAreaOf(props.data))

const inputVars = computed(() => props.data?.inputs ?? [])
const paramVars = computed(() => props.data?.variableParameters ?? [])
const outputVars = computed(() => props.data?.outputs ?? [])

/**
 * 未绑定取值的变量名（交给 VariableTags 打告警色），口径与大模型卡片、运行前校验同一份。
 *
 * 迭代容器同样要取值：输入是迭代源（`IterateNode#readItems`），迭代变量在每轮由
 * `buildRoundInput` 从父状态读取——没绑定就是取不到值（0 轮 / 注入 `null`），不是「迭代节点没有
 * 取值这个概念」。输出是本节点产出的，不存在「没取值」。
 */
const unboundInputNames = computed(() => unboundNamesOf(inputVars.value))
const unboundParamNames = computed(() => unboundNamesOf(paramVars.value))

/** 该组变量是否有可见行（空名不可见，与 VariableTags 的过滤口径一致） */
const hasVisible = (list) =>
  (Array.isArray(list) ? list : []).some((item) => (typeof item === 'string' ? item : item?.name))

const hasParams = computed(() => hasVisible(paramVars.value))
const hasOutputs = computed(() => hasVisible(outputVars.value))

/** 产出变量个数：未运行时的徽标文案 */
const outputCount = computed(() => outputVars.value.length)

/**
 * 已完成轮次：取得到总轮数时不超过它（兼容旧后端时按子块事件累加，含多个子块会多算），
 * 取不到总轮数（迭代源产出还没到）时不设上限。
 */
const doneRounds = computed(() => {
  const done = progress.value?.done ?? 0
  const total = progress.value?.total ?? 0
  return total ? Math.min(done, total) : done
})

/** 进度文案：总数未知（迭代源产出还没到）时只显示已完成轮次 */
const progressText = computed(() => {
  const total = progress.value?.total ?? 0
  if (!doneRounds.value && !total) return ''
  return total ? `${doneRounds.value}/${total} 轮` : `${doneRounds.value} 轮`
})

/** 卡片右上角徽标：优先显示进度，未运行时退化为产出变量个数 */
const badgeText = computed(
  () => progressText.value || (outputCount.value ? `${outputCount.value} 个产出` : ''),
)

/** 进度条百分比：与进度文案取同一个「已完成轮次」，两者不会出现口径不一致 */
const percent = computed(() => {
  const total = progress.value?.total ?? 0
  return total ? Math.min(100, Math.round((doneRounds.value / total) * 100)) : 0
})

/* ===== 画布区与标记的锚定（标记固定在画布区垂直中点，不随子块移动；随节点尺寸伸缩） ===== */

/** 标记尺寸来自 utils/iteration（子块 / 画布区宽度的推导依赖同一份常量） */
const markerBox = {
  width: `${ITERATION_LAYOUT.markerWidth}px`,
  height: `${ITERATION_LAYOUT.markerHeight}px`,
}

/** 迭代节点自身（读 style 用：拖拽改尺寸与变量行数联动都要从这里取当前尺寸） */
const container = computed(() => getNodes.value.find((node) => node.id === props.id))

/** 迭代体子块（响应式：拖动 / 增删子块都会更新） */
const childrenInCanvas = computed(() => getNodes.value.filter((node) => node.parentNode === props.id))

/**
 * 画布区的真实高度：由「卡片盒高 − 上下边框 − 顶部内容区 − 底部内缩」换算
 * （口径见 {@link canvasHeightOfCard}，与 CSS 的锚定方式严格对应）。
 * 节点盒还没写高度时退回画布区自身口径，保证子块居中不会算成 0。
 */
const canvasHeight = computed(() => {
  const boxHeight = parseFloat(container.value?.style?.height) || 0
  if (!boxHeight) return canvasHeightOf(childrenInCanvas.value.length)
  return canvasHeightOfCard(boxHeight, topAreaHeight.value)
})

/** 子块的真实渲染高度（尚未测量时退回预估高度） */
const childHeightOf = (child) => child.dimensions?.height ?? ITERATION_LAYOUT.childHeight

/**
 * 子块纵向**重叠消解**：只把压在前一个子块上的往后顺延，**不**重排、**不**垂直居中。
 *
 * 子块可以由用户拖进来（落点就是指针位置），摆放必须尊重用户的意图；`layoutIteration` 的
 * 「按 index 重排 + 垂直居中」那一套会把拖进来的子块弹回队列里，所以这里换成
 * {@link restackChildPositions}——只有真发生纵向重叠才动位置，默认流程（`expandIteration`
 * 已经按 `childPositionOf` 排好）走一遍结果不变。
 *
 * <p>**节点尺寸变化不触发重排**：用户把子块拖到别处后再改尺寸，摆放必须保留——
 * 画布区只是跟着变大变小，不能把用户摆好的子块强行拉回居中位置（旧实现把画布区高度也放进触发键里，
 * 拖拽把手每帧都会把子块拽回原位）。
 */
const restackChildren = () => {
  const list = childrenInCanvas.value
  if (!list.length) return

  const top = canvasTopOf(topAreaHeight.value)
  const next = restackChildPositions(
    list.map((child) => ({
      id: child.id,
      y: child.position?.y ?? 0,
      height: childHeightOf(child),
    })),
    { top, bottom: top + canvasHeight.value },
  )

  const byId = new Map(list.map((child) => [child.id, child]))
  next.forEach((item) => {
    const child = byId.get(item.id)
    if (!child) return
    // 只在确实挪动了才写回：避免每次都让 Vue Flow 重跑一轮位置夹取
    if (Math.abs((child.position?.y ?? 0) - item.y) <= 1) return
    updateNode(child.id, { position: { x: child.position?.x ?? 0, y: item.y } })
  })
}

/** 子块集合与各自高度：增删子块 / 子块内容变化时消解重叠（键里刻意不含节点尺寸） */
watch(
  () => childrenInCanvas.value
    .map((child) => `${child.id}:${Math.round(childHeightOf(child))}`)
    .join('|'),
  restackChildren,
)

/** 子块 id 集合（排序后拼串）：只在子块增删时变化，子块移动 / 改尺寸不触发 */
const childrenKey = computed(() => childrenInCanvas.value.map((child) => child.id).sort().join('|'))

/**
 * 子块增删后重算容器尺寸：{@link layoutIteration} 是尺寸口径的唯一入口（只增不减，
 * 不会把用户拖出来的尺寸弹回初始值）。只写回容器的 `style`——子块的纵坐标仍由
 * {@link restackChildren} 按**真实渲染高度**重排，比 `containerSizeOf` 的预估值更准。
 */
watch(childrenKey, () => {
  const current = container.value
  if (!current) return
  // 传实测顶部高度：用估算值会让下限偏大，把已经收敛到真实高度的卡片重新撑大
  const resized = layoutIteration(getNodes.value, props.id, topAreaHeight.value)
    .find((node) => node.id === props.id)
  const style = resized?.style
  if (!style) return
  if (style.width === current.style?.width && style.height === current.style?.height) return
  updateNode(props.id, { style })
})

/** 节点盒的真实宽度（拖拽把手改宽度后跟随；未写 style 时为 0） */
const boxWidth = computed(() => parseFloat(container.value?.style?.width) || 0)

/**
 * 节点宽度变化（拖拽把手改宽度）时子块横向**重新居中**：
 * x 是自动轴（横向只有「两个标记之间居中」这一个正确位置），y 保留用户摆放。
 */
watch(boxWidth, (width) => {
  if (!width) return
  const x = childXOfNodeWidth(width)
  childrenInCanvas.value.forEach((child) => {
    if (child.position?.x === x) return
    updateNode(child.id, { position: { x, y: child.position?.y ?? 0 } })
  })
})

/** 该迭代节点自身仍带 blocks（内层嵌套迭代体，画布上不展开）时的提示徽标 */
const nestedBlockCount = computed(() => (container.value?.blocks?.length ?? 0))

/** 迭代开始标记：画布区左侧，垂直位置固定在画布区中点（CSS 锚定，不随子块拖动移动） */
const entryMarkerStyle = computed(() => ({
  ...markerBox,
  left: `${ITERATION_LAYOUT.paddingX}px`,
}))

/** 迭代结束标记：画布区右侧，垂直位置固定在画布区中点 */
const exitMarkerStyle = computed(() => ({
  ...markerBox,
  right: `${ITERATION_LAYOUT.paddingX}px`,
}))

/** 外侧连接点贴在卡片左右边缘，y 对齐顶部内容区中线（覆盖 Vue Flow 默认的「整张卡片 50%」） */
const anchorStyle = computed(() => ({ top: `${cardAnchorYOf(topAreaHeight.value)}px` }))

/** 画布区：嵌在卡片内、上下锚定（top = 顶部内容区 + 内缩，bottom = 内缩），高度随卡片伸缩 */
const canvasStyle = computed(() => ({
  left: `${ITERATION_LAYOUT.canvasInset}px`,
  right: `${ITERATION_LAYOUT.canvasInset}px`,
  top: `${canvasTopOf(topAreaHeight.value)}px`,
  bottom: `${ITERATION_LAYOUT.canvasInset}px`,
}))

/**
 * 顶部内容区高度变化时（抽屉里增删变量、或首次测到真实高度）：
 * 按**实测 delta** 同步调整节点盒高度，使画布区高度（= 盒高 − 边框 − 顶部高 − 内缩）保持不变，
 * 子块与标记不会纵向跳动。
 *
 * delta 由实测值算出，所以「同一行内新增变量」（真实高度没变）时 delta 为 0，什么都不动；
 * 首帧估算值偏大时 delta 为负，卡片会**回缩**到真实内容高度——这是正确行为，不是抖动。
 */
watch(topAreaHeight, (next, prev) => {
  // 画布区顶部随顶部内容区变化：子块的拖拽约束（= 画布区）必须同步刷新，
  // 否则约束范围会与可见画布区错位（拖拽被夹回、或压到变量行上）
  const nextExtent = iterationChildExtent(next)
  childrenInCanvas.value.forEach((child) => {
    // 只在没对齐时写回：避免每次变化都让 Vue Flow 重跑一轮位置夹取
    if (child.extent?.range === 'parent' && child.extent.padding?.[0] === nextExtent.padding[0]) return
    updateNode(child.id, { extent: nextExtent })
  })

  // 子块坐标是相对**节点根**的绝对值，而画布区顶部平移了 delta：子块必须同步平移，
  // 才能在画布区内纹丝不动（卡片高度也平移同一个 delta，画布区高度因此守恒）。
  // 这里刻意不调 restackChildren：那是「重新垂直居中」，会覆盖用户手动摆放的 y。
  const delta = next - (prev ?? next)
  if (delta) {
    childrenInCanvas.value.forEach((child) => {
      updateNode(child.id, {
        position: { x: child.position?.x ?? 0, y: (child.position?.y ?? 0) + delta },
      })
    })
  }

  const min = minNodeSizeOf(next, childrenInCanvas.value.length)
  const currentHeight = parseFloat(container.value?.style?.height) || 0
  const width = container.value?.style?.width
  // 读不到高度时不能直接早退：否则 top 变了、height 没变，画布区会凭空变矮。
  // 用「刚好装得下当前子块」的初始尺寸补一次即可（下限语义见 minNodeSizeOf）。
  const height = currentHeight
    ? cardHeightAfterContentChange(currentHeight, prev ?? next, next, min.height)
    : min.height
  if (height === Math.round(currentHeight)) return
  updateNode(props.id, {
    style: {
      ...(width ? { width } : {}),
      height: `${height}px`,
    },
  })
})

/* ===== 拖拽改尺寸：按住卡片右下角把手，屏幕位移 / 缩放 = 画布位移 ===== */

const onResizeStart = (event) => {
  event.preventDefault()
  const grip = event.currentTarget
  grip.setPointerCapture?.(event.pointerId)

  const start = { x: event.clientX, y: event.clientY }
  const min = minNodeSizeOf(topAreaHeight.value, childrenInCanvas.value.length)
  const style = getNodes.value.find((node) => node.id === props.id)?.style ?? {}
  const startWidth = parseFloat(style.width) || min.width
  const startHeight = parseFloat(style.height) || min.height

  const onMove = (moveEvent) => {
    const zoom = getViewport().zoom || 1
    const width = Math.max(min.width, Math.round(startWidth + (moveEvent.clientX - start.x) / zoom))
    const height = Math.max(min.height, Math.round(startHeight + (moveEvent.clientY - start.y) / zoom))
    updateNode(props.id, { style: { width: `${width}px`, height: `${height}px` } })
  }
  const onEnd = () => {
    grip.removeEventListener('pointermove', onMove)
    grip.removeEventListener('pointerup', onEnd)
    grip.removeEventListener('pointercancel', onEnd)
  }
  grip.addEventListener('pointermove', onMove)
  grip.addEventListener('pointerup', onEnd)
  grip.addEventListener('pointercancel', onEnd)
}

/* ===== 实测顶部内容区高度：画布区起点、外侧锚点与卡片高度都以它为准 ===== */

/** 观测句柄：卸载时断开，避免节点被删除后仍在测量已脱离文档的元素 */
let contentObserver = null

/**
 * 从 ResizeObserver 条目里读顶部内容区高度。
 *
 * 必须用 `borderBoxSize`（布局尺寸），**不能**用 `getBoundingClientRect()`：后者返回的是
 * 渲染后的屏幕尺寸，会被 Vue Flow 画布的 `transform: scale()` 缩放，画布缩放后测到的高度会整体失真。
 * 兼容两种形态：规范里是数组，部分实现（Safari < 15.4）直接给单个对象。
 */
const topAreaFromEntry = (entry) => {
  const box = Array.isArray(entry?.borderBoxSize) ? entry.borderBoxSize[0] : entry?.borderBoxSize
  const height = box?.blockSize ?? entry?.contentRect?.height
  return Math.round(Number(height) || 0)
}

onMounted(() => {
  const element = contentRef.value
  // 无 ResizeObserver 的环境（老浏览器 / 测试）保持 measuredTopArea 为 null，一直用估算值兜底
  if (!element || typeof ResizeObserver === 'undefined') return

  contentObserver = new ResizeObserver((entries) => {
    const height = topAreaFromEntry(entries[0])
    // 高度为 0 说明这一帧还没布局完，保持现值继续等下一次回调，避免把卡片压成 0 高
    if (height > 0 && height !== measuredTopArea.value) measuredTopArea.value = height
  })
  contentObserver.observe(element)
})

onUnmounted(() => {
  contentObserver?.disconnect()
  contentObserver = null
})
</script>

<template>
  <div class="node-wrapper">
    <!-- 卡片：就是整个节点（与其它节点同款），内部除变量行外还嵌了一块「子节点画布」 -->
    <div class="node-card">
      <!-- 顶部内容区：标题栏 + 三组变量行，整体作为 ResizeObserver 的观测对象（contentRef）。
           画布区起点、外侧连接点与卡片高度都以它的**实测高度**为准，
           所以变量胶囊在同一行内增减时不会带动画布区变化（见 measuredTopArea） -->
      <div ref="contentRef" class="node-content">
        <div class="node-header">
          <div class="node-icon">
            <el-icon class="node-icon-svg">
              <RefreshRight />
            </el-icon>
          </div>
          <div class="node-title">
            <div class="node-label" :title="data.label">{{ data.label }}</div>

          </div>
          <span v-if="badgeText" class="node-badge">{{ badgeText }}</span>
          <!-- 内层嵌套迭代体：blocks 留在定义里（提交后端不丢），但画布上不展开，这里明确标出来 -->
          <span
            v-if="nestedBlockCount"
            class="node-badge node-badge--nested"
            :title="`该迭代节点内含 ${nestedBlockCount} 个嵌套子块：画布上不展开，运行时会照常执行`"
          >嵌套 {{ nestedBlockCount }}</span>
        </div>

        <VariableTags
          label="输入"
          :variables="inputVars"
          :undefined-names="unboundInputNames"
          :last="!hasParams && !hasOutputs"
        />
        <VariableTags
          label="迭代变量"
          :variables="paramVars"
          :undefined-names="unboundParamNames"
          :last="!hasOutputs"
        />
        <VariableTags label="输出" :variables="outputVars" />
      </div>

      <!-- 外侧连接点：上游连入 / 下游连出，贴在卡片左右边缘、与顶部内容区中线对齐。
           必须声明在画布区里的迭代体入口 / 出口之前：vue-flow 对没写 handle 的边会取「候选列表第一个」
           （loose 模式下候选是 source / target 合并后的顺序），外侧放前面才能保证主链路连在这里 -->
      <Handle id="in" type="target" :position="Position.Left" class="node-handle node-handle-target" :style="anchorStyle" />
      <Handle id="out" type="source" :position="Position.Right" class="node-handle node-handle-source" :style="anchorStyle" />

      <!-- 卡片内嵌的子节点画布：迭代体（标记 + 子块）在这里运行；上下锚定，随卡片尺寸伸缩。
           拖拽节点经过时高亮自己：告诉用户「松手后节点会成为这个迭代体的子块」 -->
      <div
        class="iterate-canvas"
        :class="{ 'iterate-canvas--drop-target': isDropTarget }"
        :style="canvasStyle"
      >
        <!-- 迭代开始标记：图标胶囊（房子图标，边框 / 底色与其它节点卡片一致），
             锚定画布区左缘与垂直中点；内侧 handle 放在标记元素内，随标记移动 -->
        <div
          class="iterate-marker iterate-marker--entry"
          :style="entryMarkerStyle"
          title="迭代开始"
        >
          <el-icon class="iterate-marker__icon">
            <HomeFilled />
          </el-icon>
          <Handle
            :id="ITERATE_ENTRY_HANDLE"
            type="source"
            :position="Position.Right"
            class="node-handle node-handle-source"
          />
        </div>

        <!-- 迭代结束标记：图标胶囊（开关图标），锚定画布区右缘与垂直中点 -->
        <div
          class="iterate-marker iterate-marker--exit"
          :style="exitMarkerStyle"
          title="迭代结束"
        >
          <el-icon class="iterate-marker__icon">
            <SwitchButton />
          </el-icon>
          <Handle
            :id="ITERATE_EXIT_HANDLE"
            type="target"
            :position="Position.Left"
            class="node-handle node-handle-target"
          />
        </div>
      </div>

      <!-- 拖拽把手：按住卡片右下角改节点尺寸，标记与连接点随 CSS 锚定自动跟随 -->
      <div
        class="iterate-resize nodrag nopan"
        title="拖拽调整大小"
        @pointerdown.stop="onResizeStart"
        @click.stop
      />
    </div>

    <!-- 结果区：与其它节点一致，贴在卡片正下方（进度行 + 汇总产出面板，与卡片同宽） -->
    <!-- 进度行与汇总产出都不属于节点本体：终止 click 冒泡，避免点它们打开节点抽屉 -->
    <div v-if="runtime" class="iterate-result nodrag" @click.stop>
      <div v-if="progressText" class="iterate-progress">
        <span class="iterate-progress__text">{{ progressText }}</span>
        <span class="iterate-progress__track">
          <i class="iterate-progress__bar" :style="{ width: `${percent}%` }" />
        </span>
      </div>
      <NodeResultPanel :runtime="runtime" :node-id="id" />
    </div>
  </div>
</template>

<style scoped>
/*
 * 顶部内容区（.node-content = 标题栏 + 三组变量行）只承担两件事：
 * 1) 把这三段包成一个可测量的整体，其高度就是 utils/iteration 所说的「顶部内容区高度」；
 * 2) 供脚本里的 ResizeObserver 读 borderBoxSize，得到真实高度（measuredTopArea）。
 * 这里刻意**不设** padding / margin：任何额外间距都会直接改变布局口径，
 * 而画布区起点、外侧连接点与卡片高度全部由这个高度推导。
 */

/* ===== 节点盒：卡片铺满节点盒，画布区嵌在卡片内部，结果区流到卡片正下方 ===== */
.node-wrapper {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  flex-direction: column;
}

/* ===== 卡片：与普通节点的 .node-card 完全同款；高度跟随节点盒（拖拽把手改的就是节点盒） ===== */
.node-card {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  /* 结果区流在卡片之后：卡片高度必须保住（否则内嵌画布与标记会整体错位） */
  flex-shrink: 0;
  border-radius: 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  transition: box-shadow 0.2s, border-color 0.2s;
}

.node-card:hover {
  border-color: var(--tb-border-strong);
  box-shadow: var(--tb-shadow-2);
}

/* 节点头部：与其它卡片的 .node-header 同一套内边距 */
.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
}

.node-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--tb-primary);
  color: #fff;
}

.node-icon-svg {
  font-size: 16px;
}

.node-title {
  display: flex;
  min-width: 0;
  flex-grow: 1;
  flex-direction: column;
  gap: 2px;
}

/*
 * 头部三处的行高必须写成显式值：`utils/iteration` 的 CARD_LAYOUT.titleContentHeight
 * 按「label 行 + 行距 + 副标题行 = 20 + 2 + 18 = 40」推导顶部内容区高度，进而决定卡片高度、
 * 画布区偏移与外侧连接点的位置。用默认行高（随字体/浏览器变化）会让这套计算整体偏移。
 */
.node-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  /* 与 CARD_LAYOUT.titleContentHeight 的 label 行一致 */
  line-height: 20px;
  font-weight: 600;
  color: var(--tb-text-primary);
}


.node-meta {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}

/* 轮次 / 产出徽标 */
.node-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 9999px;
  background: var(--tb-primary-soft);
  font-size: 11px;
  font-weight: 500;
  color: var(--tb-primary-text);
}

/* 嵌套迭代体提示：内层子块不在画布上展开 */
.node-badge--nested {
  border: var(--tb-border-width) solid var(--tb-warning);
  background: transparent;
  color: var(--tb-warning);
}

/* ===== 卡片内嵌的子节点画布（浅色底 + 边框 + 圆角；左右 / 上下偏移由行内 style 锚定，随卡片伸缩） ===== */
.iterate-canvas {
  position: absolute;
  box-sizing: border-box;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 12px;
  /* 用不透明底色即可：内部连线已由 ITERATION_EDGE_STYLE 的 zIndex 抬到节点层之上，不会被底色盖住 */
  background: var(--tb-bg-sunken);
  transition: border-color 0.2s;
}

.iterate-canvas:hover {
  border-color: var(--tb-primary);
}

/* 拖拽命中：告诉用户「松手后节点会成为这个迭代体的子块」。
   阴影叠在画布区内侧，不改 padding / border——那是布局口径，动了会让顶部高度测量与子块坐标漂移 */
.iterate-canvas--drop-target {
  border-color: var(--tb-primary);
  background: var(--tb-primary-soft);
  box-shadow: inset 0 0 0 1px var(--tb-primary);
}

/* ===== 迭代开始 / 结束标记：图标胶囊，固定在画布区垂直中点（左右位置由行内 style 锚定）。
   边框与底色与其它节点卡片一致（实线 --tb-border + 实底 --tb-bg-surface） ===== */
.iterate-marker {
  position: absolute;
  top: 50%;
  display: flex;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  padding: 0 6px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 12px;
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  transition: border-color 0.2s;
  transform: translateY(-50%);
}

.iterate-marker:hover {
  border-color: var(--tb-border-strong);
}

/* 图标：标记内唯一内容（宽度由 markerWidth 贴合图标给出：14 + 内边距 12 + 边框 2 = 28） */
.iterate-marker__icon {
  font-size: 16px;
}

/* ===== 拖拽把手：卡片右下角，按住改节点尺寸 ===== */
.iterate-resize {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 9px;
  height: 9px;
  cursor: nwse-resize;
  border-radius: 3px;
  opacity: 0;
  transition: opacity 0.2s;
  background: linear-gradient(
    135deg,
    transparent 0 30%,
    var(--tb-border-strong) 30% 42%,
    transparent 42% 58%,
    var(--tb-border-strong) 58% 70%,
    transparent 70%
  );
}

.node-card:hover .iterate-resize,
.iterate-resize:hover {
  opacity: 1;
}

/* ===== 结果区：与其它节点一致，贴在卡片正下方，宽度与卡片同宽 ===== */
.iterate-result {
  display: flex;
  width: 100%;
  flex-shrink: 0;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

/* 结果面板与卡片同宽（覆盖面板自带的 17rem），上边距由结果区统一给 */
.iterate-result :deep(.node-result) {
  width: 100%;
  margin-top: 0;
}

/* ===== 进度行：第 n/N 轮 + 进度条 ===== */
.iterate-progress {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}

.iterate-progress__text {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--tb-text-secondary);
}

.iterate-progress__track {
  flex-grow: 1;
  height: 4px;
  overflow: hidden;
  border-radius: 9999px;
  background: var(--tb-bg-sunken);
}

.iterate-progress__bar {
  display: block;
  height: 100%;
  border-radius: 9999px;
  background: var(--tb-primary);
  transition: width var(--tb-dur-2) var(--tb-ease-out);
}

/* ===== Handle =====
   与其它节点同一套口径：白底 + 灰边、悬停卡片时淡入、按 type 带 node-handle-source / -target 修饰类。
   内侧（迭代开始 / 结束标记上的）连接点不再单独配色，避免看起来不像同一套控件。
   内侧 handle 的位置仍由 Vue Flow 自带的 handle CSS 决定（贴在所在标记的左 / 右缘） */
.node-handle {
  width: 8px !important;
  height: 8px !important;
  border: var(--tb-border-width) solid var(--tb-border-strong) !important;
  background: var(--tb-bg-surface) !important;
  opacity: 0;
  transition: opacity 0.2s;
}

/* 悬停**卡片**时连接点淡入（与其它节点一致：卡片下方的结果区不算触发范围） */
.node-card:hover .node-handle {
  opacity: 1;
}
</style>
