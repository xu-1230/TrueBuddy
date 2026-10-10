/**
 * 在线名单的取值逻辑（纯函数，便于 `node --test` 覆盖）。
 *
 * 唯一真相源是 LiveKit 房间事件：`lecture_meeting_attendee` 只是累计入会台账，
 * 它的行**不得**被当成在线名单（docs/adr/0002、CONTEXT.md）。
 * 房间连不上时不再退回台账 —— 台账里「尚未显式离开」的行恰恰包含早就走掉的人，
 * 列出来比不列更误导。
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
 * 产物**不含 `online` 布尔**：能出现在这份列表里就意味着此刻在房间里，再挂一个 online
 * 字段只会等着被人误用成"这是在线状态的来源"。
 *
 * @param {Array<object>} participants 房间内的本端 + 远端 participant
 * @param {object} [options]
 * @param {boolean} [options.omitHost=false] 去掉老师自己 —— 控制台是主持人的操作台，
 *   不是参会席位（docs/16 Q22），把自己列进「在线成员」并计入人数会与页面自述矛盾
 * @returns {Array<{id: string, nickname: string, role: number}>}
 */
export function fromRoomParticipants(participants = [], { omitHost = false } = {}) {
  // 房间里的每一个 participant 都是人：数字人与放映页这两个「不是人」的身份已随 worker 一起删除
  // （讲稿的声音改走屏幕共享的音轨，见 docs/16 §5.7），因此不再需要按身份排除谁
  const items = (participants ?? [])
    .filter(Boolean)
    .map((participant) => ({
      id: participant.identity,
      nickname: participant.name || participant.identity,
      role: roleOfParticipant(participant),
    }))
  return omitHost ? items.filter((item) => item.role !== ROLE_HOST) : items
}

/**
 * 名单区要展示什么、能不能报人数。
 *
 * @param {object} state
 * @param {Array<object>} [state.attendees] 房间事件得出的成员
 * @param {boolean} [state.connected] 房间是否已连接
 * @returns {{degraded: boolean, attendees: Array<object>, onlineCount: number|null}}
 *   降级时名单为空、`onlineCount` 为 **null**：没有真相源就不列人名、也不编数字
 */
export function selectMemberPanel({ attendees = [], connected = true } = {}) {
  if (connected) {
    return { degraded: false, attendees, onlineCount: attendees.length }
  }
  return { degraded: true, attendees: [], onlineCount: null }
}
