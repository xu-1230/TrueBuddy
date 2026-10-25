import { createMockQuery, mockMeetingRecords } from './data'

/** 数字人形象配置（对应 public/live2d 下的模型资源） */
export const mockAvatarConfig = {
  model: 'DeepSeekWhale',
  voice: 'zh-CN-Xiaoxiao',
  speed: 1,
  scene: 'classroom',
  enabled: true,
  updatedAt: '2026-10-15 14:20',
  updatedBy: '系统管理员',
}

/**
 * 可选形象清单。
 *
 * 前 7 项是 Live2D 官方示例模型（免费可商用），
 * DeepSeekWhale 是社区二创的鲸鱼娘模型，素材 CC BY-NC-SA 4.0，只能非商业使用。
 * 具体地址与取景参数见 src/views/ppt/configs/avatar.js —— 两边要保持一致。
 */
export const AVATAR_MODEL_OPTIONS = [
  { value: 'DeepSeekWhale', label: 'DeepSeek 鲸鱼娘（默认）' },
  { value: 'DeepSeekDaFeiyu', label: 'DeepSeek 大肥鱼' },
  { value: 'Haru', label: 'Haru（示例）' },
  { value: 'Hiyori', label: 'Hiyori（示例）' },
  { value: 'Mao', label: 'Mao（示例）' },
  { value: 'Mark', label: 'Mark（示例）' },
  { value: 'Natori', label: 'Natori（示例）' },
  { value: 'Rice', label: 'Rice（示例）' },
  { value: 'Wanko', label: 'Wanko（示例）' },
]

export const AVATAR_VOICE_OPTIONS = [
  { value: 'zh-CN-Xiaoxiao', label: '晓晓 · 温和女声' },
  { value: 'zh-CN-Yunxi', label: '云希 · 青年男声' },
  { value: 'zh-CN-Xiaoyi', label: '晓伊 · 活泼女声' },
  { value: 'zh-CN-Yunjian', label: '云健 · 沉稳男声' },
]

export const AVATAR_SCENE_OPTIONS = [
  { value: 'classroom', label: '课堂讲授' },
  { value: 'tutoring', label: '课后答疑' },
  { value: 'meeting', label: '会议主持' },
]

/** 课件资源 */
export const mockSlideResources = [
  { id: 1, name: '八年级物理·浮力', owner: '林小满', slideCount: 24, size: 8_642_311, status: 'ready', updatedAt: '2026-10-17 16:22' },
  { id: 2, name: '高二数学·导数应用', owner: '陈可', slideCount: 31, size: 12_204_882, status: 'ready', updatedAt: '2026-10-17 11:04' },
  { id: 3, name: '七年级语文·春', owner: '张伟', slideCount: 18, size: 5_118_006, status: 'ready', updatedAt: '2026-10-16 20:31' },
  { id: 4, name: '九年级化学·粗盐提纯', owner: '李妍', slideCount: 22, size: 9_980_334, status: 'processing', updatedAt: '2026-10-16 15:47' },
  { id: 5, name: '高一英语·Unit 5 Reading', owner: '王强', slideCount: 27, size: 7_882_411, status: 'ready', updatedAt: '2026-10-15 09:12' },
  { id: 6, name: '信息技术·算法初步', owner: '赵琳', slideCount: 35, size: 15_402_119, status: 'ready', updatedAt: '2026-10-14 18:03' },
  { id: 7, name: '高中生物·细胞结构', owner: '孙宇', slideCount: 20, size: 6_128_774, status: 'failed', updatedAt: '2026-10-14 10:55' },
  { id: 8, name: '小学语文·古诗文诵读', owner: '周洁', slideCount: 16, size: 4_402_119, status: 'draft', updatedAt: '2026-10-13 14:26' },
]

/** 会议记录（PPT 智慧课堂维度） */
export const mockPptMeetings = mockMeetingRecords

export const querySlideResources = createMockQuery(mockSlideResources, {
  keywordFields: ['name', 'owner'],
})
export const queryPptMeetings = createMockQuery(mockPptMeetings, {
  keywordFields: ['title', 'host', 'meetingNo'],
})

/** 课堂数据统计 */
export const mockClassStats = {
  summary: [
    { key: 'sessions', label: '本周开课', value: 24, hint: '较上周 +3 场', icon: 'meeting', tone: 'primary' },
    { key: 'minutes', label: '累计时长', value: 912, hint: '约 15.2 小时', icon: 'log', tone: 'success' },
    { key: 'attendance', label: '平均到课', value: 36, hint: '到课率 91%', icon: 'users', tone: 'info' },
  ],
  series: [
    { label: '第 1 周', sessions: 18, minutes: 720, attendance: 620 },
    { label: '第 2 周', sessions: 21, minutes: 810, attendance: 684 },
    { label: '第 3 周', sessions: 19, minutes: 735, attendance: 641 },
    { label: '第 4 周', sessions: 23, minutes: 880, attendance: 752 },
    { label: '第 5 周', sessions: 22, minutes: 845, attendance: 728 },
    { label: '第 6 周', sessions: 24, minutes: 912, attendance: 786 },
  ],
  byClass: [
    { className: '初二（3）班', sessions: 8, avgAttendance: 41, minutes: 312 },
    { className: '高二（1）班', sessions: 6, avgAttendance: 38, minutes: 245 },
    { className: '初一（5）班', sessions: 5, avgAttendance: 44, minutes: 198 },
    { className: '高一（4）班', sessions: 5, avgAttendance: 33, minutes: 157 },
  ],
}

/** 课堂互动统计 */
export const mockInteractionStats = {
  summary: [
    { key: 'messages', label: '互动消息', value: 1864, hint: '本周 320 条', icon: 'meeting', tone: 'primary' },
    { key: 'questions', label: '提问次数', value: 486, hint: '平均每课 20 次', icon: 'log', tone: 'success' },
    { key: 'handRaises', label: '举手次数', value: 231, hint: '较上周 +18%', icon: 'users', tone: 'warning' },
  ],
  series: [
    { label: '第 1 周', messages: 248, questions: 62, handRaises: 28 },
    { label: '第 2 周', messages: 286, questions: 71, handRaises: 33 },
    { label: '第 3 周', messages: 262, questions: 64, handRaises: 30 },
    { label: '第 4 周', messages: 318, questions: 84, handRaises: 39 },
    { label: '第 5 周', messages: 300, questions: 78, handRaises: 36 },
    { label: '第 6 周', messages: 320, questions: 86, handRaises: 41 },
  ],
  byClass: [
    { className: '初二（3）班', messages: 486, questions: 128, handRaises: 62 },
    { className: '高二（1）班', messages: 402, questions: 104, handRaises: 48 },
    { className: '初一（5）班', messages: 358, questions: 92, handRaises: 44 },
    { className: '高一（4）班', messages: 276, questions: 71, handRaises: 33 },
  ],
}
