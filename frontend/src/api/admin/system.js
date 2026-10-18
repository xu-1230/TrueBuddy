import adminRequest from './index'

/* ---------------- 系统配置 ---------------- */

export const getSystemSettings = () => adminRequest({ url: '/system/settings', method: 'get' })

export const saveSystemSettings = (data) =>
  adminRequest({ url: '/system/settings', method: 'put', data })

/* ---------------- 操作日志 ---------------- */

export const listOperationLogs = (params) =>
  adminRequest({ url: '/system/logs', method: 'get', params })

/* ---------------- 服务健康 ---------------- */

export const getServiceHealth = () => adminRequest({ url: '/system/health', method: 'get' })
