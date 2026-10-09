/**
 * 聊天消息归属判定（显式身份，不靠 id 巧合）。
 *
 * 为什么不能直接比 `attendeeId === selfId`：后端 `sendMessage` 的口径是「老师发言时 attendeeId 为 null」，
 * 于是控制台没传 selfId（默认 null）时，`null === null` 会让"高亮自己"这件事**碰巧**成立。
 * 任何一个 attendeeId 为 null 的新来源都会一起亮。身份在这里显式给出，判定就只有一条路。
 */

/**
 * @param {object} message 消息（可能带本地回显的 self 标记）
 * @param {object} identity 当前端上身份
 * @param {number|null} [identity.selfId] 学生自己的 attendeeId
 * @param {boolean} [identity.isHost] 当前端是不是老师控制台
 * @returns {boolean}
 */
export function isOwnMessage(message, { selfId = null, isHost = false } = {}) {
  if (!message) return false
  // 本地回显（DataChannel 不会把数据发回给自己）已经知道自己是谁，不必再判
  if (message.self === true) return true
  return isHost ? message.role === 1 : message.attendeeId === selfId
}
