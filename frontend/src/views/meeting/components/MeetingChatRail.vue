<template>
  <aside class="chat-rail" aria-label="会议聊天">
    <header class="chat-rail__head">
      <div class="chat-rail__title-row">
        <h2 class="chat-rail__title">会议聊天</h2>
        <span v-if="disabled" class="chat-rail__status">已结束</span>
        <span v-else-if="!degraded" class="chat-rail__count">在线 {{ attendees.length }}</span>
      </div>

      <div class="chat-rail__roster-row">
        <el-popover
          v-if="!degraded && attendees.length"
          placement="bottom-start"
          :width="280"
          trigger="click"
          popper-class="chat-roster-popper"
        >
          <template #reference>
            <button
              type="button"
              class="chat-rail__avatars"
              :aria-label="`查看在线成员（${attendees.length}）`"
            >
              <span
                v-for="item in visibleAttendees"
                :key="item.id"
                class="chat-rail__avatar"
                :class="{ 'is-host': item.role === 1 }"
                :title="item.nickname"
              >
                {{ initialOf(item.nickname) }}
              </span>
              <span v-if="hiddenCount" class="chat-rail__more">+{{ hiddenCount }}</span>
            </button>
          </template>

          <div class="chat-roster">
            <header class="chat-roster__head">
              <span class="chat-roster__title">在线成员</span>
              <span class="chat-roster__count">{{ attendees.length }}</span>
            </header>
            <ul class="chat-roster__list">
              <li v-for="(item, index) in attendees" :key="item.id" class="chat-roster__item">
                <span class="chat-roster__index">{{ padIndex(index) }}</span>
                <span
                  class="chat-roster__avatar"
                  :class="{ 'is-host': item.role === 1 }"
                >
                  {{ initialOf(item.nickname) }}
                </span>
                <span class="chat-roster__name" :title="item.nickname">{{ item.nickname }}</span>
                <span v-if="item.role === 1" class="chat-roster__tag">老师</span>
                <i v-else class="chat-roster__dot" aria-label="在房间内" />
              </li>
            </ul>
          </div>
        </el-popover>

        <p v-else class="chat-rail__note">
          {{ degraded ? '实时连接不可用' : '还没有成员进来。' }}
        </p>
      </div>
    </header>

    <div class="chat-rail__body">
      <div
        ref="listRef"
        class="chat-rail__list"
        role="log"
        aria-live="polite"
        @scroll.passive="updateScrollState"
      >
        <div v-if="loading && !messages.length" class="chat-rail__skeleton" aria-hidden="true">
          <span v-for="n in 3" :key="n" class="chat-rail__skeleton-line" :class="`is-${n}`" />
        </div>

        <template v-else>
          <TransitionGroup name="chat-item" tag="div" class="chat-rail__stream">
            <article
              v-for="item in groupedMessages"
              :key="item.id"
              class="chat-item"
              :class="{
                'is-group-start': item.groupStart,
                'chat-item--self': item.self,
                'chat-item--host': item.host,
                'chat-item--digital': item.digital,
              }"
            >
              <header v-if="item.groupStart" class="chat-item__head">
                <template v-if="item.self">
                  <span class="chat-item__tag chat-item__tag--self">我</span>
                </template>
                <template v-else>
                  <span
                    class="chat-item__avatar"
                    :class="{ 'is-host': item.host, 'is-digital': item.digital }"
                    aria-hidden="true"
                  >
                    <el-icon v-if="item.digital" :size="14"><MagicStick /></el-icon>
                    <template v-else>{{ initialOf(item.nickname) }}</template>
                  </span>
                  <span class="chat-item__name">{{ item.nickname }}</span>
                  <span v-if="item.host" class="chat-item__tag chat-item__tag--host">老师</span>
                  <span v-else-if="item.digital" class="chat-item__tag chat-item__tag--digital">
                    数字人
                  </span>
                </template>
                <time class="chat-item__time">{{ formatTime(item.createTime) }}</time>
              </header>

              <p class="chat-item__bubble">{{ item.content }}</p>
            </article>
          </TransitionGroup>

          <p v-if="!messages.length" class="chat-rail__empty">还没有消息，发一条试试。</p>
        </template>
      </div>

      <button
        v-if="showScrollToBottom"
        type="button"
        class="chat-rail__scroll"
        aria-label="回到最新消息"
        title="回到最新消息"
        @click="scrollToBottom(true)"
      >
        <el-icon :size="14"><ArrowDownBold /></el-icon>
      </button>
    </div>

    <footer class="chat-rail__composer-wrap">
      <div class="chat-rail__composer" :class="{ 'is-disabled': disabled }">
        <textarea
          ref="inputRef"
          v-model="draft"
          class="chat-rail__input"
          rows="1"
          maxlength="500"
          :disabled="disabled"
          :placeholder="disabled ? '会议已结束' : '输入消息，Enter 发送'"
          aria-label="输入会议消息"
          @input="resizeInput"
          @keydown.enter.exact="handleEnter"
        ></textarea>
        <div class="chat-rail__actions">
          <button
            type="button"
            class="chat-rail__send"
            :disabled="disabled || !draft.trim()"
            aria-label="发送"
            @click="handleSend"
          >
            <el-icon :size="15"><ArrowUpBold /></el-icon>
          </button>
        </div>
      </div>

      <div class="chat-rail__hint">
        <span>{{ disabled ? '会议已结束' : '实时聊天' }}</span>
        <span>Enter 发送 · Shift + Enter 换行</span>
      </div>
    </footer>
  </aside>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ArrowDownBold, ArrowUpBold, MagicStick } from '@element-plus/icons-vue'
import { groupMeetingMessages } from '../utils/chat-groups'

const props = defineProps({
  /** 会议消息（store 已注入 self 标记） */
  messages: { type: Array, default: () => [] },
  /** 在线成员（来自 LiveKit 房间事件；降级时为空） */
  attendees: { type: Array, default: () => [] },
  /** 房间未连接：头像行显示提示，不渲染名单浮层 */
  degraded: { type: Boolean, default: false },
  /** 会议已结束：禁用输入与发送 */
  disabled: { type: Boolean, default: false },
  /** 首屏加载：无消息时显示骨架 */
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['send'])

const MAX_AVATARS = 6
const INPUT_MAX_LINES = 5
const NEAR_BOTTOM_PX = 48

const draft = ref('')
const listRef = ref(null)
const inputRef = ref(null)
const isNearBottom = ref(true)
const showScrollToBottom = ref(false)

const groupedMessages = computed(() => groupMeetingMessages(props.messages))
const visibleAttendees = computed(() => props.attendees.slice(0, MAX_AVATARS))
const hiddenCount = computed(() => Math.max(0, props.attendees.length - MAX_AVATARS))

function initialOf(nickname) {
  return (nickname || '?').trim().charAt(0)
}

function padIndex(index) {
  return String(index + 1).padStart(2, '0')
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
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

function handleSend() {
  if (props.disabled) return
  const content = draft.value.trim()
  if (!content) return
  emit('send', content)
  draft.value = ''
  nextTick(() => {
    resizeInput()
    scrollToBottom(true)
  })
}

function handleEnter(event) {
  // 中文输入法组合中不发送
  if (event.isComposing) return
  event.preventDefault()
  handleSend()
}

watch(
  [() => props.messages.length, () => props.loading],
  async () => {
    await nextTick()
    if (props.loading) return
    if (isNearBottom.value) scrollToBottom(true)
    else updateScrollState()
  },
)

onMounted(() => {
  resizeInput()
  scrollToBottom(true)
})
</script>

<style scoped>
.chat-rail {
  /* 数字人强调色：复刻会议原 mt-signal 的青，不引入 meeting.css */
  --chat-digital: light-dark(#087480, #7ef0ff);

  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  color: var(--tb-text-primary);
}

/* ========== 头部：标题 + 在线成员 ========== */
.chat-rail__head {
  flex: none;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.chat-rail__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px 6px;
}

.chat-rail__title {
  margin: 0;
  font-size: var(--tb-text-md);
  font-weight: 650;
  color: var(--tb-text-primary);
}

.chat-rail__count {
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.chat-rail__status {
  padding: 1px 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-full);
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.chat-rail__roster-row {
  min-height: 30px;
  display: flex;
  align-items: center;
  padding: 0 14px 10px;
}

.chat-rail__avatars {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.chat-rail__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 50%;
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}

.chat-rail__avatar.is-host {
  border-color: var(--tb-warning);
  background: color-mix(in oklab, var(--tb-warning) 8%, var(--tb-bg-surface));
  color: var(--tb-warning);
}

.chat-rail__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.chat-rail__note {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

/* ========== 消息区 ========== */
.chat-rail__body {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
}

.chat-rail__list {
  flex: 1;
  min-height: 0;
  padding: 4px 14px 14px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--tb-border-strong) transparent;
}

.chat-rail__list::-webkit-scrollbar {
  width: 6px;
}

.chat-rail__list::-webkit-scrollbar-thumb {
  border-radius: var(--tb-radius-full);
  background: var(--tb-border-strong);
}

.chat-rail__list::-webkit-scrollbar-track {
  background: transparent;
}

.chat-rail__stream {
  display: flex;
  flex-direction: column;
}

.chat-item {
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-top: 3px;
}

.chat-item.is-group-start {
  margin-top: 12px;
}

.chat-item:first-child {
  margin-top: 0;
}

.chat-item__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 4px;
}

.chat-item--self {
  align-items: flex-end;
}

.chat-item--self .chat-item__head {
  justify-content: flex-end;
}

.chat-item__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 50%;
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}

.chat-item__avatar.is-host {
  border-color: var(--tb-warning);
  background: color-mix(in oklab, var(--tb-warning) 8%, var(--tb-bg-surface));
  color: var(--tb-warning);
}

.chat-item__avatar.is-digital {
  border-color: color-mix(in oklab, var(--chat-digital) 38%, transparent);
  background: color-mix(in oklab, var(--chat-digital) 9%, var(--tb-bg-surface));
  color: var(--chat-digital);
}

.chat-item__name {
  font-size: var(--tb-text-xs);
  font-weight: 700;
  color: var(--tb-text-primary);
}

.chat-item__tag {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 6px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-full);
  font-size: 10px;
  color: var(--tb-text-tertiary);
  line-height: 1;
}

.chat-item__tag--host {
  border-color: color-mix(in oklab, var(--tb-warning) 36%, transparent);
  color: var(--tb-warning);
}

.chat-item__tag--digital {
  border-color: color-mix(in oklab, var(--chat-digital) 36%, transparent);
  color: var(--chat-digital);
}

.chat-item__tag--self {
  border-color: color-mix(in oklab, var(--tb-primary) 36%, transparent);
  color: var(--tb-primary-text);
}

.chat-item__time {
  margin-left: auto;
  color: var(--tb-text-tertiary);
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.chat-item--self .chat-item__time {
  margin-left: 0;
}

.chat-item__bubble {
  max-width: 84%;
  margin: 0;
  padding: 8px 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-item:not(.chat-item--self) .chat-item__bubble {
  margin-left: 34px;
}

.chat-item--self .chat-item__bubble {
  border-color: transparent;
  background: color-mix(in oklab, var(--tb-text-primary) 7%, var(--tb-bg-surface));
}

.chat-item--digital .chat-item__bubble {
  border-color: color-mix(in oklab, var(--chat-digital) 22%, transparent);
  background: color-mix(in oklab, var(--chat-digital) 7%, var(--tb-bg-surface));
}

/* ========== 骨架 / 空态 ========== */
.chat-rail__skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 0;
}

.chat-rail__skeleton-line {
  height: 32px;
  border-radius: var(--tb-radius-sm);
  background: color-mix(in oklab, var(--tb-text-primary) 7%, var(--tb-bg-surface));
  animation: chat-rail-pulse 1.8s var(--tb-ease-inout) infinite;
}

.chat-rail__skeleton-line.is-2 {
  width: 82%;
}

.chat-rail__skeleton-line.is-3 {
  width: 64%;
}

.chat-rail__empty {
  margin: 24px 0;
  text-align: center;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}

@keyframes chat-rail-pulse {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

/* ========== 滚到底 ========== */
.chat-rail__scroll {
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

.chat-rail__scroll:hover {
  color: var(--tb-text-primary);
  background: var(--tb-bg-raised);
}

/* ========== Composer ========== */
.chat-rail__composer-wrap {
  flex: none;
  padding: 10px 12px;
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.chat-rail__composer {
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

.chat-rail__composer:focus-within {
  border-color: var(--tb-primary-border);
  box-shadow: 0 0 0 3px var(--tb-ring);
}

.chat-rail__composer.is-disabled {
  opacity: 0.7;
}

.chat-rail__input {
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

/* 会议全局 :focus-visible 会给 textarea 套一圈 mt-signal 描边，
   这里提高优先级压掉，只保留 composer 外圈的 focus ring */
.chat-rail .chat-rail__input:focus,
.chat-rail .chat-rail__input:focus-visible {
  border: 0;
  outline: none;
  box-shadow: none;
}

.chat-rail__input::placeholder {
  color: var(--tb-text-tertiary);
}

.chat-rail__input:disabled {
  cursor: not-allowed;
}

.chat-rail__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.chat-rail__send {
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

.chat-rail__send:hover:not(:disabled) {
  background: var(--tb-primary-hover);
}

.chat-rail__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.chat-rail__hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 8px;
  padding: 6px 2px 0;
  color: var(--tb-text-tertiary);
  font-size: 10px;
}

/* ========== 动效 ========== */
.chat-item-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.chat-item-enter-active {
  transition:
    opacity var(--tb-dur-3) var(--tb-motion-ease),
    transform var(--tb-dur-3) var(--tb-motion-ease);
}

.chat-item-leave-to {
  opacity: 0;
}

.chat-item-leave-active {
  transition: opacity var(--tb-dur-2) var(--tb-motion-ease);
}

.chat-item-move {
  transition: transform var(--tb-dur-3) var(--tb-motion-ease);
}

.chat-rail button:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .chat-item-enter-active,
  .chat-item-leave-active,
  .chat-item-move {
    transition: none;
  }

  .chat-rail__skeleton-line {
    animation: none;
  }

  .chat-rail__composer,
  .chat-rail__scroll,
  .chat-rail__send {
    transition: none;
  }
}
</style>
