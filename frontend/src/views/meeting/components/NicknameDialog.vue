<template>
  <el-dialog
    :model-value="modelValue"
    title="加入会议"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="nickname-dialog__tip">
      会议号 <strong>{{ meetingNo }}</strong>
      <span v-if="title"> · {{ title }}</span>
    </p>
    <el-form @submit.prevent>
      <el-form-item label="你的名字" label-width="72px">
        <el-input
          v-model="nickname"
          placeholder="请输入名字，参会成员可见"
          maxlength="20"
          show-word-limit
          autofocus
          @keyup.enter="handleSubmit"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="handleSubmit">进入会议</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  meetingNo: { type: String, default: '' },
  title: { type: String, default: '' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const nickname = ref('')

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) nickname.value = ''
  },
)

function handleSubmit() {
  const value = nickname.value.trim()
  if (!value) return
  emit('submit', value)
}
</script>

<style scoped>
.nickname-dialog__tip {
  margin: 0 0 1rem;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-md);
}

.nickname-dialog__tip strong {
  color: var(--tb-text-primary);
  letter-spacing: 0.08em;
}
</style>
