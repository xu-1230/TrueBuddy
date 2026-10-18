/**
 * 菜单树的纯函数操作（权限页使用）
 *
 * 接口未接入时页面用本地 mock 树演示增删改，这些函数把「树怎么变」从组件里拆出来：
 * 组件只负责弹窗与提示，树结构一律由这里返回新对象（不改入参），也便于 node:test。
 */

/**
 * 深度优先拍平（调试、搜索、统计用）
 * @param {Array} nodes
 * @returns {Array}
 */
export function flattenMenuTree(nodes = []) {
  const result = []
  for (const node of nodes) {
    if (!node) continue
    result.push(node)
    if (Array.isArray(node.children) && node.children.length > 0) {
      result.push(...flattenMenuTree(node.children))
    }
  }
  return result
}

/**
 * 统计节点总数
 * @param {Array} nodes
 * @returns {number}
 */
export function countMenuNodes(nodes = []) {
  return flattenMenuTree(nodes).length
}

/**
 * 按 id 查找节点
 * @param {Array} nodes
 * @param {number|string} id
 * @returns {object|null}
 */
export function findMenuNode(nodes = [], id) {
  for (const node of nodes) {
    if (!node) continue
    if (String(node.id) === String(id)) return node
    const found = findMenuNode(node.children || [], id)
    if (found) return found
  }
  return null
}

/**
 * 新增节点：parentId 为空时挂到根，否则挂到对应父节点下
 * @param {Array} nodes
 * @param {number|string|null} parentId
 * @param {object} node
 * @returns {Array} 新树
 */
export function insertMenuNode(nodes = [], parentId, node) {
  if (parentId === null || parentId === undefined || parentId === '') {
    return [...nodes, node]
  }

  return nodes.map((item) => {
    if (String(item.id) === String(parentId)) {
      return { ...item, children: [...(item.children || []), node] }
    }
    if (Array.isArray(item.children) && item.children.length > 0) {
      return { ...item, children: insertMenuNode(item.children, parentId, node) }
    }
    return item
  })
}

/**
 * 修改节点：patch 覆盖指定字段
 * @param {Array} nodes
 * @param {number|string} id
 * @param {object} patch
 * @returns {Array} 新树
 */
export function updateMenuNode(nodes = [], id, patch = {}) {
  return nodes.map((item) => {
    if (String(item.id) === String(id)) return { ...item, ...patch }
    if (Array.isArray(item.children) && item.children.length > 0) {
      return { ...item, children: updateMenuNode(item.children, id, patch) }
    }
    return item
  })
}

/**
 * 删除节点：连同其子树一起删除
 * @param {Array} nodes
 * @param {number|string} id
 * @returns {Array} 新树
 */
export function removeMenuNode(nodes = [], id) {
  return nodes
    .filter((item) => String(item.id) !== String(id))
    .map((item) => {
      if (Array.isArray(item.children) && item.children.length > 0) {
        return { ...item, children: removeMenuNode(item.children, id) }
      }
      return item
    })
}
