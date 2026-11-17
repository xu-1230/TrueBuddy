# doc-ui · 样式设计

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-18（主题 Ink，2026-10-25 由 Harbor 换入）
> 适用范围：前端全部样式与主题、动效实现，以及展示页模块（`/guides`、`/docs`）全流程（§8）与系统首页 Zone C 的 tab 双行为模型（§9）
> 相关：[doc-conventions.md](./doc-conventions.md)（编码与组件规范）、[ui.html](./ui.html)（视觉样张）

---

## 1. 作用域原则

| 项 | 要求 |
| --- | --- |
| 默认 | 组件样式一律 `<style scoped>` |
| 全局样式 | 仅允许在 `App.vue` 或入口处声明 |
| 覆盖第三方 | 优先在 `scoped` 中使用 `:deep(...)`；确需全局覆盖时在文件内集中一段并注明原因 |
| 样式块数量 | 单个文件只保留一个 `<style>` 块（现有 4 个文件带两块：`DocTemplateView.vue`、`ConfigPanel.vue`、`workflow2/WorkflowView.vue`、`workflow2/components/NodeDataDrawer.vue`，均属待整改，见 §7） |
| 内联样式 | 禁止用于常规布局，仅限动态计算值（如元素坐标、颜色） |

---

## 2. 全局样式与现状

### 2.1 全局样式入口

| 位置 | 内容 |
| --- | --- |
| `src/main.js` | 依次引入 `element-plus/dist/index.css` → `element-plus/theme-chalk/dark/css-vars.css`（Element Plus 暗色变量包）→ `assets/fonts.css` → `assets/theme.css`（顺序不可调换：后两者依赖覆盖 EP 的字体与 `--el-*` 变量） |
| `src/assets/theme.css` | **设计 Token 层（唯一主题来源）**：`:root` 刻度 → `html[data-theme="ink"]` 原始色板与形态（状态色与阴影已 `light-dark()`）→ `:root` 语义变量（`light-dark()` 两态）→ `html, body` 页面基线 → `:root:root` 的 `--el-*` 语义映射（特异性压过 EP 暗色包的 `html.dark`）→ 旧内核兜底块；不含任何组件样式 |
| `src/App.vue` | **非 scoped** 全局样式：`*` 的 margin / padding 归零、`box-sizing: border-box`、`body` 字体、`table` 边框合并，以及 Element Plus 表格重置 |

> 项目**没有** `global.css`：全局规则由 `App.vue` 承载，主题取值由 `assets/theme.css` 承载，二者都不放组件样式，修改时需评估对全站的影响。

### 2.2 模块级样式文件

| 文件 | 作用 | 引入方 |
| --- | --- | --- |
| `views/ppt/styles/animation.css` | 精简版 animate.css（`.animate__animated` 与关键帧） | `views/ppt/index.vue` |
| `views/ppt/styles/prosemirror.css` | `.ProseMirror-static` 富文本静态样式 | `views/ppt/index.vue` |
| `views/meeting/styles/meeting.css` | 会议模块视觉层：`--mt-*` Token、自托管字体、共享原语、环境纹理、局部层级、reduced-motion（见 §10） | `views/meeting/MeetingConsoleView.vue`、`views/meeting/MeetingRoomView.vue` |

约定：模块级 CSS 文件只放在该模块的 `styles/` 目录，只由模块入口引入；`src/assets/` 只放**全局**资源（`fonts.css` 字体变量、`theme.css` 设计 Token），不放模块级样式。

### 2.3 待整改

| 位置 | 现状 | 规范要求 |
| --- | --- | --- |
| `views/doctemplate/DocTemplateView.vue` | 含一段**非 scoped** `<style>` | 收敛进 `scoped` 或使用 `:deep()` |
| `views/homework/components/ConfigPanel.vue` | 含一段非 scoped `<style>`，且共有两个样式块 | 合并为一个 `scoped` 块，全局覆盖部分改为 `:deep()` |

---

## 3. 颜色与主题

### 3.1 现状

| 项 | 现状 |
| --- | --- |
| 颜色写法 | 页面与全局组件统一写 `var(--tb-*)`，无品牌橙红 / Arco 蓝灰残留（例外见「固定色区块」与「PPT 幻灯片内容」两行）。**仍有 3 处 Tailwind 灰阶字面量待收敛**：`#d1d5db` → `views/doctemplate/DocTemplateView.vue`、`views/knowledgebase/components/{DocumentListView,SpaceCard}.vue` |
| 主色 | 全站统一为 `--tb-primary` = Ink 墨蓝 `#2b4a80`（暗色 `#3f68ab`）；`views/{doctemplate,homework}/components/SquircleButton.vue` 因内部用 JS `mixHex()` 派生侧壁色，改为传 Ink 定值 `#2b4a80` / `#24406f`（两份副本仍未合并，见 §7） |
| 主题与明暗切换 | **已全站落地**：`html[data-theme="ink"]` + `html[data-appearance="light\|dark"]` + `html.dark`（EP 暗色包开关）；三个切换入口：`components/NavbarItem.vue`、`views/IndexView.vue` 侧栏页脚、`views/LoginView.vue` 右上角；状态由 `composables/useAppearance.js` 维护（记忆 + 跟随系统 + 多标签同步），首帧防闪由 `index.html` 内联脚本完成 |
| Element Plus 变量 | `--el-*` 映射到**语义变量**（`--el-bg-color: var(--tb-bg-surface)` 等），因此 EP 表单 / 下拉 / 弹层 / 分页随明暗切换；EP 未覆盖的变量由官方暗色包（`theme-chalk/dark/css-vars.css`）铺底 |
| 固定色区块（刻意不随主题） | ① PPT 全屏放映台 `views/ppt/screen/ScreenView.vue`、`components/screen/ScreenSlideList.vue` 用 `--tb-neutral-950`；② 登录页品牌展示台 `components/LoginShowcase.vue` **跟随全局明暗**（不再容器级 `color-scheme: dark`）：文字、描边与 `--stage-*` 装饰全部由语义变量派生，亮色下是暖纸上的浅色画板、暗色下是深墨蓝画板，内部代表幻灯片的 `.preview` 仍反切 `color-scheme: light` 恒为纸白（见 §3.3 约定 3）。**底色画在父层 `views/LoginView.vue` 的 `.stage` 上**：亮色为 `--tb-paper-bg` + 9% 主色光晕 + 底部暖调下沉（不再是港湾夜蓝），暗色为 `#1f232d → #12141a`，原因见 §3.2 第 7 条；文档页 `views/guides/DocsView.vue` 的品牌展示台仍用 `light-dark(neutral-900, neutral-950)`；③ 文件类型色块与装饰标签条（`knowledgebase` 的 MD 紫 `#8b5cf6`、`#a78bfa`）保留字面量；④ `components/MarkdownViewer.vue` 的代码高亮色相不变，另建 `--md-hl-*` 双态映射；⑤ **会议的画面舞台** `views/meeting/components/ScreenStage.vue` 用 `--tb-neutral-950`（底）+ `--tb-neutral-400`（占位文字）——与放映台同理：共享出去的是别人的屏幕内容，底色必须固定为暗，否则白底 PPT 在亮色主题下刺眼、暗色主题下又整块发白。作业批改的卡堆舞台**不在**此列：它只靠语义 Token，随全局主题自适应；⑥ **会议模块视觉层**（`views/meeting/styles/meeting.css`）的固定暗场舞台与结束幕，保持共享内容在明暗主题下都落在深色画布上（见 §10） |
| PPT 幻灯片内容 | 保持数据驱动、不随 UI 主题变化：`mock/theme.js`（`themeColor #5b9bd5`）、`mock/slides.js`、`hooks/useSlideBackgroundStyle.js`、`utils/**`、`components/element/**`、`components/thumbnail/Thumbnail*`、`styles/prosemirror.css`；仅 UI 外壳（`index.vue` / 放映壳 / 缩略列表 / `ElementOutline.vue`）已 Token 化 |
| 页面级深浅区块 | 首页 `.section--light` / `.section--dark` 收敛为「交替区块 / 基底区块」两个角色（`--hv-alt` / `--tb-bg-page`）；`homework` 的页面级主题变量别名（`--primary: var(--tb-primary)` 等）已**删除**，页面直接引用 `var(--tb-*)`，原 `--el-color-primary*` 与 `--el-border-radius-base` 覆盖亦已移除；`doctemplate` 仍保留该兼容别名层（见 §7） |
| `light-dark()` | `assets/theme.css` 全量使用（语义变量、阴影、EP 色阶）；页面级固定深色区与 `MarkdownViewer` 的 `--md-hl-*` 亦使用；`views/TestView.vue` 的手写 `light-dark()` 已收敛为 Token 引用 |
| `oklch()` | 未使用（样张 `ui.html` 有实时换算演示，尚未进入代码） |
| `color-mix()` | 已在 `assets/theme.css`、`HomeView.vue`、各内页的软底 / 软描边 / 装饰光晕 / 半透明筹码中广泛使用；`SquircleButton` 仍用 JS 的 `mixHex` |
| 主题样张 | [ui.html](./ui.html)：**样张里有**七套主题（Ember / Ink / Graphite / Neon / Moss / Clay / Void）的色板、形态参数与组件外观 |
| 已实现主题 | **代码里只落地 Ink 一套**：`theme.css` 只有 `html[data-theme="ink"]` 一个色板块（`neon` 仅以注释示例形式存在于文件末尾），`index.html` 把 `data-theme` 写死为 `ink` 且运行时从不修改。**当前唯一的运行时切换是明暗**（`data-appearance` + `html.dark`）；其余六套属规划，落地方式见 §3.3 约定 2 |

### 3.2 规范要求

| # | 要求 |
| --- | --- |
| 1 | 同一模块内的颜色必须**集中定义**（CSS 变量或模块 `configs`），禁止在多个组件里重复硬编码同一色值 |
| 2 | 需要随主题变化的颜色使用 CSS 变量，组件内只引用变量名 |
| 3 | 新增深浅色自适应样式，优先使用 `light-dark()` 或语义化 CSS 变量 |
| 4 | 新增颜色优先使用 `oklch()`（可感知均匀，便于生成色阶），与既有十六进制混用时可读性优先 |
| 5 | 禁止在组件内直接修改全局 Element Plus 变量（`--el-*`），需要局部覆盖时在组件根类下限定作用域 |
| 6 | **`light-dark()` 只能出现在 `<color>` 的位置**，不能包住整条多段值。反例（曾在全站生效）：`--tb-shadow-2: light-dark(0 2px 8px rgba(…), 0 2px 8px rgba(…))` —— 传入的不是颜色，变量替换后 `box-shadow` 在 computed-value 阶段无效并**静默回落为 `none`**，34 个文件、72 处引用同时失去阴影且无任何报错。正确写法把偏移/模糊保持在外、只让 `light-dark()` 决定颜色：`--tb-shadow-2: 0 2px 8px light-dark(rgba(20,30,45,.1), rgba(0,0,0,.5))`；并在 §2.1 的旧内核兜底块里补一组不带 `light-dark()` 的亮色值。同类错误也出现在 `HomeHero.vue` / `ShowcaseCard.vue` 的 `filter: light-dark(brightness(…), brightness(…))`（`filter` 不接受颜色函数，整条声明被丢弃），已改为固定媒体层变量（见 §3.3 约定 6） |
| 7 | **`light-dark()` 在「使用处」解析，不在「定义处」**：把它写进自定义属性再传进子树时，取哪一支取决于**真正使用它的那一层**的 `color-scheme`。反例（本轮实测踩到）：`.stage { --stage-bottom: light-dark(brand-950, neutral-950) }` 配子层 `.showcase { color-scheme: dark; background: var(--stage-bottom) }`，亮色主题下仍恒取暗支。正确做法二选一：需要随全局明暗分流的底色，直接画在**未被换肤的祖先元素**上（本项目即 `LoginView.vue` 的 `.stage` 画底色、`LoginShowcase.vue` 只负责文字与叠加）；或跨边界传色时只写 `var()` 引用原始色阶——`var()` 在声明处就完成替换，传下去的是定值，不再受下游 `color-scheme` 影响 |

### 3.3 主题统一（已全站落地）

**现状**：Ink 主题（墨蓝 · 书卷文档，2026-10-25 由 Harbor 靛蓝切换）已在 `src/assets/theme.css` 落地，并且**全部页面与全局组件均已完成迁移**——首页（`views/guides/HomeView.vue`）、文档页（`guides/DocsView.vue`）、登录注册（`LoginView.vue` + `components/{LoginForm,RegisterForm,ForgetPasswordForm,LoginShowcase}.vue`）、内页外壳（`IndexView.vue`）、系统首页（`views/system/**`）、文档模板（`views/doctemplate/**`）、作业批改（`views/homework/**`）、知识库（`views/knowledgebase/**`）、PPT UI 外壳（`views/ppt/index.vue`、放映壳、缩略列表）、会议（`views/meeting/**`，舞台底色见 §3.1）、工作流（`views/workflow2/**`）、测试页（`TestView.vue`）与 `components/MarkdownViewer.vue`。

层级与命名（`assets/theme.css` 内自下而上）：

| 层 | 位置 | 内容 |
| --- | --- | --- |
| 刻度层 | `:root` | `--tb-size-*`、`--tb-text-*`、`--tb-leading-{tight,snug,normal,loose}`、`--tb-tracking-{tight,normal,wide,widest}`、`--tb-dur-*`、`--tb-ease-*`、`--tb-control-*`（与主题无关） |
| 原始色板 + 形态 | `html[data-theme="ink"]`, `:root` | `--tb-brand-50…950`（墨蓝阶）、`--tb-brand-on`、`--tb-neutral-0…950`（冷蓝灰，现仅"固定深色区块"引用）、`--tb-radius-*`（Ink 为 2/3/5/9/12/5）、`--tb-border-width`、`--tb-shadow-1..4`（**`light-dark()` 只包住颜色部分**，见 §3.2 第 6 条）与 `--tb-shadow-brand`、`--tb-font-display`（Ink 为 `var(--font-serif)`）、`--tb-weight-display`、`--tb-tracking-display`、`--tb-grain` / `--tb-noise`、`--tb-motion-ease` / `--tb-motion-dur`。**状态色 `--tb-success/warning/danger/info/accent` 实际也声明在这一层**（`theme.css` 里的注释就指向本文 §3.4），并已改为 `light-dark(深, 亮)` 成对取值，不是语义层 |
| 语义层 | `:root` | 组件唯一入口：`--tb-bg-page/surface/raised/sunken`、`--tb-border(-strong)`、`--tb-text-primary/secondary/tertiary/inverse`、`--tb-primary(-hover/-active/-text/-soft/-border)`、`--tb-on-primary`、状态色的 `-soft` 变体、`--tb-ring`、`--tb-overlay`、纸张类 `--tb-paper-{bg,line,size}` |
| 明暗 | `html[data-appearance="light\|dark"]` + `html.dark` | 前者只切 `color-scheme`（语义层 `light-dark()` 自动取另一套值），后者是 Element Plus 暗色包的开关，两者由 `useAppearance.js` 与首帧脚本同步维护 |
| 页面基线 | `html, body` | `background/color` 取自语义变量，保证未显式声明颜色的容器在暗色下也可读 |
| 第三方 | `:root:root` | `--el-*` 映射到**语义变量**（含 primary 色阶、状态色、文本、描边、填充、遮罩、圆角、阴影），特异性 0,2,0 稳定压过 EP 暗色包的 `html.dark` |

约定：

1. **组件只引用语义变量**，不直接写原始色阶、不写 `light-dark()`、不写 `html.dark` 判断。
2. 新增主题：追加 `html[data-theme="x"] { --tb-brand-*: …; --tb-radius-*: …; }` 覆盖同名变量即可，语义层无需改动（等特异性、后置生效）。
3. **模块级主题不得污染全局**：局部换肤在模块根容器上挂 `data-theme` / `data-appearance`；`views/ppt/` 的幻灯片配色由幻灯片数据驱动，需与 UI 主题分离。
   配套技法（已实测）：容器上写 `color-scheme: dark`（配 `data-appearance="dark"` 作语义标记）时，`light-dark()` 会**按容器**解析——页面亮色时容器内自动取深色一套，因此「局部固定深色」也无需硬编码任何颜色；不写这行则完全跟随全局主题。`views/homework/HomeworkGradingView.vue` 的 `.stage` 选后者，并在注释里留了这行开关。
4. 变量命名、色板取值与形态参数以 [ui.html](./ui.html) 为**唯一视觉样张**：七套主题的色阶、圆角 / 描边 / 阴影 / 动效参数与组件外观均在该文件内对齐；二者不一致时先改样张，再改代码。
5. 页面级「兼容别名」：`views/doctemplate/DocTemplateView.vue` 根类仍保留 `--primary / --primary-dark / --primary-soft / --accent*` 这组变量名（取值全部指向 Token）；`views/homework/HomeworkGradingView.vue` 的这层别名已删除，页面直接引用 `var(--tb-*)`，可作为彻底直连的参照实现（见 §7）。
6. 刻意保留的固定色：放映台与品牌展示台（固定深色）、文件类型色块与装饰标签条、首页 A2 入口卡与 Zone B 媒体卡的固定墨蓝媒体层（底色 / 染色 / 图片滤镜不随主题变亮）、`MarkdownViewer` 的代码高亮色相、会议模块的 `--mt-*` 视觉层（固定暗场舞台/结束幕，见 §10）。这些区域的语义不随明暗变化，取值方式见 §3.1 表格。
7. **不支持 `light-dark()` 的内核**：由 `assets/theme.css` 末尾的 `@supports not (color: light-dark(…))` 块降级为亮色取值（同时覆盖 `--el-*` 色阶）。

---

## 4. 尺寸与间距

- 尺寸、间距、圆角、阴影**优先参照 Element Plus 设计变量**（`--el-font-size-base`、`--el-border-radius-base` 等），避免自造数值体系。
- 常用数值（如 4 / 8 / 12 / 16 / 24 px）保持 4 的倍数；组件内不得出现孤立的 `13px`、`17px` 这类魔法值。
- 布局优先使用 Flex / Grid；禁止用固定 `height` 撑起内容区（编辑器类布局除外，需注明）。
- 卡片、按钮等出现第 3 次重复的视觉规格时，抽取为组件或共享变量。
- **响应式断点**：全站桌面优先，宽度查询只用 `max-width`（现存取值 720 / 768 / 820 / 900 / 980 / 1024 / 1080，其中 `768` 8 处、`1024` 5 处为主力）。新页面首选 `1024`（多栏 → 单栏）与 `768`（触摸微调）这一对，`views/LoginView.vue` 即按此执行；放映台类页面另有 2 处 `max-height: 780px` 的高度查询，属例外。触摸端的硬性要求是控件不低于 `--tb-control-lg`（46px）、输入框字号不低于 16px（否则 iOS 聚焦时自动放大页面）。
- **动效可关闭**：`@media (prefers-reduced-motion: reduce)` 已有 14 处实现（首页 / 登录展示台 / 会议页等），新增入场、视差、循环动画时必须同步补一条 reduce 分支，不能只做 GSAP 侧的开关。

---

## 5. Element Plus 使用与覆盖

| 项 | 约定 |
| --- | --- |
| 引入方式 | `main.js` **全量注册**（`app.use(ElementPlus)` + 引入其 CSS），组件内直接使用，无需 import 组件 |
| 图标 | 从 `@element-plus/icons-vue` 具名引入，禁止使用字体图标方案 |
| 覆盖样式 | 使用 `:deep(.el-xxx)` 并限定在组件根类下；禁止无选择器限定的全局 `.el-*` 覆盖 |
| 主题定制 | **已落地**：`assets/theme.css` 末尾用 `:root:root`（特异性 0,2,0）把 `--el-*` 全量映射到本项目的语义变量（色阶、状态色、文本、描边、填充、遮罩、圆角、阴影），因此 EP 组件随 `data-appearance` 一起换肤；不直接改 Element Plus 源码样式 |
| 按需引入 | 当前不做；若包体成为瓶颈再评估 `unplugin-vue-components` 方案 |

---

## 6. 动效与平滑滚动

### 6.1 统一入口

平滑滚动与滚动动效统一通过 `src/composables/useSmoothScroll.js` 提供（**Lenis + GSAP ScrollTrigger**）：

```js
import { useSmoothScroll } from "@/composables/useSmoothScroll";

const { refresh, scrollTo } = useSmoothScroll();
scrollTo(document.getElementById("hero"), { duration: 1.4 });
```

实现要点（现有实现）：

| 项 | 说明 |
| --- | --- |
| 返回 | `{ lenis, refresh, scrollTo }` |
| 初始化 | `onMounted` 创建 `Lenis`（`duration: 1.2`、`easing`、`smoothWheel: true`） |
| 联动 | `lenis.on("scroll", ScrollTrigger.update)`；由 `gsap.ticker` 驱动 `lenis.raf`。**刻意不调用 `gsap.ticker.lagSmoothing(0)`**：关掉掉帧补偿后，单帧超时（重绘 / GC）造成的延迟会持续累积、越滚越卡且无法自愈，因此保留 GSAP 默认补偿策略 |
| 锚点 | 用 **document 级事件委托**处理 `[data-scroll-to]`（`e.target.closest()` 命中后再 `scrollTo`），不再逐个元素挂匿名监听——那样在卸载时无从解绑 |
| 清理 | `onBeforeUnmount` 中 `lenis.destroy()` 并移除 ticker 回调 |

### 6.2 页面动效约定

以 `views/guides/HomeView.vue` 为参考实现，使用 **语义化 data 属性** 标注动效目标，避免在脚本中写脆弱的选择器：

| 属性 | 用途 |
| --- | --- |
| `data-section` | 章节容器，作为动效编排的作用域 |
| `data-reveal-word` | 逐词入场（标题拆词） |
| `data-reveal-line` | 逐行入场（段落） |
| `data-reveal-item` | 逐项入场（卡片、按钮、列表项） |
| `data-parallax` | 视差背景（随滚动位移） |
| `data-scroll-to` | 点击平滑滚动锚点 |

要求：

1. 动效只作用于展示型页面；**业务页面（表单、表格、画布）不加入场动效**。唯一例外是认证入口页 `views/LoginView.vue`：允许一次 `opacity + 10px` 位移、时长 `--tb-dur-4` 的淡入（不做滚动编排、不遮挡首屏可读性、`prefers-reduced-motion` 下整体关闭）；表单三态之间的切换属于**用户触发的过渡**，不算入场动效，故用 `--tb-dur-2/3` 的淡入淡出。**会议入口屏**（控制台空态/缺凭据/恢复中、学生入会大厅）与结束幕同样允许一次同量级的 reveal；会议中的界面不做页面入场，只做状态/操作触发与可关闭的环境动效（见 §10.4）。
2. 必须在组件卸载时清理：`ScrollTrigger.getAll().forEach((t) => t.kill())`；GSAP timeline 亦需 `kill()`。
3. 尊重无障碍：动效不得阻塞首屏内容可读性，滚动触发以「进入视口」为起点。
4. PPT 模块的动画体系独立：使用 `views/ppt/configs/animation.js` 定义的动画名与 `animate__` 前缀，时长通过 CSS 变量 `--animate-duration` 控制，与页面级 GSAP 动效**互不混用**。
5. **hover 反馈不得移动元素位置**：禁止 `translate` / `rotate` 之类的位移型 hover（卡片抬升、按钮上浮、箭头前探、纸片扇开都算），实测观感是"元素跟着鼠标跳"，很廉价。改用 `box-shadow` / `border-color` / `background` / `color` / `opacity` 表达可交互与层级。`scale()` 若只作用在图标级小元素上且不改变布局，可保留（`HomeHero.vue` 的 `.hero__thumb`）。已按此收敛的位置：`ShowcaseCard.vue`、`HomeHero.vue`、`LoginView.vue`、`LoginShowcase.vue`、`LoginForm.vue`。

### 6.3 性能约束

- 只对 `transform` / `opacity` 做动画；禁止对 `width` / `height` / `top` / `left` 做高频动画。
- 视差元素数量控制在个位数；`will-change` 仅在动画期间临时添加。
- 长时间动画（`scrub`）需评估低端设备的掉帧风险。

### 6.4 加载态（骨架屏）

- 列表 / 网格类页面首屏加载用骨架屏，不用整块 `el-loading` 转圈：骨架按真实卡分层（卡壳 / 视觉区 / 文本行），尺寸对齐真实卡，数据到达后网格高度不跳。
- 已有数据时的刷新（增删改、从子级返回）保留旧内容 + `v-loading` 轻量遮罩，不把已有卡换回骨架；骨架只在 `loading && !list.length` 时出现。
- 骨架色走 `--tb-*` Token，流光用伪元素 `transform: translateX`（§6.3）；`prefers-reduced-motion` 下关闭动画、保留静态色块。
- 挂载即请求的页面，`loading` 初值设为 `true`，避免先闪一帧空态；加载容器加 `aria-busy`，骨架层 `aria-hidden`，并提供 `role="status"` 的视觉隐藏文案。不使用 EP `el-skeleton`。
- 已落地：系统首页 Zone D（`AssetCardSkeleton`）、知识库空间 / 公有库（`SpaceCardSkeleton`）、知识库文档列表与分片区（`DocumentListView` 内联骨架）。

---

## 7. 待整改汇总

| # | 位置 | 现状 | 规范要求 |
| --- | --- | --- | --- |
| 1 | **6 个文件含非 scoped 样式块**：`App.vue`（全局重置，允许）、`components/MarkdownViewer.vue`（命名空间限定）、`views/doctemplate/DocTemplateView.vue`、`views/homework/components/ConfigPanel.vue`（teleport 到 body 的 EP 浮层）、`views/workflow2/WorkflowView.vue`、`views/workflow2/components/NodeDataDrawer.vue` | 收敛为 `scoped` + `:deep()`；确需非 scoped 的浮层样式统一抽到入口样式文件并注明原因 |
| 2 | 4 个文件同时存在两个 `<style>` 块：`DocTemplateView.vue`、`ConfigPanel.vue`、`WorkflowView.vue`、`NodeDataDrawer.vue` | 合并为一个（`scoped` + `:deep()`），避免同一组件两套作用域 |
| 3 | `views/ppt/mock/theme.js`、`mock/slides.js`、`components/element/**` | 幻灯片配色以 JS 常量散落（`themeColor #5b9bd5` 等），字符串形式的颜色无法被 CSS 变量收敛 | 属**幻灯片数据**、刻意不随 UI 主题变化；如需主题化，应在「新建幻灯片」时读取 `--tb-primary` 的解析值写入数据，而不是给渲染层注入变量 |
| 4 | `views/doctemplate/DocTemplateView.vue` 根类 | 保留 `--primary / --primary-dark / --primary-soft / --primary-soft-2 / --accent*` 兼容别名（取值已全部指向 Token） | 机械替换为 `var(--tb-*)` 后删除别名层，使「组件只引用语义变量」彻底成立（见 §3.3 约定 5）；`views/homework/HomeworkGradingView.vue` 已于本轮完成，可作参照 |
| 5 | `views/TestView.vue` | 手写 `light-dark()` 已收敛为 `var(--tb-bg-raised)` | 已完成；后续作为新页面的对照参考 |
| 6 | `views/{doctemplate,homework}/components/SquircleButton.vue` | 两份**逐字重复**（285 行）；色值已改为 Ink 定值（`#2b4a80` / `#24406f`），但内部 `mixHex()` 无法接收 `var()` | 抽到 `src/components/` 合并为一份，并改为读取 `getComputedStyle` 解析后的 Token 值，使其随主题换肤 |
| 7 | `views/LoginView.vue` | **已完成**（2026-10-09 登录页视觉重构）：模板里的行内 `style` 全部收敛为 `scoped` 类，唯一保留的动态值是展示台环境光坐标（`--gx/--gy`，由 `LoginShowcase.vue` 直接写在元素上，不进响应式） | §1「内联样式禁止用于常规布局，仅限动态计算值」 |
| 8 | `views/workflow2/node/**`、`views/homework/components/FolderUploader.vue`、`views/ppt/index.vue` 等 | 仍有 `border-radius: 0` 的方正硬值，与既有圆角语言并用 | 若要统一圆角语言，改为 `var(--tb-radius-xs)`；圆角与描边的边界按 §3.3 的形态参数执行（组件轮廓用 `--tb-border-width`，分隔线恒 1px） |
| 9 | 全站 **56 处** `z-index: <裸数字>`（含 EP 浮层避让值） | 样张 `ui.html` 里的 `--tb-z-rail`(30) / `--tb-z-toast`(90) 与 `--tb-control-radius` **在 `theme.css` 中尚未实现**，本文也没有层级表 | 先在 `theme.css` 落一层 `--tb-z-*` 刻度（含与 EP 弹层默认 2000+ 的避让关系），再逐步替换裸数字 |
| 10 | `assets/theme.css` 头部注释引用「doc-ui.md §3.3 / §3.4」，`ui.html` 使用 `--tb-z-*` 等尚未实现的变量 | 样张与代码的能力差集需显式标注，小节编号与注释保持一致 | 按 [doc-conventions.md](./doc-conventions.md) 的同步流程处理：样张先行、代码落地后回填本文 |

---

## 8. 展示页模块全流程（/guides 与 /docs）

### 8.1 模块定位

`views/guides/` 是项目的**展示型页面骨架**（对比控制台型骨架：亮场、固定侧栏 + 工具栏 + 内容区）：

| 页面 | 路由 | 文件 | 性质 | 后端依赖 |
| --- | --- | --- | --- | --- |
| 首页 | `/guides` | `views/guides/HomeView.vue` | 营销首页：顶部导航 + 8 个滚动章节 | 无 |
| 文档页 | `/docs` | `views/guides/DocsView.vue` | 左登录卡 + 右品牌展示台 | 仅登录表单 `POST /system/auth/login` |

特征：**纯前端**（除登录表单外全部为静态文案与装饰）；GSAP + ScrollTrigger 整页滚动编排 + Lenis 平滑滚动 + Canvas 2D 封面粒子形变；颜色 / 间距全部取 `assets/theme.css` 的 `--tb-*` 语义变量，组件内不写 `light-dark()`。`/guides` 与 `/docs` 是**平级顶层路由**（非子路由，以 `src/router/index.js` 为准）。

### 8.2 文件依赖

| 文件 | 职责 |
| --- | --- |
| `views/guides/{HomeView,DocsView}.vue`、`guides/components/HeroParticles.vue` | 页面本体与封面粒子（Canvas 2D 宿主） |
| `components/NavbarItem.vue` | 全局顶栏（fixed、越阈 `SCROLL_THRESHOLD = 24` 渐显、明暗切换、汉堡） |
| `components/navbar/{NavbarMenu,NavbarDrawer,menuData.js}` | 桌面 Mega 菜单 / 移动抽屉 / 菜单数据（**改文案与路由只改 menuData.js**） |
| `composables/{useSmoothScroll,useAppearance}.js` | Lenis + GSAP ticker 同步与锚点委托；明暗态（记忆 / 跟随系统 / 多标签同步，首帧防闪在 `index.html` 内联脚本） |
| `lib/{particleMorph,spring,springTo}.js` | 粒子形变引擎与弹簧补间 |
| `public/shapes/*.svg` | 粒子采样素材（书架线稿 / 学术剪影；只形变一次、不循环） |

### 8.3 首页滚动动效契约

每个 `[data-section]` 建一条与滚动进度绑定的时间线（`scrub: 1`；`MID = 1.6`：`0 → MID` 进场、`MID → 2×MID` 退场，退场幅度按层级递增）。`data-*` 属性契约（新增元素按此标注，脚本无需改选择器）：`data-reveal-word`（逐词标题）、`data-reveal-line`（段落行）、`data-reveal-item`（列表项）、`data-card`（卡片）、`data-zoom`（面板 / 圆核）、`data-slide-x`（流程步骤）、`data-fade-in`（自带 CSS 自转的元素）、`data-count`（数字随滚动增长）、`data-bar`（柱条）、`data-parallax`（视差，模板暂无对应元素）、`data-drift`（修饰反向退场）。通用页面动效约定见本文 §6.2。

**清理约定（重要）**：每个 ScrollTrigger 都经 `track()` 登记到页面级 `triggers`，`onBeforeUnmount` **只回收本页登记的实例**；禁止改回 `ScrollTrigger.getAll().forEach((t) => t.kill())` 的全局清空——会把当时挂在页面上的其它组件的 ScrollTrigger（如 `/docs`）一并杀掉。

### 8.4 修改指引（按目标定位）

| 想改什么 | 改哪里 | 注意 |
| --- | --- | --- |
| 顶栏菜单栏目 / 子项文案 / 跳转 | `components/navbar/menuData.js` | 组件无需改动；`MENU_TABS` 与 `MENUS` 的 id 必须对应 |
| 首页章节文案 / 增删章节 | `HomeView.vue` 模板 `<section>` + 脚本 `sections` 数组 | 两处必须同时改（`sections` 供 CTA `goTo(idx)` 锚点） |
| 动效节奏 | `HomeView.vue` 的 `setupAnimations()` | 退场统一挂在 `MID`；改动进场时长需同步评估 `MID` 与退场 |
| 粒子形状 / 采样密度 | `public/shapes/*.svg` + `HeroParticles.vue` | `spacing` 与 `pointRadius` 需配成一对（点径×2 ≈ 间隔） |
| 主题色 / 圆角 / 阴影 | 先改 [ui.html](./ui.html) 的 `[data-theme="ink"]` 块再同步 `assets/theme.css` | 见本文 §3.3 |
| 登录表单 | `components/LoginForm.vue` | 同时影响 `/docs` 与 `/login`；事件名需与父组件监听一致 |
| 锚点跳转 | `useSmoothScroll.js` / `HomeView.vue` 的 `goTo()` | 锚点元素 id 必须与 `sections[i].id` 一致 |

### 8.5 现状与待整改

1. `DocsView.vue` 模板引用未定义的 `tabs`，`login-tabs` 行渲染为空（控制台有 Vue 告警）；`activeTab` 无消费方。
2. `<LoginForm />` 未监听 `login-success`，`/docs` 账号登录成功后不跳转。
3. `LoginView.vue` 登录成功 `router.push('/')`，路由表无 `/` 路由，会落空视图（应改 `/index/home` 或补 `/` 重定向）。
4. 本页与 `views/LoginView.vue` 的登录能力重复：改登录逻辑应优先改 `components/LoginForm.vue`，两页同时生效。
5. 已落地（2026-11-18）：用户端路由守卫（`meta.requiresAuth`，见 doc-conventions.md §5.3）；Live2D 数字人已接入 `/index/ppt`，设置入口为顶栏最右侧齿轮（「数字人与声音设置」弹窗：形象图片卡片 + 音色列表 + 复刻，见 doc-lecture.md §5.7）。

---

## 9. 系统首页 Zone C 的 tab 双行为模型（2026-11-16 已实现；文档 tab 同日改为过滤型）

> 系统首页（`views/system/SystemHomeView.vue`，路由 `/index/home`）Zone C 的 tab 不是单纯的过滤器：六个 tab 分两类行为，配置在 `views/system/constants.js` 的 `HOME_TABS`。

### 9.1 双行为

| 类型 | tab | 点击行为 | 是否占用过滤态 |
| --- | --- | --- | --- |
| `filter` 过滤型 | 综合 / 文档 / 知识库 / 工作流 | 过滤 Zone D 资产卡；文档 tab 过滤已生成的产出文件卡 | 是（`activeTab` 更新） |
| `jump` 跳转型 | 批改 / 会议 | `router.push` 直达 `/index/homework-grading`、`/index/meeting` | **否**（返回首页仍停留在原过滤 tab） |

「文档」tab 自 2026-11-16 起改为过滤型（见 [specs/2026-11-16-首页文档tab列出生成文档.md](./specs/2026-11-16-首页文档tab列出生成文档.md)）：Zone D 的 doc 卡来自 `GET /doc-template/history` 内联的 `docs`，**一份文件一张卡**，点击直达 `/index/doc-template/:generationId?file=<doc_template_file.id>`；失败 / 0 份任务没有文件，不在首页出卡（诊断仍留在模板页历史抽屉）。

跳转型 tab（批改 / 会议）承担模块入口，且刻意不写进 `activeTab`——避免「点完跳转回来，过滤态被换页」。会话 tab 已下线：信息流来源只剩知识库空间、工作流、文档生成历史三类（`chat` kind 相关元数据一并清退，见 `utils/feed.js`）。

### 9.2 实现约定

- **tab 配置单一来源**：`HOME_TABS`（`{ key, label, type, to? }`）同时驱动 el-tabs 渲染与点击分流（`:before-leave="onBeforeTabLeave"` 对 `type === 'jump'` 拦截切换、改走路由并返回 `false`）；`FILTER_TAB_KEYS` 由配置派生，不重复手写。
- **过滤态记忆**：`sessionStorage` 键 `tb-home-active-tab`（`ACTIVE_TAB_STORAGE_KEY`，键名遵守 doc-conventions §6 的 `tb-` 前缀约定）；仅接受 filter 型 key，非法值回落 `all`。
- **信息流与资产卡**：`utils/feed.js` 的 `buildFeed` 归一三类来源；生成历史按 `docs` 展开为文件卡（key `doc-file-<fileId>`，数值条显示「本批 <docCount>」，药丸显示文件 `tag`）；知识库 / 工作流卡只展示当前登录用户自己的资产（后端列表按 `creator` 过滤，见 [adr/0006](./adr/0006-知识库与工作流归属与共享模型.md)）。
- **空态入口**：`SystemHomeView` 的 `EMPTY_ACTIONS` 按当前 tab 给出真实可落地的 CTA（文档 → 去生成文档 / 知识库 → 去创建知识库 / 工作流 → 去创建工作流）。
- **加载态**：Zone D 首次加载用 `AssetCardSkeleton.vue` 的分层骨架卡（卡壳 + 封面 + 标题 / 描述 / 数值条 / 时间），不使用 `el-skeleton` 与 `v-loading`。固定渲染 10 张，由容器查询按列数裁到两行（4 列 8 / 3 列 6 / 2 列 4 / 1 列 2）；流光是 `transform` 位移动画，`prefers-reduced-motion` 下关闭。`loading` 初值为 `true`，骨架结束立即切换，真卡沿用自身错峰入场；无障碍按 `aria-busy` + `aria-hidden` 骨架 + `role="status"` 读屏文案处理。
- **交互反馈**：hover 只用阴影 / 描边 / 底色 / 透明度表达（§6.2 要求 5）；资产卡与 tab 不做位移型 hover。

---

> 组件写法与分层、编码格式见 [doc-conventions.md](./doc-conventions.md) §3 / §4。

---

## 10. 会议视觉层（`views/meeting/styles/meeting.css`，2026-11-16）

> 完整口径、行为契约与验收项见 [specs/2026-11-16-模拟会议视觉升级.md](./specs/2026-11-16-模拟会议视觉升级.md)。

### 10.1 定位与边界

- **模块级视觉层**：只由 `views/meeting/MeetingConsoleView.vue` 与 `MeetingRoomView.vue` 引入，选择器挂在 `.meeting-studio` / `.mt-tokens` 下；**不修改** `assets/theme.css`、`ui.html` 或 `--tb-*` 全局 Token。
- **组件只引用变量**：`--mt-*` 在本文件集中声明（表层、线、字、品牌、信号青、琥珀、固定暗场、阴影、层级、节奏、版式），组件 SFC 里不写原始色值。
- **字体**：`public/fonts/` 自托管 Space Grotesk（Latin / Latin-Ext）与 JetBrains Mono（Latin / Latin-Ext），`@font-face` 只在本文件出现；中文回退现有 `--font-sans`。两者均为 SIL OFL 1.1，来源与许可见 `public/fonts/README.md`。
- **入口海报**：复用 `public/hero/hero-meeting.webp`，按 `HomeHero` 的「品牌染色 + 多层蒙层 + 颗粒」处理；只用于学生入会大厅（`JoinLobby`）的海报层，学生会议室的氛围层（无海报）与结束幕仍由 `StudioBackdrop` 承担，不铺在活动中的会议界面。老师控制台（`/index/meeting`）自 2026-11-17 起不再渲染 `StudioBackdrop`，页面只保留 `--mt-bg` 底色；其空态 / 缺凭据 / 恢复中入口屏也不再显示 eyebrow、大标题与说明段落，只留操作按钮、已知会议列表与恢复 spinner。

### 10.2 明暗与固定暗场

- 跟随全局 `useAppearance`：亮色为暖纸白（与 Ink 同源），暗色为近黑墨；`light-dark()` 只写在 `meeting.css` 的 `--mt-*` 定义里，不写进 SFC。
- 共享画面舞台 `ScreenStage` 设 `color-scheme: dark`，底为 `--tb-neutral-950`，使舞台内的 `--mt-*` 恒取暗色分支；亮色主题下舞台仍是暗场（沿用 §3.1 固定色约束）。结束幕同理。
- 弹层通过 `.mt-tokens` 携带同一组 Token；若未来把会议弹层改为 `append-to-body`，必须确保该 class 仍在弹层根上。
- 全幅控制台：`IndexView` 的 `FULL_BLEED_PATHS` 含 `/index/meeting`；≤1024px 单面板切换，≤820px 用 `:has(.meeting-console)` 隐藏全局侧栏，让 390px 宽度下控制台占满内容区（只影响会议控制台）。
- 入口屏滚动：整块入口屏不再滚动；内容超高时只让「本机还能回到这些会议」列表（`.console-entry__known`）自行滚动（复用 `mt-scroll` 细滚动条），顶栏与主操作保持可见。

### 10.3 Token 摘要

| 类别 | 变量 |
| --- | --- |
| 字体 | `--mt-font-display` / `--mt-font-mono` |
| 底 | `--mt-bg` / `--mt-bg-deep` / `--mt-bg-elevated` / `--mt-bg-rail` / `--mt-surface-glass` |
| 线与字 | `--mt-line` / `--mt-line-strong` / `--mt-text` / `--mt-text-2` / `--mt-text-3` |
| 品牌/信号 | `--mt-brand` / `--mt-brand-soft` / `--mt-signal` / `--mt-signal-strong` / `--mt-amber` / `--mt-live` / `--mt-danger` |
| 环境 | `--mt-grid-line` / `--mt-grid-size` / `--mt-aurora-1..3` / `--mt-grain-opacity` |
| 形态 | `--mt-radius-*` / `--mt-shadow-1..3` / `--mt-ring` |
| 节奏 | `--mt-dur-1..5` / `--mt-ease` / `--mt-ease-inout` |
| 版式 | `--mt-display-size` / `--mt-title-size` / `--mt-eyebrow-size` |
| 层级 | `--mt-z-ambient` / `--mt-z-stage` / `--mt-z-rail` / `--mt-z-header` / `--mt-z-dock` / `--mt-z-overlay`（EP 浮层从 2000 起，会议局部刻度不与其冲突） |

### 10.4 动效

- **入口屏例外**：控制台空态/缺凭据/恢复中、学生入会大厅、结束幕允许一次 `opacity + 10px` 的 stagger（`mt-rise-in`），不做滚动编排、不遮挡首屏；活动中的会议界面不做页面入场。
- **状态动效**：共享开始/停止、面板切换、新消息、连接变化、结束幕均为用户/状态触发；共享状态用 CSS 过渡表达 LIVE 角标与边角准星。
- **环境动效**：扫描线（`mt-scan`）、同心环呼吸（`mt-breathe`）、信号灯脉冲（`mt-pulse`）、数字人波形（`mt-equalize`）都是非阻塞装饰。
- **约束**：只动 `transform` / `opacity`；hover 不位移（边框/底色/阴影/光晕），图标级 `scale()` 可用；没有 GSAP/Lenis 时间线需要清理。
- **reduced-motion**：`meeting.css` 的统一覆盖把动画时长压到 0.01ms 并隐藏扫描线；入口内容保持可见（不使用 `animation-fill-mode: both` 长期锁在透明态）。

### 10.5 组件与验收

- 模块组件：`MeetingTopBar`（Studio 顶栏）、`ScreenStage`（program monitor）、`MeetingChatRail`（统一右轨：标题 / 在线头像行 + 浮层名单 / 消息流 / composer）、`JoinLobby`（全屏入会）、`StatusNotice`、`StudioBackdrop`、`StudioClock`、`ControlDock`；纯函数 `utils/meeting-time.js`、`utils/chat-groups.js` 有 Node 单测。
- **右轨视觉（2026-11-18）**：参考 `AiChatPanel` 的 `--tb-*` 实底圆角面板；自己右中性气泡 + 「我」，他人左头像 + 姓名 + 角色 pill + 浅气泡，数字人魔法图标 + 局部青色；多行自增高 composer + 圆形发送 + 滚到底按钮；连续同人消息 5 分钟窗口合并。`mt-rail` 玻璃外壳与 `AttendeeList` / `ChatPanel` 两段式已移除。
- **顶栏读数条（2026-11-17）**：`.studio-bar` 为极简读数条，高 56px，只用一条底线与分组竖线，不保留玻璃底、圆角、阴影与图标光点；ON AIR / STANDBY 是主状态，时钟 / 在线数 / 连接状态为竖线分隔的次要读数，顶栏内 `mt-chip` 只留文字与圆点；会议号收成一行小号 mono（`ID` + 号码）；槽位按钮经 `:slotted(.mt-btn)` 压到 32px。老师控制台与学生会议室共用同一套外观。
- 行为契约不变：路由、`?meetid=` / `?credential=1`、host/attendee token、全部 `/meeting/**` 接口、LiveKit track attach、DataChannel 聊天双写、在线名单只认房间事件（ADR-0002）、10s 轮询、`sendBeacon` 离会、讲课 BroadcastChannel 协议。
- 验收：`npm run lint` / `npm test`（358 条）/ `npm run build` 全过；桌面 1600×1000 与移动 390×844 的明暗入口/会议中/结束幕已用本机 Chrome 做过视觉验收。真实 LiveKit 共享画面与双端联调仍需在环境可用时按 [doc-lecture.md](./doc-lecture.md) §11.2 回归。

---

## 11. 文档模板 AI 聊天面板（`views/doctemplate/components/AiChatPanel.vue`，2026-11-17）

> 参考 Astryx AI Chat 模板（`astryx-stack x78zum5 xdt5ytf` 的垂直 stack）重做；2026-11-17 起接真实 SSE 编辑与撤回，并按文档持久化聊天记录。页面仍是「左 docx 预览 + 右聊天」，不翻转主次、不展示版本号/版本下拉。

### 11.1 结构与消息层级

- 面板无独立 header；首次进入先显示「正在加载聊天记录…」，空记录时显示静态提示「已加载《文档名》。可以直接描述修改要求，或先在左侧选择元素缩小范围。」（未登录时显示登录提示）；一旦有消息，顶部日期分隔取最早一条消息的时间（今天 / `M月D日`），避免历史记录被误标为「今天」。
- 消息区为 `display:flex; flex-direction:column` 的垂直堆栈；助手消息 = 26px 圆形品牌 soft 头像 + 平铺正文 / 项目列表，用户消息 = 右侧中性填充气泡。
- 每条消息带 `HH:mm`（`<time>`）；用户消息带「已送达」+ 对勾；助手消息若产生了新版本，在 chat-meta 显示**撤回图标**（不显示版本号）；已撤回/级联失效的消息灰显「已失效」。
- 助手内容来自 SSE `assistant` 事件的 `p` / `ul` 块；typing 行显示「头像 + 三点 + 阶段文案」（`status` 事件）。
- 聊天记录按文档（根文件 / 工作区）持久化：打开或切换文档时调用 `GET /doc-template/chat/history` 回放，刷新 / 重新打开后消息、选区 chips、时间、撤回图标与「已失效」状态都保留；切换预览版本不重载聊天。
- 加载中隐藏空态并禁用发送；记录映射为消息时服务端 id 加 `db-` 前缀、本地新消息用 `local-` 前缀，避免列表 key 冲突。撤回后目标消息及之后的会话消息标记失效，不参与后续 LLM 上下文。

### 11.2 形状与颜色

- 面板 / 用户气泡 / composer 统一 `--tb-radius-xl`（12px）；头像、发送按钮、滚到底按钮为 `50%`。
- 中性气泡 + 品牌点色：用户气泡 `color-mix(in oklab, var(--tb-text-primary) 7%, var(--tb-bg-surface))`；品牌色只用于发送按钮、助手头像与焦点环，不使用墨蓝渐变气泡。
- composer 为 `--tb-bg-raised` 圆角容器：选中的文档元素以 ghost-chip 风格 chips 显示在输入区上方（最多 5 个，可逐个 × 移除）；下方是自增长输入（最多 5 行）+ 圆形发送；底部小字「AI 编辑 / Enter 发送 · Shift + Enter 换行」。不设上下文 chip、提及 / 附件工具条与「提问」标识。
- 选区只是 LLM 上下文提示，不是硬性修改范围；没有选区时按全文处理。用户消息内回显本次选中的元素 chips。

### 11.3 滚动、动效与响应式

- **智能滚动**：用户距底部 ≤48px 时新消息自动跟随；上滑阅读时保持位置并显示右下角「回到最新消息」圆钮；自己发送消息始终回底。
- 撤回：chat-meta 图标 → 确认弹窗（会提示将同时丢弃之后 N 次修改）→ 目标版本及后代失效，当前版本回到上一版；成功后追加服务端落库的系统提示并把消息灰显，再重新拉取记录保证刷新前后一致。
- `≤1024px` 时查看模式改为上下堆叠：docx 预览 `height: min(60vh, 560px)`、聊天全宽 `height: min(480px, 60vh)`；预览内部滚动。
- 无障碍：`role="log"` + `aria-live="polite"`、按钮 / 输入区 `aria-label`、`<time>`、sr-only「AI 助手」标题；`prefers-reduced-motion` 下关闭 typing 三点与 `chat-in` 入场。

---

## 12. 作业批改成绩卡（`views/homework/components/ScoreCard.vue`，2026-11-17）

> 版式重构见 [specs/2026-11-17-作业批改成绩卡视觉重构.md](./specs/2026-11-17-作业批改成绩卡视觉重构.md)；评分区间与自定义导出列（右栏改为 `02 评分输出`）见 [specs/2026-11-17-作业批改评分区间与自定义导出列.md](./specs/2026-11-17-作业批改评分区间与自定义导出列.md)。卡堆几何与批改链路不变。

### 12.1 版式

- 成绩卡是一张**成绩单**：卡头（2px 得分色顶缘 + `GRADE REPORT` + 工作流 + 文件名 + 翻阅）→ 抬头（学生 / 得分 / 印章）→ 横贯全卡的 `min–max` 刻度 → 正文（`01 材料清单` ｜ `02 评分输出`）→ 页脚（`REQUIREMENT` 批改要求 + 品牌末标）。
- 得分是唯一主视觉：衬线超大数字 `clamp(4rem, 15.2cqw, 8.5rem)`、`/{score_max}` 小字、旋转双线印章落在同一基线上；未评分时分数为破折号、印章改虚线「待批改」、刻度隐藏，0 分仍按正常分数显示。
- 刻度取 `min` / `pass` / `max`：光点位置为 `(score−min)/(max−min)`；及格线贴住端点（60–100、pass=max）时把「及格」并到端点标签，避免重叠；阈值线由区间计算后内联 `left`。
- 材料清单的**公共目录只在标题下出现一次**（提交清单优先，无提交时用已读清单）；清单行 = 序号 + 文件名 + 扩展名角标，目录与公共目录不同才逐行回退；清单行之间用虚线分隔。
- `02 评分输出` 按自定义列定义顺序渲染：每项 = 小号序号 + 字段名 + 类型短码 + 值。`string/time/file` 用衬线段落，`integer/number` 右对齐，`boolean` 显示「是 / 否」，`list` 逐项列表，`object` 格式化 JSON；缺失显示「未产出」；没有定义时给出空态说明。
- 页脚上缘为虚线，暗示「撕下的批改要求」；页脚内容仍是本次批改的定性要求。

### 12.2 颜色与 Token

- 只使用 `--tb-*` 语义 Token 与 `color-mix()`；不新增全局 Token、不修改 `ui.html` / `theme.css`、不写原始色值。
- 单一 `--tone` 由 `scoreTone(score, range)` 注入（`excellent → success` / `good → primary` / `pass → warning` / `weak → danger` / `pending → tertiary`）：顶缘、分数、印章、刻度、分组序号、已读角标全部从它派生。
- 档位分界以及格线为基线向满分方向两等分：`< pass` 待改进；`≥ pass` 及格；`≥ pass + 50%×(max−pass)` 良好；`≥ pass + 75%×(max−pass)` 优秀。0–100/60 时仍是 80 / 90。
- 纸张颗粒用 `--tb-noise` 伪元素以 3.5% 透明度叠加，不引入图片资源。

### 12.3 自适应与动效

- 卡片自身 `container-type: size`，用 `@container sheet` 按卡片宽高适配，不依赖视口断点：
  - `max-width: 480px` 且 `min-height: 540px`：抬头与正文改单列，正文整体滚动；
  - `max-height: 620px` / `max-height: 500px`：逐级压缩抬头与分数、隐藏刻度文字与页脚；
  - `max-width: 460px`：整张纸滚动、卡头吸顶、隐藏文件名 / 工作流与页脚末标。
- 正文滚动区底部用 `mask-image` 做 18px 渐隐，提示还有内容可读。
- 入场只播一次：分区段 `translateY + opacity`、分数 900ms 计数、印章 `scale + rotate` 压印、刻度 `scaleX` 填充与光点出现；全部只动 `transform / opacity`。`prefers-reduced-motion` 下关闭动画，分数直接落终值。

### 12.4 无障碍与数据契约

- `<article>` 带整体读屏文案（学生 / 得分 / 第几份）；得分区 `role="group"` 的 `aria-label` 取**终值**，并带当前满分；刻度与 `/{max}` 是得分的视觉重复，对读屏隐藏；翻页按钮有 `aria-label` 与禁用态。
- 组件只读 `record` + `fields` + `range`：`files` / `readFiles` 继续以 `?? []` 兜底，`record.custom` 由 `customRows(fields, custom)` 归一；`record.score` 只接受数字，字符串 / 空值按未评分处理；`scoreTone` / `scoreRatio` / `scoreScaleOf` 都接受区间，缺省回 0–100/60。
- 页面数据中的 `status !== 1` 在抬头显示「批改未完成」，`errorMsg` 在无已读文件时作为空态说明；历史回看用 `taskMeta` 冻结的当次区间 / 字段，面板后续修改不影响已显示卡片。字段契约由 `tests/homework-card-fields.test.js` 锁定。

---

## 13. 作业批改「评分与导出设置」面板（`views/homework/components/ConfigPanel.vue`，2026-11-17）

### 13.1 结构

- 原「导出结果格式」一节改名为 **「评分与导出设置」**，去掉 Excel/CSV 切换（导出本轮仍为 TODO）。
- **评分设置**沿用 `views/workflow2/components/NodeDataDrawer.vue` 的 **输入行** 形式：分组头 `评分设置` + 数量胶囊 → 列头 `变量名 / 变量值` → 三行 `score_min / score_max / pass_score`；每行只有只读变量名（mono）与老师直接填的 `el-input-number`（默认 0/100/60，允许一位小数，不强制 `.0`；类型固定为 number，不再单独显示 `num` 短码）。顶部快捷预设 `0–100 / 0–150 / 0–20 / 60–100`，点一次填三个值，之后仍可改；分节标题右侧直接显示当前 `min–max 分 · 及格 X`。两段列表下方的解释性提示已去掉，只保留校验错误。
- **自定义导出列**沿用同一套 **输出行** 形式：分组头（名称 + 数量胶囊 + 右上角「＋」）→ 列头 `变量名 / 变量类型` → 虚线分隔的编辑行。每行 inline 输入变量名、类型，行尾是「展开描述 / 删除」两个图标键；类型下拉直接复用工作流变量类型树——`str / int / num / bool / time / obj`，`list` 读作 `[str]` 等、`file` 带元素类型；描述在行下展开（`nd-sub--stack`，单行输入）。类型字典 `views/workflow2/utils/variable-types.js` 由 NodeDataDrawer 与自定义导出列共用，不再各存一份。
- **变量名就是 key，没有 label**：每一列都是逐学生输出，由模型按描述产出；预置 `deductions` / `comment`（描述里说明中文含义），变量名可改、可删除、按添加顺序导出；新增列变量名留空，点行尾展开键填写描述。

### 13.2 校验与状态

- 区间：最低分 < 最高分，及格线 ∈ [区间]；前端即时在面板内提示并禁用「开始批改」，后端 `/homework/grade` 再校验（400）。
- 列：变量名（即 `key`，同时是模型输出键 / 成绩卡字段名 / 导出表头）1–40 字、字母/数字/下划线且不能与保留字段冲突、全表唯一；类型取工作流变量类型（`string/integer/number/boolean/time/object/list/file`；`list`/`file` 必须选元素类型），每列必须有描述。
- 批改进行中预设、输入、添加、删除不可操作；描述仍可展开查看（展开键不参与 `grading` 禁用，描述输入本身禁用）。

### 13.3 响应式与主题

- 面板仍是 344px 固定宽 + `config-panel__scroll` 内部滚动；评分输入行栅格 `minmax(0,1fr) minmax(0,2.4fr)`，自定义列输出行栅格 `minmax(0,1fr) 76px 22px 22px`（类型是 cascader）；输入 / 下拉沿用面板的方角、下沉底色和 focus 环；行 hover / focus-within 用 `--tb-bg-raised` 铺满。
- 只使用 `--tb-*` Token；行内类型短码与变量名用 mono / 次级色，暗色态自动跟随。
