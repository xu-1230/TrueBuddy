<template>
  <section class="screen-stage">
    <!-- 老师端：本地预览（srcObject）；学生端：MediaSource 播放（由父组件持有 player 实例） -->
    <video
      ref="videoRef"
      class="screen-stage__video"
      :class="{ 'is-hidden': !sharing }"
      autoplay
      playsinline
      muted
    />

    <div v-if="!sharing" class="screen-stage__placeholder">
      <el-icon class="screen-stage__icon"><Monitor /></el-icon>
      <p class="screen-stage__text">{{ placeholder }}</p>
      <p v-if="hint" class="screen-stage__hint">{{ hint }}</p>
    </div>

    <span v-if="sharing" class="screen-stage__badge">正在共享</span>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { Monitor } from '@element-plus/icons-vue'

defineProps({
  sharing: { type: Boolean, default: false },
  placeholder: { type: String, default: '等待主持人共享画面' },
  hint: { type: String, default: '' },
})

/** 视频元素：老师端写 srcObject，学生端交给 MediaSource */
const videoRef = ref(null)

defineExpose({ videoRef })
</script>

<style scoped>
.screen-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  background: var(--tb-neutral-950);
  border-radius: var(--tb-radius-xl);
}

.screen-stage__video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.screen-stage__video.is-hidden {
  display: none;
}

.screen-stage__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  color: var(--tb-neutral-400);
  text-align: center;
}

.screen-stage__icon {
  font-size: 2.5rem;
}

.screen-stage__text {
  margin: 0;
  font-size: var(--tb-text-lg);
}

.screen-stage__hint {
  margin: 0;
  font-size: var(--tb-text-sm);
  opacity: 0.75;
}

.screen-stage__badge {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  padding: 0.25rem 0.625rem;
  /* 共享进行中是「正常运作」，不是危险：红色在本项目里表示 danger/删除 */
  background: var(--tb-success);
  border-radius: var(--tb-radius-full);
  color: var(--tb-text-inverse);
  font-size: var(--tb-text-xs);
}
</style>
