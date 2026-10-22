<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, Right, User } from '@element-plus/icons-vue'
import { login, loginByCode, sendCode as sendEmailCode } from '@/api/auth'
import { setRefreshToken, setToken } from '@/utils/auth'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

const props = defineProps({
  initialUsername: { type: String, default: '' },
})

const emit = defineEmits(['go-forgot', 'login-success'])

// 登录方式：account（账号密码） / mail（邮箱验证码）
const mode = ref('account')

// 账号登录
const accountFormRef = ref()
const accountForm = reactive({ username: '', password: '', remember: true })
const accountRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}
const submitting = ref(false)
const sendingCode = ref(false)

// 找回密码成功后带回账号，直接落到输入框
watch(
  () => props.initialUsername,
  (value) => {
    if (value) accountForm.username = value
  },
  { immediate: true },
)

// 邮箱登录
const mailFormRef = ref()
const mailForm = reactive({ email: '', code: '' })
const mailRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}
const { left, running, start } = useCodeCooldown()

function toggleMode() {
  mode.value = mode.value === 'account' ? 'mail' : 'account'
}

async function submitAccount() {
  const valid = await accountFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 响应拦截器已解包 CommonResult：code !== 0 与网络异常都会 reject，故这里只需处理成功与失败两支
    const data = await login(accountForm.username, accountForm.password)
    // 勾选「记住我」才写 30 天持久 Cookie，否则维持会话级
    const expiresDays = accountForm.remember ? 30 : undefined
    setToken(data.accessToken, expiresDays)
    // 刷新令牌用于 accessToken 过期后换取新令牌，必须与访问令牌一起保存
    setRefreshToken(data.refreshToken, expiresDays)
    // 响应里的 roles / permissions 交给登录页做落地分流；管理端仍会调 profile 复核
    emit('login-success', data)
  } catch (error) {
    ElMessage.error(error?.message || '登录失败，请检查账号密码或后端服务')
  } finally {
    submitting.value = false
  }
}

async function submitMail() {
  const valid = await mailFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const data = await loginByCode(mailForm.email.trim(), mailForm.code.trim())
    setToken(data.accessToken)
    setRefreshToken(data.refreshToken)
    ElMessage.success('登录成功')
    emit('login-success', data)
  } catch (error) {
    ElMessage.error(error?.message || '登录失败，请检查验证码或后端服务')
  } finally {
    submitting.value = false
  }
}

function submit() {
  if (submitting.value) return
  if (mode.value === 'account') {
    submitAccount()
  } else {
    submitMail()
  }
}

async function sendCode() {
  // 邮箱格式没过时不进入冷却，否则用户要在 60 秒里干等
  const emailValid = await mailFormRef.value
    .validateField('email')
    .then(() => true)
    .catch(() => false)
  if (!emailValid) return
  if (sendingCode.value || running.value) return

  sendingCode.value = true
  try {
    await sendEmailCode(mailForm.email.trim(), 'login')
    start()
    ElMessage.success('验证码已发送，请注意查收')
  } catch (error) {
    ElMessage.error(error?.message || '验证码发送失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}
</script>

<template>
  <div class="login-form">
    <!-- 账号登录 -->
    <el-form
      v-if="mode === 'account'"
      ref="accountFormRef"
      :model="accountForm"
      :rules="accountRules"
      label-position="top"
      hide-required-asterisk
      size="large"
      @submit.prevent
      @keyup.enter="submitAccount"
    >
      <el-form-item label="账号" prop="username">
        <el-input v-model="accountForm.username" placeholder="学号 / 工号" :prefix-icon="User" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="accountForm.password"
          type="password"
          placeholder="登录密码"
          show-password
          :prefix-icon="Lock"
        />
      </el-form-item>
      <div class="form-row">
        <el-checkbox v-model="accountForm.remember">记住我</el-checkbox>
        <a
          class="form-link"
          role="button"
          tabindex="0"
          @click="emit('go-forgot')"
          @keyup.enter="emit('go-forgot')"
          >忘记密码？</a
        >
      </div>
    </el-form>

    <!-- 邮箱登录 -->
    <el-form
      v-else
      ref="mailFormRef"
      :model="mailForm"
      :rules="mailRules"
      label-position="top"
      hide-required-asterisk
      size="large"
      @submit.prevent
      @keyup.enter="submitMail"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="mailForm.email" placeholder="name@school.edu" :prefix-icon="Message" />
      </el-form-item>
      <el-form-item label="验证码" prop="code">
        <div class="code-row">
          <el-input v-model="mailForm.code" placeholder="6 位数字" :prefix-icon="Key" />
          <el-button
            class="code-btn"
            :disabled="running"
            :loading="sendingCode"
            @click="sendCode"
          >
            {{ running ? `${left}s 后重发` : '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>

    <el-button
      type="primary"
      class="submit-btn"
      size="large"
      :loading="submitting"
      native-type="button"
      @click="submit"
      >登录</el-button
    >

    <div class="or" aria-hidden="true"><span>或</span></div>
    <button type="button" class="mode-switch" @click="toggleMode">
      <span>{{ mode === 'account' ? '改用邮箱验证码登录' : '改用账号密码登录' }}</span>
      <el-icon><Right /></el-icon>
    </button>
  </div>
</template>

<style scoped>
/* 字段标签、输入框与主按钮的排版由 LoginView 的 .side__form :deep(...) 统一定义，
   本文件只留自己的结构件 */
.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin: calc(var(--tb-size-1) * -1) 0 var(--tb-size-2);
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
    /* 窄屏把验证码按钮挤到下一行，保证输入框不被压扁 */
    flex-wrap: wrap;
  }

  .code-btn {
    flex: 1 1 100%;
    min-height: var(--tb-control-lg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .form-link,
  .mode-switch {
    transition: none;
  }
}
</style>
