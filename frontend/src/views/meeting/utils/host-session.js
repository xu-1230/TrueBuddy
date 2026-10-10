/**
 * 主持人凭据的本机存取（按会议号切分）。
 *
 * 单独成文件的原因：控制台写它，演讲台（`/meeting/:meetingNo/present`）要读它，
 * 两边各写一份键名就会漂移，而键名漂移的后果是「凭据明明在浏览器里，页面说没有」。
 *
 * 注意：凭据**不进 URL**（会留在浏览器历史里，也会被复制到群里的链接带走）；
 * 也不提供找回通道（docs/adr/0003），读不到就回控制台重新进入。
 */

/** 按会议号切分的键前缀 */
export const SESSION_PREFIX = 'tb-meeting-host:'

/** 改造前的单个 sessionStorage 键：读到就迁移一次，迁移后不再使用 */
export const LEGACY_SESSION_KEY = 'tb-meeting-host'

export function hostSessionKey(meetingNo) {
  return `${SESSION_PREFIX}${meetingNo}`
}

export function writeHostSession(value) {
  window.localStorage.setItem(hostSessionKey(value.meetingNo), JSON.stringify(value))
}

export function clearHostSession(meetingNo) {
  window.localStorage.removeItem(hostSessionKey(meetingNo))
}

/**
 * 读取主持人会话；结构不完整时删掉坏值并返回 null（坏值留着只会每次都报错）。
 *
 * @param {string} meetingNo 会议号
 * @returns {{ meetingNo: string, hostToken: string, title?: string, joinUrl?: string, meetingId?: number }|null}
 */
export function readHostSession(meetingNo) {
  const raw = window.localStorage.getItem(hostSessionKey(meetingNo))
  if (!raw) return null
  try {
    const saved = JSON.parse(raw)
    if (!saved?.meetingNo || !saved?.hostToken) throw new Error('invalid session')
    return saved
  } catch {
    clearHostSession(meetingNo)
    return null
  }
}
