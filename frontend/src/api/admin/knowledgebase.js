import adminRequest from './index'

/** 用户知识库空间列表（管理员视角） */
export const listKnowledgeSpaces = (params) =>
  adminRequest({ url: '/knowledge-base/spaces', method: 'get', params })

/** 知识库创建统计：按时间维度的空间 / 文档新增量 */
export const getKnowledgeCreationStats = () =>
  adminRequest({ url: '/knowledge-base/stats/creation', method: 'get' })

/** 存储用量统计：按空间与文件类型拆分 */
export const getKnowledgeStorageStats = () =>
  adminRequest({ url: '/knowledge-base/stats/storage', method: 'get' })
