<template>
  <div class="shrink-0 px-3 pb-3">
    <div v-if="suggestions.length" class="flex flex-wrap gap-1.5 mb-2 fade-in-up">
      <button
        v-for="s in suggestions"
        :key="s"
        type="button"
        class="px-2.5 py-1 rounded-full text-[11px] text-ink-muted bg-white hover:text-brand-700 border border-line-soft hover:border-brand-200 hover:bg-brand-50/40 transition-all btn-active-scale max-w-full truncate"
        :title="s"
        :disabled="readOnly"
        @click="onSuggestion(s)"
      >
        {{ s }}
      </button>
    </div>
    <div class="rounded-2xl bg-white border border-line-soft shadow-card focus-within:border-brand-300 focus-within:shadow-soft transition-all p-2.5">
      <div v-if="dataAttachment" class="mb-2">
        <div
          class="relative rounded-xl border border-line-soft bg-[#f7f7f8] px-3 py-2.5 pr-9 max-w-[420px] cursor-pointer hover:border-brand-200 hover:bg-brand-50/30 transition-colors"
          role="button"
          tabindex="0"
          :title="dataAttachmentExpanded ? '收起数据明细' : '查看数据明细'"
          @click="toggleDataAttachment"
          @keydown.enter.prevent="toggleDataAttachment"
          @keydown.space.prevent="toggleDataAttachment"
        >
          <div class="flex items-start gap-2.5 min-w-0">
            <div class="w-9 h-9 rounded-lg bg-brand-600 flex items-center justify-center shrink-0 shadow-sm">
              <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2a4 4 0 014-4h2M9 7h6M9 11h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z" />
              </svg>
            </div>
            <div class="min-w-0 pt-0.5 flex-1">
              <p class="text-[13px] font-semibold text-ink-strong truncate leading-snug">{{ dataAttachment.title }}</p>
              <p class="text-[11px] text-ink-faint mt-0.5 truncate flex items-center gap-1">
                <span>{{ dataAttachment.label }} · {{ dataAttachment.stepTitle || '当前步骤' }}</span>
                <span v-if="dataAttachmentBody" class="text-brand-600 font-semibold">{{ dataAttachmentExpanded ? '收起' : '查看' }}</span>
              </p>
            </div>
          </div>
          <pre
            v-if="dataAttachmentExpanded && dataAttachmentBody"
            class="mt-2 ml-11 whitespace-pre-wrap break-words text-[12px] leading-relaxed text-ink-base bg-white/80 rounded-lg border border-line-soft px-2.5 py-2"
          >{{ dataAttachmentBody }}</pre>
          <button
            type="button"
            class="absolute top-2 right-2 w-6 h-6 rounded-full bg-ink-strong/90 text-white text-[13px] leading-none flex items-center justify-center hover:bg-ink-strong transition-colors"
            title="移除读数"
            @click.stop="$emit('clear-data')"
          >
            ×
          </button>
        </div>
      </div>

      <div v-if="imagePreview" class="mb-2 flex items-center gap-2 p-1.5 bg-surface-soft rounded-lg border border-line-soft">
        <button
          type="button"
          class="relative w-10 h-10 shrink-0 overflow-hidden rounded-md border border-line-soft bg-white cursor-zoom-in focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-300"
          title="放大查看"
          @click="openImagePreview"
        >
          <img :src="imagePreview" alt="附件" class="w-full h-full object-cover" />
        </button>
        <span class="text-[11px] text-ink-muted flex-1 leading-tight">{{ attachHint }}</span>
        <button
          type="button"
          class="text-ink-faint hover:text-red-500 text-[11px] px-1.5 py-1 rounded-md hover:bg-red-50 transition-colors"
          @click="$emit('clear-image')"
        >
          移除
        </button>
      </div>

      <textarea
        ref="textareaRef"
        v-model="input"
        rows="2"
        :placeholder="inputPlaceholder"
        class="w-full bg-transparent border-none text-[13px] text-ink-base outline-none resize-none placeholder:text-ink-faint leading-relaxed"
        :disabled="readOnly"
        @keydown="onKeydown"
      />
      <div class="flex justify-between items-center mt-1.5 gap-2">
        <div class="flex items-center gap-1.5 min-w-0 flex-wrap">
          <button
            type="button"
            class="flex items-center gap-1 px-2 py-1 text-[11px] font-semibold text-ink-muted hover:text-brand-600 rounded-md hover:bg-brand-50 transition-all btn-active-scale shrink-0"
            title="上传附件求助"
            :disabled="uploadingImage || readOnly"
            @click="triggerUpload"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" /></svg>
            附件
          </button>
          <button
            type="button"
            class="flex items-center gap-1 px-2 py-1 text-[11px] font-semibold text-ink-muted hover:text-brand-600 rounded-md hover:bg-brand-50 transition-all btn-active-scale shrink-0 disabled:opacity-50"
            title="使用平板摄像头拍照求助"
            :disabled="uploadingImage || readOnly"
            @click="triggerCapture"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 8.5A2.5 2.5 0 016.5 6H8l1.4-2h5.2L16 6h1.5A2.5 2.5 0 0120 8.5v8A2.5 2.5 0 0117.5 19h-11A2.5 2.5 0 014 16.5v-8z" />
              <circle cx="12" cy="12.5" r="3.2" />
            </svg>
            拍照
          </button>
          <button
            type="button"
            class="flex items-center gap-1 px-2 py-1 text-[11px] font-semibold rounded-md transition-all btn-active-scale shrink-0 disabled:opacity-50"
            :class="voiceRecording ? 'bg-red-50 text-red-600 hover:bg-red-100' : 'text-ink-muted hover:text-brand-600 hover:bg-brand-50'"
            :title="voiceTitle"
            :disabled="readOnly || voiceTranscribing"
            @click="toggleVoiceInput"
          >
            <svg v-if="voiceRecording" class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
              <rect x="6" y="6" width="12" height="12" rx="1.8" />
            </svg>
            <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.9" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 3.75a3 3 0 00-3 3v5.5a3 3 0 006 0v-5.5a3 3 0 00-3-3z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M5.75 11.5a6.25 6.25 0 0012.5 0M12 17.75v2.5m-3 0h6" />
            </svg>
            {{ voiceButtonText }}
          </button>
        </div>
        <button
          v-if="loadingAssist || submittingData"
          type="button"
          class="flex items-center justify-center w-8 h-8 rounded-lg shrink-0 border border-red-200 bg-red-50 text-red-600 hover:bg-red-100 transition-colors btn-active-scale"
          title="停止生成"
          aria-label="停止生成"
          @click="$emit('stop')"
        >
          <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24"><rect x="6" y="6" width="12" height="12" rx="1.5" /></svg>
        </button>
        <button
          v-else
          type="button"
          class="btn-brand flex items-center justify-center gap-1.5 px-4 py-1.5 rounded-lg text-[12px] font-bold shrink-0"
          :disabled="!canSend"
          @click="send"
        >
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" /></svg>
          发送
        </button>
      </div>
      <p
        v-if="voiceHint"
        class="mt-1.5 px-0.5 text-[11px] leading-relaxed"
        :class="voiceError ? 'text-rose-500' : 'text-ink-faint'"
      >
        {{ voiceHint }}
      </p>
      <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp,image/bmp,.jpg,.jpeg,.png,.gif,.webp,.bmp" class="hidden" @change="onFileChange" />
    </div>

    <Teleport to="body">
      <div
        v-if="previewOpen && imagePreview"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 md:p-8"
        @click.self="closeImagePreview"
      >
        <button
          type="button"
          class="absolute top-4 right-4 z-10 flex h-10 w-10 items-center justify-center rounded-full bg-white/15 text-xl text-white hover:bg-white/25"
          aria-label="关闭预览"
          @click="closeImagePreview"
        >
          ×
        </button>
        <img
          :src="imagePreview"
          alt="附件大图"
          class="max-h-[90vh] max-w-full select-none rounded-lg object-contain shadow-2xl"
          @click.stop
        />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { voiceApi } from '../../api'
import { createWavRecorder } from '../../utils/voiceRecorder'

const props = defineProps({
  loadingAssist: { type: Boolean, default: false },
  submittingData: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  dataAttachment: { type: Object, default: null },
  suggestions: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'stop', 'upload-image', 'capture-image', 'clear-image', 'clear-data'])

const input = ref('')
const fileInput = ref(null)
const textareaRef = ref(null)
const localSending = ref(false)
const previewOpen = ref(false)
const dataAttachmentExpanded = ref(false)
const voiceRecording = ref(false)
const voiceTranscribing = ref(false)
const voiceError = ref('')
const voiceStatus = ref('')
const voiceRecorder = ref(null)

const canSend = computed(() =>
  !props.readOnly
  && !props.uploadingImage
  && (input.value.trim().length > 0 || props.imageReady || !!props.dataAttachment)
)

const attachHint = computed(() => {
  if (props.uploadingImage) return '附件上传中…'
  if (props.imagePreview && !props.imageReady) return '附件上传失败或未就绪，请重新选择'
  if (props.imageReady) return '已添加附件，发送时一并提交'
  return ''
})

const inputPlaceholder = computed(() => {
  if (props.readOnly) {
    return '实验已结束，仅可查看历史对话'
  }
  if (props.dataAttachment) {
    return '可补充说明或提问，Enter 发送读数检查…（Shift+Enter 换行）'
  }
  return '问物小智：实验中遇到的问题，都可以在这里说…（Enter 发送，Shift+Enter 换行）'
})

const voiceButtonText = computed(() => {
  if (voiceTranscribing.value) return '识别中'
  if (voiceRecording.value) return '停止'
  return '语音'
})

const voiceTitle = computed(() => {
  if (voiceTranscribing.value) return '正在识别语音'
  if (voiceRecording.value) return '停止录音并识别'
  return '语音输入'
})

const voiceHint = computed(() => voiceError.value || voiceStatus.value)

const dataAttachmentBody = computed(() => {
  const body = String(props.dataAttachment?.body || '').trim()
  if (body) return body
  const values = props.dataAttachment?.values || {}
  return Object.entries(values)
    .filter(([, value]) => value != null && String(value).trim() !== '')
    .map(([key, value]) => `${key}: ${value}`)
    .join('\n')
})

async function send() {
  if (!canSend.value || props.loadingAssist || props.submittingData || localSending.value) return
  localSending.value = true
  try {
    const text = input.value.trim()
    const sent = await emit('send', text)
    if (sent !== false) input.value = ''
  } finally {
    localSending.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
    e.preventDefault()
    send()
  }
}

function triggerUpload() {
  if (props.uploadingImage || props.readOnly) return
  fileInput.value?.click()
}

function triggerCapture() {
  if (props.uploadingImage || props.readOnly) return
  emit('capture-image')
}

function openImagePreview() {
  if (!props.imagePreview) return
  previewOpen.value = true
}

function closeImagePreview() {
  previewOpen.value = false
}

function toggleDataAttachment() {
  if (!dataAttachmentBody.value) return
  dataAttachmentExpanded.value = !dataAttachmentExpanded.value
}

function onWindowKeydown(e) {
  if (e.key === 'Escape') closeImagePreview()
}

function onSuggestion(text) {
  if (!text || props.loadingAssist || props.readOnly) return
  input.value = text
  nextTick(() => textareaRef.value?.focus())
}

function appendVoiceText(text) {
  const recognized = String(text || '').trim()
  if (!recognized) return
  input.value = input.value.trim()
    ? `${input.value.trimEnd()}\n${recognized}`
    : recognized
  nextTick(() => textareaRef.value?.focus())
}

async function startVoiceInput() {
  if (props.readOnly || voiceRecording.value || voiceTranscribing.value) return
  voiceError.value = ''
  voiceStatus.value = '正在录音，点击“停止”后识别'
  try {
    voiceRecorder.value = await createWavRecorder({
      onAutoStop: () => {
        if (voiceRecording.value) stopVoiceInput()
      }
    })
    voiceRecording.value = true
  } catch (e) {
    voiceRecorder.value = null
    voiceStatus.value = ''
    voiceError.value = e.message || '无法访问麦克风，请检查浏览器或 APK 权限'
  }
}

async function stopVoiceInput() {
  const recorder = voiceRecorder.value
  if (!recorder || voiceTranscribing.value) return
  voiceRecording.value = false
  voiceTranscribing.value = true
  voiceError.value = ''
  voiceStatus.value = '正在识别语音…'
  voiceRecorder.value = null
  try {
    const blob = await recorder.stop()
    if (!blob || blob.size < 1024) {
      throw new Error('没有录到有效语音，请靠近麦克风后重试')
    }
    const file = new File([blob], `voice-${Date.now()}.wav`, { type: 'audio/wav' })
    const { data } = await voiceApi.transcribe(file)
    appendVoiceText(data?.text || '')
    voiceStatus.value = ''
  } catch (e) {
    voiceStatus.value = ''
    voiceError.value = e.message || '语音识别失败，请稍后重试'
  } finally {
    voiceTranscribing.value = false
  }
}

function toggleVoiceInput() {
  if (voiceRecording.value) {
    stopVoiceInput()
  } else {
    startVoiceInput()
  }
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) emit('upload-image', file)
  e.target.value = ''
}

watch(
  () => props.imagePreview,
  (value) => {
    if (!value) closeImagePreview()
  }
)

watch(
  () => props.dataAttachment,
  (value) => {
    dataAttachmentExpanded.value = false
    if (value && !props.readOnly) {
      nextTick(() => textareaRef.value?.focus())
    }
  }
)

watch(previewOpen, (open) => {
  if (open) window.addEventListener('keydown', onWindowKeydown)
  else window.removeEventListener('keydown', onWindowKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
  voiceRecorder.value?.cancel()
})

</script>
