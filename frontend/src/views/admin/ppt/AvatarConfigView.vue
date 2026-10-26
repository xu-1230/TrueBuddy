<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/views/admin/components/PageHeader.vue'
import StatusTag from '@/views/admin/components/StatusTag.vue'
import { isApiUnavailable } from '@/views/admin/utils/request'
import { formatDateTime } from '@/views/admin/utils/format'
import { getAvatarConfig, saveAvatarConfig } from '@/api/admin/ppt'
import {
  AVATAR_MODEL_OPTIONS,
  AVATAR_SCENE_OPTIONS,
  AVATAR_VOICE_OPTIONS,
  mockAvatarConfig,
} from '@/views/admin/mock/ppt'

const loading = ref(false)
const saving = ref(false)
const usingMock = ref(false)
const updatedAt = ref(mockAvatarConfig.updatedAt)

const form = reactive({ ...mockAvatarConfig })

const voiceLabel = computed(
  () => AVATAR_VOICE_OPTIONS.find((item) => item.value === form.voice)?.label || form.voice,
)
const sceneLabel = computed(
  () => AVATAR_SCENE_OPTIONS.find((item) => item.value === form.scene)?.label || form.scene,
)
const modelLabel = computed(
  () => AVATAR_MODEL_OPTIONS.find((item) => item.value === form.model)?.label || form.model,
)

async function load() {
  loading.value = true
  try {
    const data = await getAvatarConfig()
    if (!data || typeof data !== 'object') throw new Error('数字人配置格式不符合预期')
    Object.assign(form, data)
    updatedAt.value = data.updatedAt || updatedAt.value
    usingMock.value = false
  } catch {
    Object.assign(form, mockAvatarConfig)
    updatedAt.value = mockAvatarConfig.updatedAt
    usingMock.value = true
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await saveAvatarConfig({ ...form })
    ElMessage.success('数字人配置已保存')
    updatedAt.value = formatDateTime(new Date())
  } catch (error) {
    if (!isApiUnavailable(error)) {
      ElMessage.error(error?.message || '保存失败')
      return
    }
    usingMock.value = true
    updatedAt.value = formatDateTime(new Date())
    ElMessage.success('演示模式：配置已保存在前端，刷新后恢复')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <PageHeader
      title="数字人形象配置"
      description="数字人的模型、音色与语速。Live2D 运行时只在放映页加载，管理端仅维护配置并预览参数。"
      :demo="usingMock"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">重置</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
      </template>
    </PageHeader>

    <section class="admin-grid admin-grid--2">
      <article v-loading="loading" class="admin-panel">
        <header class="avatar-head">
          <h2 class="admin-panel__title">形象参数</h2>
        </header>

        <el-form :model="form" label-position="top" @submit.prevent>
          <el-form-item label="Live2D 模型">
            <el-select v-model="form.model" style="width: 100%">
              <el-option
                v-for="item in AVATAR_MODEL_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="播报音色">
            <el-select v-model="form.voice" style="width: 100%">
              <el-option
                v-for="item in AVATAR_VOICE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="使用场景">
            <el-select v-model="form.scene" style="width: 100%">
              <el-option
                v-for="item in AVATAR_SCENE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="语速">
            <el-slider v-model="form.speed" :min="0.6" :max="1.6" :step="0.1" show-input />
          </el-form-item>

          <el-form-item label="启用数字人">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-form>
      </article>

      <article class="admin-panel">
        <header class="avatar-head">
          <h2 class="admin-panel__title">配置预览</h2>
          <StatusTag
            :label="form.enabled ? '已启用' : '已停用'"
            :tone="form.enabled ? 'success' : 'info'"
          />
        </header>

        <div class="avatar-preview">
          <div class="avatar-preview__stage">
            <span class="avatar-preview__halo" aria-hidden="true" />
            <span class="avatar-preview__face">{{ String(form.model).slice(0, 1) }}</span>
          </div>
          <p class="avatar-preview__model">{{ modelLabel }}</p>
          <p class="avatar-preview__meta">{{ voiceLabel }} · {{ sceneLabel }}</p>
          <p class="avatar-preview__speed">语速 {{ Number(form.speed).toFixed(1) }}×</p>
        </div>

        <p class="admin-muted avatar-note">
          这里是配置结果的静态预览。放映页会在真正开讲时按该配置加载 Live2D 模型与音色；
          「语速」换算成每段播报的目标时长，具体换算只发生在后端一处。
        </p>

        <p v-if="form.model === 'DeepSeekWhale'" class="admin-muted avatar-note">
          鲸鱼娘为社区二创形象，并非 DeepSeek 官方模型。素材许可 CC BY-NC-SA 4.0，
          仅限非商业用途，须署名 上善无形 / ZipZipPipe / 氵六青。
        </p>

        <p class="avatar-updated">最后更新：{{ formatDateTime(updatedAt) }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.avatar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--tb-size-3);
  margin-bottom: var(--tb-size-4);
}

.avatar-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--tb-size-1);
  padding: var(--tb-size-5) var(--tb-size-4);
  border: var(--tb-border-width) solid var(--tb-border);
  border-radius: var(--tb-radius-lg);
  background:
    radial-gradient(120% 90% at 50% 0%, var(--tb-primary-soft), transparent 62%),
    var(--tb-bg-raised);
}

.avatar-preview__stage {
  position: relative;
  display: grid;
  place-items: center;
  width: 108px;
  height: 108px;
  margin-bottom: var(--tb-size-3);
}

.avatar-preview__halo {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(
    from 140deg,
    color-mix(in oklab, var(--tb-primary) 42%, transparent),
    transparent 62%
  );
  filter: blur(2px);
}

.avatar-preview__face {
  position: relative;
  display: grid;
  place-items: center;
  width: 84px;
  height: 84px;
  border-radius: 50%;
  background: var(--tb-bg-surface);
  border: 1px solid var(--tb-border);
  color: var(--tb-primary-text);
  font-family: var(--tb-font-display);
  font-size: var(--tb-text-2xl);
  font-weight: 700;
  box-shadow: var(--tb-shadow-2);
}

.avatar-preview__model {
  margin: 0;
  color: var(--tb-text-primary);
  font-size: var(--tb-text-lg);
  font-weight: 600;
}

.avatar-preview__meta,
.avatar-preview__speed {
  margin: 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}

.avatar-note {
  margin-top: var(--tb-size-4);
}

.avatar-updated {
  margin: var(--tb-size-3) 0 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-2xs);
}
</style>
