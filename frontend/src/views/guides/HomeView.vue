<script setup>
import { onMounted, onBeforeUnmount, nextTick, ref, computed } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { CircleCheck, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { useSmoothScroll } from '../../composables/useSmoothScroll'
import NavbarItem from '../../components/NavbarItem.vue'
import HeroParticles from './components/HeroParticles.vue'

gsap.registerPlugin(ScrollTrigger)

const { refresh, scrollTo } = useSmoothScroll()

const sections = [
  { id: 'hero', label: '首页', en: 'HOME' },
  { id: 'vision', label: '核心理念', en: 'VISION' },
  { id: 'ai', label: 'AI 助手', en: 'ASSISTANT' },
  { id: 'rail', label: 'AI 工作流', en: 'WORKFLOW' },
  { id: 'docs', label: '文档模板', en: 'TEMPLATE' },
  { id: 'grading', label: '作业批改', en: 'GRADING' },
  { id: 'knowledge', label: '知识库', en: 'KNOWLEDGE' },
  { id: 'insight', label: '数据洞察', en: 'INSIGHT' },
  { id: 'start', label: '开始使用', en: 'START' },
]

/** 本页创建的全部 ScrollTrigger：卸载时只回收这些，不再全局清空 */
let triggers = []

/** 登记本页的 ScrollTrigger（含挂在补间上的），返回原实例便于链式写法 */
function track(trigger) {
  if (trigger) triggers.push(trigger)
  return trigger
}

onMounted(async () => {
  await nextTick()
  setupAnimations()
  setupRail()
  setupScan()
  refresh()
})

/** 章节入场动画 + 装饰层视差 */
function setupAnimations() {
  const stages = Array.from(document.querySelectorAll('[data-section]'))

  // ① 每个章节一条与滚动进度绑定的时间线（整页连续滚动，不做整屏吸附与钉住）：
  //    区间 = 该章节「进入视口底部 → 完全离开视口顶部」，中线 = 章节居中于视口的时刻；
  //    前半段随滚动进场，后半段随滚动退场，
  //    同一章节内各组元素的起手时序与退出幅度各不相同，这就是「元素之间的配合」。
  stages.forEach((sec) => {
    const pick = (sel) => Array.from(sec.querySelectorAll(sel))

    const words = pick('[data-reveal-word]')
    const lines = pick('[data-reveal-line]')
    const items = pick('[data-reveal-item]')
    const cards = pick('[data-card]')
    const zooms = pick('[data-zoom]')
    const slideXs = pick('[data-slide-x]')
    const softFades = pick('[data-fade-in]')
    const fades = pick('[data-fade-out]')
    const counters = pick('[data-count]')
    const bars = pick('[data-bar]')
    const parallax = pick('[data-parallax]')

    // MID 处正好是该章节居中的时刻：时间线中线与滚动中线重合
    const MID = 1.6
    const OUT = MID // 退出段与进场段等长，保证与 100vh 的滚动区间一一对应

    const tl = gsap.timeline({
      scrollTrigger: {
        trigger: sec,
        start: 'top bottom',
        end: 'bottom top',
        scrub: 1,
      },
    })

    /* ---------------- 进场：0 → MID ---------------- */

    // 标题：逐词从遮罩里升起（最早，主视觉先到位）
    if (words.length) {
      tl.fromTo(
        words,
        { yPercent: 120, opacity: 0 },
        { yPercent: 0, opacity: 1, duration: 0.9, stagger: 0.08, ease: 'power3.out' },
        0,
      )
    }

    // 段落：随后逐行上浮
    if (lines.length) {
      tl.fromTo(
        lines,
        { y: 30, opacity: 0 },
        { y: 0, opacity: 1, duration: 0.8, stagger: 0.1, ease: 'power3.out' },
        0.15,
      )
    }

    // 列表项：逐项上浮
    if (items.length) {
      tl.fromTo(
        items,
        { y: 44, opacity: 0 },
        { y: 0, opacity: 1, duration: 0.9, stagger: 0.1, ease: 'power3.out' },
        0.25,
      )
    }

    // 卡片：带轻微旋转地错位叠入，比列表更晚、幅度更大
    if (cards.length) {
      tl.fromTo(
        cards,
        { y: 70, opacity: 0, rotate: (i) => (i % 2 === 0 ? -4 : 4) },
        { y: 0, opacity: 1, rotate: 0, duration: 1, stagger: 0.14, ease: 'power3.out' },
        0.3,
      )
    }

    // 面板 / 核心圆：带倾斜地从下方推入并放正（保留元素自身的静态旋转，如倾斜的纸张）
    zooms.forEach((el, i) => {
      const rotate = Number(el.dataset.rotate) || 0
      tl.fromTo(
        el,
        { scale: 0.9, y: 70, opacity: 0, rotate: rotate - 10 },
        { scale: 1, y: 0, opacity: 1, rotate, duration: 1, ease: 'power3.out' },
        0.25 + i * 0.12,
      )
      tl.to(el, { y: -34, scale: 0.95, opacity: 0.45, duration: OUT, ease: 'power1.in' }, MID)
    })

    // 流程步骤 / 标签：自右侧滑入
    if (slideXs.length) {
      tl.fromTo(
        slideXs,
        { x: 54, opacity: 0 },
        { x: 0, opacity: 1, duration: 0.8, stagger: 0.12, ease: 'power3.out' },
        0.4,
      )
    }

    // 自身带 CSS 自转的元素（知识库圆环）：只淡入，避免覆盖其 animation 的 transform
    if (softFades.length) {
      tl.fromTo(
        softFades,
        { opacity: 0 },
        { opacity: 1, duration: 0.9, stagger: 0.14, ease: 'power2.out' },
        0.25,
      )
    }

    // 数据：数字随滚动实时滚动到目标值
    counters.forEach((el, i) => {
      const state = { value: 0 }
      tl.to(
        state,
        {
          value: Number(el.dataset.count) || 0,
          duration: 0.9,
          ease: 'power2.out',
          onUpdate: () => {
            el.textContent = String(Math.round(state.value))
          },
        },
        0.5 + i * 0.1,
      )
    })

    // 图表：柱条自底部生长，与上方数字同步
    if (bars.length) {
      tl.fromTo(
        bars,
        { scaleY: 0, transformOrigin: 'bottom center' },
        { scaleY: 1, duration: 0.75, stagger: 0.05, ease: 'power2.out' },
        0.45,
      )
    }

    /* ---------------- 退场：MID → 2×MID ----------------
       幅度按「层级」递增：标题冲得最远、卡片几乎留在原地，
       滚动时各层被拉开，形成纵深与速度差 */
    if (words.length) {
      tl.to(words, { yPercent: -110, opacity: 0.15, duration: OUT, ease: 'power1.in' }, MID)
    }
    if (lines.length) {
      tl.to(lines, { y: -70, opacity: 0.3, duration: OUT, ease: 'power1.in' }, MID)
    }
    items.forEach((el) => {
      // data-drift 可覆写退出位移：取正值即与标题反向漂移，形成对峙与纵深
      const drift = Number(el.dataset.drift)
      tl.to(
        el,
        { y: Number.isNaN(drift) ? -34 : drift, opacity: 0.45, duration: OUT, ease: 'power1.in' },
        MID,
      )
    })
    if (cards.length) {
      tl.to(cards, { y: -22, opacity: 0.5, duration: OUT, ease: 'power1.in' }, MID)
    }
    if (slideXs.length) {
      tl.to(slideXs, { x: -18, y: -40, opacity: 0.4, duration: OUT, ease: 'power1.in' }, MID)
    }
    if (softFades.length) {
      tl.to(softFades, { opacity: 0.4, duration: OUT, ease: 'power1.in' }, MID)
    }
    if (bars.length) {
      tl.to(bars, { scaleY: 0.55, opacity: 0.6, duration: OUT, ease: 'power1.in' }, MID)
    }

    /* ---------------- 装饰层视差：与内容错开节奏 ---------------- */
    parallax.forEach((el) => {
      // 视差补间也会各自建一个 ScrollTrigger，必须一并登记，否则卸载后残留
      track(
        gsap.fromTo(
          el,
          { yPercent: 0 },
          {
            yPercent: Number(el.dataset.speed) || 22,
            ease: 'none',
            scrollTrigger: {
              trigger: sec,
              start: 'top bottom',
              end: 'bottom top',
              scrub: 1,
            },
          },
        ).scrollTrigger,
      )
    })

    // 首屏滚动提示：向下滚动即淡出
    fades.forEach((el) => {
      track(
        gsap.to(el, {
          opacity: 0,
          y: 12,
          ease: 'none',
          scrollTrigger: { trigger: sec, start: 'top top', end: '+=25%', scrub: true },
        }).scrollTrigger,
      )
    })

    track(tl.scrollTrigger)
  })
}

/* ==========================================================================
   03 区块 · 横向轨道（参考 karot）
   桌面端把整节钉住，滚动进度直接驱动轨道横移，进度条同步走；
   窄屏（≤768）不 pin，退回浏览器原生横滑 + scroll-snap，避免与页面手势打架。
   ========================================================================== */
let railMM = null

function setupRail() {
  const stage = document.querySelector('.stage--rail')
  const rail = stage?.querySelector('[data-rail]')
  const trackEl = stage?.querySelector('[data-rail-track]')
  const bar = stage?.querySelector('[data-rail-bar]')
  if (!stage || !rail || !trackEl) return

  // 可横移的距离：轨道总宽减去可视宽，视口变化时由 ScrollTrigger 重新求值
  const distance = () => Math.max(0, trackEl.scrollWidth - rail.clientWidth)

  railMM = gsap.matchMedia()
  railMM.add('(min-width: 769px) and (prefers-reduced-motion: no-preference)', () => {
    const tween = gsap.to(trackEl, {
      x: () => -distance(),
      ease: 'none',
      scrollTrigger: {
        trigger: stage,
        start: 'top top',
        end: () => `+=${distance()}`,
        pin: true,
        scrub: 0.6,
        anticipatePin: 1,
        invalidateOnRefresh: true,
        onUpdate: (self) => {
          if (bar) bar.style.transform = `scaleX(${self.progress})`
        },
      },
    })
    track(tween.scrollTrigger)
  })
}

/* ==========================================================================
   03 轨道首卡 · 逐字读取
   卡片进入视口后播一次：读线自上而下扫过，字由浅到深、标注句补上下划线，
   状态从「读取中」翻到「已生成」。reduce-motion 下不播，直接给终态。
   ========================================================================== */
const docgenRef = ref(null)
const scanState = ref('scanning') // scanning | paused | done
let scanIO = null
let scanTl = null
let linkTimer = null

const toggleLabel = computed(() => {
  if (scanState.value === 'scanning') return '暂停'
  if (scanState.value === 'paused') return '继续'
  return '重播'
})

const statusLabel = computed(() => {
  if (scanState.value === 'scanning') return '读取中'
  if (scanState.value === 'paused') return '已暂停'
  return '已生成'
})

const prefersReduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches
const canHover = window.matchMedia('(hover: hover)').matches

/** 右栏「结论 ← 出处」联动：桌面悬停高亮，触摸端点按后 1.5s 自动收起 */
function markEls() {
  const win = docgenRef.value
  return win ? Array.from(win.querySelectorAll('[data-scan-mark]')) : []
}

function linkField(index) {
  const marks = markEls()
  const mark = marks[index]
  if (!mark) return
  clearTimeout(linkTimer)
  marks.forEach((el) => el.classList.remove('is-linked'))
  mark.classList.add('is-linked')
  if (!canHover) linkTimer = setTimeout(() => mark.classList.remove('is-linked'), 1500)
}

function unlinkField(index) {
  // 触摸端不做「移出即收」，交给定时器，避免点按瞬间被清掉
  if (!canHover) return
  markEls()[index]?.classList.remove('is-linked')
}

/** 逐字切片：中日韩逐字、拉丁与数字连写；只切文本节点，不改行内排版 */
function splitWords(card) {
  const prose = card.querySelector('.docgen__prose')
  if (!prose || prose.dataset.split === 'done') return
  prose.dataset.split = 'done'

  const walker = document.createTreeWalker(prose, NodeFilter.SHOW_TEXT)
  const nodes = []
  while (walker.nextNode()) nodes.push(walker.currentNode)

  nodes.forEach((node) => {
    const text = node.nodeValue
    if (!text || !text.trim()) return
    const frag = document.createDocumentFragment()
    for (const part of text.match(/[A-Za-z0-9][A-Za-z0-9.%+-]*|\s+|[\s\S]/gu) ?? []) {
      if (/^\s+$/.test(part)) {
        frag.appendChild(document.createTextNode(part))
        continue
      }
      const span = document.createElement('span')
      span.className = 'docgen__w'
      span.textContent = part
      frag.appendChild(span)
    }
    node.parentNode.replaceChild(frag, node)
  })
}

/** 元素相对 root 的布局纵坐标：只读 offsetTop，不受入场补间的 transform 干扰 */
function topWithin(el, root) {
  let y = 0
  let node = el
  while (node && node !== root) {
    y += node.offsetTop
    node = node.offsetParent
  }
  return y
}

/** 复位：全篇回到「未读」、下划线收回、识别点熄掉、计数归零；期间关掉过渡避免倒放 */
function resetScan(win) {
  const prose = win.querySelector('.docgen__prose')
  if (!prose) return
  const words = Array.from(prose.querySelectorAll('.docgen__w')).filter((word) => word.offsetParent)
  const marks = Array.from(win.querySelectorAll('[data-scan-mark]'))
  const dots = Array.from(win.querySelectorAll('.docgen__field-dot'))
  const confidence = win.querySelector('[data-scan-confidence]')

  prose.classList.add('is-resetting')
  words.forEach((word) => word.classList.add('is-unread'))
  marks.forEach((mark) => mark.classList.remove('is-drawn'))
  if (confidence) confidence.textContent = '0'
  gsap.set(dots, { opacity: 0.25, scale: 0.6 })
  void prose.offsetWidth
  prose.classList.remove('is-resetting')
}

function runScan() {
  const win = docgenRef.value
  if (!win || prefersReduced()) return
  const prose = win.querySelector('.docgen__prose')
  const beam = win.querySelector('.docgen__beam')
  if (!prose || !beam) return

  scanTl?.kill()
  scanState.value = 'scanning'
  resetScan(win)

  // 只统计可见片段：窄屏会隐藏部分段落，隐藏的字不参与计时
  const words = Array.from(prose.querySelectorAll('.docgen__w')).filter((word) => word.offsetParent)
  const marks = Array.from(win.querySelectorAll('[data-scan-mark]'))
  const rows = Array.from(win.querySelectorAll('[data-scan-row]'))
  const dots = rows.map((row) => row.querySelector('.docgen__field-dot'))
  const confidence = win.querySelector('[data-scan-confidence]')

  const height = prose.offsetHeight || 1
  const tops = words.map((word) => topWithin(word, prose))
  const markTops = marks.map((mark) => topWithin(mark, prose))
  // 与参考站点同速：约 55px 每秒，长文案最多 8 秒
  const DUR = gsap.utils.clamp(3.2, 8, height / 55)
  const atY = (y) => gsap.utils.clamp(0, DUR, (y / height) * DUR)

  gsap.set(beam, { y: 0, opacity: 0, willChange: 'transform' })

  let cursor = 0
  const pos = { y: 0 }
  scanTl = gsap.timeline({
    onComplete: () => gsap.set(beam, { clearProps: 'willChange' }),
  })

  scanTl
    .to(beam, { opacity: 1, duration: 0.35, ease: 'power1.out' }, 0)
    .to(
      pos,
      {
        y: height,
        duration: DUR,
        ease: 'none',
        onUpdate: () => {
          beam.style.transform = `translate3d(0, ${pos.y}px, 0)`
          // 读到哪算到哪：逐字去掉未读态，字段计数同步走
          while (cursor < words.length && tops[cursor] <= pos.y + 1) {
            words[cursor].classList.remove('is-unread')
            cursor += 1
          }
          if (confidence) confidence.textContent = String(Math.round((pos.y / height) * 12))
        },
      },
      0,
    )
    .to(beam, { opacity: 0, duration: 0.4, ease: 'power1.in' }, Math.max(0, DUR - 0.3))

  // 读到某句时，给该句补上下划线
  marks.forEach((mark, i) => {
    scanTl.call(() => mark.classList.add('is-drawn'), null, atY(markTops[i]))
  })

  // 识别点按字段来源分三档：模板/需求派生的开场就亮，正文派生的读到那句才亮，总数读完才亮
  rows.forEach((row, i) => {
    const dot = dots[i]
    if (!dot) return
    const link = row.dataset.scanLink
    let at
    if (row.hasAttribute('data-scan-late')) at = Math.max(0.2, DUR - 0.45)
    else if (link !== undefined) at = Math.min(DUR - 0.2, atY(markTops[Number(link)]) + 0.2)
    else at = Math.min(DUR - 0.6, 0.25 + i * 0.12)
    scanTl
      .to(dot, { opacity: 1, scale: 1.35, duration: 0.24, ease: 'power2.out' }, at)
      .to(dot, { scale: 1, duration: 0.32, ease: 'power1.inOut' }, at + 0.24)
  })

  // 状态切换放在读线出页前，因果才成立
  scanTl.call(
    () => {
      scanState.value = 'done'
    },
    null,
    DUR - 0.35,
  )
}

/** 暂停 / 继续 / 重播：读线停下时，逐字着色与字段计数也一并停住 */
function toggleScan() {
  if (scanState.value === 'scanning') {
    scanTl?.pause()
    scanState.value = 'paused'
    return
  }
  if (scanState.value === 'paused') {
    scanTl?.play()
    scanState.value = 'scanning'
    return
  }
  runScan()
}

/** 首次进入视口中段时自动播一次；切好词、布好未读态后才会被看到 */
function setupScan() {
  const win = docgenRef.value
  if (!win) return
  splitWords(win)
  if (prefersReduced()) {
    scanState.value = 'done'
    return
  }
  resetScan(win)
  if (typeof IntersectionObserver === 'undefined') {
    runScan()
    return
  }
  scanIO = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      scanIO?.disconnect()
      scanIO = null
      runScan()
    },
    // 落在视口中段才算「看到了」，避免刚露头就读完
    { rootMargin: '-20% 0px -20% 0px', threshold: 0 },
  )
  scanIO.observe(win)
}

onBeforeUnmount(() => {
  // 只回收本页登记的实例。原先用 ScrollTrigger.getAll() 是全局清空，
  // 会把当时挂在页面上的其它组件的 ScrollTrigger 一并杀掉（例如 /docs）。
  triggers.forEach((t) => t.kill())
  triggers = []
  railMM?.revert()
  railMM = null
  scanTl?.kill()
  scanTl = null
  scanIO?.disconnect()
  scanIO = null
  clearTimeout(linkTimer)
})

/** 平滑滚动到目标章节（供页内 CTA 锚点使用） */
function goTo(idx) {
  const el = document.getElementById(sections[idx].id)
  if (!el) return
  const targetTop = el.offsetTop
  if (Math.abs(window.scrollY - targetTop) < 8) return
  scrollTo(targetTop, { duration: 1.2 })
}
</script>

<template>
  <div class="page">
    <NavbarItem />

    <main>
      <!-- 01 Hero -->
      <section id="hero" data-section data-index="0" class="hero">
        <div class="hero__inner">
          <!-- 左栏：逐词大标题 + 双行副文案 + CTA -->
          <div class="hero__copy">
            <p data-reveal-item data-drift="58" class="hero__eyebrow">TRUEBUDDY · 智能教学平台</p>
            <h1 class="hero__title">
              <span class="hero__word"><span data-reveal-word>让</span></span>
              <span class="hero__word"><span data-reveal-word>教学</span></span>
              <span class="hero__word"><span data-reveal-word>回归</span></span>
              <span class="hero__word hero__word--accent"><span data-reveal-word>创造</span></span>
            </h1>
            <p data-reveal-line class="hero__desc">
              一体化的 AI 教学协作平台，覆盖文档生成、作业批改与知识沉淀，
            </p>
            <p data-reveal-line class="hero__desc hero__desc--2">
              让教师专注于育人本身，而非重复的案头工作。
            </p>
            <div data-reveal-item class="hero__cta">
              <RouterLink to="/login" class="btn btn--primary">立即体验</RouterLink>
              <a href="#vision" class="btn btn--ghost" @click.prevent="goTo(1)">了解平台</a>
            </div>
          </div>

          <!-- 右栏：点阵粒子形变（教 ⇄ 学） -->
          <div data-zoom class="hero__visual">
            <HeroParticles />
          </div>
        </div>
      </section>

      <!-- 02 核心理念 -->
      <section id="vision" data-section data-index="1" class="section section--light">
        <header class="section__head">
          <span data-reveal-item data-drift="72" class="section__index">02 / 09</span>
          <h2 class="section__title">
            <span class="section__word"><span data-reveal-word>我们</span></span>
            <span class="section__word"><span data-reveal-word>相信</span></span>
            <span class="section__word"><span data-reveal-word>什么</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            教学的本质是人对人的影响，技术应当消解重复，放大创造。
          </p>
        </header>
        <div class="vision__grid">
          <article data-card class="vision__card">
            <span class="vision__num">01</span>
            <h3>以教师为中心</h3>
            <p>所有工具围绕教师真实工作流构建，而非让教师迁就系统。</p>
          </article>
          <article data-card class="vision__card">
            <span class="vision__num">02</span>
            <h3>AI 是协作而非替代</h3>
            <p>人工智能负责检索、草拟与校验，决策与温度始终留给教师。</p>
          </article>
          <article data-card class="vision__card">
            <span class="vision__num">03</span>
            <h3>知识应当沉淀</h3>
            <p>每一份教案、每一次批改，都应沉淀为可复用、可检索的资产。</p>
          </article>
        </div>
      </section>

      <!-- 03 AI 智能助手 · 文档读取窗口：独占一屏，读线自上而下把正文逐字点亮 -->
      <section id="ai" data-section data-index="2" class="stage stage--doc">
        <header class="stage__head">
          <span class="stage__idx">03 / 09</span>
          <p class="stage__eyebrow">AI 智能助手</p>
          <h2 class="stage__title">一句需求，<em>整份教案</em>。</h2>
          <p class="stage__lead">上传 Word 模板、说清课时与学情：AI 按模板填字段、挂引用、做校验，产出可直接提交的 docx。</p>
        </header>

        <article ref="docgenRef" class="docgen">
          <div class="docgen__bar">
            <span class="docgen__dots" aria-hidden="true"><i></i><i></i><i></i></span>
            <span class="docgen__crumb">教案模板<span class="docgen__slash">/</span>光合作用 · 第 3 课时</span>
            <span class="docgen__counts">12 个字段 · 6 处引用</span>
            <button type="button" class="docgen__toggle" :aria-label="toggleLabel" @click="toggleScan">
              <Refresh v-if="scanState === 'done'" aria-hidden="true" />
              <VideoPlay v-else-if="scanState === 'paused'" aria-hidden="true" />
              <VideoPause v-else aria-hidden="true" />
              <span class="docgen__toggle-text">{{ toggleLabel }}</span>
            </button>
            <span class="docgen__status" :class="`is-${scanState}`" aria-live="polite">
              <i aria-hidden="true"></i>{{ statusLabel }}
            </span>
          </div>

          <div class="docgen__body">
            <!-- 左：被逐字读取的模板文档 -->
            <article class="docgen__page">
              <p class="docgen__kicker">第三课时 · 能量的捕获</p>
              <p class="docgen__title">光合作用的能量转换</p>
              <div class="docgen__rule" aria-hidden="true"></div>
              <div class="docgen__prose">
                <span class="docgen__beam" aria-hidden="true"></span>
                <p class="docgen__para docgen__para--wide">
                  学情分析：学生已经能画出细胞结构，却很少把「结构」和「能量」联系起来。导入时把一株阳生、一株阴生植物的叶片并排放，让差异先被看见。
                </p>
                <p class="docgen__para">
                  以「叶片为什么总是绿色」作为悬念开场：<mark data-scan-mark class="docgen__mark">叶绿体中的色素主要吸收红光与蓝紫光</mark>。学生先在显微视野里确认叶绿体的位置，再从吸收光谱走向光能的转换。
                </p>
                <p class="docgen__para">
                  教学重点落在光反应——水的光解、ATP 与 NADPH 的生成；难点是<mark data-scan-mark class="docgen__mark docgen__mark--ai">暗反应中三碳化合物的还原路径</mark>。可先放 40 秒动画，再请学生在学案上补全循环图。
                </p>
                <p class="docgen__para">
                  课堂活动：四人小组用 8 分钟完成「叶绿体结构—功能」配对卡，各组代表在白板上贴出两条反应链的衔接点，教师<mark data-scan-mark class="docgen__mark docgen__mark--ai">圈出仍存疑的环节并即时追问</mark>。
                </p>
                <p class="docgen__para docgen__para--short">
                  评价方式：随堂 3 道选择与 1 道简答，<mark data-scan-mark class="docgen__mark">评分细则依据课标「细胞代谢」条目</mark>生成，课后自动归档到班级学情。
                </p>
                <p class="docgen__para docgen__para--wide">
                  板书设计：左栏画叶绿体剖面，标出类囊体与基质；中栏自上而下写「光反应 → ATP、NADPH → 暗反应」；右栏留白，供各组贴配对卡。
                </p>
                <p class="docgen__para docgen__para--wide">
                  作业布置：完成学案第 2、3 题，并拍一张家中绿植的叶片标注受光面；学有余力的同学可补充 C₄ 植物「花环状」碳固定途径。
                </p>
              </div>
            </article>

            <!-- 右：生成依据（模板 / 需求 / 字段 / 引用 / 校验），每条字段都能回指纸面上的对应句 -->
            <aside class="docgen__side">
              <div class="docgen__source">
                <span class="docgen__thumb" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
                <div>
                  <p class="docgen__source-name">人教版 · 教案模板.docx</p>
                  <p class="docgen__source-meta">WORD 模板 · 已解析</p>
                </div>
              </div>

              <div class="docgen__brief">
                <p class="docgen__brief-label">本次需求</p>
                <p class="docgen__brief-text">第三课时，45 分钟，学生基础中等偏上；重点讲清光反应与暗反应的衔接。</p>
              </div>

              <dl class="docgen__fields">
                <div data-scan-row class="docgen__field">
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>教学目标</dt>
                  <dd>结构与功能观</dd>
                </div>
                <div
                  data-scan-row
                  data-scan-link="1"
                  class="docgen__field"
                  @mouseenter="linkField(1)"
                  @mouseleave="unlinkField(1)"
                  @click="linkField(1)"
                >
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>重难点</dt>
                  <dd>三碳化合物还原</dd>
                </div>
                <div
                  data-scan-row
                  data-scan-link="2"
                  class="docgen__field"
                  @mouseenter="linkField(2)"
                  @mouseleave="unlinkField(2)"
                  @click="linkField(2)"
                >
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>课堂活动</dt>
                  <dd>结构—功能配对卡</dd>
                </div>
                <div data-scan-row class="docgen__field">
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>评价方式</dt>
                  <dd>3 选择 + 1 简答</dd>
                </div>
                <div data-scan-row class="docgen__field">
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>课时</dt>
                  <dd>45 分钟</dd>
                </div>
                <div data-scan-row data-scan-late class="docgen__field">
                  <dt><span class="docgen__field-dot" aria-hidden="true"></span>字段已填</dt>
                  <dd><span data-scan-confidence>12</span>/12</dd>
                </div>
              </dl>

              <!-- 引用来源：悬停任一条，纸面上对应的引用句浮出淡底 -->
              <div class="docgen__refs">
                <p class="docgen__refs-title">引用来源</p>
                <ul class="docgen__refs-list">
                  <li class="docgen__ref" @mouseenter="linkField(0)" @mouseleave="unlinkField(0)" @click="linkField(0)">
                    <span class="docgen__ref-num">01</span>教材 1 · 第 99 页
                  </li>
                  <li class="docgen__ref" @mouseenter="linkField(3)" @mouseleave="unlinkField(3)" @click="linkField(3)">
                    <span class="docgen__ref-num">02</span>课标 · 细胞代谢
                  </li>
                  <li class="docgen__ref">
                    <span class="docgen__ref-num">03</span>组内教案 · 光合作用
                  </li>
                  <li class="docgen__ref">
                    <span class="docgen__ref-num">04</span>学情档案 · 高二(3)班
                  </li>
                </ul>
              </div>

              <div class="docgen__foot">
                <p class="docgen__check">
                  <CircleCheck aria-hidden="true" />
                  结构校验通过 · 无残留占位符
                </p>
                <p class="docgen__legend">
                  <span><i class="docgen__legend-dot docgen__legend-dot--cite" aria-hidden="true"></i>教材原文</span>
                  <span><i class="docgen__legend-dot docgen__legend-dot--ai" aria-hidden="true"></i>AI 补全待核对</span>
                </p>
              </div>
            </aside>
          </div>
        </article>
      </section>

      <!-- 04 AI 工作流 · 横向轨道：滚到此后整条轨道横移（参考 karot 的 sticky 横滑） -->
      <section id="rail" data-section data-index="3" class="stage stage--rail">
        <div class="stage__head rail__head">
          <span class="stage__idx">04 / 09</span>
          <p class="stage__eyebrow">AI 工作流</p>
          <h2 class="stage__title">四步，<em>从需求到成稿</em>。</h2>
          <p class="stage__lead">对话起稿、逐字段填充、引用回溯、结构校验——每一步都看得见。</p>
          <p class="rail__hint" aria-hidden="true">横向滚动<span></span></p>
        </div>

        <div class="rail" data-rail>
          <div class="rail__track" data-rail-track>
            <article class="rail__card">
              <span class="rail__num">01</span>
              <h3 class="rail__title">对话起稿</h3>
              <p class="rail__text">说清课时、学情与目标，AI 直接给出结构完整的初稿，不必从空白页开始。</p>
              <div class="rail__art" aria-hidden="true"><i></i><i></i><i></i></div>
              <span class="rail__meta">STEP 01 / 04</span>
            </article>
            <span class="rail__link" aria-hidden="true"></span>
            <article class="rail__card">
              <span class="rail__num">02</span>
              <h3 class="rail__title">逐字段填充</h3>
              <p class="rail__text">教学目标、重难点、活动与评价按你的模板落位，段落与表格结构一处不动。</p>
              <div class="rail__art" aria-hidden="true"><i></i><i></i><i></i></div>
              <span class="rail__meta">STEP 02 / 04</span>
            </article>
            <span class="rail__link" aria-hidden="true"></span>
            <article class="rail__card">
              <span class="rail__num">03</span>
              <h3 class="rail__title">引用可回溯</h3>
              <p class="rail__text">每条结论都挂着教材页码与课标条目，点开即可核对原文，不留黑盒。</p>
              <div class="rail__art" aria-hidden="true"><i></i><i></i><i></i></div>
              <span class="rail__meta">STEP 03 / 04</span>
            </article>
            <span class="rail__link" aria-hidden="true"></span>
            <article class="rail__card">
              <span class="rail__num">04</span>
              <h3 class="rail__title">结构校验</h3>
              <p class="rail__text">导出前自动体检：占位符、表头、字号与页边距，对着模板逐项校验。</p>
              <div class="rail__art" aria-hidden="true"><i></i><i></i><i></i></div>
              <span class="rail__meta">STEP 04 / 04</span>
            </article>
            <span class="rail__link rail__link--end" aria-hidden="true"></span>
          </div>
        </div>

        <div class="rail__progress" aria-hidden="true"><span data-rail-bar></span></div>
      </section>

      <!-- 04 文档模板 · 整屏卡堆叠：上一张留在原地，下一张盖上来 -->
      <section id="docs" data-section data-index="4" class="stack">
        <article class="stack__card">
          <div class="stack__inner">
            <span class="stack__idx">01 / 04</span>
            <p class="stage__eyebrow">文档模板</p>
            <h3 class="stack__title">教案</h3>
            <p class="stack__text">目标、重难点、活动与板书按教研组范式落位，新教师也能一次写对。</p>
            <div class="stack__spec" aria-hidden="true">
              <span class="stack__spec-title"></span>
              <span style="--w: 92%"></span>
              <span style="--w: 78%"></span>
              <span style="--w: 86%"></span>
              <span style="--w: 54%"></span>
            </div>
          </div>
        </article>
        <article class="stack__card">
          <div class="stack__inner">
            <span class="stack__idx">02 / 04</span>
            <p class="stage__eyebrow">文档模板</p>
            <h3 class="stack__title">试卷</h3>
            <p class="stack__text">按章节与难度自动组卷，题号、分值、答题栏严格沿用你的卷面格式。</p>
            <div class="stack__spec" aria-hidden="true">
              <span class="stack__spec-title"></span>
              <span style="--w: 88%"></span>
              <span style="--w: 96%"></span>
              <span style="--w: 64%"></span>
              <span style="--w: 80%"></span>
            </div>
          </div>
        </article>
        <article class="stack__card">
          <div class="stack__inner">
            <span class="stack__idx">03 / 04</span>
            <p class="stage__eyebrow">文档模板</p>
            <h3 class="stack__title">学案</h3>
            <p class="stack__text">预习任务、课堂探究与课后巩固三段式，图例与留白位置保持不变。</p>
            <div class="stack__spec" aria-hidden="true">
              <span class="stack__spec-title"></span>
              <span style="--w: 72%"></span>
              <span style="--w: 90%"></span>
              <span style="--w: 58%"></span>
              <span style="--w: 84%"></span>
            </div>
          </div>
        </article>
        <article class="stack__card">
          <div class="stack__inner">
            <span class="stack__idx">04 / 04</span>
            <p class="stage__eyebrow">文档模板</p>
            <h3 class="stack__title">教研报告</h3>
            <p class="stack__text">把一学期的备课、批改与学情汇总成可提交的报告，数据自动成表。</p>
            <div class="stack__spec" aria-hidden="true">
              <span class="stack__spec-title"></span>
              <span style="--w: 94%"></span>
              <span style="--w: 66%"></span>
              <span style="--w: 88%"></span>
              <span style="--w: 48%"></span>
            </div>
          </div>
        </article>
      </section>

      <!-- 05 作业智能批改 · 左右分栏 + 大数字 -->
      <section id="grading" data-section data-index="5" class="stage stage--split">
        <div class="stage__col">
          <span class="stage__idx">06 / 09</span>
          <p class="stage__eyebrow">作业批改</p>
          <h2 class="stage__title">先让机器<em>跑一遍</em>。</h2>
          <p class="stage__lead">客观题自动判分，主观题给出批注建议；教师只复核、讲评与个别辅导。</p>
          <dl class="stats">
            <div class="stat">
              <dd><span data-count="98">0</span><i>%</i></dd>
              <dt>客观题判分准确率</dt>
            </div>
            <div class="stat">
              <dd><span data-count="3">0</span><i>×</i></dd>
              <dt>批改效率提升</dt>
            </div>
            <div class="stat">
              <dd><span data-count="24">0</span><i>h</i></dd>
              <dt>反馈周期压缩到</dt>
            </div>
          </dl>
        </div>
        <div class="stage__visual" aria-hidden="true">
          <svg class="paper-art" viewBox="0 0 320 400" fill="none">
            <rect x="24" y="16" width="248" height="348" rx="6" class="paper-art__sheet" />
            <line x1="56" y1="72" x2="240" y2="72" class="paper-art__rule" />
            <line x1="56" y1="104" x2="216" y2="104" class="paper-art__rule" />
            <line x1="56" y1="136" x2="232" y2="136" class="paper-art__rule" />
            <line x1="56" y1="168" x2="188" y2="168" class="paper-art__rule" />
            <line x1="56" y1="220" x2="240" y2="220" class="paper-art__rule" />
            <line x1="56" y1="252" x2="204" y2="252" class="paper-art__rule" />
            <path d="M56 300 L96 316 L188 268" class="paper-art__check" />
            <circle cx="232" cy="96" r="26" class="paper-art__mark" />
            <text x="232" y="104" class="paper-art__mark-text">86</text>
          </svg>
        </div>
      </section>

      <!-- 06 知识库 · 跑马灯带 -->
      <section id="knowledge" data-section data-index="6" class="stage stage--kb">
        <header class="stage__head">
          <span class="stage__idx">07 / 09</span>
          <p class="stage__eyebrow">教学知识库</p>
          <h2 class="stage__title">每一份素材，<em>都能被找回</em>。</h2>
        </header>
        <div class="mq" aria-hidden="true">
          <div class="mq__row">
            <span>人教版 · 生物必修 1</span>
            <span>课标 · 细胞代谢</span>
            <span>组内教案 · 光合作用</span>
            <span>学情档案 · 高二(3)班</span>
            <span>校本习题 · 上学期</span>
            <span>人教版 · 生物必修 1</span>
            <span>课标 · 细胞代谢</span>
            <span>组内教案 · 光合作用</span>
            <span>学情档案 · 高二(3)班</span>
            <span>校本习题 · 上学期</span>
          </div>
        </div>
        <ul class="stage__items">
          <li data-reveal-item>
            <span class="stage__items-num">01</span>
            <h3>向量检索</h3>
            <p>语义级召回，跨学科、跨学段地找到同一知识点。</p>
          </li>
          <li data-reveal-item>
            <span class="stage__items-num">02</span>
            <h3>引用可追溯</h3>
            <p>每条回答标注原始出处，复核只需点开那一条。</p>
          </li>
          <li data-reveal-item>
            <span class="stage__items-num">03</span>
            <h3>持续演进</h3>
            <p>教学沉淀自动入库，知识随使用不断变厚。</p>
          </li>
        </ul>
      </section>

      <!-- 07 数据洞察 · 柱状图 + 标签 -->
      <section id="insight" data-section data-index="7" class="stage stage--chart">
        <header class="stage__head">
          <span class="stage__idx">08 / 09</span>
          <p class="stage__eyebrow">数据洞察</p>
          <h2 class="stage__title">每一次教学，<em>都留下轨迹</em>。</h2>
        </header>
        <div class="chart">
          <div class="chart__bars">
            <span data-bar style="--h: 38%"></span>
            <span data-bar style="--h: 62%"></span>
            <span data-bar style="--h: 48%"></span>
            <span data-bar style="--h: 78%"></span>
            <span data-bar style="--h: 55%"></span>
            <span data-bar style="--h: 90%"></span>
            <span data-bar style="--h: 70%"></span>
          </div>
          <div class="chart__legend">
            <p data-reveal-item>班级知识掌握度</p>
            <small data-reveal-item>近 7 个教学单元 · 每周更新</small>
          </div>
        </div>
        <div class="stage__tags">
          <span data-slide-x>学情画像</span>
          <span data-slide-x>薄弱点识别</span>
          <span data-slide-x>分层教学建议</span>
          <span data-slide-x>成长档案</span>
        </div>
      </section>

      <!-- 08 开始使用 · 巨型收尾 -->
      <section id="start" data-section data-index="8" class="stage stage--cta">
        <span class="stage__idx">09 / 09</span>
        <h2 class="stage__title stage__title--xl">
          <span class="section__word"><span data-reveal-word>让</span></span>
          <span class="section__word"><span data-reveal-word>每一次</span></span>
          <span class="section__word"><span data-reveal-word>教学</span></span>
          <span class="section__word"><span data-reveal-word>都被</span></span>
          <span class="section__word"><span data-reveal-word>看见</span></span>
        </h2>
        <p class="stage__cta-desc" data-reveal-line>加入 TrueBuddy，开启智能教学协作的下一程。</p>
        <div class="stage__cta" data-reveal-item>
          <RouterLink to="/login" class="btn btn--primary btn--lg">立即登录</RouterLink>
          <RouterLink to="/register" class="btn btn--ghost btn--lg">注册账号</RouterLink>
        </div>
        <p class="stage__foot" data-reveal-item><span>TrueBuddy © 2026</span><span>让教学回归创造</span></p>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* ==========================================================================
  Harbor · 靛蓝 · 数据密集（皮肤层）
  本页只消费 src/assets/theme.css 的 --tb-* 语义变量，明暗两态由 light-dark() 派生。

  背景版式角色：全页统一为「纸面网格」（paper-grid，Token 见 theme.css）。
  原来「基底 / 交替 / 强调」三套区块背景已全部取消——所有章节背景透明，
  共用 .page 上同一张纸面；区块之间靠垂直节奏与内容分层，不再靠换底色。
  ========================================================================== */
.page {
  /* 面片取值（卡片 / 芯片共用），随明暗态自动换算 */
  --hv-alt-surface: light-dark(#fdfdfb, #1f232d);
  --hv-block-surface: var(--tb-bg-surface);
  color: var(--tb-text-primary);
  /* 铺在 .page 而不是某个内容容器上：fixed 顶栏所在的那条顶部区域也落在同一张纸面上，
     否则会出现「顶栏一条纯色 + 内容一段纸面」的色差断层 */
  background-color: var(--tb-paper-bg);
  background-image:
    linear-gradient(90deg, var(--tb-paper-line) 1px, transparent 1px),
    linear-gradient(var(--tb-paper-line) 1px, transparent 1px);
  background-size: var(--tb-paper-size) var(--tb-paper-size);
}

/* 横向裁切交给内容区 main，而不是 .page：
   .page 一旦 overflow 非 visible 就成为滚动容器，其内部的 position: sticky 顶栏
   会以 .page 的滚动区为基准，页面滚动时无法吸顶；main 不是顶栏的祖先，
   既裁掉装饰溢出，又不影响顶栏吸顶。 */
main {
  overflow-x: hidden;
}

/* ===== 通用按钮 ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.75rem;
  /* Harbor 无胶囊形态：999px 收敛为主题圆角 */
  border-radius: var(--tb-radius-full);
  font-size: 0.95rem;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid transparent;
  transition:
    background var(--tb-motion-dur) var(--tb-motion-ease),
    border-color var(--tb-motion-dur) var(--tb-motion-ease),
    color var(--tb-motion-dur) var(--tb-motion-ease),
    box-shadow var(--tb-motion-dur) var(--tb-motion-ease),
    transform var(--tb-dur-2) var(--tb-ease-out);
}
.btn--primary {
  background: var(--tb-primary);
  color: var(--tb-on-primary);
}
.btn--primary:hover {
  background: var(--tb-primary-hover);
  box-shadow: var(--tb-shadow-brand);
  transform: translateY(-2px);
}
.btn--ghost {
  border-color: var(--tb-border-strong);
  color: var(--tb-text-primary);
}
.btn--ghost:hover {
  border-color: var(--tb-primary);
  background: var(--tb-bg-raised);
}
.btn--lg {
  padding: var(--tb-size-4) 2.25rem;
  font-size: 1rem;
}

/* ===== 通用 section ===== */
.section {
  position: relative;
  min-height: 100vh;
  /* 上下留白随视口高度收缩：横屏手机（390 高）不再是 256px 的空白吃掉大半屏 */
  padding: clamp(48px, 12vh, 128px) var(--tb-size-6);
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 1200px;
  margin: 0 auto;
  overflow: hidden;
}
/* 深浅两态合一：区块背景一律透明，背景只由 .page 的纸面提供（不再交替换底色） */
.section--dark,
.section--light {
  background: transparent;
  color: var(--tb-text-primary);
  max-width: none;
}
.section__head {
  margin-bottom: var(--tb-size-8);
  max-width: 1200px;
  margin-left: auto;
  margin-right: auto;
  width: 100%;
}
.section__head--center {
  text-align: center;
}
.section__index {
  display: inline-block;
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--tb-primary-text);
  margin-bottom: 1.25rem;
}
.section--light .section__index {
  color: var(--tb-primary-text);
}
.section__title {
  font-family: var(--tb-font-display);
  /* 在字号阶梯的 3xl 与 display 之间取值：不引入阶梯外的魔法数值 */
  font-size: clamp(var(--tb-text-3xl), 4vw, var(--tb-text-display));
  font-weight: var(--tb-weight-display);
  line-height: var(--tb-leading-tight);
  letter-spacing: var(--tb-tracking-display);
}
.section__title--center {
  justify-content: center;
}
.section__word {
  display: inline-block;
  overflow: hidden;
  vertical-align: top;
}
.section__word > span {
  display: inline-block;
}
.section__lead {
  margin-top: var(--tb-size-5);
  font-size: 1.1rem;
  line-height: var(--tb-leading-normal);
  max-width: 640px;
  color: var(--tb-text-secondary);
}
.section__head--center .section__lead {
  margin-left: auto;
  margin-right: auto;
}

/* ===== 01 Hero ===== */
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  padding: 0 var(--tb-size-6);
  overflow: hidden;
  background: transparent;
}
/* 封面两栏：左文案 / 右粒子画布，1.15 : 0.85 分配同一条内容列
   （左栏给足宽度，保证「让教学回归创造」四个词仍在一行内逐词揭示） */
.hero__inner {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  align-items: center;
  gap: clamp(2rem, 5vw, 4.5rem);
}
.hero__copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
}
/* 粒子容器：正方形 —— 采样按画布等比归一化，非方形会把形状压扁 */
.hero__visual {
  position: relative;
  width: 100%;
  max-width: 560px;
  aspect-ratio: 1 / 1;
  margin-left: auto;
  overflow: hidden;
}
.hero__eyebrow {
  font-size: 0.85rem;
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-primary-text);
  margin-bottom: 1.75rem;
}
.hero__title {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-display);
  font-weight: var(--tb-weight-display);
  line-height: var(--tb-leading-tight);
  letter-spacing: var(--tb-tracking-display);
  margin-bottom: var(--tb-size-6);
}
.hero__word {
  display: inline-block;
  overflow: hidden;
  margin-right: 0.18em;
  vertical-align: top;
}
.hero__word > span {
  display: inline-block;
}
/* 强调词渐变：渐变与 GSAP 的 transform 必须落在同一元素上，
   否则父级 background-clip: text 的裁剪区与受 transform 的字形错位，整词不可见 */
.hero__word--accent > span {
  background: linear-gradient(135deg, var(--tb-brand-400), var(--tb-primary));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero__desc {
  font-size: clamp(1.05rem, 1.6vw, 1.35rem);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
  max-width: 620px;
}
.hero__desc--2 {
  margin-top: 0.35rem;
}
.hero__cta {
  margin-top: 2.75rem;
  display: flex;
  gap: var(--tb-size-4);
  flex-wrap: wrap;
}

@keyframes scrollLine {
  0% { transform: translateY(-100%); }
  50% { transform: translateY(0); }
  100% { transform: translateY(100%); }
}

/* ===== 02 Vision ===== */
.vision__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--tb-size-5);
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.vision__card {
  padding: 2.5rem var(--tb-size-6);
  border-radius: var(--tb-radius-lg);
  background: var(--hv-alt-surface);
  border: 1px solid var(--tb-border);
  box-shadow: var(--tb-shadow-1);
  transition:
    transform var(--tb-dur-3) var(--tb-ease-out),
    box-shadow var(--tb-dur-3) var(--tb-ease-out);
}
.vision__card:hover {
  transform: translateY(-6px);
  box-shadow: var(--tb-shadow-3);
}
.vision__num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--tb-primary-text);
  letter-spacing: 0.1em;
}
.vision__card h3 {
  margin: var(--tb-size-4) 0 var(--tb-size-3);
  font-size: 1.4rem;
  font-weight: 700;
}
.vision__card p {
  color: var(--tb-text-secondary);
  line-height: var(--tb-leading-normal);
}

/* ==========================================================================
   03–08 · 舞台区块（版式参考 karot.jiejoe.com）
   满幅面板 + 编号 + 大字号；03 是 sticky 横向轨道，04 是整屏卡堆叠，
   06 是跑马灯。颜色仍全部取自 --tb-* 语义变量。
   ========================================================================== */
.stage,
.stack {
  /* 1360 内容列 + 最小 24px 安全边；窄屏时由媒体查询压到 24px */
  --stage-inset: max(var(--tb-size-5), calc((100% - 1360px) / 2));
}

.stage {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: clamp(28px, 5vh, 56px);
  padding: clamp(56px, 9vh, 112px) var(--stage-inset);
  overflow: hidden;
}

.stage__head {
  max-width: 820px;
}

.stage__idx {
  display: inline-block;
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}

.stage__eyebrow {
  display: flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-4);
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-secondary);
}

/* 三角标记沿用 karot 的小箭头，颜色用强调色 */
.stage__eyebrow::before {
  content: '';
  width: 0;
  height: 0;
  border-left: 7px solid var(--tb-accent);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
}

.stage__title {
  margin-top: var(--tb-size-3);
  font-family: var(--tb-font-display);
  font-size: clamp(2.1rem, 4.4vw, 3.8rem);
  font-weight: var(--tb-weight-display);
  line-height: 1.06;
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

/* 每句标题的第二段用强调色，对应 karot 的「橙色第一行」 */
.stage__title em {
  font-style: normal;
  color: var(--tb-accent);
}

.stage__lead {
  margin-top: var(--tb-size-4);
  max-width: 54ch;
  font-size: var(--tb-text-lg);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* ---------- 03 横向轨道 ---------- */
.stage--rail {
  height: 100vh;
  padding-inline: 0;
  padding-block: clamp(64px, 10vh, 112px) clamp(40px, 7vh, 72px);
  justify-content: center;
  gap: clamp(32px, 6vh, 64px);
}

.rail__head {
  flex: 0 0 auto;
  padding-inline: var(--stage-inset);
}

.rail__hint {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  margin-top: var(--tb-size-5);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* 一条会自己走完的细线，暗示这一节是横向的 */
.rail__hint span {
  position: relative;
  width: 72px;
  height: 1px;
  background: var(--tb-border-strong);
  overflow: hidden;
}

.rail__hint span::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--tb-accent);
  transform-origin: left center;
  animation: railHint 2.6s var(--tb-ease-inout) infinite;
}

@keyframes railHint {
  0% {
    transform: scaleX(0);
  }
  55% {
    transform: scaleX(1);
  }
  100% {
    transform: scaleX(1);
    opacity: 0;
  }
}

.rail {
  position: relative;
  flex: 0 0 auto;
  height: clamp(280px, 40vh, 400px);
  display: flex;
  overflow: hidden;
}

.rail__track {
  display: flex;
  align-items: stretch;
  height: 100%;
  padding-inline: var(--stage-inset);
  will-change: transform;
}

.rail__card {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  width: clamp(248px, 24vw, 360px);
  padding: clamp(20px, 3vh, 32px);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  background: var(--hv-alt-surface);
  box-shadow: var(--tb-shadow-1);
}

/* ---------- 03 文档读取窗口：独占一屏 ---------- */
.docgen {
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  background: var(--tb-bg-surface);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-xl);
  box-shadow: var(--tb-shadow-4);
  overflow: hidden;
}

/* 纸面顶部：课时 + 标题 + 分隔线，与书页排版一致 */
.docgen__kicker {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
  text-align: center;
}

.docgen__rule {
  width: 64px;
  height: 1px;
  margin: var(--tb-size-4) auto var(--tb-size-6);
  background: var(--tb-primary-border);
}

/* 窗口主体：左纸面 + 右「生成依据」，与参考站点的三栏比例一致（1008 / 350 / 684） */
.docgen__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 350px;
}

.docgen__side {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-5);
  padding: var(--tb-size-5);
}

/* 模板文件 */
.docgen__source {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
}

.docgen__thumb {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--tb-size-1);
  width: var(--tb-size-7);
  height: var(--tb-size-7);
  padding: var(--tb-size-1) var(--tb-size-2);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-paper-bg);
}

.docgen__thumb i {
  display: block;
  height: 2px;
  border-radius: 1px;
  background: var(--tb-border-strong);
}

.docgen__thumb i:nth-child(2) {
  width: 72%;
}

.docgen__thumb i:nth-child(3) {
  width: 88%;
}

.docgen__thumb i:nth-child(4) {
  width: 56%;
}

.docgen__source-name {
  font-size: var(--tb-text-sm);
  font-weight: 600;
  line-height: var(--tb-leading-snug);
  color: var(--tb-text-primary);
}

.docgen__source-meta {
  margin-top: var(--tb-size-1);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* 本次需求：输入侧的依据，一条细竖线示意「引用」 */
.docgen__brief {
  padding: var(--tb-size-3);
  border-left: 2px solid var(--tb-primary-border);
  border-radius: 0 var(--tb-radius-sm) var(--tb-radius-sm) 0;
  background: var(--tb-bg-raised);
}

.docgen__brief-label {
  margin-bottom: var(--tb-size-1);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.docgen__brief-text {
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* 字段表 */
.docgen__fields {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
}

.docgen__field {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding: var(--tb-size-1) 0;
  cursor: default;
}

.docgen__field dt {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
  white-space: nowrap;
  transition: color var(--tb-dur-2) var(--tb-ease-out);
}

.docgen__field dd {
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
  text-align: right;
}

/* 识别点：静止只留一丝痕迹，读线扫到对应句才点亮（位移与透明度交给 GSAP） */
.docgen__field-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tb-primary);
  opacity: 0.25;
}

/* 悬停联动只在能悬停的设备上生效；触摸端由脚本点按定位 */
@media (hover: hover) {
  .docgen__field:hover dt {
    color: var(--tb-text-secondary);
  }
}

/* 引用来源 */
.docgen__refs {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-2);
  padding-top: var(--tb-size-3);
  border-top: 1px solid var(--tb-border);
}

.docgen__refs-title {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.docgen__refs-list {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-1);
  list-style: none;
}

.docgen__ref {
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-2);
  padding: var(--tb-size-1) 0;
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-snug);
  color: var(--tb-text-secondary);
  transition: color var(--tb-dur-2) var(--tb-ease-out);
}

.docgen__ref-num {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
  transition: color var(--tb-dur-2) var(--tb-ease-out);
}

@media (hover: hover) {
  .docgen__ref:hover {
    color: var(--tb-text-primary);
  }
  .docgen__ref:hover .docgen__ref-num {
    color: var(--tb-primary-text);
  }
}

/* 页脚：校验结论 + 颜色图例 */
.docgen__foot {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
  margin-top: auto;
  padding-top: var(--tb-size-4);
  border-top: 1px solid var(--tb-border);
}

.docgen__check {
  display: flex;
  align-items: center;
  gap: var(--tb-size-1);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-success);
}

.docgen__check svg {
  flex: 0 0 auto;
  width: 1em;
  height: 1em;
}

.docgen__legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tb-size-2) var(--tb-size-3);
  font-size: var(--tb-text-2xs);
  color: var(--tb-text-tertiary);
}

.docgen__legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-1);
}

.docgen__legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.docgen__legend-dot--cite {
  background: var(--tb-primary-text);
}

.docgen__legend-dot--ai {
  background: var(--tb-warning);
}

/* 读取控制：只换描边与底色，不做位移 */
.docgen__toggle {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--tb-size-1);
  height: var(--tb-control-sm);
  padding: 0 var(--tb-size-3);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: transparent;
  color: var(--tb-text-secondary);
  font-family: inherit;
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  white-space: nowrap;
  cursor: pointer;
  transition:
    color var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out),
    background var(--tb-dur-2) var(--tb-ease-out);
}

.docgen__toggle:hover {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.docgen__toggle svg {
  width: 1em;
  height: 1em;
}

/* 悬停右栏结论时，纸面上对应的句子浮一层同色淡底 */
.docgen__mark.is-linked {
  border-radius: var(--tb-radius-xs);
  background: color-mix(in oklab, currentColor 10%, transparent);
}

.docgen__bar {
  display: flex;
  align-items: center;
  gap: var(--tb-size-3);
  padding: var(--tb-size-2) var(--tb-size-4);
  border-bottom: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.docgen__dots {
  display: inline-flex;
  gap: 6px;
}

.docgen__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tb-border-strong);
}

.docgen__crumb {
  flex: 1 1 auto;
  min-width: 0;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.docgen__status {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--tb-size-2);
  padding: var(--tb-size-1) var(--tb-size-3);
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary-soft);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-primary-text);
  white-space: nowrap;
  transition:
    background var(--tb-dur-2) var(--tb-ease-out),
    color var(--tb-dur-2) var(--tb-ease-out);
}

.docgen__status.is-paused {
  background: var(--tb-bg-raised);
  color: var(--tb-text-secondary);
  box-shadow: inset 0 0 0 1px var(--tb-border);
}

.docgen__status.is-done {
  background: var(--tb-success-soft);
  color: var(--tb-success);
}

.docgen__status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.docgen__status.is-scanning i {
  animation: docgenPulse 1.2s var(--tb-ease-inout) infinite;
}

@keyframes docgenPulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.72);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.docgen__page {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  padding: clamp(32px, 5vh, 56px) clamp(24px, 5vw, 64px);
  background: var(--tb-paper-bg);
  border-right: 1px solid var(--tb-border);
}

/* 复位与重播时关掉过渡，否则会看到一次「倒放」 */
.docgen__prose.is-resetting .docgen__mark,
.docgen__prose.is-resetting :deep(.docgen__w) {
  transition: none;
}

.docgen__title {
  margin-top: var(--tb-size-3);
  font-family: var(--tb-font-display);
  font-size: clamp(1.7rem, 2.6vw, 2.4rem);
  text-align: center;
  letter-spacing: 0.06em;
  color: var(--tb-text-primary);
}

/* 正文列固定 684px 居中：窗口再宽也是书页式的阅读宽度 */
.docgen__prose {
  position: relative;
  max-width: 684px;
  margin: var(--tb-size-4) auto 0;
}

.docgen__para {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-lg);
  line-height: var(--tb-leading-loose);
  color: var(--tb-text-secondary);
}

.docgen__para + .docgen__para {
  margin-top: var(--tb-size-3);
}

.docgen__mark {
  background: none;
  color: var(--tb-primary-text);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 0.28em;
  text-decoration-color: currentColor;
  transition: text-decoration-color 420ms var(--tb-ease-out);
}

.docgen__mark--ai {
  color: var(--tb-warning);
}

.docgen__prose .docgen__mark:not(.is-drawn) {
  text-decoration-color: transparent;
}

/* 逐字片段由脚本生成、不带 scoped 标记，故用 :deep */
.docgen__prose :deep(.docgen__w) {
  color: inherit;
  transition: opacity 300ms var(--tb-ease-out);
}

.docgen__prose :deep(.docgen__w.is-unread) {
  opacity: 0.3;
}

.docgen__beam {
  position: absolute;
  z-index: 2;
  top: -2px;
  left: -8px;
  right: -8px;
  height: 2px;
  background: color-mix(in oklab, var(--tb-accent) 84%, transparent);
  box-shadow: 0 0 16px 4px color-mix(in oklab, var(--tb-accent) 22%, transparent);
  opacity: 0;
  pointer-events: none;
}

.rail__num {
  font-family: var(--font-mono);
  font-size: var(--tb-text-sm);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-accent);
}

.rail__title {
  margin-top: var(--tb-size-4);
  font-size: clamp(1.1rem, 1.5vw, 1.4rem);
  font-weight: 700;
  color: var(--tb-text-primary);
}

.rail__text {
  margin-top: var(--tb-size-2);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* 卡片中段的示意线条：一小段「文档」，把上下两块内容连起来 */
.rail__art {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: auto 0;
  padding-block: var(--tb-size-4);
}

.rail__art i {
  display: block;
  height: 6px;
  border-radius: 3px;
  background: var(--tb-bg-sunken);
}

.rail__art i:nth-child(1) {
  width: 72%;
}

.rail__art i:nth-child(2) {
  width: 92%;
  background: var(--tb-primary-soft);
}

.rail__art i:nth-child(3) {
  width: 54%;
}

.rail__meta {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* 卡片之间的虚线连接 + 一枚菱形节点 */
.rail__link {
  position: relative;
  flex: 0 0 clamp(40px, 6vw, 88px);
  align-self: center;
  height: 1px;
  background: repeating-linear-gradient(
    90deg,
    var(--tb-border-strong) 0 6px,
    transparent 6px 12px
  );
}

.rail__link::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 7px;
  height: 7px;
  margin: -3.5px 0 0 -3.5px;
  background: var(--tb-accent);
  transform: rotate(45deg);
}

/* 尾部留白：让最后一张卡也能推到画面中间 */
.rail__link--end {
  flex-basis: clamp(120px, 34vw, 560px);
}

.rail__link--end::after {
  display: none;
}

.rail__progress {
  position: relative;
  height: 1px;
  margin-inline: var(--stage-inset);
  background: var(--tb-border);
  overflow: hidden;
}

.rail__progress span {
  position: absolute;
  inset: 0;
  background: var(--tb-accent);
  transform: scaleX(0);
  transform-origin: left center;
}

/* ---------- 04 整屏卡堆叠 ---------- */
.stack {
  position: relative;
}

.stack__card {
  position: sticky;
  top: 0;
  min-height: 100vh;
  display: flex;
  align-items: center;
  padding: clamp(56px, 9vh, 112px) var(--stage-inset);
  border-top: 1px solid var(--tb-border);
  background: var(--tb-paper-bg);
}

.stack__card:nth-child(2n) {
  background: var(--hv-alt-surface);
}

.stack__inner {
  position: relative;
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  padding-right: min(40%, 420px);
}

.stack__idx {
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}

.stack__title {
  margin-top: var(--tb-size-4);
  font-family: var(--tb-font-display);
  font-size: clamp(3.2rem, 10vw, 8.5rem);
  font-weight: var(--tb-weight-display);
  line-height: 1;
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

.stack__text {
  margin-top: var(--tb-size-4);
  max-width: 38ch;
  font-size: var(--tb-text-lg);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* 右侧的模板样张：标题条 + 正文条 + 一张表，示意一页真实文档 */
.stack__spec {
  position: absolute;
  right: 0;
  top: 50%;
  width: min(38%, 400px);
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-4);
  padding: var(--tb-size-6);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-2);
}

.stack__spec span {
  display: block;
  width: var(--w, 100%);
  height: 10px;
  border-radius: var(--tb-radius-xs);
  background: var(--tb-bg-sunken);
}

.stack__spec .stack__spec-title {
  width: 52%;
  height: 18px;
  background: var(--tb-primary-soft);
}

/* 样张下半部分用重复渐变画一张表，比再多几个 div 省事 */
.stack__spec::after {
  content: '';
  height: 132px;
  border-top: 1px solid var(--tb-border-strong);
  border-bottom: 1px solid var(--tb-border-strong);
  background: repeating-linear-gradient(
    to bottom,
    transparent 0 32px,
    var(--tb-border) 32px 33px
  );
}

/* ---------- 05 左右分栏 + 大数字 ---------- */
.stage--split {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  gap: clamp(32px, 6vw, 96px);
}

.stage__col {
  display: flex;
  flex-direction: column;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--tb-size-5);
  margin-top: clamp(24px, 5vh, 48px);
}

.stat dd {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin: 0;
  font-family: var(--tb-font-display);
  font-size: clamp(2rem, 3.4vw, 3.2rem);
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--tb-text-primary);
}

.stat dd i {
  font-style: normal;
  font-size: 0.46em;
  color: var(--tb-accent);
}

.stat dt {
  margin-top: var(--tb-size-2);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.stage__visual {
  display: flex;
  justify-content: center;
}

.paper-art {
  width: min(100%, 320px);
  height: auto;
  color: var(--tb-text-tertiary);
}

.paper-art__sheet {
  fill: var(--hv-alt-surface);
  stroke: var(--tb-border-strong);
  stroke-width: 1.5;
}

.paper-art__rule {
  stroke: var(--tb-border);
  stroke-width: 2;
  stroke-linecap: round;
}

.paper-art__check {
  stroke: var(--tb-accent);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.paper-art__mark {
  fill: none;
  stroke: var(--tb-warning);
  stroke-width: 2;
}

.paper-art__mark-text {
  fill: var(--tb-warning);
  font-family: var(--font-mono);
  font-size: 18px;
  text-anchor: middle;
}

/* ---------- 06 跑马灯 ---------- */
.mq {
  position: relative;
  margin-inline: calc(-1 * var(--stage-inset));
  padding-block: var(--tb-size-4);
  border-block: 1px solid var(--tb-border);
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 6%, #000 94%, transparent);
}

.mq__row {
  display: flex;
  width: max-content;
  animation: mqScroll 38s linear infinite;
}

/* 每块右移一个间距：整行平移 50% 正好与第二份首尾相接，不会跳 */
.mq__row span {
  margin-right: var(--tb-size-3);
  padding: var(--tb-size-2) var(--tb-size-4);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-full);
  font-size: var(--tb-text-sm);
  color: var(--tb-text-secondary);
  white-space: nowrap;
}

@keyframes mqScroll {
  to {
    transform: translateX(-50%);
  }
}

.stage__items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--tb-size-6);
  list-style: none;
}

.stage__items-num {
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-accent);
}

.stage__items h3 {
  margin-top: var(--tb-size-3);
  font-size: var(--tb-text-xl);
  font-weight: 700;
}

.stage__items p {
  margin-top: var(--tb-size-2);
  font-size: var(--tb-text-sm);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* ---------- 07 柱状图 ---------- */
.chart {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 200px;
  align-items: end;
  gap: var(--tb-size-6);
  padding-bottom: var(--tb-size-4);
  border-bottom: 1px solid var(--tb-border-strong);
}

.chart__bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: clamp(12px, 2.4vw, 40px);
  height: clamp(220px, 38vh, 360px);
}

.chart__bars span {
  flex: 0 1 84px;
  max-width: 84px;
  height: var(--h);
  border-radius: var(--tb-radius-xs) var(--tb-radius-xs) 0 0;
  background: var(--tb-border-strong);
  transform-origin: bottom center;
}

/* 最高的一根用强调色，作为读数锚点 */
.chart__bars span:nth-child(6) {
  background: var(--tb-accent);
}

.chart__legend p {
  font-size: 1.05rem;
  font-weight: 600;
}

.chart__legend small {
  display: block;
  margin-top: var(--tb-size-1);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.stage__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tb-size-3);
  margin-top: var(--tb-size-2);
}

.stage__tags span {
  padding: var(--tb-size-3) var(--tb-size-5);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-full);
  font-size: var(--tb-text-md);
  color: var(--tb-text-secondary);
  transition: border-color var(--tb-dur-2) var(--tb-ease-out);
}

@media (hover: hover) {
  .stage__tags span:hover {
    border-color: var(--tb-accent);
    color: var(--tb-text-primary);
  }
}

/* ---------- 08 收尾 ---------- */
.stage--cta {
  align-items: center;
  text-align: center;
  gap: var(--tb-size-5);
}

.stage__title--xl {
  font-size: clamp(2.4rem, 6.4vw, 5.4rem);
  line-height: 1;
}

.stage__cta-desc {
  max-width: 46ch;
  font-size: var(--tb-text-lg);
  color: var(--tb-text-secondary);
}

.stage__cta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--tb-size-4);
}

.stage__foot {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--tb-size-5);
  margin-top: var(--tb-size-5);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* ===== 响应式 ===== */
/* 窄屏：封面改单栏，粒子画布居中并收窄 */
@media (max-width: 980px) {
  .hero__inner {
    grid-template-columns: 1fr;
    gap: 2.5rem;
  }
  .hero__visual {
    max-width: min(84vw, 420px);
    margin-inline: auto;
  }
}

@media (max-width: 1024px) {
  .stage,
  .stack {
    --stage-inset: var(--tb-size-5);
  }
  /* 文档窗口：右栏收窄，补充段落让位，窗口不至于拉成两三屏 */
  .docgen__body {
    grid-template-columns: minmax(0, 1fr) 280px;
  }
  .docgen__para--wide {
    display: none;
  }
  .stage--split {
    grid-template-columns: 1fr;
  }
  .stage__visual {
    display: none;
  }
  .chart {
    grid-template-columns: 1fr;
    gap: var(--tb-size-4);
  }
  .stage__items {
    grid-template-columns: 1fr;
    gap: var(--tb-size-4);
  }
}

@media (max-width: 768px) {
  .stage,
  .stack {
    --stage-inset: var(--tb-size-5);
  }
  .stage {
    padding-block: clamp(56px, 10vh, 96px);
  }
  /* 轨道不再 pin，退回原生横滑 + 吸附 */
  .stage--rail {
    height: auto;
    min-height: 0;
    justify-content: flex-start;
    padding-block: clamp(56px, 10vh, 96px);
  }
  .rail {
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: var(--tb-size-3);
    scroll-snap-type: x mandatory;
    scrollbar-width: none;
    -webkit-overflow-scrolling: touch;
  }
  .rail__track {
    height: auto;
    padding-inline: var(--stage-inset);
  }
  .rail__card {
    width: 78vw;
    min-height: 240px;
    scroll-snap-align: center;
  }
  /* 窄屏：窗口单列，右栏落到纸面下方，读取控制收成图标按钮 */
  .docgen {
    border-radius: var(--tb-radius-lg);
  }
  .docgen__counts {
    display: none;
  }
  .docgen__body {
    grid-template-columns: 1fr;
  }
  .docgen__page {
    border-right: none;
    border-bottom: 1px solid var(--tb-border);
  }
  .docgen__prose {
    max-width: none;
  }
  .docgen__toggle {
    width: var(--tb-control-md);
    height: var(--tb-control-md);
    justify-content: center;
    padding: 0;
  }
  .docgen__toggle-text {
    display: none;
  }
  .rail__link {
    flex-basis: var(--tb-size-6);
  }
  .rail__link--end {
    flex-basis: var(--tb-size-5);
  }
  .rail__progress {
    display: none;
  }
  .stack__inner {
    padding-right: 0;
  }
  .stack__spec {
    position: static;
    width: 100%;
    margin-top: var(--tb-size-5);
    transform: none;
  }
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--tb-size-4);
  }
  .stage__title {
    font-size: clamp(1.9rem, 7.2vw, 2.6rem);
  }
  .chart__bars {
    height: clamp(160px, 28vh, 220px);
  }
}

/* 小屏手机与横屏矮视口：评价段让位，窗口不超过一屏半 */
@media (max-width: 480px), (max-height: 560px) {
  .docgen__para--short {
    display: none;
  }
}

/* 矮视口：横向轨道的说明文字让位，保证 100vh 内放得下 */
@media (max-height: 720px) {
  .rail__head .stage__lead,
  .rail__hint {
    display: none;
  }
}
/* ===== 无障碍：尊重「减少动效」偏好 =====
   常驻装饰动画静止（内容仍可见），交互过渡取消；
   滚动编排动效由 GSAP 承担，见 composables/useSmoothScroll.js 与 09-样式规范.md §6.2 */
@media (prefers-reduced-motion: reduce) {
  .rail__hint span::after,
  .mq__row,
  .docgen__status.is-scanning i {
    animation: none;
  }
  .btn,
  .vision__card,
  .docgen__mark { transition: none; }
  .docgen__prose :deep(.docgen__w) { transition: none; }
}
</style>
