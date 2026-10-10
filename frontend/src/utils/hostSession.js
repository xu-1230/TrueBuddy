/**
 * 老师（主持人）会话的本地存取。
 *
 * <p>两个模块共用：**PPT 页**建会成功后写入，**会议控制台**打开时读出来恢复这一场。
 * 因此它不放在任何单个 view 目录下（规约见 frontend/AGENT §3「模块内私有资源就近放置」——
 * 它不是模块私有的）。
 *
 * <p>键按会议号切分，放在 `localStorage`：PPT 页建会后会把控制台开成**另一个标签页**
 * （`/index/meeting?meetid={会议号}`），凭据必须能被那个新标签页读到；复制链接到别的标签页、
 * 多开两个控制台各管一场会也靠它。`sessionStorage` 逐标签隔离、单个键也区分不了会议，因此不用。
 *
 * <p>代价是主持人凭据会留在本机浏览器里（ADR-0003：丢失不补发），所以**结束会议时立刻删除该键**。
 */

/** 主持人会话键前缀：完整键为 `tb-meeting-host:{会议号}` */
export const HOST_SESSION_PREFIX = 'tb-meeting-host:'

/** 改造前的会话键（单个 sessionStorage）：读到就迁移一次，迁移后不再使用 */
const LEGACY_HOST_SESSION_KEY = 'tb-meeting-host'

/** `{ meetingId, meetingNo, joinUrl, hostToken, title }` */
function sessionKey(meetingNo) {
  return `${HOST_SESSION_PREFIX}${meetingNo}`
}

/** 写入一场会的主持人会话（建会成功后立刻写，另一个标签页随即来读） */
export function writeHostSession(value) {
  window.localStorage.setItem(sessionKey(value.meetingNo), JSON.stringify(value))
}

/** 清掉一场会的主持人会话（结束会议时调用） */
export function clearHostSession(meetingNo) {
  window.localStorage.removeItem(sessionKey(meetingNo))
}

/** 解析会话 JSON；结构不完整就删掉坏值并返回 null */
function parseHostSession(raw, meetingNo) {
  try {
    const saved = JSON.parse(raw)
    if (!saved?.meetingNo || !saved?.hostToken) throw new Error('invalid session')
    return saved
  } catch {
    clearHostSession(meetingNo)
    return null
  }
}

/** 读一场会的主持人会话；本机没有（换机器 / 清了存储 / 会议已结束）时返回 null */
export function readHostSession(meetingNo) {
  const raw = window.localStorage.getItem(sessionKey(meetingNo))
  return raw ? parseHostSession(raw, meetingNo) : null
}

/** 扫一遍本机存着的会议会话（先收键名再读，避免读的过程中删键导致漏项） */
export function listKnownMeetings() {
  const keys = []
  for (let i = 0; i < window.localStorage.length; i += 1) {
    const key = window.localStorage.key(i)
    if (key?.startsWith(HOST_SESSION_PREFIX)) keys.push(key)
  }
  return keys
    .map((key) => readHostSession(key.slice(HOST_SESSION_PREFIX.length)))
    .filter(Boolean)
    .map((saved) => ({ meetingNo: saved.meetingNo, title: saved.title || '未命名会议' }))
    .sort((a, b) => a.meetingNo.localeCompare(b.meetingNo))
}

/**
 * 兼容改造前的会话形态（单个 `sessionStorage` 键）→ 按会议号切分的 `localStorage` 键，一次性迁移。
 *
 * @returns {string|null} 迁移后的会议号（调用方据此补地址栏），没有可迁移的会话时返回 null
 */
export function migrateLegacyHostSession() {
  const raw = window.sessionStorage.getItem(LEGACY_HOST_SESSION_KEY)
  if (!raw) return null
  window.sessionStorage.removeItem(LEGACY_HOST_SESSION_KEY)
  try {
    const saved = JSON.parse(raw)
    if (!saved?.meetingNo || !saved?.hostToken) return null
    writeHostSession(saved)
    return saved.meetingNo
  } catch {
    return null
  }
}
