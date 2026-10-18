<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import { isApiUnavailable } from '@/views/admin/utils/request'
import { getSystemSettings, saveSystemSettings } from '@/api/admin/system'
import { mockSystemSettings } from '@/views/admin/mock/data'

const formRef = ref()
const loading = ref(false)
const saving = ref(false)
const usingMock = ref(false)

const settings = reactive({ ...mockSystemSettings })

const rules = {
  siteName: [{ required: true, message: '请输入站点名称', trigger: 'blur' }],
  maxUploadMb: [{ required: true, message: '请填写上传上限', trigger: 'change' }],
  meetingCapacity: [{ required: true, message: '请填写会议人数上限', trigger: 'change' }],
}

const modelOptions = [
  { value: 'qwen-plus', label: 'qwen-plus（默认）' },
  { value: 'qwen-max', label: 'qwen-max（高质量）' },
  { value: 'gpt-4o-mini', label: 'gpt-4o-mini（低成本）' },
]

async function load() {
  loading.value = true
  try {
    const data = await getSystemSettings()
    if (!data || typeof data !== 'object') throw new Error('系统配置格式不符合预期')
    Object.assign(settings, data)
    usingMock.value = false
  } catch {
    Object.assign(settings, mockSystemSettings)
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await saveSystemSettings({ ...settings })
    ElMessage.success('配置已保存')
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '保存失败')
      return
    }
    usingMock.value = true
    ElMessage.success('演示模式：配置已保存在前端，刷新后恢复')
  } finally {
    saving.value = false
  }
}

load()
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="系统配置"
      description="站点信息、上传限制与默认模型。这里的改动直接影响用户端，保存前请确认影响范围。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">重置</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
      </template>
    </PageHeader>

    <el-form
      ref="formRef"
      v-loading="loading"
      :model="settings"
      :rules="rules"
      label-position="top"
      class="settings-form"
      @submit.prevent
    >
      <section class="admin-panel">
        <h2 class="admin-panel__title">站点信息</h2>
        <div class="admin-form-grid settings-grid">
          <el-form-item label="站点名称" prop="siteName">
            <el-input v-model="settings.siteName" placeholder="显示在浏览器标题与登录页" />
          </el-form-item>

          <el-form-item label="站点描述" prop="siteDescription">
            <el-input v-model="settings.siteDescription" placeholder="一句话介绍" />
          </el-form-item>
        </div>
      </section>

      <section class="admin-panel">
        <h2 class="admin-panel__title">上传与容量</h2>
        <div class="admin-form-grid settings-grid">
          <el-form-item label="单文件上传上限（MB）" prop="maxUploadMb">
            <el-input-number v-model="settings.maxUploadMb" :min="1" :max="2048" style="width: 100%" />
          </el-form-item>

          <el-form-item label="单场会议人数上限" prop="meetingCapacity">
            <el-input-number v-model="settings.meetingCapacity" :min="2" :max="500" style="width: 100%" />
          </el-form-item>
        </div>
      </section>

      <section class="admin-panel">
        <h2 class="admin-panel__title">注册与模型</h2>

        <div class="settings-row">
          <div class="settings-row__main">
            <p class="settings-row__label">开放注册</p>
            <p class="settings-row__hint">关闭后用户端注册入口不可用，仅管理员可创建账号</p>
          </div>
          <el-switch v-model="settings.allowRegister" />
        </div>

        <div class="settings-row">
          <div class="settings-row__main">
            <p class="settings-row__label">默认大模型</p>
            <p class="settings-row__hint">文档模板、讲稿优化与批改工作流未单独指定模型时使用</p>
          </div>
          <el-select v-model="settings.defaultModel" style="width: 220px">
            <el-option
              v-for="item in modelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <div class="settings-row">
          <div class="settings-row__main">
            <p class="settings-row__label">维护模式</p>
            <p class="settings-row__hint">开启后用户端显示维护提示，管理端仍可访问</p>
          </div>
          <el-switch v-model="settings.maintenanceMode" />
        </div>
      </section>
    </el-form>
  </div>
</template>

<style scoped>
.settings-form {
  display: flex;
  flex-direction: column;
  gap: var(--tb-size-4);
}

.settings-form .admin-panel__title {
  margin-bottom: var(--tb-size-4);
}

.settings-grid {
  margin-bottom: calc(var(--tb-size-4) * -1);
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-4);
  padding: var(--tb-size-3) 0;
  border-bottom: var(--tb-border-width) solid var(--tb-border);
}

.settings-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.settings-row__main {
  min-width: 0;
}

.settings-row__label {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-sm);
  font-weight: 600;
}

.settings-row__hint {
  margin: var(--tb-size-1) 0 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: var(--tb-leading-snug);
}

@media (max-width: 768px) {
  .settings-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .settings-row :deep(.el-select) {
    width: 100% !important;
  }
}
</style>
