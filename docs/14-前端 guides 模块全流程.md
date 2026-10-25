# 14 - 前端 guides 模块全流程

> 项目：TrueBuddy
> 生效日期：2026-10-04 ｜ 规范入口：[README.md](./README.md)
> 适用范围：`frontend/src/views/guides/`（首页 `/guides`、文档页 `/docs`）及其依赖的导航、动效、主题与登录组件
> 相关：[06-路由规范.md](./06-路由规范.md)、[09-样式规范.md](./09-样式规范.md)、[04-组件规范.md](./04-组件规范.md)、[../frontend/AGENT - 前端技术规约.md](../frontend/AGENT%20-%20前端技术规约.md)

---

## 1. 模块定位

`views/guides/` 是项目的**展示型页面骨架**（对比 `views/*` 的「控制台型」骨架：亮场、固定侧栏 + 工具栏 + 内容区）。

| 页面 | 路由 | 文件 | 性质 | 后端依赖 |
| --- | --- | --- | --- | --- |
| 首页 | `/guides` | `views/guides/HomeView.vue` | 营销首页：顶部导航 + 8 个滚动章节 | 无 |
| 文档页 | `/docs` | `views/guides/DocsView.vue` | 左登录卡 + 右品牌展示台 | 仅登录表单 `POST /system/auth/login` |

特征：

- **纯前端**：除 `LoginForm.vue` 的账号登录外，页面内容全部为静态文案与装饰，不请求业务接口。
- **动效密集**：以 GSAP + ScrollTrigger 做整页滚动编排，以 Lenis 做平滑滚动，以 Canvas 2D 做封面粒子形变。
- **主题驱动**：全部颜色 / 间距取自 `assets/theme.css` 的 `--tb-*` 语义变量，明暗两态由 `light-dark()` 派生，组件内不写 `light-dark()`。

---

## 2. 路由与入口

### 2.1 路由表（`src/router/index.js`）

| path | name | 组件（懒加载） | 说明 |
| --- | --- | --- | --- |
| `/guides` | `guides` | `views/guides/HomeView.vue` | 首页，**顶层路由，无 `children`** |
| `/docs` | `docs` | `views/guides/DocsView.vue` | 文档页，与 `/guides` **同级**（非子路由） |

> **现状与规范不一致**：[06-路由规范.md](./06-路由规范.md) 曾把 `/docs` 记为 `/guides` 的子路由；以 `src/router/index.js` 为准，二者为平级顶层路由。

### 2.2 进入本模块的入口

| 入口 | 位置 | 目标 |
| --- | --- | --- |
| 顶栏品牌 Logo / 名称 | `components/NavbarItem.vue` | `/guides` |
| 顶栏「登录」按钮 | `components/NavbarItem.vue` | `/login` |
| 顶栏 Mega 菜单 | `components/navbar/menuData.js` | 各业务路由 + `/guides`、`/docs`、`/login` |
| 移动端抽屉 | `components/navbar/NavbarDrawer.vue` | 同上 |
| 页面内 CTA | `HomeView.vue` Hero / CTA 章节 | `/login`、`/register` + 页内锚点 |

---

## 3. 文件与依赖清单

```text
frontend/src/
├── views/guides/
│   ├── HomeView.vue                    # 首页：8 章节 + GSAP 滚动编排 + 顶部导航
│   ├── DocsView.vue                    # 文档页：登录卡 + 品牌评价轮播
│   └── components/
│       └── HeroParticles.vue           # 封面点阵粒子形变（Canvas 2D 宿主）
├── components/
│   ├── NavbarItem.vue                  # 全局顶栏（fixed、越阈渐显、明暗切换、汉堡）
│   ├── LoginForm.vue                   # 登录表单（账号 / 邮箱两种模式）
│   └── navbar/
│       ├── NavbarMenu.vue              # 桌面端 Mega 多列菜单（弹簧动效）
│       ├── NavbarDrawer.vue            # 移动端抽屉（Teleport 到 body）
│       └── menuData.js                 # 菜单数据（占位骨架，改文案/路由只改这里）
├── composables/
│   ├── useSmoothScroll.js              # Lenis + GSAP ticker + ScrollTrigger 同步
│   └── useAppearance.js                # 明暗态（记忆 / 跟随系统 / 多标签同步）
├── lib/
│   ├── particleMorph.js               # 粒子形变引擎（零依赖 Canvas 2D）
│   ├── springTo.js                    # 弹簧补间（Mega 菜单 / 指示器使用）
│   └── spring.js                      # 弹簧数值求解
├── assets/
│   ├── theme.css                      # 全局设计 Token（Ink 主题）
│   └── fonts.css                      # 字体变量（Inter / Playfair Display / JetBrains Mono）
└── utils/auth.js                      # Token（js-cookie）

frontend/public/
└── shapes/
    ├── karot-shelf.svg                # 书架线稿（形变终点）
    └── karot-silhouettes.svg          # 学术符号剪影（形变起点）
```

---

## 4. 首页 `/guides` 全流程

### 4.1 页面结构：8 个章节

数据源为 `HomeView.vue` 的 `sections` 数组（供页内 CTA 锚点使用），模板中每个章节为 `<section data-section data-index="N">`：

| # | 区块 id | 导航文案 | 主要内容 | 关键动效目标 |
| --- | --- | --- | --- | --- |
| 01 | `hero` | 首页 | 逐词大标题 + 双行副文案 + CTA + 右侧粒子画布 | `data-reveal-word/line/item`、`data-zoom` |
| 02 | `vision` | 核心理念 | 三张理念卡片 | `data-card` |
| 03 | `ai` | AI 助手 | 对话面板 + 三条特性 | `data-zoom`、`data-card` |
| 04 | `docs` | 文档模板 | 倾斜「教案」纸张 + 三条列表 | `data-zoom data-rotate`、`data-reveal-item` |
| 05 | `grading` | 作业批改 | 三个数字统计 + 四步流程 | `data-count`、`data-slide-x` |
| 06 | `knowledge` | 知识库 | 三层旋转圆环 + RAG 核心 + 三条列表 | `data-fade-in`、`data-zoom` |
| 07 | `insight` | 数据洞察 | 柱状图 + 标签组 | `data-bar`、`data-slide-x` |
| 08 | `start` | 开始使用 | 收尾 CTA（立即登录 / 注册账号） | `data-reveal-word/line/item` |

页面骨架要点：

- 全页**统一纸面网格背景**（`var(--tb-paper-bg)` + 两组 `linear-gradient` 网格线）铺在 `.page` 上，章节背景一律透明，不再交替换底色。
- `.page` 不设 `overflow`，横向裁切交给 `main`：避免 `.page` 成为滚动容器而使 `position: fixed` 顶栏失去基准。

### 4.2 滚动动效编排（GSAP + ScrollTrigger）

`setupAnimations()` 为**每个 `[data-section]`** 建一条与滚动进度绑定的时间线：

```js
const tl = gsap.timeline({
  scrollTrigger: { trigger: sec, start: 'top bottom', end: 'bottom top', scrub: 1 },
})
const MID = 1.6   // 章节居中于视口的时刻
const OUT = MID   // 退出段与进场段等长
```

- **进场**（时间位置 `0 → MID`）：各组元素按上述起手时序先后入场，形成层级配合。
- **退场**（时间位置 `MID → 2×MID`）：幅度按层级递增，滚动时各层被拉开产生纵深与速度差。

`data-*` 动效属性契约（新增元素时按此标注，脚本无需改动选择器）：

| 属性 | 选中元素 | 进场 | 退场 |
| --- | --- | --- | --- |
| `data-reveal-word` | 逐词标题（外套 `overflow: hidden` 的 `__word`） | `yPercent 120→0` + 淡入，`0.08` 错峰 | `yPercent → -110`，`opacity → 0.15` |
| `data-reveal-line` | 段落行 | `y 30→0` + 淡入，`0.15` 起 | `y → -70`，`opacity → 0.3` |
| `data-reveal-item` | 列表项 / 序号 / 标签 | `y 44→0` + 淡入，`0.25` 起 | `y → -34`（可用 `data-drift` 覆写），`opacity → 0.45` |
| `data-card` | 卡片 | `y 70→0` + 交替旋转，`0.3` 起 | `y → -22`，`opacity → 0.5` |
| `data-zoom` | 面板 / 圆核 | `scale .9→1` + 推入放正，`0.25 + i×0.12`；静态旋转用 `data-rotate` 声明 | `y → -34`、`scale → .95`、`opacity → 0.45` |
| `data-slide-x` | 流程步骤 / 标签 | `x 54→0` + 淡入，`0.4` 起 | `x → -18`、`y → -40`、`opacity → 0.4` |
| `data-fade-in` | 自带 CSS 自转的元素（知识库圆环） | 仅淡入（避免覆盖 `animation` 的 `transform`），`0.25` 起 | `opacity → 0.4` |
| `data-count` | 数字计数器 | 数值随滚动实时滚到目标值，`0.5 + i×0.1` | — |
| `data-bar` | 柱条 | `scaleY 0→1`（`transformOrigin: bottom center`），`0.45` 起 | `scaleY → 0.55`、`opacity → 0.6` |
| `data-fade-out` | 首屏滚动提示 | — | 向下滚动时淡出（`top top` → `+=25%`） |
| `data-parallax` | 装饰层视差 | `yPercent → data-speed`（默认 22），独立 ScrollTrigger | — |
| `data-drift` | 修饰 `data-reveal-item` | 取正值可让退出位移反向，与标题形成对峙 | — |

**清理约定（重要）**：`setupAnimations()` 内每个 ScrollTrigger 都经 `track()` 登记到页面级 `triggers`；`onBeforeUnmount` **只回收本页登记的实例**。

> 禁止改回 `ScrollTrigger.getAll().forEach((t) => t.kill())` 的全局清空写法：那会把当时挂在页面上的其它组件的 ScrollTrigger（如 `/docs`）一并杀掉。

**现状**：模板中当前没有 `data-parallax` 与 `data-fade-out` 元素，对应两段逻辑为预留（无副作用）。

### 4.3 平滑滚动（Lenis）

`useSmoothScroll()`（`composables/useSmoothScroll.js`）返回 `{ lenis, refresh, scrollTo }`：

- 挂载时创建 Lenis（`duration: 1.2`、`smoothWheel: true`），以 `gsap.ticker` 逐帧驱动 `lenis.raf`，并用 `lenis.on('scroll', ScrollTrigger.update)` 同步滚动位置。
- 刻意**不**调用 `gsap.ticker.lagSmoothing(0)`（关掉掉帧补偿会让延迟累积，越滚越卡）。
- `[data-scroll-to]` 锚点通过 `document` 上的**事件委托**统一接管，全站只占一个监听。
- 卸载时用**具名引用** `tickerFn` 精确解绑并 `lenis.destroy()`。

页内 CTA 由 `goTo(idx)` 调用 `scrollTo(targetTop, { duration: 1.2 })`，目标为 `sections[idx].id` 元素的 `offsetTop`。

### 4.4 封面粒子（`HeroParticles.vue` + `lib/particleMorph.js`）

- 形状（`SHAPES`）：`/shapes/karot-shelf.svg`（书架线稿）与 `/shapes/karot-silhouettes.svg`（学术符号剪影）；形变方向 `MORPH_FROM_TO = [剪影, 线稿]`，**只形变一次、不循环**。
- 采样参数与素材配套：`spacing` 窄屏 3 / 桌面 2.6、`pointRadius: 1.3`、`alphaCut: 64`、`fit: 1.4`、`maxParticles` 9000 / 16000、`speed: 0.05`、`startDelay: 700`。
- 颜色跟随主题：`themeColor()` 读取宿主元素解析后的 `color`（`var(--tb-primary-text)`），由 `watch(isDark)` 在切态时 `setColor()`。
- 省电与无障碍：`visibilitychange` 时 `pause()` / `start()`；系统「减少动效」时 `autoStart: false`，只静态展示起始形状。

### 4.5 顶部导航

| 组件 | 职责 | 关键约定 |
| --- | --- | --- |
| `NavbarItem.vue` | 顶栏外壳 | `position: fixed`（不占文档流、首屏背景可铺到顶端）；`SCROLL_THRESHOLD = 24` 越阈后 `is-scrolled`，由 CSS transition 完成收窄（`max-width 1700 → 1600`、`height 4rem → 3.5rem`）与玻璃底渐显 |
| `NavbarMenu.vue` | 桌面 Mega 菜单 | 数据来自 `menuData.js`；共享指示器胶囊 + 箭头旋转用 `springTo` 弹簧；`CLOSE_DELAY = 140ms` 容忍「一级项 → 面板」的空隙；Escape / 点击外部 / 失焦均收起；展开时 `emit('open', true)` 使顶栏 `z-index` 抬到 300 |
| `NavbarDrawer.vue` | 移动端抽屉 | `Teleport to="body"` 脱离顶栏层叠上下文；打开时锁 `html` 滚动并聚焦关闭按钮；面板标 `data-lenis-prevent` 让内部滚轮交还浏览器 |
| `menuData.js` | 菜单数据 | `MENU_TABS`（4 个一级项）+ `MENUS`（每项 3 列 × 3 条）；**文案与路由均为占位，改动菜单只改此文件** |

### 4.6 主题与明暗

- Token 分层（`assets/theme.css`）：① 刻度（尺寸 / 字号 / 时长 / 缓动）→ ② 原始色板 `html[data-theme="ink"]` → ③ 语义层 `:root`（组件唯一入口）→ ④ 明暗 `light-dark()` → ⑤ `--el-*` 映射（写在 `:root:root` 下，稳定压过 Element Plus 暗色包）。
- 明暗态由 `useAppearance.js` 维护：单一写入口 `apply()` 同时写 `html[data-appearance]` 与 `html.dark`；优先级为「localStorage(`tb-ui-mode`) → 首帧脚本已写属性 → 系统偏好」；跟随系统变化与多标签 `storage` 同步。
- 首帧防闪由 `index.html` 内联脚本完成，键名与 `useAppearance.js` 必须保持一致。
- 本模块的切换入口：`NavbarItem.vue`（顶栏按钮）。

### 4.7 响应式与无障碍

- `.hero__inner` 在 `≤980px` 改单栏、粒子画布居中收窄；`≤768px` 多列布局收为单列。
- Mega 菜单 `≤768px` 隐藏，由汉堡 + 抽屉接管；顶栏「登录」按钮 `≤768px` 隐藏。
- 全部常驻装饰动画与交互过渡在 `prefers-reduced-motion: reduce` 下静止 / 取消；滚动编排放手 GSAP 判断（见 [09-样式规范.md](./09-样式规范.md) §6.2）。

---

## 5. 文档页 `/docs` 全流程

`DocsView.vue` 为左右两栏：

| 区域 | 内容 | 说明 |
| --- | --- | --- |
| 左（`flex: 0 0 50%`） | 登录卡：标题 + `login-tabs` + `LoginForm` | 表单走 `components/LoginForm.vue` |
| 右（`flex: 1`） | 品牌区：Logo + slogan + 用户评价轮播 | 主色渐变底，`≤900px` 整块隐藏 |

用户评价轮播（`testimonials`，4 条）：

- `current` 索引 + `startAuto()` 每 `5000ms` 前进一条；点击圆点 `goTo(i)` 会重置计时器。
- 轨道用 `transform: translateX(-current * 100%)` 位移，`<Transition>` 无（纯 CSS transition）。
- 卸载时清理 `timer`。

**现状与待整改**：

1. 模板 `v-for="tab in tabs"` 依赖的 `tabs` 在 `<script setup>` 中**未定义**，`login-tabs` 一行渲染为空（控制台会有 Vue 渲染告警）；同时 `activeTab` 无消费方，`LoginForm` 恒显示。
2. `<LoginForm />` 未监听 `login-success`，账号登录成功后不会跳转。
3. `LoginForm` 通过相对路径 `'../../components/LoginForm.vue'` 引入，与「优先 `@/` 别名」的约定不一致。
4. 本页与 `views/LoginView.vue` 的登录能力重复：`LoginView.vue` 是主登录页（含注册 / 忘记密码切换），本页为展示型登录卡。修改登录逻辑应优先改 `components/LoginForm.vue`，两页同时生效。

> `views/LoginView.vue` 登录成功后 `router.push('/')`，而路由表**无 `/` 路由**，会落到空视图（见 §8）。

---

## 6. 横向依赖与基础设施

| 依赖 | 提供 | 本模块用法 |
| --- | --- | --- |
| `assets/theme.css` | `--tb-*` 语义 Token 与明暗两态 | 全部颜色 / 间距 / 圆角 / 阴影 / 动效时长 |
| `assets/fonts.css` | `--font-sans` / `--font-serif` / `--font-mono` | 标题走 `--tb-font-display`（Ink 下即衬线路由；h3 及以下仍无衬线） |
| `composables/useSmoothScroll.js` | Lenis 实例 + `refresh` / `scrollTo` | `HomeView.vue` 全部滚动动效 |
| `composables/useAppearance.js` | 明暗态 `mode` / `isDark` / `setMode` / `toggle` | 顶栏按钮、粒子颜色跟随 |
| `lib/particleMorph.js` | `new ParticleMorph(host, opts)`，`setShapes` / `setColor` / `start` / `pause` / `destroy` | `HeroParticles.vue` |
| `lib/springTo.js` | `springTo(state, target, opts)` + `killSprings()` | `NavbarMenu.vue` 指示器与列错峰 |
| `public/shapes/*.svg` | 粒子采样素材（透明底 + 橙色填充，仅取 alpha） | `HeroParticles.vue` |
| `utils/auth.js` | `getToken` / `setToken` / `removeToken`（js-cookie） | `LoginForm.vue` |

---

## 7. 修改指引（按目标定位）

| 想改什么 | 改哪里 | 注意 |
| --- | --- | --- |
| 顶栏菜单栏目 / 子项文案 / 跳转 | `components/navbar/menuData.js` | 组件无需改动；`MENU_TABS` 与 `MENUS` 的 id 必须对应；替换占位后删除 `TODO` 注释 |
| 首页某章节文案 | `views/guides/HomeView.vue` 模板对应 `<section>` | 保持 `data-*` 标注，否则该元素失去动效 |
| 增删首页章节 | `HomeView.vue`：模板 `<section>` + 脚本 `sections` 数组 | 两处必须同时改（`sections` 供 CTA `goTo(idx)` 锚点）；改动后核对 `.section__index` 的「N / 08」与总段数 |
| 调度动效节奏（快慢 / 幅度 / 时序） | `HomeView.vue` 的 `setupAnimations()`：`MID`、各 `tl.fromTo` 的 `duration` / `stagger` / 位置参数 | 退场统一挂在 `MID`，改动进场时长需同步评估 `MID` 与 `OUT` |
| 改粒子形状 / 采样密度 | `public/shapes/*.svg` + `HeroParticles.vue` 的 `SHAPES` / `MORPH_FROM_TO` / 采样参数 | `spacing` 与 `pointRadius` 需配成一对（点径×2 ≈ 间隔） |
| 改主题色 / 圆角 / 阴影 | `assets/theme.css` | 先改样张 [ui.html](./ui.html) 的 `[data-theme="ink"]` 块再同步，见 [09-样式规范.md](./09-样式规范.md) §3.3 |
| 改登录表单 | `components/LoginForm.vue` | 同时影响 `/docs` 与 `/login`；发事件名（`login-success` / `go-forgot`）需与父组件监听保持一致 |
| 改 `/docs` 品牌区文案 / 评价 | `views/guides/DocsView.vue` 的 `testimonials` | — |
| 改锚点跳转行为 | `useSmoothScroll.js` / `HomeView.vue` 的 `goTo()` | 锚点元素 id 必须与 `sections[i].id` 一致 |
| 改顶栏越阈行为 | `NavbarItem.vue` 的 `SCROLL_THRESHOLD` | 与样式中的媒体查询断点 `768` 无关，勿混用 |

---

## 8. 现状与规划 / 待整改

| 项 | 现状 | 规范要求 / 规划 |
| --- | --- | --- |
| `/docs` 路由归属 | `router/index.js` 中与 `/guides` **平级** | 已按实际代码修正 [06-路由规范.md](./06-路由规范.md) 的记载 |
| `DocsView.vue` 的 `tabs` | 模板引用未定义的 `tabs`，标签行不渲染 | 补齐 `tabs` 数据或删除该行与 `activeTab` |
| `DocsView.vue` 登录成功 | 未监听 `login-success`，无跳转 | 监听并跳转（如 `/index/home`） |
| `LoginView.vue` 登录成功跳转 | `router.push('/')`，但无 `/` 路由 | 改为 `/index/home`，或补 `/` 重定向到 `/guides` |
| 首页 `data-parallax` / `data-fade-out` | 逻辑已实现，模板无对应元素 | 需要时在模板补标注即可启用 |
| 路由守卫 | 无 `beforeEach`、无 `meta` | 规划：`meta.requiresAuth` + Token 校验，未登录跳 `/login`（见 [06-路由规范.md](./06-路由规范.md) §1.4） |
| 404 兜底 | 无 | 规划：新增 `/:pathMatch(.*)*` |
| 数字人（Live2D） | `public/live2d/` 资源就位，本模块未接入 | 规划：独立组件 + `composables` 封装 Pixi 生命周期 |

---

## 9. 验收清单

| # | 方式 | 操作 / 命令 | 通过判据 |
| --- | --- | --- | --- |
| 1 | 静态检查 | 对改动文件执行 IDE 诊断 | 无新增 error / warning |
| 2 | 构建 | `cd frontend && npm run build` | 构建成功 |
| 3 | 首页滚动 | 打开 `/guides`，从顶部滚到底 | 8 个章节依次进场 / 退场；数字与柱条随滚动增长；无卡顿累积 |
| 4 | 路由切换 | `/guides` → `/docs` → 返回 | 顶栏吸顶正常；`/docs` 无残留的 ScrollTrigger 报错 |
| 5 | 明暗切换 | 顶栏按钮切换 | 页面与粒子颜色同步变化；刷新后记忆保持、无首帧闪烁 |
| 6 | 移动端 | 视口 ≤768px | Mega 菜单隐藏、汉堡 + 抽屉可用；抽屉内滚动不被 Lenis 接管 |
| 7 | 减少动效 | 系统开启「减少动效」后刷新 | 粒子静态展示、弹簧退化为直接显隐、滚动编排不异常 |

---

> 本文件以 `frontend/src/views/guides/`、`frontend/src/components/navbar/` 的实际源码为事实来源；改动本模块后请同步更新本文对应章节与 [README.md](./README.md) 索引。
