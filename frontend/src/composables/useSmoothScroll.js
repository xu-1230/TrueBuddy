import { onMounted, onBeforeUnmount, ref } from 'vue'
import Lenis from 'lenis'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

/**
 * 初始化 Lenis 平滑滚动并与 GSAP ScrollTrigger 同步
 * 返回 { lenis, refresh, scrollTo } 供组件按需调用
 */
export function useSmoothScroll(options = {}) {
  const lenis = ref(null)

  // 回调必须存成具名引用：卸载时要用「同一个函数」去解绑。
  // 早期实现写成 gsap.ticker.remove(() => {})，传入的是新建的空函数，
  // 匹配不到任何已注册回调 —— 每次挂载都会往 ticker 上再叠一份常驻 rAF，
  // 反复进出页面后每帧回调数线性增长，表现为「越用越卡」。
  const tickerFn = (time) => {
    if (lenis.value) lenis.value.raf(time * 1000)
  }

  // 锚点跳转改用事件委托：不再为每个 [data-scroll-to] 元素各挂一个
  // 匿名监听（卸载时无从解绑），全站只占一个 document 级监听。
  const onAnchorClick = (e) => {
    const el = e.target instanceof Element ? e.target.closest('[data-scroll-to]') : null
    if (!el || !lenis.value) return
    const node = document.querySelector(el.getAttribute('data-scroll-to'))
    if (!node) return
    e.preventDefault()
    lenis.value.scrollTo(node, { offset: 0, duration: 1.4 })
  }

  onMounted(() => {
    lenis.value = new Lenis({
      duration: 1.2,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      smoothWheel: true,
      ...options,
    })

    // 驱动 GSAP ticker
    const instance = lenis.value
    instance.on('scroll', ScrollTrigger.update)
    gsap.ticker.add(tickerFn)
    // 这里刻意不再调用 gsap.ticker.lagSmoothing(0)：
    // 关掉掉帧补偿后，某一帧超时（重绘 / GC）造成的延迟不会跳帧追赶，
    // 而是持续累积，滚动越久越卡且无法自愈。保留 GSAP 默认补偿策略。

    document.addEventListener('click', onAnchorClick)

    ScrollTrigger.refresh()
  })

  onBeforeUnmount(() => {
    document.removeEventListener('click', onAnchorClick)
    gsap.ticker.remove(tickerFn)

    if (lenis.value) {
      lenis.value.destroy()
      lenis.value = null
    }
  })

  const refresh = () => ScrollTrigger.refresh()
  const scrollTo = (target, opts) => lenis.value && lenis.value.scrollTo(target, opts)

  return { lenis, refresh, scrollTo }
}
