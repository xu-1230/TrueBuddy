/**
 * SSE 事件流读取（fetch + ReadableStream）。
 *
 * 为什么不用 EventSource：这些接口都是 POST + JSON 请求体，EventSource 只能 GET、无法携带 body。
 * 因此按 SSE 协议手工切分：空行分隔事件，`event:` 给事件名，`data:` 给载荷（可多行，拼接后解析）。
 *
 * 两类使用方共用这一个读取器：
 * - 工作流执行 / 人工确认恢复：事件 `run` / `node` / `interrupt`，载荷是 JSON；
 * - 讲稿播报（`POST /ppt/speech/speak`）：事件 `status` / `chunk` / `error`，其中 `chunk` 的载荷是
 *   base64 的 PCM，**原样**交给播放器（因此非 JSON 载荷必须按字符串返回，不能被解析掉）。
 *
 * `signal` 是唯一的取消手段，也是「停止演讲」的全部实现：请求一断，后端合成随即取消
 * （写不出去 → 合成侧停止继续向厂商要音频，见 docs/16 §5.7）。
 */
import { getToken } from '@/utils/auth'

/** 事件之间的分隔：空行（兼容 \n 与 \r\n） */
const EVENT_SEPARATOR = /\r?\n\r?\n/

/** data 是 JSON 对象/数组时解析成对象，其余原样返回（避免非 JSON 载荷触发解析异常） */
const parsePayload = (raw) => {
  const text = raw.trim()
  const looksLikeJson = text.startsWith('{') || text.startsWith('[')
  return looksLikeJson ? JSON.parse(text) : raw
}

/** 把一段原始事件文本解析成 { event, payload }；没有 data 行（心跳/注释）时返回 null */
const parseEventBlock = (block) => {
  let event = 'message'
  const dataLines = []
  block.split('\n').forEach((rawLine) => {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).replace(/^ /, ''))
    }
  })
  return dataLines.length ? { event, payload: parsePayload(dataLines.join('\n')) } : null
}

/**
 * 非事件流响应（后端校验失败或系统异常会返回 `CommonResult` JSON）转成带 code / message 的错误，
 * 使页面能直接展示后端给出的中文原因。
 */
const toStreamError = async (response) => {
  const isJson = (response.headers.get('content-type') ?? '').includes('json')
  const payload = isJson ? await response.json() : null
  const error = new Error(payload?.message || `请求失败（HTTP ${response.status}）`)
  error.code = payload?.code
  return error
}

/** 逐块读取响应体，按空行切出完整事件后回调；最后一个没有空行收尾的事件也要派发 */
const consume = async (stream, onEvent) => {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const dispatch = () => {
    let separator = buffer.match(EVENT_SEPARATOR)
    while (separator) {
      const parsed = parseEventBlock(buffer.slice(0, separator.index))
      buffer = buffer.slice(separator.index + separator[0].length)
      if (parsed) onEvent(parsed.event, parsed.payload)
      separator = buffer.match(EVENT_SEPARATOR)
    }
  }

  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    dispatch()
  }

  buffer += decoder.decode()
  const tail = parseEventBlock(buffer)
  if (tail) onEvent(tail.event, tail.payload)
}

/**
 * 读取一份 SSE 事件流。
 *
 * @param {string} url 接口地址，如 `/api/ppt/speech/speak`（带 `/api` 前缀走 Vite 代理）
 * @param {object} options 选项
 * @param {object} [options.body] 请求体；省略时不发送 body（用 query 传参的接口）
 * @param {AbortSignal} [options.signal] 中断信号：停止当前播报 / 重跑 / 页面卸载都靠它
 * @param {(event: string, payload: object|string) => void} options.onEvent 事件回调
 * @returns {Promise<void>} 流正常读完时 resolve；HTTP 失败或读取中断时 reject
 */
export async function readEventStream(url, options) {
  const { body, signal, onEvent } = options
  const headers = { Accept: 'text/event-stream' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })

  const isEventStream = (response.headers.get('content-type') ?? '').includes('text/event-stream')
  if (!response.ok || !isEventStream) {
    throw await toStreamError(response)
  }

  await consume(response.body, onEvent)
}
