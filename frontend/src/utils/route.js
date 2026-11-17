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

/**
 * 判断当前路径是否位于某个路径的**子路径**下（不含该路径本身）。
 *
 * 用于「列表页正常布局、详情页全幅」这类需要区分父路径与其子路径的场景：
 * `/index/workflow` 与 `/index/workflow/123` 在 `isPathActive` 下都算命中，
 * 但这里只认后者。
 *
 * @param {string} currentPath 当前路由 path（不含 query）
 * @param {string} basePath 父路径
 * @returns {boolean}
 */
export function isChildPath(currentPath, basePath) {
  if (!currentPath || !basePath) return false
  return currentPath.startsWith(`${basePath}/`)
}
