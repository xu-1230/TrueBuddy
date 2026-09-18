<template>
  <div class="doc-upload-view">
    <!-- 顶部工具栏：返回 + 空间名 -->
    <div class="kb-toolbar">
      <div class="kb-toolbar__left">
        <el-button text @click="$emit('back')">
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
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :file-list="fileList"
          :on-exceed="handleExceed"
          accept=".docx,.doc,.pdf,.ppt,.pptx,.md,.txt"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到这里 或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 docx / doc / pdf / ppt / md / txt，大小不超过 50MB</div>
          </template>
        </el-upload>
        <div v-if="uploading" class="kb-progress">
          <el-progress :percentage="uploadProgress" :status="uploadStatus" />
          <p class="kb-progress__msg">{{ uploadMsg }}</p>
        </div>
      </div>

      <!-- 第二步：分段预览 -->
      <div v-else-if="active === 1" class="kb-preview">
        <aside class="kb-preview__side">
          <div class="kb-panel-title">自动分段与清洗</div>
          <div class="kb-preview__doc">
            <el-icon><Tickets /></el-icon>
            <span class="kb-preview__doc-name" :title="docInfo.fileName">
              {{ docInfo.fileName }}
            </span>
          </div>
          <div class="kb-preview__count">共 {{ chunks.length }} 个分片</div>
        </aside>
        <section class="kb-preview__main">
          <div class="kb-panel-title">分段列表</div>
          <div class="kb-chunk-list">
            <div v-for="(item, idx) in chunks" :key="idx" class="kb-chunk-card">
              <span class="kb-chunk-card__badge">{{ typeLabel(item.type) }}</span>
              <div class="kb-chunk-card__text" v-html="item.content"></div>
            </div>
            <el-empty v-if="!chunks.length" :image-size="90" description="未解析出分片内容" />
          </div>
        </section>
      </div>

      <!-- 第三步：数据处理（向量化） -->
      <div v-else class="kb-step">
        <div class="kb-vectorize">
          <div class="kb-file-item">
            <el-icon class="kb-file-item__icon"><Document /></el-icon>
            <span class="kb-file-item__name" :title="docInfo.fileName">{{ docInfo.fileName }}</span>
            <span class="kb-file-item__size">{{ formatSize(docInfo.fileSize) }}</span>
          </div>
          <el-alert
            v-if="vectorizing"
            title="正在进行向量化处理..."
            type="info"
            :closable="false"
            show-icon
          />
          <el-progress
            v-if="vectorizing"
            :percentage="vectorizeProgress"
            :status="vectorizeStatus"
          />
          <el-alert
            v-if="vectorizeDone"
            title="向量化处理完成！"
            type="success"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="kb-actions">
      <el-button v-if="active > 0 && !vectorizeDone" @click="prev">上一步</el-button>
      <el-button v-if="active === 0" type="primary" :loading="uploading" @click="doUpload">
        上传并解析
      </el-button>
      <el-button v-if="active === 1" type="primary" @click="next">下一步</el-button>
      <el-button
        v-if="active === 2 && !vectorizeDone"
        type="primary"
        :loading="vectorizing"
        @click="doVectorize"
      >
        提交
      </el-button>
      <el-button v-if="vectorizeDone" type="success" @click="backToList">返回列表</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ArrowLeft, Document, FolderOpened, Tickets, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadDocument, vectorizeDocument } from '@/api/knowledgebase'

const props = defineProps({
  space: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['back', 'uploaded'])

const active = ref(0)

// 第一步
const fileList = ref([])
const selectedFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const uploadMsg = ref('')

// 第二步
const docInfo = reactive({ fileName: '', fileSize: 0, type: '', totalChunks: 0 })
const chunks = ref([])

// 第三步
const vectorizing = ref(false)
const vectorizeProgress = ref(0)
const vectorizeStatus = ref('')
const vectorizeDone = ref(false)

function handleFileChange(file) {
  fileList.value = [file]
}

function handleExceed() {
  ElMessage.warning('一次只能上传一个文件，请先移除已选文件')
}

async function doUpload() {
  if (!fileList.value.length) {
    ElMessage.warning('请先选择文件')
    return
  }
  const raw = fileList.value[0].raw
  selectedFile.value = raw
  uploading.value = true
  uploadProgress.value = 30
  uploadMsg.value = '正在解析文件内容...'
  uploadStatus.value = ''
  try {
    uploadProgress.value = 60
    const res = await uploadDocument(raw)
    uploadProgress.value = 100
    uploadStatus.value = 'success'
    uploadMsg.value = '解析完成'
    docInfo.fileName = res.fileName
    docInfo.fileSize = res.fileSize
    docInfo.type = res.type
    docInfo.totalChunks = res.totalChunks
    chunks.value = res.chunks || []
    ElMessage.success(`解析完成，共 ${chunks.value.length} 个分片`)
    active.value = 1
  } catch (e) {
    uploadStatus.value = 'exception'
    uploadMsg.value = e.message || '上传失败'
    ElMessage.error('上传失败：' + (e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

async function doVectorize() {
  if (!selectedFile.value) {
    ElMessage.error('未找到待处理的文件，请重新上传')
    return
  }
  vectorizing.value = true
  vectorizeProgress.value = 30
  vectorizeStatus.value = ''
  try {
    vectorizeProgress.value = 60
    const res = await vectorizeDocument(selectedFile.value, chunks.value, props.space?.id)
    vectorizeProgress.value = 100
    vectorizeStatus.value = 'success'
    vectorizeDone.value = true
    ElMessage.success('向量化处理完成')
    // 通知父级刷新文档列表（此时仍停留在本页，由「返回列表」离开）
    emit('uploaded', res)
  } catch (e) {
    vectorizeStatus.value = 'exception'
    ElMessage.error('向量化失败：' + (e.message || '未知错误'))
  } finally {
    vectorizing.value = false
  }
}

function next() {
  if (active.value < 2) active.value++
}

function prev() {
  if (active.value > 0) active.value--
}

function backToList() {
  emit('back')
}

function typeLabel(type) {
  if (type === 'h') return '标题'
  if (type === 'table') return '表格'
  return '正文'
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
  flex: 0 0 260px;
  border-right: 1px solid #f0f1f3;
  display: flex;
  flex-direction: column;
}

.kb-preview__doc {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px;
  padding: 8px 10px;
  font-size: 0.85rem;
  color: #4f46e5;
  background: #eef2ff;
  border-radius: 8px;
}

.kb-preview__doc-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-preview__count {
  padding: 0 12px;
  font-size: 0.78rem;
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

.kb-chunk-card__badge {
  display: inline-block;
  margin-bottom: 6px;
  padding: 1px 6px;
  font-size: 0.7rem;
  color: #6366f1;
  background: #eef2ff;
  border-radius: 4px;
}

.kb-chunk-card__text {
  font-size: 0.83rem;
  line-height: 1.7;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ========== 第三步 ========== */
.kb-vectorize {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 640px;
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

/* ========== 底部操作 ========== */
.kb-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
