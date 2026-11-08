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
