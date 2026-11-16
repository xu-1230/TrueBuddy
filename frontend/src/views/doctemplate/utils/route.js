/**
 * 模板生成页的 URL 契约（URL 是页面状态的唯一真相源）。
 *
 *     /index/doc-template                       基础生成页
 *     /index/doc-template/:generationId         某次生成的结果列表
 *     /index/doc-template/:generationId?file=   查看该次生成中的某份文档
 *
 * generationId 取历史条目 id（= 后端 generationId）；
 * file 取 DocFileVO.id（doc_template_file 主键），不是 infra_file.id。
 */

/** 页面基础路径 */
export const DOC_TEMPLATE_PATH = '/index/doc-template'
/** 路由 name（与 user.routes.js 保持一致） */
export const DOC_TEMPLATE_ROUTE_NAME = 'doc-template'

function isAbsent(value) {
  return value === undefined || value === null || value === ''
}

/**
 * 正整数 id 校验：接受 number 或纯数字字符串，其余一律视为非法。
 * 返回规范化后的字符串（便于与 query 里的字符串直接比较），非法返回 null。
 */
export function normalizePositiveId(value) {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : null
  }
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const num = Number(trimmed)
  return Number.isSafeInteger(num) && num > 0 ? String(num) : null
}

/**
 * 解析当前路由状态。
 *
 * @param {{ id?: string }} [params] 路由 params
 * @param {{ file?: string | string[] }} [query] 路由 query
 * @returns {{
 *   generationId: string | null,
 *   fileId: string | null,
 *   hasInvalidId: boolean,
 *   hasInvalidFile: boolean,
 *   hasFileParam: boolean,
 * }} hasInvalidId / hasInvalidFile 用于区分「参数非法」与「未携带」；
 *    hasFileParam 用于发现「有 file 无 id」的畸形地址。
 */
export function parseDocTemplateRoute(params = {}, query = {}) {
  const rawId = params?.id
  const generationId = normalizePositiveId(rawId)
  const hasInvalidId = !isAbsent(rawId) && generationId === null

  // 重复的 ?file=1&file=2 取第一个（本页只会生成单值）
  const rawFile = Array.isArray(query?.file) ? query.file[0] : query?.file
  const hasFileParam = !isAbsent(rawFile)
  const fileId = generationId ? normalizePositiveId(rawFile) : null
  const hasInvalidFile = Boolean(generationId) && hasFileParam && fileId === null

  return { generationId, fileId, hasInvalidId, hasInvalidFile, hasFileParam }
}

/**
 * 构建规范 URL 的 location 对象（交给 router.push / router.replace）。
 * 非法 id / file 会被丢弃：无 id 时 file 失去归属，因此一并丢弃。
 */
export function docTemplateLocation(generationId, fileId) {
  const id = normalizePositiveId(generationId)
  const file = id ? normalizePositiveId(fileId) : null
  return {
    name: DOC_TEMPLATE_ROUTE_NAME,
    params: id ? { id } : {},
    query: file ? { file } : {},
  }
}
