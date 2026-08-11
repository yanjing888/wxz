<template>
  <StagePanel layout="single" title="预习自测" desc="3 道单选 + 2 道判断，提交后立即判分并给出解析。">
    <template #actions>
      <button
        type="button"
        class="btn-brand px-4 py-1.5 rounded-lg text-[13px] font-semibold"
        :disabled="loading || !experimentCode"
        @click="generate"
      >
        {{ loading ? '出题中…' : questions.length ? '换一套题' : '开始自测' }}
      </button>
    </template>

    <p v-if="error" class="err-text">{{ error }}</p>
    <div v-if="fallbackText" class="fallback-card">
      <p class="fallback-title">AI 未返回结构化题目，以下为原始输出</p>
      <div class="chat-md mt-2" v-html="renderMd(fallbackText)" />
    </div>

    <div v-if="!questions.length && !loading && !fallbackText" class="empty-card">
      <p class="empty-title">检验一下预习效果</p>
      <p class="empty-desc">题目围绕本实验的原理、仪器与操作要点生成，做错的题会给出解析。</p>
    </div>

    <template v-if="questions.length">
      <section v-if="submitted" class="score-card" :class="scoreTone">
        <div>
          <p class="score-num">{{ correctCount }} / {{ gradableCount }}</p>
          <p class="score-label">{{ scoreLabel }}</p>
        </div>
        <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" @click="retry">再做一遍</button>
      </section>

      <article
        v-for="(q, qi) in questions"
        :key="q.id"
        class="quiz-card"
        :class="submitted ? (isCorrect(qi) ? 'quiz-card--right' : 'quiz-card--wrong') : ''"
      >
        <div class="quiz-head">
          <span class="quiz-no">{{ qi + 1 }}</span>
          <p class="quiz-stem">{{ q.stem }}</p>
          <span class="quiz-type">{{ q.type === 'judge' ? '判断' : '单选' }}</span>
        </div>

        <div class="option-list">
          <label v-for="(opt, oi) in q.options" :key="oi" class="option-row" :class="optionClass(qi, oi)">
            <input
              type="radio"
              :name="`q-${q.id}`"
              :value="oi"
              :checked="answers[qi] === oi"
              :disabled="submitted"
              @change="answers[qi] = oi"
            />
            <span class="option-key">{{ String.fromCharCode(65 + oi) }}</span>
            <span class="option-text">{{ opt }}</span>
          </label>
        </div>

        <div v-if="submitted && q.explain" class="explain-box">
          <span class="explain-tag">解析</span>
          <p class="explain-text">{{ q.explain }}</p>
        </div>
        <p v-else-if="submitted && q.answerIndex < 0" class="explain-text mt-3">
          本题未返回标准答案，无法自动判分。
        </p>
      </article>

      <div v-if="!submitted" class="submit-row">
        <p class="submit-hint">已作答 {{ answeredCount }} / {{ questions.length }}</p>
        <button
          type="button"
          class="btn-brand px-6 py-2.5 rounded-xl text-sm font-semibold"
          :disabled="answeredCount === 0"
          @click="submitted = true"
        >
          提交判分
        </button>
      </div>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { extractQuestions, parseStructuredData } from '../../utils/aiTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' }
})

const { loading, error, invoke } = useAgentTool('practice-quiz', () => props.experimentCode)

const questions = ref([])
const answers = ref([])
const submitted = ref(false)
const fallbackText = ref('')

const answeredCount = computed(() => answers.value.filter((a) => a != null).length)
const gradableCount = computed(() => questions.value.filter((q) => q.answerIndex >= 0).length)
const correctCount = computed(
  () => questions.value.filter((q, i) => q.answerIndex >= 0 && answers.value[i] === q.answerIndex).length
)
const scoreTone = computed(() => {
  if (!gradableCount.value) return 'score-card--warn'
  const rate = correctCount.value / gradableCount.value
  if (rate >= 0.8) return 'score-card--ok'
  if (rate >= 0.6) return 'score-card--warn'
  return 'score-card--bad'
})
const scoreLabel = computed(() => {
  if (!gradableCount.value) return '本套题未返回标准答案'
  const rate = correctCount.value / gradableCount.value
  if (rate >= 0.8) return '准备充分，可以进实验室了'
  if (rate >= 0.6) return '基本掌握，错题对应的要点再看一遍'
  return '建议先回到「预习要点」把内容过一遍'
})

watch(() => props.experimentCode, reset)

function reset() {
  questions.value = []
  answers.value = []
  submitted.value = false
  fallbackText.value = ''
}

async function generate() {
  reset()
  try {
    const data = await invoke('quiz', { experimentName: props.experimentName, count: 5 })
    questions.value = extractQuestions(parseStructuredData(data))
    answers.value = questions.value.map(() => null)
    if (!questions.value.length) fallbackText.value = data.text || ''
  } catch {
    // error 已由 composable 记录
  }
}

function retry() {
  answers.value = questions.value.map(() => null)
  submitted.value = false
}

function isCorrect(qi) {
  const q = questions.value[qi]
  return q.answerIndex >= 0 && answers.value[qi] === q.answerIndex
}

function optionClass(qi, oi) {
  if (!submitted.value) return answers.value[qi] === oi ? 'option-row--picked' : ''
  const q = questions.value[qi]
  if (q.answerIndex === oi) return 'option-row--right'
  if (answers.value[qi] === oi) return 'option-row--wrong'
  return ''
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.err-text { @apply text-[13px] text-rose-600; }
.empty-card { @apply rounded-2xl border border-dashed border-line-soft bg-white p-10 text-center; }
.empty-title { @apply text-[15px] font-semibold text-ink-strong; }
.empty-desc { @apply text-[13px] text-ink-muted mt-1.5 max-w-md mx-auto; }
.fallback-card { @apply rounded-2xl border border-amber-100 bg-amber-50/50 p-4; }
.fallback-title { @apply text-[13px] font-semibold text-amber-800; }

.score-card { @apply flex flex-wrap items-center justify-between gap-4 rounded-2xl border p-5; }
.score-card--ok { @apply bg-emerald-50 border-emerald-100; }
.score-card--warn { @apply bg-amber-50 border-amber-100; }
.score-card--bad { @apply bg-rose-50 border-rose-100; }
.score-num { @apply text-[26px] font-bold text-ink-strong leading-none; }
.score-label { @apply text-[13px] text-ink-muted mt-1.5; }

.quiz-card { @apply rounded-2xl border border-line-soft bg-white p-5; }
.quiz-card--right { @apply border-emerald-200; }
.quiz-card--wrong { @apply border-rose-200; }
.quiz-head { @apply flex items-start gap-3 mb-3; }
.quiz-no {
  @apply w-6 h-6 rounded-lg bg-surface-muted text-ink-muted text-[12px] font-bold
    flex items-center justify-center shrink-0 mt-0.5;
}
.quiz-stem { @apply flex-1 min-w-0 text-[14.5px] font-semibold text-ink-strong leading-relaxed; }
.quiz-type { @apply shrink-0 text-[11px] px-2 py-0.5 rounded bg-surface-soft text-ink-faint; }

.option-list { @apply space-y-2; }
.option-row {
  @apply flex items-center gap-2.5 rounded-xl border border-line-soft px-3 py-2.5 cursor-pointer
    text-[14px] text-ink-base transition-colors hover:border-brand-200;
}
.option-row--picked { @apply border-brand-400 bg-brand-50; }
.option-row--right { @apply border-emerald-300 bg-emerald-50; }
.option-row--wrong { @apply border-rose-300 bg-rose-50; }
.option-key { @apply text-[12px] font-bold text-ink-faint w-4 shrink-0; }
.option-text { @apply min-w-0; }

.explain-box { @apply flex gap-2 mt-3 pt-3 border-t border-line-soft; }
.explain-tag {
  @apply shrink-0 h-5 px-2 rounded text-[11px] font-bold bg-brand-50 text-brand-600 flex items-center mt-0.5;
}
.explain-text { @apply text-[13px] text-ink-muted leading-relaxed; }

.submit-row { @apply flex items-center justify-between gap-4 rounded-2xl border border-line-soft bg-white p-4; }
.submit-hint { @apply text-[13px] text-ink-muted; }
</style>
