<template>
  <div class="cap-nav">
    <button
      v-for="step in steps"
      :key="step.key"
      type="button"
      class="cap-item"
      :class="itemClass(step)"
      @click="$emit('select', step.key)"
    >
      <span class="cap-dot" :class="dotClass(step)" />
      <span class="cap-label">{{ step.label }}</span>
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  steps: { type: Array, default: () => [] },
  current: { type: String, default: '' }
})

defineEmits(['select'])

function itemClass(step) {
  return {
    'cap-item--selected': step.key === props.current,
    'cap-item--done': step.status === 'done',
    'cap-item--progress': step.status === 'in_progress'
  }
}

function dotClass(step) {
  return {
    'cap-dot--done': step.status === 'done',
    'cap-dot--progress': step.status === 'in_progress',
    'cap-dot--selected': step.key === props.current && step.status !== 'done'
  }
}
</script>

<style scoped>
.cap-nav {
  @apply flex flex-wrap items-center gap-2;
}
.cap-item {
  @apply flex items-center gap-2 px-3 py-2 rounded-xl border border-line-soft bg-white text-left transition-colors hover:border-brand-200 hover:bg-brand-50/40;
}
.cap-item--selected {
  @apply border-brand-300 bg-brand-50 text-brand-800 shadow-sm;
}
.cap-item--done {
  @apply border-emerald-200 bg-emerald-50/60 text-emerald-800;
}
.cap-item--progress {
  @apply border-amber-200 bg-amber-50/60 text-amber-900;
}
.cap-dot {
  @apply w-2 h-2 rounded-full bg-ink-faint shrink-0;
}
.cap-dot--done {
  @apply bg-emerald-500;
}
.cap-dot--progress {
  @apply bg-amber-500;
}
.cap-dot--selected {
  @apply bg-brand-500;
}
.cap-label {
  @apply text-[13px] font-semibold whitespace-nowrap;
}
</style>
