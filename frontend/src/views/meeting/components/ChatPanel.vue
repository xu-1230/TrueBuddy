<template>
  <section class="chat-panel">
    <header class="chat-panel__head">
      <span class="chat-panel__title">会议聊天</span>
      <el-tag v-if="disabled" size="small" type="warning" effect="plain">会议已结束</el-tag>
    </header>

    <div ref="listRef" v-loading="loading" class="chat-panel__list">
      <div
        v-for="item in messages"
        :key="item.id"
        class="chat-panel__item"
        :class="{ 'is-self': item.attendeeId === selfId, 'is-host': item.role === 1 }"
      >
        <div class="chat-panel__meta">
          <span class="chat-panel__name">{{ item.nickname }}</span>
          <el-tag v-if="item.role === 1" size="small" effect="plain">老师</el-tag>
          <span class="chat-panel__time">{{ formatTime(item.createTime) }}</span>
        </div>
        <p class="chat-panel__content">{{ item.content }}</p>
      </div>

      <el-empty v-if="!messages.length && !loading" description="还没有消息，发一条试试" :image-size="60" />
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
  messages: { type: Array, default: () => [] },
  selfId: { type: [Number, String], default: null },
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
  min-height: 0;
  height: 100%;
}

.chat-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-panel__title {
  font-weight: 700;
  color: var(--tb-text-primary);
}

.chat-panel__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.75rem;
  min-height: 200px;
  overflow-y: auto;
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-lg);
}

.chat-panel__item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem 0.75rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-md);
  box-shadow: var(--tb-shadow-1);
}

.chat-panel__item.is-self {
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
  font-weight: 700;
  color: var(--tb-text-primary);
}

.chat-panel__time {
  margin-left: auto;
}

.chat-panel__content {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.chat-panel__foot {
  display: flex;
  gap: 0.5rem;
}
</style>
