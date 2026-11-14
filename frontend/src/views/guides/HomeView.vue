<script setup>
import { onMounted, onBeforeUnmount, nextTick, ref, computed } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { CircleCheck, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import { useSmoothScroll } from '../../composables/useSmoothScroll'
import NavbarItem from '../../components/NavbarItem.vue'
import HeroParticles from './components/HeroParticles.vue'
import visionPlate from '../../assets/vision-proof.jpg'
import railStep1 from '../../assets/rail-step-1.jpg'
import railStep2 from '../../assets/rail-step-2.jpg'
import railStep3 from '../../assets/rail-step-3.jpg'
import railStep4 from '../../assets/rail-step-4.jpg'
import railStep5 from '../../assets/rail-step-5.jpg'
import featPlan from '../../assets/feat-plan.jpg'
import featGrading from '../../assets/feat-grading.jpg'
import featAvatar from '../../assets/feat-avatar.jpg'

gsap.registerPlugin(ScrollTrigger)

const { refresh, scrollTo } = useSmoothScroll()

const sections = [
  { id: 'hero', label: '首页', en: 'HOME' },
  { id: 'shift', label: '转折', en: 'SHIFT' },
  { id: 'vision', label: '核心理念', en: 'VISION' },
  { id: 'ai', label: 'AI 助手', en: 'ASSISTANT' },
  { id: 'rail', label: 'AI 工作流', en: 'WORKFLOW' },
  { id: 'docs', label: '核心功能', en: 'FEATURES' },
  { id: 'grading', label: '作业批改', en: 'GRADING' },
  { id: 'knowledge', label: '知识库', en: 'KNOWLEDGE' },
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
  setupShift()
  setupStrip()
  setupMagnet()
  refresh()
})

/** 章节入场动画 + 装饰层视差 */
function setupAnimations() {
  const stages = Array.from(document.querySelectorAll('[data-section]'))

  // ① 每个章节一条与滚动进度绑定的时间线（整页连续滚动，不做整屏吸附与钉住）：
  //    区间 = 该章节「进入视口底部 → 完全离开视口顶部」，中线 = 章节居中于视口的时刻；
  //    前半段随滚动进场，后半段随滚动退场，
  //    同一章节内各组元素的起手时序与退出幅度各不相同，这就是「元素之间的配合」。
  stages.forEach((sec, si) => {
    const pick = (sel) => Array.from(sec.querySelectorAll(sel))

    const words = pick('[data-reveal-word]')
    const lines = pick('[data-reveal-line]')
    const items = pick('[data-reveal-item]')
    const cards = pick('[data-card]')
    const zooms = pick('[data-zoom]')
    const softFades = pick('[data-fade-in]')
    const fades = pick('[data-fade-out]')
    const counters = pick('[data-count]')
    const parallax = pick('[data-parallax]')

    // 终幕（最后一个章节）专属：只进场、永不退场。
    // 通用时间线的退场段在「滚到底」之前就会播完——终幕恰好是最后一屏，
    // 滚动到底 = 时间线走满，若沿用退场，标题在读到之前就已淡出消失。
    if (si === stages.length - 1) {
      const tl = gsap.timeline({
        defaults: { ease: 'power2.out' },
        scrollTrigger: {
          trigger: sec,
          start: 'top bottom',
          end: 'bottom bottom',
          scrub: 1,
        },
      })
      if (words.length) {
        tl.fromTo(
          words,
          { yPercent: 120, opacity: 0 },
          { yPercent: 0, opacity: 1, duration: 0.35, stagger: 0.06 },
          0,
        )
      }
      if (lines.length) {
        tl.fromTo(
          lines,
          { y: 30, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.25, stagger: 0.08 },
          0.08,
        )
      }
      if (items.length) {
        tl.fromTo(
          items,
          { y: 44, opacity: 0 },
          { y: 0, opacity: 1, duration: 0.25, stagger: 0.08 },
          0.18,
        )
      }
      if (softFades.length) {
        tl.fromTo(softFades, { opacity: 0 }, { opacity: 1, duration: 0.2 }, 0.32)
      }
      track(tl.scrollTrigger)
      return
    }

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

    // 自身带 CSS 自转的元素（知识库圆环）：只淡入，避免覆盖其 animation 的 transform
    if (softFades.length) {
      tl.fromTo(
        softFades,
        { opacity: 0 },
        { opacity: 1, duration: 0.9, stagger: 0.14, ease: 'power2.out' },
        0.25,
      )
    }

    // 数据：数字随滚动实时滚动到目标值；data-decimals 声明小数位（默认整数）
    counters.forEach((el, i) => {
      const state = { value: 0 }
      const decimals = Number(el.dataset.decimals) || 0
      tl.to(
        state,
        {
          value: Number(el.dataset.count) || 0,
          duration: 0.9,
          ease: 'power2.out',
          onUpdate: () => {
            el.textContent = state.value.toFixed(decimals)
          },
        },
        0.5 + i * 0.1,
      )
    })

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
    if (softFades.length) {
      tl.to(softFades, { opacity: 0.4, duration: OUT, ease: 'power1.in' }, MID)
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
  const cards = trackEl ? Array.from(trackEl.querySelectorAll('.rail__card')) : []
  const linkFills = trackEl ? Array.from(trackEl.querySelectorAll('[data-rail-link] i')) : []
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
        onUpdate: () => {
          // 工序连线：第 j 段随「下一张卡逼近版心中点」从 0 描到 1
          if (linkFills.length) {
            const center = rail.clientWidth / 2
            const rects = cards.map((c) => c.getBoundingClientRect())
            linkFills.forEach((el, j) => {
              const a = rects[j]
              const b = rects[j + 1]
              if (!a || !b) return
              const pitch = b.left - a.left || 1
              const fill = Math.min(1, Math.max(0, 1 - (b.left + b.width / 2 - center) / pitch))
              el.style.transform = `scaleX(${fill.toFixed(4)})`
            })
          }
        },
      },
    })
    track(tween.scrollTrigger)

    // 横向 choreography：卡片随行进位置自下而上落位
    // （containerAnimation 挂在横移补间上，随 pin 一起由 matchMedia 统一回收）
    trackEl.querySelectorAll('.rail__card').forEach((card) => {
      gsap.fromTo(
        card,
        { scale: 0.94, y: 16, opacity: 0.7 },
        {
          scale: 1,
          y: 0,
          opacity: 1,
          ease: 'none',
          scrollTrigger: {
            trigger: card,
            containerAnimation: tween,
            start: 'left 92%',
            end: 'left 55%',
            scrub: true,
          },
        },
      )
    })
  })
}

/* ==========================================================================
   02 批注幕 · 一笔红批（sticky 钉住 260vh，滚动进度驱动整段编排）
   划掉「重复」→ 圈出「创造」→ 逐条核销一张案头账单。
   CSS 默认即终稿：跳过 / reduce-motion / 无 JS 时直接呈现完整构图。
   ========================================================================== */
let shiftTl = null

function setupShift() {
  const sec = document.getElementById('shift')
  if (!sec || prefersReduced()) return

  const q = (sel) => Array.from(sec.querySelectorAll(sel))
  const head = sec.querySelector('.shift__head')
  const kicker = sec.querySelector('.shift__kicker')
  const lead = sec.querySelector('.shift__lead')
  const strokes = [...q('.shift__strike path'), ...q('.shift__ring path')]
  const oldWord = sec.querySelector('.shift__word--old')
  const newWord = sec.querySelector('.shift__word--new')
  const arrow = sec.querySelector('.shift__arrow')
  const notes = q('.shift__note')
  const crossLines = q('.receipt__cross')
  const totalEl = sec.querySelector('[data-receipt-total]')
  const mark = sec.querySelector('.receipt__mark')

  // pathLength=1 归一化：dashoffset 1 → 0 即「一笔画完」
  gsap.set(strokes, { strokeDasharray: 1, strokeDashoffset: 1 })
  gsap.set(newWord, { opacity: 0, scale: 0.86, rotate: -3 })
  gsap.set(notes, { opacity: 0, y: 10 })
  gsap.set(crossLines, { scaleX: 0, transformOrigin: 'left center' })
  gsap.set(mark, { opacity: 0, scale: 1.35, rotate: -5 })
  gsap.set([head, kicker, lead], { opacity: 0, y: 26 })
  if (totalEl) totalEl.textContent = '0.0'

  const state = { hours: 0 }
  shiftTl = gsap.timeline({
    defaults: { ease: 'power2.out' },
    scrollTrigger: {
      trigger: sec,
      start: 'top top',
      end: 'bottom bottom',
      scrub: 0.5,
    },
  })
  track(shiftTl.scrollTrigger)

  shiftTl
    .to(head, { opacity: 1, y: 0, duration: 0.07 }, 0)
    .to(kicker, { opacity: 1, y: 0, duration: 0.09 }, 0.03)
    // 第一笔：两道红斜线划过「重复」
    .to(q('.shift__strike path'), { strokeDashoffset: 0, duration: 0.15, stagger: 0.05 }, 0.16)
    // 账单逐条核销
    .to(crossLines, { scaleX: 1, duration: 0.05, stagger: 0.04 }, 0.32)
    // 旧词退位，新词盖章落定
    .to(oldWord, { opacity: 0.16, rotate: -2, scale: 0.97, duration: 0.12 }, 0.46)
    .to(arrow, { x: 12, opacity: 0.45, duration: 0.07 }, 0.5)
    .to(newWord, { opacity: 1, scale: 1, rotate: 0, duration: 0.13, ease: 'power3.out' }, 0.53)
    .to(q('.shift__ring path'), { strokeDashoffset: 0, duration: 0.13 }, 0.58)
    .to(notes, { opacity: 1, y: 0, duration: 0.05, stagger: 0.035 }, 0.62)
    .to(lead, { opacity: 1, y: 0, duration: 0.07 }, 0.66)
    // 合计小时数跳字
    .to(
      state,
      {
        hours: 14,
        duration: 0.12,
        onUpdate: () => {
          if (totalEl) totalEl.textContent = state.hours.toFixed(1)
        },
      },
      0.72,
    )
    .to(mark, { opacity: 1, scale: 1, rotate: -2, duration: 0.07, ease: 'power3.out' }, 0.86)
}

/* ==========================================================================
   工序带 · 巨字转场（叙事幕 → 产品幕之间的帘幕）
   两行描边巨字随整页滚动反向横移；reduce-motion 下静止。
   ========================================================================== */
function setupStrip() {
  if (prefersReduced()) return
  document.querySelectorAll('[data-strip]').forEach((row) => {
    const dir = Number(row.dataset.strip) || -1
    track(
      gsap.fromTo(
        row,
        { xPercent: dir < 0 ? 2 : -12 },
        {
          xPercent: dir < 0 ? -14 : 2,
          ease: 'none',
          scrollTrigger: {
            trigger: row.closest('.strip'),
            start: 'top bottom',
            end: 'bottom top',
            scrub: 0.8,
          },
        },
      ).scrollTrigger,
    )
  })
}

/* ==========================================================================
   磁吸按钮 · 收尾 CTA 的微交互（仅桌面 + 未开启减少动效；不进入 hero / 03）
   ========================================================================== */
let magnetBinds = []

function setupMagnet() {
  if (!canHover || prefersReduced()) return
  document.querySelectorAll('#start .btn').forEach((btn) => {
    const xTo = gsap.quickTo(btn, 'x', { duration: 0.45, ease: 'power3' })
    const yTo = gsap.quickTo(btn, 'y', { duration: 0.45, ease: 'power3' })
    const onMove = (e) => {
      const r = btn.getBoundingClientRect()
      xTo((e.clientX - r.left - r.width / 2) * 0.24)
      yTo((e.clientY - r.top - r.height / 2) * 0.4)
    }
    const onLeave = () => {
      xTo(0)
      yTo(0)
    }
    btn.addEventListener('mousemove', onMove)
    btn.addEventListener('mouseleave', onLeave)
    magnetBinds.push([btn, onMove, onLeave])
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
  shiftTl?.kill()
  shiftTl = null
  magnetBinds.forEach(([el, onMove, onLeave]) => {
    el.removeEventListener('mousemove', onMove)
    el.removeEventListener('mouseleave', onLeave)
  })
  magnetBinds = []
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

      <!-- 02 批注 B-01 · 一笔红批：整屏钉住，滚动驱动划掉「重复」、圈出「创造」 -->
      <section id="shift" class="shift">
        <div class="shift__pin">
          <header class="shift__head">
            <span class="stage__idx">批注 B-01</span>
            <p class="stage__eyebrow">转折 · 一笔红批</p>
          </header>

          <div class="shift__stage">
            <div class="shift__words">
              <p class="shift__kicker">每周的案头，都堆着可以不做的「重复」。</p>
              <div class="shift__swap">
                <span class="shift__word shift__word--old">
                  重复
                  <svg class="shift__strike" viewBox="0 0 320 120" preserveAspectRatio="none" aria-hidden="true">
                    <path pathLength="1" d="M18 78 C 96 60, 210 52, 302 40" />
                    <path pathLength="1" d="M22 96 C 110 84, 204 72, 296 58" />
                  </svg>
                  <span class="shift__note shift__note--cross" aria-hidden="true">×1 划去</span>
                </span>
                <span class="shift__arrow" aria-hidden="true">→</span>
                <span class="shift__word shift__word--new">
                  创造
                  <svg class="shift__ring" viewBox="0 0 200 90" preserveAspectRatio="none" aria-hidden="true">
                    <path pathLength="1" d="M28 52 C 30 16, 168 8, 186 34 C 200 60, 150 84, 92 84 C 40 84, 22 70, 30 44" />
                  </svg>
                  <span class="shift__note shift__note--circle" aria-hidden="true">○ 圈出</span>
                </span>
              </div>
              <p class="shift__lead">案头的事交给机器，人的时间还给教学本身。</p>
            </div>

            <!-- 案头账单：一张被红笔逐条核销的小票 -->
            <aside class="receipt">
              <header class="receipt__head">
                <span>案头账单 · 每周</span>
                <span>WEEKLY PROOF</span>
              </header>
              <ul class="receipt__list">
                <li data-receipt>
                  <span>备课初稿</span>
                  <i class="receipt__leader" aria-hidden="true"></i>
                  <b>4.5h</b>
                  <i class="receipt__cross" aria-hidden="true"></i>
                </li>
                <li data-receipt>
                  <span>组卷编排</span>
                  <i class="receipt__leader" aria-hidden="true"></i>
                  <b>3.0h</b>
                  <i class="receipt__cross" aria-hidden="true"></i>
                </li>
                <li data-receipt>
                  <span>作业批改</span>
                  <i class="receipt__leader" aria-hidden="true"></i>
                  <b>5.0h</b>
                  <i class="receipt__cross" aria-hidden="true"></i>
                </li>
                <li data-receipt>
                  <span>资料归档</span>
                  <i class="receipt__leader" aria-hidden="true"></i>
                  <b>1.5h</b>
                  <i class="receipt__cross" aria-hidden="true"></i>
                </li>
              </ul>
              <footer class="receipt__total">
                <span>合计</span>
                <b><span data-receipt-total>14.0</span> h</b>
              </footer>
              <p class="receipt__mark">已核销 · 还给教学本身 ↩</p>
            </aside>
          </div>
        </div>
      </section>

      <!-- 02 核心理念 · 一张贴在桌面上的校样 + 三条批注卡 -->
      <section id="vision" data-section data-index="1" class="section section--light">
        <header class="section__head">
          <span data-reveal-item data-drift="72" class="section__index">02 / 09 · PROOF</span>
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
          <!-- 左：纸胶带钉住的校样拼贴 -->
          <figure data-parallax data-speed="-8" class="vision__plate">
            <div class="vision__frame">
              <!-- 内视差层：相框裁切不动，画芯随滚动轻微游走 -->
              <div class="vision__drift" data-parallax data-speed="6">
                <img
                  class="vision__img"
                  :src="visionPlate"
                  width="1024"
                  height="1280"
                  alt="校样拼贴：墨蓝纸片、坐标格与红笔批注"
                  loading="lazy"
                />
              </div>
            </div>
            <figcaption class="vision__cap">
              <span class="vision__cap-no">FIG. 02</span>
              <span>我们的立场，逐条校对</span>
            </figcaption>
          </figure>

          <!-- 右：三条批注卡，逐级右移的阶梯 -->
          <div class="vision__cards">
            <article data-card class="vision__card">
              <svg class="vision__glyph" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M4 13.5 L9.5 19 L20 5" />
              </svg>
              <div class="vision__card-head">
                <span class="vision__num">01</span>
                <span class="vision__tag">KEEP · 保留</span>
              </div>
              <h3>以教师为中心</h3>
              <p>所有工具围绕教师真实工作流构建，而非让教师迁就系统。</p>
            </article>
            <article data-card class="vision__card">
              <svg class="vision__glyph" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 8.5 H17" />
                <path d="M14 5.5 L17.5 8.5 L14 11.5" />
                <path d="M21 15.5 H7" />
                <path d="M10 12.5 L6.5 15.5 L10 18.5" />
              </svg>
              <div class="vision__card-head">
                <span class="vision__num">02</span>
                <span class="vision__tag">CO-EDIT · 协作</span>
              </div>
              <h3>AI 是协作而非替代</h3>
              <p>人工智能负责检索、草拟与校验，决策与温度始终留给教师。</p>
            </article>
            <article data-card class="vision__card">
              <svg class="vision__glyph" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3 V14" />
                <path d="M7.5 10 L12 14.5 L16.5 10" />
                <path d="M4 19.5 H20" />
              </svg>
              <div class="vision__card-head">
                <span class="vision__num">03</span>
                <span class="vision__tag">FILE · 归档</span>
              </div>
              <h3>知识应当沉淀</h3>
              <p>每一份教案、每一次批改，都应沉淀为可复用、可检索的资产。</p>
            </article>
          </div>
        </div>
      </section>

      <!-- 工序带 · 巨字转场：叙事幕与产品幕之间的帘幕，随滚动反向横移 -->
      <section id="strip" class="strip" aria-hidden="true">
        <div class="strip__row" data-strip="-1">
          <span>备课<i>✓</i></span>
          <span>批改<i>✓</i></span>
          <span>组卷<i>✓</i></span>
          <span>讲评<i>✓</i></span>
          <span>沉淀<i>✓</i></span>
          <span>备课<i>✓</i></span>
          <span>批改<i>✓</i></span>
          <span>组卷<i>✓</i></span>
          <span>讲评<i>✓</i></span>
          <span>沉淀<i>✓</i></span>
        </div>
        <div class="strip__row strip__row--b" data-strip="1">
          <span>备课<i>✓</i></span>
          <span>批改<i>✓</i></span>
          <span>组卷<i>✓</i></span>
          <span>讲评<i>✓</i></span>
          <span>沉淀<i>✓</i></span>
          <span>备课<i>✓</i></span>
          <span>批改<i>✓</i></span>
          <span>组卷<i>✓</i></span>
          <span>讲评<i>✓</i></span>
          <span>沉淀<i>✓</i></span>
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
        <span class="stage__run" aria-hidden="true">Nº 04 — WORKFLOW</span>
        <span class="stage__ghost" aria-hidden="true">04</span>
        <span class="rail__scroll" aria-hidden="true">SCROLL — 校样画廊</span>

        <div class="rail" data-rail>
          <div class="rail__track" data-rail-track>
            <!-- 首屏标题板：随轨道一起行进（karot 同构） -->
            <div class="rail__intro">

              <h2 class="rail__intro-title">Features</h2>
             </div>

            <article class="rail__card">
              <div class="rail__plate">
                <header class="rail__card-head">
                  <span class="rail__num">W·01</span>
                  <span class="rail__tick" aria-hidden="true"></span>
                </header>
                <figure class="rail__fig" aria-hidden="true">
                  <img class="rail__img" :src="railStep1" width="1024" height="768" alt="" loading="lazy" />
                </figure>
                <h3 class="rail__title">对话起稿</h3>
                <p class="rail__text">说清课时、学情与目标，AI 直接给出结构完整的初稿，不必从空白页开始。</p>
                <footer class="rail__card-foot">
                  <span class="rail__points" aria-hidden="true"><i class="is-on"></i><i></i><i></i><i></i><i></i></span>
                  <span class="rail__meta">STEP 01 ／ 04</span>
                </footer>
              </div>
            </article>
            <div class="rail__link" data-rail-link aria-hidden="true"><i></i></div>
            <article class="rail__card">
              <div class="rail__plate">
                <header class="rail__card-head">
                  <span class="rail__num">W·02</span>
                  <span class="rail__tick" aria-hidden="true"></span>
                </header>
                <figure class="rail__fig" aria-hidden="true">
                  <img class="rail__img" :src="railStep2" width="1024" height="768" alt="" loading="lazy" />
                </figure>
                <h3 class="rail__title">逐字段填充</h3>
                <p class="rail__text">教学目标、重难点、活动与评价按你的模板落位，段落与表格结构一处不动。</p>
                <footer class="rail__card-foot">
                  <span class="rail__points" aria-hidden="true"><i></i><i class="is-on"></i><i></i><i></i><i></i></span>
                  <span class="rail__meta">STEP 02 ／ 04</span>
                </footer>
              </div>
            </article>
            <div class="rail__link" data-rail-link aria-hidden="true"><i></i></div>
            <article class="rail__card">
              <div class="rail__plate">
                <header class="rail__card-head">
                  <span class="rail__num">W·03</span>
                  <span class="rail__tick" aria-hidden="true"></span>
                </header>
                <figure class="rail__fig" aria-hidden="true">
                  <img class="rail__img" :src="railStep3" width="1024" height="768" alt="" loading="lazy" />
                </figure>
                <h3 class="rail__title">引用可回溯</h3>
                <p class="rail__text">每条结论都挂着教材页码与课标条目，点开即可核对原文，不留黑盒。</p>
                <footer class="rail__card-foot">
                  <span class="rail__points" aria-hidden="true"><i></i><i></i><i class="is-on"></i><i></i><i></i></span>
                  <span class="rail__meta">STEP 03 ／ 04</span>
                </footer>
              </div>
            </article>
            <div class="rail__link" data-rail-link aria-hidden="true"><i></i></div>
            <article class="rail__card">
              <div class="rail__plate">
                <header class="rail__card-head">
                  <span class="rail__num">W·04</span>
                  <span class="rail__tick" aria-hidden="true"></span>
                </header>
                <figure class="rail__fig" aria-hidden="true">
                  <img class="rail__img" :src="railStep4" width="1024" height="768" alt="" loading="lazy" />
                </figure>
                <h3 class="rail__title">结构校验</h3>
                <p class="rail__text">导出前自动体检：占位符、表头、字号与页边距，对着模板逐项校验。</p>
                <footer class="rail__card-foot">
                  <span class="rail__points" aria-hidden="true"><i></i><i></i><i></i><i class="is-on"></i><i></i></span>
                  <span class="rail__meta">STEP 04 ／ 04</span>
                </footer>
              </div>
            </article>
            <div class="rail__link" data-rail-link aria-hidden="true"><i></i></div>
            <article class="rail__card rail__card--end">
              <div class="rail__plate">
                <figure class="rail__fig" aria-hidden="true">
                  <img class="rail__img" :src="railStep5" width="1024" height="768" alt="" loading="lazy" />
                </figure>
                <h3 class="rail__title">成稿交付</h3>
                <p class="rail__text">四步走完，一份可直接提交的 docx 落进你的文档库。</p>
                <footer class="rail__card-foot">
                  <span class="rail__points" aria-hidden="true"><i></i><i></i><i></i><i></i><i class="is-on"></i></span>
                  <RouterLink to="/login" class="rail__end-link">打开工作台 →</RouterLink>
                </footer>
              </div>
            </article>
          </div>
        </div>
      </section>

      <!-- 05 核心功能 · 整屏卡堆叠：上一张留在原地，下一张盖上来 -->
      <section id="docs" data-section data-index="4" class="stack">
        <article class="stack__card">
          <div class="stack__inner">
            <p class="stage__eyebrow">核心功能 · 教案复刻</p>
            <h3 class="stack__title">教案复刻</h3>
            <p class="stack__text">上传教研组的教案模板，说清章节与学情，AI 按原文逐处替换，版式一字不动。</p>
            <ul class="stack__feats">
              <li>结构锚点逐节落位：教学目标、学情、重难点、板书设计各归其位</li>
              <li>板书设计的多行单元格逐行改写，嵌套子表也不放过</li>
              <li>预演确认命中位置；签字与日期等敏感字段严禁编造</li>
            </ul>
            <p class="stack__pipe" aria-hidden="true">解析 ▸ 规划 ▸ 预演 ▸ 替换 ▸ 自检</p>
            <div class="stack__spec" aria-hidden="true">
              <p class="stack__spec-meta">FEAT-01 · DOCX · 模板复刻</p>
              <figure class="stack__spec-fig">
                <img :src="featPlan" width="1024" height="768" alt="" loading="lazy" />
              </figure>
              <div class="stack__stamp">已核对 ✓</div>
            </div>
          </div>
        </article>
        <article class="stack__card">
          <div class="stack__inner">
            <p class="stage__eyebrow">核心功能 · 作业批改</p>
            <h3 class="stack__title">作业批改</h3>
            <p class="stack__text">整包上传作业文件夹，AI 识别学生归属，教师确认评分标准后逐人批改。</p>
            <ul class="stack__feats">
              <li>逐人评分、逐项扣分依据引用原文，评语面向学生本人</li>
              <li>自动汇总成绩单：平均分、最高最低分与不及格名单</li>
              <li>人工确认节点可中断续跑，批改过程全程流式可视化</li>
            </ul>
            <p class="stack__pipe" aria-hidden="true">归组 ▸ 确认 ▸ 逐人批改 ▸ 汇总</p>
            <div class="stack__spec" aria-hidden="true">
              <p class="stack__spec-meta">FEAT-02 · WORKFLOW · SSE 流式</p>
              <figure class="stack__spec-fig">
                <img :src="featGrading" width="1024" height="768" alt="" loading="lazy" />
              </figure>
              <div class="stack__stamp">已核对 ✓</div>
            </div>
          </div>
        </article>
        <article class="stack__card">
          <div class="stack__inner">
            <p class="stage__eyebrow">核心功能 · 数字人课堂</p>
            <h3 class="stack__title">数字人课堂</h3>
            <p class="stack__text">LiveKit 实时课堂：教师屏幕与声音共享，AI 数字人语音播报讲稿，学生免登录入会。</p>
            <ul class="stack__feats">
              <li>屏幕共享含音频轨，学生断线可重连，状态由房间事件驱动</li>
              <li>DataChannel 实时聊天 + 与会者在线列表，消息历史随时回看</li>
              <li>数字人 CosyVoice 语音播报讲稿，可随时中断，发言以数字人身份入档</li>
            </ul>
            <p class="stack__pipe" aria-hidden="true">创建 ▸ 入会 ▸ 共享 ▸ 播报 ▸ 互动</p>
            <div class="stack__spec" aria-hidden="true">
              <p class="stack__spec-meta">FEAT-03 · LIVEKIT · 实时课堂</p>
              <figure class="stack__spec-fig">
                <img :src="featAvatar" width="1024" height="768" alt="" loading="lazy" />
              </figure>
              <div class="stack__stamp">已核对 ✓</div>
            </div>
          </div>
        </article>
      </section>

      <!-- 06 作业智能批改 · 左右分栏 + 大数字 + 批改工作台速写 -->
      <section id="grading" data-section data-index="5" class="stage stage--split">
        <span class="stage__run" aria-hidden="true">Nº 06 — GRADING</span>
        <span class="stage__ghost" data-parallax data-speed="-14" aria-hidden="true">06</span>
        <div class="stage__col">
          <p class="stage__eyebrow">作业批改</p>
          <h2 class="stage__title">先让机器<em>跑一遍</em>。</h2>
          <p class="stage__lead">客观题自动判分，主观题给出批注建议；教师只复核、讲评与个别辅导。</p>
          <dl class="stats">
            <div class="stat">
              <dd><span data-count="99">0</span><i>%</i></dd>
              <dt>客观题判分准确率</dt>
            </div>
            <div class="stat">
              <dd><span data-count="3.8" data-decimals="1">0</span><i>×</i></dd>
              <dt>批改效率提升</dt>
            </div>
            <div class="stat">
              <dd><span data-count="5.4" data-decimals="1">0</span><i>h</i></dd>
              <dt>反馈周期压缩到</dt>
            </div>
          </dl>
          <p data-reveal-item class="stage__margin-note">
            <span><i class="stage__note-mark" aria-hidden="true">✓</i>客观题 · 机器初判</span>
            <span><i class="stage__note-mark" aria-hidden="true">✎</i>主观题 · 教师终审</span>
          </p>
        </div>
        <div class="stage__visual" aria-hidden="true">
          <svg class="paper-art" viewBox="0 0 360 420" fill="none">
            <!-- 底下一张压着的答卷 -->
            <rect x="84" y="44" width="248" height="340" rx="6" class="paper-art__sheet paper-art__sheet--back" transform="rotate(3 208 214)" />
            <!-- 上面正在批改的答卷 -->
            <rect x="48" y="28" width="252" height="344" rx="6" class="paper-art__sheet" />
            <line x1="84" y1="52" x2="84" y2="356" class="paper-art__margin" />
            <line x1="104" y1="72" x2="196" y2="72" class="paper-art__rule" />
            <circle cx="252" cy="86" r="27" class="paper-art__mark" />
            <text x="252" y="94" class="paper-art__mark-text">99</text>
            <line x1="104" y1="116" x2="272" y2="116" class="paper-art__rule" />
            <line x1="104" y1="144" x2="240" y2="144" class="paper-art__rule" />
            <line x1="104" y1="172" x2="272" y2="172" class="paper-art__rule" />
            <path d="M104 210 L122 226 L158 192" class="paper-art__check" />
            <line x1="104" y1="252" x2="272" y2="252" class="paper-art__rule" />
            <path d="M118 288 L142 312" class="paper-art__cross" />
            <path d="M142 288 L118 312" class="paper-art__cross" />
            <line x1="104" y1="336" x2="232" y2="336" class="paper-art__rule" />
            <!-- 搁在纸角的红铅笔 -->
            <g transform="rotate(36 306 352)">
              <rect x="298" y="278" width="16" height="8" rx="2" class="paper-art__pencil-eraser" />
              <rect x="298" y="286" width="16" height="64" rx="2" class="paper-art__pencil" />
              <path d="M298 350 L306 366 L314 350 Z" class="paper-art__pencil-tip" />
            </g>
          </svg>
          <p class="stage__visual-cap">批改工作台 · 初判已就绪，等一支红笔</p>
        </div>
      </section>

      <!-- 07 知识库 · 双向跑马灯带 -->
      <section id="knowledge" data-section data-index="6" class="stage stage--kb">
        <span class="stage__run" aria-hidden="true">Nº 07 — KNOWLEDGE</span>
        <span class="stage__ghost" data-parallax data-speed="-14" aria-hidden="true">07</span>
        <header class="stage__head">
          <p class="stage__eyebrow">教学知识库</p>
          <h2 class="stage__title">每一份素材，<em>都能被找回</em>。</h2>
        </header>
        <span class="kb__axis" aria-hidden="true">双向检索 · BIDIRECTIONAL</span>
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
          <div class="mq__row mq__row--reverse">
            <span>历年期末卷</span>
            <span>微课脚本</span>
            <span>实验视频索引</span>
            <span>板书照片</span>
            <span>错题图谱</span>
            <span>历年期末卷</span>
            <span>微课脚本</span>
            <span>实验视频索引</span>
            <span>板书照片</span>
            <span>错题图谱</span>
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

      <!-- 08 开始使用 · 巨型收尾 · 定稿页 -->
      <section id="start" data-section data-index="8" class="stage stage--cta">
        <div class="stage__crop" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
        <div class="stage__stamp" data-fade-in aria-hidden="true">
          <span>TRUEBUDDY</span>
          <span>已验收 · 2026</span>
        </div>
        <h2 class="stage__title stage__title--xl">
          <span class="section__word"><span data-reveal-word>让</span></span>
          <span class="section__word"><span data-reveal-word>每一次</span></span>
          <span class="section__word"><span data-reveal-word>教学</span></span>
          <span class="section__word"><span data-reveal-word>都被</span></span>
          <span class="final__word">
            <span class="section__word"><span data-reveal-word>看见</span></span>
            <svg class="final__ring" data-fade-in viewBox="0 0 120 64" preserveAspectRatio="none" aria-hidden="true">
              <path d="M14 34 C16 12 96 4 108 18 C118 32 92 56 52 58 C22 60 10 48 16 32" />
            </svg>
          </span>
        </h2>
        <p class="stage__cta-desc" data-reveal-line>加入 TrueBuddy，开启智能教学协作的下一程。</p>
        <div class="stage__cta" data-reveal-item>
          <RouterLink to="/login" class="btn btn--primary btn--lg">立即登录</RouterLink>
          <RouterLink to="/register" class="btn btn--ghost btn--lg">注册账号</RouterLink>
        </div>
        <p class="stage__foot" data-reveal-item>
          <span>TrueBuddy © 2026</span>
          <span>PROOF Nº 09 · FINAL</span>
          <span>让教学回归创造</span>
        </p>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* ==========================================================================
  Proof · 校样美学（皮肤层）
  本页只消费 src/assets/theme.css 的 --tb-* 语义变量，明暗两态由 light-dark() 派生。

  设计主题：整页是一张正在被批注的「校样」——
  红笔对勾 / 叉 / 圈注 / 插入符是贯穿各章的批注母题，
  mono 小字充当页边批语，大号衬线承担正文宣言。
  hero 粒子画布与 03 文档读取窗口两处既有效果保持原样。

  背景版式角色：全页统一为「纸面网格」（paper-grid，Token 见 theme.css）。
  所有章节背景透明，共用 .page 上同一张纸面；
  区块之间靠垂直节奏与内容分层，不再靠换底色。
  ========================================================================== */
.page {
  /* 面片取值（卡片 / 芯片共用），随明暗态自动换算 */
  --hv-alt-surface: light-dark(#fdfdfb, #1f232d);
  --hv-block-surface: var(--tb-bg-surface);
  position: relative;
  color: var(--tb-text-primary);
  /* 铺在 .page 而不是某个内容容器上：fixed 顶栏所在的那条顶部区域也落在同一张纸面上，
     否则会出现「顶栏一条纯色 + 内容一段纸面」的色差断层 */
  background-color: var(--tb-paper-bg);
  background-image:
    linear-gradient(90deg, var(--tb-paper-line) 1px, transparent 1px),
    linear-gradient(var(--tb-paper-line) 1px, transparent 1px);
  background-size: var(--tb-paper-size) var(--tb-paper-size);
}

/* 纸张颗粒：只铺在 redesigned 章节上（hero 与 03 两处既有效果不覆盖） */
.section::after,
.stage--rail::after,
.stage--split::after,
.stage--kb::after,
.stage--cta::after,
.stack::after,
.shift::after,
.strip::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background-image: var(--tb-noise);
  opacity: calc(var(--tb-grain) * 0.7);
}

/* 选中文字 = 被荧光批注 */
.page ::selection {
  background: color-mix(in oklab, var(--tb-accent) 26%, transparent);
}

/* 横向裁切交给内容区 main，而不是 .page：
   .page 一旦 overflow 非 visible 就成为滚动容器，其内部的 position: sticky 顶栏
   会以 .page 的滚动区为基准，页面滚动时无法吸顶；main 不是顶栏的祖先，
   既裁掉装饰溢出，又不影响顶栏吸顶。
   用 clip 而非 hidden：hidden 会把 main 变成滚动容器，吞掉后代所有
   position: sticky（05 堆叠卡的整屏覆盖、02 批注幕的整屏钉住都依赖 sticky）。 */
main {
  overflow-x: clip;
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

/* ===== 02 Vision · 校样拼贴 + 批注卡 ===== */
.vision__grid {
  display: grid;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  gap: clamp(32px, 5vw, 72px);
  align-items: start;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

/* 左：用纸胶带钉在桌面上的一张校样 */
.vision__plate {
  margin: 0;
  position: relative;
  will-change: transform;
}
.vision__frame {
  position: relative;
  overflow: hidden;
  border-radius: var(--tb-radius-md);
  border: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
  transform: rotate(-1.6deg);
}
/* 两截纸胶带钉在相框上缘（挂在 plate 上，避免被相框的裁切剪掉） */
.vision__plate::before,
.vision__plate::after {
  content: '';
  position: absolute;
  z-index: 2;
  top: -12px;
  width: 88px;
  height: 26px;
  background: color-mix(in oklab, var(--tb-primary-soft) 72%, var(--tb-paper-bg));
  border: 1px solid var(--tb-border);
  box-shadow: var(--tb-shadow-1);
}
.vision__plate::before {
  left: 9%;
  transform: rotate(-5deg);
}
.vision__plate::after {
  right: 9%;
  transform: rotate(4deg);
}
.vision__img {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 4 / 5;
  object-fit: cover;
  /* 轻微放大裁切：吃掉画布毛边，让构图更满 */
  transform: scale(1.16) translateY(-2%);
}
.vision__cap {
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-3);
  margin-top: var(--tb-size-4);
  padding-left: var(--tb-size-4);
  border-left: 2px solid var(--tb-accent);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}
.vision__cap-no {
  color: var(--tb-accent);
  font-weight: 700;
}

/* 右：三条批注卡，逐级右移成阶梯 */
.vision__cards {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-5);
}
.vision__card {
  position: relative;
  padding: 2rem var(--tb-size-6) 2rem 4.8rem;
  border-radius: var(--tb-radius-lg);
  background: var(--hv-alt-surface);
  border: 1px solid var(--tb-border);
  box-shadow: var(--tb-shadow-1);
  transition:
    transform var(--tb-dur-3) var(--tb-ease-out),
    box-shadow var(--tb-dur-3) var(--tb-ease-out);
}
.vision__card:nth-child(2) {
  margin-left: clamp(0px, 4vw, 44px);
}
.vision__card:nth-child(3) {
  margin-left: clamp(0px, 9vw, 96px);
}
.vision__card:hover {
  transform: translateY(-6px);
  box-shadow: var(--tb-shadow-3);
}
/* 卡左缘的红笔批注符：保留 / 协作 / 归档 */
.vision__glyph {
  position: absolute;
  left: var(--tb-size-5);
  top: 2.15rem;
  width: 26px;
  height: 26px;
  stroke: var(--tb-accent);
  stroke-width: 2.2;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}
.vision__card-head {
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-3);
}
.vision__num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--tb-primary-text);
  letter-spacing: 0.1em;
}
.vision__tag {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}
.vision__card h3 {
  margin: var(--tb-size-3) 0 var(--tb-size-2);
  font-size: 1.4rem;
  font-weight: 700;
}
.vision__card p {
  color: var(--tb-text-secondary);
  line-height: var(--tb-leading-normal);
}

/* 画芯内视差层：相框裁切不动，画随滚动游走 */
.vision__drift {
  will-change: transform;
}

/* ==========================================================================
   02 批注 B-01 · 一笔红批
   整节 260vh，内层 sticky 钉满一屏；滚动进度由 setupShift 的时间线驱动：
   划掉「重复」→ 圈出「创造」→ 逐条核销案头账单。
   ========================================================================== */
.shift {
  --stage-inset: max(var(--tb-size-5), calc((100% - 1360px) / 2));
  position: relative;
  height: 260vh;
}

.shift__pin {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: clamp(24px, 5vh, 48px);
  padding: clamp(72px, 12vh, 120px) var(--stage-inset) clamp(40px, 8vh, 72px);
  overflow: hidden;
}

.shift__head {
  max-width: 1360px;
  width: 100%;
  margin: 0 auto;
}

.shift__stage {
  display: grid;
  grid-template-columns: minmax(0, 1.22fr) minmax(0, 0.78fr);
  align-items: center;
  gap: clamp(32px, 6vw, 96px);
  max-width: 1360px;
  width: 100%;
  margin: 0 auto;
}

.shift__kicker {
  max-width: 34ch;
  font-size: clamp(1rem, 1.5vw, 1.25rem);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* 大字对：重复 → 创造 */
.shift__swap {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: clamp(14px, 3vw, 40px);
  margin-top: clamp(18px, 4vh, 36px);
}

.shift__word {
  position: relative;
  display: inline-block;
  font-family: var(--tb-font-display);
  font-size: clamp(3.2rem, 8.4vw, 7.2rem);
  font-weight: var(--tb-weight-display);
  line-height: 1.08;
  letter-spacing: var(--tb-tracking-display);
}
.shift__word--old {
  color: var(--tb-text-primary);
}
.shift__word--new {
  color: var(--tb-primary-text);
}

/* 两道红斜线压在「重复」上；圈注套住「创造」 */
.shift__strike,
.shift__ring {
  position: absolute;
  overflow: visible;
  pointer-events: none;
}
.shift__strike {
  left: -6%;
  top: 10%;
  width: 112%;
  height: 84%;
}
.shift__ring {
  left: -8%;
  top: -14%;
  width: 116%;
  height: 128%;
}
.shift__strike path,
.shift__ring path {
  fill: none;
  stroke: var(--tb-accent);
  stroke-linecap: round;
  vector-effect: non-scaling-stroke;
}
.shift__strike path {
  stroke-width: 5px;
}
.shift__ring path {
  stroke-width: 3px;
  opacity: 0.85;
}

/* 页边小注：划去 / 圈出 */
.shift__note {
  position: absolute;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-accent);
  white-space: nowrap;
}
.shift__note--cross {
  top: -1.6em;
  right: -1.2em;
  transform: rotate(6deg);
}
.shift__note--circle {
  bottom: -1.8em;
  right: -0.6em;
  transform: rotate(-4deg);
}

.shift__arrow {
  font-family: var(--tb-font-display);
  font-size: clamp(1.5rem, 3vw, 2.6rem);
  color: var(--tb-accent);
}

.shift__lead {
  margin-top: clamp(18px, 4vh, 36px);
  font-size: clamp(1rem, 1.4vw, 1.2rem);
  color: var(--tb-text-secondary);
}

/* 案头账单：被红笔核销的小票 */
.receipt {
  justify-self: end;
  width: min(100%, 380px);
  padding: var(--tb-size-5) var(--tb-size-5) var(--tb-size-4);
  border: 1px dashed var(--tb-border-strong);
  border-radius: var(--tb-radius-md);
  background: var(--hv-alt-surface);
  box-shadow: var(--tb-shadow-3);
  transform: rotate(1.6deg);
  font-family: var(--font-mono);
}

.receipt__head {
  display: flex;
  justify-content: space-between;
  gap: var(--tb-size-3);
  padding-bottom: var(--tb-size-3);
  border-bottom: 1px dashed var(--tb-border-strong);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.receipt__list {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
  margin-top: var(--tb-size-4);
  list-style: none;
}

.receipt__list li {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: var(--tb-size-2);
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.receipt__leader {
  flex: 1 1 auto;
  align-self: center;
  height: 1px;
  background: repeating-linear-gradient(90deg, var(--tb-border) 0 4px, transparent 4px 8px);
}

.receipt__list b {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-primary);
}

/* 红笔核销线：默认已划（无 JS / reduce-motion 的静态终稿），滚动时由脚本重划 */
.receipt__cross {
  position: absolute;
  top: 50%;
  left: -4px;
  right: -4px;
  height: 2px;
  margin-top: -1px;
  border-radius: 1px;
  background: var(--tb-accent);
  transform-origin: left center;
}

.receipt__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: var(--tb-size-4);
  padding-top: var(--tb-size-3);
  border-top: 1px dashed var(--tb-border-strong);
  font-size: var(--tb-text-xs);
  color: var(--tb-text-secondary);
}

.receipt__total b {
  font-size: 1.4rem;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-primary);
}

.receipt__mark {
  margin-top: var(--tb-size-3);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  text-align: right;
  color: var(--tb-accent);
}

/* ==========================================================================
   工序带 · 巨字转场：两行描边衬线巨字随整页滚动反向横移
   ========================================================================== */
.strip {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 2vh, 20px);
  padding-block: clamp(40px, 9vh, 96px);
  border-block: 1px solid var(--tb-border);
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 8%, #000 92%, transparent);
}

.strip__row {
  display: flex;
  align-items: baseline;
  gap: clamp(24px, 4vw, 64px);
  width: max-content;
  will-change: transform;
}

.strip__row span {
  display: inline-flex;
  align-items: baseline;
  gap: 0.32em;
  font-family: var(--tb-font-display);
  font-size: clamp(3.2rem, 8.4vw, 7.6rem);
  font-weight: var(--tb-weight-display);
  line-height: 1.1;
  white-space: nowrap;
  color: transparent;
  -webkit-text-stroke: 1.5px var(--tb-text-tertiary);
}
@supports not (-webkit-text-stroke: 1px #000) {
  .strip__row span {
    color: var(--tb-border-strong);
  }
}

/* 字后的红勾：这一道工序已被校对 */
.strip__row span i {
  font-style: normal;
  font-family: var(--font-mono);
  font-size: 0.2em;
  color: var(--tb-accent);
  -webkit-text-stroke: 0;
}

/* 第二行换成极淡的实心墨色，反向而行 */
.strip__row--b span {
  -webkit-text-stroke: 0;
  color: color-mix(in oklab, var(--tb-primary) 16%, transparent);
}

/* ---------- 章节页眉与幽灵序号（红批版式的「 Running head」） ---------- */
.stage__run {
  position: absolute;
  top: clamp(20px, 4vh, 36px);
  left: var(--stage-inset);
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}
.stage__run::before {
  content: '';
  width: 7px;
  height: 7px;
  border: 1px solid var(--tb-accent);
  border-radius: 50%;
}

/* 大号描边数字沉在内容后面（z-index: -1 落到纸面之上、内容之下） */
.stage__ghost {
  position: absolute;
  top: clamp(4px, 1.5vh, 20px);
  right: clamp(8px, 4vw, 72px);
  z-index: -1;
  font-family: var(--font-mono);
  font-size: clamp(7rem, 16vw, 15rem);
  font-weight: 700;
  line-height: 1;
  color: transparent;
  -webkit-text-stroke: 1px var(--tb-border-strong);
  opacity: 0.55;
  pointer-events: none;
  user-select: none;
}
@supports not (-webkit-text-stroke: 1px #000) {
  .stage__ghost {
    color: var(--tb-border);
    opacity: 0.4;
  }
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

/* ---------- 03 横向轨道 · 校样画廊 ----------
   满幅钉住横移：切角卡上下交错串在一条点线轨道上 */
.stage--rail {
  height: 100vh;
  padding-inline: 0;
  padding-block: clamp(64px, 10vh, 112px) clamp(40px, 7vh, 72px);
  justify-content: center;
  gap: clamp(32px, 6vh, 64px);
}

/* 右缘竖排 SCROLL 标签（karot 同款位置） */
.rail__scroll {
  position: absolute;
  top: 50%;
  right: clamp(10px, 1.6vw, 24px);
  z-index: 2;
  transform: translateY(-50%);
  writing-mode: vertical-rl;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}

/* 页眉已移除：画廊独占本节，章序信息交给左上角 Running head 与右缘竖排标签 */

.rail {
  position: relative;
  flex: 0 0 auto;
  height: clamp(400px, 54vh, 560px);
  display: flex;
  overflow: hidden;
}

/* 首屏标题板：随轨道一起行进（karot 同构） */
.rail__intro {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--tb-size-4);
  width: clamp(320px, 32vw, 460px);
  padding-right: clamp(16px, 3vw, 56px);
}

.rail__intro-title {
  font-family: var(--tb-font-display);
  font-size: clamp(4.1rem, 6.4vw, 6.2rem);
  font-weight: var(--tb-weight-display);
  line-height: 1.14;
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}
.rail__intro-title em {
  font-style: normal;
  color: var(--tb-accent);
}


.rail__track {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: stretch;
  gap: clamp(28px, 3vw, 64px);
  height: 100%;
  padding-inline: var(--stage-inset);
  will-change: transform;
}

/* 折角卡：左上斜切 + 右下折角（karot 折角掩膜的同构，像一张折了一角的校样） */
.rail__card {
  position: relative;
  flex: 0 0 auto;
  width: clamp(300px, 26vw, 420px);
}

.rail__plate {
  height: 100%;
  display: flex;
  flex-direction: column;
  /* 左缘加宽避开左上斜切；底部加高让内容停在右下折角上方 */
  padding: clamp(18px, 2.6vh, 26px);
  padding-left: calc(clamp(18px, 2.6vh, 26px) + 7%);
  padding-bottom: calc(clamp(18px, 2.6vh, 26px) + clamp(22px, 4vh, 42px));
  background: var(--hv-alt-surface);
  /* drop-shadow 沿折角轮廓投出，而不是沿矩形 */
  filter: drop-shadow(0 14px 26px color-mix(in oklab, var(--tb-brand-600) 16%, transparent));
  clip-path: polygon(
    7% 0,
    100% 0,
    100% 94%,
    77% 94%,
    71% 100%,
    0 100%,
    0 26%,
    7% 16%
  );
}

/* 工序连线：红笔随行进逐段描红（karot scrollline 同构） */
.rail__link {
  position: relative;
  flex: 0 0 clamp(64px, 8vw, 150px);
  align-self: center;
  height: 2px;
  background: repeating-linear-gradient(
    90deg,
    var(--tb-border-strong) 0 3px,
    transparent 3px 10px
  );
}
.rail__link i {
  position: absolute;
  inset: 0;
  background: var(--tb-accent);
  transform: scaleX(0);
  transform-origin: left center;
}
/* 进度指示已收敛到卡上的分页点与工序连线，不再有独立进度条 */

/* 卡底：分页点（本卡序号点亮）+ STEP 角标 */
.rail__card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-top: auto;
  padding-top: var(--tb-size-3);
}
.rail__points {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.rail__points i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tb-border-strong);
}
.rail__points i.is-on {
  background: var(--tb-accent);
}

/* 卡头：工序板号 + 右上角的验收勾 */
.rail__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--tb-size-4);
}

.rail__tick {
  font-family: var(--font-mono);
  font-size: var(--tb-text-sm);
  color: var(--tb-accent);
}
.rail__tick::before {
  content: '✓';
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

/* 卡首插画：一张装裱在纸衬上的样张，弹性撑满卡身剩余高度 */
.rail__fig {
  flex: 1 1 auto;
  min-height: clamp(140px, 20vh, 220px);
  display: flex;
  margin: 0 0 var(--tb-size-4);
  padding: clamp(5px, 0.7vh, 9px);
  border: 1px solid var(--tb-border);
  background: var(--tb-paper-bg);
  box-shadow: var(--tb-shadow-1);
  overflow: hidden;
}
.rail__img {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 100%;
  object-fit: cover;
  /* 构图略上移并放大：吃掉画布毛边（含水印位） */
  object-position: center 42%;
  transform: scale(1.06);
  transition: transform var(--tb-dur-4) var(--tb-ease-out);
}
@media (hover: hover) {
  .rail__card:hover .rail__img {
    transform: scale(1.12);
  }
}

/* 终点卡：工序走完，交付成稿（淡蓝卡面 + 入口） */
.rail__card--end .rail__plate {
  background: var(--tb-primary-soft);
}
.rail__card--end .rail__title {
  color: var(--tb-primary-text);
}
.rail__end-link {
  display: inline-flex;
  align-items: center;
  gap: var(--tb-size-2);
  margin-top: var(--tb-size-4);
  padding: var(--tb-size-2) var(--tb-size-4);
  border: 1px solid var(--tb-primary-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-surface);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-primary-text);
  text-decoration: none;
  white-space: nowrap;
  transition:
    background var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}
@media (hover: hover) {
  .rail__end-link:hover {
    border-color: var(--tb-primary);
    background: var(--tb-bg-raised);
  }
}

.rail__meta {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* 尾部留白：让终点卡也能推到画面中间 */
.rail__track::after {
  content: '';
  flex: 0 0 clamp(120px, 30vw, 520px);
}

/* ---------- 05 整屏卡堆叠 · 模板样张 ---------- */
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
  padding-right: min(40%, 460px);
}

/* 每页三条真实功能点：红勾标记（亮色红笔 / 暗色蓝笔） */
.stack__feats {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
  margin-top: var(--tb-size-5);
  max-width: 46ch;
  list-style: none;
}
.stack__feats li {
  position: relative;
  padding-left: var(--tb-size-5);
  font-size: var(--tb-text-md);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}
.stack__feats li::before {
  content: '✓';
  position: absolute;
  left: 0;
  top: 0.1em;
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  font-weight: 700;
  color: var(--tb-accent);
}

/* 共享管线带：五个环节一览 */
.stack__pipe {
  margin-top: var(--tb-size-4);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.stack__title {
  position: relative;
  margin-top: var(--tb-size-4);
  font-family: var(--tb-font-display);
  font-size: clamp(3.2rem, 10vw, 8.5rem);
  font-weight: var(--tb-weight-display);
  line-height: 1;
  letter-spacing: var(--tb-tracking-display);
  color: var(--tb-text-primary);
}

.stack__text {
  position: relative;
  margin-top: var(--tb-size-4);
  max-width: 38ch;
  font-size: var(--tb-text-lg);
  line-height: var(--tb-leading-normal);
  color: var(--tb-text-secondary);
}

/* 右侧的模板样张：元信息 + 插画 + 红章 */
.stack__spec {
  position: absolute;
  right: 0;
  top: 50%;
  width: min(38%, 400px);
  height: clamp(340px, 46vh, 480px);
  transform: translateY(-50%) rotate(1.2deg);
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-4);
  padding: var(--tb-size-5);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
}

.stack__spec-meta {
  margin: 0;
  padding-bottom: var(--tb-size-3);
  border-bottom: 1px solid var(--tb-border);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

/* 插画样张：弹性撑满元信息与红章之间 */
.stack__spec-fig {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0;
  border: 1px solid var(--tb-border);
  background: var(--tb-paper-bg);
  overflow: hidden;
}
.stack__spec-fig img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 45%;
}

/* 教研组的红章：这张样张已经被核对过 */
.stack__stamp {
  position: absolute;
  right: var(--tb-size-5);
  bottom: var(--tb-size-5);
  padding: var(--tb-size-2) var(--tb-size-4);
  border: 2px solid var(--tb-accent);
  border-radius: var(--tb-radius-sm);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--tb-accent) 40%, transparent);
  color: var(--tb-accent);
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-widest);
  transform: rotate(-6deg);
  opacity: 0.92;
  user-select: none;
}
.stack__card:nth-child(2n) .stack__stamp {
  transform: rotate(5deg);
}

/* ---------- 06 左右分栏 + 大数字 + 批改工作台 ---------- */
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

/* 页边批注：分工口径像写在版心外侧的批语 */
.stage__margin-note {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tb-size-2) var(--tb-size-5);
  margin: clamp(20px, 4vh, 40px) 0 0;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.stage__note-mark {
  font-style: normal;
  margin-right: 6px;
  color: var(--tb-accent);
}

.stage__visual {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--tb-size-3);
}

.stage__visual-cap {
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-wide);
  color: var(--tb-text-tertiary);
}

.paper-art {
  width: min(100%, 380px);
  height: auto;
  color: var(--tb-text-tertiary);
}

.paper-art__sheet {
  fill: var(--hv-alt-surface);
  stroke: var(--tb-border-strong);
  stroke-width: 1.5;
}

.paper-art__sheet--back {
  fill: var(--tb-bg-raised);
  stroke: var(--tb-border);
}

.paper-art__rule {
  stroke: var(--tb-border);
  stroke-width: 2;
  stroke-linecap: round;
}

/* 红笔批改痕迹：勾 / 叉 / 圈分 / 批注线，统一一支笔的颜色 */
.paper-art__margin {
  stroke: var(--tb-accent);
  stroke-width: 1;
  opacity: 0.5;
}

.paper-art__check {
  stroke: var(--tb-accent);
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  fill: none;
}

.paper-art__cross {
  stroke: var(--tb-accent);
  stroke-width: 3;
  stroke-linecap: round;
}

.paper-art__mark {
  fill: none;
  stroke: var(--tb-accent);
  stroke-width: 2;
}

.paper-art__mark-text {
  fill: var(--tb-accent);
  font-family: var(--font-mono);
  font-size: 20px;
  text-anchor: middle;
}

.paper-art__pencil {
  fill: var(--tb-primary);
}

.paper-art__pencil-tip {
  fill: var(--tb-border-strong);
}

.paper-art__pencil-eraser {
  fill: var(--tb-accent);
}

/* ---------- 07 知识库 · 双向跑马灯 ---------- */
.mq {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-3);
  margin-inline: calc(-1 * var(--stage-inset));
  padding-block: var(--tb-size-4);
  border-block: 1px solid var(--tb-border);
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 6%, #000 94%, transparent);
}

.mq__row {
  display: flex;
  width: max-content;
  animation: mqScroll 42s linear infinite;
}

/* 第二行反向对开：两条来源带相向而行 */
.mq__row--reverse {
  animation-direction: reverse;
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

.mq__row--reverse span {
  background: var(--tb-bg-raised);
  color: var(--tb-text-tertiary);
}

/* 跑马灯左缘的竖排注记，垫一小块纸面保证可读 */
.kb__axis {
  position: absolute;
  left: max(10px, calc(var(--stage-inset) / 2));
  top: 50%;
  z-index: 1;
  transform: translateY(-50%);
  padding: var(--tb-size-3) 0;
  background: var(--tb-paper-bg);
  writing-mode: vertical-rl;
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-text-tertiary);
}

.stage__items {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--tb-size-6);
  list-style: none;
}

/* 条目改成批注式：一条顶线压住，悬停时线变红 */
.stage__items li {
  padding-top: var(--tb-size-4);
  border-top: 2px solid var(--tb-border-strong);
  transition: border-color var(--tb-dur-3) var(--tb-ease-out);
}

@media (hover: hover) {
  .stage__items li:hover {
    border-top-color: var(--tb-accent);
  }
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

/* ---------- 08 收尾 · 定稿页 ---------- */
.stage--cta {
  align-items: center;
  text-align: center;
  gap: var(--tb-size-5);
}

.stage__title--xl {
  font-size: clamp(2.4rem, 6.4vw, 5.4rem);
  line-height: 1;
}

/* 四角裁切线：这是最终定稿的那一版 */
.stage__crop {
  position: absolute;
  inset: clamp(16px, 3vh, 28px);
  pointer-events: none;
}
.stage__crop i {
  position: absolute;
  width: 18px;
  height: 18px;
  border: 0 solid var(--tb-border-strong);
}
.stage__crop i:nth-child(1) {
  left: 0;
  top: 0;
  border-left-width: 1px;
  border-top-width: 1px;
}
.stage__crop i:nth-child(2) {
  right: 0;
  top: 0;
  border-right-width: 1px;
  border-top-width: 1px;
}
.stage__crop i:nth-child(3) {
  left: 0;
  bottom: 0;
  border-left-width: 1px;
  border-bottom-width: 1px;
}
.stage__crop i:nth-child(4) {
  right: 0;
  bottom: 0;
  border-right-width: 1px;
  border-bottom-width: 1px;
}

/* 验收章：随章节入场盖下来 */
.stage__stamp {
  position: absolute;
  right: clamp(6%, 12vw, 18%);
  top: 22%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--tb-size-3) var(--tb-size-5);
  border: 2px solid var(--tb-accent);
  border-radius: var(--tb-radius-md);
  box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--tb-accent) 40%, transparent);
  color: var(--tb-accent);
  font-family: var(--font-mono);
  font-size: var(--tb-text-2xs);
  font-weight: 700;
  letter-spacing: var(--tb-tracking-widest);
  transform: rotate(-7deg);
  user-select: none;
  pointer-events: none;
}

/* 被红笔圈出的「看见」：圈在手绘路径上，不随逐词动画裁切 */
.final__word {
  position: relative;
  display: inline-block;
}
.final__ring {
  position: absolute;
  left: -10%;
  top: -16%;
  width: 120%;
  height: 132%;
  overflow: visible;
  pointer-events: none;
}
.final__ring path {
  fill: none;
  stroke: var(--tb-accent);
  stroke-width: 3px;
  stroke-linecap: round;
  vector-effect: non-scaling-stroke;
  opacity: 0.85;
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
  .shift {
    --stage-inset: var(--tb-size-5);
  }
  /* 文档窗口：右栏收窄，补充段落让位，窗口不至于拉成两三屏 */
  .docgen__body {
    grid-template-columns: minmax(0, 1fr) 280px;
  }
  .docgen__para--wide {
    display: none;
  }
  /* 宣言区：单栏，拼贴提到批注卡前面，阶梯取消 */
  .vision__grid {
    grid-template-columns: 1fr;
    gap: var(--tb-size-6);
  }
  .vision__plate {
    order: -1;
    max-width: 520px;
    margin-inline: auto;
    width: 100%;
  }
  .vision__card:nth-child(2),
  .vision__card:nth-child(3) {
    margin-left: 0;
  }
  .stage--split {
    grid-template-columns: 1fr;
  }
  .stage__visual {
    display: none;
  }
  .kb__axis {
    display: none;
  }
  .rail__scroll {
    display: none;
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
  /* 批注幕：单栏、小票居中，页边小注收起 */
  .shift {
    --stage-inset: var(--tb-size-5);
    height: 220vh;
  }
  .shift__pin {
    padding-block: 88px var(--tb-size-6);
  }
  .shift__stage {
    grid-template-columns: 1fr;
    align-content: center;
    gap: var(--tb-size-5);
  }
  .shift__word {
    font-size: clamp(2.6rem, 11vw, 4rem);
  }
  .shift__note {
    display: none;
  }
  .receipt {
    justify-self: center;
    width: min(100%, 360px);
    transform: rotate(0.8deg);
  }
  .receipt__list li {
    font-size: var(--tb-text-2xs);
  }
  .receipt__total b {
    font-size: 1.2rem;
  }
  /* 页眉与幽灵序号在窄屏让位 */
  .stage__run,
  .stage__ghost {
    display: none;
  }
  .strip__row span {
    font-size: clamp(2.4rem, 10.5vw, 4rem);
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
  .rail__intro {
    width: 78vw;
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
  .stack__inner {
    padding-right: 0;
  }
  .stack__spec {
    position: static;
    width: 100%;
    height: clamp(300px, 44vh, 400px);
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
  /* 收尾区：验收章收回正文流，跟在标题上方 */
  .stage__stamp {
    position: static;
    margin-inline: auto;
    transform: rotate(-4deg);
  }
}

/* 小屏手机与横屏矮视口：评价段让位，窗口不超过一屏半 */
@media (max-width: 480px), (max-height: 560px) {
  .docgen__para--short {
    display: none;
  }
}

/* 矮视口：插画让位，保证 100vh 内放得下 */
@media (max-height: 720px) {
  .rail__fig {
    display: none;
  }
}
/* ===== 无障碍：尊重「减少动效」偏好 =====
   常驻装饰动画静止（内容仍可见），交互过渡取消；
   滚动编排动效由 GSAP 承担，见 composables/useSmoothScroll.js 与 09-样式规范.md §6.2 */
@media (prefers-reduced-motion: reduce) {
  .mq__row,
  .docgen__status.is-scanning i {
    animation: none;
  }
  .btn,
  .vision__card,
  .stage__items li,
  .docgen__mark { transition: none; }
  .docgen__prose :deep(.docgen__w) { transition: none; }
}
</style>
