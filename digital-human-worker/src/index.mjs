/**
 * 数字人 worker 入口。
 *
 * 启动：cd digital-human-worker && npm install && npm start
 * 环境变量见同目录 .env.example；只绑环回地址，见 docs/17 §5.1。
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { SessionRegistry } from './session.mjs'
import { createWorkerServer } from './server.mjs'

/** 与仓库根 .env 同一套语法：值里不要放反斜杠等转义字符 */
function loadDotEnv() {
  try {
    const text = readFileSync(fileURLToPath(new URL('../.env', import.meta.url)), 'utf8')
    for (const line of text.split(/\r?\n/)) {
      const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/)
      if (match && process.env[match[1]] === undefined) {
        process.env[match[1]] = match[2].trim()
      }
    }
  } catch {
    // 没有 .env 就用进程环境
  }
}

loadDotEnv()

const host = process.env.WORKER_HOST ?? '127.0.0.1'
const port = Number(process.env.WORKER_PORT ?? 9090)
const secret = process.env.AGENT_SHARED_SECRET ?? ''

const registry = new SessionRegistry()
const server = createWorkerServer({ registry, secret })

if (secret === '') {
  console.warn('[worker][警告] AGENT_SHARED_SECRET 未配置：除 /healthz 外一律返回 503')
}

server.listen(port, host, () => {
  console.log(`[worker][listening http://${host}:${port}]`)
})

let closing = false
async function shutdown(signal) {
  if (closing) {
    return
  }
  closing = true
  console.log(`[worker][${signal} 退出中]`)
  server.close()
  // 必须走 dispose()：只 disconnect 的话原生线程仍在，进程不会退出（docs/17 §13.3）
  await registry.shutdown().catch((error) => console.error('[worker][清理失败]', error))
  process.exit(0)
}

process.on('SIGINT', () => void shutdown('SIGINT'))
process.on('SIGTERM', () => void shutdown('SIGTERM'))
