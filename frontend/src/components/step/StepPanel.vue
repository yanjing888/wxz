<template>
  <section class="h-1/3 shrink-0 p-4 flex flex-col border-b border-[#dbe3ee] bg-white overflow-hidden">
    <h2 class="text-[10px] font-bold text-slate-500 uppercase tracking-widest flex items-center gap-2 mb-2">
      <span class="w-2 h-2 bg-blue-500 rounded-full" />
      请自主选择当前实验步骤
    </h2>
    <div class="flex-1 flex gap-2 overflow-hidden min-h-0">
      <div class="w-48 bg-[#f8fafc] rounded-xl border border-[#dbe3ee] overflow-y-auto custom-scroll p-1 shrink-0">
        <div
          v-for="(label, idx) in menuLabels"
          :key="idx"
          class="step-item p-2 rounded-lg cursor-pointer mb-1"
          :class="activeStep === idx + 1 ? 'active' : 'text-slate-600 hover:bg-slate-50'"
          @click="$emit('select', idx + 1)"
        >
          <p class="text-[10px] font-mono opacity-50">STEP {{ String(idx + 1).padStart(2, '0') }}</p>
          <p class="text-[11px] font-bold truncate">{{ label }}</p>
        </div>
      </div>
      <div class="flex-1 glass-panel rounded-xl p-4 flex flex-col justify-between overflow-hidden min-w-0">
        <div class="overflow-y-auto custom-scroll pr-2">
          <h3 class="text-sm font-bold text-[#1e3a5f] mb-1">{{ step?.title }}</h3>
          <p class="text-[11px] text-slate-600 leading-relaxed">{{ step?.desc }}</p>
        </div>
        <button type="button" class="mt-2 flex items-center justify-center gap-2 px-3 py-1.5 bg-blue-50 hover:bg-blue-100 text-[#2c6fb8] border border-blue-200 rounded-lg text-[10px] font-bold transition-all btn-active-scale" @click="$emit('tutorial')">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" /></svg>
          查看本步骤教程
        </button>
      </div>
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
