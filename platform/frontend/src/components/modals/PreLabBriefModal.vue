<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-slate-900/30 backdrop-blur-md"
    @click.self="dismiss"
  >
    <div class="relative w-full max-w-lg max-h-[88vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full brand-gradient shrink-0" />
      <div class="p-5 md:p-6 border-b border-line-soft shrink-0">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="text-[11px] font-semibold text-brand-600 uppercase tracking-wide mb-1">课前要点</p>
            <h3 class="text-lg font-bold text-ink-strong leading-snug">{{ experimentName || '本次实验' }}</h3>
          <p class="text-[13px] text-ink-muted mt-1">进实验室前快速了解目标与易错点；课上直接回实验台问助教即可。</p>
          </div>
          <button
            type="button"
            class="w-9 h-9 rounded-xl text-ink-faint hover:text-ink-strong hover:bg-surface-muted text-2xl leading-none flex items-center justify-center shrink-0"
            @click="dismiss"
          >
            ×
          </button>
        </div>
      </div>

      <div class="p-5 md:p-6 overflow-y-auto custom-scroll flex-1 min-h-0">
        <div v-if="loading" class="flex flex-col items-center justify-center py-10 gap-3 text-ink-muted text-sm">
          <div class="w-10 h-10 rounded-xl brand-gradient flex items-center justify-center text-white font-bold shadow-brand">智</div>
          <p>正在生成课前要点…</p>
        </div>
        <div v-else-if="error" class="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
          {{ error }}
          <button type="button" class="block mt-3 text-brand-600 font-semibold hover:underline" @click="loadBrief">重试</button>
        </div>
        <div v-else class="chat-md brief-content" v-html="renderMd(content)" />
      </div>

      <div class="p-4 border-t border-line-soft bg-surface-soft flex flex-wrap justify-between items-center gap-3 shrink-0">
        <button
          type="button"
          class="text-[13px] text-ink-muted hover:text-brand-600 font-medium"
          :disabled="loading"
          @click="loadBrief"
        >
          重新生成
        </button>
        <div class="flex flex-wrap gap-2">
          <button
            v-if="experimentCode"
            type="button"
            class="btn-ghost px-4 py-2.5 rounded-xl font-semibold text-sm border border-line-soft"
            @click="goFullPrep"
          >
            去做预习自测
          </button>
          <button type="button" class="btn-brand px-6 py-2.5 rounded-xl font-bold text-sm" @click="dismiss">
            已了解，开始实验
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { aiToolApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  visible: Boolean,
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' }
})

const emit = defineEmits(['close'])
const router = useRouter()

function goFullPrep() {
  if (!props.experimentCode) return
  emit('close')
  router.push({ name: 'prep-ready', params: { code: props.experimentCode } })
}

const loading = ref(false)
const error = ref('')
const content = ref('')

watch(
  () => [props.visible, props.experimentCode],
  ([visible, code]) => {
    if (visible && code) loadBrief()
  },
  { immediate: true }
)

async function loadBrief() {
  if (!props.experimentCode) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await aiToolApi.invoke('lab-brief', {
      action: 'brief',
      inputs: {
        experimentCode: props.experimentCode,
        experimentName: props.experimentName
      }
    })
    content.value = data?.text || '暂无要点内容，请直接开始实验。'
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function dismiss() {
  if (props.experimentCode) {
    localStorage.setItem(`wxz_brief_${props.experimentCode}`, '1')
  }
  emit('close')
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.brief-content :deep(p) {
  @apply text-[14px] leading-relaxed text-ink-base mb-3 last:mb-0;
}
.brief-content :deep(ul) {
  @apply list-disc pl-5 space-y-1.5 text-[14px] text-ink-base;
}
.brief-content :deep(h2),
.brief-content :deep(h3) {
  @apply text-[15px] font-bold text-ink-strong mt-4 mb-2 first:mt-0;
}
</style>
