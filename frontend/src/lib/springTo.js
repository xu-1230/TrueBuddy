import { gsap } from 'gsap'
import { createSpring } from './spring'

const EPSILON = 0.001

/**
 * key -> 句柄：同 key 的弹簧互相打断（用于指示器、箭头这类单例动画）。
 * 不传 key 时不入表，仅在 live 中登记以便统一回收。
 */
const keyed = new Map()
const live = new Set()

/**
 * 用弹簧物理把 state 上的若干数值属性过渡到 target，等价于 Motion 的
 * `transition: { type: 'spring', stiffness, damping, mass }`。
 *
 * 与 gsap.to 的区别：弹簧没有固定时长，靠物理收敛，适合「跟随 / 游走 / 回弹」类微交互；
 * 由 gsap.ticker 驱动，因此与页面既有 GSAP / ScrollTrigger 共用同一帧循环，不会各跑一套 rAF。
 *
 * @param {object} state 会被就地修改的状态对象（如 { x, y, width, height }）
 * @param {object} target 目标值，键集决定参与动画的属性
 * @param {object} [options] { stiffness, damping, mass, delay, key, onUpdate, onComplete }
 * @returns {{ kill: () => void }}
 */
export function springTo(state, target, options = {}) {
  const { stiffness = 100, damping = 10, mass = 1, delay = 0, key, onUpdate, onComplete } = options

  if (key) keyed.get(key)?.kill()

  const keys = Object.keys(target)
  const springs = keys.map((prop) => {
    const spring = createSpring(state[prop], { stiffness, damping, mass })
    spring.set(target[prop])
    return spring
  })

  let finished = false
  let delayedCall = null

  const settle = () => {
    keys.forEach((prop) => {
      state[prop] = target[prop]
    })
    onUpdate?.(state)
  }

  const finish = () => {
    if (finished) return
    finished = true
    gsap.ticker.remove(tick)
    delayedCall?.kill()
    live.delete(handle)
    if (key && keyed.get(key) === handle) keyed.delete(key)
    settle()
    onComplete?.()
  }

  function tick(_time, deltaTime) {
    // 单帧最大按 64ms 计入，防止切标签页回来后一次积分跨度过大导致突跳
    const dt = Math.min(deltaTime, 64) / 1000
    let moving = false
    keys.forEach((prop, i) => {
      const previous = state[prop]
      state[prop] = springs[i].step(dt)
      if (Math.abs(state[prop] - previous) > EPSILON) moving = true
    })
    onUpdate?.(state)
    if (!moving) finish()
  }

  const handle = {
    kill() {
      if (finished) return
      finished = true
      gsap.ticker.remove(tick)
      delayedCall?.kill()
      live.delete(handle)
      if (key && keyed.get(key) === handle) keyed.delete(key)
    },
  }

  live.add(handle)
  if (key) keyed.set(key, handle)

  if (delay > 0) delayedCall = gsap.delayedCall(delay, () => gsap.ticker.add(tick))
  else gsap.ticker.add(tick)

  return handle
}

/** 组件卸载时统一回收，避免 gsap.ticker 上残留回调 */
export function killSprings() {
  for (const handle of live) handle.kill()
  live.clear()
  keyed.clear()
}
