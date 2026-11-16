<template>
  <section class="screen-stage" :data-live="sharing ? 'true' : 'false'" aria-label="共享画面舞台">
    <!-- 老师端：本地预览（srcObject）；学生端：LiveKit 轨 attach 到这个 video -->
    <video
      ref="videoRef"
      class="screen-stage__video"
      :class="{ 'is-hidden': !sharing }"
      autoplay
      playsinline
      muted
    />

    <div class="screen-stage__vignette" aria-hidden="true" />
    <span class="mt-corner mt-corner--tl" aria-hidden="true" />
    <span class="mt-corner mt-corner--tr" aria-hidden="true" />
    <span class="mt-corner mt-corner--bl" aria-hidden="true" />
    <span class="mt-corner mt-corner--br" aria-hidden="true" />

    <div class="screen-stage__top">
      <span class="mt-chip" :class="sharing ? 'mt-chip--live' : 'mt-chip--muted'">
        <i class="mt-dot" :class="{ 'mt-dot--live': sharing }" />
        {{ sharing ? 'ON AIR' : 'STANDBY' }}
      </span>
      <span class="screen-stage__top-label mt-eyebrow">
        {{ sharing ? 'SCREEN SHARE / 共享中' : eyebrow }}
      </span>
    </div>

    <div v-if="!sharing" class="screen-stage__standby">
      <div class="screen-stage__rings" aria-hidden="true">
        <i /><i /><i />
      </div>
      <el-icon class="screen-stage__icon"><Monitor /></el-icon>
      <p class="screen-stage__text">{{ placeholder }}</p>
      <p v-if="hint" class="screen-stage__hint">{{ hint }}</p>
    </div>

    <div v-if="!sharing" class="screen-stage__scan" aria-hidden="true" />
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { Monitor } from '@element-plus/icons-vue'

defineProps({
  sharing: { type: Boolean, default: false },
  placeholder: { type: String, default: '等待主持人共享画面' },
  hint: { type: String, default: '' },
  /** 未共享时右上角的模式眉题 */
  eyebrow: { type: String, default: 'PROGRAM MONITOR / 待机' },
})

/** 视频元素：老师端写 srcObject，学生端由 LiveKit attach —— 必须始终存在 */
const videoRef = ref(null)

defineExpose({ videoRef })
</script>

<style scoped>
.screen-stage {
  /* 固定深色演播区：color-scheme 让 --mt-* 在这里恒取暗色一支 */
  color-scheme: dark;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  border: 1px solid var(--mt-line);
  border-radius: var(--mt-radius-lg);
  background:
    radial-gradient(72% 70% at 50% 40%, color-mix(in oklab, var(--mt-signal) 5%, transparent) 0%, transparent 70%),
    var(--tb-neutral-950);
  color: var(--mt-text);
}

.screen-stage[data-live='true'] {
  border-color: color-mix(in oklab, var(--mt-signal) 28%, transparent);
  box-shadow:
    0 0 0 1px color-mix(in oklab, var(--mt-signal) 10%, transparent),
    0 40px 100px -50px color-mix(in oklab, var(--mt-signal) 28%, transparent);
}

.screen-stage__video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.screen-stage__video.is-hidden {
  display: none;
}

.screen-stage__vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(82% 78% at 50% 50%, transparent 52%, color-mix(in oklab, var(--tb-neutral-950) 72%, transparent) 100%);
}

.screen-stage__top {
  position: absolute;
  top: 14px;
  right: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  pointer-events: none;
}

.screen-stage__top-label {
  color: var(--mt-text-3);
  text-align: right;
}

.screen-stage__standby {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--tb-size-2);
  padding: var(--tb-size-6);
  text-align: center;
}

.screen-stage__rings {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(48vw, 380px);
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.screen-stage__rings i {
  position: absolute;
  inset: 0;
  border: 1px solid color-mix(in oklab, var(--mt-signal) 18%, transparent);
  border-radius: 50%;
  animation: mt-breathe 7s var(--mt-ease-inout) infinite;
}

.screen-stage__rings i:nth-child(2) {
  inset: 14%;
  animation-delay: -2.3s;
  opacity: 0.7;
}

.screen-stage__rings i:nth-child(3) {
  inset: 28%;
  animation-delay: -4.6s;
  opacity: 0.5;
}

.screen-stage__icon {
  position: relative;
  z-index: 1;
  font-size: 2.5rem;
  color: var(--mt-signal);
  opacity: 0.86;
}

.screen-stage__text {
  position: relative;
  z-index: 1;
  margin: 0;
  color: var(--mt-text);
  font-family: var(--mt-font-display);
  font-size: clamp(1.15rem, 2vw, 1.6rem);
  font-weight: 500;
  letter-spacing: -0.02em;
}

.screen-stage__hint {
  position: relative;
  z-index: 1;
  max-width: 46ch;
  margin: 0;
  color: var(--mt-text-2);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}

/* 极细扫描线：只在待机态出现，避免干扰共享画面 */
.screen-stage__scan {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}

.screen-stage__scan::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 22%;
  background: linear-gradient(
    180deg,
    transparent 0%,
    color-mix(in oklab, var(--mt-signal) 7%, transparent) 55%,
    transparent 100%
  );
  animation: mt-scan 9s linear infinite;
}

@media (max-width: 820px) {
  .screen-stage {
    min-height: 240px;
  }

  .screen-stage__top {
    top: 10px;
    right: 10px;
    left: 10px;
  }

  .screen-stage__top-label {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .screen-stage__rings i,
  .screen-stage__scan::after {
    animation: none;
  }

  .screen-stage__scan {
    display: none;
  }
}
</style>
