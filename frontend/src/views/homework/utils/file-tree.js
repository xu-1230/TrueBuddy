/**
 * 由平铺的文件明细构建目录树（历史抽屉只读回看的文件树）
 *
 * 输入来自 /homework/task/detail 的 files（后端按 relative_path 升序返回），
 * 目录节点按首次出现的顺序生成，与后端的树阅读顺序一致。
 */

/** 系统 / 临时文件忽略规则（与 FolderUploader 保持一致） */
const IGNORED_NAME_PATTERN = /^(\.|~\$|__MACOSX)/i

/**
 * @typedef {Object} TreeFileInput 任务详情里的一份作业
 * @property {string} path   相对路径，如 电子技术基础-第3次作业/20230101-张三/第三章习题.docx
 * @property {string} name   文件基础名
 * @property {number} size   文件大小（字节）
 * @property {string} [url]  预签名 URL（可空）
 */

/**
 * 平铺文件清单 → el-tree 数据（[{ id, label, size?, url?, file?, children? }]）
 * @param {TreeFileInput[]} files
 * @returns {Array}
 */
export function buildFileTree(files) {
  const root = []
  const pathToNode = new Map()
  ;(files ?? []).forEach((file) => {
    const path = String(file?.path ?? '')
    const parts = path.split('/').filter(Boolean)
    if (!parts.length) return

    let level = root
    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1
      const prefix = parts.slice(0, index + 1).join('/')
      if (isFile) {
        if (IGNORED_NAME_PATTERN.test(part)) return
        level.push({
          id: path,
          label: file?.name || part,
          size: file?.size ?? 0,
          url: file?.url || '',
          file: true,
        })
        return
      }
      let node = pathToNode.get(prefix)
      if (!node) {
        node = { id: `dir:${prefix}`, label: part, children: [] }
        pathToNode.set(prefix, node)
        level.push(node)
      }
      level = node.children
    })
  })
  return root
}
