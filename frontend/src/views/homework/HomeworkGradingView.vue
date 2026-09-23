<template>
  <div class="homework-studio">
    <div class="studio-body">
      <!-- ============ 左侧配置面板 ============ -->
      <ConfigPanel
        v-model:space-id="config.spaceId"
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

      <!-- ============ 右侧工作区 ============ -->
      <section class="stage">
        <Transition name="stage-swap" mode="out-in">
          <!-- 结果列表 -->
          <div v-if="!activeRecord" key="list" class="stage-body">
            <div class="display-box">
              <div class="display-box__toolbar">
                <div class="display-box__status">
                  <span class="display-box__badge">{{ displayBadge }}</span>
                  <span class="demo-chip" title="当前为前端演示数据，接入后端后即为真实批改结果">
                    演示数据
                  </span>
                </div>
                <div class="display-box__actions">
                  <button type="button" class="ghost-chip" @click="openHistoryDrawer">
                    <el-icon><Clock /></el-icon>
                    <span>历史记录</span>
                  </button>
                  <button
                    type="button"
                    class="ghost-chip"
                    :disabled="!canExport"
                    @click="handleExport"
                  >
                    <el-icon><Download /></el-icon>
                    <span>导出成绩表</span>
                  </button>
                  <button
                    type="button"
                    class="ghost-chip"
                    :disabled="!canGrade"
                    @click="handleGrade"
                  >
                    <el-icon><Refresh /></el-icon>
                    <span>再次批改</span>
                  </button>
                </div>
              </div>
              <div
                class="display-box__canvas"
                :class="{ 'display-box__canvas--results': records.length && !(grading && !records.length) }"
              >
                <ResultGrid
                  :records="records"
                  :grading="grading"
                  :uploading="gradingPhase === 'upload'"
                  :upload-percent="uploadPercent"
                  :done-count="records.length"
                  :total-count="folder?.files?.length ?? 0"
                  :stage="currentStage"
                  @open="openRecord"
                />
              </div>
            </div>
          </div>

          <!-- 单份详情 -->
          <div v-else key="detail" class="stage-body">
            <SubmissionDetail
              :record="activeRecord"
              :description="activeDescription"
              :workflow-label="activeWorkflowLabel"
              @back="closeDetail"
            />
          </div>
        </Transition>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Download, Refresh } from '@element-plus/icons-vue'
import { getSpaceList } from '@/api/knowledgebase'
import {
  DEMO_KB_LIST,
  buildHistoryItem,
  gradeByFiles,
  loadHistory,
  nextTaskId,
  pushHistory,
} from '@/views/homework/mock/grading'
import { exportScores } from '@/views/homework/utils/export'
import { defaultWorkflowId, loadWorkflows } from '@/views/homework/utils/workflows'
import ConfigPanel from './components/ConfigPanel.vue'
import HistoryDrawer from './components/HistoryDrawer.vue'
import ResultGrid from './components/ResultGrid.vue'
import SubmissionDetail from './components/SubmissionDetail.vue'

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
  spaceId: DEFAULT_KB.id,
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

const selectedKbName = computed(
  () => kbOptions.value.find((kb) => kb.id === config.spaceId)?.name ?? DEFAULT_KB.name,
)

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    const list = await getSpaceList()
    // 知识库接口无数据时回落到演示数据，保证下拉始终可预览
    knowledgeBases.value = list?.length ? list : DEMO_KB_LIST
  } catch {
    // 后端不可用时不阻断页面，直接使用演示知识库
    knowledgeBases.value = DEMO_KB_LIST
  } finally {
    kbLoading.value = false
  }
}

/* ---------- 作业文件夹 ---------- */
const folder = ref(null)

/* ---------- 批改 ---------- */
const grading = ref(false)
/** 批改阶段：''（空闲）/ upload（上传中）/ grading（批改中） */
const gradingPhase = ref('')
const uploadPercent = ref(0)
const records = ref([])
/** 当前批改阶段（由所选工作流的节点驱动，用于右侧展示进度） */
const currentStage = ref(null)
/** 当前结果对应的任务信息：{ taskId, description, workflow, exportFormat } */
const taskMeta = ref(null)

const canGrade = computed(
  () => Boolean(folder.value?.files?.length)
    && Boolean(config.description.trim())
    && !grading.value,
)

const canExport = computed(() => records.value.length > 0 && !grading.value)

const displayBadge = computed(() => {
  if (grading.value) {
    const total = folder.value?.files?.length ?? 0
    return gradingPhase.value === 'upload'
      ? `正在上传 ${total} 份作业`
      : `正在批改 ${records.value.length}/${total} 份`
  }
  if (records.value.length) {
    return activeHistoryId.value
      ? `历史记录 · 已载入 ${records.value.length} 份`
      : `已批改 ${records.value.length} 份作业`
  }
  return folder.value ? folder.value.name : '未选择作业文件夹'
})

async function handleGrade() {
  if (grading.value) return
  if (!folder.value?.files?.length) {
    ElMessage.warning('请先选择作业文件夹')
    return
  }
  if (!config.description.trim()) {
    ElMessage.warning('请填写批改要求（AI 描述说明）')
    return
  }

  const description = config.description.trim()
  const taskId = nextTaskId()
  grading.value = true
  gradingPhase.value = 'upload'
  uploadPercent.value = 0
  currentStage.value = null
  records.value = []
  activeRecord.value = null
  activeHistoryId.value = null

  try {
    const list = await gradeByFiles(folder.value.files, {
      description,
      kbName: selectedKbName.value,
      workflow: selectedWorkflow.value,
      workflowId: config.workflow,
      workflowLabel: workflowName(config.workflow),
      exportFormat: config.exportFormat,
    }, {
      onUploadProgress: (percent) => {
        uploadPercent.value = percent
        if (percent >= 100) gradingPhase.value = 'grading'
      },
      onStage: (stage) => {
        currentStage.value = stage
      },
      onRecord: (record) => {
        records.value = [...records.value, record]
      },
    })

    taskMeta.value = {
      taskId,
      description,
      workflow: config.workflow,
      exportFormat: config.exportFormat,
    }

    if (!list.length) {
      ElMessage.error('未生成批改结果，请重试')
      return
    }
    const failed = list.filter((item) => item.status !== 1).length
    if (failed) {
      ElMessage.warning(`已完成批改，其中 ${failed} 份未能批改`)
    } else {
      ElMessage.success(`已完成批改：共 ${list.length} 份作业`)
    }

    // 写入演示历史记录（接入后端后由后端落库）
    pushHistory({
      ...buildHistoryItem(taskId, {
        description,
        kbName: selectedKbName.value,
        workflowId: config.workflow,
        workflowLabel: workflowName(config.workflow),
        exportFormat: config.exportFormat,
      }, list),
      records: list,
    })
    refreshHistory()
  } catch (e) {
    ElMessage.error(e?.message || '批改失败，请重试')
  } finally {
    grading.value = false
    gradingPhase.value = ''
    uploadPercent.value = 0
    currentStage.value = null
  }
}

/* ---------- 导出 ---------- */
function handleExport() {
  if (!records.value.length) {
    ElMessage.warning('暂无可导出的批改结果')
    return
  }
  const format = taskMeta.value?.exportFormat || config.exportFormat
  const name = exportScores(format, {
    taskId: taskMeta.value?.taskId,
    records: records.value,
  })
  ElMessage.success(`成绩汇总表已生成（${format === 'csv' ? 'CSV' : 'Excel'}）：${name}`)
}

/* ---------- 单份详情 ---------- */
const activeRecord = ref(null)

const activeDescription = computed(
  () => taskMeta.value?.description || config.description,
)

const activeWorkflowLabel = computed(
  () => workflowName(taskMeta.value?.workflow || config.workflow),
)

function openRecord(record) {
  activeRecord.value = record
}

function closeDetail() {
  activeRecord.value = null
}

/* ---------- 历史记录（演示模式：本地存储） ---------- */
const historyOpen = ref(false)
const historyRecords = ref([])
const historyLoading = ref(false)
const activeHistoryId = ref(null)
const historyDetailLoadingId = ref(null)

async function refreshHistory() {
  historyLoading.value = true
  try {
    historyRecords.value = loadHistory()
  } catch {
    ElMessage.error('加载批改历史记录失败')
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
    activeRecord.value = null
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

  /* 页面级兼容别名：一律指向全局 Token（src/assets/theme.css），
     页面内既有的 var(--primary) 等引用无需逐个替换即可随主题换肤 */
  --primary: var(--tb-primary);
  --primary-dark: var(--tb-primary-text);
  --primary-soft: var(--tb-primary-soft);
  --accent: var(--tb-accent);
  --accent-deep: color-mix(in oklab, var(--tb-accent) 72%, #000000);
  --accent-soft: var(--tb-accent-soft);
  --accent-soft-2: var(--tb-accent-soft);

  /* Element Plus 主色与圆角交由全局 Token 层映射（原页面级 --el-* 覆盖已移除） */
}

.studio-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
}

/* ================= 右侧工作区 ================= */
.stage {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.stage-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
}

.display-box {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-radius: 0;
  background: var(--tb-bg-surface);
  border: var(--tb-border-width) solid var(--tb-border);
  overflow: hidden;
}

.display-box__toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0.85rem;
  border-bottom: 1px solid var(--tb-border);
}

.display-box__status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.display-box__badge {
  padding: 3px 10px;
  border-radius: 0;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--accent-deep);
  background: var(--accent-soft);
  border: 1px solid var(--accent-soft-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.demo-chip {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 0;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
  border: var(--tb-border-width) solid var(--tb-border);
  cursor: help;
}

/* 操作区整体不参与收缩：文字换行会被 30px 固定高度裁掉，
   空间不足时交由可省略的左侧状态标签让位 */
.display-box__actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ghost-chip {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  height: 30px;
  padding: 0 0.7rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  font-size: 0.74rem;
  white-space: nowrap;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.ghost-chip:hover:not(:disabled) {
  color: var(--primary-dark);
  border-color: var(--tb-primary-border);
  background: var(--primary-soft);
}

.ghost-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.display-box__canvas {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background-color: var(--tb-bg-sunken);
  background-image: radial-gradient(var(--tb-border) 1px, transparent 1px);
  background-size: 22px 22px;
}

.display-box__canvas--results {
  display: block;
  padding: 1.25rem;
  overflow: hidden;
}

/* ================= 模式切换过渡 ================= */
.stage-swap-enter-active {
  transition: opacity 0.24s ease, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.stage-swap-leave-active {
  transition: opacity 0.16s ease, transform 0.16s cubic-bezier(0.4, 0, 1, 1);
}

.stage-swap-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.stage-swap-leave-to {
  opacity: 0;
  transform: translateX(-22px);
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
}

@media (max-width: 720px) {
  .stage-body {
    flex-direction: column;
  }
}
</style>
