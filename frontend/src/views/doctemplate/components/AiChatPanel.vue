<template>
  <aside class="ai-chat" aria-label="AI 助手对话">
    <h2 class="ai-chat__sr-only">AI 助手</h2>

    <div class="ai-chat__body">
      <div
        ref="listRef"
        class="ai-chat__list"
        role="log"
        aria-live="polite"
        aria-label="AI 对话消息"
        @scroll.passive="updateScrollState"
      >
        <div v-if="historyLoading && !messages.length" class="chat-system">正在加载聊天记录…</div>

        <div v-if="messages.length" class="chat-day" aria-hidden="true">{{ dayLabel }}</div>

        <div v-if="!messages.length && !historyLoading && !historyError" class="chat-system">
          {{ emptyHint }}
        </div>

        <div v-if="!messages.length && !historyLoading && historyError" class="chat-system">
          {{ historyError }}
        </div>

        <template v-for="message in messages" :key="message.id">
          <div v-if="message.role === 'system'" class="chat-system">
            {{ message.content }}
          </div>

          <div
            v-else
            class="chat-msg"
            :class="[
              `chat-msg--${message.role}`,
              { 'chat-msg--invalid': message.superseded, 'chat-msg--error': message.error },
            ]"
          >
            <span
              v-if="message.role === 'assistant'"
              class="chat-msg__avatar"
              aria-hidden="true"
            >
              <el-icon :size="14"><MagicStick /></el-icon>
            </span>

            <div class="chat-msg__content">
              <template v-if="message.role === 'user'">
                <div v-if="message.selections?.length" class="chat-selections">
                  <span
                    v-for="selection in message.selections"
                    :key="selection.locator"
                    class="chat-selection"
                    :title="selection.label"
                  >
                    {{ selection.label }}
                  </span>
                </div>
                <div class="chat-msg__bubble">{{ message.content }}</div>
              </template>

              <template v-else>
                <template v-if="Array.isArray(message.content)">
                  <template v-for="(block, index) in message.content" :key="index">
                    <p v-if="block.type === 'p'" class="chat-block">{{ block.text }}</p>
                    <ul v-else-if="block.type === 'ul'" class="chat-block chat-list">
                      <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
                        {{ item }}
                      </li>
                    </ul>
                  </template>
                </template>
                <p v-else class="chat-block">{{ message.content }}</p>
              </template>

              <div class="chat-meta">
                <template v-if="message.role === 'user'">
                  <span class="chat-delivered">
                    <el-icon :size="10"><Check /></el-icon>
                    已送达
                  </span>
                  <time class="chat-time" :datetime="formatIso(message.at)">
                    {{ formatClock(message.at) }}
                  </time>
                </template>
                <template v-else>
                  <time class="chat-time" :datetime="formatIso(message.at)">
                    {{ formatClock(message.at) }}
                  </time>
                  <span v-if="message.superseded" class="chat-invalid">已失效</span>
                  <button
                    v-else-if="message.versionId"
                    type="button"
                    class="chat-undo"
                    :disabled="undoing || streaming || historyLoading"
                    :aria-label="`撤回这次修改（${formatClock(message.at)}）`"
                    title="撤回这次修改"
                    @click="handleUndo(message)"
                  >
                    <el-icon :size="12"><RefreshLeft /></el-icon>
                  </button>
                </template>
              </div>
            </div>
          </div>
        </template>

        <div v-if="streaming" class="chat-msg chat-msg--assistant">
          <span class="chat-msg__avatar" aria-hidden="true">
            <el-icon :size="14"><MagicStick /></el-icon>
          </span>
          <span class="chat-typing" aria-hidden="true">
            <span></span><span></span><span></span>
          </span>
          <span class="chat-stage">{{ stageText || '正在处理…' }}</span>
        </div>
      </div>

      <button
        v-if="showScrollToBottom"
        type="button"
        class="ai-chat__scroll"
        aria-label="回到最新消息"
        title="回到最新消息"
        @click="scrollToBottom(true)"
      >
        <el-icon :size="14"><ArrowDownBold /></el-icon>
      </button>
    </div>

    <footer class="ai-chat__composer-wrap">
      <div class="ai-chat__composer">
        <div v-if="selections?.length" class="ai-chat__selections">
          <span
            v-for="selection in selections"
            :key="selection.locator"
            class="ai-chat__selection"
            :title="selection.label"
          >
            <span class="ai-chat__selection-label">{{ selection.label }}</span>
            <button
              type="button"
              class="ai-chat__selection-remove"
              :aria-label="`移除选中元素：${selection.label}`"
              @click="$emit('remove-selection', selection.locator)"
            >
              ×
            </button>
          </span>
        </div>

        <textarea
          ref="inputRef"
          v-model="draft"
          class="ai-chat__input"
          rows="1"
          :disabled="!canEdit"
          :aria-label="`就${documentName || '参考文档'}向 AI 提问`"
          :placeholder="inputPlaceholder"
          @input="resizeInput"
          @keydown.enter.exact.prevent="send"
        ></textarea>

        <div class="ai-chat__actions">
          <button
            type="button"
            class="ai-chat__send"
            :disabled="!canSend"
            aria-label="发送"
            @click="send"
          >
            <el-icon :size="15"><ArrowUpBold /></el-icon>
          </button>
        </div>
      </div>

      <div class="ai-chat__hint">
        <span class="ai-chat__status">AI 编辑</span>
        <span class="ai-chat__keys">Enter 发送 · Shift + Enter 换行</span>
      </div>
    </footer>
  </aside>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDownBold,
  ArrowUpBold,
  Check,
  MagicStick,
  RefreshLeft,
} from '@element-plus/icons-vue'
import { readEventStream } from '@/utils/event-stream'
import { DOC_CHAT_API, getDocChatHistory, undoDocEdit } from '@/api/doctemplate'
import { toChatMessage, toChatMessages } from '../utils/chat-history'

const props = defineProps({
  /** 根文件 id（doc_template_file.id，即工作区） */
  rootFileId: { type: [Number, String], default: null },
  /** 当前正在查看的参考文档名 */
  documentName: { type: String, default: '' },
  /** 已选中的文档元素（上下文提示，可为空） */
  selections: { type: Array, default: () => [] },
  /** 是否登录；未登录时只能查看，不能编辑 */
  canEdit: { type: Boolean, default: false },
})

const emit = defineEmits(['remove-selection', 'clear-selections', 'version-changed'])

const INPUT_MAX_LINES = 5
const NEAR_BOTTOM_PX = 48
const MAX_HISTORY = 12
const HISTORY_LIMIT = 200

let messageSeed = 0
const nextId = () => `local-${(messageSeed += 1)}`

const messages = ref([])
const draft = ref('')
const streaming = ref(false)
const stageText = ref('')
const undoing = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
let historySeq = 0
const listRef = ref(null)
const inputRef = ref(null)
const isNearBottom = ref(true)
const showScrollToBottom = ref(false)
let abortController = null

const canSend = computed(
  () =>
    props.canEdit &&
    !streaming.value &&
    !undoing.value &&
    !historyLoading.value &&
    Boolean(draft.value.trim()) &&
    Boolean(props.rootFileId),
)

/** 顶部日期分隔：取最早一条带时间的消息；加载到历史记录时不误标「今天」 */
const dayLabel = computed(() => {
  const first = messages.value.find((message) => message.at)
  if (!first) return '今天'
  const date = new Date(first.at)
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  return sameDay ? '今天' : `${date.getMonth() + 1}月${date.getDate()}日`
})

const emptyHint = computed(() => {
  if (!props.canEdit) {
    return '请先登录后再编辑文档。'
  }
  const name = props.documentName || '参考文档'
  return `已加载《${name}》。可以直接描述修改要求，或先在左侧选择元素缩小范围。`
})

const inputPlaceholder = computed(() => {
  if (!props.canEdit) return '登录后可编辑文档'
  return props.selections?.length
    ? '描述要如何修改选中的元素…'
    : '描述修改要求，未选择元素时按全文处理…'
})

function formatClock(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

function formatIso(timestamp) {
  return timestamp ? new Date(timestamp).toISOString() : ''
}

function updateScrollState() {
  const el = listRef.value
  if (!el) return
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  isNearBottom.value = distance <= NEAR_BOTTOM_PX
  showScrollToBottom.value = !isNearBottom.value
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = listRef.value
    if (!el) return
    if (!force && !isNearBottom.value) return
    el.scrollTop = el.scrollHeight
    updateScrollState()
  })
}

function resizeInput() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  const styles = window.getComputedStyle(el)
  const lineHeight = Number.parseFloat(styles.lineHeight) || 20
  const padding =
    (Number.parseFloat(styles.paddingTop) || 0) +
    (Number.parseFloat(styles.paddingBottom) || 0)
  const maxHeight = lineHeight * INPUT_MAX_LINES + padding
  el.style.height = `${Math.min(el.scrollHeight, maxHeight)}px`
  el.style.overflowY = el.scrollHeight > maxHeight ? 'auto' : 'hidden'
  nextTick(updateScrollState)
}

/** 把结构化消息展平成送给 LLM 的历史文本 */
function flattenContent(content) {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  return content
    .map((block) => {
      if (block?.type === 'ul') {
        return (block.items || []).map((item) => `- ${item}`).join('\n')
      }
      return block?.text || ''
    })
    .filter(Boolean)
    .join('\n')
}

/** 只把当前有效的 user/assistant 消息送给模型；失效/错误消息不参与 */
function buildHistory() {
  return messages.value
    .filter(
      (message) =>
        (message.role === 'user' || message.role === 'assistant') &&
        !message.superseded &&
        !message.error,
    )
    .slice(-MAX_HISTORY)
    .map((message) => ({ role: message.role, content: flattenContent(message.content) }))
    .filter((message) => message.content.trim())
}

function addErrorMessage(message) {
  messages.value.push({
    id: nextId(),
    role: 'assistant',
    content: [{ type: 'p', text: message }],
    at: Date.now(),
    error: true,
    versionId: null,
  })
  ElMessage.error(message)
  scrollToBottom()
}

function handleStreamEvent(event, payload) {
  if (event === 'status') {
    stageText.value = payload?.message || '正在处理…'
    scrollToBottom()
    return
  }
  if (event === 'assistant') {
    const reply = Array.isArray(payload?.reply) ? payload.reply : []
    messages.value.push({
      id: nextId(),
      role: 'assistant',
      content: reply.length ? reply : [{ type: 'p', text: '已处理。' }],
      at: Date.now(),
      versionId: payload?.versionId ?? null,
      summary: payload?.summary || '',
    })
    streaming.value = false
    stageText.value = ''
    scrollToBottom()
    return
  }
  if (event === 'version') {
    if (payload?.src) emit('version-changed', { src: payload.src })
    return
  }
  if (event === 'error') {
    addErrorMessage(payload?.message || '修改失败')
    streaming.value = false
    stageText.value = ''
    return
  }
  if (event === 'done') {
    streaming.value = false
    stageText.value = ''
    scrollToBottom()
  }
}

async function send() {
  if (!canSend.value) return
  const rootFileId = props.rootFileId
  if (!rootFileId) {
    ElMessage.warning('文档尚未就绪')
    return
  }
  const instruction = draft.value.trim()
  const history = buildHistory()
  // 单元格/合并单元格用表格级 hintLocator 作为 LLM 上下文，文本仍保留具体选中内容
  const selected = (props.selections || []).map((selection) => ({
    locator: selection.hintLocator || selection.locator,
    tag: selection.tag,
    label: selection.label,
    text: selection.text,
  }))

  messages.value.push({
    id: nextId(),
    role: 'user',
    content: instruction,
    at: Date.now(),
    delivered: true,
    selections: selected,
  })
  draft.value = ''
  emit('clear-selections')
  nextTick(resizeInput)
  scrollToBottom(true)

  streaming.value = true
  stageText.value = '正在分析…'
  abortController?.abort()
  abortController = new AbortController()
  try {
    await readEventStream(DOC_CHAT_API.chat, {
      body: { rootFileId, instruction, selections: selected, history },
      signal: abortController.signal,
      onEvent: handleStreamEvent,
    })
  } catch (error) {
    if (error?.name !== 'AbortError') {
      addErrorMessage(error?.message || '修改失败')
    }
  } finally {
    streaming.value = false
    stageText.value = ''
    abortController = null
    scrollToBottom()
  }
}

async function handleUndo(message) {
  if (
    !message?.versionId ||
    undoing.value ||
    streaming.value ||
    historyLoading.value ||
    !props.rootFileId
  )
    return
  const rootFileId = props.rootFileId
  const index = messages.value.indexOf(message)
  if (index < 0) return
  const laterEdits = messages.value
    .slice(index + 1)
    .filter(
      (item) => item.role === 'assistant' && item.versionId && !item.superseded && !item.error,
    ).length

  try {
    await ElMessageBox.confirm(
      laterEdits
        ? `撤回将同时丢弃之后 ${laterEdits} 次修改，确定？`
        : '确定撤回这次修改？',
      '撤回修改',
      {
        type: 'warning',
        confirmButtonText: '撤回',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  // 确认框期间可能已切换文档
  if (String(props.rootFileId) !== String(rootFileId)) return

  undoing.value = true
  try {
    const result = await undoDocEdit({ rootFileId, targetVersionId: message.versionId })
    emit('version-changed', { src: result?.src })

    // 先本地置失效并用服务端提示补齐，保证响应即时；随后以服务端记录为准重新加载
    const latestIndex = messages.value.indexOf(message)
    if (latestIndex >= 0) {
      messages.value.slice(latestIndex).forEach((item) => {
        item.superseded = true
      })
      const notice = result?.notice ? toChatMessage(result.notice) : null
      messages.value.push(
        notice || {
          id: nextId(),
          role: 'system',
          content: laterEdits
            ? `已撤回到上一版，之后 ${laterEdits} 条修改消息已失效。`
            : '已撤回到上一版。',
          at: null,
        },
      )
      scrollToBottom(true)
    }
    await loadHistory()
    ElMessage.success('已撤回本次修改')
  } catch (error) {
    ElMessage.error(error?.message || '撤回失败')
  } finally {
    undoing.value = false
  }
}

/** 按当前根文件加载历史；请求令牌防止切换文档后旧响应串档 */
async function loadHistory() {
  const rootFileId = props.rootFileId
  const seq = (historySeq += 1)
  historyError.value = ''
  if (!rootFileId || !props.canEdit) {
    messages.value = []
    historyLoading.value = false
    return
  }
  historyLoading.value = true
  try {
    const rows = await getDocChatHistory(rootFileId, HISTORY_LIMIT)
    if (seq !== historySeq || String(props.rootFileId) !== String(rootFileId)) return
    messages.value = toChatMessages(rows)
    nextTick(() => scrollToBottom(true))
  } catch (error) {
    if (seq !== historySeq) return
    historyError.value = error?.message || '聊天记录加载失败'
    ElMessage.warning(historyError.value)
  } finally {
    if (seq === historySeq) historyLoading.value = false
  }
}

watch(
  () => props.rootFileId,
  () => {
    historySeq += 1
    abortController?.abort()
    abortController = null
    messages.value = []
    draft.value = ''
    streaming.value = false
    stageText.value = ''
    undoing.value = false
    historyLoading.value = false
    historyError.value = ''
    isNearBottom.value = true
    showScrollToBottom.value = false
    nextTick(() => {
      resizeInput()
      scrollToBottom(true)
    })
    loadHistory()
  },
  { immediate: true },
)

onMounted(() => {
  nextTick(resizeInput)
})

onBeforeUnmount(() => {
  historySeq += 1
  abortController?.abort()
  abortController = null
})
</script>

<style scoped>
.ai-chat {
  flex: 0 0 380px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--tb-radius-xl);
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  overflow: hidden;
}

.ai-chat__body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.ai-chat__list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 14px 10px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--tb-border-strong) transparent;
}

.ai-chat__list::-webkit-scrollbar {
  width: 6px;
}

.ai-chat__list::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-border-strong);
}

.ai-chat__list::-webkit-scrollbar-track {
  background: transparent;
}

/* ========== 日期 / 系统消息 ========== */
.chat-day {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tb-text-tertiary);
  font-size: 10px;
  letter-spacing: var(--tb-tracking-wide);
}

.chat-day::before,
.chat-day::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--tb-border);
}

.chat-system {
  align-self: center;
  max-width: 92%;
  text-align: center;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
  line-height: var(--tb-leading-normal);
}

/* ========== 消息 ========== */
.chat-msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.chat-msg--user {
  justify-content: flex-end;
}

.chat-msg--invalid {
  opacity: 0.55;
}

.chat-msg__avatar {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-top: 2px;
  border-radius: 50%;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.chat-msg__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-msg--assistant .chat-msg__content {
  flex: 1;
}

.chat-msg--user .chat-msg__content {
  flex: 0 1 auto;
  align-items: flex-end;
  max-width: 84%;
}

.chat-msg__bubble {
  padding: 8px 12px;
  border-radius: var(--tb-radius-xl);
  color: var(--tb-text-primary);
  background: color-mix(in oklab, var(--tb-text-primary) 7%, var(--tb-bg-surface));
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-block {
  margin: 0 0 6px;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}

.chat-block:last-child {
  margin-bottom: 0;
}

.chat-msg--error .chat-block {
  color: var(--tb-danger);
}

.chat-list {
  padding-left: 1.15em;
}

.chat-list li {
  margin: 0 0 3px;
}

.chat-list li:last-child {
  margin-bottom: 0;
}

.chat-list li::marker {
  color: var(--tb-text-tertiary);
}

.chat-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  color: var(--tb-text-tertiary);
  font-size: 10px;
}

.chat-msg--user .chat-meta {
  justify-content: flex-end;
}

.chat-delivered {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.chat-time {
  font-variant-numeric: tabular-nums;
}

.chat-invalid {
  color: var(--tb-text-tertiary);
}

.chat-undo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.chat-undo:hover:not(:disabled) {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.chat-undo:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 选区 chips（composer 与用户消息共用外观） */
.chat-selections,
.ai-chat__selections {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}

.chat-selection,
.ai-chat__selection {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  line-height: 1.5;
}

.chat-selection {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat__selection-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat__selection-remove {
  flex: none;
  padding: 0 2px;
  border: 0;
  background: transparent;
  color: var(--tb-text-tertiary);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.ai-chat__selection-remove:hover {
  color: var(--tb-danger);
}

.chat-typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
}

.chat-typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--tb-text-tertiary);
  animation: chat-typing 1s ease-in-out infinite;
}

.chat-typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.chat-typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.chat-stage {
  padding-top: 6px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

/* ========== 滚到底按钮 ========== */
.ai-chat__scroll {
  position: absolute;
  right: 12px;
  bottom: 10px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 50%;
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  box-shadow: var(--tb-shadow-2);
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-motion-ease),
    background var(--tb-dur-2) var(--tb-motion-ease);
}

.ai-chat__scroll:hover {
  color: var(--tb-text-primary);
  background: var(--tb-bg-raised);
}

/* ========== Composer ========== */
.ai-chat__composer-wrap {
  flex: none;
  padding: 0 12px 10px;
}

.ai-chat__composer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  background: var(--tb-bg-raised);
  transition:
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    box-shadow var(--tb-dur-2) var(--tb-motion-ease);
}

.ai-chat__composer:focus-within {
  border-color: var(--tb-primary-border);
  box-shadow: 0 0 0 3px var(--tb-ring);
}

.ai-chat__input {
  width: 100%;
  min-height: 24px;
  max-height: 120px;
  padding: 4px 2px;
  border: 0;
  background: transparent;
  color: var(--tb-text-primary);
  font-family: inherit;
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
  resize: none;
  outline: none;
  overflow-y: hidden;
}

.ai-chat__input::placeholder {
  color: var(--tb-text-tertiary);
}

.ai-chat__input:disabled {
  cursor: not-allowed;
}

.ai-chat__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.ai-chat__send {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  cursor: pointer;
  transition:
    background var(--tb-dur-2) var(--tb-motion-ease),
    opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.ai-chat__send:hover:not(:disabled) {
  background: var(--tb-primary-hover);
}

.ai-chat__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.ai-chat__hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 8px;
  padding: 6px 2px 0;
  color: var(--tb-text-tertiary);
  font-size: 10px;
}

.ai-chat__status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ai-chat__status::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--tb-text-tertiary);
}

.ai-chat__keys {
  font-variant-numeric: tabular-nums;
}

/* ========== 无障碍 ========== */
.ai-chat button:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.ai-chat__sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  white-space: nowrap;
  border: 0;
  clip-path: inset(50%);
}

@keyframes chat-typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

/* 窄屏：页面会把聊天堆到预览下方，这里改成全宽固定高度 */
@media (max-width: 1024px) {
  .ai-chat {
    flex: 0 0 auto;
    width: 100%;
    height: min(480px, 60vh);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-typing span {
    animation: none;
    opacity: 0.6;
  }

  .ai-chat__composer,
  .ai-chat__scroll,
  .ai-chat__send {
    transition: none;
  }
}
</style>
