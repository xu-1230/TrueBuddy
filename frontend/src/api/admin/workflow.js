import adminRequest from './index'

/** 工作流列表 */
export const listWorkflows = (params) => adminRequest({ url: '/workflow/list', method: 'get', params })

/** 节点模板维护：可复用节点的定义 */
export const listNodeTemplates = (params) =>
  adminRequest({ url: '/workflow/node-templates', method: 'get', params })

/** 运行记录查询 */
export const listWorkflowRuns = (params) =>
  adminRequest({ url: '/workflow/runs', method: 'get', params })

/** 执行异常排查 */
export const listWorkflowExceptions = (params) =>
  adminRequest({ url: '/workflow/exceptions', method: 'get', params })

/** 标记异常已处理 */
export const resolveWorkflowException = (id) =>
  adminRequest({ url: '/workflow/exceptions/resolve', method: 'put', data: { id } })

/** 会话记录查看 */
export const listWorkflowConversations = (params) =>
  adminRequest({ url: '/workflow/conversations', method: 'get', params })
