<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message } from '@element-plus/icons-vue'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

const emit = defineEmits(['go-login'])

const formRef = ref()
const form = reactive({ email: '', code: '', password: '' })
const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [{ required: true, message: '请设置新密码', trigger: 'blur' }],
}
const { left, running, start } = useCodeCooldown()

async function sendCode() {
  // 邮箱没填对时不进入冷却，避免白白等一分钟
  const emailValid = await formRef.value
    .validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!emailValid) return
  if (!start()) return
  ElMessage.info('验证码已发送，请注意查收')
}

function submit() {
  formRef.value.validate((valid) => {
    if (!valid) return
    ElMessage.success('密码重置成功')
    emit('go-login')
  })
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
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" placeholder="注册时填写的邮箱" :prefix-icon="Message" />
    </el-form-item>
    <el-form-item label="验证码" prop="code">
      <div class="code-row">
        <el-input v-model="form.code" placeholder="6 位数字" :prefix-icon="Key" />
        <el-button class="code-btn" :disabled="running" @click="sendCode">
          {{ running ? `${left}s 后重发` : '获取验证码' }}
        </el-button>
      </div>
    </el-form-item>
    <el-form-item label="新密码" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        placeholder="设置新密码"
        show-password
        :prefix-icon="Lock"
      />
    </el-form-item>
    <el-button type="primary" class="submit-btn" size="large" native-type="button" @click="submit"
      >重置密码</el-button
    >
    <p class="form-row">
      <a
        class="form-link"
        role="button"
        tabindex="0"
        @click="emit('go-login')"
        @keyup.enter="emit('go-login')"
        >返回登录</a
      >
    </p>
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

.form-row {
  margin: var(--tb-size-3) 0 0;
  text-align: center;
}

.form-link {
  color: var(--tb-primary-text);
  font-size: var(--tb-text-sm);
  cursor: pointer;
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition:
    color var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease);
}

.form-link:hover {
  color: var(--tb-primary);
  border-bottom-color: var(--tb-primary-border);
}

.form-link:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  border-radius: var(--tb-radius-xs);
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

@media (prefers-reduced-motion: reduce) {
  .form-link {
    transition: none;
  }
}
</style>
