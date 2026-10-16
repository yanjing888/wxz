<template>
  <aside class="grade-panel" aria-label="报告批改">
    <section class="ai-review-card">
      <header class="card-heading"><h2>AI 预评结果</h2><button type="button" class="btn-review" :disabled="busy || !sessionId" @click="$emit('ai-review')">{{ reviewing ? '预评中…' : aiScore == null ? '开始预评' : '重新预评' }}</button></header>
      <p class="ai-note">AI 结果仅供参考，最终成绩以教师批改为准。</p>
      <div class="ai-score" :class="{ 'is-empty': aiScore == null }"><strong>{{ reviewing ? '…' : aiScore ?? '—' }}</strong><span>/ {{ maxScore }} 分</span></div>
      <div class="ai-comment-label">AI 评语</div>
      <div class="ai-comment custom-scroll" :aria-busy="reviewing" role="status">{{ reviewing ? '正在阅读报告并生成评分与评语…' : aiComment || '暂无预评结果，可先阅读报告并手动批改。' }}</div>
      <p v-if="reviewError" class="review-error" role="alert">{{ reviewError }}</p>
    </section>
    <section class="teacher-grade-card">
      <header class="card-heading"><h2>教师批改</h2><button type="button" class="btn-adopt" :disabled="busy || aiScore == null" @click="adoptReview">采用 AI 结果</button></header>
      <div class="teacher-score-row"><label for="teacher-score">实验得分</label><input id="teacher-score" v-model.number="scoreModel" type="number" min="0" :max="maxScore" step="0.1" placeholder="待评分" :disabled="busy" :aria-invalid="score != null && !canFinish" /><span>/ {{ maxScore }}</span></div>
      <p v-if="score != null && !canFinish" class="review-error">请输入 0–{{ maxScore }} 之间的分数。</p>
      <label class="field-label" for="teacher-comment">批改意见</label>
      <textarea id="teacher-comment" v-model="commentModel" class="comment-input" placeholder="填写批改意见" :disabled="busy" />
      <p class="grade-status" :class="{ completed: gradingCompleted && !draftPending }" aria-live="polite">{{ draftPending ? '有修改待提交 · 切换报告时暂存' : gradingCompleted ? '批改已完成' : '尚未提交批改结果' }}</p>
      <footer class="grade-footer">
        <div class="report-pagination"><button type="button" :disabled="busy || !hasPrevious" @click="$emit('previous')">上一份</button><button type="button" :disabled="busy || !hasNext" @click="$emit('next')">下一份</button></div>
        <button type="button" class="btn-submit" :disabled="busy || !canFinish || !sessionId" @click="$emit('finish')">{{ saving ? '正在提交…' : gradingCompleted ? '保存批改修改' : '提交批改结果' }}</button>
      </footer>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
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
  draftPending: { type: Boolean, default: false },
  hasPrevious: { type: Boolean, default: false },
  hasNext: { type: Boolean, default: false }
})
const emit = defineEmits(['update:score', 'update:comment', 'ai-review', 'finish', 'previous', 'next'])
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
.grade-panel { display: grid; grid-template-rows: minmax(220px, 0.92fr) minmax(310px, 1fr); gap: 12px; min-width: 0; min-height: 0; }
.ai-review-card, .teacher-grade-card { display: flex; flex-direction: column; min-height: 0; min-width: 0; border: 1px solid #e4e9f3; border-radius: 12px; background: #fff; padding: 18px; }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0; }
.card-heading h2 { font-size: 15px; font-weight: 700; color: #1e293b; margin: 0; }
.card-heading button { border: 1px solid #e4e9f3; border-radius: 6px; background: #fff; padding: 6px 10px; font-size: 12px; color: #4f46e5; white-space: nowrap; }
.card-heading button:hover:not(:disabled) { background: #f5f3ff; border-color: #c4b5fd; }
.ai-note { font-size: 12px; color: #64748b; line-height: 1.6; margin: 12px 0 0; }
.ai-score { display: flex; align-items: baseline; gap: 8px; margin: 12px 0; color: #4f46e5; }
.ai-score strong { font-size: 30px; line-height: 1.15; font-weight: 700; font-variant-numeric: tabular-nums; }
.ai-score span { color: #64748b; font-size: 12px; }
.ai-score.is-empty { color: #94a3b8; }
.ai-comment-label, .field-label { font-size: 12px; font-weight: 600; color: #475569; margin-bottom: 8px; }
.ai-comment { flex: 1; min-height: 56px; overflow-y: auto; padding: 12px; background: #f8fafc; border-radius: 6px; color: #475569; font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.teacher-score-row { display: flex; align-items: center; gap: 10px; margin: 18px 0; font-size: 13px; color: #64748b; }
.teacher-score-row label { color: #475569; font-weight: 600; }
.teacher-score-row input { width: 96px; min-width: 0; height: 38px; padding: 6px 10px; border: 1px solid #dbe2ec; border-radius: 6px; font-size: 18px; font-weight: 600; color: #1e293b; }
.comment-input { flex: 1; width: 100%; min-height: 72px; resize: none; padding: 12px; border: 1px solid #dbe2ec; border-radius: 6px; color: #334155; background: #fff; font-size: 13px; line-height: 1.7; }
.grade-status { margin: 8px 0 12px; color: #64748b; font-size: 11px; line-height: 1.5; }
.grade-status.completed { color: #059669; }
.grade-footer { flex-shrink: 0; }
.report-pagination { display: flex; gap: 10px; margin-bottom: 10px; }
.report-pagination button { flex: 1; height: 32px; border-radius: 6px; border: 1px solid #e4e9f3; background: #fff; color: #64748b; font-size: 12px; }
.btn-submit { width: 100%; height: 40px; border: 0; border-radius: 7px; background: #6366f1; color: #fff; font-weight: 600; font-size: 13px; }
.btn-submit:hover:not(:disabled) { background: #4f46e5; }
button:disabled { opacity: 0.45; cursor: not-allowed; }
input:focus-visible, textarea:focus-visible, button:focus-visible { outline: 2px solid #a5b4fc; outline-offset: 2px; }
.review-error { color: #dc2626; font-size: 12px; line-height: 1.6; margin: 8px 0; overflow-wrap: anywhere; }
.custom-scroll::-webkit-scrollbar { width: 5px; }
.custom-scroll::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
@media (max-height: 760px) and (min-width: 1101px) { .grade-panel { overflow-y: auto; } .ai-review-card, .teacher-grade-card { padding: 12px; } }
@media (max-width: 1100px) { .grade-panel { grid-template-rows: auto auto; overflow: visible; } .ai-review-card { min-height: 260px; } .ai-comment { max-height: 220px; } .teacher-grade-card { min-height: 360px; } }
</style>