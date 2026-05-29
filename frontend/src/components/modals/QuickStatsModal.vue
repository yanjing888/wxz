<template>
  <div v-if="visible" class="fixed inset-0 z-[55] flex items-center justify-center p-6 bg-slate-900/20 backdrop-blur-sm" @click.self="$emit('close')">
    <div class="relative w-full max-w-md glass-panel rounded-2xl p-6 shadow-2xl bg-white">
      <h3 class="text-lg font-bold text-[#1e3a5f] mb-4">实验数据速览</h3>
      <ul class="space-y-3 text-sm">
        <li class="flex justify-between border-b border-slate-100 pb-2"><span class="text-slate-500">当前步骤</span><span class="font-medium text-slate-800 text-right max-w-[60%]">{{ stepTitle || '—' }}</span></li>
        <li class="flex justify-between border-b border-slate-100 pb-2"><span class="text-slate-500">有效纠错次数</span><span class="font-mono text-blue-600">{{ helpCount }}</span></li>
        <li class="flex justify-between border-b border-slate-100 pb-2"><span class="text-slate-500">纠错标注点数</span><span class="font-mono text-red-500">{{ errorPointCount }}</span></li>
        <li class="flex justify-between border-b border-slate-100 pb-2"><span class="text-slate-500">教程查阅次数</span><span class="font-mono text-emerald-500">{{ tutViewCount }}</span></li>
        <li class="flex justify-between"><span class="text-slate-500">严重告警(L3)</span><span class="font-mono text-red-500">{{ labL3Count }}</span></li>
      </ul>
      <button type="button" class="mt-6 w-full py-2 bg-slate-100 hover:bg-slate-200 rounded-xl text-sm font-bold text-slate-600" @click="$emit('close')">关闭</button>
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
