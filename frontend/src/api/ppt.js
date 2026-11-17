/**
 * PPT 讲稿生成接口层。
 *
 * 前端 PPT 数据来自本地导入（`pptx-to-pptist`），服务端不持有这份文档，因此接口是**无状态**的：
 * 每次把要生成的页（标题 + 正文 + 现有备注）整批送上去，拿回每页的讲稿。
 *
 * `pages` 长度 1 就是「优化本页」，长度 N 就是「优化全部」——整篇是**一次请求**，
 * 后端内部按 `tb.lecture.ppt.batch-size` 分批调用模型（输出有 token 上限，整篇一次问会被截断）。
 */
import service from './index'

/** 讲稿 REST 接口路径（不带 `/api` 前缀：axios 实例的 baseURL 已是 `/api`） */
export const PPT_REST_API = {
  /** 讲稿生成：{ targetSeconds, pages: [{ index, title, text, remark }] } */
  optimizeSpeech: '/ppt/speech/optimize',
  narrateSpeech: '/ppt/speech/narrate',
  /** 数字人形象目录（启用中的形象） */
  avatarList: '/ppt/avatar/list',
  /** 音色库（内置 + 我的复刻） */
  voiceBase: '/ppt/voice',
  voiceList: '/ppt/voice/list',
  /** 当前用户的数字人与音色偏好 */
  preference: '/ppt/preference',
  /** 声音复刻（multipart） */
  voiceClone: '/ppt/voice/clone',
}

/** 启用中的数字人形象目录 */
export function getAvatarList() {
  return service.get(PPT_REST_API.avatarList)
}

/** 当前用户可见的音色（内置 + 自己复刻）与能力开关 */
export function getVoiceList() {
  return service.get(PPT_REST_API.voiceList)
}

/** 读取当前用户的数字人与音色偏好；没有记录时两个字段为空 */
export function getAvatarPreference() {
  return service.get(PPT_REST_API.preference)
}

/** 保存当前用户的数字人与音色偏好 */
export function saveAvatarPreference(payload) {
  return service.put(PPT_REST_API.preference, payload)
}

/** 复刻音色：FormData 字段 name/gender/trait/language/sourceType/url/file */
export function cloneVoice(formData) {
  return service.post(PPT_REST_API.voiceClone, formData)
}

/** 删除自己的复刻音色 */
export function deleteVoice(id) {
  return service.delete(`${PPT_REST_API.voiceBase}/${id}`)
}

/**
 * 生成讲稿。
 *
 * @param {{ targetSeconds?: number, pages: Array<object> }} payload 页清单与目标总时长（秒）
 * @param {AbortSignal} [signal] 离开 PPT 页时中止等待（已知代价：后端会把当前批次跑完，见 docs/16 风险表）
 * @returns {Promise<{ pages: Array<{ index: number, remark: string, chars: number, seconds: number }>, failedIndexes: number[] }>}
 */
export function optimizeSpeech(payload, signal) {
  return service.post(PPT_REST_API.optimizeSpeech, payload, { signal })
}
/* AI 现场讲词：一次请求 = 一页口播文本（ai 模式逐页调用，失败走重试/跳过）。 */
export function narrateSpeech(payload, signal) {
  return service.post(PPT_REST_API.narrateSpeech, payload, { signal })
}
