import { computed, onBeforeUnmount, ref } from 'vue'

/**
 * 邮箱验证码的发送冷却。
 *
 * 只做一件事：把「点一次就 60 秒内不能再点」这套计时与清理收在一处，
 * 供 LoginForm 与 ForgetPasswordForm 共用；卸载时必须清掉 interval。
 * 真正的频控与重发限制属于后端职责，这里不冒充。
 */
export function useCodeCooldown(seconds = 60) {
  const left = ref(0)
  let timer = 0

  const running = computed(() => left.value > 0)

  function stop() {
    clearInterval(timer)
    timer = 0
  }

  /** 开始冷却；已在冷却中则返回 false，调用方据此决定要不要真的发送 */
  function start() {
    if (running.value) return false
    left.value = seconds
    timer = setInterval(() => {
      left.value -= 1
      if (left.value <= 0) stop()
    }, 1000)
    return true
  }

  onBeforeUnmount(stop)

  return { left, running, start, stop }
}
