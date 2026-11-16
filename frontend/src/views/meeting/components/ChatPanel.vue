<template>
  <section class="chat-panel">
    <header class="chat-panel__head">
      <div>
        <p class="mt-eyebrow">Comms</p>
        <span class="chat-panel__title">会议聊天</span>
      </div>
      <span v-if="disabled" class="mt-chip mt-chip--muted">已结束</span>
      <span v-else class="chat-panel__count mt-mono">{{ messages.length }}</span>
    </header>

    <div
      ref="listRef"
      class="chat-panel__list mt-scroll"
      role="log"
      aria-live="polite"
      aria-relevant="additions"
    >
      <div v-if="loading" class="chat-panel__skeleton" aria-hidden="true">
        <span v-for="n in 3" :key="n" class="chat-panel__skeleton-line" :class="`is-${n}`" />
      </div>

      <TransitionGroup v-else name="chat" tag="div" class="chat-panel__stream">
        <article
          v-for="item in messages"
          :key="item.id"
          class="chat-panel__item"
          :class="{
            'is-self': item.self === true,
            'is-host': item.role === 1,
            'is-digital': isDigital(item),
          }"
        >
          <header class="chat-panel__meta">
            <span class="chat-panel__avatar" aria-hidden="true">{{ initialOf(item.nickname) }}</span>
            <span class="chat-panel__name">{{ item.nickname }}</span>
            <span v-if="item.role === 1" class="chat-panel__tag">老师</span>
            <span v-if="isDigital(item)" class="chat-panel__tag chat-panel__tag--digital">数字人</span>
            <span v-if="item.self" class="chat-panel__tag chat-panel__tag--self">我</span>
            <span class="chat-panel__time mt-mono">{{ formatTime(item.createTime) }}</span>
          </header>
          <p class="chat-panel__content">{{ item.content }}</p>
        </article>
      </TransitionGroup>

      <p v-if="!messages.length && !loading" class="chat-panel__note">还没有消息，发一条试试。</p>
    </div>

    <footer class="chat-panel__foot">
      <el-input
        v-model="draft"
        :disabled="disabled"
        class="chat-panel__input"
        aria-label="输入消息"
        placeholder="输入消息，回车发送"
        maxlength="500"
        @keyup.enter="handleSend"
      />
      <button
        type="button"
        class="mt-btn mt-btn--primary chat-panel__send"
        :disabled="disabled || !draft.trim()"
        @click="handleSend"
      >
        发送
      </button>
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

/** 新消息到达 / 骨架结束时滚到底部 */
watch(
  [() => props.messages.length, () => props.loading],
  async () => {
    await nextTick()
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  },
  { immediate: true },
)

/** 数字人发言：后端用 role=3 / kind=2，前端只做展示区分 */
function isDigital(item) {
  return item?.role === 3 || item?.kind === 2
}

function initialOf(nickname) {
  return (nickname || '?').trim().charAt(0)
}

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
  gap: var(--tb-size-2);
  /* 高度由父侧栏分配（flex:1），不在组件里写死 100% */
  min-height: 0;
}

.chat-panel__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--tb-size-2);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--mt-line);
}

.chat-panel__title {
  display: block;
  margin-top: 2px;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.chat-panel__count {
  color: var(--mt-text-3);
  font-size: var(--tb-text-2xs);
  letter-spacing: 0.12em;
}

.chat-panel__list {
  flex: 1;
  min-height: 0;
  padding: 10px 2px 10px 0;
  overflow-y: auto;
}

.chat-panel__stream {
  display: flex;
  flex-direction: column;
}

.chat-panel__item {
  padding: 10px 12px;
  border-left: 2px solid transparent;
  border-radius: var(--mt-radius-xs);
  transition: background var(--mt-dur-1) var(--mt-ease);
}

.chat-panel__item + .chat-panel__item {
  margin-top: 2px;
}

.chat-panel__item:hover {
  background: color-mix(in oklab, var(--mt-text) 3%, transparent);
}

.chat-panel__item.is-self {
  border-left-color: var(--mt-brand);
  background: color-mix(in oklab, var(--mt-brand) 7%, transparent);
}

.chat-panel__item.is-host {
  border-left-color: var(--mt-amber);
}

.chat-panel__item.is-digital {
  border-left-color: var(--mt-signal);
  background: color-mix(in oklab, var(--mt-signal) 6%, transparent);
}

.chat-panel__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-panel__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--mt-line);
  border-radius: 50%;
  background: color-mix(in oklab, var(--mt-text) 5%, transparent);
  color: var(--mt-text-2);
  font-size: 10px;
  font-weight: 700;
}

.chat-panel__name {
  color: var(--mt-text);
  font-size: var(--tb-text-xs);
  font-weight: 700;
}

.chat-panel__tag {
  padding: 0 5px;
  border: 1px solid var(--mt-line);
  border-radius: var(--mt-radius-pill);
  color: var(--mt-text-2);
  font-family: var(--mt-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.chat-panel__tag--digital {
  color: var(--mt-signal);
  border-color: color-mix(in oklab, var(--mt-signal) 34%, transparent);
}

.chat-panel__tag--self {
  color: var(--mt-brand);
  border-color: color-mix(in oklab, var(--mt-brand) 34%, transparent);
}

.chat-panel__time {
  margin-left: auto;
  color: var(--mt-text-3);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.chat-panel__content {
  margin: 5px 0 0 28px;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-panel__note {
  margin: 0;
  color: var(--mt-text-3);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}

.chat-panel__foot {
  display: flex;
  flex: none;
  gap: var(--tb-size-2);
  padding-top: 10px;
  border-top: 1px solid var(--mt-line);
}

.chat-panel__input {
  flex: 1;
  min-width: 0;
}

.chat-panel__input :deep(.el-input__wrapper) {
  min-height: 44px;
  padding: 0 12px;
  border-radius: var(--mt-radius-sm);
  background: color-mix(in oklab, var(--mt-bg) 62%, transparent);
  box-shadow: 0 0 0 1px var(--mt-line) inset;
}

.chat-panel__input :deep(.el-input__wrapper.is-focus) {
  background: var(--mt-bg-elevated);
  box-shadow:
    0 0 0 1px var(--mt-signal) inset,
    0 0 0 4px var(--mt-ring);
}

.chat-panel__input :deep(.el-input__inner) {
  color: var(--mt-text);
  font-size: 16px;
}

.chat-panel__input :deep(.el-input__inner::placeholder) {
  color: var(--mt-text-3);
}

.chat-panel__send {
  flex: none;
  min-height: 44px;
}

.chat-panel__skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 10px 12px;
}

.chat-panel__skeleton-line {
  height: 32px;
  border-radius: var(--mt-radius-sm);
  background: color-mix(in oklab, var(--mt-text) 6%, transparent);
  animation: mt-pulse 1.8s var(--mt-ease-inout) infinite;
}

.chat-panel__skeleton-line.is-2 {
  width: 82%;
}

.chat-panel__skeleton-line.is-3 {
  width: 64%;
}

/* 新消息：只动 opacity / transform */
.chat-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.chat-enter-active {
  transition:
    opacity var(--mt-dur-3) var(--mt-ease),
    transform var(--mt-dur-3) var(--mt-ease);
}

.chat-leave-to {
  opacity: 0;
}

.chat-leave-active {
  transition: opacity var(--mt-dur-2) var(--mt-ease);
}

.chat-move {
  transition: transform var(--mt-dur-3) var(--mt-ease);
}

@media (max-width: 820px) {
  .chat-panel__send {
    min-height: 46px;
  }

  .chat-panel__input :deep(.el-input__wrapper) {
    min-height: 46px;
  }
}
</style>
