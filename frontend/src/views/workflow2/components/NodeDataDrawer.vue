<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CloseBold, Delete, FullScreen, Plus, MagicStick, SwitchButton, VideoPlay } from '@element-plus/icons-vue'
import { useVueFlow } from '@vue-flow/core'

defineProps({
  /** 受控显隐，配 `v-model:visible` */
  visible: { type: Boolean, default: false },
  /** 当前编辑的节点 id：只存 id，节点对象每次实时从画布取，避免拿到过期引用 */
  nodeId: { type: String, default: null },
})
</script>

<template>

</template>

<style scoped>

</style>
