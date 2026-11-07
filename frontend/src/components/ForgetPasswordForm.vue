<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, Right } from '@element-plus/icons-vue'
import { resetPassword, sendCode as sendEmailCode } from '@/api/auth'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

const emit = defineEmits(['go-login'])

const formRef = ref()
const submitting = ref(false)
const sendingCode = ref(false)
const form = reactive({ email: '', code: '', password: '', confirm: '' })
const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [{ required: true, message: '请设置新密码', trigger: 'blur' }],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
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
  // 邮箱没填对时不进入冷却，避免白白等一分钟
  const emailValid = await formRef.value
    .validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!emailValid) return
  if (sendingCode.value || running.value) return

  sendingCode.value = true
  try {
    await sendEmailCode(form.email.trim(), 'reset')
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
    const data = await resetPassword({
      email: form.email.trim(),
      code: form.code.trim(),
      password: form.password,
    })
    ElMessage.success('密码重置成功，请用新密码登录')
    emit('go-login', data?.email || '')
  } catch (error) {
    ElMessage.error(error?.message || '密码重置失败，请稍后重试')
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
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" placeholder="注册时填写的邮箱" :prefix-icon="Message" />
    </el-form-item>
    <el-form-item label="验证码" prop="code">
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
    <el-form-item label="新密码" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        placeholder="设置新密码"
        show-password
        :prefix-icon="Lock"
      />
    </el-form-item>
    <el-form-item label="确认新密码" prop="confirm">
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
      >重置密码</el-button
    >
    <!-- 与登录/注册同一套「提交按钮 → 或 → 切换」结构，三页底部节奏一致 -->
    <div class="or" aria-hidden="true"><span>或</span></div>
    <button type="button" class="mode-switch" @click="emit('go-login')">
      <span>返回登录</span>
      <el-icon><Right /></el-icon>
    </button>
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

/* 「或」分隔与切换按钮沿用 LoginForm 的结构件，三页底部观感一致 */
.or {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  margin: var(--tb-size-4) 0 var(--tb-size-2);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

.or::before,
.or::after {
  content: '';
  flex: 1;
  height: 1px;
  background-color: var(--tb-border);
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  padding: var(--tb-size-1) 0;
  border: 0;
  background: none;
  color: var(--tb-text-secondary);
  font-family: inherit;
  font-size: var(--tb-text-sm);
  cursor: pointer;
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.mode-switch:hover {
  color: var(--tb-primary-text);
}

.mode-switch:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 3px;
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
  .mode-switch {
    transition: none;
  }
}
</style>
