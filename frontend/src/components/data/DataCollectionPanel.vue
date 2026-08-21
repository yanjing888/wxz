<template>
  <section class="flex-1 flex flex-col overflow-hidden min-h-0">
    <div v-if="submissionCount || lastSaved" class="flex items-center justify-between px-5 pb-1 shrink-0">
      <span v-if="submissionCount" class="text-[10px] text-ink-muted">
        本步骤已提交 {{ submissionCount }} 次
      </span>
      <span v-if="lastSaved" class="text-[10px] font-semibold text-emerald-600 flex items-center gap-1 ml-auto">
        <span class="w-1 h-1 rounded-full bg-emerald-500" /> 已有记录
      </span>
    </div>

    <div class="flex-1 min-h-0 overflow-y-auto custom-scroll px-5 pb-3" @keydown.enter.prevent="onSubmit">
      <div
        v-for="field in fields"
        :key="field.key"
        class="field-row"
        :class="{ 'is-required': field.required }"
      >
        <span class="field-label">
          {{ field.label }}
        </span>
        <input
          v-model="localValues[field.key]"
          :type="field.type === 'number' ? 'number' : 'text'"
          step="any"
          class="field-input"
          :placeholder="field.placeholder || '—'"
          :disabled="readOnly || submitting || isComputedField(field) || field.readOnly"
        />
        <span class="field-unit">{{ field.unit || '' }}</span>
        <button
          v-if="!isComputedField(field) && !field.readOnly"
          type="button"
          class="field-photo-btn"
          :disabled="readOnly || submitting"
          title="拍照识别该读数"
          @click="$emit('recognize-field', field)"
        >
          拍照识别
        </button>
        <span v-else class="field-computed-label">自动计算</span>
      </div>

      <p v-if="!fields.length" class="text-[11px] text-ink-faint text-center py-6">本步骤暂无需要采集的数据。</p>

      <div v-if="validationErrors.length" class="mt-2 rounded-lg bg-red-50 border border-red-100 px-3 py-2 text-[11px] text-red-700 space-y-0.5">
        <p v-for="(err, i) in validationErrors" :key="i">{{ err }}</p>
      </div>
    </div>

    <div class="shrink-0 px-5 pb-4 pt-1">
      <button
        type="button"
        class="btn-brand w-full py-2.5 rounded-xl text-xs font-bold btn-active-scale disabled:opacity-50"
        :disabled="readOnly || submitting || !fields.length"
        @click="onSubmit"
      >
        {{ submitting ? '分析中…' : '提交数据并纠错' }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  fields: { type: Array, default: () => [] },
  stepTitle: { type: String, default: '' },
  values: { type: Object, default: () => ({}) },
  submissionCount: { type: Number, default: 0 },
  lastSaved: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  validationErrors: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false }
})

const emit = defineEmits(['submit', 'recognize-field', 'update:values'])

const localValues = ref({})
let syncingFromProps = false

const fieldKeys = computed(() => props.fields.map((f) => f.key).join(','))

watch(
  () => [fieldKeys.value, props.values],
  () => {
    const next = {}
    for (const f of props.fields) {
      const v = props.values?.[f.key]
      next[f.key] = v != null ? String(v) : ''
    }
    syncingFromProps = true
    localValues.value = withComputedValues(next)
    syncingFromProps = false
  },
  { immediate: true, deep: true }
)

watch(
  localValues,
  () => {
    if (syncingFromProps) return
    const next = withComputedValues(localValues.value)
    if (JSON.stringify(next) !== JSON.stringify(localValues.value)) {
      syncingFromProps = true
      localValues.value = next
      syncingFromProps = false
    }
    emit('update:values', payloadFromValues(next))
  },
  { deep: true }
)

function isComputedField(field) {
  return !!String(field?.computed || '').trim()
}

function withComputedValues(values) {
  const next = { ...(values || {}) }
  for (const field of props.fields) {
    if (!isComputedField(field)) continue
    const computed = computeExpression(field.computed, next)
    next[field.key] = computed == null ? '' : formatNumber(computed)
  }
  return next
}

function computeExpression(expression, values) {
  const expr = String(expression || '').trim()
  const abs = expr.match(/^abs\((.+)\)$/i)
  if (abs) {
    const inner = computeExpression(abs[1], values)
    return inner == null ? null : Math.abs(inner)
  }
  const binary = expr.match(/^\s*([a-zA-Z_][\w]*)\s*([+\-*/])\s*([a-zA-Z_][\w]*)\s*$/)
  if (!binary) return null
  const left = Number(values[binary[1]])
  const right = Number(values[binary[3]])
  if (!Number.isFinite(left) || !Number.isFinite(right)) return null
  switch (binary[2]) {
    case '+': return left + right
    case '-': return left - right
    case '*': return left * right
    case '/': return right === 0 ? null : left / right
    default: return null
  }
}

function formatNumber(value) {
  if (!Number.isFinite(value)) return ''
  return Number(value.toFixed(6)).toString()
}

function payloadFromValues(values) {
  const payload = {}
  for (const f of props.fields) {
    const raw = values?.[f.key]
    if (raw == null || String(raw).trim() === '') continue
    payload[f.key] = String(raw).trim()
  }
  return payload
}

function onSubmit() {
  emit('submit', payloadFromValues(withComputedValues(localValues.value)))
}
</script>

<style scoped>
.field-photo-btn {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 7px;
  border: 1px solid rgba(199, 210, 254, 0.9);
  background: #eef2ff;
  color: #4f46e5;
  font-size: 11px;
  font-weight: 700;
  transition: background 0.16s ease, border-color 0.16s ease, transform 0.16s ease;
}
.field-photo-btn:hover:not(:disabled) {
  background: #e0e7ff;
  border-color: #a5b4fc;
}
.field-photo-btn:active:not(:disabled) {
  transform: scale(0.98);
}
.field-photo-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.field-computed-label {
  flex-shrink: 0;
  min-width: 52px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 600;
}
</style>
