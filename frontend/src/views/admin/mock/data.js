/**
 * 管理端演示数据
 *
 * 定位：**仅当 `/admin/**` 接口不可用**（后端未启动、接口未实现）时由页面兜底使用，
 * 有真实接口时不会走到这里。表格会明确标注「演示数据」，避免把演示值当成线上事实。
 *
 * 数据形状刻意与将来接口返回保持一致（分页统一 { list, total }），
 * 接上后端时页面不需要改取值逻辑。
 */

export const mockUsers = [
  { id: 1, username: 'admin', nickname: '系统管理员', email: 'admin@truebuddy.cn', roles: ['admin'], status: 0, createTime: '2026-03-02 09:00', lastLogin: '2026-10-18 08:42' },
  { id: 2, username: 'normal', nickname: '林小满', email: 'linxiaoman@stu.cn', roles: ['normal'], status: 0, createTime: '2026-04-11 10:24', lastLogin: '2026-10-17 21:08' },
  { id: 3, username: 'member', nickname: '陈可', email: 'chenke@stu.cn', roles: ['member'], status: 0, createTime: '2026-04-18 14:02', lastLogin: '2026-10-18 07:35' },
  { id: 4, username: 'zhangwei', nickname: '张伟', email: 'zhangwei@stu.cn', roles: ['normal'], status: 0, createTime: '2026-05-06 09:12', lastLogin: '2026-10-16 19:40' },
  { id: 5, username: 'liyan', nickname: '李妍', email: 'liyan@stu.cn', roles: ['member'], status: 0, createTime: '2026-05-20 16:48', lastLogin: '2026-10-18 09:02' },
  { id: 6, username: 'wangqiang', nickname: '王强', email: 'wangqiang@stu.cn', roles: ['normal'], status: 0, createTime: '2026-06-01 11:36', lastLogin: '2026-10-15 13:22' },
  { id: 7, username: 'zhaolin', nickname: '赵琳', email: 'zhaolin@truebuddy.cn', roles: ['auditor'], status: 0, createTime: '2026-06-14 08:55', lastLogin: '2026-10-17 10:11' },
  { id: 8, username: 'sunyu', nickname: '孙宇', email: 'sunyu@stu.cn', roles: ['normal'], status: 0, createTime: '2026-07-08 15:20', lastLogin: '2026-10-12 20:45' },
  { id: 9, username: 'zhoujie', nickname: '周洁', email: 'zhoujie@stu.cn', roles: ['member'], status: 0, createTime: '2026-08-19 10:05', lastLogin: '2026-10-18 08:16' },
  { id: 10, username: 'wuhao', nickname: '吴昊', email: 'wuhao@stu.cn', roles: ['normal'], status: 0, createTime: '2026-09-02 13:41', lastLogin: '2026-10-11 09:33' },
  { id: 11, username: 'xujing', nickname: '徐静', email: 'xujing@stu.cn', roles: ['member'], status: 1, createTime: '2026-09-15 17:09', lastLogin: '2026-09-28 22:10' },
  { id: 12, username: 'hechen', nickname: '何晨', email: 'hechen@stu.cn', roles: ['normal'], status: 0, createTime: '2026-10-03 09:47', lastLogin: '2026-10-17 18:52' },
]

export const mockRoles = [
  { id: 1, name: '普通用户', code: 'normal', memberCount: 42, status: 0, remark: '注册后的默认角色，可使用基础教学工具' },
  { id: 2, name: '会员', code: 'member', memberCount: 18, status: 0, remark: '开放全部创作能力与更高配额' },
  { id: 3, name: '管理员', code: 'admin', memberCount: 2, status: 0, remark: '管理端全部权限' },
  { id: 4, name: '审核员', code: 'auditor', memberCount: 3, status: 0, remark: '只读查看内容与日志，不能改配置' },
]

export const mockMenuTree = [
  {
    id: 100,
    name: '概览',
    type: 2,
    path: '/admin/dashboard',
    component: 'admin/dashboard/DashboardView',
    permission: 'admin:dashboard',
    icon: 'dashboard',
    sort: 1,
    visible: true,
  },
  {
    id: 200,
    name: '用户与权限管理',
    type: 1,
    path: '/admin/user',
    component: '',
    permission: '',
    icon: 'shield',
    sort: 2,
    visible: true,
    children: [
      {
        id: 201,
        name: '用户列表',
        type: 2,
        path: '/admin/user/list',
        component: 'admin/user/UserListView',
        permission: 'system:user:list',
        icon: 'users',
        sort: 1,
        visible: true,
        children: [
          { id: 2011, name: '新增用户', type: 3, path: '', component: '', permission: 'system:user:create', icon: '', sort: 1, visible: true },
          { id: 2012, name: '编辑用户', type: 3, path: '', component: '', permission: 'system:user:update', icon: '', sort: 2, visible: true },
          { id: 2013, name: '启用与禁用', type: 3, path: '', component: '', permission: 'system:user:status', icon: '', sort: 3, visible: true },
          { id: 2014, name: '重置密码', type: 3, path: '', component: '', permission: 'system:user:reset-password', icon: '', sort: 4, visible: true },
        ],
      },
      {
        id: 202,
        name: '角色权限分配',
        type: 2,
        path: '/admin/user/role',
        component: 'admin/user/RolePermissionView',
        permission: 'system:role:list',
        icon: 'shield',
        sort: 2,
        visible: true,
        children: [
          { id: 2021, name: '分配权限', type: 3, path: '', component: '', permission: 'system:role:assign', icon: '', sort: 1, visible: true },
        ],
      },
      {
        id: 203,
        name: '菜单结构维护',
        type: 2,
        path: '/admin/user/menu',
        component: 'admin/user/MenuTreeView',
        permission: 'system:menu:list',
        icon: 'key',
        sort: 3,
        visible: false,
      },
    ],
  },
  {
    id: 300,
    name: '文档模板管理',
    type: 1,
    path: '/admin/doc-template',
    component: '',
    permission: '',
    icon: 'doc',
    sort: 3,
    visible: true,
    children: [
      { id: 301, name: '模板资源列表', type: 2, path: '/admin/doc-template/templates', component: 'admin/doctemplate/TemplateListView', permission: 'doc-template:template:list', icon: 'doc', sort: 1, visible: true },
      { id: 302, name: '生成记录查看', type: 2, path: '/admin/doc-template/records', component: 'admin/doctemplate/GenerationRecordView', permission: 'doc-template:record:list', icon: 'log', sort: 2, visible: true },
      { id: 303, name: '自检与修复问答记录', type: 2, path: '/admin/doc-template/inspections', component: 'admin/doctemplate/InspectionRecordView', permission: 'doc-template:inspection:list', icon: 'health', sort: 3, visible: true },
    ],
  },
  {
    id: 400,
    name: '作业批改管理',
    type: 1,
    path: '/admin/homework',
    component: '',
    permission: '',
    icon: 'grading',
    sort: 4,
    visible: true,
    children: [
      { id: 401, name: '批改资源列表', type: 2, path: '/admin/homework/resources', component: 'admin/homework/GradingResourceView', permission: 'homework:resource:list', icon: 'grading', sort: 1, visible: true },
      { id: 402, name: '批改记录查看', type: 2, path: '/admin/homework/records', component: 'admin/homework/GradingRecordView', permission: 'homework:record:list', icon: 'log', sort: 2, visible: true },
    ],
  },
  {
    id: 500,
    name: '知识库管理',
    type: 1,
    path: '/admin/knowledge-base',
    component: '',
    permission: '',
    icon: 'book',
    sort: 5,
    visible: true,
    children: [
      { id: 501, name: '用户空间列表', type: 2, path: '/admin/knowledge-base/spaces', component: 'admin/knowledgebase/SpaceListView', permission: 'kb:space:list', icon: 'book', sort: 1, visible: true },
      { id: 502, name: '知识库创建统计', type: 2, path: '/admin/knowledge-base/creation-stats', component: 'admin/knowledgebase/CreationStatsView', permission: 'kb:stats:creation', icon: 'health', sort: 2, visible: true },
      { id: 503, name: '存储用量统计', type: 2, path: '/admin/knowledge-base/storage-stats', component: 'admin/knowledgebase/StorageStatsView', permission: 'kb:stats:storage', icon: 'log', sort: 3, visible: true },
    ],
  },
  {
    id: 600,
    name: 'PPT 智慧课堂管理',
    type: 1,
    path: '/admin/ppt',
    component: '',
    permission: '',
    icon: 'slide',
    sort: 6,
    visible: true,
    children: [
      { id: 601, name: '数字人形象配置', type: 2, path: '/admin/ppt/avatar', component: 'admin/ppt/AvatarConfigView', permission: 'ppt:avatar:config', icon: 'users', sort: 1, visible: true },
      { id: 602, name: '课件资源管理', type: 2, path: '/admin/ppt/slides', component: 'admin/ppt/SlideResourceView', permission: 'ppt:slide:list', icon: 'slide', sort: 2, visible: true },
      { id: 603, name: '会议记录查询', type: 2, path: '/admin/ppt/meetings', component: 'admin/ppt/MeetingRecordView', permission: 'ppt:meeting:list', icon: 'meeting', sort: 3, visible: true },
      { id: 604, name: '课堂数据统计', type: 2, path: '/admin/ppt/class-stats', component: 'admin/ppt/ClassStatsView', permission: 'ppt:stats:class', icon: 'health', sort: 4, visible: true },
      { id: 605, name: '课堂互动统计', type: 2, path: '/admin/ppt/interaction-stats', component: 'admin/ppt/InteractionStatsView', permission: 'ppt:stats:interaction', icon: 'log', sort: 5, visible: true },
    ],
  },
  {
    id: 700,
    name: '工作流管理',
    type: 1,
    path: '/admin/workflow',
    component: '',
    permission: '',
    icon: 'workflow',
    sort: 7,
    visible: true,
    children: [
      { id: 701, name: '工作流列表', type: 2, path: '/admin/workflow/list', component: 'admin/workflow/WorkflowListView', permission: 'workflow:definition:list', icon: 'workflow', sort: 1, visible: true },
      { id: 702, name: '节点模板维护', type: 2, path: '/admin/workflow/node-templates', component: 'admin/workflow/NodeTemplateView', permission: 'workflow:node-template:list', icon: 'key', sort: 2, visible: true },
      { id: 703, name: '运行记录查询', type: 2, path: '/admin/workflow/runs', component: 'admin/workflow/RunRecordView', permission: 'workflow:run:list', icon: 'log', sort: 3, visible: true },
      { id: 704, name: '执行异常排查', type: 2, path: '/admin/workflow/exceptions', component: 'admin/workflow/ExecutionExceptionView', permission: 'workflow:exception:list', icon: 'health', sort: 4, visible: true },
      { id: 705, name: '会话记录查看', type: 2, path: '/admin/workflow/conversations', component: 'admin/workflow/ConversationRecordView', permission: 'workflow:conversation:list', icon: 'meeting', sort: 5, visible: true },
    ],
  },
  {
    id: 800,
    name: '系统',
    type: 1,
    path: '/admin/system',
    component: '',
    permission: '',
    icon: 'setting',
    sort: 8,
    visible: false,
    children: [
      { id: 801, name: '系统配置', type: 2, path: '/admin/system/setting', component: 'admin/system/SettingView', permission: 'system:config:list', icon: 'setting', sort: 1, visible: false },
      { id: 802, name: '操作日志', type: 2, path: '/admin/system/log', component: 'admin/system/OperationLogView', permission: 'system:log:list', icon: 'log', sort: 2, visible: false },
      { id: 803, name: '服务健康', type: 2, path: '/admin/system/health', component: 'admin/system/HealthView', permission: 'system:health:list', icon: 'health', sort: 3, visible: false },
    ],
  },
]

export const mockRoleMenuIds = {
  1: [100, 200, 201, 300, 301, 400, 402, 500, 501, 600, 603, 700, 701],
  2: [100, 200, 201, 300, 301, 302, 400, 401, 402, 500, 501, 502, 600, 602, 603, 604, 700, 701, 703],
  3: [
    100, 200, 201, 2011, 2012, 2013, 2014, 202, 2021, 203,
    300, 301, 302, 303,
    400, 401, 402,
    500, 501, 502, 503,
    600, 601, 602, 603, 604, 605,
    700, 701, 702, 703, 704, 705,
    800, 801, 802, 803,
  ],
  4: [
    100, 200, 201, 202, 300, 301, 302, 303, 400, 401, 402,
    500, 501, 502, 503, 600, 601, 602, 603, 604, 605,
    700, 701, 702, 703, 704, 705, 800, 802, 803,
  ],
}

export const mockKbDocuments = [
  { id: 1, name: '人教版八年级物理·上册.pdf', space: '初中物理', type: 'pdf', size: 8_642_311, status: 2, chunks: 186, updatedBy: '林小满', updatedAt: '2026-10-17 16:22' },
  { id: 2, name: '函数与导数复习提纲.docx', space: '高中数学', type: 'docx', size: 1_204_882, status: 2, chunks: 74, updatedBy: '陈可', updatedAt: '2026-10-17 11:04' },
  { id: 3, name: '中国古代史·朝代时间轴.md', space: '高中历史', type: 'md', size: 42_118, status: 4, chunks: 21, updatedBy: '张伟', updatedAt: '2026-10-16 20:31' },
  { id: 4, name: '化学实验安全规范.pdf', space: '初中化学', type: 'pdf', size: 3_118_006, status: 6, chunks: 0, updatedBy: '李妍', updatedAt: '2026-10-16 15:47' },
  { id: 5, name: '英语写作常用句式.docx', space: '高中英语', type: 'docx', size: 980_334, status: 2, chunks: 58, updatedBy: '王强', updatedAt: '2026-10-15 09:12' },
  { id: 6, name: '信息技术·算法入门.pptx', space: '信息技术', type: 'pptx', size: 15_882_411, status: 3, chunks: 0, updatedBy: '赵琳', updatedAt: '2026-10-14 18:03' },
  { id: 7, name: '生物·细胞结构图解.pdf', space: '高中生物', type: 'pdf', size: 6_402_119, status: 1, chunks: 0, updatedBy: '孙宇', updatedAt: '2026-10-14 10:55' },
  { id: 8, name: '小学语文·古诗文诵读.md', space: '小学语文', type: 'md', size: 128_774, status: 7, chunks: 33, updatedBy: '周洁', updatedAt: '2026-10-13 14:26' },
]

export const mockDocTemplateTasks = [
  { id: 1, title: '八年级物理《浮力》教案', template: '标准教案模板 v3', owner: '林小满', status: 'success', progress: 100, createdAt: '2026-10-18 08:40', finishedAt: '2026-10-18 08:44' },
  { id: 2, title: '高二数学《导数应用》学案', template: '学案模板 v2', owner: '陈可', status: 'running', progress: 62, createdAt: '2026-10-18 09:12', finishedAt: '' },
  { id: 3, title: '七年级语文《春》说课稿', template: '说课稿模板 v1', owner: '张伟', status: 'success', progress: 100, createdAt: '2026-10-17 20:05', finishedAt: '2026-10-17 20:09' },
  { id: 4, title: '化学实验《粗盐提纯》报告单', template: '实验报告模板 v2', owner: '李妍', status: 'failed', progress: 38, createdAt: '2026-10-17 16:31', finishedAt: '2026-10-17 16:35' },
  { id: 5, title: '英语 Unit 5 阅读课教案', template: '标准教案模板 v3', owner: '王强', status: 'success', progress: 100, createdAt: '2026-10-17 10:18', finishedAt: '2026-10-17 10:23' },
  { id: 6, title: '信息技术《算法初步》教学设计', template: '教学设计模板 v1', owner: '赵琳', status: 'pending', progress: 0, createdAt: '2026-10-18 09:35', finishedAt: '' },
]

export const mockHomeworkTasks = [
  { id: 1, name: '八年级物理·第三章作业', className: '初二（3）班', fileCount: 42, owner: '林小满', status: 'success', averageScore: 86.4, createdAt: '2026-10-17 19:20' },
  { id: 2, name: '高二数学·导数单元测', className: '高二（1）班', fileCount: 38, owner: '陈可', status: 'running', averageScore: null, createdAt: '2026-10-18 08:55' },
  { id: 3, name: '七年级语文·散文阅读', className: '初一（5）班', fileCount: 45, owner: '张伟', status: 'success', averageScore: 78.2, createdAt: '2026-10-17 15:02' },
  { id: 4, name: '九年级化学·方程式书写', className: '初三（2）班', fileCount: 40, owner: '李妍', status: 'failed', averageScore: null, createdAt: '2026-10-17 09:48' },
  { id: 5, name: '高一英语·完形填空专项', className: '高一（4）班', fileCount: 44, owner: '王强', status: 'success', averageScore: 81.9, createdAt: '2026-10-16 20:35' },
  { id: 6, name: '信息技术·Python 基础练习', className: '高一（6）班', fileCount: 36, owner: '赵琳', status: 'pending', averageScore: null, createdAt: '2026-10-18 09:41' },
  { id: 7, name: '八年级生物·细胞结构', className: '初二（1）班', fileCount: 41, owner: '孙宇', status: 'success', averageScore: 88.6, createdAt: '2026-10-16 14:12' },
]

export const mockMeetingRecords = [
  { id: 1, meetingNo: '384217', title: '八年级物理·浮力习题课', host: '林小满', attendeeCount: 41, duration: 2712, status: 2, startedAt: '2026-10-17 14:00', endedAt: '2026-10-17 14:45' },
  { id: 2, meetingNo: '512903', title: '高二数学·导数答疑', host: '陈可', attendeeCount: 27, duration: 1805, status: 2, startedAt: '2026-10-17 19:30', endedAt: '2026-10-17 20:00' },
  { id: 3, meetingNo: '760418', title: '七年级语文·散文阅读讲评', host: '张伟', attendeeCount: 38, duration: 2380, status: 2, startedAt: '2026-10-16 10:10', endedAt: '2026-10-16 10:50' },
  { id: 4, meetingNo: '192744', title: '九年级化学·实验安全', host: '李妍', attendeeCount: 12, duration: 640, status: 2, startedAt: '2026-10-15 16:20', endedAt: '2026-10-15 16:31' },
  { id: 5, meetingNo: '638052', title: '高一英语·写作工作坊', host: '王强', attendeeCount: 33, duration: 1980, status: 2, startedAt: '2026-10-15 09:00', endedAt: '2026-10-15 09:33' },
  { id: 6, meetingNo: '845176', title: '信息技术·算法初步', host: '赵琳', attendeeCount: 29, duration: 0, status: 1, startedAt: '2026-10-18 09:30', endedAt: '' },
]

export const mockWorkflowDefinitions = [
  { id: 1, name: '作业批改标准流程', description: '上传 → 抽取正文 → 评分 → 生成评语', nodeCount: 9, status: 'published', updatedBy: '系统管理员', updatedAt: '2026-10-14 11:20' },
  { id: 2, name: '作文批改（含人工复核）', description: 'AI 初评后进入人工确认节点', nodeCount: 12, status: 'published', updatedBy: '林小满', updatedAt: '2026-10-12 16:45' },
  { id: 3, name: '实验报告批改', description: '按实验步骤逐项打分', nodeCount: 7, status: 'draft', updatedBy: '李妍', updatedAt: '2026-10-09 09:12' },
  { id: 4, name: '英语口语评测', description: '语音转写 + 口语维度评分', nodeCount: 8, status: 'disabled', updatedBy: '王强', updatedAt: '2026-09-28 14:30' },
  { id: 5, name: '学情周报生成', description: '汇总一周作业与会议数据', nodeCount: 6, status: 'published', updatedBy: '陈可', updatedAt: '2026-10-16 18:02' },
]

export const mockOperationLogs = [
  { id: 1, time: '2026-10-18 09:41', operator: 'admin', module: 'content', action: '提交作业批改任务', target: '信息技术·Python 基础练习', result: 'success', ip: '192.168.1.24', durationMs: 412 },
  { id: 2, time: '2026-10-18 09:12', operator: 'admin', module: 'user', action: '禁用用户', target: 'xujing', result: 'success', ip: '192.168.1.24', durationMs: 86 },
  { id: 3, time: '2026-10-18 08:40', operator: 'linxiaoman', module: 'content', action: '创建文档模板任务', target: '八年级物理《浮力》教案', result: 'success', ip: '10.12.3.51', durationMs: 128 },
  { id: 4, time: '2026-10-17 20:31', operator: 'zhangwei', module: 'content', action: '更新知识库文档', target: '中国古代史·朝代时间轴.md', result: 'success', ip: '10.12.3.77', durationMs: 204 },
  { id: 5, time: '2026-10-17 19:30', operator: 'chenke', module: 'content', action: '创建会议', target: '高二数学·导数答疑', result: 'success', ip: '10.12.4.19', durationMs: 331 },
  { id: 6, time: '2026-10-17 16:35', operator: 'liyan', module: 'content', action: '文档模板生成', target: '化学实验《粗盐提纯》报告单', result: 'fail', ip: '10.12.4.62', durationMs: 5280 },
  { id: 7, time: '2026-10-17 11:04', operator: 'admin', module: 'role', action: '更新角色权限', target: '审核员', result: 'success', ip: '192.168.1.24', durationMs: 152 },
  { id: 8, time: '2026-10-17 09:02', operator: 'admin', module: 'system', action: '修改系统配置', target: '单文件上传上限', result: 'success', ip: '192.168.1.24', durationMs: 97 },
  { id: 9, time: '2026-10-16 21:48', operator: 'sunyu', module: 'auth', action: '登录', target: '-', result: 'success', ip: '10.12.5.8', durationMs: 233 },
  { id: 10, time: '2026-10-16 20:15', operator: 'wangqiang', module: 'auth', action: '登录', target: '-', result: 'fail', ip: '10.12.5.31', durationMs: 61 },
  { id: 11, time: '2026-10-16 14:12', operator: 'admin', module: 'user', action: '重置密码', target: 'hechen', result: 'success', ip: '192.168.1.24', durationMs: 74 },
  { id: 12, time: '2026-10-16 09:00', operator: 'admin', module: 'system', action: '导出操作日志', target: '2026-10 全量', result: 'success', ip: '192.168.1.24', durationMs: 1860 },
]

export const mockSystemSettings = {
  siteName: 'TrueBuddy 教学智能体工作台',
  siteDescription: '师范生教学练一体化平台',
  maxUploadMb: 200,
  allowRegister: true,
  defaultModel: 'qwen-plus',
  meetingCapacity: 60,
  maintenanceMode: false,
}

export const mockServiceHealth = {
  checkedAt: '2026-10-18 09:45',
  services: [
    { key: 'backend', name: '后端服务', status: 'healthy', latencyMs: 42, detail: 'Spring Boot · 8086', version: '1.0.0' },
    { key: 'database', name: 'PostgreSQL', status: 'healthy', latencyMs: 12, detail: '连接池 4 / 20', version: '16.4' },
    { key: 'storage', name: 'MinIO 对象存储', status: 'healthy', latencyMs: 28, detail: '已用 2.4 GB / 10 GB', version: '8.5.10' },
    { key: 'ai', name: '大模型服务', status: 'degraded', latencyMs: 1180, detail: 'qwen-plus · 偶发超时', version: '-' },
    { key: 'livekit', name: 'LiveKit 媒体服务', status: 'healthy', latencyMs: 35, detail: '活动房间 1', version: '1.8.2' },
    { key: 'docmind', name: '文档解析服务', status: 'down', latencyMs: null, detail: '上次调用失败：连接超时', version: '-' },
  ],
}

export const mockDashboardOverview = {
  stats: [
    { key: 'users', label: '注册用户', value: 1286, hint: '本周新增 38', icon: 'users', tone: 'primary' },
    { key: 'homework', label: '本周批改任务', value: 92, hint: '较上周 +12%', icon: 'grading', tone: 'success' },
    { key: 'meetings', label: '本周会议', value: 24, hint: '平均时长 38 分钟', icon: 'meeting', tone: 'warning' },
    { key: 'documents', label: '知识库文档', value: 348, hint: '待处理 6 份', icon: 'book', tone: 'info' },
  ],
  todos: [
    { id: 1, title: '6 份知识库文档等待分片确认', meta: '知识库管理 · 用户空间列表', level: 'warning' },
    { id: 2, title: '1 个文档模板任务生成失败', meta: '化学实验《粗盐提纯》报告单', level: 'danger' },
    { id: 3, title: '3 个作业批改任务待复核', meta: '高二数学·导数单元测', level: 'info' },
    { id: 4, title: '大模型服务响应偏慢', meta: '平均 1180 ms，建议检查配额', level: 'warning' },
  ],
  activities: [
    { id: 1, operator: 'admin', action: '禁用用户', target: 'xujing', time: '2026-10-18 09:12', result: 'success' },
    { id: 2, operator: 'linxiaoman', action: '创建文档模板任务', target: '八年级物理《浮力》教案', time: '2026-10-18 08:40', result: 'success' },
    { id: 3, operator: 'chenke', action: '创建会议', target: '高二数学·导数答疑', time: '2026-10-17 19:30', result: 'success' },
    { id: 4, operator: 'liyan', action: '文档模板生成失败', target: '化学实验《粗盐提纯》报告单', time: '2026-10-17 16:35', result: 'fail' },
    { id: 5, operator: 'admin', action: '更新角色权限', target: '审核员', time: '2026-10-17 11:04', result: 'success' },
  ],
  services: [
    { key: 'backend', name: '后端服务', status: 'healthy', latencyMs: 42 },
    { key: 'database', name: 'PostgreSQL', status: 'healthy', latencyMs: 12 },
    { key: 'storage', name: '对象存储', status: 'healthy', latencyMs: 28 },
    { key: 'ai', name: '大模型服务', status: 'degraded', latencyMs: 1180 },
  ],
}

/**
 * 生成一个本地查询函数：关键字模糊匹配 + 等值筛选 + 分页。
 * 数组字段（如用户的 roles）按「包含」判断，其余按字符串相等判断。
 *
 * @param {Array} source
 * @param {{ keywordFields?: string[] }} options
 * @returns {(params?: object) => { list: Array, total: number }}
 */
export function createMockQuery(source, { keywordFields = [] } = {}) {
  return (params = {}) => {
    const page = Math.max(1, Number(params.page) || 1)
    const size = Math.max(1, Number(params.size) || 10)
    const keyword = String(params.keyword || '').trim().toLowerCase()

    let list = source.slice()

    if (keyword && keywordFields.length > 0) {
      list = list.filter((item) =>
        keywordFields.some((field) => String(item[field] ?? '').toLowerCase().includes(keyword)),
      )
    }

    for (const [key, value] of Object.entries(params)) {
      if (key === 'page' || key === 'size' || key === 'keyword') continue
      if (value === '' || value === null || value === undefined) continue

      list = list.filter((item) => {
        const current = item[key]
        if (Array.isArray(current)) return current.map(String).includes(String(value))
        return String(current) === String(value)
      })
    }

    const start = (page - 1) * size
    return { list: list.slice(start, start + size), total: list.length }
  }
}

export const queryUsers = createMockQuery(mockUsers, { keywordFields: ['username', 'nickname', 'email'] })
export const queryOperationLogs = createMockQuery(mockOperationLogs, { keywordFields: ['operator', 'action', 'target', 'ip'] })
