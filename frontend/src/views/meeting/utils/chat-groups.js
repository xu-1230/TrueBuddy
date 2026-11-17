/**
 * 会议聊天的连续消息分组（纯函数）。
 *
 * 同一发送者在 `gapMs` 内连续发言合并为一组：组首渲染头像/姓名/角色/时间，
 * 后续气泡只渲染正文。发送者身份优先用 `attendeeId`；老师 / 数字人等后端
 * 不带 attendeeId 的消息按 `role + kind + nickname` 归一。
 */

/** 连续同人消息的最大间隔：超过则新起一组 */
export const CHAT_GROUP_GAP_MS = 5 * 60 * 1000

function toTime(value) {
  if (!value) return null
  const time = new Date(value).getTime()
  return Number.isFinite(time) ? time : null
}

/**
 * 发送者标识：自己的消息统一为 `self`；有 attendeeId 用 id；否则用
 * `role:kind:nickname`（老师发言 attendeeId 为 null，数字人 role=3 / kind=2）。
 *
 * @param {object} message
 * @returns {string}
 */
export function senderKeyOf(message) {
  if (!message) return ''
  if (message.self === true) return 'self'
  if (message.attendeeId !== null && message.attendeeId !== undefined) {
    return `attendee:${message.attendeeId}`
  }
  return `role:${message.role ?? ''}:${message.kind ?? ''}:${message.nickname ?? ''}`
}

/**
 * 把消息列表展开为带分组标记的扁平数组（保留原顺序，便于 TransitionGroup 逐条渲染）。
 *
 * @param {Array<object>} messages 会议消息（store 已注入 `self`）
 * @param {{ gapMs?: number }} [options]
 * @returns {Array<object>} 原消息 + `senderKey` / `groupStart` / `self` / `host` / `digital` / `at`
 */
export function groupMeetingMessages(messages = [], { gapMs = CHAT_GROUP_GAP_MS } = {}) {
  const list = Array.isArray(messages) ? messages : []
  const result = []
  let hasPrev = false
  let prevKey = ''
  let prevAt = null

  for (const message of list) {
    if (!message) continue
    const key = senderKeyOf(message)
    const at = toTime(message.createTime)
    const sameSender = hasPrev && key === prevKey
    // 时间缺失时不按时间窗拆组，只按发送者连续合并
    const tooLate = sameSender && at !== null && prevAt !== null && at - prevAt > gapMs
    result.push({
      ...message,
      senderKey: key,
      groupStart: !sameSender || tooLate,
      self: message.self === true,
      host: message.role === 1,
      digital: message.role === 3 || message.kind === 2,
      at,
    })
    hasPrev = true
    prevKey = key
    prevAt = at
  }

  return result
}
