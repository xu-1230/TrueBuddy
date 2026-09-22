<template>
   <div style="display: flex;padding: 20px;height: 100vh;">
     <!-- 明暗切换入口：登录页不使用内页外壳，故独立固定在右上角 -->
     <button
       type="button"
       class="page-appearance"
       role="switch"
       :aria-checked="isDark"
       :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
       :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
       @click="toggle"
     >
       <span class="page-appearance__icon" :class="{ 'is-shown': !isDark }">
         <el-icon><Sunny /></el-icon>
       </span>
       <span class="page-appearance__icon" :class="{ 'is-shown': isDark }">
         <el-icon><MoonNight /></el-icon>
       </span>
     </button>
     <div style="background-color: var(--tb-bg-raised);display: flex;flex: 1;flex-direction: row;gap: 12px;border-radius: var(--tb-radius-lg);overflow: hidden;">
        <div style="inline-size: 50%;align-items: center;display: flex;justify-content: center;">
                <div class="login-left">
                <div class="login-card">
                  <div class="login-card__header">
                    <h2 class="login-card__title">HELLO.</h2>
                  </div>

                  <Transition name="el-zoom-in-center" mode="out-in" appear>
                    <component
                      :is="currentComponent"
                      :key="formType"
                      @go-forgot="goForgot"
                      @go-login="switchForm('login')"
                      @login-success="onLoginSuccess"
                      @register-success="switchForm('login')"
                    />
                  </Transition>

                  <div class="account-switch">
                    <span>{{ formType === 'register' ? '已有账号？' : '没有账号？' }}</span>
                    <a class="account-switch__link" @click="switchForm(formType === 'register' ? 'login' : 'register')">
                      {{ formType === 'register' ? '点击登录' : '进行注册' }}
                    </a>
                  </div>
                </div>
                </div>
        </div>
        <!-- 右侧展示台：刻意保持深色（两态都比表单半屏更暗），故直接引用色板而非语义变量；
             内部白色块代表「幻灯片预览」，同样不随 UI 主题变化 -->
        <div style="inline-size: 50%;background-color: light-dark(var(--tb-neutral-900), var(--tb-neutral-950));display: flex;flex-direction: column;justify-content: center;overflow: hidden;gap: 40px;">
           <div style="font-size: 15px;color: var(--tb-neutral-50);">Continue exploring powerful features that make data collection effortless</div>
           <el-carousel class="carousel" height="400px" indicator-position="outside" type="card" arrow="never" interval="10000000">
             <el-carousel-item v-for="item in 4" :key="item">
                 <div style="font-size: 24px;color: var(--tb-neutral-50);">6666</div>
                 <div style="font-size: 15px;color: var(--tb-neutral-50);"> 777777777777777</div>
                 <div style="width: 90%;height: 300px;background-color: var(--tb-neutral-0);"> </div>
             </el-carousel-item>
           </el-carousel>
        </div>
     </div>
   </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MoonNight, Sunny } from '@element-plus/icons-vue'
import LoginForm from '../components/LoginForm.vue'
import RegisterForm from '../components/RegisterForm.vue'
import ForgetPasswordForm from '../components/ForgetPasswordForm.vue'
import { useAppearance } from '@/composables/useAppearance'

const router = useRouter()

/** 明暗态：本页不使用内页外壳，故独立提供切换入口 */
const { isDark, toggle } = useAppearance()

const formType = ref('login')

const formMap = {
  login: LoginForm,
  register: RegisterForm,
  forgot: ForgetPasswordForm,
}

const currentComponent = computed(() => formMap[formType.value])

function switchForm(key) {
  formType.value = key
}

function goForgot() {
  formType.value = 'forgot'
}

function onLoginSuccess() {
  router.push('/')
}
</script>

<style scoped>
/* 明暗切换按钮：固定右上角（登录页无内页外壳），两枚图标叠放交叉淡入 */
.page-appearance {
  position: fixed;
  top: 28px;
  right: 28px;
  z-index: 10;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--tb-control-md);
  height: var(--tb-control-md);
  padding: 0;
  border: 1px solid var(--tb-border-strong);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease),
    color var(--tb-motion-dur) var(--tb-motion-ease);
}

.page-appearance:hover {
  background: var(--tb-primary-soft);
  border-color: var(--tb-primary-border);
  color: var(--tb-primary-text);
}

.page-appearance:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.page-appearance__icon {
  position: absolute;
  display: inline-flex;
  opacity: 0;
  transform: rotate(-35deg) scale(0.7);
  transition:
    opacity var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-motion-dur) var(--tb-ease-spring);
}

.page-appearance__icon.is-shown {
  opacity: 1;
  transform: rotate(0) scale(1);
}

.el-carousel__item h3 {
  /* display: flex;
  color: #fff;
  border: 1px solid #409eff;
  background-color: red;
  align-items: center;
  justify-content: center;
  line-height: 280px;
  width: 100%;
  margin: 0; */
}

.el-carousel__item{
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  border: 1px solid var(--tb-brand-500);
  border-radius: var(--tb-radius-lg);
  background-color: light-dark(var(--tb-neutral-900), var(--tb-neutral-950));
}
:deep(.el-carousel__mask) {
  background-color: light-dark(var(--tb-neutral-900), var(--tb-neutral-950));
}

.carousel {
  width: 160%;
  margin-left: -30%;
}

.login-left{
  width: 100%;
  max-width: 400px;
}

.login-card__title{
  font-family: var(--tb-font-display);
  font-size: 38px;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  margin-bottom: 20px;
}

.account-switch {
  margin-top: 1rem;
  font-size: 0.875rem;
  color: var(--tb-text-secondary);
}

.account-switch__link {
  color: var(--tb-primary-text);
  cursor: pointer;
  margin-left: 4px;
}

@media (prefers-reduced-motion: reduce) {
  .page-appearance__icon {
    transition: none;
  }
}

/* @media (max-width: 900px) {

} */
</style>
