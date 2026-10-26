<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 登录页右侧展示台：把「这张产品长什么样」讲清楚。
 *
 * 颜色策略：容器写 color-scheme: dark，light-dark() 会按容器解析（docs/09-样式规范.md §3.3 约定 3），
 * 因此展示台整块恒为深色且无需硬编码色值；而代表幻灯片的纸面 .preview 再把 color-scheme 反切回 light，
 * 于是「深色展台 + 白色纸面」这两层语义都由 Token 表达，不随 UI 主题翻转。
 *
 * 预览内容用 CSS 假排版而不是截图：不引入静态资源、不受后端影响，
 * 且沿用了 DocTemplateView / HomeworkGradingView 的 toolbar + 点阵 canvas 文档范式。
 */

const AUTOPLAY_S = 6.8
const AUTOPLAY_MS = AUTOPLAY_S * 1000
/** CSS 侧动画时长必须与 AUTOPLAY_MS 同值，且带单位 */
const autoplayCss = `${AUTOPLAY_S}s`

const PREIEWS = [
  {
    id: 'doc',
    no: '01',
    name: '文档模板',
    claim: '一句课题，交出一份排版合规的教案',
    file: '七年级数学 · 有理数.docx',
    kind: 'doc',
    chips: ['模板解析', '槽位填充', '格式校验'],
  },
  {
    id: 'grade',
    no: '02',
    name: '作业批改',
    claim: '整班上传，逐题给出分值与批语',
    file: '八班 · 第 3 章课后作业',
    kind: 'grade',
    chips: ['手迹识别', '分项评分', '评语下发'],
  },
  {
    id: 'kb',
    no: '03',
    name: '课程知识库',
    claim: '资料切片入库，提问即得带出处的回答',
    file: '《教育学基础》.md',
    kind: 'kb',
    chips: ['切片向量化', '混合检索', '引用溯源'],
  },
  {
    id: 'talk',
    no: '04',
    name: '演讲与会议',
    claim: 'AI 讲稿与数字人会议室，把课讲完也讲透',
    file: '期中家长会 · 演讲稿.pptx',
    kind: 'deck',
    chips: ['讲稿生成', '提词放映', '模拟会议'],
  },
]

const active = ref(0)
const paused = ref(false)
const current = computed(() => PREIEWS[active.value])

const prefersReduced =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const finePointer =
  typeof window !== 'undefined' && window.matchMedia('(pointer: fine)').matches

let timer = 0

function select(index) {
  active.value = index
  restart()
}

function advance() {
  active.value = (active.value + 1) % PREIEWS.length
  restart()
}

/** 暂停时清定时器并冻结进度动画；恢复则整轮重来，避免「进度条与切换时刻」失步 */
function stopTimer() {
  clearTimeout(timer)
  timer = 0
}

function restart() {
  stopTimer()
  if (prefersReduced || paused.value) return
  timer = setTimeout(advance, AUTOPLAY_MS)
}

function hold() {
  paused.value = true
  stopTimer()
}

function release() {
  paused.value = false
  restart()
}

/** 键盘按规范只给左右箭头，走到两端即停 */
function onTabKeydown(event, index) {
  let to = -1
  if (event.key === 'ArrowRight') to = Math.min(index + 1, PREIEWS.length - 1)
  if (event.key === 'ArrowLeft') to = Math.max(index - 1, 0)
  if (to < 0) return
  event.preventDefault()
  select(to)
  const tabs = event.currentTarget.parentNode?.children
  tabs?.[to]?.focus()
}

/** 展台环境光随指针游移：直接写 CSS 自定义属性，不进响应式，避免每帧重渲染 */
const stageEl = ref(null)

function onStagePointerMove(event) {
  if (!finePointer || !stageEl.value) return
  const rect = stageEl.value.getBoundingClientRect()
  stageEl.value.style.setProperty('--gx', `${((event.clientX - rect.left) / rect.width) * 100}%`)
  stageEl.value.style.setProperty('--gy', `${((event.clientY - rect.top) / rect.height) * 100}%`)
}

function onVisibility() {
  if (document.hidden) hold()
  else release()
}

onMounted(() => {
  restart()
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  stopTimer()
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <section
    ref="stageEl"
    class="showcase"
    data-appearance="dark"
    :class="{ 'is-paused': paused || prefersReduced }"
    aria-label="功能预览"
    @pointermove="onStagePointerMove"
    @mouseenter="hold"
    @mouseleave="release"
    @focusin="hold"
    @focusout="release"
  >
    <span class="showcase__grain" aria-hidden="true" />
    <span class="showcase__glow" aria-hidden="true" />
    <span class="showcase__ring showcase__ring--a" aria-hidden="true" />
    <span class="showcase__ring showcase__ring--b" aria-hidden="true" />

    <header class="showcase__head">
      <p class="showcase__eyebrow">TrueBuddy Workbench</p>
      <h2 class="showcase__claim">把一学期的备课、批改与演讲，收进同一张工作台</h2>
      <p class="showcase__lede">
        登录后直接进入文档模板、作业批改、课程知识库、PPT 演讲与模拟会议。
      </p>
    </header>

    <div class="showcase__deck">
      <p class="deck__caption">
        <span class="deck__no">{{ current.no }}</span>
        <span class="deck__title">{{ current.claim }}</span>
      </p>

      <div class="deck__stack">
        <span class="stack__ghost stack__ghost--far" aria-hidden="true" />
        <span class="stack__ghost stack__ghost--near" aria-hidden="true" />

        <article
          id="pv-panel"
          class="preview"
          :key="current.id"
          role="tabpanel"
          :aria-label="current.name"
        >
          <div class="preview__toolbar">
            <span class="preview__dots" aria-hidden="true"><i /><i /><i /></span>
            <span class="preview__file">{{ current.file }}</span>
            <span class="preview__tag">{{ current.name }}</span>
          </div>

          <!-- 文档：标题槽 + 正文行，行宽用类名而不是内联样式 -->
          <div v-if="current.kind === 'doc'" class="pv pv--doc">
            <p class="pv__h">1. 教学目标</p>
            <span class="ln ln--92" />
            <span class="ln ln--86" />
            <span class="ln ln--70" />
            <p class="pv__h">2. 重难点</p>
            <span class="ln ln--80" />
            <span class="ln ln--56" />
            <p class="pv__h">3. 教学过程</p>
            <span class="ln ln--92" />
            <span class="ln ln--74" />
            <p class="pv__badge">由模板槽位自动填充</p>
          </div>

          <!-- 批改：分值环 + 评分项 + 生成的批语 -->
          <div v-else-if="current.kind === 'grade'" class="pv pv--grade">
            <div class="score">
              <svg class="score__ring" viewBox="0 0 44 44" aria-hidden="true">
                <circle class="score__track" cx="22" cy="22" r="19" />
                <circle class="score__fill" cx="22" cy="22" r="19" />
              </svg>
              <span class="score__num">82</span>
              <span class="score__unit">分</span>
            </div>
            <ul class="rub">
              <li>
                <span class="rub__label">步骤完整</span>
                <span class="rub__bar"><i class="rub__fill rub__fill--90" /></span>
              </li>
              <li>
                <span class="rub__label">计算准确</span>
                <span class="rub__bar"><i class="rub__fill rub__fill--70" /></span>
              </li>
              <li>
                <span class="rub__label">书写规范</span>
                <span class="rub__bar"><i class="rub__fill rub__fill--45" /></span>
              </li>
            </ul>
            <div class="remark">
              <p class="pv__h">批语</p>
              <span class="ln ln--92" />
              <span class="ln ln--64" />
            </div>
            <p class="pv__badge">可直接下发给学生</p>
          </div>

          <!-- 知识库：问答气泡 + 引用 -->
          <div v-else-if="current.kind === 'kb'" class="pv pv--kb">
            <p class="bub bub--q">建构主义强调怎样的学习情境？</p>
            <p class="bub bub--a">
              以学习者主动建构为中心，把新知挂到已有经验上……
              <span class="cite">〔1〕教育学基础 · P42</span>
            </p>
            <p class="bub bub--q">给一个课堂导入例子</p>
            <p class="bub bub--a">
              用一段 30 秒的生活情境开场……
              <span class="cite">〔2〕课程与教学论 · P17</span>
            </p>
          </div>

          <!-- 演讲：缩略列 + 讲稿行 + 提词计时 -->
          <div v-else class="pv pv--deck">
            <div class="thumbs" aria-hidden="true">
              <i /><i class="is-active" /><i />
            </div>
            <p class="pv__h">本页讲稿</p>
            <span class="ln ln--92" />
            <span class="ln ln--86" />
            <span class="ln ln--74" />
            <p class="timer">提词 04:12 / 12:00</p>
          </div>
        </article>
      </div>

      <ul class="deck__chips">
        <li v-for="chip in current.chips" :key="chip">{{ chip }}</li>
      </ul>
    </div>

    <nav class="showcase__nav" aria-label="功能预览切换">
      <button
        v-for="(item, index) in PREIEWS"
        :id="`pv-tab-${item.id}`"
        :key="item.id"
        type="button"
        role="tab"
        class="nav__item"
        :class="{ 'is-active': index === active }"
        :aria-selected="index === active"
        aria-controls="pv-panel"
        :tabindex="index === active ? 0 : -1"
        @click="select(index)"
        @keydown="onTabKeydown($event, index)"
      >
        <span class="nav__no">{{ item.no }}</span>
        <span class="nav__name">{{ item.name }}</span>
        <span class="nav__bar" aria-hidden="true">
          <i :key="`${active}-${paused}`" :class="{ 'is-run': index === active }" />
        </span>
      </button>
    </nav>
  </section>
</template>

<style scoped>
.showcase {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--tb-size-5);
  min-height: 0;
  height: 100%;
  padding: clamp(var(--tb-size-5), 3.4vw, var(--tb-size-7));
  overflow: hidden;
  /* 容器跟随全局明暗（不再强制 color-scheme: dark）：
     亮色下展台是暖纸上的浅色画板，暗色下是深墨蓝画板，
     文字、描边、软底仍由语义变量自动换算，无需硬编码色值。
     展台**底色**由父层 .stage 画出（见 LoginView），本块保持透明。 */
  color: var(--tb-text-primary);
  isolation: isolate;

  /* 四枚叠加装饰：用语义色派生，两态自动成立 */
  --stage-glow: color-mix(in oklab, var(--tb-primary) 20%, transparent);
  --stage-line: color-mix(in oklab, var(--tb-text-primary) 14%, transparent);
  --stage-ghost: color-mix(in oklab, var(--tb-text-primary) 8%, transparent);
  --stage-card-shadow: light-dark(
    color-mix(in oklab, #191d24 14%, transparent),
    color-mix(in oklab, #000000 60%, transparent)
  );
}

/* —— 装饰层：全部不响应指针，且只动画 transform / opacity —— */
.showcase__grain {
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: var(--tb-noise);
  opacity: calc(var(--tb-grain) * 0.5);
  /* 不用 mix-blend-mode：底色已移到父层 .stage，本块这个隔离组里没有可混合的背板 */
  pointer-events: none;
}

.showcase__glow {
  --gx: 60%;
  --gy: 40%;
  position: absolute;
  inset: -20%;
  z-index: -1;
  background: radial-gradient(
    38% 38% at var(--gx) var(--gy),
    var(--stage-glow),
    transparent 70%
  );
  opacity: 0.75;
  filter: blur(6px);
  pointer-events: none;
  transition: opacity var(--tb-dur-4) var(--tb-motion-ease);
}

.showcase__ring {
  position: absolute;
  z-index: -1;
  border: 1px dashed var(--stage-line);
  border-radius: 50%;
  pointer-events: none;
}

.showcase__ring--a {
  width: 460px;
  height: 460px;
  right: -150px;
  top: -140px;
  animation: showcase-spin 42s var(--tb-ease-linear) infinite;
}

.showcase__ring--b {
  width: 280px;
  height: 280px;
  left: -110px;
  bottom: -120px;
  border-style: solid;
  /* 亮色态展台底是 brand-800 夜蓝，用 --tb-primary（brand-600）描边几乎看不见，改用浅阶 */
  border-color: color-mix(in oklab, var(--tb-brand-300) 42%, transparent);
  animation: showcase-spin 28s var(--tb-ease-linear) infinite reverse;
}

@keyframes showcase-spin {
  to {
    transform: rotate(360deg);
  }
}

/* —— 文案区 —— */
.showcase__head {
  max-width: 34ch;
}

.showcase__eyebrow {
  margin: 0 0 var(--tb-size-3);
  color: var(--tb-primary-text);
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-widest);
  text-transform: uppercase;
}

.showcase__claim {
  margin: 0;
  font-family: var(--tb-font-display);
  font-size: clamp(1.4rem, 2.2vw, var(--tb-text-2xl));
  font-weight: var(--tb-weight-display);
  line-height: var(--tb-leading-snug);
  letter-spacing: var(--tb-tracking-tight);
}

.showcase__lede {
  margin: var(--tb-size-3) 0 0;
  max-width: 32ch;
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
}

/* —— 卡组 —— */
.showcase__deck {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: var(--tb-size-4);
  min-height: 0;
}

.deck__caption {
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-3);
  margin: 0;
}

.deck__no {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  /* 亮色态展台底是夜蓝，tertiary 在这个底上只有 2.6:1，抬到 secondary */
  color: var(--tb-text-secondary);
  font-variant-numeric: tabular-nums;
}

.deck__title {
  font-size: var(--tb-text-md);
  color: var(--tb-text-primary);
}

.deck__stack {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
}

/* 后两张「纸」只露边缘，暗示可翻；偏移与倾斜都走 transform */
.stack__ghost {
  position: absolute;
  inset: 6% 8%;
  border-radius: var(--tb-radius-lg);
  background: var(--stage-ghost);
  border: 1px solid color-mix(in oklab, var(--tb-text-primary) 16%, transparent);
  pointer-events: none;
}

.stack__ghost--far {
  transform: translate(4%, 6%) rotate(2.4deg) scale(0.92);
  opacity: 0.5;
}

.stack__ghost--near {
  transform: translate(-3%, 3%) rotate(-1.6deg) scale(0.96);
  opacity: 0.75;
}

/* 幻灯片预览：纸面恒浅，与展台相反 */
.preview {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  max-width: 460px;
  height: 100%;
  max-height: 320px;
  overflow: hidden;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  /* 反切换回浅色解析：这块纸两态都白 */
  color-scheme: light;
  background-color: var(--tb-bg-surface);
  color: var(--tb-text-primary);
  box-shadow: 0 22px 60px var(--stage-card-shadow);
  animation: preview-in var(--tb-dur-4) var(--tb-ease-out) both;
}

@keyframes preview-in {
  from {
    opacity: 0;
    transform: translate3d(0, 12px, 0) scale(0.985);
  }
}

.preview__toolbar {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  padding: var(--tb-size-2) var(--tb-size-3);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  background-color: var(--tb-bg-raised);
}

.preview__dots {
  display: inline-flex;
  gap: var(--tb-size-1);
}

.preview__dots i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--tb-border-strong);
}

.preview__file {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-secondary);
}

.preview__tag {
  flex: 0 0 auto;
  padding: 2px var(--tb-size-2);
  border-radius: var(--tb-radius-sm);
  background-color: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: var(--tb-text-2xs);
}

.pv {
  min-height: 0;
  overflow: hidden;
}

.pv {
  padding: var(--tb-size-4);
  /* 纸面上的点阵底纹，与 DocTemplateView 的 .display-box__canvas 同源 */
  background-image: radial-gradient(var(--tb-border) 1px, transparent 1px);
  background-size: 22px 22px;
}

.pv--grade {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--tb-size-4);
  align-content: start;
}

.pv--kb {
  display: grid;
  gap: var(--tb-size-3);
  align-content: start;
}

.pv--deck {
  display: grid;
  gap: var(--tb-size-2);
  align-content: start;
}

.pv__h {
  margin: 0 0 var(--tb-size-2);
  font-size: var(--tb-text-sm);
  font-weight: 600;
  color: var(--tb-primary-text);
}

.pv__h + .ln {
  margin-top: var(--tb-size-1);
}

/* 假正文行：宽度用刻度类，不写内联样式 */
.ln {
  display: block;
  height: 9px;
  margin-bottom: var(--tb-size-2);
  border-radius: var(--tb-radius-xs);
  background-color: color-mix(in oklab, var(--tb-text-secondary) 22%, transparent);
}

.ln--92 { width: 92%; }
.ln--86 { width: 86%; }
.ln--80 { width: 80%; }
.ln--74 { width: 74%; }
.ln--70 { width: 70%; }
.ln--64 { width: 64%; }
.ln--56 { width: 56%; }

.pv__badge {
  display: inline-block;
  margin: var(--tb-size-2) 0 0;
  padding: 2px var(--tb-size-2);
  border: var(--tb-border-width) solid var(--tb-primary-border);
  border-radius: var(--tb-radius-sm);
  background-color: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-size: var(--tb-text-2xs);
}

/* 批改卡的批语区横跨分值环与评分项两列 */
.remark {
  grid-column: 1 / -1;
  margin-top: 0;
  padding-top: var(--tb-size-2);
  border-top: var(--tb-border-width) solid var(--tb-border);
}

.pv--grade .pv__badge {
  grid-column: 1 / -1;
  justify-self: start;
}

.score {
  position: relative;
  display: grid;
  place-content: center;
  width: 92px;
  height: 92px;
}

.score__ring {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.score__track,
.score__fill {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
}

.score__track {
  stroke: color-mix(in oklab, var(--tb-text-secondary) 18%, transparent);
}

.score__fill {
  stroke: var(--tb-primary);
  stroke-dasharray: 119.4;
  stroke-dashoffset: 21.5;
}

.score__num {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-2xl);
  font-weight: var(--tb-weight-display);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.score__unit {
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
}

.rub {
  display: grid;
  gap: var(--tb-size-3);
  align-content: center;
  margin: 0;
  padding: 0;
  list-style: none;
}

.rub li {
  display: grid;
  grid-template-columns: 5.5em minmax(0, 1fr);
  align-items: center;
  gap: var(--tb-size-2);
}

.rub__label {
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-secondary);
}

.rub__bar {
  height: 6px;
  border-radius: var(--tb-radius-xs);
  background-color: color-mix(in oklab, var(--tb-text-secondary) 14%, transparent);
  overflow: hidden;
}

.rub__fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background-color: var(--tb-primary);
  transform-origin: left center;
}

.rub__fill--90 { width: 90%; }
.rub__fill--70 { width: 70%; }
.rub__fill--45 { width: 45%; }

.bub {
  margin: 0;
  max-width: 88%;
  padding: var(--tb-size-2) var(--tb-size-3);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-snug);
  border-radius: var(--tb-radius-md);
}

.bub--q {
  justify-self: end;
  background-color: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.bub--a {
  justify-self: start;
  border: var(--tb-border-width) solid var(--tb-border);
  background-color: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
}

.cite {
  display: block;
  margin-top: var(--tb-size-1);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
}

.thumbs {
  display: flex;
  gap: var(--tb-size-2);
  margin-bottom: var(--tb-size-3);
}

.thumbs i {
  width: 54px;
  height: 34px;
  border: var(--tb-border-width) solid var(--tb-border-strong);
  border-radius: var(--tb-radius-xs);
  background-color: var(--tb-bg-raised);
}

.thumbs i.is-active {
  border-color: var(--tb-primary-border);
  background-color: var(--tb-primary-soft);
}

.timer {
  margin: var(--tb-size-2) 0 0;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.deck__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tb-size-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.deck__chips li {
  padding: 2px var(--tb-size-3);
  border: var(--tb-border-width) solid
    var(--stage-line);
  border-radius: var(--tb-radius-sm);
  color: var(--tb-text-secondary);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-normal);
}

/* —— 底部导航 —— */
.showcase__nav {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--tb-size-3);
}

.nav__item {
  display: grid;
  grid-template-rows: auto auto 2px;
  gap: var(--tb-size-1);
  padding: var(--tb-size-2) 0;
  border: 0;
  background: none;
  color: var(--tb-text-secondary);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: color var(--tb-motion-dur) var(--tb-motion-ease);
}

.nav__item:hover {
  color: var(--tb-text-primary);
}

.nav__item:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 4px;
  border-radius: var(--tb-radius-xs);
}

.nav__no {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  font-variant-numeric: tabular-nums;
}

.nav__name {
  font-size: var(--tb-text-xs);
  white-space: nowrap;
}

.nav__item.is-active {
  color: var(--tb-text-primary);
}

.nav__bar {
  display: block;
  height: 2px;
  background-color: var(--stage-line);
  overflow: hidden;
}

.nav__bar i {
  display: block;
  height: 100%;
  background-color: var(--tb-primary);
  transform: scaleX(0);
  transform-origin: left center;
}

.nav__bar i.is-run {
  /* 时长取自脚本里的 autoplayCss，与 AUTOPLAY_MS 同源，改一处即可 */
  animation: nav-fill v-bind(autoplayCss) linear;
}

/* 悬停 / 焦点 / 减弱动效时冻结进度，与 JS 里的 hold() 同步 */
.showcase.is-paused .nav__bar i.is-run {
  animation-play-state: paused;
  transform: scaleX(0);
  animation: none;
}

@keyframes nav-fill {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}

/* 矮视口：先保证纸面预览不被裁切，副文案与装饰让位 */
@media (max-height: 780px) and (min-width: 1025px) {
  .showcase {
    gap: var(--tb-size-4);
  }

  .showcase__lede {
    display: none;
  }

  .showcase__ring--b {
    display: none;
  }

  .pv {
    padding: var(--tb-size-2);
  }

  .ln {
    margin-bottom: var(--tb-size-1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .showcase__ring--a,
  .showcase__ring--b {
    animation: none;
  }

  .preview {
    animation: none;
  }
}
</style>
