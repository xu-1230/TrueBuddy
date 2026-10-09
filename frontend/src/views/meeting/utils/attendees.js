/**
 * 在线名单与入会台账的裁决逻辑（纯函数，便于 `node --test` 覆盖）。
 *
 * 为什么要单独成文件：房间事件与 10s 轮询的 REST 响应曾写**同一个** `attendees` 数组，
 * 谁后写谁赢——于是在线名单每 10 秒被台账脏数据覆盖一次。现在两者分家：
 * **在线成员的唯一真相源是 LiveKit 房间事件**（见 docs/adr/0002 与 CONTEXT.md），
 * REST 返回的 `attendees` 只是累计入会台账，不得冒充在线。
 */

/** 角色：老师（与后端 role 口径一致） */
const ROLE_HOST = 1
/** 角色：学生 */
const ROLE_STUDENT = 2

/**
 * 从 LiveKit participant 的 metadata 取角色；缺失或非法按学生处理。
 *
 * @param {object} participant LiveKit participant（或任何带 metadata 的对象）
 * @returns {number} 1=老师，2=学生
 */
export function roleOfParticipant(participant) {
  try {
    const metadata = JSON.parse(participant?.metadata || '{}')
    return metadata.role === ROLE_HOST ? ROLE_HOST : ROLE_STUDENT
  } catch {
    return ROLE_STUDENT
  }
}

/**
 * 房间 participant 列表 → 展示用成员项。
 *
 * 产物**不含 `online` 布尔**：能出现在这份列表里就意味着在房间里，再挂一个 online 字段
 * 只会等着被人误用成"这是在线状态的来源"。
 *
 * @param {Array<object>} participants 房间内的本端 + 远端 participant
 * @returns {Array<{id: string, nickname: string, role: number}>}
 */
export function fromRoomParticipants(participants = []) {
  return (participants ?? [])
    .filter(Boolean)
    .map((participant) => ({
      id: participant.identity,
      nickname: participant.name || participant.identity,
      role: roleOfParticipant(participant),
    }))
}

/**
 * 名单区该展示哪一份数据、是否处于降级态。
 *
 * @param {object} state
 * @param {Array<object>} [state.roomAttendees] 房间事件得出的成员（在线真相）
 * @param {Array<object>} [state.joinLedger] REST 返回的累计入会台账
 * @param {string} [state.connection] 房间连接态（`connected` 才算拿到真相）
 * @returns {{degraded: boolean, attendees: Array<object>, onlineCount: number|null}}
 *   降级时 `onlineCount` 为 **null**（不能拿台账行数谎称在线人数）
 */
export function pickDisplayAttendees({ roomAttendees = [], joinLedger = [], connection = 'idle' } = {}) {
  if (connection === 'connected') {
    return { degraded: false, attendees: roomAttendees, onlineCount: roomAttendees.length }
  }
  // 降级：房间连不上，只能给出「尚未显式离开」的台账行，并明确标注它不是在线名单
  const pending = joinLedger.filter((item) => item && !item.leaveTime)
  return { degraded: true, attendees: pending, onlineCount: null }
}
