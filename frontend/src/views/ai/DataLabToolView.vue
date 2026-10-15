<template>
  <AiToolShell :tool="tool" fill>
    <div v-if="!activeTool" class="hub-page custom-scroll">
      <header class="hub-header">
        <h2 class="hub-title">数据处理</h2>
        <p class="hub-lead">
          先从实验会话导入测量数据，由系统<strong>本地计算</strong>不确定度与拟合结果，可直接复制进报告。AI 仅作补充说明。
        </p>
      </header>
      <div class="tool-grid">
        <button type="button" class="tool-card tool-card--primary" @click="openTool('uncertainty')">
          <span class="tool-icon tool-icon--blue">±</span>
          <span class="tool-name">不确定度计算器</span>
          <span class="tool-desc">算术平均值、A/B 类不确定度、合成不确定度与结果表示</span>
          <span class="tool-badge">本地计算 · 可写进报告</span>
        </button>
        <button type="button" class="tool-card" @click="openTool('fit')">
          <span class="tool-icon tool-icon--purple">↗</span>
          <span class="tool-name">线性拟合</span>
          <span class="tool-desc">x-y 数据线性拟合，斜率、截距与 R²</span>
          <span class="tool-badge">本地计算</span>
        </button>
        <button type="button" class="tool-card" @click="openTool('table')">
          <span class="tool-icon tool-icon--green">表</span>
          <span class="tool-name">实验数据表</span>
          <span class="tool-desc">查看并整理实验台提交的各步测量数据</span>
          <span class="tool-badge">来自实验会话</span>
        </button>
      </div>
    </div>

    <div v-else class="workspace flex flex-col flex-1 min-h-0">
      <header class="workspace-header shrink-0">
        <button type="button" class="back-btn" @click="closeTool">← 返回</button>
        <h2 class="workspace-title">{{ toolTitle }}</h2>
      </header>

      <div class="workspace-body flex-1 min-h-0 custom-scroll">
        <!-- 会话选择（共用） -->
        <section class="block">
          <h3 class="block-label">实验会话</h3>
          <select v-model="sessionId" class="field-input" @change="loadSessionData">
            <option value="">请选择已提交的实验会话</option>
            <option v-for="item in sessions" :key="item.id" :value="item.id">
              {{ item.experimentName }} · {{ formatSessionTime(item.endTime || item.startTime) }}
            </option>
          </select>
          <p v-if="!sessions.length" class="hint">暂无会话。请先在实验工作台完成测量并提交数据。</p>
        </section>

        <!-- 数据表视图 -->
        <template v-if="activeTool === 'table'">
          <section v-if="sessionRows.length" class="block">
            <div class="table-wrap">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>步骤</th>
                    <th v-for="col in tableColumns" :key="col">{{ col }}</th>
                    <th>校验</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in sessionRows" :key="i">
                    <td>{{ row.stepTitle }}</td>
                    <td v-for="col in tableColumns" :key="col">{{ row.values[col] ?? '—' }}</td>
                    <td>{{ validationLabel(row.validation) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <button type="button" class="btn-ghost mt-3 text-sm" @click="copyTableText">复制为文本</button>
          </section>
          <p v-else class="empty-block">该会话暂无结构化数据，请在实验工作台各测量步骤提交数据。</p>
        </template>

        <!-- 不确定度 -->
        <template v-if="activeTool === 'uncertainty'">
          <section class="block">
            <h3 class="block-label">测量数据</h3>
            <div v-if="numericSeries.length" class="series-pick">
              <p class="sub-hint">从实验数据选择一组重复测量：</p>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="s in numericSeries"
                  :key="s.key"
                  type="button"
                  class="series-chip"
                  :class="{ 'series-chip--active': selectedSeriesKey === s.key }"
                  @click="applySeries(s)"
                >
                  {{ s.key }}（{{ s.values.length }} 个值）
                </button>
              </div>
            </div>
            <textarea
              v-model="dataText"
              class="field-textarea mt-2"
              rows="4"
              placeholder="输入或粘贴各次测量值，用逗号或换行分隔，如：12.34, 12.36, 12.35"
            />
          </section>
          <section class="block grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="sub-label">仪器示值误差（B 类）</label>
              <input v-model="instrumentError" class="field-input" placeholder="如 ±0.02 mm" />
            </div>
            <div>
              <label class="sub-label">单位（可选）</label>
              <input v-model="unit" class="field-input" placeholder="如 mm、m/s²" />
            </div>
          </section>
          <button type="button" class="btn-brand w-full sm:w-auto px-6 py-2.5 rounded-xl text-sm font-semibold" :disabled="!parsedValues.length" @click="runUncertainty">
            计算不确定度
          </button>

          <section v-if="uncertaintyResult" class="block result-box">
            <h3 class="block-label">计算结果</h3>
            <div class="result-grid">
              <div><span class="rk">n</span><span class="rv">{{ uncertaintyResult.stats.n }}</span></div>
              <div><span class="rk">平均值 x̄</span><span class="rv">{{ uncertaintyResult.stats.mean.toFixed(4) }}</span></div>
              <div><span class="rk">标准差 s</span><span class="rv">{{ uncertaintyResult.stats.std.toFixed(4) }}</span></div>
              <div><span class="rk">u_A</span><span class="rv">{{ uncertaintyResult.stats.uA.toFixed(4) }}</span></div>
              <div><span class="rk">u_B</span><span class="rv">{{ uncertaintyResult.uB.toFixed(4) }}</span></div>
              <div><span class="rk">u_c</span><span class="rv">{{ uncertaintyResult.uCombined.toFixed(4) }}</span></div>
              <div class="col-span-2 highlight">
                <span class="rk">结果表示</span>
                <span class="rv-lg">{{ uncertaintyResult.formatted.formatted }}</span>
              </div>
              <div><span class="rk">相对不确定度</span><span class="rv">{{ uncertaintyResult.formatted.relativePercent }}%</span></div>
            </div>
            <div class="report-para">
              <h4 class="text-[13px] font-bold text-ink-strong mb-2">报告用段落（可直接复制）</h4>
              <pre class="para-text">{{ uncertaintyResult.paragraph }}</pre>
              <div class="flex gap-2 mt-3">
                <button type="button" class="btn-brand px-4 py-2 rounded-lg text-sm" @click="copyText(uncertaintyResult.paragraph)">复制段落</button>
                <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-sm" :disabled="aiLoading" @click="aiExplain('uncertainty')">
                  {{ aiLoading ? '生成中…' : 'AI 补充说明' }}
                </button>
              </div>
            </div>
          </section>
        </template>

        <!-- 线性拟合 -->
        <template v-if="activeTool === 'fit'">
          <section class="block grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="sub-label">x 数据</label>
              <textarea v-model="fitX" class="field-textarea" rows="4" placeholder="1, 2, 3, 4, 5" />
            </div>
            <div>
              <label class="sub-label">y 数据</label>
              <textarea v-model="fitY" class="field-textarea" rows="4" placeholder="2.1, 4.0, 6.2, 7.9, 10.1" />
            </div>
          </section>
          <button type="button" class="btn-brand px-6 py-2.5 rounded-xl text-sm font-semibold" @click="runFit">计算拟合</button>
          <section v-if="fitResult" class="block result-box">
            <div class="result-grid">
              <div><span class="rk">斜率 k</span><span class="rv">{{ fitResult.slope.toFixed(4) }}</span></div>
              <div><span class="rk">截距 b</span><span class="rv">{{ fitResult.intercept.toFixed(4) }}</span></div>
              <div><span class="rk">R²</span><span class="rv">{{ fitResult.r2.toFixed(4) }}</span></div>
              <div class="col-span-2 highlight">
                <span class="rk">拟合方程</span>
                <span class="rv-lg">y = {{ fitResult.slope.toFixed(4) }}x + {{ fitResult.intercept.toFixed(4) }}</span>
              </div>
            </div>
            <pre class="para-text mt-3">{{ fitParagraph }}</pre>
            <button type="button" class="btn-brand px-4 py-2 rounded-lg text-sm mt-3" @click="copyText(fitParagraph)">复制段落</button>
          </section>
        </template>

        <section v-if="aiText" class="block ai-box">
          <h3 class="block-label">AI 补充说明</h3>
          <div class="chat-md" v-html="renderMd(aiText)" />
        </section>
      </div>
    </div>
  </AiToolShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AiToolShell from '../../components/ai/AiToolShell.vue'
import { aiToolApi, experimentApi, sessionApi } from '../../api'
import { formatSessionTime } from '../../utils/studentFlow'
import { renderChatMarkdown } from '../../utils/markdown'
import {
  parseNumbers,
  calcBasicStats,
  calcInstrumentUB,
  calcCombinedUncertainty,
  formatUncertaintyResult,
  buildUncertaintyParagraph,
  linearFit
} from '../../utils/physicsCalc'
import { parseSessionRows, findNumericSeries } from '../../utils/sessionReport'

const props = defineProps({ tool: { type: Object, required: true } })
const route = useRoute()

const activeTool = ref('')
const sessions = ref([])
const sessionId = ref('')
const sessionRows = ref([])
const numericSeries = ref([])
const selectedSeriesKey = ref('')
const dataText = ref('')
const instrumentError = ref('')
const unit = ref('')
const uncertaintyResult = ref(null)
const fitX = ref('')
const fitY = ref('')
const fitResult = ref(null)
const aiLoading = ref(false)
const aiText = ref('')

const toolTitle = computed(() => {
  if (activeTool.value === 'uncertainty') return '不确定度计算器'
  if (activeTool.value === 'fit') return '线性拟合'
  if (activeTool.value === 'table') return '实验数据表'
  return ''
})

const parsedValues = computed(() => parseNumbers(dataText.value))

const tableColumns = computed(() => {
  const cols = new Set()
  sessionRows.value.forEach((row) => {
    Object.keys(row.values || {}).forEach((k) => cols.add(k))
  })
  return [...cols]
})

const fitParagraph = computed(() => {
  if (!fitResult.value) return ''
  const { slope, intercept, r2, n } = fitResult.value
  return `对 ${n} 组数据作线性拟合，得 y = ${slope.toFixed(4)}x + ${intercept.toFixed(4)}，相关系数 R² = ${r2.toFixed(4)}。`
})

onMounted(async () => {
  const [expRes, sessionRes] = await Promise.all([experimentApi.list(), sessionApi.list()])
  sessions.value = sessionRes.data || []
  applyRouteQuery()
})

watch(() => route.query, applyRouteQuery, { deep: true })

function applyRouteQuery() {
  const mode = String(route.query.mode || '').trim()
  if (['uncertainty', 'fit', 'table'].includes(mode)) activeTool.value = mode
  const exp = String(route.query.exp || '').trim()
  if (exp) {
    const hit = sessions.value.find((s) => s.experimentCode === exp)
    if (hit) {
      sessionId.value = hit.id
      loadSessionData()
    }
  }
}

function openTool(tool) {
  activeTool.value = tool
  aiText.value = ''
  uncertaintyResult.value = null
  fitResult.value = null
}

function closeTool() {
  activeTool.value = ''
}

async function loadSessionData() {
  sessionRows.value = []
  numericSeries.value = []
  if (!sessionId.value) return
  try {
    const { data } = await sessionApi.getData(sessionId.value)
    sessionRows.value = parseSessionRows(data?.byStep)
    numericSeries.value = findNumericSeries(sessionRows.value)
    if (numericSeries.value.length === 1) applySeries(numericSeries.value[0])
  } catch {
    // ignore
  }
}

function applySeries(series) {
  selectedSeriesKey.value = series.key
  dataText.value = series.values.join(', ')
}

function validationLabel(v) {
  if (!v) return '—'
  if (v.ok === true) return '通过'
  if (v.errors?.length) return v.errors.join('；')
  return '待复核'
}

function runUncertainty() {
  const values = parsedValues.value
  const stats = calcBasicStats(values)
  if (!stats) return
  const uB = calcInstrumentUB(instrumentError.value)
  const uCombined = calcCombinedUncertainty(stats.uA, uB)
  const formatted = formatUncertaintyResult(stats.mean, uCombined, unit.value)
  uncertaintyResult.value = {
    stats,
    uB,
    uCombined,
    formatted,
    paragraph: buildUncertaintyParagraph(stats, uB, uCombined, unit.value, selectedSeriesKey.value || '测量量')
  }
}

function runFit() {
  const xs = parseNumbers(fitX.value)
  const ys = parseNumbers(fitY.value)
  fitResult.value = linearFit(xs, ys)
}

function copyTableText() {
  const lines = sessionRows.value.map((row) => {
    const vals = Object.entries(row.values).map(([k, v]) => `${k}=${v}`).join('，')
    return `${row.stepTitle}：${vals}`
  })
  copyText(lines.join('\n'))
}

function copyText(text) {
  navigator.clipboard?.writeText(text).catch(() => {})
}

async function aiExplain() {
  if (!uncertaintyResult.value) return
  aiLoading.value = true
  aiText.value = ''
  try {
    const session = sessions.value.find((s) => String(s.id) === String(sessionId.value))
    const { data } = await aiToolApi.invoke(props.tool.code, {
      action: 'analyze',
      inputs: {
        analysisType: 'uncertainty',
        experimentName: session?.experimentName || '',
        dataText: dataText.value,
        note: uncertaintyResult.value.paragraph,
        instrumentError: instrumentError.value,
        sessionId: sessionId.value ? Number(sessionId.value) : null
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
.hub-page { @apply flex-1 min-h-0 overflow-y-auto max-w-[880px] mx-auto w-full px-6 py-8; }
.hub-title { @apply text-[20px] font-bold text-ink-strong; }
.hub-lead { @apply text-[14px] text-ink-muted mt-2 leading-relaxed; }
.tool-grid { @apply grid grid-cols-1 md:grid-cols-3 gap-4 mt-6; }
.tool-card {
  @apply flex flex-col items-start text-left rounded-2xl border border-line-soft bg-white p-5
    hover:border-brand-200 hover:shadow-card transition-all;
}
.tool-card--primary { @apply border-brand-200 bg-brand-50/30; }
.tool-icon { @apply w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold mb-3; }
.tool-icon--blue { @apply bg-blue-500; }
.tool-icon--purple { @apply bg-violet-500; }
.tool-icon--green { @apply bg-emerald-500; }
.tool-name { @apply text-[15px] font-bold text-ink-strong; }
.tool-desc { @apply text-[13px] text-ink-muted mt-1 leading-relaxed; }
.tool-badge { @apply mt-3 text-[11px] font-semibold text-brand-600 bg-brand-50 px-2 py-0.5 rounded; }

.workspace { @apply bg-surface-soft/40; }
.workspace-header { @apply flex items-center gap-4 px-5 py-3 border-b border-line-soft bg-white; }
.back-btn { @apply text-[13px] text-ink-muted hover:text-brand-600 font-medium; }
.workspace-title { @apply text-[16px] font-bold text-ink-strong; }
.workspace-body { @apply flex-1 min-h-0 overflow-y-auto max-w-[720px] mx-auto w-full px-5 py-5 space-y-5; }

.block { @apply rounded-xl border border-line-soft bg-white p-4; }
.block-label { @apply text-[13px] font-bold text-ink-strong mb-2; }
.sub-label { @apply block text-[12px] text-ink-muted mb-1; }
.sub-hint { @apply text-[12px] text-ink-muted mb-2; }
.field-input { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.field-textarea { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px] font-mono; }
.hint { @apply text-[13px] text-ink-faint mt-2; }
.empty-block { @apply text-center text-ink-muted text-sm py-8; }

.series-chip {
  @apply px-3 py-1.5 rounded-lg text-[12px] border border-line-soft bg-surface-soft
    hover:border-brand-200 transition-colors;
}
.series-chip--active { @apply border-brand-400 bg-brand-50 text-brand-700 font-semibold; }

.table-wrap { @apply overflow-x-auto; }
.data-table { @apply w-full text-[13px] text-left; }
.data-table th { @apply px-3 py-2 bg-surface-soft text-ink-muted font-semibold border-b border-line-soft; }
.data-table td { @apply px-3 py-2 border-b border-line-soft/60; }

.result-box { @apply bg-surface-soft/50; }
.result-grid { @apply grid grid-cols-2 gap-3 text-[13px]; }
.rk { @apply block text-ink-faint text-[11px]; }
.rv { @apply font-semibold text-ink-strong font-mono; }
.rv-lg { @apply font-bold text-brand-700 font-mono text-[15px]; }
.highlight { @apply col-span-2 p-3 rounded-lg bg-brand-50 border border-brand-100; }
.para-text { @apply text-[13px] leading-relaxed whitespace-pre-wrap bg-white p-3 rounded-lg border border-line-soft font-sans; }
.ai-box { @apply bg-violet-50/50 border-violet-100; }
</style>
