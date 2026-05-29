<template>
  <header class="flex items-center px-6 py-2 shrink-0 border-b border-[#dbe3ee] bg-white min-h-[3.5rem]">
    <div class="flex items-center gap-4 min-w-0 flex-1">
      <span class="truncate text-[#1e3a5f] text-lg font-bold">{{ experimentName }}</span>
      <span class="w-px h-4 bg-slate-300 shrink-0" />
      <div class="flex items-center gap-2 shrink-0">
        <span v-if="studentClass" class="text-xs text-[#2c6fb8] bg-[#eff6ff] border border-[#bfdbfe] rounded px-2 py-0.5 font-medium">{{ studentClass }}</span>
        <span class="text-xs text-slate-600 truncate">学生：{{ studentName }}</span>
      </div>
    </div>
  </header>
</template>

<script setup>
defineProps({
  experimentName: { type: String, default: '--' },
  studentName: { type: String, default: '--' },
  studentClass: { type: String, default: '' }
})
</script>
