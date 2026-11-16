/**
 * 路由匹配小工具。
 */

/**
 * 判断当前路径是否落在某个菜单路径上：精确命中，或位于其子路径。
 *
 * 用于侧边栏高亮：/index/doc-template/12 应点亮 /index/doc-template。
 * 前缀必须带 `/`，避免 /index/home 误命中 /index/homework-grading。
 *
 * @param {string} currentPath 当前路由 path（不含 query）
 * @param {string} itemPath 菜单项路径
 * @returns {boolean}
 */
export function isPathActive(currentPath, itemPath) {
  if (!currentPath || !itemPath) return false
  return currentPath === itemPath || currentPath.startsWith(`${itemPath}/`)
}
