<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ROLE_OPTIONS, USER_STATUS_OPTIONS } from '@/views/admin/constants'

/**
 * 用户新增 / 编辑弹窗
 *
 * 只负责表单与校验，保存请求交给页面：同一份表单在「接口可用」与
 * 「演示数据」两种模式下走不同落点，请求逻辑留在页面更容易看清。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  user: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const formRef = ref()
const isEdit = computed(() => Boolean(props.user?.id))

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  roles: ['normal'],
  status: 0,
})

const rules = computed(() => ({
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  roles: [{ type: 'array', required: true, message: '请至少选择一个角色', trigger: 'change' }],
  ...(isEdit.value
    ? {}
    : {
        password: [
          { required: true, message: '请输入初始密码', trigger: 'blur' },
          { min: 6, message: '密码至少 6 位', trigger: 'blur' },
        ],
      }),
}))

function syncForm() {
  const user = props.user
  Object.assign(form, {
    username: user?.username || '',
    nickname: user?.nickname || '',
    email: user?.email || '',
    password: '',
    roles: Array.isArray(user?.roles) && user.roles.length > 0 ? [...user.roles] : ['normal'],
    status: user?.status ?? 0,
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
    username: form.username.trim(),
    nickname: form.nickname.trim(),
    email: form.email.trim(),
    password: form.password,
    roles: [...form.roles],
    status: form.status,
  })
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑用户' : '新增用户'"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <div class="admin-form-grid">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号，创建后不可修改" />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示名称" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于通知与找回密码" />
        </el-form-item>

        <el-form-item v-if="!isEdit" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>

        <el-form-item label="角色" prop="roles" class="admin-form-grid__full">
          <el-select v-model="form.roles" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status" class="admin-form-grid__full">
          <el-radio-group v-model="form.status">
            <el-radio v-for="item in USER_STATUS_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
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
.admin-form-grid__full {
  grid-column: 1 / -1;
}
</style>
