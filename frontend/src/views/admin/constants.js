/**
 * 管理端常量与字典
 *
 * 状态码尽量与后端 / 数据库保持同一口径（user.status 0 正常 1 禁用、
 * kb_document.status 0~7、meeting.status 1 进行中 2 已结束），
 * 汉字只在 META 里出现一次，页面不各写一份。
 */

export const PAGE_SIZES = [10, 20, 50]
export const DEFAULT_PAGE_SIZE = 10

/* ---------------- 用户 ---------------- */

export const USER_STATUS = { ENABLED: 0, DISABLED: 1 }

export const USER_STATUS_META = {
  0: { label: '正常', tone: 'success' },
  1: { label: '禁用', tone: 'danger' },
}

export const USER_STATUS_OPTIONS = [
  { value: USER_STATUS.ENABLED, label: '正常' },
  { value: USER_STATUS.DISABLED, label: '禁用' },
]

/* ---------------- 角色 ---------------- */

export const ROLE_META = {
  normal: { label: '普通用户', tone: 'info' },
  member: { label: '会员', tone: 'warning' },
  admin: { label: '管理员', tone: 'danger' },
  auditor: { label: '审核员', tone: 'primary' },
}

export const ROLE_OPTIONS = Object.entries(ROLE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

/* ---------------- 菜单 ---------------- */

export const MENU_TYPE = { DIR: 1, MENU: 2, BUTTON: 3 }

export const MENU_TYPE_META = {
  1: { label: '目录', tone: 'info' },
  2: { label: '菜单', tone: 'success' },
  3: { label: '按钮', tone: 'warning' },
}

export const MENU_TYPE_OPTIONS = [
  { value: MENU_TYPE.DIR, label: '目录' },
  { value: MENU_TYPE.MENU, label: '菜单' },
  { value: MENU_TYPE.BUTTON, label: '按钮' },
]

/* ---------------- 任务（文档模板 / 作业批改共用） ---------------- */

export const TASK_STATUS_META = {
  pending: { label: '待处理', tone: 'info' },
  running: { label: '处理中', tone: 'primary' },
  success: { label: '已完成', tone: 'success' },
  failed: { label: '失败', tone: 'danger' },
  cancelled: { label: '已取消', tone: 'info' },
}

/* ---------------- 知识库文档 ---------------- */

export const KB_DOC_STATUS_META = {
  0: { label: '待处理', tone: 'info' },
  1: { label: '解析中', tone: 'primary' },
  2: { label: '已完成', tone: 'success' },
  3: { label: '失败', tone: 'danger' },
  4: { label: '分片待确认', tone: 'warning' },
  5: { label: '待分片', tone: 'info' },
  6: { label: 'AI 分片中', tone: 'primary' },
  7: { label: '向量化中', tone: 'primary' },
}

/* ---------------- 会议 ---------------- */

export const MEETING_STATUS_META = {
  1: { label: '进行中', tone: 'success' },
  2: { label: '已结束', tone: 'info' },
}

/* ---------------- 工作流 ---------------- */

export const WORKFLOW_STATUS_META = {
  draft: { label: '草稿', tone: 'info' },
  published: { label: '已发布', tone: 'success' },
  disabled: { label: '已停用', tone: 'warning' },
}

/* ---------------- 操作日志 ---------------- */

export const LOG_RESULT_META = {
  success: { label: '成功', tone: 'success' },
  fail: { label: '失败', tone: 'danger' },
}

export const LOG_MODULE_OPTIONS = [
  { value: 'auth', label: '登录认证' },
  { value: 'user', label: '用户管理' },
  { value: 'role', label: '角色权限' },
  { value: 'content', label: '内容管理' },
  { value: 'system', label: '系统配置' },
]

/* ---------------- 服务健康 ---------------- */

export const HEALTH_STATUS_META = {
  healthy: { label: '正常', tone: 'success' },
  degraded: { label: '降级', tone: 'warning' },
  down: { label: '不可用', tone: 'danger' },
}

/* ---------------- 资源类（模板 / 课件 / 批改资源） ---------------- */

export const RESOURCE_STATUS_META = {
  ready: { label: '可用', tone: 'success' },
  enabled: { label: '启用', tone: 'success' },
  disabled: { label: '停用', tone: 'info' },
  draft: { label: '草稿', tone: 'info' },
  processing: { label: '处理中', tone: 'primary' },
  failed: { label: '失败', tone: 'danger' },
}

export const TEMPLATE_CATEGORY_OPTIONS = [
  { value: 'lesson-plan', label: '教案' },
  { value: 'study-plan', label: '学案' },
  { value: 'lecture', label: '说课稿' },
  { value: 'lab-report', label: '实验报告' },
  { value: 'teaching-design', label: '教学设计' },
]

export const HOMEWORK_RESOURCE_TYPE_OPTIONS = [
  { value: 'rubric', label: '评分标准' },
  { value: 'prompt', label: '提示词' },
  { value: 'workflow', label: '批改工作流' },
]

/* ---------------- 文档模板自检 ---------------- */

export const INSPECTION_RESULT_META = {
  fixed: { label: '已修复', tone: 'success' },
  pending: { label: '待处理', tone: 'warning' },
  ignored: { label: '已忽略', tone: 'info' },
}

/* ---------------- 知识库空间 ---------------- */

export const SPACE_VISIBILITY_META = {
  private: { label: '私有', tone: 'info' },
  team: { label: '团队', tone: 'primary' },
  public: { label: '公开', tone: 'success' },
}

export const SPACE_STATUS_META = {
  active: { label: '正常', tone: 'success' },
  archived: { label: '已归档', tone: 'info' },
}

/* ---------------- 工作流运行 / 异常 ---------------- */

export const RUN_STATUS_META = {
  running: { label: '运行中', tone: 'primary' },
  success: { label: '成功', tone: 'success' },
  failed: { label: '失败', tone: 'danger' },
  cancelled: { label: '已取消', tone: 'info' },
}

export const EXCEPTION_STATUS_META = {
  open: { label: '待处理', tone: 'danger' },
  resolved: { label: '已解决', tone: 'success' },
  ignored: { label: '已忽略', tone: 'info' },
}

export const CONVERSATION_STATUS_META = {
  active: { label: '进行中', tone: 'primary' },
  completed: { label: '已完成', tone: 'success' },
  failed: { label: '失败', tone: 'danger' },
}

export const NODE_CATEGORY_OPTIONS = [
  { value: 'input', label: '输入输出' },
  { value: 'llm', label: '大模型' },
  { value: 'logic', label: '业务逻辑' },
  { value: 'data', label: '数据库' },
  { value: 'knowledge', label: '知识库' },
]

