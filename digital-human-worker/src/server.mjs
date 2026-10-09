/**
 * worker 的 HTTP 层（契约见 docs/17 §5.1 / §5.2）。
 *
 * <p>本文件刻意不 import rtc-node：路由、鉴权、参数校验都能离线测，媒体侧只经 registry 注入。
 */
import { createServer } from 'node:http'

import { CHANNELS, SAMPLE_RATE } from './audio.mjs'

/** 口令未配置时拒绝一切写操作：宁可起不来，也不要一个同网段谁都能驱动的房间注入器 */
const NOT_CONFIGURED = 'AGENT_SHARED_SECRET 未配置，worker 拒绝服务'
const ROOM_PATTERN = /^[A-Za-z0-9_-]{1,64}$/
const REQUEST_ID_PATTERN = /^[A-Za-z0-9-]{1,64}$/
const MAX_JSON_BODY = 64 * 1024

function json(res, status, body) {
  if (status === 204) {
    res.writeHead(204)
    res.end()
    return
  }
  const payload = JSON.stringify(body)
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(payload) })
  res.end(payload)
}

async function readJsonBody(req) {
  const chunks = []
  let size = 0
  for await (const chunk of req) {
    size += chunk.length
    if (size > MAX_JSON_BODY) {
      throw new Error('请求体过大')
    }
    chunks.push(chunk)
  }
  if (size === 0) {
    throw new Error('请求体不能为空')
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

/**
 * @param registry SessionRegistry（或测试里的替身）
 * @param secret   与后端 TB_AGENT_SHARED_SECRET 同值的内部口令
 */
export function createWorkerServer({ registry, secret, log = console, version = '0.0.1' }) {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://worker')
    const path = url.pathname

    try {
      if (req.method === 'GET' && path === '/healthz') {
        json(res, 200, { status: 'ok', sessions: registry.list().length, version })
        return
      }

      if (secret == null || secret === '') {
        json(res, 503, { error: NOT_CONFIGURED })
        return
      }
      if (req.headers['x-agent-token'] !== secret) {
        json(res, 401, { error: '口令不符' })
        return
      }

      if (req.method === 'POST' && path === '/sessions') {
        const body = await readJsonBody(req)
        const { room, url: signallingUrl, token } = body ?? {}
        if (!ROOM_PATTERN.test(String(room ?? ''))) {
          json(res, 400, { error: 'room 不合法' })
          return
        }
        if (!signallingUrl || !token) {
          json(res, 400, { error: 'url 与 token 必填' })
          return
        }
        await registry.open({ room, url: signallingUrl, token })
        json(res, 201, { room })
        return
      }

      const sessionMatch = path.match(/^\/sessions\/([^/]+)$/)
      if (sessionMatch && req.method === 'DELETE') {
        const room = decodeURIComponent(sessionMatch[1])
        const closed = await registry.close(room)
        json(res, closed ? 204 : 404, closed ? null : { error: '会话不存在' })
        return
      }

      if (req.method === 'POST' && path === '/say') {
        const room = url.searchParams.get('room') ?? ''
        const requestId = url.searchParams.get('requestId') ?? ''
        const sampleRate = Number(url.searchParams.get('sampleRate'))
        const channels = Number(url.searchParams.get('channels'))
        if (!ROOM_PATTERN.test(room)) {
          json(res, 400, { error: 'room 不合法' })
          return
        }
        if (!REQUEST_ID_PATTERN.test(requestId)) {
          json(res, 400, { error: 'requestId 不合法' })
          return
        }
        // 采样率不匹配会静默变速，比不播更糟：直接拒掉，让后端看得见（docs/17 §6）。
        // 不主动 destroy 请求：响应先返回，后端 HttpClient 会中止请求体，合成侧随之拿到 IOException
        if (sampleRate !== SAMPLE_RATE || channels !== CHANNELS) {
          json(res, 400, { error: `worker 只接受 ${SAMPLE_RATE}Hz/${CHANNELS} 声道裸 PCM` })
          return
        }
        if (registry.hasSay(requestId)) {
          json(res, 200, { requestId, duplicated: true })
          return
        }
        const entry = await registry.say({ room, requestId, iterable: req })
        json(res, 202, { requestId, frames: entry.frames, state: entry.state })
        return
      }

      const sayMatch = path.match(/^\/say\/([^/]+)$/)
      if (sayMatch) {
        const requestId = decodeURIComponent(sayMatch[1])
        if (req.method === 'DELETE') {
          json(res, registry.cancel(requestId) ? 202 : 404, { requestId })
          return
        }
        if (req.method === 'GET') {
          const entry = registry.state(requestId)
          json(res, entry ? 200 : 404, entry ?? { error: '播报不存在' })
          return
        }
      }

      json(res, 404, { error: '未知路径' })
    } catch (error) {
      log.error?.(`[server][${req.method} ${path}] ${error?.message ?? error}`)
      if (!res.headersSent) {
        json(res, 500, { error: String(error?.message ?? error) })
      } else {
        res.end()
      }
    }
  })

  return server
}
