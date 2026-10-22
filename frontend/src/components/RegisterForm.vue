<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, User } from '@element-plus/icons-vue'
import { register, sendCode as sendEmailCode } from '@/api/auth'
import { setRefreshToken, setToken } from '@/utils/auth'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

const emit = defineEmits(['login-success'])

const formRef = ref()
const submitting = ref(false)
const sendingCode = ref(false)
const form = reactive({ username: '', email: '', code: '', password: '', confirm: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [{ required: true, message: '请设置密码', trigger: 'blur' }],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}
const { left, running, start } = useCodeCooldown()

async function handleSendCode() {
  if (sendingCode.value || running.value) return
  // 邮箱没填对时不发请求，也不进入冷却
  const emailValid = await formRef.value
    .validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!emailValid) return

  sendingCode.value = true
  try {
    await sendEmailCode(form.email.trim(), 'register')
    start()
    ElMessage.success('验证码已发送，请注意查收')
  } catch (error) {
    ElMessage.error(error?.message || '验证码发送失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = await register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      code: form.code.trim(),
    })
    if (data?.accessToken) {
      setToken(data.accessToken)
      setRefreshToken(data.refreshToken)
    }
    ElMessage.success('注册成功')
    emit('login-success', data)
  } catch (error) {
    ElMessage.error(error?.message || '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-position="top"
    hide-required-asterisk
    size="large"
    @submit.prevent
    @keyup.enter="submit"
  >
    <el-form-item label="账号" prop="username">
      <el-input v-model="form.username" placeholder="学号 / 工号" :prefix-icon="User" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" placeholder="name@school.edu" :prefix-icon="Message" />
    </el-form-item>
    <el-form-item label="邮箱验证码" prop="code">
      <div class="code-row">
        <el-input v-model="form.code" placeholder="6 位数字" :prefix-icon="Key" />
        <el-button
          class="code-btn"
          :disabled="running"
          :loading="sendingCode"
          @click="handleSendCode"
        >
          {{ running ? `${left}s 后重发` : '获取验证码' }}
        </el-button>
      </div>
    </el-form-item>
    <el-form-item label="密码" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        placeholder="设置登录密码"
        show-password
        :prefix-icon="Lock"
      />
    </el-form-item>
    <el-form-item label="确认密码" prop="confirm">
      <el-input
        v-model="form.confirm"
        type="password"
        placeholder="再输入一次"
        show-password
        :prefix-icon="Lock"
      />
    </el-form-item>
    <el-button
      type="primary"
      class="submit-btn"
      size="large"
      :loading="submitting"
      native-type="button"
      @click="submit"
      >注册</el-button
    >
  </el-form>
</template>

<style scoped>
/* 字段与按钮排版统一在 LoginView 的 .side__form :deep(...) 里 */
.code-row {
  display: flex;
  gap: var(--tb-size-3);
  width: 100%;
}

.code-row .el-input {
  flex: 1;
  min-width: 0;
}

.code-btn {
  flex: 0 0 auto;
  min-width: 112px;
}

.submit-btn {
  width: 100%;
  margin-top: var(--tb-size-2);
}

@media (max-width: 768px) {
  .code-row {
    flex-wrap: wrap;
  }

  .code-btn {
    flex: 1 1 100%;
    min-height: var(--tb-control-lg);
  }
}
</style>
