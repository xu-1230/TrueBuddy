<template>
  <div class="folder">
    <!-- 隐藏的原生文件输入：directory 只能由原生 input 精确控制（含 webkitdirectory） -->
    <input
      ref="inputRef"
      class="folder__input"
      type="file"
      webkitdirectory
      multiple
      :disabled="locked"
      @change="handleInputChange"
    />

    <div class="folder__slot">
      <button
        type="button"
        class="folder__drop"
        :class="{ 'folder__drop--filled': hasFolder, 'folder__drop--dragging': dragging }"
        :disabled="locked"
        :title="hasFolder ? '点击重新选择作业文件夹' : '点击选择作业文件夹，或把文件夹拖到此处'"
        @click="openPicker"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="handleDrop"
      >
        <span class="folder__tile" :class="{ 'folder__tile--filled': hasFolder }">
          <el-icon><component :is="hasFolder ? FolderOpened : FolderAdd" /></el-icon>
        </span>
        <template v-if="extracting">
          <span class="folder__spinner"></span>
          <span class="folder__title">正在解压压缩包…</span>
        </template>
        <template v-else-if="hasFolder">
          <span class="folder__name" :title="folderName">{{ folderName }}</span>
          <span class="folder__meta">已选 {{ selectedCount }}/{{ totalCount }} · {{ sizeText }}</span>
        </template>
        <span v-else class="folder__title">
          {{ dragging ? '松开即可导入' : '点击或拖拽选择作业文件夹（支持 zip）' }}
        </span>
      </button>

      <button
        v-if="hasFolder"
        type="button"
        class="folder__clear"
        title="移除该文件夹"
        :disabled="locked"
        @click="clear"
      >
        <el-icon><Close /></el-icon>
      </button>
    </div>

    <div v-if="hasFolder" class="folder__picker">
      <div class="folder__picker-head">
        <span class="folder__picker-title">选择需要批改的文件</span>
        <span class="folder__picker-count">已选 {{ selectedCount }}/{{ totalCount }}</span>
      </div>
      <div class="folder__picker-actions">
        <button type="button" class="picker-link" :disabled="locked" @click="selectAll">全选</button>
        <span class="picker-split">·</span>
        <button type="button" class="picker-link" :disabled="locked" @click="clearSelection">清空</button>
        <span class="picker-tip">{{ selectedCount ? '取消勾选的文件不会参与批改' : '请至少勾选一份作业' }}</span>
      </div>
      <el-tree
        class="folder__tree"
        :data="treeData"
        default-expand-all
        node-key="id"
        :props="TREE_PROPS"
      >
        <template #default="{ data }">
          <span
            class="node"
            :class="{ 'node--off': data.file && !isSelected(data) }"
            @click.stop="toggleNode(data)"
          >
            <span class="node__check" :class="`node__check--${checkState(data)}`"></span>
            <el-icon
              class="node__icon"
              :class="data.file ? `node__icon--${fileTone(data.label)}` : 'node__icon--dir'"
            >
              <component :is="data.file ? Document : FolderOpened" />
            </el-icon>
            <span class="node__label" :title="data.label">{{ data.label }}</span>
            <span v-if="isZipDir(data)" class="node__zip">zip</span>
            <span v-if="!data.file" class="node__tag">{{ countFiles(data.children ?? []) }} 份</span>
            <span v-else class="node__size">{{ formatFileSize(data.size) }}</span>
          </span>
        </template>
      </el-tree>
    </div>

  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Document, FolderAdd, FolderOpened } from '@element-plus/icons-vue'
import { formatFileSize } from '@/views/homework/utils/format'
import { extractZip } from '@/views/homework/utils/zip'

/** 与后端 FileServiceImpl.ALLOWED_EXTENSIONS 保持一致 */
const ALLOWED_EXTENSIONS = new Set([
  'pdf', 'doc', 'docx', 'md', 'markdown', 'txt',
  'ppt', 'pptx', 'xls', 'xlsx',
  'png', 'jpg', 'jpeg', 'gif', 'webp',
])
/** 压缩包：本地解压后再并入目录树（不进上面的白名单通道） */
const ZIP_EXTENSIONS = new Set(['zip'])
/** 单文件上限（与后端 spring.servlet.multipart.max-file-size 一致） */
const MAX_FILE_SIZE = 50 * 1024 * 1024
/** 单次批改份数上限（与后端 tb.homework.max-file-count 一致） */
const MAX_FILE_COUNT = 50
/** 目录树字段映射 */
const TREE_PROPS = { children: 'children', label: 'label' }
/** 系统 / 临时文件忽略规则 */
const IGNORED_NAME_PATTERN = /^(\.|~\$|__MACOSX)/i

const props = defineProps({
  /** 批改中禁用交互 */
  disabled: { type: Boolean, default: false },
})

const folder = defineModel({ type: Object, default: null })

const inputRef = ref(null)
const dragging = ref(false)
/** 正在解压压缩包：期间锁定交互，避免重复导入 */
const extracting = ref(false)
/** 目录树（由文件夹内所有文件按 webkitRelativePath 构建） */
const treeData = ref([])
/** 勾选的文件（默认全选，由用户在树中调整） */
const selectedFiles = ref([])
/** 当前文件夹名（选择后固定，不随勾选变化） */
const folderName = ref('')


/** 交互锁：批改中或解压中均不可操作 */
const locked = computed(() => props.disabled || extracting.value)

const hasFolder = computed(() => totalCount.value > 0)

const sizeText = computed(() => formatFileSize(selectedTotalSize.value))

const totalCount = computed(() => countFiles(treeData.value))

const selectedCount = computed(() => selectedFiles.value.length)

const selectedTotalSize = computed(
  () => selectedFiles.value.reduce((sum, file) => sum + file.size, 0),
)

/* ---------- 选择 / 拖拽 ---------- */

function openPicker() {
  if (locked.value) return
  inputRef.value?.click()
}

async function handleInputChange(event) {
  const files = Array.from(event.target.files ?? [])
  // 允许重复选择同一个文件夹
  event.target.value = ''
  await applyFiles(files)
}

async function handleDrop(event) {
  dragging.value = false
  if (locked.value) return
  // 1. 直接拖入文件：dataTransfer.files 即全部文件
  const plainFiles = Array.from(event.dataTransfer?.files ?? [])
  if (plainFiles.length) {
    await applyFiles(plainFiles)
    return
  }

  // 2. 拖入文件夹：浏览器只在 items 中给出目录句柄，需递归读取（Chrome / Edge）
  const entries = Array.from(event.dataTransfer?.items ?? [])
    .map((item) => item.webkitGetAsEntry?.())
    .filter(Boolean)
  if (!entries.length) return
  const files = []
  for (const entry of entries) {
    files.push(...(await readEntry(entry)))
  }
  await applyFiles(files)
}

/** 递归读取拖入的文件夹（readEntries 每次最多返回 100 项，需反复读取） */
async function readEntry(entry) {
  if (entry.isFile) {
    try {
      return [await new Promise((resolve, reject) => entry.file(resolve, reject))]
    } catch {
      return []
    }
  }
  if (!entry.isDirectory) return []
  const reader = entry.createReader()
  const children = []
  while (true) {
    const batch = await new Promise((resolve, reject) => reader.readEntries(resolve, reject))
    if (!batch.length) break
    children.push(...batch)
  }
  const files = []
  for (const child of children) {
    files.push(...(await readEntry(child)))
  }
  return files
}

/* ---------- 归一化 ---------- */

async function applyFiles(rawFiles) {
  if (!rawFiles.length) return
  const skipped = []
  const warnings = []
  const accepted = []
  const zipFiles = rawFiles.filter((file) => ZIP_EXTENSIONS.has(getExt(file.name)))

  for (const file of rawFiles) {
    if (zipFiles.includes(file) || IGNORED_NAME_PATTERN.test(file.name)) continue
    if (!ALLOWED_EXTENSIONS.has(getExt(file.name))) {
      skipped.push(file.name)
      continue
    }
    if (file.size > MAX_FILE_SIZE) {
      skipped.push(`${file.name}（超过 50MB）`)
      continue
    }
    accepted.push(file)
  }

  // 压缩包本地解压后并入同一批次（包内层级保留在 webkitRelativePath 中）
  if (zipFiles.length) {
    extracting.value = true
    try {
      for (const zipFile of zipFiles) {
        const result = await extractZip(zipFile)
        accepted.push(...result.files)
        skipped.push(...result.skipped)
        warnings.push(...result.warnings)
      }
    } finally {
      extracting.value = false
    }
  }

  if (skipped.length) {
    ElMessage.warning(`已跳过 ${skipped.length} 个文件：${skipped.slice(0, 3).join('、')}`)
  }
  warnings.forEach((text) => ElMessage.warning(text))
  if (!accepted.length) {
    ElMessage.error('没有可批改的作业文件（支持 Word / PDF / 文本 / 表格 / 图片，以及 zip 压缩包）')
    return
  }

  const exceeded = Math.max(0, accepted.length - MAX_FILE_COUNT)
  const files = exceeded ? accepted.slice(0, MAX_FILE_COUNT) : accepted
  if (exceeded) {
    ElMessage.warning(`单次批改最多 ${MAX_FILE_COUNT} 份，已保留前 ${MAX_FILE_COUNT} 份`)
  }

  treeData.value = buildTreeFromFiles(files)
  folderName.value = resolveFolderName(files)
  // 默认全选：勾选状态由本组件自管，不依赖树组件的内部状态
  selectedFiles.value = files
  projectFolder()
  ElMessage.success(`已导入 ${files.length} 份文件，默认全部勾选`)
}

function clear() {
  folder.value = null
  treeData.value = []
  selectedFiles.value = []
  folderName.value = ''
  ElMessage.info('已移除作业文件夹')
}

/* ---------- 目录树 ---------- */

/** 文件在树中的唯一 id（同名同大小同修改时间的文件视为同一份） */
function idOf(file) {
  return `${file.webkitRelativePath || file.name}#${file.size}#${file.lastModified}`
}

/** 由文件列表按 webkitRelativePath 构建目录树（保留文件夹层级） */
function buildTreeFromFiles(files) {
  const root = []
  files.forEach((file) => {
    const parts = (file.webkitRelativePath || file.name).split('/').filter(Boolean)
    let level = root
    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1
      const existing = level.find((node) => node.label === part)
      if (existing) {
        level = existing.children ?? []
        return
      }
      const node = isFile
        ? { id: idOf(file), label: part, size: file.size, file }
        : { id: `dir:${parts.slice(0, index + 1).join('/')}`, label: part, children: [] }
      level.push(node)
      level = node.children ?? []
    })
  })
  return root
}

/** 统计树中的文件节点数量 */
function countFiles(nodes) {
  return nodes.reduce(
    (sum, node) => sum + (node.file ? 1 : countFiles(node.children ?? [])),
    0,
  )
}

/** 文件类型 → 图标配色 */
const FILE_TONES = {
  doc: 'word', docx: 'word',
  pdf: 'pdf',
  md: 'md', markdown: 'md',
  txt: 'text',
  ppt: 'slide', pptx: 'slide',
  xls: 'sheet', xlsx: 'sheet',
}

function fileTone(fileName) {
  return FILE_TONES[getExt(fileName)] ?? 'other'
}

/** 该节点是否参与批改 */
function isSelected(data) {
  return Boolean(data?.file) && selectedFiles.value.includes(data.file)
}

/** 收集树中全部文件节点 */
function allFiles(nodes = treeData.value) {
  return nodes.flatMap((node) => (node.file ? [node.file] : allFiles(node.children ?? [])))
}

/** 勾选状态：on / half / off（目录按其子文件聚合） */
function checkState(data) {
  const files = data?.file ? [data.file] : allFiles(data?.children ?? [])
  if (!files.length) return 'off'
  const checked = files.filter((file) => selectedFiles.value.includes(file)).length
  if (checked === 0) return 'off'
  return checked === files.length ? 'on' : 'half'
}

/** 点击节点：文件切换自身，目录整组切换 */
function toggleNode(data) {
  if (locked.value) return
  const files = data?.file ? [data.file] : allFiles(data?.children ?? [])
  if (!files.length) return
  const set = new Set(selectedFiles.value)
  const allChecked = files.every((file) => set.has(file))
  files.forEach((file) => (allChecked ? set.delete(file) : set.add(file)))
  selectedFiles.value = [...set]
  projectFolder()
}

/** 全选 */
function selectAll() {
  selectedFiles.value = allFiles()
  projectFolder()
}

/** 清空勾选 */
function clearSelection() {
  selectedFiles.value = []
  projectFolder()
}

/** 把当前勾选结果写入 v-model（父组件只关心待批改文件） */
function projectFolder() {
  const files = selectedFiles.value
  if (!files.length) {
    folder.value = null
    return
  }
  folder.value = {
    name: folderName.value,
    files,
    totalSize: files.reduce((sum, file) => sum + file.size, 0),
  }
}

/** 文件夹名：所有文件共用同一根目录时取该根目录名（含「张三.zip」这类解压根目录） */
function resolveFolderName(files) {
  const roots = new Set()
  files.forEach((file) => {
    const relative = file.webkitRelativePath
    if (relative && relative.includes('/')) roots.add(relative.split('/')[0])
  })
  if (roots.size === 1) return [...roots][0]
  if (!roots.size) return `本次作业（${files.length} 份）`
  return `${roots.size} 个来源（${files.length} 份）`
}

/** 目录节点是否为压缩包根目录 */
function isZipDir(data) {
  return !data?.file && ZIP_EXTENSIONS.has(getExt(data?.label ?? ''))
}

function getExt(name) {
  if (!name || !name.includes('.')) return ''
  return name.slice(name.lastIndexOf('.') + 1).toLowerCase()
}
</script>

<style scoped>
.folder {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.folder__input {
  display: none;
}

.folder__slot {
  position: relative;
}

.folder__drop {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  height: 50px;
  padding: 0 2.1rem 0 0.7rem;
  overflow: hidden;
  border: var(--tb-border-width) dashed var(--tb-border-strong);
  border-radius: 0;
  background: var(--tb-bg-raised);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease,
    transform 0.2s ease;
}

.folder__drop:hover:not(:disabled) {
  border-color: var(--tb-primary);
  background: var(--tb-primary-soft);
  box-shadow: var(--tb-shadow-brand);
  transform: translateY(-1px);
}

.folder__drop--filled {
  /* 右侧给悬浮的移除按钮留位，避免「已选 n/m · 大小」被裁掉 */
  padding-right: 2.2rem;
  border-style: solid;
  border-color: var(--tb-border);
  background: var(--tb-bg-surface);
}

.folder__drop--filled:hover:not(:disabled) {
  border-color: var(--tb-primary-border);
  background: var(--tb-primary-soft);
}

.folder__drop--dragging {
  border-style: solid;
  border-color: var(--tb-primary);
  background: var(--tb-primary-soft);
  box-shadow: 0 0 0 4px var(--tb-ring);
}

.folder__drop:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.folder__tile {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 0;
  font-size: 14px;
  color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
}

.folder__tile--filled {
  color: var(--tb-on-primary);
  background: var(--tb-primary);
  box-shadow: none;
}

.folder__title {
  min-width: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder__name {
  flex: 1;
  min-width: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder__meta {
  flex: 0 0 auto;
  font-size: 0.66rem;
  color: var(--tb-text-tertiary);
}

.folder__clear {
  position: absolute;
  top: 50%;
  right: 8px;
  z-index: 2;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 50%;
  font-size: 12px;
  color: var(--tb-text-tertiary);
  background: var(--tb-bg-surface);
  cursor: pointer;
  transition: color 0.18s ease, background 0.2s ease, border-color 0.2s ease,
    transform 0.18s ease;
}

.folder__clear:hover:not(:disabled) {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: var(--tb-danger-soft);
  transform: translateY(-50%) scale(1.08);
}

/* ---------- 目录树（勾选待批改文件） ---------- */
.folder__picker {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.5rem 0.55rem;
  border-radius: 0;
  background: var(--tb-bg-raised);
  border: var(--tb-border-width) solid var(--tb-border);
}

.folder__picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.folder__picker-title {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.folder__picker-count {
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-primary-text);
}

.folder__tree {
  max-height: 216px;
  padding: 2px 0;
  overflow-y: auto;
  background: transparent;
  --el-tree-node-hover-bg-color: var(--tb-primary-soft);
}

/* 全选 / 清空 */
.folder__picker-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.picker-link {
  padding: 0;
  border: none;
  background: transparent;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-primary);
  cursor: pointer;
}

.picker-link:hover:not(:disabled) {
  color: var(--tb-primary-text);
  text-decoration: underline;
}

.picker-link:disabled {
  color: var(--tb-text-tertiary);
  cursor: not-allowed;
}

.picker-split {
  color: var(--tb-text-tertiary);
  font-size: 0.66rem;
}

.picker-tip {
  margin-left: auto;
  font-size: 0.64rem;
  color: var(--tb-text-tertiary);
}

/* 自绘勾选框：on / half / off 三态，不依赖 el-tree 内部勾选状态 */
.node__check {
  position: relative;
  flex: 0 0 auto;
  width: 13px;
  height: 13px;
  border-radius: 0;
  border: var(--tb-border-width) solid var(--tb-border-strong);
  background: var(--tb-bg-surface);
  transition: background 0.16s ease, border-color 0.16s ease;
}

.node__check--on,
.node__check--half {
  border-color: var(--tb-primary);
  background: var(--tb-primary);
}

.node__check--on::after {
  content: '';
  position: absolute;
  left: 3.5px;
  top: 0.5px;
  width: 4px;
  height: 7px;
  border: solid var(--tb-on-primary);
  border-width: 0 1.6px 1.6px 0;
  transform: rotate(45deg);
}

.node__check--half::after {
  content: '';
  position: absolute;
  left: 2.5px;
  top: 5px;
  width: 6px;
  height: 1.6px;
  border-radius: 0;
  background: var(--tb-on-primary);
}

.node {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  min-width: 0;
  padding: 0 4px 0 2px;
  cursor: pointer;
}

.node--off .node__label {
  color: var(--tb-text-tertiary);
}

.node--off .node__icon {
  opacity: 0.45;
}

.node__icon {
  flex: 0 0 auto;
  font-size: 16px;
  color: var(--tb-primary);
}

.node__icon--dir {
  color: var(--tb-accent);
}

.node__icon--word {
  color: var(--tb-primary-text);
}

.node__icon--pdf {
  color: var(--tb-danger);
}

.node__icon--md {
  color: var(--tb-info);
}

.node__icon--sheet {
  color: var(--tb-success);
}

.node__icon--slide {
  color: var(--tb-warning);
}

.node__icon--text {
  color: var(--tb-text-secondary);
}

.node__label {
  flex: 1;
  min-width: 0;
  font-size: 0.88rem;
  color: var(--tb-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node__zip {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 0;
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
  border: var(--tb-border-width) solid var(--tb-border);
}

/* 解压中指示器 */
.folder__spinner {
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--tb-primary-border);
  border-top-color: var(--tb-primary);
  animation: folder-spin 0.8s linear infinite;
}

@keyframes folder-spin {
  to {
    transform: rotate(360deg);
  }
}

.node__tag {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: 0;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--tb-warning);
  background: var(--tb-warning-soft);
  border: var(--tb-border-width) solid var(--tb-warning-soft);
}

.node__size {
  flex: 0 0 auto;
  font-size: 0.6rem;
  color: var(--tb-text-tertiary);
}

.folder__tree::-webkit-scrollbar {
  width: 5px;
}

.folder__tree::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--tb-border-strong);
}

.folder__tree :deep(.el-tree-node__content) {
  height: 28px;
  margin: 1px 0;
  padding-right: 4px;
  border-radius: 0;
  transition: background 0.16s ease;
}

.folder__tree :deep(.el-tree-node__label) {
  display: flex;
  flex: 1;
  min-width: 0;
}

.folder__tree :deep(.el-tree-node__expand-icon) {
  padding: 4px 2px 4px 0;
  font-size: 11px;
  color: var(--tb-text-tertiary);
}
</style>
