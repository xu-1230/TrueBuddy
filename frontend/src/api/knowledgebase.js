import service from './index'

/**
 * 知识库 API 接入层
 *
 * 对接后端 tb-module-knowledgebase 模块（Controller @RequestMapping("/knowledge-base")）：
 *     GET     /space/list                       → CommonResult<SpaceVO[]>
 *     POST    /space/create                     → CommonResult<Long>
 *     PUT     /space/update                     → CommonResult<SpaceVO>
 *     DELETE  /space/delete?id={id}             → CommonResult<Boolean>
 *     GET     /document/list?spaceId={spaceId}  → CommonResult<DocumentVO[]>
 *     POST    /document/create                  → CommonResult<Long>
 *     DELETE  /document/delete?id={id}          → CommonResult<Boolean>
 *     GET     /chunk/list?documentId={id}       → CommonResult<ChunkVO[]>
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

// ========== 前后端共享 VO 契约 ==========
/**
 * @typedef {Object} SpaceVO
 * @property {number} id              空间 ID
 * @property {string} name            空间名称
 * @property {string} description     空间描述
 * @property {number} documentCount    文档数量
 * @property {string} createTime      创建时间（ISO 字符串）
 */

/**
 * @typedef {Object} DocumentVO
 * @property {number} id              文档 ID
 * @property {number} spaceId         所属空间 ID
 * @property {string} name            文档名称
 * @property {'PDF'|'WORD'|'MARKDOWN'} type 文档类型
 * @property {string} createTime      创建时间（ISO 字符串）
 */

/**
 * @typedef {Object} ChunkVO
 * @property {number} id              分片 ID
 * @property {number} documentId      所属文档 ID
 * @property {number} index           分片序号（从 0 开始）
 * @property {string} title           分片标题
 * @property {string} content         分片正文
 */

/**
 * 获取知识库空间列表
 * GET /knowledge-base/space/list → SpaceVO[]
 */
export function getSpaceList() {
  return service({ url: '/knowledge-base/space/list', method: 'get' })
}

/**
 * 创建知识库空间
 * POST /knowledge-base/space/create → Long id
 * @param {{ name: string, description?: string }} data
 */
export function createSpace(data) {
  return service({ url: '/knowledge-base/space/create', method: 'post', data })
}

/**
 * 更新知识库空间
 * PUT /knowledge-base/space/update → SpaceVO
 * @param {number} id
 * @param {{ name: string, description?: string }} data
 */
export function updateSpace(id, data) {
  return service({ url: '/knowledge-base/space/update', method: 'put', data: { id, ...data } })
}

/**
 * 删除知识库空间（级联删除空间下文档）
 * DELETE /knowledge-base/space/delete?id={id} → Boolean
 * @param {number} id
 */
export function deleteSpace(id) {
  return service({ url: '/knowledge-base/space/delete', method: 'delete', params: { id } })
}

/**
 * 获取指定空间下的文档列表
 * GET /knowledge-base/document/list?spaceId={spaceId} → DocumentVO[]
 * @param {number} spaceId
 */
export function getDocumentList(spaceId) {
  return service({ url: '/knowledge-base/document/list', method: 'get', params: { spaceId } })
}

/**
 * 添加文档（仅元数据，不做真实文件上传）
 * POST /knowledge-base/document/create → Long id
 * @param {{ spaceId: number, name: string, type: 'PDF'|'WORD'|'MARKDOWN' }} data
 */
export function createDocument(data) {
  return service({ url: '/knowledge-base/document/create', method: 'post', data })
}

/**
 * 删除文档
 * DELETE /knowledge-base/document/delete?id={id} → Boolean
 * @param {number} id
 */
export function deleteDocument(id) {
  return service({ url: '/knowledge-base/document/delete', method: 'delete', params: { id } })
}

/**
 * 获取指定文档的分片列表
 * GET /knowledge-base/chunk/list?documentId={documentId} → ChunkVO[]
 * @param {number} documentId
 */
export function getChunkList(documentId) {
  return service({ url: '/knowledge-base/chunk/list', method: 'get', params: { documentId } })
}

/**
 * 第一步：上传文档并解析分片预览（不落库）
 * POST /knowledge-base/document/upload → { fileName, fileSize, type, totalChunks, chunks:[{index,type,content}] }
 * @param {File} file
 */
export function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)
  // 不手动设置 Content-Type，交给浏览器生成 multipart/form-data（含 boundary）
  return service({ url: '/knowledge-base/document/upload', method: 'post', data: formData })
}

/**
 * 第三步：确认分片后向量化（MinIO 存储 + 文档/分片落库 + 写入向量库）
 * POST /knowledge-base/document/vectorize → { documentId, savedChunks, status }
 * @param {File} file
 * @param {Array<{index:number,type:string,content:string}>} chunks
 * @param {number} spaceId
 */
export function vectorizeDocument(file, chunks, spaceId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('chunks', JSON.stringify(chunks))
  formData.append('spaceId', spaceId)
  // 不手动设置 Content-Type，交给浏览器生成 multipart/form-data（含 boundary）
  return service({ url: '/knowledge-base/document/vectorize', method: 'post', data: formData })
}
