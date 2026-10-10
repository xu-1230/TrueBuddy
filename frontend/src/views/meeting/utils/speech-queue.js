/**
 * 跟页播报的纯逻辑（见 docs/18 §5.4、§6）。
 *
 * 单独成文件的原因只有两个：① 后端单条播报有字数上限，而整页 `remark` 常常超（docs/18 §9 风险 4），
 * 切段规则必须能被单测覆盖；② 编排的「先掐断再播」顺序是这套体系最容易出错的地方，
 * 把它从组合式函数里剥出来，才能在不挂载组件的前提下测（项目零测试依赖，见 docs/11 §3.1）。
 */

/** 后端 `tb.lecture.digital-human.max-text-length` 的默认值，前端只是兜底默认，实际以后端为准 */
export const DEFAULT_SEGMENT_CHARS = 1000

/** 句末标点：中英文都收进来，因为讲稿常见混排 */
const SENTENCE_ENDINGS = '。！？；!?;\n'

/** 次级断点：句子本身超长时优先在这些位置切，避免把一个逗号前后的语义劈开 */
const SOFT_BREAKS = '，、, 　'

/**
 * 把一段讲稿切成若干条「一次 say 能收下」的片段。
 *
 * 规则按优先级：
 * 1. 空文本（含只有空白）→ 空数组，调用方据此跳过这一页，不发出空播报；
 * 2. 整段不超上限 → 原样一段（**不做任何改写**，老师写的标点一个不动）；
 * 3. 否则按句末标点累加装箱，直到再加一句就超上限；
 * 4. 单句本身就超上限 → 先在次级断点处找最后的可切位置，找不到就硬切。
 *
 * @param {string} text 整段讲稿（应是纯文本；HTML 备注请先用 ppt/utils/slide-text.js 转plain）
 * @param {number} [maxChars] 单条上限
 * @returns {string[]} 有序片段，均非空且已 trim
 */
export function splitSpeech(text, maxChars = DEFAULT_SEGMENT_CHARS) {
  const limit = Number.isFinite(maxChars) && maxChars > 0 ? Math.floor(maxChars) : DEFAULT_SEGMENT_CHARS
  const source = typeof text === 'string' ? text.trim() : ''
  if (!source) return []
  if (source.length <= limit) return [source]

  const segments = []
  let buffer = ''

  const push = (piece) => {
    const trimmed = piece.trim()
    if (trimmed) segments.push(trimmed)
  }
  const flush = () => {
    if (!buffer) return
    push(buffer)
    buffer = ''
  }
  /** 一句太长：优先在窗口内最靠后的次级断点处切，找不到才硬切；返回没切完的尾巴 */
  const cutLongSentence = (sentence) => {
    let rest = sentence
    while (rest.length > limit) {
      let take = limit
      let best = -1
      for (const mark of SOFT_BREAKS) {
        const at = rest.lastIndexOf(mark, limit)
        // 断点落在窗口后半段才用：太靠前的断点会把片段切得粉碎
        if (at >= 0 && at + 1 > limit / 2 && at + 1 > best) best = at + 1
      }
      if (best > 0) take = best
      push(rest.slice(0, take))
      rest = rest.slice(take)
    }
    return rest
  }

  for (const sentence of splitByEndings(source)) {
    if (sentence.length > limit) {
      flush()
      buffer = cutLongSentence(sentence)
      continue
    }
    if (buffer && (buffer.length + sentence.length) > limit) flush()
    buffer += sentence
  }
  flush()
  return segments
}

/** 按句末标点切开，标点留在前一句尾（换行同样视为一句结束） */
function splitByEndings(source) {
  const parts = []
  let current = ''
  for (const char of source) {
    current += char
    if (SENTENCE_ENDINGS.includes(char)) {
      parts.push(current)
      current = ''
    }
  }
  if (current) parts.push(current)
  return parts
}

/**
 * 一次页变更要做的事：先掐断在途的这段，再播新页。
 *
 * 抽成纯函数是为了钉住顺序——「先 cancel 再播」写反了就会出现「上一页的尾音压在下一页开头」。
 *
 * @param {{ running: boolean, index: number, count: number }} current 当前播报状态
 * @param {{ index: number, segments: number }} next 即将播的页
 * @returns {{ cancelFirst: boolean, play: boolean, reason: string }}
 */
export function planPageChange(current, next) {
  if (!next || next.segments === 0) return { cancelFirst: Boolean(current?.running), play: false, reason: 'empty-page' }
  if (current && current.index === next.index) return { cancelFirst: false, play: false, reason: 'same-page' }
  return { cancelFirst: Boolean(current?.running), play: true, reason: 'advance' }
}
