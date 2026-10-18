-- =============================================================
-- TrueBuddy - RBAC 种子数据（可重复执行）
--
-- 权限码唯一来源：system_menu.permission
-- 生效条件：system_role.status = 0 且 system_menu.status = 0
--
-- 角色约定：
--   normal(1)  / member(2)  普通用户与会员，无管理端权限
--   admin(3)               管理员，拥有全部菜单与按钮权限
--   auditor(4)             审核员，只读（无 config / 编辑类权限）
--
-- 说明：菜单 id 与前端 views/admin/mock/data.js 的 mockMenuTree 对齐，
-- 避免后端权限码与前端路由 meta.permission 出现两套编号。
-- =============================================================

-- ---------- 1. 角色 ----------
INSERT INTO system_role (id, name, code, status, remark) VALUES
    (1, '普通用户', 'normal',  0, '注册后的默认角色，仅使用用户端功能'),
    (2, '会员',     'member',  0, '用户端全部功能'),
    (3, '管理员',   'admin',   0, '管理端全部权限'),
    (4, '审核员',   'auditor', 0, '只读查看管理端内容与日志，不能修改配置')
ON CONFLICT DO NOTHING;

-- ---------- 2. 菜单 / 权限 ----------
INSERT INTO system_menu (id, name, parent_id, type, path, component, permission, icon, sort, status, visible) VALUES
    -- 概览
    (100, '概览', 0, 2, '/admin/dashboard', 'admin/dashboard/DashboardView', 'admin:dashboard', 'dashboard', 1, 0, TRUE),

    -- 用户与权限管理
    (200, '用户与权限管理', 0, 1, '/admin/user', '', '', 'shield', 2, 0, TRUE),
    (201, '用户列表', 200, 2, '/admin/user/list', 'admin/user/UserListView', 'system:user:list', 'users', 1, 0, TRUE),
    (2011, '新增用户', 201, 3, '', '', 'system:user:create', '', 1, 0, FALSE),
    (2012, '编辑用户', 201, 3, '', '', 'system:user:update', '', 2, 0, FALSE),
    (2013, '启用与禁用', 201, 3, '', '', 'system:user:status', '', 3, 0, FALSE),
    (2014, '重置密码', 201, 3, '', '', 'system:user:reset-password', '', 4, 0, FALSE),
    (202, '角色权限分配', 200, 2, '/admin/user/role', 'admin/user/RolePermissionView', 'system:role:list', 'shield', 2, 0, TRUE),
    (2021, '分配权限', 202, 3, '', '', 'system:role:assign', '', 1, 0, FALSE),
    (203, '菜单结构维护', 200, 2, '/admin/user/menu', 'admin/user/MenuTreeView', 'system:menu:list', 'key', 3, 0, FALSE),

    -- 文档模板管理
    (300, '文档模板管理', 0, 1, '/admin/doc-template', '', '', 'doc', 3, 0, TRUE),
    (301, '模板资源列表', 300, 2, '/admin/doc-template/templates', 'admin/doctemplate/TemplateListView', 'doc-template:template:list', 'doc', 1, 0, TRUE),
    (302, '生成记录查看', 300, 2, '/admin/doc-template/records', 'admin/doctemplate/GenerationRecordView', 'doc-template:record:list', 'log', 2, 0, TRUE),
    (303, '自检与修复问答记录', 300, 2, '/admin/doc-template/inspections', 'admin/doctemplate/InspectionRecordView', 'doc-template:inspection:list', 'health', 3, 0, TRUE),

    -- 作业批改管理
    (400, '作业批改管理', 0, 1, '/admin/homework', '', '', 'grading', 4, 0, TRUE),
    (401, '批改资源列表', 400, 2, '/admin/homework/resources', 'admin/homework/GradingResourceView', 'homework:resource:list', 'grading', 1, 0, TRUE),
    (402, '批改记录查看', 400, 2, '/admin/homework/records', 'admin/homework/GradingRecordView', 'homework:record:list', 'log', 2, 0, TRUE),

    -- 知识库管理
    (500, '知识库管理', 0, 1, '/admin/knowledge-base', '', '', 'book', 5, 0, TRUE),
    (501, '用户空间列表', 500, 2, '/admin/knowledge-base/spaces', 'admin/knowledgebase/SpaceListView', 'kb:space:list', 'book', 1, 0, TRUE),
    (502, '知识库创建统计', 500, 2, '/admin/knowledge-base/creation-stats', 'admin/knowledgebase/CreationStatsView', 'kb:stats:creation', 'health', 2, 0, TRUE),
    (503, '存储用量统计', 500, 2, '/admin/knowledge-base/storage-stats', 'admin/knowledgebase/StorageStatsView', 'kb:stats:storage', 'log', 3, 0, TRUE),

    -- PPT 智慧课堂管理
    (600, 'PPT 智慧课堂管理', 0, 1, '/admin/ppt', '', '', 'slide', 6, 0, TRUE),
    (601, '数字人形象配置', 600, 2, '/admin/ppt/avatar', 'admin/ppt/AvatarConfigView', 'ppt:avatar:config', 'users', 1, 0, TRUE),
    (602, '课件资源管理', 600, 2, '/admin/ppt/slides', 'admin/ppt/SlideResourceView', 'ppt:slide:list', 'slide', 2, 0, TRUE),
    (603, '会议记录查询', 600, 2, '/admin/ppt/meetings', 'admin/ppt/MeetingRecordView', 'ppt:meeting:list', 'meeting', 3, 0, TRUE),
    (604, '课堂数据统计', 600, 2, '/admin/ppt/class-stats', 'admin/ppt/ClassStatsView', 'ppt:stats:class', 'health', 4, 0, TRUE),
    (605, '课堂互动统计', 600, 2, '/admin/ppt/interaction-stats', 'admin/ppt/InteractionStatsView', 'ppt:stats:interaction', 'log', 5, 0, TRUE),

    -- 工作流管理
    (700, '工作流管理', 0, 1, '/admin/workflow', '', '', 'workflow', 7, 0, TRUE),
    (701, '工作流列表', 700, 2, '/admin/workflow/list', 'admin/workflow/WorkflowListView', 'workflow:definition:list', 'workflow', 1, 0, TRUE),
    (702, '节点模板维护', 700, 2, '/admin/workflow/node-templates', 'admin/workflow/NodeTemplateView', 'workflow:node-template:list', 'key', 2, 0, TRUE),
    (703, '运行记录查询', 700, 2, '/admin/workflow/runs', 'admin/workflow/RunRecordView', 'workflow:run:list', 'log', 3, 0, TRUE),
    (704, '执行异常排查', 700, 2, '/admin/workflow/exceptions', 'admin/workflow/ExecutionExceptionView', 'workflow:exception:list', 'health', 4, 0, TRUE),
    (705, '会话记录查看', 700, 2, '/admin/workflow/conversations', 'admin/workflow/ConversationRecordView', 'workflow:conversation:list', 'meeting', 5, 0, TRUE),

    -- 系统（功能图外，顶栏入口）
    (800, '系统', 0, 1, '/admin/system', '', '', 'setting', 8, 0, FALSE),
    (801, '系统配置', 800, 2, '/admin/system/setting', 'admin/system/SettingView', 'system:config:list', 'setting', 1, 0, FALSE),
    (802, '操作日志', 800, 2, '/admin/system/log', 'admin/system/OperationLogView', 'system:log:list', 'log', 2, 0, FALSE),
    (803, '服务健康', 800, 2, '/admin/system/health', 'admin/system/HealthView', 'system:health:list', 'health', 3, 0, FALSE)
ON CONFLICT DO NOTHING;

-- ---------- 3. 角色-菜单 ----------

-- 管理员：全部菜单（含后续新增，保持“全量”语义）
INSERT INTO system_role_menu (role_id, menu_id)
SELECT 3, id FROM system_menu
ON CONFLICT DO NOTHING;

-- 审核员：只读（概览 + 列表 + 统计 + 日志/健康；不含配置、编辑、分配权限）
INSERT INTO system_role_menu (role_id, menu_id)
SELECT 4, id FROM system_menu
WHERE id IN (
    100,
    200, 201, 202,
    300, 301, 302, 303,
    400, 401, 402,
    500, 501, 502, 503,
    600, 602, 603, 604, 605,
    700, 701, 702, 703, 704, 705,
    800, 802, 803
)
ON CONFLICT DO NOTHING;

-- 普通用户 / 会员：不关联任何管理端菜单（管理端入口由 admin:dashboard 控制）

-- ---------- 4. 演示账号的角色关联（幂等） ----------
INSERT INTO system_user_role (user_id, role_id)
SELECT v.user_id, v.role_id
FROM (VALUES (1, 3), (2, 1), (3, 2)) AS v(user_id, role_id)
-- system_user 是 PostgreSQL 保留字（等同 current_user），必须加引号
WHERE EXISTS (SELECT 1 FROM "system_user" u WHERE u.id = v.user_id)
ON CONFLICT DO NOTHING;

-- ---------- 5. 修正自增序列 ----------
SELECT setval('system_role_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_role), 4));
SELECT setval('system_menu_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_menu), 803));
SELECT setval('system_role_menu_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_role_menu), 1));
