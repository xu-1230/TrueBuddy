<script setup>
/**
 * 数字人 Tab：静态图片卡片网格。
 *
 * 这里刻意不加载 Live2D：9 个模型同时挂 WebGL 上下文既慢又容易把弹窗拖死，
 * 真实效果在右侧主面板里看；卡片只负责「选谁」。
 */
import { reactive } from 'vue'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import { AVATAR_MODELS } from '../../configs/avatar'

defineProps({
  /** 形象目录：[{ modelCode, name, kind, previewImage }] */
  avatars: { type: Array, default: () => [] },
  /** 当前草稿选中的形象编码 */
  selected: { type: String, default: '' },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

/** 缩略图加载失败的编码 → 显示占位块，避免破图 */
const brokenImages = reactive({})

const brokenKey = (code) => `avatar-${code}`
const markBroken = (avatar) => {
  brokenImages[brokenKey(avatar.modelCode)] = true
}
const isBroken = (avatar) => Boolean(brokenImages[brokenKey(avatar.modelCode)])

/** 目录里可能有前端尚未支持的新编码：禁用而不是让它静默加载默认模型 */
const isSupported = (avatar) => Boolean(AVATAR_MODELS[avatar.modelCode])

const kindLabel = (kind) => (kind === 'deepseek' ? 'DeepSeek 二创' : '官方示例')

const select = (avatar) => {
  if (!isSupported(avatar)) return
  emit('select', avatar.modelCode)
}

const imageSrc = (avatar) => avatar.previewImage || ''
</script>

<template>
  <div class="avatar-tab">
    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty
      v-else-if="!avatars.length"
      description="暂无可用形象，请确认 avatar_model 表已导入"
      :image-size="72"
    />

    <template v-else>
      <div class="avatar-grid" role="radiogroup" aria-label="选择数字人形象">
        <button
          v-for="avatar in avatars"
          :key="avatar.modelCode"
          type="button"
          class="avatar-card"
          :class="{
            'is-selected': selected === avatar.modelCode,
            'is-disabled': !isSupported(avatar),
          }"
          :disabled="!isSupported(avatar)"
          :aria-checked="selected === avatar.modelCode"
          role="radio"
          :title="isSupported(avatar) ? `选择 ${avatar.name}` : '当前版本不支持该形象'"
          @click="select(avatar)"
        >
          <div class="avatar-card__stage">
            <img
              v-if="imageSrc(avatar) && !isBroken(avatar)"
              class="avatar-card__image"
              :src="imageSrc(avatar)"
              :alt="avatar.name"
              loading="lazy"
              @error="markBroken(avatar)"
            />
            <span v-else class="avatar-card__fallback">{{ avatar.name.slice(0, 1) }}</span>

            <span v-if="selected === avatar.modelCode" class="avatar-card__check">
              <el-icon :size="18"><CircleCheckFilled /></el-icon>
            </span>
          </div>

          <div class="avatar-card__meta">
            <span class="avatar-card__name">{{ avatar.name }}</span>
            <span class="avatar-card__kind" :class="`is-${avatar.kind}`">{{ kindLabel(avatar.kind) }}</span>
          </div>
        </button>
      </div>

      <p class="avatar-tab__note">
        DeepSeek 鲸鱼娘 / 大肥鱼为社区二创形象，并非 DeepSeek 官方形象；素材仅限非商业用途，须保留原作者署名。
      </p>
    </template>
  </div>
</template>

<style scoped>
.avatar-tab {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 340px;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 640px) {
  .avatar-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.avatar-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--tb-dur-2) var(--tb-motion-ease),
    box-shadow var(--tb-dur-2) var(--tb-motion-ease),
    transform var(--tb-dur-2) var(--tb-motion-ease);
}

.avatar-card:hover:not(:disabled) {
  border-color: var(--tb-border-strong);
  box-shadow: var(--tb-shadow-2);
  transform: translateY(-1px);
}

.avatar-card.is-selected {
  border-color: var(--tb-primary);
  box-shadow: 0 0 0 1px var(--tb-primary) inset, var(--tb-shadow-2);
}

.avatar-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.avatar-card__stage {
  position: relative;
  aspect-ratio: 4 / 5;
  border-radius: var(--tb-radius-md);
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 50% 0%, var(--tb-primary-soft), transparent 65%),
    var(--tb-bg-sunken);
}

.avatar-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-card__fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--tb-primary-text);
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-2xl);
  font-weight: 700;
}

.avatar-card__check {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  box-shadow: var(--tb-shadow-2);
}

.avatar-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 0 2px 2px;
}

.avatar-card__name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.avatar-card__kind {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: var(--tb-radius-full);
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
  background: var(--tb-bg-sunken);
}

.avatar-card__kind.is-deepseek {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.avatar-tab__note {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: 1.6;
}
</style>
