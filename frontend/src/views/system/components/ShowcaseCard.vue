<template>
  <button
    class="showcase-card"
    :class="[`showcase-card--${variant}`, `is-${pattern}`, { 'has-media': image }]"
    type="button"
    @click="$emit('open')"
  >
    <!-- entry 版式：与 HomeHero 的 A2 双入口卡同一外观（标题 + 描述药丸 + 右侧图标块） -->
    <template v-if="variant === 'entry'">
      <!-- 位图打底：与 HomeHero 的 A2/A3 同一套「图—染—压」三层处理，文字压左侧留白区 -->
      <span v-if="image" class="showcase-card__media" aria-hidden="true">
        <img class="showcase-card__img" :src="image" alt="" decoding="async" />
        <span class="showcase-card__tint"></span>
        <span class="showcase-card__scrim"></span>
        <span class="showcase-card__grain"></span>
      </span>

      <span class="showcase-card__text">
        <span class="showcase-card__row">
          <span class="showcase-card__title" :title="title">{{ title }}</span>
          <span v-if="badge" class="showcase-card__badge" :class="`is-${badge}`">
            <el-icon :size="12"><component :is="badgeIcon" /></el-icon>
            <span>{{ badgeLabel }}</span>
          </span>
        </span>
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
    </template>

    <template v-else>
      <span class="showcase-card__art">
        <span v-if="badge" class="showcase-card__badge" :class="`is-${badge}`">
          <el-icon :size="12"><component :is="badgeIcon" /></el-icon>
          <span>{{ badgeLabel }}</span>
        </span>

        <span v-if="kindLabel" class="showcase-card__kind">{{ kindLabel }}</span>

        <el-icon class="showcase-card__mark" :size="44">
          <component :is="icon" />
        </el-icon>

        <span class="showcase-card__strip">
          <span v-if="timeLabel" class="showcase-card__time">{{ timeLabel }}</span>
          <span
            v-if="state"
            class="showcase-card__state"
            :class="`is-${stateTone}`"
            :title="`生成状态：${state}`"
          >
            {{ state }}
          </span>
        </span>
      </span>

      <span class="showcase-card__foot">
        <span class="showcase-card__text">
          <span class="showcase-card__title" :title="title">{{ title }}</span>
          <span class="showcase-card__desc" :title="description">{{ description }}</span>
        </span>

        <span v-if="metric !== null && metric !== ''" class="showcase-card__metric">
          {{ metric }}
          <small v-if="metricLabel">{{ metricLabel }}</small>
        </span>
      </span>
    </template>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, Lightning, MagicStick } from '@element-plus/icons-vue'

const props = defineProps({
  variant: { type: String, default: 'asset' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  kindLabel: { type: String, default: '' },
  icon: { type: [Object, Function], required: true },
  /** 封面版式名，取值见 `../constants.js` 的 pattern 字段 */
  pattern: { type: String, default: 'page' },
  /** entry 版式的背景位图（public/hero/），为空时保持纯色渐变底 */
  image: { type: String, default: '' },
  /** 'new' | 'hot' | null */
  badge: { type: String, default: null },
  metric: { type: [Number, String], default: null },
  metricLabel: { type: String, default: '' },
  state: { type: String, default: '' },
  stateTone: { type: String, default: 'neutral' },
  timeLabel: { type: String, default: '' },
})

defineEmits(['open'])

const BADGE_LABEL = { new: '上新', hot: '热门' }
const BADGE_ICON = { new: MagicStick, hot: Lightning }

const badgeLabel = computed(() => BADGE_LABEL[props.badge] || '')
const badgeIcon = computed(() => BADGE_ICON[props.badge] || MagicStick)
</script>

<style scoped>
/* 根节点是 button：白送键盘可达与焦点环，这里先抹掉浏览器默认外观 */
.showcase-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0;
  text-align: left;
  font: inherit;
  color: inherit;
  cursor: pointer;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  /* 媒体卡破例用大圆角，外壳其余元素仍守 Harbor（docs/09 §3.3） */
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

/* ========== 封面 ========== */
.showcase-card__art {
  /* --art-line 供各版式的 ::before 继承取用，换主题时只此一处 */
  --art-line: color-mix(in oklab, var(--tb-primary) 46%, transparent);

  position: relative;
  display: block;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 16%, color-mix(in oklab, var(--tb-primary) 30%, transparent), transparent 56%),
    radial-gradient(circle at 10% 98%, var(--tb-primary-soft), transparent 62%),
    linear-gradient(155deg, var(--tb-bg-sunken) 0%, var(--tb-bg-raised) 100%);
}

/* 封面只有 asset 版式在用：统一 16/9 */
.showcase-card--asset .showcase-card__art {
  aspect-ratio: 16 / 9;
}

/* 版式层：只用几何与 Token 区分类型，不拿状态色当装饰（theme.css 色板注释） */
.is-stack .showcase-card__art::before,
.is-page .showcase-card__art::before,
.is-node .showcase-card__art::before,
.is-bubble .showcase-card__art::before,
.is-slide .showcase-card__art::before,
.is-beam .showcase-card__art::before,
.is-grid .showcase-card__art::before,
.is-glow .showcase-card__art::before,
.is-lines .showcase-card__art::before {
  content: '';
  position: absolute;
  inset: 0;
}

/* 纸张颗粒：给纯色深底一点材质，避免大面积空洞（Token 自带内联 SVG） */
.showcase-card__art::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: var(--tb-noise);
  opacity: 0.09;
}

/* 知识库：一摞纸 */
.is-stack .showcase-card__art::before {
  background-image:
    repeating-linear-gradient(180deg, var(--art-line) 0 2px, transparent 2px 13px),
    linear-gradient(var(--tb-primary-soft), var(--tb-primary-soft)),
    repeating-linear-gradient(180deg, var(--art-line) 0 2px, transparent 2px 13px),
    linear-gradient(var(--tb-primary-soft), var(--tb-primary-soft));
  background-position:
    58% 20%,
    50% 8%,
    30% 34%,
    22% 22%;
  background-size:
    46% 62%,
    58% 78%,
    46% 62%,
    58% 78%;
  background-repeat: no-repeat;
  opacity: 0.9;
}

/* 文档生成：单张带文本行的纸 */
.is-page .showcase-card__art::before {
  background-image:
    repeating-linear-gradient(180deg, var(--art-line) 0 2px, transparent 2px 15px),
    linear-gradient(var(--tb-primary-soft), var(--tb-primary-soft));
  background-position:
    34% 24%,
    26% 12%;
  background-size:
    44% 62%,
    60% 82%;
  background-repeat: no-repeat;
  opacity: 0.95;
}

/* 工作流：局部点阵 + 一条连线（点径与不透明度都取小值，否则亮色下会糊成一片） */
.is-node .showcase-card__art::before {
  background-image:
    linear-gradient(90deg, var(--art-line) 0 100%),
    radial-gradient(circle at center, var(--art-line) 0 2px, transparent 2.5px);
  background-position:
    20% 54%,
    18% 16%;
  background-size:
    60% 2px,
    22px 22px;
  background-repeat: no-repeat, repeat;
  opacity: 0.32;
}

/* 会话：两枚气泡 */
.is-bubble .showcase-card__art::before {
  background-image:
    linear-gradient(var(--tb-primary-soft), var(--tb-primary-soft)),
    linear-gradient(var(--tb-primary-soft), var(--tb-primary-soft));
  background-position:
    18% 20%,
    42% 52%;
  background-size:
    48% 30%,
    34% 24%;
  background-repeat: no-repeat;
  border-radius: 0;
  opacity: 1;
}

/* PPT：屏幕框 + 柱状 */
.is-slide .showcase-card__art::before {
  background-image:
    repeating-linear-gradient(90deg, var(--art-line) 0 5px, transparent 5px 16px),
    linear-gradient(var(--art-line), var(--art-line));
  background-position:
    28% 60%,
    18% 20%;
  background-size:
    44% 24%,
    64% 2px;
  background-repeat: no-repeat;
  opacity: 0.75;
}

/* 会议：斜向光束 */
.is-beam .showcase-card__art::before {
  background-image: repeating-linear-gradient(
    115deg,
    var(--art-line) 0 2px,
    transparent 2px 26px
  );
  opacity: 0.34;
}

/* 批改：方格纸 */
.is-grid .showcase-card__art::before {
  background-image:
    repeating-linear-gradient(90deg, var(--art-line) 0 1px, transparent 1px 20px),
    repeating-linear-gradient(180deg, var(--art-line) 0 1px, transparent 1px 20px);
  opacity: 0.3;
}

/* 光晕：中心柔光 */
.is-glow .showcase-card__art::before {
  background-image: radial-gradient(
    circle at 62% 38%,
    color-mix(in oklab, var(--tb-primary) 52%, transparent) 0%,
    transparent 58%
  );
  opacity: 0.7;
}

/* 指南：通栏细行 */
.is-lines .showcase-card__art::before {
  background-image: repeating-linear-gradient(
    180deg,
    var(--art-line) 0 1px,
    transparent 1px 11px
  );
  opacity: 0.28;
}

/* 水印图标：压在版式层之上、筹码之下 */
.showcase-card__mark {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 1;
  color: var(--tb-primary-text);
  opacity: 0.34;
  transition: opacity var(--tb-dur-2) var(--tb-ease-out);
}

.showcase-card:hover .showcase-card__mark,
.showcase-card:focus-visible .showcase-card__mark {
  opacity: 0.6;
}

/* ========== 角标 ========== */
.showcase-card__badge,
.showcase-card__kind {
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 20px;
  padding: 0 var(--tb-size-2);
  font-size: var(--tb-text-2xs);
  font-weight: 600;
  line-height: 1;
  border-radius: var(--tb-radius-sm);
}

/* 封面版式里角标浮在封面左上；entry 版式里它跟着标题排布，故定位只在封面下生效 */
.showcase-card__art .showcase-card__badge,
.showcase-card__art .showcase-card__kind {
  position: absolute;
  top: 8px;
}

.showcase-card__badge {
  left: 8px;
  gap: var(--tb-size-1);
  color: var(--tb-on-primary);
}

/* 上新 / 热门 是资产自身的数据语义，不是装饰，因此允许占用状态色 */
.showcase-card__badge.is-new {
  background: var(--tb-warning);
}

.showcase-card__badge.is-hot {
  background: var(--tb-danger);
}

.showcase-card__kind {
  right: 8px;
  color: var(--tb-text-secondary);
  background: color-mix(in oklab, var(--tb-bg-surface) 82%, transparent);
  border: var(--tb-border-width) solid var(--tb-border);
}

.showcase-card__strip {
  position: absolute;
  left: 8px;
  bottom: 8px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
}

.showcase-card__time {
  padding: 2px var(--tb-size-2);
  font-size: var(--tb-text-2xs);
  line-height: 1.4;
  color: var(--tb-text-secondary);
  background: color-mix(in oklab, var(--tb-bg-surface) 82%, transparent);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
}

.showcase-card__state {
  padding: 2px var(--tb-size-2);
  font-size: var(--tb-text-2xs);
  line-height: 1.4;
  border-radius: var(--tb-radius-sm);
  border: var(--tb-border-width) solid transparent;
}

.showcase-card__state.is-success {
  color: var(--tb-success);
  background: var(--tb-success-soft);
  border-color: color-mix(in oklab, var(--tb-success) 32%, transparent);
}

.showcase-card__state.is-warning {
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border-color: color-mix(in oklab, var(--tb-warning) 32%, transparent);
}

.showcase-card__state.is-danger {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: color-mix(in oklab, var(--tb-danger) 32%, transparent);
}

.showcase-card__state.is-neutral {
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border-color: var(--tb-border);
}

/* ========== 页脚 ========== */
.showcase-card__foot {
  display: flex;
  align-items: flex-start;
  gap: var(--tb-size-2);
  padding: var(--tb-size-3);
  background: var(--tb-bg-surface);
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.showcase-card__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.showcase-card__title {
  overflow: hidden;
  font-size: var(--tb-text-md);
  font-weight: 600;
  line-height: 1.4;
  color: var(--tb-text-primary);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.showcase-card__desc {
  overflow: hidden;
  font-size: var(--tb-text-xs);
  line-height: 1.5;
  color: var(--tb-text-secondary);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
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

/* ========== entry 版式：与 HomeHero 的 A2 双入口卡同一外观 ========== */
.showcase-card--entry {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding: var(--tb-size-4);
  background:
    radial-gradient(circle at 50% 118%, var(--tb-primary-soft), transparent 62%),
    var(--tb-bg-surface);
}

/* —— 有背景位图时：与 HomeHero 的 A2/A3 同一套「深色画报」处理 ——
   位图恒暗、文字恒白，墨色与白字就地定义、不随明暗反相，其余中性色仍走 Token */
.showcase-card--entry.has-media {
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

.showcase-card--entry .showcase-card__text {
  gap: var(--tb-size-2);
}

.showcase-card--entry .showcase-card__title {
  font-size: var(--tb-text-lg);
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
.showcase-card--entry:hover .showcase-card__thumb,
.showcase-card--entry:focus-visible .showcase-card__thumb {
  transform: scale(1.06);
}

/* —— has-media 的文字反白 —— */
.showcase-card--entry.has-media .showcase-card__text {
  position: relative;
  z-index: 1;
}

.showcase-card--entry.has-media .showcase-card__title {
  color: var(--entry-on-media);
  text-shadow: 0 1px 10px rgb(var(--entry-shade) / 0.45);
}

.showcase-card--entry.has-media .showcase-card__pill {
  color: rgb(255 255 255 / 0.82);
  background: color-mix(in oklab, var(--entry-on-media) 12%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 26%, transparent);
  backdrop-filter: blur(6px);
}

.showcase-card--entry.has-media .showcase-card__metric {
  color: rgb(255 255 255 / 0.9);
  background: color-mix(in oklab, var(--entry-on-media) 12%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 26%, transparent);
}

.showcase-card--entry.has-media .showcase-card__thumb {
  position: relative;
  z-index: 1;
  color: var(--entry-on-media);
  background: color-mix(in oklab, var(--entry-on-media) 14%, transparent);
  border-color: color-mix(in oklab, var(--entry-on-media) 32%, transparent);
}
</style>
