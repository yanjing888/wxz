<template>
  <aside class="grade-panel" aria-label="报告批改">
    <section class="ai-review-card">
      <header class="card-heading"><h2>AI 预评</h2><div class="ai-score" :class="{ 'is-empty': aiScore == null }"><strong>{{ reviewing ? '…' : aiScore ?? '—' }}</strong><span>/ {{ maxScore }}</span></div><button type="button" class="btn-review" :disabled="busy || !sessionId" @click="$emit('ai-review')">{{ reviewing ? '预评中…' : aiScore == null ? '开始预评' : '重新预评' }}</button></header>
      <div class="ai-comment custom-scroll" aria-label="AI 评语" :aria-busy="reviewing" role="status">{{ reviewing ? '正在阅读报告并生成评分与评语…' : aiComment || '暂无预评结果，可先阅读报告并手动批改。' }}</div>
      <p v-if="reviewError" class="review-error" role="alert">{{ reviewError }}</p>
    </section>
    <section class="teacher-grade-card">
      <header class="card-heading"><h2>教师批改</h2><button type="button" class="btn-adopt" :disabled="busy || aiScore == null" @click="adoptReview">采用 AI 结果</button></header>
      <div class="grade-editor custom-scroll">
      <ProcessScoreSummary :assessment="processAssessment" :report-score="gradingCompleted ? publishedReportScore : null" :overall-score="overallScore" :process-weight="processWeight" />
      <div class="teacher-score-row"><label for="teacher-score">报告得分</label><input id="teacher-score" v-model.number="scoreModel" type="number" min="0" :max="maxScore" step="0.1" placeholder="待评分" :disabled="busy" :aria-invalid="score != null && !canFinish" /><span>/ {{ maxScore }}</span></div>
      <p v-if="score != null && !canFinish" class="review-error">请输入 0–{{ maxScore }} 之间的分数。</p>
      <label class="field-label" for="teacher-comment">批改意见</label>
      <textarea id="teacher-comment" v-model="commentModel" class="comment-input" placeholder="填写批改意见" :disabled="busy" />
      </div>
      <footer class="grade-footer">
        <p class="grade-status" :class="{ completed: gradingCompleted && !draftPending }" aria-live="polite">{{ draftPending ? '有修改待提交 · 切换报告时暂存' : gradingCompleted ? '批改已完成' : '尚未提交批改结果' }}</p>
        <button type="button" class="btn-submit" :disabled="busy || !canFinish || !sessionId" @click="$emit('finish')">{{ saving ? '正在提交…' : gradingCompleted ? '保存批改修改' : '提交批改结果' }}</button>
      </footer>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import ProcessScoreSummary from '../experiment/ProcessScoreSummary.vue'

const props = defineProps({
  processAssessment: Object,
  overallScore: { type: Number, default: null },
  publishedReportScore: { type: Number, default: null },
  processWeight: { type: Number, default: 40 },
  sessionId: { type: [Number, String], default: null },
  score: { type: Number, default: null },
  comment: { type: String, default: '' },
  maxScore: { type: Number, default: 100 },
  aiScore: { type: Number, default: null },
  aiComment: { type: String, default: '' },
  reviewError: { type: String, default: '' },
  reviewing: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  gradingCompleted: { type: Boolean, default: false },
  draftPending: { type: Boolean, default: false }
})
const emit = defineEmits(['update:score', 'update:comment', 'ai-review', 'finish'])
const busy = computed(() => props.reviewing || props.saving || props.loading)
const scoreModel = computed({ get: () => props.score, set: value => emit('update:score', value === '' ? null : value) })
const commentModel = computed({ get: () => props.comment, set: value => emit('update:comment', value) })
const canFinish = computed(() => props.score != null && Number.isFinite(Number(props.score)) && Number(props.score) >= 0 && Number(props.score) <= props.maxScore)
function adoptReview() {
  if (busy.value || props.aiScore == null) return
  emit('update:score', props.aiScore)
  emit('update:comment', props.aiComment)
}
</script>

<style scoped>
.grade-panel { display: flex; flex-direction: column; overflow: hidden; gap: 10px; min-width: 0; min-height: 0; }
.ai-review-card, .teacher-grade-card { display: flex; flex-direction: column; min-height: 0; min-width: 0; border: 1px solid #e4e9f3; border-radius: 12px; background: #fff; padding: 12px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0; }
.card-heading h2 { white-space: nowrap; font-size: 15px; font-weight: 700; color: #1e293b; margin: 0; }
.card-heading button { border: 1px solid #e4e9f3; border-radius: 6px; background: #fff; padding: 6px 10px; font-size: 12px; color: #4f46e5; white-space: nowrap; }
.card-heading button:hover:not(:disabled) { background: #f5f3ff; border-color: #c4b5fd; }
.ai-score { display: flex; align-items: baseline; gap: 8px; margin: 0 auto 0 4px; color: #4f46e5; }
.ai-score strong { font-size: 24px; line-height: 1.15; font-weight: 700; font-variant-numeric: tabular-nums; }
.ai-score span { color: #64748b; font-size: 12px; }
.ai-score.is-empty { color: #94a3b8; }
.field-label { font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 6px; }
.ai-comment { max-height: clamp(80px, 14vh, 140px); min-height: 56px; margin-top: 8px; overflow-y: auto; padding: 8px 10px; background: #f8fafc; border-radius: 6px; color: #475569; font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.teacher-score-row { display: flex; align-items: center; gap: 10px; margin: 6px 0; flex-shrink: 0; font-size: 13px; color: #64748b; }
.teacher-score-row label { color: #475569; font-weight: 600; }
.teacher-score-row input { width: 96px; min-width: 0; height: 34px; padding: 4px 10px; border: 1px solid #dbe2ec; border-radius: 6px; font-size: 18px; font-weight: 600; color: #1e293b; }
.comment-input { flex: 1; width: 100%; min-height: 180px; flex-shrink: 0; resize: none; padding: 12px; border: 1px solid #dbe2ec; border-radius: 6px; color: #334155; background: #fff; font-size: 13px; line-height: 1.7; }
.grade-status { flex: 1; margin: 0; color: #64748b; font-size: 11px; line-height: 1.5; }
.grade-status.completed { color: #059669; }
.grade-footer { display: flex; align-items: center; gap: 12px; flex-shrink: 0; padding-top: 8px; margin-top: 8px; border-top: 1px solid #e4e9f3; background: #fff; }
.btn-submit { min-width: 140px; padding: 0 18px; height: 36px; border: 0; border-radius: 7px; background: #6366f1; color: #fff; font-weight: 600; font-size: 13px; }
.btn-submit:hover:not(:disabled) { background: #4f46e5; }
button:disabled { opacity: 0.45; cursor: not-allowed; }
input:focus-visible, textarea:focus-visible, button:focus-visible { outline: 2px solid #a5b4fc; outline-offset: 2px; }
.review-error { color: #dc2626; font-size: 12px; line-height: 1.6; margin: 8px 0; overflow-wrap: anywhere; }
.custom-scroll::-webkit-scrollbar { width: 5px; }
.custom-scroll::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
.ai-review-card { flex: 0 0 auto; }
.teacher-grade-card { flex: 1; overflow: hidden; }
.grade-editor { display: flex; flex-direction: column; flex: 1; min-height: 0; overflow-y: auto; margin-top: 8px; padding: 2px; }
.grade-editor > :not(.comment-input) { flex-shrink: 0; }
.grade-editor :deep(.score-summary) { margin: 0; padding: 5px 8px; gap: 2px 12px; font-size: 11px; line-height: 1.5; border-radius: 6px; }
.grade-editor :deep(.score-summary > span) { white-space: nowrap; }
.grade-editor :deep(.score-summary small) { font-size: 10px; }
.ai-review-card .card-heading { flex-wrap: wrap; }
@media (max-height: 800px) and (min-width: 1101px) { .ai-comment { max-height: 88px; } }
@media (max-width: 1100px) { .grade-panel { height: 640px; min-height: 520px; } }
</style>
