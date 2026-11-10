<template>
  <!--
    Zone D 资产卡：复刻 AtomGit 赛事卡效果（共识 Q2c/Q4a/Q5a）。
    卡体结构色走 --tb-* 语义 Token（明暗自适应）；封面恒为深色画布，
    --tc 按资产类型固定色系（知识库=蓝 / 工作流=紫 / 文档=橙 / 会话=绿）。

    本页是对「hover 只改阴影与描边，不做位移」规范（docs/09 §3.3）的刻意豁免：
    参考效果的 3D 倾斜与上浮是手感核心，均带 prefers-reduced-motion 降级。
  -->
  <button
    ref="stageEl"
    class="stage enter"
    :class="{ 'is-tilting': tilting, 'enter-visible': entered }"
    type="button"
    :style="{ '--tc': tc, '--delay': delay }"
    :aria-label="`${kindLabel}：${title}`"
    @click="$emit('open')"
    @pointermove="onPointerMove"
    @pointerleave="onPointerLeave"
  >
    <!-- 卡片后方氛围色斑 -->
    <span class="amb-local" aria-hidden="true"></span>

    <article class="card">
      <!-- 深色动画封面：静止时盖纱、hover 才播放（coverArt.js 种子化生成） -->
      <div class="art" aria-hidden="true">
        <div class="art-svg" v-html="coverSvg"></div>
        <div class="sheen" :style="{ '--sd': sd }"></div>
        <i
          v-for="(s, i) in sparks"
          :key="i"
          class="spark"
          :style="{ left: s.left, top: s.top, width: s.size, height: s.size, '--sd': s.delay }"
        ></i>
        <div class="veil"></div>
        <div class="glare"></div>
        <!-- 封面左上药丸：文档类显示真实生成状态，其余显示类型名（共识 Q3） -->
        <span class="pill status" :class="pillClass">{{ pillText }}</span>
      </div>

      <div class="body">
        <h3 class="title" :title="title">{{ title }}</h3>
        <p class="desc" :title="description">{{ description }}</p>

        <!-- 深色数值条：key=规模单位（文档/节点/产出），会话无计数显示占位（共识 Q3/Q1） -->
        <div class="bar">
          <span class="bar-key">{{ barKey }}</span>
          <b class="bar-val">{{ barValue }}</b>
        </div>

        <div class="meta">
          <div class="meta-item"><b>{{ timeLabel }}</b><span>更新</span></div>
        </div>

        <div v-if="dates" class="time">
          <div class="dates">
            <span>{{ dates[0] }}</span>
            <span v-if="dates[1]">{{ dates[1] }}</span>
          </div>
        </div>
      </div>
    </article>
  </button>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { coverArt, sheenDelay, sparkLayouts, trackColor } from '../utils/coverArt'
import { formatCount } from '../utils/feed'

const DAY = 86400000

const props = defineProps({
  /** v-for 稳定键，同时作 SVG 渐变 id 的去重后缀 */
  uid: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  /** 资产类型 key（knowledge / workflow / doc / chat），决定封面色系与图案 */
  kind: { type: String, default: 'knowledge' },
  /** 类型名药丸（文档类有状态时改显状态） */
  kindLabel: { type: String, default: '' },
  /** 规模计数（文档数 / 节点数 / 产出数），会话为 null */
  metric: { type: Number, default: null },
  metricLabel: { type: String, default: '' },
  /** 文档生成状态文案（成功 / 部分成功 / 失败） */
  state: { type: String, default: '' },
  stateTone: { type: String, default: 'neutral' },
  timeLabel: { type: String, default: '' },
  createdAt: { type: Number, default: null },
  updatedAt: { type: Number, default: null },
  /** 入场 stagger 次序 */
  index: { type: Number, default: 0 },
})

defineEmits(['open'])

/* ---------- 封面：种子化生成，同一卡片恒定同一图案 ---------- */
const tc = computed(() => trackColor(props.title, props.kind))
const coverSvg = computed(() => coverArt({ id: props.uid || props.title, title: props.title, kind: props.kind, metric: props.metric }))
const sd = computed(() => sheenDelay(props.title))
const sparks = computed(() => sparkLayouts(props.title))
const delay = computed(() => `${Math.min(55 * props.index, 440)}ms`)

/* ---------- 药丸 / 数值条 / 日期 ---------- */
const pillText = computed(() => props.state || props.kindLabel)
const pillClass = computed(() => (props.state ? `is-${props.stateTone}` : 'is-kind'))

const barKey = computed(() => props.metricLabel || '对话')
const barValue = computed(() =>
  typeof props.metric === 'number' && Number.isFinite(props.metric) ? formatCount(props.metric) : '—',
)

function formatDate(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}/${month}/${day}`
}

/** [创建, 更新]；同日合并为单日期，均缺失则整行不渲染 */
const dates = computed(() => {
  const { createdAt, updatedAt } = props
  if (!createdAt && !updatedAt) return null
  const start = formatDate(createdAt || updatedAt)
  const end = createdAt && updatedAt && formatDate(createdAt) !== formatDate(updatedAt) ? formatDate(updatedAt) : ''
  return [start, end]
})

/* ---------- 指针倾斜 + 光斑坐标（rAF 节流，降级条件与参考一致） ---------- */
const stageEl = ref(null)
const tilting = ref(false)
let raf = null

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function canHover() {
  return window.matchMedia('(hover: hover)').matches
}

function onPointerMove(event) {
  if (prefersReducedMotion() || !canHover()) return
  const stage = stageEl.value
  if (!stage) return
  const rect = stage.getBoundingClientRect()
  const x = (event.clientX - rect.left) / rect.width
  const y = (event.clientY - rect.top) / rect.height
  tilting.value = true
  stage.style.setProperty('--mx', `${(100 * x).toFixed(1)}%`)
  stage.style.setProperty('--my', `${(100 * y).toFixed(1)}%`)
  if (raf === null) {
    raf = requestAnimationFrame(() => {
      raf = null
      stage.style.setProperty('--rx', `${((0.5 - y) * 9).toFixed(2)}deg`)
      stage.style.setProperty('--ry', `${((x - 0.5) * 12).toFixed(2)}deg`)
    })
  }
}

function onPointerLeave() {
  const stage = stageEl.value
  if (!stage) return
  tilting.value = false
  stage.style.setProperty('--rx', '0deg')
  stage.style.setProperty('--ry', '0deg')
}

/* ---------- 入场 stagger（IntersectionObserver，freeze 一次） ---------- */
const entered = ref(false)
let io = null

onMounted(() => {
  if (!('IntersectionObserver' in window)) {
    entered.value = true
    return
  }
  io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entered.value = true
          io.disconnect()
          io = null
        }
      })
    },
    { threshold: 0.05, rootMargin: '0px 0px -20px 0px' },
  )
  io.observe(stageEl.value)
})

onBeforeUnmount(() => {
  if (io) io.disconnect()
  if (raf !== null) cancelAnimationFrame(raf)
})
</script>

<style scoped>
/* beamRot 依赖的 CSS 注册属性（参考件同款） */
@property --beam {
  syntax: '<angle>';
  inherits: false;
  initial-value: 0deg;
}

/* ---------- 卡面颜色：结构色走 Token，明暗自适应（组件内 light-dark 与 ShowcaseCard 同例） ---------- */
.stage {
  --card-from: light-dark(#ffffffed, #1d212c);
  --card-to: light-dark(#ffffffd1, #161a23);
  --card-border: light-dark(#ffffffeb, rgb(255 255 255 / 10%));
  --veil-a: light-dark(#ffffff61, #0b0d1261);
  --veil-b: light-dark(#ffffff7a, #0b0d127a);
  --veil-c: light-dark(#ffffff99, #0b0d12f2);
  /* 药丸底恒为白（压深色封面），文字用定值语义色，不随明暗反相 */
  --pill-ink: #3a404b;
  --pill-success: #15803d;
  --pill-warning: #b45309;
  --pill-danger: #e8153a;

  --rx: 0deg;
  --ry: 0deg;
  --sc: 1;
  --ty: 0px;
}

/* ---------- 卡片舞台（button 外壳） ---------- */
.stage {
  perspective: 1400px;
  border-radius: var(--tb-radius-md, 8px);
  color: inherit;
  padding: 0;
  margin: 0;
  font: inherit;
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: none;
  display: block;
  position: relative;
}

/* 鼠标跟随的舞台光晕（hover 显现） */
.stage:before {
  content: '';
  z-index: 0;
  pointer-events: none;
  border-radius: inherit;
  background: radial-gradient(
    190px circle at var(--mx, 50%) var(--my, 50%),
    color-mix(in srgb, var(--tc) 42%, transparent),
    transparent 66%
  );
  opacity: 0;
  transition: opacity 0.45s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1));
  position: absolute;
  inset: 0;
  -webkit-mask: radial-gradient(closest-side, #000 42%, #0000 100%);
  mask: radial-gradient(closest-side, #000 42%, #0000 100%);
}

/* 卡片后方氛围色斑 */
.amb-local {
  z-index: -1;
  pointer-events: none;
  border-radius: inherit;
  opacity: 0.3;
  background: radial-gradient(38% 34% at 10% 6%, #f7455d8c, #0000 70%),
    radial-gradient(34% 30% at 75% 2%, #f786457a, #0000 70%),
    radial-gradient(36% 32% at 34% 58%, #984ff86b, #0000 72%),
    radial-gradient(32% 30% at 90% 54%, #31f6e966, #0000 72%),
    radial-gradient(34% 30% at 52% 97%, #458cf761, #0000 72%),
    radial-gradient(30% 28% at 20% 76%, #f84fc55c, #0000 72%);
  position: absolute;
  inset: 0;
}

/* ---------- 卡片主体 ---------- */
.card {
  border-radius: var(--tb-radius-md, 8px);
  transform: perspective(1400px) rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg)) translateY(var(--ty))
    scale(var(--sc));
  transition: transform 0.62s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1)),
    box-shadow 0.62s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1)), border-color 0.3s;
  background: linear-gradient(158deg, var(--card-from), var(--card-to));
  border: 1px solid var(--card-border);
  flex-direction: column;
  height: 100%;
  display: flex;
  position: relative;
  overflow: hidden;
  box-shadow: 0 10px 30px -14px light-dark(rgb(11 13 18 / 30%), rgb(0 0 0 / 55%)),
    0 2px 8px -4px light-dark(rgb(11 13 18 / 14%), rgb(0 0 0 / 40%)),
    inset 0 1px light-dark(rgb(255 255 255 / 95%), rgb(255 255 255 / 6%)),
    inset 0 -1px light-dark(rgb(255 255 255 / 35%), rgb(255 255 255 / 3%));
}

/* 悬停时的主题色光束描边（conic-gradient + mask 打孔） */
.card:before {
  content: '';
  border-radius: inherit;
  z-index: 9;
  pointer-events: none;
  background: radial-gradient(
      230px circle at var(--mx, 50%) var(--my, 50%),
      var(--tc) 0%,
      color-mix(in srgb, var(--tc) 78%, #fff) 38%,
      transparent 74%
    ),
    conic-gradient(
      from var(--beam),
      transparent 0 52%,
      color-mix(in srgb, var(--tc) 88%, transparent) 70%,
      #fff 81%,
      color-mix(in srgb, var(--tc) 92%, transparent) 89%,
      transparent 95%
    );
  opacity: 0;
  transition: opacity 0.42s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1));
  padding: 2.2px;
  position: absolute;
  inset: 0;
  -webkit-mask-image: linear-gradient(#000 0 0), linear-gradient(#000 0 0);
  mask-image: linear-gradient(#000 0 0), linear-gradient(#000 0 0);
  -webkit-mask-position: 0 0, 0 0;
  mask-position: 0 0, 0 0;
  -webkit-mask-size: auto, auto;
  mask-size: auto, auto;
  -webkit-mask-repeat: repeat, repeat;
  mask-repeat: repeat, repeat;
  -webkit-mask-clip: content-box, border-box;
  mask-clip: content-box, border-box;
  -webkit-mask-origin: content-box, border-box;
  mask-origin: content-box, border-box;
  -webkit-mask-composite: xor;
  mask-composite: exclude;
}

/* 鼠标倾斜中的短过渡 */
.stage.is-tilting .card {
  transition: box-shadow 0.3s, border-color 0.3s, transform 90ms linear;
}

.stage:active .card {
  --sc: 0.985;
}

/* 键盘可达：焦点环与 hover 同强度（参考件无此层，补齐 a11y） */
.stage:focus-visible {
  outline: none;
}

/* ---------- 封面装饰区 ---------- */
.art {
  aspect-ratio: 400 / 130;
  --play: paused;
  background: #0b0d12;
  flex: none;
  width: 100%;
  position: relative;
  overflow: hidden;
}

.art-svg {
  position: absolute;
  inset: 0;
}

.art-svg :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

/* 斜向扫过的高光带 */
.sheen {
  z-index: 2;
  pointer-events: none;
  animation: sheen 7.4s cubic-bezier(0.5, 0, 0.2, 1) infinite;
  animation-delay: var(--sd, 0s);
  background: linear-gradient(102deg, #0000 40%, #ffffff4d 49%, #ffffff0f 56%, #0000 64%);
  position: absolute;
  inset: -40% -14%;
  transform: translate(-135%) skew(-9deg);
}

/* 白色粒子 */
.spark {
  z-index: 3;
  opacity: 0;
  pointer-events: none;
  animation: spark 3.6s ease-in-out infinite;
  animation-delay: var(--sd, 0s);
  background: #fff;
  border-radius: 50%;
  position: absolute;
  box-shadow: 0 0 6px 1px #ffffffd9;
}

/* 静止时的柔化白幕（暗色态换深纱，hover 淡出） */
.veil {
  z-index: 4;
  pointer-events: none;
  -webkit-backdrop-filter: blur(12px) saturate(1.14);
  backdrop-filter: blur(12px) saturate(1.14);
  transition: opacity 0.55s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1));
  background: linear-gradient(var(--veil-a) 0%, var(--veil-b) 62%, var(--veil-c) 100%);
  position: absolute;
  inset: 0;
}

/* 鼠标跟随眩光（hover 显现） */
.glare {
  z-index: 5;
  opacity: 0;
  transition: opacity 0.28s;
  background: radial-gradient(
    340px 250px at var(--mx, 50%) var(--my, 50%),
    #ffffff4d,
    transparent 64%
  );
  position: absolute;
  inset: 0;
}

/* 封面内 SVG 动画层的播放状态与 hover 加速 */
.sheen,
.spark {
  animation-play-state: var(--play, running);
}

.cover-spin {
  animation: cover-spin 30s linear infinite;
  animation-play-state: var(--play, running);
}

.cover-spin-slow {
  animation: cover-spin 52s linear infinite;
  animation-play-state: var(--play, running);
}

.cover-flow {
  animation: cover-flow 5s ease-in-out infinite alternate;
  animation-play-state: var(--play, running);
}

.cover-node {
  transform-box: fill-box;
  transform-origin: 50%;
  animation: cover-node-pulse 3s ease-in-out infinite;
  animation-play-state: var(--play, running);
}

.cover-twinkle {
  animation: cover-twinkle 3.6s ease-in-out infinite alternate;
  animation-play-state: var(--play, running);
}

.cover-breathe {
  animation: cover-breathe 7s ease-in-out infinite alternate;
  animation-play-state: var(--play, running);
}

.cover-bar {
  transform-box: fill-box;
  transform-origin: bottom;
  animation: cover-bar-grow 2.8s ease-in-out infinite alternate;
  animation-play-state: var(--play, running);
}

.cover-drift {
  transform-origin: 50%;
  animation: cover-drift 24s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1)) infinite alternate;
}

/* ---------- 状态药丸（封面左上） ---------- */
.status {
  z-index: 6;
  position: absolute;
  top: 12px;
  left: 12px;
}

.pill {
  border-radius: 999px;
  white-space: nowrap;
  -webkit-backdrop-filter: blur(10px) saturate(1.6);
  backdrop-filter: blur(10px) saturate(1.6);
  border: 1px solid #ffffff47;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 10px;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  color: var(--pill-ink);
  background: #ffffffeb;
}

.pill.is-success {
  color: var(--pill-success);
}

.pill.is-warning {
  color: var(--pill-warning);
}

.pill.is-danger {
  color: var(--pill-danger);
}

/* ---------- 卡片内容区 ---------- */
.body {
  flex-direction: column;
  flex: 1;
  gap: 10px;
  min-width: 0;
  padding: 15px 16px 16px;
  display: flex;
  position: relative;
}

.title {
  letter-spacing: -0.018em;
  color: var(--tb-text-primary);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 43px;
  margin: 0;
  font-size: 16px;
  font-weight: 720;
  line-height: 1.34;
  display: -webkit-box;
  overflow: hidden;
}

.desc {
  color: var(--tb-text-secondary);
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 39px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.56;
  display: -webkit-box;
  overflow: hidden;
}

/* 深色数值条（参考件的「总奖金」条）：key 左、渐变数值右，右上角透主题色 */
.bar {
  box-sizing: border-box;
  background: linear-gradient(142deg, #0e1016d1, #0e1016a3);
  border: 1px solid #ffffff26;
  border-radius: var(--tb-radius-xs, 4px);
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  min-height: 47px;
  padding: 10px 13px;
  display: flex;
  position: relative;
  overflow: hidden;
  box-shadow: 0 8px 22px -10px #0000008c, inset 0 1px #ffffff29;
}

.bar:after {
  content: '';
  pointer-events: none;
  background: radial-gradient(
    130px 64px at 90% -10%,
    color-mix(in srgb, var(--tc) 52%, transparent),
    transparent 72%
  );
  position: absolute;
  inset: 0;
}

.bar-key {
  z-index: 1;
  letter-spacing: 0.02em;
  color: #ffffffb3;
  font-size: 11.5px;
  font-weight: 680;
  position: relative;
}

.bar-val {
  z-index: 1;
  letter-spacing: -0.04em;
  font-variant-numeric: tabular-nums;
  background: linear-gradient(96deg, #fff 8%, #ffe1e7 54%, var(--tc) 132%);
  color: #0000;
  -webkit-background-clip: text;
  background-clip: text;
  font-size: 21px;
  font-weight: 820;
  line-height: 1.15;
  position: relative;
}

/* 数据行 */
.meta {
  align-items: baseline;
  gap: 14px;
  min-height: 21px;
  display: flex;
}

.meta-item {
  align-items: baseline;
  gap: 5px;
  min-width: 0;
  display: flex;
}

.meta-item b {
  color: var(--tb-text-primary);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  font-size: 15px;
  font-weight: 780;
}

.meta-item span {
  color: var(--tb-text-tertiary);
  font-size: 12px;
}

/* 日期行 */
.time {
  margin-top: auto;
  padding-top: 2px;
}

.dates {
  font-family: var(--font-mono, ui-monospace, monospace);
  color: var(--tb-text-tertiary);
  font-variant-numeric: tabular-nums;
  justify-content: space-between;
  font-size: 11px;
  display: flex;
}

/* ---------- 悬停交互（仅支持 hover 的设备） ---------- */
@media (hover: hover) {
  .stage:hover:before {
    opacity: 0.72;
  }

  .stage:hover .card:before {
    opacity: 1;
    animation: beam-rot 3.6s linear infinite;
  }

  .stage:hover .art {
    --play: running;
  }

  .stage:hover .veil {
    opacity: 0;
  }

  .stage:hover .glare {
    opacity: 1;
  }

  .stage:hover .card {
    --sc: 1.022;
    --ty: -7px;
    box-shadow: 0 30px 64px -22px light-dark(rgb(11 13 18 / 42%), rgb(0 0 0 / 65%)),
      0 8px 22px -10px light-dark(rgb(11 13 18 / 20%), rgb(0 0 0 / 45%)),
      0 0 0 1px color-mix(in srgb, var(--tc) 30%, transparent),
      inset 0 1px 0 light-dark(#fff, rgb(255 255 255 / 12%));
    border-color: light-dark(#fffffffa, rgb(255 255 255 / 22%));
  }

  .stage:focus-visible .card {
    box-shadow: 0 30px 64px -22px light-dark(rgb(11 13 18 / 42%), rgb(0 0 0 / 65%)),
      0 8px 22px -10px light-dark(rgb(11 13 18 / 20%), rgb(0 0 0 / 45%)),
      0 0 0 1px color-mix(in srgb, var(--tc) 30%, transparent), 0 0 0 3px var(--tb-ring);
  }

  .stage:hover .sheen {
    animation-duration: 2.6s;
  }

  .stage:hover .cover-spin {
    animation-duration: 8s;
  }

  .stage:hover .cover-spin-slow {
    animation-duration: 16s;
  }

  .stage:hover .cover-flow {
    animation-duration: 1.7s;
  }

  .stage:hover .cover-breathe {
    animation-duration: 2.4s;
  }

  .stage:hover .cover-bar {
    animation-duration: 0.95s;
  }

  .stage:hover .cover-drift {
    animation-duration: 8s;
  }
}

/* ---------- 动画关键帧 ---------- */
@keyframes card-enter {
  0% {
    opacity: 0;
    transform: translateY(26px) rotateX(-8deg) scale(0.97);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes beam-rot {
  to {
    --beam: 360deg;
  }
}

@keyframes sheen {
  0% {
    transform: translate(-135%) skew(-9deg);
  }

  34%,
  to {
    transform: translate(135%) skew(-9deg);
  }
}

@keyframes spark {
  0%,
  to {
    opacity: 0;
    transform: scale(0.35);
  }

  50% {
    opacity: 0.95;
    transform: scale(1);
  }
}

@keyframes cover-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes cover-flow {
  0% {
    transform: translate(0);
  }

  to {
    transform: translate(-20px, 11px);
  }
}

@keyframes cover-breathe {
  0% {
    opacity: 0.55;
    transform: scale(0.93);
  }

  to {
    opacity: 1;
    transform: scale(1.07);
  }
}

@keyframes cover-node-pulse {
  0%,
  to {
    opacity: 0.42;
    transform: scale(0.85);
  }

  50% {
    opacity: 1;
    transform: scale(1.18);
  }
}

@keyframes cover-bar-grow {
  0% {
    transform: scaleY(0.7);
  }

  to {
    transform: scaleY(1.08);
  }
}

@keyframes cover-twinkle {
  0% {
    opacity: 0.5;
  }

  to {
    opacity: 1;
  }
}

@keyframes cover-drift {
  to {
    transform: translate(-2.4%, 1.7%) scale(1.08);
  }
}

/* ---------- 入场：观察前隐藏，进入视口后以 --delay 逐张浮起 ---------- */
.enter {
  opacity: 0;
}

.enter-visible {
  animation: card-enter 0.72s var(--tb-ease-out, cubic-bezier(0.16, 1, 0.3, 1)) both;
  animation-delay: var(--delay, 0s);
}

/* ---------- 动效降级 ---------- */
@media (prefers-reduced-motion: reduce) {
  .card:before,
  .stage:before {
    opacity: 0 !important;
    animation: none !important;
  }

  .art {
    --play: paused;
  }

  .enter {
    opacity: 1;
  }

  .enter-visible {
    animation: none;
  }
}
</style>
