/**
 * 数字人（Live2D）形象配置。
 *
 * 这里只放「有哪些形象、从哪加载、怎么摆」这类静态事实；
 * 真正的 PIXI 初始化与口型驱动留在 ppt/index.vue，配置和渲染分开。
 */

/**
 * 社区「DeepSeek 鲸鱼娘」模型（c_0120）。
 *
 * 注意：DeepSeek 官方从未发布过 Live2D 模型，也没有官方拟人形象，
 * 官方视觉标识只有一枚抽象蓝鲸 logo。这里用的是社区二创角色，
 * 出自 https://github.com/Andersen216/dsh-whale-girl-live2d ，
 * 美术素材许可 CC BY-NC-SA 4.0（署名 · 非商业 · 相同方式共享），
 * 版权链：上善无形（角色原作）/ ZipZipPipe（DeepSeek 元素二设）/ 氵六青（建模与动画）。
 * 仅可用于毕设、课堂演示这类非商业场景，且必须保留署名。
 */
const DEEPSEEK_WHALE_REMOTE = 'https://cdn.jsdelivr.net/gh/Andersen216/dsh-whale-girl-live2d@main/assets/model/c_0120.model3.json'

/**
 * 形象登记表。
 *
 * sources 是**按顺序尝试**的地址列表：先本地 public 目录，坏了再走 CDN。
 * 本地优先的理由是放映现场常常断网 / 走校园网，远程 CDN 只作兜底。
 *
 * framing 是取景参数，单位是 canvas 的 CSS 像素：
 * - zoom：在「整只塞进画布」的基础上再放大多少倍，>1 会裁掉四周
 * - offsetX / offsetY：摆好后再平移，用来把主体推到视觉重心
 */
export const AVATAR_MODELS = {
  DeepSeekWhale: {
    label: 'DeepSeek 鲸鱼娘',
    // deepseek = 真的 DeepSeek 二创形象；sample = Live2D 官方示例模型，不是 DeepSeek 形象
    kind: 'deepseek',
    // 书桌场景横构图，塞进右侧 400×800 的竖条后主体偏小，稍微推近一点
    framing: { zoom: 1.08, offsetX: 0, offsetY: 10 },
    sources: [
      '/live2d/deepseek-whale/c_0120.model3.json',
      DEEPSEEK_WHALE_REMOTE,
    ],
  },
  /**
   * 第二个 DeepSeek 鲸鱼娘模型，作者 @Mao绒绒家园。
   *
   * 与上面 c_0120 是**两个不同的模型**（moc3 1.18MB vs 2.1MB，无动作、15 个表情）。
   * 作者以「免费公皮」公开：非官方二创、非商业、禁止盗用与出售。
   * 只配了本地地址 —— 它带 8192 贴图（约 30MB），走 CDN 会拖慢整个切换循环，
   * 没下载就直接跳过，比卡住等远程强。
   */
  DeepSeekDaFeiyu: {
    label: 'DeepSeek 大肥鱼',
    kind: 'deepseek',
    framing: { zoom: 1.05, offsetX: 0, offsetY: 0 },
    sources: ['/live2d/deepseek-da-feiyu/大肥鱼.model3.json'],
  },
  Haru: {
    label: 'Haru（示例）',
    kind: 'sample',
    framing: { zoom: 1, offsetX: 0, offsetY: 0 },
    sources: ['/live2d/Haru/Haru.model3.json'],
  },
  Hiyori: { label: 'Hiyori（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Hiyori/Hiyori.model3.json'] },
  Mao: { label: 'Mao（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Mao/Mao.model3.json'] },
  Mark: { label: 'Mark（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Mark/Mark.model3.json'] },
  Natori: { label: 'Natori（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Natori/Natori.model3.json'] },
  Rice: { label: 'Rice（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Rice/Rice.model3.json'] },
  Wanko: { label: 'Wanko（示例）', kind: 'sample', framing: { zoom: 1, offsetX: 0, offsetY: 0 }, sources: ['/live2d/Wanko/Wanko.model3.json'] },
}

/** 切换顺序：直接沿用登记表的键顺序，改表即改顺序 */
export const AVATAR_MODEL_IDS = Object.keys(AVATAR_MODELS)

/** 放映页默认形象：Wanko（示例）；偏好为空时使用 */
export const DEFAULT_AVATAR_MODEL = 'Wanko'

/**
 * 鲸鱼娘加载不出来时的兜底形象。
 *
 * 本地没跑过 scripts/fetch-deepseek-live2d.mjs、CDN 又被墙时，
 * 与其让右侧面板空着，不如退回 Cubism 官方示例模型 Haru —— 这个一定在仓库里。
 */
export const FALLBACK_AVATAR_MODEL = 'Haru'

/** 按 id 取配置，取不到就给默认 */
export const getAvatarModelConfig = (id) =>
  AVATAR_MODELS[id] ?? AVATAR_MODELS[DEFAULT_AVATAR_MODEL]

/**
 * 依次尝试一个形象的候选地址，返回第一个能真正加载的 Live2DModel。
 *
 * @param {(url: string) => Promise<import('pixi-live2d-display/cubism4').Live2DModel>} loadOf
 *        加载器，由调用方注入，避免这里反向依赖 pixi
 * @param {string} id 形象 id
 * @returns {Promise<{ model: any, url: string, id: string }>} 成功加载的模型、真实地址与所属形象 id
 */
export const loadAvatarModel = async (loadOf, id) => {
  const cfg = getAvatarModelConfig(id)
  let lastError = null

  for (const url of cfg.sources) {
    try {
      const model = await loadOf(url)
      return { model, url, id }
    }
    catch (e) {
      lastError = e
      console.warn(`[数字人] 加载失败，试下一个地址：${url}`, e)
    }
  }

  throw lastError ?? new Error(`形象 ${id} 没有可用的模型地址`)
}

/**
 * 嘴部开合参数名候选。不同模型的命名不统一，必须先探测再写。
 */
const MOUTH_PARAM_CANDIDATES = ['ParamMouthOpenY', 'ParamMouthOpen', 'ParamMouthY']

/**
 * 找出这个模型真正的「嘴张开」参数 ID。
 *
 * 各家的命名差得很远，按可信度依次试三条路：
 *
 * 1. 模型自己在 model3.json 的 LipSync 组里声明的 ID —— 作者说了算。
 *    Mao 声明的是 `ParamA`，Wanko 是 Cubism 2 风格的 `PARAM_MOUTH_OPEN_Y`，
 *    这两个都不是标准驼峰名，只看命名规则永远匹配不到。
 * 2. Cubism 3/4 的标准名。
 * 3. 兜底扫描：把下划线、空格、连字符去掉再比，让 `PARAM_MOUTH_OPEN_Y`
 *    这类老命名也能落到 `mouthopen` 上。
 *
 * 三步都拿不到就返回 null，调用方据此跳过口型 —— 例如 Rice 模型里
 * 压根没有嘴部参数，硬写只会是空转。
 *
 * @param {any} internalModel Cubism4 的 internalModel（可能为 undefined）
 * @returns {string | null} 参数 ID，找不到返回 null
 */
export const resolveMouthParamId = (internalModel) => {
  const coreModel = internalModel?.coreModel
  if (!coreModel) return null

  const hasParam = (id) => {
    try {
      return (coreModel.getParameterIndex?.(id) ?? -1) >= 0
    }
    catch {
      // coreModel 是 Cubism 内部对象，版本差异可能没有这个方法，当作不存在
      return false
    }
  }

  const declared = internalModel.settings?.groups?.find((group) => group.Name === 'LipSync')?.Ids ?? []
  for (const id of declared) {
    if (hasParam(id)) return id
  }

  for (const id of MOUTH_PARAM_CANDIDATES) {
    if (hasParam(id)) return id
  }

  try {
    const count = coreModel.getParameterCount?.() ?? 0
    for (let i = 0; i < count; i++) {
      const id = coreModel.getParameterId?.(i)
      if (typeof id === 'string' && /mouthopen/i.test(id.replace(/[_\s-]/g, ''))) return id
    }
  }
  catch {
    // 同上：探测失败就当这个模型没有嘴部参数，退回不动嘴
  }

  return null
}

/**
 * 把模型摆进画布。
 *
 * 先按「整只塞得下」算一个基准比例，再乘 zoom。用 internalModel 的原始宽高算，
 * 所以换任何模型都不需要再手调 scale —— 原来写死的 scale.set(0.2) 只对 Haru 成立。
 *
 * @param {any} model Live2DModel
 * @param {{ width: number, height: number }} stage 画布的 CSS 尺寸
 * @param {{ zoom?: number, offsetX?: number, offsetY?: number }} framing 取景参数
 */
export const applyFraming = (model, stage, framing = {}) => {
  const { width = 0, height = 0 } = model.internalModel ?? {}
  if (!width || !height || !stage.width || !stage.height) return

  const { zoom = 1, offsetX = 0, offsetY = 0 } = framing
  const base = Math.min(stage.width / width, stage.height / height)
  const scale = base * zoom

  model.scale.set(scale)
  // Live2DModel 的坐标原点是模型左上角，不是中心
  model.position.set(
    (stage.width - width * scale) / 2 + offsetX,
    (stage.height - height * scale) / 2 + offsetY,
  )
}
