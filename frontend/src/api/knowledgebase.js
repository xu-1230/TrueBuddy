import service from './index'

/**
 * 知识库 API 接入层
 *
 * 对接后端 tb-module-knowledgebase 模块（Controller @RequestMapping("/knowledge-base")）：
 *     GET     /space/list                       → CommonResult<SpaceVO[]>
 *     GET     /space/public-list                → CommonResult<PublicSpaceVO[]>（他人的公有空间，只读）
 *     PUT     /space/visibility?id=&visibility= → CommonResult<Boolean>（仅创建者可调）
 *     POST    /space/fork?id={id}               → CommonResult<Long>（fork 为独立副本，返回副本 id）
 *     POST    /space/create                     → CommonResult<Long>
 *     PUT     /space/update                     → CommonResult<SpaceVO>
 *     DELETE  /space/delete?id={id}             → CommonResult<Boolean>
 *     GET     /document/list?spaceId={spaceId}  → CommonResult<DocumentVO[]>
 *     POST    /document/create                  → CommonResult<Long>
 *     DELETE  /document/delete?id={id}          → CommonResult<Boolean>
 *     GET     /chunk/list?documentId={id}       → CommonResult<ChunkVO[]>
 *     POST    /document/upload                  → CommonResult<Object[]>（文件列表批量提交解析任务）
 *     GET     /document/parse-status            → CommonResult<Object>（轮询解析状态/分片预览）
 *     POST    /document/vectorize               → CommonResult<Object>（分片向量化）
 *     POST    /document/abandon                 → CommonResult<Boolean>（放弃未确认分片的文档）
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

// ========== 前后端共享 VO 契约 ==========
/**
 * @typedef {Object} SpaceVO
 * @property {number} id              空间 ID
 * @property {string} name            空间名称
 * @property {string} description     空间描述
 * @property {number} visibility      可见性：0=私有，1=公有
 * @property {number} documentCount    文档数量
 * @property {string} createTime      创建时间（ISO 字符串）
 */

/**
 * @typedef {Object} PublicSpaceVO
 * @property {number} id              空间 ID
 * @property {string} name            空间名称
 * @property {string} description     空间描述
 * @property {number} documentCount   文档数量
 * @property {string} createTime      创建时间（ISO 字符串）
 * @property {string} creatorName     创建者显示名
 */

/**
 * @typedef {Object} DocumentVO
 * @property {number} id              文档 ID
 * @property {number} spaceId         所属空间 ID
 * @property {string} name            文档名称
 * @property {'PDF'|'WORD'|'MARKDOWN'} type 文档类型
 * @property {number} status          处理状态：0=待处理，1=解析中，2=已完成（已向量化），3=失败，4=待确认（已解析未向量化）
 * @property {number} parseProgress   解析进度 0-100
 * @property {string} [parseError]    解析失败原因
 * @property {string} createTime      创建时间（ISO 字符串）
 */

/**
 * @typedef {Object} ChunkVO
 * @property {number} id              分片 ID
 * @property {number} documentId      所属文档 ID
 * @property {number} index           分片序号（从 0 开始）
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
 * 获取他人的公有空间列表（「公有库」浏览区，只读）
 * GET /knowledge-base/space/public-list → PublicSpaceVO[]
 */
export function getPublicSpaceList() {
  return service({ url: '/knowledge-base/space/public-list', method: 'get' })
}

/**
 * 切换空间可见性（仅创建者可调）
 * PUT /knowledge-base/space/visibility?id={id}&visibility={0|1} → Boolean
 * @param {number} id
 * @param {0|1} visibility 0=私有，1=公有
 */
export function updateSpaceVisibility(id, visibility) {
  return service({
    url: '/knowledge-base/space/visibility',
    method: 'put',
    params: { id, visibility },
  })
}

/**
 * fork 空间为「添加为自己的」独立副本（含文档/分片/向量的单事务深拷贝）
 * POST /knowledge-base/space/fork?id={id} → Long 副本空间 id
 * @param {number} id 源空间 id（他人的公有空间或自己的空间）
 */
export function forkSpace(id) {
  return service({ url: '/knowledge-base/space/fork', method: 'post', params: { id } })
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
 * 第一步：文件列表上传并提交解析任务（MinIO 存储 + 落库 + 提交解析引擎）
 * POST /knowledge-base/document/upload → [{ documentId, fileName, fileSize, type, engine, status }]
 * 返回数组，按上传顺序一一对应对每个文件的提交结果。
 * @param {File | File[]} files 单个文件或文件列表
 * @param {number} spaceId
 */
export function uploadDocument(files, spaceId) {
  const list = Array.isArray(files) ? files : [files]
  const formData = new FormData()
  list.forEach((file) => formData.append('files', file))
  formData.append('spaceId', spaceId)
  // 不手动设置 Content-Type，交给浏览器生成 multipart/form-data（含 boundary）
  return service({ url: '/knowledge-base/document/upload', method: 'post', data: formData })
}

/**
 * 第二步：轮询解析状态；解析完成后返回分片预览
 * GET /knowledge-base/document/parse-status?documentId={documentId}
 *   → { documentId, status, progress, totalChunks, chunks:[{index,content}], error? }
 * @param {number} documentId
 */
export function getParseStatus(documentId) {
  return service({ url: '/knowledge-base/document/parse-status', method: 'get', params: { documentId } })
}

/**
 * 第三步：确认分片后向量化（覆盖落库分片 + 写入向量库）
 * POST /knowledge-base/document/vectorize → { documentId, savedChunks, status }
 * @param {number} documentId
 * @param {number} spaceId
 * @param {Array<{index:number,content:string}>} chunks
 */
export function vectorizeDocument(documentId, spaceId, chunks) {
  const formData = new FormData()
  formData.append('documentId', documentId)
  formData.append('spaceId', spaceId)
  formData.append('chunks', JSON.stringify(chunks))
  // 不手动设置 Content-Type，交给浏览器生成 multipart/form-data（含 boundary）
  return service({ url: '/knowledge-base/document/vectorize', method: 'post', data: formData })
}

/**
 * 放弃未完成向量化的文档（逻辑删除，不再出现在列表）
 * POST /knowledge-base/document/abandon?documentId={documentId} → Boolean
 * @param {number} documentId
 */
export function abandonDocument(documentId) {
  return service({ url: '/knowledge-base/document/abandon', method: 'post', params: { documentId } })
}
