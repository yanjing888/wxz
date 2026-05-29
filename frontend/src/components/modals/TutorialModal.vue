<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center p-6 bg-slate-900/20 backdrop-blur-sm" @click.self="$emit('close')">
    <div class="relative w-full max-w-3xl max-h-[90vh] glass-panel rounded-2xl overflow-hidden flex flex-col shadow-2xl bg-white">
      <div class="p-5 border-b border-[#dbe3ee] flex items-center justify-between shrink-0">
        <div>
          <h3 class="text-xl font-bold text-[#1e3a5f]">{{ step?.title || '多模态实验指导' }}</h3>
          <p class="text-xs text-slate-500 mt-1">{{ experimentName }}</p>
        </div>
        <button type="button" class="text-slate-400 hover:text-slate-600 text-2xl leading-none" @click="$emit('close')">×</button>
      </div>
      <div class="p-6 overflow-y-auto custom-scroll flex-1 space-y-6">
        <div class="aspect-video rounded-2xl bg-slate-900 overflow-hidden relative border border-[#dbe3ee] flex items-center justify-center">
          <p class="text-slate-400 text-sm">步骤 {{ stepNo }} · 动画演示占位</p>
        </div>
        <div>
          <h4 class="text-sm font-bold text-[#1e3a5f] mb-3">操作步骤</h4>
          <ol class="space-y-2">
            <li v-for="(s, i) in step?.tut?.steps || []" :key="i" class="text-xs text-slate-600 flex gap-2">
              <span class="w-5 h-5 bg-blue-100 text-blue-600 rounded flex items-center justify-center text-[10px] font-bold shrink-0">{{ i + 1 }}</span>
              {{ s }}
            </li>
          </ol>
        </div>
        <div v-if="step?.tut?.warnings?.length">
          <h4 class="text-sm font-bold text-red-600 mb-3">注意事项</h4>
          <ul class="space-y-2 bg-red-50 p-4 rounded-xl border border-red-100">
            <li v-for="(w, i) in step.tut.warnings" :key="i" class="text-xs text-red-700">• {{ w }}</li>
          </ul>
        </div>
      </div>
      <div class="p-4 border-t border-[#dbe3ee] bg-[#f7f9fc] flex justify-end shrink-0">
        <button type="button" class="px-8 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-bold text-sm btn-active-scale" @click="$emit('close')">已了解，返回实验</button>
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
