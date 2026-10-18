<template>
  <div class="student-shell min-h-screen bg-bg-base flex flex-col">
    <StudentShellHeader />

    <main class="flex-1 min-h-0 overflow-y-auto custom-scroll">
      <div v-if="loading" class="py-20 text-center text-sm text-ink-faint">加载中…</div>
      <div v-else-if="error" class="py-20 text-center text-sm text-red-600">{{ error }}</div>

      <div v-else-if="progress" class="hub-wrap py-8">
        <div class="hub-head">
          <button type="button" class="back-link" @click="router.push('/')">← 我的实验</button>
          <div class="hub-title-row">
            <div>
              <h1 class="hub-title">{{ progress.experimentName }}</h1>
              <p class="hub-desc">预习、实验、数据、报告与复盘可并行使用，按你的节奏完成实验闭环。</p>
            </div>
            <span class="progress-badge">{{ doneCount }}/{{ progress.steps.length }} 已完成</span>
          </div>
          <ExperimentStepper
            :steps="progress.steps"
            :current="currentStep"
            class="mt-5"
            @select="goStep"
          />
        </div>

        <section class="hub-panel">
          <!-- 预习 -->
          <template v-if="currentStep === 'pre-lab'">
            <div class="panel-actions">
              <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="preLabLoading" @click="generatePreLab">
                {{ preLabLoading ? '生成中…' : '生成预习任务' }}
              </button>
              <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" @click="markPreLabDone">标记预习完成</button>
            </div>
            <ul v-if="preLabChecklist.length" class="check-list mt-5">
              <li v-for="(item, index) in preLabChecklist" :key="index">{{ item }}</li>
            </ul>
            <div v-if="preLabQuestions.length" class="space-y-4 mt-5">
              <div v-for="q in preLabQuestions" :key="q.id" class="quiz-box">
                <p class="font-semibold text-[14px]">{{ q.id }}. {{ q.stem }}</p>
                <textarea v-model="preLabAnswers[q.id]" class="field-textarea mt-2" rows="2" />
              </div>
              <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="preLabGrading" @click="gradePreLab">
                {{ preLabGrading ? '批改中…' : '提交自测' }}
              </button>
            </div>
            <div v-if="preLabResult" class="result-box mt-5" v-html="renderMd(preLabResult)" />
          </template>

          <!-- 实验 -->
          <template v-else-if="currentStep === 'lab'">
            <p class="panel-lead">进入实验工作台：按步骤操作，拍照纠错、仪器采集与问答在同一界面完成。</p>
            <div class="lab-actions">
              <button type="button" class="btn-brand px-5 py-3 rounded-xl text-sm font-semibold" @click="enterLab(false)">
                {{ progress.activeSessionId ? '继续实验' : '开始实验' }}
              </button>
              <button v-if="progress.activeSessionId" type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" @click="enterLab(true)">
                重新开始
              </button>
            </div>
            <p v-if="progress.activeSessionId" class="hint mt-4">你有进行中的实验会话，可随时继续。</p>
          </template>

          <!-- 数据 -->
          <template v-else-if="currentStep === 'data'">
            <p v-if="!progress.dataCollectionEnabled" class="panel-lead">本实验无需单独数据分析环节。</p>
            <template v-else>
              <p class="panel-lead">粘贴测量数据，进行误差分析或异常判断；也可在实验台步骤中直接提交数据。</p>
              <p v-if="progress.dataSubmitted" class="hint mb-4">已检测到实验台提交的数据。</p>
              <select v-model="analysisType" class="field-input mb-3 max-w-xs">
                <option value="error">误差分析</option>
                <option value="trend">趋势判断</option>
                <option value="outlier">异常点检测</option>
              </select>
              <textarea v-model="dataText" class="field-textarea" rows="8" placeholder="粘贴实验数据…" />
              <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold mt-4" :disabled="dataLoading || !dataText.trim()" @click="analyzeData">
                {{ dataLoading ? '分析中…' : '分析数据' }}
              </button>
              <div v-if="dataResult" class="result-box mt-5" v-html="renderMd(dataResult)" />
            </template>
          </template>

          <!-- 报告 -->
          <template v-else-if="currentStep === 'report'">
            <p class="panel-lead">可将各段报告内容粘贴到下方获取修改建议；也可先在实验台生成报告再回来批改。</p>
            <p v-if="progress.finishedSessionId" class="hint mb-4">已检测到完成的实验会话，报告已在工作台生成。</p>
            <div class="space-y-3">
              <div v-for="section in reportSections" :key="section.key">
                <label class="field-label">{{ section.label }}</label>
                <textarea v-model="reportForm[section.key]" class="field-textarea" rows="3" />
              </div>
            </div>
            <div class="panel-actions mt-4">
              <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="reportLoading" @click="reviewReport">
                {{ reportLoading ? '批改中…' : '获取报告建议' }}
              </button>
              <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" @click="enterLab(false)">去实验工作台</button>
            </div>
            <div v-if="reportResult" class="result-box mt-5" v-html="renderMd(reportResult)" />
          </template>

          <!-- 复盘 -->
          <template v-else-if="currentStep === 'recap'">
            <p class="panel-lead">基于你的实验记录（问答次数、纠错点等）生成本次实验复盘。</p>
            <p v-if="!progress.finishedSessionId" class="hint mb-3">尚无完成的实验会话，复盘内容可能较少；可先完成实验或直接尝试生成。</p>
            <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="recapLoading" @click="generateRecap">
              {{ recapLoading ? '生成中…' : '生成复盘' }}
            </button>
            <div v-if="recapResult" class="result-box mt-5" v-html="renderMd(recapResult)" />
          </template>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StudentShellHeader from '../components/layout/StudentShellHeader.vue'
import ExperimentStepper from '../components/experiment/ExperimentStepper.vue'
import { aiToolApi, studentExperimentApi } from '../api'
import { extractChecklist, extractQuestions, parseStructuredData } from '../utils/aiTool'
import { renderChatMarkdown } from '../utils/markdown'

const route = useRoute()
const router = useRouter()

const experimentCode = computed(() => route.params.code)
const progress = ref(null)
const loading = ref(true)
const error = ref('')

const currentStep = ref('lab')

const preLabLoading = ref(false)
const preLabGrading = ref(false)
const preLabChecklist = ref([])
const preLabQuestions = ref([])
const preLabAnswers = ref({})
const preLabResult = ref('')

const analysisType = ref('error')
const dataText = ref('')
const dataLoading = ref(false)
const dataResult = ref('')

const reportSections = [
  { key: 'purpose', label: '实验目的' },
  { key: 'principle', label: '实验原理' },
  { key: 'data', label: '数据与处理' },
  { key: 'conclusion', label: '实验结论' },
  { key: 'discussion', label: '讨论与拓展' }
]
const reportForm = reactive({ purpose: '', principle: '', data: '', conclusion: '', discussion: '' })
const reportLoading = ref(false)
const reportResult = ref('')

const recapLoading = ref(false)
const recapResult = ref('')

const doneCount = computed(() => progress.value?.steps?.filter((s) => s.status === 'done').length || 0)

const VALID_STEPS = ['pre-lab', 'lab', 'data', 'report', 'recap']

async function loadProgress() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await studentExperimentApi.getProgress(experimentCode.value)
    progress.value = data
    syncCurrentStep()
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function syncCurrentStep() {
  const queryStep = route.query.step
  if (queryStep && VALID_STEPS.includes(String(queryStep))) {
    currentStep.value = String(queryStep)
    return
  }
  currentStep.value = progress.value?.activeSessionId ? 'lab' : 'lab'
}

function goStep(step) {
  currentStep.value = step
  router.replace({ path: `/experiment/${experimentCode.value}`, query: { step } })
}

function enterLab(restart) {
  const query = { exp: experimentCode.value }
  if (restart) query.restart = '1'
  router.push({ path: '/lab', query })
}

async function generatePreLab() {
  preLabLoading.value = true
  preLabResult.value = ''
  try {
    const { data } = await aiToolApi.invoke('pre-lab', {
      action: 'generate',
      inputs: {
        experimentCode: experimentCode.value,
        experimentName: progress.value?.experimentName || ''
      }
    })
    const structured = parseStructuredData(data)
    preLabChecklist.value = extractChecklist(structured)
    preLabQuestions.value = extractQuestions(structured)
    if (!preLabChecklist.value.length && !preLabQuestions.value.length) {
      preLabResult.value = data.text || ''
    }
  } finally {
    preLabLoading.value = false
  }
}

async function gradePreLab() {
  preLabGrading.value = true
  try {
    if (preLabQuestions.value.length) {
      const { data } = await aiToolApi.invoke('pre-lab', {
        action: 'grade',
        inputs: {
          experimentCode: experimentCode.value,
          experimentName: progress.value?.experimentName || '',
          questions: preLabQuestions.value,
          answers: preLabAnswers.value
        }
      })
      preLabResult.value = data.text || ''
    }
    await studentExperimentApi.completePreLab(experimentCode.value)
    await loadProgress()
  } finally {
    preLabGrading.value = false
  }
}

async function markPreLabDone() {
  await studentExperimentApi.completePreLab(experimentCode.value)
  await loadProgress()
}

async function analyzeData() {
  dataLoading.value = true
  try {
    const { data } = await aiToolApi.invoke('data-lab', {
      action: 'analyze',
      inputs: {
        experimentCode: experimentCode.value,
        experimentName: progress.value?.experimentName || '',
        analysisType: analysisType.value,
        dataText: dataText.value
      }
    })
    dataResult.value = data.text || ''
  } finally {
    dataLoading.value = false
  }
}

async function reviewReport() {
  reportLoading.value = true
  try {
    const { data } = await aiToolApi.invoke('report-review', {
      action: 'review',
      inputs: { ...reportForm, experimentCode: experimentCode.value }
    })
    reportResult.value = data.text || ''
  } finally {
    reportLoading.value = false
  }
}

async function generateRecap() {
  recapLoading.value = true
  try {
    const { data } = await aiToolApi.invoke('lab-recap', {
      action: 'recap',
      inputs: {
        experimentCode: experimentCode.value,
        sessionId: progress.value?.finishedSessionId
      }
    })
    recapResult.value = data.text || ''
    await studentExperimentApi.completeRecap(experimentCode.value)
    await loadProgress()
  } finally {
    recapLoading.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}

onMounted(loadProgress)
watch(experimentCode, loadProgress)
watch(() => route.query.step, syncCurrentStep)
</script>

<style scoped>
.hub-wrap { @apply w-full max-w-[960px] mx-auto px-6; }
.hub-head { @apply mb-6; }
.back-link { @apply text-[13px] font-semibold text-brand-700 mb-3; }
.hub-title-row { @apply flex items-start justify-between gap-4; }
.hub-title { @apply text-[24px] font-bold text-ink-strong; }
.hub-desc { @apply mt-2 text-[14px] text-ink-muted; }
.progress-badge { @apply shrink-0 px-3 py-1.5 rounded-full bg-surface-soft text-[12px] font-semibold text-ink-muted; }
.hub-panel { @apply rounded-2xl border border-line-soft bg-white p-6; }
.panel-lead { @apply text-[14px] text-ink-base leading-relaxed; }
.panel-actions, .lab-actions { @apply flex flex-wrap gap-3 mt-4; }
.field-label { @apply block text-[13px] font-semibold text-ink-muted mb-2; }
.field-input { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.field-textarea { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.check-list { @apply space-y-2 text-[14px] list-disc pl-5 text-ink-base; }
.quiz-box { @apply rounded-xl border border-line-soft p-4; }
.result-box { @apply rounded-xl border border-line-soft bg-surface-soft/40 p-4 text-[14px]; }
.hint { @apply text-[13px] text-ink-faint; }
</style>
