<template>
  <!--
    Zone D 资产卡骨架：与 AssetCard 同形（卡壳 + 封面条 + 标题 / 描述 / 数值条 / 底部时间），
    只在首页首次加载时出现。读屏语义由父级 aria-busy 与 status 文案承担，这里整体隐藏。
  -->
  <div class="asset-skeleton" aria-hidden="true">
    <div class="asset-skeleton__block asset-skeleton__art">
      <span class="asset-skeleton__block asset-skeleton__pill"></span>
    </div>

    <div class="asset-skeleton__body">
      <span class="asset-skeleton__title">
        <span class="asset-skeleton__block asset-skeleton__line is-title-a"></span>
        <span class="asset-skeleton__block asset-skeleton__line is-title-b"></span>
      </span>

      <span class="asset-skeleton__desc">
        <span class="asset-skeleton__block asset-skeleton__line is-desc is-desc-a"></span>
        <span class="asset-skeleton__block asset-skeleton__line is-desc is-desc-b"></span>
      </span>

      <span class="asset-skeleton__block asset-skeleton__bar"></span>

      <span class="asset-skeleton__meta">
        <span class="asset-skeleton__block is-meta"></span>
      </span>

      <span class="asset-skeleton__block asset-skeleton__time"></span>
    </div>
  </div>
</template>

<style scoped>
/* 卡壳形态对齐 AssetCard：同底、同描边、同圆角，切换真卡时网格轮廓不跳 */
.asset-skeleton {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(158deg, var(--tb-bg-surface), var(--tb-bg-raised));
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  box-shadow: var(--tb-shadow-2);
}

/* 灰块基类：底色按文字色与表面对比混出，明暗两态各自成立；流光由伪元素承担 */
.asset-skeleton__block {
  position: relative;
  display: block;
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-text-primary) 8%, var(--tb-bg-surface));
  border-radius: var(--tb-radius-xs);
}

/* 流光只做 transform 位移动画（doc-ui §6.3），不用 background-position */
.asset-skeleton__block::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 24%,
    light-dark(rgb(255 255 255 / 0.65), rgb(255 255 255 / 0.09)) 50%,
    transparent 76%
  );
  animation: asset-skeleton-shimmer 1.4s var(--tb-ease-linear) infinite;
  transform: translateX(-100%);
}

@keyframes asset-skeleton-shimmer {
  to {
    transform: translateX(100%);
  }
}

/* ========== 封面条 ========== */
/* 覆盖基类底色：比正文灰块更深，暗示真实卡的深色封面 */
.asset-skeleton__art {
  flex: none;
  width: 100%;
  aspect-ratio: 400 / 130;
  background: var(--tb-bg-sunken);
  border-radius: 0;
}

.asset-skeleton__pill {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 56px;
  height: 24px;
  border-radius: var(--tb-radius-full);
}

/* ========== 内容区 ========== */
/* 内边距与行高刻度直接对齐 AssetCard，数据到达前后卡高一致 */
.asset-skeleton__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 15px 16px 16px;
}

.asset-skeleton__title,
.asset-skeleton__desc {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.asset-skeleton__title {
  gap: 8px;
  min-height: 43px;
}

.asset-skeleton__desc {
  gap: 7px;
  min-height: 39px;
}

.asset-skeleton__line {
  height: 12px;
}

.asset-skeleton__line.is-desc {
  height: 10px;
}

.is-title-a {
  width: 82%;
}

.is-title-b {
  width: 56%;
}

.is-desc-a {
  width: 92%;
}

.is-desc-b {
  width: 68%;
}

/* 数值条：比正文灰块更深一档，对应真实卡的深色 bar */
.asset-skeleton__bar {
  min-height: 47px;
  background: color-mix(in oklab, var(--tb-text-primary) 13%, var(--tb-bg-surface));
}

.asset-skeleton__meta {
  display: flex;
  align-items: center;
  min-height: 21px;
}

.is-meta {
  width: 48%;
  height: 12px;
}

.asset-skeleton__time {
  margin-top: auto;
  width: 42%;
  height: 11px;
}

/* ========== 动效降级 ========== */
@media (prefers-reduced-motion: reduce) {
  .asset-skeleton__block::after {
    animation: none;
    opacity: 0;
  }
}
</style>
