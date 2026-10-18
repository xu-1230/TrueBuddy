<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { MENU_TYPE, MENU_TYPE_OPTIONS } from '@/views/admin/constants'

/**
 * 菜单 / 权限点表单
 *
 * 目录与菜单需要 path，按钮只认 permission；类型切换时按需启用字段，
 * 避免出现「按钮带一个打不开的 path」这种脏数据。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  menu: { type: Object, default: null },
  parent: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()
const isEdit = computed(() => Boolean(props.menu?.id))
const isButton = computed(() => Number(form.type) === MENU_TYPE.BUTTON)

const form = reactive({
  name: '',
  type: MENU_TYPE.MENU,
  path: '',
  permission: '',
  icon: '',
  sort: 1,
  visible: true,
})

const rules = computed(() => ({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  ...(isButton.value
    ? {
        permission: [{ required: true, message: '按钮必须填写权限标识', trigger: 'blur' }],
      }
    : {
        path: [{ required: true, message: '目录 / 菜单必须填写路由地址', trigger: 'blur' }],
      }),
}))

function syncForm() {
  Object.assign(form, {
    name: props.menu?.name || '',
    type: props.menu?.type ?? MENU_TYPE.MENU,
    path: props.menu?.path || '',
    permission: props.menu?.permission || '',
    icon: props.menu?.icon || '',
    sort: props.menu?.sort ?? 1,
    visible: props.menu?.visible ?? true,
  })
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) syncForm()
  },
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  if (props.submitting) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  emit('submit', {
    parentId: props.parent?.id ?? props.menu?.parentId ?? null,
    name: form.name.trim(),
    type: Number(form.type),
    path: isButton.value ? '' : form.path.trim(),
    permission: form.permission.trim(),
    icon: form.icon.trim(),
    sort: Number(form.sort) || 0,
    visible: form.visible,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑菜单' : parent ? `新增子项 · ${parent.name}` : '新增根菜单'"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <div class="admin-form-grid">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：用户管理" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option
              v-for="item in MENU_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="路由地址" prop="path">
          <el-input v-model="form.path" :disabled="isButton" placeholder="如：/admin/user" />
        </el-form-item>

        <el-form-item label="权限标识" prop="permission">
          <el-input v-model="form.permission" placeholder="如：system:user:list" />
        </el-form-item>

        <el-form-item label="图标名" prop="icon">
          <el-input v-model="form.icon" placeholder="AdminIcon 的 name，如 users" />
        </el-form-item>

        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
        </el-form-item>

        <el-form-item label="是否显示" prop="visible" class="menu-form__full">
          <el-switch v-model="form.visible" active-text="显示" inactive-text="隐藏" />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <div class="admin-dialog__footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.menu-form__full {
  grid-column: 1 / -1;
}
</style>
