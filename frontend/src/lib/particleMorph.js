/**
 * particleMorph —— 点阵粒子形变（Canvas 2D，零依赖）
 *
 * 复刻 karot.jiejoe.com 首页 welcome_canvas 的算法：
 *   1. 把两张形状（图片或文字）等比居中画进与画布同尺寸的离屏 canvas，
 *      按 spacing（默认 5 设备像素）扫一遍像素取点，归一化成相对坐标 —— 即「起始 / 目标」两组粒子；
 *   2. 每颗粒子朝目标点缓动：x += (targetX - x) * speed * f，
 *      f = clamp(frame * ramp - delay, 0, 1)，delay 每颗随机 → 粒子「分批醒过来」而非整体位移；
 *   3. 鼠标划得越快排斥半径越大（速度 × min(innerW, innerH) / radiusScale），
 *      100ms 不动即归零；被推开的是「目标点」，粒子随后再追过去。
 *
 * 相对原实现的最小改造（其余逐项对齐）：
 *   · 事件绑定从 init 中拆出，构造时只做一次 —— 便于在同一块画布上反复切换形状做循环形变；
 *   · init 更名为 setShapes 并允许重复调用；新增 setColor / autoStart / fit 三个开关；
 *   · 同色粒子合并成一条 path 再一次 fill（视觉等价，fill 调用从 N 次降到 1 次），
 *     让大屏高 DPI 下数千颗粒子仍能稳定 60fps。
 */

export const PARTICLE_DEFAULTS = {
  // spacing / pointRadius / pushStrength 一律以 CSS 像素给出，内部按 dpr 换算成设备像素：
  // 这样同一份参数在 DPR 1 与 DPR 2 的屏幕上点距、点径、推力完全一致
  // （原实现直接用设备像素，同一份参数在 2 倍屏上点距与点径都只有一半）。
  spacing: 4, // 采样间隔（CSS 像素）
  speed: 0.05, // 每帧朝目标靠近的比例
  color: '#3d7ab5', // 粒子颜色
  pointRadius: 1, // 点的半径（CSS 像素）
  // 图片形状的缩放：先按「contain」等比铺满画布，再乘该系数。
  // >1 会裁掉素材自身的外留白（素材通常留白较多，放大后主体更饱满）；
  // 文字形状自带 86% 排版宽度，不参与该系数。
  fit: 1,
  alphaCut: 128, // alpha 大于它才算形状
  darkCut: 16, // 平均亮度低于它按背景处理（泛洪失效时的兜底）
  bgTolerance: 28, // 与背景色的色距阈值：泛洪时用来判断「还算背景」
  maxParticles: 9000, // 粒子上限，超出自动加大采样间隔
  pushStrength: 200, // 鼠标推开的最大距离（CSS 像素）
  idleClear: 100, // 鼠标静止多久后排斥半径归零（毫秒）
  radiusScale: 8, // 排斥半径 = 速度 * min(innerW, innerH) / 这个系数
  ramp: 0.02, // 每帧启动进度：f = frame * ramp - delay
  startDelay: 900, // 首帧静态画面之后，多久开始形变（毫秒）
  autoStart: true, // false 时只画一帧静态形状，不做形变（「减少动效」用）
  maxDpr: 2, // devicePixelRatio 上限
}

const TAU = Math.PI * 2

/** 把一个文字形状按容器尺寸自适应地画到离屏画布上 */
function drawText(ctx, text, W, H, fontFamily, weight = 800) {
  ctx.clearRect(0, 0, W, H)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = '#ffffff'

  const target = W * 0.86
  let size = H * 0.5
  ctx.font = `${weight} ${size}px ${fontFamily}`
  const measured = ctx.measureText(text).width || 1
  size *= target / measured
  ctx.font = `${weight} ${size}px ${fontFamily}`
  ctx.fillText(text, W / 2, H / 2)
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('图片加载失败：' + src))
    img.src = src
  })
}

/**
 * 估计背景色：看四角与四边中点。
 *  - 都透明 → null（透明底图按 alpha 判定即可）
 *  - 颜色彼此接近 → 认定纯色背景，返回平均色
 *  - 颜色差别大 → null（复杂背景照片，不作处理）
 */
function estimateBackground(data, W, H) {
  const probes = []
  const push = (x, y) => {
    const i = (y * W + x) * 4
    probes.push([data[i], data[i + 1], data[i + 2], data[i + 3]])
  }
  push(1, 1)
  push(W - 2, 1)
  push(1, H - 2)
  push(W - 2, H - 2)
  push(W >> 1, 1)
  push(W >> 1, H - 2)
  push(1, H >> 1)
  push(W - 2, H >> 1)

  if (probes.every((p) => p[3] < 32)) return null

  const avg = [0, 0, 0]
  let n = 0
  for (const p of probes) {
    if (p[3] < 32) continue
    avg[0] += p[0]
    avg[1] += p[1]
    avg[2] += p[2]
    n++
  }
  if (!n) return null
  avg[0] /= n
  avg[1] /= n
  avg[2] /= n

  let far = 0
  for (const p of probes) {
    if (p[3] < 32) continue
    far = Math.max(far, Math.hypot(p[0] - avg[0], p[1] - avg[1], p[2] - avg[2]))
  }
  return far > 70 ? null : avg
}

/**
 * 从画布四边向内泛洪，只标记「与边缘连通的背景像素」。
 * 这样白底插图里被轮廓包住的白色（书页、高光等）不会被误删 —— 单纯按颜色阈值做不到。
 */
function floodBackground(data, W, H, bg, tolerance) {
  const total = W * H
  const isBg = new Uint8Array(total)
  const stack = new Int32Array(total)
  let top = 0
  const tol2 = tolerance * tolerance

  const isBackground = (idx) => {
    const i = idx * 4
    if (data[i + 3] < 32) return true // 透明当然算背景
    const dr = data[i] - bg[0]
    const dg = data[i + 1] - bg[1]
    const db = data[i + 2] - bg[2]
    return dr * dr + dg * dg + db * db <= tol2
  }

  const seed = (idx) => {
    if (isBg[idx] || !isBackground(idx)) return
    isBg[idx] = 1
    stack[top++] = idx
  }

  for (let x = 0; x < W; x++) {
    seed(x)
    seed((H - 1) * W + x)
  }
  for (let y = 0; y < H; y++) {
    seed(y * W)
    seed(y * W + W - 1)
  }

  while (top > 0) {
    const idx = stack[--top]
    const x = idx % W
    const y = (idx - x) / W
    if (x > 0) seed(idx - 1)
    if (x < W - 1) seed(idx + 1)
    if (y > 0) seed(idx - W)
    if (y < H - 1) seed(idx + W)
  }
  return isBg
}

export class ParticleMorph {
  /**
   * @param {HTMLElement} container 画布容器（负责尺寸，通常是正方形）
   * @param {object} options 见 PARTICLE_DEFAULTS
   */
  constructor(container, options = {}) {
    this.container = container
    this.cfg = { ...PARTICLE_DEFAULTS, ...options }

    this.canvas = document.createElement('canvas')
    this.ctx = this.canvas.getContext('2d')
    this.canvas.style.display = 'block'
    this.canvas.style.width = '100%'
    this.canvas.style.height = '100%'
    container.appendChild(this.canvas)

    this.from = [] // 起始形状
    this.to = [] // 目标形状
    this.now = [] // 当前粒子（带随机 delay）
    this.frame = 0
    this.running = false
    this.destroyed = false
    this.mouse = { x: 0, y: 0, r: 0, hovering: true, lastTime: null, timer: null }

    this.resize()
    this.onResize = () => this.resize()
    window.addEventListener('resize', this.onResize)
    // 事件只绑定一次：setShapes 会被反复调用（循环形变），不能每次都挂一遍监听
    this.bindEvents()
  }

  /* ---------------- 尺寸 ---------------- */
  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, this.cfg.maxDpr)
    this.dpr = dpr
    this.width = Math.max(1, Math.round(this.container.offsetWidth * dpr))
    this.height = Math.max(1, Math.round(this.container.offsetHeight * dpr))
    this.canvas.width = this.width
    this.canvas.height = this.height
    // 粒子存的是归一化坐标，尺寸变了重绘一帧即可
    if (this.now.length) this.draw()
  }

  /* ---------------- 形状 → 粒子 ---------------- */
  /**
   * 按给定间隔扫一遍像素，取不透明、非背景、非纯黑的点（归一化坐标）。
   * step 允许是小数：按浮点步长推进、取样时再取整到像素。
   * 若把 step 量化成整数设备像素，2.6 与 3.2 在 DPR 1 上都会变成 3，
   * 采样密度就调不动了 —— 这也是「点阵出现空洞」的根因之一。
   */
  samplePoints(data, step, isBg) {
    const W = this.width
    const H = this.height
    const { alphaCut, darkCut } = this.cfg
    const list = []
    for (let y = 0; y < H; y += step) {
      const iy = Math.round(y)
      if (iy >= H) break
      for (let x = 0; x < W; x += step) {
        const ix = Math.round(x)
        if (ix >= W) break
        const idx = iy * W + ix
        const i = idx * 4
        if (data[i + 3] <= alphaCut) continue
        if (isBg && isBg[idx]) continue // 与边缘连通的背景，剔掉
        if ((data[i] + data[i + 1] + data[i + 2]) / 3 <= darkCut) continue
        list.push({ x: (ix - W / 2) / W, y: (iy - H / 2) / H })
      }
    }
    return list
  }

  /**
   * @param {string|{text:string,font?:string,weight?:number}} source 图片路径或文字形状
   * @param {{target?:number}} opts target = 期望粒子数（用来和另一个形状对齐密度）
   */
  async buildParticles(source, opts = {}) {
    const W = this.width
    const H = this.height
    const off = document.createElement('canvas')
    off.width = W
    off.height = H
    const ctx = off.getContext('2d', { willReadFrequently: true })

    if (source && typeof source === 'object' && source.text) {
      drawText(ctx, source.text, W, H, source.font, source.weight)
    } else {
      const img = await loadImage(typeof source === 'string' ? source : source.src)
      const scale = Math.min(W / img.width, H / img.height) * (this.cfg.fit || 1)
      const dw = img.width * scale
      const dh = img.height * scale
      ctx.drawImage(img, (W - dw) / 2, (H - dh) / 2, dw, dh)
    }

    const data = ctx.getImageData(0, 0, W, H).data
    const { spacing, maxParticles, bgTolerance } = this.cfg
    const target = opts.target || 0

    // 纯色背景（黑底、白底都算）：从边缘泛洪剔掉；透明底图跳过
    const bg = estimateBackground(data, W, H)
    const isBg = bg ? floodBackground(data, W, H, bg, bgTolerance) : null

    // spacing 以 CSS 像素给出 → 换算成设备像素再扫像素（保留小数，见 samplePoints 注释）
    const baseStep = Math.max(1.5, spacing * this.dpr)
    let step = baseStep
    let list = this.samplePoints(data, step, isBg)

    // 只加密、不稀释：线稿类素材点数天然偏少，加密才能把目标形状画完整
    if (target > 0) {
      let guard = 0
      while (list.length < target && step > 1.5 && guard++ < 24) {
        list = this.samplePoints(data, --step, isBg)
      }
    }

    // 上限保护：大屏 / 高密度素材上别把点数顶爆
    while (list.length > maxParticles && step < baseStep * 8) {
      list = this.samplePoints(data, ++step, isBg)
    }
    return list
  }

  /* ---------------- 装配形状 ---------------- */
  /**
   * 装配「起始 → 目标」两组粒子并（按需）开始形变。
   * 可重复调用：每次调用都会重采样并重置形变进度，用于循环换形状。
   */
  async setShapes(shapes) {
    let from = await this.buildParticles(shapes[0])
    let to = await this.buildParticles(shapes[1])
    if (from.length < to.length) {
      from = await this.buildParticles(shapes[0], { target: to.length })
    }
    if (this.destroyed) return this

    this.from = from
    this.to = to
    this.now = from.map((p) => ({ x: p.x, y: p.y, delay: Math.random() }))

    this.draw() // 先静态显示起始形状（与原站解锁前那一帧一致）
    clearTimeout(this.startTimer)
    if (this.cfg.autoStart) {
      this.startTimer = setTimeout(() => this.start(), this.cfg.startDelay)
    }
    return this
  }

  setColor(color) {
    if (color) this.cfg.color = color
    if (this.now.length) this.draw()
  }

  /* ---------------- 一帧 ---------------- */
  step() {
    this.frame++
    const { ctx, width: W, height: H, cfg } = this
    const now = this.now
    const to = this.to

    ctx.clearRect(0, 0, W, H)
    if (!now.length || !to.length) return
    ctx.fillStyle = cfg.color

    const toLen = to.length
    const pushing = this.mouse.hovering && this.mouse.r > 0.001
    const mx = this.mouse.x
    const my = this.mouse.y
    const mr = this.mouse.r
    const halfW = W / 2
    const halfH = H / 2
    // CSS 像素 → 设备像素
    const r = cfg.pointRadius * this.dpr
    const pushStrength = cfg.pushStrength * this.dpr

    // 同色粒子合并成一条 path，最后一次性 fill（视觉等价，fill 调用数从 N 降到 1）
    ctx.beginPath()

    for (let k = 0; k < now.length; k++) {
      const p = now[k]
      // 按比例映射到目标点：now.length ≥ to.length 时每个目标点都被覆盖
      const n = to[Math.min(toLen - 1, ((k * toLen) / now.length) | 0)]

      let tx = n.x * W + halfW
      let ty = n.y * H + halfH
      const px = p.x * W + halfW
      const py = p.y * H + halfH

      if (pushing) {
        const dx = px - mx
        const dy = py - my
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < mr) {
          const push = (1 - dist / mr) * Math.random() * pushStrength
          const ang = Math.atan2(dy, dx)
          tx += Math.cos(ang) * push
          ty += Math.sin(ang) * push
        }
      }

      // 分批启动：每颗粒子 delay 不同，f 从 0 涨到 1 之后才开始全力追目标
      const f = Math.min(1, Math.max(0, this.frame * cfg.ramp - p.delay))
      p.x += ((tx - halfW) / W - p.x) * cfg.speed * f
      p.y += ((ty - halfH) / H - p.y) * cfg.speed * f

      const cx = p.x * W + halfW
      const cy = p.y * H + halfH
      // moveTo 后再 arc：避免同一 path 内的圆之间被连线
      ctx.moveTo(cx + r, cy)
      ctx.arc(cx, cy, r, 0, TAU)
    }

    ctx.fill()
  }

  draw() {
    const f = this.frame
    this.frame = Math.max(1, f)
    this.step()
  }

  start() {
    if (this.running || this.destroyed || !this.now.length) return
    this.running = true
    const tick = () => {
      if (!this.running || this.destroyed) return
      this.step()
      requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }

  pause() {
    this.running = false
  }

  /* ---------------- 鼠标 / 触摸 ---------------- */
  bindEvents() {
    const track = (clientX, clientY) => {
      const rect = this.canvas.getBoundingClientRect()
      const x = (clientX - rect.left) * this.dpr
      const y = (clientY - rect.top) * this.dpr
      const t = Date.now()

      // 排斥半径由「移动速度」换算：划得越快，推开范围越大
      if (this.mouse.lastTime !== null) {
        const dt = t - this.mouse.lastTime
        const dx = x - this.mouse.x
        const dy = y - this.mouse.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        this.mouse.r =
          (dt > 0 ? dist / dt : 0) *
          (Math.min(window.innerWidth, window.innerHeight) / this.cfg.radiusScale) *
          this.dpr
      }

      clearTimeout(this.mouse.timer)
      this.mouse.timer = setTimeout(() => {
        this.mouse.r = 0
      }, this.cfg.idleClear)

      this.mouse.x = x
      this.mouse.y = y
      this.mouse.lastTime = t
    }

    this.handlers = {
      mousemove: (e) => track(e.clientX, e.clientY),
      touchmove: (e) => track(e.touches[0].clientX, e.touches[0].clientY),
      enter: () => {
        this.mouse.hovering = true
      },
      leave: () => {
        this.mouse.hovering = false
        this.mouse.r = 0
      },
    }

    const c = this.canvas
    c.addEventListener('mousemove', this.handlers.mousemove)
    c.addEventListener('touchmove', this.handlers.touchmove, { passive: true })
    c.addEventListener('mouseenter', this.handlers.enter)
    c.addEventListener('mouseleave', this.handlers.leave)
    c.addEventListener('touchstart', this.handlers.enter, { passive: true })
    c.addEventListener('touchend', this.handlers.leave)
  }

  destroy() {
    this.destroyed = true
    this.pause()
    clearTimeout(this.startTimer)
    clearTimeout(this.mouse.timer)
    window.removeEventListener('resize', this.onResize)

    const c = this.canvas
    if (this.handlers) {
      c.removeEventListener('mousemove', this.handlers.mousemove)
      c.removeEventListener('touchmove', this.handlers.touchmove)
      c.removeEventListener('mouseenter', this.handlers.enter)
      c.removeEventListener('mouseleave', this.handlers.leave)
      c.removeEventListener('touchstart', this.handlers.enter)
      c.removeEventListener('touchend', this.handlers.leave)
    }
    c.remove()
    this.now = []
    this.from = []
    this.to = []
  }
}
