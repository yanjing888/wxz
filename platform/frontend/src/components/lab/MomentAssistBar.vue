<template>
  <div v-if="actions.length" class="moment-bar shrink-0">
    <p class="moment-label">此刻可用</p>
    <div class="moment-actions">
      <button
        v-for="action in actions"
        :key="action.key"
        type="button"
        class="moment-btn"
        :class="action.primary ? 'moment-btn--primary' : ''"
        :disabled="disabled"
        @click="$emit('action', action.key)"
      >
        <span class="moment-ai">AI</span>
        {{ action.label }}
      </button>
    </div>
    <p class="moment-hint">{{ hint }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  step: { type: Object, default: null },
  hasData: { type: Boolean, default: false },
  sessionFinished: { type: Boolean, default: false },
  preLabCompleted: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false }
})

defineEmits(['action'])

const actions = computed(() => {
  const step = props.step || {}
  const mode = step.correctionMode || ''
  const hasFields = Array.isArray(step.dataFields) && step.dataFields.length > 0
  const list = []

  // 课上主能力：始终可问助教；按步骤追加专项
  if (mode === 'vision' || !mode) {
    list.push({ key: 'photo-ask', label: '拍照问助教', primary: mode === 'vision' })
  }
  if (mode === 'data' || hasFields || step.dataSource === 'device') {
    list.push({ key: 'reading', label: '拍读数', primary: mode === 'data' })
    list.push({ key: 'doctor', label: '检查这组数据', primary: false })
  } else {
    // 非数据步也允许读数（显微镜调节等）
    list.push({ key: 'reading', label: '拍读数', primary: false })
  }

  if (props.hasData || props.sessionFinished) {
    list.push({ key: 'report', label: '用今天的数据写报告', primary: false })
  }

  if (!props.preLabCompleted) {
    list.unshift({ key: 'prep', label: '先做课前自测', primary: false })
  }

  return list
})

const hint = computed(() => {
  const step = props.step || {}
  if (step.correctionMode === 'vision') {
    return '这一步适合拍照：对准装置或光路，直接问旁边的助教。'
  }
  if (step.correctionMode === 'data' || step.dataSource === 'device') {
    return '这一步在记数据：读不准就拍刻度，记完就体检，别带着离谱数下课。'
  }
  return '卡住就直接打字或拍照问助教，不用先选工具。'
})
</script>

<style scoped>
.moment-bar {
  @apply px-4 pt-3 pb-2 border-b border-line-soft bg-gradient-to-b from-brand-50/40 to-white;
}
.moment-label {
  @apply text-[11px] font-bold uppercase tracking-wide text-brand-700 mb-1.5;
}
.moment-actions { @apply flex flex-wrap gap-2; }
.moment-btn {
  @apply inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[12.5px] font-semibold
    border border-line-soft bg-white text-ink-base
    hover:border-brand-200 hover:text-brand-700 transition-colors
    disabled:opacity-50 disabled:pointer-events-none;
}
.moment-btn--primary {
  @apply border-brand-200 bg-brand-50 text-brand-800;
}
.moment-ai {
  @apply text-[9px] font-bold px-1 py-0.5 rounded bg-brand-600 text-white leading-none;
}
.moment-hint {
  @apply text-[11.5px] text-ink-muted mt-2 leading-relaxed;
}
</style>
