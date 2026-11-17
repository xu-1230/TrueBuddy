<script setup>
/**
 * 声音 Tab：搜索 + 性别/分类筛选 + 音色行列表。
 *
 * 试听只用一个 Audio 实例：切换行时先停上一条，关闭弹窗/卸载时统一释放。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Headset, Microphone, VideoPlay } from '@element-plus/icons-vue'

const props = defineProps({
  /** 音色列表：[{ id, voiceCode, name, trait, age, language, category, gender, source, previewUrl, canDelete }] */
  voices: { type: Array, default: () => [] },
  /** 当前草稿选中的音色编码 */
  selected: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  /** 是否允许复刻（数字人启用 + Key 已配置） */
  cloneEnabled: { type: Boolean, default: false },
  /** 全局默认音色编码 */
  defaultVoiceCode: { type: String, default: '' },
})

const emit = defineEmits(['select', 'delete', 'clone'])

const keyword = ref('')
const gender = ref('all')
const category = ref('all')

const categories = computed(() => {
  const seen = new Set()
  props.voices.forEach((voice) => {
    if (voice.category) seen.add(voice.category)
  })
  return Array.from(seen)
})

const filteredVoices = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return props.voices.filter((voice) => {
    if (gender.value !== 'all' && voice.gender !== gender.value) return false
    if (category.value !== 'all' && voice.category !== category.value) return false
    if (!key) return true
    return [voice.name, voice.trait, voice.voiceCode, voice.category]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(key))
  })
})

const genderLabel = (value) => (value === 'male' ? '男' : '女')
const sourceLabel = (voice) => (voice.source === 'cloned' ? '我的复刻' : '内置')
const isDefault = (voice) => Boolean(props.defaultVoiceCode) && voice.voiceCode === props.defaultVoiceCode

// ===== 试听：单实例，切行/关闭/卸载都先停 =====
const playingId = ref(null)
let audio = null

const stopPreview = () => {
  if (audio) {
    audio.onended = null
    audio.onerror = null
    audio.pause()
    audio = null
  }
  playingId.value = null
}

const togglePreview = (voice) => {
  if (!voice.previewUrl) {
    ElMessage.warning('这条音色暂无试听音频')
    return
  }
  if (playingId.value === voice.id) {
    stopPreview()
    return
  }
  stopPreview()
  audio = new Audio(voice.previewUrl)
  playingId.value = voice.id
  audio.onended = stopPreview
  audio.onerror = () => {
    stopPreview()
    ElMessage.warning('试听音频加载失败，可能已失效')
  }
  audio.play().catch(() => {
    stopPreview()
    ElMessage.warning('浏览器未能播放试听音频')
  })
}

const confirmDelete = (voice) => {
  emit('delete', voice)
}

onBeforeUnmount(stopPreview)
</script>

<template>
  <div class="voice-tab">
    <div class="voice-toolbar">
      <el-input
        v-model="keyword"
        class="voice-search"
        placeholder="搜索音色名称 / 特征 / 编码"
        clearable
      >
        <template #prefix>
          <el-icon><Headset /></el-icon>
        </template>
      </el-input>

      <el-radio-group v-model="gender" class="voice-gender">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="male">男</el-radio-button>
        <el-radio-button value="female">女</el-radio-button>
      </el-radio-group>

      <el-select v-model="category" class="voice-category" placeholder="分类">
        <el-option label="全部分类" value="all" />
        <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
      </el-select>

      <el-tooltip
        :content="cloneEnabled ? '用一段音频复刻自己的声音' : '数字人未启用或未配置 DashScope Key，暂不能复刻'"
        placement="top"
      >
        <span>
          <el-button type="primary" plain :disabled="!cloneEnabled" @click="emit('clone')">
            <el-icon class="btn-icon"><Microphone /></el-icon>
            复刻音色
          </el-button>
        </span>
      </el-tooltip>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty
      v-else-if="!filteredVoices.length"
      :description="voices.length ? '没有匹配的音色' : '暂无音色，请确认 avatar_voice 表已导入'"
      :image-size="72"
    />

    <div v-else class="voice-list" role="radiogroup" aria-label="选择播报音色">
      <button
        v-for="voice in filteredVoices"
        :key="voice.id"
        type="button"
        class="voice-row"
        :class="{ 'is-selected': selected === voice.voiceCode }"
        :aria-checked="selected === voice.voiceCode"
        role="radio"
        @click="emit('select', voice.voiceCode)"
      >
        <span class="voice-row__radio" :class="{ 'is-on': selected === voice.voiceCode }" aria-hidden="true" />

        <span class="voice-row__main">
          <span class="voice-row__title">
            <span class="voice-row__name">{{ voice.name }}</span>
            <span class="voice-row__tag" :class="{ 'is-cloned': voice.source === 'cloned' }">{{ sourceLabel(voice) }}</span>
            <span v-if="isDefault(voice)" class="voice-row__tag is-default">默认</span>
          </span>
          <span class="voice-row__meta">
            <span>{{ voice.trait || '未填写特征' }}</span>
            <span v-if="voice.age">· {{ voice.age }}</span>
            <span v-if="voice.language">· {{ voice.language }}</span>
            <span>· {{ genderLabel(voice.gender) }}</span>
            <span>· {{ voice.category }}</span>
          </span>
        </span>

        <span class="voice-row__actions">
          <el-tooltip content="试听" placement="top">
            <span class="voice-row__action" @click.stop="togglePreview(voice)">
              <el-icon :size="16">
                <VideoPlay v-if="playingId !== voice.id" />
                <span v-else class="voice-row__stop">■</span>
              </el-icon>
            </span>
          </el-tooltip>
          <el-tooltip v-if="voice.canDelete" content="删除这条复刻音色" placement="top">
            <span class="voice-row__action is-danger" @click.stop="confirmDelete(voice)">
              <el-icon :size="16"><Delete /></el-icon>
            </span>
          </el-tooltip>
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.voice-tab {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 340px;
}

.voice-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.voice-search {
  width: 230px;
  flex: 1 1 200px;
}

.voice-gender {
  flex: 0 0 auto;
}

.voice-category {
  width: 150px;
  flex: 0 0 auto;
}

.btn-icon {
  margin-right: 4px;
}

.voice-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}

.voice-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    background-color var(--tb-dur-2) var(--tb-motion-ease);
}

.voice-row:hover {
  border-color: var(--tb-border-strong);
  background: var(--tb-bg-surface);
}

.voice-row.is-selected {
  border-color: var(--tb-primary);
  background: color-mix(in oklab, var(--tb-primary-soft) 55%, var(--tb-bg-raised));
}

.voice-row__radio {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--tb-border-strong);
  border-radius: 50%;
}

.voice-row__radio.is-on {
  border: 5px solid var(--tb-primary);
}

.voice-row__main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.voice-row__title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.voice-row__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.voice-row__tag {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: var(--tb-radius-full);
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
  background: var(--tb-bg-sunken);
}

.voice-row__tag.is-cloned {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.voice-row__tag.is-default {
  color: var(--tb-success-text, var(--tb-primary-text));
}

.voice-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: 1.5;
}

.voice-row__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.voice-row__action {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--tb-radius-sm);
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition: background-color var(--tb-dur-1) var(--tb-motion-ease), color var(--tb-dur-1) var(--tb-motion-ease);
}

.voice-row__action:hover {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.voice-row__action.is-danger:hover {
  color: var(--tb-danger);
  background: color-mix(in oklab, var(--tb-danger) 12%, transparent);
}

.voice-row__stop {
  font-size: 12px;
  line-height: 1;
}
</style>
