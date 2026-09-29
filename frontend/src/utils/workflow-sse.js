import { getToken } from '@/utils/auth'

/**
 * SSE 事件流读取（`fetch` + `ReadableStream`）。
 *
 * 为什么不用 `EventSource`：它只支持 GET，而工作流的提交 / 恢复都是 POST（画布 schema 可达数十 KB，
 * 且需要携带 `confirmedPlan`），因此改为读取 `fetch` 的响应体并自行按 SSE 语法解析。
 *
 * 与 axios 实例（`api/index.js`）的分工：axios 会解包 `CommonResult`，而 SSE 的负载是**业务数据本身**
 * （不经 `CommonResult` 包装），因此本模块直接用 `fetch`，并自行处理「响应不是事件流」的错误分支
 * （提交阶段抛业务异常时，后端会落到全局异常处理器返回 JSON 错误体）。
 *
 * 解析要点（对齐 SSE 规范的最小实现）：
 * - 消息块以空行分隔（兼容 `\r\n`），块内每行形如 `field: value`；
 * - `event:` 指定事件名（缺省为 `message`），`data:` 允许多行（以 `\n` 拼接），`:` 开头为注释（心跳）；
 * - `data` 尝试 `JSON.parse`，失败则保留原文（本项目的负载始终是 JSON 对象）。
 */

/** 后端 context-path 前缀：与 `api/index.js` 的 axios baseURL 保持一致 */
const API_BASE = '/api'

/** SSE 消息块分隔符 */
const BLOCK_SEPARATOR = /\r?\n\r?\n/

/** 构造带业务错误码的 Error（与 axios 响应拦截器口径一致） */
const buildError = (message, code) => {
  const error = new Error(message || '请求失败')
  if (code !== undefined && code !== null) error.code = code
  return error
}

/**
 * 打开一条 SSE 事件流。
 *
 * @param {string} path 接口路径（如 `/workflow/run`），不含 `/api` 前缀
 * @param {Object} [payload] 请求体（序列化为 JSON）
 * @param {{
 *   onEvent?: (event: string, data: any) => void,
 *   onError?: (error: Error) => void,
 *   onDone?: (reason: 'done' | 'closed' | 'abort') => void,
 * }} [handlers] 事件回调；`onDone` 在流终止时**必定回调一次**
 * @returns {{ abort: () => void, done: Promise<void> }} `abort` 主动中止（页面卸载 / 重新执行）
 */
export function openEventStream(path, payload = {}, handlers = {}) {
  const { onEvent, onError, onDone } = handlers
  const controller = new AbortController()
  let finished = false

  const finish = (reason) => {
    if (finished) return
    finished = true
    onDone?.(reason)
  }

  const fail = (error) => {
    if (finished) return
    finished = true
    onError?.(error)
  }

  /** 解析单个消息块并分发 */
  const handleBlock = (block) => {
    let event = 'message'
    const dataLines = []
    for (const rawLine of block.split(/\r?\n/)) {
      if (!rawLine || rawLine.startsWith(':')) continue
      const colon = rawLine.indexOf(':')
      const field = colon < 0 ? rawLine : rawLine.slice(0, colon)
      let value = colon < 0 ? '' : rawLine.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'event') event = value
      else if (field === 'data') dataLines.push(value)
    }
    if (!dataLines.length) return
    const text = dataLines.join('\n')
    let data = text
    try {
      data = JSON.parse(text)
    } catch {
      // 非 JSON 负载：保留原文，交由调用方判断
    }
    onEvent?.(event, data)
    if (event === 'done') {
      finished = true
      onDone?.('done')
      // 服务端已声明流终止：主动收掉连接，不再等待底层 EOF
      controller.abort()
    }
  }

  const task = (async () => {
    const token = getToken()
    let response
    try {
      response = await fetch(`${API_BASE}${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(payload ?? {}),
        signal: controller.signal,
      })
    } catch (error) {
      if (error?.name === 'AbortError') return
      fail(buildError(error?.message || '网络异常，请确认后端服务已启动'))
      return
    }

    const contentType = response.headers?.get?.('content-type') || ''
    if (!response.ok || !contentType.includes('text/event-stream')) {
      // 提交 / 恢复被业务校验拦下时后端返回 JSON 错误体（HTTP 仍为 200）
      const body = await response.json().catch(() => null)
      fail(buildError(body?.message || `请求失败（HTTP ${response.status}）`, body?.code))
      return
    }
    if (!response.body) {
      fail(buildError('当前浏览器不支持流式响应'))
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    try {
      for (;;) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(BLOCK_SEPARATOR)
        // 最后一段可能被截断（事件尚未收完），留到下一轮再解析
        buffer = blocks.pop() ?? ''
        for (const block of blocks) {
          if (finished) break
          handleBlock(block)
        }
        if (finished) return
      }
      // 兜底：连接结束但缓冲区还留着最后一段（缺尾部空行）
      if (buffer.trim()) handleBlock(buffer)
      finish('closed')
    } catch (error) {
      if (error?.name === 'AbortError') return
      fail(buildError(error?.message || '读取执行事件失败'))
    }
  })()

  return {
    abort: () => {
      if (finished) return
      finished = true
      controller.abort()
      onDone?.('abort')
    },
    done: task,
  }
}
