/**
 * 用户端（教师 / 学生）路由
 *
 * 说明：内容与拆分前 src/router/index.js 的 routes 完全一致，只做搬家不做改写；
 * 管理端路由见 ./admin.routes.js，两端在 ./index.js 汇总。
 */
export const userRoutes = [
  {
    // 默认路由：根路径直接进引导页
    path: "/",
    redirect: "/guides",
  },
  {
    path: "/guides",
    name: "guides",
    component: () => import("../views/guides/HomeView.vue"),
  },
  {
    path: "/docs",
    name: "docs",
    component: () => import("../views/guides/DocsView.vue"),
  },
  //登录页
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginView.vue"),
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../views/LoginView.vue"),
  },
  {
    path: "/index",
    name: "index",
    component: () => import("../views/IndexView.vue"),
    redirect: "/index/home",
    // 用户端后台整体要求登录（ADR 0007）：列表 / 详情 / 编辑都按 creator 取数，匿名没有可展示的归属数据
    meta: { requiresAuth: true },
    children: [
      { path: "home", name: "system-home", component: () => import("../views/system/SystemHomeView.vue") },
      // 文档模板生成：:id? 对应「某次生成」，?file= 对应「该次生成中的某份文档」（见 views/doctemplate/utils/route.js）
      { path: "doc-template/:id?", name: "doc-template", component: () => import("../views/doctemplate/DocTemplateView.vue") },
      { path: "homework-grading", name: "homework-grading", component: () => import("../views/homework/HomeworkGradingView.vue") },
      { path: "knowledge-base", name: "knowledge-base", component: () => import("../views/knowledgebase/KnowledgeBaseView.vue") },
      { path: "ppt", name: "ppt", component: () => import("../views/ppt/index.vue") },
      // 会议：老师控制台（建会、看名单与聊天、共享屏幕）
      { path: "meeting", name: "meeting-console", component: () => import("../views/meeting/MeetingConsoleView.vue") },
      // 工作流：模块入口是列表（我的 + 公有库），画布与新建是子路径（ADR 0007）
      { path: "workflow", name: "workflow", component: () => import("../views/workflow2/WorkflowListView.vue") },
      // 新建画布放在 :id 前，避免被动态段吞掉
      { path: "workflow/new", name: "workflow-new", component: () => import("../views/workflow2/WorkflowView.vue") },
      { path: "workflow/:id", name: "workflow-canvas", component: () => import("../views/workflow2/WorkflowView.vue") },
    ],
  },
  {
    // 学生会议室：独立于 /index 布局、免登录，学生点入会链接直接进入
    path: "/meeting/:meetingNo",
    name: "meeting-room",
    component: () => import("../views/meeting/MeetingRoomView.vue"),
  },
  {
    path: "/test",
    name: "test",
    component: () => import("../views/TestView.vue"),
  },
]
