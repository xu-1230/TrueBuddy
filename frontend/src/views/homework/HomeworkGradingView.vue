<template>
  <div class="homework-studio">
    <div class="studio-body">
      <!-- ============ 左侧配置面板 ============ -->
      <ConfigPanel
        v-model:space-ids="config.spaceIds"
        v-model:description="config.description"
        v-model:export-format="config.exportFormat"
        v-model:workflow="config.workflow"
        v-model:recognize-image="config.recognizeImage"
        v-model:concurrency="config.concurrency"
        v-model:folder="folder"
        :knowledge-bases="kbOptions"
        :workflows="workflows"
        :kb-loading="kbLoading"
        :grading="grading"
        :can-grade="canGrade"
        @grade="handleGrade"
        @create-workflow="openWorkflowEditor('')"
        @edit-workflow="openWorkflowEditor"
      />

      <!-- ============ 右侧工作区：作业文件卡堆 ============ -->
      <section class="stage">
        <!-- 卡头：上传进度 + 全局操作（不随卡片旋转）
             规格对齐 views/doctemplate 的展示框卡头：左侧状态徽标 + 上下文，右侧图标 + 文案的操作键 -->
        <header class="stage__toolbar">
          <div class="stage__status">
            <span class="stage__badge">{{ displayBadge }}</span>
            <p class="stage__meta" :title="headerMeta">{{ headerMeta }}</p>
          </div>
          <div class="stage__actions">
            <button
              type="button"
              class="ghost-chip"
              aria-label="历史记录"
              @click="openHistoryDrawer"
            >
              <el-icon><Clock /></el-icon>
              <span>历史记录</span>
            </button>
            <button
              type="button"
              class="ghost-chip"
              aria-label="导出成绩表"
              :disabled="!canExport"
              @click="handleExport"
            >
              <el-icon><Download /></el-icon>
              <span>导出成绩表</span>
            </button>
            <button
              type="button"
              class="ghost-chip ghost-chip--danger"
              aria-label="再次上传（会清空当前结果）"
              title="再次上传（会清空当前结果）"
              :disabled="!canGrade"
              @click="handleGrade"
            >
              <el-icon><Refresh /></el-icon>
              <span>再次批改</span>
            </button>
          </div>

          <!-- 上传进度：贴在卡头底缘的一条细线 -->
          <span
            v-if="grading"
            class="stage__progress"
            role="progressbar"
            aria-label="上传进度"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="runningPercent"
          >
            <span class="stage__progress-bar" :style="{ width: `${runningPercent}%` }"></span>
          </span>
        </header>

        <!-- 卡堆画布：对应 .display-box__canvas，卡头之下就是一块贴着面板内沿的场地 -->
        <div class="stage__canvas">
          <!-- 卡片堆栈容器：w-full / h-full / relative / overflow-visible / z-0
               每张卡一份作业文件，绝对定位居中，深度几何由 stackStyle() 下发 -->
          <div v-if="records.length" class="card-stack">
            <div
              v-for="card in deck"
              :key="card.key"
              class="card-container"
              :class="{
                'card-container--entering': card.entering,
                'card-container--leaving': card.leaving,
              }"
              :style="cardStyle(card)"
              @pointerdown="focusDepth(card.depth)"
            >
              <ScoreCard
                :record="card.record"
                :description="activeDescription"
                :workflow-label="activeWorkflowLabel"
                :index="card.position"
                :total="records.length"
                @prev="step(-1)"
                @next="step(1)"
              />
            </div>
          </div>

          <!-- 无结果：上传中 / 未开始 -->
          <div v-else class="stage__blank">
            <template v-if="grading">
              <span class="stage__blank-spinner"></span>
              <p class="stage__blank-title">
                {{ uploading ? `正在上传作业文件 ${uploadPercent}%` : '正在上传作业文件…' }}
              </p>
              <p class="stage__blank-desc">
                {{ stageLabel || `共 ${fileCount} 份作业，正在逐份存储并保留目录结构，请勿关闭页面` }}
              </p>
            </template>
            <template v-else>
              <!-- 空卡堆剪影：先把「会堆成什么」画出来，再讲怎么开始 -->
              <div class="stage__ghost" aria-hidden="true">
                <span class="stage__ghost-card stage__ghost-card--back"></span>
                <span class="stage__ghost-card stage__ghost-card--mid"></span>
                <span class="stage__ghost-card stage__ghost-card--front">
                  <span class="stage__ghost-bar"></span>
                  <span class="stage__ghost-line"></span>
                  <span class="stage__ghost-line stage__ghost-line--short"></span>
                  <span class="stage__ghost-score">--</span>
                </span>
              </div>
              <p class="stage__blank-title">作业清单将在这里堆成一叠</p>
              <p class="stage__blank-desc">
                每张卡就是一份作业文件：左边是它在文件夹中的相对路径，右边是文件信息与评语位；
                点卡片上的箭头、右侧光点，或在卡片间点击露出的边角，即可逐份翻阅。
              </p>

              <!-- 就绪清单：直接复述左下角的两项前置条件，满足即打勾 -->
              <ol class="stage__steps">
                <li class="stage__step" :class="{ 'stage__step--done': fileCount > 0 }">
                  <span class="stage__step-mark">
                    <el-icon v-if="fileCount > 0"><Check /></el-icon>
                    <span v-else>1</span>
                  </span>
                  <span class="stage__step-text">选择作业文件夹</span>
                  <span class="stage__step-note">
                    {{ fileCount > 0 ? `已选 ${fileCount} 份` : '支持 Word / PDF / 文本 / 图片' }}
                  </span>
                </li>
                <li class="stage__step" :class="{ 'stage__step--done': descriptionFilled }">
                  <span class="stage__step-mark">
                    <el-icon v-if="descriptionFilled"><Check /></el-icon>
                    <span v-else>2</span>
                  </span>
                  <span class="stage__step-text">填写批改要求</span>
                  <span class="stage__step-note">
                    {{ descriptionFilled ? '已填写' : '左侧「AI 描述说明」' }}
                  </span>
                </li>
              </ol>


            </template>
          </div>

          <!-- 右侧发光节点轨道：一个节点 = 一份作业文件，点亮的是堆顶那一份 -->
          <ul class="stage__spine" :style="{ '--spine-height': `${records.length * 34}px` }">
            <li v-for="(record, index) in records" :key="record.id">
              <button
                type="button"
                class="stage__spine-node"
                :class="{
                  'stage__spine-node--lit': index === activeIndex,
                  'stage__spine-node--passed': index < activeIndex,
                }"
                :title="record.fileName"
                :aria-label="`第 ${index + 1} 份作业`"
                :aria-current="index === activeIndex"
                @click="goTo(index)"
              ></button>
            </li>
          </ul>
        </div>
      </section>
    </div>

    <!-- ============ 历史记录抽屉 ============ -->
    <HistoryDrawer
      v-model="historyOpen"
      :records="historyRecords"
      :loading="historyLoading"
      :active-id="activeHistoryId"
      :detail-loading-id="historyDetailLoadingId"
      @select="handleOpenHistoryRecord"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Clock, Download, Refresh } from '@element-plus/icons-vue'
import { getSpaceList } from '@/api/knowledgebase'
import { submitGrading, uploadHomeworkFiles } from '@/api/homework'
import { defaultWorkflowId, loadWorkflows } from '@/views/homework/utils/workflows'
import ConfigPanel from './components/ConfigPanel.vue'
import HistoryDrawer from './components/HistoryDrawer.vue'
import ScoreCard from './components/ScoreCard.vue'

const route = useRoute()

/* ---------- 批改工作流（内置模板 + 自定义，存本地） ---------- */
const workflows = ref(loadWorkflows())

const selectedWorkflow = computed(
  () => workflows.value.find((item) => item.id === config.workflow) ?? workflows.value[0] ?? null,
)

function workflowName(id) {
  return workflows.value.find((item) => item.id === id)?.name ?? '标准批改作业'
}

/** 工作流编排页已移除，待重新实现后恢复跳转到独立编辑器 */
function openWorkflowEditor() {
  ElMessage.info('工作流编排功能正在重新开发中')
}

/** 内置默认知识库：始终置于列表首位，且作为默认选中项 */
const DEFAULT_KB = {
  id: 'default',
  name: '默认知识库',
  description: '系统内置，未指定时使用',
  isDefault: true,
}

/* ---------- 配置 ---------- */
const config = reactive({
  spaceIds: [DEFAULT_KB.id],
  description: '',
  exportFormat: 'xlsx',
  workflow: defaultWorkflowId(),
  recognizeImage: false,
  concurrency: 3,
})

/* ---------- 知识库选择 ---------- */
const knowledgeBases = ref([])
const kbLoading = ref(false)
const kbOptions = computed(() => [DEFAULT_KB, ...knowledgeBases.value])

/** 已选知识库名称（多选，用「、」连接）；未选择时回落到内置默认知识库 */
const selectedKbNames = computed(() => {
  const names = config.spaceIds
    .map((id) => kbOptions.value.find((kb) => kb.id === id)?.name)
    .filter(Boolean)
  return names.length ? names.join('、') : DEFAULT_KB.name
})

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    const list = await getSpaceList()
    knowledgeBases.value = list ?? []
  } catch {
    // 后端不可用时不阻断页面，下拉仅保留内置默认知识库
    knowledgeBases.value = []
  } finally {
    kbLoading.value = false
  }
}

/* ---------- 作业文件夹 ---------- */
const folder = ref(null)

/* ---------- 上传 ---------- */
const grading = ref(false)
/** 上传阶段：''（空闲）/ upload（上传中）/ grading（上传完成、结果整理中） */
const gradingPhase = ref('')
const uploadPercent = ref(0)
/** 右侧卡堆数据源：一份作业文件 = 一张卡 */
const records = ref([])
/** 当前阶段（预留，工作流阶段回显用） */
const currentStage = ref(null)
/** 当前结果对应的任务信息：{ taskId, description, workflow, exportFormat } */
const taskMeta = ref(null)

/** 已选作业份数（卡头副行、空态清单、开始批改按钮共用） */
const fileCount = computed(() => folder.value?.files?.length ?? 0)

/** 是否已填写批改要求（空态清单的第二项） */
const descriptionFilled = computed(() => Boolean(config.description.trim()))

const canGrade = computed(
  () => fileCount.value > 0 && !grading.value,
)

const canExport = computed(() => records.value.length > 0 && !grading.value)

/** 上传阶段 */
const uploading = computed(() => gradingPhase.value === 'upload')

/** 上传进度百分比 */
const runningPercent = computed(() => {
  if (uploading.value) return uploadPercent.value
  if (!fileCount.value) return 0
  return Math.round((records.value.length / fileCount.value) * 100)
})

/** 当前工作流阶段的文案（预留） */
const stageLabel = computed(() => {
  const stage = currentStage.value
  if (!stage) return ''
  const { index, total, label, model, kbName, iteration } = stage
  const head = iteration
    ? `迭代 ${iteration.index}/${iteration.total} · ${label}`
    : `第 ${index}/${total} 步 · ${label}`
  const parts = [head]
  if (model) parts.push(`模型 ${model}`)
  if (kbName) parts.push(`上下文 ${kbName}`)
  return parts.join(' · ')
})

/** 卡头徽标：只交代当前这件事的状态 */
const displayBadge = computed(() => {
  if (grading.value) {
    return `正在上传 ${records.value.length}/${fileCount.value} 份`
  }
  if (records.value.length) {
    return activeHistoryId.value
      ? `历史记录 · 已载入 ${records.value.length} 份`
      : `已上传 ${records.value.length} 份`
  }
  return '待上传'
})

/** 卡头上下文：空闲时交代「传哪个文件夹、传多少」，上传中换成进度 */
const headerMeta = computed(() => {
  if (grading.value) {
    return uploading.value
      ? `正在上传作业文件 ${uploadPercent.value}%`
      : stageLabel.value || '正在逐份存储作业文件…'
  }
  if (!folder.value) return '选择作业文件夹后点击「开始批改」即可上传'
  return [
    folder.value.name,
    activeWorkflowLabel.value,
    selectedKbNames.value,
    `${fileCount.value} 份作业`,
  ].join(' · ')
})

/**
 * 把后端返回的一份作业文件映射为卡片数据结构。
 * 评分与评语待批改接口接入，这里保留占位，UI 与批改结果卡完全一致。
 */
function toRecord(item) {
  return {
    id: item.id ?? item.path,
    fileName: item.name,
    studentName: '',
    score: null,
    grade: '待批改',
    comment: '本次仅完成作业文件上传，评分与评语待批改接口接入。',
    originalText: `【相对路径】${item.path}\n\n（作业原文的解析结果待批改接口接入后展示）`,
    model: '',
    kbName: '',
    fileId: item.fileId,
    fileUrl: item.url,
    size: item.size,
    status: 0,
    errorMsg: '',
  }
}

async function handleGrade() {
  if (grading.value) return
  if (!fileCount.value) {
    ElMessage.warning('请先选择作业文件夹')
    return
  }

  const files = folder.value.files
  // 相对路径口径：点击选择文件夹用 webkitRelativePath，拖拽导入已在 FolderUploader 中补写
  const paths = files.map((file) => file.webkitRelativePath || file.name)

  grading.value = true
  gradingPhase.value = 'upload'
  uploadPercent.value = 0
  currentStage.value = null
  records.value = []
  resetCardDeck()
  activeHistoryId.value = null

  try {
    const data = await uploadHomeworkFiles(files, paths, folder.value.name, (event) => {
      if (event?.total) {
        uploadPercent.value = Math.round((event.loaded / event.total) * 100)
      }
    })

    taskMeta.value = {
      taskId: data.taskId,
      description: config.description.trim(),
      workflow: config.workflow,
      exportFormat: config.exportFormat,
    }

    records.value = (data.files ?? []).map(toRecord)
    if (!records.value.length) {
      ElMessage.error('未返回作业文件清单，请重试')
      return
    }
    ElMessage.success(`上传完成：共 ${data.fileCount} 份作业`)

    // 第二步：把批改配置交给后端（当前后端只接收并记录，真实批改待接入）。
    // 独立容错：这一步失败不应影响第一步已经展示出来的文件清单。
    try {
      await submitGrading({
        taskId: data.taskId,
        spaceIds: [...config.spaceIds],
        // 文件清单（含相对路径、fileId 与 MinIO 预签名 URL），直接透传第一步的返回
        files: data.files ?? [],
        description: config.description.trim(),
        exportFormat: config.exportFormat,
        workflowId: config.workflow,
      })
      ElMessage.success('批改请求已提交')
    } catch (gradeError) {
      ElMessage.error(gradeError?.message || '批改请求提交失败，请重试')
    }
  } catch (e) {
    ElMessage.error(e?.message || '上传失败，请重试')
  } finally {
    grading.value = false
    gradingPhase.value = ''
    uploadPercent.value = 0
    currentStage.value = null
  }
}

/* ---------- 导出（待接入后端） ---------- */
function handleExport() {
  if (!records.value.length) {
    ElMessage.warning('暂无可导出的作业结果')
    return
  }
  ElMessage.info('成绩表导出待批改接口接入后开放')
}

/* ---------- 作业文件卡堆（一卡一份作业文件） ----------
   对齐参考站点 chat 的 card-container：容器 relative + overflow-visible，
   每张卡绝对定位居中，用「深度几何」决定它在卡堆里的位置。 */
/** 最深可见层：更深的卡片淡出（参考实现的「远端态」） */
const STACK_MAX_DEPTH = 3
/** 让位弧线半径：参考实现取「可用高度 / 8」，此处按卡片高度约 960px 对齐 */
const STACK_RADIUS = 120
/** 参与渲染的层数：可见层之外多留一层，保证卡片进出 (DOM) 时已经淡出 */
const STACK_RENDER = STACK_MAX_DEPTH + 2

/** 入堆淡入 / 出堆淡出时长（与 .card-container--entering / --leaving 动画一致） */
const CARD_ENTER_MS = 520
const CARD_LEAVE_MS = 350

/** 当前最前的作业文件在 records 中的下标（0 = 堆顶） */
const activeIndex = ref(0)
/** 已翻过的作业文件：{ id, key, fromDepth }，留到「向上飞出」动画结束再移除 */
const leavingCards = ref([])
/** 刚进入卡堆的卡片 key 集合：用于触发淡入动画 */
const enteringKeys = ref(new Set())

const stackTimers = []
/** 卡堆内的延时任务统一登记，组件卸载时清掉，避免残留定时器改动已销毁的状态 */
function scheduleStackTask(task, delay) {
  stackTimers.push(window.setTimeout(task, delay))
}

onBeforeUnmount(() => {
  stackTimers.forEach((timer) => window.clearTimeout(timer))
})

/** 结果集变化：上传后一次写入，只把越界的下标收回 */
watch(
  () => records.value.length,
  (length) => {
    if (!length) {
      activeIndex.value = 0
      leavingCards.value = []
      return
    }
    if (activeIndex.value >= length) activeIndex.value = 0
  },
)

/**
 * 卡堆：一叠纸，不是环形。堆顶（activeIndex）之后依次往下叠 STACK_RENDER 份，
 * 翻过的作业文件从堆顶抽走（向上飞出），不再回绕。
 */
const deck = computed(() => {
  const list = records.value
  const total = list.length
  if (!total) return []

  const flying = new Set(leavingCards.value.map((item) => item.key))
  const cardsInStack = []

  for (let depth = 0; depth < STACK_RENDER; depth += 1) {
    const position = activeIndex.value + depth
    if (position >= total) break

    const record = list[position]
    const key = `record-${record.id}`
    // 正在飞出的作业文件单独成条，避免同一 key 出现两次
    if (flying.has(key)) continue

    cardsInStack.push({
      key,
      record,
      depth,
      position: position + 1,
      entering: enteringKeys.value.has(key),
    })
  }

  // 已翻过的作业文件：沿用离开时的深度（不改几何，交给飞出动画），压在堆顶之上
  const flyingCards = leavingCards.value
    .map((item) => {
      const record = list.find((entry) => entry.id === item.id)
      if (!record) return null
      return { key: item.key, record, depth: item.fromDepth, leaving: true }
    })
    .filter(Boolean)

  return [...cardsInStack, ...flyingCards]
})

/** 卡堆可见范围内的 key 串变化时，标记新出现的卡片淡入 */
watch(
  () => deck.value.filter((card) => !card.leaving).map((card) => card.key).join(','),
  (now, previous) => {
    if (!now) return
    const already = new Set((previous || '').split(',').filter(Boolean))
    const added = now.split(',').filter((key) => !already.has(key))
    if (!added.length) return

    enteringKeys.value = new Set(added)
    scheduleStackTask(() => {
      enteringKeys.value = new Set()
    }, CARD_ENTER_MS)
  },
)

/** 翻阅：+1 抽走堆顶看下一份，-1 把上一份放回堆顶；到头即停，不回绕 */
function step(offset) {
  if (!records.value.length) return
  goTo(activeIndex.value + offset)
}

/** 点击后方卡片露出的边角：把它翻到堆顶，挡在它前面的作业文件依次抽走 */
function focusDepth(depth) {
  if (depth > 0) goTo(activeIndex.value + depth)
}

/** 跳到第 n 份（节点轨道 / 卡片点击共用），越界时夹紧到首尾两端 */
function goTo(index) {
  const total = records.value.length
  if (!total) return

  const target = Math.min(Math.max(index, 0), total - 1)
  if (target === activeIndex.value) return

  if (target > activeIndex.value) {
    // 往后翻：被越过的作业文件从堆顶抽走，向上飞出后移除
    const passed = []
    for (let position = activeIndex.value; position < target; position += 1) {
      const record = records.value[position]
      if (!record) continue
      const key = `record-${record.id}`
      if (!leavingCards.value.some((item) => item.key === key)) {
        passed.push({ id: record.id, key, fromDepth: position - activeIndex.value })
      }
    }
    if (passed.length) {
      const keys = passed.map((item) => item.key)
      leavingCards.value = [...leavingCards.value, ...passed]
      scheduleStackTask(() => {
        leavingCards.value = leavingCards.value.filter((item) => !keys.includes(item.key))
      }, CARD_LEAVE_MS)
    }
  } else {
    // 往回翻：还没飞完的卡片重新回到堆里
    const back = records.value
      .slice(target, activeIndex.value)
      .map((record) => `record-${record.id}`)
    if (back.length) {
      leavingCards.value = leavingCards.value.filter((item) => !back.includes(item.key))
    }
  }

  activeIndex.value = target
}

/** 已翻过的作业文件压在堆顶之上飞出，避免被后面的卡片盖住 */
function cardStyle(card) {
  const style = stackStyle(card.depth)
  return card.leaving ? { ...style, zIndex: 30 } : style
}

/**
 * 卡片堆叠几何：depth 0 为最前，居中满幅。
 * 每深一层：旋转 -5°、缩小 4%、模糊 +0.5px、亮度 -5%，并沿弧线向左后方让位；
 * z-index 取 25 - depth，深度变化时按整数阶梯把卡片翻到前方。
 */
function stackStyle(depth) {
  const zIndex = 25 - depth
  if (depth <= 0) {
    return { zIndex, opacity: 1, filter: 'none', transform: 'translate(-50%, -50%)' }
  }

  const far = depth > STACK_MAX_DEPTH
  const angle = ((90 + 5 * depth) * Math.PI) / 180
  const offsetX = STACK_RADIUS * Math.cos(angle)
  const offsetY = STACK_RADIUS - STACK_RADIUS * Math.sin(angle)

  return {
    zIndex,
    opacity: far ? 0 : 0.9,
    filter: far
      ? 'blur(20px) brightness(0.5)'
      : `blur(${(0.5 * depth).toFixed(1)}px) brightness(${Math.max(0.6, 1 - 0.05 * depth).toFixed(2)})`,
    transform: [
      `translate(calc(-50% + ${offsetX.toFixed(1)}px), calc(-50% + ${offsetY.toFixed(1)}px))`,
      `rotate(${-5 * depth}deg)`,
      `scale(${Math.max(0, 1 - 0.04 * depth).toFixed(2)})`,
    ].join(' '),
  }
}

/** 重新上传 / 载入历史记录：回到第一份 */
function resetCardDeck() {
  activeIndex.value = 0
  leavingCards.value = []
}

const activeDescription = computed(
  () => taskMeta.value?.description || config.description,
)

const activeWorkflowLabel = computed(
  () => workflowName(taskMeta.value?.workflow || config.workflow),
)

/* ---------- 历史记录（待接入后端，当前仅保留抽屉 UI） ---------- */
const historyOpen = ref(false)
const historyRecords = ref([])
const historyLoading = ref(false)
const activeHistoryId = ref(null)
const historyDetailLoadingId = ref(null)

async function refreshHistory() {
  historyLoading.value = true
  try {
    historyRecords.value = []
  } finally {
    historyLoading.value = false
  }
}

function openHistoryDrawer() {
  historyOpen.value = true
  refreshHistory()
}

function handleOpenHistoryRecord(record) {
  if (!record?.id || historyDetailLoadingId.value) return
  historyDetailLoadingId.value = record.id
  try {
    if (!record.records?.length) {
      ElMessage.warning('该次批改没有可查看的记录')
      return
    }
    records.value = record.records
    taskMeta.value = {
      taskId: record.id,
      description: record.description || record.title,
      workflow: record.workflowId || resolveWorkflowKey(record.workflow),
      exportFormat: record.exportFormat || config.exportFormat,
    }
    resetCardDeck()
    activeHistoryId.value = record.id
    historyOpen.value = false
    ElMessage.success(`已载入历史记录，共 ${record.records.length} 份`)
  } catch (e) {
    ElMessage.error(e?.message || '加载批改详情失败')
  } finally {
    historyDetailLoadingId.value = null
  }
}

/** 历史记录中的工作流文案 → 工作流 id（找不到时回落到默认模板） */
function resolveWorkflowKey(label) {
  const matched = workflows.value.find((item) => item.name === label)
  return matched ? matched.id : defaultWorkflowId()
}

onMounted(() => {
  loadKnowledgeBases()
  refreshHistory()
  // 从工作流编排页返回时，自动选中刚保存/复制的工作流
  const selected = route.query.workflow
  if (typeof selected === 'string' && workflows.value.some((item) => item.id === selected)) {
    config.workflow = selected
  }
})
</script>

<style scoped>
.homework-studio {
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 2rem);
  min-height: 640px;
  border-radius: 0;
  overflow: hidden;
  background: var(--tb-bg-page);
  color: var(--tb-text-primary);
  box-shadow: var(--tb-shadow-2);
}

.studio-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
}

/* ================= 右侧工作区：作业文件卡堆面板 =================
   面板分层对齐 views/doctemplate 的展示框：
   .stage = .display-box（面色 + 描边 + 圆角）、
   .stage__toolbar = .display-box__toolbar（贴齐上沿的标题栏）、
   .stage__canvas = .display-box__canvas（下沉底色 + 点阵网纹的场地）。
   颜色 / 描边 / 圆角全部取自语义 Token，明暗与主题切换时自动跟随
   （docs/09-样式规范.md §3.2 第 3 条、§3.3 约定 1）。
   若某天需要还原成参考图里那块「固定深色场」，只需给下面的根容器加
   data-appearance="dark" 并切 color-scheme: dark —— 全局 light-dark() 会自动
   让整棵子树的语义 Token 取深色一套，无需再写任何颜色。 */
/* 面板本体（对应文档模板页的 .display-box）：卡头贴齐上沿，画布铺满其余部分。
   padding 交给画布，panel 自身留 0，卡头背景才能满幅贴到描边上。 */
.stage {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  /* border-radius: var(--tb-radius-xl); */
  /* 裁掉卡头的直角背景，让它顺着面板圆角收口 */
  overflow: hidden;
}

/* 画布（对应文档模板页的 .display-box__canvas）：
   下沉一档的底色 + 点阵网纹，右上留一抹品牌色环境光；
   左 / 上 / 下留出卡堆扇形让位的余量，右侧 3.6rem 留给发光轨道。 */
.stage__canvas {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 2.5rem 3.6rem 2.5rem 4.5rem;
  background-color: var(--tb-bg-sunken);
  background-image:
    radial-gradient(
      120% 110% at 100% 0%,
      color-mix(in oklab, var(--tb-primary) 12%, transparent),
      transparent 62%
    ),
    radial-gradient(var(--tb-border) 1px, transparent 1px);
  background-size: auto, 22px 22px;
}

/* 卡片堆栈容器（对应参考站点的 w-full h-full relative overflow-visible z-0）：
   overflow-visible 让后方卡片可以越出容器边界，扇形露在主卡之外 */
.card-stack {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: visible;
  z-index: 0;
}

/* 单张卡：绝对定位居中；左 / 上 / 宽 / 高保持不变，全部几何走 transform，
   过渡名单 = 几何 + 不透明度 + 滤镜 + z-index（不含 width/height，避免内容重排） */
.card-container {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 100%;
  height: 100%;
  transition:
    transform 350ms ease-in-out,
    opacity 500ms ease-in-out,
    filter 350ms ease-in-out,
    z-index 350ms ease-in-out;
}

/* 入堆：从不透明 0 淡入（几何已由深度决定，动画只动 opacity） */
.card-container--entering {
  animation: card-stack-enter 500ms ease-out;
}

@keyframes card-stack-enter {
  from {
    opacity: 0;
  }
}

/* 出栈：原位淡出，卡片列表在淡出结束后移除，避免突兀消失 */
.card-container--leaving {
  pointer-events: none;
  animation: card-stack-leave 350ms ease-in forwards;
}

/* 与参考实现的 card-exit-up-and-grow 一致：向上抽出并放大后消失 */
@keyframes card-stack-leave {
  to {
    opacity: 0;
    transform: translate(-50%, -150%) scale(1.2);
  }
}

/* 发光节点轨道：一个节点 = 一份作业文件，纵向均分整条脊（份数多时自动收紧间距） */
.stage__spine {
  position: absolute;
  top: 50%;
  right: 1.55rem;
  /* 节点少时收成一簇，份数多时铺开，最多占舞台高度的 66% */
  height: min(var(--spine-height, 34px), 66%);
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  margin: 0;
  padding: 0;
  list-style: none;
}

.stage__spine::before {
  content: '';
  position: absolute;
  top: 5px;
  bottom: 5px;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: linear-gradient(
    180deg,
    color-mix(in oklab, var(--tb-text-primary) 55%, transparent),
    color-mix(in oklab, var(--tb-text-primary) 8%, transparent)
  );
}

.stage__spine-node {
  /* 波纹（::after）以节点自身为定位基准；缺了它光环会以整个舞台为基准 */
  position: relative;
  display: block;
  width: 10px;
  height: 10px;
  padding: 0;
  border-radius: 50%;
  border: 1.5px solid var(--tb-text-secondary);
  /* 用舞台底色当填充：节点读作场上的一个孔 */
  background: var(--tb-bg-sunken);
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

/* 扩大命中区（6~15px 的节点太小），透明不可见，随节点一起缩放故不会抖 */
.stage__spine-node::before {
  content: '';
  position: absolute;
  inset: -7px;
  border-radius: 50%;
}

.stage__spine-node:hover {
  transform: scale(1.35);
  border-color: var(--tb-text-primary);
}

/* 已翻过的节点：收成实心小点，比未翻的空心节点更暗 */
.stage__spine-node--passed {
  width: 6px;
  height: 6px;
  border-color: var(--tb-text-tertiary);
  background: var(--tb-text-tertiary);
}

.stage__spine-node--lit {
  width: 15px;
  height: 15px;
  border-color: var(--tb-text-primary);
  background: var(--tb-text-primary);
  box-shadow: 0 0 16px 3px color-mix(in oklab, var(--tb-text-primary) 55%, transparent);
}

/* 呼吸光晕：只动 transform / opacity（09-样式规范.md §6.3）。
   pointer-events: none 是关键 —— 否则光晕扫过指针时会来回触发 hover，造成抖动 */
.stage__spine-node--lit::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 1px solid color-mix(in oklab, var(--tb-text-primary) 65%, transparent);
  pointer-events: none;
  animation: hilbert-ping 2.6s var(--tb-ease-out) infinite;
}

@keyframes hilbert-ping {
  0% {
    opacity: 0.75;
    transform: scale(1);
  }

  70%,
  100% {
    opacity: 0;
    transform: scale(2.2);
  }
}

/* ---------- 卡头：上传进度 + 全局操作（不随卡片旋转，故放在卡片之外） ----------
   两行结构：主行是身份与状态（状态灯 + 标题 + 演示标记），副行交代上传上下文；
   上传中的进度线贴在底缘（.stage__progress），与分隔线重合，不额外占高度。 */
.stage__toolbar {
  position: relative;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  z-index: 2;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0.85rem;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.stage__status {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

/* 状态徽标：文档模板页展示框卡头里的那块「框内小牌」 */
.stage__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: var(--tb-radius-xs);
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

/* 上下文：一行说清「传哪个文件夹、传多少」（上传中替换为进度） */
.stage__meta {
  min-width: 0;
  margin: 0;
  font-size: 0.72rem;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 上传进度：与卡头底缘的分隔线重合，读作「这条线正在被填满」 */
.stage__progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(-1 * var(--tb-border-width));
  height: 2px;
  overflow: hidden;
  background: color-mix(in oklab, var(--tb-primary) 14%, transparent);
}

.stage__progress-bar {
  display: block;
  height: 100%;
  background: var(--tb-primary);
  transition: width 0.3s ease;
}

/* 操作区不参与收缩：空间不足时交由左侧可省略的上下文让位 */
.stage__actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* 操作键：与文档模板页 .ghost-chip 同一套规格（图标 + 文案，30px 高，方角） */
.ghost-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  height: 30px;
  padding: 0 0.7rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-xs);
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  font-size: 0.74rem;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.ghost-chip .el-icon {
  font-size: 15px;
}

.ghost-chip:hover:not(:disabled) {
  color: var(--tb-primary-text);
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.ghost-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ghost-chip:focus-visible {
  outline: 2px solid var(--tb-primary);
  outline-offset: 2px;
}

/* 破坏性操作（再次上传会清空当前结果）：常态与其它键一致，悬停才转警示色 */
.ghost-chip--danger:hover:not(:disabled) {
  color: var(--tb-danger);
  border-color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

/* ---------- 无结果：上传中 / 未开始 ---------- */
.stage__blank {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0 1rem;
  text-align: center;
  /* 舞台被压矮时（窄屏 / 小窗口）允许内部滚动，不撑破卡头 */
  overflow-y: auto;
}

/* ---------- 空卡堆剪影：把后续会出现的卡片先画出来 ---------- */
.stage__ghost {
  position: relative;
  flex: 0 0 auto;
  width: 180px;
  height: 118px;
  margin-bottom: 0.75rem;
  animation: stage-ghost-float 7s var(--tb-ease-inout) infinite;
}

@keyframes stage-ghost-float {
  50% {
    transform: translateY(-6px);
  }
}

.stage__ghost-card {
  position: absolute;
  inset: 0;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
  box-shadow: var(--tb-shadow-2);
}

/* 后方两张卡：与真实卡堆同样的「向左后方旋转让位」 */
.stage__ghost-card--back {
  transform: translate(-18px, 9px) rotate(-8deg);
  opacity: 0.45;
}

.stage__ghost-card--mid {
  transform: translate(-9px, 4px) rotate(-4deg);
  opacity: 0.72;
}

.stage__ghost-card--front {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 13px;
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-3);
  text-align: left;
}

.stage__ghost-bar {
  width: 48%;
  height: 7px;
  border-radius: var(--tb-radius-full);
  background: var(--tb-primary-soft);
}

.stage__ghost-line {
  height: 6px;
  border-radius: var(--tb-radius-full);
  background: color-mix(in oklab, var(--tb-text-primary) 8%, transparent);
}

.stage__ghost-line--short {
  width: 62%;
}

.stage__ghost-score {
  margin-top: auto;
  font-size: 1.5rem;
  font-weight: 800;
  line-height: 1;
  color: var(--tb-primary);
  opacity: 0.3;
}

.stage__blank-spinner {
  width: 34px;
  height: 34px;
  margin-bottom: 0.35rem;
  border-radius: 50%;
  border: 3px solid var(--tb-primary-border);
  border-top-color: var(--tb-primary);
  animation: homework-spin 0.8s linear infinite;
}

.stage__blank-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.stage__blank-desc {
  margin: 0;
  max-width: 26rem;
  font-size: 0.78rem;
  line-height: 1.7;
  color: var(--tb-text-tertiary);
}

/* ---------- 就绪清单：复述开始上传的两个前置条件 ---------- */
.stage__steps {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  width: min(100%, 24rem);
  margin: 0.6rem 0 0;
  padding: 0;
  list-style: none;
  text-align: left;
}

.stage__step {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.45rem 0.7rem;
  border-radius: var(--tb-radius-sm);
  background: color-mix(in oklab, var(--tb-bg-surface) 70%, transparent);
  border: var(--tb-border-width) solid var(--tb-border);
}

.stage__step-mark {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--tb-text-tertiary);
  background: var(--tb-bg-sunken);
  border: var(--tb-border-width) solid var(--tb-border);
}

.stage__step--done .stage__step-mark {
  color: var(--tb-on-primary);
  background: var(--tb-success);
  border-color: var(--tb-success);
}

.stage__step--done .stage__step-mark .el-icon {
  font-size: 12px;
}

.stage__step-text {
  flex: 1;
  min-width: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.stage__step--done .stage__step-text {
  color: var(--tb-text-primary);
}

.stage__step-note {
  flex: 0 0 auto;
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
}

.stage__step--done .stage__step-note {
  color: var(--tb-success);
}


@keyframes homework-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 动效偏好：装饰性的浮空一律停用，加载指示保留 */
@media (prefers-reduced-motion: reduce) {
  .stage__ghost {
    animation: none;
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1024px) {
  .homework-studio {
    height: auto;
    min-height: 0;
  }

  .studio-body {
    flex-direction: column;
  }

  .stage {
    min-height: 560px;
  }

  .stage__canvas {
    padding: 1.5rem 1rem 2.5rem 3rem;
  }

  /* 窄屏：清单只留主文，备注让位 */
  .stage__step-note {
    display: none;
  }

  /* 窄屏：操作键收成方图标键（aria-label 兜底），给卡头上下文让出宽度 */
  .ghost-chip {
    width: 30px;
    padding: 0;
    justify-content: center;
  }

  .ghost-chip span {
    display: none;
  }

  /* 窄屏不再为发光轨道留白，直接隐藏 */
  .stage__spine {
    display: none;
  }
}
</style>
