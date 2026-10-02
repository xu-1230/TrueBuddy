/**
 * 「打包产物必须包含运行前校验」的守卫用例。
 *
 * 为什么需要：`frontend/dist` 是 git-ignored 的构建产物，部署 / 预览 / nginx 用的是它而不是源码。
 * 一旦源码里加了「运行前校验」而 dist 还停在上一次构建，现象就是**「点击运行根本没有校验」**——
 * 而在 dev server 上怎么点都是好的，很容易被误判成「代码没写 / 模块没加载」。
 *
 * 默认只做**诊断**（不红）：源码一动 dist 天然就过期，让 `npm test` 每次都因为「产物过期」而失败
 * 只会让人忽略它。需要在部署前 / CI 上硬校验时显式开启：
 *
 * ```powershell
 * $env:CHECK_DIST=1; npm test
 * ```
 *
 * 运行：cd frontend && npm test
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

/** 构建产物目录（与 vite 的 outDir 一致） */
const DIST = fileURLToPath(new URL('../dist/', import.meta.url))

/** 校验失败弹窗的标题：源码里一定会被构建进产物，用它判断「这套产物里有没有运行前校验」 */
const MARKER = '运行前校验未通过'

/**
 * 产物里是否存在运行前校验代码。
 *
 * 逐块扫描 `dist/assets/*.js`：当前画布页是路由懒加载的独立 chunk，入口只引用它的文件名，
 * 扫描全量最省心（体积可接受，且本用例默认只做诊断）。中文文案不会被压缩器改写，可直接匹配。
 *
 * @returns {boolean|null} null = 没有构建产物
 */
const artifactHasValidation = () => {
  const assets = join(DIST, 'assets')
  if (!existsSync(assets)) return null
  return readdirSync(assets)
    .filter((name) => name.endsWith('.js'))
    .some((name) => readFileSync(join(assets, name), 'utf8').includes(MARKER))
}

/** 产物构建时间（读 index.html 的 mtime，最接近「这次构建是什么时候」） */
const builtAt = () => {
  const index = join(DIST, 'index.html')
  return existsSync(index) ? statSync(index).mtime.toLocaleString() : '未知'
}

test('打包产物（若存在）必须包含运行前校验，否则用产物访问就一定没有校验', (t) => {
  const hasValidation = artifactHasValidation()

  if (hasValidation === null) {
    t.diagnostic('未发现 frontend/dist（没有构建产物）—— 用 dev server 开发时不需要它')
    return
  }
  if (hasValidation) {
    t.diagnostic(`frontend/dist 含运行前校验（构建于 ${builtAt()}）`)
    return
  }

  const message = `frontend/dist 是旧构建（构建于 ${builtAt()}，不含「${MARKER}」）：`
    + '用打包产物访问时会表现为「点击运行没有前端校验」，请执行 npm run build 重新构建（或删除 dist 不要用它）'
  if (process.env.CHECK_DIST === '1') {
    assert.equal(hasValidation, true, message)
    return
  }
  t.diagnostic(`⚠ ${message}`)
})
