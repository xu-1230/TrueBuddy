import { createMockQuery, mockKbDocuments } from './data'

/** 用户知识库空间（管理员视角；documents 供展开行演示） */
export const mockKnowledgeSpaces = [
  {
    id: 1, name: '初中物理', owner: '林小满', visibility: 'team', docCount: 26, chunkCount: 486, storageBytes: 128_642_311, status: 'active', createdAt: '2026-04-12 09:30',
    documents: mockKbDocuments.filter((doc) => doc.space === '初中物理'),
  },
  {
    id: 2, name: '高中数学', owner: '陈可', visibility: 'private', docCount: 18, chunkCount: 342, storageBytes: 86_204_882, status: 'active', createdAt: '2026-04-18 14:02',
    documents: mockKbDocuments.filter((doc) => doc.space === '高中数学'),
  },
  {
    id: 3, name: '高中历史', owner: '张伟', visibility: 'team', docCount: 14, chunkCount: 208, storageBytes: 34_118_006, status: 'active', createdAt: '2026-05-06 09:12',
    documents: mockKbDocuments.filter((doc) => doc.space === '高中历史'),
  },
  {
    id: 4, name: '初中化学', owner: '李妍', visibility: 'team', docCount: 11, chunkCount: 156, storageBytes: 62_006_400, status: 'active', createdAt: '2026-05-20 16:48',
    documents: mockKbDocuments.filter((doc) => doc.space === '初中化学'),
  },
  {
    id: 5, name: '高中英语', owner: '王强', visibility: 'private', docCount: 22, chunkCount: 398, storageBytes: 47_334_118, status: 'active', createdAt: '2026-06-01 11:36',
    documents: mockKbDocuments.filter((doc) => doc.space === '高中英语'),
  },
  {
    id: 6, name: '信息技术', owner: '赵琳', visibility: 'public', docCount: 9, chunkCount: 88, storageBytes: 158_824_110, status: 'active', createdAt: '2026-06-14 08:55',
    documents: mockKbDocuments.filter((doc) => doc.space === '信息技术'),
  },
  {
    id: 7, name: '高中生物', owner: '孙宇', visibility: 'team', docCount: 12, chunkCount: 174, storageBytes: 64_021_190, status: 'active', createdAt: '2026-07-08 15:20',
    documents: mockKbDocuments.filter((doc) => doc.space === '高中生物'),
  },
  {
    id: 8, name: '小学语文', owner: '周洁', visibility: 'public', docCount: 16, chunkCount: 244, storageBytes: 28_774_002, status: 'archived', createdAt: '2026-08-19 10:05',
    documents: mockKbDocuments.filter((doc) => doc.space === '小学语文'),
  },
]

export const queryKnowledgeSpaces = createMockQuery(mockKnowledgeSpaces, {
  keywordFields: ['name', 'owner'],
})

/** 知识库创建统计 */
export const mockKnowledgeCreationStats = {
  summary: [
    { key: 'spaces', label: '空间总数', value: 128, hint: '本月新增 9 个', icon: 'book', tone: 'primary' },
    { key: 'documents', label: '文档总数', value: 348, hint: '本月新增 42 份', icon: 'doc', tone: 'success' },
    { key: 'active', label: '活跃空间', value: 96, hint: '近 30 天有更新', icon: 'users', tone: 'info' },
  ],
  series: [
    { label: '3 月', spaces: 4, documents: 18 },
    { label: '4 月', spaces: 6, documents: 26 },
    { label: '5 月', spaces: 8, documents: 31 },
    { label: '6 月', spaces: 7, documents: 28 },
    { label: '7 月', spaces: 5, documents: 22 },
    { label: '8 月', spaces: 9, documents: 39 },
    { label: '9 月', spaces: 11, documents: 47 },
    { label: '10 月', spaces: 9, documents: 42 },
  ],
}

/** 存储用量统计 */
export const mockKnowledgeStorageStats = {
  totalBytes: 610_412_019,
  quotaBytes: 1_073_741_824,
  bySpace: [
    { space: '信息技术', documents: 9, chunks: 88, bytes: 158_824_110 },
    { space: '初中物理', documents: 26, chunks: 486, bytes: 128_642_311 },
    { space: '高中数学', documents: 18, chunks: 342, bytes: 86_204_882 },
    { space: '高中生物', documents: 12, chunks: 174, bytes: 64_021_190 },
    { space: '初中化学', documents: 11, chunks: 156, bytes: 62_006_400 },
    { space: '高中英语', documents: 22, chunks: 398, bytes: 47_334_118 },
    { space: '高中历史', documents: 14, chunks: 208, bytes: 34_118_006 },
    { space: '小学语文', documents: 16, chunks: 244, bytes: 28_774_002 },
  ],
  byType: [
    { type: 'pdf', bytes: 356_204_882, share: 0.58 },
    { type: 'pptx', bytes: 128_642_311, share: 0.21 },
    { type: 'docx', bytes: 86_204_882, share: 0.14 },
    { type: 'md', bytes: 39_359_944, share: 0.07 },
  ],
}
