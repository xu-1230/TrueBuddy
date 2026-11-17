/**
 * 批改结果回写 payload 的组装（零依赖纯函数，仿 result-contract.js 风格）
 *
 * 执行终态（conversation.chat.completed + records 齐备）后，把运行结果映射成
 * POST /homework/{taskId}/results 的请求体。终态状态机（2/3/4）与漏批份数由服务端
 * 从 records 数量对比任务 fileCount 推导 —— 这里不组装 finalStatus，避免双头真相。
 *
 * 归一口径与页面渲染保持一致：文本 null → 空串、标量当单项、分数取不到就置 null
 * （服务端按可空列落库，编造 0 分比留空更失真）。
 */

/** 文本归一：null / undefined 当空串 */
const textOf = (value) => (value == null ? '' : String(value).trim())

/** 列表归一：标量当单项（模型偶尔把单个值写成一个字符串而不是数组） */
const listOf = (value) => {
  if (value == null) return []
  return Array.isArray(value) ? value : [value]
}

/** 分数归一：null / 空串与取不到有限数都置 null（Number(null) 是 0，会编造出 0 分） */
const scoreOf = (value) => {
  if (value == null || String(value).trim() === '') return null
  const score = Number(value)
  return Number.isFinite(score) ? score : null
}

/**
 * 自定义导出列归一：只接受对象（模型偶尔把 custom 写成 JSON 字符串，解析一次）。
 * 静态列的默认值由服务端按任务定义合并，前端不在这里重复填。
 */
const customOf = (value) => {
  if (value == null) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
    } catch {
      return {}
    }
  }
  return {}
}

/**
 * 运行结果 → 回写请求体
 *
 * @param {object} options 选项
 * @param {Array} options.records 逐学生记录（字段名来自输出节点契约 / 历史落库行）
 * @param {object|null} [options.statistics] 汇总节点的产出（仅透传给服务端记日志）
 * @param {string|number|null} [options.conversationId] 产出本批结果的工作流会话 id
 * @returns {{conversationId: string, statistics: object|null, records: Array<{student: string, score: number|null, grade: string, custom: object, readFiles: string[]}>}}
 */
export function buildResultWritePayload({ records, statistics, conversationId }) {
  return {
    conversationId: conversationId == null ? '' : String(conversationId),
    statistics: statistics ?? null,
    records: (records ?? []).map((record) => ({
      student: textOf(record?.student),
      score: scoreOf(record?.score),
      grade: textOf(record?.grade),
      custom: customOf(record?.custom),
      readFiles: listOf(record?.readFiles).map(textOf),
    })),
  }
}
