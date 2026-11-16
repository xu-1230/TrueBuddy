<template>
  <div class="space-grid-view">
    <div class="kb-toolbar">
      <div class="kb-toolbar__title">
        <el-icon><Collection /></el-icon>
        <span>知识库空间</span>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon class="btn-icon"><Plus /></el-icon>
        创建空间
      </el-button>
    </div>

    <div v-loading="loading" class="space-grid-view__body">
      <el-row :gutter="16">
        <el-col
          v-for="space in spaces"
          :key="space.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
        >
          <SpaceCard
            :space="space"
            @click="$emit('open-space', space)"
            @edit="openEdit(space)"
            @delete="onDelete(space)"
            @toggle-visibility="(target) => $emit('toggle-visibility', space, target)"
          />
        </el-col>
      </el-row>
      <el-empty
        v-if="!loading && !spaces.length"
        description="暂无知识库空间，点击创建"
      />
    </div>

    <!-- 公有库浏览区：他人的公有空间，只读（fork 入口在共享链路打通） -->
    <div class="kb-public">
      <div class="kb-public__header">
        <div class="kb-public__title">
          <el-icon><User /></el-icon>
          <span>公有库</span>
        </div>
        <span class="kb-public__desc">他人公开的空间，只读可浏览</span>
      </div>
      <div v-loading="publicLoading" class="kb-public__body">
        <el-row :gutter="16">
          <el-col
            v-for="space in publicSpaces"
            :key="space.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <SpaceCard
              :space="space"
              readonly
              :forking="space.id === forkingId"
              @click="$emit('open-public-space', space)"
              @fork="$emit('fork-space', space)"
            />
          </el-col>
        </el-row>
        <p v-if="!publicLoading && !publicSpaces.length" class="kb-public__empty">
          暂无他人公开的知识库空间
        </p>
      </div>
    </div>

    <!-- 创建 / 编辑空间对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'edit' ? '编辑知识库空间' : '创建知识库空间'"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="空间名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入空间名称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="空间描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="选填，描述空间用途"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">
          {{ dialogMode === 'edit' ? '保存修改' : '确定创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'
import { Collection, Plus, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SpaceCard from './SpaceCard.vue'

defineProps({
  spaces: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  publicSpaces: { type: Array, default: () => [] },
  publicLoading: { type: Boolean, default: false },
  /** 正在执行 fork 的空间 id（卡片按钮转 loading） */
  forkingId: { type: Number, default: null },
})

const emit = defineEmits([
  'open-space',
  'open-public-space',
  'create-space',
  'update-space',
  'delete-space',
  'toggle-visibility',
  'fork-space',
])

const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingSpace = ref(null)
const formRef = ref(null)
const form = reactive({
  name: '',
  description: '',
})

const rules = {
  name: [{ required: true, message: '请输入空间名称', trigger: 'blur' }],
}

function openCreate() {
  dialogMode.value = 'create'
  editingSpace.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(space) {
  dialogMode.value = 'edit'
  editingSpace.value = space
  form.name = space.name
  form.description = space.description || ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (!valid) return
    const payload = { name: form.name, description: form.description }
    if (dialogMode.value === 'edit' && editingSpace.value) {
      emit('update-space', { id: editingSpace.value.id, ...payload })
    } else {
      emit('create-space', payload)
    }
    dialogVisible.value = false
    resetForm()
  })
}

function resetForm() {
  form.name = ''
  form.description = ''
  formRef.value?.clearValidate()
}

function onDelete(space) {
  ElMessageBox.confirm(
    `确定删除空间「${space.name}」吗？空间下的文档将一并删除。`,
    '删除确认',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )
    .then(() => {
      emit('delete-space', space)
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}
</script>

<style scoped>
.space-grid-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.kb-toolbar__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.btn-icon {
  margin-right: 4px;
}

.space-grid-view__body {
  min-height: 200px;
}

/* ========== 公有库浏览区 ========== */
.kb-public {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: var(--tb-bg-surface);
  border-radius: var(--tb-radius-lg);
  box-shadow: var(--tb-shadow-1);
}

.kb-public__header {
  display: flex;
  align-items: baseline;
  gap: 0.75rem;
}

.kb-public__title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 1rem;
  font-weight: 700;
  color: var(--tb-text-primary);
}

.kb-public__desc {
  font-size: 0.78rem;
  color: var(--tb-text-tertiary);
}

.kb-public__body {
  min-height: 64px;
}

.kb-public__empty {
  margin: 0.5rem 0 0.25rem;
  font-size: 0.8rem;
  color: var(--tb-text-tertiary);
  text-align: center;
}

.el-col {
  margin-bottom: 1rem;
}
</style>
