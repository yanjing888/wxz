<template>
  <AgentWorkspace
    :agent="agent"
    :tool="tool"
    v-model:experiment="experimentCode"
    layout="single"
    @experiment-change="onExperimentChange"
  >
    <section class="panel">
      <label class="field-label">选择已完成的实验</label>
      <select v-model="sessionId" class="field-input" @change="loadReport">
        <option value="">请选择</option>
        <option v-for="item in sessions" :key="item.sessionId" :value="item.sessionId">
          {{ item.experimentName }} · {{ formatTime(item.endTime) }}
        </option>
      </select>
      <p v-if="!sessions.length" class="empty-tip">还没有已完成的实验，请先在实验工作台完成一次实验并生成报告。</p>
    </section>

    <div v-if="!sessionId" class="empty-state">
      <p>选择实验会话后，系统将展示操作与数据问题清单。</p>
    </div>

    <div v-else-if="loadingReport" class="empty-state">加载实验记录…</div>

    <template v-else>
      <section class="stats-row">
        <div class="stat-card">
          <p class="stat-val">{{ report?.helpCount ?? 0 }}</p>
          <p class="stat-label">问答/纠错</p>
        </div>
        <div class="stat-card">
          <p class="stat-val text-red-500">{{ report?.errorPointCount ?? 0 }}</p>
          <p class="stat-label">纠错标注</p>
        </div>
        <div class="stat-card">
          <p class="stat-val text-emerald-600">{{ report?.tutViewCount ?? 0 }}</p>
          <p class="stat-label">教程查阅</p>
        </div>
        <div class="stat-card">
          <p class="stat-val">{{ dataEntryCount }}</p>
          <p class="stat-label">数据提交</p>
        </div>
      </section>

      <section class="panel">
        <h3 class="panel-title">发现的问题（来自实验记录）</h3>
        <ul v-if="issues.length" class="issue-list">
          <li v-for="(issue, i) in issues" :key="i" class="issue-item" :class="`issue-item--${issue.severity}`">
            <span class="issue-tag">{{ issue.tag }}</span>
            <p class="issue-title">{{ issue.title }}</p>
            <p class="issue-detail">{{ issue.detail }}</p>
          </li>
        </ul>
        <div v-else class="success-box">
          <span class="success-icon">✓</span>
          <p>本次实验未发现明显操作或数据校验问题，表现良好。</p>
        </div>
      </section>

      <section v-if="report?.corrections?.length" class="panel">
        <h3 class="panel-title">操作纠错记录（{{ report.corrections.length }} 处）</h3>
        <div class="space-y-2">
          <div v-for="(c, i) in report.corrections" :key="i" class="corr-card">
            <div class="flex justify-between gap-2 mb-1">
              <span class="text-[12px] font-bold text-ink-strong">{{ c.stepTitle }}</span>
              <span class="corr-type">{{ c.errorType }}</span>
            </div>
            <p class="text-[13px] text-ink-base leading-relaxed">{{ c.detail || c.feedback }}</p>
          </div>
        </div>
      </section>

      <section v-if="report?.dataLogEntries?.length" class="panel">
        <h3 class="panel-title">数据提交回顾</h3>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>步骤</th><th>数据</th><th>校验</th></tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in report.dataLogEntries" :key="i">
                <td>{{ row.stepTitle }}</td>
                <td>{{ row.valuesSummary }}</td>
                <td :class="{ 'text-red-600': row.validationSummary && row.validationSummary !== '通过' }">
                  {{ row.validationSummary || '—' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel">
        <h3 class="panel-title">下次实验可以改进</h3>
        <ul class="todo-list">
          <li v-for="(tip, i) in improvementTips" :key="i">{{ tip }}</li>
        </ul>
      </section>

      <section class="panel panel-ai">
        <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
          <h3 class="panel-title mb-0">AI 深度复盘</h3>
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="aiLoading" @click="runAiRecap">
            {{ aiLoading ? '生成中…' : '生成改进计划' }}
          </button>
        </div>
        <p class="text-[13px] text-ink-muted mb-3">在上方问题清单基础上，AI 帮你整理学习要点与练习建议。</p>
        <div v-if="aiText" class="chat-md" v-html="renderMd(aiText)" />
      </section>
    </template>
  </AgentWorkspace>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AgentWorkspace from '../../components/ai/AgentWorkspace.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { aiToolApi, sessionApi } from '../../api'
import { formatSessionTime } from '../../utils/studentFlow'
import { renderChatMarkdown } from '../../utils/markdown'
import { extractRecapIssues } from '../../utils/sessionReport'

const props = defineProps({
  tool: { type: Object, required: true },
  agent: { type: Object, default: null }
})

const { experimentCode } = useAgentTool(props.tool.code)

const sessions = ref([])
const sessionId = ref('')
const report = ref(null)
const loadingReport = ref(false)
const aiLoading = ref(false)
const aiText = ref('')

const issues = computed(() => extractRecapIssues(report.value))
const dataEntryCount = computed(() => report.value?.dataLogEntries?.length ?? 0)

const improvementTips = computed(() => {
  const tips = []
  if ((report.value?.corrections?.length ?? 0) > 0) {
    tips.push('重做实验前，针对上方纠错记录逐步对照操作教程练习。')
  }
  if ((report.value?.tutViewCount ?? 0) >= 2) {
    tips.push('进实验室前用「课前要点」再过一遍关键步骤，减少课中查教程。')
  }
  if (dataEntryCount.value === 0) {
    tips.push('下次记得在实验工作台各测量步骤提交数据，便于数据处理与报告撰写。')
  } else {
    tips.push('在「数据处理」中完成不确定度计算，并将结果写入报告。')
  }
  if ((report.value?.helpCount ?? 0) >= 5) {
    tips.push('整理本次提问较多的环节，写入报告「讨论」段作为误差来源分析。')
  }
  if (tips.length === 0) {
    tips.push('保持当前操作习惯，可尝试拓展实验参数加深理解。')
  }
  return tips
})

onMounted(async () => {
  await loadSessions()
  preselectSession()
})

watch(experimentCode, () => {
  preselectSession(true)
})

async function loadSessions() {
  const { data } = await aiToolApi.recapSessions()
  sessions.value = data || []
}

function onExperimentChange() {
  sessionId.value = ''
  report.value = null
  aiText.value = ''
  preselectSession(true)
}

function preselectSession(force = false) {
  if (!force && sessionId.value) return
  const exp = experimentCode.value
  const hit = exp
    ? sessions.value.find((s) => s.experimentCode === exp)
    : sessions.value[0]
  if (hit) {
    sessionId.value = hit.sessionId
    loadReport()
  }
}

async function loadReport() {
  aiText.value = ''
  report.value = null
  if (!sessionId.value) return
  loadingReport.value = true
  try {
    const { data } = await sessionApi.report(sessionId.value)
    report.value = data
  } finally {
    loadingReport.value = false
  }
}

async function runAiRecap() {
  aiLoading.value = true
  aiText.value = ''
  try {
    const issueSummary = issues.value.map((i) => `- [${i.tag}] ${i.title}：${i.detail}`).join('\n')
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'recap',
      inputs: {
        sessionId: Number(sessionId.value),
        issueSummary,
        improvementTips: improvementTips.value.join('\n')
      }
    })
    aiText.value = data.text || ''
  } finally {
    aiLoading.value = false
  }
}

function formatTime(value) {
  return formatSessionTime(value)
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.field-label { @apply block text-[13px] font-semibold text-ink-muted mb-2; }
.field-input { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[14px]; }
.empty-tip { @apply text-[13px] text-ink-faint mt-2; }
.empty-state { @apply text-center text-sm text-ink-muted py-10; }

.stats-row { @apply grid grid-cols-2 sm:grid-cols-4 gap-3; }
.stat-card { @apply rounded-xl border border-line-soft bg-white p-4 text-center; }
.stat-val { @apply text-2xl font-black text-ink-strong; }
.stat-label { @apply text-[11px] text-ink-faint mt-1 font-semibold uppercase; }

.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.panel-ai { @apply bg-violet-50/40 border-violet-100; }
.panel-title { @apply text-[15px] font-bold text-ink-strong mb-4; }

.issue-list { @apply space-y-3; }
.issue-item { @apply rounded-xl border p-4; }
.issue-item--high { @apply border-red-100 bg-red-50/60; }
.issue-item--medium { @apply border-amber-100 bg-amber-50/50; }
.issue-item--low { @apply border-blue-100 bg-blue-50/40; }
.issue-tag { @apply text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-white border border-line-soft text-ink-muted; }
.issue-title { @apply text-[14px] font-semibold text-ink-strong mt-2; }
.issue-detail { @apply text-[13px] text-ink-muted mt-1 leading-relaxed; }

.success-box { @apply flex items-center gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-100 text-emerald-800 text-sm; }
.success-icon { @apply w-8 h-8 rounded-lg bg-emerald-100 flex items-center justify-center font-bold; }

.corr-card { @apply p-3 rounded-xl bg-red-50/50 border border-red-100; }
.corr-type { @apply text-[10px] px-2 py-0.5 rounded bg-white text-red-600 border border-red-100; }

.table-wrap { @apply overflow-x-auto; }
.data-table { @apply w-full text-[13px]; }
.data-table th { @apply px-3 py-2 bg-surface-soft text-left text-ink-muted font-semibold; }
.data-table td { @apply px-3 py-2 border-t border-line-soft; }

.todo-list { @apply space-y-2 text-[14px] text-ink-base list-disc pl-5; }
</style>
