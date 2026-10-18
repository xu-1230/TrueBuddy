import { createMockQuery, mockWorkflowDefinitions } from './data'

/** 工作流列表 */
export const mockWorkflowList = mockWorkflowDefinitions

/** 节点模板维护 */
export const mockNodeTemplates = [
  { id: 1, name: '开始节点', type: 1, category: 'input', fieldCount: 3, status: 'enabled', updatedAt: '2026-09-20 10:00' },
  { id: 2, name: '结束节点', type: 2, category: 'input', fieldCount: 2, status: 'enabled', updatedAt: '2026-09-20 10:00' },
  { id: 3, name: '大模型节点', type: 3, category: 'llm', fieldCount: 8, status: 'enabled', updatedAt: '2026-10-08 15:20' },
  { id: 4, name: '知识库检索节点', type: 4, category: 'knowledge', fieldCount: 5, status: 'enabled', updatedAt: '2026-10-02 09:36' },
  { id: 5, name: '条件分支节点', type: 5, category: 'logic', fieldCount: 6, status: 'enabled', updatedAt: '2026-09-26 14:12' },
  { id: 6, name: '循环节点', type: 21, category: 'logic', fieldCount: 4, status: 'enabled', updatedAt: '2026-10-11 11:48' },
  { id: 7, name: '数据库查询节点', type: 6, category: 'data', fieldCount: 7, status: 'draft', updatedAt: '2026-10-15 17:05' },
  { id: 8, name: '人工确认节点', type: 7, category: 'logic', fieldCount: 4, status: 'disabled', updatedAt: '2026-09-12 08:30' },
]

/** 运行记录 */
export const mockWorkflowRuns = [
  { id: 'run-20261018-0042', workflow: '作业批改标准流程', user: '林小满', status: 'success', durationMs: 18_420, startedAt: '2026-10-18 09:40', finishedAt: '2026-10-18 09:41' },
  { id: 'run-20261018-0041', workflow: '学情周报生成', user: '陈可', status: 'running', durationMs: 6_200, startedAt: '2026-10-18 09:36', finishedAt: '' },
  { id: 'run-20261018-0039', workflow: '作业批改标准流程', user: '张伟', status: 'success', durationMs: 21_760, startedAt: '2026-10-18 08:55', finishedAt: '2026-10-18 08:56' },
  { id: 'run-20261017-0036', workflow: '作文批改（含人工复核）', user: '李妍', status: 'failed', durationMs: 5_280, startedAt: '2026-10-17 16:31', finishedAt: '2026-10-17 16:35' },
  { id: 'run-20261017-0033', workflow: '作业批改标准流程', user: '王强', status: 'success', durationMs: 16_884, startedAt: '2026-10-17 10:18', finishedAt: '2026-10-17 10:19' },
  { id: 'run-20261016-0029', workflow: '实验报告批改', user: '赵琳', status: 'cancelled', durationMs: 2_100, startedAt: '2026-10-16 20:02', finishedAt: '2026-10-16 20:03' },
  { id: 'run-20261016-0026', workflow: '学情周报生成', user: '孙宇', status: 'success', durationMs: 9_640, startedAt: '2026-10-16 14:12', finishedAt: '2026-10-16 14:13' },
  { id: 'run-20261015-0022', workflow: '英语口语评测', user: '周洁', status: 'failed', durationMs: 4_020, startedAt: '2026-10-15 09:30', finishedAt: '2026-10-15 09:31' },
  { id: 'run-20261014-0019', workflow: '作业批改标准流程', user: '林小满', status: 'success', durationMs: 19_860, startedAt: '2026-10-14 15:20', finishedAt: '2026-10-14 15:22' },
  { id: 'run-20261013-0016', workflow: '作文批改（含人工复核）', user: '陈可', status: 'success', durationMs: 24_300, startedAt: '2026-10-13 11:08', finishedAt: '2026-10-13 11:10' },
]

/** 执行异常 */
export const mockWorkflowExceptions = [
  { id: 1, workflow: '作文批改（含人工复核）', node: '大模型节点', errorCode: 'LLM_TIMEOUT', message: '模型响应超时（30s），未拿到结构化评分结果', status: 'open', occurredAt: '2026-10-17 16:35' },
  { id: 2, workflow: '英语口语评测', node: '语音转写节点', errorCode: 'ASR_EMPTY', message: '音频轨为空，转写结果长度为 0', status: 'open', occurredAt: '2026-10-15 09:31' },
  { id: 3, workflow: '作业批改标准流程', node: '知识库检索节点', errorCode: 'KB_EMPTY', message: '检索命中 0 个分片，已按空结果继续', status: 'resolved', occurredAt: '2026-10-14 15:21' },
  { id: 4, workflow: '实验报告批改', node: '文件解析节点', errorCode: 'PARSE_FAILED', message: 'docx 解析失败：文件加密', status: 'resolved', occurredAt: '2026-10-13 10:40' },
  { id: 5, workflow: '学情周报生成', node: '数据聚合节点', errorCode: 'DB_TIMEOUT', message: '聚合查询超时（5s）', status: 'ignored', occurredAt: '2026-10-12 18:02' },
  { id: 6, workflow: '作文批改（含人工复核）', node: '人工确认节点', errorCode: 'HUMAN_TIMEOUT', message: '人工确认超过 24 小时未处理', status: 'resolved', occurredAt: '2026-10-10 09:15' },
]

/** 会话记录 */
export const mockWorkflowConversations = [
  { id: 'conv-1024', workflow: '作业批改标准流程', user: '林小满', messageCount: 18, status: 'completed', startedAt: '2026-10-18 09:40', updatedAt: '2026-10-18 09:41' },
  { id: 'conv-1023', workflow: '学情周报生成', user: '陈可', messageCount: 6, status: 'active', startedAt: '2026-10-18 09:36', updatedAt: '2026-10-18 09:38' },
  { id: 'conv-1021', workflow: '作业批改标准流程', user: '张伟', messageCount: 22, status: 'completed', startedAt: '2026-10-18 08:55', updatedAt: '2026-10-18 08:56' },
  { id: 'conv-1018', workflow: '作文批改（含人工复核）', user: '李妍', messageCount: 9, status: 'failed', startedAt: '2026-10-17 16:31', updatedAt: '2026-10-17 16:35' },
  { id: 'conv-1015', workflow: '作业批改标准流程', user: '王强', messageCount: 20, status: 'completed', startedAt: '2026-10-17 10:18', updatedAt: '2026-10-17 10:19' },
  { id: 'conv-1011', workflow: '实验报告批改', user: '赵琳', messageCount: 4, status: 'completed', startedAt: '2026-10-16 20:02', updatedAt: '2026-10-16 20:03' },
  { id: 'conv-1008', workflow: '学情周报生成', user: '孙宇', messageCount: 12, status: 'completed', startedAt: '2026-10-16 14:12', updatedAt: '2026-10-16 14:13' },
  { id: 'conv-1004', workflow: '英语口语评测', user: '周洁', messageCount: 3, status: 'failed', startedAt: '2026-10-15 09:30', updatedAt: '2026-10-15 09:31' },
]

export const queryWorkflows = createMockQuery(mockWorkflowList, {
  keywordFields: ['name', 'description'],
})
export const queryNodeTemplates = createMockQuery(mockNodeTemplates, {
  keywordFields: ['name'],
})
export const queryWorkflowRuns = createMockQuery(mockWorkflowRuns, {
  keywordFields: ['id', 'workflow', 'user'],
})
export const queryWorkflowExceptions = createMockQuery(mockWorkflowExceptions, {
  keywordFields: ['workflow', 'node', 'errorCode', 'message'],
})
export const queryWorkflowConversations = createMockQuery(mockWorkflowConversations, {
  keywordFields: ['id', 'workflow', 'user'],
})
