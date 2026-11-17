<script setup>
/**
 * 数字人与声音设置弹窗。
 *
 * 弹窗只负责「选」：形象与音色的草稿在这里维护，点「保存并应用」时交给父组件的
 * saveHandler —— 父组件先加载形象，成功后才写偏好（全有或全无）。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAvatarList, getVoiceList, deleteVoice } from '@/api/ppt'
import { AVATAR_MODELS, DEFAULT_AVATAR_MODEL } from '../../configs/avatar'
import AvatarTab from './AvatarTab.vue'
import VoiceTab from './VoiceTab.vue'
import VoiceCloneDialog from './VoiceCloneDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 右侧主面板当前挂载的形象编码 */
  currentAvatar: { type: String, default: '' },
  /** 当前已保存的音色编码；为空表示默认音色 */
  currentVoice: { type: String, default: '' },
  /** 父组件的保存函数：({ avatarModelCode, voiceCode }) => Promise<void> */
  saveHandler: { type: Function, default: null },
})

const emit = defineEmits(['update:modelValue', 'saved', 'voice-deleted'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const activeTab = ref('avatar')
const avatars = ref([])
const voices = ref([])
const meta = reactive({
  defaultVoiceCode: '',
  cloneEnabled: false,
  uploadEnabled: false,
})

const loadingAvatars = ref(false)
const loadingVoices = ref(false)
const saving = ref(false)
const cloneVisible = ref(false)

/** 草稿：保存前不影响右侧面板 */
const draftAvatar = ref('')
const draftVoice = ref('')

const selectedAvatarName = computed(
  () => avatars.value.find((item) => item.modelCode === draftAvatar.value)?.name || '默认形象',
)
const selectedVoiceName = computed(
  () => voices.value.find((item) => item.voiceCode === draftVoice.value)?.name || '默认音色',
)

const supported = (code) => Boolean(AVATAR_MODELS[code])

const pickDefaultAvatar = () => {
  const supportedCodes = avatars.value.filter((item) => supported(item.modelCode))
  if (supportedCodes.some((item) => item.modelCode === DEFAULT_AVATAR_MODEL)) {
    return DEFAULT_AVATAR_MODEL
  }
  return supportedCodes[0]?.modelCode || avatars.value[0]?.modelCode || ''
}

const loadAvatars = async () => {
  loadingAvatars.value = true
  try {
    const list = await getAvatarList()
    avatars.value = Array.isArray(list) ? list : []
    if (!avatars.value.some((item) => item.modelCode === draftAvatar.value)) {
      draftAvatar.value = pickDefaultAvatar()
    }
  } catch (error) {
    avatars.value = []
    ElMessage.error(error?.message || '数字人形象加载失败')
  } finally {
    loadingAvatars.value = false
  }
}

const applyVoiceMeta = (data) => {
  meta.defaultVoiceCode = data?.defaultVoiceCode || ''
  meta.cloneEnabled = Boolean(data?.cloneEnabled)
  meta.uploadEnabled = Boolean(data?.uploadEnabled)
  voices.value = Array.isArray(data?.items) ? data.items : []

  const exists = (code) => voices.value.some((item) => item.voiceCode === code)
  if (!draftVoice.value || !exists(draftVoice.value)) {
    draftVoice.value = exists(meta.defaultVoiceCode) ? meta.defaultVoiceCode : ''
  }
}

const loadVoices = async () => {
  loadingVoices.value = true
  try {
    const data = await getVoiceList()
    applyVoiceMeta(data)
  } catch (error) {
    voices.value = []
    ElMessage.error(error?.message || '音色列表加载失败')
  } finally {
    loadingVoices.value = false
  }
}

/** 每次打开都从当前值重建草稿，并并行拉取两份目录 */
const initialize = async () => {
  activeTab.value = 'avatar'
  cloneVisible.value = false
  draftAvatar.value = props.currentAvatar || ''
  draftVoice.value = props.currentVoice || ''
  await Promise.all([loadAvatars(), loadVoices()])
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) initialize()
    else cloneVisible.value = false
  },
)

const handleDeleteVoice = async (voice) => {
  const confirmed = await ElMessageBox.confirm(
    `删除复刻音色「${voice.name}」？删除后无法恢复，已选它的偏好会回退默认。`,
    '删除复刻音色',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
    .then(() => true)
    .catch(() => false)
  if (!confirmed) return

  try {
    await deleteVoice(voice.id)
    ElMessage.success('已删除复刻音色')
    emit('voice-deleted', voice.voiceCode)
    await loadVoices()
    if (draftVoice.value === voice.voiceCode) {
      draftVoice.value = voices.value.some((item) => item.voiceCode === meta.defaultVoiceCode)
        ? meta.defaultVoiceCode
        : ''
    }
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

const handleCloned = async (created) => {
  await loadVoices()
  if (created?.voiceCode && voices.value.some((item) => item.voiceCode === created.voiceCode)) {
    draftVoice.value = created.voiceCode
  }
}

const save = async () => {
  if (saving.value) return
  if (!props.saveHandler) {
    ElMessage.error('保存入口未就绪，请刷新页面后重试')
    return
  }
  saving.value = true
  try {
    await props.saveHandler({
      avatarModelCode: draftAvatar.value || null,
      voiceCode: draftVoice.value || null,
    })
    ElMessage.success('数字人与声音设置已保存')
    emit('saved')
    visible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="数字人与声音设置"
    width="820px"
    destroy-on-close
    class="avatar-voice-dialog"
  >
    <p class="settings-lead">
      选择要展示的数字人，以及讲稿播报使用的声音。保存后会应用到右侧数字人面板与之后的播报。
    </p>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="数字人" name="avatar">
        <AvatarTab
          :avatars="avatars"
          :selected="draftAvatar"
          :loading="loadingAvatars"
          @select="draftAvatar = $event"
        />
      </el-tab-pane>

      <el-tab-pane label="声音" name="voice">
        <VoiceTab
          :voices="voices"
          :selected="draftVoice"
          :loading="loadingVoices"
          :clone-enabled="meta.cloneEnabled"
          :default-voice-code="meta.defaultVoiceCode"
          @select="draftVoice = $event"
          @clone="cloneVisible = true"
          @delete="handleDeleteVoice"
        />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="settings-footer">
        <span class="settings-footer__summary">
          已选：{{ selectedAvatarName }} · {{ selectedVoiceName }}
        </span>
        <div class="settings-footer__actions">
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存并应用</el-button>
        </div>
      </div>
    </template>

    <VoiceCloneDialog
      v-model="cloneVisible"
      :upload-enabled="meta.uploadEnabled"
      @created="handleCloned"
    />
  </el-dialog>
</template>

<style scoped>
.settings-lead {
  margin: 0 0 12px;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-sm);
  line-height: 1.6;
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.settings-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.settings-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settings-footer__summary {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
}

.settings-footer__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

/* el-dialog 被 teleport 到 body，scoped 样式够不到，只能用 :global 限定到本弹窗类名 */
:global(.avatar-voice-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.avatar-voice-dialog .el-dialog__body) {
  max-height: calc(82vh - 170px);
  overflow-y: auto;
  padding-right: 6px;
}

@media (max-width: 640px) {
  :global(.avatar-voice-dialog .el-dialog__body) {
    max-height: calc(88vh - 150px);
  }
}
</style>
