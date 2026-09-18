<template>
  <div
    class="ppt-screen-view"
    @wheel="$event => mousewheelListener($event)"
    @touchstart="$event => touchStartListener($event)"
    @touchend="$event => touchEndListener($event)"
  >
    <ScreenSlideList
      :slideWidth="slideWidth"
      :slideHeight="slideHeight"
      :animationIndex="animationIndex"
      :turnSlideToId="turnSlideToId"
      :manualExitFullscreen="manualExitFullscreen"
    />

    <div class="tools-left">
      <el-tooltip content="上一页" placement="top" :show-after="300">
        <el-icon class="tool-btn" :size="24" @click="execPrev()"><CaretLeft /></el-icon>
      </el-tooltip>
      <el-tooltip content="下一页" placement="top" :show-after="300">
        <el-icon class="tool-btn" :size="24" @click="execNext()"><CaretRight /></el-icon>
      </el-tooltip>
    </div>

    <div
      class="tools-right"
      :class="{ 'visible': rightToolsVisible }"
      @mouseleave="rightToolsVisible = false"
      @mouseenter="rightToolsVisible = true"
    >
      <div class="content">
        <div class="tool-btn page-number">{{ slideIndex + 1 }} / {{ slides.length }}</div>
        <el-tooltip :content="autoPlayTimer ? '取消自动放映' : '自动放映'" placement="top" :show-after="300">
          <el-icon class="tool-btn" :size="22" @click="autoPlayTimer ? closeAutoPlay() : autoPlay()">
            <VideoPause v-if="autoPlayTimer" />
            <VideoPlay v-else />
          </el-icon>
        </el-tooltip>
        <el-tooltip :content="fullscreenState ? '退出全屏' : '进入全屏'" placement="top" :show-after="300">
          <el-icon class="tool-btn" :size="22" @click="fullscreenState ? manualExitFullscreen() : enterFullscreen()">
            <Aim v-if="fullscreenState" />
            <FullScreen v-else />
          </el-icon>
        </el-tooltip>
        <el-tooltip content="结束放映 (ESC)" placement="top" :show-after="300">
          <el-icon class="tool-btn" :size="22" @click="exitScreening()"><SwitchButton /></el-icon>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { Aim, CaretLeft, CaretRight, FullScreen, SwitchButton, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { useSlidesStore } from '../stores/slides'
import { KEYS } from '../configs/hotkey'
import { enterFullscreen } from '../utils/fullscreen'
import useExecPlay from '../hooks/useExecPlay'
import useSlideSize from '../hooks/useSlideSize'
import useFullscreen from '../hooks/useFullscreen'
import useScreening from '../hooks/useScreening'
import { onMounted, onUnmounted } from 'vue'

import ScreenSlideList from '../components/screen/ScreenSlideList.vue'

const slidesStore = useSlidesStore()
const { slides, slideIndex } = storeToRefs(slidesStore)

const {
  autoPlayTimer,
  autoPlay,
  closeAutoPlay,
  mousewheelListener,
  touchStartListener,
  touchEndListener,
  turnPrevSlide,
  turnNextSlide,
  turnSlideToIndex,
  turnSlideToId,
  execPrev,
  execNext,
  animationIndex,
} = useExecPlay()

const { slideWidth, slideHeight } = useSlideSize()
const { exitScreening } = useScreening()
const { fullscreenState, manualExitFullscreen } = useFullscreen()

const rightToolsVisible = ref(false)

// 快捷键退出放映
const keydownListener = (e) => {
  const key = e.key.toUpperCase()
  if (key === KEYS.ESC) exitScreening()
}
onMounted(() => document.addEventListener('keydown', keydownListener))
onUnmounted(() => document.removeEventListener('keydown', keydownListener))
</script>

<style lang="css" scoped>
.ppt-screen-view {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: #1d1d1d;
}
.tools-left {
  position: fixed;
  bottom: 8px;
  left: 8px;
  font-size: 25px;
  color: #666;
  z-index: 10;
  display: flex;
  align-items: center;
}
.tool-btn {
  opacity: 0.35;
  cursor: pointer;
  transition: opacity 0.2s;
}
.tool-btn:hover {
  opacity: 0.9;
}
.tool-btn + .tool-btn {
  margin-left: 8px;
}
.tools-right {
  height: 66px;
  position: fixed;
  bottom: -66px;
  right: 0;
  z-index: 5;
  padding: 8px;
  transition: bottom 0.2s;
}
.tools-right.visible {
  bottom: 0;
}
.tools-right::after {
  content: '';
  width: 100%;
  height: 66px;
  position: absolute;
  left: 0;
  top: -66px;
}
.tools-right .content {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 8px;
  font-size: 25px;
  background-color: #fff;
  color: #333;
  padding: 8px 10px;
  box-shadow: 0 2px 12px 0 rgb(56 56 56 / 20%);
  border: 1px solid #e2e6ed;
}
.tools-right .tool-btn {
  cursor: pointer;
}
.tools-right .tool-btn:hover,
.tools-right .tool-btn.active {
  color: #5b9bd5;
}
.tools-right .tool-btn + .tool-btn {
  margin-left: 15px;
}
.tools-right .page-number {
  font-size: 13px;
  padding: 8px 12px;
  cursor: pointer;
  color: #333;
}
</style>