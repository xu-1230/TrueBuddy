<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import QueryBar from '@/views/admin/components/QueryBar.vue'
import AdminTable from '@/views/admin/components/AdminTable.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import UserFormDialog from './components/UserFormDialog.vue'
import { usePagedTable } from '@/views/admin/hooks/usePagedTable'
import { formatDateTime } from '@/views/admin/utils/format'
import { ROLE_META, ROLE_OPTIONS, USER_STATUS_META, USER_STATUS_OPTIONS } from '@/views/admin/constants'
import { createUser, deleteUser, listUsers, updateUser } from '@/api/admin/user'

const filters = reactive({ keyword: '', status: '', role: '' })

const { rows, total, page, size, loading, errorMessage, load, search } = usePagedTable({
  fetcher: listUsers,
  filters: () => ({
    keyword: filters.keyword.trim(),
    status: filters.status,
    role: filters.role,
  }),
})

const dialogVisible = ref(false)
const editingUser = ref(null)
const saving = ref(false)

/** 查看详情弹窗：只读展示整条用户记录（含表格中精简掉的角色与时间字段） */
const viewVisible = ref(false)
const viewingUser = ref(null)

function openView(row) {
  viewingUser.value = row
  viewVisible.value = true
}

function openCreate() {
  editingUser.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingUser.value = { ...row }
  dialogVisible.value = true
}

function resetFilters() {
  filters.keyword = ''
  filters.status = ''
  filters.role = ''
  search()
}

function roleMeta(code) {
  return ROLE_META[code] || { label: code, tone: 'info' }
}

function statusMeta(status) {
  return USER_STATUS_META[status] || { label: '未知', tone: 'info' }
}

async function handleSubmit(payload) {
  saving.value = true
  try {
    if (editingUser.value) {
      await updateUser({ ...payload, id: editingUser.value.id })
    } else {
      await createUser(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.nickname || row.username}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="用户列表"
      description="账号、昵称、邮箱与激活状态；查看、编辑、删除都在本页完成。管理员账号的角色变更请走角色权限分配。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
      </template>
    </PageHeader>

    <QueryBar :loading="loading" @search="search" @reset="resetFilters">
      <el-form-item label="关键词" class="query-item">
        <el-input
          v-model="filters.keyword"
          placeholder="账号 / 昵称 / 邮箱"
          clearable
          style="width: 220px"
        />
      </el-form-item>

      <el-form-item label="状态" class="query-item">
        <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
          <el-option
            v-for="item in USER_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="角色" class="query-item">
        <el-select v-model="filters.role" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="item in ROLE_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </QueryBar>

    <AdminTable
      v-model:page="page"
      v-model:size="size"
      :rows="rows"
      :total="total"
      :loading="loading"
      :error-message="errorMessage"
      @change="load"
    >
      <el-table-column prop="username" label="账号" min-width="130" show-overflow-tooltip />

      <el-table-column prop="nickname" label="用户昵称" min-width="120" show-overflow-tooltip />

      <el-table-column prop="id" label="ID" width="70" />

      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />

      <el-table-column label="激活状态" width="90">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="登录IP" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.loginIp || '-' }}</template>
      </el-table-column>

      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <div class="admin-inline">
            <el-button link @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </AdminTable>

    <UserFormDialog
      v-model="dialogVisible"
      :user="editingUser"
      :submitting="saving"
      @submit="handleSubmit"
    />

    <el-dialog v-model="viewVisible" title="用户详情" width="520px">
      <el-descriptions v-if="viewingUser" :column="1" border>
        <el-descriptions-item label="ID">{{ viewingUser.id }}</el-descriptions-item>
        <el-descriptions-item label="账号">{{ viewingUser.username }}</el-descriptions-item>
        <el-descriptions-item label="用户昵称">{{ viewingUser.nickname }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ viewingUser.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="激活状态">
          <StatusTag
            :label="statusMeta(viewingUser.status).label"
            :tone="statusMeta(viewingUser.status).tone"
          />
        </el-descriptions-item>
        <el-descriptions-item label="角色">
          <div class="admin-inline">
            <StatusTag
              v-for="code in viewingUser.roles"
              :key="code"
              :label="roleMeta(code).label"
              :tone="roleMeta(code).tone"
            />
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="登录IP">{{ viewingUser.loginIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近登录">
          {{ formatDateTime(viewingUser.loginDate) || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="admin-dialog__footer">
          <el-button @click="viewVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}
</style>
