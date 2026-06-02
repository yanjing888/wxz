<template>
  <div v-if="visible" class="fixed inset-0 z-[55] flex items-center justify-center p-6 bg-slate-900/30 backdrop-blur-md" @click.self="$emit('close')">
    <div class="relative w-full max-w-md rounded-3xl p-7 bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="flex items-center gap-3 mb-5">
        <div class="w-10 h-10 rounded-xl brand-gradient text-white flex items-center justify-center shadow-brand font-bold">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
        </div>
        <div>
          <h3 class="text-lg font-bold text-ink-strong leading-tight">实验数据速览</h3>
          <p class="text-[11px] text-ink-faint">当前会话实时统计</p>
        </div>
      </div>

      <ul class="space-y-2.5 text-sm">
        <li class="flex justify-between items-center p-3 rounded-xl bg-surface-soft border border-line-soft">
          <span class="text-ink-muted text-[12px]">当前步骤</span>
          <span class="font-semibold text-ink-base text-right max-w-[60%] truncate">{{ stepTitle || '—' }}</span>
        </li>
        <li class="flex justify-between items-center p-3 rounded-xl bg-surface-soft border border-line-soft">
          <span class="text-ink-muted text-[12px]">有效纠错次数</span>
          <span class="font-mono font-bold brand-text-gradient text-base">{{ helpCount }}</span>
        </li>
        <li class="flex justify-between items-center p-3 rounded-xl bg-surface-soft border border-line-soft">
          <span class="text-ink-muted text-[12px]">纠错标注点数</span>
          <span class="font-mono font-bold text-red-500 text-base">{{ errorPointCount }}</span>
        </li>
        <li class="flex justify-between items-center p-3 rounded-xl bg-surface-soft border border-line-soft">
          <span class="text-ink-muted text-[12px]">教程查阅次数</span>
          <span class="font-mono font-bold text-emerald-500 text-base">{{ tutViewCount }}</span>
        </li>
        <li class="flex justify-between items-center p-3 rounded-xl bg-red-50/60 border border-red-100">
          <span class="text-ink-muted text-[12px]">严重告警 (L3)</span>
          <span class="font-mono font-bold text-red-500 text-base">{{ labL3Count }}</span>
        </li>
      </ul>

      <button
        type="button"
        class="mt-6 w-full py-2.5 bg-surface-muted hover:bg-surface-soft text-ink-base border border-line-soft rounded-xl text-sm font-bold btn-active-scale transition-colors"
        @click="$emit('close')"
      >
        关闭
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: Boolean,
  stepTitle: String,
  helpCount: Number,
  errorPointCount: Number,
  tutViewCount: Number,
  labL3Count: Number
})
defineEmits(['close'])
</script>
