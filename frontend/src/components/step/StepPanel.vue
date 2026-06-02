<template>
  <section class="surface-card rounded-2xl p-4 shrink-0">
    <!-- 头部：标签 + 总览 + 教程 -->
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <span class="section-label">实验步骤</span>
      </div>
      <button
        type="button"
        class="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-bold text-brand-700 bg-brand-50 hover:bg-brand-100 border border-brand-100 btn-active-scale transition-all"
        @click="$emit('tutorial')"
      >
        <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
        本步骤教程
      </button>
    </div>

    <!-- 当前步骤详情 -->
    <div class="fade-in-up" :key="activeStep">
      <div class="flex items-center gap-2 mb-1.5">
        <span class="px-1.5 py-0.5 text-[10px] font-bold brand-gradient text-white rounded font-mono tracking-wider">
          STEP {{ String(activeStep).padStart(2, '0') }}
        </span>
        <span class="text-[10px] text-ink-faint font-mono">{{ String(activeStep).padStart(2, '0') }} / {{ String(menuLabels.length).padStart(2, '0') }}</span>
      </div>
      <h3 class="text-[15px] font-bold text-ink-strong leading-snug mb-1.5">{{ step?.title || '—' }}</h3>
      <p class="text-[12px] text-ink-muted leading-relaxed line-clamp-3">{{ step?.desc || '请选择当前进行的实验环节。' }}</p>
    </div>

    <!-- 上一步 / 下一步 -->
    <div class="mt-3 pt-3 border-t border-line-soft flex items-center justify-between">
      <button
        type="button"
        class="flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-medium text-ink-muted hover:text-brand-600 hover:bg-brand-50 disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-ink-muted transition-all btn-active-scale"
        :disabled="activeStep <= 1"
        @click="$emit('select', activeStep - 1)"
      >
        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" /></svg>
        上一步
      </button>
      <span class="text-[10px] text-ink-faint truncate max-w-[140px]" :title="step?.title">{{ step?.title || '' }}</span>
      <button
        type="button"
        class="flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-medium text-ink-muted hover:text-brand-600 hover:bg-brand-50 disabled:opacity-30 disabled:cursor-not-allowed disabled:hover:bg-transparent disabled:hover:text-ink-muted transition-all btn-active-scale"
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
