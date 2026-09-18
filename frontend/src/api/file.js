import service from './index'

/**
 * 通用文件上传 API 接入层
 *
 * 对接后端 tb-module-infra 模块（Controller @RequestMapping("/infra/file")）：
 *     POST /infra/file/upload  → CommonResult<FileUploadVO>
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

/**
 * @typedef {Object} FileUploadVO
 * @property {number} id              文件 id（infra_file 主键，供后续业务引用）
 * @property {string} name            原始文件名（含扩展名）
 * @property {number} size            文件大小（字节）
 * @property {string} type            文件类型（MIME）
 * @property {string} url             访问地址（MinIO 预签名 URL）
 */

/**
 * 上传单个文件
 * POST /infra/file/upload
 * @param {File} file
 * @returns {Promise<FileUploadVO>}
 */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  // 不手动设置 Content-Type，交给浏览器生成 multipart/form-data（含 boundary）
  return service({ url: '/infra/file/upload', method: 'post', data: formData })
}
