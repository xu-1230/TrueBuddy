import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
        // 工作流编排：画布页（workflow2）
        { path: "workflow", name: "workflow", component: () => import("../views/workflow2/WorkflowView.vue") },
      ],
    },
    {
      path: "/test",
      name: "test",
      component: () => import("../views/TestView.vue"),
    },
  ],
});

export default router;
