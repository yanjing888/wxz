<template>
  <div ref="scrollEl" class="flex-1 overflow-y-auto px-3 py-3 space-y-4 custom-scroll min-h-0">
    <div
      v-for="(msg, i) in messages"
      :key="i"
      class="flex gap-3 fade-in-up"
      :class="msg.role === 'user' ? 'flex-row-reverse' : ''"
    >
      <div
        class="w-7 h-7 flex flex-shrink-0 items-center justify-center text-[10px] font-bold shadow-card"
        :class="[
          msg.role === 'ai'
            ? 'rounded-lg brand-gradient text-white'
            : 'rounded-full brand-gradient text-white',
          msg.role === 'ai' && msg.streaming && !msg.text ? 'chat-loading-avatar' : ''
        ]"
      >
        {{ msg.role === 'ai' ? '智' : userInitial }}
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
        <div v-else-if="msg.text || msg.streaming" class="min-w-0 w-full">
          <div v-html="renderMd(msg.text, msg.role)" />
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

    <div v-if="!messages.length && !loading" class="flex flex-col items-center justify-center py-10 px-6 text-center fade-in-up">
      <div class="w-14 h-14 rounded-2xl brand-gradient flex items-center justify-center text-white text-base font-bold mb-3 shadow-brand chat-loading-avatar">智</div>
      <p class="text-[15px] font-bold text-ink-strong mb-1.5">你好，我是物小智</p>
      <p class="text-[11px] text-ink-muted leading-relaxed max-w-[320px]">{{ welcomeSubtitle || '可以根据当前实验步骤为你纠错、答疑、复盘。' }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { renderChatMarkdown } from '../../utils/markdown'

const scrollEl = ref(null)

function scrollToBottom(smooth = false) {
  nextTick(() => {
    requestAnimationFrame(() => {
      const el = scrollEl.value
      if (!el) return
      el.scrollTo({
        top: el.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto'
      })
    })
  })
}

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  welcomeSubtitle: { type: String, default: '' },
  studentName: { type: String, default: '' }
})

const hasStreaming = computed(() => props.messages.some(m => m.streaming))

watch(() => props.messages.length, () => scrollToBottom(true))

watch(
  () => props.loading,
  (v) => {
    if (v) scrollToBottom(true)
  }
)

watch(
  () => {
    const last = props.messages[props.messages.length - 1]
    return last?.streaming ? `${last.text || ''}|${last.image || ''}` : ''
  },
  () => {
    if (hasStreaming.value) scrollToBottom(false)
  }
)

const userInitial = computed(() => {
  const n = (props.studentName || '').trim()
  if (!n || n === '--' || n === '学生') return '学'
  return n.charAt(0)
})

function renderMd(text, role) {
  // 仅 AI 回复做「短行标题」规范化；用户消息（含预设问题）保持原样
  return renderChatMarkdown(text, { normalize: role === 'ai' })
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
