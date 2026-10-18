import adminRequest from './index'

/** 概览页统计与动态 */
export const getDashboardOverview = () => adminRequest({ url: '/dashboard/overview', method: 'get' })

/** 概览页「最近操作」列表 */
export const getDashboardActivities = (params) =>
  adminRequest({ url: '/dashboard/activities', method: 'get', params })
