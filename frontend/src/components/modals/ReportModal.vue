<template>
  <div v-if="visible" class="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-slate-900/30 backdrop-blur-md" @click.self="$emit('close')">
    <div class="relative w-full max-w-4xl max-h-[90vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full bg-gradient-to-r from-brand-600 via-accent-cyan to-emerald-500 shrink-0" />
      <div class="p-8 overflow-y-auto custom-scroll flex-1">
        <div class="text-center mb-8">
          <span class="chip text-brand-700 bg-brand-50 border-brand-100 mx-auto mb-3">EXPERIMENT REPORT</span>
          <h2 class="text-3xl font-black brand-text-gradient mb-2 tracking-tight">实验总结报告</h2>
          <p class="text-ink-faint text-sm">生成时间：{{ report?.generatedAt || '--' }}</p>
        </div>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 rounded-full brand-gradient" />1. 实操回顾与数据统计
          </h3>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
            <div class="p-5 surface-card rounded-2xl text-center">
              <p class="text-[10px] text-ink-faint uppercase mb-2 font-bold tracking-wider">有效纠错次数</p>
              <p class="text-3xl font-black brand-text-gradient">{{ report?.helpCount ?? 0 }}</p>
            </div>
            <div class="p-5 surface-card rounded-2xl text-center">
              <p class="text-[10px] text-ink-faint uppercase mb-2 font-bold tracking-wider">纠错标注点数</p>
              <p class="text-3xl font-black text-red-500">{{ report?.errorPointCount ?? 0 }}</p>
            </div>
            <div class="p-5 surface-card rounded-2xl text-center">
              <p class="text-[10px] text-ink-faint uppercase mb-2 font-bold tracking-wider">教程查阅次数</p>
              <p class="text-3xl font-black text-emerald-500">{{ report?.tutViewCount ?? 0 }}</p>
            </div>
            <div class="p-5 surface-card rounded-2xl text-center">
              <p class="text-[10px] text-ink-faint uppercase mb-2 font-bold tracking-wider">严重告警(L3)</p>
              <p class="text-3xl font-black text-red-500">{{ report?.labL3Count ?? 0 }}</p>
            </div>
          </div>
        </section>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-red-500 rounded-full" />2. 操作纠错记录
          </h3>
          <div v-if="report?.corrections?.length" class="space-y-3">
            <div v-for="(log, i) in report.corrections" :key="i" class="p-5 bg-red-50/70 rounded-2xl border border-red-100">
              <div class="flex flex-wrap justify-between gap-2 mb-2">
                <span class="text-[10px] font-bold text-red-600 uppercase tracking-wider">{{ log.stepTitle }}</span>
                <span class="text-[10px] px-2 py-0.5 bg-white border border-red-200 rounded-md text-red-600">{{ log.errorType }}</span>
              </div>
              <p class="text-sm text-ink-base leading-relaxed">{{ log.detail }}</p>
            </div>
          </div>
          <div v-else class="p-6 bg-emerald-50/70 rounded-2xl border border-emerald-100 flex items-center gap-3">
            <span class="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-600 flex items-center justify-center font-bold">✓</span>
            <p class="text-sm text-emerald-700">操作表现优异！整个实验过程中未发现明显操作逻辑错误。</p>
          </div>
        </section>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-orange-500 rounded-full" />3. 环境安全巡检记录
          </h3>
          <div v-if="report?.envLogs?.length" class="space-y-3">
            <div
              v-for="(log, i) in report.envLogs"
              :key="log.id ?? i"
              class="p-4 rounded-2xl border border-line-soft bg-surface-soft"
            >
              <div class="flex flex-wrap items-center gap-2 mb-2">
                <span class="text-xs font-mono text-ink-faint">{{ formatLogTime(log.createdAt) }}</span>
                <span class="text-[10px] font-bold px-2 py-0.5 rounded-md" :class="envLevelClass(log.level)">{{ log.level }}</span>
              </div>
              <p class="text-sm text-ink-base leading-relaxed line-clamp-4" :title="log.summary">{{ log.summary }}</p>
              <p v-if="log.suggestion" class="text-xs text-ink-faint mt-2 leading-relaxed border-t border-line-soft pt-2">{{ log.suggestion }}</p>
            </div>
          </div>
          <div v-else class="p-6 rounded-2xl border border-line-soft bg-surface-soft text-sm text-ink-faint text-center">
            本次实验未产生环境巡检记录（未开启安全监测或未触发巡检）。
          </div>
        </section>

        <section class="mb-10">
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-amber-500 rounded-full" />4. 知识巩固建议
          </h3>
          <ul class="text-sm text-ink-base space-y-2 surface-card p-5 rounded-2xl">
            <li v-for="(item, i) in report?.reportKnowledge || []" :key="i" class="flex gap-2 leading-relaxed">
              <span class="text-amber-500 shrink-0">●</span>
              <span>{{ item }}</span>
            </li>
          </ul>
        </section>

        <section>
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 brand-gradient rounded-full" />5. 后续学习路径
          </h3>
          <div class="space-y-3">
            <div v-for="(text, i) in report?.reportPath || []" :key="i" class="flex items-start gap-3 p-3 rounded-xl hover:bg-surface-soft transition-colors">
              <div class="w-6 h-6 brand-gradient text-white rounded-lg flex items-center justify-center text-[11px] font-bold shrink-0 shadow-card">{{ i + 1 }}</div>
              <p class="text-sm text-ink-base leading-relaxed pt-0.5">{{ text }}</p>
            </div>
          </div>
        </section>
      </div>
      <div class="p-4 border-t border-line-soft bg-surface-soft flex justify-end gap-3 shrink-0">
        <button
          type="button"
          class="btn-ghost px-6 py-2.5 rounded-xl text-sm font-bold btn-active-scale disabled:opacity-50"
          @click="$emit('download-docx')"
          :disabled="downloading"
        >
          {{ downloading ? '生成中…' : '下载 DOCX' }}
        </button>
        <button type="button" class="btn-brand px-8 py-2.5 rounded-xl font-bold text-sm" @click="$emit('close')">关闭</button>
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

function formatLogTime(v) {
  if (!v) return '--'
  const d = new Date(v)
  if (!Number.isNaN(d.getTime())) {
    return d.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }
  return String(v)
}

function envLevelClass(level) {
  if (level === 'L3') return 'text-red-600 bg-red-50 border border-red-100'
  if (level === 'L2') return 'text-orange-600 bg-orange-50 border border-orange-100'
  if (level === 'L1') return 'text-amber-600 bg-amber-50 border border-amber-100'
  return 'text-emerald-600 bg-emerald-50 border border-emerald-100'
}
</script>
