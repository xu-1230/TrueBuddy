import { createRouter, createWebHistory } from "vue-router";
import { userRoutes } from "./user.routes";
import { adminRoutes } from "./admin.routes";
import { getToken } from "@/utils/auth";
import { buildLoginLocation, mergeRouteMeta, resolveAccess } from "@/utils/access";
import { useAdminSession, isAdminPreview } from "@/views/admin/hooks/useAdminSession";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...userRoutes,
    ...adminRoutes,
    {
      path: "/403",
      name: "forbidden",
      component: () => import("@/views/error/ForbiddenView.vue"),
    },
    {
      // 兜底：未命中的路径统一落到 404，而不是白屏
      path: "/:pathMatch(.*)*",
      name: "not-found",
      component: () => import("@/views/error/NotFoundView.vue"),
    },
  ],
});

/**
 * 全局守卫：只处理「已声明 requiresAuth」的路由，未声明的页面维持原有免登录行为。
 * 管理端登录页是 /admin/login，其余端仍是 /login。
 */
router.beforeEach((to) => {
  const meta = mergeRouteMeta(to.matched);
  const { status, roles } = useAdminSession();

  const access = resolveAccess(meta, {
    // 开发环境的「演示数据预览」不需要真实登录（生产构建里 isAdminPreview 恒为 false）
    hasToken: Boolean(getToken()) || isAdminPreview(),
    roleStatus: status.value,
    roles: roles.value,
  });

  if (access === "login") {
    const loginPath = to.path.startsWith("/admin") ? "/admin/login" : "/login";
    return buildLoginLocation(to.fullPath, loginPath);
  }

  if (access === "forbidden") {
    return { name: "forbidden", query: { from: to.fullPath } };
  }

  return true;
});

export default router;
