<template>
  <article class="workflow-card" @click="$emit('click')">
    <div class="workflow-card__badges">
      <span v-if="builtin" class="workflow-card__chip workflow-card__chip--builtin">内置</span>
      <span
        v-else-if="!readonly && workflow.visibility === 1"
        class="workflow-card__chip workflow-card__chip--public"
      >
        公有
      </span>
    </div>

    <div class="workflow-card__actions" :class="{ 'workflow-card__actions--always': readonly }">
      <template v-if="readonly">
        <el-button
          size="small"
          type="primary"
          plain
          class="workflow-card__fork-btn"
          :loading="forking"
          @click.stop="$emit('fork')"
        >
          添加为自己的
        </el-button>
      </template>
      <template v-else-if="!builtin">
        <el-tooltip :content="workflow.visibility === 1 ? '设为私有' : '设为公有'" placement="top">
          <el-icon
            class="workflow-card__action"
            :class="{ 'is-busy': visibilityBusy }"
            :title="workflow.visibility === 1 ? '设为私有' : '设为公有'"
            @click.stop="onToggleVisibility"
          >
            <Hide v-if="workflow.visibility === 1" />
            <View v-else />
          </el-icon>
        </el-tooltip>
        <el-tooltip content="重命名" placement="top">
          <el-icon class="workflow-card__action" title="重命名" @click.stop="$emit('rename')">
            <Edit />
          </el-icon>
        </el-tooltip>
        <el-icon
          class="workflow-card__action workflow-card__action--danger"
          :class="{ 'is-busy': deleting }"
          title="删除"
          @click.stop="$emit('delete')"
        >
          <Delete />
        </el-icon>
      </template>
    </div>

    <!-- 画布缩略：开始 → 节点 → 分支/汇聚 → 结束，纯 CSS，不依赖 schemaJson -->
    <div class="workflow-card__visual" aria-hidden="true">
      <span class="wf-node wf-node--start"></span>
      <span class="wf-link wf-link--start"></span>
      <span class="wf-node wf-node--main"></span>
      <span class="wf-link wf-link--branch"></span>
      <span class="wf-node wf-node--branch"></span>
      <span class="wf-link wf-link--end"></span>
      <span class="wf-node wf-node--end"></span>
    </div>

    <h3 class="workflow-card__name" :title="workflow.name">{{ workflow.name || '未命名工作流' }}</h3>
    <p class="workflow-card__desc" :title="workflow.description">
      {{ workflow.description || '暂无说明' }}
    </p>
    <div class="workflow-card__meta">
      <span>{{ workflow.stepCount ?? 0 }} 个步骤</span>
      <span class="workflow-card__dot">·</span>
      <span>{{ workflow.updateTime || '—' }}</span>
      <template v-if="readonly">
        <span class="workflow-card__dot">·</span>
        <span class="workflow-card__creator" :title="workflow.creatorName">
          {{ workflow.creatorName || '未知用户' }}
        </span>
      </template>
    </div>
  </article>
</template>

<script setup>
import { Delete, Edit, Hide, View } from '@element-plus/icons-vue'

const props = defineProps({
  workflow: {
    type: Object,
    required: true,
  },
  /** 内置模板：只读、无操作入口 */
  builtin: {
    type: Boolean,
    default: false,
  },
  /** 公有库只读形态：无编辑 / 删除 / 可见性切换，提供 fork 入口 */
  readonly: {
    type: Boolean,
    default: false,
  },
  /** 该卡 fork 请求进行中 */
  forking: {
    type: Boolean,
    default: false,
  },
  /** 该卡可见性切换进行中 */
  visibilityBusy: {
    type: Boolean,
    default: false,
  },
  /** 该卡删除请求进行中 */
  deleting: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['click', 'toggle-visibility', 'rename', 'delete', 'fork'])

function onToggleVisibility() {
  if (props.visibilityBusy) return
  emit('toggle-visibility')
}
</script>

<style scoped>
.workflow-card {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 1rem 1rem 0.9rem;
  cursor: pointer;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.workflow-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--tb-shadow-2);
  border-color: color-mix(in oklab, var(--tb-primary) 35%, var(--tb-border));
}

.workflow-card__badges {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
}

.workflow-card__chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--tb-radius-full);
  line-height: 1;
}

.workflow-card__chip--builtin {
  color: var(--tb-accent);
  background: var(--tb-accent-soft);
}

.workflow-card__chip--public {
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
}

.workflow-card__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.workflow-card:hover .workflow-card__actions,
.workflow-card:focus-within .workflow-card__actions,
.workflow-card__actions--always {
  opacity: 1;
}

/* 触屏没有 hover：操作入口常驻，避免「点不到」 */
@media (hover: none) {
  .workflow-card__actions {
    opacity: 1;
  }
}

/* 公有卡的 fork 是主操作，常驻可见 */
.workflow-card__fork-btn {
  margin: 0;
}

/* 图标尺寸与知识库空间卡一致（SpaceCard 的 32px） */
.workflow-card__action {
  padding: 4px;
  font-size: 32px;
  color: var(--tb-text-tertiary);
  border-radius: var(--tb-radius-sm);
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.workflow-card__action:hover {
  color: var(--tb-primary);
  background: var(--tb-primary-soft);
}

.workflow-card__action--danger:hover {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

.workflow-card__action.is-busy {
  pointer-events: none;
  opacity: 0.45;
}

/* ========== 画布缩略 ========== */
.workflow-card__visual {
  position: relative;
  height: 84px;
  margin: 0.5rem 0 0.25rem;
  overflow: hidden;
  border-radius: var(--tb-radius-md);
  background:
    radial-gradient(circle at 1px 1px, color-mix(in oklab, var(--tb-text-primary) 10%, transparent) 1px, transparent 1.4px)
    0 0 / 14px 14px,
    var(--tb-bg-raised);
}

.wf-node {
  position: absolute;
  z-index: 2;
  border: 1.5px solid var(--tb-border);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
  transition: transform 0.25s ease;
}

.wf-node--start,
.wf-node--end {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--tb-primary-soft);
  border-color: var(--tb-primary);
}

.wf-node--start {
  left: 10%;
  top: 50%;
  transform: translateY(-50%);
}

.wf-node--end {
  right: 10%;
  top: 50%;
  transform: translateY(-50%);
}

.wf-node--main {
  left: 28%;
  top: 50%;
  width: 68px;
  height: 28px;
  border-radius: var(--tb-radius-sm);
  transform: translateY(-50%);
}

.wf-node--branch {
  left: 53%;
  top: 20%;
  width: 40px;
  height: 20px;
  border-style: dashed;
  border-radius: var(--tb-radius-xs);
}

.wf-link {
  position: absolute;
  z-index: 1;
  height: 1.5px;
  background: color-mix(in oklab, var(--tb-primary) 45%, var(--tb-border));
  transform-origin: left center;
}

.wf-link--start {
  left: calc(10% + 14px);
  top: 50%;
  width: calc(18% - 8px);
}

.wf-link--branch {
  left: calc(28% + 58px);
  top: 50%;
  width: 58px;
  transform: rotate(-32deg);
}

.wf-link--end {
  left: 53%;
  top: 30%;
  width: calc(37% - 30px);
  transform: rotate(32deg);
}

.workflow-card:hover .wf-node--start {
  transform: translate(-2px, -50%);
}

.workflow-card:hover .wf-node--main {
  transform: translate(0, -50%) scale(1.04);
}

.workflow-card:hover .wf-node--branch {
  transform: translate(2px, -2px);
}

.workflow-card:hover .wf-node--end {
  transform: translate(2px, -50%);
}

.workflow-card__name {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-card__desc {
  margin: 4px 0 0;
  min-height: 2.6em;
  font-size: 12px;
  line-height: 1.3;
  color: var(--tb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.workflow-card__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 0.6rem;
  font-size: 11px;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
}

.workflow-card__creator {
  overflow: hidden;
  text-overflow: ellipsis;
}

.workflow-card__dot {
  opacity: 0.7;
}

@media (prefers-reduced-motion: reduce) {
  .workflow-card,
  .wf-node {
    transition: none;
  }
  .workflow-card:hover {
    transform: none;
  }
}
</style>
