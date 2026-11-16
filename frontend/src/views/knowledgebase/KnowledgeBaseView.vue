<template>
  <div class="knowledge-base-view">
    <SpaceGridView
      v-if="currentLevel === 'space'"
      :spaces="spaces"
      :loading="loading"
      :public-spaces="publicSpaces"
      :public-loading="publicLoading"
      :forking-id="forkingId"
      @open-space="handleOpenSpace"
      @open-public-space="handleOpenPublicSpace"
      @create-space="handleCreateSpace"
      @update-space="handleUpdateSpace"
      @delete-space="handleDeleteSpace"
      @toggle-visibility="handleToggleVisibility"
      @fork-space="handleForkSpace"
    />
    <DocumentListView
      v-else-if="currentLevel === 'document'"
      :space="currentSpace"
      :documents="documents"
      :loading="loading"
      :active-document="currentDocument"
      :chunks="chunks"
      :chunk-loading="chunkLoading"
      :readonly="isPublicReadonly"
      @back="handleBackToSpace"
      @select-document="handleSelectDocument"
      @add-document="handleOpenUpload"
      @delete-document="handleDeleteDocument"
    />
    <DocumentUploadView
      v-else-if="currentLevel === 'upload'"
      :space="currentSpace"
      @back="handleBackToDocuments"
      @uploaded="handleDocumentUploaded"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createSpace,
  deleteDocument,
  deleteSpace,
  forkSpace,
  getChunkList,
  getDocumentList,
  getPublicSpaceList,
  getSpaceList,
  updateSpace,
  updateSpaceVisibility,
} from '@/api/knowledgebase'
import DocumentListView from './components/DocumentListView.vue'
import DocumentUploadView from './components/DocumentUploadView.vue'
import SpaceGridView from './components/SpaceGridView.vue'

const currentLevel = ref('space')
const currentSpace = ref(null)
// 当前选中的文档（右栏展示其分片）
const currentDocument = ref(null)
// 公有库浏览：打开的是他人的公有空间时，文档区整个切只读形态
const isPublicReadonly = ref(false)

const spaces = ref([])
const publicSpaces = ref([])
const documents = ref([])
const chunks = ref([])
const loading = ref(false)
const publicLoading = ref(false)
const chunkLoading = ref(false)
// 正在 fork 的空间 id（卡片按钮 loading；单请求进行中，无并发去重需求）
const forkingId = ref(null)

async function loadSpaces() {
  loading.value = true
  try {
    spaces.value = await getSpaceList()
  } catch {
    ElMessage.error('加载空间列表失败')
  } finally {
    loading.value = false
  }
}

async function loadPublicSpaces() {
  publicLoading.value = true
  try {
    publicSpaces.value = await getPublicSpaceList()
  } catch {
    // 公有区加载失败不拖垮页面主体，静默留空
    publicSpaces.value = []
  } finally {
    publicLoading.value = false
  }
}

async function loadDocuments(spaceId) {
  loading.value = true
  try {
    documents.value = await getDocumentList(spaceId)
  } catch {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

async function loadChunks(documentId) {
  chunkLoading.value = true
  try {
    chunks.value = await getChunkList(documentId)
  } catch {
    ElMessage.error('加载文档分片失败')
  } finally {
    chunkLoading.value = false
  }
}

async function handleCreateSpace(form) {
  try {
    await createSpace(form)
    ElMessage.success('空间创建成功')
    await loadSpaces()
  } catch {
    ElMessage.error('空间创建失败')
  }
}

async function handleUpdateSpace(form) {
  try {
    await updateSpace(form.id, { name: form.name, description: form.description })
    ElMessage.success('空间已更新')
    await loadSpaces()
  } catch {
    ElMessage.error('空间更新失败')
  }
}

async function handleDeleteSpace(space) {
  try {
    await deleteSpace(space.id)
    ElMessage.success('空间已删除')
    await loadSpaces()
  } catch {
    ElMessage.error('删除空间失败')
  }
}

// 公有 / 私有切换：目标取值由卡片算好（1=公开 0=私有），成功后刷新列表
async function handleToggleVisibility(space, target) {
  try {
    await updateSpaceVisibility(space.id, target)
    ElMessage.success(target === 1 ? '空间已公开，他人可在「公有库」中浏览' : '空间已设为私有')
    await loadSpaces()
  } catch {
    ElMessage.error('切换可见性失败')
  }
}

// 「添加为自己的」：fork 成独立副本（后端单事务复制文档/分片/向量），完成后出现在我的空间列表
async function handleForkSpace(space) {
  if (forkingId.value != null) return
  forkingId.value = space.id
  try {
    await forkSpace(space.id)
    ElMessage.success(`已把「${space.name}」添加为自己的空间`)
    await loadSpaces()
  } catch {
    ElMessage.error('添加为自己的空间失败')
  } finally {
    forkingId.value = null
  }
}

async function handleOpenSpace(space) {
  isPublicReadonly.value = false
  currentSpace.value = space
  currentLevel.value = 'document'
  currentDocument.value = null
  chunks.value = []
  await loadDocuments(space.id)
  // 默认选中第一篇文档，右栏直接展示其分片
  if (documents.value.length) {
    handleSelectDocument(documents.value[0])
  }
}

// 打开他人的公有空间：只读浏览，不提供任何写入口
async function handleOpenPublicSpace(space) {
  isPublicReadonly.value = true
  currentSpace.value = space
  currentLevel.value = 'document'
  currentDocument.value = null
  chunks.value = []
  await loadDocuments(space.id)
  if (documents.value.length) {
    handleSelectDocument(documents.value[0])
  }
}

function handleBackToSpace() {
  currentLevel.value = 'space'
  currentSpace.value = null
  currentDocument.value = null
  isPublicReadonly.value = false
  documents.value = []
  chunks.value = []
  // 空间内文档数量可能已变化，刷新空间列表（自己的 + 公有区的）
  loadSpaces()
  loadPublicSpaces()
}

function handleSelectDocument(doc) {
  if (currentDocument.value?.id === doc.id && chunks.value.length) return
  currentDocument.value = doc
  loadChunks(doc.id)
}

// 进入「添加文档」流程页
function handleOpenUpload() {
  currentLevel.value = 'upload'
}

// 从「添加文档」流程页返回文档列表
// 用户可能未确认分片就返回（该文档已被后端逻辑删除），需刷新列表剔除该记录
async function handleBackToDocuments() {
  currentLevel.value = 'document'
  if (!currentSpace.value) return
  await loadDocuments(currentSpace.value.id)
  const active = currentDocument.value
  if (active && !documents.value.some((d) => d.id === active.id)) {
    currentDocument.value = null
    chunks.value = []
    if (documents.value.length) {
      handleSelectDocument(documents.value[0])
    }
  }
}

async function handleDocumentUploaded(result) {
  if (!currentSpace.value) return
  try {
    ElMessage.success('文档添加成功')
    await loadDocuments(currentSpace.value.id)
    const created = documents.value.find((d) => d.id === result?.documentId)
    if (created) handleSelectDocument(created)
  } catch {
    ElMessage.error('刷新文档列表失败')
  }
}

async function handleDeleteDocument(doc) {
  try {
    await deleteDocument(doc.id)
    ElMessage.success('文档已删除')
    const wasActive = currentDocument.value?.id === doc.id
    await loadDocuments(currentSpace.value.id)
    if (wasActive) {
      currentDocument.value = null
      chunks.value = []
      if (documents.value.length) {
        handleSelectDocument(documents.value[0])
      }
    }
  } catch {
    ElMessage.error('删除文档失败')
  }
}

onMounted(() => {
  loadSpaces()
  loadPublicSpaces()
})
</script>

<style scoped>
.knowledge-base-view {
  display: flex;
  flex-direction: column;
}
</style>
