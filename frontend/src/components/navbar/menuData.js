/**
 * 顶部导航菜单数据（占位骨架）
 *
 * 说明：本轮只搭建 Mega 菜单的结构与动效，栏目名 / 子项文案与路由均为占位，
 * 后续替换时只需改本文件，组件无需改动。
 *
 * 数据约定：
 *   MENU_TABS : [{ id, label }]                 —— 一级项，id 用于与 MENUS 对应
 *   MENUS     : { [id]: [{ title, items }] }    —— 每个一级项下方的多列网格
 *   items     : [{ label, to }]                 —— to 为 RouterLink 的目标路由
 */

// TODO: 占位栏目，待替换为最终导航结构
export const MENU_TABS = [
  { id: 'capability', label: '产品能力' },
  { id: 'flow', label: '教学流程' },
  { id: 'resource', label: '资源中心' },
  { id: 'about', label: '关于' },
]

// TODO: 占位内容（文案与路由均待确认）
export const MENUS = {
  capability: [
    {
      title: '内容生成',
      items: [
        { label: '文档模板生成', to: '/index/doc-template' },
        { label: 'PPT 演示', to: '/index/ppt' },
        { label: '教学知识库', to: '/index/knowledge-base' },
      ],
    },
    {
      title: '教学辅助',
      items: [
        { label: '作业智能批改', to: '/index/homework-grading' },
        { label: '工作流编排', to: '/index/workflow' },
        { label: '系统首页', to: '/index/home' },
      ],
    },
    {
      title: '平台服务',
      items: [
        { label: '快速开始', to: '/guides' },
        { label: '登录账号', to: '/login' },
        { label: '注册账号', to: '/register' },
      ],
    },
  ],
  flow: [
    {
      title: '课前准备',
      items: [
        { label: '教案生成', to: '/index/doc-template' },
        { label: '课件制作', to: '/index/ppt' },
        { label: '素材检索', to: '/docs' },
      ],
    },
    {
      title: '课中执行',
      items: [
        { label: '课堂演示', to: '/index/ppt' },
        { label: '互动问答', to: '/docs' },
        { label: '流程编排', to: '/index/workflow' },
      ],
    },
    {
      title: '课后反馈',
      items: [
        { label: '作业批改', to: '/index/homework-grading' },
        { label: '学情分析', to: '/docs' },
        { label: '成长档案', to: '/docs' },
      ],
    },
  ],
  resource: [
    {
      title: '使用文档',
      items: [
        { label: '产品手册', to: '/docs' },
        { label: '接入指南', to: '/docs' },
        { label: '常见问题', to: '/docs' },
      ],
    },
    {
      title: '示例资源',
      items: [
        { label: '教案样例', to: '/docs' },
        { label: '课件样例', to: '/docs' },
        { label: '批改样例', to: '/docs' },
      ],
    },
    {
      title: '更新与支持',
      items: [
        { label: '版本更新日志', to: '/docs' },
        { label: '反馈问题', to: '/docs' },
        { label: '联系我们', to: '/docs' },
      ],
    },
  ],
  about: [
    {
      title: '项目介绍',
      items: [
        { label: '平台愿景', to: '/guides' },
        { label: '技术架构', to: '/docs' },
        { label: '功能总览', to: '/guides' },
      ],
    },
    {
      title: '团队',
      items: [
        { label: '研发团队', to: '/docs' },
        { label: '指导老师', to: '/docs' },
        { label: '加入我们', to: '/docs' },
      ],
    },
    {
      title: '联系',
      items: [
        { label: '邮件联系', to: '/docs' },
        { label: '问题反馈', to: '/docs' },
        { label: '合作洽谈', to: '/docs' },
      ],
    },
  ],
}
