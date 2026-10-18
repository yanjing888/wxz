<template>
  <div v-if="visible" class="fixed inset-0 z-[60] flex items-center justify-center p-6 bg-slate-900/30 backdrop-blur-md" @click.self="$emit('close')">
    <div class="relative w-full max-w-4xl max-h-[90vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full bg-gradient-to-r from-brand-600 via-accent-cyan to-emerald-500 shrink-0" />
      <div class="p-6 overflow-y-auto custom-scroll flex-1 bg-slate-50">
        <StudentReportDocument :report="report" />

        <section v-if="reviewScore != null || reviewComment || reviewText || reviewError || reviewing" class="mt-8 max-w-[720px] mx-auto">
          <h3 class="text-lg font-bold text-ink-strong flex items-center gap-3 mb-4">
            <span class="w-1.5 h-6 bg-indigo-500 rounded-full" />AI 预评（待教师确认）
          </h3>
          <p v-if="reviewScore != null" class="text-2xl font-extrabold text-indigo-700 mb-2">
            建议得分 {{ reviewScore }} / 10
          </p>
          <p class="text-[12.5px] text-ink-muted mb-3">
            以下为建议分与批注草稿，不直接作为最终成绩。请在右侧批改栏确认。
            <span v-if="(reviewText || reviewComment) && !reviewFromDify" class="text-amber-700">（当前回退本地提示，专用批改工作流未接入）</span>
          </p>
          <p v-if="reviewError" class="text-sm text-rose-600 mb-2">{{ reviewError }}</p>
          <p v-else-if="reviewing" class="text-sm text-ink-muted">正在生成预评…</p>
          <div v-else-if="reviewComment || reviewText" class="chat-md surface-card rounded-2xl p-5" v-html="renderMd(reviewComment || reviewText)" />
        </section>
      </div>
      <div class="p-4 border-t border-line-soft bg-surface-soft flex justify-end gap-3 shrink-0">
        <button
          type="button"
          class="btn-ghost px-6 py-2.5 rounded-xl text-sm font-bold btn-active-scale disabled:opacity-50"
          @click="$emit('ai-review')"
          :disabled="reviewing"
        >
          {{ reviewing ? '预评中…' : 'AI 预评' }}
        </button>
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
import { renderChatMarkdown } from '../../utils/markdown'
import StudentReportDocument from '../teacher/StudentReportDocument.vue'

defineProps({
  visible: Boolean,
  report: Object,
  downloading: Boolean,
  reviewing: Boolean,
  reviewText: { type: String, default: '' },
  reviewFromDify: { type: Boolean, default: true },
  reviewError: { type: String, default: '' },
  reviewScore: { type: Number, default: null },
  reviewComment: { type: String, default: '' }
})
defineEmits(['close', 'download-docx', 'ai-review'])

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>
