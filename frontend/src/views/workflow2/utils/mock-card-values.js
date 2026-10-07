/**
 * 表单卡片的**模拟取值**（一期）。
 *
 * 一期不做真实的文件上传：卡片里的文件类字段由前端造一份与后端 `HomeworkFileItemVO`
 * 同构的假数据（字段口径参考 `HomeworkGradeReqVO.files`：`{id, fileId, name, path, size, type, url}`）。
 *
 * 二期接上 `POST /homework/upload` 后，这里换成上传返回的真实 `files` 即可，**协议不用改**——
 * 卡片字段名与提交结构都保持一致。
 */

/** 模拟的文件相对路径清单：与后端 `StartNode` 的演示数据同一批，便于两处对照 */
const MOCK_PATHS = [
  '电子技术基础-第3次作业/20230101-张三/第三章习题.docx',
  '电子技术基础-第3次作业/20230102-李四/第三章习题.docx',
  '电子技术基础-第3次作业/20230103-王五/第三章习题.pdf',
  '电子技术基础-第3次作业/20230201-赵六/第三章习题.docx',
  '电子技术基础-第3次作业/20230202-钱七/课堂笔记.md',
]

/** 扩展名 → MIME 类型（只覆盖演示用到的几种） */
const MIME_BY_EXTENSION = {
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  pdf: 'application/pdf',
  md: 'text/markdown',
}

const mimeOf = (name) => MIME_BY_EXTENSION[name.split('.').pop()?.toLowerCase()] ?? 'application/octet-stream'

/**
 * 造一份与后端 `HomeworkFileItemVO` 同构的模拟文件清单。
 *
 * `id` / `fileId` / `url` 留空：一期没有真实上传，这三个字段只有接上上传接口后才有值。
 *
 * @returns {Array<object>} 文件明细列表
 */
export const mockFileItemsOf = () =>
  MOCK_PATHS.map((path, index) => {
    const name = path.split('/').pop()
    return {
      id: null,
      fileId: null,
      name,
      path,
      size: 20480 + index * 4096,
      type: mimeOf(name),
      url: '',
    }
  })

/**
 * 判断一个 list 字段是否应该按「文件清单」渲染（元素 schema 里带 `path` / `name`）。
 *
 * @param {object} field 卡片字段声明 `{name, type, label, required, schema}`
 */
export const isFileListField = (field) => {
  const properties = field?.schema?.properties
  return Boolean(properties && (properties.path || properties.fileId))
}

/** 字段类型的归一化：卡片可能用 text / string，也可能只给了 schema */
export const kindOf = (field) => {
  const type = String(field?.type ?? '').toLowerCase()
  if (type === 'list' || type === 'array') return 'list'
  if (type === 'object') return 'object'
  return 'text'
}
