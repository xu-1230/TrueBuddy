<template>
  <!-- 工作流卡骨架：与 WorkflowCard 同形（缩略图 + 名称 + 说明 + 元信息） -->
  <div class="workflow-card-skeleton" aria-hidden="true">
    <div class="workflow-card-skeleton__visual"></div>
    <span class="workflow-card-skeleton__line workflow-card-skeleton__name"></span>
    <span class="workflow-card-skeleton__line workflow-card-skeleton__desc"></span>
    <span class="workflow-card-skeleton__line workflow-card-skeleton__meta"></span>
  </div>
</template>

<style scoped>
.workflow-card-skeleton {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 1rem 1rem 0.9rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
}

.workflow-card-skeleton__visual {
  position: relative;
  height: 84px;
  margin-bottom: 0.75rem;
  overflow: hidden;
  border-radius: var(--tb-radius-md);
  background: color-mix(in oklab, var(--tb-text-primary) 6%, var(--tb-bg-raised));
}

.workflow-card-skeleton__line {
  position: relative;
  display: block;
  overflow: hidden;
  height: 12px;
  margin-top: 6px;
  border-radius: var(--tb-radius-xs);
  background: color-mix(in oklab, var(--tb-text-primary) 8%, var(--tb-bg-surface));
}

.workflow-card-skeleton__name {
  width: 58%;
  height: 14px;
}

.workflow-card-skeleton__desc {
  width: 88%;
}

.workflow-card-skeleton__meta {
  width: 42%;
  margin-top: 12px;
}

.workflow-card-skeleton__visual::after,
.workflow-card-skeleton__line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 24%,
    light-dark(rgb(255 255 255 / 0.6), rgb(255 255 255 / 0.08)) 50%,
    transparent 76%
  );
  animation: workflow-card-shimmer 1.4s var(--tb-ease-linear) infinite;
  transform: translateX(-100%);
}

@keyframes workflow-card-shimmer {
  to {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .workflow-card-skeleton__visual::after,
  .workflow-card-skeleton__line::after {
    animation: none;
    opacity: 0;
  }
}
</style>
