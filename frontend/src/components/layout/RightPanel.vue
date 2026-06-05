<template>
  <section class="surface-card rounded-2xl flex-1 flex flex-col overflow-hidden min-h-0">
    <header class="shrink-0 flex items-center gap-2.5 px-5 py-3 border-b border-line-soft">
      <div class="w-9 h-9 rounded-xl brand-gradient flex items-center justify-center text-white text-sm font-bold shadow-brand">智</div>
      <div class="flex flex-col leading-tight">
        <span class="text-sm font-bold text-ink-strong">智能助手 · AI Tutor</span>
        <span class="text-[10px] text-ink-faint">基于当前实验步骤与知识库回答 · 支持图像理解</span>
      </div>
    </header>

    <!-- 对话主区（占据所有剩余高度） -->
    <ChatBox :messages="messages" :loading="loadingAssist" />

    <!-- 底部输入区 -->
    <Composer
      :loading-assist="loadingAssist"
      :uploading-image="uploadingImage"
      :image-preview="imagePreview"
      :image-ready="imageReady"
      @send="onComposerSend"
      @upload-image="(file) => $emit('upload-image', file)"
      @clear-image="$emit('clear-image')"
    />
  </section>
</template>

<script setup>
import ChatBox from '../chat/ChatBox.vue'
import Composer from '../chat/Composer.vue'

defineProps({
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'upload-image', 'clear-image'])

async function onComposerSend(text) {
  return emit('send', text)
}
</script>
