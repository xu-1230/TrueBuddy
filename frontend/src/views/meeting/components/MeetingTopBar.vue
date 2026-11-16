<template>
  <header class="studio-bar">
    <div class="studio-bar__brand">
      <span class="studio-bar__mark" aria-hidden="true">
        <el-icon v-if="icon"><component :is="icon" /></el-icon>
      </span>
      <div class="studio-bar__identity">
        <p class="mt-eyebrow">{{ subNote || 'Simulation meeting' }}</p>
        <h1 class="studio-bar__title">{{ title }}</h1>
      </div>
    </div>

    <div v-if="meetingNo" class="studio-bar__code">
      <span class="mt-eyebrow">Meeting ID</span>
      <strong class="mt-mono" :aria-label="`会议号 ${meetingNo}`">{{ formattedNo }}</strong>
    </div>

    <div class="studio-bar__status">
      <span class="mt-chip" :class="`mt-chip--${air.tone}`">
        <i
          class="mt-dot"
          :class="{ 'mt-dot--live': air.tone === 'live', 'mt-dot--warn': air.tone === 'warn' }"
        />
        {{ air.label }}
      </span>
      <StudioClock :start-time="startTime" :end-time="endTime" :ended="ended" />
      <span v-if="showCount" class="mt-chip mt-chip--muted">
        <i class="mt-dot mt-dot--live" />
        {{ countText }}
      </span>
      <span class="mt-chip" :class="connectionClass" aria-live="polite">{{ connection.text }}</span>
    </div>

    <div v-if="$slots.actions" class="studio-bar__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { describeConnection } from '../utils/meeting-connection'
import { formatMeetingNo } from '../utils/meeting-time'
import StudioClock from './StudioClock.vue'

const props = defineProps({
  /** 会议标题（未就绪时由调用方给兜底文案） */
  title: { type: String, default: '模拟会议' },
  /** 会议号 */
  meetingNo: { type: String, default: '' },
  /** 会议号后面的补充说明，如「老师控制台 / 学生会议室」 */
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
  /** 是否正在共享屏幕（导播台 ON AIR） */
  sharing: { type: Boolean, default: false },
  /** 会议开始时间，供开讲时钟使用 */
  startTime: { type: String, default: '' },
  /** 会议结束时间，结束后时钟冻结 */
  endTime: { type: String, default: '' },
})

const connection = computed(() => describeConnection(props.connection, { ended: props.ended }))
const showCount = computed(() => props.connected && !props.ended && props.onlineCount != null)
const countText = computed(() => `${props.onlineCount} 在线`)
const formattedNo = computed(() => formatMeetingNo(props.meetingNo))

/** 导播台状态：ON AIR / STANDBY / LINKING / OFFLINE / ENDED */
const air = computed(() => {
  if (props.ended) return { label: 'ENDED', tone: 'muted' }
  if (props.sharing) return { label: 'ON AIR', tone: 'live' }
  if (props.connection === 'connected') return { label: 'STANDBY', tone: 'muted' }
  if (props.connection === 'connecting' || props.connection === 'reconnecting') {
    return { label: 'LINKING', tone: 'warn' }
  }
  if (props.connection === 'disconnected') return { label: 'OFFLINE', tone: 'danger' }
  return { label: 'STANDBY', tone: 'muted' }
})

/** 连接状态 chip 的色调（与 describeConnection 的 tone 一一对应） */
const connectionClass = computed(
  () =>
    ({
      live: 'mt-chip--live',
      pending: 'mt-chip--warn',
      danger: 'mt-chip--danger',
      ended: 'mt-chip--muted',
      neutral: 'mt-chip--muted',
    })[connection.value.tone] || 'mt-chip--muted',
)
</script>

<style scoped>
.studio-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--tb-size-3);
  min-height: 68px;
  padding: 10px 14px;
  border: 1px solid var(--mt-line);
  border-radius: var(--mt-radius-lg);
  background: var(--mt-bg-rail);
  box-shadow: var(--mt-shadow-1);
  backdrop-filter: blur(20px) saturate(1.3);
}

.studio-bar__brand {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  min-width: 0;
}

.studio-bar__mark {
  position: relative;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--mt-line-strong);
  border-radius: var(--mt-radius-sm);
  color: var(--mt-signal);
  background: color-mix(in oklab, var(--mt-signal) 8%, transparent);
}

.studio-bar__mark::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -3px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mt-signal);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--mt-signal) 12%, transparent);
}

.studio-bar__identity {
  min-width: 0;
}

.studio-bar__title {
  margin: 2px 0 0;
  overflow: hidden;
  color: var(--mt-text);
  font-family: var(--mt-font-display);
  font-size: clamp(1.1rem, 1.6vw, 1.45rem);
  font-weight: 500;
  letter-spacing: -0.025em;
  line-height: 1.12;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.studio-bar__code {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-left: var(--tb-size-3);
  border-left: 1px solid var(--mt-line);
}

.studio-bar__code strong {
  color: var(--mt-text);
  font-size: clamp(1.05rem, 1.4vw, 1.35rem);
  font-weight: 500;
  letter-spacing: 0.16em;
  line-height: 1.1;
}

.studio-bar__status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
  margin-left: auto;
}

.studio-bar__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
}

@media (max-width: 1080px) {
  .studio-bar__status {
    margin-left: 0;
  }
}

@media (max-width: 820px) {
  .studio-bar {
    gap: var(--tb-size-2);
    padding: 10px 12px;
  }

  .studio-bar__mark {
    display: none;
  }

  .studio-bar__code {
    display: none;
  }

  .studio-bar__status {
    order: 3;
  }

  .studio-bar__actions {
    order: 4;
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
