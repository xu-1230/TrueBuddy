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

  onMounted(() => {
    lenis.value = new Lenis({
      duration: 1.2,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      smoothWheel: true,
      ...options,
    })

    // 驱动 GSAP ticker
    lenis.value.on('scroll', ScrollTrigger.update)
    gsap.ticker.add((time) => {
      lenis.value && lenis.value.raf(time * 1000)
    })
    gsap.ticker.lagSmoothing(0)

    // 锚点点击平滑跳转
    document.querySelectorAll('[data-scroll-to]').forEach((el) => {
      el.addEventListener('click', (e) => {
        const target = el.getAttribute('data-scroll-to')
        const node = document.querySelector(target)
        if (node) {
          e.preventDefault()
          lenis.value.scrollTo(node, { offset: 0, duration: 1.4 })
        }
      })
    })

    ScrollTrigger.refresh()
  })

  onBeforeUnmount(() => {
    if (lenis.value) {
      lenis.value.destroy()
      lenis.value = null
    }
    gsap.ticker.remove(() => {})
  })

  const refresh = () => ScrollTrigger.refresh()
  const scrollTo = (target, opts) => lenis.value && lenis.value.scrollTo(target, opts)

  return { lenis, refresh, scrollTo }
}
