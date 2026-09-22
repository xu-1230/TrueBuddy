import { computed, ref } from 'vue'

/**
 * 明暗态（light / dark）控制
 *
 * - 同时写入 html[data-appearance]（驱动 assets/theme.css 的 light-dark()）
 *   与 html.dark 类（驱动 Element Plus 暗色变量包），两者始终同步
 * - 优先级：localStorage 记忆 → 首帧脚本已写入的属性 → 系统偏好
 * - 监听系统偏好变化（仅当用户未显式选择过时跟随）与 storage 事件（多标签同步）
 *
 * 说明：状态与监听器在模块级只初始化一次（应用生命周期内常驻），
 * 因此组件侧无需 onBeforeUnmount 清理，也不会因组件卸载产生泄漏。
 */

/** 存储键：index.html 首帧防闪脚本使用同一键名，改动需同步 */
const STORAGE_KEY = 'tb-ui-mode'
const MODES = ['light', 'dark']

const mode = ref('light')
let initialized = false

/** 读取记忆值；非法或不可用（隐私模式）时返回 null */
function readStored() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return MODES.includes(saved) ? saved : null
  } catch {
    return null
  }
}

function writeStored(next) {
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    /* 隐私模式下写入失败，忽略即可：本次会话仍生效 */
  }
}

function systemMode() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** 单一写入口：同时更新响应式状态、html 属性与 Element Plus 的 dark 类 */
function apply(next) {
  mode.value = MODES.includes(next) ? next : 'light'
  const root = document.documentElement
  // data-appearance 驱动本项目的 light-dark()（assets/theme.css）
  root.setAttribute('data-appearance', mode.value)
  // dark 类驱动 Element Plus 暗色变量包（theme-chalk/dark/css-vars.css），两者必须同步
  root.classList.toggle('dark', mode.value === 'dark')
}

function init() {
  if (initialized) return
  initialized = true

  // 首帧脚本已在 index.html 中写过属性，这里只做状态对齐，避免重复触发样式重算
  const current = document.documentElement.getAttribute('data-appearance')
  apply(readStored() || (MODES.includes(current) ? current : systemMode()))

  const query = window.matchMedia('(prefers-color-scheme: dark)')
  query.addEventListener('change', () => {
    if (!readStored()) apply(systemMode())
  })

  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) apply(event.newValue === null ? systemMode() : event.newValue)
  })
}

export function useAppearance() {
  init()

  const isDark = computed(() => mode.value === 'dark')

  function setMode(next, { persist = true } = {}) {
    apply(next)
    if (persist) writeStored(mode.value)
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  return { mode, isDark, setMode, toggle }
}
