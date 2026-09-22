<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Message, Key, Lock } from '@element-plus/icons-vue'

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

function submit() {
  formRef.value.validate((valid) => {
    if (!valid) return
    ElMessage.success('密码重置成功')
    emit('go-login')
  })
}

function sendCode() {
  ElMessage.info('验证码已发送，请注意查收')
}
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" size="large">
    <el-form-item prop="email">
      <el-input v-model="form.email" placeholder="邮箱" :prefix-icon="Message" />
    </el-form-item>
    <el-form-item prop="code">
      <div class="code-row">
        <el-input v-model="form.code" placeholder="验证码" :prefix-icon="Key" />
        <el-button class="code-btn" @click="sendCode">获取验证码</el-button>
      </div>
    </el-form-item>
    <el-form-item prop="password">
      <el-input
        v-model="form.password"
        type="password"
        placeholder="新密码"
        show-password
        :prefix-icon="Lock"
      />
    </el-form-item>
    <el-button type="primary" class="submit-btn" size="large" @click="submit">重置密码</el-button>
    <div class="form-row">
      <a class="form-link" @click="emit('go-login')">返回登录</a>
    </div>
  </el-form>
</template>

<style scoped>
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
  border-radius: var(--tb-radius-md);
  background: var(--tb-primary);
  border-color: var(--tb-primary);
}

.form-row {
  margin-top: 0.75rem;
  text-align: center;
}

.form-link {
  font-size: 0.875rem;
  color: var(--tb-primary-text);
  cursor: pointer;
}
</style>