import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// 全局字体系统（Playfair Display + Inter），需在 Element Plus 样式之后引入以覆盖其字体变量
import './assets/fonts.css'

const app = createApp(App)

app.use(createPinia())
app.use(ElementPlus)
app.use(router)
app.mount('#app')
