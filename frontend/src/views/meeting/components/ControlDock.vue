<template>
  <div class="mt-dock control-dock" role="toolbar" :aria-label="label">
    <slot />
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, default: '讲台控制' },
})
</script>

<style scoped>
.control-dock {
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.control-dock::-webkit-scrollbar {
  display: none;
}
</style>
