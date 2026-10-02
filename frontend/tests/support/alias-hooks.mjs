/**
 * 让 node:test 也能解析 `@/` 别名与「省略扩展名」的 import。
 *
 * 应用源码是给打包器看的：`@` → `frontend/src` 这份 alias 写在 `vite.config.js` 里，
 * 且 import 一律省略 `.js`。Node 的原生 ESM 两条都不认，于是 `src/api/workflow.js` 这类模块
 * 在 node:test 里根本 import 不进来。需要它们的用例先：
 *
 * ```js
 * register(new URL('./support/alias-hooks.mjs', import.meta.url))
 * const { buildRunDefinition } = await import('../src/api/workflow.js')
 * ```
 *
 * 注意：钩子只对 `register()` 之后**动态 import** 的模块树生效，不影响用例自己的静态 import。
 */
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/** `@` → `frontend/src` */
const SRC = new URL('../../src/', import.meta.url)

/** 补扩展名：先原样，其次 `.js`，最后 `/index.js`（与打包器的解析顺序一致） */
const withExtension = (url) => {
  const path = fileURLToPath(url)
  if (existsSync(path)) return url.href
  if (existsSync(`${path}.js`)) return `${url.href}.js`
  if (existsSync(`${path}/index.js`)) return `${url.href}/index.js`
  return url.href
}

/** 只接管 `@/xxx` 与相对路径，其余（如 `vue`、`node:fs`）原样交给 Node 默认解析 */
export function resolve(specifier, context, next) {
  if (specifier.startsWith('@/')) {
    return next(withExtension(new URL(specifier.slice('@/'.length), SRC)), context)
  }
  if (specifier.startsWith('.') && context.parentURL) {
    return next(withExtension(new URL(specifier, context.parentURL)), context)
  }
  return next(specifier, context)
}
