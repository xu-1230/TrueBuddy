import { test } from 'node:test'
import assert from 'node:assert/strict'

import { createWorkerServer } from '../src/server.mjs'

const SECRET = 'unit-test-secret'

/** SessionRegistry 的替身：只记调用，不碰 rtc-node，因此这些用例是纯 HTTP 契约测试 */
function fakeRegistry() {
  const says = new Map()
  return {
    says,
    opened: [],
    closed: [],
    fedBytes: 0,
    list() {
      return this.opened.filter((room) => !this.closed.includes(room))
    },
    async open({ room, url, token }) {
      assert.ok(url, 'url 必须透传')
      assert.ok(token, 'token 必须透传')
      this.opened.push(room)
    },
    async close(room) {
      if (!this.opened.includes(room) || this.closed.includes(room)) {
        return false
      }
      this.closed.push(room)
      return true
    },
    hasSay(requestId) {
      return says.has(requestId)
    },
    async say({ room, requestId, iterable }) {
      assert.ok(this.opened.includes(room), `会话不存在: ${room}`)
      let bytes = 0
      for await (const chunk of iterable) {
        bytes += chunk.length
      }
      this.fedBytes = bytes
      const entry = { room, requestId, state: 'done', frames: Math.ceil(bytes / 1920) }
      says.set(requestId, entry)
      return entry
    },
    cancel(requestId) {
      const entry = says.get(requestId)
      if (!entry) {
        return false
      }
      entry.state = 'cancelled'
      return true
    },
    state(requestId) {
      return says.get(requestId) ?? null
    },
  }
}

async function withServer({ secret = SECRET, registry = fakeRegistry() } = {}) {
  const server = createWorkerServer({ registry, secret, version: 'test' })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const base = `http://127.0.0.1:${server.address().port}`
  return {
    server,
    base,
    registry,
    close: () => new Promise((resolve) => server.close(resolve)),
  }
}

const authHeaders = (extra = {}) => ({ 'x-agent-token': SECRET, ...extra })

test('/healthz 免口令可探活，且只暴露计数不暴露房间名', async () => {
  const ctx = await withServer()
  try {
    const res = await fetch(`${ctx.base}/healthz`)
    assert.equal(res.status, 200)
    const body = await res.json()
    assert.equal(body.status, 'ok')
    assert.equal(body.sessions, 0)
    assert.ok(!JSON.stringify(body).includes('meeting-'))
  } finally {
    await ctx.close()
  }
})

test('口令缺失 → 401；worker 未配口令 → 503（宁可拒绝服务也不裸奔）', async () => {
  const ctx = await withServer()
  try {
    const res = await fetch(`${ctx.base}/sessions`, { method: 'POST', body: '{}' })
    assert.equal(res.status, 401)
  } finally {
    await ctx.close()
  }

  const naked = await withServer({ secret: '' })
  try {
    const res = await fetch(`${naked.base}/sessions`, {
      method: 'POST',
      body: JSON.stringify({ room: 'meeting-1', url: 'ws://x:7880', token: 't' }),
    })
    assert.equal(res.status, 503)
    assert.match((await res.json()).error, /AGENT_SHARED_SECRET/)
  } finally {
    await naked.close()
  }
})

test('开会话：合法即 201，room 非法或缺 url/token 即 400', async () => {
  const ctx = await withServer()
  try {
    const ok = await fetch(`${ctx.base}/sessions`, {
      method: 'POST',
      headers: authHeaders({ 'content-type': 'application/json' }),
      body: JSON.stringify({ room: 'meeting-123456', url: 'ws://192.168.1.5:7880', token: 'jwt' }),
    })
    assert.equal(ok.status, 201)
    assert.deepEqual(ctx.registry.opened, ['meeting-123456'])

    for (const body of [
      { room: 'meeting/../etc', url: 'ws://x', token: 't' },
      { room: 'meeting-1', token: 't' },
      { room: 'meeting-1', url: 'ws://x' },
    ]) {
      const res = await fetch(`${ctx.base}/sessions`, {
        method: 'POST',
        headers: authHeaders({ 'content-type': 'application/json' }),
        body: JSON.stringify(body),
      })
      assert.equal(res.status, 400, `应拒绝: ${JSON.stringify(body)}`)
    }
  } finally {
    await ctx.close()
  }
})

test('播报：PCM 全量收到并回 202；采样率不匹配回 400（不静默变速）', async () => {
  const ctx = await withServer()
  try {
    await fetch(`${ctx.base}/sessions`, {
      method: 'POST',
      headers: authHeaders({ 'content-type': 'application/json' }),
      body: JSON.stringify({ room: 'meeting-123456', url: 'ws://x:7880', token: 'jwt' }),
    })

    const res = await fetch(
      `${ctx.base}/say?requestId=abc123&room=meeting-123456&sampleRate=48000&channels=1`,
      { method: 'POST', headers: authHeaders({ 'content-type': 'application/octet-stream' }), body: Buffer.alloc(3840, 3) },
    )
    assert.equal(res.status, 202)
    const body = await res.json()
    assert.equal(body.requestId, 'abc123')
    assert.equal(body.frames, 2)
    assert.equal(ctx.registry.fedBytes, 3840, '一个字节都不能少')

    const wrongRate = await fetch(
      `${ctx.base}/say?requestId=def456&room=meeting-123456&sampleRate=22050&channels=1`,
      { method: 'POST', headers: authHeaders(), body: Buffer.alloc(100) },
    )
    assert.equal(wrongRate.status, 400)
    assert.match((await wrongRate.json()).error, /48000/)

    const badId = await fetch(
      `${ctx.base}/say?requestId=%24%23%24&room=meeting-123456&sampleRate=48000&channels=1`,
      { method: 'POST', headers: authHeaders(), body: Buffer.alloc(4) },
    )
    assert.equal(badId.status, 400)
  } finally {
    await ctx.close()
  }
})

test('同一 requestId 重复投递：第二次标记 duplicated，不重播', async () => {
  const ctx = await withServer()
  try {
    await fetch(`${ctx.base}/sessions`, {
      method: 'POST',
      headers: authHeaders({ 'content-type': 'application/json' }),
      body: JSON.stringify({ room: 'meeting-1', url: 'ws://x', token: 'jwt' }),
    })
    const send = () =>
      fetch(`${ctx.base}/say?requestId=same&room=meeting-1&sampleRate=48000&channels=1`, {
        method: 'POST',
        headers: authHeaders(),
        body: Buffer.alloc(1920),
      })
    await send()
    const second = await send()
    assert.equal(second.status, 200)
    assert.equal((await second.json()).duplicated, true)
  } finally {
    await ctx.close()
  }
})

test('播报状态与打断：GET/DELETE /say/{id}，未知 id 一律 404', async () => {
  const ctx = await withServer()
  try {
    await fetch(`${ctx.base}/sessions`, {
      method: 'POST',
      headers: authHeaders({ 'content-type': 'application/json' }),
      body: JSON.stringify({ room: 'meeting-1', url: 'ws://x', token: 'jwt' }),
    })
    await fetch(`${ctx.base}/say?requestId=t1&room=meeting-1&sampleRate=48000&channels=1`, {
      method: 'POST',
      headers: authHeaders(),
      body: Buffer.alloc(1920),
    })

    const got = await fetch(`${ctx.base}/say/t1`, { headers: authHeaders() })
    assert.equal(got.status, 200)
    assert.equal((await got.json()).state, 'done')

    const cancelled = await fetch(`${ctx.base}/say/t1`, { method: 'DELETE', headers: authHeaders() })
    assert.equal(cancelled.status, 202)
    assert.equal(ctx.registry.state('t1').state, 'cancelled', '打断要落到任务状态上')

    assert.equal((await fetch(`${ctx.base}/say/nope`, { headers: authHeaders() })).status, 404)
    assert.equal((await fetch(`${ctx.base}/say/nope`, { method: 'DELETE', headers: authHeaders() })).status, 404)
  } finally {
    await ctx.close()
  }
})

test('关会话：命中 204 且无响应体，未知房间 404', async () => {
  const ctx = await withServer()
  try {
    await fetch(`${ctx.base}/sessions`, {
      method: 'POST',
      headers: authHeaders({ 'content-type': 'application/json' }),
      body: JSON.stringify({ room: 'meeting-1', url: 'ws://x', token: 'jwt' }),
    })
    const closed = await fetch(`${ctx.base}/sessions/meeting-1`, { method: 'DELETE', headers: authHeaders() })
    assert.equal(closed.status, 204)
    assert.equal(await closed.text(), '', '204 不能带 body')
    assert.equal((await fetch(`${ctx.base}/sessions/meeting-1`, { method: 'DELETE', headers: authHeaders() })).status, 404)
  } finally {
    await ctx.close()
  }
})

test('未知路径 404，不带口令也是 401（先鉴权后路由）', async () => {
  const ctx = await withServer()
  try {
    assert.equal((await fetch(`${ctx.base}/nope`, { headers: authHeaders() })).status, 404)
    assert.equal((await fetch(`${ctx.base}/nope`)).status, 401)
  } finally {
    await ctx.close()
  }
})
