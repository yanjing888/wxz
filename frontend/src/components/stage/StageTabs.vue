<template>
  <nav class="stage-tabs shrink-0" aria-label="阶段功能">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      type="button"
      class="tab"
      :class="{ 'tab--active': modelValue === tab.key, 'tab--off': unavailable.includes(tab.tool) }"
      @click="$emit('update:modelValue', tab.key)"
    >
      {{ tab.label }}
      <span v-if="unavailable.includes(tab.tool)" class="tab-dot" title="尚未接入 AI 服务" />
    </button>
  </nav>
</template>

<script setup>
defineProps({
  tabs: { type: Array, required: true },
  modelValue: { type: String, default: '' },
  unavailable: { type: Array, default: () => [] }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.stage-tabs {
  @apply flex items-center gap-1 px-5 py-2 bg-white border-b border-line-soft overflow-x-auto;
}
.tab {
  @apply relative flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-[13px] text-ink-muted
    whitespace-nowrap transition-colors hover:bg-surface-soft hover:text-ink-strong;
}
.tab--active { @apply bg-brand-50 text-brand-700 font-semibold; }
.tab--off { @apply text-ink-faint; }
.tab-dot { @apply w-1.5 h-1.5 rounded-full bg-amber-400; }
</style>
