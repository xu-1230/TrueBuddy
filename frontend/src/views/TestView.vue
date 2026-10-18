<script setup>
import { ref } from 'vue'
import { login, ping, ping2 } from '@/api/test'
import { setToken } from '@/utils/auth'

const username = ref('')
const password = ref('')
const loginResult = ref('')
const pingResult = ref('')
const ping2Result = ref('')

const accounts = {
  normal: { label: '普通用户', u: 'normal', p: '123456' },
  member: { label: '会员', u: 'member', p: '123456' },
  admin: { label: '管理员', u: 'admin', p: '123456' },
}

function fillAccount(name) {
  const a = accounts[name]
  username.value = a.u
  password.value = a.p
}

async function handleLogin() {
  loginResult.value = '登录中...'
  try {
    const data = await login(username.value, password.value)
    if (data?.accessToken) {
      setToken(data.accessToken)
    }
    loginResult.value = JSON.stringify(data, null, 2)
  } catch (e) {
    loginResult.value = `登录失败：${e?.message ?? e}`
  }
}

async function handlePing() {
  pingResult.value = '请求中...'
  try {
    const data = await ping()
    pingResult.value = JSON.stringify(data, null, 2)
  } catch (e) {
    pingResult.value = formatError(e)
  }
}

async function handlePing2() {
  ping2Result.value = '请求中...'
  try {
    const data = await ping2()
    ping2Result.value = JSON.stringify(data, null, 2)
  } catch (e) {
    ping2Result.value = formatError(e)
  }
}

function formatError(e) {
  const status = e?.response?.status
  const msg = e?.response?.data?.message || e?.message || JSON.stringify(e)
  return `${msg}${status ? `（HTTP ${status}）` : ''}`
}
</script>

<template>
  <main class="test-page">
    <h1>登录与连通性测试</h1>

    <section>
      <h2>登录</h2>
      <div class="account-buttons">
        <button v-for="(a, key) in accounts" :key="key" @click="fillAccount(key)">
          {{ a.label }}
        </button>
      </div>
      <div class="login-form">
        <input v-model="username" placeholder="账号" />
        <input v-model="password" type="password" placeholder="密码" />
        <button @click="handleLogin">登录</button>
      </div>
      <pre>{{ loginResult || '暂无登录结果' }}</pre>
    </section>

    <section>
      <h2>受保护接口</h2>
      <div class="actions">
        <button @click="handlePing">/test/ping</button>
        <button @click="handlePing2">/test2/ping</button>
      </div>
      <pre>{{ pingResult || '未测试' }}</pre>
      <pre>{{ ping2Result || '未测试' }}</pre>
    </section>
  </main>
</template>

<style scoped>
.test-page {
  padding: 24px;
}

section {
  margin-bottom: 24px;
}

.actions,
.account-buttons,
.login-form {
  display: flex;
  gap: 12px;
  margin: 12px 0;
}

input {
  padding: 8px;
  min-width: 160px;
}

button {
  padding: 8px 16px;
  cursor: pointer;
}

pre {
  min-height: 40px;
  padding: 12px;
  background: var(--tb-bg-raised);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-all;
}

</style>