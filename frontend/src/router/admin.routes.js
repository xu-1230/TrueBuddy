/**
 * 管理端路由
 *
 * 一级模块与《管理员端系统功能模块图》一一对应：
 *   用户与权限管理 / 文档模板管理 / 作业批改管理 / 知识库管理 / PPT 智慧课堂管理 / 工作流管理
 * 图里的叶子功能（新增用户、编辑用户、启用与禁用、重置密码等）是页面内操作，
 * 不各开一条菜单；「概览」不属于功能模块，作为固定首页单独标记 pinned。
 *
 * 约定：
 *   - 路径层级与 views/admin/ 目录层级对应；
 *   - name 统一 admin- 前缀；
 *   - 侧栏菜单由本文件 meta 生成（title / icon / group / permission / pinned），
 *     新增页面只改这里一处，菜单、面包屑、权限过滤同时生效。
 */
export const adminRoutes = [
  {
    // 旧入口兼容：管理端已与用户端共用 /login，书签与旧链接不 404
    path: '/admin/login',
    redirect: (to) => {
      const target = typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/admin')
        ? to.query.redirect
        : '/admin/dashboard'
      return { path: '/login', query: { redirect: target } }
    },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/admin/AdminLayoutView.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/dashboard/DashboardView.vue'),
        meta: { title: '概览', icon: 'dashboard', group: '工作台', permission: 'admin:dashboard', pinned: true },
      },

      /* ---------------- 用户与权限管理 ---------------- */
      {
        path: 'user/list',
        name: 'admin-user-list',
        component: () => import('@/views/admin/user/UserListView.vue'),
        meta: { title: '用户列表', icon: 'users', group: '用户与权限管理', permission: 'system:user:list' },
      },
      {
        path: 'user/role',
        name: 'admin-user-role',
        component: () => import('@/views/admin/user/RolePermissionView.vue'),
        meta: { title: '角色权限分配', icon: 'shield', group: '用户与权限管理', permission: 'system:role:list' },
      },
      {
        // 菜单结构维护不在功能图内，作为角色权限页的附属入口，不进侧栏
        path: 'user/menu',
        name: 'admin-user-menu',
        component: () => import('@/views/admin/user/MenuTreeView.vue'),
        meta: { title: '菜单结构维护', menu: false, permission: 'system:menu:list' },
      },

      /* ---------------- 文档模板管理 ---------------- */
      {
        path: 'doc-template/templates',
        name: 'admin-doc-templates',
        component: () => import('@/views/admin/doctemplate/TemplateListView.vue'),
        meta: { title: '模板资源列表', icon: 'doc', group: '文档模板管理', permission: 'doc-template:template:list' },
      },
      {
        path: 'doc-template/records',
        name: 'admin-doc-records',
        component: () => import('@/views/admin/doctemplate/GenerationRecordView.vue'),
        meta: { title: '生成记录查看', icon: 'log', group: '文档模板管理', permission: 'doc-template:record:list' },
      },
      {
        path: 'doc-template/inspections',
        name: 'admin-doc-inspections',
        component: () => import('@/views/admin/doctemplate/InspectionRecordView.vue'),
        meta: { title: '自检与修复问答记录', icon: 'health', group: '文档模板管理', permission: 'doc-template:inspection:list' },
      },

      /* ---------------- 作业批改管理 ---------------- */
      {
        path: 'homework/resources',
        name: 'admin-homework-resources',
        component: () => import('@/views/admin/homework/GradingResourceView.vue'),
        meta: { title: '批改资源列表', icon: 'grading', group: '作业批改管理', permission: 'homework:resource:list' },
      },
      {
        path: 'homework/records',
        name: 'admin-homework-records',
        component: () => import('@/views/admin/homework/GradingRecordView.vue'),
        meta: { title: '批改记录查看', icon: 'log', group: '作业批改管理', permission: 'homework:record:list' },
      },

      /* ---------------- 知识库管理 ---------------- */
      {
        path: 'knowledge-base/spaces',
        name: 'admin-kb-spaces',
        component: () => import('@/views/admin/knowledgebase/SpaceListView.vue'),
        meta: { title: '用户空间列表', icon: 'book', group: '知识库管理', permission: 'kb:space:list' },
      },
      {
        path: 'knowledge-base/creation-stats',
        name: 'admin-kb-creation-stats',
        component: () => import('@/views/admin/knowledgebase/CreationStatsView.vue'),
        meta: { title: '知识库创建统计', icon: 'health', group: '知识库管理', permission: 'kb:stats:creation' },
      },
      {
        path: 'knowledge-base/storage-stats',
        name: 'admin-kb-storage-stats',
        component: () => import('@/views/admin/knowledgebase/StorageStatsView.vue'),
        meta: { title: '存储用量统计', icon: 'log', group: '知识库管理', permission: 'kb:stats:storage' },
      },

      /* ---------------- PPT 智慧课堂管理 ---------------- */
      {
        path: 'ppt/avatar',
        name: 'admin-ppt-avatar',
        component: () => import('@/views/admin/ppt/AvatarConfigView.vue'),
        meta: { title: '数字人形象配置', icon: 'users', group: 'PPT 智慧课堂管理', permission: 'ppt:avatar:config' },
      },
      {
        path: 'ppt/slides',
        name: 'admin-ppt-slides',
        component: () => import('@/views/admin/ppt/SlideResourceView.vue'),
        meta: { title: '课件资源管理', icon: 'slide', group: 'PPT 智慧课堂管理', permission: 'ppt:slide:list' },
      },
      {
        path: 'ppt/meetings',
        name: 'admin-ppt-meetings',
        component: () => import('@/views/admin/ppt/MeetingRecordView.vue'),
        meta: { title: '会议记录查询', icon: 'meeting', group: 'PPT 智慧课堂管理', permission: 'ppt:meeting:list' },
      },
      {
        path: 'ppt/class-stats',
        name: 'admin-ppt-class-stats',
        component: () => import('@/views/admin/ppt/ClassStatsView.vue'),
        meta: { title: '课堂数据统计', icon: 'health', group: 'PPT 智慧课堂管理', permission: 'ppt:stats:class' },
      },
      {
        path: 'ppt/interaction-stats',
        name: 'admin-ppt-interaction-stats',
        component: () => import('@/views/admin/ppt/InteractionStatsView.vue'),
        meta: { title: '课堂互动统计', icon: 'log', group: 'PPT 智慧课堂管理', permission: 'ppt:stats:interaction' },
      },

      /* ---------------- 工作流管理 ---------------- */
      {
        path: 'workflow/list',
        name: 'admin-workflow-list',
        component: () => import('@/views/admin/workflow/WorkflowListView.vue'),
        meta: { title: '工作流列表', icon: 'workflow', group: '工作流管理', permission: 'workflow:definition:list' },
      },
      {
        path: 'workflow/node-templates',
        name: 'admin-workflow-node-templates',
        component: () => import('@/views/admin/workflow/NodeTemplateView.vue'),
        meta: { title: '节点模板维护', icon: 'key', group: '工作流管理', permission: 'workflow:node-template:list' },
      },
      {
        path: 'workflow/runs',
        name: 'admin-workflow-runs',
        component: () => import('@/views/admin/workflow/RunRecordView.vue'),
        meta: { title: '运行记录查询', icon: 'log', group: '工作流管理', permission: 'workflow:run:list' },
      },
      {
        path: 'workflow/exceptions',
        name: 'admin-workflow-exceptions',
        component: () => import('@/views/admin/workflow/ExecutionExceptionView.vue'),
        meta: { title: '执行异常排查', icon: 'health', group: '工作流管理', permission: 'workflow:exception:list' },
      },
      {
        path: 'workflow/conversations',
        name: 'admin-workflow-conversations',
        component: () => import('@/views/admin/workflow/ConversationRecordView.vue'),
        meta: { title: '会话记录查看', icon: 'meeting', group: '工作流管理', permission: 'workflow:conversation:list' },
      },

      /* ---------------- 平台运维（功能图外，从顶栏进入） ---------------- */
      {
        path: 'system/setting',
        name: 'admin-system-setting',
        component: () => import('@/views/admin/system/SettingView.vue'),
        meta: { title: '系统配置', menu: false, permission: 'system:config:list' },
      },
      {
        path: 'system/log',
        name: 'admin-system-log',
        component: () => import('@/views/admin/system/OperationLogView.vue'),
        meta: { title: '操作日志', menu: false, permission: 'system:log:list' },
      },
      {
        path: 'system/health',
        name: 'admin-system-health',
        component: () => import('@/views/admin/system/HealthView.vue'),
        meta: { title: '服务健康', menu: false, permission: 'system:health:list' },
      },
    ],
  },
]
