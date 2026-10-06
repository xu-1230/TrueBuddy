<script setup>
import { onMounted, onBeforeUnmount, nextTick } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useSmoothScroll } from '../../composables/useSmoothScroll'
import NavbarItem from '../../components/NavbarItem.vue'
import HeroParticles from './components/HeroParticles.vue'

gsap.registerPlugin(ScrollTrigger)

const { refresh, scrollTo } = useSmoothScroll()

const sections = [
  { id: 'hero', label: '首页', en: 'HOME' },
  { id: 'vision', label: '核心理念', en: 'VISION' },
  { id: 'ai', label: 'AI 助手', en: 'ASSISTANT' },
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

onBeforeUnmount(() => {
  // 只回收本页登记的实例。原先用 ScrollTrigger.getAll() 是全局清空，
  // 会把当时挂在页面上的其它组件的 ScrollTrigger 一并杀掉（例如 /docs）。
  triggers.forEach((t) => t.kill())
  triggers = []
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
          <span data-reveal-item data-drift="72" class="section__index">02 / 08</span>
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

      <!-- 03 AI 智能助手 -->
      <section id="ai" data-section data-index="2" class="section section--dark">
        <header class="section__head section__head--center">
          <span data-reveal-item data-drift="72" class="section__index">03 / 08</span>
          <h2 class="section__title section__title--center">
            <span class="section__word"><span data-reveal-word>AI</span></span>
            <span class="section__word"><span data-reveal-word>智能</span></span>
            <span class="section__word"><span data-reveal-word>助手</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            基于大模型的教学副驾，随时响应课程设计、答疑与内容创作。
          </p>
        </header>
        <div class="ai__grid">
          <div data-zoom class="ai__panel">
            <div class="ai__chat">
              <div class="ai__chat-row ai__chat-row--user">帮我设计一节《光合作用》的导入环节</div>
              <div class="ai__chat-row ai__chat-row--ai">
                好的，建议以「叶片为什么是绿色的」作为悬念切入，配合显微图片观察叶绿体，再引出光反应与暗反应的概念建模……
              </div>
            </div>
          </div>
          <div class="ai__features">
            <div data-card class="ai__feature">
              <h3>自然语言驱动</h3>
              <p>用一句话描述需求，AI 即可生成结构化方案、课件与测评。</p>
            </div>
            <div data-card class="ai__feature">
              <h3>学科知识增强</h3>
              <p>接入教学知识库，回答具备可追溯的依据与出处。</p>
            </div>
            <div data-card class="ai__feature">
              <h3>多模态创作</h3>
              <p>支持文本、图表与结构化文档的协同输出，所见即所得。</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 04 文档模板系统 -->
      <section id="docs" data-section data-index="3" class="section section--light">
        <header class="section__head">
          <span data-reveal-item data-drift="72" class="section__index">04 / 08</span>
          <h2 class="section__title">
            <span class="section__word"><span data-reveal-word>文档</span></span>
            <span class="section__word"><span data-reveal-word>模板</span></span>
            <span class="section__word"><span data-reveal-word>系统</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            教案、试卷、学案一键生成，团队规范统一沉淀。
          </p>
        </header>
        <div class="docs__layout">
          <div data-zoom data-rotate="-1.2" class="docs__paper">
            <div class="docs__paper-head">
              <span class="docs__paper-tag">教案</span>
              <span class="docs__paper-title">光合作用 · 第3课时</span>
            </div>
            <div class="docs__paper-line"></div>
            <div class="docs__paper-line docs__paper-line--w80"></div>
            <div class="docs__paper-line docs__paper-line--w60"></div>
            <div class="docs__paper-line"></div>
            <div class="docs__paper-line docs__paper-line--w90"></div>
            <div class="docs__paper-line docs__paper-line--w50"></div>
          </div>
          <ul class="docs__list">
            <li data-reveal-item class="docs__item">
              <span class="docs__item-num">01</span>
              <div>
                <h3>结构化模板</h3>
                <p>预设教学目标、重难点、活动设计等模块，覆盖全流程。</p>
              </div>
            </li>
            <li data-reveal-item class="docs__item">
              <span class="docs__item-num">02</span>
              <div>
                <h3>智能填充</h3>
                <p>AI 根据课程主题自动补全内容，教师只需审阅与微调。</p>
              </div>
            </li>
            <li data-reveal-item class="docs__item">
              <span class="docs__item-num">03</span>
              <div>
                <h3>团队共享</h3>
                <p>沉淀备课组成统一资源库，新教师可即时继承优秀实践。</p>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <!-- 05 作业智能批改 -->
      <section id="grading" data-section data-index="4" class="section section--dark">
        <header class="section__head section__head--center">
          <span data-reveal-item data-drift="72" class="section__index">05 / 08</span>
          <h2 class="section__title section__title--center">
            <span class="section__word"><span data-reveal-word>作业</span></span>
            <span class="section__word"><span data-reveal-word>智能</span></span>
            <span class="section__word"><span data-reveal-word>批改</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            客观题自动判分，主观题 AI 辅助标注，教师聚焦反馈与讲评。
          </p>
        </header>
        <div class="grading__board">
          <div data-reveal-item class="grading__stat">
            <span class="grading__stat-num"><span data-count="98">0</span><span>%</span></span>
            <p>客观题自动判分准确率</p>
          </div>
          <div data-reveal-item class="grading__stat">
            <span class="grading__stat-num"><span data-count="3">0</span><span>×</span></span>
            <p>批改效率提升</p>
          </div>
          <div data-reveal-item class="grading__stat">
            <span class="grading__stat-num"><span data-count="24">0</span><span>h</span></span>
            <p>学生反馈平均时长缩短</p>
          </div>
        </div>
        <div class="grading__flow">
          <div data-slide-x class="grading__step">上传作业</div>
          <div class="grading__arrow">→</div>
          <div data-slide-x class="grading__step">AI 自动批阅</div>
          <div class="grading__arrow">→</div>
          <div data-slide-x class="grading__step">教师复核</div>
          <div class="grading__arrow">→</div>
          <div data-slide-x class="grading__step grading__step--accent">个性讲评</div>
        </div>
      </section>

      <!-- 06 知识库 -->
      <section id="knowledge" data-section data-index="5" class="section section--light">
        <header class="section__head">
          <span data-reveal-item data-drift="72" class="section__index">06 / 08</span>
          <h2 class="section__title">
            <span class="section__word"><span data-reveal-word>教学</span></span>
            <span class="section__word"><span data-reveal-word>知识</span></span>
            <span class="section__word"><span data-reveal-word>库</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            基于 RAG 的学科知识中枢，让每一份素材可检索、可追溯、可复用。
          </p>
        </header>
        <div class="kb__layout">
          <div class="kb__visual">
            <div data-fade-in class="kb__ring kb__ring--1"></div>
            <div data-fade-in class="kb__ring kb__ring--2"></div>
            <div data-fade-in class="kb__ring kb__ring--3"></div>
            <div data-zoom class="kb__core">RAG</div>
          </div>
          <ul class="kb__list">
            <li data-reveal-item class="kb__item">
              <h3>向量检索</h3>
              <p>语义级精准召回，跨学科、跨学段关联知识。</p>
            </li>
            <li data-reveal-item class="kb__item">
              <h3>引用可追溯</h3>
              <p>每个 AI 回答标注原始出处，杜绝黑盒与幻觉。</p>
            </li>
            <li data-reveal-item class="kb__item">
              <h3>持续演进</h3>
              <p>教学沉淀自动入库，知识图谱随使用不断生长。</p>
            </li>
          </ul>
        </div>
      </section>

      <!-- 07 数据洞察 -->
      <section id="insight" data-section data-index="6" class="section section--dark">
        <header class="section__head section__head--center">
          <span data-reveal-item data-drift="72" class="section__index">07 / 08</span>
          <h2 class="section__title section__title--center">
            <span class="section__word"><span data-reveal-word>教学</span></span>
            <span class="section__word"><span data-reveal-word>数据</span></span>
            <span class="section__word"><span data-reveal-word>洞察</span></span>
          </h2>
          <p data-reveal-line class="section__lead">
            把每一次教学行为转化为可量化的成长轨迹。
          </p>
        </header>
        <div class="insight__chart">
          <div data-reveal-item class="insight__bars">
            <span data-bar style="--h:38%"></span>
            <span data-bar style="--h:62%"></span>
            <span data-bar style="--h:48%"></span>
            <span data-bar style="--h:78%"></span>
            <span data-bar style="--h:55%"></span>
            <span data-bar style="--h:90%"></span>
            <span data-bar style="--h:70%"></span>
          </div>
          <div data-reveal-item class="insight__caption">
            <p>班级知识掌握度趋势</p>
            <small>近 7 个教学单元 · 实时更新</small>
          </div>
        </div>
        <div class="insight__tags">
          <span data-slide-x>学情画像</span>
          <span data-slide-x>薄弱点识别</span>
          <span data-slide-x>分层教学建议</span>
          <span data-slide-x>成长档案</span>
        </div>
      </section>

      <!-- 08 开始使用 -->
      <section id="start" data-section data-index="7" class="cta">
        <div class="cta__inner">
          <span data-reveal-item class="cta__eyebrow">08 / 08 · 立即开始</span>
          <h2 class="cta__title">
            <span class="cta__word"><span data-reveal-word>让</span></span>
            <span class="cta__word"><span data-reveal-word>每一次</span></span>
            <span class="cta__word"><span data-reveal-word>教学</span></span>
            <span class="cta__word"><span data-reveal-word>都被</span></span>
            <span class="cta__word cta__word--accent"><span data-reveal-word>看见</span></span>
          </h2>
          <p data-reveal-line class="cta__desc">
            加入 TrueBuddy，开启智能教学协作的下一程。
          </p>
          <div data-reveal-item class="cta__buttons">
            <RouterLink to="/login" class="btn btn--primary btn--lg">立即登录</RouterLink>
            <RouterLink to="/register" class="btn btn--ghost btn--lg">注册账号</RouterLink>
          </div>
          <p data-reveal-item class="cta__foot">TrueBuddy © 2026 · 让教学回归创造</p>
        </div>
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
  --hv-alt-surface: light-dark(var(--tb-neutral-0), var(--tb-neutral-800));
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
  padding: var(--tb-size-10) var(--tb-size-6);
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

/* ===== 03 AI ===== */
.ai__grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: var(--tb-size-8);
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.ai__panel {
  border-radius: var(--tb-radius-xl);
  /* 面板整体带一层主色，与纯中性卡片区分，亮暗两态都成立 */
  background: linear-gradient(
    145deg,
    color-mix(in oklab, var(--tb-primary) 7%, var(--tb-bg-surface)),
    var(--tb-bg-sunken)
  );
  border: 1px solid var(--tb-border);
  padding: var(--tb-size-6);
  box-shadow: var(--tb-shadow-3);
}
.ai__chat {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.ai__chat-row {
  padding: var(--tb-size-4) 1.25rem;
  border-radius: var(--tb-radius-lg);
  font-size: 0.95rem;
  line-height: var(--tb-leading-normal);
}
.ai__chat-row--user {
  background: var(--tb-primary);
  color: var(--tb-on-primary);
  align-self: flex-end;
  max-width: 80%;
}
.ai__chat-row--ai {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
  border: 1px solid var(--tb-border);
}
.ai__features {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}
.ai__feature h3 {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: var(--tb-size-2);
}
.ai__feature p {
  color: var(--tb-text-secondary);
  line-height: var(--tb-leading-normal);
}

/* ===== 04 Docs ===== */
.docs__layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tb-size-8);
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.docs__paper {
  background: var(--hv-alt-surface);
  border: 1px solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  padding: 2.25rem;
  box-shadow: var(--tb-shadow-3);
  transform: rotate(-1.2deg);
  transition: transform var(--tb-dur-3) var(--tb-ease-out);
}
.docs__paper:hover {
  transform: rotate(0deg) translateY(-4px);
}
.docs__paper-head {
  display: flex;
  align-items: center;
  gap: var(--tb-size-4);
  margin-bottom: var(--tb-size-5);
}
.docs__paper-tag {
  font-size: 0.75rem;
  padding: 0.3rem var(--tb-size-3);
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary-soft);
  border: 1px solid var(--tb-primary-border);
  color: var(--tb-primary-text);
  font-weight: 600;
}
.docs__paper-title {
  font-weight: 700;
  color: var(--tb-text-primary);
}
.docs__paper-line {
  height: 8px;
  background: var(--tb-bg-sunken);
  border-radius: var(--tb-radius-xs);
  margin-bottom: var(--tb-size-3);
}
.docs__paper-line--w80 { width: 80%; }
.docs__paper-line--w60 { width: 60%; }
.docs__paper-line--w90 { width: 90%; }
.docs__paper-line--w50 { width: 50%; }
.docs__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-6);
}
.docs__item {
  display: flex;
  gap: var(--tb-size-5);
  align-items: flex-start;
}
.docs__item-num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--tb-primary-text);
  flex: 0 0 auto;
}
.docs__item h3 {
  font-size: 1.2rem;
  font-weight: 700;
  margin-bottom: 0.4rem;
}
.docs__item p {
  color: var(--tb-text-secondary);
  line-height: var(--tb-leading-normal);
}

/* ===== 05 Grading ===== */
.grading__board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--tb-size-6);
  max-width: 1000px;
  margin: 0 auto var(--tb-size-7);
  width: 100%;
}
.grading__stat {
  text-align: center;
  padding: 2.5rem var(--tb-size-4);
  border-radius: var(--tb-radius-lg);
  background: var(--hv-block-surface);
  border: 1px solid var(--tb-border);
}
.grading__stat-num {
  font-family: var(--tb-font-display);
  font-variant-numeric: tabular-nums;
  font-size: clamp(3rem, 6vw, 4.5rem);
  font-weight: var(--tb-weight-display);
  background: linear-gradient(135deg, var(--tb-brand-400), var(--tb-primary));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1;
}
/* 仅单位部分缩小：数字本体由计数器（data-count）驱动 */
.grading__stat-num > span:last-child {
  font-size: 0.5em;
  font-weight: 700;
}
.grading__stat p {
  margin-top: var(--tb-size-4);
  color: var(--tb-text-secondary);
  font-size: 0.95rem;
}
.grading__flow {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--tb-size-4);
  max-width: 1000px;
  margin: 0 auto;
}
.grading__step {
  padding: var(--tb-size-3) var(--tb-size-5);
  border-radius: var(--tb-radius-full);
  background: var(--hv-block-surface);
  border: 1px solid var(--tb-border);
  font-size: 0.9rem;
  font-weight: 600;
}
.grading__step--accent {
  background: var(--tb-primary);
  border-color: var(--tb-primary);
  color: var(--tb-on-primary);
}
.grading__arrow {
  color: var(--tb-text-tertiary);
}

/* ===== 06 Knowledge ===== */
.kb__layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--tb-size-8);
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.kb__visual {
  position: relative;
  height: 380px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kb__ring {
  position: absolute;
  border-radius: 50%;
  border: 1px dashed color-mix(in oklab, var(--tb-brand-600) 45%, transparent);
}
.kb__ring--1 { width: 120px; height: 120px; animation: spin 18s linear infinite; }
.kb__ring--2 { width: 220px; height: 220px; border-color: color-mix(in oklab, var(--tb-brand-400) 45%, transparent); animation: spin 28s linear infinite reverse; }
.kb__ring--3 { width: 320px; height: 320px; border-color: color-mix(in oklab, var(--tb-text-primary) 20%, transparent); animation: spin 40s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.kb__core {
  position: relative;
  z-index: 2;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--tb-brand-500), var(--tb-brand-700));
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-weight: var(--tb-weight-display);
  color: var(--tb-on-primary);
  letter-spacing: var(--tb-tracking-wide);
  box-shadow: var(--tb-shadow-brand);
}
.kb__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-6);
}
.kb__item h3 {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: var(--tb-size-2);
}
.kb__item p {
  color: var(--tb-text-secondary);
  line-height: var(--tb-leading-normal);
}

/* ===== 07 Insight ===== */
.insight__chart {
  max-width: 900px;
  margin: 0 auto var(--tb-size-7);
  width: 100%;
}
.insight__bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--tb-size-4);
  height: 280px;
  padding: var(--tb-size-5);
  border-radius: var(--tb-radius-lg);
  background: var(--hv-block-surface);
  border: 1px solid var(--tb-border);
}
.insight__bars span {
  flex: 1;
  height: var(--h);
  background: linear-gradient(to top, var(--tb-brand-700), var(--tb-brand-400));
  border-radius: var(--tb-radius-xs) var(--tb-radius-xs) 0 0;
  opacity: 0.9;
}
.insight__caption {
  text-align: center;
  margin-top: var(--tb-size-5);
}
.insight__caption p {
  font-weight: 600;
  font-size: 1.05rem;
}
.insight__caption small {
  font-family: var(--font-mono);
  font-size: var(--tb-text-xs);
  color: var(--tb-text-tertiary);
}
.insight__tags {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: var(--tb-size-3);
  max-width: 700px;
  margin: 0 auto;
}
.insight__tags span {
  padding: var(--tb-size-2) 1.1rem;
  border-radius: var(--tb-radius-full);
  background: var(--hv-block-surface);
  border: 1px solid var(--tb-border);
  color: var(--tb-text-secondary);
  font-size: 0.85rem;
}

/* ===== 08 CTA ===== */
.cta {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--tb-size-9) var(--tb-size-6);
  overflow: hidden;
  /* 背景与其它章节同为纸面（透明） */
  background: transparent;
}
.cta__inner {
  position: relative;
  z-index: 1;
  max-width: 900px;
}
.cta__eyebrow {
  font-size: 0.8rem;
  letter-spacing: var(--tb-tracking-widest);
  color: var(--tb-primary-text);
  margin-bottom: 1.75rem;
}
.cta__title {
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-display);
  font-weight: var(--tb-weight-display);
  line-height: var(--tb-leading-tight);
  letter-spacing: var(--tb-tracking-display);
  margin-bottom: var(--tb-size-5);
}
.cta__word {
  display: inline-block;
  overflow: hidden;
  margin-right: 0.15em;
  vertical-align: top;
}
.cta__word > span {
  display: inline-block;
}
.cta__word--accent > span {
  background: linear-gradient(135deg, var(--tb-brand-400), var(--tb-primary));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.cta__desc {
  font-size: 1.15rem;
  color: var(--tb-text-secondary);
  margin-bottom: 2.75rem;
}
.cta__buttons {
  display: flex;
  justify-content: center;
  gap: var(--tb-size-4);
  flex-wrap: wrap;
  margin-bottom: var(--tb-size-8);
}
.cta__foot {
  font-family: var(--font-mono);
  color: var(--tb-text-tertiary);
  font-size: 0.85rem;
  letter-spacing: 0.1em;
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

@media (max-width: 768px) {
  .section { padding: var(--tb-size-9) 1.25rem; }
  .ai__grid,
  .docs__layout,
  .kb__layout { grid-template-columns: 1fr; gap: 2.5rem; }
  .grading__board { grid-template-columns: 1fr; }
}

/* ===== 无障碍：尊重「减少动效」偏好 =====
   常驻装饰动画静止（内容仍可见），交互过渡取消；
   滚动编排动效由 GSAP 承担，见 composables/useSmoothScroll.js 与 09-样式规范.md §6.2 */
@media (prefers-reduced-motion: reduce) {
  .kb__ring { animation: none; }
  .btn,
  .vision__card,
  .docs__paper { transition: none; }
}
</style>
