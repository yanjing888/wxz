<template>
  <div class="flex-1 overflow-y-auto px-3 py-3 space-y-4 custom-scroll min-h-0">
    <div
      v-for="(msg, i) in messages"
      :key="i"
      class="flex gap-3 fade-in-up"
      :class="msg.role === 'user' ? 'flex-row-reverse' : ''"
    >
      <div
        class="w-7 h-7 rounded-lg flex flex-shrink-0 items-center justify-center text-[10px] font-bold shadow-card"
        :class="[
          msg.role === 'ai' ? 'brand-gradient text-white' : 'bg-white text-ink-base border border-line-soft',
          msg.role === 'ai' && msg.streaming && !msg.text ? 'chat-loading-avatar' : ''
        ]"
      >
        {{ msg.role === 'ai' ? '智' : '我' }}
      </div>
      <div
        class="px-3 py-2.5 rounded-2xl leading-relaxed border max-w-[88%] chat-md shadow-card"
        :class="msg.role === 'ai'
          ? 'bg-white text-ink-base border-line-soft rounded-tl-sm text-[13px]'
          : 'brand-gradient-soft text-ink-base border-brand-100 rounded-tr-sm text-[12px]'"
      >
        <img
          v-if="msg.image"
          :src="msg.image"
          alt="用户上传图片"
          class="max-w-full max-h-48 rounded-xl border border-line-soft mb-2 object-contain bg-white"
          @error="onImageError($event, msg)"
        />
        <div v-if="msg.streaming && !msg.text" class="flex items-center gap-2 text-ink-muted">
          <span>正在分析</span>
          <span class="chat-loading-dots" aria-hidden="true">
            <span /><span /><span />
          </span>
        </div>
        <div v-else-if="msg.text || msg.streaming" class="inline">
          <span v-html="renderMd(msg.text)" />
          <span v-if="msg.streaming" class="chat-stream-cursor" aria-hidden="true" />
        </div>
      </div>
    </div>
    <div v-if="loading && !hasStreaming" class="flex gap-3 chat-loading fade-in-up">
      <div class="w-7 h-7 brand-gradient rounded-lg flex items-center justify-center text-[10px] font-bold text-white chat-loading-avatar shadow-card">智</div>
      <div class="px-3 py-2.5 rounded-2xl rounded-tl-sm bg-white border border-line-soft text-[13px] text-ink-muted flex items-center gap-2 shadow-card">
        <span>正在分析</span>
        <span class="chat-loading-dots" aria-hidden="true">
          <span /><span /><span />
        </span>
      </div>
    </div>

    <div v-if="!messages.length && !loading" class="flex flex-col items-center justify-center py-8 text-center text-ink-faint">
      <div class="w-10 h-10 rounded-xl brand-gradient-soft border border-brand-100 flex items-center justify-center text-brand-600 text-sm font-bold mb-2">智</div>
      <p class="text-[13px] text-ink-muted">向 AI 提问或上传图片求助</p>
      <p class="text-[10px] mt-1">回答基于当前步骤上下文</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const hasStreaming = computed(() => props.messages.some(m => m.streaming))

function renderMd(text) {
  return renderChatMarkdown(text)
}

function onImageError(event, msg) {
  const el = event.target
  if (msg.imageFallback && el.src !== msg.imageFallback) {
    el.src = msg.imageFallback
    return
  }
  el.alt = '图片无法预览'
}
</script>
