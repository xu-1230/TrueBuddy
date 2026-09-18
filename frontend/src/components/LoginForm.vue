<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, Key } from '@element-plus/icons-vue'
import { login } from '@/api/test'
import { setToken } from '@/utils/auth'

const emit = defineEmits(['go-forgot', 'login-success'])

// 登录方式：account（账号密码） / mail（邮箱验证码）
const mode = ref('mail')

// 账号登录
const accountFormRef = ref()
const accountForm = reactive({ username: '', password: '', remember: true })
const accountRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

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

function toggleMode() {
  mode.value = mode.value === 'account' ? 'mail' : 'account'
}

async function submitAccount() {
  accountFormRef.value.validate(async (valid) => {
    if (!valid) return
    const data = await login(accountForm.username, accountForm.password)
    if (data?.accessToken) {
      setToken(data.accessToken)
      emit('login-success')
    } else {
      ElMessage.error(data || '登录失败')
    }
  })
}

function submitMail() {
  mailFormRef.value.validate((valid) => {
    if (!valid) return
    ElMessage.success('登录成功')
    emit('login-success')
  })
}

function submit() {
  mode.value === 'account' ? submitAccount() : submitMail()
}

function sendCode() {
  ElMessage.info('验证码已发送，请注意查收')
}
</script>

<template>
  <div>
    <!-- 账号登录 -->
    <el-form
      v-if="mode === 'account'"
      ref="accountFormRef"
      :model="accountForm"
      :rules="accountRules"
      size="large"
      @keyup.enter="submitAccount"
    >
      <el-form-item prop="username">
        <el-input v-model="accountForm.username" placeholder="账号" :prefix-icon="User" />
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="accountForm.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
      </el-form-item>
      <!-- <div class="form-row">
        <el-checkbox v-model="accountForm.remember">记住我</el-checkbox>
        <a class="form-link" @click="emit('go-forgot')">忘记密码？</a>
      </div> -->
    </el-form>

    <!-- 邮箱登录 -->
    <el-form
      v-else
      ref="mailFormRef"
      :model="mailForm"
      :rules="mailRules"
      size="large"
      @keyup.enter="submitMail"
    >
      <el-form-item prop="email">
        <el-input v-model="mailForm.email" placeholder="邮箱" :prefix-icon="Message" />
      </el-form-item>
      <el-form-item prop="code">
        <div class="code-row">
          <el-input v-model="mailForm.code" placeholder="验证码" :prefix-icon="Key" />
          <el-button class="code-btn" @click="sendCode">获取验证码</el-button>
        </div>
      </el-form-item>
    </el-form>

    <el-button type="primary" class="submit-btn" size="large" @click="submit">登录</el-button>
    <el-button type="primary" class="toggle-btn" size="large" plain @click="toggleMode">{{ mode === 'account' ? '使用邮箱登录' : '使用账号登录' }}</el-button>
  </div>
</template>

<style scoped>
.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-link {
  font-size: 0.875rem;
  color: #6366f1;
  cursor: pointer;
}

.code-row {
  display: flex;
  gap: 0.75rem;
  width: 100%;
}

.code-btn {
  flex: 0 0 auto;
}

.submit-btn {
  width: 100%;
  margin-top: 0.5rem;
  border-radius: 10px;
}

.toggle-btn {
  width: 100%;
  margin-top: 0.75rem;
  margin-left: 0 !important;
  border-radius: 10px;
}

.toggle-row {
  margin-top: 0.75rem;
  text-align: center;
}
</style>