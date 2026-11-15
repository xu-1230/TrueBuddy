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
    children: [
      { path: "home", name: "system-home", component: () => import("../views/system/SystemHomeView.vue") },
      { path: "doc-template", name: "doc-template", component: () => import("../views/doctemplate/DocTemplateView.vue") },
      { path: "homework-grading", name: "homework-grading", component: () => import("../views/homework/HomeworkGradingView.vue") },
      { path: "knowledge-base", name: "knowledge-base", component: () => import("../views/knowledgebase/KnowledgeBaseView.vue") },
      { path: "ppt", name: "ppt", component: () => import("../views/ppt/index.vue") },
      // 模拟会议：老师控制台（建会、看名单与聊天、共享屏幕）
      { path: "meeting", name: "meeting-console", component: () => import("../views/meeting/MeetingConsoleView.vue") },
      // 工作流编排：画布页（workflow2）
      { path: "workflow", name: "workflow", component: () => import("../views/workflow2/WorkflowView.vue") },
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
