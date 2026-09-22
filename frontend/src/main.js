import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 暗色变量包（选择器 html.dark，由 useAppearance / 首帧脚本同步开关）
// 放在 EP 主样式之后铺底；theme.css 里 :root:root 的 --el-* 语义映射会稳定覆盖它
import 'element-plus/theme-chalk/dark/css-vars.css'

// 全局字体系统（Playfair Display + Inter），需在 Element Plus 样式之后引入以覆盖其字体变量
import './assets/fonts.css'

// 全局设计 Token 层（Harbor 主题：色板 + 语义变量 + 明暗两态 + --el-* 映射）
// 必须排在 Element Plus 样式之后，才能覆盖其默认的 --el-* 变量
import './assets/theme.css'

const app = createApp(App)

app.use(createPinia())
app.use(ElementPlus)
app.use(router)
app.mount('#app')
