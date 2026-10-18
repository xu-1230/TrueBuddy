<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const path = computed(() => route.fullPath)

function goHome() {
  router.replace('/guides')
}

function goWorkspace() {
  router.replace('/index')
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  goHome()
}
</script>

<template>
  <main class="status-page">
    <section class="status-card">
      <p class="status-card__code">404</p>
      <h1 class="status-card__title">页面不存在</h1>
      <p class="status-card__text">地址可能已经变更，或这个页面还没有创建。</p>
      <p class="status-card__path">{{ path }}</p>

      <div class="status-card__actions">
        <el-button type="primary" @click="goWorkspace">进入工作台</el-button>
        <el-button @click="goHome">返回首页</el-button>
        <el-button text @click="goBack">返回上一页</el-button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.status-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: var(--tb-size-5);
  background-color: var(--tb-paper-bg);
  background-image:
    linear-gradient(90deg, var(--tb-paper-line) 1px, transparent 1px),
    linear-gradient(var(--tb-paper-line) 1px, transparent 1px);
  background-size: var(--tb-paper-size) var(--tb-paper-size);
}

.status-card {
  width: min(520px, 100%);
  padding: var(--tb-size-7) var(--tb-size-6);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
  text-align: center;
}

.status-card__code {
  margin: 0;
  color: var(--tb-primary-text);
  font-family: var(--font-mono);
  font-size: var(--tb-text-4xl, 56px);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-tight);
  line-height: 1;
}

.status-card__title {
  margin: var(--tb-size-3) 0 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-xl);
}

.status-card__text {
  margin: var(--tb-size-3) 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}

.status-card__path {
  margin: var(--tb-size-3) 0 0;
  padding: var(--tb-size-2) var(--tb-size-3);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-raised);
  color: var(--tb-text-tertiary);
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  word-break: break-all;
}

.status-card__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-6);
}
</style>
