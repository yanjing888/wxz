<template>
  <section class="flex-1 flex flex-col overflow-hidden min-h-0">
    <header class="shrink-0 flex items-center gap-2.5 px-5 pt-3 pb-2">
      <div class="w-8 h-8 rounded-xl brand-gradient flex items-center justify-center text-white text-[13px] font-bold shadow-brand">智</div>
      <div class="flex flex-col leading-tight min-w-0">
        <span class="text-[13px] font-bold text-ink-strong">智能助手 · AI Tutor</span>
        <span class="text-[10px] text-ink-faint truncate" :title="subtitle">{{ subtitle }}</span>
      </div>
    </header>
    <div class="workzone-divider" />

    <ChatBox
      :messages="messages"
      :loading="loadingAssist"
      :welcome-subtitle="subtitle"
      :student-name="studentName"
    />

    <Composer
      :loading-assist="loadingAssist"
      :uploading-image="uploadingImage"
      :image-preview="imagePreview"
      :image-ready="imageReady"
      :suggestions="suggestions"
      @send="onComposerSend"
      @stop="$emit('stop')"
      @upload-image="(file) => $emit('upload-image', file)"
      @clear-image="$emit('clear-image')"
    />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import ChatBox from '../chat/ChatBox.vue'
import Composer from '../chat/Composer.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  experimentName: { type: String, default: '' },
  stepTitle: { type: String, default: '' },
  studentName: { type: String, default: '' },
  suggestions: { type: Array, default: () => [] }
})

const emit = defineEmits(['send', 'stop', 'upload-image', 'clear-image'])

const subtitle = computed(() => {
  const exp = props.experimentName
  const step = props.stepTitle
  if (exp && step) return `正在协助：${exp} · ${step}`
  if (exp) return `正在协助：${exp}`
  if (step) return `当前步骤：${step}`
  return '可以根据当前实验步骤为你纠错、答疑、复盘'
})

async function onComposerSend(text) {
  return emit('send', text)
}
</script>
