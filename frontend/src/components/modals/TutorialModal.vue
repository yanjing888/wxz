<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center p-6 bg-slate-900/30 backdrop-blur-md" @click.self="$emit('close')">
    <div class="relative w-full max-w-3xl max-h-[90vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full brand-gradient shrink-0" />
      <div class="p-6 border-b border-line-soft flex items-center justify-between shrink-0">
        <div>
          <div class="flex items-center gap-2 mb-1.5">
            <span class="chip text-brand-700 bg-brand-50 border-brand-100">
              <span class="font-mono">STEP {{ String(stepNo).padStart(2, '0') }}</span>
            </span>
            <span class="text-[11px] text-ink-faint">{{ experimentName }}</span>
          </div>
          <h3 class="text-xl font-bold text-ink-strong">{{ step?.title || '多模态实验指导' }}</h3>
        </div>
        <button
          type="button"
          class="w-9 h-9 rounded-xl text-ink-faint hover:text-ink-strong hover:bg-surface-muted text-2xl leading-none flex items-center justify-center transition-colors"
          @click="$emit('close')"
        >×</button>
      </div>
      <div class="p-6 overflow-y-auto custom-scroll flex-1 space-y-6">
        <div class="aspect-video rounded-2xl bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 overflow-hidden relative border border-line-soft flex items-center justify-center shadow-card">
          <div class="absolute inset-0 opacity-30 bg-[repeating-linear-gradient(0deg,transparent,transparent_2px,rgba(255,255,255,.06)_2px,rgba(255,255,255,.06)_4px)]" />
          <p class="text-slate-300 text-sm relative z-10">步骤 {{ stepNo }} · 动画演示占位</p>
        </div>
        <div>
          <h4 class="section-label mb-3">操作步骤</h4>
          <ol class="space-y-2">
            <li v-for="(s, i) in step?.tut?.steps || []" :key="i" class="text-sm text-ink-base flex gap-3 items-start">
              <span class="w-6 h-6 brand-gradient text-white rounded-lg flex items-center justify-center text-[11px] font-bold shrink-0 shadow-card">{{ i + 1 }}</span>
              <span class="leading-relaxed pt-0.5">{{ s }}</span>
            </li>
          </ol>
        </div>
        <div v-if="step?.tut?.warnings?.length">
          <h4 class="text-sm font-bold text-red-600 mb-3 flex items-center gap-2">
            <span class="w-1 h-4 bg-red-500 rounded-full" />注意事项
          </h4>
          <ul class="space-y-2 bg-red-50 p-4 rounded-2xl border border-red-100">
            <li v-for="(w, i) in step.tut.warnings" :key="i" class="text-xs text-red-700 leading-relaxed flex gap-2">
              <span class="text-red-400">●</span>
              <span>{{ w }}</span>
            </li>
          </ul>
        </div>
      </div>
      <div class="p-4 border-t border-line-soft bg-surface-soft flex justify-end shrink-0">
        <button type="button" class="btn-brand px-8 py-2.5 rounded-xl font-bold text-sm" @click="$emit('close')">已了解，返回实验</button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: Boolean,
  experimentName: String,
  step: Object,
  stepNo: { type: Number, default: 1 }
})
defineEmits(['close'])
</script>
