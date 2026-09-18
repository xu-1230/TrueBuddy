/**
 * 作业批改 —— 演示模式数据层（后端未接入前使用）
 *
 * 说明：
 * - 批改结果为本地模拟生成（得分 / 等级 / 评语），并写入 localStorage 作为「历史记录」；
 * - 文本类文件（txt / md / csv / json）会读取真实内容作为「作业原文」，
 *   Word / PDF 等二进制格式由前端给出占位说明（解析需要后端能力）；
 * - 接入后端时，把 HomeworkGradingView 中的调用替换为 `@/api/homework` 即可。
 */

import { childrenOf, nodeMeta, orderedNodes, traitsOf } from '@/views/homework/utils/workflows'

/** 演示历史记录存储键 */
const HISTORY_KEY = 'tb-homework-history-demo'
/** 历史记录保留条数 */
const HISTORY_LIMIT = 20
/** 未知学生占位 */
const UNKNOWN_STUDENT = '未知'
/** 原文入库长度上限 */
const MAX_TEXT_LENGTH = 20000
/** 可读取正文的文本类扩展名 */
const TEXT_EXTENSIONS = new Set(['txt', 'md', 'markdown', 'csv', 'json'])
/** 文件名噪音词（作业 / 实验 / 学号 / 日期等） */
const NOISE_PATTERN = /(第\s*\d+\s*次|作业|实验|练习|报告|试卷|说明|文档|资料|讲义|讲稿|目录|模板|参考答案|homework|hw|final|answer|答案|副本|\d{2,})/gi
/** 分隔符 */
const SEPARATOR_PATTERN = /[\s_\-—–+&()（）[\]【】,，.。]+/

/** 演示用知识库（接口不可用时的兜底数据） */
export const DEMO_KB_LIST = [
  { id: 'demo-software', name: '软件工程学 · 课程知识库', description: '教材要点与评分细则', documentCount: 18 },
  { id: 'demo-structure', name: '数据结构 · 习题答案库', description: '课后习题参考答案', documentCount: 12 },
  { id: 'demo-database', name: '数据库原理 · 实验指导', description: '实验报告规范与样例', documentCount: 9 },
]

/** 演示历史记录种子（首次进入即可看到历史记录抽屉的效果） */
const SEED_TASKS = [
  {
    id: 3,
    title: '以满分 100 分评分，重点考查需求分析的完整性与数据流图的规范性',
    kb: '软件工程学 · 课程知识库',
    workflow: '标准批改作业',
    time: '2026-09-12 15:20',
    status: '成功',
    recordCount: 3,
    fileNames: ['2021010101-张三-需求分析作业.docx', '2021010102-李四-需求分析作业.docx', '2021010103-王五-需求分析作业.docx'],
  },
  {
    id: 2,
    title: '按题号逐题点评，指出每一步的失分点与正确思路',
    kb: '数据结构 · 习题答案库',
    workflow: '标准批改作业',
    time: '2026-09-11 09:05',
    status: '成功',
    recordCount: 2,
    fileNames: ['第3章习题-赵六.md', '第3章习题-孙七.md'],
  },
  {
    id: 1,
    title: '只给反馈不打分，重点指出改进方向',
    kb: '默认知识库',
    workflow: '标准批改作业',
    time: '2026-09-09 20:41',
    status: '部分成功',
    recordCount: 2,
    fileNames: ['数据库实验报告-周八.docx', '数据库实验报告-吴九.pdf'],
  },
]

/** 评语开头（与模板组合，避免同一档位评语雷同） */
const COMMENT_OPENINGS = ['整体来看，', '从批阅情况看，', '通读全文，', '本次作业']

/** 评语模板：按分数档位取用，拼装成「亮点 + 问题 + 建议」 */
const COMMENT_TEMPLATES = {
  excellent: [
    {
      highlight: '对核心概念的表述准确完整，论述层次分明，能够结合课程知识点展开说明。',
      issue: '个别结论只给出结果，缺少关键推导或数据支撑。',
      advice: '建议补充关键步骤的依据，并给出一处课堂案例作为佐证，形成完整论证链条。',
    },
    {
      highlight: '结构清晰、要点齐全，术语使用规范，能看出对本章内容做了系统梳理。',
      issue: '偶有表述偏口语化，部分说明可以更贴近教材定义。',
      advice: '建议统一使用教材术语，并在每个要点后用一句话点明其作用。',
    },
    {
      highlight: '知识迁移能力较强，能把本章方法用到题目场景中，分析有条理。',
      issue: '对边界情况讨论不足，结论的适用范围没有交代清楚。',
      advice: '建议补充适用条件与例外情形的说明，进一步提升答案的严谨性。',
    },
  ],
  good: [
    {
      highlight: '主要知识点掌握到位，整体思路正确，论述基本完整。',
      issue: '对部分概念的界定不够严谨，个别环节之间缺少过渡。',
      advice: '建议补充概念的定义与适用场景，并在章节之间增加承上启下的说明。',
    },
    {
      highlight: '答题态度认真，覆盖面较全，能够按题目要求逐项回答。',
      issue: '分析深度略显不足，多停留在现象描述层面。',
      advice: '建议采用「现象—原因—对策」的结构展开，每个结论都给出对应依据。',
    },
    {
      highlight: '框架搭建合理，关键步骤完整，图表使用基本规范。',
      issue: '图例与文字说明的对应关系不够清晰，个别标注缺失。',
      advice: '建议为每张图补充图例与简要说明，并检查标注是否与正文一致。',
    },
    {
      highlight: '能按题目要求组织答案，关键术语使用正确。',
      issue: '论述以罗列为主，要点之间缺少比较与归纳。',
      advice: '建议在每段结尾补一句小结，点明该要点与整体结论的关系。',
    },
  ],
  pass: [
    {
      highlight: '基本完成了题目要求，能抓住主要知识点。',
      issue: '存在概念混淆与要点遗漏，部分表述与教材不一致。',
      advice: '建议对照教材重新梳理本章概念体系，重点补足缺失的要点后再复盘一遍。',
    },
    {
      highlight: '答题有一定思路，能列出关键步骤。',
      issue: '论证过程跳跃，缺少必要的中间推导与结论归纳。',
      advice: '建议按「已知—求解—验证」的顺序完整书写推理过程，并补充结论说明。',
    },
    {
      highlight: '能按要求作答，主要环节都有涉及。',
      issue: '部分要点只写了结论，未展开说明其依据与作用。',
      advice: '建议每个要点补写 1-2 句解释，并标注对应的教材章节便于复查。',
    },
  ],
  weak: [
    {
      highlight: '有作答意识，能够列出部分相关内容。',
      issue: '主要概念理解偏差较大，答题内容与题目要求的对应关系不明确。',
      advice: '建议先复习本章基本概念与例题，按题目要点逐条重做，并对照参考答案订正。',
    },
    {
      highlight: '书写较为完整，按要求提交了作业。',
      issue: '关键知识点缺失较多，部分结论缺乏依据。',
      advice: '建议课后完成本章配套练习，整理概念对比表，下次作业前先自查要点是否齐全。',
    },
    {
      highlight: '部分小题作答方向正确。',
      issue: '整体完成度偏低，多数题目未给出完整解答。',
      advice: '建议按题目顺序逐题补全，先保证要点齐全，再逐步提升表述的准确性。',
    },
  ],
}

/* ========== 历史记录 ========== */

/** 读取历史记录（首次返回种子数据，随后读 localStorage） */
export function loadHistory() {
  const stored = readStore()
  if (stored) return stored
  const seeded = SEED_TASKS.map((task) => buildSeedTask(task))
  writeStore(seeded)
  return seeded
}

/** 新增一条历史记录（最新在前） */
export function pushHistory(task) {
  const list = [task, ...loadHistory()]
  writeStore(list.slice(0, HISTORY_LIMIT))
  return task
}

/** 生成一个新的任务号 */
export function nextTaskId() {
  const list = loadHistory()
  const max = list.reduce((value, item) => Math.max(value, Number(item.id) || 0), 0)
  return max + 1
}

function buildSeedTask(task) {
  const records = task.fileNames.map((fileName, index) => {
    const record = buildRecord(fileName, index, 180 * 1024 + index * 24 * 1024)
    record.originalText = binaryPlaceholder(fileName)
    return record
  })
  return {
    ...buildHistoryItem(task.id, { description: task.title, kbName: task.kb, workflowLabel: task.workflow }, records),
    time: task.time,
    status: task.status,
    records,
  }
}

function readStore() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) && parsed.length ? parsed : null
  } catch {
    return null
  }
}

function writeStore(list) {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(list))
  } catch {
    // 存储不可用时不影响演示流程
  }
}

/* ========== 模拟批改 ========== */

/**
 * 模拟一次批量批改：先模拟上传进度，再逐份产出结果。
 *
 * @param {File[]} files 作业文件
 * @param {object} config 批改配置（用于生成历史记录标题与工作流文案）
 * @param {{onUploadProgress?: Function, onRecord?: Function}} handlers
 * @returns {Promise<Array>} 逐份批改结果
 */
export async function gradeByFiles(files, config, handlers = {}) {
  const { onUploadProgress, onStage, onRecord } = handlers
  const stepCount = 10

  // 1. 模拟上传进度
  for (let step = 1; step <= stepCount; step += 1) {
    await sleep(60)
    onUploadProgress?.(Math.round((step / stepCount) * 100))
  }

  // 2. 按所选工作流的节点链路逐份执行（节点即批改步骤）
  const chain = orderedNodes(config?.workflow)
  const traits = traitsOf(config?.workflow)
  // 份数越多单步越快，整体耗时控制在可接受范围
  const speed = files.length > 6 ? 0.6 : 1
  // LLM 节点启用知识库上下文后，才有「上下文来源」可回显
  const kbName = traits?.kbEnabled ? (config?.kbName || '默认知识库') : ''

  const records = []
  for (let index = 0; index < files.length; index += 1) {
    const file = files[index]
    const score = scoreOf(file.name)

    for (let stepIndex = 0; stepIndex < chain.length; stepIndex += 1) {
      const node = chain[stepIndex]
      const kind = node.data?.kind
      const label = node.data?.label || nodeMeta(kind).label
      const duration = nodeMeta(kind).duration ?? 240

      // 迭代节点：把容器内的迭代体按份 / 按题重复执行，并回显迭代计数
      if (kind === 'iteration') {
        const children = childrenOf(config?.workflow?.nodes ?? [], node.id)
        const rounds = iterationRoundsOf(file.name)
        // 入口节点「迭代开始」只作起点，不计入实际执行步骤
        const body = (children.length ? children : [node])
          .filter((item) => item.data?.kind !== 'iteration-start')
        for (let round = 1; round <= rounds; round += 1) {
          for (let childIndex = 0; childIndex < body.length; childIndex += 1) {
            const child = body[childIndex]
            const childKind = child.data?.kind
            onStage?.({
              label: child.data?.label || nodeMeta(childKind).label,
              index: stepIndex + 1,
              total: chain.length,
              file: file.name,
              iteration: { index: round, total: rounds },
              ...(childKind === 'llm' ? { model: traits?.model || '', kbName } : null),
            })
            await sleep(Math.round(140 * speed))
          }
        }
        continue
      }

      onStage?.({
        label,
        index: stepIndex + 1,
        total: chain.length,
        file: file.name,
        // LLM 步骤额外带上模型与上下文，便于进度区回显
        ...(kind === 'llm' ? { model: traits?.model || '', kbName } : null),
      })
      await sleep(Math.round(duration * speed))
    }

    const record = await buildRecordAsync(file, index, traits, score, kbName)
    records.push(record)
    onRecord?.(record, records.length, files.length)
  }
  return records
}

/**
 * 组装历史记录条目（字段与后端 HistoryVO 一致，
 * 另附 description / exportFormat 供演示模式回显批改要求）
 */
export function buildHistoryItem(taskId, config, records) {
  return {
    id: taskId,
    title: truncate(config.description, 24),
    kb: config.kbName || '默认知识库',
    workflow: config.workflowLabel || '标准批改',
    time: formatNow(),
    status: records.every((record) => record.status === 1) ? '成功' : '部分成功',
    recordCount: records.length,
    workflowId: config.workflowId || '',
    description: config.description || '',
    exportFormat: config.exportFormat || 'xlsx',
  }
}

/* ========== 单份结果 ========== */

async function buildRecordAsync(file, index, traits, score, kbName) {
  const record = buildRecord(file.name, index, file.size, traits, score, kbName)
  record.originalText = await readOriginalText(file)
  return record
}

/** 生成一份批改结果（同一文件名 + 同一工作流下结果稳定，便于反复演示） */
function buildRecord(fileName, index, size, traits, presetScore, kbName = '') {
  const score = Number.isFinite(presetScore) ? presetScore : scoreOf(fileName)
  const grade = gradeOf(score)
  return {
    id: `${Date.now()}-${index}`,
    fileName,
    studentName: guessStudentName(fileName),
    score,
    grade,
    comment: composeComment(fileName, score),
    // 回显本次批改使用的模型与上下文来源
    model: traits?.model || '',
    kbName: traits?.kbEnabled ? kbName : '',
    originalText: '',
    fileId: null,
    fileUrl: null,
    size: size || 0,
    status: 1,
    errorMsg: '',
  }
}

/** 分数：按文件名散列取档位，保证同一份作业结果稳定 */
function scoreOf(fileName) {
  return baseScoreOf(fileName)
}

/** 迭代次数：同一份作业稳定在 3 ~ 5 次，模拟「逐题 / 逐段」的迭代体执行 */
function iterationRoundsOf(fileName) {
  return 3 + hashOf(`${fileName}#rounds`, 3)
}

/**
 * 基础分档：按文件名散列出档位，再在档位内取值，
 * 保证同一份作业结果稳定，同时一批作业能覆盖优秀 / 良好 / 及格 / 待改进各档
 */
function baseScoreOf(fileName) {
  const bucket = hashOf(fileName, 100)
  if (bucket < 14) return 90 + hashOf(`${fileName}#a`, 9)
  if (bucket < 47) return 80 + hashOf(`${fileName}#b`, 10)
  if (bucket < 88) return 62 + hashOf(`${fileName}#c`, 18)
  return 50 + hashOf(`${fileName}#d`, 10)
}

function gradeOf(score) {
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 60) return '及格'
  return '待改进'
}

function composeComment(fileName, score) {
  const tier = score >= 90 ? 'excellent' : score >= 80 ? 'good' : score >= 60 ? 'pass' : 'weak'
  const list = COMMENT_TEMPLATES[tier]
  const template = list[hashOf(fileName, list.length)]
  const opening = COMMENT_OPENINGS[hashOf(`${fileName}@open`, COMMENT_OPENINGS.length)]
  const student = guessStudentName(fileName)
  const prefix = student === UNKNOWN_STUDENT ? '本次作业' : `${student}同学`
  return `${prefix}：${opening}${template.highlight}${template.issue}${template.advice}`
}

function hashOf(text, modulo) {
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 17 + text.charCodeAt(i)) % 100000
  }
  return hash % modulo
}

/** 文本类文件读取真实正文，二进制文档给出占位说明 */
async function readOriginalText(file) {
  if (TEXT_EXTENSIONS.has(getExt(file.name))) {
    try {
      const text = await file.text()
      if (text.trim()) return text.slice(0, MAX_TEXT_LENGTH)
    } catch {
      // 读取失败时回落到占位说明
    }
  }
  return binaryPlaceholder(file.name)
}

/** 二进制文档的原文占位说明 */
function binaryPlaceholder(fileName) {
  return `（演示模式）「${fileName}」为 Word / PDF 等二进制格式，前端无法解析正文内容。\n\n`
    + '接入后端后，该区域将展示后端解析出的作业原文（含段落与表格结构），'
    + '并支持在此逐条对照评语查看失分点。'
}

/** 由文件名推测学生姓名 */
function guessStudentName(fileName) {
  const base = stripExtension(fileName).replace(NOISE_PATTERN, ' ').trim()
  const tokens = base.split(SEPARATOR_PATTERN).filter(Boolean)
  const chinese = tokens.filter((token) => /^[\u4e00-\u9fa5]{2,4}$/.test(token))
  if (chinese.length) return chinese[chinese.length - 1]
  // 兜底：单个 token 需含中文或字母且长度合理，避免把「1」「2」这类编号当成姓名
  if (tokens.length === 1 && tokens[0].length >= 2 && tokens[0].length <= 20
    && /[\u4e00-\u9fa5A-Za-z]/.test(tokens[0])) {
    return tokens[0]
  }
  return UNKNOWN_STUDENT
}

/* ========== 工具 ========== */

function stripExtension(fileName) {
  const dot = fileName.lastIndexOf('.')
  return dot <= 0 ? fileName : fileName.slice(0, dot)
}

function getExt(fileName) {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}

function truncate(text, max) {
  const value = (text || '').replace(/\s+/g, ' ').trim()
  if (!value) return '未命名批改任务'
  return value.length <= max ? value : `${value.slice(0, max)}…`
}

function formatNow() {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} `
    + `${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
}
