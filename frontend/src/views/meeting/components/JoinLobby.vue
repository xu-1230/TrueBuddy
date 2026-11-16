<template>
  <el-dialog
    :model-value="modelValue"
    fullscreen
    title="加入会议"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="mt-tokens mt-dialog mt-join-dialog"
    modal-class="mt-join-modal"
    @opened="focusInput"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="join-lobby meeting-studio">
      <StudioBackdrop poster animate />
      <div class="join-lobby__inner">
        <header class="join-lobby__bar">
          <span class="mt-eyebrow">TrueBuddy · Simulation Meeting</span>
          <span class="join-lobby__code mt-mono">{{ formattedNo }}</span>
        </header>

        <main class="join-lobby__main">
          <p class="mt-eyebrow join-lobby__reveal">Audience entry / 学生入口</p>
          <h1 class="mt-display join-lobby__title join-lobby__reveal">
            {{ title || '等待入会' }}
          </h1>
          <p class="join-lobby__desc join-lobby__reveal">
            输入你的名字，进入这场课堂的观众席。老师共享的画面与声音会实时出现在这里。
          </p>

          <form class="join-lobby__form join-lobby__reveal" @submit.prevent="handleSubmit">
            <label class="join-lobby__label mt-mono" for="mt-join-name">你的名字</label>
            <div class="join-lobby__field">
              <input
                id="mt-join-name"
                ref="inputRef"
                v-model="nickname"
                class="join-lobby__input"
                type="text"
                maxlength="20"
                autocomplete="nickname"
                placeholder="参会成员可见"
              />
              <button type="submit" class="mt-btn mt-btn--primary join-lobby__cta" :disabled="loading">
                <span>{{ loading ? '正在接入…' : '进入会议' }}</span>
                <span class="join-lobby__cta-arrow" aria-hidden="true">→</span>
              </button>
            </div>
            <p class="join-lobby__hint">名字最多 20 字 · 会议号 {{ formattedNo }}</p>
          </form>
        </main>

        <footer class="join-lobby__foot">
          <span class="mt-eyebrow">Live stage · screen share</span>
          <span class="join-lobby__signal">
            <i class="mt-dot mt-dot--live" />
            等待接入
          </span>
        </footer>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import StudioBackdrop from './StudioBackdrop.vue'
import { formatMeetingNo } from '../utils/meeting-time'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  meetingNo: { type: String, default: '' },
  title: { type: String, default: '' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const nickname = ref('')
const inputRef = ref(null)
const formattedNo = computed(() => formatMeetingNo(props.meetingNo))

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    nickname.value = ''
    await nextTick()
    inputRef.value?.focus()
  },
)

function focusInput() {
  inputRef.value?.focus()
}

function handleSubmit() {
  const value = nickname.value.trim()
  if (!value || props.loading) return
  emit('submit', value)
}
</script>

<style scoped>
.join-lobby {
  position: relative;
  height: 100%;
  min-height: 100dvh;
  overflow: auto;
  background: var(--mt-bg);
}

.join-lobby__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-4);
  min-height: 100dvh;
  padding: clamp(16px, 2.4vw, 32px);
}

.join-lobby__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding-bottom: var(--tb-size-3);
  border-bottom: 1px solid var(--mt-line);
}

.join-lobby__code {
  flex: none;
  color: var(--mt-text);
  font-size: var(--tb-text-sm);
  letter-spacing: 0.2em;
  white-space: nowrap;
}

.join-lobby__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  max-width: 880px;
  padding: clamp(24px, 4vw, 64px) 0;
}

.join-lobby__title {
  margin-top: clamp(20px, 3vw, 40px);
  font-size: clamp(2.75rem, 7vw, 6rem);
}

.join-lobby__desc {
  max-width: 46ch;
  margin: var(--tb-size-4) 0 0;
  color: var(--mt-text-2);
  font-size: var(--tb-text-lg);
  line-height: 1.7;
}

.join-lobby__form {
  width: min(560px, 100%);
  margin-top: clamp(24px, 4vw, 48px);
}

.join-lobby__label {
  display: block;
  margin-bottom: var(--tb-size-2);
  color: var(--mt-text-3);
  font-size: var(--tb-text-2xs);
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.join-lobby__field {
  display: flex;
  gap: var(--tb-size-2);
}

.join-lobby__input {
  flex: 1;
  min-width: 0;
  height: 54px;
  padding: 0 16px;
  border: 1px solid var(--mt-line-strong);
  border-radius: var(--mt-radius-sm);
  background: color-mix(in oklab, var(--mt-bg-elevated) 74%, transparent);
  color: var(--mt-text);
  font-family: var(--font-sans);
  font-size: 16px;
  outline: none;
  transition:
    border-color var(--mt-dur-2) var(--mt-ease),
    background var(--mt-dur-2) var(--mt-ease),
    box-shadow var(--mt-dur-2) var(--mt-ease);
}

.join-lobby__input::placeholder {
  color: var(--mt-text-3);
}

.join-lobby__input:focus {
  border-color: var(--mt-signal);
  background: var(--mt-bg-elevated);
  box-shadow: 0 0 0 4px var(--mt-ring);
}

.join-lobby__cta {
  min-height: 54px;
  padding: 0 22px;
  border-radius: var(--mt-radius-sm);
}

.join-lobby__cta-arrow {
  font-family: var(--mt-font-mono);
  transition: transform var(--mt-dur-2) var(--mt-ease);
}

.join-lobby__cta:hover:not(:disabled) .join-lobby__cta-arrow {
  transform: translateX(2px);
}

.join-lobby__hint {
  margin: var(--tb-size-2) 0 0;
  color: var(--mt-text-3);
  font-family: var(--mt-font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: 0.1em;
}

.join-lobby__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding-top: var(--tb-size-3);
  border-top: 1px solid var(--mt-line);
}

.join-lobby__signal {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  color: var(--mt-text-2);
  font-family: var(--mt-font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: 0.12em;
}

/* 入口一次性 stagger；reduced-motion 由 meeting.css 统一关闭 */
.join-lobby__reveal {
  animation: mt-rise-in 0.7s var(--mt-ease);
}

.join-lobby__reveal:nth-child(2) {
  animation-delay: 0.08s;
}

.join-lobby__reveal:nth-child(3) {
  animation-delay: 0.14s;
}

.join-lobby__reveal:nth-child(4) {
  animation-delay: 0.2s;
}

@media (max-width: 640px) {
  .join-lobby__field {
    flex-direction: column;
  }

  .join-lobby__cta {
    width: 100%;
  }

  .join-lobby__foot {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--tb-size-2);
  }
}
</style>
