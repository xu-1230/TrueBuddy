<template>
  <span class="studio-clock mt-mono" :aria-label="ariaLabel">{{ label }}</span>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatElapsed } from '../utils/meeting-time'

const props = defineProps({
  /** 会议开始时间（后端 LocalDateTime 字符串） */
  startTime: { type: String, default: '' },
  /** 会议结束时间；已结束时冻结在这里 */
  endTime: { type: String, default: '' },
  /** 会议是否已结束 */
  ended: { type: Boolean, default: false },
})

/** 每秒推进的当前时间；只在有开始时间且未结束时开定时器 */
const now = ref(Date.now())
let timer = null

const label = computed(() => formatElapsed(props.startTime, now.value, props.endTime || null))
const ariaLabel = computed(() =>
  props.startTime ? `会议已进行 ${label.value}` : '会议尚未开始',
)

function stop() {
  if (timer) window.clearInterval(timer)
  timer = null
}

function start() {
  stop()
  if (!props.startTime || props.ended) return
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

watch(
  () => [props.startTime, props.endTime, props.ended],
  () => {
    now.value = Date.now()
    start()
  },
  { immediate: true },
)

onBeforeUnmount(stop)
</script>

<style scoped>
.studio-clock {
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.14em;
  white-space: nowrap;
}
</style>
