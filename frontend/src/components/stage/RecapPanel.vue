<template>
  <StagePanel layout="single" title="实验复盘" desc="问题清单来自你本次实验的真实纠错与校验记录，不是通用说教。">
    <div v-if="!sessionId" class="empty-state">
      还没有本实验的记录。请先到「实验操作」完成一次实验。
    </div>
    <div v-else-if="loadingReport" class="empty-state">加载实验记录…</div>

    <template v-else-if="report">
      <section class="stats-row">
        <div class="stat-card">
          <p class="stat-val">{{ report.helpCount ?? 0 }}</p>
          <p class="stat-label">问答/纠错</p>
        </div>
        <div class="stat-card">
          <p class="stat-val text-rose-500">{{ report.errorPointCount ?? 0 }}</p>
          <p class="stat-label">纠错标注</p>
        </div>
        <div class="stat-card">
          <p class="stat-val text-emerald-600">{{ report.tutViewCount ?? 0 }}</p>
          <p class="stat-label">教程查阅</p>
        </div>
        <div class="stat-card">
          <p class="stat-val">{{ dataEntryCount }}</p>
          <p class="stat-label">数据提交</p>
        </div>
      </section>

      <section class="panel">
        <h3 class="panel-title">发现的问题</h3>
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

      <section v-if="report.dataLogEntries?.length" class="panel">
        <h3 class="panel-title">数据提交回顾</h3>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>步骤</th><th>数据</th><th>校验</th></tr></thead>
            <tbody>
              <tr v-for="(row, i) in report.dataLogEntries" :key="i">
                <td>{{ row.stepTitle }}</td>
                <td>{{ row.valuesSummary }}</td>
                <td :class="{ 'text-rose-600': row.validationSummary && row.validationSummary !== '通过' }">
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
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { aiToolApi, sessionApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'
import { extractRecapIssues } from '../../utils/sessionReport'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessionId: { type: [Number, String], default: null }
})

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
    tips.push('进实验室前用「课前预习 → 预习要点」再过一遍关键步骤，减少课中查教程。')
  }
  if (dataEntryCount.value === 0) {
    tips.push('下次记得在实验操作各测量步骤提交数据，便于数据处理与报告撰写。')
  } else {
    tips.push('在「数据处理 → 计算与作图」中完成不确定度计算，并将结果写入报告。')
  }
  if ((report.value?.helpCount ?? 0) >= 5) {
    tips.push('整理本次提问较多的环节，写入报告「讨论」段作为误差来源分析。')
  }
  if (!tips.length) {
    tips.push('保持当前操作习惯，可尝试拓展实验参数加深理解。')
  }
  return tips
})

watch(() => props.sessionId, loadReport, { immediate: true })

async function loadReport() {
  aiText.value = ''
  report.value = null
  if (!props.sessionId) return
  loadingReport.value = true
  try {
    const { data } = await sessionApi.report(props.sessionId)
    report.value = data
  } catch {
    report.value = null
  } finally {
    loadingReport.value = false
  }
}

async function runAiRecap() {
  aiLoading.value = true
  aiText.value = ''
  try {
    const issueSummary = issues.value.map((i) => `- [${i.tag}] ${i.title}：${i.detail}`).join('\n')
    const { data } = await aiToolApi.invoke('lab-recap', {
      action: 'recap',
      inputs: {
        sessionId: Number(props.sessionId),
        experimentCode: props.experimentCode,
        experimentName: props.experimentName,
        issueSummary,
        improvementTips: improvementTips.value.join('\n')
      }
    })
    aiText.value = data.text || ''
  } finally {
    aiLoading.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.empty-state { @apply text-center text-sm text-ink-muted py-16; }

.stats-row { @apply grid grid-cols-2 sm:grid-cols-4 gap-3; }
.stat-card { @apply rounded-xl border border-line-soft bg-white p-4 text-center; }
.stat-val { @apply text-2xl font-black text-ink-strong; }
.stat-label { @apply text-[11px] text-ink-faint mt-1 font-semibold; }

.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.panel-ai { @apply bg-violet-50/40 border-violet-100; }
.panel-title { @apply text-[15px] font-bold text-ink-strong mb-4; }

.issue-list { @apply space-y-3; }
.issue-item { @apply rounded-xl border p-4; }
.issue-item--high { @apply border-rose-100 bg-rose-50/60; }
.issue-item--medium { @apply border-amber-100 bg-amber-50/50; }
.issue-item--low { @apply border-sky-100 bg-sky-50/40; }
.issue-tag { @apply text-[10px] font-bold px-2 py-0.5 rounded bg-white border border-line-soft text-ink-muted; }
.issue-title { @apply text-[14px] font-semibold text-ink-strong mt-2; }
.issue-detail { @apply text-[13px] text-ink-muted mt-1 leading-relaxed; }

.success-box { @apply flex items-center gap-3 p-4 rounded-xl bg-emerald-50 border border-emerald-100 text-emerald-800 text-sm; }
.success-icon { @apply w-8 h-8 rounded-lg bg-emerald-100 flex items-center justify-center font-bold; }

.table-wrap { @apply overflow-x-auto; }
.data-table { @apply w-full text-[13px]; }
.data-table th { @apply px-3 py-2 bg-surface-soft text-left text-ink-muted font-semibold; }
.data-table td { @apply px-3 py-2 border-t border-line-soft; }

.todo-list { @apply space-y-2 text-[14px] text-ink-base list-disc pl-5; }
</style>
