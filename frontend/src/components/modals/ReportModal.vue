<template>
  <div v-if="visible" class="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-slate-900/20 backdrop-blur-sm" @click.self="$emit('close')">
    <div class="relative w-full max-w-4xl max-h-[90vh] glass-panel rounded-3xl overflow-hidden flex flex-col shadow-2xl bg-white">
      <div class="h-1.5 w-full bg-gradient-to-r from-emerald-500 via-blue-500 to-emerald-500 shrink-0" />
      <div class="p-8 overflow-y-auto custom-scroll flex-1">
        <div class="text-center mb-8">
          <h2 class="text-3xl font-black text-[#1e3a5f] mb-2">实验总结报告</h2>
          <p class="text-slate-500 text-sm">生成时间：{{ report?.generatedAt || '--' }}</p>
        </div>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-[#1e3a5f] flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-blue-500 rounded-full" />1. 实操回顾与数据统计
          </h3>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div class="p-4 bg-slate-50 rounded-2xl border border-[#dbe3ee] text-center">
              <p class="text-[10px] text-slate-500 uppercase mb-2 font-bold">有效纠错次数</p>
              <p class="text-2xl font-black text-blue-600">{{ report?.helpCount ?? 0 }}</p>
            </div>
            <div class="p-4 bg-slate-50 rounded-2xl border border-[#dbe3ee] text-center">
              <p class="text-[10px] text-slate-500 uppercase mb-2 font-bold">纠错标注点数</p>
              <p class="text-2xl font-black text-red-500">{{ report?.errorPointCount ?? 0 }}</p>
            </div>
            <div class="p-4 bg-slate-50 rounded-2xl border border-[#dbe3ee] text-center">
              <p class="text-[10px] text-slate-500 uppercase mb-2 font-bold">教程查阅次数</p>
              <p class="text-2xl font-black text-emerald-500">{{ report?.tutViewCount ?? 0 }}</p>
            </div>
            <div class="p-4 bg-slate-50 rounded-2xl border border-[#dbe3ee] text-center">
              <p class="text-[10px] text-slate-500 uppercase mb-2 font-bold">严重告警(L3)</p>
              <p class="text-2xl font-black text-red-500">{{ report?.labL3Count ?? 0 }}</p>
            </div>
          </div>
        </section>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-[#1e3a5f] flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-red-500 rounded-full" />2. 操作纠错记录
          </h3>
          <div v-if="report?.corrections?.length" class="space-y-4">
            <div v-for="(log, i) in report.corrections" :key="i" class="p-5 bg-red-50 rounded-2xl border border-red-100">
              <div class="flex flex-wrap justify-between gap-2 mb-2">
                <span class="text-[10px] font-bold text-red-500 uppercase">{{ log.stepTitle }}</span>
                <span class="text-[10px] px-2 py-0.5 bg-red-100 rounded text-red-600">{{ log.errorType }}</span>
              </div>
              <p class="text-sm text-slate-600">{{ log.detail }}</p>
            </div>
          </div>
          <div v-else class="p-5 bg-emerald-50 rounded-2xl border border-emerald-100">
            <p class="text-sm text-emerald-600">操作表现优异！整个实验过程中未发现明显操作逻辑错误。</p>
          </div>
        </section>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-[#1e3a5f] flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-amber-500 rounded-full" />3. 知识巩固建议
          </h3>
          <ul class="text-sm text-slate-600 space-y-2">
            <li v-for="(item, i) in report?.reportKnowledge || []" :key="i">• {{ item }}</li>
          </ul>
        </section>

        <section>
          <h3 class="text-lg font-bold text-[#1e3a5f] flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-blue-500 rounded-full" />4. 后续学习路径
          </h3>
          <div class="space-y-3">
            <div v-for="(text, i) in report?.reportPath || []" :key="i" class="flex items-start gap-3">
              <div class="w-5 h-5 bg-blue-100 text-blue-600 rounded flex items-center justify-center text-[10px] font-bold shrink-0">{{ i + 1 }}</div>
              <p class="text-sm text-slate-600">{{ text }}</p>
            </div>
          </div>
        </section>
      </div>
      <div class="p-4 border-t border-[#dbe3ee] bg-[#f7f9fc] flex justify-end gap-3 shrink-0">
        <button type="button" class="px-6 py-2.5 border border-slate-300 rounded-xl text-sm font-bold text-slate-600 hover:bg-white btn-active-scale" @click="$emit('download-docx')" :disabled="downloading">
          {{ downloading ? '生成中…' : '下载 DOCX' }}
        </button>
        <button type="button" class="px-8 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-bold text-sm btn-active-scale" @click="$emit('close')">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: Boolean,
  report: Object,
  downloading: Boolean
})
defineEmits(['close', 'download-docx'])
</script>
