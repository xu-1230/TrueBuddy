import { defineConfig, globalIgnores } from 'eslint/config'
import globals from 'globals'
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import pluginOxlint from 'eslint-plugin-oxlint'

export default defineConfig([
  {
    name: 'app/files-to-lint',
    files: ['**/*.{vue,js,mjs,jsx}'],
  },

  // public/ 下为第三方静态资源（如 live2d cubism 运行时），不纳入 lint
  globalIgnores(['**/dist/**', '**/dist-ssr/**', '**/coverage/**', 'public/**']),

  {
    languageOptions: {
      globals: {
        ...globals.browser,
      },
    },
  },

  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],

  // 既有单名单词组件（路由入口 / 图表元素），重命名会波及路由与引用，单独放开命名规则
  {
    files: [
      'src/views/ppt/index.vue',
      'src/views/ppt/components/element/ChartElement/Chart.vue',
    ],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },

  ...pluginOxlint.buildFromOxlintConfigFile('.oxlintrc.json'),
])
