/**
 * 知识库页的 URL 契约（URL 是「当前打开哪个空间」的唯一真相源）。
 *
 *     /index/knowledge-base                 空间列表（我的空间 + 公有库）
 *     /index/knowledge-base?space=<id>      某个空间的文档列表
 *
 * space 取 KbSpaceVO.id；只读与否由 id 落在「我的空间」还是「公有库」决定，
 * 文档选择与上传页是过程态，不进 URL。
 */

/** 路由 name（与 user.routes.js 保持一致） */
export const KNOWLEDGE_BASE_ROUTE_NAME = 'knowledge-base'

function isAbsent(value) {
  return value === undefined || value === null || value === ''
}

/**
 * 正整数 id 校验：接受 number 或纯数字字符串，其余一律视为非法。
 * 返回 number（便于与接口返回的空间 id 直接比较），非法返回 null。
 */
export function normalizeSpaceId(value) {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? value : null
  }
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  if (!/^\d+$/.test(trimmed)) return null
  const num = Number(trimmed)
  return Number.isSafeInteger(num) && num > 0 ? num : null
}

/**
 * 解析当前路由状态。
 *
 * @param {{ space?: string | string[] }} [query] 路由 query
 * @returns {{ spaceId: number | null, hasInvalidSpace: boolean }}
 *   hasInvalidSpace 用于区分「参数非法」与「未携带」：非法地址回退列表并提示。
 */
export function parseKnowledgeBaseRoute(query = {}) {
  // 重复的 ?space=1&space=2 取第一个（本页只会生成单值）
  const raw = Array.isArray(query?.space) ? query.space[0] : query?.space
  const spaceId = normalizeSpaceId(raw)
  return {
    spaceId,
    hasInvalidSpace: !isAbsent(raw) && spaceId === null,
  }
}

/**
 * 构建规范 URL 的 location 对象（交给 router.push / router.replace）。
 * 无 id（或 id 非法）时回到空间列表，并丢弃其它 query。
 */
export function knowledgeBaseLocation(spaceId) {
  const id = normalizeSpaceId(spaceId)
  return {
    name: KNOWLEDGE_BASE_ROUTE_NAME,
    query: id === null ? {} : { space: String(id) },
  }
}
