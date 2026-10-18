import adminRequest from './index'

/** 模板资源列表：params = { page, size, keyword, category, status } */
export const listDocTemplates = (params) =>
  adminRequest({ url: '/doc-template/templates', method: 'get', params })

/** 生成记录：一次生成任务的台账（进度 / 状态 / 耗时） */
export const listDocTemplateRecords = (params) =>
  adminRequest({ url: '/doc-template/records', method: 'get', params })

/** 自检与修复问答记录 */
export const listDocTemplateInspections = (params) =>
  adminRequest({ url: '/doc-template/inspections', method: 'get', params })
