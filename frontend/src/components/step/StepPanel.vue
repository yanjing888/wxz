<template>
  <section class="shrink-0 px-5 pt-5 pb-4">
    <div class="flex items-start justify-between gap-2 mb-2">
      <div class="flex items-center gap-2 min-w-0">
        <span class="workzone-eyebrow">STEP {{ String(activeStep).padStart(2, '0') }} / {{ String(menuLabels.length).padStart(2, '0') }}</span>
      </div>
      <button
        type="button"
        class="shrink-0 flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-medium text-ink-muted hover:text-brand-600 hover:bg-brand-50 transition-colors btn-active-scale"
        title="查看本步骤操作教程"
        @click="$emit('tutorial')"
      >
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
        本步骤教程
      </button>
    </div>

    <div class="fade-in-up" :key="activeStep">
      <h3 class="text-[18px] font-bold text-ink-strong leading-snug mb-1.5 tracking-tight">{{ step?.title || '—' }}</h3>
      <p class="text-[12px] text-ink-muted leading-relaxed line-clamp-3">{{ step?.desc || '请选择当前进行的实验环节。' }}</p>
    </div>

    <div class="mt-3 flex items-center justify-between">
      <button
        type="button"
        class="flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-medium text-ink-muted hover:text-brand-600 hover:bg-brand-50 disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-ink-muted transition-all btn-active-scale"
        :disabled="activeStep <= 1"
        @click="$emit('select', activeStep - 1)"
      >
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" /></svg>
        上一步
      </button>
      <button
        type="button"
        class="flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-medium text-ink-muted hover:text-brand-600 hover:bg-brand-50 disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-ink-muted transition-all btn-active-scale"
        :disabled="activeStep >= menuLabels.length"
        @click="$emit('select', activeStep + 1)"
      >
        下一步
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" /></svg>
      </button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  menuLabels: { type: Array, default: () => [] },
  activeStep: { type: Number, default: 1 },
  step: { type: Object, default: null }
})

defineEmits(['select', 'tutorial'])
</script>
