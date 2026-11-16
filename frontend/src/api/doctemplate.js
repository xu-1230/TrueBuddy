import service from './index'

/**
 * 文档模板生成 API 接入层
 *
 * 对接后端 tb-module-doctemplate 模块（Controller @RequestMapping("/doc-template")）：
 *     POST /doc-template/generate              → CommonResult<GenerationVO>
 *     GET  /doc-template/history?limit={n}     → CommonResult<HistoryVO[]>
 *     GET  /doc-template/detail?id={id}        → CommonResult<GenerationVO>
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

// ========== 前后端共享 VO 契约 ==========
/**
 * @typedef {Object} DocFileVO
 * @property {number} id          结果记录 id（doc_template_file 主键）
 * @property {number} fileId      文件 id（infra_file 主键）
 * @property {string} name        结果文件名（含扩展名）
 * @property {string} type        文档类型：WORD / PDF / MARKDOWN
 * @property {number} size        文件大小（字节）
 * @property {string} tag         份数定位标签：复刻 / 优化 / 精简…
 * @property {string} summary     内容摘要
 * @property {string} src         访问地址（MinIO 预签名 URL；历史接口内联时为 null）
 */

/**
 * @typedef {Object} GenerationVO
 * @property {number} generationId  生成任务 id
 * @property {number} status        任务状态：1=成功，2=部分成功，3=失败
 * @property {number} durationMs    任务耗时（毫秒）
 * @property {string} errorMsg      错误摘要（成功时为空）
 * @property {DocFileVO[]} docs     生成文件列表
 */

/**
 * @typedef {Object} HistoryVO
 * @property {number} id          生成任务 id
 * @property {string} title       标题（描述摘要）
 * @property {string} kb          知识库名称
 * @property {string} type        类型（模板生成）
 * @property {string} time        生成时间（yyyy-MM-dd HH:mm）
 * @property {string} status      状态文案：成功 / 部分成功 / 失败
 * @property {number} docCount    产出文档数量
 * @property {DocFileVO[]} docs   本次任务产出的文件摘要（src 为 null；首页「文档」tab 用 id + generationId 拼深链接）
 */

/**
 * 生成文档（以模板为基底复刻多份同版式文档）
 * POST /doc-template/generate
 * @param {{ templateFileId: number, spaceId?: string, description: string, quality?: string }} data
 * @returns {Promise<GenerationVO>}
 */
export function generateDoc(data) {
  return service({ url: '/doc-template/generate', method: 'post', data })
}

/**
 * 获取生成历史记录（按时间倒序）
 * GET /doc-template/history?limit={limit}
 * 每条记录内联 docs（文件摘要，不签发 MinIO URL）；首页据此展开文件卡。
 * @param {number} [limit] 任务条数（不是文件条数）
 * @returns {Promise<HistoryVO[]>}
 */
export function getGenerationHistory(limit) {
  return service({
    url: '/doc-template/history',
    method: 'get',
    params: limit ? { limit } : {},
  })
}

/**
 * 获取生成详情（任务 + 文件列表）
 * GET /doc-template/detail?id={id}
 * @param {number} id
 * @returns {Promise<GenerationVO>}
 */
export function getGenerationDetail(id) {
  return service({ url: '/doc-template/detail', method: 'get', params: { id } })
}
