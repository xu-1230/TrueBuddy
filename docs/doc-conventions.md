# doc-conventions · 工程规范（编码 / 组件 / 路由 / 状态 / 测试 / 文档）

> 项目：TrueBuddy ｜ 规范入口：[doc.md](./doc.md) ｜ 最近核对代码现状：2026-11-13
> 适用范围：`frontend/`、`backend/`、`docx-mcp/` 的编码、组件、路由、状态、测试约定与文档书写规则
> 相关：[doc-api.md](./doc-api.md)（接口与 Controller 映射）、[doc-ui.md](./doc-ui.md)（样式与主题）、[doc-devops.md](./doc-devops.md)（配置与门禁）、分端细则 `frontend|backend/AGENT - {端}技术规约.md`

---

## 1. 项目结构速览

| 部分 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 多模块 Maven：`tb-dependencies`（BOM）→ `tb-framework`（`tb-common` + web / mybatis / security starter）→ `tb-module-{test, test2, infra, knowledgebase, doctemplate, homework, workflow, lecture}` → `tb-server`（启动聚合，主类 `BackendApplication`，scanBasePackages 含 `org.example.backend` 与 `org.example.textflow`） |
| `frontend/` | Vue 3 + Vite SPA：`src/{api, assets, components, composables, lib, router, utils, views}`；views 按业务模块一目录（内含 `components/hooks/stores/utils`，模块视觉层另有 `styles/`，画布类另有 `node/configs`） |
| `docx-mcp/` | Python MCP 服务（uv 管理），被后端以 stdio 子进程拉起 |
| `backend/sql/` | 建表 SQL（每表一文件 + 日期命名增量补丁），表结构见 [doc-database.md](./doc-database.md) |

命名陷阱：① 前端工作流页面目录叫 `views/workflow2/`，路由仍是 `/index/workflow`；② 后端 workflow 包根是 `org.example.textflow`（全项目唯一例外）；③ `tb-module-test` / `tb-module-test2` 是业务验证模块，**不是测试代码**；④ `src/` 没有 `stores/` 目录（store 一律在各模块内）；⑤ 原 `digital-human-worker/` 已删除，不再存在独立语音进程。

## 2. 通用编码要求

| 项 | 要求 |
| --- | --- |
| 编码 | UTF-8（无 BOM）；换行 LF（靠 `frontend/.gitattributes` 与 `backend/.gitattributes` 各自约束） |
| 缩进 | 2 空格（前端）；后端 4 空格（Java 默认） |
| 行宽 | `.editorconfig` 建议 `max_line_length = 100`（仅 `frontend/.editorconfig`，后端无该文件） |
| 尾随空格 | 禁止 |
| 语言 | 前端纯 JavaScript（无 TypeScript）；后端 Java 17 |
| 命名语言 | 代码标识符用英文；注释与文档用中文 |
| 现状与规划 | 未完成的能力必须在注释/文档中标注「规划 / 待开发」 |

## 3. 前端编码规范

### 3.1 语法

| 规范 | 要求 |
| --- | --- |
| 组件写法 | 一律 `<script setup>`（Composition API）；顺序 `<script setup>` → `<template>` → `<style scoped>` |
| 变量声明 | 优先 `const`，需要重新赋值才用 `let`，禁用 `var` |
| 比较 | 使用 `===` / `!==`，**禁止** `==` / `!=` |
| 异步 | `async/await` + `try/catch`，禁止裸 `Promise.then` 链式忽略错误 |
| Props / Emits | `defineProps` / `defineEmits`；双向绑定用 `defineModel` |
| 模板 | 禁止在模板中写复杂表达式，逻辑放 `<script setup>` |
| 调试残留 | 提交前清除 `console.log` / 调试用写死数据 |

### 3.2 命名

| 对象 | 规则 | 示例 |
| --- | --- | --- |
| 组件文件 | 多单词 PascalCase | `LoginForm.vue`、`SpaceGridView.vue` |
| 页面文件 | PascalCase，以 `View.vue` 结尾 | `KnowledgeBaseView.vue` |
| 组合式函数 | `use` + PascalCase | `useSmoothScroll.js` |
| 变量 / 函数 | camelCase | `currentSlide`、`setSlides` |
| 常量 | UPPER_SNAKE_CASE | `CHAT_API`、`MEETING_REST_API` |
| props | camelCase | `documentName`、`spaceId` |
| emits | **kebab-case** | `login-success`、`delete-document` |
| Pinia store id | kebab-case | `"ppt-slides"`、`"meeting"` |
| 路由 name | kebab-case | `"system-home"`、`"doc-template"` |
| 测试文件 | `tests/{行为}.test.js`；多组用例用 `{行为}.{限定}.test.js`；复现历史缺陷加 `repro-` 前缀 | `run-state.iterations.test.js` |

### 3.3 导入与注释

- 跨目录导入统一使用 `@/` 别名（`@` → `frontend/src`）；同一目录内可用 `./`；**禁止** `../../` 跨层级相对路径。**现状**：仍有 15 处 `../../` 待收敛（集中在 `views/ppt/components/{screen,thumbnail,element}/**` 与 `views/guides/{HomeView,DocsView}.vue`）。
- 对外导出的接口函数建议写 JSDoc 契约（`api/doctemplate.js`、`api/homework.js` 已采用）；复杂逻辑必须写「为什么」；现状 / 规划、临时方案用 `// TODO:` / `// 规划:` 标注。

## 4. 前端组件规范

### 4.1 分层（禁止跨层混放）

| 层级 | 位置 | 判定标准 |
| --- | --- | --- |
| 全局通用组件 | `src/components/` | 被 ≥2 个模块使用，或属于全局框架（登录、导航） |
| 模块私有组件 | `views/{模块}/components/` | 仅本模块使用 |
| 页面内联 | 页面 `.vue` 内部 | 只服务于单个页面 |

判定口诀：**先放模块内，出现第二个使用方再上提到 `src/components/`**。已存在的细分扩展层（均属「模块私有」，不得随意上提）：`views/{模块}/node/`（画布节点卡片）、`views/{模块}/{screen,element,ai,thumbnail}/`（PPT 再分层）、`views/{模块}/hooks/`（模块私有组合式）、`views/{模块}/stores/`（模块私有 Pinia store）、`views/{模块}/utils/`（模块私有纯函数，**可测函数优先写这里**）、`views/{模块}/styles/`（模块私有全局样式，只由模块入口引入，如 `views/meeting/styles/meeting.css`）、`src/composables/`（跨模块组合式）、`src/lib/`（跨模块无 Vue 依赖纯函数）、`views/{模块}/mock/`（演示数据，须标注「演示态」）。

### 4.2 写法与约束

- 组件名与文件名一致（多单词 PascalCase）；模板中使用 PascalCase 标签；列表渲染必须给 `key`。
- Props 必须声明 `type`；必填 `required: true`，可选给 `default`；emits 事件名 kebab-case。
- Element Plus 已全量注册，优先使用（表单 `el-form`、列表 `el-table`、弹层 `el-dialog`）；图标从 `@element-plus/icons-vue` 具名引入；不进行自定义组件的全局注册。
- 样式一律 `<style scoped>`（细则见 [doc-ui.md](./doc-ui.md)）；覆盖 Element Plus 用 `:deep()` 并限定在组件根类下。
- **生命周期清理**：注册的监听、定时器、第三方实例（Lenis / GSAP / Pixi / LiveKit `Room`）必须在 `onBeforeUnmount` 清理；「拆毁路径上仍要发出让服务端收到」的请求走 `navigator.sendBeacon`（如离会）。

### 4.3 待整改

| 现状 | 问题 | 待整改 |
| --- | --- | --- |
| `views/{doctemplate,homework}/components/SquircleButton.vue` 两份逐字重复（各约 285 行） | 同名组件两份维护 | 抽取到 `src/components/`，并改读 `getComputedStyle` 解析的 Token 值 |
| 6 个文件含非 scoped `<style>` 块（`App.vue` 入口允许；`MarkdownViewer`、`DocTemplateView`、`ConfigPanel`、`WorkflowView`、`NodeDataDrawer`） | 全局污染风险 | 除入口外移入 `scoped` 或改 `:deep()` |
| 4 个文件同时有 scoped + 非 scoped 两块样式 | 两套作用域难判断 | 合并为单一样式块 + `:deep()` |
| `views/doctemplate/components/AiChatPanel.vue` 回复来自前端写死回复池 | 演示态与真实能力混排 | 接入真实 SSE 对话流 |

## 5. 前端路由规范

### 5.1 模式与写法

| 项 | 要求 |
| --- | --- |
| 模式 | `createWebHistory(import.meta.env.BASE_URL)`（HTML5 History） |
| 加载 | **全部**路由组件动态 `import()` 懒加载 |
| 配置位置 | 统一在 `src/router/`：`index.js` 汇总与守卫，管理端单独 `admin.routes.js` |
| 嵌套 | 布局页作父路由，功能页作 `children`；布局路由用 `redirect` 指向默认子页 |
| 命名 | `name` 与 `path` 都用小写 kebab-case（`system-home`、`knowledge-base`），二者保持一致 |

### 5.2 实际路由表（`src/router/index.js`，用户端）

| 路径 | name | 组件（懒加载） | 说明 |
| --- | --- | --- | --- |
| `/guides` | `guides` | `views/guides/HomeView.vue` | 首页，顶层路由、无 children |
| `/docs` | `docs` | `views/guides/DocsView.vue` | 文档 / 登录展示页，与 `/guides` 平级 |
| `/login`、`/register` | `login` / `register` | `views/LoginView.vue` | 登录 / 注册（复用同组件） |
| `/index` | `index` | `views/IndexView.vue` | 侧边栏布局，redirect → `/index/home` |
| `/index/home` | `system-home` | `views/system/SystemHomeView.vue` | 系统首页（展示台 + 动态聚合 `utils/feed.js`） |
| `/index/doc-template/:id?` | `doc-template` | `views/doctemplate/DocTemplateView.vue` | 文档模板生成；`:id` = 生成记录、`?file=<docId>` = 该次生成中的某份文档（URL 为页面状态唯一真相源，见 [specs/2026-11-16-模板生成页路由状态.md](./specs/2026-11-16-模板生成页路由状态.md)） |
| `/index/homework-grading` | `homework-grading` | `views/homework/HomeworkGradingView.vue` | 作业批改 |
| `/index/knowledge-base` | `knowledge-base` | `views/knowledgebase/KnowledgeBaseView.vue` | 知识库；`?space=<spaceId>` = 当前打开的空间（无参数为空间列表，URL 为页面状态唯一真相源，见 [specs/2026-11-17-知识库空间路由状态.md](./specs/2026-11-17-知识库空间路由状态.md)） |
| `/index/ppt` | `ppt` | `views/ppt/index.vue` | PPT 预览 / 放映 + 讲稿 AI 优化 + **创建会议入口**（`?screen=1` 直接进放映；`?meetid=` 时订阅音频驱动口型） |
| `/index/meeting` | `meeting-console` | `views/meeting/MeetingConsoleView.vue` | 老师控制台（由 PPT 页建会后带 `?meetid=` 打开；本页不提供建会） |
| `/index/workflow` | `workflow` | `views/workflow2/WorkflowListView.vue` | 工作流模块入口：我的工作流（内置模板单独置顶）+ 公有库；URL 为页面状态唯一真相源，见 [specs/2026-11-17-工作流入口列表与公有复刻.md](./specs/2026-11-17-工作流入口列表与公有复刻.md) 与 [adr/0007](./adr/0007-工作流入口收敛为登录态列表.md) |
| `/index/workflow/new` | `workflow-new` | `views/workflow2/WorkflowView.vue` | 新画布（未落库）；显式保存后 `replace` 到 `/:id` |
| `/index/workflow/:id` | `workflow-canvas` | `views/workflow2/WorkflowView.vue` | 某个工作流的画布：本人可编辑、builtin 只读可运行、他人公有只读可 fork；旧的 `?workflow_id=` 已废弃 |
| `/meeting/:meetingNo` | `meeting-room` | `views/meeting/MeetingRoomView.vue` | 学生会议室：顶层路由、免登录 |
| `/test` | `test` | `views/TestView.vue` | 连通性 / 身份测试页 |
| `/403`、`/:pathMatch(.*)*` | `forbidden` / `not-found` | `views/error/*` | 非管理员跳转 / 兜底 404 |

管理端路由（`admin.routes.js`，侧栏菜单由 `meta` 的 `title/icon/group/permission/pinned` 生成）：`/admin` 布局（redirect → dashboard，`meta.requiresAuth + requiresAdmin`）下分 6 组——`/admin/user/{list,role,menu}`（用户 / 角色权限 / 菜单）、`/admin/doc-template/{templates,records,inspections}`、`/admin/homework/{resources,records}`、`/admin/knowledge-base/{spaces,creation-stats,storage-stats}`、`/admin/ppt/{avatar,slides,meetings,class-stats,interaction-stats}`、`/admin/workflow/{list,node-templates,runs,exceptions,conversations}`、`/admin/system/{setting,log,health}`（后三者 `meta.menu=false`）。管理端与用户端**共用 `/login`**，按 `permissions` 里的 `admin:dashboard` 分流。

### 5.3 规划与待整改

| 项 | 现状 | 规范要求 / 规划 |
| --- | --- | --- |
| 路由守卫 | 管理端已实现 `beforeEach`（Token + `requiresAdmin`）；用户端 `/index` 自 2026-11-17 起在父路由声明 `meta.requiresAuth`，未登录跳 `/login?redirect=…`（ADR 0007） | 其余顶层用户页（`/guides`、`/docs`）维持公开 |
| 权限控制 | 管理端按 `meta.permission` + 服务端 `permissions` 过滤；用户端 `/index` 只校验登录态，未按角色细分 | 用户端按身份控制菜单与可达性 |
| 菜单来源 | 用户端在 `IndexView.vue` 内写死 `menuGroups`；管理端由路由 `meta` 生成 | 维持现状（管理端只改路由表一处） |

### 5.4 路由与安全的对应（需成对维护）

| 后端放行 URL（`application.yml`） | 前端对应 |
| --- | --- |
| `/system/auth/login`、`/system/auth/refresh` | `/login`、`/register`（refresh 由 401 拦截器自动调用，不跳路由） |
| `/test2/read-pptx`、`/test2/file-text` | 仅测试 / 联调桩，无正式页面 |
| `/homework/**` | `/index/homework-grading`（页面随 `/index` 要求登录；API 仍放行用于未登录态联调） |
| ~~`/workflow/**`~~ | **2026-11-17 起移出白名单**（ADR 0007）：工作流列表 / 详情 / 可见性 / fork / 删除全部要求登录并按 `creator` 授权；执行入口 `POST /chat` 同样要求登录 |
| `/meeting/**` | `/meeting/:meetingNo`（免登录入会）；`/index/meeting`（页面随 `/index` 要求登录，用 hostToken） |
| `/ppt/speech/speak` | `/index/ppt`（页面随 `/index` 要求登录）；**只有播报保留无状态放行**（2026-11-18）；讲稿生成、narrate、形象目录、音色库、偏好均要求登录 |
| `/admin/**`（仅身份接口已实现） | `/admin/**` 页面；其余业务接口为前端 mock，安全边界在后端 |

> `/doc-template/**` 自 2026-11-17 起**移出免登录白名单**：生成 / 历史 / 详情 / AI 编辑 / 撤回都要求登录并按 `creator` 隔离；`/index/doc-template/:id?` 页面未登录时不拉数据、显示登录提示。
>
> `/workflow/**` 同日移出白名单（ADR 0007）：`/index` 整个用户端后台要求登录，工作流定义类接口不再匿名可达；`/workflow/text` 联调接口随之要求登录。
>
> 新增免登录接口时，需同步 [doc-api.md](./doc-api.md) §2.4 与本文 §5.4。

## 6. 状态管理（前端）

| 原则 | 说明 |
| --- | --- |
| 局部优先 | 仅单个组件使用的状态用 `ref` / `reactive`，不得提升为全局 store |
| 就近放置 | 模块内状态放 `views/{模块}/stores/`；跨模块共享才放 `src/stores/` |
| 组合式优先 | 新 store 一律 **setup 写法**（`defineStore(id, () => {...})`） |
| 单一职责 | 一个 store 只管理一类状态（如「幻灯片数据」与「放映状态」分离） |
| 命名 | store id kebab-case；导出 `useXxxStore` PascalCase |

现有 store：`views/meeting/stores/meeting.js`（id `meeting`，setup 写法；**成员只有一份 `roomAttendees`，来自 LiveKit 房间事件**，REST 返回的台账前端不消费）、`views/ppt/stores/slides.js` 与 `screen.js`（options 写法，待迁移 setup）。画布运行时状态用组件内 ref + `provide/inject`（归约逻辑在 `workflow2/utils/run-state.js` 纯函数，有测试）。

持久化约定：

| 键 | 存储 | 用途 |
| --- | --- | --- |
| `token` / `refreshToken` | Cookie（`src/utils/auth.js`） | **登录态只由 auth.js 管理**，禁止组件自行读写 Token |
| `tb-ui-mode` | localStorage | 明暗态（null=跟随系统） |
| `tb-meeting-host:{meetingNo}` | localStorage | 按会议号切分的主持人凭据（**会议凭据不是登录态**，不进 auth.js） |
| `tb-meeting-attendee-{meetingNo}` | sessionStorage | 学生参会会话，关标签页即弃 |
| `tb-home-active-tab` | sessionStorage | 首页 Zone C 的过滤 tab 记忆（仅 filter 型 key，非法回落 `all`；见 [doc-ui.md](./doc-ui.md) §9） |

键名统一 `tb-{模块}-{用途}` 前缀；规划长期业务状态改用 Pinia 持久化插件统一处理。待整改：`views/TestView.vue` 额外写 localStorage、`views/ppt/stores/*.js` options 写法。

## 7. 后端编码规范

### 7.1 包与命名

| 对象 | 规则 |
| --- | --- |
| 包名 | `org.example.backend.*` 全小写；**唯一例外**：`tb-module-workflow` 包根 `org.example.textflow.*`，新增模块不得引入第二个包根 |
| 模块前缀 | 框架 `framework.*`；业务 `module.{模块名}.*` |
| 类 / 方法 / 常量 | PascalCase / camelCase / UPPER_SNAKE_CASE |
| DO | 表名对应 PascalCase + `DO` 后缀（普通 POJO + Lombok，**不继承 `BaseDO`**） |
| VO | `XxxReqVO` / `XxxRespVO` / `XxxSaveReqVO` |
| Mapper | `XxxMapper` 接口，无 XML |

### 7.2 分层与依赖

- 分层：`controller` → `service`（接口 + `service/Impl`）→ `mapper` → `model`(DO) / `model/vo`；依赖单向，`Controller` 不得直接调 `Mapper`。
- 跨模块调用只依赖对方的 `service` 接口，禁止直接依赖实现类与内部 DO。

### 7.3 语言与工具

| 规范 | 要求 |
| --- | --- |
| Lombok | 一律使用（`@Data`、`@Accessors(chain = true)`），禁止手写 getter/setter |
| 数据访问 | 业务 Mapper 用 **MyBatis 注解 SQL**（`@Select/@Insert/@Update`）+ `@Options(useGeneratedKeys = true)` 回填主键；**禁止新增 MyBatis XML**。例外：workflow 模块用 `JdbcTemplate` 手写 SQL（`repository/`），类注释明确「不引入 MyBatis」 |
| 校验 | 入参用 VO + `@Valid`，服务端必须校验 |
| 异常 | 业务异常抛 `ServiceException`（经 `ServiceExceptionUtil`）；禁止吞异常、禁止 `e.printStackTrace()` |
| 返回 | Controller 返回 `CommonResult<T>`；例外见 doc.md 硬约束 #1 |
| 配置 | 使用 `@ConfigurationProperties` 绑定，禁止散落 `@Value`（现状仍有 3 处，待整改） |
| 注释 | 公共类与对外方法写中文 Javadoc |

### 7.4 待整改

| 位置 | 现状 | 规范要求 |
| --- | --- | --- |
| `tb-module-test2/.../controller/PptxPage.java` | 普通 VO 放在 controller 包 | 移入 `model/vo` |
| `tb-module-infra/.../service/MinioFileStorageService.java` | 实现类直接在 `service/` | 放入 `service/Impl/` |
| `tb-module-test2/Test2Controller` | 硬编码绝对路径读本地 pptx | 移入配置或删除 |
| `tb-module-test/.../OAuth2TokenServiceImpl` | 两处 `@Value` | 收敛为 `@ConfigurationProperties` |
| `tb-module-homework/.../HomeworkController.grade` | `System.out.println(reqVO)` | 改用日志框架 |
| `sql/newsql/public.sql` | 全量导出内含 `DROP TABLE` | 只作参考，禁止当迁移脚本执行 |

## 8. 后端组件规范

框架组件以 **Spring Boot Starter** 形式提供（`tb-framework/`，`@AutoConfiguration` 自动装配）：`tb-common`（`CommonResult` / `PageResult` / `ErrorCode` / `ServiceException` 等基础库）、`tb-spring-boot-starter-web`（`GlobalExceptionHandler` / `WebProperties`）、`tb-spring-boot-starter-security`（`SecurityConfig` / `TokenAuthenticationFilter` / `SecurityProperties`）。约定：starter 不感知业务（禁止 import `module.*`）；配置走 `@ConfigurationProperties`（前缀 `tb.*`）；`tb-spring-boot-starter-mybatis`（MyBatis Plus）**当前没有任何业务模块依赖**，选它需先把 starter 接进模块。

业务模块内组件按职责归位：网关（`XxxGateway`，如 `DocxMcpGateway`）、解析网关（`service/parse/`）、分片策略（`service/chunk/`）、实时通道适配（`livekit/`）、语音合成（`digital/`）、可执行图节点（workflow `node/`）、SSE 发布器（`sse/ChatSsePublisher`）、仓储（workflow `repository/`）、提示词常量（`prompt/`）、TypeHandler（`mybatis/handler/`）、模块配置（`config/`，如 `DocTemplateProperties`、`MeetingProperties`）。约束：跨模块复用只依赖对方 service 接口；通用能力优先沉淀到 `tb-framework`；命名体现职责（Gateway / Service / Builder / Properties）。

## 9. 后端会话与请求上下文

| 项 | 约定 |
| --- | --- |
| 会话策略 | `STATELESS`，不用 `HttpSession`；CSRF 关闭 |
| 认证载体 | 请求头 `Authorization: Bearer <token>` 或参数 `token`；`TokenAuthenticationFilter` 解析 → `LoginUser` → `SecurityContext` |
| 用户上下文 | 业务代码一律 `SecurityFrameworkUtils.getLoginUser()`，禁止自行解析 Token 或直接取 userId |
| 方法级鉴权 | `@EnableMethodSecurity` 已开；`@PreAuthorize("hasAuthority('admin:dashboard')")` 可直接用（现有 `/admin/auth/**`） |
| 401 / 403 | `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`，响应体均为 `CommonResult` 结构 |
| 会议凭据 | `hostToken` / `attendeeToken` **不走** Spring Security，是业务侧字符串比对，与登录态无关 |

规划：签发标准 JWT（现为 `IdUtil.fastSimpleUUID()` 不透明令牌）、令牌存储落 `system_oauth2_*` 表；刷新机制已实现（`POST /system/auth/refresh` 一次性消费轮换 + 前端 401 单飞刷新重放）。

## 10. 测试规范

### 10.1 总体现状

| 端 | 现状 | 框架 |
| --- | --- | --- |
| 后端 | 4 个模块 22 个 JUnit 测试类（knowledgebase 8 / lecture 7 / workflow 5 / homework 2），全部是单元测试，零 `@SpringBootTest` / MockMvc | JUnit 5 + `spring-boot-starter-test`（Mockito、AssertJ） |
| 前端 | `frontend/tests/*.test.js` 40 个测试文件，`npm test` = `node --test`，零新增依赖 | Node 内置 `node:test` + `node:assert/strict` |
| docx-mcp | **零测试**（有 `.pytest_cache` 但无 test 文件） | — |

> 命名陷阱：`tb-module-test` / `tb-module-test2` 是业务验证模块，不进入 `./mvnw test` 测试集。组件渲染、`src/api/` 拦截器、端到端流程仍无覆盖。

### 10.2 后端约定

- 测试类与被测类**包路径一致**（workflow 的测试镜像 `org.example.textflow.**`，不要为「统一」挪进 `org.example.backend`）。
- 命名：单元 `XxxTest`（Mockito 打桩）、集成 `XxxIntegrationTest`（尚未使用）、探针 `XxxProbeTest`（真连外部服务但必须 `Assumptions` 无密钥时跳过）；方法名 `方法名_场景_期望结果`。
- 涉数据库的测试必须独立测试库或事务回滚，禁止污染 `tb` 库；外部依赖一律 Mock；断言统一 AssertJ。
- 命令：`./mvnw test`；单模块 `./mvnw -pl tb-module-lecture -am test`（`-am` 顺带构建依赖）。

### 10.3 前端约定

- 测试文件集中 `frontend/tests/*.test.js`，不散落 `src/`；断言用 `node:assert/strict`；不起真实后端请求。
- 在无 jsdom / @vue/test-utils 前提下的三种手法：①真实函数 + 替身环境（读 `.vue` 源码抽函数体求值）；②源码契约断言（对源码做正则断言防约束被改）；③`node:module` 别名 hook 解析 `@/` 后动态 import。
- 优先覆盖：`src/api/index.js` 拦截器、`src/utils/auth.js`、`meeting/hooks/useLiveKitRoom.js` 事件映射；组件测试（Vitest + @vue/test-utils）与端到端（Playwright）属**规划**，引入前不得假设 `*.spec.js` 存在。

### 10.4 提交前检查清单

1. `cd frontend && npm run lint` 2. `cd frontend && npm test` 3. `cd frontend && npm run build` 4. `cd backend && ./mvnw clean install` 5. `cd backend && ./mvnw test` 6. 手工验收关键路径（登录 → 知识库上传/向量化 → 文档模板生成 → 作业批改 → PPT 预览与讲稿 → 建会/入会/聊天/结束 → 数字人播报） 7. 按 §11 同步表检查文档。

## 11. 文档书写与同步约定

### 11.1 结构与排版

- 每个文件只有一个 `#` 标题，格式 `# doc-{主题} · 中文标题`；标题最多三级、不跳级；二级标题带序号（`## 1. xxx`）便于「见 §2.4」式引用。
- 文件头为引用块：项目 / 最近核对代码现状 / 规范入口（指向 doc.md）/ 适用范围 / 相关。**「最近核对代码现状」是事实截止日期**：按代码复核过才改，只改内容不复核不要动。
- 同时涉及前后端时先前端后后端；表格优先承载清单类信息；代码块必须标注语言；关键约束加粗、规划用引用块；不使用 emoji；中英文之间加空格。
- 链接：`docs/` 内部相对路径（`[doc-api.md](./doc-api.md)`）；指向子规约空格需编码；指向源码用反引号路径不建链接。**禁止指向已删除文件的链接**；新增/重命名文件后全量检索引用并同步。

### 11.2 内容红线

1. **现状与规划分离**：未落地能力必须标注「规划 / 待开发」并给出落地位置建议。
2. **不得美化**：代码与规范不一致时按「现状 / 规范要求 / 待整改」三段式如实记录。
3. 禁止泄露敏感信息（只能写 `${VAR}` 占位符名）。
4. 不得留空章节。
5. 版本号必须与 `pom.xml` / `package.json` 一致。
6. **失同步是双向的**：已落地能力不得挂在「规划」标签下；已删除的文件、端点、目录不得再作为现状描述。

### 11.3 命名与存放

| 对象 | 规则 |
| --- | --- |
| 规范文件 | `doc-{主题}.md`（总入口固定 `doc.md`） |
| 决策记录 | `adr/NNNN-决策标题.md`，四位序号递增，**不复用、不删除、不回溯修改**；被推翻时新开一篇并互相链接 |
| 变更规格 | `specs/YYYY-MM-DD-变更名.md`，落地后结论回写专题文档，spec 本身保留不改 |
| 视觉样张 | `docs/ui.html`，唯一视觉基准：先改样张 → 再改 `theme.css` |
| 临时/过程文档 | 不提交到 `docs/`（放 `.repro/`、`backend/tmp/` 等已忽略目录） |

### 11.4 必须同步文档的代码变更

| 代码变更 | 需同步的文档 |
| --- | --- |
| 新增 / 删除 / 重命名接口 | [doc-api.md](./doc-api.md)（接口清单）、本文 §5（映射与安全对应） |
| 新增路由 / 页面 | 本文 §5 |
| 新增模块 / 目录调整 | 本文 §1、[doc.md](./doc.md) 模块地图 |
| 依赖版本变更 | [doc.md](./doc.md) 技术栈 |
| 配置项 / 环境变量增删 | [doc-devops.md](./doc-devops.md) §2 |
| 放行白名单调整 | [doc-api.md](./doc-api.md) §2.4、本文 §5.4 |
| 组件 / 样式约定变化 | 本文 §3 / §4、[doc-ui.md](./doc-ui.md) |
| 主题 Token 变化 | 先 [ui.html](./ui.html) 样张 → `assets/theme.css` → doc-ui.md §3.3 分层表 |
| 工作流接口 / 节点 / 定义字段 | [doc-workflow.md](./doc-workflow.md)、doc-api.md |
| 会议 / 数字人 / 放映链路 | [doc-lecture.md](./doc-lecture.md)；术语口径变化同步其对应章节 |
| 数据库表结构变更 | [doc-database.md](./doc-database.md) |
| 做出不可逆决策 | 新增 `adr/NNNN-*.md` 并在受影响文档回链 |
| 开始一次多步变更 | 先落 `specs/YYYY-MM-DD-*.md`，完成后回写专题文档 |
| 新增 / 调整测试 | 本文 §10 |
| 新增进程 / 部署形态 | [doc.md](./doc.md)、[doc-devops.md](./doc-devops.md) |

## 12. 检查与门禁

| 范围 | 命令 | 说明 |
| --- | --- | --- |
| 前端 lint | `npm run lint` | `oxlint . --fix` + `eslint . --fix --cache`（`.oxlintrc.json` correctness 为 error；ESLint flat config） |
| 前端测试 | `npm test` | `node --test "tests/*.test.js"`（38 个文件） |
| 后端构建 | `./mvnw clean install` | 全模块构建，CI 缺位下的人工门禁 |
| 后端测试 | `./mvnw test` | 22 个测试类（§10.1） |

- 提交前必须本地跑通上述命令，不得以「稍后修」为由提交带 lint 报错的代码。
- **工具强制范围要如实看待**：`==` 禁止、组件名多单词、`@/` 别名统一**没有**被 ESLint 全量拦截，靠 review 与本文约束。
