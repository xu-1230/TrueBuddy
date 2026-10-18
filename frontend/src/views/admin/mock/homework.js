import { createMockQuery, mockHomeworkTasks } from './data'

/** 批改资源：评分标准 / 提示词 / 批改工作流 */
export const mockHomeworkResources = [
  { id: 1, name: '初中物理作业评分标准', type: 'rubric', version: 'v2.3', usageCount: 186, status: 'enabled', owner: '林小满', updatedAt: '2026-10-11 09:20' },
  { id: 2, name: '高中数学解题步骤评分标准', type: 'rubric', version: 'v1.8', usageCount: 142, status: 'enabled', owner: '陈可', updatedAt: '2026-10-06 15:42' },
  { id: 3, name: '语文阅读理解批改提示词', type: 'prompt', version: 'v3.0', usageCount: 98, status: 'enabled', owner: '张伟', updatedAt: '2026-09-30 11:08' },
  { id: 4, name: '英语作文批改提示词', type: 'prompt', version: 'v2.2', usageCount: 76, status: 'enabled', owner: '王强', updatedAt: '2026-09-25 17:35' },
  { id: 5, name: '作业批改标准流程', type: 'workflow', version: 'v1.6', usageCount: 224, status: 'enabled', owner: '系统管理员', updatedAt: '2026-10-14 11:20' },
  { id: 6, name: '作文批改（含人工复核）', type: 'workflow', version: 'v1.2', usageCount: 64, status: 'enabled', owner: '林小满', updatedAt: '2026-10-12 16:45' },
  { id: 7, name: '化学实验报告评分标准', type: 'rubric', version: 'v1.0', usageCount: 12, status: 'draft', owner: '李妍', updatedAt: '2026-10-16 10:12' },
  { id: 8, name: '体育过程性评价提示词', type: 'prompt', version: 'v0.8', usageCount: 4, status: 'disabled', owner: '赵琳', updatedAt: '2026-09-18 14:30' },
]

/** 批改记录：复用任务台账（班级 / 文件数 / 平均分 / 状态） */
export const mockHomeworkRecords = mockHomeworkTasks

export const queryHomeworkResources = createMockQuery(mockHomeworkResources, {
  keywordFields: ['name', 'owner'],
})
export const queryHomeworkRecords = createMockQuery(mockHomeworkRecords, {
  keywordFields: ['name', 'className', 'owner'],
})
