<template>
  <section class="attendee-list">
    <header class="attendee-list__head">
      <span class="attendee-list__title">参会成员</span>
      <span class="attendee-list__count">{{ onlineCount }}/{{ attendees.length }}</span>
    </header>

    <ul class="attendee-list__body">
      <li v-for="item in attendees" :key="item.id" class="attendee-list__item">
        <span class="attendee-list__avatar" :class="{ 'is-host': item.role === 1 }">
          {{ initialOf(item.nickname) }}
        </span>
        <span class="attendee-list__name">{{ item.nickname }}</span>
        <span class="attendee-list__state" :class="{ 'is-offline': !item.online }">
          {{ item.online ? '在线' : '已离开' }}
        </span>
      </li>
    </ul>

    <el-empty v-if="!attendees.length" description="暂无成员" :image-size="60" />
  </section>
</template>

<script setup>
defineProps({
  attendees: { type: Array, default: () => [] },
  onlineCount: { type: Number, default: 0 },
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

.attendee-list__state.is-offline {
  color: var(--tb-text-tertiary);
}
</style>
