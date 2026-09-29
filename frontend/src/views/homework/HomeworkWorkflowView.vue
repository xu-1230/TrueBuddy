<script setup>
/**
 * 作业批改工作流实测页（对接后端 tb-module-workflow 的 SSE 执行接口）
 *
 * 流程：填写执行参数 → 提交执行（`POST /workflow/run` 返回 SSE 事件流，实时推送节点进度与逐份批改结果）
 *      → 执行到「人工确认」节点时弹出清单，勾选后提交（同一条流继续推后续事件）
 *      → 终态展示逐份批改结果与结束节点输出。
 *
 * 兜底：SSE 断开 / 报错（或刷新页面）时自动降级为每 2 秒轮询 `GET /workflow/get_process`，
 *      等待用户输入的待确认清单同样能从轮询结果里取回。
 *
 * 现状说明：后端为 MVP 阶段——画布由本页内置的本地文件提供（`mock/workflow-canvas.json`），
 *          大模型与知识库检索均为模拟实现，执行会话存内存（后端重启即失效），
 *          因此本页定位为**联调实测页**而非正式业务页。
 */
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, Refresh } from '@element-plus/icons-vue'
import {
  resumeWorkflow,
  runWorkflowStream,
  WORKFLOW_EXE_STATUS,
  WORKFLOW_NODE_STATUS,
  WORKFLOW_STREAM_EVENT,
} from '@/api/workflow'
import { pollWorkflowProcess } from '@/utils/workflow-poll'
import {
  applyMessageEvent,
  buildCreatedProcess,
  isTerminalStatus,
  sortLiveRecords,
  withLiveRecord,
} from '@/utils/workflow-run-state'
import { formatDuration, scoreTone } from '@/views/homework/utils/format'
import {
  DEMO_AI_DESCRIPTION,
  DEMO_FILE_TREE,
  DEMO_KNOWLEDGE_LIST,
} from '@/views/homework/mock/workflow-test'
import workflowCanvas from '@/views/homework/mock/workflow-canvas.json'
import SquircleButton from './components/SquircleButton.vue'

/* ---------- 执行参数 ---------- */
const form = reactive({
  /** 文件树：每行一个相对路径，目录以 / 结尾；留空则使用后端内置模拟数据 */
  fileTreeText: '',
  /** 知识库列表：逗号或换行分隔；留空则使用后端内置模拟数据 */
  knowledgeText: '',
  aiDescription: '',
  fullScore: 100,
})

/* ---------- 执行状态 ---------- */
/** 是否正在执行（已提交但未到终态） */
const running = ref(false)
/** 执行会话 id（SSE 的 `created` 事件下发，轮询时作为 workflow_id 回传） */
const executeId = ref('')
/** 当前执行状态快照（前端按事件增量拼装为 WorkflowProcessRespVO 同构对象） */
const process = ref(null)
/** 提交失败 / 推送中断等错误信息 */
const errorText = ref('')
/** SSE 流句柄（`{ abort }`）；`null` 表示当前未订阅实时事件 */
let streamHandle = null
/** 轮询句柄（SSE 不可用时的兜底通道） */
let pollHandle = null
/** 是否已降级为轮询（仅用于状态文案） */
const pollingMode = ref(false)
/** 等待用户输入的待确认清单（弹窗关闭后仍可重新打开） */
const pendingConfirm = ref(null)
/** 循环内逐条回传的批改结果，按序号存放（并发迭代时可能乱序到达，展示时再排序） */
const liveRecords = ref({})

/** 业务结果：执行中为 null，终态由后端返回 */
const result = computed(() => process.value?.result || null)
/** 节点级执行结果（按画布节点顺序） */
const nodeResults = computed(() => process.value?.nodeResults || [])
/** 逐份批改结果：终态以服务端结果为准，执行中先用流式增量 */
const records = computed(() => {
  const finalRecords = result.value?.records || []
  return finalRecords.length ? finalRecords : sortLiveRecords(liveRecords.value)
})
/** 结束节点渲染出的最终展示内容 */
const content = computed(() => result.value?.content || '')
/** 本次批改满分：终态取后端口径，执行中先用表单值（流式记录据此换算档位） */
const fullScore = computed(() =>
  result.value?.fullScore ?? (Number.isFinite(form.fullScore) ? form.fullScore : 100),
)
/** 已执行节点顺序 */
const nodeTrace = computed(() => result.value?.nodeTrace || [])
/** 本次执行状态：1=执行中，2=执行成功，3=执行失败，4=等待用户输入 */
const exeStatus = computed(() => process.value?.exeHistoryStatus ?? null)

const STATUS_LABELS = {
  [WORKFLOW_EXE_STATUS.RUNNING]: '执行中',
  [WORKFLOW_EXE_STATUS.WAITING]: '等待确认',
  [WORKFLOW_EXE_STATUS.FINISHED]: '执行完成',
  [WORKFLOW_EXE_STATUS.FAILED]: '执行失败',
}

const statusLabel = computed(() => {
  if (!executeId.value) return '未开始'
  if (exeStatus.value === WORKFLOW_EXE_STATUS.WAITING) return '等待确认'
  if (running.value) return '执行中'
  return STATUS_LABELS[exeStatus.value] || '未开始'
})

const badgeTone = computed(() => {
  if (exeStatus.value === WORKFLOW_EXE_STATUS.WAITING) return 'is-warning'
  if (running.value) return 'is-warning'
  if (exeStatus.value === WORKFLOW_EXE_STATUS.FINISHED) return 'is-success'
  if (exeStatus.value === WORKFLOW_EXE_STATUS.FAILED) return 'is-danger'
  return ''
})

/** 卡头副行：一句话交代当前会话与耗时 */
const metaLine = computed(() => {
  if (!executeId.value) return '点击「开始执行」提交任务后，进度由后端实时推送（断线自动转为轮询）'
  const parts = [`会话 ${executeId.value}`, statusLabel.value]
  const costMs = process.value?.exeCostMs
  if (Number.isFinite(costMs)) parts.push(`耗时 ${formatDuration(costMs)}`)
  if (pollingMode.value) parts.push('轮询兜底')
  return parts.join(' · ')
})

/** 卡头右侧的失败原因（仅失败 / 出错时非空） */
const failReason = computed(() => {
  if (errorText.value) return errorText.value
  return exeStatus.value === WORKFLOW_EXE_STATUS.FAILED ? process.value?.message || '' : ''
})

/** 是否可重置 */
const canReset = computed(
  () => !running.value && (Boolean(executeId.value) || records.value.length > 0),
)

/* ---------- 参数解析 ---------- */
function parseList(text) {
  return text
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 组装执行入参：画布必传；留空的项不下发，由后端回退到内置模拟数据 */
function buildRunPayload() {
  // 画布来自本页内置的本地文件：/workflow/run 的 schemaJson 为必填，为空会报「工作流定义为空，无法执行」
  const payload = { schemaJson: JSON.stringify(workflowCanvas) }
  const fileTree = parseList(form.fileTreeText)
  if (fileTree.length) payload.fileTree = fileTree
  const knowledgeList = parseList(form.knowledgeText)
  if (knowledgeList.length) payload.knowledgeList = knowledgeList
  const description = form.aiDescription.trim()
  if (description) payload.aiDescription = description
  if (Number.isFinite(form.fullScore)) payload.fullScore = form.fullScore
  return payload
}

/* ---------- 执行状态落地 ---------- */
/** 用补丁更新状态快照（保持 WorkflowProcessRespVO 同构，模板与诊断弹窗可复用） */
function patchProcess(patch) {
  process.value = { ...process.value, ...patch }
}

/** 落地一份完整状态响应（`completed` 事件 / 轮询结果 / 断线重连后使用） */
function applyProcess(next) {
  if (!next) return
  process.value = next
  if (next.exeHistoryStatus === WORKFLOW_EXE_STATUS.WAITING && next.pendingConfirm) {
    pendingConfirm.value = next.pendingConfirm
    openConfirm(next.pendingConfirm)
  }
}

function resetRunState() {
  process.value = null
  errorText.value = ''
  pendingConfirm.value = null
  liveRecords.value = {}
  pollingMode.value = false
  confirmState.visible = false
}

/** 停止 SSE 流：重新提交 / 重置 / 页面卸载时必须调用，避免残留连接 */
function stopStream() {
  streamHandle?.abort()
  streamHandle = null
}

/** 停止轮询：重新提交 / 重置 / 页面卸载时必须调用，避免残留定时器 */
function stopPoll() {
  pollHandle?.stop()
  pollHandle = null
}

/** 重新开始：保留执行参数，清空执行结果与会话 */
function handleReset() {
  stopStream()
  stopPoll()
  resetRunState()
  executeId.value = ''
  running.value = false
}

/* ---------- SSE 事件处理 ---------- */
/** `message` 事件分发：把事件累积成状态快照（见 utils/workflow-run-state） */
function applyMessage(message) {
  const { process: nextProcess, pendingConfirm: nextPending, liveRecord } = applyMessageEvent(process.value, message)
  if (nextProcess !== process.value) {
    process.value = nextProcess
  }
  if (liveRecord) {
    liveRecords.value = withLiveRecord(liveRecords.value, liveRecord.index, liveRecord.record)
  }
  if (nextPending) {
    pendingConfirm.value = nextPending
    openConfirm(nextPending)
  }
}

/** SSE 事件入口 */
function handleStreamEvent(event, data) {
  switch (event) {
    case WORKFLOW_STREAM_EVENT.CREATED:
      executeId.value = data?.executeId || ''
      process.value = buildCreatedProcess(data)
      ElMessage.success('已提交执行任务，正在实时推送执行进度…')
      break
    case WORKFLOW_STREAM_EVENT.MESSAGE:
      applyMessage(data)
      break
    case WORKFLOW_STREAM_EVENT.COMPLETED:
      applyProcess(data)
      running.value = false
      ElMessage.success(`执行完成，共 ${data?.result?.records?.length || 0} 份批改结果`)
      break
    case WORKFLOW_STREAM_EVENT.ERROR:
      errorText.value = data?.message || '工作流执行失败'
      running.value = false
      ElMessage.error(errorText.value)
      break
    default:
      break
  }
}

/** 降级为轮询：SSE 断开 / 失败后仍能取回后续进度与终态 */
function switchToPoll(tip) {
  if (!executeId.value || pollHandle) return
  stopStream()
  pollingMode.value = true
  ElMessage.warning(tip)
  pollHandle = pollWorkflowProcess(executeId.value, {
    onProcess: applyProcess,
    onWaiting: (waitingProcess) => applyProcess(waitingProcess),
    onFinish: (finalProcess) => {
      applyProcess(finalProcess)
      running.value = false
      pollHandle = null
      if (finalProcess?.exeHistoryStatus === WORKFLOW_EXE_STATUS.FINISHED) {
        ElMessage.success(`执行完成，共 ${finalProcess?.result?.records?.length || 0} 份批改结果`)
      } else {
        ElMessage.error(finalProcess?.message || '工作流执行失败')
      }
    },
    onError: (error) => {
      running.value = false
      pollHandle = null
      errorText.value = error?.message || '查询执行状态失败'
      ElMessage.error(errorText.value)
    },
  })
}

/* ---------- 提交执行 + 订阅事件流 ---------- */
function handleRun() {
  if (running.value) return
  stopStream()
  stopPoll()
  executeId.value = ''
  resetRunState()
  running.value = true
  streamHandle = runWorkflowStream(buildRunPayload(), {
    onEvent: handleStreamEvent,
    onError: (error) => {
      if (!executeId.value) {
        // 提交阶段就被拦下（画布为空 / 非法）：此时尚无会话，直接展示错误
        running.value = false
        errorText.value = error?.message || '提交执行失败'
        ElMessage.error(errorText.value)
        return
      }
      switchToPoll('实时推送失败，已切换为轮询')
    },
    onDone: (reason) => {
      // 等待用户输入时服务端不会发 done（连接保持打开）；连接意外结束则降级轮询
      if (reason === 'closed' && running.value && !isTerminalStatus(exeStatus.value)) {
        switchToPoll('实时连接已断开，已切换为轮询')
      }
    },
  })
}

/* ---------- 人工确认弹窗 ---------- */
const confirmState = reactive({
  visible: false,
  nodeName: '',
  items: [],
  selected: [],
  submitting: false,
})

/** 打开确认弹窗：默认勾选后端给出的 defaultPlan（已打开时保留用户当前勾选） */
function openConfirm(pending) {
  if (!pending || confirmState.visible) return
  confirmState.nodeName = pending.nodeName || pending.nodeId || ''
  confirmState.items = Array.isArray(pending.plan) ? [...pending.plan] : []
  const defaults = Array.isArray(pending.defaultPlan) ? pending.defaultPlan : []
  const paths = confirmState.items.map((item) => item.filePath).filter(Boolean)
  confirmState.selected = defaults.length ? paths.filter((path) => defaults.includes(path)) : []
  confirmState.visible = true
}

function selectAllConfirm() {
  confirmState.selected = confirmState.items.map((item) => item.filePath).filter(Boolean)
}

function invertConfirm() {
  const selected = new Set(confirmState.selected)
  confirmState.selected = confirmState.items
    .map((item) => item.filePath)
    .filter((path) => path && !selected.has(path))
}

/** 提交确认清单：恢复后的事件继续从**原事件流**推来 */
async function submitConfirm() {
  const sessionId = process.value?.executeId || executeId.value
  if (!sessionId) return
  if (!confirmState.selected.length) {
    ElMessage.warning('请至少勾选一份需要批改的作业')
    return
  }
  confirmState.submitting = true
  try {
    await resumeWorkflow({ executeId: sessionId, confirmedPlan: [...confirmState.selected] })
    confirmState.visible = false
    patchProcess({
      exeHistoryStatus: WORKFLOW_EXE_STATUS.RUNNING,
      message: '已提交确认清单，正在执行…',
    })
    ElMessage.success('已提交确认清单，继续执行')
  } catch (e) {
    ElMessage.error(e?.message || '提交确认清单失败')
  } finally {
    confirmState.submitting = false
  }
}

/** 取消执行：后端无取消接口，仅停止本地订阅（会话仍在等待，可在超时前重新确认） */
function cancelConfirm() {
  confirmState.visible = false
  stopStream()
  stopPoll()
  running.value = false
  patchProcess({ message: '已停止接收进度（后端会话仍在等待确认，可点「处理确认清单」重新提交）' })
  ElMessage.info('已停止接收进度')
}

onBeforeUnmount(() => {
  stopStream()
  stopPoll()
})

/* ---------- 节点状态展示 ---------- */
const NODE_STATUS_TEXT = {
  [WORKFLOW_NODE_STATUS.PENDING]: '未执行',
  [WORKFLOW_NODE_STATUS.RUNNING]: '执行中',
  [WORKFLOW_NODE_STATUS.SUCCESS]: '成功',
  [WORKFLOW_NODE_STATUS.FAILED]: '失败',
}

function nodeStatusText(nodeStatus) {
  return NODE_STATUS_TEXT[nodeStatus] || '未执行'
}

function nodeTagType(node) {
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.SUCCESS) return 'success'
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.FAILED) return 'danger'
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.RUNNING) return 'warning'
  return 'info'
}

function nodeTone(node) {
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.SUCCESS) return 'is-success'
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.FAILED) return 'is-danger'
  if (node.nodeStatus === WORKFLOW_NODE_STATUS.RUNNING) return 'is-running'
  return ''
}

/* ---------- 执行参数快捷操作 ---------- */
function fillDemoInputs() {
  form.fileTreeText = DEMO_FILE_TREE.join('\n')
  form.knowledgeText = DEMO_KNOWLEDGE_LIST.join(', ')
  form.aiDescription = DEMO_AI_DESCRIPTION
  form.fullScore = 100
}

function clearInputs() {
  form.fileTreeText = ''
  form.knowledgeText = ''
  form.aiDescription = ''
  form.fullScore = 100
}

/* ---------- 诊断弹窗 ---------- */
const dialog = reactive({ visible: false, title: '', content: '' })

function openState() {
  if (!process.value) return
  dialog.title = `执行状态 · ${process.value.executeId}`
  dialog.content = JSON.stringify(process.value, null, 2)
  dialog.visible = true
}

/* ---------- 结果展示 ---------- */
/** 得分档位：换算成百分制后再取色（满分由执行参数决定） */
function scoreClass(record) {
  const full = fullScore.value || 100
  const percent = Number.isFinite(record.totalScore) ? (record.totalScore * 100) / full : NaN
  return `score--${scoreTone(percent)}`
}

function dimensionText(dimensions) {
  if (!dimensions) return '—'
  const values = [dimensions.accuracy, dimensions.completeness, dimensions.expression]
  return values.map((value) => (Number.isFinite(value) ? value.toFixed(0) : '—')).join(' / ')
}
</script>

<template>
  <div class="workflow-test">
    <!-- ============ 卡头：状态 + 诊断入口 ============ -->
    <header class="test-bar">
      <div class="test-bar__status">
        <span class="test-bar__badge" :class="badgeTone">{{ statusLabel }}</span>
        <span class="test-bar__chip" title="后端 tb-module-workflow 为 MVP 阶段：本地画布 + 模拟大模型/知识库">
          后端模拟数据
        </span>
        <p class="test-bar__meta" :title="metaLine">{{ metaLine }}</p>
        <span v-if="failReason" class="test-bar__fail" :title="failReason">{{ failReason }}</span>
      </div>
      <div class="test-bar__actions">
        <button type="button" class="ghost-chip" :disabled="!process" @click="openState">
          <el-icon><DataLine /></el-icon>
          <span>执行状态</span>
        </button>
        <button
          type="button"
          class="ghost-chip ghost-chip--danger"
          :disabled="!canReset"
          @click="handleReset"
        >
          <el-icon><Refresh /></el-icon>
          <span>重置</span>
        </button>
      </div>
    </header>

    <div class="test-grid">
      <!-- ============ 步骤 1：执行参数 ============ -->
      <section class="panel">
        <h3 class="panel__title">
          <span class="panel__step">1</span>
          执行参数
          <span class="panel__hint">留空即用后端内置模拟数据</span>
        </h3>

        <label class="field">
          <span class="field__label">文件夹文件树（每行一个路径，目录以 / 结尾）</span>
          <el-input
            v-model="form.fileTreeText"
            type="textarea"
            :rows="6"
            resize="vertical"
            placeholder="留空使用后端内置模拟文件树"
          />
        </label>
        <div class="field__actions">
          <button type="button" class="text-chip" @click="fillDemoInputs">填入演示参数</button>
          <button type="button" class="text-chip" @click="clearInputs">清空</button>
        </div>

        <label class="field">
          <span class="field__label">知识库列表（逗号分隔）</span>
          <el-input v-model="form.knowledgeText" placeholder="留空使用后端内置知识库" />
        </label>

        <label class="field">
          <span class="field__label">AI 描述说明</span>
          <el-input
            v-model="form.aiDescription"
            type="textarea"
            :rows="3"
            resize="vertical"
            placeholder="留空使用后端内置批改说明"
          />
        </label>

        <label class="field field--inline">
          <span class="field__label">本次满分</span>
          <el-input-number v-model="form.fullScore" :min="1" :max="200" :step="10" />
        </label>

        <SquircleButton block :height="40" :disabled="running" @click="handleRun">
          <span class="action-text">{{ running ? '执行中…' : '开始执行' }}</span>
        </SquircleButton>
      </section>

      <!-- ============ 步骤 2：执行进度 ============ -->
      <section class="panel">
        <h3 class="panel__title">
          <span class="panel__step">2</span>
          执行进度
          <span class="panel__hint">SSE 实时推送，断线自动轮询</span>
        </h3>

        <button
          v-if="pendingConfirm && !confirmState.visible"
          type="button"
          class="text-chip text-chip--strong"
          @click="openConfirm(pendingConfirm)"
        >
          处理确认清单（{{ pendingConfirm.plan?.length || 0 }} 项）
        </button>

        <template v-if="nodeResults.length">
          <p class="panel__note">
            {{ process.message || '正在执行，节点状态实时刷新' }}
          </p>

          <ul class="node-list">
            <li v-for="node in nodeResults" :key="node.nodeId" class="node-item">
              <span class="node-item__dot" :class="nodeTone(node)" />
              <span class="node-item__main">
                <span class="node-item__name" :title="node.nodeId">
                  {{ node.nodeName || node.nodeId }}
                </span>
                <span class="node-item__type">{{ node.nodeType }}</span>
              </span>
              <span class="node-item__cost">{{ node.nodeExeCost }}</span>
              <el-tag size="small" :type="nodeTagType(node)" effect="plain">
                {{ nodeStatusText(node.nodeStatus) }}
              </el-tag>
            </li>
          </ul>

          <p v-if="failReason" class="panel__error">{{ failReason }}</p>
        </template>

        <p v-else class="panel__empty">
          点击「开始执行」后，后端以 SSE 实时推送进度：每个节点的状态与耗时、循环内每完成一份批改的
          结果都会立刻出现在这里；连接异常时本页自动改为每 2 秒轮询兜底。
        </p>
      </section>

      <!-- ============ 步骤 3：批改结果 ============ -->
      <section class="panel panel--wide">
        <h3 class="panel__title">
          <span class="panel__step">3</span>
          批改结果
          <span v-if="running" class="panel__hint">执行中…</span>
          <span v-else-if="records.length" class="panel__hint">
            共 {{ records.length }} 份 · 满分 {{ fullScore || 100 }}
          </span>
        </h3>

        <template v-if="records.length">
          <el-table :data="records" size="small" class="record-table">
            <el-table-column prop="fileName" label="作业文件" min-width="220" show-overflow-tooltip />
            <el-table-column label="总分" width="90" align="center">
              <template #default="{ row }">
                <span class="score" :class="scoreClass(row)">
                  {{ Number.isFinite(row.totalScore) ? row.totalScore : '—' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="grade" label="等级" width="90" align="center" />
            <el-table-column label="准确性 / 完整性 / 表达" width="170" align="center">
              <template #default="{ row }">{{ dimensionText(row.dimensions) }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="教师评语" min-width="260" show-overflow-tooltip />
          </el-table>

          <details v-if="content" class="content-block">
            <summary>结束节点输出（{{ content.length }} 字）</summary>
            <pre class="content-block__body">{{ content }}</pre>
          </details>
        </template>

        <p v-else-if="running" class="panel__empty">
          循环节点每完成一份批改就会实时回传一行，无需等整个循环结束。
        </p>
        <p v-else class="panel__empty">执行完成后，逐份批改结果（分数 / 三维度 / 评语）会出现在这里。</p>
      </section>
    </div>

    <!-- ============ 节点轨迹 ============ -->
    <footer v-if="nodeTrace.length" class="trace">
      <span class="trace__label">节点轨迹</span>
      <ol class="trace__list">
        <li v-for="(node, index) in nodeTrace" :key="`${node}-${index}`" class="trace__node">
          {{ node }}
        </li>
      </ol>
    </footer>

    <!-- ============ 人工确认弹窗（等待用户输入） ============ -->
    <el-dialog
      v-model="confirmState.visible"
      title="人工确认：请勾选本次需要批改的作业"
      width="640px"
      top="8vh"
      :close-on-click-modal="false"
    >
      <p class="confirm__hint">
        执行到「{{ confirmState.nodeName }}」节点暂停，确认清单后继续（提交后同一条连接继续推送）。
      </p>
      <div class="confirm__tools">
        <button type="button" class="text-chip" @click="selectAllConfirm">全选</button>
        <button type="button" class="text-chip" @click="invertConfirm">反选</button>
        <span class="confirm__count">已选 {{ confirmState.selected.length }} / {{ confirmState.items.length }}</span>
      </div>
      <el-checkbox-group v-model="confirmState.selected" class="confirm__list">
        <label v-for="item in confirmState.items" :key="item.filePath" class="confirm__item">
          <el-checkbox :value="item.filePath">
            <span class="confirm__path">{{ item.filePath }}</span>
            <span v-if="item.reason" class="confirm__reason">{{ item.reason }}</span>
          </el-checkbox>
        </label>
      </el-checkbox-group>
      <template #footer>
        <el-button :disabled="confirmState.submitting" @click="cancelConfirm">停止接收进度</el-button>
        <el-button type="primary" :loading="confirmState.submitting" @click="submitConfirm">
          提交确认并继续
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 诊断弹窗（执行状态原始响应） ============ -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="760px" top="6vh">
      <pre class="json-view">{{ dialog.content }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
/*
 * 布局沿用作业批改页的双栏骨架（.studio > .studio-body）思路，但本页是三块纵向流程，
 * 故用自适应网格：窄屏单列、宽屏「参数 | 进度」并排 + 结果整行。
 * 颜色 / 描边 / 圆角一律取语义 Token，明暗主题自动跟随（docs/09-样式规范.md）。
 */
.workflow-test {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  min-height: calc(100vh - 2rem);
  padding: 0.9rem;
  background: var(--tb-bg-page);
  color: var(--tb-text-primary);
}

/* ---------- 卡头 ---------- */
.test-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0.85rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
}

.test-bar__status {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

.test-bar__badge {
  flex: 0 0 auto;
  padding: 3px 10px;
  border-radius: var(--tb-radius-xs);
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

.test-bar__badge.is-success {
  color: var(--tb-success);
  background: var(--tb-success-soft);
  border-color: var(--tb-success);
}

.test-bar__badge.is-warning {
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border-color: var(--tb-warning);
}

.test-bar__badge.is-danger {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: var(--tb-danger);
}

.test-bar__chip {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: var(--tb-radius-xs);
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-text-tertiary);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
  cursor: help;
}

.test-bar__meta {
  min-width: 0;
  margin: 0;
  font-size: 0.72rem;
  color: var(--tb-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.test-bar__fail {
  min-width: 0;
  font-size: 0.72rem;
  color: var(--tb-danger);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.test-bar__actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* ---------- 面板网格 ---------- */
.test-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 0.9rem;
  align-items: start;
}

.panel {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
}

.panel--wide {
  grid-column: 1 / -1;
}

.panel__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.2rem;
  font-size: 0.86rem;
  font-weight: 700;
}

.panel__step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 0.7rem;
  color: var(--tb-on-primary);
  background: var(--tb-primary);
}

.panel__hint {
  margin-left: auto;
  font-size: 0.68rem;
  font-weight: 500;
  color: var(--tb-text-tertiary);
}

.panel__note {
  margin: 0;
  padding: 0.5rem 0.7rem;
  border-radius: var(--tb-radius-sm);
  font-size: 0.76rem;
  line-height: 1.6;
  color: var(--tb-text-secondary);
  background: var(--tb-primary-soft);
  border: var(--tb-border-width) solid var(--tb-primary-border);
}

.panel__error {
  margin: 0;
  padding: 0.5rem 0.7rem;
  border-radius: var(--tb-radius-sm);
  font-size: 0.74rem;
  line-height: 1.6;
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border: var(--tb-border-width) solid var(--tb-danger);
}

.panel__empty {
  margin: 0;
  padding: 1.2rem 0.7rem;
  font-size: 0.76rem;
  line-height: 1.8;
  text-align: center;
  color: var(--tb-text-tertiary);
}

/* ---------- 表单字段 ---------- */
.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field--inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.field__label {
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.field__actions {
  display: flex;
  gap: 0.5rem;
  margin-top: -0.2rem;
}

/* ---------- 文字键 / 幽灵键（与作业批改页同一套规格） ---------- */
.text-chip {
  padding: 0;
  border: none;
  background: none;
  font-size: 0.72rem;
  color: var(--tb-primary-text);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.text-chip:hover {
  color: var(--tb-primary);
}

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

.ghost-chip--danger:hover:not(:disabled) {
  color: var(--tb-danger);
  border-color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

/* ---------- 节点执行进度 ---------- */
.node-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 340px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.6rem;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

.node-item__dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tb-text-tertiary);
}

.node-item__dot.is-running {
  background: var(--tb-warning);
}

.node-item__dot.is-success {
  background: var(--tb-success);
}

.node-item__dot.is-danger {
  background: var(--tb-danger);
}

.node-item__main {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  flex: 1;
  min-width: 0;
}

.node-item__name {
  font-size: 0.76rem;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.node-item__type {
  font-size: 0.66rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--tb-text-tertiary);
}

.node-item__cost {
  flex: 0 0 auto;
  font-size: 0.68rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--tb-text-tertiary);
}

.action-text {
  font-weight: 700;
  letter-spacing: 0.02em;
}

/* ---------- 结果 ---------- */
.record-table {
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  overflow: hidden;
}

.score {
  font-weight: 700;
}

.score--excellent {
  color: var(--tb-success);
}

.score--good {
  color: var(--tb-primary);
}

.score--pass {
  color: var(--tb-warning);
}

.score--weak {
  color: var(--tb-danger);
}

.score--pending {
  color: var(--tb-text-tertiary);
}

.content-block {
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg-sunken);
}

.content-block summary {
  padding: 0.45rem 0.7rem;
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
  cursor: pointer;
}

.content-block__body {
  max-height: 260px;
  margin: 0;
  padding: 0 0.7rem 0.7rem;
  overflow: auto;
  font-size: 0.72rem;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--tb-text-primary);
}

/* ---------- 轨迹 ---------- */
.trace {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.85rem;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
}

.trace__label {
  flex: 0 0 auto;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--tb-text-tertiary);
}

.trace__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace__node {
  padding: 2px 8px;
  border-radius: var(--tb-radius-full);
  font-size: 0.68rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

/* ---------- 诊断弹窗 ---------- */
.json-view {
  max-height: 60vh;
  margin: 0;
  padding: 0.7rem;
  overflow: auto;
  border-radius: var(--tb-radius-sm);
  font-size: 0.72rem;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--tb-text-primary);
  background: var(--tb-bg-sunken);
}

/* ---------- 人工确认弹窗 ---------- */
.text-chip--strong {
  align-self: flex-start;
  font-weight: 600;
}

.confirm__hint {
  margin: 0 0 0.6rem;
  font-size: 0.76rem;
  line-height: 1.7;
  color: var(--tb-text-secondary);
}

.confirm__tools {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  margin-bottom: 0.5rem;
}

.confirm__count {
  margin-left: auto;
  font-size: 0.72rem;
  color: var(--tb-text-tertiary);
}

.confirm__list {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  max-height: 46vh;
  overflow: auto;
}

.confirm__item {
  display: block;
  padding: 0.25rem 0;
  cursor: pointer;
}

.confirm__path {
  font-size: 0.76rem;
  color: var(--tb-text-primary);
}

.confirm__reason {
  margin-left: 0.5rem;
  font-size: 0.7rem;
  color: var(--tb-text-tertiary);
}

@media (max-width: 1024px) {
  .test-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .ghost-chip span {
    display: none;
  }
}
</style>
