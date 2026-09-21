/**
 * 知识库文档处理状态常量与展示映射。
 *
 * 与后端 kb_document.status 保持一致：
 *   0=待处理、1=解析中、2=已完成（已向量化）、3=失败、
 *   4=分片待确认、5=解析完成待分片、6=AI分片中、7=向量化中
 *
 * 说明：解析完成仅代表分片就绪，需用户在第二步确认分片并完成第三步向量化后，
 * 文档状态才置为「已完成」，因此列表可明确区分「待确认」这一中间态。
 */

/** 文档处理状态 */
export const DOC_STATUS = {
  /** 待处理：已登记元数据，尚未提交解析 */
  PENDING: 0,
  /** 解析中：解析任务已提交，等待引擎产出 */
  PARSING: 1,
  /** 已完成：分片已确认并写入向量库 */
  COMPLETED: 2,
  /** 失败：解析、分片或向量化失败 */
  FAILED: 3,
  /** 分片待确认：分片已落库，等待用户确认后向量化 */
  PENDING_CONFIRM: 4,
  /** 解析完成待分片：解析产物已落库，等待 AI 语义分片 */
  PARSE_DONE: 5,
  /** AI 分片中：大模型正在按语义划分分片 */
  CHUNKING: 6,
  /** 向量化中：正在写入向量库 */
  VECTORIZING: 7,
}

/** 解析阶段可终止轮询的状态集合 */
export const PARSE_SETTLED_STATUS = [DOC_STATUS.COMPLETED, DOC_STATUS.PENDING_CONFIRM]

/** 处理中状态集合：仍处于流水线上，轮询需继续 */
export const IN_PROGRESS_STATUS = [
  DOC_STATUS.PENDING,
  DOC_STATUS.PARSING,
  DOC_STATUS.PARSE_DONE,
  DOC_STATUS.CHUNKING,
]

/** 状态展示元数据：文案 / el-tag 类型 / 悬浮提示 */
const DOC_STATUS_META = {
  [DOC_STATUS.PENDING]: { label: '待处理', tagType: 'info', tip: '文档已登记，尚未提交解析' },
  [DOC_STATUS.PARSING]: { label: '解析中', tagType: 'warning', tip: '正在解析文档内容，请稍候' },
  [DOC_STATUS.PARSE_DONE]: {
    label: '待分片',
    tagType: 'warning',
    tip: '解析产物已就绪，正在准备 AI 语义分片',
  },
  [DOC_STATUS.CHUNKING]: {
    label: 'AI分片中',
    tagType: 'warning',
    tip: '大模型正在按语义划分分片，请稍候',
  },
  [DOC_STATUS.PENDING_CONFIRM]: { label: '待确认', tagType: 'warning', tip: '分片已就绪，待确认后向量化' },
  [DOC_STATUS.VECTORIZING]: { label: '向量化中', tagType: 'warning', tip: '正在写入向量库' },
  [DOC_STATUS.COMPLETED]: { label: '已完成', tagType: 'success', tip: '已完成向量化，可被知识库检索' },
  [DOC_STATUS.FAILED]: { label: '失败', tagType: 'danger', tip: '处理失败，请重新上传' },
}

/**
 * 获取状态展示元数据
 * @param {number} status 后端返回的文档状态
 * @returns {{label: string, tagType: string, tip: string}}
 */
export function docStatusMeta(status) {
  return DOC_STATUS_META[status] ?? { label: '未知', tagType: 'info', tip: '' }
}
