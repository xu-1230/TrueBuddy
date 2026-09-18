<template>
  <div
    class="base-element-video"
    :style="{
      top: elementInfo.top + 'px',
      left: elementInfo.left + 'px',
      width: elementInfo.width + 'px',
      height: elementInfo.height + 'px',
    }"
  >
    <div class="rotate-wrapper" :style="{ transform: `rotate(${elementInfo.rotate}deg)` }">
      <video
        class="base-element-video-content"
        :src="elementInfo.src"
        :poster="elementInfo.poster"
        controls
        playsinline
      ></video>
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
.base-element-video {
  position: absolute;
}
.rotate-wrapper {
  width: 100%;
  height: 100%;
  transform-origin: center;
}
.base-element-video-content {
  width: 100%;
  height: 100%;
  display: block;
  background-color: #000;
  object-fit: contain;
}
</style>