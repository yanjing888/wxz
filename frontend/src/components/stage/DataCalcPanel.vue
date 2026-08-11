<template>
  <StagePanel
    title="计算与作图"
    desc="结果由本地算法算出，数值可直接写进报告；AI 只在你需要时补充说明。"
    empty-title="选一个计算工具"
    empty-hint="在左侧选择工具、填入数据后点击开始计算。数据可以从「原始数据」表按列导入。"
  >
    <template #input>
      <section>
        <label class="field-label">计算工具</label>
        <div class="mode-list">
          <button
            v-for="m in MODES"
            :key="m.key"
            type="button"
            class="mode-btn"
            :class="{ 'mode-btn--active': mode === m.key }"
            @click="mode = m.key"
          >
            <span class="mode-name">{{ m.name }}</span>
            <span class="mode-desc">{{ m.desc }}</span>
          </button>
        </div>
      </section>

      <section v-if="columns.length">
        <label class="field-label">从数据表导入</label>
        <div class="chip-row">
          <button
            v-for="col in columns"
            :key="col.index"
            type="button"
            class="chip"
            @click="applyColumn(col)"
          >
            {{ col.header }}（{{ col.values.length }}）
          </button>
        </div>
        <p v-if="mode === 'fit'" class="field-hint">先点一列作为 x，再点另一列作为 y。</p>
      </section>
      <p v-else class="field-hint">「原始数据」表里还没有可用的数值列，可以先去录入。</p>

      <template v-if="mode === 'uncertainty' || mode === 'successive'">
        <section>
          <label class="field-label">{{ mode === 'successive' ? '等间隔测量列（偶数个）' : '各次测量值' }}</label>
          <textarea v-model="dataText" class="field-textarea font-mono" rows="5" :placeholder="dataPlaceholder" />
          <p class="field-hint">已识别 {{ parsedValues.length }} 个数值。</p>
        </section>
        <section class="grid grid-cols-2 gap-3">
          <div>
            <label class="field-label">{{ mode === 'successive' ? '每档间隔数' : '仪器示值误差' }}</label>
            <input v-model="secondaryInput" class="field-input" :placeholder="mode === 'successive' ? '1' : '0.02'" />
          </div>
          <div>
            <label class="field-label">单位</label>
            <input v-model="unit" class="field-input" placeholder="mm" />
          </div>
        </section>
        <section>
          <label class="field-label">物理量名称</label>
          <input v-model="quantityLabel" class="field-input" placeholder="如 小球直径" />
        </section>
      </template>

      <template v-else>
        <section>
          <label class="field-label">x 数据</label>
          <textarea v-model="fitX" class="field-textarea font-mono" rows="4" placeholder="1, 2, 3, 4, 5" />
        </section>
        <section>
          <label class="field-label">y 数据</label>
          <textarea v-model="fitY" class="field-textarea font-mono" rows="4" placeholder="2.1, 4.0, 6.2, 7.9, 10.1" />
        </section>
        <section class="grid grid-cols-2 gap-3">
          <div>
            <label class="field-label">x 轴名称</label>
            <input v-model="xLabel" class="field-input" placeholder="环序数 m" />
          </div>
          <div>
            <label class="field-label">y 轴名称</label>
            <input v-model="yLabel" class="field-input" placeholder="D² / mm²" />
          </div>
        </section>
      </template>

      <button type="button" class="btn-brand w-full py-2.5 rounded-xl text-sm font-semibold" :disabled="!canRun" @click="run">
        开始计算
      </button>
      <p v-if="runError" class="err-text">{{ runError }}</p>
    </template>

    <template v-if="hasResult" #result>
      <template v-if="mode === 'uncertainty' && uncertainty">
        <h3 class="result-title">不确定度计算结果</h3>
        <div class="kv-grid">
          <div><span class="kv-k">n</span><span class="kv-v">{{ uncertainty.stats.n }}</span></div>
          <div><span class="kv-k">平均值 x̄</span><span class="kv-v">{{ uncertainty.stats.mean.toFixed(4) }}</span></div>
          <div><span class="kv-k">标准差 s</span><span class="kv-v">{{ uncertainty.stats.std.toFixed(4) }}</span></div>
          <div><span class="kv-k">u_A</span><span class="kv-v">{{ uncertainty.stats.uA.toFixed(4) }}</span></div>
          <div><span class="kv-k">u_B</span><span class="kv-v">{{ uncertainty.uB.toFixed(4) }}</span></div>
          <div><span class="kv-k">u_c</span><span class="kv-v">{{ uncertainty.uCombined.toFixed(4) }}</span></div>
        </div>
        <div class="highlight-box">
          <span class="kv-k">按有效数字修约后的结果</span>
          <span class="highlight-value">{{ uncertainty.rounded }}</span>
          <span class="kv-k mt-1">相对不确定度 {{ uncertainty.formatted.relativePercent }}%</span>
        </div>
        <ResultParagraph :text="uncertainty.paragraph" :loading="loading" @explain="aiExplain('uncertainty')" />
      </template>

      <template v-else-if="mode === 'successive' && successive">
        <h3 class="result-title">逐差法计算结果</h3>
        <div v-if="!successive.ok" class="warn-box">{{ successive.reason }}</div>
        <template v-else>
          <div class="table-wrap">
            <table class="data-table">
              <thead><tr><th>差值组</th><th>差值</th></tr></thead>
              <tbody>
                <tr v-for="(d, i) in successive.diffs" :key="i">
                  <td>{{ d.pair }}</td>
                  <td class="font-mono">{{ d.value.toFixed(4) }}{{ unitSuffix }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="highlight-box mt-4">
            <span class="kv-k">每间隔平均增量</span>
            <span class="highlight-value">{{ successive.stepValue.toFixed(4) }}{{ unitSuffix }}</span>
          </div>
          <ResultParagraph :text="successiveParagraph" :loading="loading" @explain="aiExplain('successive')" />
        </template>
      </template>

      <template v-else-if="mode === 'fit' && fitResult">
        <h3 class="result-title">线性拟合与作图</h3>
        <div class="kv-grid">
          <div><span class="kv-k">斜率 k</span><span class="kv-v">{{ fitResult.slope.toFixed(4) }}</span></div>
          <div><span class="kv-k">截距 b</span><span class="kv-v">{{ fitResult.intercept.toFixed(4) }}</span></div>
          <div><span class="kv-k">R²</span><span class="kv-v">{{ fitResult.r2.toFixed(4) }}</span></div>
          <div><span class="kv-k">数据点</span><span class="kv-v">{{ fitResult.n }}</span></div>
        </div>
        <ScatterPlot
          class="mt-4"
          savable
          :xs="fitXs"
          :ys="fitYs"
          :fit="fitResult"
          :x-label="xLabel || 'x'"
          :y-label="yLabel || 'y'"
          :title="plotTitle"
          @save="saveChart"
        />
        <p v-if="notice" class="ok-text">{{ notice }}</p>
        <ResultParagraph :text="fitParagraph" :loading="loading" @explain="aiExplain('fit')" />
      </template>

      <section v-if="aiText" class="ai-box">
        <h3 class="ai-title">AI 补充说明</h3>
        <div class="chat-md" v-html="renderMd(aiText)" />
      </section>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref } from 'vue'
import StagePanel from './StagePanel.vue'
import ScatterPlot from '../data/ScatterPlot.vue'
import ResultParagraph from '../data/ResultParagraph.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { studentFileApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'
import { numericColumns } from '../../utils/dataTable'
import {
  parseNumbers,
  calcBasicStats,
  calcInstrumentUB,
  calcCombinedUncertainty,
  formatUncertaintyResult,
  buildUncertaintyParagraph,
  buildSuccessiveParagraph,
  successiveDifference,
  roundToSigFigs,
  roundUncertainty,
  linearFit
} from '../../utils/physicsCalc'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessionId: { type: [Number, String], default: null },
  table: { type: Object, default: null }
})
const emit = defineEmits(['saved'])

const MODES = [
  { key: 'uncertainty', name: '不确定度', desc: 'A/B 类与合成不确定度，含修约' },
  { key: 'successive', name: '逐差法', desc: '等间隔测量列，抵消线性系统误差' },
  { key: 'fit', name: '线性拟合与作图', desc: '斜率、截距、R²，可导出 PNG' }
]

const { loading, invoke } = useAgentTool('data-lab', () => props.experimentCode)

const mode = ref('uncertainty')
const dataText = ref('')
const secondaryInput = ref('')
const unit = ref('')
const quantityLabel = ref('')
const fitX = ref('')
const fitY = ref('')
const xLabel = ref('')
const yLabel = ref('')
const uncertainty = ref(null)
const successive = ref(null)
const fitResult = ref(null)
const aiText = ref('')
const runError = ref('')
const notice = ref('')

const columns = computed(() => numericColumns(props.table))
const parsedValues = computed(() => parseNumbers(dataText.value))
const fitXs = computed(() => parseNumbers(fitX.value))
const fitYs = computed(() => parseNumbers(fitY.value))
const unitSuffix = computed(() => (unit.value ? ` ${unit.value}` : ''))
const dataPlaceholder = computed(() =>
  mode.value === 'successive' ? '2.10, 4.05, 6.02, 8.01, 9.98, 12.03' : '12.34, 12.36, 12.35'
)

const canRun = computed(() => {
  if (mode.value === 'fit') return fitXs.value.length >= 2 && fitYs.value.length >= 2
  return parsedValues.value.length > 0
})

const hasResult = computed(() => {
  if (mode.value === 'fit') return !!fitResult.value
  if (mode.value === 'successive') return !!successive.value
  return !!uncertainty.value
})

const successiveParagraph = computed(() =>
  buildSuccessiveParagraph(successive.value, unit.value, quantityLabel.value || '测量量')
)

const plotTitle = computed(() => {
  if (xLabel.value && yLabel.value) return `${yLabel.value} — ${xLabel.value} 关系图`
  return `${props.experimentName || '实验'}数据拟合图`
})

const fitParagraph = computed(() => {
  if (!fitResult.value) return ''
  const { slope, intercept, r2, n } = fitResult.value
  return [
    `以 ${xLabel.value || 'x'} 为横坐标、${yLabel.value || 'y'} 为纵坐标作图，共 ${n} 组数据点。`,
    `用最小二乘法作线性拟合，得 y = ${slope.toFixed(4)}x ${intercept >= 0 ? '+' : '−'} ${Math.abs(intercept).toFixed(4)}，`,
    `相关系数 R² = ${r2.toFixed(4)}${r2 > 0.99 ? '，线性关系良好。' : '，线性度一般，建议检查数据。'}`
  ].join('\n')
})

function applyColumn(col) {
  const joined = col.values.join(', ')
  if (mode.value === 'fit') {
    if (!fitX.value) {
      fitX.value = joined
      if (!xLabel.value) xLabel.value = col.header
    } else {
      fitY.value = joined
      if (!yLabel.value) yLabel.value = col.header
    }
    return
  }
  dataText.value = joined
  if (!quantityLabel.value) quantityLabel.value = col.header
}

function run() {
  runError.value = ''
  aiText.value = ''
  notice.value = ''
  if (mode.value === 'uncertainty') return runUncertainty()
  if (mode.value === 'successive') return runSuccessive()
  return runFit()
}

function runUncertainty() {
  const stats = calcBasicStats(parsedValues.value)
  if (!stats) {
    runError.value = '未识别到有效数值'
    return
  }
  const uB = calcInstrumentUB(secondaryInput.value)
  const uCombined = calcCombinedUncertainty(stats.uA, uB)
  const formatted = formatUncertaintyResult(stats.mean, uCombined, unit.value)
  const ru = roundUncertainty(uCombined)
  const meanRounded = ru.value > 0 ? stats.mean.toFixed(ru.decimals) : String(roundToSigFigs(stats.mean, 4))
  uncertainty.value = {
    stats,
    uB,
    uCombined,
    formatted,
    rounded: `(${meanRounded} ± ${ru.value.toFixed(ru.decimals)})${unitSuffix.value}`,
    paragraph: buildUncertaintyParagraph(stats, uB, uCombined, unit.value, quantityLabel.value || '测量量')
  }
}

function runSuccessive() {
  const interval = Number(secondaryInput.value) || 1
  successive.value = successiveDifference(parsedValues.value, interval)
  if (!successive.value.ok) runError.value = successive.value.reason
}

function runFit() {
  const result = linearFit(fitXs.value, fitYs.value)
  if (!result) {
    runError.value = 'x、y 至少各需要 2 个数值，且 x 不能全部相同'
    return
  }
  fitResult.value = result
}

async function saveChart(file) {
  notice.value = ''
  try {
    await studentFileApi.upload(file, {
      experimentCode: props.experimentCode,
      category: 'chart',
      stage: 'data',
      sessionId: props.sessionId || '',
      note: plotTitle.value
    })
    notice.value = '图表已存入实验资料库'
    emit('saved')
  } catch (e) {
    runError.value = e.message || '图表存档失败'
  }
}

async function aiExplain(kind) {
  aiText.value = ''
  const noteMap = {
    uncertainty: uncertainty.value?.paragraph,
    successive: successiveParagraph.value,
    fit: fitParagraph.value
  }
  try {
    const data = await invoke('analyze', {
      analysisType: kind,
      dataText: mode.value === 'fit' ? `x: ${fitX.value}\ny: ${fitY.value}` : dataText.value,
      note: noteMap[kind] || '',
      unit: unit.value,
      quantity: quantityLabel.value,
      sessionId: props.sessionId ? Number(props.sessionId) : null
    })
    aiText.value = data.text || ''
  } catch (e) {
    runError.value = e.response?.data?.message || 'AI 说明获取失败'
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
.ok-text { @apply text-[12.5px] text-emerald-600 mt-2; }

.mode-list { @apply space-y-2; }
.mode-btn {
  @apply w-full flex flex-col items-start text-left rounded-xl border border-line-soft bg-white px-3 py-2.5
    hover:border-brand-200 transition-colors;
}
.mode-btn--active { @apply border-brand-400 bg-brand-50; }
.mode-name { @apply text-[13.5px] font-bold text-ink-strong; }
.mode-desc { @apply text-[11.5px] text-ink-muted mt-0.5; }

.chip-row { @apply flex flex-wrap gap-2; }
.chip {
  @apply px-3 py-1.5 rounded-lg text-[12.5px] border border-line-soft bg-surface-soft
    hover:border-brand-200 transition-colors;
}

.result-title { @apply text-[15px] font-bold text-ink-strong mb-3; }
.kv-grid { @apply grid grid-cols-2 sm:grid-cols-3 gap-3; }
.kv-k { @apply block text-[11px] text-ink-faint; }
.kv-v { @apply font-semibold text-ink-strong font-mono text-[14px]; }
.highlight-box { @apply flex flex-col rounded-xl bg-brand-50 border border-brand-100 p-4 mt-4; }
.highlight-value { @apply text-[19px] font-bold text-brand-700 font-mono mt-1; }
.warn-box { @apply rounded-xl bg-amber-50 border border-amber-100 p-4 text-[13px] text-amber-800; }

.table-wrap { @apply overflow-x-auto; }
.data-table { @apply w-full text-[13px] text-left; }
.data-table th { @apply px-3 py-2 bg-surface-soft text-ink-muted font-semibold border-b border-line-soft; }
.data-table td { @apply px-3 py-2 border-b border-line-soft/60; }

.ai-box { @apply mt-5 pt-4 border-t border-line-soft; }
.ai-title { @apply text-[14px] font-bold text-ink-strong mb-2; }
</style>
