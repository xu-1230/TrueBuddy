<template>
  <div class="doc-studio">
    <div class="studio-body">
      <!-- ============ 左侧配置面板（查看模式下平滑收起） ============ -->
      <aside
        class="config-panel"
        :class="{ 'config-panel--collapsed': viewing }"
        :inert="viewing"
      >
        <div class="config-panel__scroll">
          <!-- 模板文档选择 -->
          <section class="panel-section">
            <div class="section-head">
              <span class="section-head__title">模板文档选择</span>
              <span class="section-head__hint">PDF / Word / Markdown</span>
            </div>

            <!-- 模板槽位：始终只有一个。空态是上传框，有模板时原地显示文件信息，不额外占位 -->
            <div class="template-slot">
              <el-upload
                v-model:file-list="fileList"
                class="template-upload"
                :class="{ 'template-upload--filled': templateDoc }"
                drag
                :auto-upload="false"
                :show-file-list="false"
                :disabled="uploading"
                accept=".pdf,.doc,.docx,.md,.markdown"
                @change="handleTemplateChange"
              >
                <!-- 上传中：单行加载提示 -->
                <span v-if="uploading" class="template-upload__inner">
                  <span class="template-upload__tile template-upload__tile--loading">
                    <span class="template-upload__spinner"></span>
                  </span>
                  <span class="template-upload__title">正在上传模板文件…</span>
                </span>

                <!-- 已选模板：单行文件信息（与空态同为一行，高度固定） -->
                <span
                  v-else-if="templateDoc"
                  class="template-upload__inner"
                >
                  <span
                    class="template-upload__type"
                    :class="`template-upload__type--${templateDoc.type.toLowerCase()}`"
                    :title="templateDoc.type"
                  >
                    <el-icon><component :is="templateIcon" /></el-icon>
                  </span>
                  <span class="template-upload__name" :title="templateDoc.name">
                    {{ templateDoc.name }}
                  </span>
                  <span class="template-upload__size">{{ templateDoc.size }}</span>
                </span>

                <!-- 空态：上传引导（单行：图标 + 文案） -->
                <span v-else class="template-upload__inner">
                  <span class="template-upload__tile">
                    <el-icon><Upload /></el-icon>
                  </span>
                  <span class="template-upload__title">点击或拖拽上传模板文件</span>
                </span>
              </el-upload>

              <button
                v-if="templateDoc && !uploading"
                type="button"
                class="template-slot__clear"
                title="移除模板"
                @click="removeTemplate"
              >
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </section>

          <!-- 知识库选择 -->
          <section class="panel-section">
            <div class="section-head">
              <span class="section-head__title">知识库选择</span>
              <span class="section-head__hint">用于检索增强生成</span>
            </div>
            <el-select
              v-model="selectedKbId"
              class="kb-select"
              popper-class="kb-select-popper"
              placeholder="请选择知识库"
              :loading="kbLoading"
              clearable
              no-data-text="暂无更多知识库"
            >
              <template #prefix>
                <el-icon class="kb-select__prefix"><Collection /></el-icon>
              </template>
              <el-option
                v-for="kb in kbOptions"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              >
                <span class="kb-option">
                  <span
                    class="kb-option__tile"
                    :class="{ 'kb-option__tile--active': selectedKbId === kb.id }"
                  >
                    <el-icon><Collection /></el-icon>
                  </span>
                  <span class="kb-option__main">
                    <span class="kb-option__name">{{ kb.name }}</span>
                    <span v-if="kb.description" class="kb-option__desc">
                      {{ kb.description }}
                    </span>
                  </span>
                  <span v-if="kb.isDefault" class="kb-option__tag">默认</span>
                  <span v-else class="kb-option__count">{{ kb.documentCount ?? 0 }} 篇</span>
                  <el-icon v-if="selectedKbId === kb.id" class="kb-option__check">
                    <Check />
                  </el-icon>
                </span>
              </el-option>
              <template #footer>
                <span>共 {{ kbOptions.length }} 个知识库</span>
              </template>
            </el-select>
          </section>

          <!-- AI 描述说明 -->
          <section class="panel-section">
            <div class="section-head">
              <span class="section-head__title">AI 描述说明</span>
              <span class="section-head__count">{{ description.length }}/{{ DESC_MAX }}</span>
            </div>
            <el-input
              v-model="description"
              type="textarea"
              :rows="5"
              :maxlength="DESC_MAX"
              resize="none"
              placeholder="请详细描述您想要生成的文档内容，例如：面向教育学的毕业论文，包含研究背景、文献综述、实验设计与结论…"
            />
          </section>

          <!-- 高级 -->
          <section class="panel-section">
            <button
              type="button"
              class="section-head section-head--toggle"
              @click="advancedOpen = !advancedOpen"
            >
              <span class="section-head__title">高级</span>
              <el-icon class="section-head__arrow">
                <ArrowDown v-if="advancedOpen" />
                <ArrowRight v-else />
              </el-icon>
            </button>
            <div v-show="advancedOpen" class="panel-section__body">
              <div class="field">
                <span class="field__label">清晰度</span>
                <el-segmented v-model="quality" :options="qualityOptions" size="small" />
              </div>
            </div>
          </section>
        </div>
        <footer class="config-panel__footer">
          <SquircleButton
            block
            class="create-btn"
            base="#2b4a80"
            dark="#24406f"
            :height="42"
            :disabled="!canGenerate"
            @click="handleGenerate"
          >
            <span class="create-btn__inner">
              <span>生成</span>
              <span class="create-btn__cost">
                <span class="coin"></span>
                {{ COST }}
              </span>
            </span>
          </SquircleButton>
        </footer>
      </aside>

      <!-- ============ 右侧工作区 ============ -->
      <section class="stage">
        <Transition name="stage-swap" mode="out-in">
        <div v-if="!viewing" key="browse" class="stage-body">
          <!-- 展示框 -->
          <div class="display-box">
            <div class="display-box__toolbar">
              <span class="display-box__badge">{{ displayBadge }}</span>
              <div class="display-box__actions">
                <button type="button" class="ghost-chip" @click="historyOpen = true">
                  <el-icon><Clock /></el-icon>
                  <span>历史记录查看</span>
                </button>
                <button
                  type="button"
                  class="ghost-chip"
                  :disabled="!canGenerate"
                  @click="handleGenerate"
                >
                  <el-icon><Refresh /></el-icon>
                  再次生成
                </button>
              </div>
            </div>
            <div
              class="display-box__canvas"
              :class="{ 'display-box__canvas--results': resultDocs.length && !generating }"
            >
              <!-- 生成中（模拟后端处理耗时） -->
              <div v-if="generating" class="result-state">
                <span class="result-state__spinner"></span>
                <p class="result-state__title">正在复刻模板文档…</p>
                <p class="result-state__desc">后端正在生成多份候选文档，请稍候</p>
              </div>

              <!-- 详情载入中（深链接 / 后退前进 / 点历史条目） -->
              <div v-else-if="generationLoading" class="result-state">
                <span class="result-state__spinner"></span>
                <p class="result-state__title">正在载入生成记录…</p>
                <p class="result-state__desc">正在获取该次生成的文档列表</p>
              </div>

              <!-- 详情载入失败（暂时性故障，保留 URL 供重试） -->
              <div v-else-if="generationLoadError" class="result-state">
                <el-icon class="result-state__icon"><WarningFilled /></el-icon>
                <p class="result-state__title">载入生成记录失败</p>
                <p class="result-state__desc">{{ generationLoadError }}</p>
                <button type="button" class="ghost-chip" @click="retryLoad">
                  <el-icon><Refresh /></el-icon>
                  <span>重新载入</span>
                </button>
              </div>

              <!-- 多文档结果列表（模拟后端一次返回多个文件） -->
              <div v-else-if="resultDocs.length" class="result-grid">
                <article
                  v-for="doc in resultDocs"
                  :key="doc.id"
                  class="result-card"
                  :class="`result-card--${doc.tone}`"
                  @click="openDoc(doc)"
                >
                  <div class="result-card__cover">
                    <span class="result-card__lines">
                      <span
                        v-for="n in 5"
                        :key="n"
                        class="result-card__line"
                        :style="{ width: lineWidth(n + 1) }"
                      ></span>
                    </span>
                  </div>
                  <h3 class="result-card__name" :title="doc.name">{{ doc.name }}</h3>
                  <p class="result-card__summary">{{ doc.summary }}</p>
                  <div class="result-card__foot">
                    <span class="result-card__meta">{{ docMeta(doc) }}</span>
                    <button type="button" class="result-card__view" @click.stop="openDoc(doc)">
                      <el-icon><View /></el-icon>
                      <span>查看</span>
                    </button>
                  </div>
                </article>
              </div>

              <!-- 该次生成失败 / 0 份 / 仍在生成 -->
              <div v-else-if="activeGenerationId" class="display-box__empty">
                <el-icon class="display-box__icon"><Picture /></el-icon>
                <p class="display-box__title">{{ generationEmptyState.title }}</p>
                <p class="display-box__desc">{{ generationEmptyState.desc }}</p>
              </div>

              <!-- 空状态 -->
              <div v-else class="display-box__empty">
                <el-icon class="display-box__icon"><Picture /></el-icon>
                <p class="display-box__title">生成结果将在这里展示</p>
                <p class="display-box__desc">
                  上传模板文档、填写 AI 描述后点击左下角「生成」，将生成多份同版式文档
                </p>
              </div>
            </div>
          </div>
        </div>

        <!-- ============ 查看模式：中间 docx 预览 + 右侧 AI 聊天面板 ============ -->
        <div v-else key="viewing" class="stage-body stage-body--viewing">
          <div class="viewer">
            <div class="viewer__toolbar">
              <button type="button" class="ghost-chip" @click="closeViewer">
                <el-icon><Back /></el-icon>
                <span>返回结果列表</span>
              </button>
              <button
                type="button"
                class="ghost-chip"
                :class="{ 'ghost-chip--active': picking }"
                :title="picking ? '退出元素选择' : '在文档中选择元素'"
                @click="togglePicking"
              >
                <el-icon><Mouse /></el-icon>
                <span>{{ picking ? '退出选择' : '选择元素' }}</span>
              </button>
              <span v-if="activeDoc" class="viewer__title" :title="activeDoc.name">
                <el-icon><Document /></el-icon>
                {{ activeDoc.name }}
              </span>
              <span v-if="pickedElement" class="viewer__picked" :title="pickedElement.label">
                <el-icon><Mouse /></el-icon>
                <span class="viewer__picked-text">{{ pickedElement.label }}</span>
              </span>
            </div>
            <div class="viewer__canvas">
              <DocxPreview
                v-if="activeDoc"
                :key="activeDoc.id"
                :src="activeDoc.src"
                :picking="picking"
                @pick="handlePick"
              />
            </div>
          </div>
          <AiChatPanel :document-name="activeDoc ? activeDoc.name : ''" />
        </div>
        </Transition>
      </section>
    </div>

    <!-- ============ 历史记录抽屉 ============ -->
    <transition name="fade">
      <div v-if="historyOpen" class="studio-overlay" @click="historyOpen = false"></div>
    </transition>
    <transition name="slide">
      <aside v-if="historyOpen" class="history-drawer">
        <header class="history-drawer__head">
          <div class="history-drawer__title">
            <el-icon><Clock /></el-icon>
            <span>历史记录</span>
          </div>
          <button type="button" class="history-drawer__close" @click="historyOpen = false">
            <el-icon><Close /></el-icon>
          </button>
        </header>
        <div v-if="historyLoading" class="history-drawer__loading">
          <span class="history-item__spinner"></span>
          <span>正在加载历史记录…</span>
        </div>
        <ul class="history-drawer__list">
          <li
            v-for="record in historyRecords"
            :key="record.id"
            class="history-item"
            :class="{
              'is-active': isActiveRecord(record),
              'is-no-doc': !record.docCount,
            }"
            role="button"
            tabindex="0"
            :title="record.docCount ? '点击查看该次生成的文档' : '点击查看该次生成记录'"
            @click="openHistory(record)"
            @keydown.enter="openHistory(record)"
            @keydown.space.prevent="openHistory(record)"
          >
            <div class="history-item__main">
              <span class="history-item__title">{{ record.title }}</span>
              <span
                class="history-item__status"
                :class="record.status === '失败' ? 'is-failed' : 'is-success'"
              >
                {{ record.status }}
              </span>
            </div>
            <div class="history-item__meta">
              <span>{{ record.kb }}</span>
              <span class="history-item__dot">·</span>
              <span>{{ record.type }}</span>
              <span class="history-item__dot">·</span>
              <span>{{ record.time }}</span>
              <span class="history-item__count">{{ record.docCount || 0 }} 份</span>
              <span class="history-item__open">
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </li>
        </ul>
        <el-empty
          v-if="!historyRecords.length && !historyLoading"
          :image-size="80"
          description="暂无历史记录"
          class="history-drawer__empty"
        />
      </aside>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  ArrowRight,
  Back,
  Check,
  Clock,
  Close,
  Collection,
  Document,
  Files,
  Mouse,
  Notebook,
  Picture,
  Refresh,
  Tickets,
  Upload,
  View,
  WarningFilled,
} from '@element-plus/icons-vue'
import { getSpaceList } from '@/api/knowledgebase'
import { uploadFile as uploadTemplateFile } from '@/api/file'
import { generateDoc, getGenerationDetail, getGenerationHistory } from '@/api/doctemplate'
import SquircleButton from './components/SquircleButton.vue'
import DocxPreview from './components/DocxPreview.vue'
import AiChatPanel from './components/AiChatPanel.vue'
import {
  docTemplateLocation,
  normalizePositiveId,
  parseDocTemplateRoute,
} from './utils/route'

const COST = 50
const DESC_MAX = 500
const LINE_WIDTHS = [92, 74, 86, 62, 80, 68, 96, 56, 84, 72]
/** 结果卡片配色轮转（后端按份序给出 tone 语义，此处映射为样式类） */
const TONE_CYCLE = ['indigo', 'emerald', 'amber']
/** 历史记录拉取条数 */
const HISTORY_LIMIT = 20
/** 任务状态 → badge 文案（1=成功不额外展示） */
const GENERATION_STATUS_TEXT = {
  0: '生成中',
  1: '',
  2: '部分成功',
  3: '失败',
}
/** 后端 GENERATION_NOT_EXISTS（见 tb-module-doctemplate ErrorCodeConstants） */
const GENERATION_NOT_EXISTS_CODE = 1_004_000_005

/* ---------- 模板文档（用户上传，作为生成基准；始终只保留一个） ---------- */
const TEMPLATE_TYPE_MAP = {
  doc: 'WORD',
  docx: 'WORD',
  pdf: 'PDF',
  md: 'MARKDOWN',
  markdown: 'MARKDOWN',
}

/** 模板类型 → 图标（已选模板的类型标识） */
const TEMPLATE_ICON_MAP = {
  WORD: Document,
  PDF: Tickets,
  MARKDOWN: Notebook,
  FILE: Files,
}

/** 当前模板：上传新文件直接替换，因此只会存在一个 */
const templateDoc = ref(null)
const fileList = ref([])
/** 模板文件是否正在上传 */
const uploading = ref(false)

const templateIcon = computed(
  () => TEMPLATE_ICON_MAP[templateDoc.value?.type] ?? Files,
)

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function resolveTemplateType(name) {
  const ext = name.split('.').pop()?.toLowerCase() ?? ''
  return TEMPLATE_TYPE_MAP[ext] ?? 'FILE'
}

/** el-upload 文件列表变化回调：真实上传后端，只保留最新上传的一份模板 */
async function handleTemplateChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) return
  const replaced = Boolean(templateDoc.value)
  uploading.value = true
  try {
    // 真实上传：POST /infra/file/upload（MinIO 存储 + infra_file 落库）
    const res = await uploadTemplateFile(raw)
    const name = res?.name ?? raw.name
    templateDoc.value = {
      // 后端返回的文件 id：供后续「生成」流程引用模板文件
      id: res?.id,
      uid: uploadFile.uid,
      name,
      size: formatFileSize(res?.size ?? raw.size),
      type: resolveTemplateType(name),
      url: res?.url,
      file: raw,
    }
    // 与 el-upload 内部列表保持一致，避免历史文件堆积
    fileList.value = [uploadFile]
    ElMessage.success(`${replaced ? '已替换' : '已添加'}模板文档：${name}`)
  } catch (e) {
    templateDoc.value = null
    fileList.value = []
    ElMessage.error(e?.message || '模板文件上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

function removeTemplate() {
  const name = templateDoc.value?.name
  templateDoc.value = null
  fileList.value = []
  if (name) ElMessage.info(`已移除模板文档：${name}`)
}

/* ---------- 知识库选择（来自 tb-module-knowledgebase 的空间列表） ---------- */
/** 内置默认知识库：始终置于列表首位，且作为默认选中项 */
const DEFAULT_KB = {
  id: 'default',
  name: '默认知识库',
  description: '系统内置，未指定时使用',
  isDefault: true,
}

const knowledgeBases = ref([])
const selectedKbId = ref(DEFAULT_KB.id)
const kbLoading = ref(false)

/** 下拉选项：内置默认知识库 + 接口返回的知识库 */
const kbOptions = computed(() => [DEFAULT_KB, ...knowledgeBases.value])

/** 当前选中的知识库对象 */
const selectedKnowledgeBase = computed(
  () => kbOptions.value.find((kb) => kb.id === selectedKbId.value) ?? null,
)

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    knowledgeBases.value = await getSpaceList()
  } catch {
    ElMessage.error('加载知识库列表失败')
  } finally {
    kbLoading.value = false
  }
}

onMounted(() => {
  loadKnowledgeBases()
  loadHistory()
})

/* ---------- AI 描述 ---------- */
const description = ref('')

/* ---------- 高级 ---------- */
const advancedOpen = ref(false)
const qualityOptions = ['480p', '720p', '1080p']
const quality = ref('720p')




const canGenerate = computed(
  () => Boolean(templateDoc.value && description.value.trim()) && !uploading.value,
)

/* ---------- URL 契约（URL 是页面状态的唯一真相源） ---------- */
const route = useRoute()
const router = useRouter()

/* ---------- 生成结果（后端一次返回多份复刻文档） ---------- */
const generating = ref(false)
const resultDocs = ref([])
/** 当前已载入的生成记录 id（字符串；null 表示基础生成页） */
const activeGenerationId = ref(null)
/** 当前生成记录的任务状态：0=生成中，1=成功，2=部分成功，3=失败 */
const activeGenerationStatus = ref(null)
/** 当前生成记录的错误摘要 */
const activeGenerationError = ref('')
/** 详情拉取中（区别于“正在生成”） */
const generationLoading = ref(false)
/** 详情拉取失败（暂时性故障，保留 URL 并允许重试） */
const generationLoadError = ref('')
/** 详情请求令牌：快速切换记录时丢弃过期响应 */
let loadToken = 0

const displayBadge = computed(() => {
  if (generationLoading.value) return '正在载入生成记录…'
  if (generationLoadError.value) return '生成记录 · 载入失败'
  if (activeGenerationId.value) {
    const state = GENERATION_STATUS_TEXT[activeGenerationStatus.value] ?? ''
    const parts = ['生成记录']
    if (state) parts.push(state)
    if (resultDocs.value.length) parts.push(`已载入 ${resultDocs.value.length} 份文档`)
    return parts.join(' · ')
  }
  return templateDoc.value ? templateDoc.value.name : '未选择模板'
})

/** 失败 / 生成中记录的空白结果区文案 */
const generationEmptyState = computed(() => {
  if (activeGenerationStatus.value === 0) {
    return { title: '该次生成仍在进行中', desc: '请稍后刷新页面查看结果' }
  }
  return {
    title: '该次生成没有产出文档',
    desc: activeGenerationError.value || '可以回到生成页调整模板或描述后重试',
  }
})

function lineWidth(index) {
  return `${LINE_WIDTHS[(index - 1) % LINE_WIDTHS.length]}%`
}

/** 结果卡片 meta：标签 · 大小（有页数时追加页数） */
function docMeta(doc) {
  const parts = []
  if (doc.tag) parts.push(doc.tag)
  if (doc.size) parts.push(doc.size)
  if (doc.pages) parts.push(`${doc.pages} 页`)
  return parts.join(' · ')
}

/** 后端 DocFileVO → 结果卡片数据 */
function mapDoc(doc, index) {
  return {
    id: doc.id ?? doc.fileId ?? `doc-${index}`,
    name: doc.name || `文档${index + 1}`,
    type: doc.type || 'WORD',
    tone: TONE_CYCLE[index % TONE_CYCLE.length],
    tag: doc.tag || '',
    summary: doc.summary || '',
    size: formatFileSize(doc.size),
    pages: null,
    createdAt: '',
    src: doc.src,
  }
}

/* ---------- 操作 ---------- */
async function handleGenerate() {
  if (!canGenerate.value) {
    ElMessage.warning('请先上传模板文档并填写描述')
    return
  }
  if (generating.value) return
  generating.value = true
  try {
    const res = await generateDoc({
      templateFileId: templateDoc.value.id,
      // 内置默认知识库不启用检索增强，统一传 "default" 由后端判定
      spaceId: selectedKbId.value === null || selectedKbId.value === undefined
        ? 'default'
        : String(selectedKbId.value),
      description: description.value.trim(),
      quality: quality.value,
    })
    const docs = (res?.docs ?? []).map(mapDoc)
    if (!docs.length) {
      // 生成失败不改 URL 与当前结果：保持地址与画面一致，只提示错误
      ElMessage.error(res?.errorMsg || '生成失败，请稍后重试')
      return
    }

    const generationId = normalizePositiveId(res?.generationId)
    if (generationId) {
      // 先落状态再改地址：watcher 会因“该 id 已载入”跳过重复 detail 请求
      applyLoadedGeneration({
        generationId,
        docs,
        status: res?.status ?? null,
        errorMsg: res?.errorMsg ?? '',
      })
      await navigate(docTemplateLocation(generationId), { replace: true })
    } else {
      // 后端未返回任务 id（理论上不会发生）：保底仍展示本次结果
      resultDocs.value = docs
      activeGenerationId.value = null
      activeGenerationStatus.value = res?.status ?? null
      activeGenerationError.value = res?.errorMsg ?? ''
    }

    if (res?.status === 2) {
      ElMessage.warning(`部分文档生成失败：${res?.errorMsg || ''}`)
    } else {
      const kbText = selectedKnowledgeBase.value ? `，知识库「${selectedKnowledgeBase.value.name}」` : ''
      ElMessage.success(
        `已基于模板「${templateDoc.value.name}」${kbText}返回 ${docs.length} 份文档`,
      )
    }
    // 生成后刷新历史记录
    loadHistory()
  } catch (e) {
    ElMessage.error(e?.message || '生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

/* ---------- 查看模式（docx-preview + AI 聊天面板） ---------- */
const viewing = ref(false)
const activeDoc = ref(null)

/* 文档元素选择：由查看区工具栏的鼠标按钮切换，命中与高亮由 DocxPreview 负责 */
const picking = ref(false)
const pickedElement = ref(null)

/** 仅改本地视图状态；对外入口一律走 URL（openDoc / closeViewer） */
function openDocState(doc) {
  activeDoc.value = doc
  viewing.value = true
  picking.value = false
  pickedElement.value = null
}

function closeViewerState() {
  viewing.value = false
  activeDoc.value = null
  picking.value = false
  pickedElement.value = null
}

function findDocById(fileId) {
  return resultDocs.value.find((doc) => String(doc.id) === String(fileId)) ?? null
}

/** 点结果卡：push ?file=，由 URL watcher 打开查看模式 */
function openDoc(doc) {
  if (!activeGenerationId.value || !doc?.id) return
  navigate(docTemplateLocation(activeGenerationId.value, doc.id))
}

/** 返回结果列表：push 去掉 file 的地址（不用 back，保证深链接刷新后可用） */
function closeViewer() {
  if (!activeGenerationId.value) {
    closeViewerState()
    return
  }
  navigate(docTemplateLocation(activeGenerationId.value))
}

function togglePicking() {
  picking.value = !picking.value
}

function handlePick(payload) {
  pickedElement.value = payload
}


/* ---------- 历史记录（后端真实生成记录） ---------- */
const historyOpen = ref(false)
const historyRecords = ref([])
const historyLoading = ref(false)

async function loadHistory() {
  historyLoading.value = true
  try {
    historyRecords.value = await getGenerationHistory(HISTORY_LIMIT)
  } catch (e) {
    // 历史加载失败不阻断主流程
    ElMessage.error(e?.message || '加载历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

/** 当前载入的记录是否就是这条历史 */
function isActiveRecord(record) {
  return Boolean(activeGenerationId.value) && String(record?.id) === activeGenerationId.value
}

/**
 * 点击历史条目：立即关闭抽屉并 push 到 /:id；详情载入交给 URL watcher。
 * 0 份 / 失败记录同样进入 URL —— 深链接与点击走同一条路径。
 */
function openHistory(record) {
  if (!record?.id) return
  historyOpen.value = false
  navigate(docTemplateLocation(record.id))
}

watch(historyOpen, (open) => {
  if (open) loadHistory()
})

/* ---------- URL → 页面状态 ---------- */

/** 规范化跳转：同地址不发请求，避免 redundant navigation */
function navigate(location, { replace = false } = {}) {
  const target = router.resolve(location).fullPath
  if (target === route.fullPath) return Promise.resolve()
  return replace ? router.replace(location) : router.push(location)
}

function isGenerationNotExists(error) {
  return error?.code === GENERATION_NOT_EXISTS_CODE
}

/** 按 ?file= 切换查看模式；文件不存在时剔除 query 回到结果列表 */
function applyGenerationFile(fileId) {
  const doc = fileId ? findDocById(fileId) : null
  if (fileId && !doc) {
    ElMessage.warning('文档不存在或不属于该次生成，已返回结果列表')
    navigate(docTemplateLocation(activeGenerationId.value), { replace: true })
    return
  }
  if (doc) openDocState(doc)
  else closeViewerState()
}

/** 一次生成的结果落库（本次生成 / URL 载入共用） */
function applyLoadedGeneration({ generationId, docs, status, errorMsg }) {
  // 作废可能仍在途的 detail 请求
  loadToken++
  generationLoading.value = false
  generationLoadError.value = ''
  resultDocs.value = docs
  activeGenerationId.value = generationId
  activeGenerationStatus.value = status ?? null
  activeGenerationError.value = errorMsg || ''
  closeViewerState()
}

/** 回到基础生成页：清空结果与查看态，保留表单输入 */
function resetLoadedGeneration() {
  loadToken++
  generationLoading.value = false
  generationLoadError.value = ''
  resultDocs.value = []
  activeGenerationId.value = null
  activeGenerationStatus.value = null
  activeGenerationError.value = ''
  closeViewerState()
}

/** 拉取某次生成的详情并落状态；失败按确定性 / 暂时性分层处理 */
async function loadGeneration(generationId, fileId, hasInvalidFile) {
  const token = ++loadToken
  generationLoading.value = true
  generationLoadError.value = ''
  // 先清空上一份记录，避免地址与画面错配
  resultDocs.value = []
  activeGenerationId.value = null
  activeGenerationStatus.value = null
  activeGenerationError.value = ''
  closeViewerState()
  try {
    const res = await getGenerationDetail(generationId)
    if (token !== loadToken) return
    resultDocs.value = (res?.docs ?? []).map(mapDoc)
    activeGenerationId.value = generationId
    activeGenerationStatus.value = res?.status ?? null
    activeGenerationError.value = res?.errorMsg ?? ''
    if (hasInvalidFile) {
      ElMessage.warning('文档参数无效，已返回结果列表')
      navigate(docTemplateLocation(generationId), { replace: true })
      return
    }
    applyGenerationFile(fileId)
  } catch (e) {
    if (token !== loadToken) return
    if (isGenerationNotExists(e)) {
      // 确定性坏地址：回基础页
      ElMessage.error(e?.message || '生成记录不存在')
      navigate(docTemplateLocation(), { replace: true })
      return
    }
    // 暂时性故障：保留 URL，交由结果区提供「重新载入」
    generationLoadError.value = e?.message || '载入生成记录失败'
  } finally {
    if (token === loadToken) generationLoading.value = false
  }
}

/** 路由变化 → 载入 / 复位；同一条记录只切换查看状态，不重复请求详情 */
async function syncFromRoute(rawId, rawFile) {
  const { generationId, fileId, hasInvalidId, hasInvalidFile, hasFileParam } =
    parseDocTemplateRoute({ id: rawId }, { file: rawFile })

  if (!generationId) {
    resetLoadedGeneration()
    if (hasInvalidId) {
      ElMessage.warning('生成记录地址无效，已返回生成页')
      await navigate(docTemplateLocation(), { replace: true })
      return
    }
    if (hasFileParam) {
      // 有 file 无 id：畸形地址，静默剔除
      await navigate(docTemplateLocation(), { replace: true })
    }
    return
  }

  if (generationId === activeGenerationId.value) {
    if (hasInvalidFile) {
      ElMessage.warning('文档参数无效，已返回结果列表')
      await navigate(docTemplateLocation(generationId), { replace: true })
      return
    }
    applyGenerationFile(fileId)
    return
  }

  await loadGeneration(generationId, fileId, hasInvalidFile)
}

/** 暂时性失败后的「重新载入」 */
function retryLoad() {
  const { generationId, fileId, hasInvalidFile } = parseDocTemplateRoute(route.params, route.query)
  if (generationId) loadGeneration(generationId, fileId, hasInvalidFile)
}

watch(
  [() => route.params.id, () => route.query.file],
  ([rawId, rawFile]) => {
    syncFromRoute(rawId, rawFile)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  // 页面卸载后丢弃在途响应
  loadToken++
})
</script>

<style scoped>
/* ================= 主题变量 ================= */
.doc-studio {
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 2rem);
  min-height: 640px;
  /* border-radius: 14px; */
  overflow: hidden;
  color: var(--tb-text-primary);
  box-shadow: var(--tb-shadow-2);

  /* 页面级兼容别名：一律指向全局 Token（src/assets/theme.css），
     页面内既有的 var(--primary) 等引用无需逐个替换即可随主题换肤 */
  --primary: var(--tb-primary);
  --primary-dark: var(--tb-primary-text);
  --primary-soft: var(--tb-primary-soft);
  --primary-soft-2: var(--tb-primary-border);
  --accent: var(--tb-accent);
  --accent-deep: color-mix(in oklab, var(--tb-accent) 72%, #000000);
  --accent-soft: var(--tb-accent-soft);
  --accent-soft-2: var(--tb-accent-soft);

  /* Element Plus 主色与圆角交由全局 Token 层映射（原页面级 --el-* 覆盖已移除），
     仅保留 EP 未映射的滑块主色 */
  --el-slider-main-bg-color: var(--tb-primary);
}

/* ================= 主体 ================= */
/* 左右两栏由 .studio-body 统一留白，保证配置面板与工作区的上/下边界完全对齐 */
.studio-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
}

/* ---------------- 左侧配置面板 ---------------- */
.config-panel {
  flex: 0 0 auto;
  width: 344px;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  /* border-radius: 12px; */
  background: var(--tb-bg-surface);
  /* 用 ring 代替 border：不占用内容宽度，收起时可整体淡出 */
  /* box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border); */
  overflow: hidden;
  border: var(--tb-border-width) solid var(--tb-border);
  /* 查看模式：宽度与间距同步过渡，配合内层内容左移 */
  transition: width 0.36s cubic-bezier(0.4, 0, 0.2, 1),
    margin-right 0.36s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.26s ease;
}

.config-panel--collapsed {
  width: 0;
  margin-right: -0.9rem;
  opacity: 0;
  pointer-events: none;
}

/* 固定内宽：收起动画期间文字不重排，多余部分由 overflow 裁掉 */
.config-panel__scroll,
.config-panel__footer {
  min-width: 344px;
}

.config-panel__scroll {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
  padding: 1rem;
  overflow-y: auto;
  transition: transform 0.36s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-panel__footer {
  transition: transform 0.36s cubic-bezier(0.4, 0, 0.2, 1);
}

.config-panel--collapsed .config-panel__scroll,
.config-panel--collapsed .config-panel__footer {
  transform: translateX(-26px);
}

.panel-section {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.panel-section__body {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
}

.section-head--toggle {
  cursor: pointer;
}

.section-head__title {
  display: inline-flex;
  align-items: center;
  font-size: 0.99rem;
  font-weight: 500;
  letter-spacing: 0.2px;
  color: var(--tb-text-primary);
}

/* 琥珀橙色条：统一的分节视觉标记 */
.section-head__title::before {
  /* content: '';
  width: 3px;
  height: 12px;

  border-radius: 2px;
  background: linear-gradient(180deg, #fbbf24, #f59e0b);
 */
  margin-right: 6px;
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background-color: var(--tb-primary-text);
}

.section-head__hint {
  font-size: 0.7rem;
  color: var(--tb-text-tertiary);
}

.section-head__count {
  font-size: 0.7rem;
  color: var(--tb-text-tertiary);
}

.section-head__arrow {
  font-size: 14px;
  color: var(--tb-text-tertiary);
}

/* ---------------- 模板文档选择 ---------------- */
.template-slot {
  position: relative;
}

/* 移除按钮：竖直居中于模板槽位右侧 */
.template-slot__clear {
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

.template-slot__clear:hover {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
  border-color: color-mix(in oklab, var(--tb-danger) 35%, transparent);
  transform: translateY(-50%) scale(1.08);
}

/* ---------------- 模板上传 ---------------- */
.template-upload :deep(.el-upload) {
  display: block;
  width: 100%;
}

.template-upload :deep(.el-upload-dragger) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  box-sizing: border-box;
  width: 100%;
  /* 固定高度 50px：空态与已选态完全一致，切换时面板不跳动 */
  height: 50px;
  padding: 0 0.7rem;
  overflow: hidden;
  border: var(--tb-border-width) dashed var(--tb-border);
  border-radius: 0px;
  background: var(--tb-bg-sunken);
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease,
    transform 0.2s ease;
}

.template-upload :deep(.el-upload-dragger:hover) {
  border-color: var(--tb-primary-text);
  background: var(--tb-primary-soft);
  box-shadow: var(--tb-shadow-brand);
  transform: translateY(-1px);
}

.template-upload :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--primary);
  border-style: solid;
  background: var(--primary-soft);
  box-shadow: 0 0 0 4px var(--tb-ring);
  transform: translateY(-1px);
}

/* 键盘操作时的聚焦反馈 */
.template-upload :deep(.el-upload:focus-visible .el-upload-dragger) {
  border-color: var(--primary);
  box-shadow: 0 0 0 4px var(--tb-ring);
}

/* 已选模板：仅换配色（虚线框 → 白底实线卡），高度仍是 50px */
.template-upload--filled :deep(.el-upload-dragger) {
  /* 右侧给移除按钮留位 */
  padding-right: 2.1rem;
  border-style: solid;
  border-color: var(--tb-border);
  background: var(--tb-bg-surface);
}

.template-upload--filled :deep(.el-upload-dragger:hover) {
  border-color: var(--tb-primary-border);
  background: var(--primary-soft);
}

/* 两种状态均为单行横向排布 */
.template-upload__inner {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  min-width: 0;
  text-align: left;
}

/* 空态图标：直角方块（镂空：描边 + 线性图标，无实心底色） */
.template-upload__tile {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 0;
  font-size: 15px;
  color: var(--primary);
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
  transition: color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease,
    transform 0.2s ease;
}

.template-upload :deep(.el-upload-dragger:hover) .template-upload__tile {
  color: var(--primary-dark);
  background: var(--primary-soft);
  box-shadow: 0 0 0 1px var(--primary) inset;
  transform: translateY(-1px);
}

/* 上传中：直角方块内显示旋转指示器（与空态同为镂空描边） */
.template-upload__tile--loading {
  background: var(--primary-soft);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
}

.template-upload__spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--tb-primary-border);
  border-top-color: var(--primary);
  animation: doc-studio-spin 0.8s linear infinite;
}

/* 已选态类型图标（按类型着色，与空态图标方块同尺寸） */
.template-upload__type {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 0;
  font-size: 15px;
  color: var(--tb-on-primary);
  background: linear-gradient(135deg, var(--tb-primary-hover), var(--tb-primary));
}

.template-upload__type--pdf {
  background: linear-gradient(135deg, color-mix(in oklab, var(--tb-danger) 55%, #ffffff), var(--tb-danger));
}

.template-upload__type--markdown {
  background: linear-gradient(135deg, color-mix(in oklab, var(--tb-info) 55%, #ffffff), var(--tb-info));
}

.template-upload__type--file {
  background: linear-gradient(135deg, var(--tb-text-tertiary), var(--tb-text-secondary));
}

.template-upload__title {
  min-width: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-upload__name {
  flex: 1;
  min-width: 0;
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-upload__size {
  flex: 0 0 auto;
  font-size: 0.66rem;
  color: var(--tb-text-tertiary);
}

/* ---------------- 知识库选择 ---------------- */
.doc-studio :deep(.kb-select) {
  width: 100%;
}

.doc-studio :deep(.kb-select .el-select__wrapper) {
  min-height: 40px;
  padding: 0.3rem 0.75rem;
  border-radius: 0px;
  background: var(--tb-bg-sunken);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
  font-size: 0.8rem;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

.doc-studio :deep(.kb-select .el-select__wrapper:hover) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border-strong) inset;
}

.doc-studio :deep(.kb-select .el-select__wrapper.is-focused) {
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 1px var(--primary) inset;
}

.doc-studio :deep(.kb-select .el-select__placeholder) {
  color: var(--tb-text-tertiary);
}

.kb-select__prefix {
  flex: 0 0 auto;
  font-size: 15px;
  color: var(--primary);
}

/* 选项内容（teleport 后仍带 scopeId，可继续用 scoped 样式） */
.kb-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 1px 0;
}

.kb-option__tile {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 0;
  font-size: 15px;
  color: var(--primary-dark);
  background: linear-gradient(135deg, var(--tb-primary-soft), var(--tb-primary-border));
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-primary-border) inset;
  transition: color 0.18s ease, background 0.18s ease;
}

.kb-option__tile--active {
  color: var(--tb-on-primary);
  background: linear-gradient(135deg, var(--tb-primary-hover), var(--tb-primary));
  box-shadow: none;
}

.kb-option__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.kb-option__name {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-option__desc {
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-option__count {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 0;
  font-size: 0.66rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
  background: var(--tb-bg-sunken);
  transition: color 0.18s ease, background 0.18s ease;
}

/* 内置默认知识库标记 */
.kb-option__tag {
  flex: 0 0 auto;
  padding: 1px 7px;
  border-radius: 0;
  font-size: 0.64rem;
  font-weight: 600;
  color: var(--accent-deep);
  background: var(--accent-soft);
  border: 1px solid var(--accent-soft-2);
  transition: color 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.kb-option__check {
  flex: 0 0 auto;
  font-size: 14px;
  color: var(--primary);
}

/* ---------------- 表单字段 ---------------- */
.field {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.field__label {
  font-size: 0.74rem;
  color: var(--tb-text-secondary);
}

.field__label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field__value {
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--primary-dark);
}

.doc-studio :deep(.el-textarea__inner) {
  background: var(--tb-bg-sunken);
  border-radius: 0;
  padding: 0.65rem 0.75rem;
  font-size: 0.8rem;
  line-height: 1.7;
  color: var(--tb-text-primary);
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border) inset;
}

.doc-studio :deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 var(--tb-border-width) var(--tb-border-strong) inset;
}

.doc-studio :deep(.el-textarea__inner:focus) {
  background: var(--tb-bg-surface);
  box-shadow: 0 0 0 1px var(--primary) inset;
}

/* 分段控件浅色适配 */
.doc-studio :deep(.el-segmented) {
  --el-segmented-bg-color: var(--tb-bg-sunken);
  --el-segmented-color: var(--tb-text-secondary);
  --el-segmented-item-selected-bg-color: var(--tb-bg-surface);
  --el-segmented-item-selected-color: var(--primary-dark);
  --el-segmented-item-hover-bg-color: var(--tb-bg-sunken);
  --el-segmented-item-hover-color: var(--tb-text-primary);
  --el-segmented-item-active-bg-color: var(--tb-border);
  padding: 3px;
  border-radius: 0;
}

.doc-studio :deep(.el-segmented__item),
.doc-studio :deep(.el-segmented__item-selected) {
  border-radius: 0;
}

.doc-studio :deep(.el-segmented__item) {
  font-size: 0.74rem;
}

.doc-studio :deep(.el-segmented__item-selected) {
  box-shadow: var(--tb-shadow-1);
}

/* 滑块浅色适配 */
.doc-studio :deep(.el-slider) {
  padding: 0 6px;
}

.doc-studio :deep(.el-slider__button) {
  width: 14px;
  height: 14px;
  border: 2px solid var(--tb-bg-surface);
  background: var(--primary);
  box-shadow: var(--tb-shadow-brand);
}

/* ---------------- 底部按钮 ---------------- */
.config-panel__footer {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.9rem 1rem 1rem;
  border-top: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
}

/* ---------------- 创作按钮：Squircle 3D 按钮（SquircleButton 组件） ---------------- */
.create-btn {
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: 0.4px;
  color: var(--tb-on-primary);
}

.create-btn__inner {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
}

.create-btn__cost {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 10px;
  border-radius: var(--tb-radius-full);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--tb-on-primary);
  background: color-mix(in srgb, var(--tb-on-primary) 20%, transparent);
}

.coin {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: linear-gradient(135deg, color-mix(in oklab, var(--tb-accent) 40%, #ffffff), var(--tb-accent));
  box-shadow: 0 0 0 1.5px color-mix(in oklab, var(--tb-accent) 45%, transparent);
}

.reward-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  height: 38px;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  color: var(--tb-text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.reward-btn .el-icon {
  color: var(--accent);
}

.reward-btn:hover {
  color: var(--accent-deep);
  border-color: var(--accent-soft-2);
  background: var(--accent-soft);
}

/* ================= 右侧工作区 ================= */
.stage {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

.stage-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 0.9rem;
}

/* ---------------- 展示框 ---------------- */
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

.display-box__badge {
  padding: 3px 10px;
  border-radius: 0;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--accent-deep);
  background: var(--accent-soft);
  border: 1px solid var(--accent-soft-2);
}

.display-box__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.ghost-chip {
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

.ghost-chip--active {
  color: var(--primary-dark);
  border-color: var(--tb-primary-border);
  background: var(--primary-soft);
  box-shadow: 0 0 0 3px var(--tb-ring);
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

.display-box__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  text-align: center;
}

.display-box__icon {
  font-size: 46px;
  color: var(--primary);
}

.display-box__title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.display-box__desc {
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
}

/* ---------------- 生成中状态 ---------------- */
.result-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
  text-align: center;
}

.result-state__spinner {
  width: 32px;
  height: 32px;
  margin-bottom: 0.35rem;
  border-radius: 50%;
  border: 3px solid var(--tb-primary-border);
  border-top-color: var(--primary);
  animation: doc-studio-spin 0.8s linear infinite;
}

.result-state__icon {
  margin-bottom: 0.35rem;
  font-size: 34px;
  color: var(--tb-danger);
}

.result-state .ghost-chip {
  margin-top: 0.35rem;
}

.result-state__title {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
}

.result-state__desc {
  margin: 0;
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
}

/* ---------------- 多文档结果卡片 ---------------- */
.display-box__canvas--results {
  display: block;
  padding: 1.25rem;
  overflow-y: auto;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
  gap: 0.9rem;
}

.result-card {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.75rem;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.result-card:hover {
  transform: translateY(-3px);
  border-color: var(--tb-primary-border);
  box-shadow: var(--tb-shadow-3);
}

.result-card__cover {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  height: 78px;
  padding: 26px 10px 10px;
  border-radius: 0;
  background: var(--tb-bg-sunken);
  border: var(--tb-border-width) solid var(--tb-border);
  overflow: hidden;
}






.result-card__lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.result-card__line {
  display: block;
  height: 3px;
  border-radius: 0;
  background: var(--tb-border);
}

.result-card__name {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--tb-text-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-card__summary {
  margin: 0;
  font-size: 0.72rem;
  line-height: 1.6;
  color: var(--tb-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: auto;
  padding-top: 0.5rem;
  border-top: 1px dashed var(--tb-border);
}

.result-card__meta {
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
}

.result-card__view {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  height: 26px;
  padding: 0 0.6rem;
  border: none;
  border-radius: 0;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--tb-on-primary);
  background: linear-gradient(135deg, var(--tb-primary-hover), var(--tb-primary-active));
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.result-card__view:hover {
  transform: translateY(-1px);
  box-shadow: var(--tb-shadow-brand);
}

/* ---------------- 查看模式 ---------------- */
.stage-body--viewing {
  align-items: stretch;
}

.viewer {
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

.viewer__toolbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
  border-bottom: 1px solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.viewer__title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer__title .el-icon {
  flex: 0 0 auto;
  color: var(--primary);
}



/* 已选中的文档元素 */
.viewer__picked {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  min-width: 0;
  max-width: 240px;
  padding: 2px 9px;
  border-radius: 0;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border: 1px solid var(--primary-soft-2);
}

.viewer__picked .el-icon {
  flex: 0 0 auto;
}

.viewer__picked-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer__canvas {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

@keyframes doc-studio-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ================= 历史记录抽屉 ================= */
.studio-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  background: var(--tb-overlay);
  backdrop-filter: blur(2px);
}

.history-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 21;
  width: 360px;
  max-width: 86%;
  display: flex;
  flex-direction: column;
  background: var(--tb-bg-surface);
  border-left: 1px solid var(--tb-border);
  box-shadow: var(--tb-shadow-4);
}

.history-drawer__head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--tb-border);
}

.history-drawer__title {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.history-drawer__title .el-icon {
  color: var(--accent);
}

.history-drawer__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 0;
  background: transparent;
  color: var(--tb-text-tertiary);
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.history-drawer__close:hover {
  background: var(--tb-bg-raised);
  color: var(--tb-text-primary);
}

.history-drawer__list {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0.75rem;
  list-style: none;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.history-item {
  padding: 0.7rem 0.85rem;
  border-radius: 0;
  background: var(--tb-bg-sunken);
  border: var(--tb-border-width) solid var(--tb-border);
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.history-item:hover {
  border-color: var(--tb-primary-border);
  background: var(--primary-soft);
}

.history-item:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

/* 当前结果列表正来自这条记录 */
.history-item.is-active {
  border-color: var(--primary);
  background: var(--tb-primary-soft);
}

/* 该次生成没有产出文档：弱化展示，但仍可点进 URL 查看失败记录 */
.history-item.is-no-doc {
  opacity: 0.75;
}

.history-drawer__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1.2rem 0;
  font-size: 0.78rem;
  color: var(--tb-text-secondary);
}

.history-item__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.history-item__title {
  min-width: 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--tb-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item__status {
  flex: 0 0 auto;
  padding: 1px 8px;
  border-radius: 0;
  font-size: 0.68rem;
  font-weight: 600;
}

.history-item__status.is-success {
  color: var(--tb-success);
  background: var(--tb-success-soft);
}

.history-item__status.is-failed {
  color: var(--tb-danger);
  background: var(--tb-danger-soft);
}

.history-item__meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.4rem;
  white-space: nowrap;
  font-size: 0.72rem;
  color: var(--tb-text-tertiary);
}

.history-item__dot {
  color: #d1d5db;
}

/* 产出份数：贴右显示，与右侧箭头一起构成「可查看」示意 */
.history-item__count {
  margin-left: auto;
  padding: 1px 7px;
  border-radius: 0;
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
  font-weight: 600;
  font-size: 0.68rem;
}

.history-item__open {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: var(--tb-primary-border);
  font-size: 0.8rem;
  transition: color 0.2s ease, transform 0.2s ease;
}

.history-item:hover .history-item__open {
  color: var(--primary);
  transform: translateX(2px);
}

.history-item__spinner {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2px solid var(--tb-primary-border);
  border-top-color: var(--primary);
  animation: doc-studio-spin 0.8s linear infinite;
}

.history-drawer__empty {
  margin-top: 4rem;
}

/* ================= 过渡动画 ================= */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.26s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

/* ================= 滚动条 ================= */
.config-panel__scroll::-webkit-scrollbar,
.history-drawer__list::-webkit-scrollbar {
  width: 6px;
}

.config-panel__scroll::-webkit-scrollbar-thumb,
.history-drawer__list::-webkit-scrollbar-thumb {
  border-radius: 0;
  background: var(--tb-border-strong);
}

/* ================= 模式切换过渡 ================= */
/* 中间工作区在「结果列表 / 文档查看」之间横向瞬滑（仅 X 轴，无纵向位移与缩放） */
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

/* 查看模式：聊天面板紧随其后自右侧滑入（同样仅 X 轴） */
.stage-body--viewing .ai-chat {
  animation: chat-in 0.36s cubic-bezier(0.22, 1, 0.36, 1) 0.04s both;
}

@keyframes chat-in {
  from {
    opacity: 0;
    transform: translateX(22px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .stage-body--viewing .ai-chat {
    animation: none;
  }
}

/* ================= 响应式 ================= */
@media (max-width: 1024px) {
  .doc-studio {
    height: auto;
    min-height: 0;
  }

  .studio-body {
    flex-direction: column;
  }

  /* 纵向排列时改为沿高度方向平滑收起 */
  .config-panel {
    width: auto;
    max-height: 3000px;
    margin-right: 0;
    transition: max-height 0.4s cubic-bezier(0.4, 0, 0.2, 1),
      margin-bottom 0.36s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.26s ease;
  }

  .config-panel--collapsed {
    width: auto;
    max-height: 0;
    margin-right: 0;
    margin-bottom: -0.9rem;
  }

  .config-panel__scroll,
  .config-panel__footer {
    min-width: 0;
  }

  .config-panel__scroll {
    overflow: visible;
  }

  .stage {
    min-height: 520px;
  }

  /* 查看模式：380px 聊天列在 1024px 以下会挤压 docx 预览，改为上下堆叠 */
  .stage-body--viewing {
    flex-direction: column;
  }

  .stage-body--viewing .viewer {
    flex: 0 0 auto;
    height: min(60vh, 560px);
    min-height: 420px;
  }
}

@media (max-width: 720px) {
  .stage-body {
    flex-direction: column;
  }

  .viewer {
    min-height: 380px;
  }
}
</style>

<style>
/* ================= 知识库下拉浮层 =================
   el-select 的下拉默认 teleport 到 body，脱离组件根节点，
   scoped 样式与 :deep() 均无法命中，因此这里使用非 scoped 样式，
   并通过 popper-class="kb-select-popper" 精确限定作用范围。
   重复类名用于提升优先级，覆盖 Element Plus 默认的 .el-select__popper 样式。 */
.el-popper.kb-select-popper.kb-select-popper {
  padding: 0;
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: 0;
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-4);
  overflow: hidden;
}

.el-popper.kb-select-popper .el-popper__arrow::before {
  border: var(--tb-border-width) solid var(--tb-border);
  background: var(--tb-bg-surface);
}

.kb-select-popper .el-select-dropdown__list {
  padding: 6px;
}

.kb-select-popper .el-select-dropdown__item {
  height: auto;
  margin: 0 0 2px;
  padding: 6px 8px;
  border-radius: 0;
  line-height: 1.4;
  color: var(--tb-text-primary);
  transition: background 0.18s ease, color 0.18s ease;
}

.kb-select-popper .el-select-dropdown__item:last-child {
  margin-bottom: 0;
}

.kb-select-popper .el-select-dropdown__item.is-hovering,
.kb-select-popper .el-select-dropdown__item:hover {
  background: var(--tb-primary-soft);
}

.kb-select-popper .el-select-dropdown__item.is-selected {
  background: var(--tb-primary-soft);
  color: var(--tb-primary-text);
}

.kb-select-popper .el-select-dropdown__item.is-selected .kb-option__name {
  color: var(--tb-primary-text);
}

.kb-select-popper .el-select-dropdown__item.is-selected .kb-option__count {
  color: var(--tb-primary-text);
  background: var(--tb-bg-surface);
}

.kb-select-popper .el-select-dropdown__item.is-selected .kb-option__tag {
  color: var(--tb-primary-text);
  background: var(--tb-bg-surface);
  border-color: var(--tb-primary-border);
}

.kb-select-popper .el-select-dropdown__empty {
  padding: 18px 0;
  font-size: 0.76rem;
  color: var(--tb-text-tertiary);
}

.kb-select-popper .el-select-dropdown__footer {
  padding: 6px 12px;
  border-top: 1px solid var(--tb-border);
  background: var(--tb-bg-sunken);
  font-size: 0.68rem;
  color: var(--tb-text-tertiary);
  text-align: right;
}
</style>

