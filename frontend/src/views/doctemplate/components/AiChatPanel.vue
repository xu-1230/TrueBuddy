<template>
  <aside class="ai-chat">
    <header class="ai-chat__head">
      <div class="ai-chat__title">
        <span class="ai-chat__avatar"><el-icon><MagicStick /></el-icon></span>
        <span class="ai-chat__title-text">
          <span class="ai-chat__name">AI 助手</span>
          <span class="ai-chat__sub" :title="documentName">{{ documentName || '参考文档' }}</span>
        </span>
      </div>
      <span class="ai-chat__status">模拟对话</span>
    </header>

    <div ref="listRef" class="ai-chat__list">
      <div
        v-for="message in messages"
        :key="message.id"
        class="chat-msg"
        :class="`chat-msg--${message.role}`"
      >
        <span v-if="message.role === 'assistant'" class="chat-msg__avatar">
          <el-icon><MagicStick /></el-icon>
        </span>
        <p class="chat-msg__bubble">{{ message.content }}</p>
      </div>

      <div v-if="typing" class="chat-msg chat-msg--assistant">
        <span class="chat-msg__avatar"><el-icon><MagicStick /></el-icon></span>
        <p class="chat-msg__bubble chat-msg__bubble--typing">
          <span></span><span></span><span></span>
        </p>
      </div>
    </div>

    <footer class="ai-chat__foot">
      <textarea
        v-model="draft"
        class="ai-chat__input"
        rows="2"
        placeholder="就参考文档向 AI 提问，Enter 发送 / Shift + Enter 换行"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button type="button" class="ai-chat__send" :disabled="!canSend" @click="send">
        <el-icon><Promotion /></el-icon>
        <span>发送</span>
      </button>
    </footer>
  </aside>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { MagicStick, Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  /** 当前正在查看的参考文档名，用于面板标题上下文 */
  documentName: { type: String, default: '' },
})

/** 模拟助手回复池（纯前端演示，按顺序循环取用） */
const MOCK_REPLIES = [
  '已对照参考文档核对版式：标题层级、教学目标、学情分析与板书设计结构完整，可以直接使用。',
  '建议在「课中实施」表格中把 SQL Server 2012 的演示环节单独拆出一行，突出实践操作的时间占比。',
  '检测到「教学反思与持续改进」篇幅偏长，可提炼为 2-3 条要点，与板书设计形成呼应。',
  '参考文档的重点为数据库的创建与使用，难点为迁移用户数据库，建议在拓展内容中补充对应练习。',
  '文案表述整体规范，个别处可把「了解 / 熟悉 / 掌握」改为可观测的行为动词，便于教学评价。',
]

let messageSeed = 0
let replyTimer = null
const nextId = () => (messageSeed += 1)

const messages = ref([
  {
    id: nextId(),
    role: 'assistant',
    content: '你好，我是文档复刻助手。已为你加载参考文档，可以帮你核对版式、补充内容或调整表述。',
  },
  {
    id: nextId(),
    role: 'user',
    content: '帮我看看这份教案的板书设计是否完整？',
  },
  {
    id: nextId(),
    role: 'assistant',
    content:
      '板书设计已覆盖第 3 章的三级目录（SQL 的基本概念与特点 / SQL Server 2012 简介 / 数据库的创建和使用），结构完整，建议补充「迁移用户数据库」的要点标注。',
  },
])

const draft = ref('')
const typing = ref(false)
const listRef = ref(null)
const replyIndex = ref(0)

const canSend = computed(() => Boolean(draft.value.trim()) && !typing.value)

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function send() {
  if (!canSend.value) return
  messages.value.push({ id: nextId(), role: 'user', content: draft.value.trim() })
  draft.value = ''
  typing.value = true
  scrollToBottom()

  replyTimer = setTimeout(() => {
    messages.value.push({
      id: nextId(),
      role: 'assistant',
      content: MOCK_REPLIES[replyIndex.value % MOCK_REPLIES.length],
    })
    replyIndex.value += 1
    typing.value = false
    replyTimer = null
    scrollToBottom()
  }, 900)
}

watch(
  () => props.documentName,
  () => scrollToBottom(),
)

onBeforeUnmount(() => {
  if (replyTimer) {
    clearTimeout(replyTimer)
    replyTimer = null
  }
})
</script>

<style scoped>
.ai-chat {
  flex: 0 0 340px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e8e9ee;
  overflow: hidden;
}

.ai-chat__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid #eceef2;
  background: var(--primary-soft);
}

.ai-chat__title {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.ai-chat__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: #ffffff;
  background: linear-gradient(135deg, #ff7d4d, #ff4500);
}

.ai-chat__title-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ai-chat__name {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--primary-dark);
}

.ai-chat__sub {
  max-width: 190px;
  font-size: 0.68rem;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat__status {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--primary-dark);
  background: var(--primary-soft-2);
}

.ai-chat__list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 0.85rem 0.75rem;
  overflow-y: auto;
  background: #fafbfc;
}

.chat-msg {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
}

.chat-msg--user {
  justify-content: flex-end;
}

.chat-msg__avatar {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-top: 2px;
  border-radius: 7px;
  font-size: 13px;
  color: #ffffff;
  background: linear-gradient(135deg, #ff7d4d, #ff4500);
}

.chat-msg__bubble {
  max-width: 78%;
  margin: 0;
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  font-size: 0.78rem;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-msg--assistant .chat-msg__bubble {
  color: #374151;
  background: #ffffff;
  border: 1px solid #eef0f3;
  border-top-left-radius: 3px;
}

.chat-msg--user .chat-msg__bubble {
  color: #ffffff;
  background: linear-gradient(135deg, #ff6a2e, #e63e00);
  border-top-right-radius: 3px;
}

.chat-msg__bubble--typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0.6rem 0.75rem;
}

.chat-msg__bubble--typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c7cad1;
  animation: chat-typing 1s ease-in-out infinite;
}

.chat-msg__bubble--typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.chat-msg__bubble--typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.ai-chat__foot {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  padding: 0.7rem 0.75rem;
  border-top: 1px solid #eceef2;
  background: #ffffff;
}

.ai-chat__input {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.6rem;
  border: 1px solid #e8e9ee;
  border-radius: 9px;
  background: #f7f8fa;
  font-family: inherit;
  font-size: 0.78rem;
  line-height: 1.6;
  color: #111827;
  outline: none;
  resize: none;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.ai-chat__input::placeholder {
  color: #b0b4bd;
}

.ai-chat__input:hover {
  border-color: #d5d7de;
}

.ai-chat__input:focus {
  background: #ffffff;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(255, 69, 0, 0.12);
}

.ai-chat__send {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  height: 34px;
  padding: 0 0.85rem;
  border: none;
  border-radius: 9px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #ff6a2e, #e63e00);
  box-shadow: 0 6px 14px rgba(255, 69, 0, 0.24);
  cursor: pointer;
  transition: opacity 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.ai-chat__send:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(255, 69, 0, 0.3);
}

.ai-chat__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  box-shadow: none;
}

.ai-chat__list::-webkit-scrollbar {
  width: 6px;
}

.ai-chat__list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #d8dade;
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

@media (max-width: 720px) {
  .ai-chat {
    flex: 0 0 auto;
    max-height: 360px;
  }
}
</style>
