<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import MenuFormDialog from './components/MenuFormDialog.vue'
import { MENU_TYPE, MENU_TYPE_META } from '@/views/admin/constants'
import { isApiUnavailable } from '@/views/admin/utils/request'
import {
  countMenuNodes,
  flattenMenuTree,
  insertMenuNode,
  removeMenuNode,
  updateMenuNode,
} from '@/views/admin/utils/menu-tree'
import { createMenu, deleteMenu, getMenuTree, updateMenu } from '@/api/admin/menu'
import { mockMenuTree } from '@/views/admin/mock/data'

const tree = ref([])
const loading = ref(false)
const usingMock = ref(false)

const router = useRouter()

const dialogVisible = ref(false)
const editingMenu = ref(null)
const parentMenu = ref(null)
const saving = ref(false)

const totalCount = computed(() => countMenuNodes(tree.value))

function cloneTree(nodes) {
  return JSON.parse(JSON.stringify(nodes))
}

function nextMenuId() {
  const maxId = flattenMenuTree(tree.value).reduce((max, node) => Math.max(max, Number(node.id) || 0), 0)
  return maxId + 1
}

function typeMeta(type) {
  return MENU_TYPE_META[type] || { label: '未知', tone: 'info' }
}

async function load() {
  loading.value = true
  try {
    const data = await getMenuTree()
    if (!Array.isArray(data)) throw new Error('菜单树格式不符合预期')
    tree.value = data
    usingMock.value = false
  } catch {
    tree.value = cloneTree(mockMenuTree)
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

function openCreateRoot() {
  editingMenu.value = null
  parentMenu.value = null
  dialogVisible.value = true
}

function openCreateChild(row) {
  editingMenu.value = null
  parentMenu.value = { ...row }
  dialogVisible.value = true
}

function openEdit(row) {
  editingMenu.value = { ...row }
  parentMenu.value = null
  dialogVisible.value = true
}

async function handleSubmit(payload) {
  saving.value = true
  try {
    if (editingMenu.value) {
      await updateMenu({ ...payload, id: editingMenu.value.id })
    } else {
      await createMenu(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '保存失败')
      return
    }

    const node = {
      id: editingMenu.value?.id ?? nextMenuId(),
      name: payload.name,
      type: payload.type,
      path: payload.path,
      component: editingMenu.value?.component || '',
      permission: payload.permission,
      icon: payload.icon,
      sort: payload.sort,
      visible: payload.visible,
    }

    tree.value = editingMenu.value
      ? updateMenuNode(tree.value, editingMenu.value.id, node)
      : insertMenuNode(tree.value, payload.parentId, node)

    usingMock.value = true
    dialogVisible.value = false
    ElMessage.success('演示模式：已保存到前端数据')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  const childCount = Array.isArray(row.children) ? row.children.length : 0
  const tip =
    childCount > 0
      ? `「${row.name}」下还有 ${childCount} 个子项，删除会连同子项一起移除，确定继续吗？`
      : `确定删除「${row.name}」吗？`

  try {
    await ElMessageBox.confirm(tip, '操作确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '删除失败')
      return
    }
    tree.value = removeMenuNode(tree.value, row.id)
    usingMock.value = true
    ElMessage.success('演示模式：已删除')
  }
}

load()

function backToRoles() {
  router.push('/admin/user/role')
}
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="菜单结构维护"
      description="system_menu 的可视化维护：目录 / 菜单决定侧栏，按钮决定页面内操作权限。它是角色权限分配的附属页，不单独占功能模块。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button @click="backToRoles">返回角色权限</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateRoot">新增根菜单</el-button>
      </template>
    </PageHeader>

    <section class="admin-panel admin-panel--flush">
      <div class="menu-tree__summary">
        <span class="admin-muted">共 {{ totalCount }} 个节点（含按钮）</span>
      </div>

      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" label="名称" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'admin-strong': row.type !== MENU_TYPE.BUTTON }">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <StatusTag :label="typeMeta(row.type).label" :tone="typeMeta(row.type).tone" />
          </template>
        </el-table-column>

        <el-table-column prop="path" label="路由地址" min-width="200">
          <template #default="{ row }">
            <span v-if="row.path" class="admin-code">{{ row.path }}</span>
            <span v-else class="admin-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="permission" label="权限标识" min-width="190">
          <template #default="{ row }">
            <span v-if="row.permission" class="admin-code">{{ row.permission }}</span>
            <span v-else class="admin-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="sort" label="排序" width="70" align="center" />

        <el-table-column label="显示" width="80" align="center">
          <template #default="{ row }">
            <StatusTag
              :label="row.visible ? '显示' : '隐藏'"
              :tone="row.visible ? 'success' : 'info'"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="admin-inline">
              <el-button
                link
                type="primary"
                :disabled="row.type === MENU_TYPE.BUTTON"
                @click="openCreateChild(row)"
              >
                新增子项
              </el-button>
              <el-button link @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无菜单" :image-size="64" />
        </template>
      </el-table>
    </section>

    <MenuFormDialog
      v-model="dialogVisible"
      :menu="editingMenu"
      :parent="parentMenu"
      :submitting="saving"
      @submit="handleSubmit"
    />
  </div>
</template>

<style scoped>
.menu-tree__summary {
  padding: var(--tb-size-3) var(--tb-size-4);
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}
</style>
