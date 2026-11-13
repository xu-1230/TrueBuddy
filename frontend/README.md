# TrueBuddy 前端（frontend/）

> AI 驱动的教学辅助平台 TrueBuddy 的前端子项目。
> 详细规范请参阅 [AGENT - 前端技术规约](./AGENT%20-%20前端技术规约.md) 与 [docs/doc.md](../docs/doc.md)。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3（Composition API，`<script setup>`） | ^3.5.40 |
| 构建 | Vite | ^8.1.5 |
| 状态管理 | Pinia | ^4.0.2 |
| 路由 | Vue Router | ^5.2.0 |
| UI | Element Plus | ^2.14.5 |
| 图标 | @element-plus/icons-vue | ^2.3.2 |
| HTTP | Axios | ^1.19.0 |
| 存储 | js-cookie | ^3.0.8 |
| 流程图 | @vue-flow/core + background | ^1.48 / ^1.3 |

- 语言：纯 JavaScript（无 TypeScript）。
- 代码检查：oxlint + ESLint。

## 目录结构

```
frontend/src/
├── api/
│   ├── index.js          # axios 实例 + 请求/响应拦截器（携带 Token、解包 CommonResult）
│   └── test.js           # 登录 / 测试类接口
├── components/
│   ├── LoginForm.vue     # 登录表单（账号 / 邮箱两种方式，底部一键切换）
│   ├── RegisterForm.vue  # 注册表单
│   ├── ForgetPasswordForm.vue
│   ├── MailForm.vue      # 邮箱验证码登录（兼容保留）
│   └── NavbarItem.vue    # 顶部导航项
├── router/
│   └── index.js          # 路由配置（login / register / index / guides / test）
├── stores/
│   └── counter.js        # Pinia store 示例
├── utils/
│   └── auth.js           # Token 工具（基于 js-cookie）
├── views/
│   ├── guides/
│   │   ├── HomeView.vue  # 首页（含顶部导航）
│   │   └── DocsView.vue
│   ├── system/
│   │   ├── SystemHomeView.vue      # 系统首页
│   │   ├── DocTemplateView.vue     # 文档模板生成
│   │   ├── HomeworkGradingView.vue # 作业批改
│   │   └── KnowledgeBaseView.vue   # 知识库模块
│   ├── IndexView.vue     # 侧边栏布局（Logo + 功能模块 + 用户信息）
│   ├── LoginView.vue     # 登录页（账号/邮箱/注册/忘记密码）
│   └── TestView.vue
├── App.vue
└── main.js
```

## 路由概览

| 路径 | 说明 |
|------|------|
| `/login` | 登录页 |
| `/register` | 注册页（复用 LoginView） |
| `/index` | 侧边栏布局，默认重定向到 `/index/home` |
| `/index/home` | 系统首页 |
| `/index/doc-template` | 文档模板生成 |
| `/index/homework-grading` | 作业批改 |
| `/index/knowledge-base` | 知识库模块 |
| `/guides` | 首页（含顶部导航），子路由 `/docs` |
| `/test` | 测试页 |

## 快速开始

```sh
npm install      # 首次安装依赖
npm run dev      # 开发启动（Vite dev server）
npm run build    # 生产构建
npm run preview  # 预览构建产物
npm run lint     # 代码检查（oxlint + eslint）
```

## 说明

- 别名 `@/` 指向 `src/`（已配置于 `vite.config.js`）。
- 后端服务默认运行在 `8086` 端口，上下文路径为 `/api`；开发环境经 Vite 代理转发（`vite.config.js` 的 `server.proxy`）。
- Token 统一使用 Cookie 存取（见 `src/utils/auth.js`），请求拦截器通过 `getToken()` 自动携带 `Authorization` 头。
- 接口统一返回 `CommonResult<T>`（`{ code, message, data }`），响应拦截器在 `code === 0` 时自动解包为内层 `data`。