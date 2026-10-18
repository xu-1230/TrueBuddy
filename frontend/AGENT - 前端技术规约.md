# AGENT - 前端技术规约

> 项目：TrueBuddy
> 生效日期：2026-09-10
> 上游文档：[总体技术规约](../总体技术规约.md)
> 本规约是前端子项目（`frontend/`）的详细规范，开发前需先阅读总体技术规约。

---

## 1. 技术栈

| 类别       | 技术                                      | 版本              |
| ---------- | ----------------------------------------- | ----------------- |
| 框架       | Vue 3 (Composition API, `<script setup>`) | ^3.5.40           |
| 构建       | Vite                                      | ^8.1.5            |
| 状态管理   | Pinia                                     | ^4.0.2            |
| 路由       | Vue Router                                | ^5.2.0            |
| UI         | Element Plus                              | ^2.14.5           |
| 图标       | @element-plus/icons-vue                   | ^2.3.2            |
| HTTP       | Axios                                     | ^1.19.0           |
| 存储       | js-cookie                                 | ^3.0.8            |
| 动效       | GSAP (+ ScrollTrigger)                    | ^3.15.0           |
| 平滑滚动   | Lenis                                     | ^1.3.26           |
| PPT 解析   | pptx-to-pptist                            | ^1.0.2            |
| 图形渲染   | pixi.js + pixi-live2d-display             | ^6.5.10 / ^0.4.0  |
| 流程图     | @vue-flow/core + background               | ^1.48 / ^1.3      |

工程化（devDependencies）：

| 类别     | 技术                                                             | 版本              |
| -------- | ---------------------------------------------------------------- | ----------------- |
| 代码检查 | oxlint + ESLint（`eslint-plugin-vue` / `eslint-plugin-oxlint`） | ~1.74.0 / ^10.7.0 |
| 构建插件 | @vitejs/plugin-vue + vite-plugin-vue-devtools                    | ^6.0.8 / ^8.1.5   |
| 脚本编排 | npm-run-all2                                                     | ^9.0.2            |

> 语言：**纯 JavaScript（无 TypeScript）**，降低复杂度。
> Node 版本要求：`^22.18.0 || >=24.12.0`（见 `package.json` 的 `engines`）。

> 以下为**规划 / 已引入未使用**：
> - 实时通信 `SSE (EventSource)`：规划中，暂未引入。
> - `pptxtojson` `^2.0.6`：依赖已声明，源码尚未引用。
> - `pixi.js` / `pixi-live2d-display`：依赖已声明、`public/live2d/` 模型资源已就位，源码尚未接入，数字人展示目前为占位（详见第 12 章）。

---

## 2. 目录结构

根级配置与资源：

```
frontend/
├── public/
│   ├── live2d/                     # Live2D 模型资源（Haru/Hiyori/Mao/Mark/Natori/Rice/Wanko + cubism core，规划中）
│   ├── favicon.ico
│   └── logo-exploration.html
├── vite.config.js                  # 别名 @ → ./src；/api 代理到 http://localhost:8086
├── eslint.config.js                # ESLint flat config
├── .oxlintrc.json                  # oxlint 配置
├── jsconfig.json                   # 编辑器路径别名提示（@/* → ./src/*）
├── index.html
└── package.json
```

`src/` 目录：

```
frontend/src/
├── api/
│   ├── index.js          # axios 实例 + 请求/响应拦截器
│   ├── test.js           # 登录 / 连通性 / 身份判断接口
│   └── admin/            # 管理端接口（auth / dashboard / user / role / menu / doctemplate /
│                         #   homework / knowledgebase / ppt / workflow / system），统一 /admin/** 前缀
├── assets/               # 静态资源（当前为空）
├── components/
│   ├── LoginForm.vue     # 登录表单（账号 / 邮箱）
│   ├── RegisterForm.vue  # 注册表单
│   ├── ForgetPasswordForm.vue  # 忘记密码
│   ├── MailForm.vue      # 邮箱验证码登录（兼容保留）
│   └── NavbarItem.vue    # 顶部导航项
├── composables/
│   └── useSmoothScroll.js  # Lenis + GSAP 平滑滚动（见第 9 章）
├── router/
│   ├── index.js          # 汇总两端路由 + 全局守卫（requiresAuth / requiresAdmin）
│   ├── user.routes.js    # 用户端路由
│   └── admin.routes.js   # 管理端路由（meta 同时驱动侧栏菜单）
├── stores/
│   └── counter.js        # Pinia store 示例
├── utils/
│   ├── auth.js           # Token 工具（基于 js-cookie）
│   └── access.js         # 角色 / 权限判断（纯函数，两端共用，配套 tests/admin-access.test.js）
├── views/
│   ├── guides/
│   │   ├── HomeView.vue  # 首页（顶部导航 + GSAP 滚动动效）
│   │   └── DocsView.vue  # 文档 / 登录展示页
│   ├── ppt/              # PPT 演示模块（仿 PPTist，见第 10 章）
│   │   ├── components/
│   │   │   ├── element/  # 元素渲染组件（文本/图片/形状/线条/图表/表格/公式/音视频）
│   │   │   ├── screen/   # 放映渲染（ScreenSlide / ScreenElement / ScreenSlideList）
│   │   │   └── thumbnail/ # 缩略图渲染（ThumbnailSlide / ThumbnailElement）
│   │   ├── configs/      # 画布 / 动画 / 快捷键 / 图片裁剪配置
│   │   ├── hooks/        # 放映、播放、全屏、尺寸、背景等组合式函数
│   │   ├── mock/         # 示例幻灯片与默认主题
│   │   ├── screen/       # ScreenView.vue（全屏放映视图）
│   │   ├── stores/       # useSlidesStore / useScreenStore（模块级 store）
│   │   ├── styles/       # animation.css / prosemirror.css
│   │   ├── utils/        # element.js / fullscreen.js
│   │   ├── vendor/       # 预留（当前为空）
│   │   └── index.vue     # 编辑器 / 预览主页
│   ├── workflow/         # 工作流编排模块（Coze 风格 Vue Flow 画布，见第 11 章）
│   │   ├── components/   # EditorToolbar（工具条）/ NodePanel（节点面板，仅后端支持的类型）
│   │   │                 # NodeCard（节点厚卡）/ LoopBodyNode（循环体）
│   │   │                 # NodeToolbar（悬浮工具条）/ NodeStatusBadge（运行态角标）
│   │   │                 # NodeConfigPanel（配置面板）/ NodeFieldList（字段渲染）
│   │   │                 # RunPanel（运行预览）/ CanvasControls（缩放 + 缩略图）
│   │   │                 # DropzoneBackground（点阵背景）/ CustomConnectionLine（连接预览线）
│   │   ├── composables/  # useDragAndDrop / useWorkflowRun / useWorkflowTheme / useWorkflowActions / useInlineLabel
│   │   ├── configs/      # cozeSchema.js（后端支持的节点类型与字段注册表）/ canvas.js（画布、连线、容器几何）
│   │   ├── utils/        # cozeWorkflow.js（schema_json 解析 / 序列化回写）
│   │   ├── mock/         # workflowDetail.json（内置的后端返回体，与接口同构）
│   │   └── WorkflowView.vue # 编辑器主页（工具条 + 节点面板 + 画布 + 右侧面板 + 画布控件）
│   ├── system/
│   │   ├── SystemHomeView.vue       # 系统首页（占位）
│   │   ├── DocTemplateView.vue      # 文档模板生成（UI 骨架）
│   │   ├── HomeworkGradingView.vue  # 作业批改（占位）
│   │   └── KnowledgeBaseView.vue    # 知识库模块（占位）
│   ├── error/            # ForbiddenView（403）/ NotFoundView（404），两端共用
│   ├── admin/            # 管理端（独立外壳 /admin，按功能图 6 个模块组织）
│   │   ├── AdminLayoutView.vue   # 侧栏（概览固定项 + 6 个模块）+ 顶栏 + RouterView
│   │   ├── login/        # AdminLoginView（管理端独立登录；开发环境可演示数据预览）
│   │   ├── dashboard/    # DashboardView（概览，pinned，不属于 6 个功能模块）
│   │   ├── user/         # 用户与权限管理：UserListView / RolePermissionView / MenuTreeView + components
│   │   ├── doctemplate/  # 文档模板管理：TemplateListView / GenerationRecordView / InspectionRecordView
│   │   ├── homework/     # 作业批改管理：GradingResourceView / GradingRecordView
│   │   ├── knowledgebase/ # 知识库管理：SpaceListView / CreationStatsView / StorageStatsView
│   │   ├── ppt/          # PPT 智慧课堂管理：AvatarConfig / SlideResource / MeetingRecord /
│   │   │                 #   ClassStats / InteractionStats
│   │   ├── workflow/     # 工作流管理：WorkflowList / NodeTemplate / RunRecord /
│   │   │                 #   ExecutionException / ConversationRecord
│   │   ├── system/       # setting / log / health（顶栏齿轮进入，menu:false）
│   │   ├── components/   # Sidebar / Header / Breadcrumb / AdminIcon / PageHeader /
│   │   │                 #   QueryBar / AdminTable / StatCard / StatusTag / BarChart
│   │   ├── hooks/        # useAdminSession（身份校验）/ usePagedTable（分页表格状态机）
│   │   ├── mock/         # data（账号 / 菜单 / 概览）+ 按模块拆分的演示数据
│   │   ├── utils/        # menu / menu-tree / format / request
│   │   ├── constants.js  # 权限码、状态字典、分页默认值
│   │   └── styles/admin.css  # 管理端页面骨架（.admin-* 前缀）
│   ├── IndexView.vue     # 侧边栏布局（Logo + 功能模块 + 用户信息）
│   ├── LoginView.vue     # 登录页（账号/邮箱/注册/忘记密码）
│   └── TestView.vue      # 接口连通性 / 登录测试页
├── App.vue
└── main.js
```

> 目录统一放在 `src/` 下；`@/` 别名已在 `vite.config.js` 中配置（`jsconfig.json` 同步提供编辑器提示）。

---

## 3. 开发规范

| 规范        | 要求                                                              |
| ----------- | ----------------------------------------------------------------- |
| 语法        | `<script setup>`，纯 JS（无 TypeScript）                          |
| 样式        | `<style scoped>`                                                  |
| 导入        | 优先使用 `@/` 别名；模块内部允许相对导入（现状中二者并存）        |
| 缩进        | 2 空格，LF 换行，UTF-8                                            |
| 比较        | 使用 `===`，禁止 `==`                                             |
| 异步        | async/await + try/catch                                           |
| Props/Emits | `defineProps` / `defineEmits`                                     |

补充约束：

- 组件命名：多单词 PascalCase（如 `FileUpload.vue`）。
- 页面文件：统一放在 `views/`，按路由层级命名。
- 禁止在模板中写复杂表达式，逻辑放 `<script setup>`。
- 事件命名使用 kebab-case，props 使用 camelCase。
- 模块内私有资源（store / hooks / composables / 工具）就近放置于模块目录，如 `views/ppt/` 自带 `stores/`、`hooks/`、`utils/`、`configs/`。

---

## 4. API 层规范

### 4.1 axios 实例

`src/api/index.js` 创建统一 axios 实例：

```js
import axios from "axios";
import { getToken } from "@/utils/auth";

const service = axios.create({
  baseURL: "/api", // 开发环境走 Vite 代理；后端 context-path 为 /api
  headers: { "Content-Type": "application/json" },
});
```

### 4.2 请求拦截器

- 自动从 Cookie 读取 Token（`getToken()`），写入 `Authorization: Bearer <token>`。

### 4.3 响应解包

后端所有接口统一返回 `CommonResult<T>`：

```json
{ "code": 0, "message": "", "data": <T> }
```

响应拦截器规则：

- `code === 0`：透明替换返回值为内层 `data`，业务代码直接拿到数据。
- `code !== 0`：抛出携带 `code` 的 `Error`，由业务层捕获处理。

### 4.4 接口定义

每个模块一个文件（如 `test.js`），统一导出函数。当前 `src/api/test.js` 已导出：

| 函数                                   | 方法 | 路径                      | 说明               |
| -------------------------------------- | ---- | ------------------------- | ------------------ |
| `login(username, password)`            | POST | `/system/auth/login`      | 登录，返回 Token 与 `roles` / `permissions` |
| `ping()`                               | GET  | `/test/ping`              | 连通性测试（需登录） |
| `ping2()`                              | GET  | `/test2/ping`             | 连通性测试（需登录） |

```js
import service from "./index";

export const login = (username, password) =>
  service({
    url: "/system/auth/login",
    method: "post",
    data: { username, password },
  });
```

---

## 5. 状态管理 (Pinia)

- 使用组合式写法（setup store）。
- 全局跨页共享的状态放入 `src/stores/`；模块独享状态就近放在模块目录。
- 局部状态优先使用组件内 `ref`，避免滥用全局 store。

```js
import { ref } from "vue";
import { defineStore } from "pinia";

export const useUserStore = defineStore("user", () => {
  const token = ref("");
  return { token };
});
```

当前 store 分布：

| 位置                      | Store                          | 说明                                                         |
| ------------------------- | ------------------------------ | ------------------------------------------------------------ |
| `src/stores/counter.js`   | `useCounterStore`              | 示例 store（count / doubleCount / increment）                |
| `views/ppt/stores/slides.js` | `useSlidesStore`（id `ppt-slides`） | 幻灯片数据：`theme` / `slides` / `slideIndex` / `viewportRatio`，含 `currentSlide` 等 getters 与 `setSlides`、`addSlide`、`updateElement` 等 actions |
| `views/ppt/stores/screen.js` | `useScreenStore`（id `ppt-screen`） | 放映状态：`screening` 与 `setScreening`                       |

> 业务 store（用户信息、文件列表、知识库列表等）为**规划**，当前尚未创建。

---

## 6. 路由规范

- 使用 `createWebHistory`。
- 页面组件懒加载：`component: () => import('../views/xxx.vue')`。
- 支持嵌套路由（如 `/index` 布局下的各功能模块、`/guides` 下的文档页）。

当前路由表（`src/router/index.js`）：

| 路径                        | 名称               | 组件                              | 说明                     |
| --------------------------- | ------------------ | --------------------------------- | ------------------------ |
| `/guides`                   | `guides`           | `views/guides/HomeView.vue`       | 首页（含顶部导航），声明子路由 `/docs` |
| `/docs`                     | `docs`             | `views/guides/DocsView.vue`       | 文档 / 登录展示页        |
| `/login`                    | `login`            | `views/LoginView.vue`             | 登录页                   |
| `/register`                 | `register`         | `views/LoginView.vue`             | 注册页（复用登录页）     |
| `/index`                    | `index`            | `views/IndexView.vue`             | 侧边栏布局，重定向到 home |
| `/index/home`               | `system-home`      | `views/system/SystemHomeView.vue` | 系统首页                 |
| `/index/doc-template`       | `doc-template`     | `views/system/DocTemplateView.vue`| 文档模板生成             |
| `/index/homework-grading`   | `homework-grading` | `views/system/HomeworkGradingView.vue` | 作业批改             |
| `/index/knowledge-base`     | `knowledge-base`   | `views/system/KnowledgeBaseView.vue`   | 知识库模块           |
| `/index/ppt`                | `ppt`              | `views/ppt/index.vue`             | PPT 演示                 |
| `/index/workflow`           | `workflow`         | `views/workflow/WorkflowView.vue` | 工作流编排（节点画布）   |
| `/test`                     | `test`             | `views/TestView.vue`              | 接口连通性 / 登录测试页  |
| `/admin/login`              | `admin-login`      | `views/admin/login/AdminLoginView.vue` | 管理端登录（开发环境可「以演示数据预览」） |
| `/admin`                    | `admin`            | `views/admin/AdminLayoutView.vue` | 管理端外壳，重定向到 `dashboard`，`meta.requiresAuth + requiresAdmin` |
| `/admin/dashboard`          | `admin-dashboard`  | `views/admin/dashboard/DashboardView.vue` | 概览（pinned，不属于功能模块） |
| `/admin/user/list`          | `admin-user-list`  | `views/admin/user/UserListView.vue` | 用户与权限管理：用户列表（新增/编辑/启停/重置密码在页内） |
| `/admin/user/role`          | `admin-user-role`  | `views/admin/user/RolePermissionView.vue` | 角色权限分配 |
| `/admin/user/menu`          | `admin-user-menu`  | `views/admin/user/MenuTreeView.vue` | 菜单结构维护（menu:false） |
| `/admin/doc-template/templates` | `admin-doc-templates` | `views/admin/doctemplate/TemplateListView.vue` | 文档模板管理：模板资源列表 |
| `/admin/doc-template/records` | `admin-doc-records` | `views/admin/doctemplate/GenerationRecordView.vue` | 生成记录查看 |
| `/admin/doc-template/inspections` | `admin-doc-inspections` | `views/admin/doctemplate/InspectionRecordView.vue` | 自检与修复问答记录 |
| `/admin/homework/resources` | `admin-homework-resources` | `views/admin/homework/GradingResourceView.vue` | 作业批改管理：批改资源列表 |
| `/admin/homework/records`   | `admin-homework-records` | `views/admin/homework/GradingRecordView.vue` | 批改记录查看 |
| `/admin/knowledge-base/spaces` | `admin-kb-spaces` | `views/admin/knowledgebase/SpaceListView.vue` | 知识库管理：用户空间列表 |
| `/admin/knowledge-base/creation-stats` | `admin-kb-creation-stats` | `views/admin/knowledgebase/CreationStatsView.vue` | 知识库创建统计 |
| `/admin/knowledge-base/storage-stats` | `admin-kb-storage-stats` | `views/admin/knowledgebase/StorageStatsView.vue` | 存储用量统计 |
| `/admin/ppt/avatar`         | `admin-ppt-avatar` | `views/admin/ppt/AvatarConfigView.vue` | PPT 智慧课堂管理：数字人形象配置 |
| `/admin/ppt/slides`         | `admin-ppt-slides` | `views/admin/ppt/SlideResourceView.vue` | 课件资源管理 |
| `/admin/ppt/meetings`       | `admin-ppt-meetings` | `views/admin/ppt/MeetingRecordView.vue` | 会议记录查询 |
| `/admin/ppt/class-stats`    | `admin-ppt-class-stats` | `views/admin/ppt/ClassStatsView.vue` | 课堂数据统计 |
| `/admin/ppt/interaction-stats` | `admin-ppt-interaction-stats` | `views/admin/ppt/InteractionStatsView.vue` | 课堂互动统计 |
| `/admin/workflow/list`      | `admin-workflow-list` | `views/admin/workflow/WorkflowListView.vue` | 工作流管理：工作流列表 |
| `/admin/workflow/node-templates` | `admin-workflow-node-templates` | `views/admin/workflow/NodeTemplateView.vue` | 节点模板维护 |
| `/admin/workflow/runs`      | `admin-workflow-runs` | `views/admin/workflow/RunRecordView.vue` | 运行记录查询 |
| `/admin/workflow/exceptions` | `admin-workflow-exceptions` | `views/admin/workflow/ExecutionExceptionView.vue` | 执行异常排查 |
| `/admin/workflow/conversations` | `admin-workflow-conversations` | `views/admin/workflow/ConversationRecordView.vue` | 会话记录查看 |
| `/admin/system/setting`     | `admin-system-setting` | `views/admin/system/SettingView.vue` | 系统配置（menu:false，顶栏齿轮） |
| `/admin/system/log`         | `admin-system-log` | `views/admin/system/OperationLogView.vue` | 操作日志（同上） |
| `/admin/system/health`      | `admin-system-health` | `views/admin/system/HealthView.vue` | 服务健康（同上） |
| `/403`                      | `forbidden`        | `views/error/ForbiddenView.vue`   | 非管理员访问管理端时跳转 |
| `/:pathMatch(.*)*`          | `not-found`        | `views/error/NotFoundView.vue`    | 兜底 404 |

> 路由守卫现状：**管理端已实现**（`meta.requiresAuth` 校验 Token，`meta.requiresAdmin` 校验 `admin:dashboard` 权限，未登录跳 `/admin/login`，无权限跳 `/403`）；
> 用户端仍未加 Token 校验（规划）。管理端侧栏菜单由 `admin.routes.js` 的 `meta` 生成，不另写 `menuData`。

---

## 7. 组件规范

- 可复用 UI 优先使用 Element Plus 组件，不重复造轮子。
- 业务可复用组件放入 `components/`，如 `LoginForm`、`RegisterForm` 等。
- 组件内部样式一律 `<style scoped>`；全局样式仅在 `App.vue` 或入口处声明。

---

## 8. 样式与主题

- 尺寸、间距优先参照 Element Plus 设计变量。
- 主题色当前以十六进制硬编码为主（常用主色 `#ff4500`，PPT 模块主题色 `#5b9bd5`）。
- 已支持 `light-dark()`（如 `views/TestView.vue`）；统一主题变量与切换为**规划**。

---

## 9. 动效与平滑滚动

技术选型：**Lenis**（平滑滚动）+ **GSAP / ScrollTrigger**（时间轴与滚动触发动画）。

统一入口为 `src/composables/useSmoothScroll.js`，返回 `{ lenis, refresh, scrollTo }`：

- 挂载时创建 `Lenis`（`duration: 1.2`、`smoothWheel: true`），通过 `gsap.ticker` 驱动 `lenis.raf`，并以 `lenis.on('scroll', ScrollTrigger.update)` 同步滚动位置，`gsap.ticker.lagSmoothing(0)` 关闭抖动平滑。
- 支持 `[data-scroll-to]` 锚点的平滑跳转（`lenis.scrollTo(node)`）。
- 卸载时 `lenis.destroy()`；`refresh()` 用于布局变化后重算 `ScrollTrigger`。

```js
import { useSmoothScroll } from '@/composables/useSmoothScroll'

const { refresh, scrollTo } = useSmoothScroll()
scrollTo(document.getElementById('hero'), { duration: 1.4 })
```

页面级动效约定（参考 `views/guides/HomeView.vue`）：

- 使用 GSAP `timeline` + `scrollTrigger` 驱动章节入场：`[data-reveal-word]`（逐词）、`[data-reveal-line]`（逐行）、`[data-reveal-item]`（逐项）。
- 使用 `[data-parallax]` 做视差背景（`scrub`）。
- 组件卸载时必须清理：`ScrollTrigger.getAll().forEach((t) => t.kill())`。

---

## 10. PPT 演示模块

位置：`src/views/ppt/`，路由 `/index/ppt`。定位为基于 PPTist 数据结构的**导入 + 预览 + 放映**模块（编辑态工具栏尚未实现）。

### 10.1 子模块职责

| 目录/文件                | 职责                                                                 |
| ------------------------ | -------------------------------------------------------------------- |
| `index.vue`              | 主页：导入数据、左侧缩略图、中部画布（缩放/翻页）、备注区、右侧数字人占位 |
| `components/element/`    | 元素渲染组件（见 10.3），含 `ElementOutline` 与元素级 hooks           |
| `components/screen/`     | 放映渲染：`ScreenSlideList`（翻页过渡）/ `ScreenSlide` / `ScreenElement` |
| `components/thumbnail/`  | 缩略图渲染：`ThumbnailSlide` / `ThumbnailElement`（不播放动画）       |
| `screen/ScreenView.vue`  | 全屏放映视图（进度、翻页、自动播放、全屏、结束放映）                  |
| `stores/`                | `useSlidesStore`（数据）/ `useScreenStore`（放映状态）                |
| `hooks/`                 | `useExecPlay` / `useScreening` / `useFullscreen` / `useSlideSize` / `useSlideBackgroundStyle` |
| `configs/`               | `canvas.js` / `animation.js` / `hotkey.js` / `imageClip.js`           |
| `mock/`                  | `slides.js`（示例数据）/ `theme.js`（默认主题）                       |
| `styles/`                | `animation.css`（精简 animate.css）/ `prosemirror.css`（静态富文本样式） |
| `utils/`                 | `element.js`（ID、颜色、路径工具）/ `fullscreen.js`（全屏兼容封装）   |

### 10.2 画布与元素约定

- 画布常量（`configs/canvas.js`）：`VIEWPORT_SIZE = 1000`、`VIEWPORT_RATIO = 0.5625`（16:9）；导入时按数据 `width/height` 覆盖比例。
- 元素类型（`type`）：`image` / `text` / `shape` / `line` / `chart` / `table` / `latex` / `video` / `audio`，由 `ScreenElement.vue`、`ThumbnailElement.vue` 的 `elementTypeMap` 映射到渲染组件。
- 图表为**自研纯 SVG 实现**（`ChartElement/Chart.vue`），未引入 echarts；公式为 SVG path 渲染，未引入 KaTeX。
- 文本元素以 `v-html` 渲染 `content` 并套用 `.ProseMirror-static` 样式，**不包含 ProseMirror 编辑器**。
- 动画基于 `animate__` 前缀（`ANIMATION_CLASS_PREFIX`）与 CSS 变量 `--animate-duration`；动画名定义于 `configs/animation.js`（入场 / 退场 / 强调）。
- 快捷键键码统一定义于 `configs/hotkey.js`。

### 10.3 元素渲染组件

| 元素      | 组件                                                         |
| --------- | ------------------------------------------------------------ |
| 文本      | `TextElement/BaseTextElement.vue`                            |
| 图片      | `ImageElement/BaseImageElement.vue`（裁剪 `imageClip`、滤镜、阴影、翻转） |
| 形状      | `ShapeElement/BaseShapeElement.vue`（SVG path、渐变、图案填充） |
| 线条      | `LineElement/BaseLineElement.vue` + `LinePointMarker.vue`      |
| 图表      | `ChartElement/Chart.vue` + `ScreenChartElement.vue`            |
| 表格      | `TableElement/BaseTableElement.vue` + `StaticTable.vue` + `utils.js` |
| 公式      | `LatexElement/BaseLatexElement.vue`                            |
| 视频      | `VideoElement/ScreenVideoElement.vue`                          |
| 音频      | `AudioElement/ScreenAudioElement.vue`                          |

### 10.4 数据导入

`index.vue` 支持导入 `.pptx` 与 PPTist JSON 两种格式：

- `.pptx`：`FileReader` 读取 `ArrayBuffer` → `convertPPTXToPPTist(buffer, { fixedViewport: true, viewportWidth: VIEWPORT_SIZE })`（来自 `pptx-to-pptist`）→ `normalizeSlideData` 归一化 → `importSlideData`。
- JSON：直接 `JSON.parse` 后导入；要求包含 `slides` 数组，可选 `theme` / `width` / `height` / `viewportRatio`。
- 归一化：统一 `turningMode = 'no'`；将线条元素绝对坐标的 `start/end` 换算为相对包围盒坐标；数值保留 2 位小数。
- 兼容处理：因解析库在浏览器环境依赖 Node `Buffer`，入口处补 `window.Buffer` 兜底。

### 10.5 放映

- 进入放映：`useScreening` 的 `enterScreening()` / `enterScreeningFromStart()`；`useScreenStore.screening` 控制 `ScreenView` 显隐。
- 放映行为：`useExecPlay` 负责元素动画执行、翻页（键盘 / 滚轮 / 触摸）、自动播放（间隔 2500ms）、按 `turningMode`（`no` / `fade` / `slideX` / `slideY`）做过渡。
- 尺寸与全屏：`useSlideSize` 按视口比例自适应，`useFullscreen` 处理全屏切换与 ESC 退出。

> **待开发**：`index.vue` 右侧「数字人展示页面（待开发）」占位；`vendor/` 目录为空；编辑态工具栏未实现。

---

## 11. 工作流编排模块

位置：`src/views/workflow/`，路由 `/index/workflow`。定位为**作业批改工作流的可视化编排**（对标 Coze 工作流编辑器的节点画布）：画布数据来自后端 `schema_json`、节点是信息密度较高的「厚卡」、具备完整运行态（未执行 / 运行中 / 成功 / 失败）与节点悬浮工具条，并保留拖拽建节点、按类型配置、运行预览、缩放 / 缩略图与浅色 / 深色双主题。

### 11.1 目录结构

| 目录/文件                               | 职责                                                                                  |
| --------------------------------------- | ------------------------------------------------------------------------------------- |
| `WorkflowView.vue`                      | 编辑器主页：组装工具条 / 节点面板 / 画布 / 右侧面板 / 画布控件，持有节点、连线、选中态、主题态与运行编排 |
| `components/EditorToolbar.vue`          | 顶部工具条：返回、名称就地编辑、保存状态、节点面板开关、主题切换、保存 / 发布 / 试运行（运行中禁用并显示进度） |
| `components/NodePanel.vue`              | 左侧节点面板：搜索框 + Coze 五组（输入输出 / 基础节点 / 业务逻辑 / 数据库 / 知识库），条目为实色图标块 + 名称，可拖拽到画布 |
| `components/NodeCard.vue`               | 节点厚卡：32px 实色图标块 + 标题（双击就地编辑）+ 摘要 + 分隔线 + 分区内容 + 运行结果行；多出口由节点库 `branches` 生成多个连接点 |
| `components/LoopBodyNode.vue`            | 循环体节点（循环的「下半身」）：48px 头部（图标 + 「循环体」+ 提示图标，底色带循环主色渐变）+ 内嵌点阵内层画布 + 右下角调整大小手柄；不持有数据，按 `data.loopId` 回查循环节点 |
| `components/NodeToolbar.vue`            | 节点悬浮工具条：悬停或选中时浮在卡片上方（试运行到此处 / 复制 / 删除，输入与输出不提供删除） |
| `components/NodeStatusBadge.vue`        | 运行态角标：运行中蓝色进度环、成功绿勾、失败红叉（卡片与容器共用）                        |
| `components/NodeConfigPanel.vue`        | 节点配置面板：头部（图标 + 名称 + 类型标签 + 节点 id）+ 字段区 + 底部操作区（试运行到此处 / 复制 / 删除），字段完全由节点库 schema 决定 |
| `components/NodeFieldList.vue`          | 字段渲染器：文本 / 多行 / 代码 / 下拉 / 数字 / 滑块 / 开关 / 参数列表（列表含「名称非空、不重复」校验与增删） |
| `components/RunPanel.vue`               | 运行预览面板：运行状态条、输入 / 输出变量与逐步运行日志，全部消费运行编排的状态，自身不再内置演示数据 |
| `components/CanvasControls.vue`         | 画布控件：左下缩放（放大 / 缩小 / 重置百分比 / 适应画布）+ 右下只读缩略图（节点分布与当前视口） |
| `components/DropzoneBackground.vue`     | 点阵网格背景 + 拖拽悬停高亮遮罩                                                        |
| `components/CustomConnectionLine.vue`   | 连接预览线（浅灰细线，跟随主题）                                                       |
| `composables/useDragAndDrop.js`         | 拖放逻辑：按 kind 建节点，落点尺寸取自 `draggedSizeOf()`                                 |
| `composables/useWorkflowRun.js`         | 运行编排：Kahn 拓扑排序、分支命中、容器成组依赖、确定性失败与「忽略异常」、运行日志与复位    |
| `composables/useWorkflowTheme.js`       | 浅色 / 深色主题状态（模块级 ref，不持久化）                                              |
| `composables/useWorkflowActions.js`     | 编辑器动作的 provide / inject 通道（节点卡片 / 容器 / 悬浮条 → 主页）                      |
| `composables/useInlineLabel.js`         | 标题就地编辑的共用逻辑（节点卡片、容器与编辑器工具条复用）                                 |
| `configs/cozeSchema.js`                 | 后端支持的节点类型映射（1/2/3/21）、字段注册表（怎么读、怎么写回原始结构）、卡片分区内容与卡片几何 |
| `configs/canvas.js`                     | 画布与连线：点阵常量、连线类型与箭头、节点层级、容器几何与 `createFlowNode` 等工厂          |
| `mock/workflowDetail.json`              | 内置的后端返回体（与 `GET /workflow/detail` 同构，含 `schema_json` 字符串），首屏由它解析出画布 |
| `utils/cozeWorkflow.js`                 | Coze schema 解析与序列化：坐标归一化、循环体归一化与回写、以原始对象为基底的保真回写、新建节点的最小合法结构 |

### 11.2 节点模型与数据来源

- 节点元信息**完全来自后端**：`schema_json` 里每个节点的 `data.nodeMeta`（`title` / `subTitle` / `description` / `icon` / `mainColor`）与数字 `type`，经 `utils/cozeWorkflow.js` 解析成画布节点；本模块与批改模块 `views/homework/utils/workflows.js` **彻底解耦**（不再 import 其任何内容，批改模块也未被改动）；
- 只保留后端 schema 支持的类型：`1` 开始 / `2` 结束 / `3` 大模型 / `21` 循环（循环是容器，子节点放在 `node.blocks`）；未识别的 type 渲染为通用卡片，不报错也不丢数据；
- 字段注册表（`fieldsOf` / `readFieldValues` / `writeFieldValue`）声明每个字段「怎么读、怎么写回原始结构」（如大模型参数读写 `data.inputs.llmParam` 数组、异常设置读写 `data.inputs.settingOnError`），面板与卡片都读同一份定义；
- 卡片几何由分区行数推导（`cardHeightFor(rows)` / `NODE_CARD_HEIGHT`）：标准卡片 240×153；拖拽落点（`draggedSizeOf`）与循环体默认尺寸共用同一份推导结果；
- 节点类型在 `WorkflowView.vue` 的 `nodeTypes` 中注册：厚卡 `card`（`NodeCard.vue`，含循环节点）与循环体 `loopBody`（`LoopBodyNode.vue`，常量 `LOOP_BODY_TYPE`）。

### 11.3 画布与交互要点

- **数据来源与回写**：首屏解析内置 mock（与 `GET /workflow/detail` 同构），`utils/cozeWorkflow.js` 负责 `schema_json` 的解析与序列化；回写**以原始节点对象为基底**，只覆盖界面编辑过的字段，未建模字段（如 `llmParam` 里的 `loopPrompt*`）原样保留，未移动过的节点直接复用原始坐标，因此未编辑时往返结果与后端字符串逐字节一致；
- **拖放**：面板条目 `dragstart` 把节点 type 写入 `dataTransfer` 的自定义 MIME（`application/vueflow`）；画布容器 `dragover` 必须 `preventDefault()`；`drop` 用 `useVueFlow().screenToFlowCoordinate()` 换算坐标，再按 `size / 2` 居中；`dragleave` 需排除画布内部子元素之间的移动；
- **状态归属**：首屏节点图由 `mock/initial.js` 提供，`<VueFlow>` 只接收初始 `nodes` / `edges`；挂载后的增删改一律通过 `useVueFlow()` 的动作完成（`addNodes` / `addEdges` / `removeNodes` / `updateEdge` / `updateNodeData`），避免与 props 双向同步互相覆盖；
- **节点动作归属**：`NodeToolbar.vue` 在悬停或选中时于卡片上方浮出（试运行到此处 / 复制 / 删除），`NodeConfigPanel.vue` 底部操作区提供同样三项（输入 / 输出不提供删除）；两者都经 `provideWorkflowActions()` / `useWorkflowActions()` 交回主页统一处理，保证选中态、右侧面板与「未保存」标记同步更新；
- **连接点**：`Handle` 本体是 9×9 圆点（灰底 + 2px 卡片底色描边），hover 转主色并放大；放大用独立 `scale` 属性，避免覆盖 Vue Flow 用于居中的 `transform`；输入节点无入口连接点、输出节点无出口连接点；IF 选择器按 `branches` 在右边缘纵向均分多个出口连接点，并在卡片外侧标注分支名（连线记录 `sourceHandle`，运行时只走命中分支）；
- **循环节点（两个节点构成的一个节点）**：与 Coze 开源一致，循环在画布上是**两个节点**叠成的一个整体——上面是「循环节点」厚卡（身份、循环类型 / 次数、输入输出端口都在它身上），下面是「循环体」子画布。循环体是一个**派生节点**：id 由循环节点派生（`loopBodyId()`，形如 `<循环id>-body`），以 `parentNode` 挂在循环节点下方、坐标相对循环节点（`loopBodyOffset()`，默认落在卡片下方 40px 并与卡片中线对齐），因此拖动循环节点时它自动跟随；它**不写回 schema**（回写时子节点仍然落到循环节点的 `blocks`、内部连线仍然用 `LOOP_PORT` 的固定端口名），也不参与运行编排（运行态统一记在循环节点上）。它不持有数据，图标 / 主色 / 标题都按 `data.loopId` 回查循环节点，因此改名、运行态天然同步；拖它的 48px 头部会驱动循环节点移动（`moveNode`），视觉上就是「拖循环体 = 拖整个循环」；
- **循环体**：内层画布是一块「画布中的画布」——用 CSS `radial-gradient` 铺静态点阵，数值与主画布共用 `configs/canvas.js` 的 `CANVAS_DOT`（18px 间距 / 1.4px 点径 / 同一点色），且**不做缩放换算**：节点渲染在 Vue Flow 被缩放的面板内，直接写世界坐标即可与主画布 `<Background>`（内部乘 `zoom` 补偿）在任何缩放级别保持同样的点距与点径；内层底色 `--wf-iteration-canvas` 引用 `--wf-canvas`，因此内外几乎同色、仅以头部与 1px 描边划分边界。**不用 `@vue-flow/background` 的 `<Background>`**，因为它会绑定主画布的 viewport 平移（pattern 的 DOM id 也会与主画布冲突）；循环体内子步骤是 Vue Flow 的独立节点（父节点是循环体），坐标相对循环体、**纵向排布**（与 Coze 一致，循环体因此保持窄长而不撑宽整张图）；循环体与循环节点之间用一条 CSS 伪元素竖线连接（位置固定，永远落在两者的中线上）；
- **循环体调整大小**：`LoopBodyNode.vue` 右下角手柄（带 `nodrag`，hover / 选中 / 拖拽中显现）在 `mousedown` 时记录起始鼠标位置、循环体实测尺寸与当前 `viewport.zoom`；`document` 上的 `mousemove` 按 `delta / zoom` 换算并收敛到 `CONTAINER_MIN_SIZE`，再经 `provideWorkflowActions().resizeNode()` → `updateNode(style)` 写回，并按新宽度重算 `loopBodyOffset()` 保持与循环节点中线对齐；`mouseup` 与 `onBeforeUnmount` 两处清理监听与 `document.body.style.userSelect`；
- **运行态**：运行态写在 `node.data.run`（`{ state: 'idle' | 'running' | 'success' | 'failed' | 'skipped', durationMs, summary }`），节点卡片据此换描边色、显示 `NodeStatusBadge` 角标与卡片内结果行（循环体不参与，运行态统一由循环节点承载）；**运行结果行的位置在卡片高度里始终预留**，运行时卡片尺寸不跳变、连线不会重排。`useWorkflowRun.js` 用 **Kahn 拓扑排序**（执行集合 = 所有根节点沿命中分支的前向可达）决定执行序列：循环体内的子步骤排在循环节点之前（子步骤额外依赖循环节点的上游），未命中的分支与上游失败后的节点标记为「未执行」；开启「忽略异常」时子节点失败不中断整条工作流；
- **连线样式**：连线与箭头颜色由 CSS 变量驱动——`EDGE_MARKER.color` 固定为 `currentColor`，由 `.workflow-canvas` 内 `.vue-flow__marker` 的 `color` 决定，因此可随主题切换；选中 / 悬停时描边转主色；
- **主题**：全部主题变量定义在 `.workflow-editor` / `.workflow-editor.is-dark` 上，并局部覆盖该页内的 `--el-*` 变量；**不改动 `html.dark`**，不影响 `IndexView` 侧边栏与其它模块。下拉浮层会挂到 `body`，通过 `body.wf-theme-dark` 这一段全局样式同步深色；
- **布局**：`IndexView.vue` 为 `/index/workflow` 提供 `index-main--flush`（去掉主区内边距与滚动），使编辑器全高铺满。

### 11.4 现状与待开发

已实现：后端 `schema_json` 解析与回写（未编辑时往返逐字节一致）、Coze 风格工具条（含试运行进度态）、四类节点的搜索面板、拖拽建节点、节点厚卡（分区内容读真实字段）、圆点连接点、**循环双节点**（循环节点厚卡 + 循环体子画布：48px 头部带主色渐变、内嵌点阵画布、纵向子步骤、右下角拖拽调整大小、整体联动移动）、贝塞尔连线、节点就地重命名、节点悬浮工具条、按类型渲染的配置面板、运行编排（拓扑逐节点亮起、成功 / 失败 / 未执行三态 + 卡片内耗时与结果摘要、「试运行到此处」只跑上游依赖链、「忽略异常」语义）、运行预览面板、缩放控件与缩略图、浅色 / 深色双主题。

> **待开发**：`src/api/workflow.js` 的接口路径（`/workflow/detail`、`/workflow/save`）待与后端确认，当前保存失败会提示并打印序列化结果到控制台；循环体暂不支持「拖入循环体并自动撑开」的手势语义，也没有空循环体内的「添加节点」引导（Coze 的 tips 提示条也未接入）；缩略图为只读，未支持点击跳转视口；未做源连接点上的「+」快捷添加下游节点与画布「自动布局」按钮；`views/homework/HomeworkGradingView.vue` 的「编辑工作流」按钮仍是占位提示（未跳转到本页）。

---

## 12. 数字人（Live2D，规划）

- 依赖：`pixi.js` `^6.5.10` + `pixi-live2d-display` `^0.4.0`（已在 `package.json` 声明）。
- 资源：`public/live2d/` 已放置 7 个模型（Haru / Hiyori / Mao / Mark / Natori / Rice / Wanko）及 `live2dcubismcore.min.js`。
- 现状：源码尚未接入（全量检索 `pixi` / `live2d` 无引用）；`views/ppt/index.vue` 右侧保留了「数字人展示页面」占位区。

> 该能力整体标注为**规划 / 待开发**，接入时建议以独立组件 + `composables` 封装 Pixi 生命周期。

---

## 13. 代码检查

采用 **oxlint + ESLint** 双工具（`npm run lint` 经 `npm-run-all2` 顺序执行）。

- oxlint：配置文件 `.oxlintrc.json`（`plugins: eslint/unicorn/oxc/vue`；`env.browser`；`categories.correctness = error`），命令 `oxlint . --fix`。
- ESLint：flat config `eslint.config.js`
  - 作用文件：`**/*.{vue,js,mjs,jsx}`；忽略 `dist` / `dist-ssr` / `coverage`。
  - 全局环境：`globals.browser`。
  - 规则集：`js.configs.recommended` + `pluginVue.configs['flat/essential']` + `pluginOxlint.buildFromOxlintConfigFile('.oxlintrc.json')`。
  - 命令：`eslint . --fix --cache`。

---

## 14. 启动与构建

```bash
cd frontend
npm install     # 首次安装依赖
npm run dev     # 开发启动 (Vite dev server)
npm run build   # 生产构建
npm run preview # 预览构建产物
npm run lint    # 代码检查（oxlint + eslint）
```

- Node 版本要求：`^22.18.0 || >=24.12.0`（见 `package.json` 的 `engines`）。
- 开发时通过 Vite 代理转发 `/api` 到 `http://localhost:8086`。
- 构建产物输出到 `dist/`。

---

## 15. 与后端的对接约定

| 项       | 约定                                                                                   |
| -------- | -------------------------------------------------------------------------------------- |
| baseURL  | `/api`（开发环境经 Vite 代理到 `http://localhost:8086`）                               |
| 数据格式 | JSON                                                                                   |
| 统一返回 | `CommonResult<T>`（code=0 成功），拦截器自动解包                                       |
| 认证     | Token 存储于 Cookie（`utils/auth.js` 管理，基于 js-cookie），请求自动携带 Bearer Token |
| 实时通信 | SSE (EventSource)，`text/event-stream`（规划）                                         |

> 现状说明：`views/TestView.vue` 的测试逻辑额外将 Token 写入 `localStorage`，与 Cookie 方式并存，以现状为准。

---

> **提醒**：前端开发前应先阅读 [总体技术规约](../总体技术规约.md) 与本文档，遵循统一约定。
