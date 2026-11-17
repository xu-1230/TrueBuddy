<template>
  <div class="workflow-list-view">
    <header class="wf-hero">
      <div class="wf-hero__text">
        <h1 class="wf-hero__title">工作流</h1>
        <p class="wf-hero__desc">内置模板与自建流程；公有库里的他人流程可只读浏览，或添加为自己的副本。</p>
      </div>
      <el-button type="primary" @click="createWorkflow">
        <el-icon class="btn-icon"><Plus /></el-icon>
        新建工作流
      </el-button>
    </header>

    <!-- 我的工作流：内置模板单列小组置顶，用户自建在其下 -->
    <section class="wf-section">
      <div class="wf-section__header">
        <div class="wf-section__title">
          <el-icon><Connection /></el-icon>
          <h2>我的工作流</h2>
        </div>
        <span class="wf-section__desc">内置模板与自建流程</span>
      </div>
      <div
        v-loading="myLoading && myWorkflows.length > 0"
        class="wf-section__body"
        :aria-busy="myLoading"
      >
        <el-row v-if="myLoading && !myWorkflows.length" :gutter="16" aria-hidden="true">
          <el-col
            v-for="n in SKELETON_COUNT"
            :key="n"
            class="wf-col wf-col--skeleton"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <WorkflowCardSkeleton />
          </el-col>
        </el-row>

        <template v-else>
          <div v-if="grouped.builtin.length" class="wf-subgroup">
            <h3 class="wf-subgroup__title">内置模板</h3>
            <el-row :gutter="16">
              <el-col
                v-for="item in grouped.builtin"
                :key="item.id"
                class="wf-col"
                :xs="24"
                :sm="12"
                :md="8"
                :lg="6"
              >
                <WorkflowCard :workflow="item" builtin @click="openWorkflow(item)" />
              </el-col>
            </el-row>
          </div>

          <div v-if="grouped.mine.length" class="wf-subgroup">
            <h3 class="wf-subgroup__title">我的流程</h3>
            <el-row :gutter="16">
              <el-col
                v-for="item in grouped.mine"
                :key="item.id"
                class="wf-col"
                :xs="24"
                :sm="12"
                :md="8"
                :lg="6"
              >
                <WorkflowCard
                  :workflow="item"
                  :visibility-busy="visibilityBusyId === item.id"
                  :deleting="deletingId === item.id"
                  @click="openWorkflow(item)"
                  @toggle-visibility="toggleVisibility(item)"
                  @rename="openRename(item)"
                  @delete="removeWorkflow(item)"
                />
              </el-col>
            </el-row>
          </div>

          <el-empty
            v-if="!myLoading && !grouped.builtin.length && !grouped.mine.length"
            description="还没有工作流，先新建一个吧"
          >
            <el-button type="primary" @click="createWorkflow">新建工作流</el-button>
          </el-empty>
        </template>

        <p v-if="myLoading" class="wf-sr-only" role="status">正在加载我的工作流…</p>
      </div>
    </section>

    <!-- 公有库：他人公开的工作流，只读浏览或 fork -->
    <section class="wf-section wf-section--public">
      <div class="wf-section__header">
        <div class="wf-section__title">
          <el-icon><User /></el-icon>
          <h2>公有库</h2>
        </div>
        <span class="wf-section__desc">他人公开的工作流，打开后为只读画布</span>
      </div>
      <div
        v-loading="publicLoading && publicWorkflows.length > 0"
        class="wf-section__body"
        :aria-busy="publicLoading"
      >
        <el-row v-if="publicLoading && !publicWorkflows.length" :gutter="16" aria-hidden="true">
          <el-col
            v-for="n in SKELETON_COUNT"
            :key="n"
            class="wf-col wf-col--skeleton"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <WorkflowCardSkeleton />
          </el-col>
        </el-row>

        <el-row v-else :gutter="16">
          <el-col
            v-for="item in publicWorkflows"
            :key="item.id"
            class="wf-col"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <WorkflowCard
              :workflow="item"
              readonly
              :forking="forkingId === item.id"
              @click="openWorkflow(item)"
              @fork="forkPublic(item)"
            />
          </el-col>
        </el-row>

        <p v-if="!publicLoading && !publicWorkflows.length" class="wf-public-empty">
          暂无他人公开的工作流
        </p>
        <p v-if="publicLoading" class="wf-sr-only" role="status">正在加载公有库…</p>
      </div>
    </section>

    <!-- 重命名：只改名称，画布内容仍走画布页保存 -->
    <el-dialog
      v-model="renameDialogVisible"
      title="重命名工作流"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="工作流名称">
          <el-input
            v-model="renameName"
            placeholder="请输入工作流名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Plus, User } from '@element-plus/icons-vue'
import {
  deleteWorkflow,
  forkWorkflow,
  getPublicWorkflows,
  listWorkflows,
  renameWorkflow,
  updateWorkflowVisibility,
} from '@/api/workflow'
import WorkflowCard from './components/WorkflowCard.vue'
import WorkflowCardSkeleton from './components/WorkflowCardSkeleton.vue'
import { workflowCanvasLocation, workflowNewLocation } from './utils/route'
import { groupMyWorkflows, visibilityTarget } from './utils/workflow-list'

/** 首屏骨架：lg 档一行 4 张 */
const SKELETON_COUNT = 4

/** 名称长度上限（与 workflow 表的 varchar(100) 对齐） */
const WORKFLOW_NAME_MAX = 100

const router = useRouter()

const myWorkflows = ref([])
const publicWorkflows = ref([])
const myLoading = ref(true)
const publicLoading = ref(true)
// 单请求进行中的卡片 id：同一时刻只允许一个可见性切换 / 删除 / fork
const visibilityBusyId = ref(null)
const deletingId = ref(null)
const forkingId = ref(null)

// 重命名弹窗：只改名称，不触碰画布定义
const renameDialogVisible = ref(false)
const renameTarget = ref(null)
const renameName = ref('')
const renaming = ref(false)

const grouped = computed(() => groupMyWorkflows(myWorkflows.value))

async function loadMine() {
  myLoading.value = true
  try {
    myWorkflows.value = await listWorkflows()
  } catch (error) {
    ElMessage.error(error?.message || '加载我的工作流失败')
  } finally {
    myLoading.value = false
  }
}

/** 公有区加载失败不拖垮页面主体，静默留空（与知识库页一致） */
async function loadPublic() {
  publicLoading.value = true
  try {
    publicWorkflows.value = await getPublicWorkflows()
  } catch {
    publicWorkflows.value = []
  } finally {
    publicLoading.value = false
  }
}

function openWorkflow(item) {
  router.push(workflowCanvasLocation(item.id))
}

function createWorkflow() {
  router.push(workflowNewLocation())
}

/** 可见性切换：目标值由纯函数计算，成功后只改本地列表，不重拉整页 */
async function toggleVisibility(item) {
  if (visibilityBusyId.value != null || deletingId.value != null) return
  const target = visibilityTarget(item.visibility)
  visibilityBusyId.value = item.id
  try {
    await updateWorkflowVisibility(item.id, target)
    myWorkflows.value = myWorkflows.value.map((workflow) =>
      workflow.id === item.id ? { ...workflow, visibility: target } : workflow,
    )
    ElMessage.success(target === 1 ? '已设为公有，他人可在「公有库」浏览' : '已设为私有')
  } catch (error) {
    ElMessage.error(error?.message || '可见性切换失败')
  } finally {
    visibilityBusyId.value = null
  }
}

/** 打开重命名弹窗（原名称回填）；同一时刻只允许一个卡片操作 */
function openRename(item) {
  if (visibilityBusyId.value != null || deletingId.value != null) return
  renameTarget.value = item
  renameName.value = item.name || ''
  renameDialogVisible.value = true
}

/** 提交重命名：名称未变化不发请求 */
async function submitRename() {
  const target = renameTarget.value
  if (!target || renaming.value) return
  const name = renameName.value.trim()
  if (!name) {
    ElMessage.warning('请填写工作流名称')
    return
  }
  if (name.length > WORKFLOW_NAME_MAX) {
    ElMessage.warning(`工作流名称不能超过 ${WORKFLOW_NAME_MAX} 个字符`)
    return
  }
  if (name === target.name) {
    renameDialogVisible.value = false
    return
  }
  renaming.value = true
  try {
    await renameWorkflow(target.id, name)
    myWorkflows.value = myWorkflows.value.map((workflow) =>
      workflow.id === target.id ? { ...workflow, name } : workflow,
    )
    ElMessage.success('工作流已重命名')
    renameDialogVisible.value = false
    renameTarget.value = null
  } catch (error) {
    ElMessage.error(error?.message || '重命名失败')
  } finally {
    renaming.value = false
  }
}

/** 软删除：会话 / 执行记录保留；确认文案把这一点讲清楚 */
function removeWorkflow(item) {
  if (deletingId.value != null || visibilityBusyId.value != null) return
  ElMessageBox.confirm(
    `删除后不可恢复；已有会话与执行记录会保留，但不能再用它运行。确定删除「${item.name}」吗？`,
    '删除工作流',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )
    .then(async () => {
      deletingId.value = item.id
      try {
        await deleteWorkflow(item.id)
        ElMessage.success('工作流已删除')
        await loadMine()
      } catch (error) {
        ElMessage.error(error?.message || '删除工作流失败')
      } finally {
        deletingId.value = null
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

/** 公有卡 fork：留在列表并刷新，副本出现在「我的流程」顶部 */
async function forkPublic(item) {
  if (forkingId.value != null) return
  forkingId.value = item.id
  try {
    await forkWorkflow(item.id)
    ElMessage.success(`已把「${item.name}」添加为自己的工作流`)
    await loadMine()
  } catch (error) {
    ElMessage.error(error?.message || '添加为自己的工作流失败')
  } finally {
    forkingId.value = null
  }
}

onMounted(() => {
  loadMine()
  loadPublic()
})
</script>

<style scoped>
.workflow-list-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.wf-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.25rem;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
}

.wf-hero__title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.wf-hero__desc {
  margin: 0.3rem 0 0;
  font-size: 0.82rem;
  color: var(--tb-text-tertiary);
}

.btn-icon {
  margin-right: 4px;
}

.wf-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.9rem 1rem 0.35rem;
  border-radius: var(--tb-radius-lg);
  background: var(--tb-bg-surface);
  box-shadow: var(--tb-shadow-1);
}

.wf-section__header {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

.wf-section__title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.wf-section__title h2 {
  margin: 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.wf-section__desc {
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
}

.wf-section__body {
  min-height: 150px;
}

.wf-subgroup + .wf-subgroup {
  margin-top: 0.35rem;
}

.wf-subgroup__title {
  margin: 0.25rem 0 0.1rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--tb-text-secondary);
  letter-spacing: 0.02em;
}

.wf-col {
  margin-bottom: 1rem;
}

.wf-public-empty {
  margin: 0.5rem 0 0.75rem;
  text-align: center;
  font-size: 0.8rem;
  color: var(--tb-text-tertiary);
}

/* 读屏加载提示：视觉隐藏，不参与布局 */
.wf-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  white-space: nowrap;
  border: 0;
  clip-path: inset(50%);
}

/* 手机上骨架只留两张，避免首屏被占位卡撑得过长 */
@media (max-width: 767px) {
  .wf-col--skeleton:nth-child(n + 3) {
    display: none;
  }
  .wf-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
