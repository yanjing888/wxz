<template>
  <section class="flex-1 flex flex-col overflow-hidden min-h-0">
    <div class="flex items-center justify-between px-5 pb-1 shrink-0 gap-2">
      <span v-if="deviceName" class="text-[10px] text-ink-faint truncate" :title="deviceName">{{ deviceName }}</span>
      <span class="chip !text-[9px] !px-1.5 shrink-0 ml-auto" :class="stateChipClass">
        <span class="w-1 h-1 rounded-full" :class="stateDotClass" />
        {{ stateLabel }}
      </span>
    </div>

    <div class="flex-1 min-h-0 overflow-y-auto custom-scroll px-5 py-2 space-y-3 flex flex-col">
      <!-- 万能试验机：实时曲线 + 读数 -->
      <template v-if="deviceType === 'universal_tester'">
        <div
          v-if="deviceState === 'acquiring' && acquireProgress?.total"
          class="shrink-0 rounded-lg bg-brand-50/70 border border-brand-100 px-2.5 py-1.5"
        >
          <div class="flex justify-between text-[10px] text-brand-700 mb-1">
            <span>试验机实时采集中</span>
            <span class="tabular-nums font-semibold">{{ acquireProgress.current }} / {{ acquireProgress.total }}</span>
          </div>
          <div class="h-1.5 rounded-full bg-brand-100 overflow-hidden">
            <div
              class="h-full brand-gradient rounded-full transition-all duration-75"
              :style="{ width: progressPct + '%' }"
            />
          </div>
        </div>
        <div class="shrink-0 h-[152px] rounded-xl bg-bg-soft overflow-hidden">
          <StressStrainChart :points="curvePoints" />
        </div>
        <div class="grid grid-cols-2 gap-2 shrink-0">
          <div class="gauge-cell">
            <div class="gauge-label">拉力 F</div>
            <div class="gauge-value">{{ fmt(live.forceKn) }}<span class="gauge-unit">kN</span></div>
          </div>
          <div class="gauge-cell">
            <div class="gauge-label">应变 ε</div>
            <div class="gauge-value">{{ fmt(live.strainPct) }}<span class="gauge-unit">%</span></div>
          </div>
          <div class="gauge-cell">
            <div class="gauge-label">应力 σ</div>
            <div class="gauge-value">{{ fmt(live.stressMpa) }}<span class="gauge-unit">MPa</span></div>
          </div>
          <div class="gauge-cell">
            <div class="gauge-label">位移 ΔL</div>
            <div class="gauge-value">{{ fmt(live.displacementMm) }}<span class="gauge-unit">mm</span></div>
          </div>
        </div>
        <div
          v-if="snapshotRows.length && deviceState !== 'acquiring'"
          class="shrink-0 pt-1"
        >
          <p class="workzone-eyebrow !text-[9px] mb-1">特征点记录</p>
          <div class="space-y-0">
            <div v-for="row in snapshotRows" :key="row.key" class="flex items-baseline justify-between py-1.5 border-b border-line-soft/50 last:border-b-0">
              <span class="text-[11px] text-ink-muted">{{ row.label }}</span>
              <span class="font-semibold text-ink-strong tabular-nums text-[13px]">{{ row.value }}<span v-if="row.unit" class="text-[10px] font-normal text-ink-faint ml-1">{{ row.unit }}</span></span>
            </div>
          </div>
        </div>
        <div class="flex gap-2 shrink-0 mt-auto pt-1">
          <button
            v-if="deviceState !== 'acquiring'"
            type="button"
            class="btn-brand flex-1 py-2 rounded-xl text-xs font-bold btn-active-scale disabled:opacity-50"
            :disabled="!deviceConnected || deviceBusy"
            @click="$emit('start-acquire')"
          >
            {{ deviceState === 'completed' ? '重新采集' : '开始采集' }}
          </button>
          <button
            v-else
            type="button"
            class="btn-ghost flex-1 py-2 rounded-xl text-xs font-bold btn-active-scale"
            @click="$emit('stop-acquire')"
          >
            停止采集
          </button>
        </div>
      </template>

      <!-- 尺寸 / 断后测量 -->
      <template v-else>
        <div v-if="reading" class="flex items-center justify-center py-8 text-[11px] text-ink-muted gap-2">
          <span class="chat-loading-dots"><span /><span /><span /></span>
          正在读取传感器…
        </div>
        <div v-else-if="snapshotRows.length" class="space-y-0">
          <div v-for="row in snapshotRows" :key="row.key" class="flex items-baseline justify-between py-2 border-b border-line-soft/50 last:border-b-0">
            <span class="text-[11px] text-ink-muted">{{ row.label }}</span>
            <span class="font-semibold text-ink-strong tabular-nums text-[13px]">{{ row.value }}<span v-if="row.unit" class="text-[10px] font-normal text-ink-faint ml-1">{{ row.unit }}</span></span>
          </div>
        </div>
        <div v-else class="py-8 text-center text-[11px] text-ink-faint">尚未读取数据，请点下方按钮采集。</div>

        <button
          v-if="deviceState !== 'completed' && !reading"
          type="button"
          class="btn-brand w-full py-2.5 rounded-xl text-xs font-bold btn-active-scale disabled:opacity-50 shrink-0 mt-auto"
          :disabled="!deviceConnected || deviceBusy"
          @click="$emit('read-once')"
        >
          读取测量数据
        </button>
        <button
          v-else-if="deviceState === 'completed'"
          type="button"
          class="btn-ghost w-full py-2 rounded-xl text-[11px] font-semibold btn-active-scale shrink-0 mt-auto"
          :disabled="deviceBusy"
          @click="$emit('read-once')"
        >
          重新读取
        </button>
      </template>

      <div v-if="validationErrors.length" class="rounded-lg bg-red-50 border border-red-100 px-2.5 py-2 text-[11px] text-red-700 space-y-0.5 shrink-0">
        <p v-for="(err, i) in validationErrors" :key="i">{{ err }}</p>
      </div>
    </div>

    <div class="shrink-0 px-5 pb-4 pt-1 space-y-1">
      <p v-if="submitHint" class="text-[10px] text-amber-700 leading-snug">{{ submitHint }}</p>
      <button
        type="button"
        class="btn-brand w-full py-2.5 rounded-xl text-xs font-bold btn-active-scale disabled:opacity-50"
        :disabled="!canSubmit"
        @click="$emit('submit')"
      >
        {{ submitting ? '分析中…' : '提交数据并纠错' }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import StressStrainChart from './StressStrainChart.vue'

const props = defineProps({
  stepTitle: { type: String, default: '' },
  deviceType: { type: String, default: '' },
  deviceName: { type: String, default: '' },
  deviceState: { type: String, default: 'idle' },
  deviceConnected: { type: Boolean, default: false },
  deviceBusy: { type: Boolean, default: false },
  samplingHz: { type: Number, default: 0 },
  fields: { type: Array, default: () => [] },
  snapshot: { type: Object, default: () => ({}) },
  live: { type: Object, default: () => ({}) },
  curvePoints: { type: Array, default: () => [] },
  reading: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  validationErrors: { type: Array, default: () => [] },
  canSubmitData: { type: Boolean, default: false },
  acquireProgress: { type: Object, default: null }
})

defineEmits(['start-acquire', 'stop-acquire', 'read-once', 'submit'])

const progressPct = computed(() => {
  const p = props.acquireProgress
  if (!p?.total) return 0
  return Math.min(100, Math.round((p.current / p.total) * 100))
})

const stateLabel = computed(() => {
  const map = {
    idle: '未连接',
    ready: '已就绪',
    acquiring: '采集中',
    completed: '采集完成'
  }
  return map[props.deviceState] || props.deviceState
})

const stateChipClass = computed(() => {
  if (props.deviceState === 'acquiring') return 'text-brand-600 bg-brand-50 border-brand-200'
  if (props.deviceState === 'completed') return 'text-emerald-600 bg-emerald-50 border-emerald-200'
  if (props.deviceConnected) return 'text-slate-600 bg-slate-50 border-line-soft'
  return ''
})

const stateDotClass = computed(() => {
  if (props.deviceState === 'acquiring') return 'bg-brand-500 animate-pulse'
  if (props.deviceState === 'completed') return 'bg-emerald-500'
  if (props.deviceConnected) return 'bg-emerald-500'
  return 'bg-ink-faint'
})

const snapshotRows = computed(() => {
  const snap = props.snapshot || {}
  return props.fields
    .map((f) => {
      const v = snap[f.key]
      if (v == null || v === '') return null
      return { key: f.key, label: f.label, value: v, unit: f.unit || '' }
    })
    .filter(Boolean)
})

const hasRequiredSnapshot = computed(() => {
  const required = props.fields.filter((f) => f.required !== false)
  if (!required.length) return Object.keys(props.snapshot || {}).length > 0
  return required.every((f) => {
    const v = props.snapshot?.[f.key]
    return v != null && String(v).trim() !== ''
  })
})

const canSubmit = computed(() => {
  if (props.submitting || props.reading || props.deviceBusy) return false
  if (props.deviceState === 'acquiring') return false
  return props.canSubmitData || hasRequiredSnapshot.value
})

const submitHint = computed(() => {
  if (canSubmit.value) return ''
  if (props.deviceState === 'acquiring') return '试验机采集中，请等待曲线完成。'
  if (props.deviceState !== 'completed' && !hasRequiredSnapshot.value) {
    return '请先采集或读取数据。'
  }
  if (!hasRequiredSnapshot.value) return '缺少必填读数，请重新采集。'
  return ''
})

function fmt(v) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  return Number.isFinite(n) ? (Math.abs(n) >= 100 ? n.toFixed(1) : n.toFixed(2)) : v
}
</script>
