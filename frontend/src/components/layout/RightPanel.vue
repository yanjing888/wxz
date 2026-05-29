<template>
  <aside class="w-[520px] max-w-[38vw] min-w-[520px] flex flex-col bg-white shrink-0 border-l border-[#dbe3ee]">
    <BenchCameraPanel
      :env-check-enabled="envCheckEnabled"
      :env-level="envLevel"
      :env-hint="envHint"
      :env-logs="envLogs"
      :env-check-running="envCheckRunning"
      @toggle-env="$emit('toggle-env', $event)"
      @env-check="$emit('env-check')"
    />
    <ChatBox :messages="messages" :loading="loadingAssist" />
    <div class="p-6 bg-white border-t border-[#dbe3ee]">
      <div class="glass-panel p-3 rounded-xl bg-slate-50 border border-[#dbe3ee]">
        <div v-if="imagePreview" class="mb-2 flex items-center gap-2 p-2 bg-white rounded-lg border border-[#dbe3ee]">
          <img :src="imagePreview" alt="附件" class="w-12 h-12 object-cover rounded-md border border-slate-200" />
          <span class="text-[10px] text-slate-500 flex-1">{{ uploadingImage ? '图片上传中…' : '已添加图片，发送时将一并提交' }}</span>
          <button type="button" class="text-slate-400 hover:text-red-500 text-xs px-2" @click="$emit('clear-image')">移除</button>
        </div>
        <textarea
          v-model="input"
          rows="3"
          placeholder="输入你的问题…（Enter 发送，Shift+Enter 换行）"
          class="w-full bg-transparent border-none text-sm text-slate-700 outline-none p-1 resize-none"
          @keydown="onKeydown"
        />
        <div class="flex justify-between items-center mt-2 gap-2">
          <button type="button" class="flex items-center gap-2 p-2 px-3 text-[10px] text-slate-400 hover:text-blue-500 rounded-lg hover:bg-blue-50 transition-all btn-active-scale shrink-0" @click="triggerUpload">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" /></svg>
            上传图片
          </button>
          <button
            type="button"
            class="flex items-center justify-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl text-sm font-bold text-white shadow-md transition-all btn-active-scale shrink-0"
            :disabled="loadingAssist || !canSend"
            @click="send"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" /></svg>
            发送
          </button>
        </div>
        <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFileChange" />
      </div>
      <button type="button" class="w-full mt-4 py-3 bg-white hover:bg-slate-50 text-slate-600 hover:text-slate-800 border border-slate-300 rounded-xl text-xs font-bold transition-all btn-active-scale" @click="$emit('report')">
        结束实验并生成总结报告
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import BenchCameraPanel from '../monitor/BenchCameraPanel.vue'
import ChatBox from '../chat/ChatBox.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  envCheckEnabled: { type: Boolean, default: true },
  envLevel: { type: String, default: 'L0' },
  envHint: { type: String, default: '' },
  envLogs: { type: Array, default: () => [] },
  envCheckRunning: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'upload-image', 'clear-image', 'report', 'toggle-env', 'env-check'])

const input = ref('')
const fileInput = ref(null)

const canSend = computed(() =>
  !props.uploadingImage && (input.value.trim().length > 0 || !!props.imagePreview)
)

function send() {
  if (!canSend.value || props.loadingAssist) return
  emit('send', input.value.trim())
  input.value = ''
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function triggerUpload() {
  fileInput.value?.click()
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (file) emit('upload-image', file)
  e.target.value = ''
}
</script>
