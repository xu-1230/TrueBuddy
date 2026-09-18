<template>
  <div
    class="base-element-audio"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper">
      <div class="audio-player" :style="{ backgroundColor: elementInfo.color || '#5b9bd5' }">
        <audio
          :src="elementInfo.src"
          controls
          playsinline
          :loop="elementInfo.loop"
          :autoplay="elementInfo.autoplay"
        ></audio>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  elementInfo: {
    type: Object,
    required: true,
  },
})
</script>

<style lang="css" scoped>
.base-element-audio {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
  transform-origin: center;
}
.audio-player {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.audio-player audio {
  width: 90%;
  height: 40px;
}
</style>