<script setup>
import { computed } from 'vue'

/**
 * 状态标签：颜色只表达语义（success / warning / danger ...），
 * 具体中文文案由 constants.js 里的 META 提供，页面不重复写。
 */
const props = defineProps({
  label: { type: String, default: '' },
  tone: { type: String, default: 'info' },
})

const TONE_MAP = {
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  info: 'info',
  danger: 'danger',
  neutral: 'info',
}

const tagType = computed(() => TONE_MAP[props.tone] || 'info')
</script>

<template>
  <el-tag :type="tagType" size="small" effect="light" disable-transitions>
    {{ label || '—' }}
  </el-tag>
</template>
