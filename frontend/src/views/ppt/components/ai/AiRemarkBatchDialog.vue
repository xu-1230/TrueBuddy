<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 讲稿 · 全部备注"
    width="960px"
    class="ai-batch-dialog"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ai-batch-dialog__summary">
      <span>成功 {{ succeededCount }} 页</span>
      <span v-if="failedPages.length" class="ai-batch-dialog__summary--warn">
        · 失败 {{ failedPages.length }} 页（第 {{ failedPages.map(page => page.index).join('、') }} 页）
      </span>
      <span class="ai-batch-dialog__summary-hint">
        默认全选，取消勾选的页保留原备注
      </span>
    </div>

    <div class="ai-batch-dialog__body">
      <!-- 左：页列表（勾选采纳 + 点击查看对照） -->
      <aside class="ai-batch-dialog__list">
        <div
          v-for="page in pages"
          :key="page.index"
          class="ai-batch-dialog__row"
          :class="{ 'is-active': page.index === activeIndex, 'is-failed': page.failed }"
        >
          <el-checkbox
            class="ai-batch-dialog__pick"
            :model-value="isSelected(page.index)"
            :disabled="page.failed"
            @change="toggle(page.index)"
            @click.stop
          />
          <button type="button" class="ai-batch-dialog__jump" @click="activeIndex = page.index">
            <span class="ai-batch-dialog__page">第 {{ page.index }} 页</span>
            <span class="ai-batch-dialog__title">{{ page.title }}</span>
            <span class="ai-batch-dialog__meta">
              {{ page.failed ? '生成失败' : `约 ${page.chars} 字 · ${page.seconds} 秒` }}
            </span>
          </button>
        </div>
      </aside>

      <!-- 右：当前选中页的原/新对照 -->
      <section class="ai-batch-dialog__preview">
        <template v-if="activePage">
          <header class="ai-batch-dialog__preview-head">
            <span class="ai-batch-dialog__label">第 {{ activePage.index }} 页 · 现有备注</span>
          </header>
          <div class="ai-batch-dialog__text ai-batch-dialog__text--origin">
            {{ activePage.original || '（这一页原本没有备注）' }}
          </div>

          <header class="ai-batch-dialog__preview-head">
            <span class="ai-batch-dialog__label">AI 讲稿</span>
            <span v-if="activePage.failed" class="ai-batch-dialog__label--warn">未生成，可点下方「只重试失败页」</span>
          </header>
          <div class="ai-batch-dialog__text ai-batch-dialog__text--result">
            {{ activePage.remark || '（这一页没有生成讲稿）' }}
          </div>
        </template>
      </section>
    </div>

    <template #footer>
      <el-button v-if="failedPages.length" :loading="retrying" @click="emit('retryFailed')">
        只重试失败页
      </el-button>
      <el-button @click="emit('update:modelValue', false)">放弃</el-button>
      <el-button type="primary" :disabled="!selectedList.length" @click="emit('apply', selectedList)">
        采纳选中的 {{ selectedList.length }} 页
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 整篇讲稿对比弹窗。
 *
 * 与单页弹窗同一套语义（对比后再采纳），差别只在规模：一次生成几十页时逐页弹确认没法用，
 * 因此这里「跑之前确认一次 → 跑完在这个弹窗里逐页勾选 → 一次性写上」，默认全选。
 * 失败的页不参与勾选，但留在列表里标红，可原地重试。
 */
import { computed, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 每页：{ index, title, original, remark, chars, seconds, failed } */
  pages: { type: Array, default: () => [] },
  /** 正在重试失败页 */
  retrying: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'apply', 'retryFailed'])

/** 勾选采纳的页码 */
const selected = ref(new Set())
/** 右侧对照展示的页码 */
const activeIndex = ref(null)

const failedPages = computed(() => props.pages.filter(page => page.failed))
const succeededCount = computed(() => props.pages.length - failedPages.value.length)
const selectedList = computed(() => [...selected.value])
const activePage = computed(() => props.pages.find(page => page.index === activeIndex.value) ?? props.pages[0])

/** 打开时默认全选成功的页；重试后有新页成功，也自动补进勾选（否则「重试成功」却不会采纳） */
watch(
  [() => props.modelValue, () => props.pages],
  ([visible]) => {
    if (!visible) return
    const next = new Set(selected.value)
    props.pages.forEach(page => {
      if (!page.failed) next.add(page.index)
      if (page.failed) next.delete(page.index)
    })
    selected.value = next
    if (!props.pages.some(page => page.index === activeIndex.value)) {
      activeIndex.value = props.pages[0]?.index ?? null
    }
  },
  { immediate: true, deep: true },
)

function isSelected(index) {
  return selected.value.has(index)
}

function toggle(index) {
  const next = new Set(selected.value)
  if (next.has(index)) next.delete(index)
  else next.add(index)
  selected.value = next
}
</script>

<style scoped>
.ai-batch-dialog__summary {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  margin-bottom: 0.5rem;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
}

.ai-batch-dialog__summary--warn {
  color: var(--tb-warning, #d97706);
}

.ai-batch-dialog__summary-hint {
  margin-left: auto;
}

.ai-batch-dialog__body {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 1rem;
  min-height: 0;
}

.ai-batch-dialog__list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 420px;
  padding: 0.375rem;
  overflow-y: auto;
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-md);
}

.ai-batch-dialog__row {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.375rem;
  border-radius: var(--tb-radius-sm, 6px);
}

.ai-batch-dialog__row.is-active {
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
}

.ai-batch-dialog__row.is-failed .ai-batch-dialog__meta {
  color: var(--tb-danger);
}

.ai-batch-dialog__jump {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
  padding: 0.25rem;
  font: inherit;
  text-align: left;
  background: none;
  border: 0;
  cursor: pointer;
}

.ai-batch-dialog__page {
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.ai-batch-dialog__title {
  overflow: hidden;
  color: var(--tb-text-primary);
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ai-batch-dialog__meta {
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.ai-batch-dialog__preview {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.ai-batch-dialog__preview-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ai-batch-dialog__label {
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  font-weight: 700;
}

.ai-batch-dialog__label--warn {
  color: var(--tb-warning, #d97706);
  font-size: var(--tb-text-xs);
}

.ai-batch-dialog__text {
  height: 150px;
  margin-bottom: 0.25rem;
  padding: 0.625rem;
  overflow-y: auto;
  border-radius: var(--tb-radius-md);
  font-size: var(--tb-text-md);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-batch-dialog__text--origin {
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
}

.ai-batch-dialog__text--result {
  color: var(--tb-text-primary);
  background: var(--tb-primary-soft);
}
</style>
