<template>
   <div style="display: flex;padding: 20px;height: 100vh;">
     <div style="background-color: rgb(247, 247, 248);display: flex;flex: 1;flex-direction: row;gap: 12px;border-radius: 15px;overflow: hidden;"> 
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
        <div style="inline-size: 50%;background-color: #262727;display: flex;flex-direction: column;justify-content: center;overflow: hidden;gap: 40px;">    
          <div style="font-size: 15px;color: #fff;">Continue exploring powerful features that make data collection effortless</div>
          <el-carousel class="carousel" height="400px" indicator-position="outside" type="card" arrow="never" interval="10000000"> 
            <el-carousel-item v-for="item in 4" :key="item">
                <div  style="font-size: 24px;color: #fff;">6666</div>
                <div style="font-size: 15px;color: #fff;"> 777777777777777</div>
                <div style="width: 90%;height: 300px;background-color: #fff;"> </div>
            </el-carousel-item>
          </el-carousel>
        </div>
     </div>
   </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import LoginForm from '../components/LoginForm.vue'
import RegisterForm from '../components/RegisterForm.vue'
import ForgetPasswordForm from '../components/ForgetPasswordForm.vue'

const router = useRouter()

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
  border: 1px solid #409eff;
  border-radius: 15px;
  background-color: #262727;
}
:deep(.el-carousel__mask) {
  background-color: #262727;
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
  font-family: var(--font-serif);
  font-size: 38px;
  font-weight: 700;
  letter-spacing: 0.02em;
  margin-bottom: 20px;
}

.account-switch {
  margin-top: 1rem;
  font-size: 0.875rem;
  color: #6b7280;
}

.account-switch__link {
  color: #ff4500;
  cursor: pointer;
  margin-left: 4px;
}

/* @media (max-width: 900px) {

} */
</style>
