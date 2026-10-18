<template>
  <div class="chat-record-panel flex-1 min-h-0 flex flex-col overflow-hidden">
    <!-- 两个列表切换 -->
    <div v-if="!selectedPair" class="record-tabs shrink-0">
      <button
        type="button"
        class="record-tab"
        :class="{ active: activeList === 'helpful' }"
        @click="activeList = 'helpful'"
      >有帮助 ({{ helpfulPairs.length }})</button>
      <button
        type="button"
        class="record-tab"
        :class="{ active: activeList === 'notHelpful' }"
        @click="activeList = 'notHelpful'"
      >无帮助 ({{ notHelpfulPairs.length }})</button>
    </div>

    <!-- 加载/空 -->
    <template v-if="!selectedPair">
      <div v-if="loading" class="record-empty">加载中…</div>
      <div v-else-if="!currentList.length" class="record-empty">
        暂无{{ activeList === 'helpful' ? '有帮助' : '无帮助' }}记录。
      </div>
      <div v-else class="record-list custom-scroll flex-1 min-h-0 overflow-y-auto">
        <button
          v-for="(pair, i) in currentList"
          :key="i"
          type="button"
          class="record-row"
          @click="selectedPair = pair"
        >
          <div class="row-left">
            <span class="row-step">步骤 {{ pair.stepId || '—' }}</span>
            <span class="row-time">{{ formatTime(pair.createdAt) }}</span>
          </div>
          <p class="row-question">{{ pair.question || '（无提问文本）' }}</p>
          <p class="row-answer">{{ pair.answer || '（无 AI 回复）' }}</p>
        </button>
      </div>
    </template>

    <!-- 详情 -->
    <div v-else class="record-detail flex-1 min-h-0 flex flex-col overflow-hidden">
      <div class="detail-top shrink-0">
        <button type="button" class="back-btn" @click="selectedPair = null">
          <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" class="back-icon">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
          返回列表
        </button>
        <span class="detail-time">{{ formatTime(selectedPair.createdAt) }}</span>
      </div>

      <div class="detail-body custom-scroll flex-1 min-h-0 overflow-y-auto">
        <div class="detail-step">步骤 {{ selectedPair.stepId || '—' }}</div>

        <div v-if="selectedPair.question" class="detail-block detail-q">
          <span class="detail-role">学生提问</span>
          <p>{{ selectedPair.question }}</p>
        </div>

        <div v-if="selectedPair.answer" class="detail-block detail-a">
          <span class="detail-role">AI 回答</span>
          <p>{{ selectedPair.answer }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { sessionApi } from '../../api'

const props = defineProps({
  sessionId: { type: Number, default: 0 }
})

const loading = ref(false)
const messages = ref([])
const selectedPair = ref(null)
const activeList = ref('helpful')

const qaPairs = computed(() => {
  const pairs = []
  let current = null
  for (const msg of messages.value) {
    if (msg.role === 'user') {
      if (current) pairs.push(current)
      current = {
        stepId: msg.stepId,
        createdAt: msg.createdAt,
        question: msg.text,
        answer: '',
        rating: null
      }
    } else if (msg.role === 'ai') {
      if (!current) {
        current = { stepId: msg.stepId, createdAt: msg.createdAt, question: '', answer: '', rating: null }
      }
      current.answer = msg.text
      current.rating = msg.feedbackRating
      pairs.push(current)
      current = null
    }
  }
  if (current) pairs.push(current)
  return pairs
})

const helpfulPairs = computed(() => qaPairs.value.filter((p) => p.rating === 'HELPFUL'))
const notHelpfulPairs = computed(() => qaPairs.value.filter((p) => p.rating === 'NOT_HELPFUL'))
const currentList = computed(() => activeList.value === 'helpful' ? helpfulPairs.value : notHelpfulPairs.value)

async function loadMessages() {
  if (!props.sessionId) { messages.value = []; return }
  loading.value = true
  try {
    const { data } = await sessionApi.messages(props.sessionId)
    messages.value = data || []
  } catch {
    messages.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadMessages)
watch(() => props.sessionId, () => { selectedPair.value = null; loadMessages() })

function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.chat-record-panel {
  @apply bg-white;
}

/* ====== 两个列表切换 ====== */
.record-tabs {
  @apply flex border-b border-line-soft;
}
.record-tab {
  @apply flex-1 py-2.5 text-[13px] font-semibold text-ink-faint transition-colors hover:text-ink-muted;
  position: relative;
}
.record-tab.active {
  @apply text-brand-600;
}
.record-tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20%;
  right: 20%;
  height: 2px;
  @apply bg-brand-600 rounded-full;
}

.record-empty {
  @apply flex-1 flex items-center justify-center text-ink-faint text-[14px];
}

/* ====== 列表 ====== */
.record-list {
  @apply p-3 space-y-2;
}
.record-row {
  @apply w-full text-left rounded-xl border border-line-soft bg-white p-3 transition-all hover:border-brand-300 hover:bg-brand-50;
}
.row-left {
  @apply flex items-center gap-2 mb-1.5;
}
.row-step {
  @apply text-[10px] font-bold text-brand-600 bg-brand-50 px-2 py-0.5 rounded;
}
.row-time {
  @apply text-[10px] text-ink-faint;
}
.row-question {
  @apply text-[13px] text-ink-strong leading-snug mb-1;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.row-answer {
  @apply text-[12px] text-ink-faint leading-snug;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
}

/* ====== 详情 ====== */
.detail-top {
  @apply flex items-center justify-between px-5 py-3 border-b border-line-soft;
}
.back-btn {
  @apply flex items-center gap-1 text-[13px] font-semibold text-ink-muted hover:text-brand-600 transition-colors;
}
.back-icon {
  width: 16px;
  height: 16px;
}
.detail-time {
  @apply text-[11px] text-ink-faint;
}
.detail-body {
  @apply p-5 space-y-4;
}
.detail-step {
  @apply text-[11px] font-bold text-brand-600 bg-brand-50 px-2.5 py-1 rounded-md inline-block;
}
.detail-block {
  @apply rounded-xl p-4;
}
.detail-q {
  @apply bg-surface-soft border border-line-soft;
}
.detail-a {
  @apply bg-brand-50 border border-brand-100;
}
.detail-role {
  @apply block text-[10px] font-bold text-ink-faint uppercase tracking-wider mb-2;
}
.detail-block p {
  @apply text-[13px] leading-relaxed;
}
.detail-q p {
  @apply text-ink-strong;
}
.detail-a p {
  @apply text-ink-base;
}
</style>
