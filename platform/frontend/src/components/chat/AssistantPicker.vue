<template>
  <div class="assistant-picker" :class="{ 'assistant-picker--expanded': expanded }" role="group" aria-label="选择回复助手">
    <button
      v-for="assistant in assistants"
      :key="assistant.value"
      type="button"
      class="assistant-option"
      :class="{ 'assistant-option--active': modelValue === assistant.value }"
      :aria-pressed="modelValue === assistant.value"
      :disabled="disabled"
      @click="$emit('update:modelValue', assistant.value)"
    >
      <span class="assistant-option-head">
        <span class="assistant-name">{{ assistant.name }}</span>
        <span v-if="expanded" class="assistant-status">
          <svg v-if="modelValue === assistant.value" aria-hidden="true" viewBox="0 0 16 16"><path d="m3 8 3 3 7-7" /></svg>
          {{ modelValue === assistant.value ? '使用中' : '切换使用' }}
        </span>
      </span>
      <span v-if="expanded" class="assistant-description">{{ assistant.description }}</span>
    </button>
  </div>
</template>

<script setup>
defineProps({ modelValue: { type: String, default: 'dify' }, disabled: Boolean, expanded: Boolean })
defineEmits(['update:modelValue'])
const assistants = [
  { value: 'dify', name: '实验助教', description: '实验步骤与操作答疑' },
  { value: 'octop', name: '专家顾问', description: '实验原理与分析思路' }
]
</script>

<style scoped>
.assistant-picker { @apply grid grid-cols-2 gap-2; }
.assistant-option { @apply min-h-[44px] rounded-lg border border-line-soft bg-white px-3 py-2 text-left text-ink-muted transition-colors hover:border-brand-300 hover:bg-surface-soft focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 disabled:opacity-50 disabled:cursor-not-allowed; }
.assistant-option--active { @apply border-brand-500 bg-brand-50 text-brand-800; }
.assistant-option-head { @apply flex flex-wrap items-center justify-between gap-x-2 gap-y-1; }
.assistant-name { @apply text-[13px] font-bold; }
.assistant-status { @apply flex items-center gap-1 text-[11px] whitespace-nowrap; }
.assistant-status svg { @apply w-3.5 h-3.5 fill-none stroke-current; stroke-width: 2; }
.assistant-description { @apply block mt-1 text-xs leading-relaxed text-ink-muted; }
.assistant-picker--expanded .assistant-option { @apply px-3 py-3; }
.assistant-picker--expanded .assistant-name { @apply text-sm; }
</style>
