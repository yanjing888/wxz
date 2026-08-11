<template>
  <StagePanel layout="single" title="预习要点" desc="目标、准备事项、核心公式与易错点，一屏看完。">
    <template #actions>
      <button
        type="button"
        class="btn-brand px-4 py-1.5 rounded-lg text-[13px] font-semibold"
        :disabled="loading || !experimentCode"
        @click="generate"
      >
        {{ loading ? '生成中…' : brief ? '重新生成' : '生成要点' }}
      </button>
    </template>

    <p v-if="error" class="err-text">{{ error }}</p>

    <div v-if="!brief && !loading" class="empty-card">
      <p class="empty-title">{{ experimentName || '本实验' }} 的预习要点</p>
      <p class="empty-desc">进实验室前花几分钟看完，能省下大量返工时间。</p>
    </div>

    <template v-if="brief">
      <div class="brief-grid">
        <article v-for="card in cards" :key="card.key" class="brief-card">
          <div class="brief-head">
            <span class="brief-icon" :class="`brief-icon--${card.tone}`">{{ card.mark }}</span>
            <h3 class="brief-title">{{ card.title }}</h3>
          </div>
          <ul v-if="card.items.length" class="bullet-list">
            <li v-for="(item, i) in card.items" :key="i">{{ item }}</li>
          </ul>
          <p v-else class="brief-empty">{{ card.empty }}</p>
        </article>
      </div>

      <details v-if="rawText" class="raw-box">
        <summary>查看 AI 原始输出</summary>
        <div class="chat-md mt-3" v-html="renderMd(rawText)" />
      </details>

      <section class="next-card">
        <div class="min-w-0">
          <p class="next-title">看完了？做 5 道题检验一下</p>
          <p class="next-desc">预习自测会围绕上面的要点出题，做完即时判分。</p>
        </div>
        <div class="flex gap-2 shrink-0">
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" :disabled="marked" @click="markDone">
            {{ marked ? '已标记完成' : '标记预习完成' }}
          </button>
          <button type="button" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold" @click="$emit('next', 'quiz')">
            去自测
          </button>
        </div>
      </section>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { studentExperimentApi } from '../../api'
import { extractBriefSections, parseStructuredData } from '../../utils/aiTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  knowledge: { type: Array, default: () => [] }
})
defineEmits(['next'])

const { loading, error, invoke } = useAgentTool('pre-lab', () => props.experimentCode)

const brief = ref(null)
const rawText = ref('')
const marked = ref(false)

const cards = computed(() => {
  const b = brief.value || {}
  return [
    { key: 'objective', title: '这次要做什么', mark: '目', tone: 'sky', items: b.objective || [], empty: '未提取到实验目标。' },
    { key: 'checklist', title: '进实验室前检查', mark: '备', tone: 'emerald', items: b.checklist || [], empty: '未提取到准备清单。' },
    { key: 'formula', title: '核心公式与原理', mark: '式', tone: 'violet', items: b.formula || [], empty: '未提取到核心公式。' },
    { key: 'pitfalls', title: '最容易踩的坑', mark: '坑', tone: 'amber', items: b.pitfalls || [], empty: '未提取到易错点。' }
  ]
})

watch(() => props.experimentCode, () => {
  brief.value = null
  rawText.value = ''
  marked.value = false
})

async function generate() {
  brief.value = null
  rawText.value = ''
  try {
    const data = await invoke('brief', {
      experimentName: props.experimentName,
      knowledge: props.knowledge.join('\n')
    })
    rawText.value = data.text || ''
    brief.value = extractBriefSections(parseStructuredData(data), rawText.value)
  } catch {
    // error 已由 composable 记录
  }
}

async function markDone() {
  if (!props.experimentCode) return
  try {
    await studentExperimentApi.completePreLab(props.experimentCode)
    marked.value = true
  } catch {
    marked.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.err-text { @apply text-[13px] text-rose-600; }
.empty-card { @apply rounded-2xl border border-dashed border-line-soft bg-white p-10 text-center; }
.empty-title { @apply text-[15px] font-semibold text-ink-strong; }
.empty-desc { @apply text-[13px] text-ink-muted mt-1.5; }

.brief-grid { @apply grid grid-cols-1 md:grid-cols-2 gap-4; }
.brief-card { @apply rounded-2xl border border-line-soft bg-white p-4; }
.brief-head { @apply flex items-center gap-2 mb-3; }
.brief-icon { @apply w-7 h-7 rounded-lg flex items-center justify-center text-[12px] font-bold text-white; }
.brief-icon--sky { @apply bg-sky-500; }
.brief-icon--emerald { @apply bg-emerald-500; }
.brief-icon--violet { @apply bg-violet-500; }
.brief-icon--amber { @apply bg-amber-500; }
.brief-title { @apply text-[14.5px] font-bold text-ink-strong; }
.bullet-list { @apply space-y-1.5 text-[13.5px] text-ink-base leading-relaxed list-disc pl-5; }
.brief-empty { @apply text-[13px] text-ink-faint; }

.raw-box { @apply rounded-2xl border border-line-soft bg-white p-4 text-[13px] text-ink-muted; }
.raw-box summary { @apply cursor-pointer font-medium; }

.next-card { @apply flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-brand-50 border border-brand-100 p-5; }
.next-title { @apply text-[15px] font-bold text-ink-strong; }
.next-desc { @apply text-[13px] text-ink-muted mt-0.5; }
</style>
