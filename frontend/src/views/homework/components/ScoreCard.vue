<template>
  <article
    class="sheet"
    :class="`sheet--${tone}`"
    :style="{ '--ratio': ratio }"
    :aria-label="cardLabel"
  >
    <div class="sheet__inner">
      <!-- ============ 卡头：文件身份 + 翻阅 ============ -->
      <header class="sheet__head">
        <div class="head__brand">
          <span class="head__mark" aria-hidden="true"></span>
          <span class="head__kicker">GRADE REPORT</span>
          <span
            v-if="workflowLabel"
            class="head__workflow"
            :title="workflowLabel"
          >{{ workflowLabel }}</span>
        </div>

        <p class="head__file" :title="record.fileName">
          <el-icon class="head__file-icon"><Document /></el-icon>
          <span class="head__file-name">{{ record.fileName }}</span>
          <!-- 一学生一卡时「文件名」就是学生名，此时不重复挂一个学生标签 -->
          <span
            v-if="record.studentName && record.studentName !== record.fileName"
            class="head__tag"
          >{{ record.studentName }}</span>
        </p>

        <a
          v-if="record.fileUrl"
          class="head__open"
          :href="record.fileUrl"
          target="_blank"
          rel="noopener"
          :aria-label="`在新标签页打开 ${record.fileName}`"
        >
          原件
          <el-icon><TopRight /></el-icon>
        </a>

        <div class="head__nav">
          <button
            type="button"
            class="head__arrow"
            aria-label="上一份成绩单"
            :disabled="index <= 1"
            @click="emit('prev')"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <span class="head__pager">
            <b>{{ pad(index) }}</b>
            <span class="head__pager-sep" aria-hidden="true">/</span>
            <span class="head__pager-total">{{ pad(total) }}</span>
          </span>
          <button
            type="button"
            class="head__arrow"
            aria-label="下一份成绩单"
            :disabled="index >= total"
            @click="emit('next')"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </header>

      <!-- ============ 抬头：学生身份 + 得分刻度 ============ -->
      <section class="hero">
        <div class="hero__who">
          <p class="eyebrow" aria-hidden="true">
            STUDENT
            <span class="eyebrow__sep">/</span>
            学生
          </p>
          <h2 class="hero__name" :title="record.studentName || record.fileName">
            {{ record.studentName || '未署名' }}
          </h2>

          <p class="hero__facts">
            <span class="fact"><b>{{ submittedCount }}</b> 份提交</span>
            <span class="fact__dot" aria-hidden="true"></span>
            <span class="fact"><b>{{ readCount }}</b> 份已读</span>
            <template v-if="sizeText">
              <span class="fact__dot" aria-hidden="true"></span>
              <span class="fact">{{ sizeText }}</span>
            </template>
          </p>

          <p v-if="contextLine" class="hero__context" :title="contextLine">
            {{ contextLine }}
          </p>
          <p v-if="record.status !== 1" class="hero__alert">
            批改未完成<span v-if="record.errorMsg"> · {{ record.errorMsg }}</span>
          </p>
        </div>

        <div
          class="hero__score"
          role="group"
          :aria-label="`得分 ${scoreAriaText}，满分 ${scoreMaxText} 分`"
        >
          <p class="eyebrow eyebrow--end" aria-hidden="true">
            SCORE
            <span class="eyebrow__sep">/</span>
            得分
          </p>

          <div class="scoreline">
            <span
              class="scoreline__num"
              :class="{ 'scoreline__num--pending': !hasScore }"
              aria-hidden="true"
            >
              {{ scoreText }}
            </span>
            <span class="scoreline__den" aria-hidden="true">/{{ scoreMaxText }}</span>
            <span class="seal" :class="{ 'seal--pending': !hasScore }">
              {{ record.grade || (hasScore ? '已评' : '待批改') }}
            </span>
          </div>
        </div>

        <!-- 刻度横贯整张卡：得分点与右上角的大数字对齐；作为得分的视觉重复，读屏交给上方 role=group -->
        <div v-if="hasScore" class="meter" aria-hidden="true">
          <div class="meter__track">
            <span class="meter__fill"></span>
            <span
              class="meter__tick meter__tick--pass"
              :style="{ left: `${passRatio * 100}%` }"
            ></span>
            <span
              class="meter__tick meter__tick--excellent"
              :style="{ left: `${excellentRatio * 100}%` }"
            ></span>
            <span class="meter__dot"></span>
          </div>
          <div class="meter__scale">
            <span class="meter__scale-start">{{ startLabel }}</span>
            <span
              v-if="showPassLabel"
              class="meter__scale-pass"
              :style="{ left: `${passRatio * 100}%` }"
            >
              {{ passText }} 及格
            </span>
            <span class="meter__scale-end">{{ endLabel }}</span>
          </div>
        </div>
      </section>

      <!-- ============ 正文：材料清单 / 扣分账 / 评语 ============ -->
      <div class="sheet__body">
        <aside class="dossier" aria-labelledby="score-dossier-title">
          <header class="sec-head">
            <span class="sec-head__no">01</span>
            <h3 id="score-dossier-title" class="sec-head__title">材料清单</h3>
            <span class="sec-head__en" aria-hidden="true">DOSSIER</span>
          </header>

          <p v-if="sourceRoot" class="dossier__root" :title="sourceRoot">
            <span class="dossier__root-mark" aria-hidden="true"></span>
            <span class="dossier__root-text">{{ sourceRoot }}</span>
          </p>

          <div class="dossier__group">
            <div class="group-head">
              <span>提交文件</span>
              <b>{{ submittedCount }}</b>
            </div>
            <ol v-if="(record.files ?? []).length" class="filelist">
              <li
                v-for="(file, fileIndex) in record.files ?? []"
                :key="pathOf(file) || fileIndex"
                class="filelist__item"
              >
                <span class="filelist__no">{{ pad(fileIndex + 1) }}</span>
                <span class="filelist__body">
                  <span class="filelist__name" :title="pathOf(file)">{{ baseName(file) }}</span>
                  <span
                    v-if="dirName(file) && dirName(file) !== submittedRoot"
                    class="filelist__dir"
                    :title="pathOf(file)"
                  >
                    {{ dirName(file) }}
                  </span>
                </span>
                <span v-if="extName(file)" class="filelist__ext">{{ extName(file) }}</span>
              </li>
            </ol>
            <p v-else class="empty-note">
              未匹配到该学生提交的文件，可按姓名从作业文件夹里核对
            </p>
          </div>

          <div class="dossier__group">
            <div class="group-head">
              <span>模型已读</span>
              <b>{{ readCount }}</b>
            </div>
            <ol v-if="(record.readFiles ?? []).length" class="filelist filelist--read">
              <li
                v-for="(path, readIndex) in record.readFiles ?? []"
                :key="`${path}-${readIndex}`"
                class="filelist__item"
              >
                <span class="filelist__no">{{ pad(readIndex + 1) }}</span>
                <span class="filelist__body">
                  <span class="filelist__name" :title="pathOf(path)">{{ baseName(path) }}</span>
                  <span
                    v-if="dirName(path) && dirName(path) !== readRoot"
                    class="filelist__dir"
                    :title="pathOf(path)"
                  >
                    {{ dirName(path) }}
                  </span>
                </span>
                <span v-if="extName(path)" class="filelist__ext">{{ extName(path) }}</span>
              </li>
            </ol>
            <p v-else class="empty-note">
              {{ record.errorMsg || '本次没有记录到已读文件' }}
            </p>
            <p v-if="readCount > 0 && readCount < submittedCount" class="dossier__hint">
              已读份数少于提交份数，注意是否漏读
            </p>
          </div>
        </aside>

        <section class="verdict" aria-labelledby="score-output-title">
          <header class="sec-head">
            <span class="sec-head__no">02</span>
            <h3 id="score-output-title" class="sec-head__title">评分输出</h3>
            <span class="sec-head__en" aria-hidden="true">ASSESSMENT</span>
            <span v-if="hasScore" class="sec-head__meta">
              {{ outputRows.length }} 项 · 失 {{ lostPoints }} 分
            </span>
          </header>

          <ol v-if="outputRows.length" class="output-list">
            <li v-for="(row, rowIndex) in outputRows" :key="row.key" class="output">
              <div class="output__head">
                <span class="output__no">{{ pad(rowIndex + 1) }}</span>
                <span class="output__label">{{ row.label }}</span>
                <span class="output__type" aria-hidden="true">{{ row.typeCode }}</span>
              </div>
              <p
                v-if="row.kind === 'number'"
                class="output__number"
                :class="{ 'output__number--empty': isEmptyValue(row.value) }"
              >
                {{ numberText(row.value) }}
              </p>
              <p
                v-else-if="row.kind === 'boolean'"
                class="output__boolean"
                :class="{ 'output__boolean--empty': isEmptyValue(row.value) }"
              >
                {{ isEmptyValue(row.value) ? '未产出' : booleanText(row.value) }}
              </p>
              <ul
                v-else-if="row.kind === 'list'"
                class="output__list"
                :class="{ 'output__list--empty': isEmptyValue(row.value) }"
              >
                <li v-if="isEmptyValue(row.value)">未产出</li>
                <template v-else>
                  <li v-for="(item, itemIndex) in listItems(row.value)" :key="itemIndex">
                    {{ item }}
                  </li>
                </template>
              </ul>
              <pre
                v-else-if="row.kind === 'object'"
                class="output__json"
                :class="{ 'output__json--empty': isEmptyValue(row.value) }"
              >{{ isEmptyValue(row.value) ? '未产出' : prettyJson(row.value) }}</pre>
              <p
                v-else
                class="output__text"
                :class="{ 'output__text--empty': isEmptyValue(row.value) }"
              >
                {{ isEmptyValue(row.value) ? '未产出' : row.value }}
              </p>
            </li>
          </ol>

          <p v-else class="output-empty">
            本次未定义评分输出字段；在左侧「评分与导出设置」中添加导出列后重新批改
          </p>
        </section>
      </div>

      <!-- ============ 页脚：本次批改要求 ============ -->
      <footer class="sheet__foot">
        <span class="foot__label" aria-hidden="true">REQUIREMENT</span>
        <p class="foot__text" :title="description">
          {{ description || '本次批改未填写额外要求' }}
        </p>
        <span class="foot__mark" aria-hidden="true">TRUEBUDDY · AUTO GRADING</span>
      </footer>
    </div>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Document, TopRight } from '@element-plus/icons-vue'
import { formatFileSize, scoreRatio, scoreScaleOf, scoreTone } from '@/views/homework/utils/format'
import { customRows } from '@/views/homework/utils/custom-fields'

const props = defineProps({
  /** 一份成绩的批改结果 */
  record: { type: Object, required: true },
  /** 本次批改要求（AI 描述说明） */
  description: { type: String, default: '' },
  /** 工作流文案 */
  workflowLabel: { type: String, default: '' },
  /** 自定义导出列定义（成绩卡右栏按定义顺序渲染） */
  fields: { type: Array, default: () => [] },
  /** 评分区间 { min, max, pass } */
  range: { type: Object, default: () => ({ min: 0, max: 100, pass: 60 }) },
  /** 当前是第几份（1 起） */
  index: { type: Number, default: 1 },
  /** 本次批改总份数 */
  total: { type: Number, default: 1 },
})

const emit = defineEmits(['prev', 'next'])

/** 两位序号：01 / 02 … 份数超过 99 时原样展示，不截断 */
const pad = (value) => String(value).padStart(2, '0')

/** 区间归一：缺省 / 非法值回到 0–100 / 60，避免旧卡片或坏数据把刻度画飞 */
const range = computed(() => {
  const min = Number(props.range?.min)
  const max = Number(props.range?.max)
  const pass = Number(props.range?.pass)
  return {
    min: Number.isFinite(min) ? min : 0,
    max: Number.isFinite(max) ? max : 100,
    pass: Number.isFinite(pass) ? pass : 60,
  }
})

/** 整数分数显示整数，带小数的分数保留一位，动画结束再落回精确值 */
const formatScore = (value) => (Number.isInteger(value) ? String(value) : value.toFixed(1))

/** 得分只接受数字；字符串 / 空值一律视为「未评分」，避免把 null 当成 0 分 */
const scoreValue = computed(() => {
  const raw = props.record.score
  return typeof raw === 'number' && Number.isFinite(raw) ? raw : null
})
const hasScore = computed(() => scoreValue.value != null)

const tone = computed(() => scoreTone(scoreValue.value, range.value))

/** 评分刻度比例：得分在 [min, max] 中的位置 */
const ratio = computed(() => scoreRatio(scoreValue.value, range.value))
const scale = computed(() => scoreScaleOf(range.value))
const passRatio = computed(() => scoreRatio(scale.value.pass, range.value))
const excellentRatio = computed(() => scoreRatio(scale.value.excellent, range.value))

/** 刻度与分母文案（区间值允许一位小数） */
const scoreMinText = computed(() => formatScore(range.value.min))
const scoreMaxText = computed(() => formatScore(range.value.max))
const passText = computed(() => formatScore(range.value.pass))

/** 及格线贴住区间两端时（60–100 / 0–100 pass=max），把「及格」并到端点标签，避免文字重叠 */
const passAtStart = computed(() => Math.abs(range.value.pass - range.value.min) < 1e-9)
const passAtEnd = computed(() => Math.abs(range.value.pass - range.value.max) < 1e-9)
const showPassLabel = computed(() => !passAtStart.value && !passAtEnd.value)
const startLabel = computed(() => (passAtStart.value ? `${scoreMinText.value} 及格` : scoreMinText.value))
const endLabel = computed(() => (passAtEnd.value ? `${scoreMaxText.value} 及格` : scoreMaxText.value))

/* ---------- 得分滚动：卡进入视野时从 0 数到实际分数 ---------- */
const displayedScore = ref(scoreValue.value)
let scoreRaf = 0

const stopScoreAnimation = () => {
  if (scoreRaf) cancelAnimationFrame(scoreRaf)
  scoreRaf = 0
}

function animateScore(target) {
  stopScoreAnimation()
  if (target == null) {
    displayedScore.value = null
    return
  }

  const reduceMotion =
    typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reduceMotion) {
    displayedScore.value = target
    return
  }

  const duration = 900
  const startAt = performance.now()
  displayedScore.value = 0
  const tick = (now) => {
    const progress = Math.min(1, (now - startAt) / duration)
    const eased = 1 - (1 - progress) ** 3
    displayedScore.value = Math.round(target * eased)
    if (progress < 1) {
      scoreRaf = requestAnimationFrame(tick)
    } else {
      displayedScore.value = target
      scoreRaf = 0
    }
  }
  scoreRaf = requestAnimationFrame(tick)
}

watch(
  () => [props.record.id, scoreValue.value],
  () => animateScore(scoreValue.value),
  { immediate: true },
)

onBeforeUnmount(stopScoreAnimation)

/** 得分文本：未评分时是一个破折号，而不是 0 */
const scoreText = computed(() => {
  if (displayedScore.value == null) return '—'
  return formatScore(displayedScore.value)
})

/** 读屏用的得分文案：取终值，不跟随数字滚动刷屏 */
const scoreAriaText = computed(() =>
  scoreValue.value == null ? '尚未评分' : `${formatScore(scoreValue.value)} 分`,
)

/** 失分 = 满分 - 得分（异常分数夹紧到 0，不出现负失分） */
const lostPoints = computed(() => {
  if (scoreValue.value == null) return 0
  return Math.max(0, Math.round((range.value.max - scoreValue.value) * 10) / 10)
})

const submittedFiles = computed(() => props.record.files ?? [])
const readFiles = computed(() => props.record.readFiles ?? [])
const submittedCount = computed(() => submittedFiles.value.length)
const readCount = computed(() => readFiles.value.length)

/** 右栏输出：按定义顺序给出 {key,label,type,static,value}，静态列已填默认值 */
const outputRows = computed(() => customRows(props.fields, props.record.custom))

const isEmptyValue = (value) => {
  if (value == null) return true
  if (Array.isArray(value)) return value.length === 0
  if (typeof value === 'object') return Object.keys(value).length === 0
  return String(value).trim() === ''
}

/** 数字列展示：可解析为有限数就按分数格式，否则保留原文本（模型偶尔写成字符串） */
const numberText = (value) => {
  if (isEmptyValue(value)) return '未产出'
  const number = Number(value)
  return Number.isFinite(number) ? formatScore(number) : String(value)
}

/** boolean 列展示：是 / 否，非标准值原样 */
const booleanText = (value) => {
  if (value === true || value === 'true') return '是'
  if (value === false || value === 'false') return '否'
  return String(value)
}

/** list 列展示：数组逐项；元素是对象时序列化；非数组标量当单项 */
const listItems = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => (
      item != null && typeof item === 'object' ? JSON.stringify(item) : String(item)
    ))
  }
  if (value == null || String(value).trim() === '') return []
  return [String(value)]
}

/** object 列展示：格式化 JSON；模型给 JSON 字符串时先解析 */
const prettyJson = (value) => {
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

const sizeText = computed(() => {
  const size = Number(props.record.size)
  return Number.isFinite(size) && size > 0 ? formatFileSize(size) : ''
})

/** 模型 / 知识库：一行小字，交代这次评分背后的上下文 */
const contextLine = computed(() => {
  const parts = []
  if (props.record.model) parts.push(`MODEL ${props.record.model}`)
  if (props.record.kbName) parts.push(`KB ${props.record.kbName}`)
  return parts.join('  ·  ')
})

/* ---------- 文件路径拆解：文件名读大、目录读小、扩展名做角标 ---------- */
const pathOf = (file) => {
  if (file == null) return ''
  if (typeof file === 'string') return file
  return String(file.path || file.name || '')
}

const baseName = (file) => {
  const path = pathOf(file)
  if (!path) return '未命名文件'
  return path.split(/[\\/]/).filter(Boolean).pop() || path
}

const dirName = (file) => {
  const path = pathOf(file)
  const base = baseName(file)
  const dir = path.slice(0, Math.max(0, path.length - base.length)).replace(/[\\/]+$/, '')
  return dir === path ? '' : dir
}

const extName = (file) => {
  const base = baseName(file)
  const dot = base.lastIndexOf('.')
  if (dot <= 0 || dot === base.length - 1) return ''
  return base.slice(dot + 1).toUpperCase().slice(0, 4)
}

/** 一组文件的最长公共目录：目录只在一组清单的组头上出现一次，清单本身只留文件名 */
const commonDirOf = (files) => {
  const dirs = files.map((file) => dirName(file)).filter(Boolean)
  if (!dirs.length) return ''

  let common = dirs[0].split(/[\\/]+/)
  for (const dir of dirs.slice(1)) {
    const parts = dir.split(/[\\/]+/)
    let same = 0
    while (same < common.length && same < parts.length && common[same] === parts[same]) {
      same += 1
    }
    common = common.slice(0, same)
    if (!common.length) break
  }
  return common.join('/')
}

const submittedRoot = computed(() => commonDirOf(submittedFiles.value))
const readRoot = computed(() => commonDirOf(readFiles.value))
/** 材料来源目录：提交清单优先，没有提交时退回已读清单 */
const sourceRoot = computed(() => submittedRoot.value || readRoot.value)

/** 卡片整体读屏文案：谁来读、多少分、第几份 */
const cardLabel = computed(() => {
  const who = props.record.studentName || props.record.fileName || '未署名'
  return `成绩单：${who}，${scoreAriaText.value}，第 ${props.index} 份，共 ${props.total} 份`
})
</script>

<style scoped>
/* ==========================================================================
   成绩单（EXAM REPORT）
   —— 一张纸的版式：卡头读数 / 超大分数与刻度 / 材料与扣分账 / 页脚要求。
   不使用卡片套卡片；分组只靠细线与留白，颜色由得分档位（--tone）统一派生。
   容器查询按「卡片自身宽高」适配，卡堆缩放与页面响应式都不需要另写断点。
   ========================================================================== */
.sheet {
  --tone: var(--tb-text-tertiary);
  --tone-line: color-mix(in oklab, var(--tone) 32%, transparent);
  --tone-wash: color-mix(in oklab, var(--tone) 8%, var(--tb-bg-surface));
  --rule: color-mix(in oklab, var(--tb-text-primary) 9%, transparent);
  --sheet-x: clamp(18px, 4.2cqw, 46px);

  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  container-type: size;
  container-name: sheet;
  isolation: isolate;
  overflow-x: hidden;
  /* 常态没有溢出；窄卡时整张纸一起滚（内层改为自动高度） */
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--tb-border-strong) transparent;
  border-radius: var(--tb-radius-xl);
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
  color: var(--tb-text-primary);
}

.sheet__inner {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
}

.sheet--excellent { --tone: var(--tb-success); }
.sheet--good { --tone: var(--tb-primary); }
.sheet--pass { --tone: var(--tb-warning); }
.sheet--weak { --tone: var(--tb-danger); }

/* 纸张颗粒：只在最上层铺一层极淡噪点，不引进图片、不改主题 */
.sheet::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 5;
  background-image: var(--tb-noise);
  opacity: 0.035;
  pointer-events: none;
}

/* ==========================================================================
   卡头
   ========================================================================== */
.sheet__head {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: clamp(10px, 1.8cqw, 20px);
  min-width: 0;
  height: clamp(44px, 7.2cqw, 58px);
  padding: 0 var(--sheet-x);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  background: linear-gradient(
    180deg,
    color-mix(in oklab, var(--tb-text-primary) 3.5%, transparent),
    transparent
  );
}

/* 顶缘的得分色刻度线：全卡唯一一条连续色带，用来远距离识别分数档位 */
.sheet__head::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(
    90deg,
    var(--tone) 0%,
    color-mix(in oklab, var(--tone) 34%, transparent) 38%,
    transparent 82%
  );
}

.head__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 0 0 auto;
}

.head__mark {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  background: var(--tone);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--tone) 14%, transparent);
}

.head__kicker {
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.45cqw, 10px);
  font-weight: 600;
  letter-spacing: 0.2em;
  color: var(--tb-text-secondary);
  white-space: nowrap;
}

.head__workflow {
  max-width: 12em;
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.3cqw, 10px);
  letter-spacing: 0.04em;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head__workflow::before {
  content: '/ ';
  color: var(--tb-border-strong);
}

.head__file {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin: 0;
  font-size: clamp(10px, 1.7cqw, 12px);
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.head__file-icon {
  flex: 0 0 auto;
  font-size: 13px;
  color: var(--tb-text-tertiary);
}

.head__file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head__tag {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: var(--tb-radius-full);
  background: var(--tone-wash);
  font-size: clamp(8px, 1.3cqw, 10px);
  font-weight: 600;
  color: var(--tone);
  white-space: nowrap;
}

.head__open {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 0;
  font-size: clamp(9px, 1.5cqw, 11px);
  color: var(--tb-text-tertiary);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: color var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out);
}

.head__open .el-icon {
  font-size: 11px;
}

.head__open:hover {
  color: var(--tone);
  border-color: var(--tone-line);
}

.head__nav {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

.head__arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: clamp(22px, 3.6cqw, 28px);
  height: clamp(22px, 3.6cqw, 28px);
  padding: 0;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xs);
  background: transparent;
  color: var(--tb-text-secondary);
  cursor: pointer;
  transition: color var(--tb-dur-2) var(--tb-ease-out),
    border-color var(--tb-dur-2) var(--tb-ease-out),
    background var(--tb-dur-2) var(--tb-ease-out);
}

.head__arrow .el-icon {
  font-size: 12px;
}

.head__arrow:hover:not(:disabled) {
  color: var(--tone);
  border-color: var(--tone-line);
  background: var(--tone-wash);
}

.head__arrow:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

.head__arrow:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

.head__pager {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  min-width: 3.4em;
  margin: 0 3px;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: clamp(9px, 1.5cqw, 11px);
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.head__pager b {
  font-family: var(--font-serif);
  font-size: 1.35em;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.head__pager-sep {
  opacity: 0.45;
}

/* ==========================================================================
   抬头
   ========================================================================== */
.hero {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: clamp(14px, 2.6cqw, 34px);
  padding: clamp(16px, 2.9cqw, 32px) var(--sheet-x) clamp(14px, 2.6cqw, 28px);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  overflow: hidden;
}

.hero__who {
  position: relative;
  z-index: 1;
  min-width: 0;
  align-self: center;
}

.eyebrow {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.45cqw, 10px);
  font-weight: 600;
  letter-spacing: 0.2em;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.eyebrow__sep {
  color: var(--tone);
  opacity: 0.8;
}

.eyebrow--end {
  justify-content: flex-end;
}

.hero__name {
  margin: clamp(6px, 1.2cqw, 12px) 0 0;
  font-family: var(--font-serif);
  font-size: clamp(1.35rem, 4.6cqw, 2.6rem);
  font-weight: 650;
  line-height: 1.1;
  letter-spacing: 0.015em;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero__facts {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin: clamp(8px, 1.5cqw, 14px) 0 0;
  font-size: clamp(10px, 1.75cqw, 12px);
  color: var(--tb-text-tertiary);
}

.fact b {
  font-family: var(--font-serif);
  font-size: 1.35em;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-primary);
}

.fact__dot {
  width: 3px;
  height: 3px;
  background: var(--tb-border-strong);
}

.hero__context {
  margin: 7px 0 0;
  font-family: var(--font-mono);
  font-size: clamp(8.5px, 1.45cqw, 10.5px);
  letter-spacing: 0.06em;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hero__alert {
  margin: 7px 0 0;
  font-size: clamp(9px, 1.5cqw, 11px);
  color: var(--tb-warning);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 得分 ---------- */
.hero__score {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: clamp(190px, 26cqw, 320px);
}

.scoreline {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-top: 2px;
  white-space: nowrap;
}

.scoreline__num {
  font-family: var(--font-serif);
  font-size: clamp(4rem, 15.2cqw, 8.5rem);
  font-weight: 700;
  line-height: 0.78;
  letter-spacing: -0.055em;
  font-variant-numeric: lining-nums tabular-nums;
  color: var(--tone);
}

.scoreline__num--pending {
  font-size: clamp(2.6rem, 7.2cqw, 4.4rem);
  line-height: 1;
  letter-spacing: 0;
  color: var(--tb-text-tertiary);
}

.scoreline__den {
  font-family: var(--font-mono);
  font-size: clamp(9px, 1.5cqw, 11px);
  letter-spacing: 0.04em;
  color: var(--tb-text-tertiary);
}

/* 印章：双线边框 + 轻微旋转，读作「这张成绩已经盖过章」 */
.seal {
  position: relative;
  align-self: center;
  margin-left: clamp(6px, 1.2cqw, 14px);
  padding: clamp(3px, 0.7cqw, 6px) clamp(7px, 1.2cqw, 12px);
  border: 2px solid color-mix(in oklab, var(--tone) 66%, transparent);
  border-radius: var(--tb-radius-xs);
  font-family: var(--font-serif);
  font-size: clamp(10px, 1.85cqw, 14px);
  font-weight: 700;
  letter-spacing: 0.12em;
  text-indent: 0.12em;
  line-height: 1.1;
  color: var(--tone);
  white-space: nowrap;
  max-width: 8em;
  overflow: hidden;
  text-overflow: ellipsis;
  transform: rotate(-7deg);
}

.seal::after {
  content: '';
  position: absolute;
  inset: 2px;
  border: 1px solid color-mix(in oklab, var(--tone) 34%, transparent);
  border-radius: 1px;
  pointer-events: none;
}

.seal--pending {
  border-style: dashed;
  opacity: 0.62;
}

/* 刻度：一条横贯全卡的细轨 + 60 / 90 两个阈值刻度 + 得分光点 */
.meter {
  grid-column: 1 / -1;
  width: 100%;
  margin-top: clamp(12px, 2.2cqw, 22px);
  padding-bottom: 14px;
}

.meter__track {
  position: relative;
  height: 2px;
  background: var(--tb-border);
}

.meter__fill {
  position: absolute;
  inset: 0 auto 0 0;
  width: 100%;
  transform-origin: left center;
  transform: scaleX(var(--ratio));
  background: linear-gradient(
    90deg,
    color-mix(in oklab, var(--tone) 45%, transparent),
    var(--tone)
  );
}

.meter__tick {
  position: absolute;
  top: -2px;
  width: 1px;
  height: 6px;
  background: var(--tb-border-strong);
}

/* 60 / 90 阈值线的 left 由得分区间计算后内联下发（见 meter__tick） */
.meter__dot {
  position: absolute;
  top: 1px;
  left: calc(var(--ratio) * 100%);
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--tone);
  transform: translate(-50%, -50%);
  box-shadow: 0 0 0 4px color-mix(in oklab, var(--tone) 16%, transparent);
}

.meter__scale {
  position: relative;
  height: 10px;
  margin-top: 5px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.06em;
  color: var(--tb-text-tertiary);
}

.meter__scale span {
  position: absolute;
  top: 0;
}

.meter__scale-start { left: 0; }
.meter__scale-pass { transform: translateX(-50%); }
.meter__scale-end { right: 0; }

/* ==========================================================================
   正文
   ========================================================================== */
.sheet__body {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.34fr);
  gap: clamp(16px, 3.1cqw, 40px);
  padding: clamp(14px, 2.5cqw, 26px) var(--sheet-x) clamp(12px, 2.2cqw, 22px);
}

.dossier,
.verdict {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--tb-border-strong) transparent;
  /* 底部渐隐：内容超过一屏时给出「还能往下读」的视觉提示 */
  -webkit-mask-image: linear-gradient(to bottom, currentColor calc(100% - 18px), transparent);
  mask-image: linear-gradient(to bottom, currentColor calc(100% - 18px), transparent);
}

.dossier::-webkit-scrollbar,
.verdict::-webkit-scrollbar {
  width: 4px;
}

.dossier::-webkit-scrollbar-thumb,
.verdict::-webkit-scrollbar-thumb {
  background: var(--tb-border-strong);
}

.verdict {
  border-left: var(--tb-border-width) solid var(--tb-border);
  padding-left: clamp(14px, 2.6cqw, 34px);
}

.sec-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 clamp(8px, 1.5cqw, 13px);
  padding-bottom: clamp(6px, 1.1cqw, 10px);
  border-bottom: var(--tb-border-width) solid var(--rule);
}

.sec-head__no {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.35cqw, 10px);
  font-weight: 600;
  letter-spacing: 0.1em;
  color: var(--tone);
}

.sec-head__title {
  margin: 0;
  font-family: var(--font-serif);
  font-size: clamp(13px, 2.15cqw, 17px);
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--tb-text-primary);
  white-space: nowrap;
}

.sec-head__en {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: clamp(7px, 1.15cqw, 9px);
  letter-spacing: 0.16em;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.sec-head__meta {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: clamp(9px, 1.4cqw, 10.5px);
  letter-spacing: 0.04em;
  color: var(--tb-text-tertiary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ---------- 材料清单 ---------- */
.group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 2px;
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.3cqw, 10px);
  letter-spacing: 0.14em;
  color: var(--tb-text-tertiary);
}

.dossier__root {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin: -2px 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(8px, 1.3cqw, 9.5px);
  letter-spacing: 0.04em;
  color: var(--tb-text-tertiary);
}

.dossier__root-mark {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  border: 1px solid var(--tone-line);
  transform: rotate(45deg);
}

.dossier__root-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-head b {
  font-family: var(--font-serif);
  font-size: 1.5em;
  font-weight: 700;
  letter-spacing: 0;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-secondary);
}

.dossier__group + .dossier__group {
  margin-top: clamp(12px, 2.2cqw, 20px);
}

.filelist {
  margin: 0;
  padding: 0;
  list-style: none;
}

.filelist__item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 6px;
  padding: clamp(4px, 0.9cqw, 7px) 0;
  border-bottom: var(--tb-border-width) dashed var(--rule);
}

.filelist__item:last-child {
  border-bottom: 0;
}

.filelist__no {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.04em;
  color: var(--tb-text-tertiary);
}

.filelist__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.filelist__name {
  font-size: clamp(10px, 1.7cqw, 12px);
  font-weight: 600;
  color: var(--tb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filelist__dir {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.02em;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filelist__ext {
  padding: 1px 4px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xs);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.06em;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
}

.filelist--read .filelist__ext {
  border-color: var(--tone-line);
  color: var(--tone);
}

.empty-note {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-left: 2px solid var(--tb-border-strong);
  font-size: clamp(9px, 1.5cqw, 11px);
  line-height: 1.7;
  color: var(--tb-text-tertiary);
}

.dossier__hint {
  margin: 7px 0 0;
  font-size: clamp(8px, 1.35cqw, 10px);
  line-height: 1.6;
  color: var(--tb-warning);
}

/* ---------- 评分输出（自定义导出列） ---------- */
.output-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.output {
  padding: clamp(9px, 1.6cqw, 14px) 0;
  border-bottom: var(--tb-border-width) solid var(--rule);
}

.output:last-child {
  border-bottom: 0;
}

.output__head {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-bottom: 4px;
}

.output__no {
  flex: 0 0 auto;
  width: 16px;
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--tb-text-tertiary);
}

.output__label {
  flex: 1 1 auto;
  min-width: 0;
  font-size: clamp(10px, 1.8cqw, 13px);
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.output__type {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.06em;
  color: var(--tb-text-tertiary);
}

.output__text {
  margin: 0 0 0 23px;
  font-family: var(--font-serif);
  font-size: clamp(11px, 1.95cqw, 15px);
  line-height: 1.95;
  letter-spacing: 0.006em;
  color: var(--tb-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.output__text--empty {
  font-family: var(--font-sans);
  font-size: clamp(9px, 1.5cqw, 11px);
  color: var(--tb-text-tertiary);
}

.output__number {
  margin: 0 0 0 23px;
  font-family: var(--font-serif);
  font-size: clamp(17px, 3.1cqw, 25px);
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--tb-text-primary);
}

.output__number--empty {
  font-family: var(--font-sans);
  font-size: clamp(9px, 1.5cqw, 11px);
  font-weight: 400;
  color: var(--tb-text-tertiary);
}

.output__boolean {
  margin: 0 0 0 23px;
  font-family: var(--font-serif);
  font-size: clamp(15px, 2.7cqw, 22px);
  font-weight: 700;
  line-height: 1.3;
  color: var(--tb-text-primary);
}

.output__boolean--empty {
  font-family: var(--font-sans);
  font-size: clamp(9px, 1.5cqw, 11px);
  font-weight: 400;
  color: var(--tb-text-tertiary);
}

.output__list {
  margin: 0 0 0 23px;
  padding-left: 1.15em;
  font-family: var(--font-serif);
  font-size: clamp(10px, 1.8cqw, 13px);
  line-height: 1.85;
  color: var(--tb-text-primary);
  word-break: break-word;
}

.output__list li::marker {
  color: var(--tone);
}

.output__list--empty {
  padding-left: 0;
  list-style: none;
  font-family: var(--font-sans);
  font-size: clamp(9px, 1.5cqw, 11px);
  color: var(--tb-text-tertiary);
}

.output__json {
  margin: 0 0 0 23px;
  padding: 6px 8px;
  border-left: 2px solid var(--tone-line);
  background: var(--tb-bg-raised);
  font-family: var(--font-mono);
  font-size: clamp(9px, 1.5cqw, 11px);
  line-height: 1.6;
  color: var(--tb-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 12rem;
  overflow: auto;
}

.output__json--empty {
  border-left: 0;
  background: transparent;
  font-family: var(--font-sans);
  color: var(--tb-text-tertiary);
}

.output-empty {
  margin: 0;
  padding: clamp(10px, 1.8cqw, 16px) 0;
  font-size: clamp(9px, 1.5cqw, 11px);
  line-height: 1.7;
  color: var(--tb-text-tertiary);
}

/* ==========================================================================
   页脚
   ========================================================================== */
.sheet__foot {
  display: flex;
  align-items: baseline;
  gap: clamp(8px, 1.6cqw, 16px);
  min-width: 0;
  padding: clamp(8px, 1.5cqw, 14px) var(--sheet-x);
  border-top: var(--tb-border-width) dashed color-mix(in oklab, var(--tb-text-primary) 16%, transparent);
  background: color-mix(in oklab, var(--tb-bg-raised) 62%, transparent);
}

.foot__label {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.16em;
  color: var(--tb-text-tertiary);
}

.foot__text {
  flex: 1 1 auto;
  min-width: 0;
  margin: 0;
  font-size: clamp(9px, 1.5cqw, 11px);
  line-height: 1.6;
  color: var(--tb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.foot__mark {
  flex: 0 0 auto;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.16em;
  color: var(--tb-text-tertiary);
  opacity: 0.7;
  white-space: nowrap;
}

/* ==========================================================================
   入场动效：只在首次挂载时发生，卡片翻动不重复播放
   ========================================================================== */
@media (prefers-reduced-motion: no-preference) {
  .sheet__head {
    animation: sheet-rise 0.62s var(--tb-ease-out) both;
  }

  .hero {
    animation: sheet-rise 0.72s var(--tb-ease-out) 0.06s both;
  }

  .sheet__body {
    animation: sheet-rise 0.72s var(--tb-ease-out) 0.12s both;
  }

  .sheet__foot {
    animation: sheet-rise 0.7s var(--tb-ease-out) 0.18s both;
  }

  .scoreline__num {
    animation: score-rise 0.9s var(--tb-ease-out) 0.1s both;
  }

  .seal {
    animation: seal-press 0.52s var(--tb-ease-spring) 0.62s both;
  }

  .meter__fill {
    animation: meter-fill 1.15s var(--tb-ease-out) 0.3s both;
  }

  .meter__dot {
    animation: meter-dot 0.5s var(--tb-ease-out) 1.15s both;
  }
}

@keyframes sheet-rise {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes score-rise {
  from {
    opacity: 0;
    transform: translateY(16px);
    filter: blur(5px);
  }

  to {
    opacity: 1;
    transform: none;
    filter: none;
  }
}

@keyframes seal-press {
  from {
    opacity: 0;
    transform: scale(1.7) rotate(-16deg);
  }

  to {
    opacity: 1;
    transform: scale(1) rotate(-7deg);
  }
}

@keyframes meter-fill {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(var(--ratio));
  }
}

@keyframes meter-dot {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.3);
  }

  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

/* ==========================================================================
   容器自适应：按卡片自身宽度 / 高度收紧，不依赖视口断点
   ========================================================================== */
@container sheet (max-height: 620px) {
  .sheet__head {
    height: 44px;
  }

  .hero {
    padding-top: 14px;
    padding-bottom: 12px;
  }

  .hero__name {
    font-size: clamp(1.15rem, 3.6cqw, 1.85rem);
  }

  .scoreline__num {
    font-size: clamp(3rem, 10.5cqw, 5.5rem);
  }

  .meter {
    margin-top: 8px;
  }

  .sheet__foot {
    padding-top: 7px;
    padding-bottom: 7px;
  }
}

@container sheet (max-height: 500px) {
  .sheet__foot {
    display: none;
  }

  .meter__scale {
    display: none;
  }

  .meter {
    padding-bottom: 0;
  }

  .sheet__body {
    padding-top: 12px;
  }
}

@container sheet (max-width: 480px) and (min-height: 540px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }

  .hero__score {
    align-items: flex-start;
    width: 100%;
  }

  .eyebrow--end {
    justify-content: flex-start;
  }

  .sheet__body {
    display: block;
    overflow-y: auto;
    overscroll-behavior: contain;
    scrollbar-width: thin;
    scrollbar-color: var(--tb-border-strong) transparent;
    -webkit-mask-image: linear-gradient(to bottom, currentColor calc(100% - 18px), transparent);
    mask-image: linear-gradient(to bottom, currentColor calc(100% - 18px), transparent);
  }

  .dossier {
    overflow: visible;
    padding-bottom: 16px;
    border-bottom: var(--tb-border-width) solid var(--tb-border);
    margin-bottom: 18px;
    -webkit-mask-image: none;
    mask-image: none;
  }

  .verdict {
    overflow: visible;
    border-left: 0;
    padding-left: 0;
    -webkit-mask-image: none;
    mask-image: none;
  }
}

@container sheet (max-width: 460px) {
  .sheet__inner {
    display: block;
    height: auto;
    min-height: 100%;
  }

  .sheet__head {
    position: sticky;
    top: 0;
    z-index: 4;
    background: var(--tb-bg-surface);
  }

  .head__file,
  .head__workflow,
  .head__open {
    display: none;
  }

  .head__brand {
    flex: 1 1 auto;
  }

  .hero {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }

  .hero__score {
    align-items: flex-start;
    width: 100%;
  }

  .eyebrow--end {
    justify-content: flex-start;
  }

  .sheet__body {
    display: block;
    overflow: visible;
    -webkit-mask-image: none;
    mask-image: none;
  }

  .dossier {
    overflow: visible;
    padding-bottom: 16px;
    border-bottom: var(--tb-border-width) solid var(--tb-border);
    margin-bottom: 18px;
    -webkit-mask-image: none;
    mask-image: none;
  }

  .verdict {
    overflow: visible;
    border-left: 0;
    padding-left: 0;
    -webkit-mask-image: none;
    mask-image: none;
  }

  .foot__mark {
    display: none;
  }
}
</style>
