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
 * 1. 尺寸是「活的」：初始尺寸由 `expandIteration` 按 `containerSizeOf(子块数, 变量行数)` 写进节点 style，
 *    用户可拖住卡片右下角把手改变（`updateNode` 更新 style，下限 `minNodeSizeOf(变量行数, 子块数)`——
 *    缩到装不下子块就没有意义）。卡片 `height: 100%` 跟随节点盒，画布区以 `top/bottom` 锚定在卡片内随之伸缩。
 *    改尺寸**不重排子块**：宽度变化只让子块横向重新居中（x 是自动轴），高度变化保留用户摆放的 y；
 * 2. 「迭代开始 / 结束」是画布区左右两侧的**视觉标记**（不是画布节点），**固定在画布区的垂直中点**
 *    （CSS 锚定，不随子块拖动移动；子块区在子块增删时按真实高度重新垂直居中）；
 *    迭代体内部连线由放在标记元素内部的内侧 handle（`iterate-in` / `iterate-out`）承载
 *    （DOM 锚定），折叠回后端时不会多出节点；
 * 3. 在抽屉里增删变量（变量行数变化）时，`watch` 把节点高度同步增高 / 降低差值，画布区高度
 *    保持不变，子块与标记不会跳动；子块不各自展示运行结果（见 `utils/use-nested-node`），
 *    迭代体各轮的产出统一由结果区体现。内层嵌套迭代体（自身仍带 `blocks`）画布上不展开，
 *    卡片上以「嵌套 N」徽标标明（数据不丢，提交后端时原样带回）。
 */
import { computed, inject, watch } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { HomeFilled, RefreshRight, SwitchButton } from '@element-plus/icons-vue'
import VariableTags from '../components/VariableTags.vue'
import NodeResultPanel from '../components/NodeResultPanel.vue'
import { RUN_STATE_KEY } from '../utils/run-inject'
import { iterationProgressOf, nodeRuntimeOf } from '../utils/run-state'
import {
  ITERATE_ENTRY_HANDLE,
  ITERATE_EXIT_HANDLE,
  ITERATION_LAYOUT,
  canvasHeightOf,
  canvasTopOf,
  cardAnchorYOf,
  cardRowCountOf,
  childXOfNodeWidth,
  minNodeSizeOf,
} from '../utils/iteration'

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

/** 迭代进度：`{ done, total }`，由子块的 node 事件累计（见 utils/run-state） */
const progress = computed(() => (runState ? iterationProgressOf(runState.value, props.id) : null))

/* ===== 卡片内容 ===== */

/** 变量行数：输入 + 迭代变量 + 输出（与 utils/iteration 的卡片高度口径同一份计算） */
const rowCount = computed(() => cardRowCountOf(props.data))

const inputVars = computed(() => props.data?.inputs ?? [])
const paramVars = computed(() => props.data?.variableParameters ?? [])
const outputVars = computed(() => props.data?.outputs ?? [])

/** 该组变量是否有可见行（空名不可见，与 VariableTags 的过滤口径一致） */
const hasVisible = (list) =>
  (Array.isArray(list) ? list : []).some((item) => (typeof item === 'string' ? item : item?.name))

const hasParams = computed(() => hasVisible(paramVars.value))
const hasOutputs = computed(() => hasVisible(outputVars.value))

/** 迭代源：inputs 里声明为 list 的那一项（取不到就退回第一个输入） */
const iteratorName = computed(() => {
  const inputs = inputVars.value
  const listInput = inputs.find((item) => item?.type === 'list' || item?.input?.type === 'list')
  return (listInput ?? inputs[0])?.name ?? ''
})

/** 产出变量个数：未运行时的徽标文案 */
const outputCount = computed(() => outputVars.value.length)

/** 进度文案：总数未知（迭代源产出还没到）时只显示已完成轮次 */
const progressText = computed(() => {
  const done = progress.value?.done ?? 0
  const total = progress.value?.total ?? 0
  if (!done && !total) return ''
  return total ? `${done}/${total} 轮` : `${done} 轮`
})

/** 卡片右上角徽标：优先显示进度，未运行时退化为产出变量个数 */
const badgeText = computed(
  () => progressText.value || (outputCount.value ? `${outputCount.value} 个产出` : ''),
)

const percent = computed(() => {
  const done = progress.value?.done ?? 0
  const total = progress.value?.total ?? 0
  return total ? Math.min(100, Math.round((done / total) * 100)) : 0
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

/** 画布区的真实高度：节点盒高 − 顶部内容区 − 上下内缩（拖拽改尺寸后跟随） */
const canvasHeight = computed(() => {
  const boxHeight = parseFloat(container.value?.style?.height) || 0
  if (!boxHeight) return canvasHeightOf(childrenInCanvas.value.length)
  return boxHeight - canvasTopOf(rowCount.value) - ITERATION_LAYOUT.canvasInset
})

/** 子块的真实渲染高度（尚未测量时退回预估高度） */
const childHeightOf = (child) => child.dimensions?.height ?? ITERATION_LAYOUT.childHeight

/**
 * 把子块按真实高度在画布区内**重新垂直居中**（子块增删、子块内容变高变矮时调用）。
 * <p>**节点尺寸变化不触发重排**：用户把子块拖到别处后再改尺寸，摆放必须保留——
 * 画布区只是跟着变大变小，不能把用户摆好的子块强行拉回居中位置（旧实现把画布区高度也放进触发键里，
 * 拖拽把手每帧都会把子块拽回原位）。
 */
const restackChildren = () => {
  const list = childrenInCanvas.value
  if (!list.length) return
  const heights = list.map(childHeightOf)
  const blockHeight = heights.reduce((sum, height) => sum + height, 0)
    + (list.length - 1) * ITERATION_LAYOUT.childGap
  const top = Math.max(
    ITERATION_LAYOUT.canvasPaddingY,
    Math.round((canvasHeight.value - blockHeight) / 2),
  )
  let offsetY = canvasTopOf(rowCount.value) + top
  list.forEach((child, index) => {
    const y = Math.round(offsetY)
    if (Math.abs((child.position?.y ?? 0) - y) > 1) {
      updateNode(child.id, { position: { x: child.position?.x ?? 0, y } })
    }
    offsetY += heights[index] + ITERATION_LAYOUT.childGap
  })
}

/** 子块集合与各自高度：增删子块 / 子块内容变化时重排（键里刻意不含节点尺寸） */
watch(
  () => childrenInCanvas.value
    .map((child) => `${child.id}:${Math.round(childHeightOf(child))}`)
    .join('|'),
  restackChildren,
)

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
const anchorStyle = computed(() => ({ top: `${cardAnchorYOf(rowCount.value)}px` }))

/** 画布区：嵌在卡片内、上下锚定（top = 顶部内容区 + 内缩，bottom = 内缩），高度随卡片伸缩 */
const canvasStyle = computed(() => ({
  left: `${ITERATION_LAYOUT.canvasInset}px`,
  right: `${ITERATION_LAYOUT.canvasInset}px`,
  top: `${canvasTopOf(rowCount.value)}px`,
  bottom: `${ITERATION_LAYOUT.canvasInset}px`,
}))

/**
 * 变量行数变化（在抽屉里增删变量）时，顶部内容区高度随之变化：
 * 把节点高度同步增高 / 降低差值，画布区（bottom 锚定）高度保持不变，子块与标记不会跳动。
 */
watch(rowCount, (next, prev) => {
  const delta = canvasTopOf(next) - canvasTopOf(prev)
  if (!delta) return
  const currentHeight = parseFloat(container.value?.style?.height) || 0
  if (!currentHeight) return
  const min = minNodeSizeOf(next, childrenInCanvas.value.length)
  const width = container.value?.style?.width
  updateNode(props.id, {
    style: {
      ...(width ? { width } : {}),
      height: `${Math.max(min.height, Math.round(currentHeight + delta))}px`,
    },
  })
})

/* ===== 拖拽改尺寸：按住卡片右下角把手，屏幕位移 / 缩放 = 画布位移 ===== */

const onResizeStart = (event) => {
  event.preventDefault()
  const grip = event.currentTarget
  grip.setPointerCapture?.(event.pointerId)

  const start = { x: event.clientX, y: event.clientY }
  const min = minNodeSizeOf(rowCount.value, childrenInCanvas.value.length)
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
</script>

<template>
  <div class="node-wrapper">
    <!-- 卡片：就是整个节点（与其它节点同款），内部除变量行外还嵌了一块「子节点画布」 -->
    <div class="node-card">
      <div class="node-header">
        <div class="node-icon">
          <el-icon class="node-icon-svg">
            <RefreshRight />
          </el-icon>
        </div>
        <div class="node-title">
          <div class="node-label" :title="data.label">{{ data.label }}</div>
          <div class="node-subtitle">
            <span class="node-chip">迭代</span>
            <span v-if="iteratorName" class="node-meta">遍历 {{ iteratorName }}</span>
          </div>
        </div>
        <span v-if="badgeText" class="node-badge">{{ badgeText }}</span>
        <!-- 内层嵌套迭代体：blocks 留在定义里（提交后端不丢），但画布上不展开，这里明确标出来 -->
        <span
          v-if="nestedBlockCount"
          class="node-badge node-badge--nested"
          :title="`该迭代节点内含 ${nestedBlockCount} 个嵌套子块：画布上不展开，运行时会照常执行`"
        >嵌套 {{ nestedBlockCount }}</span>
      </div>

      <VariableTags label="输入" :variables="inputVars" :last="!hasParams && !hasOutputs" />
      <VariableTags label="迭代变量" :variables="paramVars" :last="!hasOutputs" />
      <VariableTags label="输出" :variables="outputVars" />

      <!-- 外侧连接点：上游连入 / 下游连出，贴在卡片左右边缘、与顶部内容区中线对齐。
           必须声明在画布区里的迭代体入口 / 出口之前：vue-flow 对没写 handle 的边会取「候选列表第一个」
           （loose 模式下候选是 source / target 合并后的顺序），外侧放前面才能保证主链路连在这里 -->
      <Handle id="in" type="target" :position="Position.Left" class="node-handle" :style="anchorStyle" />
      <Handle id="out" type="source" :position="Position.Right" class="node-handle" :style="anchorStyle" />

      <!-- 卡片内嵌的子节点画布：迭代体（标记 + 子块）在这里运行；上下锚定，随卡片尺寸伸缩 -->
      <div class="iterate-canvas" :style="canvasStyle">
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
            class="node-handle iterate-handle"
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
            class="node-handle iterate-handle"
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
    <div v-if="runtime" class="iterate-result">
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

.node-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  color: var(--tb-text-primary);
}

.node-subtitle {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

/* 「迭代」标识：主色软底（暗色下有专门取值，避免浅底在深色上消失） */
.node-chip {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-primary-soft);
  font-size: 11px;
  font-weight: 500;
  color: var(--tb-primary-text);
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
  border-radius: 9999px;
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
  font-size: 14px;
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

/* ===== Handle ===== */
.node-handle {
  width: 8px !important;
  height: 8px !important;
  border: var(--tb-border-width) solid var(--tb-border-strong) !important;
  background: var(--tb-bg-surface) !important;
  opacity: 0;
  transition: opacity 0.2s;
}

/* 悬停节点盒任意位置（卡片或画布区）时，两段上的连接点一起淡入 */
.node-wrapper:hover .node-handle {
  opacity: 1;
}

/* 内侧 handle：放在标记元素内部，贴标记左 / 右缘（位置由 Vue Flow 自带的 handle CSS 给出） */
.iterate-handle {
  background: var(--tb-primary) !important;
}
</style>
