<script setup>
import { computed, markRaw, onUnmounted, provide, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MarkerType, VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import StartNode from './node/StartNode.vue'
import EndNode from './node/EndNode.vue'
import LlmNode from './node/LlmNode.vue'
import IterateNode from './node/IterateNode.vue'
import HumanNode from './node/HumanNode.vue'
import ConnectionLine from './ConnectionLine.vue'
import NodeDataDrawer from './components/NodeDataDrawer.vue'
import ToolsWrap from './components/ToolsWrap.vue'
import { buildResumeUrl, buildRunDefinition, WORKFLOW_API } from '@/api/workflow'
import { readWorkflowStream } from '@/utils/workflow-sse'
import { RUN_ACTION_KEY, RUN_STATE_KEY } from './utils/run-inject'
import { ITERATE_NODE_TYPE, expandIteration } from './utils/iteration'
import { DEFAULT_FLOW_DEFINITION } from './default-flow'
import { applyRunEvent, beginRun, createRunState, finishRun, resumeRun } from './utils/run-state'

const { addEdges, getEdges} = useVueFlow()

/**
 * 节点类型注册表：与后端 NodeActionFactory 的数字编码一一对应。
 * key 必须是字符串，节点的 `type` 也统一写字符串，如 `'4'`。
 */
const nodeTypes = {
  "1": markRaw(StartNode),
  "2": markRaw(EndNode),
  "3": markRaw(LlmNode),
  "4": markRaw(IterateNode),
  "5": markRaw(HumanNode),
}

/** 运行态：状态与产出由 run-state 归约，节点卡片按节点 id 读取 */
const runState = ref(createRunState())
provide(RUN_STATE_KEY, computed(() => runState.value))
provide(RUN_ACTION_KEY, { confirm: (nodeId, content) => confirmNode(nodeId, content) })



/** 连线统一样式：新建连线与初始化连线共用，保证两种来源的线完全一致 */
const CANVAS_EDGE_STYLE = {
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: MarkerType.Arrow, color: 'var(--tb-primary)', width: 15, height: 15 },
}

/** 外侧连接点的 handle id：所有节点卡片的入 / 出连接点都用这两个 id（迭代容器另有内侧 handle） */
const CANVAS_INPUT_HANDLE = 'in'
const CANVAS_OUTPUT_HANDLE = 'out'

/** 节点所属的迭代容器 id；顶层节点返回 null */
const containerIdOf = (nodeId) =>
  nodes.value.find((node) => node.id === nodeId)?.parentNode ?? null

const handleConnect = (connection) => {
  const { source, target, sourceHandle = null, targetHandle = null } = connection

  if (source === target) {
    ElMessage.warning('不能把节点连到它自己')
    return
  }

  // 迭代体是容器的内部结构：子块只能和同一容器内的节点（含容器自身）连线。
  // 跨越边界的边在折叠成 blocks 之后，其中一端已不在顶层节点里，后端建图会直接失败。
  const sourceContainer = containerIdOf(source)
  const targetContainer = containerIdOf(target)
  const sameLevel = sourceContainer === targetContainer
  const insideItself = sourceContainer === target || targetContainer === source
  if (!sameLevel && !insideItself) {
    ElMessage.warning('迭代体内部的节点只能和同一迭代容器内的节点连线')
    return
  }

  const duplicated = getEdges.value.some(
    (edge) =>
      edge.source === source &&
      edge.target === target &&
      (edge.sourceHandle ?? null) === sourceHandle &&
      (edge.targetHandle ?? null) === targetHandle,
  )
  if (duplicated) {
    ElMessage.warning('这两个节点之间已经有连线了')
    return
  }

  addEdges([{ ...connection, ...CANVAS_EDGE_STYLE }])
}

/**
 * 节点数据抽屉：点击节点即打开（卡片右上角「···」在节点内部，点击同样会冒泡到节点上），
 * 点画布空白处关闭。抽屉只编辑草稿，保存时写回 Vue Flow store，卡片随之刷新。
 */
const drawerVisible = ref(false)
const activeNodeId = ref(null)


function onNodeClick({ event, node }) {
  activeNodeId.value = node.id
  drawerVisible.value = true
}


/** 当前事件流的控制器：重新执行或离开页面时用它中断旧连接 */
const runAbort = ref(null)

/** 中断当前事件流 */
const abortRun = () => {
  runAbort.value?.abort()
  runAbort.value = null
}

/**
 * 提交一次执行并消费事件流：事件交给 run-state 归约，节点卡片按 id 渲染状态与产出。
 *
 * @param {string} url 接口地址（执行或人工确认恢复）
 * @param {object} [options] 选项
 * @param {object} [options.body] 请求体；恢复执行不需要 body
 */
const execute = async (url, options = {}) => {
  abortRun()
  const controller = new AbortController()
  runAbort.value = controller
  // 提交执行时把整份画布节点交给归约：除了给每个节点建运行态，还用它推导迭代进度（容器 → 子块、迭代源）
  runState.value = options.body ? beginRun(runState.value, nodes.value) : resumeRun(runState.value)

  try {
    await readWorkflowStream(url, {
      body: options.body,
      signal: controller.signal,
      onEvent: (event, payload) => {
        runState.value = applyRunEvent(runState.value, event, payload)
      },
    })
    runState.value = finishRun(runState.value)
  } catch (error) {
    // 主动中断（重新运行 / 离开页面）不算失败
    if (controller.signal.aborted) return
    runState.value = finishRun(runState.value, true)
    console.error('[workflow] 执行失败:', error)
    ElMessage.error(error?.message || '工作流执行失败')
  } finally {
    if (runAbort.value === controller) runAbort.value = null
  }
}

/**
 * 工具栏「运行」：把当前画布提交给后端执行，节点进度与产出通过 SSE 回传。
 */
const handleRun = () => {
  if (runState.value.streaming) {
    ElMessage.info('正在执行中，请等待本次执行结束')
    return
  }
  const definition = buildRunDefinition(nodes.value, edges.value)
  if (!definition.nodes.length) {
    ElMessage.warning('画布上还没有节点，先添加节点再运行')
    return
  }
  execute(WORKFLOW_API.run, { body: definition })
}

/**
 * 人工确认节点的「确认继续」：把确认内容回传后端，从暂停处继续执行（新起一条事件流）。
 *
 * @param {string} nodeId 等待确认的节点 id
 * @param {string} content 用户填写的确认内容
 */
function confirmNode(nodeId, content) {
  const runId = runState.value.runId
  if (!runId) {
    ElMessage.warning('没有正在等待确认的执行')
    return
  }
  if (!content?.trim()) {
    ElMessage.warning('请先填写确认内容')
    return
  }
  execute(buildResumeUrl(runId, content))
}

onUnmounted(abortRun)


/**
 * 把后端结构的定义摊平成画布表示：迭代节点用 `expandIteration` 展开为「容器 + 子块 + 内部边」
 * （子块带 `parentNode`），顶层边统一套上画布连线样式并换成 vue-flow 的字段名。
 *
 * 顶层边显式指定外侧 handle（`out` → `in`）：vue-flow 在边没写 handle 时会取「候选列表第一个」
 * （loose 模式下候选会把 source / target 两边的 handle 合并），而迭代容器有 4 个 handle，
 * 不指定就会连到内侧的「迭代开始 / 迭代结束」上。已有 handle 的边保持原值。
 */
const toCanvasFlow = (definition) => {
  const canvasNodes = []
  const canvasEdges = []
  for (const node of definition.nodes) {
    if (String(node.type) === ITERATE_NODE_TYPE) {
      const expanded = expandIteration(node)
      canvasNodes.push(...expanded.nodes)
      canvasEdges.push(...expanded.edges)
      continue
    }
    canvasNodes.push(node)
  }
  for (const edge of definition.edges) {
    canvasEdges.push({
      id: edge.id,
      source: edge.sourceNodeID ?? edge.source,
      target: edge.targetNodeID ?? edge.target,
      sourceHandle: edge.sourceHandle ?? CANVAS_OUTPUT_HANDLE,
      targetHandle: edge.targetHandle ?? CANVAS_INPUT_HANDLE,
      ...CANVAS_EDGE_STYLE,
    })
  }
  return { nodes: canvasNodes, edges: canvasEdges }
}

/**
 * 画布数据：默认加载 `default-flow.js` 的链路（开始 → 大模型 → 迭代 → 结束），
 * 后续的节点库 / 打开工作流会在此之上增删。
 * 迭代节点的迭代体（blocks）以 `parentNode` 子节点的形式挂在容器下。
 */
const defaultCanvas = toCanvasFlow(DEFAULT_FLOW_DEFINITION)

const nodes = ref(defaultCanvas.nodes)

// 画布上的边用 vue-flow 的字段名：source / target（不是后端 schema 的 sourceNodeID / targetNodeID）
// handle 由 toCanvasFlow 显式补齐（外侧 out / in）：多 handle 的节点（如迭代容器）必须显式指定，
// 否则 vue-flow 会按「候选列表第一个」兜底，把主链路连到内侧的「迭代开始 / 迭代结束」上
const edges = ref(defaultCanvas.edges)

</script>

<template>
  <div class="wf-canvas">
    <!-- elevate-nodes-on-select 关闭：迭代节点是不透明卡片，子块与内部连线靠固定层级（见 utils/iteration 的
         ITERATION_LAYER）压在卡片之上；若保留「选中即抬到 1000」的默认行为，选中迭代节点时会把这两者反压回去 -->
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :node-types="nodeTypes"
      :elevate-nodes-on-select="false"
      fit-view-on-init
      @connect="handleConnect"
      @node-click="onNodeClick"
    >
      <Background :gap="22" :size="1.4" />

      <!-- 自定义连线预览：拖拽连线时跟随指针的紫色虚线 + 终点圆点 -->
      <template #connection-line="connectionLineProps">
        <ConnectionLine v-bind="connectionLineProps" />
      </template>
    </VueFlow>

    <!-- 画布工具栏：目前只有「运行」（提交画布定义并消费 SSE），浮层由组件自己定位 -->
    <ToolsWrap :running="runState.streaming" @run="handleRun" />

    <!-- 节点数据抽屉：编辑当前选中节点的变量与提示词 -->
    <NodeDataDrawer v-model:visible="drawerVisible" :node-id="activeNodeId" />
  </div>
</template>

<style scoped>
.wf-canvas {
  /* relative：工具栏（ToolsWrap）是浮层，靠它定位 */
  position: relative;
  width: 100%;
  height: 100%;
  background: var(--tb-bg-page);
}

.wf-canvas :deep(.vue-flow__background circle) {
  fill: var(--tb-border-strong);
}
</style>
