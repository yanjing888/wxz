<template>
  <section class="flex-1 flex flex-col overflow-hidden min-h-0">
    <div v-if="lastSaved" class="flex items-center justify-end px-5 pb-1 shrink-0">
      <span class="text-[10px] font-semibold text-emerald-600 flex items-center gap-1">
        <span class="w-1 h-1 rounded-full bg-emerald-500" /> 已提交
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
        />
        <span class="field-unit">{{ field.unit || '' }}</span>
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
        :disabled="submitting || !fields.length"
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
