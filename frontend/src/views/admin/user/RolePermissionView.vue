<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import RoleFormDialog from './components/RoleFormDialog.vue'
import PermissionDialog from './components/PermissionDialog.vue'
import { USER_STATUS_META } from '@/views/admin/constants'
import { isApiUnavailable } from '@/views/admin/utils/request'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  getRoleMenuIds,
  listRoles,
  updateRole,
} from '@/api/admin/role'
import { getMenuTree } from '@/api/admin/menu'
import { mockMenuTree, mockRoleMenuIds, mockRoles } from '@/views/admin/mock/data'

const roles = ref([])
const loading = ref(false)
const usingMock = ref(false)

const router = useRouter()

const dialogVisible = ref(false)
const editingRole = ref(null)
const saving = ref(false)

const permissionVisible = ref(false)
const permissionRole = ref(null)
const permissionTree = ref([])
const checkedMenuIds = ref([])
const permissionSaving = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listRoles()
    const list = Array.isArray(data) ? data : data?.list
    if (!Array.isArray(list)) throw new Error('角色列表格式不符合预期')
    roles.value = list
    usingMock.value = false
  } catch {
    roles.value = mockRoles.map((item) => ({ ...item }))
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

function statusMeta(status) {
  return USER_STATUS_META[status] || { label: '未知', tone: 'info' }
}

function openCreate() {
  editingRole.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingRole.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit(payload) {
  saving.value = true
  try {
    if (editingRole.value) {
      await updateRole({ ...payload, id: editingRole.value.id })
    } else {
      await createRole(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '保存失败')
      return
    }

    if (editingRole.value) {
      const index = mockRoles.findIndex((item) => item.id === editingRole.value.id)
      if (index >= 0) mockRoles.splice(index, 1, { ...mockRoles[index], ...payload })
    } else {
      const nextId = mockRoles.reduce((max, item) => Math.max(max, item.id), 0) + 1
      mockRoles.push({ id: nextId, memberCount: 0, ...payload })
    }

    usingMock.value = true
    dialogVisible.value = false
    ElMessage.success('演示模式：已保存到前端数据')
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  if (row.code === 'admin') {
    ElMessage.warning('管理员角色不可删除')
    return
  }
  if (row.memberCount > 0) {
    ElMessage.warning('该角色下还有用户，请先调整用户角色')
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '删除失败')
      return
    }
    const index = mockRoles.findIndex((item) => item.id === row.id)
    if (index >= 0) mockRoles.splice(index, 1)
    usingMock.value = true
    ElMessage.success('演示模式：已删除')
    await load()
  }
}

async function openPermission(row) {
  permissionRole.value = row

  const [treeResult, idsResult] = await Promise.allSettled([getMenuTree(), getRoleMenuIds(row.id)])

  permissionTree.value =
    treeResult.status === 'fulfilled' && Array.isArray(treeResult.value) ? treeResult.value : mockMenuTree
  checkedMenuIds.value =
    idsResult.status === 'fulfilled' && Array.isArray(idsResult.value)
      ? idsResult.value
      : mockRoleMenuIds[row.id] || []

  permissionVisible.value = true
}

async function handlePermissionSubmit(menuIds) {
  permissionSaving.value = true
  try {
    await assignRoleMenus(permissionRole.value.id, menuIds)
    ElMessage.success('权限已保存')
    permissionVisible.value = false
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '权限保存失败')
      return
    }
    mockRoleMenuIds[permissionRole.value.id] = menuIds
    ElMessage.success('演示模式：权限已保存')
    permissionVisible.value = false
  } finally {
    permissionSaving.value = false
  }
}

load()

function openMenuTree() {
  router.push('/admin/user/menu')
}
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="角色权限分配"
      description="角色编码是权限判断的唯一口径；在「权限」里勾选该角色可访问的菜单与按钮。成员数大于 0 的角色不允许删除。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button @click="openMenuTree">维护菜单结构</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增角色</el-button>
      </template>
    </PageHeader>

    <section class="admin-panel admin-panel--flush">
      <el-table v-loading="loading" :data="roles" row-key="id">
        <el-table-column prop="name" label="角色名称" min-width="140">
          <template #default="{ row }">
            <span class="admin-strong">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="角色编码" min-width="140">
          <template #default="{ row }">
            <span class="admin-code">{{ row.code }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="memberCount" label="成员数" width="100" align="center" />

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :label="statusMeta(row.status).label" :tone="statusMeta(row.status).tone" />
          </template>
        </el-table-column>

        <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="admin-inline">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link @click="openPermission(row)">权限</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无角色" :image-size="64" />
        </template>
      </el-table>
    </section>

    <RoleFormDialog v-model="dialogVisible" :role="editingRole" :submitting="saving" @submit="handleSubmit" />

    <PermissionDialog
      v-model="permissionVisible"
      :role="permissionRole"
      :tree-data="permissionTree"
      :checked-ids="checkedMenuIds"
      :submitting="permissionSaving"
      @submit="handlePermissionSubmit"
    />
  </div>
</template>
