/**
 * 房间连接状态的统一描述（老师控制台与学生会议室共用一份，避免同一状态两种说法）。
 *
 * 之前两个页面各写一份映射表，且同一个 `disconnected` 在控制台是灰色 `neutral`、
 * 在学生端是红色 `danger`——同一事实两种色调，用户无法据此建立心智模型。
 */

/**
 * @param {string} status idle / connecting / connected / reconnecting / disconnected
 * @param {boolean} ended 会议是否已结束（结束后连接断开是**预期**，不该再报警）
 * @returns {{text: string, tone: string}} tone: neutral / pending / live / ended
 */
export function describeConnection(status, { ended = false } = {}) {
  if (ended) return { text: '会议已结束', tone: 'ended' }
  const map = {
    idle: { text: '未连接', tone: 'neutral' },
    connecting: { text: '连接中', tone: 'pending' },
    connected: { text: '已连接', tone: 'live' },
    reconnecting: { text: '重连中', tone: 'pending' },
    disconnected: { text: '连接已断开', tone: 'danger' },
  }
  return map[status] ?? { text: '状态未知', tone: 'neutral' }
}
