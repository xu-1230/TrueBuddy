/**
 * 讲稿按句切段（讲课工作项 P0）。
 *
 * 为什么必须有这一步：整页 remark 上限 1500 字（`PptProperties.maxRemarkChars`），
 * 而播报单条上限 1000 字（`DigitalHumanProperties.maxTextLength`）——整页照读必须拆段。
 *
 * 两个口径（docs「解决方案-04」§4）：
 * - 段长按 UTF-16 `length` 计：后端对 `text` 的校验就是 `String.length`，前端必须同口径；
 * - 按句切、贪心合并：段边界落在句读符后，念出来才有自然的停顿点，绝不在词中间断。
 *
 * 纯函数、零依赖，`node --test` 直接覆盖。
 */

/** 单段上限：与后端 `DigitalHumanProperties.maxTextLength` 对齐（P0 不读配置，写死同值） */
export const SEGMENT_MAX_CHARS = 1000

/** 句读符：出现在字符后面就算一句话结束（省略号按整体看待，逐字比对即可） */
const SENTENCE_ENDERS = new Set(['。', '！', '？', '；', '!', '?', ';', '…'])

/**
 * 把讲稿文本切成播报段。
 *
 * 规则（自上而下）：
 * 1. 清洗：统一换行、掐首尾空白；全空白返回 `[]`；
 * 2. 断句：句读符或换行处断开，句子各自 trim（换行不进段——TTS 的停顿由句读符给）；
 * 3. 贪心合并：相邻句子能塞进同一段就合并，塞不下才开新段；
 * 4. 单句超长硬切：没有任何句读的长句按 `limit` 硬切（按码点切，不切碎 emoji 等代理对）。
 *
 * @param {string} text 讲稿原文（纯文本，调用方先用 `htmlToPlainText` 清洗）
 * @param {number} [maxLen=SEGMENT_MAX_CHARS] 单段上限（UTF-16 length 口径）
 * @returns {string[]} 段数组，保序；每段非空且 `length <= maxLen`（硬切段按码点计，只短不长）
 */
export function splitIntoSegments(text, maxLen = SEGMENT_MAX_CHARS) {
  const limit = Number.isFinite(maxLen) && maxLen > 0 ? Math.floor(maxLen) : SEGMENT_MAX_CHARS
  const source = (text ?? '').replace(/\r\n?/g, '\n').trim()
  if (!source) return []

  // 断句：for...of 按码点迭代，句读符留在句尾
  const sentences = []
  let buffer = ''
  for (const ch of source) {
    buffer += ch
    if (ch === '\n' || SENTENCE_ENDERS.has(ch)) {
      const piece = buffer.trim()
      if (piece) sentences.push(piece)
      buffer = ''
    }
  }
  const tail = buffer.trim()
  if (tail) sentences.push(tail)

  // 合并与硬切
  const segments = []
  let current = ''
  for (const sentence of sentences) {
    if (sentence.length > limit) {
      // 超长句：先冲刷手头的半段，再按码点硬切（Array.from 保证代理对不被拆开）
      if (current) {
        segments.push(current)
        current = ''
      }
      const points = Array.from(sentence)
      for (let i = 0; i < points.length; i += limit) {
        segments.push(points.slice(i, i + limit).join(''))
      }
      continue
    }
    if (!current) {
      current = sentence
      continue
    }
    if (current.length + sentence.length <= limit) {
      current += sentence
      continue
    }
    segments.push(current)
    current = sentence
  }
  if (current) segments.push(current)
  return segments
}
