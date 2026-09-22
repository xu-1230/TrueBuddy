<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useSmoothScroll } from '../../composables/useSmoothScroll'
import NavbarItem from '../../components/NavbarItem.vue'

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

/** 章节编号（001 ~ 00N），对应参考站的 PAGE 编号 */
const codeOf = (idx) => String(idx + 1).padStart(3, '0')

const progress = ref(0)
const activeIndex = ref(0)
let triggers = []

onMounted(async () => {
  await nextTick()
  setupAnimations()
  refresh()
})

/** 滚动进度条 + 章节入场动画 + 层叠滚动（钉住 / 覆盖 / 推出） */
function setupAnimations() {
  const stages = Array.from(document.querySelectorAll('[data-section]'))

  // 滚动进度条
  ScrollTrigger.create({
    start: 0,
    end: () => document.body.scrollHeight - window.innerHeight,
    onUpdate: (self) => {
      progress.value = self.progress
    },
  })

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
      )
    })

    // 首屏滚动提示：向下滚动即淡出
    fades.forEach((el) => {
      gsap.to(el, {
        opacity: 0,
        y: 12,
        ease: 'none',
        scrollTrigger: { trigger: sec, start: 'top top', end: '+=25%', scrub: true },
      })
    })

    triggers.push(tl.scrollTrigger)
  })

  // ② 章节指示：视口中心落入哪一屏，右侧导航就高亮哪一项
  stages.forEach((sec, i) => {
    ScrollTrigger.create({
      trigger: sec,
      start: 'top center',
      end: 'bottom center',
      onToggle: (self) => {
        if (self.isActive) activeIndex.value = i
      },
    })
  })
}

onBeforeUnmount(() => {
  ScrollTrigger.getAll().forEach((t) => t.kill())
})

/** 章节导航：平滑滚动到目标章节 */
function goTo(idx) {
  const el = document.getElementById(sections[idx].id)
  if (!el) return
  const targetTop = el.offsetTop
  if (Math.abs(window.scrollY - targetTop) < 8) return
  activeIndex.value = idx
  scrollTo(targetTop, { duration: 1.2 })
}
</script>

<template>
  <div class="page">
    <NavbarItem />

    <!-- 滚动进度条 -->
    <div class="progress">
      <div class="progress__bar" :style="{ transform: `scaleX(${progress})` }"></div>
    </div>

    <!-- 侧边章节导航 -->
    <nav class="dots">
      <button
        v-for="(s, i) in sections"
        :key="s.id"
        class="dots__item"
        :class="{ 'is-active': activeIndex === i }"
        :aria-label="s.label"
        @click="goTo(i)"
      >
        <span class="dots__point"></span>
        <span class="dots__label">{{ codeOf(i) }} {{ s.label }}</span>
      </button>
    </nav>

    <main>
      <!-- 01 Hero -->
      <section id="hero" data-section data-index="0" class="hero">
        <div class="hero__bg">
          <span data-parallax data-speed="16" class="hero__blob hero__blob--1"></span>
          <span data-parallax data-speed="26" class="hero__blob hero__blob--2"></span>
          <span data-parallax data-speed="8" class="hero__grid"></span>
        </div>
        <div class="hero__inner">
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
          <div data-fade-out class="hero__scroll">
            <span>SCROLL</span>
            <span class="hero__scroll-line"></span>
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
        <div data-parallax data-speed="34" class="ai__glow"></div>
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
        <div data-parallax data-speed="-30" class="grading__glow"></div>
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
        <div data-parallax data-speed="28" class="insight__glow"></div>
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
        <div data-parallax class="cta__glow"></div>
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
.page {
  background: #0a0a0a;
  color: #fafafa;
  overflow-x: hidden;
}

/* ===== 进度条 ===== */
.progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 200;
  background: rgba(255, 255, 255, 0.08);
}
.progress__bar {
  height: 100%;
  background: linear-gradient(90deg, #ff4500, #ec4899);
  transform-origin: left;
  transform: scaleX(0);
}

/* ===== 侧边导航点 ===== */
.dots {
  position: fixed;
  right: 1.5rem;
  top: 50%;
  transform: translateY(-50%);
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.dots__item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.dots__point {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  transition: all 0.3s ease;
}
.dots__label {
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  color: rgba(255, 255, 255, 0.4);
  opacity: 0;
  transform: translateX(8px);
  transition: all 0.3s ease;
  white-space: nowrap;
}
.dots__item.is-active .dots__point {
  background: #ec4899;
  transform: scale(1.4);
}
.dots__item.is-active .dots__label,
.dots__item:hover .dots__label {
  opacity: 1;
  transform: translateX(0);
}

/* ===== 通用按钮 ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.75rem;
  border-radius: 999px;
  font-size: 0.95rem;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.25s ease;
  border: 1px solid transparent;
}
.btn--primary {
  background: #fff;
  color: #0a0a0a;
}
.btn--primary:hover {
  background: #e5e5e5;
  transform: translateY(-2px);
}
.btn--ghost {
  border-color: rgba(255, 255, 255, 0.3);
  color: #fafafa;
}
.btn--ghost:hover {
  border-color: #fafafa;
  background: rgba(255, 255, 255, 0.06);
}
.btn--lg {
  padding: 1rem 2.25rem;
  font-size: 1rem;
}

/* ===== 通用 section ===== */
.section {
  position: relative;
  min-height: 100vh;
  padding: 8rem 2rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 1200px;
  margin: 0 auto;
  overflow: hidden;
}
.section--dark {
  background: #0a0a0a;
  color: #fafafa;
  max-width: none;
}
.section--light {
  background: #fafafa;
  color: #111827;
  max-width: none;
}
.section__head {
  margin-bottom: 4rem;
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
  font-family: var(--font-serif);
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #ff4500;
  margin-bottom: 1.25rem;
}
.section--light .section__index {
  color: #ff4500;
}
.section__title {
  font-family: var(--font-serif);
  font-size: clamp(2.4rem, 5vw, 4rem);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.02em;
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
  margin-top: 1.5rem;
  font-size: 1.1rem;
  line-height: 1.7;
  max-width: 640px;
  opacity: 0.7;
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
  padding: 0 2rem;
  overflow: hidden;
  background: #0a0a0a;
}
.hero__bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}
.hero__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.hero__blob--1 {
  width: 520px;
  height: 520px;
  background: #ff4500;
  top: -120px;
  right: -100px;
}
.hero__blob--2 {
  width: 420px;
  height: 420px;
  background: #ec4899;
  bottom: -100px;
  left: -80px;
}
.hero__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(circle at 50% 50%, #000 30%, transparent 75%);
}
.hero__inner {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.hero__eyebrow {
  font-size: 0.85rem;
  letter-spacing: 0.2em;
  color: #ff7d4d;
  margin-bottom: 1.75rem;
}
.hero__title {
  font-family: var(--font-serif);
  font-size: clamp(3.2rem, 9vw, 7.5rem);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  margin-bottom: 2rem;
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
.hero__word--accent {
  background: linear-gradient(135deg, #ff7d4d, #ec4899);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero__desc {
  font-size: clamp(1.05rem, 1.6vw, 1.35rem);
  line-height: 1.7;
  color: rgba(250, 250, 250, 0.7);
  max-width: 620px;
}
.hero__desc--2 {
  margin-top: 0.35rem;
}
.hero__cta {
  margin-top: 2.75rem;
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}
.hero__scroll {
  position: absolute;
  bottom: -2rem;
  left: 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.7rem;
  letter-spacing: 0.25em;
  color: rgba(250, 250, 250, 0.45);
}
.hero__scroll-line {
  width: 1px;
  height: 60px;
  background: linear-gradient(to bottom, rgba(250, 250, 250, 0.5), transparent);
  position: relative;
  overflow: hidden;
}
.hero__scroll-line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #ec4899;
  transform: translateY(-100%);
  animation: scrollLine 2.2s ease-in-out infinite;
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
  gap: 1.5rem;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.vision__card {
  padding: 2.5rem 2rem;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #eceef2;
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.04);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.vision__card:hover {
  transform: translateY(-6px);
  box-shadow: 0 20px 40px -12px rgba(17, 24, 39, 0.12);
}
.vision__num {
  font-size: 0.85rem;
  font-weight: 700;
  color: #ff4500;
  letter-spacing: 0.1em;
}
.vision__card h3 {
  margin: 1rem 0 0.75rem;
  font-size: 1.4rem;
  font-weight: 700;
}
.vision__card p {
  color: #6b7280;
  line-height: 1.7;
}

/* ===== 03 AI ===== */
.ai__glow {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 69, 0, 0.35), transparent 70%);
  top: 10%;
  right: -150px;
  filter: blur(40px);
}
.ai__grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 4rem;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.ai__panel {
  border-radius: 24px;
  background: linear-gradient(145deg, #1a1a2e, #0f0f1e);
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 2rem;
  box-shadow: 0 30px 60px -20px rgba(0, 0, 0, 0.6);
}
.ai__chat {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.ai__chat-row {
  padding: 1rem 1.25rem;
  border-radius: 16px;
  font-size: 0.95rem;
  line-height: 1.6;
}
.ai__chat-row--user {
  background: rgba(255, 69, 0, 0.15);
  color: #ffc7b3;
  align-self: flex-end;
  max-width: 80%;
}
.ai__chat-row--ai {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(250, 250, 250, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.ai__features {
  display: flex;
  flex-direction: column;
  gap: 1.75rem;
}
.ai__feature h3 {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}
.ai__feature p {
  color: rgba(250, 250, 250, 0.6);
  line-height: 1.7;
}

/* ===== 04 Docs ===== */
.docs__layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4rem;
  align-items: center;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.docs__paper {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  padding: 2.25rem;
  box-shadow: 0 30px 60px -20px rgba(17, 24, 39, 0.18);
  transform: rotate(-1.2deg);
  transition: transform 0.3s ease;
}
.docs__paper:hover {
  transform: rotate(0deg) translateY(-4px);
}
.docs__paper-head {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.docs__paper-tag {
  font-size: 0.75rem;
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  background: #ffece6;
  color: #ff4500;
  font-weight: 600;
}
.docs__paper-title {
  font-weight: 700;
  color: #111827;
}
.docs__paper-line {
  height: 8px;
  background: #f3f4f6;
  border-radius: 4px;
  margin-bottom: 0.75rem;
}
.docs__paper-line--w80 { width: 80%; }
.docs__paper-line--w60 { width: 60%; }
.docs__paper-line--w90 { width: 90%; }
.docs__paper-line--w50 { width: 50%; }
.docs__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}
.docs__item {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
}
.docs__item-num {
  font-size: 0.85rem;
  font-weight: 700;
  color: #ff4500;
  flex: 0 0 auto;
}
.docs__item h3 {
  font-size: 1.2rem;
  font-weight: 700;
  margin-bottom: 0.4rem;
}
.docs__item p {
  color: #6b7280;
  line-height: 1.7;
}

/* ===== 05 Grading ===== */
.grading__glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.3), transparent 70%);
  bottom: -100px;
  left: -100px;
  filter: blur(50px);
}
.grading__board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2rem;
  max-width: 1000px;
  margin: 0 auto 3rem;
  width: 100%;
}
.grading__stat {
  text-align: center;
  padding: 2.5rem 1rem;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.grading__stat-num {
  font-family: var(--font-serif);
  font-size: clamp(3rem, 6vw, 4.5rem);
  font-weight: 800;
  background: linear-gradient(135deg, #ff7d4d, #ec4899);
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
  margin-top: 1rem;
  color: rgba(250, 250, 250, 0.6);
  font-size: 0.95rem;
}
.grading__flow {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 1rem;
  max-width: 1000px;
  margin: 0 auto;
}
.grading__step {
  padding: 0.75rem 1.5rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 0.9rem;
  font-weight: 600;
}
.grading__step--accent {
  background: linear-gradient(135deg, #ff4500, #ec4899);
  border-color: transparent;
  color: #fff;
}
.grading__arrow {
  color: rgba(250, 250, 250, 0.4);
}

/* ===== 06 Knowledge ===== */
.kb__layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4rem;
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
  border: 1px dashed rgba(255, 69, 0, 0.4);
}
.kb__ring--1 { width: 120px; height: 120px; animation: spin 18s linear infinite; }
.kb__ring--2 { width: 220px; height: 220px; border-color: rgba(236, 72, 153, 0.3); animation: spin 28s linear infinite reverse; }
.kb__ring--3 { width: 320px; height: 320px; border-color: rgba(17, 24, 39, 0.15); animation: spin 40s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.kb__core {
  position: relative;
  z-index: 2;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4500, #ec4899);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0.05em;
  box-shadow: 0 20px 40px -10px rgba(255, 69, 0, 0.5);
}
.kb__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}
.kb__item h3 {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}
.kb__item p {
  color: #6b7280;
  line-height: 1.7;
}

/* ===== 07 Insight ===== */
.insight__glow {
  position: absolute;
  width: 500px;
  height: 500px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 125, 77, 0.3), transparent 70%);
  top: 10%;
  right: -100px;
  filter: blur(50px);
}
.insight__chart {
  max-width: 900px;
  margin: 0 auto 3rem;
  width: 100%;
}
.insight__bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  height: 280px;
  padding: 1.5rem;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.insight__bars span {
  flex: 1;
  height: var(--h);
  background: linear-gradient(to top, #ff4500, #ec4899);
  border-radius: 6px 6px 0 0;
  opacity: 0.9;
}
.insight__caption {
  text-align: center;
  margin-top: 1.5rem;
}
.insight__caption p {
  font-weight: 600;
  font-size: 1.05rem;
}
.insight__caption small {
  color: rgba(250, 250, 250, 0.5);
}
.insight__tags {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  max-width: 700px;
  margin: 0 auto;
}
.insight__tags span {
  padding: 0.5rem 1.1rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
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
  padding: 6rem 2rem;
  overflow: hidden;
  background: #0a0a0a;
}
.cta__glow {
  position: absolute;
  width: 700px;
  height: 700px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.25), transparent 65%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  filter: blur(60px);
}
.cta__inner {
  position: relative;
  z-index: 1;
  max-width: 900px;
}
.cta__eyebrow {
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  color: #ff7d4d;
  margin-bottom: 1.75rem;
}
.cta__title {
  font-family: var(--font-serif);
  font-size: clamp(2.6rem, 7vw, 5.5rem);
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.02em;
  margin-bottom: 1.5rem;
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
.cta__word--accent {
  background: linear-gradient(135deg, #ff7d4d, #ec4899);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.cta__desc {
  font-size: 1.15rem;
  color: rgba(250, 250, 250, 0.7);
  margin-bottom: 2.75rem;
}
.cta__buttons {
  display: flex;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 4rem;
}
.cta__foot {
  color: rgba(250, 250, 250, 0.4);
  font-size: 0.85rem;
  letter-spacing: 0.1em;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .section { padding: 6rem 1.25rem; }
  .ai__grid,
  .docs__layout,
  .kb__layout { grid-template-columns: 1fr; gap: 2.5rem; }
  .grading__board { grid-template-columns: 1fr; }
  .dots { display: none; }
  .hero__scroll { display: none; }
}
</style>
