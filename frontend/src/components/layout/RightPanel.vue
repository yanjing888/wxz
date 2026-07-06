<template>
  <section
    class="right-panel-shell relative flex-1 h-full grid overflow-hidden min-h-0 min-w-0"
    :style="{ '--history-panel-width': `${historyWidth}px` }"
  >
    <aside class="order-2 hidden lg:flex min-w-0 min-h-0 h-full flex-col gap-3 overflow-hidden border-l border-line-soft bg-white">
      <button
        type="button"
        class="mx-3 mt-3 h-10 rounded-lg brand-gradient text-white text-sm font-bold shadow-brand flex items-center justify-center gap-2 btn-active-scale"
        title="开始新的问答会话"
        @click="$emit('new-session')"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2.2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 5v14m-7-7h14" />
        </svg>
        新建对话
      </button>

      <div class="min-h-0 flex-1 overflow-hidden border-t border-line-soft bg-white">
        <div class="shrink-0 px-3 py-2 border-b border-line-soft flex items-center justify-between bg-white">
          <span class="text-[11px] font-bold text-ink-faint">最近对话</span>
          <span v-if="sessionHistoryLoading" class="text-[10px] text-ink-faint">加载中</span>
        </div>

        <div class="relative min-h-0 flex-1 h-[calc(100%-33px)]">
          <div class="absolute inset-0 overflow-y-auto custom-scroll px-2 py-2">
            <div v-if="!sessionHistoryLoading && !sessionHistory.length" class="px-2 py-4 text-[11px] text-ink-faint leading-relaxed">
              暂无历史会话
            </div>
            <button
              v-for="item in sessionHistory"
              :key="item.id"
              type="button"
              class="relative w-full text-left rounded-lg px-2.5 py-2.5 mb-1.5 border transition-all btn-active-scale"
              :class="item.id === currentSessionId ? 'bg-brand-50 border-brand-200 text-brand-900' : 'bg-transparent border-transparent text-ink-muted hover:bg-surface-soft hover:text-ink-base'"
              :title="sessionTitle(item)"
              @click="$emit('select-session', item)"
            >
              <div class="flex items-center gap-2 min-w-0">
                <svg class="w-3.5 h-3.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M7 8h10M7 12h6m-8 8l3-3h9a3 3 0 003-3V7a3 3 0 00-3-3H7a3 3 0 00-3 3v7a3 3 0 003 3" />
                </svg>
                <span class="text-xs font-semibold truncate">{{ sessionTitle(item) }}</span>
              </div>
              <span class="history-title-tooltip pointer-events-none absolute left-2 right-2 top-full z-20 mt-1 hidden rounded-md border border-line-soft bg-white px-2 py-1.5 text-[11px] font-semibold leading-snug text-ink-strong shadow-card">
                {{ sessionTitle(item) }}
              </span>
            </button>
          </div>
          <div class="pointer-events-none absolute inset-x-0 bottom-0 h-6 bg-gradient-to-t from-white to-transparent" />
        </div>
      </div>
    </aside>

    <div
      class="history-resizer hidden lg:block"
      title="拖拽调整会话记录宽度"
      @pointerdown="startResize"
    />

    <div class="order-1 min-w-0 min-h-0 h-full flex flex-col overflow-hidden">
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
        :suggestions="readOnly ? [] : suggestions"
        :read-only="readOnly"
        @send="onComposerSend"
        @stop="$emit('stop')"
        @upload-image="(file) => $emit('upload-image', file)"
        @capture-image="(file) => $emit('capture-image', file)"
        @clear-image="$emit('clear-image')"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import ChatBox from '../chat/ChatBox.vue'
import Composer from '../chat/Composer.vue'

const HISTORY_WIDTH_KEY = 'wxz_history_panel_width'
const MIN_HISTORY_WIDTH = 150
const MAX_HISTORY_WIDTH = 420
const DEFAULT_HISTORY_WIDTH = 220

const savedHistoryWidth = Number(localStorage.getItem(HISTORY_WIDTH_KEY))
const historyWidth = ref(
  Number.isFinite(savedHistoryWidth)
    ? Math.min(MAX_HISTORY_WIDTH, Math.max(MIN_HISTORY_WIDTH, savedHistoryWidth))
    : DEFAULT_HISTORY_WIDTH
)

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  experimentName: { type: String, default: '' },
  stepTitle: { type: String, default: '' },
  studentName: { type: String, default: '' },
  suggestions: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false },
  sessionHistory: { type: Array, default: () => [] },
  currentSessionId: { type: Number, default: 0 },
  sessionHistoryLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['send', 'stop', 'upload-image', 'capture-image', 'clear-image', 'new-session', 'select-session'])

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

function startResize(event) {
  event.preventDefault()
  const startX = event.clientX
  const startWidth = historyWidth.value

  const onMove = (moveEvent) => {
    const nextWidth = startWidth - (moveEvent.clientX - startX)
    historyWidth.value = Math.min(MAX_HISTORY_WIDTH, Math.max(MIN_HISTORY_WIDTH, nextWidth))
  }

  const onUp = () => {
    localStorage.setItem(HISTORY_WIDTH_KEY, String(historyWidth.value))
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp, { once: true })
  window.addEventListener('pointercancel', onUp, { once: true })
}

function sessionTitle(item) {
  if (!item) return '实验会话'
  if (item.historyTitle) return item.historyTitle
  return item.status === 'FINISHED' ? `已完成会话 #${item.id}` : `实验会话 #${item.id}`
}

onBeforeUnmount(() => {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})
</script>

<style scoped>
.right-panel-shell {
  grid-template-columns: minmax(0, 1fr);
}

@media (min-width: 1024px) {
  .right-panel-shell {
    grid-template-columns: minmax(0, 1fr) var(--history-panel-width);
  }
}

.history-resizer {
  position: absolute;
  top: 0;
  bottom: 0;
  right: var(--history-panel-width);
  z-index: 30;
  width: 10px;
  transform: translateX(50%);
  cursor: col-resize;
  background: transparent;
}

.history-resizer:hover {
  background:
    linear-gradient(
      to right,
      transparent 0,
      transparent 4px,
      rgba(79, 70, 229, 0.55) 4px,
      rgba(79, 70, 229, 0.55) 6px,
      transparent 6px
    );
}

button:hover > .history-title-tooltip {
  display: block;
}
</style>
