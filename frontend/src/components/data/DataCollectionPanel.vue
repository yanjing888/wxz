<template>
  <section class="flex-1 flex flex-col overflow-hidden relative min-h-0 surface-card rounded-2xl">
    <div class="shrink-0 flex items-center justify-between px-3 py-2 border-b border-line-soft gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <div class="w-7 h-7 rounded-lg brand-gradient-soft border border-brand-100 flex items-center justify-center text-brand-600 shrink-0">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h7" />
          </svg>
        </div>
        <div class="flex flex-col leading-tight min-w-0">
          <h2 class="text-xs font-bold text-ink-strong truncate">数据采集纠错</h2>
          <span class="text-[9px] text-ink-faint truncate">填写本步骤测量数据后提交</span>
        </div>
      </div>
      <span v-if="lastSaved" class="chip text-emerald-600 bg-emerald-50 border-emerald-200 !text-[9px] !px-1.5 shrink-0">已提交</span>
    </div>

    <form class="flex-1 min-h-0 overflow-y-auto px-3 py-3 space-y-2.5" @submit.prevent="onSubmit">
      <p v-if="stepTitle" class="text-[10px] text-ink-muted leading-snug">
        当前步骤：<span class="font-semibold text-ink-strong">{{ stepTitle }}</span>
      </p>

      <div v-for="field in fields" :key="field.key" class="space-y-1">
        <label class="text-[10px] font-semibold text-ink-strong flex items-center gap-1">
          {{ field.label }}
          <span v-if="field.unit" class="text-ink-faint font-normal">({{ field.unit }})</span>
          <span v-if="field.required" class="text-red-500">*</span>
        </label>
        <input
          v-if="field.type === 'number'"
          v-model="localValues[field.key]"
          type="number"
          step="any"
          class="w-full px-2.5 py-2 rounded-lg border border-line-soft text-xs bg-white focus:border-brand-400 focus:ring-1 focus:ring-brand-200 outline-none"
          :placeholder="field.placeholder || '请输入数值'"
        />
        <input
          v-else
          v-model="localValues[field.key]"
          type="text"
          class="w-full px-2.5 py-2 rounded-lg border border-line-soft text-xs bg-white focus:border-brand-400 focus:ring-1 focus:ring-brand-200 outline-none"
          :placeholder="field.placeholder || '请输入'"
        />
      </div>

      <div v-if="validationErrors.length" class="rounded-lg bg-red-50 border border-red-100 px-2.5 py-2 text-[10px] text-red-700 space-y-0.5">
        <p v-for="(err, i) in validationErrors" :key="i">{{ err }}</p>
      </div>

      <button
        type="submit"
        class="btn-brand w-full py-2.5 rounded-xl text-xs font-bold btn-active-scale disabled:opacity-50"
        :disabled="submitting || !fields.length"
      >
        {{ submitting ? '分析中…' : '提交数据并纠错' }}
      </button>
    </form>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  fields: { type: Array, default: () => [] },
  stepTitle: { type: String, default: '' },
  values: { type: Object, default: () => ({}) },
  lastSaved: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  validationErrors: { type: Array, default: () => [] }
})

const emit = defineEmits(['submit'])

const localValues = ref({})

const fieldKeys = computed(() => props.fields.map((f) => f.key).join(','))

watch(
  () => [fieldKeys.value, props.values],
  () => {
    const next = {}
    for (const f of props.fields) {
      const v = props.values?.[f.key]
      next[f.key] = v != null ? String(v) : ''
    }
    localValues.value = next
  },
  { immediate: true, deep: true }
)

function onSubmit() {
  const payload = {}
  for (const f of props.fields) {
    const raw = localValues.value[f.key]
    if (raw == null || String(raw).trim() === '') continue
    payload[f.key] = f.type === 'number' ? String(raw).trim() : String(raw).trim()
  }
  emit('submit', payload)
}
</script>
