<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'

const emit = defineEmits(['register-success'])

const formRef = ref()
const form = reactive({ username: '', email: '', password: '', confirm: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
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

function submit() {
  formRef.value.validate((valid) => {
    if (!valid) return
    ElMessage.success('注册成功')
    emit('register-success')
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
    <el-form-item label="账号" prop="username">
      <el-input v-model="form.username" placeholder="学号 / 工号" :prefix-icon="User" />
    </el-form-item>
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" placeholder="name@school.edu" :prefix-icon="Message" />
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
    <el-button type="primary" class="submit-btn" size="large" native-type="button" @click="submit"
      >注册</el-button
    >
  </el-form>
</template>

<style scoped>
/* 字段与按钮排版统一在 LoginView 的 .side__form :deep(...) 里 */
.submit-btn {
  width: 100%;
  margin-top: var(--tb-size-2);
}
</style>
