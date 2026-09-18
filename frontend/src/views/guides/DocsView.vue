<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import LoginForm from '../../components/LoginForm.vue'

const activeTab = ref('account')


// 用户评价轮播数据
const testimonials = [
  {
    quote: 'TrueBuddy 让情绪有了出口，无论多晚都能被温柔接住。',
    author: '林晓',
    role: '大学生',
  },
  {
    quote: '它更像是懂我的朋友，帮我理清了很多说不出口的烦恼。',
    author: '陈晨',
    role: '职场新人',
  },
  {
    quote: '每天的陪伴和记录让我更专注，也更有动力坚持下去。',
    author: '苏然',
    role: '自由职业者',
  },
  {
    quote: '简单好用，尤其喜欢 AI 的理解力，回应总是恰到好处。',
    author: '顾言',
    role: '产品经理',
  },
]

const current = ref(0)
let timer = null

function goTo(index) {
  current.value = index
  startAuto()
}

function startAuto() {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    current.value = (current.value + 1) % testimonials.length
  }, 5000)
}

onMounted(startAuto)
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="login-page">
    <!-- 左侧登录区 -->
    <div class="login-left">
      <div class="login-card">
        <div class="login-card__header">
          <h2 class="login-card__title">登录</h2>
        </div>

        <div class="login-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.name"
            type="button"
            class="login-tab"
            :class="{ 'login-tab--active': activeTab === tab.name }"
            @click="activeTab = tab.name"
          >
            {{ tab.label }}
          </button>
        </div>

        <Transition name="el-zoom-in-center" mode="out-in" appear>
            <LoginForm />
        </Transition>
      </div>
    </div>

    <!-- 右侧品牌区 -->
    <div class="login-right">
      <div class="brand">
        <div class="brand__logo">
          <svg viewBox="0 0 120 120" aria-hidden="true">
            <rect x="24" y="37" width="16" height="62" fill="#111" />
            <line x1="24" y1="44" x2="40" y2="44" stroke="#fff" stroke-width="3" />
            <rect x="46" y="22" width="16" height="74" fill="none" stroke="#111" stroke-width="6" />
            <rect x="68" y="47" width="16" height="52" fill="#111" />
            <line x1="67" y1="55" x2="85" y2="55" stroke="#fff" stroke-width="3" />
            <rect x="90" y="28" width="16" height="68" fill="none" stroke="#111" stroke-width="6" />
          </svg>
        </div>
        <div class="brand__text">
          <span class="brand__name">TrueBuddy</span>
          <span class="brand__slogan">你的智能陪伴助手</span>
        </div>
      </div>

      <div class="testimonial">
        <div class="testimonial__dots">
          <button
            v-for="(t, i) in testimonials"
            :key="i"
            type="button"
            class="dot"
            :class="{ 'dot--active': i === current }"
            :aria-label="`前往用户评价 ${i + 1}`"
            @click="goTo(i)"
          ></button>
        </div>

        <div class="testimonial__slides">
          <div
            class="testimonial__track"
            :style="{ transform: `translateX(-${current * 100}%)` }"
          >
            <div v-for="(t, i) in testimonials" :key="i" class="testimonial__slide">
              <blockquote class="quote">
                <span class="quote__mark quote__mark--left">“</span>
                <span class="quote__mark quote__mark--right">”</span>
                <p class="quote__text">{{ t.quote }}</p>
              </blockquote>
              <div class="author">
                <div class="author__avatar">{{ t.author.substring(0, 1) }}</div>
                <div class="author__info">
                  <div class="author__name">{{ t.author }}</div>
                  <div class="author__role">{{ t.role }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  background: #f5f7fb;
}

/* 左侧登录区 */
.login-left {
  flex: 0 0 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 2.5rem 2.25rem;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.06);
}

.login-card__header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.login-card__title {
  font-size: 1.75rem;
  font-weight: 700;
  color: #111827;
}

.login-card__subtitle {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  color: #6b7280;
}

.login-tabs {
  display: flex;
  margin-bottom: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
}

.login-tab {
  flex: 1;
  padding: 0.75rem 0.25rem;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 0.95rem;
  color: #6b7280;
  position: relative;
  transition: color 0.2s;
}

.login-tab:hover {
  color: #6366f1;
}

.login-tab--active {
  color: #6366f1;
  font-weight: 600;
}

.login-tab--active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #6366f1;
}

/* 右侧品牌区 */
.login-right {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 55%, #a855f7 100%);
  padding: 5rem 3rem 3rem;
}

.brand {
  position: absolute;
  top: 2rem;
  right: 3rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand__logo {
  width: 52px;
  height: 52px;
  padding: 8px;
  background: #fff;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand__logo svg {
  width: 100%;
  height: 100%;
}

.brand__text {
  display: flex;
  flex-direction: column;
}

.brand__name {
  font-size: 1.15rem;
  font-weight: 700;
  color: #fff;
}

.brand__slogan {
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.85);
}

.testimonial {
  width: 100%;
  max-width: 640px;
}

.testimonial__dots {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-bottom: 2rem;
  z-index: 1;
}

.dot {
  height: 4px;
  width: 8px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.4);
  transition: all 0.4s ease;
}

.dot:hover {
  background: rgba(255, 255, 255, 0.7);
}

.dot--active {
  width: 20px;
  background: #fff;
}

.testimonial__slides {
  overflow: hidden;
}

.testimonial__track {
  display: flex;
  transition: transform 0.4s ease;
}

.testimonial__slide {
  flex: 0 0 100%;
  padding: 0 28px;
}

.quote {
  position: relative;
  margin: 0 0 2rem;
  color: #fff;
}

.quote__mark {
  position: absolute;
  font-size: 96px;
  line-height: 1;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.35);
}

.quote__mark--left {
  top: -24px;
  left: -24px;
}

.quote__mark--right {
  bottom: -52px;
  right: -24px;
}

.quote__text {
  position: relative;
  font-size: 1.15rem;
  line-height: 1.8;
}

.author {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.author__avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-weight: 600;
  font-size: 1rem;
}

.author__name {
  font-weight: 600;
  color: #fff;
}

.author__role {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.75);
}

@media (max-width: 900px) {
  .login-right {
    display: none;
  }

  .login-left {
    flex: 1;
  }
}
</style>