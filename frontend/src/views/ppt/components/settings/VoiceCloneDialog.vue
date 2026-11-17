<script setup>
/**
 * 声音复刻二级弹窗。
 *
 * DashScope 只接受公网音频 URL；「上传音频」只有在后端配置了公网存储时才可用，
 * 否则强制走「音频链接」。表单字段与 POST /ppt/voice/clone 的 multipart 口径一致。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { cloneVoice } from '@/api/ppt'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 后端是否允许上传（配置了公网可达存储） */
  uploadEnabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'created'])

const form = reactive({
  name: '',
  gender: 'female',
  trait: '',
  language: '',
  sourceType: 'url',
  url: '',
})

const fileList = ref([])
const selectedFile = ref(null)
const submitting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const reset = () => {
  Object.assign(form, {
    name: '',
    gender: 'female',
    trait: '',
    language: '',
    sourceType: 'url',
    url: '',
  })
  fileList.value = []
  selectedFile.value = null
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) reset()
  },
)

watch(
  () => props.uploadEnabled,
  (enabled) => {
    if (!enabled && form.sourceType === 'upload') form.sourceType = 'url'
  },
)

const extOf = (name) => {
  const index = String(name || '').lastIndexOf('.')
  return index >= 0 ? String(name).slice(index + 1).toLowerCase() : ''
}

const handleFileChange = (uploadFile) => {
  const raw = uploadFile?.raw
  if (!raw) return
  const ext = extOf(raw.name)
  if (ext !== 'wav' && ext !== 'mp3') {
    ElMessage.warning('仅支持 wav / mp3 音频')
    fileList.value = []
    selectedFile.value = null
    return
  }
  if (raw.size > 10 * 1024 * 1024) {
    ElMessage.warning('音频不能超过 10MB')
    fileList.value = []
    selectedFile.value = null
    return
  }
  // 只保留最后一个选择
  fileList.value = [uploadFile]
  selectedFile.value = raw
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const isHttpUrl = (value) => {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

const validate = () => {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请填写音色名称')
    return false
  }
  if (name.length > 50) {
    ElMessage.warning('音色名称不能超过 50 个字符')
    return false
  }
  if (form.sourceType === 'upload') {
    if (!props.uploadEnabled) {
      ElMessage.warning('当前未配置公网存储，请改用音频链接')
      return false
    }
    if (!selectedFile.value) {
      ElMessage.warning('请选择要复刻的音频文件')
      return false
    }
  } else {
    const url = form.url.trim()
    if (!url) {
      ElMessage.warning('请填写公网音频链接')
      return false
    }
    if (!isHttpUrl(url)) {
      ElMessage.warning('音频链接必须是 http/https 地址')
      return false
    }
  }
  return true
}

const submit = async () => {
  if (submitting.value || !validate()) return
  const formData = new FormData()
  formData.append('name', form.name.trim())
  formData.append('gender', form.gender)
  if (form.trait.trim()) formData.append('trait', form.trait.trim())
  if (form.language.trim()) formData.append('language', form.language.trim())
  formData.append('sourceType', form.sourceType)
  if (form.sourceType === 'upload') {
    formData.append('file', selectedFile.value)
  } else {
    formData.append('url', form.url.trim())
  }

  submitting.value = true
  try {
    const created = await cloneVoice(formData)
    ElMessage.success(`已复刻音色：${created?.name || form.name.trim()}`)
    emit('created', created)
    visible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '声音复刻失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="复刻音色"
    width="520px"
    append-to-body
    destroy-on-close
    class="voice-clone-dialog"
  >
    <el-form :model="form" label-width="96px" @submit.prevent>
      <el-form-item label="音色名称" required>
        <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="如：我的课堂声音" />
      </el-form-item>

      <el-form-item label="性别" required>
        <el-radio-group v-model="form.gender">
          <el-radio value="female">女</el-radio>
          <el-radio value="male">男</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="特征描述">
        <el-input v-model="form.trait" maxlength="100" placeholder="选填，如：温和、语速偏慢" />
      </el-form-item>

      <el-form-item label="支持语言">
        <el-input v-model="form.language" maxlength="100" placeholder="选填，如：中文、英文" />
      </el-form-item>

      <el-form-item label="音频来源" required>
        <el-radio-group v-model="form.sourceType">
          <el-radio value="url">音频链接</el-radio>
          <el-radio value="upload" :disabled="!uploadEnabled">上传音频</el-radio>
        </el-radio-group>
        <p v-if="!uploadEnabled" class="clone-hint">
          当前未配置公网存储，上传不可用；请粘贴公网音频链接。
        </p>
      </el-form-item>

      <el-form-item v-if="form.sourceType === 'url'" label="音频链接" required>
        <el-input
          v-model="form.url"
          maxlength="500"
          placeholder="https://example.com/sample.mp3"
        />
        <p class="clone-hint">链接必须能被阿里云访问（公网 http/https）。</p>
      </el-form-item>

      <el-form-item v-else label="音频文件" required>
        <el-upload
          v-model:file-list="fileList"
          :auto-upload="false"
          :limit="1"
          accept=".wav,.mp3,audio/wav,audio/mpeg"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button :icon="UploadFilled">选择音频</el-button>
        </el-upload>
        <p class="clone-hint">wav / mp3，10–60 秒，≤10MB；建议安静环境、单人连续说话。</p>
      </el-form-item>
    </el-form>

    <p class="clone-note">
      复刻会调用阿里云声音复刻服务并占用一次额度；请只使用本人或已获授权的声音样本。
    </p>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        开始复刻
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.clone-hint {
  margin: 6px 0 0;
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: 1.5;
}

.clone-note {
  margin: 0;
  padding: 8px 10px;
  border-radius: var(--tb-radius-md);
  background: var(--tb-bg-sunken);
  color: var(--tb-text-tertiary);
  font-size: var(--tb-text-xs);
  line-height: 1.6;
}

/* 小屏时让表单滚动，底部按钮始终可见 */
:global(.voice-clone-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.voice-clone-dialog .el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
