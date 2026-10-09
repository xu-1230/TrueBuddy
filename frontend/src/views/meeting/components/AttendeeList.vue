<template>
  <section class="attendee-list">
    <header class="attendee-list__head">
      <span class="attendee-list__title">在线成员</span>
      <!-- 计数只在拿到房间真相时出现；降级时报一个 0 比不报更坏 -->
      <span v-if="!degraded" class="attendee-list__count">{{ attendees.length }}</span>
    </header>

    <!-- 房间没连上时**不列名单**：库里「尚未显式离开」的行恰恰包含早已走掉的人，列出来就是误导 -->
    <p v-if="degraded" class="attendee-list__note">实时连接不可用，暂时拿不到在线成员。</p>

    <ul v-else-if="attendees.length" class="attendee-list__body">
      <li v-for="item in attendees" :key="item.id" class="attendee-list__item">
        <span class="attendee-list__avatar" :class="{ 'is-host': item.role === 1 }">
          {{ initialOf(item.nickname) }}
        </span>
        <span class="attendee-list__name">{{ item.nickname }}</span>
        <span v-if="item.role === 1" class="attendee-list__tag">老师</span>
        <span v-else class="attendee-list__dot" title="在房间内" />
      </li>
    </ul>

    <p v-else class="attendee-list__note">还没有成员进来。</p>
  </section>
</template>

<script setup>
defineProps({
  /** 在线成员（来自 LiveKit 房间事件；降级时由上层给空数组） */
  attendees: { type: Array, default: () => [] },
  /** 房间未连接：不列名单，只说明原因 */
  degraded: { type: Boolean, default: false },
})

/** 头像取昵称首字符 */
function initialOf(nickname) {
  return (nickname || '?').trim().charAt(0)
}
</script>

<style scoped>
.attendee-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
}

.attendee-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--tb-text-sm);
}

.attendee-list__title {
  color: var(--tb-text-secondary);
  font-weight: 600;
}

/* 计数筹码：与 workflow 侧栏 NodePanel 同一语言 */
.attendee-list__count {
  min-width: 18px;
  padding: 0 0.3125rem;
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-full);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  font-weight: 500;
  line-height: 18px;
  text-align: center;
}

/* 长名单在自己这一格里滚动，不吃掉聊天区的高度 */
.attendee-list__body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 12rem;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.attendee-list__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.5rem 0.25rem 0;
  border-radius: var(--tb-radius-md);
}

.attendee-list__item:hover {
  background: var(--tb-bg-raised);
}

.attendee-list__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: var(--tb-text-sm);
  font-weight: 700;
}

.attendee-list__avatar.is-host {
  background: var(--tb-accent-soft);
  color: var(--tb-accent);
}

.attendee-list__name {
  flex: 1;
  overflow: hidden;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-md);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attendee-list__tag {
  padding: 0.125rem 0.5rem;
  background: var(--tb-accent-soft);
  border-radius: var(--tb-radius-xs);
  color: var(--tb-accent);
  font-size: var(--tb-text-xs);
}

/* 能进这份名单就意味着此刻在房间里：用一个点表达，不再挂 online 字段 */
.attendee-list__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-success);
}

.attendee-list__note {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}
</style>
