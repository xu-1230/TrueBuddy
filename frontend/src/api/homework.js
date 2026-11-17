import service from './index'

/**
 * 作业批改 API 接入层
 *
 * 对接后端 tb-module-homework 模块（Controller @RequestMapping("/homework")）：
 *     POST /homework/upload        → CommonResult<HomeworkUploadRespVO>        （multipart：files + paths + folderName）
 *     POST /homework/grade         → CommonResult<Boolean>                    （JSON：批改配置存档）
 *     GET  /homework/task/list     → CommonResult<HomeworkTaskItemVO[]>       （历史抽屉：最近任务）
 *     GET  /homework/task/detail   → CommonResult<HomeworkTaskDetailRespVO>   （只读回看：配置 + 文件树）
 *     POST /homework/task/start    → CommonResult<Boolean>                    （状态 0→1）
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
 * @property {number} scoreMin      评分区间下限（默认 0）
 * @property {number} scoreMax      评分区间上限 / 满分（默认 100）
 * @property {number} passScore     及格线（默认 60，必须落在区间内）
 * @property {Array<{key:string,description:string,type:string,schema?:{type:string}}>} customFields 自定义导出列（key 即变量名 / 导出表头）
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

// ========== 任务回看与执行 ==========

/**
 * @typedef {Object} HomeworkTaskItemVO 批改任务列表项（历史抽屉条目）
 * @property {number} id           任务 id（homework_task.id）
 * @property {string} folderName   作业文件夹名
 * @property {number} status       0=已上传待批改，1=批改中，2=成功，3=部分成功，4=失败
 * @property {number} fileCount    作业份数
 * @property {number} totalSize    文件总大小（字节）
 * @property {string|null} description AI 描述说明（批改要求，未提交批改请求前为 null）
 * @property {string[]} spaceIds   知识库 id 列表（未提交批改请求前为空数组）
 * @property {number} scoreMin     评分区间下限
 * @property {number} scoreMax     评分区间上限 / 满分
 * @property {number} passScore    及格线
 * @property {string} createTime   创建时间
 */

/**
 * 批改任务列表（历史抽屉）：最近 N 条，按创建时间倒序
 * GET /homework/task/list
 *
 * @param {number} [limit] 条数上限（非正 / 缺省由后端取默认 20，超过 100 截断）
 * @returns {Promise<HomeworkTaskItemVO[]>}
 */
export function listHomeworkTasks(limit) {
  return service({ url: '/homework/task/list', method: 'get', params: { limit } })
}

/**
 * @typedef {HomeworkTaskItemVO} HomeworkTaskDetailRespVO 任务详情（只读回看）
 * 任务字段同列表项，另含文件树、自定义列定义与逐学生结果：
 * @property {HomeworkFileItemVO[]} files 任务下全部作业（按相对路径升序，含预签名 URL 与 MIME）
 * @property {Array<{key:string,description:string,type:string,schema?:{type:string}}>} customFields 自定义导出列定义（key 即变量名 / 导出表头）
 * @property {Array<{student:string,score:number|null,grade:string,custom:object,readFiles:string[]}>} results 逐学生结果
 */

/**
 * 批改任务详情（只读回看）：任务信息 + 批改配置 + 文件树
 * GET /homework/task/detail
 *
 * @param {number} taskId 任务 id
 * @returns {Promise<HomeworkTaskDetailRespVO>}
 */
export function getHomeworkTaskDetail(taskId) {
  return service({ url: '/homework/task/detail', method: 'get', params: { taskId } })
}

/**
 * 开始执行批改：任务状态从「已上传待批改(0)」推进到「批改中(1)」
 * POST /homework/task/start
 *
 * 已不在待批改状态（重复开始）时后端按业务错误码拒绝。
 *
 * @param {number} taskId 任务 id
 * @returns {Promise<boolean>}
 */
export function startHomeworkGrading(taskId) {
  return service({ url: '/homework/task/start', method: 'post', params: { taskId } })
}

/**
 * @typedef {Object} HomeworkResultRecordVO 逐学生评分记录（回写请求与详情回看共用）
 * @property {string} student     学生姓名（空值由服务端填「未署名」）
 * @property {number|null} score  得分（可空）
 * @property {string} grade       等级（优 / 良 / 及格…）
 * @property {string} comment     评语
 * @property {string[]} deductions 扣分明细
 * @property {string[]} readFiles 模型实际读取的文件相对路径
 */

/**
 * @typedef {Object} HomeworkResultWriteReqVO 批改结果回写请求（覆盖式，最后一次为准）
 * @property {string} conversationId 产出本批结果的工作流会话 id（溯源，可空）
 * @property {Object|null} statistics 汇总节点的产出（服务端仅记日志）
 * @property {HomeworkResultRecordVO[]} records 逐学生评分记录（执行失败收敛终态时为空列表）
 */

/**
 * @typedef {Object} HomeworkResultWriteRespVO 回写响应（终态由服务端推导）
 * @property {number} taskId  任务 id
 * @property {number} status  2=成功 / 3=部分成功（有漏批）/ 4=失败
 * @property {number} total   落库的评分记录数
 * @property {number} missed  漏批份数（fileCount - records 数量）
 */

/**
 * 回写批改结果（执行终态后调用）：逐学生评分覆盖式落库，任务状态收敛到终态
 * POST /homework/{taskId}/results
 *
 * @param {number} taskId 任务 id
 * @param {HomeworkResultWriteReqVO} payload 评分记录 + 会话溯源
 * @returns {Promise<HomeworkResultWriteRespVO>}
 */
export function writeHomeworkResults(taskId, payload) {
  return service({ url: `/homework/${taskId}/results`, method: 'post', data: payload })
}
