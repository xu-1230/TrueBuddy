<script setup>
/**
 * 管理端侧边栏 —— 结构、刻度与交互完整复刻用户端 IndexView 的侧边栏：
 *   ① 头部品牌区（Logo + 名称，折叠态 Logo 居中、折叠按钮悬停浮现）
 *   ② 滑块选中态（--tb-primary-soft 底 + 左侧主色条，位移由脚本按刻度累加）
 *   ③ 折叠态文案收拢、tooltip 提示、滚动条隐藏
 * 与用户端的差异仅在内容：管理端多了 pinned 概览区（分隔线占位计入刻度），
 * 保留 RouterLink 导航语义与「管理控制台」副标题；页脚（版本号/收起按钮）已删，
 * 折叠入口只留在头部。两端组件不互相 import，刻度逻辑各存一份。
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Expand, Fold } from '@element-plus/icons-vue'
import AdminIcon from './AdminIcon.vue'

const props = defineProps({
  pinned: { type: Array, default: () => [] },
  groups: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle'])

const route = useRoute()

function isActive(path) {
  return route.path === path
}

/**
 * 侧栏纵向刻度（px）：分组标题高度与留白、行高与行距都只在这里定义一次，
 * 由 sidebarVars 注入 CSS、由 menuOffset 计算滑块位移，避免两处各写一份数值。
 * dividerGap / dividerBorder：pinned 分隔线的上下留白与边框宽度，
 * 与样式中的硬编码值一一对应
 */
const SB_LAYOUT = {
  itemH: 40,
  itemGap: 4,
  labelH: 28,
  labelGap: 8,
  groupGap: 16,
  collapsedGroupGap: 12,
  dividerGap: 12,
  dividerBorder: 1,
}

/** 折叠态压缩分组标题，只留组间留白（尺寸仍由脚本注入，保证与滑块位移一致） */
const sidebarVars = computed(() => ({
  '--sb-item-h': `${SB_LAYOUT.itemH}px`,
  '--sb-item-gap': `${SB_LAYOUT.itemGap}px`,
  '--sb-label-h': `${props.collapsed ? 0 : SB_LAYOUT.labelH}px`,
  '--sb-label-gap': `${props.collapsed ? 0 : SB_LAYOUT.labelGap}px`,
  '--sb-group-gap': `${props.collapsed ? SB_LAYOUT.collapsedGroupGap : SB_LAYOUT.groupGap}px`,
}))

/** 选中项在轨道中的 Y 坐标：先累加 pinned 区与分隔线，再逐组累加标题与行距 */
const menuOffset = computed(() => {
  const groupGap = props.collapsed ? SB_LAYOUT.collapsedGroupGap : SB_LAYOUT.groupGap
  const labelBlock = props.collapsed ? 0 : SB_LAYOUT.labelH + SB_LAYOUT.labelGap
  const step = SB_LAYOUT.itemH + SB_LAYOUT.itemGap
  // pinned 分隔线总占位：下留白 + 边框 + 上留白（两种状态下高度一致）
  const dividerBlock = SB_LAYOUT.dividerGap * 2 + SB_LAYOUT.dividerBorder

  let y = 0

  const pinnedIdx = props.pinned.findIndex((item) => isActive(item.path))
  if (pinnedIdx >= 0) return pinnedIdx * step
  if (props.pinned.length) y += props.pinned.length * step + dividerBlock

  for (const [index, group] of props.groups.entries()) {
    if (index > 0) y += groupGap
    y += labelBlock

    const activeIdx = group.items.findIndex((item) => isActive(item.path))
    if (activeIdx >= 0) return y + activeIdx * step

    // 未命中的组整组跳过：标题已计入，这里补上该组所有行的占位
    y += group.items.length * step
  }

  return 0
})

/** 是否存在命中项：决定选中滑块显隐 */
const hasActive = computed(
  () =>
    props.pinned.some((item) => isActive(item.path)) ||
    props.groups.some((group) => group.items.some((item) => isActive(item.path))),
)
</script>

<template>
  <aside class="admin-sidebar" :class="{ 'is-collapsed': collapsed }" :style="sidebarVars">
    <div class="admin-sidebar__header">
      <RouterLink class="admin-sidebar__brand" to="/admin/dashboard" aria-label="TrueBuddy 管理端首页">
        <svg class="admin-sidebar__logo" viewBox="0 0 120 120" aria-hidden="true">
          <rect x="24" y="37" width="16" height="62" fill="currentColor" />
          <line x1="24" y1="44" x2="40" y2="44" stroke="var(--tb-bg-raised)" stroke-width="3" />
          <rect x="46" y="22" width="16" height="74" fill="none" stroke="currentColor" stroke-width="6" />
          <rect x="68" y="47" width="16" height="52" fill="currentColor" />
          <line x1="67" y1="55" x2="85" y2="55" stroke="var(--tb-bg-raised)" stroke-width="3" />
          <rect x="90" y="28" width="16" height="68" fill="none" stroke="currentColor" stroke-width="6" />
        </svg>
        <span class="admin-sidebar__brand-text">
          <span class="admin-sidebar__name">TrueBuddy</span>
          <span class="admin-sidebar__sub">管理控制台</span>
        </span>
      </RouterLink>
      <el-tooltip
        content="展开侧边栏"
        placement="right"
        :offset="14"
        :show-after="160"
        :disabled="!collapsed"
      >
        <button
          type="button"
          class="admin-sidebar__collapse"
          :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
          :aria-expanded="!collapsed"
          @click="emit('toggle')"
        >
          <el-icon :size="30">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
        </button>
      </el-tooltip>
    </div>

    <nav class="admin-sidebar__nav" aria-label="管理端导航">
      <div class="admin-sidebar__track" :style="{ '--menu-y': menuOffset + 'px' }">
        <!-- 选中滑块：位移为脚本按「pinned + 分隔线 + 分组标题 + 固定行高」算出的像素值，不做运行时测量 -->
        <span class="admin-sidebar__pill" :class="{ 'is-hidden': !hasActive }" aria-hidden="true" />

        <!-- pinned 概览区：与功能模块之间用分隔线断开，占位计入滑块刻度 -->
        <div v-if="pinned.length" class="admin-sidebar__pinned">
          <el-tooltip
            v-for="item in pinned"
            :key="item.path"
            :content="item.title"
            placement="right"
            :offset="14"
            :show-after="160"
            :disabled="!collapsed"
          >
            <RouterLink
              class="admin-menu-item"
              :class="{ 'is-active': isActive(item.path) }"
              :to="item.path"
              :aria-current="isActive(item.path) ? 'page' : undefined"
            >
              <AdminIcon class="admin-menu-item__icon" :name="item.icon" :size="18" />
              <span class="admin-menu-item__label">{{ item.title }}</span>
            </RouterLink>
          </el-tooltip>
        </div>

        <div v-for="group in groups" :key="group.label" class="admin-sidebar__group">
          <p class="admin-sidebar__group-label">{{ group.label }}</p>

          <el-tooltip
            v-for="item in group.items"
            :key="item.path"
            :content="item.title"
            placement="right"
            :offset="14"
            :show-after="160"
            :disabled="!collapsed"
          >
            <RouterLink
              class="admin-menu-item"
              :class="{ 'is-active': isActive(item.path) }"
              :to="item.path"
              :aria-current="isActive(item.path) ? 'page' : undefined"
            >
              <AdminIcon class="admin-menu-item__icon" :name="item.icon" :size="18" />
              <span class="admin-menu-item__label">{{ item.title }}</span>
            </RouterLink>
          </el-tooltip>
        </div>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
/* ==========================================================================
   侧边栏外壳：与用户端 IndexView 的 .sidebar 同一套规则，仅类名前缀不同
   ========================================================================== */
.admin-sidebar {
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
.admin-sidebar::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  z-index: 0;
  height: 200px;
  pointer-events: none;
  background: linear-gradient(180deg, color-mix(in oklab, var(--tb-primary) 6%, transparent), transparent);
}

.admin-sidebar.is-collapsed {
  width: 76px;
}

.admin-sidebar__header,
.admin-sidebar__nav {
  position: relative;
  z-index: 1;
}

/* ---------- 头部 ---------- */
.admin-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-2);
  margin-bottom: var(--tb-size-4);
}

/* 折叠态：头部只留居中的 Logo，展开按钮绝对定位叠在 Logo 上，不额外占纵向空间 */
.admin-sidebar.is-collapsed .admin-sidebar__header {
  position: relative;
  justify-content: center;
}

.admin-sidebar__brand {
  display: flex;
  align-items: center;
  min-width: 0;
  color: var(--tb-text-primary);
  text-decoration: none;
  transition: opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-sidebar__brand:hover {
  opacity: 0.72;
}

.admin-sidebar__brand:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
  border-radius: var(--tb-radius-sm);
}

.admin-sidebar__logo {
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

.admin-sidebar__brand:hover .admin-sidebar__logo {
  transform: scale(1.06);
}

/* 品牌文字两行（名称 + 副标题）：折叠态整体收拢为 0 宽 */
.admin-sidebar__brand-text {
  display: flex;
  flex-direction: column;
  margin-left: var(--tb-size-2);
  min-width: 0;
  max-width: 160px;
  overflow: hidden;
  transition:
    max-width var(--tb-dur-3) var(--tb-ease-out),
    margin-left var(--tb-dur-3) var(--tb-ease-out),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-sidebar__name {
  font-family: var(--tb-font-display);
  font-size: 1.25rem;
  font-weight: var(--tb-weight-display);
  letter-spacing: var(--tb-tracking-display);
  white-space: nowrap;
}

.admin-sidebar__sub {
  margin-top: 1px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  white-space: nowrap;
}

.admin-sidebar.is-collapsed .admin-sidebar__brand-text {
  max-width: 0;
  margin-left: 0;
  opacity: 0;
}

.admin-sidebar__collapse {
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

.admin-sidebar__collapse:hover {
  color: var(--tb-text-primary);
  background: var(--sb-hover);
}

.admin-sidebar__collapse:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 折叠态：展开按钮居中盖在 Logo 上，默认隐藏；悬停「头部」时与 Logo 交叉淡入。
   触发区必须落在共同父级 header——若绑在 brand 上，按钮盖住 brand 会立刻
   取消 brand:hover，造成显隐抖动。
   键盘侧用 :has(button:focus-visible)：不用 :focus-within，否则鼠标点击后
   按钮保持焦点，Logo 会被一直隐藏 */
.admin-sidebar.is-collapsed .admin-sidebar__collapse {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  opacity: 0;
  pointer-events: none;
}

.admin-sidebar.is-collapsed .admin-sidebar__header:hover .admin-sidebar__brand,
.admin-sidebar.is-collapsed .admin-sidebar__header:has(.admin-sidebar__collapse:focus-visible) .admin-sidebar__brand {
  opacity: 0;
}

/* 折叠态 Logo 让位时不做放大，避免与淡出叠成两层动效 */
.admin-sidebar.is-collapsed .admin-sidebar__header:hover .admin-sidebar__logo,
.admin-sidebar.is-collapsed .admin-sidebar__header:has(.admin-sidebar__collapse:focus-visible) .admin-sidebar__logo {
  transform: none;
}

.admin-sidebar.is-collapsed .admin-sidebar__header:hover .admin-sidebar__collapse,
.admin-sidebar.is-collapsed .admin-sidebar__header:has(.admin-sidebar__collapse:focus-visible) .admin-sidebar__collapse {
  opacity: 1;
  pointer-events: auto;
}

/* ---------- 菜单 ---------- */
.admin-sidebar__nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  /* 滚动条常态保持低调，悬停时再增强，避免抢走菜单本身的注意力 */
  scrollbar-color: var(--tb-border) transparent;
}

.admin-sidebar__nav::-webkit-scrollbar {
  width: 6px;
}

.admin-sidebar__nav::-webkit-scrollbar-track {
  background: transparent;
}

.admin-sidebar__nav::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-border);
}

.admin-sidebar__nav:hover::-webkit-scrollbar-thumb {
  background: var(--tb-border-strong);
}

/* 折叠态：菜单仍可滚动，但隐藏滚动条，避免图标因滚动条占位而偏离侧栏中线 */
.admin-sidebar.is-collapsed .admin-sidebar__nav {
  scrollbar-width: none;
}

.admin-sidebar.is-collapsed .admin-sidebar__nav::-webkit-scrollbar {
  width: 0;
}

/* 内层承载滑块与菜单项：滑块宽度始终等于菜单项宽度（不受滚动条挤压错位） */
.admin-sidebar__track {
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 逻辑分组：组内连续排列，组间由标题与 --sb-group-gap 拉开留白 */
.admin-sidebar__group {
  display: flex;
  flex-direction: column;
}

/* 分组标题：小字号 + 宽字距；折叠态高度收为 0，只保留组间留白 */
.admin-sidebar__group-label {
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
.admin-sidebar__group + .admin-sidebar__group .admin-sidebar__group-label {
  margin-top: var(--sb-group-gap, 16px);
}

/* pinned 分隔线：下留白 12 + 边框 1 + 上留白 12，与脚本 dividerBlock 同源 */
.admin-sidebar__pinned {
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--tb-border);
}

.admin-sidebar__pill {
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
.admin-sidebar__pill::before {
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

.admin-sidebar__pill.is-hidden {
  opacity: 0;
}

.admin-menu-item {
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
  border-radius: var(--tb-radius-md);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  font-weight: 500;
  text-decoration: none;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.admin-menu-item:hover {
  color: var(--tb-text-primary);
  background: var(--sb-hover);
}

.admin-menu-item:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 选中态底色由滑块承担，避免悬停底色盖住滑块 */
.admin-menu-item.is-active,
.admin-menu-item.is-active:hover {
  background: transparent;
  color: var(--tb-primary-text);
  font-weight: 600;
}

/* 图标配色与用户端菜单一致：常态三级文字色，选中随主色 */
.admin-menu-item .admin-menu-item__icon {
  color: var(--tb-text-tertiary);
}

.admin-menu-item.is-active .admin-menu-item__icon {
  color: var(--tb-primary-text);
}

.admin-menu-item__label {
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
.admin-sidebar.is-collapsed .admin-menu-item {
  justify-content: center;
  padding: 0;
}

/* 折叠态滑块已随图标居中，左侧高亮条反而偏位 */
.admin-sidebar.is-collapsed .admin-sidebar__pill::before {
  display: none;
}

.admin-sidebar.is-collapsed .admin-menu-item__label {
  max-width: 0;
  margin-left: 0;
  opacity: 0;
  transform: translateX(-6px);
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar,
  .admin-sidebar__brand-text,
  .admin-sidebar__group-label,
  .admin-sidebar__pill,
  .admin-menu-item,
  .admin-menu-item__label {
    transition: none;
  }
}
</style>
