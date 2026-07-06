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
      <div v-if="imagePreview" class="mb-2 flex items-center gap-2 p-1.5 bg-surface-soft rounded-lg border border-line-soft">
        <img :src="imagePreview" alt="附件" class="w-10 h-10 object-cover rounded-md border border-line-soft" />
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
        placeholder="向物小智询问实验操作问题…（Enter 发送，Shift+Enter 换行）"
        class="w-full bg-transparent border-none text-[13px] text-ink-base outline-none resize-none placeholder:text-ink-faint leading-relaxed"
        :disabled="readOnly"
        @keydown="onKeydown"
      />
      <div class="flex justify-between items-center mt-1.5 gap-2">
        <div class="flex items-center gap-1.5 min-w-0">
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
        </div>
        <button
          v-if="loadingAssist"
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
      <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp,image/bmp,.jpg,.jpeg,.png,.gif,.webp,.bmp" class="hidden" @change="onFileChange" />
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'

const props = defineProps({
  loadingAssist: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  suggestions: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'stop', 'upload-image', 'capture-image', 'clear-image'])

const input = ref('')
const fileInput = ref(null)
const textareaRef = ref(null)

const canSend = computed(() =>
  !props.readOnly && !props.uploadingImage && (input.value.trim().length > 0 || props.imageReady)
)

const attachHint = computed(() => {
  if (props.uploadingImage) return '附件上传中…'
  if (props.imagePreview && !props.imageReady) return '附件上传失败或未就绪，请重新选择'
  if (props.imageReady) return '已添加附件，发送时一并提交'
  return ''
})

async function send() {
  if (!canSend.value || props.loadingAssist) return
  const text = input.value.trim()
  const sent = await emit('send', text)
  if (sent !== false) input.value = ''
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
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

function onSuggestion(text) {
  if (!text || props.loadingAssist || props.readOnly) return
  input.value = text
  nextTick(() => textareaRef.value?.focus())
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) emit('upload-image', file)
  e.target.value = ''
}

</script>
