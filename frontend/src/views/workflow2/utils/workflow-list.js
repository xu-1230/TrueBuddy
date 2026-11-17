/**
 * 工作流列表的纯函数：分组与可见性目标。
 *
 * 列表接口 `/workflow/list` 已按 creator 过滤（登录用户 = 自己的 + builtin），
 * 前端只负责把 builtin 拆成置顶小组，不重新判断归属。
 */

/**
 * 把「我的工作流」列表拆成内置模板与用户自建两组，保持后端返回的更新时间倒序。
 *
 * @param {Array<{ builtin?: boolean }>} list
 * @returns {{ builtin: Array, mine: Array }}
 */
export function groupMyWorkflows(list = []) {
  const builtin = []
  const mine = []
  for (const item of Array.isArray(list) ? list : []) {
    if (!item) continue
    if (item.builtin) builtin.push(item)
    else mine.push(item)
  }
  return { builtin, mine }
}

/** 可见性切换目标：0=私有 / 1=公有；非法值按私有处理 */
export function visibilityTarget(visibility) {
  return visibility === 1 ? 0 : 1
}
