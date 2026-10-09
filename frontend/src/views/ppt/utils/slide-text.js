/**
 * 幻灯片文本提取：把一页 PPT 里「看得见的字」按视觉顺序拼成纯文本。
 *
 * 为什么要它：AI 生成讲稿时，光有备注往往只有几个词（甚至没有），必须把这一页的正文一起给它，
 * 否则模型只能把备注改得更通顺，写不出「这页在讲什么」。
 *
 * 文字分布（类型清单见 `components/screen/ScreenElement.vue` 与 `components/thumbnail/ThumbnailElement.vue`）：
 * - `text` ：`content`（HTML）
 * - `shape`：`text.content`（HTML，形状上的文字，可空）
 * - `table`：`data` 二维数组，单元格取 `cell.text`（纯文本）
 * - `chart`：`data.labels`（类目轴）+ `data.legends`（图例）；`data.series` 是纯数字，不算文字
 * - `line` / `image` / `latex` / `video` / `audio`：无文字，跳过
 */

/** 有文字的元素类型（其余类型一律跳过） */
const TEXT_ELEMENT_TYPES = ['text', 'shape', 'table', 'chart']

/**
 * HTML 转纯文本。
 *
 * 与备注区原有实现同一口径：已是纯文本（用户编辑过的内容）**原样返回**，
 * 避免二次处理把换行与空格吃掉。
 */
export function htmlToPlainText(html) {
  if (!html) return ''
  if (!/<[a-z][^>]*>/i.test(html)) return html
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|li|div|h[1-6]|tr)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{2,}/g, '\n')
    .trim()
}

/** 单个元素的文字（无文字返回空串） */
function elementText(element) {
  if (!element) return ''
  switch (element.type) {
    case 'text':
      return htmlToPlainText(element.content)
    case 'shape':
      return htmlToPlainText(element.text?.content)
    case 'table': {
      const rows = Array.isArray(element.data) ? element.data : []
      return rows
        .map(row => (Array.isArray(row) ? row : [])
          .map(cell => cell?.text ?? '')
          .filter(text => String(text).trim())
          .join(' | '))
        .filter(rowText => rowText.trim())
        .join('\n')
    }
    case 'chart': {
      const labels = Array.isArray(element.data?.labels) ? element.data.labels : []
      const legends = Array.isArray(element.data?.legends) ? element.data.legends : []
      return [...labels, ...legends].filter(text => String(text).trim()).join(' ')
    }
    default:
      return ''
  }
}

/** 按视觉顺序（上→下、左→右）取出有文字的元素 */
function textElementsOf(slide) {
  const elements = Array.isArray(slide?.elements) ? slide.elements : []
  return elements
    .filter(element => element && TEXT_ELEMENT_TYPES.includes(element.type))
    .slice()
    .sort((a, b) => (a.top ?? 0) - (b.top ?? 0) || (a.left ?? 0) - (b.left ?? 0))
}

/**
 * 一页的正文（按视觉顺序拼接，块之间换行）。
 *
 * @param {object} slide 幻灯片
 * @returns {string} 纯文本；没有文字时返回空串
 */
export function extractSlideText(slide) {
  return textElementsOf(slide)
    .map(elementText)
    .map(text => text?.trim())
    .filter(Boolean)
    .join('\n')
    .trim()
}

/**
 * 一页的标题：优先取最靠上的纯文本框/形状文字的首行，没有再退到表格、图表。
 *
 * @param {object} slide 幻灯片
 * @returns {string} 标题（最长 40 字）；提取不到返回空串
 */
export function extractSlideTitle(slide) {
  const elements = textElementsOf(slide)
  const candidates = [
    ...elements.filter(element => element.type === 'text' || element.type === 'shape'),
    ...elements.filter(element => element.type !== 'text' && element.type !== 'shape'),
  ]
  for (const element of candidates) {
    const firstLine = elementText(element)
      .split('\n')
      .map(line => line.trim())
      .find(Boolean)
    if (firstLine) return firstLine.slice(0, 40)
  }
  return ''
}

/**
 * 把要生成的页转成接口入参。
 *
 * @param {Array<{ index: number, slide: object }>} entries 页码（1 基）+ 幻灯片
 * @returns {Array<{ index: number, title: string, text: string, remark: string }>}
 */
export function buildSpeechPages(entries) {
  return (entries ?? []).map(({ index, slide }) => ({
    index,
    title: extractSlideTitle(slide),
    text: extractSlideText(slide),
    remark: htmlToPlainText(slide?.remark),
  }))
}
