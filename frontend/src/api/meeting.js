/**
 * 模拟会议接口层。
 *
 * 会议内身份不依赖会话级 Token：老师用 `hostToken`（创建时一次性返回），
 * 学生用 `attendeeToken`（入会时下发）。实时媒体与聊天走 LiveKit
 * （见 `@/views/meeting/hooks/useLiveKitRoom`），本文件只负责 REST 部分。
 */
import service from './index'

/** 会议 REST 接口路径（不带 `/api` 前缀：axios 实例的 baseURL 已经是 `/api`） */
export const MEETING_REST_API = {
  /** 创建会议：{ title } → { meetingId, meetingNo, joinUrl, hostToken } */
  create: '/meeting/create',
  /** 学生入会：{ meetingNo, nickname } → { attendeeId, attendeeToken, meeting, attendees, messages } */
  join: '/meeting/join',
  /** 会议详情（query：meetingNo + hostToken） */
  detail: '/meeting/detail',
  /** 学生侧会议状态（query：meetingNo + attendeeToken），用于刷新与断线重连后补齐数据 */
  state: '/meeting/state',
  /** 消息落库：{ meetingNo, attendeeToken | hostToken, content }（实时走 LiveKit DataChannel，本接口只写历史） */
  message: '/meeting/message',
  /** 结束会议：{ meetingNo, hostToken } */
  close: '/meeting/close',
}

/**
 * 创建会议。
 *
 * @param {string} title 会议标题
 * @returns {Promise<{ meetingId: number, meetingNo: string, joinUrl: string, hostToken: string }>}
 */
export function createMeeting(title) {
  return service.post(MEETING_REST_API.create, { title })
}

/**
 * 学生入会。
 *
 * @param {string} meetingNo 会议号（6 位数字）
 * @param {string} nickname 入会弹窗输入的名字
 * @returns {Promise<object>} 参会凭据与会议初始数据
 */
export function joinMeeting(meetingNo, nickname) {
  return service.post(MEETING_REST_API.join, { meetingNo, nickname })
}

/**
 * 会议详情（老师控制台刷新后恢复用）。
 *
 * @param {string} meetingNo 会议号
 * @param {string} hostToken 主持人凭据
 * @returns {Promise<object>} { meeting, attendees, messages }
 */
export function getMeetingDetail(meetingNo, hostToken) {
  return service.get(MEETING_REST_API.detail, { params: { meetingNo, hostToken } })
}

/**
 * 消息落库（聊天的「双写」之一：实时走 LiveKit DataChannel，历史走这里）。
 *
 * @param {{ meetingNo: string, content: string, attendeeToken?: string, hostToken?: string }} body
 * @returns {Promise<object>} 已落库的消息（含数据库 id 与创建时间）
 */
export function sendMeetingMessage(body) {
  return service.post(MEETING_REST_API.message, body)
}

/**
 * 学生侧会议状态：刷新页面或断线重连后补齐成员与消息。
 *
 * @param {string} meetingNo 会议号
 * @param {string} attendeeToken 参会凭据
 * @returns {Promise<object>} { meeting, attendees, messages }
 */
export function getMeetingState(meetingNo, attendeeToken) {
  return service.get(MEETING_REST_API.state, { params: { meetingNo, attendeeToken } })
}

/**
 * 结束会议。
 *
 * @param {string} meetingNo 会议号
 * @param {string} hostToken 主持人凭据
 * @returns {Promise<boolean>}
 */
export function closeMeeting(meetingNo, hostToken) {
  return service.post(MEETING_REST_API.close, { meetingNo, hostToken })
}
