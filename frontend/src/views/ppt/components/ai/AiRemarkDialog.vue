<template>
  <el-dialog
    :model-value="modelValue"
    :title="`AI 讲稿 · ${pageLabel}`"
    width="820px"
    class="ai-remark-dialog"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ai-remark-dialog__meta">
      <span v-if="remark">约 {{ chars }} 字 · 预计 {{ seconds }} 秒</span>
      <span v-else class="ai-remark-dialog__meta--warn">这一页没有生成讲稿，可以重新生成一次</span>
    </div>

    <div class="ai-remark-dialog__body">
      <section class="ai-remark-dialog__column">
        <header class="ai-remark-dialog__label">现有备注</header>
        <div class="ai-remark-dialog__text ai-remark-dialog__text--origin">
          {{ original || '（这一页原本没有备注）' }}
        </div>
      </section>

      <section class="ai-remark-dialog__column">
        <header class="ai-remark-dialog__label">AI 讲稿</header>
        <div v-loading="regenerating" class="ai-remark-dialog__text ai-remark-dialog__text--result">
          {{ remark || '（等待重新生成…）' }}
        </div>
      </section>
    </div>

    <template #footer>
      <el-button :loading="regenerating" @click="emit('regenerate')">重新生成</el-button>
      <el-button @click="emit('update:modelValue', false)">放弃</el-button>
      <el-button type="primary" :disabled="!remark" @click="emit('apply', remark)">采纳并写入备注</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 单页讲稿对比弹窗。
 *
 * 为什么不做「直接覆盖备注区」：备注没有撤销栈，点一下就毁掉老师的稿子；
 * 对比弹窗同时是答辩要演示的画面（看得见 AI 改了什么）。
 * 弹窗内保留原备注文本，因此关闭前随时可以对照着决定要不要采纳。
 */
defineProps({
  modelValue: { type: Boolean, default: false },
  /** 页标签，如「第 3 页」 */
  pageLabel: { type: String, default: '' },
  /** 该页原备注（弹窗内保留快照，不随采纳变化） */
  original: { type: String, default: '' },
  /** AI 生成的讲稿 */
  remark: { type: String, default: '' },
  chars: { type: Number, default: 0 },
  seconds: { type: Number, default: 0 },
  /** 正在重新生成 */
  regenerating: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'apply', 'regenerate'])
</script>

<style scoped>
.ai-remark-dialog__meta {
  margin-bottom: 0.5rem;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.ai-remark-dialog__meta--warn {
  color: var(--tb-warning, #d97706);
}

.ai-remark-dialog__body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.ai-remark-dialog__column {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  min-width: 0;
}

.ai-remark-dialog__label {
  font-weight: 700;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.ai-remark-dialog__text {
  height: 320px;
  padding: 0.75rem;
  overflow-y: auto;
  border-radius: var(--tb-radius-md);
  font-size: var(--tb-text-md);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-remark-dialog__text--origin {
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
}

.ai-remark-dialog__text--result {
  color: var(--tb-text-primary);
  background: var(--tb-primary-soft);
}

@media (max-width: 900px) {
  .ai-remark-dialog__body {
    grid-template-columns: 1fr;
  }

  .ai-remark-dialog__text {
    height: 200px;
  }
}
</style>
