<template>
  <!--
    entry 版式：与 HomeHero 的 A2 双入口卡同一外观（标题 + 描述药丸 + 右侧图标块）。
    仅供 Zone B 可用工具卡使用；Zone D 资产卡已由 AssetCard.vue 承载。
  -->
  <button
    class="showcase-card"
    :class="{ 'has-media': image }"
    type="button"
    @click="$emit('open')"
  >
    <!-- 位图打底：与 HomeHero 的 A2/A3 同一套「图—染—压」三层处理，文字压左侧留白区 -->
    <span v-if="image" class="showcase-card__media" aria-hidden="true">
      <img class="showcase-card__img" :src="image" alt="" decoding="async" />
      <span class="showcase-card__tint"></span>
      <span class="showcase-card__scrim"></span>
      <span class="showcase-card__grain"></span>
    </span>

    <span class="showcase-card__text">
      <span class="showcase-card__title" :title="title">{{ title }}</span>
      <span class="showcase-card__row">
        <span class="showcase-card__pill">
          <el-icon :size="14"><ArrowRight /></el-icon>
          <span>{{ description }}</span>
        </span>
        <span v-if="metric !== null && metric !== ''" class="showcase-card__metric">
          {{ metric }}
          <small v-if="metricLabel">{{ metricLabel }}</small>
        </span>
      </span>
    </span>

    <span class="showcase-card__thumb">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </span>
  </button>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  icon: { type: [Object, Function], required: true },
  /** 背景位图（public/hero/），为空时保持纯色渐变底 */
  image: { type: String, default: '' },
  metric: { type: [Number, String], default: null },
  metricLabel: { type: String, default: '' },
})

defineEmits(['open'])
</script>

<style scoped>
/* 根节点是 button：白送键盘可达与焦点环，这里先抹掉浏览器默认外观。
   布局与 HomeHero 的 A2 双入口卡同一套：横向排布，文字左、图标块右。 */
.showcase-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  width: 100%;
  padding: var(--tb-size-4);
  text-align: left;
  font: inherit;
  color: inherit;
  cursor: pointer;
  background:
    radial-gradient(circle at 50% 118%, var(--tb-primary-soft), transparent 62%),
    var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  overflow: hidden;
  /* hover 只改阴影与描边，不做位移：卡片会跟着鼠标跳，读起来很廉价 */
  transition:
    box-shadow var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}

.showcase-card:hover,
.showcase-card:focus-visible {
  box-shadow: var(--tb-shadow-3);
  border-color: var(--tb-primary-border);
}

.showcase-card:focus-visible {
  outline: none;
  box-shadow: var(--tb-shadow-3), 0 0 0 3px var(--tb-ring);
}

/* —— 有背景位图时：与 HomeHero 的 A2/A3 同一套「深色画报」处理 ——
   位图恒暗、文字恒白，墨色与白字就地定义、不随明暗反相，其余中性色仍走 Token */
.showcase-card.has-media {
  --entry-shade: 26 44 78;
  --entry-on-media: #ffffff;

  position: relative; /* 基类无定位，媒体层与角标都要锚在这里 */
  color: var(--entry-on-media);
  /* 兜底底色与主推卡同一支主色，图片未解码时不会闪白 */
  background: linear-gradient(150deg, var(--tb-primary) 0%, rgb(var(--entry-shade)) 100%);
  isolation: isolate;
}

.showcase-card__media {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.showcase-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 位图主体在右半，左侧留白供文字落位 */
  object-position: 72% 50%;
  filter: light-dark(brightness(1.12) saturate(1.02), brightness(1.05));
}

/* 品牌染色：把整帧色相拉到主色上、只留明暗，与首页其它媒体位归到同一色系 */
.showcase-card__tint {
  position: absolute;
  inset: 0;
  background: linear-gradient(140deg, var(--tb-primary-active) 0%, var(--tb-primary) 100%);
  mix-blend-mode: color;
  opacity: 0.66;
}

/* 压暗层：左侧保文字对比度，顶部与右上角收一次高光纸面 */
.showcase-card__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(
      96deg,
      rgb(var(--entry-shade) / 0.92) 0%,
      rgb(var(--entry-shade) / 0.72) 34%,
      rgb(var(--entry-shade) / 0.24) 68%,
      rgb(var(--entry-shade) / 0.12) 100%
    ),
    linear-gradient(180deg, rgb(var(--entry-shade) / 0.42) 0%, transparent 42%),
    radial-gradient(90% 120% at 100% 0%, rgb(var(--entry-shade) / 0.5) 0%, transparent 58%);
}

.showcase-card__grain {
  position: absolute;
  inset: 0;
  background-image: var(--tb-noise);
  opacity: calc(var(--tb-grain) * 0.6);
  mix-blend-mode: overlay;
}

.showcase-card__text {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
  min-width: 0;
  flex: 1;
}

.showcase-card__title {
  overflow: hidden;
  font-size: var(--tb-text-lg);
  font-weight: 600;
  line-height: 1.4;
  color: var(--tb-text-primary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.showcase-card__row {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  min-width: 0;
}

.showcase-card__pill {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
  min-width: 0;
  height: var(--tb-control-sm);
  padding: 0 var(--tb-size-3);
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-full);
}

.showcase-card__pill span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.showcase-card__metric {
  flex: none;
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  height: var(--tb-control-sm);
  padding: 0 var(--tb-size-2);
  font-size: var(--tb-text-sm);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
}

.showcase-card__metric small {
  font-size: var(--tb-text-2xs);
  font-weight: 400;
}

.showcase-card__thumb {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  color: var(--tb-primary-text);
  background: linear-gradient(150deg, var(--tb-bg-sunken), var(--tb-primary-soft));
  border: var(--tb-border-width) solid var(--tb-primary-border);
  border-radius: var(--tb-radius-lg);
  transition: transform var(--tb-dur-3) var(--tb-ease-spring);
}

/* 与 hero__entry 一致：只有图标块做 scale，卡片本体不移位 */
.showcase-card:hover .showcase-card__thumb,
.showcase-card:focus-visible .showcase-card__thumb {
  transform: scale(1.06);
}

/* —— has-media 的文字反白 —— */
.showcase-card.has-media .showcase-card__text {
  position: relative;
  z-index: 1;
}

.showcase-card.has-media .showcase-card__title {
  color: var(--entry-on-media);
  text-shadow: 0 1px 10px rgb(var(--entry-shade) / 0.45);
}

.showcase-card.has-media .showcase-card__pill {
  color: rgb(255 255 255 / 0.82);
  background: color-mix(in oklab, var(--entry-on-media) 12%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 26%, transparent);
  backdrop-filter: blur(6px);
}

.showcase-card.has-media .showcase-card__metric {
  color: rgb(255 255 255 / 0.9);
  background: color-mix(in oklab, var(--entry-on-media) 12%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 26%, transparent);
}

.showcase-card.has-media .showcase-card__thumb {
  position: relative;
  z-index: 1;
  color: var(--entry-on-media);
  background: color-mix(in oklab, var(--entry-on-media) 14%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 32%, transparent);
}
</style>
