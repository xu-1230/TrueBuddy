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
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import { knowledgeBaseLocation, parseKnowledgeBaseRoute } from './utils/route'

const route = useRoute()
const router = useRouter()

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
// 初值 true：本页挂载即请求，首帧直接出骨架，避免先闪一帧空态
const loading = ref(true)
const publicLoading = ref(true)
const chunkLoading = ref(false)
// 正在 fork 的空间 id（卡片按钮 loading；单请求进行中，无并发去重需求）
const forkingId = ref(null)

// 路由 / 文档 / 分片请求令牌：快速切换时丢弃过期响应，保证地址与画面一致
let routeToken = 0
let documentsToken = 0
let chunksToken = 0
// 空间列表就绪信号：深链进入时先等两份列表载入，才能判断空间归属
let listsReady = null

/** 载入我的空间；返回值表示请求是否成功（深链归属判定要区分「不存在」与「加载失败」） */
async function loadSpaces() {
  loading.value = true
  try {
    spaces.value = await getSpaceList()
    return true
  } catch {
    ElMessage.error('加载空间列表失败')
    return false
  } finally {
    loading.value = false
  }
}

async function loadPublicSpaces() {
  publicLoading.value = true
  try {
    publicSpaces.value = await getPublicSpaceList()
    return true
  } catch {
    // 公有区加载失败不拖垮页面主体，静默留空
    publicSpaces.value = []
    return false
  } finally {
    publicLoading.value = false
  }
}

/** 并行载入我的空间与公有库；解析出各自成败供深链归属判定等待 */
function loadAllSpaceLists() {
  listsReady = Promise.all([loadSpaces(), loadPublicSpaces()]).then(
    ([ownedOk, publicOk]) => ({ ownedOk, publicOk }),
  )
  return listsReady
}

function ensureSpaceLists() {
  return listsReady ?? loadAllSpaceLists()
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
  const token = ++chunksToken
  chunkLoading.value = true
  try {
    const list = await getChunkList(documentId)
    if (token !== chunksToken) return
    chunks.value = list
  } catch {
    if (token !== chunksToken) return
    ElMessage.error('加载文档分片失败')
  } finally {
    if (token === chunksToken) chunkLoading.value = false
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

// 点击空间只改地址，载入交给 URL watcher；自己的与公有走同一条路径
function handleOpenSpace(space) {
  navigate(knowledgeBaseLocation(space.id))
}

function handleOpenPublicSpace(space) {
  navigate(knowledgeBaseLocation(space.id))
}

function handleBackToSpace() {
  navigate(knowledgeBaseLocation())
}

function handleSelectDocument(doc) {
  if (currentDocument.value?.id === doc.id && chunks.value.length) return
  currentDocument.value = doc
  // 切文档先清空旧分片，避免新文档标题下短暂展示上一份文档的内容，也让骨架态生效
  chunks.value = []
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

/* ---------- URL → 页面状态（?space= 为当前打开的空间） ---------- */

/** 规范化跳转：同地址不发请求，避免 redundant navigation */
function navigate(location, { replace = false } = {}) {
  const target = router.resolve(location).fullPath
  if (target === route.fullPath) return Promise.resolve()
  return replace ? router.replace(location) : router.push(location)
}

/** 回到空间列表：清空空间内状态（列表数据由 URL watcher 重新拉取） */
function resetToSpaceList() {
  currentLevel.value = 'space'
  currentSpace.value = null
  currentDocument.value = null
  isPublicReadonly.value = false
  documents.value = []
  chunks.value = []
  documentsToken++
  chunksToken++
  chunkLoading.value = false
}

/** URL 指向的空间落状态；readonly 由归属判定结果传入 */
async function applySpace(space, readonly, token) {
  isPublicReadonly.value = readonly
  currentSpace.value = space
  currentLevel.value = 'document'
  currentDocument.value = null
  documents.value = []
  chunks.value = []
  chunksToken++
  chunkLoading.value = false
  loading.value = true
  const docToken = ++documentsToken
  try {
    const list = await getDocumentList(space.id)
    if (docToken !== documentsToken || token !== routeToken) return
    documents.value = list
  } catch {
    if (docToken !== documentsToken || token !== routeToken) return
    ElMessage.error('加载文档列表失败')
  } finally {
    if (docToken === documentsToken) loading.value = false
  }
  if (docToken !== documentsToken || token !== routeToken) return
  // 默认选中第一篇文档，右栏直接展示其分片（文档选择不进 URL）
  if (documents.value.length) {
    handleSelectDocument(documents.value[0])
  }
}

/**
 * 路由变化 → 载入 / 复位。
 * 深链进入时先等两份空间列表就绪：先查「我的空间」（可写），再查公有库（只读）。
 */
async function syncFromRoute(rawSpace) {
  const token = ++routeToken
  const { spaceId, hasInvalidSpace } = parseKnowledgeBaseRoute({ space: rawSpace })

  if (!spaceId) {
    resetToSpaceList()
    if (hasInvalidSpace) {
      ElMessage.warning('知识库地址无效，已返回空间列表')
      await navigate(knowledgeBaseLocation(), { replace: true })
      return
    }
    await loadAllSpaceLists()
    return
  }

  const { ownedOk, publicOk } = await ensureSpaceLists()
  if (token !== routeToken) return

  const owned = spaces.value.find((item) => item.id === spaceId)
  const shared = owned ? null : publicSpaces.value.find((item) => item.id === spaceId)
  const space = owned || shared
  if (!space) {
    if (!ownedOk || !publicOk) {
      // 暂时性失败：保留深链地址，回落到空间列表，刷新后可重试
      resetToSpaceList()
      return
    }
    ElMessage.warning('知识库不存在或无权访问，已返回空间列表')
    await navigate(knowledgeBaseLocation(), { replace: true })
    return
  }
  await applySpace(space, Boolean(shared), token)
}

// 首次挂载、点击空间、前后退都走同一条路径
watch(
  () => route.query.space,
  (rawSpace) => {
    syncFromRoute(rawSpace)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  // 页面卸载后丢弃在途响应
  routeToken++
  documentsToken++
  chunksToken++
})
</script>

<style scoped>
.knowledge-base-view {
  display: flex;
  flex-direction: column;
}
</style>
