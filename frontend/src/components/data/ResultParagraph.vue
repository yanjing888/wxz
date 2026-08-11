<template>
  <section class="para-block">
    <div class="flex items-center justify-between gap-3 mb-2">
      <h4 class="para-title">报告用段落</h4>
      <span v-if="copied" class="copied-tag">已复制</span>
    </div>
    <pre class="para-text">{{ text }}</pre>
    <div class="flex flex-wrap gap-2 mt-3">
      <button type="button" class="btn-brand px-4 py-2 rounded-lg text-[13px] font-semibold" @click="copy">复制段落</button>
      <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px]" :disabled="loading" @click="emit('explain')">
        {{ loading ? '生成中…' : 'AI 补充说明' }}
      </button>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['explain'])
const copied = ref(false)

function copy() {
  navigator.clipboard?.writeText(props.text).then(() => {
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  }).catch(() => {})
}
</script>

<style scoped>
.para-block { @apply mt-5 pt-4 border-t border-line-soft; }
.para-title { @apply text-[14px] font-bold text-ink-strong; }
.copied-tag { @apply text-[11px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-100; }
.para-text {
  @apply text-[13px] leading-relaxed whitespace-pre-wrap bg-surface-soft/60 p-3 rounded-lg
    border border-line-soft font-sans text-ink-base;
}
</style>
