import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
// import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 依赖预构建白名单：把体积大或引用层深的依赖在首次启动时一次预构建到位。
  // 不显式列出时，Vite 会在开发过程中陆续扫描到新依赖并触发
  // 「optimized dependencies changed, reloading」整页重载 —— 在 element-plus /
  // pixi.js / bytemd 这类大包上尤其容易反复发生，体感就是页面卡顿闪断。
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'js-cookie',
      'element-plus',
      '@element-plus/icons-vue',
      'gsap',
      'lenis',
      'pixi.js',
      'pixi-live2d-display/cubism4',
      'bytemd',
      '@bytemd/vue-next',
      '@bytemd/plugin-gfm',
      '@bytemd/plugin-highlight',
      '@bytemd/plugin-mermaid',
      'docx-preview',
      'jszip',
      'pptx-to-pptist',
      'pptxtojson',
      '@vue-flow/core',
      '@vue-flow/background',
    ],
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8086',
        changeOrigin: true,
      },
    },
  },
})
