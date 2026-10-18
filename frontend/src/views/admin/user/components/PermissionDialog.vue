<script setup>
import { ref } from 'vue'

/**
 * 角色权限分配弹窗
 *
 * 用 el-tree 的父子联动勾选；保存时把「全选节点 + 半选节点」一起提交，
 * 否则只勾了部分子项时父目录会丢，后端重建菜单树时这一支会缺一级。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  role: { type: Object, default: null },
  treeData: { type: Array, default: () => [] },
  checkedIds: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const treeRef = ref()

const treeProps = { label: 'name', children: 'children' }

function close() {
  emit('update:modelValue', false)
}

function submit() {
  if (props.submitting) return
  const checked = treeRef.value?.getCheckedKeys() || []
  const halfChecked = treeRef.value?.getHalfCheckedKeys() || []
  emit('submit', [...new Set([...checked, ...halfChecked])])
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`分配权限 · ${role?.name || ''}`"
    width="520px"
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="permission-dialog__tip">
      勾选该角色可访问的菜单与按钮。未勾选的页面即使通过地址直接访问，后端也应拒绝。
    </p>

    <div class="permission-dialog__tree">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        :default-checked-keys="checkedIds"
      />
      <el-empty v-if="treeData.length === 0" description="暂无菜单数据" :image-size="56" />
    </div>

    <template #footer>
      <div class="admin-dialog__footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存权限</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.permission-dialog__tip {
  margin: 0 0 var(--tb-size-3);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
}

.permission-dialog__tree {
  max-height: 420px;
  padding: var(--tb-size-2);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  overflow-y: auto;
}
</style>
