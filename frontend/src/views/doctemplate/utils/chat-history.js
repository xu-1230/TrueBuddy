/**
 * 把后端聊天记录（doc_template_chat_message）映射为 AiChatPanel 的消息结构。
 *
 * - 服务端 id 统一加 `db-` 前缀，避免与本地 `local-` 自增 id 冲突（v-for key 必须唯一）；
 * - 助手消息优先使用结构化 blocks，缺失时回退为单段落；
 * - system 提示只保留文本，不显示时间。
 */
const toEpoch = (value) => {
  if (value == null || value === '') return null
  const ms = typeof value === 'number' ? value : Date.parse(value)
  return Number.isFinite(ms) ? ms : null
}

export function toChatMessage(row) {
  if (!row || !['user', 'assistant', 'system'].includes(row.role)) return null

  let content
  if (row.role === 'assistant') {
    content =
      Array.isArray(row.blocks) && row.blocks.length
        ? row.blocks
        : [{ type: 'p', text: row.content || '已处理。' }]
  } else {
    content = row.content || ''
  }

  const message = {
    id: `db-${row.id}`,
    role: row.role,
    content,
    at: row.role === 'system' ? null : toEpoch(row.createTime),
    superseded: Boolean(row.superseded),
    error: Boolean(row.error),
  }

  if (row.role === 'user') {
    message.delivered = true
    message.selections = Array.isArray(row.selections) ? row.selections : []
  }
  if (row.role === 'assistant') {
    message.versionId = row.versionId ?? null
  }
  return message
}

export function toChatMessages(rows) {
  return Array.isArray(rows) ? rows.map(toChatMessage).filter(Boolean) : []
}
