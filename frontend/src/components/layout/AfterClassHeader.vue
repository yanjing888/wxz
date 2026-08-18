<template>
  <header class="after-head shrink-0">
    <router-link :to="labRoute" class="back-link">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
      </svg>
      返回实验台
    </router-link>
    <h1 class="after-title">{{ title }}</h1>
    <p v-if="subtitle" class="after-sub">{{ subtitle }}</p>
  </header>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' }
})

const labRoute = computed(() => ({ name: 'lab', query: { exp: props.experimentCode } }))
</script>

<style scoped>
.after-head { @apply px-5 py-2.5 bg-white border-b border-line-soft; }
.back-link {
  @apply inline-flex items-center gap-1 text-[12px] font-medium text-ink-muted
    hover:text-brand-600 transition-colors mb-0.5;
}
.after-title { @apply text-[15px] font-bold text-ink-strong; }
.after-sub { @apply text-[12px] text-ink-muted mt-0.5; }
</style>
