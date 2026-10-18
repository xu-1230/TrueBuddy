<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { USER_STATUS_OPTIONS } from '@/views/admin/constants'

/**
 * 角色新增 / 编辑弹窗
 *
 * code 是权限判断真正使用的值（system_role.code），创建后不建议再改，
 * 编辑态直接禁用输入，避免「改了编码但用户还在用旧角色」的隐性故障。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  role: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()
const isEdit = computed(() => Boolean(props.role?.id))

const form = reactive({
  name: '',
  code: '',
  status: 0,
  remark: '',
})

const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]*$/, message: '小写字母开头，仅含小写字母、数字、_ 或 -', trigger: 'blur' },
  ],
}

function syncForm() {
  Object.assign(form, {
    name: props.role?.name || '',
    code: props.role?.code || '',
    status: props.role?.status ?? 0,
    remark: props.role?.remark || '',
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
    name: form.name.trim(),
    code: form.code.trim(),
    status: form.status,
    remark: form.remark.trim(),
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑角色' : '新增角色'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <div class="admin-form-grid">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如：审核员" />
        </el-form-item>

        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" placeholder="如：auditor" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in USER_STATUS_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注" prop="remark" class="role-form__full">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="这个角色能做什么" />
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
.role-form__full {
  grid-column: 1 / -1;
}
</style>
