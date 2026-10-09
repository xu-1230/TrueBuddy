<template>
  <div class="dh-avatar">
    <canvas ref="canvasRef" class="dh-avatar__canvas" />
    <p v-if="loadError" class="dh-avatar__error">{{ loadError }}</p>
    <span class="dh-avatar__badge" :class="{ 'is-speaking': level > 0.05 }">数字人</span>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as PIXI from 'pixi.js'
import { Live2DModel } from 'pixi-live2d-display/cubism4'

/**
 * 数字人形象：Live2D（Cubism4）+ 响度驱动口型。
 *
 * 口型只有一个自由度（`ParamMouthOpenY`），值由父级从远端音频轨算好后传进来
 * （见 `utils/agentAudio.js`）——本组件不碰 WebRTC，也不碰 AudioContext。
 */
const props = defineProps({
  /** 口型开合度 0–1（同时用于角标的「正在说话」态） */
  level: { type: Number, default: 0 },
  /** 模型地址；与放映页用同一份 Haru */
  modelUrl: { type: String, default: '/live2d/Haru/Haru.model3.json' },
  width: { type: Number, default: 150 },
  height: { type: Number, default: 220 },
})

// pixi-live2d-display 依赖全局 PIXI 注册 Ticker，必须在创建模型之前赋值
window.PIXI = PIXI

const canvasRef = ref(null)
const loadError = ref('')
let app = null
let model = null
let destroyed = false

function writeMouth(level) {
  const coreModel = model?.internalModel?.coreModel
  // Cubism 4 才有 setParameterValueById；缺了就静默不动，不能让渲染循环抛错
  coreModel?.setParameterValueById?.('ParamMouthOpenY', Math.max(0, Math.min(1, level)))
}

onBeforeUnmount(() => {
  destroyed = true
  app?.ticker?.stop()
  if (model) {
    model.removeAllListeners()
    model.destroy({ children: true, texture: true, baseTexture: true })
    model = null
  }
  if (app) {
    // removeView 传 false：canvas 由 Vue 托管
    app.destroy(false, { children: true, texture: true, baseTexture: true })
    app = null
  }
})

watch(
  () => props.level,
  (level) => writeMouth(level),
)

async function init() {
  if (!canvasRef.value) return
  app = new PIXI.Application({
    view: canvasRef.value,
    width: props.width,
    height: props.height,
    backgroundAlpha: 0,
    resolution: window.devicePixelRatio || 1,
    autoDensity: true,
    // 保留绘制缓冲：否则 WebGL 画布在合成后被清空，验收时无法用像素判断「到底画没画」
    preserveDrawingBuffer: true,
  })
  try {
    const loaded = await Live2DModel.from(props.modelUrl, { autoInteract: false })
    // 加载期间组件可能已卸载：丢弃模型，不往已销毁的 app 上挂
    if (destroyed || !app) {
      loaded.destroy({ children: true, texture: true, baseTexture: true })
      return
    }
    model = loaded
    // 按模型原始尺寸等比缩进画框：直接给 from() 传 width/height 不生效，
    // 而用 model.width（已含缩放）反算会把形象推到画框外
    const naturalWidth = model.internalModel?.width || model.width || props.width
    const naturalHeight = model.internalModel?.height || model.height || props.height
    const scale = (Math.min(props.width / naturalWidth, props.height / naturalHeight) || 1) * 0.96
    model.scale.set(scale)
    model.x = (props.width - naturalWidth * scale) / 2
    // 底边对齐：形象是「站着的人」，居中会让脚被切掉
    model.y = props.height - naturalHeight * scale
    app.stage.addChild(model)
    // 每帧在模型更新之后再写口型，否则会被动作覆盖
    app.ticker.add(() => writeMouth(props.level))
  } catch (error) {
    console.error('[DigitalHumanAvatar][模型加载失败]', error)
    loadError.value = '数字人形象加载失败（模型或 Cubism 核心脚本不可用）'
  }
}

// canvas 由 Vue 托管，setup 阶段还拿不到它——必须等挂载后再建渲染器
onMounted(init)
</script>

<style lang="css" scoped>
.dh-avatar {
  position: relative;
  width: v-bind('props.width + "px"');
  height: v-bind('props.height + "px"');
  border-radius: var(--tb-radius-md, 12px);
  background: color-mix(in srgb, var(--tb-bg-surface) 82%, transparent);
  border: 1px solid var(--tb-border);
  overflow: hidden;
}

.dh-avatar__canvas {
  display: block;
}

.dh-avatar__error {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
  margin: 0;
  font-size: 12px;
  text-align: center;
  color: var(--tb-text-secondary);
}

.dh-avatar__badge {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 999px;
  color: var(--tb-text-primary);
  background: color-mix(in srgb, var(--tb-bg-surface) 70%, transparent);
  transition: opacity 0.2s ease;
}

.dh-avatar__badge.is-speaking {
  background: var(--tb-accent-soft, color-mix(in srgb, var(--tb-primary) 18%, transparent));
}
</style>
