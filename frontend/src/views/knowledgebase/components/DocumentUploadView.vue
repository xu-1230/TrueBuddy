<template>
  <div class="doc-upload-view">
    <!-- 顶部工具栏：返回 + 空间名 -->
    <div class="kb-toolbar">
      <div class="kb-toolbar__left">
        <el-button text @click="backToList">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <el-divider direction="vertical" />
        <el-icon class="kb-toolbar__folder-icon"><FolderOpened /></el-icon>
        <span class="kb-toolbar__title" :title="space?.name">{{ space?.name }}</span>
        <el-divider direction="vertical" />
        <span class="kb-toolbar__subtitle">添加文档</span>
      </div>
    </div>

    <!-- 步骤条 -->
    <div class="kb-panel">
      <el-steps :active="active" finish-status="success" align-center>
        <el-step title="上传" />
        <el-step title="分段预览" />
        <el-step title="数据处理" />
      </el-steps>
    </div>

    <!-- 步骤内容 -->
    <div class="kb-panel kb-panel--body">
      <!-- 第一步：上传 -->
      <div v-if="active === 0" class="kb-step">
        <el-upload
          drag
          multiple
          :auto-upload="false"
          v-model:file-list="fileList"
          accept=".docx,.doc,.pdf,.ppt,.pptx,.md,.txt"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到这里 或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">
              支持 docx / doc / pdf / ppt / md / txt，单文件不超过 50MB，可一次选择多个文件
            </div>
          </template>
        </el-upload>
        <div v-if="uploading" class="kb-progress">
          <el-progress :percentage="uploadProgress" :status="uploadStatus" />
          <p class="kb-progress__msg">{{ uploadMsg }}</p>
        </div>
      </div>

      <!-- 第二步：分段预览（多文件并行解析，左侧切换文件，右侧编辑其分片） -->
      <div v-else-if="active === 1" class="kb-preview">
        <aside class="kb-preview__side">
          <div class="kb-panel-title">自动分段与清洗（{{ docs.length }} 个文件）</div>
          <ul class="kb-file-list">
            <li
              v-for="(doc, idx) in docs"
              :key="doc.uid"
              class="kb-file-list__item"
              :class="{ 'kb-file-list__item--active': idx === activeDocIndex }"
              @click="activeDocIndex = idx"
            >
              <el-icon class="kb-file-list__icon"><Document /></el-icon>
              <div class="kb-file-list__body">
                <span class="kb-file-list__name" :title="doc.fileName">{{ doc.fileName }}</span>
                <span
                  class="kb-file-list__status"
                  :class="`kb-file-list__status--${docStatusMeta(doc.status).tagType}`"
                >
                  {{ docStatusText(doc) }}
                </span>
              </div>
            </li>
          </ul>
        </aside>

        <section v-if="currentDoc" class="kb-preview__main">
          <div class="kb-panel-title kb-panel-title--row">
            <span>分段列表 · {{ currentDoc.fileName }}</span>
            <el-button
              v-if="currentDoc.status === DOC_STATUS.PENDING_CONFIRM && currentDoc.chunks.length"
              text
              type="primary"
              size="small"
              @click="addChunk"
            >
              <el-icon><Plus /></el-icon>
              新增分片
            </el-button>
          </div>
          <div v-if="isProcessingStatus(currentDoc.status)" class="kb-parsing">
            <el-progress :percentage="currentDoc.progress || 0" />
            <p class="kb-parsing__msg">{{ processingHint(currentDoc.status) }}</p>
          </div>
          <el-alert
            v-else-if="currentDoc.status === DOC_STATUS.FAILED"
            class="kb-parse-error"
            :title="`解析失败：${currentDoc.error || '文档解析失败'}`"
            type="error"
            :closable="false"
            show-icon
          />
          <div v-else class="kb-chunk-list">
            <div
              v-for="(item, idx) in currentDoc.chunks"
              :key="item.uid"
              class="kb-chunk-card"
            >
              <div class="kb-chunk-card__head">
                <span class="kb-chunk-card__seq">#{{ idx + 1 }}</span>
                <div class="kb-chunk-card__ops">
                  <el-button text size="small" @click="toggleEdit(idx)">
                    {{ item.editing ? '完成' : '编辑' }}
                  </el-button>
                  <el-button text size="small" :disabled="idx === 0" @click="mergeWithPrev(idx)">
                    并入上一段
                  </el-button>
                  <el-button
                    text
                    size="small"
                    :disabled="!item.editing"
                    @click="splitChunk(idx)"
                  >
                    光标处拆分
                  </el-button>
                  <el-button text size="small" type="danger" @click="removeChunk(idx)">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-button>
                </div>
              </div>
              <el-input
                v-if="item.editing"
                v-model="item.content"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 12 }"
                class="kb-chunk-card__editor"
                placeholder="分片内容"
                @click="updateCursor(item, $event)"
                @keyup="updateCursor(item, $event)"
                @blur="updateCursor(item, $event)"
              />
              <MarkdownViewer v-else :value="item.content" class="kb-chunk-card__markdown" />
            </div>
            <el-empty
              v-if="!currentDoc.chunks.length"
              :image-size="90"
              description="未解析出分片内容"
            />
          </div>
        </section>
      </div>

      <!-- 第三步：数据处理（多文件并行/顺序向量化，逐项展示进度） -->
      <div v-else class="kb-step">
        <div class="kb-vectorize">
          <div v-for="doc in docs" :key="doc.uid" class="kb-file-item">
            <el-icon class="kb-file-item__icon"><Document /></el-icon>
            <span class="kb-file-item__name" :title="doc.fileName">{{ doc.fileName }}</span>
            <span class="kb-file-item__size">{{ formatSize(doc.fileSize) }}</span>
            <el-progress
              v-if="doc.vectorizing"
              class="kb-file-item__progress"
              :percentage="doc.vectorizeProgress"
              :status="doc.vectorizeStatus"
            />
            <el-tag v-else-if="doc.vectorized" type="success" size="small">已完成</el-tag>
            <el-tag
              v-else-if="doc.vectorizeError || doc.status === DOC_STATUS.FAILED"
              type="danger"
              size="small"
            >
              失败
            </el-tag>
            <el-tag v-else type="info" size="small">等待</el-tag>
          </div>
          <el-alert
            v-if="hasVectorizeFailure"
            title="部分文件处理失败，可点击「重试失败项」再次提交"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="kb-actions">
      <el-button v-if="active === 1" @click="prev">上一步</el-button>
      <el-button v-if="active === 0" type="primary" :loading="uploading" @click="doUpload">
        上传并解析{{ fileList.length > 1 ? `（${fileList.length}）` : '' }}
      </el-button>
      <el-button
        v-if="active === 1"
        type="primary"
        :disabled="hasParsing || !confirmableCount"
        :loading="anyVectorizing"
        @click="doVectorizeAll"
      >
        确认并向量化
      </el-button>
      <el-button
        v-if="active === 2 && hasVectorizeFailure && !anyVectorizing"
        type="primary"
        @click="doVectorizeAll"
      >
        重试失败项
      </el-button>
      <el-button v-if="active === 2" type="success" :disabled="anyVectorizing" @click="backToList">
        返回列表
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  ArrowLeft,
  Delete,
  Document,
  FolderOpened,
  Plus,
  UploadFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import { abandonDocument, getParseStatus, uploadDocument, vectorizeDocument } from '@/api/knowledgebase'
import { getToken } from '@/utils/auth'
import { DOC_STATUS, IN_PROGRESS_STATUS, PARSE_SETTLED_STATUS, docStatusMeta } from '../constants'

/** 解析状态轮询间隔（毫秒） */
const POLL_INTERVAL = 2000
/** 轮询次数上限（约 10 分钟），避免大文件异常时无限轮询 */
const MAX_POLL_COUNT = 300

const props = defineProps({
  space: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['back', 'uploaded'])

const active = ref(0)

// 第一步
const fileList = ref([])
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const uploadMsg = ref('')

// 第二步：多文件并行处理（每个文件独立解析、编辑与向量化）
const docs = ref([])
const activeDocIndex = ref(0)

/** 当前查看的文件 */
const currentDoc = computed(() => docs.value[activeDocIndex.value] || null)
/** 是否仍有文件处于处理中（待处理 / 解析中 / 待分片 / AI分片中） */
const hasParsing = computed(() => docs.value.some((doc) => isProcessingStatus(doc.status)))
/** 可向量化的文件数量（已解析出分片且尚未向量化） */
const confirmableCount = computed(
  () => docs.value.filter((doc) => doc.status === DOC_STATUS.PENDING_CONFIRM && doc.chunks.length).length,
)
/** 是否有文件正在向量化 */
const anyVectorizing = computed(() => docs.value.some((doc) => doc.vectorizing))
/** 是否存在向量化失败的文件 */
const hasVectorizeFailure = computed(() => docs.value.some((doc) => Boolean(doc.vectorizeError)))

let chunkUid = 0
let pollTimer = null
let pollCount = 0
let pollBusy = false

async function doUpload() {
  const raws = fileList.value.map((item) => item.raw).filter(Boolean)
  if (!raws.length) {
    ElMessage.warning('请先选择文件')
    return
  }
  // 重新上传前，先放弃上一批尚未完成向量化的文档
  await abandonIfNeeded()
  uploading.value = true
  uploadProgress.value = 30
  uploadMsg.value = `正在提交 ${raws.length} 个文件的解析任务...`
  uploadStatus.value = ''
  try {
    const res = await uploadDocument(raws, props.space?.id)
    // 后端按上传顺序返回每个文件的提交结果
    const list = (Array.isArray(res) ? res : [res]).filter((item) => item?.documentId != null)
    if (!list.length) {
      throw new Error('未返回有效的提交结果')
    }
    uploadProgress.value = 100
    uploadStatus.value = 'success'
    uploadMsg.value = `已提交 ${list.length} 个文件的解析任务`
    docs.value = list.map((item) => ({
      uid: ++chunkUid,
      documentId: item.documentId,
      fileName: item.fileName,
      fileSize: item.fileSize,
      type: item.type,
      status: DOC_STATUS.PARSING,
      progress: 0,
      error: '',
      chunks: [],
      vectorizing: false,
      vectorizeProgress: 0,
      vectorizeStatus: '',
      vectorizeError: '',
      vectorized: false,
    }))
    activeDocIndex.value = 0
    active.value = 1
    startPolling()
  } catch (e) {
    uploadStatus.value = 'exception'
    uploadMsg.value = e.message || '上传失败'
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

// ========== 解析状态轮询（对所有未完成文件并行查询） ==========

function startPolling() {
  stopPolling()
  pollCount = 0
  pollAll()
  pollTimer = setInterval(pollAll, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollAll() {
  if (pollBusy) return
  const targets = docs.value.filter((doc) => isProcessingStatus(doc.status))
  if (!targets.length) {
    stopPolling()
    return
  }
  pollCount += 1
  if (pollCount > MAX_POLL_COUNT) {
    stopPolling()
    targets.forEach((doc) => {
      doc.status = DOC_STATUS.FAILED
      doc.error = '解析超时，请稍后重新上传'
    })
    ElMessage.error('解析超时，请稍后重试')
    return
  }
  pollBusy = true
  try {
    await Promise.all(targets.map(pollOne))
  } finally {
    pollBusy = false
  }
}

async function pollOne(doc) {
  if (doc.documentId == null) return
  try {
    const res = await getParseStatus(doc.documentId)
    doc.progress = res.progress ?? 0
    // 解析完成（4=待确认；2=已完成的历史数据）即终止该文件轮询，展示分片供用户校对
    if (PARSE_SETTLED_STATUS.includes(res.status)) {
      doc.status = res.status
      doc.chunks = normalizeChunks(res.chunks)
      doc.error = ''
      return
    }
    if (res.status === DOC_STATUS.FAILED || res.error) {
      doc.status = DOC_STATUS.FAILED
      doc.error = res.error || '文档解析失败'
      return
    }
    doc.status = res.status
  } catch (e) {
    // 瞬时故障：保持原状态等待下次轮询，不影响其他文件
    console.warn('查询解析状态失败（稍后重试）:', e?.message)
  }
}

// ========== 向量化（对所有已解析文件逐个提交） ==========

async function doVectorizeAll() {
  const targets = docs.value.filter(
    (doc) => doc.status === DOC_STATUS.PENDING_CONFIRM && doc.chunks.length && !doc.vectorized,
  )
  if (!targets.length) {
    ElMessage.warning('没有可提交的文件')
    return
  }
  active.value = 2
  for (const doc of targets) {
    const payload = buildConfirmPayload(doc)
    if (!payload.length) {
      doc.vectorizeError = '请至少保留一个非空白分片'
      continue
    }
    doc.vectorizing = true
    doc.vectorizeProgress = 30
    doc.vectorizeStatus = ''
    doc.vectorizeError = ''
    try {
      doc.vectorizeProgress = 60
      const res = await vectorizeDocument(doc.documentId, props.space?.id, payload)
      doc.vectorizeProgress = 100
      doc.vectorizeStatus = 'success'
      doc.vectorized = true
      doc.status = DOC_STATUS.COMPLETED
      // 通知父级刷新文档列表（此时仍停留在本页，由「返回列表」离开）
      emit('uploaded', res)
    } catch (e) {
      doc.vectorizeStatus = 'exception'
      doc.vectorizeError = e.message || '未知错误'
      ElMessage.error(`「${doc.fileName}」向量化失败：${doc.vectorizeError}`)
    } finally {
      doc.vectorizing = false
    }
  }
  const failed = targets.filter((doc) => !doc.vectorized)
  if (failed.length) {
    ElMessage.warning(`有 ${failed.length} 个文件向量化失败，可点击「重试失败项」`)
  } else {
    ElMessage.success('向量化处理完成')
  }
}

function prev() {
  if (active.value > 0) active.value--
}

// ========== 分片编辑（纯前端内存操作，提交时整体覆盖落库） ==========

/** 归一化后端分片：补齐 uid、编辑标记与光标位，便于列表渲染与增删编辑 */
function normalizeChunks(list) {
  return (list || []).map((item) => ({
    uid: ++chunkUid,
    content: item.content || '',
    cursor: 0,
    editing: false,
  }))
}

/** 构建提交给后端的分片数组：过滤空白分片并按当前顺序重排序号 */
function buildConfirmPayload(doc) {
  return (doc?.chunks || [])
    .filter((item) => (item.content || '').trim())
    .map((item, index) => ({ index, content: item.content }))
}

/** 切换分片的 Markdown 预览 / 编辑态 */
function toggleEdit(index) {
  const item = currentDoc.value?.chunks?.[index]
  if (!item) return
  item.editing = !item.editing
}

/** 记录光标位置，供「光标处拆分」定位 */
function updateCursor(item, event) {
  item.cursor = event?.target?.selectionStart ?? 0
}

/** 新增空白分片（追加到当前文件列表末尾，直接进入编辑态） */
function addChunk() {
  currentDoc.value?.chunks.push({ uid: ++chunkUid, content: '', cursor: 0, editing: true })
}

/** 删除当前文件的单个分片 */
function removeChunk(index) {
  currentDoc.value?.chunks.splice(index, 1)
}

/** 与上一段合并：内容拼接，类型沿用上一段 */
function mergeWithPrev(index) {
  const list = currentDoc.value?.chunks
  if (!list || index <= 0) return
  const prev = list[index - 1]
  const current = list[index]
  prev.content = `${prev.content}\n${current.content}`.trim()
  list.splice(index, 1)
}

/** 在光标处拆分：光标位于首尾或内容为空时不拆分 */
function splitChunk(index) {
  const list = currentDoc.value?.chunks
  if (!list) return
  const item = list[index]
  const cursor = item.cursor ?? 0
  if (!item.content.trim()) {
    ElMessage.warning('空白分片无需拆分')
    return
  }
  if (cursor <= 0 || cursor >= item.content.length) {
    ElMessage.warning('请将光标放到分片内容中间再拆分')
    return
  }
  const head = item.content.slice(0, cursor)
  const tail = item.content.slice(cursor)
  item.content = head
  item.cursor = head.length
  list.splice(index + 1, 0, {
    uid: ++chunkUid,
    content: tail,
    cursor: 0,
    editing: item.editing,
  })
}

// ========== 放弃流程（未完成向量化就离开 => 逻辑删除） ==========

/** 需放弃的文档 id：尚未完成向量化的项 */
function pendingAbandonIds() {
  return docs.value.filter((doc) => !doc.vectorized && doc.documentId != null).map((doc) => doc.documentId)
}

/** 主动放弃：静默逻辑删除所有未完成向量化的文档，失败不阻塞用户离开 */
async function abandonIfNeeded() {
  const ids = pendingAbandonIds()
  if (!ids.length) return
  // 先清空，避免卸载与路由离开重复触发
  docs.value = []
  await Promise.all(
    ids.map((id) =>
      abandonDocument(id).catch((e) => {
        // 放弃属兜底行为，失败仅记录，不打扰用户
        console.warn('放弃文档失败（已忽略）:', e?.message)
      }),
    ),
  )
}

/** 离开上传页（返回列表）：未完成向量化的文档执行逻辑删除 */
async function backToList() {
  await abandonIfNeeded()
  emit('back')
}

/** 页面关闭/刷新兜底：keepalive 请求可在页面卸载后继续发送 */
function abandonOnUnload() {
  const ids = pendingAbandonIds()
  if (!ids.length) return
  const token = getToken()
  ids.forEach((id) => {
    try {
      fetch(`/api/knowledge-base/document/abandon?documentId=${id}`, {
        method: 'POST',
        keepalive: true,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
    } catch {
      // 浏览器不支持 keepalive 时忽略，等待用户下次重新上传
    }
  })
  docs.value = []
}

// ========== 展示辅助 ==========

/** 是否处于处理中（待处理 / 解析中 / 待分片 / AI分片中），此类状态需继续轮询 */
function isProcessingStatus(status) {
  return IN_PROGRESS_STATUS.includes(status)
}

/** 文件状态文案：解析阶段展示实时进度，其余展示状态标签 */
function docStatusText(doc) {
  if (doc.status === DOC_STATUS.PENDING || doc.status === DOC_STATUS.PARSING) {
    return `解析中 ${doc.progress || 0}%`
  }
  return docStatusMeta(doc.status).label
}

/** 处理中的提示文案：区分「解析」与「AI 分片」两个阶段 */
function processingHint(status) {
  if (status === DOC_STATUS.PARSE_DONE || status === DOC_STATUS.CHUNKING) {
    return '大模型正在按语义自动分片，请稍候（长文档可能需要数分钟）...'
  }
  return '正在解析文档内容，请稍候（大文件可能需要数分钟）...'
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(1) + ' ' + units[i]
}

onMounted(() => {
  window.addEventListener('beforeunload', abandonOnUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', abandonOnUnload)
  stopPolling()
  // 组件卸载即视为离开上传流程（路由离开已提前处理，此处为兜底）
  abandonIfNeeded()
})

// 路由离开（如侧边栏跳转）同样视为放弃
onBeforeRouteLeave(async () => {
  await abandonIfNeeded()
  return true
})
</script>

<style scoped>
.doc-upload-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* ========== 顶部工具栏 ========== */
.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.kb-toolbar__left {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.kb-toolbar__folder-icon {
  color: #f59e0b;
  font-size: 18px;
}

.kb-toolbar__title {
  font-size: 1.125rem;
  font-weight: 700;
  color: #111827;
  margin-left: 0.25rem;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-toolbar__subtitle {
  font-size: 0.9rem;
  color: #6b7280;
  margin-left: 0.25rem;
}

/* ========== 面板 ========== */
.kb-panel {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  padding: 1.25rem 1rem;
}

.kb-panel--body {
  padding: 0;
  overflow: hidden;
}

.kb-step {
  padding: 2rem 1.5rem;
  min-height: 340px;
}

.kb-panel-title {
  padding: 12px 16px;
  font-size: 0.82rem;
  font-weight: 600;
  color: #6b7280;
  letter-spacing: 0.04em;
  border-bottom: 1px solid #f0f1f3;
}

/* ========== 第一步 ========== */
.kb-progress {
  margin-top: 1.25rem;
}

.kb-progress__msg {
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: #6b7280;
}

:deep(.el-upload-dragger:hover),
:deep(.el-upload:focus .el-upload-dragger) {
  border-color: #6366f1;
}

:deep(.el-upload__text em) {
  color: #6366f1;
  font-style: normal;
}

/* ========== 第二步 ========== */
.kb-preview {
  display: flex;
  min-height: 420px;
}

.kb-preview__side {
  flex: 0 0 280px;
  border-right: 1px solid #f0f1f3;
  display: flex;
  flex-direction: column;
}

/* 多文件列表：左侧逐个文件展示解析状态，点击切换 */
.kb-file-list {
  flex: 1;
  margin: 0;
  padding: 8px;
  list-style: none;
  overflow-y: auto;
}

.kb-file-list__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.kb-file-list__item:hover {
  background: #f3f4f6;
}

.kb-file-list__item--active {
  background: #eef2ff;
}

.kb-file-list__icon {
  flex: 0 0 auto;
  font-size: 18px;
  color: #6366f1;
}

.kb-file-list__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-file-list__name {
  font-size: 0.83rem;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-file-list__item--active .kb-file-list__name {
  color: #4f46e5;
  font-weight: 600;
}

.kb-file-list__status {
  font-size: 0.72rem;
}

.kb-file-list__status--warning {
  color: #d97706;
}

.kb-file-list__status--success {
  color: #16a34a;
}

.kb-file-list__status--danger {
  color: #dc2626;
}

.kb-file-list__status--info {
  color: #9ca3af;
}

.kb-preview__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.kb-chunk-list {
  flex: 1;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 460px;
  overflow-y: auto;
  background: #f9fafb;
}

.kb-chunk-card {
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.kb-chunk-card:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.08);
}

/* 分片正文：Markdown 渲染容器（字号 / 颜色由 MarkdownViewer 内的 .markdown-body 继承） */
.kb-chunk-card__markdown {
  font-size: 0.83rem;
  line-height: 1.7;
  color: #4b5563;
  word-break: break-word;
}

/* 分段列表标题行：左标题 + 右操作 */
.kb-panel-title--row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-chunk-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.kb-chunk-card__seq {
  font-size: 0.72rem;
  color: #9ca3af;
}

.kb-chunk-card__ops {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}

.kb-chunk-card__editor :deep(.el-textarea__inner) {
  font-family: inherit;
  font-size: 0.83rem;
  line-height: 1.7;
  color: #4b5563;
}

/* ========== 解析中 / 解析失败 ========== */
.kb-parsing {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 2.5rem 2rem;
  background: #f9fafb;
}

.kb-parsing__msg {
  margin: 0;
  font-size: 0.85rem;
  color: #6b7280;
}

.kb-parse-error {
  margin: 16px;
}

/* ========== 第三步 ========== */
.kb-vectorize {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 720px;
  margin: 0 auto;
}

.kb-file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f9fafb;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
}

.kb-file-item__icon {
  font-size: 22px;
  color: #6366f1;
}

.kb-file-item__name {
  flex: 1;
  min-width: 0;
  font-size: 0.88rem;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-file-item__size {
  font-size: 0.8rem;
  color: #9ca3af;
}

.kb-file-item__progress {
  flex: 0 0 180px;
}

/* ========== 底部操作 ========== */
.kb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
