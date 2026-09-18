<script setup>
import { computed, markRaw, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { VueFlow, useVueFlow, ConnectionMode, MarkerType } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

import StartNode from './test/StartNode.vue'
import ForNode from './test/ForNode.vue'
import LoopFunctionNode from './test/FunNode.vue'


const {
  addNodes,
  addEdges,
  removeNodes,
  updateEdge,
  updateNode,
  updateNodeData,
  getNodes,
  getEdges,
  removeSelectedElements,
} = useVueFlow()

const nodes = ref([
  { id: "100001", type: 'input', data: { label: '开始'}, position: { x: 100, y: 100 }, deletable: false,sourcePosition: 'right',targetPosition: 'left',blocks:[] },
  // { id:"3", type: 'start', data: { label: '测试6666666666666666'}, position: { x: 100, y: 100 },blocks:[{ id:"loog_3", type: 'start', data: { label: '测试6666666666666666'}, position: { x: 0, y: 100 },blocks:[]}]},
  // { id:"6", type: 'LoopFunctionNod', data: { label: '测试6666666666666666'}, position: { x: -20, y: 180 },parentNode: '4',},
  // { id:"6-node-1", type: 'start', position: { x: 20, y: 64 }, parentNode: '6', data: { label: '大模型：生成批改意见' }},
  // { id:"6-node-2", type: 'start', position: { x: 20, y: 206 }, parentNode: '6', data: { label: '代码：汇总本轮结果' }},
  { id:"4", type: 'start', data: { label: '测试6666666666666666'}, position: { x: 100, y: 100 },expandParent: true,style: { width: '400px', height: '300px',background:"red"},},
  { id:"6", type: 'start', data: { label: '测试6666666666666666'}, position: { x: -20, y: 180 },parentNode: '4',extent: 'parent'},

  { id: "__END__", type: 'output', data: { label: '结束'}, position: { x: 100, y: 100 }, deletable: false,sourcePosition: 'right',targetPosition: 'left',blocks:[] },
])

const edges = ref([
  // for 节点底部 source 句柄 -> 循环函数节点顶部 target 句柄
  { id: 'e4-6', source: '4', target: '6', sourceHandle: 'for-bottom', targetHandle: 'loop-top', type: 'smoothstep' },
])

const nodeTypes = {
  start: markRaw(StartNode),
  for: markRaw(ForNode),
  LoopFunctionNod :markRaw(LoopFunctionNode),
}

</script>

<template>
<VueFlow
  v-model:nodes="nodes"
  v-model:edges="edges"
  :node-types="nodeTypes"
>

</VueFlow>

</template>



<style scoped>
.vue-flow__node-my-custom-node {
  background: #f0f0f0;
  border: 2px solid #6366f1;
}
</style>
