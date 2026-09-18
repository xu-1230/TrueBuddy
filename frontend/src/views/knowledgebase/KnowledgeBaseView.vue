<template>
  <div class="knowledge-base-view">
    <SpaceGridView
      v-if="currentLevel === 'space'"
      :spaces="spaces"
      :loading="loading"
      @open-space="handleOpenSpace"
      @create-space="handleCreateSpace"
      @update-space="handleUpdateSpace"
      @delete-space="handleDeleteSpace"
    />
    <DocumentListView
      v-else-if="currentLevel === 'document'"
      :space="currentSpace"
      :documents="documents"
      :loading="loading"
      :active-document="currentDocument"
      :chunks="chunks"
      :chunk-loading="chunkLoading"
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
  getChunkList,
  getDocumentList,
  getSpaceList,
  updateSpace,
} from '@/api/knowledgebase'
import DocumentListView from './components/DocumentListView.vue'
import DocumentUploadView from './components/DocumentUploadView.vue'
import SpaceGridView from './components/SpaceGridView.vue'

const currentLevel = ref('space')
const currentSpace = ref(null)
// 当前选中的文档（右栏展示其分片）
const currentDocument = ref(null)

const spaces = ref([])
const documents = ref([])
const chunks = ref([])
const loading = ref(false)
const chunkLoading = ref(false)

async function loadSpaces() {
  loading.value = true
  try {
    spaces.value = await getSpaceList()
  } catch (e) {
    ElMessage.error('加载空间列表失败')
  } finally {
    loading.value = false
  }
}

async function loadDocuments(spaceId) {
  loading.value = true
  try {
    documents.value = await getDocumentList(spaceId)
  } catch (e) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

async function loadChunks(documentId) {
  chunkLoading.value = true
  try {
    chunks.value = await getChunkList(documentId)
  } catch (e) {
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
  } catch (e) {
    ElMessage.error('空间创建失败')
  }
}

async function handleUpdateSpace(form) {
  try {
    await updateSpace(form.id, { name: form.name, description: form.description })
    ElMessage.success('空间已更新')
    await loadSpaces()
  } catch (e) {
    ElMessage.error('空间更新失败')
  }
}

async function handleDeleteSpace(space) {
  try {
    await deleteSpace(space.id)
    ElMessage.success('空间已删除')
    await loadSpaces()
  } catch (e) {
    ElMessage.error('删除空间失败')
  }
}

async function handleOpenSpace(space) {
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

function handleBackToSpace() {
  currentLevel.value = 'space'
  currentSpace.value = null
  currentDocument.value = null
  documents.value = []
  chunks.value = []
  // 空间内文档数量可能已变化，刷新空间列表
  loadSpaces()
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
function handleBackToDocuments() {
  currentLevel.value = 'document'
}

async function handleDocumentUploaded(result) {
  if (!currentSpace.value) return
  try {
    ElMessage.success('文档添加成功')
    await loadDocuments(currentSpace.value.id)
    const created = documents.value.find((d) => d.id === result?.documentId)
    if (created) handleSelectDocument(created)
  } catch (e) {
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
  } catch (e) {
    ElMessage.error('删除文档失败')
  }
}

onMounted(() => {
  loadSpaces()
})
</script>

<style scoped>
.knowledge-base-view {
  display: flex;
  flex-direction: column;
}
</style>
