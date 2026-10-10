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

/**
 * 试听接口地址（**带** `/api` 前缀）。
 *
 * 试听返回的是音频字节而不是 `CommonResult`，走 axios 会被响应拦截器当成「解包失败」抛错，
 * 因此和 `CHAT_API.chat` 一样用原生 `fetch`（`fetch` 不经过实例、不会自动补 baseURL）。
 */
export const PPT_SPEAK_URL = '/api/ppt/speech/speak'

/**
 * 试听：把这页讲稿合成成一段 WAV 取回来（放映形态自己听，见 docs/18 §5.3）。
 *
 * 后端刻意先把 PCM 收满再写 WAV 头（data 长度必须已知），所以这里是「整段取回 → 播放」，
 * 不是边合成边播；代价是第一声要等整段合成完。
 *
 * @param {string} text 要试听的文本（通常是本页 `remark` 的一个片段）
 * @param {AbortSignal} [signal] 翻页或点「停止」时中止：后端在写出第一个字节前失败会正常返回错误 JSON
 * @returns {Promise<Blob>} `audio/wav` 的 Blob
 * @throws {Error} 业务错误（未启用 / 超长 / 合成失败）时带后端的 message
 */
export async function speakPreview(text, signal) {
  const response = await fetch(PPT_SPEAK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
    signal,
  })
  const type = response.headers.get('content-type') ?? ''
  // 合成失败发生在写出第一个字节之前，所以错误仍然是 JSON：把 message 取出来给老师看
  if (type.includes('application/json')) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `试听失败（HTTP ${response.status}）`)
  }
  if (!response.ok) throw new Error(`试听失败（HTTP ${response.status}）`)
  return response.blob()
}
