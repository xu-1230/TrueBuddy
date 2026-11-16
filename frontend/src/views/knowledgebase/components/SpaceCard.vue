<template>
  <div class="space-card" @click="$emit('click')">
    <div class="space-card__actions">
      <template v-if="readonly">
        <el-button
          size="small"
          type="primary"
          plain
          class="space-card__fork-btn"
          :loading="forking"
          @click.stop="$emit('fork')"
        >
          添加为自己的
        </el-button>
      </template>
      <template v-else>
        <el-tooltip :content="space.visibility === 1 ? '设为私有' : '设为公有'" placement="top">
          <el-icon
            class="space-card__action"
            :title="space.visibility === 1 ? '设为私有' : '设为公有'"
            @click.stop="$emit('toggle-visibility', space.visibility === 1 ? 0 : 1)"
          >
            <Hide v-if="space.visibility === 1" />
            <View v-else />
          </el-icon>
        </el-tooltip>
        <el-icon class="space-card__action" title="编辑" @click.stop="$emit('edit')">
          <Edit />
        </el-icon>
        <el-icon
          class="space-card__action space-card__action--danger"
          title="删除"
          @click.stop="$emit('delete')"
        >
          <Delete />
        </el-icon>
      </template>
    </div>
    <span v-if="!readonly && space.visibility === 1" class="space-card__public-chip">公有</span>
    <div class="space-card__visual">
      <!-- 叠加文档组（3 层堆叠，探出文件夹顶部）-->
      <div v-if="space.documentCount > 0" class="stack-docs">
        <div class="stack-doc stack-doc--back">
          <span class="stack-doc__tag stack-doc__tag--blue">DOC</span>
          <div class="stack-doc__lines"><span></span><span></span><span></span></div>
        </div>
        <div class="stack-doc stack-doc--mid">
          <span class="stack-doc__tag stack-doc__tag--purple">MD</span>
          <div class="stack-doc__lines"><span></span><span></span><span></span></div>
        </div>
        <div class="stack-doc stack-doc--front">
          <span class="stack-doc__tag stack-doc__tag--red">PDF</span>
          <div class="stack-doc__lines"><span></span><span></span><span></span></div>
        </div>
      </div>
      <!-- 文件夹 -->
      <div class="folder">
        <div class="folder__tab"></div>
        <div class="folder__body">
          <el-icon class="folder__empty"><FolderOpened /></el-icon>
        </div>
      </div>
    </div>
    <div class="space-card__name" :title="space.name">{{ space.name }}</div>
    <div class="space-card__meta">
      <el-icon><Document /></el-icon>
      <span>{{ space.documentCount }} 个文档</span>
      <template v-if="readonly">
        <span class="space-card__meta-divider">·</span>
        <span :title="space.creatorName">{{ space.creatorName }}</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { Delete, Document, Edit, FolderOpened, Hide, View } from '@element-plus/icons-vue'

defineProps({
  space: {
    type: Object,
    required: true,
  },
  /** 公有库浏览区的只读形态：无编辑/删除/可见性切换，提供 fork 入口 */
  readonly: {
    type: Boolean,
    default: false,
  },
  /** 该卡的 fork 请求进行中（按钮转 loading） */
  forking: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['click', 'edit', 'delete', 'toggle-visibility', 'fork'])
</script>

<style scoped>
.space-card {
  position: relative;
  cursor: pointer;
  padding: 1.5rem 1rem 1.25rem;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  border: var(--tb-border-width) solid var(--tb-border);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.space-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--tb-shadow-2);
}

.space-card__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
  z-index: 5;
}

.space-card:hover .space-card__actions {
  opacity: 1;
}

.space-card__action {
  color: var(--tb-text-tertiary);
  font-size: 32px;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--tb-radius-sm);
  transition: color 0.2s, background 0.2s;
}

.space-card__action:hover {
  color: var(--tb-primary);
  background: var(--tb-primary-soft);
}

.space-card__action--danger:hover {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

/* 公有库只读形态：「添加为自己的」按钮（fork 入口） */
.space-card__fork-btn {
  margin: 0;
}

/* 我的空间已公开的常驻小徽标 */
.space-card__public-chip {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 5;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border-radius: var(--tb-radius-full);
}

.space-card__meta-divider {
  color: var(--tb-text-tertiary);
}

.space-card__visual {
  position: relative;
  width: 120px;
  height: 110px;
}

/* ========== 叠加文档组 ========== */
.stack-docs {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.stack-doc {
  position: absolute;
  width: 44px;
  height: 56px;
  background: linear-gradient(180deg, var(--tb-bg-surface) 0%, var(--tb-bg-raised) 100%);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  box-shadow: var(--tb-shadow-1);
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

/* 三层堆叠：后层探出最少、前层探出最多，模拟真实文件夹内文档 */
.stack-doc--back {
  top: 44px;
  left: 28px;
  transform: rotate(-9deg);
  z-index: 1;
  opacity: 0.82;
}

.stack-doc--mid {
  top: 38px;
  left: 39px;
  transform: rotate(1deg);
  z-index: 2;
}

.stack-doc--front {
  top: 32px;
  left: 50px;
  transform: rotate(8deg);
  z-index: 3;
  box-shadow: var(--tb-shadow-2);
}

/* hover 时文档扇形展开，增强层次感 */
.space-card:hover .stack-doc--back {
  transform: rotate(-13deg) translate(-3px, -2px);
}

.space-card:hover .stack-doc--mid {
  transform: rotate(2deg) translate(0, -3px);
}

.space-card:hover .stack-doc--front {
  transform: rotate(12deg) translate(3px, -4px);
  box-shadow: var(--tb-shadow-3);
}

/* 顶部彩色标签条 */
.stack-doc__tag {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 8px;
  font-weight: 700;
  color: var(--tb-on-primary);
  letter-spacing: 0.3px;
  line-height: 1;
}

.stack-doc__tag--red {
  background: var(--tb-danger);
}

.stack-doc__tag--blue {
  background: var(--tb-info);
}

.stack-doc__tag--purple {
  background: linear-gradient(180deg, #a78bfa, #8b5cf6);
}

/* 模拟文档内容文字行 */
.stack-doc__lines {
  padding: 16px 5px 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
}

.stack-doc__lines span {
  display: block;
  height: 2px;
  background: #d1d5db;
  border-radius: var(--tb-radius-xs);
}

.stack-doc__lines span:nth-child(1) { width: 70%; }
.stack-doc__lines span:nth-child(2) { width: 90%; }
.stack-doc__lines span:nth-child(3) { width: 55%; }

/* ========== 文件夹 ========== */
.folder {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100px;
  height: 70px;
  z-index: 4;
}

.folder__tab {
  position: absolute;
  top: 0;
  left: 8px;
  width: 42px;
  height: 14px;
  background: var(--tb-accent);
  border-radius: var(--tb-radius-md) var(--tb-radius-md) 0 0;
  box-shadow: 0 -1px 2px color-mix(in oklab, var(--tb-accent) 30%, transparent);
}

.folder__body {
  position: absolute;
  top: 10px;
  left: 0;
  right: 0;
  height: 60px;
  /* 强调色不再承载白字（白压琥珀仅 2.1:1）：改浅底 + 深字，两态都达标 */
  background: var(--tb-accent-soft);
  border: 1px solid color-mix(in oklab, var(--tb-accent) 30%, transparent);
  border-radius: var(--tb-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tb-accent);
}

.folder__count {
  font-size: 22px;
  font-weight: 700;
  color: var(--tb-accent);
}

.folder__empty {
  font-size: 28px;
  color: color-mix(in oklab, var(--tb-accent) 62%, transparent);
}

.space-card__name {
  margin-top: 0.75rem;
  font-size: 14px;
  font-weight: 600;
  color: var(--tb-text-primary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-card__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: var(--tb-text-tertiary);
}
</style>
