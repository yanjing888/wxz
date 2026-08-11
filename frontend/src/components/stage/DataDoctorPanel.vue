<template>
  <StagePanel
    title="数据体检"
    desc="先做本地检查看数据本身，再让 AI 判断在物理上是否说得通。"
    empty-title="先做一次本地体检"
    empty-hint="粘贴或从数据表导入一组重复测量值，系统会立即给出离散度、离群值与有效数字检查结果，无需等待 AI。"
  >
    <template #input>
      <section v-if="columns.length">
        <label class="field-label">从数据表导入</label>
        <div class="chip-row">
          <button v-for="col in columns" :key="col.index" type="button" class="chip" @click="applyColumn(col)">
            {{ col.header }}（{{ col.values.length }}）
          </button>
        </div>
      </section>

      <section>
        <label class="field-label">测量数据</label>
        <textarea v-model="dataText" class="field-textarea font-mono" rows="5" placeholder="12.34, 12.36, 12.35, 12.33, 12.35" />
        <p class="field-hint">用逗号或换行分隔。请保留原始读数位数，不要提前修约。</p>
      </section>

      <section class="grid grid-cols-2 gap-3">
        <div>
          <label class="field-label">仪器分度值</label>
          <input v-model="precision" class="field-input" placeholder="0.02" />
        </div>
        <div>
          <label class="field-label">单位</label>
          <input v-model="unit" class="field-input" placeholder="mm" />
        </div>
      </section>

      <section>
        <label class="field-label">参考值 / 理论值（可选）</label>
        <input v-model="expectedValue" class="field-input" placeholder="如 589.3" />
        <p class="field-hint">填写后会额外检查相对偏差是否超出合理范围。</p>
      </section>

      <section>
        <label class="field-label">测量对象说明</label>
        <input v-model="quantity" class="field-input" placeholder="如 小球直径 / 第 10 环半径" />
      </section>

      <button type="button" class="btn-brand w-full py-2.5 rounded-xl text-sm font-semibold" :disabled="!parsedCount" @click="runLocalCheck">
        开始体检（本地计算）
      </button>
      <p v-if="error" class="err-text">{{ error }}</p>
    </template>

    <template v-if="health" #result>
      <div class="score-card" :class="`score-card--${overall.level}`">
        <div>
          <p class="score-title">{{ overall.title }}</p>
          <p class="score-sub">{{ overall.sub }}</p>
        </div>
        <div class="score-stat">
          <span>n = {{ health.stats?.n }}</span>
          <span>x̄ = {{ health.stats?.mean.toFixed(4) }}{{ unitSuffix }}</span>
          <span>s = {{ health.stats?.std.toFixed(4) }}{{ unitSuffix }}</span>
        </div>
      </div>

      <ul class="check-list">
        <li v-for="(c, i) in health.checks" :key="i" class="check-item" :class="`check-item--${c.level}`">
          <span class="check-mark">{{ markOf(c.level) }}</span>
          <div class="min-w-0">
            <p class="check-title">{{ c.title }}</p>
            <p class="check-detail">{{ c.detail }}</p>
          </div>
        </li>
      </ul>

      <div class="ai-section">
        <div class="flex items-center justify-between gap-3 mb-2">
          <h3 class="ai-title">物理合理性判断</h3>
          <button type="button" class="btn-ghost px-4 py-1.5 rounded-lg text-[13px]" :disabled="loading" @click="askAi">
            {{ loading ? '分析中…' : aiText ? '重新分析' : '让 AI 判断' }}
          </button>
        </div>
        <p v-if="!aiText && !loading" class="field-hint">
          本地体检只看数据本身。点击后 AI 会结合本实验的典型量级与常见错误，判断这组数据在物理上是否说得通。
        </p>
        <div v-if="aiText" class="chat-md" v-html="renderMd(aiText)" />
      </div>

      <div v-if="showNextActions" class="next-row">
        <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px]" @click="$emit('go', 'calc')">
          去算不确定度
        </button>
        <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px]" @click="$emit('go', 'trace')">
          偏差较大？去误差溯源
        </button>
      </div>
    </template>
  </StagePanel>
</template>

<script setup>
import { computed, ref } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { checkDataHealth, parseNumbers } from '../../utils/physicsCalc'
import { numericColumns } from '../../utils/dataTable'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  table: { type: Object, default: null },
  showNextActions: { type: Boolean, default: true }
})
defineEmits(['go'])

const { loading, error, invoke } = useAgentTool('data-doctor', () => props.experimentCode)

const dataText = ref('')
const precision = ref('')
const unit = ref('')
const expectedValue = ref('')
const quantity = ref('')
const health = ref(null)
const aiText = ref('')

const columns = computed(() => numericColumns(props.table))
const parsedCount = computed(() => parseNumbers(dataText.value).length)
const unitSuffix = computed(() => (unit.value ? ` ${unit.value}` : ''))

const overall = computed(() => {
  const checks = health.value?.checks || []
  if (checks.some((c) => c.level === 'error')) {
    return { level: 'error', title: '发现明显问题', sub: '有条目需要复测或修正后再继续处理。' }
  }
  if (checks.some((c) => c.level === 'warn')) {
    return { level: 'warn', title: '基本可用，但有待改进', sub: '存在需要注意的记录规范或离散度问题。' }
  }
  return { level: 'ok', title: '数据状态良好', sub: '各项本地检查均通过，可以进入数据处理。' }
})

function applyColumn(col) {
  dataText.value = col.values.join(', ')
  quantity.value = col.header
  runLocalCheck()
}

function runLocalCheck() {
  aiText.value = ''
  health.value = checkDataHealth(dataText.value, {
    precision: precision.value,
    expectedValue: expectedValue.value
  })
}

function markOf(level) {
  if (level === 'error') return '!'
  if (level === 'warn') return '?'
  return '✓'
}

async function askAi() {
  aiText.value = ''
  const localSummary = (health.value?.checks || [])
    .map((c) => `[${c.level}] ${c.title}：${c.detail}`)
    .join('\n')
  try {
    const data = await invoke('doctor', {
      experimentName: props.experimentName,
      quantity: quantity.value,
      unit: unit.value,
      precision: precision.value,
      expectedValue: expectedValue.value,
      dataText: dataText.value,
      data_json: JSON.stringify(health.value?.values || []),
      local_check_summary: localSummary,
      mean: health.value?.stats?.mean ?? null,
      std: health.value?.stats?.std ?? null
    })
    aiText.value = data.text || ''
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

.chip-row { @apply flex flex-wrap gap-2; }
.chip {
  @apply px-3 py-1.5 rounded-lg text-[12.5px] border border-line-soft bg-surface-soft
    hover:border-brand-200 transition-colors;
}

.score-card { @apply flex flex-wrap items-center justify-between gap-3 rounded-xl border p-4 mb-4; }
.score-card--ok { @apply bg-emerald-50 border-emerald-100; }
.score-card--warn { @apply bg-amber-50 border-amber-100; }
.score-card--error { @apply bg-rose-50 border-rose-100; }
.score-title { @apply text-[15px] font-bold text-ink-strong; }
.score-sub { @apply text-[12.5px] text-ink-muted mt-0.5; }
.score-stat { @apply flex flex-col items-end gap-0.5 text-[12px] font-mono text-ink-base; }

.check-list { @apply space-y-2; }
.check-item { @apply flex gap-3 rounded-xl border p-3; }
.check-item--ok { @apply bg-white border-line-soft; }
.check-item--warn { @apply bg-amber-50/50 border-amber-100; }
.check-item--error { @apply bg-rose-50/50 border-rose-100; }
.check-mark {
  @apply w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold text-white shrink-0 mt-0.5;
}
.check-item--ok .check-mark { @apply bg-emerald-500; }
.check-item--warn .check-mark { @apply bg-amber-500; }
.check-item--error .check-mark { @apply bg-rose-500; }
.check-title { @apply text-[13.5px] font-semibold text-ink-strong; }
.check-detail { @apply text-[12.5px] text-ink-muted mt-0.5 leading-relaxed; }

.ai-section { @apply mt-5 pt-4 border-t border-line-soft; }
.ai-title { @apply text-[14px] font-bold text-ink-strong; }
.next-row { @apply flex flex-wrap gap-2 mt-5 pt-4 border-t border-line-soft; }
</style>
