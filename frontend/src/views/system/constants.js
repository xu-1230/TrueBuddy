/**
 * 首页模块静态配置 —— 运营位、可用工具、标签页与资源类型元数据。
 *
 * 颜色与图标在此集中声明，组件内不再重复硬编码（docs/09-样式规范.md §3.2 第 1 条）。
 * `pattern` 是封面版式的类名后缀，取值须与 `ShowcaseCard.vue` 里的 `.art--*` 一一对应。
 */
import { ChatDotRound, Connection, Document, EditPen, Reading } from '@element-plus/icons-vue'

/** Zone A1 主推卡 */
export const PROMO_MAIN = {
  id: 'ppt',
  eyebrow: '创作中心',
  title: 'PPT 演示',
  description: '导入 .pptx，一键放映与演讲备注',
  to: '/index/ppt',
  pattern: 'slide',
}

/**
 * Zone A2 双入口卡（pill 是单行省略号，文案要短到能在窄栏里读全）。
 * `image` 是与 Zone A3 同一套渲染风格的横幅位图（public/hero/），
 * 供卡片做「位图打底 + 品牌染色」的背景，构图统一「左侧留白 + 右侧主体」。
 */
export const PROMO_ENTRIES = [
  {
    id: 'doc-template',
    title: '文档模板生成',
    description: '一键复刻多份',
    to: '/index/doc-template',
    icon: Document,
    pattern: 'page',
    image: '/hero/hero-docgen.webp',
  },
  {
    id: 'homework',
    title: '作业批改',
    description: '整包上传自动评分',
    to: '/index/homework-grading',
    icon: EditPen,
    pattern: 'grid',
    image: '/hero/hero-homework.webp',
  },
]

/**
 * Zone A3 运营轮播。
 * `image` 是 1400×622 的横幅位图（public/hero/），构图统一「左侧留白 + 右侧主体」，
 * 供文字压左侧；三张同源自同一套渲染风格，换图时请保持同一湿度与光源方向。
 */
export const CAROUSEL_SLIDES = [
  {
    id: 'roadmap',
    eyebrow: '即将上线',
    title: 'AI 数字人演讲台',
    description: 'Live2D 形象 + 讲稿驱动，把幻灯片变成一次出镜演讲',
    cta: '查看方案',
    to: '/docs',
    image: '/hero/hero-speaker.webp',
  },
  {
    id: 'meeting',
    eyebrow: '创作工具',
    title: '模拟会议',
    description: '实时音视频 + 屏幕共享 + 会议内问答',
    cta: '进入控制台',
    to: '/index/meeting',
    image: '/hero/hero-meeting.webp',
  },
  {
    id: 'guide',
    eyebrow: '上手',
    title: '使用指南与接口文档',
    description: '七个模块的完整操作流程',
    cta: '阅读文档',
    to: '/guides',
    image: '/hero/hero-guide.webp',
  },
]

/** 轮播自动播放间隔（ms） */
export const CAROUSEL_INTERVAL = 6000

/**
 * Zone B 可用工具。
 * 只收「有真实列表接口可统计」的两个模块：文档模板 / PPT / 会议 / 批改已由 Zone A 的
 * 主推卡、双入口卡与运营轮播承载，在这里再列一遍只是重复占位。
 * `source` 声明该卡统计哪一类真实资产，null 表示后端暂无列表接口（不编造数字）。
 */
export const MODULE_CARDS = [
  {
    id: 'knowledge',
    title: '知识库',
    description: '文档解析、分片与向量检索',
    to: '/index/knowledge-base',
    icon: Reading,
    pattern: 'stack',
    source: 'knowledge',
    unit: '空间',
    image: '/hero/hero-knowledge.webp',
  },
  {
    id: 'workflow',
    title: '工作流编排',
    description: '节点画布与逐节点运行态',
    to: '/index/workflow',
    icon: Connection,
    pattern: 'node',
    source: 'workflow',
    unit: '条',
    image: '/hero/hero-workflow.webp',
  },
]

/** Zone C 标签页：过滤 Zone D 的资源类型 */
export const HOME_TABS = [
  { key: 'all', label: '综合' },
  { key: 'knowledge', label: '知识库' },
  { key: 'workflow', label: '工作流' },
  { key: 'doc', label: '文档生成' },
  { key: 'chat', label: '会话' },
]

/** Zone C 排序项 */
export const HOME_SORTS = [
  { key: 'recommended', label: '推荐' },
  { key: 'updated', label: '最近更新' },
  { key: 'created', label: '最近创建' },
]

/** Zone D 资源类型元数据 */
export const KIND_META = {
  knowledge: { label: '知识库空间', pattern: 'stack', icon: Reading },
  workflow: { label: '工作流', pattern: 'node', icon: Connection },
  doc: { label: '文档生成', pattern: 'page', icon: Document },
  chat: { label: '会话', pattern: 'bubble', icon: ChatDotRound },
}
