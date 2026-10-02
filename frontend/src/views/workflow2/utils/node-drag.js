/**
 * 「节点库面板 → 画布」的拖拽协议（HTML5 拖放）。
 *
 * 面板与画布页是两个组件，载荷格式必须只有一处定义：面板写、画布读，各写一份 MIME 字符串
 * 一旦拼错就会表现为「拖到画布上没有任何反应」且不报错。
 *
 * 用自定义 MIME 而不是 `text/plain`：画布上可能有其它可拖拽内容（文本、浏览器拖进来的链接），
 * 用自定义类型可以把它们区分开，`dragover` 时才决定要不要接收。
 *
 * 三个函数都对 `dataTransfer` 缺失做了兜底：拖拽事件在部分环境（合成事件、测试）里没有它，
 * 调用方不需要到处判空。
 */

/** 面板拖拽载荷的 MIME 类型（必须全小写，浏览器会把自定义类型规范化成小写） */
export const NODE_DRAG_MIME = 'application/x-tb-workflow-node'

/**
 * 写入拖拽载荷。
 *
 * @param {DataTransfer|null} dataTransfer 拖拽事件的 `dataTransfer`
 * @param {string} type 节点类型编码（`'1'`~`'5'`）
 */
export function writeNodeDragPayload(dataTransfer, type) {
  if (!dataTransfer) return
  dataTransfer.effectAllowed = 'copy'
  dataTransfer.setData(NODE_DRAG_MIME, String(type ?? ''))
}

/**
 * 读取拖拽载荷。
 *
 * 只在 `drop` 里可读（`dragover` 出于安全限制读不到具体数据，只能看 `types`）。
 *
 * @param {DataTransfer|null} dataTransfer 拖拽事件的 `dataTransfer`
 * @returns {string} 节点类型编码；不是本协议的数据时返回空串
 */
export function readNodeDragPayload(dataTransfer) {
  if (!dataTransfer) return ''
  return String(dataTransfer.getData(NODE_DRAG_MIME) ?? '')
}

/**
 * `dataTransfer` 是否携带本协议的载荷。
 *
 * `dragover` 阶段用它决定要不要 `preventDefault()`（不阻止默认行为就等于不接受放置）。
 *
 * @param {DataTransfer|null} dataTransfer 拖拽事件的 `dataTransfer`
 * @returns {boolean}
 */
export function hasNodeDragPayload(dataTransfer) {
  if (!dataTransfer) return false
  const types = dataTransfer.types
  if (!types) return false
  // types 在不同浏览器下是数组或 DOMStringList，统一按可迭代处理
  return Array.from(types).includes(NODE_DRAG_MIME)
}
