<template>
  <div class="flex-1 overflow-y-auto p-6 space-y-6 custom-scroll min-h-0 bg-white">
    <div v-for="(msg, i) in messages" :key="i" class="flex gap-3" :class="msg.role === 'user' ? 'flex-row-reverse' : ''">
      <div
        class="w-8 h-8 rounded-lg flex flex-shrink-0 items-center justify-center text-[10px] font-bold"
        :class="[
          msg.role === 'ai' ? 'bg-blue-600 text-white' : 'bg-slate-200 text-slate-600',
          msg.role === 'ai' && msg.streaming && !msg.text ? 'chat-loading-avatar' : ''
        ]"
      >
        {{ msg.role === 'ai' ? '智' : '我' }}
      </div>
      <div
        class="p-4 rounded-2xl leading-relaxed border shadow-sm max-w-[85%] chat-md"
        :class="msg.role === 'ai'
          ? 'bg-white text-slate-700 border-slate-200 rounded-tl-none text-sm'
          : 'bg-blue-50 text-slate-800 border-blue-100 rounded-tr-none text-xs'"
      >
        <img
          v-if="msg.image"
          :src="msg.image"
          alt="用户上传图片"
          class="max-w-full max-h-48 rounded-lg border border-slate-200 mb-2 object-contain bg-white"
        />
        <div v-if="msg.streaming && !msg.text" class="flex items-center gap-2 text-slate-500">
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
    <div v-if="loading && !hasStreaming" class="flex gap-3 chat-loading">
      <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-[10px] font-bold text-white chat-loading-avatar">智</div>
      <div class="p-4 rounded-2xl rounded-tl-none bg-white border border-slate-200 text-sm text-slate-500 flex items-center gap-2">
        <span>正在分析</span>
        <span class="chat-loading-dots" aria-hidden="true">
          <span /><span /><span />
        </span>
      </div>
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
</script>
