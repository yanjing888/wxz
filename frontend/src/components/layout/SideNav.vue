<template>
  <aside class="w-[76px] bg-white/85 backdrop-blur-md border-r border-line-soft flex flex-col items-center py-6 shrink-0 z-30">
    <div class="relative mb-10">
      <div class="w-11 h-11 brand-gradient rounded-2xl flex items-center justify-center font-bold text-white text-base shadow-brand">智</div>
      <span class="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-1.5 h-1.5 rounded-full bg-accent-cyan ring-2 ring-white" />
    </div>

    <nav class="flex flex-col gap-3 text-ink-faint w-full px-2">
      <div class="group relative flex flex-col items-center" title="实验台">
        <div class="w-11 h-11 rounded-2xl bg-brand-50 text-brand-600 flex items-center justify-center shadow-card border border-brand-100">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" /></svg>
        </div>
        <span class="mt-1 text-[10px] font-bold text-brand-600">实验台</span>
      </div>

      <button
        type="button"
        class="group relative flex flex-col items-center"
        title="实验数据速览"
        @click="$emit('quick-stats')"
      >
        <div class="w-11 h-11 rounded-2xl bg-white text-ink-muted flex items-center justify-center border border-line-soft transition-all group-hover:text-brand-600 group-hover:border-brand-300 group-hover:bg-brand-50 btn-active-scale">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
        </div>
        <span class="mt-1 text-[10px] font-medium text-ink-faint group-hover:text-brand-600 transition-colors">速览</span>
      </button>
    </nav>

    <div class="mt-auto flex flex-col items-center gap-2 text-[9px] text-ink-faint">
      <span class="w-px h-6 bg-line-soft" />
      <span class="font-mono tracking-widest">v1.0</span>
    </div>
  </aside>
</template>

<script setup>
defineEmits(['quick-stats'])
</script>
