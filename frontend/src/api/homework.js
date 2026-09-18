import service from './index'

/**
 * 作业批改 API 接入层（后端接口契约，暂未接入）
 *
 * 现状：作业批改页面为**前端演示模式**，批改结果、历史记录与导出均由
 *      `views/homework/mock/grading.js` 与 `views/homework/utils/export.js`
 *      在本地模拟完成；本文件为后续接入后端时使用的接口层，当前未被引用。
 *
 * 对接后端 tb-module-homework 模块（Controller @RequestMapping("/homework")）：
 *     POST /homework/grade                      → CommonResult<GradingVO>   （multipart：config + files）
 *     GET  /homework/history?limit={n}          → CommonResult<HistoryVO[]>
 *     GET  /homework/detail?id={id}             → CommonResult<GradingVO>
 *     POST /homework/export                     → CommonResult<ExportVO>
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

// ========== 前后端共享 VO 契约 ==========
/**
 * @typedef {Object} GradeConfig
 * @property {string|number} [spaceId]      知识库空间 id（'default' 或留空表示不启用检索增强）
 * @property {string} description           批改要求（AI 描述说明，<=500 字）
 * @property {'standard'|'strict'|'feedback'|'question'} [workflow] 批改工作流
 * @property {boolean} [recognizeImage]     是否开启图片识别
 * @property {number} [concurrency]         批改并发数（后端上限 5）
 * @property {'xlsx'|'csv'} [exportFormat]  导出格式
 */

/**
 * @typedef {Object} SubmissionVO
 * @property {number} id              批改记录 id
 * @property {string} fileName        作业文件名
 * @property {string} studentName     学生姓名（无法判断时为「未知」）
 * @property {number|null} score      得分（0-100，失败时为 null）
 * @property {string} grade           等级：优秀 / 良好 / 及格 / 待改进
 * @property {string} comment         AI 评语
 * @property {string} originalText    作业原文（解析后的纯文本）
 * @property {number|null} fileId     作业原件 id（infra_file.id）
 * @property {string|null} fileUrl    原件访问地址（预签名 URL）
 * @property {number|null} size       文件大小（字节）
 * @property {number} status          单份状态：1=成功，3=失败
 * @property {string} errorMsg        单份失败原因（成功时为空）
 */

/**
 * @typedef {Object} GradingVO
 * @property {number} taskId         批改任务 id
 * @property {number} status         任务状态：1=成功，2=部分成功，3=失败
 * @property {number} durationMs     任务耗时（毫秒）
 * @property {string} errorMsg       失败/部分失败的错误摘要
 * @property {string} description    本次批改要求
 * @property {string} workflow       批改工作流
 * @property {'xlsx'|'csv'} exportFormat 导出格式
 * @property {number} fileCount      提交的作业份数
 * @property {SubmissionVO[]} records 逐份批改结果
 */

/**
 * @typedef {Object} HistoryVO
 * @property {number} id             批改任务 id
 * @property {string} title          标题（批改要求摘要）
 * @property {string} kb             知识库名称
 * @property {string} workflow       工作流文案（标准批改 / 严格评分 / 仅反馈 / 逐题精批）
 * @property {string} time           批改时间（yyyy-MM-dd HH:mm）
 * @property {string} status         状态文案：成功 / 部分成功 / 失败
 * @property {number} recordCount    批改份数
 */

/**
 * @typedef {Object} ExportVO
 * @property {string} name           文件名（含扩展名）
 * @property {string} url            下载地址（MinIO 预签名 URL）
 * @property {number} size           文件大小（字节）
 */

// ========== 接口定义 ==========

/**
 * 批量批改：一次提交整个作业文件夹
 * POST /homework/grade（multipart/form-data）
 * @param {GradeConfig} config 批改配置
 * @param {File[]} files 作业文件列表
 * @param {(event: ProgressEvent) => void} [onUploadProgress] 上传进度回调
 * @returns {Promise<GradingVO>}
 */
export function gradeHomework(config, files, onUploadProgress) {
  const formData = new FormData()
  formData.append('config', JSON.stringify(config))
  files.forEach((file) => formData.append('files', file))
  return service({
    url: '/homework/grade',
    method: 'post',
    data: formData,
    // 整批批改含解析 + 多次大模型调用，耗时较长，不做超时限制
    timeout: 0,
    onUploadProgress,
  })
}

/**
 * 批改历史记录（按时间倒序）
 * GET /homework/history?limit={limit}
 * @param {number} [limit]
 * @returns {Promise<HistoryVO[]>}
 */
export function getGradingHistory(limit) {
  return service({
    url: '/homework/history',
    method: 'get',
    params: limit ? { limit } : {},
  })
}

/**
 * 批改详情（任务 + 逐份结果）
 * GET /homework/detail?id={id}
 * @param {number} id 批改任务 id
 * @returns {Promise<GradingVO>}
 */
export function getGradingDetail(id) {
  return service({ url: '/homework/detail', method: 'get', params: { id } })
}

/**
 * 导出成绩汇总表（Excel / CSV）
 * POST /homework/export
 * @param {number} taskId 批改任务 id
 * @param {'xlsx'|'csv'} format 导出格式
 * @returns {Promise<ExportVO>}
 */
export function exportGradingResult(taskId, format) {
  return service({ url: '/homework/export', method: 'post', data: { taskId, format } })
}
