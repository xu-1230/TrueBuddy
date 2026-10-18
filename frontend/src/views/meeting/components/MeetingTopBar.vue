<template>
  <header class="meeting-bar">
    <div class="meeting-bar__info">
      <el-icon v-if="icon" class="meeting-bar__icon"><component :is="icon" /></el-icon>
      <div class="meeting-bar__text">
        <p class="meeting-bar__title">{{ title }}</p>
        <p class="meeting-bar__sub">
          会议号 <strong>{{ meetingNo || '—' }}</strong>
          <span v-if="subNote"> · {{ subNote }}</span>
        </p>
      </div>
    </div>

    <div class="meeting-bar__meta">
      <!-- 人数只在拿到房间真相时才出现：没有真相源就不报数（docs/adr/0002） -->
      <span v-if="showCount" class="chip chip--live">{{ countText }}</span>
      <span class="chip" :class="`chip--${connection.tone}`">{{ connection.text }}</span>
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { describeConnection } from '../utils/meeting-connection'

const props = defineProps({
  /** 会议标题（未就绪时由调用方给兜底文案） */
  title: { type: String, default: '模拟会议' },
  /** 会议号 */
  meetingNo: { type: String, default: '' },
  /** 会议号后面的补充说明，如「管理台」「已结束」 */
  subNote: { type: String, default: '' },
  /** 标题左侧图标（组件对象，由调用方具名引入） */
  icon: { type: [Object, Function], default: null },
  /** 在线人数；降级时为 null */
  onlineCount: { type: Number, default: null },
  /** 房间是否已连上（唯一能报人数的前提） */
  connected: { type: Boolean, default: false },
  /** 会议是否已结束：结束后连接断开是预期，不再报警 */
  ended: { type: Boolean, default: false },
  /** 连接状态：idle / connecting / connected / reconnecting / disconnected */
  connection: { type: String, default: 'idle' },
})

const connection = computed(() => describeConnection(props.connection, { ended: props.ended }))
const showCount = computed(() => props.connected && !props.ended && props.onlineCount != null)
const countText = computed(() => `${props.onlineCount} 人在线`)
</script>

<style scoped>
.meeting-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.75rem 1rem;
  background: var(--tb-bg-surface);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.meeting-bar__info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

.meeting-bar__icon {
  font-size: 1.5rem;
  color: var(--tb-primary);
}

.meeting-bar__text {
  min-width: 0;
}

.meeting-bar__title {
  margin: 0;
  overflow: hidden;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-lg);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meeting-bar__sub {
  margin: 0.125rem 0 0;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.meeting-bar__sub strong {
  color: var(--tb-text-primary);
  letter-spacing: 0.08em;
}

.meeting-bar__meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* 状态筹码：与 homework / workflow 内页同一语言，不用裸 el-tag 堆在顶栏 */
.chip {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.625rem;
  border-radius: var(--tb-radius-xs);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
  white-space: nowrap;
}

.chip--live {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.chip--success {
  background: var(--tb-success-soft);
  color: var(--tb-success);
}

.chip--pending {
  background: var(--tb-warning-soft);
  color: var(--tb-warning);
}

.chip--danger {
  background: var(--tb-danger-soft);
  color: var(--tb-danger);
}

.chip--neutral {
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
}

.chip--ended {
  background: var(--tb-bg-raised);
  color: var(--tb-text-tertiary);
}
</style>
