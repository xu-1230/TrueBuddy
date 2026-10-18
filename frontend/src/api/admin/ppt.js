import adminRequest from './index'

/* ---------------- 数字人形象配置 ---------------- */

export const getAvatarConfig = () => adminRequest({ url: '/ppt/avatar', method: 'get' })

export const saveAvatarConfig = (data) => adminRequest({ url: '/ppt/avatar', method: 'put', data })

/* ---------------- 课件资源 ---------------- */

export const listSlideResources = (params) =>
  adminRequest({ url: '/ppt/slides', method: 'get', params })

/* ---------------- 会议记录 ---------------- */

/** 会议记录：只读台账，实时在线人数仍以 LiveKit 房间事件为准（docs/adr/0002） */
export const listPptMeetings = (params) =>
  adminRequest({ url: '/ppt/meetings', method: 'get', params })

/* ---------------- 课堂统计 ---------------- */

export const getClassStats = () => adminRequest({ url: '/ppt/stats/class', method: 'get' })

export const getInteractionStats = () =>
  adminRequest({ url: '/ppt/stats/interaction', method: 'get' })
