import service from './index'

/**
 * 作业批改 API 接入层
 *
 * 对接后端 tb-module-homework 模块（Controller @RequestMapping("/homework")）：
 *     POST /homework/upload   → CommonResult<HomeworkUploadRespVO>   （multipart：files + paths + folderName）
 *
 * service 响应拦截器已自动解包 CommonResult.data。
 */

// ========== 前后端共享 VO 契约 ==========
/**
 * @typedef {Object} HomeworkFileItemVO 任务下的一份作业
 * @property {number} id       作业文件 id（homework_file.id）
 * @property {number|null} fileId 关联文件 id（infra_file.id）
 * @property {string} name     文件基础名（含扩展名）
 * @property {string} path     相对路径，如 电子技术基础-第3次作业/20230101-张三/第三章习题.docx
 * @property {number} size     文件大小（字节）
 * @property {string} type     文件类型（MIME）
 * @property {string} url      访问地址（MinIO 预签名 URL，7 天有效）
 */

/**
 * @typedef {Object} HomeworkUploadRespVO 作业文件夹上传结果
 * @property {number} taskId         批改任务 id
 * @property {string} folderName     作业文件夹名
 * @property {number} fileCount      作业份数
 * @property {number} totalSize      文件总大小（字节）
 * @property {string[]} paths        文件相对路径清单
 * @property {HomeworkFileItemVO[]} files 每份文件的明细
 */

// ========== 接口定义 ==========

/**
 * 作业文件夹上传：一次提交文件夹内的全部作业，返回以相对路径表示的文件清单
 * POST /homework/upload（multipart/form-data）
 *
 * @param {File[]} files 待上传作业，顺序与 paths 严格一致
 * @param {string[]} paths 文件相对路径（与 files 一一对应）
 * @param {string} [folderName] 作业文件夹显示名
 * @param {(event: ProgressEvent) => void} [onUploadProgress] 上传进度回调
 * @returns {Promise<HomeworkUploadRespVO>}
 */
export function uploadHomeworkFiles(files, paths, folderName, onUploadProgress) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  paths.forEach((path) => formData.append('paths', path))
  if (folderName) {
    formData.append('folderName', folderName)
  }
  return service({
    url: '/homework/upload',
    method: 'post',
    data: formData,
    // 文件夹批量上传耗时较长，不做超时限制
    timeout: 0,
    onUploadProgress,
  })
}

/**
 * @typedef {Object} HomeworkGradeReqVO 批改请求（第二步）
 * @property {number} taskId        第一步上传返回的批改任务 id
 * @property {string[]} spaceIds    选中的知识库 id 列表
 * @property {HomeworkFileItemVO[]} files 作业文件清单（含相对路径、fileId 与 MinIO 预签名 URL，直接透传上传返回）
 * @property {string} description   AI 描述说明（批改要求）
 * @property {'xlsx'|'csv'} exportFormat 导出结果格式
 * @property {string} workflowId    选择的工作流 id
 */

/**
 * 提交批改请求：把本次批改的全部配置交给后端
 * POST /homework/grade（application/json）
 *
 * @param {HomeworkGradeReqVO} payload 批改配置
 * @returns {Promise<boolean>}
 */
export function submitGrading(payload) {
  return service({ url: '/homework/grade', method: 'post', data: payload })
}
