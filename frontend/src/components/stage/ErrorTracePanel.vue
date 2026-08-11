<template>
  <StagePanel
    title="误差溯源"
    desc="读取本次实验每一步的提交数据、纠错记录与环境检查结果，反推偏差来自哪一步。"
    empty-title="选择实验会话开始溯源"
    empty-hint="这是本平台独有的能力——它看得到你实验过程中的每一次纠错和校验，而不是泛泛罗列误差来源。"
  >
    <template #input>
      <section>
        <label class="field-label">实验会话</label>
        <select v-model="localSessionId" class="field-input" @change="loadSession">
          <option value="">请选择</option>
          <option v-for="s in sessions" :key="s.id" :value="s.id">
            {{ formatSessionTime(s.endTime || s.startTime) }} · {{ s.status === 'FINISHED' ? '已完成' : '进行中' }}
          </option>
        </select>
        <p v-if="!sessions.length" class="field-hint">本实验暂无记录。请先到「实验操作」完成一次实验。</p>
      </section>

      <section>
        <label class="field-label">你遇到的问题</label>
        <textarea
          v-model="symptom"
          class="field-textarea"
          rows="3"
          placeholder="如：算出的钠光波长是 620nm，比理论值 589nm 大了不少"
        />
      </section>

      <section class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">理论 / 参考值</label>
          <input v-model="expectedValue" class="field-input" placeholder="589.3" />
        </div>
        <div>
          <label class="field-label">你的实测值</label>
          <input v-model="actualValue" class="field-input" placeholder="620.1" />
        </div>
      </section>

      <p v-if="deviationText" class="deviation-box">相对偏差 {{ deviationText }}</p>

      <button
        type="button"
        class="btn-brand w-full py-2.5 rounded-xl text-sm font-semibold"
        :disabled="loading || !localSessionId"
        @click="runTrace"
      >
        {{ loading ? '分析中…' : '开始溯源' }}
      </button>
      <p v-if="error" class="err-text">{{ error }}</p>
    </template>

    <template v-if="report" #result>
      <section class="evidence">
        <h3 class="section-title">本次实验的可用线索</h3>
        <div class="evidence-grid">
          <div class="ev-card">
            <span class="ev-num">{{ report.stepSummaries?.length || 0 }}</span>
            <span class="ev-label">操作步骤</span>
          </div>
          <div class="ev-card">
            <span class="ev-num">{{ report.dataLogEntries?.length || 0 }}</span>
            <span class="ev-label">数据提交</span>
          </div>
          <div class="ev-card" :class="{ 'ev-card--alert': (report.corrections?.length || 0) > 0 }">
            <span class="ev-num">{{ report.corrections?.length || 0 }}</span>
            <span class="ev-label">纠错记录</span>
          </div>
          <div class="ev-card" :class="{ 'ev-card--alert': failedChecks.length > 0 }">
            <span class="ev-num">{{ failedChecks.length }}</span>
            <span class="ev-label">校验未通过</span>
          </div>
        </div>

        <ul v-if="suspects.length" class="suspect-list">
          <li v-for="(s, i) in suspects" :key="i" class="suspect-item">
            <span class="suspect-tag">{{ s.tag }}</span>
            <div class="min-w-0">
              <p class="suspect-title">{{ s.title }}</p>
              <p class="suspect-detail">{{ s.detail }}</p>
            </div>
          </li>
        </ul>
        <p v-else class="field-hint">
          本次实验没有留下纠错或校验失败记录，偏差更可能来自读数习惯或仪器系统误差，AI 分析会侧重这两个方向。
        </p>
      </section>

      <section v-if="aiText" class="ai-section">
        <div class="flex items-center gap-2 mb-2">
          <h3 class="section-title mb-0">溯源分析</h3>
          <span v-if="!fromDify" class="warn-tag">AI 服务未接入</span>
        </div>
        <div class="chat-md" v-html="renderMd(aiText)" />
      </section>
      <p v-else-if="!loading" class="field-hint mt-4">
        填写左侧的问题描述后点击「开始溯源」，AI 会结合上述线索给出 2-3 个嫌疑步骤与验证方法。
      </p>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { sessionApi } from '../../api'
import { formatSessionTime } from '../../utils/studentFlow'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessions: { type: Array, default: () => [] },
  sessionId: { type: [Number, String], default: null }
})

const { loading, error, invoke } = useAgentTool('error-trace', () => props.experimentCode)

const localSessionId = ref('')
const report = ref(null)
const symptom = ref('')
const expectedValue = ref('')
const actualValue = ref('')
const aiText = ref('')
const fromDify = ref(true)

const deviationText = computed(() => {
  const e = Number(expectedValue.value)
  const a = Number(actualValue.value)
  if (!Number.isFinite(e) || !Number.isFinite(a) || e === 0) return ''
  const d = ((a - e) / Math.abs(e)) * 100
  return `${d > 0 ? '+' : ''}${d.toFixed(2)}%（实测${d > 0 ? '偏大' : '偏小'}）`
})

const failedChecks = computed(() =>
  (report.value?.dataLogEntries || []).filter((row) => {
    const v = row.validationSummary
    return v && v !== '通过' && v !== '—'
  })
)

const suspects = computed(() => {
  const list = []
  ;(report.value?.corrections || []).forEach((c) => {
    list.push({
      tag: '纠错',
      title: c.stepTitle || '操作纠错',
      detail: c.detail || c.feedback || '该步骤曾被判定为操作不规范。'
    })
  })
  failedChecks.value.forEach((row) => {
    list.push({ tag: '校验', title: row.stepTitle || '数据校验未通过', detail: row.validationSummary })
  })
  ;(report.value?.envLogs || []).forEach((log) => {
    if (log.level && log.level !== 'L0') {
      list.push({ tag: '环境', title: `环境告警 ${log.level}`, detail: log.summary || '实验台环境存在异常。' })
    }
  })
  return list
})

watch(
  () => [props.sessionId, props.sessions.length],
  () => {
    if (localSessionId.value) return
    const preferred = props.sessionId || props.sessions[0]?.id || ''
    if (preferred) {
      localSessionId.value = preferred
      loadSession()
    }
  },
  { immediate: true }
)

async function loadSession() {
  report.value = null
  aiText.value = ''
  if (!localSessionId.value) return
  try {
    const { data } = await sessionApi.report(localSessionId.value)
    report.value = data
  } catch {
    report.value = null
  }
}

async function runTrace() {
  aiText.value = ''
  try {
    const data = await invoke('trace', {
      sessionId: Number(localSessionId.value),
      experimentName: props.experimentName,
      symptom: symptom.value,
      expectedValue: expectedValue.value,
      actualValue: actualValue.value,
      deviation: deviationText.value,
      local_suspects: suspects.value.map((s) => `[${s.tag}] ${s.title}：${s.detail}`).join('\n')
    })
    aiText.value = data.text || ''
    fromDify.value = data.fromDify !== false
  } catch {
    // error 已由 composable 记录
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.field-label { @apply block text-[13px] font-bold text-ink-strong mb-2; }
.field-input { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.field-textarea { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.field-hint { @apply text-[12px] text-ink-faint mt-1.5 leading-relaxed; }
.err-text { @apply text-[12px] text-rose-600; }
.deviation-box {
  @apply rounded-lg bg-amber-50 border border-amber-100 px-3 py-2 text-[13px] font-semibold text-amber-800;
}

.section-title { @apply text-[14px] font-bold text-ink-strong mb-3; }
.evidence-grid { @apply grid grid-cols-4 gap-2 mb-4; }
.ev-card { @apply flex flex-col items-center rounded-xl border border-line-soft bg-surface-soft/60 py-3; }
.ev-card--alert { @apply border-amber-200 bg-amber-50; }
.ev-num { @apply text-[19px] font-bold text-ink-strong; }
.ev-label { @apply text-[11.5px] text-ink-muted mt-0.5; }

.suspect-list { @apply space-y-2; }
.suspect-item { @apply flex gap-3 rounded-xl border border-line-soft bg-white p-3; }
.suspect-tag {
  @apply shrink-0 h-5 px-2 rounded text-[11px] font-bold bg-amber-100 text-amber-700 flex items-center mt-0.5;
}
.suspect-title { @apply text-[13.5px] font-semibold text-ink-strong; }
.suspect-detail { @apply text-[12.5px] text-ink-muted mt-0.5 leading-relaxed; }

.ai-section { @apply mt-5 pt-4 border-t border-line-soft; }
.warn-tag { @apply text-[11px] px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100; }
</style>
