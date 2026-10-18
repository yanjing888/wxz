<template>
  <AiToolShell :tool="tool">
    <div class="tool-page custom-scroll">
      <div class="panel-grid">
        <section class="panel">
          <label class="field-label">选择实验</label>
          <select v-model="experimentCode" class="field-input">
            <option value="">请选择</option>
            <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
          </select>
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold mt-4" :disabled="loading || !experimentCode" @click="generate">
            {{ loading ? '生成中…' : '生成预习任务' }}
          </button>
        </section>

        <section class="panel">
          <h3 class="panel-title">预习清单</h3>
          <ul v-if="checklist.length" class="check-list">
            <li v-for="(item, index) in checklist" :key="index">{{ item }}</li>
          </ul>
          <p v-else class="empty-tip">生成后将显示目标与注意事项</p>
        </section>
      </div>

      <section class="panel mt-5">
        <h3 class="panel-title">自测题</h3>
        <div v-if="questions.length" class="space-y-4">
          <div v-for="question in questions" :key="question.id" class="quiz-item">
            <p class="quiz-stem">{{ question.id }}. {{ question.stem }}</p>
            <div v-if="question.type === 'choice'" class="space-y-2 mt-2">
              <label v-for="(opt, idx) in question.options" :key="idx" class="choice-row">
                <input v-model="answers[question.id]" type="radio" :value="opt" />
                <span>{{ opt }}</span>
              </label>
            </div>
            <textarea v-else v-model="answers[question.id]" class="field-textarea mt-2" rows="2" />
          </div>
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="grading" @click="grade">
            {{ grading ? '批改中…' : '提交自测' }}
          </button>
        </div>
        <p v-else class="empty-tip">生成后将出现自测题</p>
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
import { extractChecklist, extractQuestions, parseStructuredData } from '../../utils/aiTool'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({ tool: { type: Object, required: true } })

const experiments = ref([])
const experimentCode = ref('')
const checklist = ref([])
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
  try {
    const exp = experiments.value.find((item) => item.code === experimentCode.value)
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'generate',
      inputs: { experimentCode: experimentCode.value, experimentName: exp?.name || '' }
    })
    const structured = parseStructuredData(data)
    checklist.value = extractChecklist(structured)
    questions.value = extractQuestions(structured)
    answers.value = {}
    if (!checklist.value.length && !questions.value.length) {
      resultText.value = data.text || ''
    }
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
.panel-grid { @apply grid grid-cols-1 lg:grid-cols-2 gap-5; }
.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.panel-title { @apply text-[15px] font-bold text-ink-strong mb-3; }
.field-label { @apply block text-[13px] font-semibold text-ink-muted mb-2; }
.field-input { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.field-textarea { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.check-list { @apply space-y-2 text-[14px] text-ink-base list-disc pl-5; }
.quiz-item { @apply rounded-xl border border-line-soft p-4; }
.quiz-stem { @apply text-[14px] font-semibold text-ink-strong; }
.choice-row { @apply flex items-center gap-2 text-[14px] text-ink-base; }
.empty-tip { @apply text-[13px] text-ink-faint; }
.result-panel { @apply bg-surface-soft/40; }
</style>
