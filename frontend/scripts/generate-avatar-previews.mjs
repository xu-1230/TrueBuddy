/**
 * 为 9 个 Live2D 形象生成静态缩略图（设置弹窗「数字人」Tab 用）。
 *
 * 做法：写一个临时 Vite 入口页 → 启动 dev server → 用 Chrome headless（CDP）逐个
 * 打开模型、等 `window.__AVATAR_READY__` 后截图 → 输出到
 * `public/live2d/previews/{modelCode}.png`。
 *
 * 运行：
 *   cd frontend && node scripts/generate-avatar-previews.mjs
 *   cd frontend && node scripts/generate-avatar-previews.mjs DeepSeekWhale Haru
 *
 * 需要本机有 Chrome（可用 CHROME_PATH 指定）。脚本只在开发时跑，不参与构建。
 */
import { spawn } from 'node:child_process'
import { Buffer } from 'node:buffer'
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:net'
import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const proc = globalThis.process
const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outDir = path.join(frontendDir, 'public', 'live2d', 'previews')
const previewHtml = path.join(frontendDir, 'avatar-preview.html')
const WIDTH = 480
const HEIGHT = 600
const DEV_PORT = 5174

const ALL_MODELS = {
  DeepSeekWhale: true,
  DeepSeekDaFeiyu: true,
  Haru: true,
  Hiyori: true,
  Mao: true,
  Mark: true,
  Natori: true,
  Rice: true,
  Wanko: true,
}

const pageHtml = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>avatar preview</title>
  <style>
    html, body { margin: 0; padding: 0; width: ${WIDTH}px; height: ${HEIGHT}px; overflow: hidden; }
    body {
      background:
        radial-gradient(120% 90% at 50% 0%, #f6f1e7 0%, transparent 60%),
        linear-gradient(165deg, #efe9de 0%, #d9d2c4 100%);
    }
    #stage { display: block; width: ${WIDTH}px; height: ${HEIGHT}px; }
  </style>
</head>
<body>
  <canvas id="stage"></canvas>
  <script src="/live2d/live2dcubismcore.min.js"></script>
  <script type="module">
    import * as PIXI from 'pixi.js'
    import { Live2DModel } from 'pixi-live2d-display/cubism4'
    import { applyFraming, getAvatarModelConfig, loadAvatarModel } from '/src/views/ppt/configs/avatar.js'

    window.PIXI = PIXI
    const code = new URLSearchParams(location.search).get('model')

    const hideWatermark = (model, app) => {
      if (code !== 'DeepSeekDaFeiyu') return
      const internal = model.internalModel
      const core = internal?.coreModel
      const hide = () => {
        try { core?.setParameterValueById('Paramcol_3', 1) } catch {}
      }
      internal?.on('beforeModelUpdate', hide)
      // 双保险：ticker 每帧也写一次（Cubism 每帧会还原参数）
      app.ticker.add(hide)
      hide()
      // 作者提供的「水印消除」表情：部分版本里参数需要配合表情才彻底透明
      const expressions = internal?.settings?.expressions ?? []
      const watermark = expressions.find((item) => String(item?.Name || item || '').includes('水印'))
      if (watermark) {
        try { model.expression(watermark.Name || watermark) } catch {}
      }
    }

    try {
      const app = new PIXI.Application({
        view: document.getElementById('stage'),
        width: ${WIDTH},
        height: ${HEIGHT},
        backgroundAlpha: 0,
        resolution: window.devicePixelRatio || 1,
        autoDensity: true,
      })
      const loaded = await loadAvatarModel(
        (url) => Live2DModel.from(url, { autoInteract: false, crossOrigin: 'anonymous' }),
        code,
      )
      hideWatermark(loaded.model, app)
      app.stage.addChild(loaded.model)
      applyFraming(loaded.model, { width: ${WIDTH}, height: ${HEIGHT} }, getAvatarModelConfig(code).framing)
      window.__AVATAR_READY__ = true
    } catch (error) {
      window.__AVATAR_ERROR__ = String(error?.stack || error)
      console.error(error)
    }
  </script>
</body>
</html>
`

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function findFreePort(start) {
  for (let port = start; port < start + 40; port++) {
    const free = await new Promise((resolve) => {
      const server = createServer()
      server.once('error', () => resolve(false))
      server.once('listening', () => server.close(() => resolve(true)))
      server.listen(port, '127.0.0.1')
    })
    if (free) return port
  }
  throw new Error('找不到空闲端口')
}

async function waitForHttp(url, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
    } catch {
      // 还没起来
    }
    await sleep(300)
  }
  throw new Error(`${label} 启动超时：${url}`)
}

function chromePath() {
  const candidates = [
    proc.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome',
    '/usr/bin/chromium',
  ].filter(Boolean)
  const found = candidates.find((item) => existsSync(item))
  if (!found) throw new Error('找不到 Chrome，请用 CHROME_PATH 指定')
  return found
}

class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl
    this.id = 0
    this.pending = new Map()
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl)
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true })
      this.ws.addEventListener('error', reject, { once: true })
    })
    this.ws.addEventListener('message', (event) => {
      let message
      try {
        message = JSON.parse(event.data)
      } catch {
        return
      }
      if (!message.id) return
      const pending = this.pending.get(message.id)
      if (!pending) return
      this.pending.delete(message.id)
      if (message.error) pending.reject(new Error(message.error.message || 'CDP 调用失败'))
      else pending.resolve(message.result)
    })
  }

  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }

  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', {
      expression,
      returnByValue: true,
      awaitPromise: true,
    })
    if (result?.exceptionDetails) {
      throw new Error(result.exceptionDetails.text || '页面脚本执行失败')
    }
    return result?.result?.value
  }

  close() {
    try {
      this.ws?.close()
    } catch {
      // 忽略
    }
  }
}

async function captureModel(cdpPort, devPort, code) {
  // 先开 about:blank，把视口/DPR 设好再导航：保证页面脚本读到的是 2x DPR
  const targetResponse = await fetch(
    `http://127.0.0.1:${cdpPort}/json/new?${encodeURIComponent('about:blank')}`,
    { method: 'PUT' },
  )
  if (!targetResponse.ok) throw new Error(`创建标签页失败：${targetResponse.status}`)
  const target = await targetResponse.json()
  const client = new CdpClient(target.webSocketDebuggerUrl)

  try {
    await client.connect()
    await client.send('Page.enable')
    await client.send('Runtime.enable')
    await client.send('Emulation.setDeviceMetricsOverride', {
      width: WIDTH,
      height: HEIGHT,
      deviceScaleFactor: 2,
      mobile: false,
    })
    const nav = await client.send('Page.navigate', {
      url: `http://127.0.0.1:${devPort}/avatar-preview.html?model=${encodeURIComponent(code)}`,
    })
    if (nav?.errorText) throw new Error(`页面导航失败：${nav.errorText}`)

    const deadline = Date.now() + 90_000
    let ready = false
    while (Date.now() < deadline) {
      const state = await client.evaluate(
        '(() => ({ ready: window.__AVATAR_READY__ === true, error: window.__AVATAR_ERROR__ || null }))()',
      )
      if (state?.error) throw new Error(state.error)
      if (state?.ready) {
        ready = true
        break
      }
      await sleep(500)
    }
    if (!ready) throw new Error(`${code} 90s 内未完成加载`)

    // 让 Ticker 至少画几帧（模型有入场/呼吸动作；水印表情淡出约 1s）
    await sleep(1600)
    const shot = await client.send('Page.captureScreenshot', {
      format: 'png',
      fromSurface: true,
      captureBeyondViewport: false,
    })
    const file = path.join(outDir, `${code}.png`)
    await writeFile(file, Buffer.from(shot.data, 'base64'))
    console.log(`OK ${code} -> ${path.relative(frontendDir, file)}`)
  } finally {
    client.close()
    await fetch(`http://127.0.0.1:${cdpPort}/json/close/${target.id}`).catch(() => {})
  }
}

async function main() {
  const requested = proc.argv.slice(2).filter((item) => item && !item.startsWith('-'))
  const models = requested.length ? requested : Object.keys(ALL_MODELS)
  const unknown = models.filter((code) => !ALL_MODELS[code])
  if (unknown.length) throw new Error(`未知形象：${unknown.join(', ')}`)

  await mkdir(outDir, { recursive: true })
  await writeFile(previewHtml, pageHtml, 'utf-8')

  const vitePort = await findFreePort(DEV_PORT)
  const cdpPort = await findFreePort(9222)
  const userDataDir = path.join(os.tmpdir(), `tb-avatar-preview-${Date.now()}`)
  const viteLog = []
  let vite = null
  let chrome = null

  try {
    vite = spawn('npm', ['run', 'dev', '--', '--host', '127.0.0.1', '--port', String(vitePort), '--strictPort'], {
      cwd: frontendDir,
      shell: proc.platform === 'win32',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    vite.stdout.on('data', (chunk) => viteLog.push(String(chunk)))
    vite.stderr.on('data', (chunk) => viteLog.push(String(chunk)))
    vite.on('exit', (code) => {
      if (code && code !== 0) console.error(`[vite] 提前退出 code=${code}\n${viteLog.join('')}`)
    })
    await waitForHttp(`http://127.0.0.1:${vitePort}/avatar-preview.html`, 30_000, 'Vite')

    const chromeArgs = [
      '--headless=new',
      `--remote-debugging-port=${cdpPort}`,
      `--user-data-dir=${userDataDir}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--hide-scrollbars',
      '--use-angle=swiftshader',
      '--enable-unsafe-swiftshader',
      'about:blank',
    ]
    chrome = spawn(chromePath(), chromeArgs, { stdio: ['ignore', 'pipe', 'pipe'] })
    chrome.stderr.on('data', () => {})
    await waitForHttp(`http://127.0.0.1:${cdpPort}/json/version`, 30_000, 'Chrome')

    for (const code of models) {
      console.log(`... ${code}`)
      await captureModel(cdpPort, vitePort, code)
    }
  } finally {
    await killTree(chrome)
    await killTree(vite)
    await rm(previewHtml, { force: true })
    await rm(userDataDir, { recursive: true, force: true }).catch(() => {})
  }
}

/** Windows 上 child.kill() 只杀 shell，Vite / Chrome 的子进程会留下来；按进程树杀。 */
function killTree(child) {
  if (!child?.pid) return Promise.resolve()
  if (proc.platform !== 'win32') {
    child.kill()
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    const killer = spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { stdio: 'ignore' })
    killer.once('exit', resolve)
    killer.once('error', resolve)
  })
}

main().catch((error) => {
  console.error(error?.stack || error)
  proc.exitCode = 1
})
