<template>
  <!--
    知识库空间卡骨架：与 SpaceCard 同形（卡壳 + 文件夹视觉区 + 名称 + 文档数），
    只在首屏 / 空列表加载时出现；读屏语义由父级 aria-busy 与 status 文案承担。
  -->
  <div class="space-card-skeleton" aria-hidden="true">
    <div class="space-card-skeleton__visual">
      <span class="space-card-skeleton__folder"></span>
    </div>
    <span class="space-card-skeleton__line space-card-skeleton__name"></span>
    <span class="space-card-skeleton__line space-card-skeleton__meta"></span>
  </div>
</template>

<style scoped>
/* 卡壳尺寸与 SpaceCard 对齐，数据到达后网格高度不跳 */
.space-card-skeleton {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem 1rem 1.25rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.space-card-skeleton__visual {
  position: relative;
  width: 120px;
  height: 110px;
}

/* 文件夹主体：与 SpaceCard 的 100×70 文件夹同尺寸，只留灰阶 */
.space-card-skeleton__folder {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 100px;
  height: 70px;
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-text-primary) 7%, var(--tb-bg-raised));
  border-radius: var(--tb-radius-md);
  transform: translateX(-50%);
}

.space-card-skeleton__line {
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-text-primary) 8%, var(--tb-bg-surface));
  border-radius: var(--tb-radius-xs);
}

.space-card-skeleton__name {
  width: 60%;
  height: 14px;
  margin-top: 0.75rem;
}

.space-card-skeleton__meta {
  width: 40%;
  height: 12px;
  margin-top: 6px;
}

/* 流光只用 transform 位移（doc-ui §6.3），与首页骨架同一套手感 */
.space-card-skeleton__folder::after,
.space-card-skeleton__line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 24%,
    light-dark(rgb(255 255 255 / 0.6), rgb(255 255 255 / 0.08)) 50%,
    transparent 76%
  );
  animation: space-card-shimmer 1.4s var(--tb-ease-linear) infinite;
  transform: translateX(-100%);
}

.space-card-skeleton__line {
  position: relative;
}

@keyframes space-card-shimmer {
  to {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .space-card-skeleton__folder::after,
  .space-card-skeleton__line::after {
    animation: none;
    opacity: 0;
  }
}
</style>
