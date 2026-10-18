import adminRequest from './index'

/** 批改资源列表：评分标准 / 提示词 / 批改工作流 */
export const listHomeworkResources = (params) =>
  adminRequest({ url: '/homework/resources', method: 'get', params })

/** 批改记录：作业批改任务与平均分 */
export const listHomeworkRecords = (params) =>
  adminRequest({ url: '/homework/records', method: 'get', params })
