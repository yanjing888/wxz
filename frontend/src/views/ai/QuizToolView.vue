<template>
  <AiToolShell :tool="tool">
    <div class="tool-page custom-scroll">
      <section class="panel">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label class="field-label">实验</label>
            <select v-model="experimentCode" class="field-input">
              <option value="">请选择</option>
              <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
            </select>
          </div>
          <div>
            <label class="field-label">题量</label>
            <select v-model="count" class="field-input">
              <option :value="5">5 题</option>
              <option :value="8">8 题</option>
              <option :value="10">10 题</option>
            </select>
          </div>
          <div class="flex items-end">
            <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold w-full" :disabled="loading || !experimentCode" @click="generate">
              {{ loading ? '出题中…' : '生成试题' }}
            </button>
          </div>
        </div>
      </section>

      <section v-if="questions.length" class="panel mt-5 space-y-4">
        <div v-for="question in questions" :key="question.id" class="quiz-item">
          <p class="quiz-stem">{{ question.id }}. {{ question.stem }}</p>
          <div v-if="question.type === 'choice'" class="space-y-2 mt-2">
            <label v-for="(opt, idx) in question.options" :key="idx" class="choice-row">
              <input v-model="answers[question.id]" type="radio" :value="String.fromCharCode(65 + idx)" />
              <span>{{ String.fromCharCode(65 + idx) }}. {{ opt }}</span>
            </label>
          </div>
          <input v-else v-model="answers[question.id]" class="field-input mt-2" />
        </div>
        <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="grading" @click="grade">
          {{ grading ? '判分中…' : '提交判分' }}
        </button>
      </section>

      <section v-if="resultText" class="panel mt-5 result-panel">
        <div class="chat-md" v-html="renderMd(resultText)" />
      </section>
    </div>
  </AiToolShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AiToolShell from '../../components/ai/AiToolShell.vue'
import { aiToolApi, experimentApi } from '../../api'
import { extractQuestions, parseStructuredData } from '../../utils/aiTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({ tool: { type: Object, required: true } })

const experiments = ref([])
const experimentCode = ref('')
const count = ref(5)
const questions = ref([])
const answers = ref({})
const loading = ref(false)
const grading = ref(false)
const resultText = ref('')

onMounted(async () => {
  const { data } = await experimentApi.list()
  experiments.value = data || []
})

async function generate() {
  loading.value = true
  resultText.value = ''
  questions.value = []
  answers.value = {}
  try {
    const exp = experiments.value.find((item) => item.code === experimentCode.value)
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'generate',
      inputs: { experimentCode: experimentCode.value, experimentName: exp?.name || '', count: count.value }
    })
    const structured = parseStructuredData(data)
    questions.value = extractQuestions(structured)
    if (!questions.value.length) resultText.value = data.text || ''
  } finally {
    loading.value = false
  }
}

async function grade() {
  grading.value = true
  try {
    const exp = experiments.value.find((item) => item.code === experimentCode.value)
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'grade',
      inputs: {
        experimentCode: experimentCode.value,
        experimentName: exp?.name || '',
        questions: questions.value,
        answers: answers.value
      }
    })
    resultText.value = data.text || ''
  } finally {
    grading.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.tool-page { @apply max-w-[1080px] mx-auto w-full px-6 py-6 overflow-y-auto h-full; }
.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.field-label { @apply block text-[13px] font-semibold text-ink-muted mb-2; }
.field-input { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.quiz-item { @apply rounded-xl border border-line-soft p-4; }
.quiz-stem { @apply text-[14px] font-semibold text-ink-strong; }
.choice-row { @apply flex items-center gap-2 text-[14px] text-ink-base; }
.result-panel { @apply bg-surface-soft/40; }
</style>
