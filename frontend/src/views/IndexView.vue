<template>
  <div class="index-layout">
    <aside
      class="sidebar"
      :class="{ 'sidebar--collapsed': collapsed }"
      :style="sidebarVars"
    >
      <div class="sidebar__header">
        <a class="sidebar__brand" href="/index" aria-label="TrueBuddy 首页">
          <svg class="sidebar__logo" viewBox="0 0 120 120" aria-hidden="true">
            <rect x="24" y="37" width="16" height="62" fill="currentColor" />
            <line x1="24" y1="44" x2="40" y2="44" stroke="var(--tb-bg-raised)" stroke-width="3" />
            <rect x="46" y="22" width="16" height="74" fill="none" stroke="currentColor" stroke-width="6" />
            <rect x="68" y="47" width="16" height="52" fill="currentColor" />
            <line x1="67" y1="55" x2="85" y2="55" stroke="var(--tb-bg-raised)" stroke-width="3" />
            <rect x="90" y="28" width="16" height="68" fill="none" stroke="currentColor" stroke-width="6" />
          </svg>
          <span class="sidebar__name">TrueBuddy</span>
        </a>
        <el-tooltip
          content="展开侧边栏"
          placement="right"
          :offset="14"
          :show-after="160"
          :disabled="!collapsed"
        >
          <button
            type="button"
            class="sidebar__collapse"
            :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
            :aria-expanded="!collapsed"
            @click="collapsed = !collapsed"
          >
            <el-icon :size="30">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </button>
        </el-tooltip>
      </div>

      <!-- 积分卡片：Logo 下方常驻，折叠态收敛为只显示余额的整卡可点入口 -->
      <el-tooltip
        :content="creditsTip"
        placement="right"
        :offset="14"
        :show-after="160"
        :disabled="!collapsed"
      >
        <section
          class="credits"
          :role="collapsed ? 'button' : undefined"
          :tabindex="collapsed ? 0 : undefined"
          :aria-label="collapsed ? creditsTip : '积分余额'"
          @click="collapsed && handleUpgrade()"
          @keydown.enter="collapsed && handleUpgrade()"
        >
          <span class="credits__main">
            <span class="credits__label">剩余积分</span>
            <span class="credits__value">{{ creditsText }}</span>
          </span>
          <button type="button" class="credits__cta" @click="handleUpgrade">立即升级</button>
        </section>
      </el-tooltip>

      <nav class="sidebar__menu" aria-label="主导航">
        <div class="sidebar__track" :style="{ '--menu-y': menuOffset + 'px' }">
          <!-- 选中滑块：位移为脚本按「分组标题 + 固定行高」算出的像素值，不做运行时测量 -->
          <span class="sidebar__pill" :class="{ 'is-hidden': !hasActive }" aria-hidden="true" />

          <div v-for="group in menuGroups" :key="group.label" class="sidebar__group">
            <p class="sidebar__group-label">{{ group.label }}</p>

            <el-tooltip
              v-for="item in group.items"
              :key="item.label"
              :content="item.label"
              placement="right"
              :offset="14"
              :show-after="160"
              :disabled="!collapsed"
            >
              <button
                type="button"
                class="menu-item"
                :class="{ 'menu-item--active': isActive(item) }"
                :aria-current="isActive(item) ? 'page' : undefined"
                @click="go(item)"
              >
                <AdminIcon class="admin-menu-item__icon" :name="item.icon" :size="18" />
                <span class="menu-item__label">{{ item.label }}</span>
              </button>
            </el-tooltip>
          </div>
        </div>
      </nav>

      <section class="sidebar__insights" aria-label="存储空间">
        <el-tooltip
          :content="storageTip"
          placement="right"
          :offset="14"
          :show-after="160"
          :disabled="!collapsed"
        >
          <div
            class="storage"
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="storagePercent"
            :aria-label="storageTip"
          >
            <span class="storage__ring">
              <svg class="storage__svg" viewBox="0 0 44 44" aria-hidden="true">
                <defs>
                  <linearGradient id="tb-storage-ring" x1="0" y1="0" x2="1" y2="1">
                    <stop class="storage__stop-from" offset="0" />
                    <stop class="storage__stop-to" offset="1" />
                  </linearGradient>
                </defs>
                <circle class="storage__ring-track" cx="22" cy="22" r="18" />
                <circle
                  class="storage__ring-bar"
                  cx="22"
                  cy="22"
                  r="18"
                  :stroke-dasharray="ringLength"
                  :stroke-dashoffset="ringOffset"
                />
              </svg>
              <span class="storage__percent">{{ storagePercent }}%</span>
            </span>
            <span class="storage__body">
              <span class="storage__label">存储空间</span>
              <span class="storage__value">{{ storageText }}</span>
            </span>
          </div>
        </el-tooltip>
      </section>

      <div class="sidebar__footer">
        <!-- 账户入口即菜单入口：点击头像区域弹出账户菜单，不再单设「更多」按钮 -->
        <el-dropdown
          class="sidebar__user-drop"
          trigger="click"
          placement="top-end"
          @command="handleUserCommand"
        >
          <button
            type="button"
            class="sidebar__user"
            aria-label="账户菜单"
            aria-haspopup="menu"
          >
            <el-avatar :size="32" class="sidebar__avatar">{{ user.initial }}</el-avatar>
            <span class="sidebar__user-info">
              <span class="sidebar__username">{{ user.name }}</span>
              <span class="sidebar__role">{{ user.role }}</span>
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="app" :icon="Iphone">下载手机应用</el-dropdown-item>
              <el-dropdown-item command="settings" :icon="Setting">系统设置</el-dropdown-item>
              <el-dropdown-item command="help" :icon="QuestionFilled">帮助与反馈</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div class="sidebar__actions">
          <el-tooltip :content="notificationTip" placement="top" :offset="10" :show-after="160">
            <button
              type="button"
              class="sidebar__setting"
              :aria-label="notificationTip"
            >
              <el-icon :size="18"><Bell /></el-icon>
              <span v-if="notifications.unread > 0" class="sidebar__badge" aria-hidden="true">
                {{ notifications.unread }}
              </span>
            </button>
          </el-tooltip>
        </div>
      </div>
    </aside>

    <!-- 系统设置：主题切换与偏好项统一收在这里 -->
    <el-dialog v-model="settingsVisible" title="系统设置" width="400px" append-to-body>
      <div class="settings-group">
        <div class="settings-group__title">外观</div>
        <div class="settings-row">
          <div class="settings-row__main">
            <div class="settings-row__label">暗色模式</div>
            <div class="settings-row__hint">使用深色界面，弱光环境下阅读更舒适</div>
          </div>
          <el-switch
            v-model="isDarkMode"
            :active-icon="MoonNight"
            :inactive-icon="Sunny"
            inline-prompt
            aria-label="暗色模式"
          />
        </div>
      </div>
    </el-dialog>

    <main class="index-main" :class="{ 'index-main--flush': isFullBleed }">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Expand,
  Fold,
  Iphone,
  MoonNight,
  QuestionFilled,
  Setting,
  Sunny,
  SwitchButton,
} from '@element-plus/icons-vue'
import AdminIcon from '@/views/admin/components/AdminIcon.vue'
import { useAppearance } from '@/composables/useAppearance'
import { removeToken } from '@/utils/auth'
import { isPathActive } from '@/utils/route'

const route = useRoute()
const router = useRouter()

/** 明暗态：写入 html[data-appearance] 与 html.dark，全站 Token 与 Element Plus 随之切换 */
const { isDark, setMode } = useAppearance()

const collapsed = ref(false)

const settingsVisible = ref(false)

/** 系统设置里的主题开关：读写都走 useAppearance，避免两处状态分叉 */
const isDarkMode = computed({
  get: () => isDark.value,
  set: (value) => setMode(value ? 'dark' : 'light'),
})

/** 全幅页面：工作流画布与模拟会议控制台都自己管理内边距/滚动 */
const FULL_BLEED_PATHS = new Set(['/index/workflow', '/index/meeting'])
const isFullBleed = computed(() => FULL_BLEED_PATHS.has(route.path))

const user = reactive({
  name: '张三',
  role: '免费用户',
  initial: '张',
})

/**
 * 菜单按「工作台 / 创作工具 / 效率工具 / 系统设置」逻辑分组，组间以留白与标题分隔，
 * 不再把入口纵向堆砌；icon 取 AdminIcon 的线性图标名，action 为非路由入口
 */
const menuGroups = [
  {
    label: '工作台',
    items: [{ path: '/index/home', label: '首页', icon: 'home' }],
  },
  {
    label: '创作工具',
    items: [
      { path: '/index/doc-template', label: '文档模板生成', icon: 'doc' },
      { path: '/index/homework-grading', label: '作业批改', icon: 'grading' },
      { path: '/index/ppt', label: 'PPT 演示', icon: 'slide' },
      { path: '/index/meeting', label: '模拟会议', icon: 'meeting' },
    ],
  },
  {
    label: '效率工具',
    items: [
      { path: '/index/knowledge-base', label: '知识库模块', icon: 'knowledge' },
      { path: '/index/workflow', label: '工作流编排', icon: 'workflow' },
    ],
  },
]

/**
 * 侧栏纵向刻度（px）：分组标题高度与留白、行高与行距都只在这里定义一次，
 * 由 sidebarVars 注入 CSS、由 menuOffset 计算滑块位移，避免两处各写一份数值
 */
const SB_LAYOUT = {
  itemH: 40,
  itemGap: 4,
  labelH: 28,
  labelGap: 8,
  groupGap: 16,
  collapsedGroupGap: 12,
}

/** 折叠态压缩分组标题，只留组间留白（尺寸仍由脚本注入，保证与滑块位移一致） */
const sidebarVars = computed(() => ({
  '--sb-item-h': `${SB_LAYOUT.itemH}px`,
  '--sb-item-gap': `${SB_LAYOUT.itemGap}px`,
  '--sb-label-h': `${collapsed.value ? 0 : SB_LAYOUT.labelH}px`,
  '--sb-label-gap': `${collapsed.value ? 0 : SB_LAYOUT.labelGap}px`,
  '--sb-group-gap': `${collapsed.value ? SB_LAYOUT.collapsedGroupGap : SB_LAYOUT.groupGap}px`,
}))

/** 选中项在轨道中的 Y 坐标：逐组累加标题高度与行距，不做运行时测量 */
const menuOffset = computed(() => {
  const groupGap = collapsed.value ? SB_LAYOUT.collapsedGroupGap : SB_LAYOUT.groupGap
  const labelBlock = collapsed.value ? 0 : SB_LAYOUT.labelH + SB_LAYOUT.labelGap
  const step = SB_LAYOUT.itemH + SB_LAYOUT.itemGap

  let y = 0
  for (const [index, group] of menuGroups.entries()) {
    if (index > 0) y += groupGap
    y += labelBlock

    const activeIdx = group.items.findIndex((item) => isActive(item))
    if (activeIdx >= 0) return y + activeIdx * step

    // 未命中的组整组跳过：标题已计入，这里补上该组所有行的占位
    y += group.items.length * step
  }

  return 0
})

/** 是否存在命中项：决定选中滑块显隐 */
const hasActive = computed(() => menuGroups.some((group) => group.items.some(isActive)))

/** 底部信息区：暂为前端 mock，接接口后替换为请求数据即可 */
const notifications = reactive({ unread: 3 })

const storage = reactive({ used: 2.4, total: 10, unit: 'GB' })

/** 积分卡片数据：同样为 mock，接接口后替换为真实余额 */
const credits = reactive({ balance: 1280 })

const creditsText = computed(() => credits.balance.toLocaleString('zh-CN'))

const creditsTip = computed(() => `剩余积分 ${creditsText.value} · 点击升级`)

const storagePercent = computed(() =>
  Math.min(100, Math.max(0, Math.round((storage.used / storage.total) * 100))),
)

const storageText = computed(() => `${storage.used} / ${storage.total} ${storage.unit}`)

/** 环形进度半径：与模板 viewBox 中的 r 对应，周长供 stroke-dasharray / dashoffset 使用 */
const RING_RADIUS = 18
const ringLength = Number((2 * Math.PI * RING_RADIUS).toFixed(2))
const ringOffset = computed(() =>
  Number((ringLength * (1 - storagePercent.value / 100)).toFixed(2)),
)

const notificationTip = computed(() => `通知中心 · ${notifications.unread} 条未读`)

const storageTip = computed(
  () => `已用 ${storage.used} ${storage.unit} / 共 ${storage.total} ${storage.unit}`,
)

/** 账户菜单与侧栏「系统设置」组共用入口：系统设置与退出登录已接，其余为占位 */
function handleUserCommand(command) {
  if (command === 'settings') {
    settingsVisible.value = true
    return
  }

  if (command === 'logout') {
    removeToken()
    router.push('/login')
    return
  }

  ElMessage.info('功能开发中，敬请期待')
}

/** 升级入口：弹窗与支付通道待接，先给统一反馈 */
function handleUpgrade() {
  ElMessage.info('升级通道开发中，敬请期待')
}

function go(item) {
  router.push(item.path)
}

function isActive(item) {
  // 子路径（如 /index/doc-template/12?file=5）也要点亮父菜单
  return isPathActive(route.path, item.path)
}
</script>

<style scoped>
.index-layout {
  display: flex;
  height: 100vh;
  background: var(--tb-bg-page);
}

/* ==========================================================================
   侧边栏外壳
   纵向刻度（行高 / 分组标题 / 组间距）由脚本注入 --sb-* 变量，
   与滑块位移共用同一组数值，避免运行时测量 DOM
   ========================================================================== */
.sidebar {
  /* 悬停叠加色：取自文字色，亮色下压暗、暗色下提亮，两态均有可见对比 */
  --sb-hover: color-mix(in oklab, var(--tb-text-primary) 7%, transparent);

  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  width: 260px;
  padding: var(--tb-size-4) var(--tb-size-3);
  background: var(--tb-bg-raised);
  border-right: 1px solid var(--tb-border);
  overflow: hidden;
  transition: width var(--tb-dur-3) var(--tb-ease-out);
}

/* 顶部一层极淡的品牌渐变，为平铺底色补一点纵深（不拦截指针事件） */
.sidebar::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  z-index: 0;
  height: 200px;
  pointer-events: none;
  background: linear-gradient(180deg, color-mix(in oklab, var(--tb-primary) 6%, transparent), transparent);
}

.sidebar--collapsed {
  width: 76px;
}

.sidebar__header,
.credits,
.sidebar__menu,
.sidebar__insights,
.sidebar__footer {
  position: relative;
  z-index: 1;
}

/* ---------- 头部 ---------- */
.sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-2);
  margin-bottom: var(--tb-size-4);
}

/* 折叠态：头部只留居中的 Logo，展开按钮绝对定位叠在 Logo 上，不额外占纵向空间 */
.sidebar--collapsed .sidebar__header {
  position: relative;
  justify-content: center;
}

.sidebar__brand {
  display: flex;
  align-items: center;
  min-width: 0;
  text-decoration: none;
  color: var(--tb-text-primary);
  transition: opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar__brand:hover {
  opacity: 0.72;
}

.sidebar__brand:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  border-radius: var(--tb-radius-sm);
}

.sidebar__logo {
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

.sidebar__brand:hover .sidebar__logo {
  transform: scale(1.06);
}

.sidebar__name {
  margin-left: var(--tb-size-2);
  max-width: 160px;
  overflow: hidden;
  font-family: var(--tb-font-display);
  font-size: 1.25rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  white-space: nowrap;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    margin-left var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar--collapsed .sidebar__name {
  max-width: 0;
  margin-left: 0;
  opacity: 0;
}

.sidebar__collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: var(--tb-control-sm);
  height: var(--tb-control-sm);
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar__collapse:hover {
  color: var(--tb-text-primary);
  background: var(--sb-hover);
}

.sidebar__collapse:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 折叠态：展开按钮居中盖在 Logo 上，默认隐藏；悬停「头部」时与 Logo 交叉淡入。
   触发区必须落在共同父级 header——若绑在 brand 上，按钮盖住 brand 会立刻
   取消 brand:hover，造成显隐抖动。
   键盘侧用 :has(button:focus-visible)：不用 :focus-within，否则鼠标点击后
   按钮保持焦点，Logo 会被一直隐藏 */
.sidebar--collapsed .sidebar__collapse {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  opacity: 0;
  pointer-events: none;
}

.sidebar--collapsed .sidebar__header:hover .sidebar__brand,
.sidebar--collapsed .sidebar__header:has(.sidebar__collapse:focus-visible) .sidebar__brand {
  opacity: 0;
}

/* 折叠态 Logo 让位时不做放大，避免与淡出叠成两层动效 */
.sidebar--collapsed .sidebar__header:hover .sidebar__logo,
.sidebar--collapsed .sidebar__header:has(.sidebar__collapse:focus-visible) .sidebar__logo {
  transform: none;
}

.sidebar--collapsed .sidebar__header:hover .sidebar__collapse,
.sidebar--collapsed .sidebar__header:has(.sidebar__collapse:focus-visible) .sidebar__collapse {
  opacity: 1;
  pointer-events: auto;
}

/* ---------- 积分卡片 ---------- */
.credits {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-4);
  padding: var(--tb-size-3);
  border: 1px solid color-mix(in oklab, var(--tb-primary) 22%, var(--tb-border));
  border-radius: var(--tb-radius-lg);
  background: linear-gradient(
    135deg,
    color-mix(in oklab, var(--tb-primary) 18%, var(--tb-bg-surface)),
    var(--tb-bg-surface) 58%
  );
  overflow: hidden;
  transition:
    gap var(--tb-dur-3) var(--tb-ease-out),
    padding var(--tb-dur-3) var(--tb-ease-out);
}

/* 右上角光斑：卡片自带的微型插画式装饰 */
.credits::after {
  content: "";
  position: absolute;
  top: -46px;
  right: -34px;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in oklab, var(--tb-primary) 42%, transparent), transparent 68%);
  pointer-events: none;
}

.credits__main {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-1);
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.credits__label {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  white-space: nowrap;
}

.credits__value {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.credits__cta {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  height: 28px;
  padding: 0 var(--tb-size-2);
  border: 1px solid color-mix(in oklab, var(--tb-primary) 34%, transparent);
  border-radius: var(--tb-radius-sm);
  background: color-mix(in oklab, var(--tb-bg-surface) 82%, transparent);
  color: var(--tb-primary-text);
  font-family: inherit;
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    transform var(--tb-dur-2) var(--tb-ease-spring);
}

.credits__cta:hover {
  background: var(--tb-bg-surface);
  border-color: var(--tb-primary);
  transform: translateY(-1px);
}

.credits__cta:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 折叠态：标签与按钮让位，只留余额数值居中；整张卡片即升级入口 */
.sidebar--collapsed .credits {
  justify-content: center;
  gap: 0;
  padding: var(--tb-size-2) var(--tb-size-1);
  border-radius: var(--tb-radius-md);
}

.sidebar--collapsed .credits__main {
  align-items: center;
  flex: 0 0 auto;
}

.sidebar--collapsed .credits__value {
  font-size: var(--tb-text-xs);
}

.sidebar--collapsed .credits__label,
.sidebar--collapsed .credits__cta {
  display: none;
}

/* ---------- 菜单 ---------- */
.sidebar__menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  /* 滚动条常态保持低调，悬停时再增强，避免抢走菜单本身的注意力 */
  scrollbar-color: var(--tb-border) transparent;
}

.sidebar__menu::-webkit-scrollbar {
  width: 6px;
}

.sidebar__menu::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar__menu::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-border);
}

.sidebar__menu:hover::-webkit-scrollbar-thumb {
  background: var(--tb-border-strong);
}

/* 折叠态：菜单仍可滚动，但隐藏滚动条，避免图标因滚动条占位而偏离侧栏中线 */
.sidebar--collapsed .sidebar__menu {
  scrollbar-width: none;
}

.sidebar--collapsed .sidebar__menu::-webkit-scrollbar {
  width: 0;
}

/* 内层承载滑块与菜单项：滑块宽度始终等于菜单项宽度（不受滚动条挤压错位） */
.sidebar__track {
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 逻辑分组：组内连续排列，组间由标题与 --sb-group-gap 拉开留白 */
.sidebar__group {
  display: flex;
  flex-direction: column;
}

/* 分组标题：小字号 + 宽字距；折叠态高度收为 0，只保留组间留白 */
.sidebar__group-label {
  display: flex;
  align-items: center;
  height: var(--sb-label-h, 28px);
  margin: 0 0 var(--sb-label-gap, 8px);
  padding: 0 var(--tb-size-3);
  overflow: hidden;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
  white-space: nowrap;
  transition:
    height var(--tb-dur-3) var(--tb-ease-out),
    margin var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

/* 只有组与组之间才留出上间距：首组对齐轨道顶部，与脚本的 index > 0 判断一一对应
   （不能用 :first-child——轨道首个子元素是滑块，会把首组让给后面的组） */
.sidebar__group + .sidebar__group .sidebar__group-label {
  margin-top: var(--sb-group-gap, 16px);
}

.sidebar__pill {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: var(--sb-item-h, 40px);
  border-radius: var(--tb-radius-md);
  background: var(--tb-primary-soft);
  pointer-events: none;
  transform: translate3d(0, var(--menu-y, 0), 0);
  transition:
    transform var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

/* 选中项左侧高亮条：随滑块一起位移，强化「当前所在位置」 */
.sidebar__pill::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 18px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary);
  transform: translateY(-50%);
}

.sidebar__pill.is-hidden {
  opacity: 0;
}

.menu-item {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  width: 100%;
  height: var(--sb-item-h, 40px);
  /* 行距作为外边距参与轨道排版，与脚本累加的偏移量一一对应 */
  margin-bottom: var(--sb-item-gap, 4px);
  padding: 0 var(--tb-size-3);
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-secondary);
  font-family: inherit;
  font-size: var(--tb-text-sm);
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.menu-item:hover {
  color: var(--tb-text-primary);
  background: var(--sb-hover);
}

.menu-item:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 选中态底色由滑块承担，避免悬停底色盖住滑块 */
.menu-item--active,
.menu-item--active:hover {
  background: transparent;
  color: var(--tb-primary-text);
  font-weight: 600;
}

/* 图标与 AdminSidebar 的 admin-menu-item__icon 同一套配色：常态三级文字色，选中随主色 */
.menu-item .admin-menu-item__icon {
  color: var(--tb-text-tertiary);
}

.menu-item--active .admin-menu-item__icon {
  color: var(--tb-primary-text);
}

.menu-item__label {
  margin-left: var(--tb-size-3);
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    margin-left var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease),
    transform var(--tb-dur-3) var(--tb-ease-out);
}

/* 折叠态：图标居中，文案收拢为 0 宽（仍保留在 DOM 中供读屏识别） */
.sidebar--collapsed .menu-item {
  justify-content: center;
  padding: 0;
}

/* 折叠态滑块已随图标居中，左侧高亮条反而偏位 */
.sidebar--collapsed .sidebar__pill::before {
  display: none;
}

.sidebar--collapsed .menu-item__label {
  max-width: 0;
  margin-left: 0;
  opacity: 0;
  transform: translateX(-6px);
}

/* ---------- 底部信息区：存储空间（环形进度） ---------- */
.sidebar__insights {
  margin-top: var(--tb-size-3);
  padding-top: var(--tb-size-3);
  border-top: 1px solid var(--tb-border);
}

/* 行内刻度与菜单项对齐：左内边距 12px、圆环 40px、间距 12px */
.storage {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  width: 100%;
  padding: var(--tb-size-2) var(--tb-size-3);
  border-radius: var(--tb-radius-md);
}

.storage__ring {
  position: relative;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
}

/* 环从 12 点方向起笔：旋转整个 svg；百分比文字在 svg 之外，不受影响 */
.storage__svg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.storage__ring-track {
  fill: none;
  stroke: var(--tb-border);
  stroke-width: 3.5;
}

.storage__ring-bar {
  fill: none;
  stroke: url(#tb-storage-ring);
  stroke-width: 3.5;
  stroke-linecap: round;
  transition: stroke-dashoffset var(--tb-dur-4) var(--tb-ease-out);
}

/* 渐变两端取自语义变量，随明暗两态各自取值 */
.storage__stop-from {
  stop-color: var(--tb-primary);
}

.storage__stop-to {
  stop-color: var(--tb-primary-hover);
}

.storage__percent {
  color: var(--tb-primary-text);
  font-size: var(--tb-text-2xs);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.storage__body {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-1);
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.storage__label {
  overflow: hidden;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-xs);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.storage__value {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 折叠态：只留圆环并居中（文案仍留在 DOM 供读屏） */
.sidebar--collapsed .storage {
  justify-content: center;
  gap: 0;
  padding: 0;
}

.sidebar--collapsed .storage__body {
  max-width: 0;
  opacity: 0;
}

/* ---------- 页脚 ---------- */
.sidebar__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-3);
  padding-top: var(--tb-size-3);
  border-top: 1px solid var(--tb-border);
}

.sidebar--collapsed .sidebar__footer {
  flex-direction: column;
  justify-content: center;
  /* 竖向排列时给未读角标留出外溢空间 */
  gap: var(--tb-size-3);
}

/* 折叠态：账户入口落到页脚最底部（通知入口在上），与菜单项同样居中 */
.sidebar--collapsed .sidebar__user-drop {
  order: 1;
}

.sidebar--collapsed .sidebar__user {
  justify-content: center;
  margin: 0;
  padding: 0;
}

/* 账户入口：整体可点，点击弹出账户菜单 */
.sidebar__user {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  margin: 0 calc(var(--tb-size-1) * -1);
  padding: var(--tb-size-1);
  border: none;
  border-radius: var(--tb-radius-md);
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar__user:hover {
  background: var(--sb-hover);
}

.sidebar__user:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.sidebar__avatar {
  flex: 0 0 auto;
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  box-shadow: 0 0 0 1px color-mix(in oklab, var(--tb-primary) 32%, transparent);
}

/* 与头像合成一行：昵称 + 身份，超宽整体省略 */
.sidebar__user-info {
  display: flex;
  align-items: center;
  gap: var(--tb-size-1);
  margin-left: var(--tb-size-2);
  min-width: 0;
  max-width: 160px;
  overflow: hidden;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    margin-left var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar--collapsed .sidebar__user-info {
  max-width: 0;
  margin-left: 0;
  opacity: 0;
}

.sidebar__username {
  font-size: var(--tb-text-sm);
  font-weight: 600;
  color: var(--tb-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__role {
  flex: 0 0 auto;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  white-space: nowrap;
}

/* 身份前用间隔点分隔，保持单行可读 */
.sidebar__role::before {
  content: "·";
  margin-right: var(--tb-size-1);
}

.sidebar__actions {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  flex: 0 0 auto;
}

/* 折叠态：76px 放不下并排按钮，操作区改竖向排列并与头像对齐居中 */
.sidebar--collapsed .sidebar__actions {
  flex-direction: column;
  align-items: center;
}

/* el-dropdown 会包一层根元素：占满页脚剩余宽度，内部按钮随之外扩 */
.sidebar__user-drop {
  display: flex;
  flex: 1;
  width: 100%;
  min-width: 0;
}

.sidebar__setting {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: var(--tb-size-6);
  height: var(--tb-size-6);
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--tb-radius-md);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease),
    border-color var(--tb-dur-2) var(--tb-motion-ease);
}

.sidebar__setting:hover {
  color: var(--tb-primary-text);
  background: var(--sb-hover);
  border-color: var(--tb-border);
}

.sidebar__setting:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 未读角标：贴按钮右上角，用侧栏底色描边与图标分离 */
.sidebar__badge {
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: var(--tb-size-4);
  height: var(--tb-size-4);
  padding: 0 var(--tb-size-1);
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  /* 外移一角，避免压住铃铛图标本体 */
  transform: translate(25%, -25%);
  box-shadow: 0 0 0 2px var(--tb-bg-raised);
}

/* ---------- 系统设置弹窗（内容在 slot 内，随组件 scope 生效） ---------- */
.settings-group__title {
  margin-bottom: var(--tb-size-3);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  font-weight: 600;
  letter-spacing: var(--tb-tracking-wide);
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-4);
  padding: var(--tb-size-3) var(--tb-size-4);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-raised);
}

.settings-row__main {
  min-width: 0;
}

.settings-row__label {
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  font-weight: 600;
}

.settings-row__hint {
  margin-top: var(--tb-size-1);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-snug);
}

.settings-row .el-switch {
  flex: 0 0 auto;
}

/* EP 开关内嵌图标恒为白色，关闭态落在浅灰轨道上会看不见，按语义变量补可读色 */
.settings-row :deep(.el-switch:not(.is-checked) .el-switch__core .el-icon) {
  color: var(--tb-text-secondary);
}

.index-main {
  flex: 1;
  padding: var(--tb-size-4);
  overflow-y: auto;
}

.index-main--flush {
  padding: 0;
  overflow: hidden;
}

@media (prefers-reduced-motion: reduce) {
  .sidebar,
  .sidebar__name,
  .sidebar__collapse,
  .credits,
  .credits__cta,
  .credits__main,
  .sidebar__group-label,
  .sidebar__pill,
  .menu-item,
  .menu-item__label,
  .storage__body,
  .storage__ring-bar,
  .sidebar__user,
  .sidebar__user-info,
  .sidebar__setting {
    transition: none;
  }
}
</style>
