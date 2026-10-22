<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCheck, Key, Lock, Message, Right } from '@element-plus/icons-vue'
import { register, sendCode as sendEmailCode } from '@/api/auth'
import { setRefreshToken, setToken } from '@/utils/auth'
import { useCodeCooldown } from '@/composables/useCodeCooldown'

const emit = defineEmits(['login-success'])

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

function normalizeCode() {
  form.code = form.code.replace(/\D/g, '').slice(0, 6)
}

async function submit() {
  if (submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = await register({
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
  <div class="register-form">
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
      <div class="register-form__grid">
        <el-form-item class="register-form__item register-form__full" label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="name@example.com"
            maxlength="50"
            autocomplete="email"
            :prefix-icon="Message"
          />
        </el-form-item>

        <el-form-item
          class="register-form__item register-form__full"
          label="邮箱验证码"
          prop="code"
        >
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="6 位数字"
              maxlength="6"
              inputmode="numeric"
              autocomplete="one-time-code"
              :prefix-icon="Key"
              @input="normalizeCode"
            />
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

        <el-form-item class="register-form__item" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="设置登录密码"
            autocomplete="new-password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item class="register-form__item" label="确认密码" prop="confirm">
          <el-input
            v-model="form.confirm"
            type="password"
            placeholder="再输入一次"
            autocomplete="new-password"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
      </div>

      <el-button
        type="primary"
        class="submit-btn"
        size="large"
        :loading="submitting"
        native-type="button"
        @click="submit"
      >
        <span>创建账号</span>
        <el-icon class="submit-btn__icon"><Right /></el-icon>
      </el-button>

      <p class="register-form__note">
        <el-icon><CircleCheck /></el-icon>
        注册成功后直接进入工作台，无需再次登录
      </p>
    </el-form>
  </div>
</template>

<style scoped>
.register-form {
  width: 100%;
}

.register-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--tb-size-4);
}

.register-form__full {
  grid-column: 1 / -1;
}

/* 两列布局压缩纵向空间，同时给校验提示留出足够高度；
   选择器需压过 LoginView 中 .side__form .el-form-item 的同权重规则 */
.register-form__grid .register-form__item.el-form-item {
  margin-bottom: 20px;
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
  display: inline-flex;
  gap: var(--tb-size-1);
  width: 100%;
  margin-top: var(--tb-size-1);
}

.submit-btn__icon {
  font-size: 14px;
}

.register-form__note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--tb-size-1);
  margin: var(--tb-size-1) 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
}

.register-form__note .el-icon {
  color: var(--tb-primary-text);
  font-size: 12px;
}

@media (max-width: 768px) {
  .register-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .register-form__grid .register-form__item.el-form-item {
    margin-bottom: var(--tb-size-5);
  }

  /* 移动端保持验证码输入与按钮同一行，按钮固定宽度；仅极窄屏才换行 */
  .register-form .code-row {
    flex-wrap: nowrap;
  }

  .register-form .code-btn {
    flex: 0 0 112px;
    min-height: var(--tb-control-lg);
  }
}

@media (max-width: 340px) {
  .register-form .code-row {
    flex-wrap: wrap;
  }

  .register-form .code-btn {
    flex: 1 1 100%;
  }
}
</style>
