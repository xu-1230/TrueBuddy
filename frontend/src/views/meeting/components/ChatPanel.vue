<template>
  <section class="chat-panel">
    <header class="chat-panel__head">
      <span class="chat-panel__title">会议聊天</span>
      <span v-if="disabled" class="chat-panel__state">会议已结束</span>
    </header>

    <div ref="listRef" v-loading="loading" class="chat-panel__list">
      <div
        v-for="item in messages"
        :key="item.id"
        class="chat-panel__item"
        :class="{ 'is-self': item.self === true, 'is-host': item.role === 1 }"
      >
        <div class="chat-panel__meta">
          <span class="chat-panel__name">{{ item.nickname }}</span>
          <span v-if="item.role === 1" class="chat-panel__tag">老师</span>
          <span class="chat-panel__time">{{ formatTime(item.createTime) }}</span>
        </div>
        <p class="chat-panel__content">{{ item.content }}</p>
      </div>

      <p v-if="!messages.length && !loading" class="chat-panel__note">还没有消息，发一条试试。</p>
    </div>

    <footer class="chat-panel__foot">
      <el-input
        v-model="draft"
        :disabled="disabled"
        placeholder="输入消息，回车发送"
        maxlength="500"
        @keyup.enter="handleSend"
      />
      <el-button type="primary" :disabled="disabled || !draft.trim()" @click="handleSend">发送</el-button>
    </footer>
  </section>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  /** 消息列表：`self` 标记由 store 注入（判定见 utils/chat.js，不在这里比 id） */
  messages: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['send'])

const draft = ref('')
const listRef = ref(null)

/** 新消息到达后滚到底部 */
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  },
  { immediate: true },
)

function handleSend() {
  const content = draft.value.trim()
  if (!content || props.disabled) return
  emit('send', content)
  draft.value = ''
}

/** 后端返回 ISO 字符串（LocalDateTime），这里只展示时分 */
function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  /* 高度由父侧栏分配（flex:1），不在组件里写死 100%，否则与名单区加起来会溢出面板 */
  min-height: 0;
}

.chat-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--tb-text-sm);
}

.chat-panel__title {
  color: var(--tb-text-secondary);
  font-weight: 600;
}

.chat-panel__state {
  padding: 0.125rem 0.5rem;
  background: var(--tb-warning-soft);
  border-radius: var(--tb-radius-xs);
  color: var(--tb-warning);
  font-size: var(--tb-text-xs);
}

.chat-panel__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.75rem;
  min-height: 0;
  overflow-y: auto;
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-md);
}

.chat-panel__item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem 0.75rem;
  max-width: 92%;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-md);
  box-shadow: var(--tb-shadow-1);
}

/* 自己发的靠右：聊天流的通用读法，只换个底色时左右同列很难分辨谁说的 */
.chat-panel__item.is-self {
  margin-left: auto;
  background: var(--tb-primary-soft);
}

.chat-panel__meta {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.chat-panel__name {
  color: var(--tb-text-primary);
  font-weight: 700;
}

.chat-panel__tag {
  padding: 0 0.375rem;
  background: var(--tb-accent-soft);
  border-radius: var(--tb-radius-xs);
  color: var(--tb-accent);
}

.chat-panel__time {
  margin-left: auto;
  color: var(--tb-text-tertiary);
}

.chat-panel__content {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  line-height: var(--tb-leading-normal);
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-panel__note {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-sm);
}

.chat-panel__foot {
  display: flex;
  flex: none;
  gap: 0.5rem;
}
</style>
