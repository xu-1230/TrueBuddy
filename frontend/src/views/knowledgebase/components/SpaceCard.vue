<template>
  <div class="space-card" @click="$emit('click')">
    <div class="space-card__actions">
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
    </div>
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
    </div>
  </div>
</template>

<script setup>
import { Delete, Document, Edit, FolderOpened } from '@element-plus/icons-vue'

defineProps({
  space: {
    type: Object,
    required: true,
  },
})

defineEmits(['click', 'edit', 'delete'])
</script>

<style scoped>
.space-card {
  position: relative;
  cursor: pointer;
  padding: 1.5rem 1rem 1.25rem;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
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
  background: var(--tb-accent);
  border-radius: var(--tb-radius-md);
  box-shadow: 0 4px 10px color-mix(in oklab, var(--tb-accent) 40%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--tb-on-primary);
}

.folder__count {
  font-size: 22px;
  font-weight: 700;
  color: var(--tb-on-primary);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
}

.folder__empty {
  font-size: 28px;
  color: color-mix(in oklab, var(--tb-on-primary) 85%, transparent);
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
