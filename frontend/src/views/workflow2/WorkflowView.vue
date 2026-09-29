<script setup>
import { ref, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { MarkerType, VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import StartNode from './node/StartNode.vue'
import EndNode from './node/EndNode.vue'
import LlmNode from './node/LlmNode.vue'
import ConnectionLine from './ConnectionLine.vue'
import NodeDataDrawer from './components/NodeDataDrawer.vue'

const { addEdges, getEdges} = useVueFlow()

const nodeTypes = {
  "1": markRaw(StartNode),
  "2":markRaw(EndNode),
  "3":markRaw(LlmNode),
}



/** 连线统一样式：新建连线与初始化连线共用，保证两种来源的线完全一致 */
const CANVAS_EDGE_STYLE = {
  type: 'default',
  style: { stroke: 'var(--tb-text-primary)', strokeWidth: 1.5 },
  animated: true,
  markerEnd: { type: MarkerType.Arrow, color: 'var(--tb-primary)', width: 15, height: 15 },
}

const handleConnect = (connection) => {
  const { source, target, sourceHandle = null, targetHandle = null } = connection

  if (source === target) {
    ElMessage.warning('不能把节点连到它自己')
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
  console.log(event);
}


const nodes = ref([
  {
    id: '100001',
    type: '1',
    position: { x: 1460, y: 120 },
    data: {
      label: '开始',
      outputs: [
        {
          name: 'file_tree',
          type: 'list',
          required: true,
          schema: { type: 'object' },
          description: '文件树（列表）',
        },
        {
          name: 'description',
          type: 'string',
          required: true,
          description: 'AI批改描述',
        },
        {
          name: 'know',
          type: 'list',
          required: true,
          schema: { type: 'string' },
          description: 'AI知识（列表）',
        },
      ],
    },
    blocks: [],
    edges: null,
  }, 
  // 测试用的大模型节点：覆盖抽屉里的三类取值（引用上游变量、引用数组、字面量常量）与带 schema 的数组输出
  {
    id: '800001',
    type: '3',
    position: { x: 1460, y: 420 },
    data: {
      label: '学生名单规划',
      system:
        '<role>你是一个教务作业批改辅助系统，负责从文件树中判断出教师最终需要批改和登记成绩的学生名单。</role>',
      user: '## 用户描述\n{{description}}\n\n## 文件树\n{{file_tree}}\n\n请依据以上信息输出规划 JSON。',
      inputs: [
        // 引用开始节点的同名输出
        {
          name: 'description',
          input: { type: 'string', blockID: '100001', name: 'description' },
        },
        // 引用开始节点的数组输出
        {
          name: 'file_tree',
          type: 'list',
          schema: { type: 'object' },
          input: { type: 'list', blockID: '100001', name: 'file_tree' },
        },
        // 字面量常量：不引用任何上游变量
        {
          name: 'score_max',
          type: 'number',
          input: { type: 'number', content: '100' },
        },
      ],
      outputs: [
        {
          name: 'student_categories',
          type: 'list',
          required: true,
          schema: {
            type: 'object',
            properties: {
              student: { type: 'string', description: '学生姓名' },
              paths: {
                type: 'array',
                items: { type: 'string' },
                description: '该学生对应的文件路径列表',
              },
            },
            required: ['student', 'paths'],
          },
          description:
            '根据 file_tree 判断出的学生分类（列表），每个元素形如 {student: 学生姓名, paths: [该学生对应的文件路径列表]}',
        },
        {
          name: 'student_count',
          type: 'integer',
          description: '待批改学生总数',
        },
      ],
    },
    blocks: [],
    edges: null,
  },
  {
    id: '900001',
    type: '2',
    // 与开始节点错开：两个节点完全重叠时，开始节点的源 Handle 会被结束节点卡片盖住，无法拖出连线
    position: { x: 1960, y: 120 },
    data: {
      label: '结束',
      inputs: [
        {
          name: 'input',
          input: { type: 'string', blockID: '100001', name: 'description' },
        },
      ],
    },
    blocks: [],
    edges: null,
  },
])
// 画布上的边用 vue-flow 的字段名：source / target（不是后端 schema 的 sourceNodeID / targetNodeID）
// 不写 sourceHandle / targetHandle：渲染时取源节点第一个 source handle、目标节点第一个 target handle
const edges = ref([
  {
    id: 'e-100001-900001',
    source: '100001',
    target: '900001',
    ...CANVAS_EDGE_STYLE,
  },
  // 测试用大模型节点的上游连线：有这条边，节点里对开始节点输出的引用才算合法上游
  {
    id: 'e-100001-800001',
    source: '100001',
    target: '800001',
    ...CANVAS_EDGE_STYLE,
  },
])



const nodemock = [
  {
    id: '100001',
    type: '1',
    position: { x: 1460, y: 120 },
    data: {
      label: '开始',
      outputs: [
        {
          name: 'file_tree',
          type: 'list',
          required: true,
          schema: { type: 'object' },
          description: '文件树（列表）',
        },
        {
          name: 'description',
          type: 'string',
          required: true,
          description: 'AI批改描述',
        },
        {
          name: 'know',
          type: 'list',
          required: true,
          schema: { type: 'string' },
          description: 'AI知识（列表）',
        },
      ],
    },
    blocks: [],
    edges: null,
  },

  {
    id: '132996',
    type: '4',
    position: { x: 1460, y: 120 },
    data: {
      label: '迭代',
      inputs: [
        {
          name: 'output2',
          input: { type: 'list', blockID: '100001', name: 'file_tree' },
        },
      ],
      variableParameters: [
        {
          name: 'jj',
          input: { type: 'string', blockID: '100001', name: 'description' },
        },
      ],
      outputs: [
        {
          name: 'description',
          type: 'string',
          required: true,
          description: 'AI批改描述',
        },
      ],
    },
    blocks: [
      {
        id: '173594',
        type: '3',
        position: { x: 1460, y: 120 },
        data: {
          label: 'llm',
          system: '<role>你是一个教务作业批改辅助系统，判断出教师最终需要批改和登记成绩的学生名单。</role>',
          user: ' ## 用户描述  {{output}}  ## 文件文件树  {{output2}}   请依据以上信息输出规划 JSON。',
          inputs: [
            {
              name: 'output',
              input: { type: 'string', blockID: '132996', name: 'input' },
            },
          ],
          outputs: [
            {
              name: 'student_categories',
              type: 'list',
              required: true,
              schema: {
                type: 'object',
                properties: {
                  student: { type: 'string', description: '学生姓名' },
                  paths: {
                    type: 'array',
                    items: { type: 'string' },
                    description: '该学生对应的文件路径列表',
                  },
                },
                required: ['student', 'paths'],
              },
              description: '根据 file_tree 判断出的学生分类（列表），每个元素形如 {student: 学生姓名, paths: [该学生对应的文件路径列表]}',
            },
          ],
        },
        blocks: [],
        edges: null,
      },
    ],
    edges: [
      { sourceNodeID: '132996', targetNodeID: '173594' },
      { sourceNodeID: '173594', targetNodeID: '132996' },
    ],
  },
]
const edgemock = [
  { sourceNodeID: '100001', targetNodeID: '132996' },
  { sourceNodeID: '132996', targetNodeID: '900001' },
]

</script>

<template>
  <div class="wf-canvas">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :node-types="nodeTypes"
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

    <!-- 节点数据抽屉：编辑当前选中节点的变量与提示词 -->
    <NodeDataDrawer v-model:visible="drawerVisible" :node-id="activeNodeId" />
  </div>
</template>

<style scoped>
.wf-canvas {
  width: 100%;
  height: 100%;
  background: var(--tb-bg-page);
}

.wf-canvas :deep(.vue-flow__background circle) {
  fill: var(--tb-border-strong);
}
</style>
