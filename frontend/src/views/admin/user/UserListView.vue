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
import { isApiUnavailable } from '@/views/admin/utils/request'
import { formatDateTime } from '@/views/admin/utils/format'
import { ROLE_META, ROLE_OPTIONS, USER_STATUS_META, USER_STATUS_OPTIONS } from '@/views/admin/constants'
import { createUser, listUsers, resetUserPassword, updateUser, updateUserStatus } from '@/api/admin/user'
import { mockUsers, queryUsers } from '@/views/admin/mock/data'

const filters = reactive({ keyword: '', status: '', role: '' })

const { rows, total, page, size, loading, errorMessage, usingMock, load, search } = usePagedTable({
  fetcher: listUsers,
  filters: () => ({
    keyword: filters.keyword.trim(),
    status: filters.status,
    role: filters.role,
  }),
  // mock 里角色字段叫 roles（数组），接口参数叫 role，这里做一次字段映射
  fallback: ({ role, ...params }) => queryUsers({ ...params, roles: role }),
})

const dialogVisible = ref(false)
const editingUser = ref(null)
const saving = ref(false)

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

/** 演示模式下的本地写入：只改内存，刷新页面即恢复 */
function saveToMock(payload) {
  const next = { ...payload }
  delete next.password
  const rest = next

  if (editingUser.value) {
    const index = mockUsers.findIndex((item) => item.id === editingUser.value.id)
    if (index >= 0) mockUsers.splice(index, 1, { ...mockUsers[index], ...rest })
    return
  }

  const nextId = mockUsers.reduce((max, item) => Math.max(max, item.id), 0) + 1
  mockUsers.unshift({
    id: nextId,
    ...rest,
    createTime: formatDateTime(new Date()),
    lastLogin: '',
  })
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
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '保存失败')
      return
    }
    saveToMock(payload)
    usingMock.value = true
    dialogVisible.value = false
    ElMessage.success('演示模式：已保存到前端数据')
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const action = nextStatus === 1 ? '禁用' : '启用'

  try {
    await ElMessageBox.confirm(`确定要${action}用户「${row.nickname}」吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await updateUserStatus(row.id, nextStatus)
    ElMessage.success(`已${action}`)
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || `${action}失败`)
      return
    }
    const index = mockUsers.findIndex((item) => item.id === row.id)
    if (index >= 0) mockUsers.splice(index, 1, { ...mockUsers[index], status: nextStatus })
    usingMock.value = true
    ElMessage.success(`演示模式：已${action}`)
    await load()
  }
}

async function handleResetPassword(row) {
  try {
    await ElMessageBox.confirm(`确定重置「${row.nickname}」的密码吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: '重置',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await resetUserPassword(row.id)
    ElMessage.success('密码已重置')
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '重置失败')
      return
    }
    ElMessage.success('演示模式：密码已重置为 123456')
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="用户列表"
      description="账号、角色与启停状态；新增、编辑、启用与禁用、重置密码都在本页完成。管理员账号的角色变更请走角色权限分配。"
      :demo="usingMock"
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
      :using-mock="usingMock"
      @change="load"
    >
      <el-table-column prop="username" label="账号" min-width="130">
        <template #default="{ row }">
          <div class="user-cell">
            <span class="user-cell__name">{{ row.username }}</span>
            <span class="user-cell__nick">{{ row.nickname }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="角色" min-width="130">
        <template #default="{ row }">
          <div class="admin-inline">
            <StatusTag
              v-for="code in row.roles"
              :key="code"
              :label="roleMeta(code).label"
              :tone="roleMeta(code).tone"
            />
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip />

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="140">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>

      <el-table-column label="最近登录" width="140">
        <template #default="{ row }">{{ formatDateTime(row.lastLogin) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <div class="admin-inline">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              :type="row.status === 0 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 0 ? '禁用' : '启用' }}
            </el-button>
            <el-button link @click="handleResetPassword(row)">重置密码</el-button>
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
  </div>
</template>

<style scoped>
.query-item {
  margin-bottom: 0;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-cell__name {
  color: var(--tb-text-primary);
  font-weight: 600;
}

.user-cell__nick {
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}
</style>
