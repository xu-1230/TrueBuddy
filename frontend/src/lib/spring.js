/**
 * 弹簧积分器（无框架工具层）
 *
 * 与 Motion 的 useSpring 等价的数值实现，供顶栏等弹性微交互复用：
 *   force        = -stiffness * (value - target)
 *   dampingF     = -damping * velocity
 *   acceleration = (force + dampingF) / mass
 *   velocity    += acceleration * dt
 *   value       += velocity * dt
 *
 * 采用半隐式欧拉法按秒积分；对较大的 dt 做子步切分（每步不超过 1/120s），
 * 避免掉帧或标签页切回时单步过大导致数值发散/抖动。
 */
export function createSpring(initial = 0, { stiffness = 100, damping = 10, mass = 1 } = {}) {
  let value = initial
  let target = initial
  let velocity = 0
  const maxStep = 1 / 120

  return {
    get() {
      return value
    },
    /** 设置弹簧目标值（对应 Motion 中 motionValue.set） */
    set(next) {
      target = next
    },
    /** 无动画地把当前值与目标值对齐 */
    jump(next) {
      value = target = next
      velocity = 0
    },
    /** 推进 dt 秒，返回当前值 */
    step(dt) {
      if (!(dt > 0)) return value
      const steps = Math.min(8, Math.max(1, Math.ceil(dt / maxStep)))
      const h = dt / steps
      for (let i = 0; i < steps; i++) {
        const acceleration = (-stiffness * (value - target) - damping * velocity) / mass
        velocity += acceleration * h
        value += velocity * h
      }
      return value
    },
  }
}
