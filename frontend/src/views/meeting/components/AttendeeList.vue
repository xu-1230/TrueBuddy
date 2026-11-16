<template>
  <section class="attendee-list">
    <header class="attendee-list__head">
      <div>
        <p class="mt-eyebrow">Presence</p>
        <span class="attendee-list__title">在线成员</span>
      </div>
      <!-- 计数只在拿到房间真相时出现；降级时报一个 0 比不报更坏 -->
      <span v-if="!degraded" class="attendee-list__count mt-mono">{{ paddedCount }}</span>
    </header>

    <!-- 房间没连上时不列名单：库里「尚未显式离开」的行恰恰包含早已走掉的人 -->
    <p v-if="degraded" class="attendee-list__note">实时连接不可用，暂时拿不到在线成员。</p>

    <TransitionGroup
      v-else-if="attendees.length"
      name="attendee"
      tag="ul"
      class="attendee-list__body mt-scroll"
    >
      <li v-for="(item, index) in attendees" :key="item.id" class="attendee-list__item">
        <span class="attendee-list__index mt-mono">{{ indexOf(index) }}</span>
        <span class="attendee-list__avatar" :class="{ 'is-host': item.role === 1 }">
          {{ initialOf(item.nickname) }}
        </span>
        <span class="attendee-list__name">{{ item.nickname }}</span>
        <span v-if="item.role === 1" class="attendee-list__tag">老师</span>
        <i v-else class="mt-dot mt-dot--live" title="在房间内" />
      </li>
    </TransitionGroup>

    <p v-else class="attendee-list__note">还没有成员进来。</p>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 在线成员（来自 LiveKit 房间事件；降级时由上层给空数组） */
  attendees: { type: Array, default: () => [] },
  /** 房间未连接：不列名单，只说明原因 */
  degraded: { type: Boolean, default: false },
})

const paddedCount = computed(() => String(props.attendees.length).padStart(2, '0'))

/** 头像取昵称首字符 */
function initialOf(nickname) {
  return (nickname || '?').trim().charAt(0)
}

/** 名单序号：视觉索引，不承担身份语义 */
function indexOf(index) {
  return String(index + 1).padStart(2, '0')
}
</script>

<style scoped>
.attendee-list {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
  min-height: 0;
}

.attendee-list__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--tb-size-2);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--mt-line);
}

.attendee-list__title {
  display: block;
  margin-top: 2px;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.attendee-list__count {
  color: var(--mt-signal);
  font-size: var(--tb-text-xs);
  letter-spacing: 0.18em;
}

/* 长名单在自己这一格里滚动，不吃掉聊天区的高度 */
.attendee-list__body {
  display: flex;
  flex-direction: column;
  gap: 0;
  max-height: 13rem;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.attendee-list__item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 4px;
  border-bottom: 1px solid color-mix(in oklab, var(--mt-line) 55%, transparent);
  border-radius: var(--mt-radius-xs);
  transition: background var(--mt-dur-1) var(--mt-ease);
}

.attendee-list__item:last-child {
  border-bottom: 0;
}

.attendee-list__item:hover {
  background: color-mix(in oklab, var(--mt-text) 4%, transparent);
}

.attendee-list__index {
  flex: none;
  width: 18px;
  color: var(--mt-text-3);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.attendee-list__avatar {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--mt-line);
  border-radius: 50%;
  background: color-mix(in oklab, var(--mt-text) 5%, transparent);
  color: var(--mt-text);
  font-size: var(--tb-text-xs);
  font-weight: 700;
}

.attendee-list__avatar.is-host {
  border-color: color-mix(in oklab, var(--mt-amber) 40%, transparent);
  background: color-mix(in oklab, var(--mt-amber) 10%, transparent);
  color: var(--mt-amber);
}

.attendee-list__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attendee-list__tag {
  flex: none;
  padding: 1px 6px;
  border: 1px solid color-mix(in oklab, var(--mt-amber) 34%, transparent);
  border-radius: var(--mt-radius-pill);
  color: var(--mt-amber);
  font-family: var(--mt-font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.attendee-list__note {
  margin: 0;
  color: var(--mt-text-3);
  font-size: var(--tb-text-sm);
  line-height: 1.65;
}

/* 名单增删：只动 opacity / transform */
.attendee-enter-from {
  opacity: 0;
  transform: translateX(-6px);
}

.attendee-enter-active {
  transition:
    opacity var(--mt-dur-3) var(--mt-ease),
    transform var(--mt-dur-3) var(--mt-ease);
}

.attendee-leave-to {
  opacity: 0;
}

.attendee-leave-active {
  transition: opacity var(--mt-dur-2) var(--mt-ease);
}

.attendee-move {
  transition: transform var(--mt-dur-3) var(--mt-ease);
}
</style>
