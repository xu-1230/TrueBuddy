import { createMockQuery, mockDocTemplateTasks } from './data'

/** 文档模板资源（模板库中的可复用模板） */
export const mockDocTemplates = [
  { id: 1, name: '标准教案模板', category: 'lesson-plan', version: 'v3.2', size: 186_240, usageCount: 268, status: 'enabled', owner: '系统管理员', updatedAt: '2026-10-12 10:24' },
  { id: 2, name: '学案模板（两栏）', category: 'study-plan', version: 'v2.0', size: 142_880, usageCount: 156, status: 'enabled', owner: '林小满', updatedAt: '2026-10-08 16:40' },
  { id: 3, name: '说课稿模板', category: 'lecture', version: 'v1.4', size: 98_304, usageCount: 92, status: 'enabled', owner: '张伟', updatedAt: '2026-09-28 09:12' },
  { id: 4, name: '实验报告模板', category: 'lab-report', version: 'v2.1', size: 121_344, usageCount: 74, status: 'enabled', owner: '李妍', updatedAt: '2026-09-21 14:05' },
  { id: 5, name: '教学设计（大单元）', category: 'teaching-design', version: 'v1.0', size: 204_800, usageCount: 31, status: 'draft', owner: '陈可', updatedAt: '2026-10-15 11:36' },
  { id: 6, name: '班会课教案模板', category: 'lesson-plan', version: 'v1.2', size: 76_800, usageCount: 18, status: 'disabled', owner: '王强', updatedAt: '2026-08-30 15:18' },
  { id: 7, name: '复习课学案模板', category: 'study-plan', version: 'v1.1', size: 112_640, usageCount: 47, status: 'enabled', owner: '赵琳', updatedAt: '2026-09-14 08:55' },
  { id: 8, name: '跨学科项目式学习模板', category: 'teaching-design', version: 'v0.9', size: 164_864, usageCount: 6, status: 'draft', owner: '孙宇', updatedAt: '2026-10-16 17:02' },
]

/** 生成记录：复用任务台账（title / template / owner / status / progress / 时间） */
export const mockDocTemplateRecords = mockDocTemplateTasks

/** 自检与修复问答记录：一轮生成的自检发现与修复建议 */
export const mockDocTemplateInspections = [
  { id: 1, taskTitle: '八年级物理《浮力》教案', round: 1, finding: '缺少「浮力方向」的知识点讲解', suggestion: '在活动二后补充竖直向上的受力示意', result: 'fixed', createdAt: '2026-10-18 08:42' },
  { id: 2, taskTitle: '八年级物理《浮力》教案', round: 2, finding: '教学目标未对应课标编号', suggestion: '引用课标 2.2.3，补充可测量行为动词', result: 'fixed', createdAt: '2026-10-18 08:43' },
  { id: 3, taskTitle: '高二数学《导数应用》学案', round: 1, finding: '例题难度梯度跨度过大', suggestion: '在例 2 与例 3 之间补一道中档题', result: 'pending', createdAt: '2026-10-18 09:14' },
  { id: 4, taskTitle: '七年级语文《春》说课稿', round: 1, finding: '说课环节缺少学情分析', suggestion: '补充七年级学生的朗读基础与意象理解难点', result: 'fixed', createdAt: '2026-10-17 20:06' },
  { id: 5, taskTitle: '化学实验《粗盐提纯》报告单', round: 1, finding: '实验步骤与安全提示顺序不一致', suggestion: '把「加热蒸发」的安全提示前移到步骤三', result: 'ignored', createdAt: '2026-10-17 16:32' },
  { id: 6, taskTitle: '英语 Unit 5 阅读课教案', round: 1, finding: '阅读策略目标表述笼统', suggestion: '改为「能通过找读定位主题句」等可观察目标', result: 'fixed', createdAt: '2026-10-17 10:19' },
  { id: 7, taskTitle: '英语 Unit 5 阅读课教案', round: 2, finding: '作业分层只分了两层', suggestion: '增加挑战层：续写课文结尾', result: 'pending', createdAt: '2026-10-17 10:21' },
  { id: 8, taskTitle: '信息技术《算法初步》教学设计', round: 1, finding: '缺少算法复杂度的直观类比', suggestion: '用「找字典」类比线性查找与二分查找', result: 'fixed', createdAt: '2026-10-18 09:37' },
]

export const queryDocTemplates = createMockQuery(mockDocTemplates, {
  keywordFields: ['name', 'owner'],
})
export const queryDocTemplateRecords = createMockQuery(mockDocTemplateRecords, {
  keywordFields: ['title', 'owner', 'template'],
})
export const queryDocTemplateInspections = createMockQuery(mockDocTemplateInspections, {
  keywordFields: ['taskTitle', 'finding', 'suggestion'],
})
