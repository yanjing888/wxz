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
        :class="[
          bubbleBaseClass,
          msg.role === 'ai' ? aiBubbleClass : userBubbleClass
        ]"
      >
        <div v-if="msg.image" class="mb-2">
          <p
            v-if="msg.role === 'ai' && msg.annotated"
            class="text-[10px] font-semibold text-red-600 mb-1.5 flex items-center gap-1"
          >
            <span class="w-1.5 h-1.5 rounded-full bg-red-500 shrink-0" />
            纠错标注图
          </p>
          <img
            :src="msg.image"
            :alt="msg.role === 'ai' && msg.annotated ? '纠错标注图' : '用户上传图片'"
            class="max-w-full max-h-48 rounded-xl border border-line-soft object-contain bg-white"
            @error="onImageError($event, msg)"
          />
        </div>
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
        <div
          v-if="canRate(msg)"
          class="mt-2 pt-2 border-t border-line-soft flex items-center gap-2 flex-wrap"
        >
          <span class="text-[10px] text-ink-faint">本次回答是否有帮助？</span>
          <button
            type="button"
            class="feedback-btn"
            :disabled="feedbackLoadingId === msg.id"
            @click="submitFeedback(msg, 'HELPFUL')"
          >
            有帮助
          </button>
          <button
            type="button"
            class="feedback-btn"
            :disabled="feedbackLoadingId === msg.id"
            @click="submitFeedback(msg, 'NOT_HELPFUL')"
          >
            无帮助
          </button>
        </div>
        <div
          v-else-if="msg.feedbackRating && msg.role === 'ai' && !msg.streaming"
          class="mt-2 pt-2 border-t border-line-soft flex items-center gap-1.5"
        >
          <span class="text-[10px] text-ink-faint">已评价：</span>
          <span
            class="text-[10px] font-semibold px-1.5 py-0.5 rounded"
            :class="msg.feedbackRating === 'HELPFUL' ? 'text-emerald-600 bg-emerald-50' : 'text-rose-500 bg-rose-50'"
          >
            {{ msg.feedbackRating === 'HELPFUL' ? '有帮助' : '无帮助' }}
          </span>
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
import { useLabStore } from '../../stores/lab'

const scrollEl = ref(null)
const feedbackLoadingId = ref(null)
const lab = useLabStore()

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
  studentName: { type: String, default: '' },
  enableFeedback: { type: Boolean, default: true }
})

const bubbleBaseClass = 'px-3 py-2.5 rounded-2xl leading-relaxed border chat-md shadow-card'
const aiBubbleClass = 'w-[min(78%,680px)] bg-white text-ink-base border-line-soft rounded-tl-sm text-[13px]'
const userBubbleClass = 'max-w-[88%] brand-gradient-soft text-ink-base border-brand-100 rounded-tr-sm text-[12px]'

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

function canRate(msg) {
  return props.enableFeedback && msg?.role === 'ai' && msg?.id && !msg?.streaming && !msg?.localWelcome && !!lab.session?.id && !msg?.feedbackRating
}

async function submitFeedback(msg, rating) {
  if (!msg?.id || feedbackLoadingId.value) return
  feedbackLoadingId.value = msg.id
  try {
    await lab.submitMessageFeedback(msg.id, rating)
  } catch {
    window.dispatchEvent(new CustomEvent('wxz-app-alert', {
      detail: { title: '评价失败', message: '请稍后重试' }
    }))
  } finally {
    feedbackLoadingId.value = null
  }
}
</script>

<style scoped>
.feedback-btn {
  @apply px-2.5 py-1 rounded-lg text-[11px] font-semibold border border-line-soft text-ink-muted bg-white hover:bg-surface-soft transition-colors;
}
.feedback-btn--active {
  @apply border-brand-200 bg-brand-50 text-brand-700;
}
.feedback-btn--active-danger {
  @apply border-red-200 bg-red-50 text-red-600;
}
.feedback-btn:disabled {
  @apply opacity-60 cursor-not-allowed;
}
</style>
