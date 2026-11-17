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
        <div class="chat-day" aria-hidden="true">今天</div>

        <template v-for="message in messages" :key="message.id">
          <div v-if="message.role === 'system'" class="chat-system">
            {{ message.content }}
          </div>

          <div v-else class="chat-msg" :class="`chat-msg--${message.role}`">
            <span
              v-if="message.role === 'assistant'"
              class="chat-msg__avatar"
              aria-hidden="true"
            >
              <el-icon :size="14"><MagicStick /></el-icon>
            </span>

            <div class="chat-msg__content">
              <div v-if="message.role === 'user'" class="chat-msg__bubble">
                {{ message.content }}
              </div>

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
                <span v-if="message.role === 'user'" class="chat-delivered">
                  <el-icon :size="10"><Check /></el-icon>
                  已送达
                </span>
                <time class="chat-time" :datetime="formatIso(message.at)">
                  {{ formatClock(message.at) }}
                </time>
              </div>
            </div>
          </div>
        </template>

        <div v-if="typing" class="chat-msg chat-msg--assistant" aria-label="AI 正在回复">
          <span class="chat-msg__avatar" aria-hidden="true">
            <el-icon :size="14"><MagicStick /></el-icon>
          </span>
          <span class="chat-typing" aria-hidden="true">
            <span></span><span></span><span></span>
          </span>
          <span class="ai-chat__sr-only">AI 正在回复</span>
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
        <textarea
          ref="inputRef"
          v-model="draft"
          class="ai-chat__input"
          rows="1"
          :aria-label="`就${documentName || '参考文档'}向 AI 提问`"
          placeholder="就参考文档提问…"
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
        <span class="ai-chat__status">模拟对话</span>
        <span class="ai-chat__keys">Enter 发送 · Shift + Enter 换行</span>
      </div>
    </footer>
  </aside>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDownBold, ArrowUpBold, Check, MagicStick } from '@element-plus/icons-vue'

const props = defineProps({
  /** 当前正在查看的参考文档名，用于上下文 chip 与 mock 文案 */
  documentName: { type: String, default: '' },
})

/** 输入框最多自动增长到 5 行，超过后内部滚动 */
const INPUT_MAX_LINES = 5
/** 距底部小于该距离视为「在底部附近」，新消息自动跟随 */
const NEAR_BOTTOM_PX = 48

let messageSeed = 0
let replyTimer = null
const nextId = () => (messageSeed += 1)

const minute = 60 * 1000

/** 把字符串或结构化块数组统一交给模板渲染 */
function buildInitialMessages(documentName) {
  const name = documentName || '参考文档'
  const now = Date.now()

  return [
    {
      id: nextId(),
      role: 'system',
      content: `已加载《${name}》，可以开始评审。`,
      at: null,
    },
    {
      id: nextId(),
      role: 'assistant',
      at: now - 5 * minute,
      content: [
        {
          type: 'p',
          text: '我先按「结构完整性 → 版式 → 表述」三层做了一轮检查，下面三点可以直接落地。',
        },
        {
          type: 'ul',
          items: [
            '结构：教学目标、学情分析、重难点、教学过程、板书设计、教学反思六个部分齐全。',
            '版式：课中实施表格的两列偏窄，建议加宽「教师活动 / 学生活动」，避免长句折行。',
            '表述：教学目标里的「了解 / 熟悉」建议改成可观测的行为动词，便于评价。',
          ],
        },
      ],
    },
    {
      id: nextId(),
      role: 'user',
      content: '先帮我改教学反思，控制在 3 条以内。',
      at: now - 4 * minute,
      delivered: true,
    },
    {
      id: nextId(),
      role: 'assistant',
      at: now - 3 * minute,
      content: [
        { type: 'p', text: '可以，保留原意、压到 3 条：' },
        {
          type: 'ul',
          items: [
            '分层练习完成度较好，下一步补充迁移场景的变式题。',
            'SQL Server 2012 演示环节时间偏紧，可把建库步骤前置为课前预习。',
            '板书与课堂小结的对应关系再明确一些，减少重复表述。',
          ],
        },
        { type: 'p', text: '如果你希望更贴近原文档的语气，我也可以再给一版更书面的表述。' },
      ],
    },
    {
      id: nextId(),
      role: 'user',
      content: '顺便看看板书设计有没有漏项。',
      at: now - 2 * minute,
      delivered: true,
    },
    {
      id: nextId(),
      role: 'assistant',
      at: now - 1 * minute,
      content: [
        {
          type: 'p',
          text: '板书覆盖了三级目录，但「迁移用户数据库」这一块缺要点标注。建议在第三级目录下补：',
        },
        {
          type: 'ul',
          items: ['迁移前的兼容性检查', '备份与还原顺序', '迁移后的验证 SQL'],
        },
        { type: 'p', text: '其余结构完整，可以直接使用。' },
      ],
    },
  ]
}

/** 用户提问后的演示回复池（按顺序循环） */
const MOCK_REPLIES = [
  [
    { type: 'p', text: '我按当前版面再看一遍，优先改这三处：' },
    {
      type: 'ul',
      items: [
        '把「教学反思」里的长句拆成短句，每条只保留一个改进动作。',
        '课中实施表格的「教师活动 / 学生活动」列宽再均衡一些。',
        '板书设计与课堂小结的对应关系补一句过渡说明。',
      ],
    },
    { type: 'p', text: '这些改动不会动到原文档的结构，可以直接在生成结果里替换。' },
  ],
  [
    { type: 'p', text: '可以，这一版我更偏向「少改但改准」：' },
    {
      type: 'ul',
      items: [
        '教学目标统一用可观测动词，避免「了解 / 熟悉」这类模糊表述。',
        '教学反思压到 3 条以内，按「完成度 → 问题 → 下一步」排序。',
        '迁移用户数据库补上兼容性检查和验证 SQL 两个要点。',
      ],
    },
  ],
  [
    { type: 'p', text: '我核对了结构、版式、表述三层，结论是：' },
    {
      type: 'ul',
      items: [
        '结构：六个部分齐全，不需要增删章节。',
        '版式：表格列宽和段间距需要微调。',
        '表述：个别动词可以更具体，其余可以直接使用。',
      ],
    },
    { type: 'p', text: '如果要继续，我可以只给「修改前 / 修改后」对照版。' },
  ],
]

const messages = ref(buildInitialMessages(props.documentName))
const draft = ref('')
const typing = ref(false)
const listRef = ref(null)
const inputRef = ref(null)
const replyIndex = ref(0)
const isNearBottom = ref(true)
const showScrollToBottom = ref(false)

const canSend = computed(() => Boolean(draft.value.trim()) && !typing.value)

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

/** force=true 用于发送消息 / 切换文档 / 点击回底按钮，无条件滚到底 */
function scrollToBottom(force = false) {
  nextTick(() => {
    const el = listRef.value
    if (!el) return
    if (!force && !isNearBottom.value) return
    el.scrollTop = el.scrollHeight
    updateScrollState()
  })
}

/** 输入框自增长：先归零再按 scrollHeight 取高，超过 5 行后内部滚动 */
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

function clearReplyTimer() {
  if (!replyTimer) return
  window.clearTimeout(replyTimer)
  replyTimer = null
}

function send() {
  if (!canSend.value) return
  const text = draft.value.trim()
  if (!text) return

  messages.value.push({
    id: nextId(),
    role: 'user',
    content: text,
    at: Date.now(),
    delivered: true,
  })
  draft.value = ''
  nextTick(resizeInput)
  typing.value = true
  scrollToBottom(true)

  clearReplyTimer()
  replyTimer = window.setTimeout(() => {
    const reply = MOCK_REPLIES[replyIndex.value % MOCK_REPLIES.length]
    replyIndex.value += 1
    messages.value.push({
      id: nextId(),
      role: 'assistant',
      content: reply,
      at: Date.now(),
    })
    typing.value = false
    replyTimer = null
    scrollToBottom()
  }, 1000)
}

function resetConversation() {
  clearReplyTimer()
  messages.value = buildInitialMessages(props.documentName)
  draft.value = ''
  typing.value = false
  replyIndex.value = 0
  isNearBottom.value = true
  showScrollToBottom.value = false
  nextTick(() => {
    resizeInput()
    scrollToBottom(true)
  })
}

watch(
  () => props.documentName,
  () => resetConversation(),
)

onMounted(() => {
  nextTick(() => {
    resizeInput()
    scrollToBottom(true)
  })
})

onBeforeUnmount(clearReplyTimer)
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
