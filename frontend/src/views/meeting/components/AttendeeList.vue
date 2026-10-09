<template>
  <section class="attendee-list">
    <header class="attendee-list__head">
      <!-- 连上房间才敢叫「在线成员」；降级时这里只是台账，不能冒充在线 -->
      <span class="attendee-list__title">{{ degraded ? '入会记录（实时连接不可用）' : '在线成员' }}</span>
      <span v-if="!degraded" class="attendee-list__count">{{ onlineCount }}</span>
    </header>

    <ul class="attendee-list__body">
      <li v-for="item in attendees" :key="item.id" class="attendee-list__item">
        <span class="attendee-list__avatar" :class="{ 'is-host': item.role === 1 }">
          {{ initialOf(item.nickname) }}
        </span>
        <span class="attendee-list__name">{{ item.nickname }}</span>
        <span v-if="!degraded" class="attendee-list__state">在线</span>
      </li>
    </ul>

    <el-empty v-if="!attendees.length" :description="degraded ? '暂无入会记录' : '暂无在线成员'" :image-size="60" />
  </section>
</template>

<script setup>
defineProps({
  /** 要展示的成员项：在线成员或降级台账，由 store 的 displayAttendees 裁决 */
  attendees: { type: Array, default: () => [] },
  /** 在线人数；降级时为 null（没有真相源就不显示数字） */
  onlineCount: { type: Number, default: null },
  /** 是否处于降级态（房间连不上，展示的是入会台账） */
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
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.attendee-list__title {
  font-weight: 700;
  color: var(--tb-text-primary);
}

.attendee-list__body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin: 0;
  padding: 0;
  max-height: 220px;
  overflow-y: auto;
  list-style: none;
}

.attendee-list__item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
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

.attendee-list__state {
  color: var(--tb-success);
  font-size: var(--tb-text-xs);
}
</style>
