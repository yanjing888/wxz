<template>
  <aside class="grade-panel">
    <div class="score-block">
      <div class="score-label">得分</div>
      <div v-if="reviewing" class="score-row" aria-busy="true">
        <span class="score-skeleton" />
        <span class="score-max">/ {{ maxScore }}</span>
      </div>
      <div v-else class="score-row">
        <input
          v-model.number="scoreModel"
          class="score-input"
          type="number"
          min="0"
          max="10"
          step="0.1"
          :disabled="saving"
        />
        <span class="score-max">/ {{ maxScore }}</span>
      </div>
      <p class="score-status" :class="statusClass">{{ statusText }}</p>
    </div>

    <div class="grade-actions">
      <button type="button" class="btn-ai" :disabled="reviewing || saving || !sessionId" @click="$emit('ai-review')">
        {{ reviewing ? '预评中…' : 'AI 预评' }}
      </button>
      <button type="button" class="btn-done" :disabled="saving || reviewing || !canFinish" @click="$emit('finish')">
        {{ saving ? '保存中…' : '完成批改' }}
      </button>
    </div>

    <label class="field-label" for="teacher-comment">评语</label>
    <div class="comment-wrap">
      <textarea
        id="teacher-comment"
        v-model="commentModel"
        class="comment-input"
        placeholder="可直接采用 AI 评语，或在此修改后完成批改。"
        :disabled="saving || reviewing"
      />
      <div v-if="reviewing" class="comment-loading" role="status" aria-live="polite">
        <div class="sk-lines" aria-hidden="true">
          <span class="sk-bar w92" />
          <span class="sk-bar w100" />
          <span class="sk-bar w86" />
          <span class="sk-bar w96" />
          <span class="sk-bar w78" />
          <span class="sk-bar w88" />
          <span class="sk-bar w64" />
        </div>
        <p class="sk-hint">
          <span class="sk-spinner" />
          AI 正在阅读报告并生成评语，请稍候…
        </p>
      </div>
    </div>
    <p v-if="reviewError" class="review-error">{{ reviewError }}</p>
  </aside>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  sessionId: { type: [Number, String], default: null },
  score: { type: Number, default: null },
  comment: { type: String, default: '' },
  maxScore: { type: Number, default: 10 },
  reviewError: { type: String, default: '' },
  reviewing: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  gradingCompleted: { type: Boolean, default: false },
  gradeBand: { type: String, default: '' }
})

const emit = defineEmits(['update:score', 'update:comment', 'ai-review', 'finish'])

const scoreModel = computed({
  get: () => props.score,
  set: (value) => emit('update:score', value)
})

const commentModel = computed({
  get: () => props.comment,
  set: (value) => emit('update:comment', value)
})

const canFinish = computed(() => props.score != null && Number.isFinite(Number(props.score)))

const statusText = computed(() => {
  if (props.reviewing) return 'AI 正在评分…'
  if (props.gradingCompleted) return '批改已完成'
  if (props.score != null && Number.isFinite(Number(props.score))) {
    const band = props.gradeBand ? ` · ${props.gradeBand}` : ''
    return `建议分待教师确认${band}`
  }
  return '批改未完成或未开始'
})

const statusClass = computed(() => {
  if (props.reviewing) return 'loading'
  if (props.gradingCompleted) return 'ok'
  if (props.score != null) return 'pending'
  return 'empty'
})
</script>

<style scoped>
.grade-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  padding: 16px;
  background: #f8fafc;
  border-left: 1px solid #e4e9f3;
  overflow: hidden;
}
.score-block { margin-bottom: 14px; flex-shrink: 0; }
.score-label { font-size: 12px; font-weight: 700; color: #64748b; letter-spacing: 0.04em; }
.score-row { display: flex; align-items: baseline; gap: 8px; margin-top: 6px; }
.score-input {
  width: 92px; height: 44px; border: 1px solid #c7d2fe; border-radius: 10px;
  background: #fff; color: #1e1b4b; font-size: 28px; font-weight: 800;
  text-align: center; padding: 0 8px;
}
.score-input:focus { outline: 2px solid #818cf8; border-color: #6366f1; }
.score-skeleton {
  display: inline-block;
  width: 92px;
  height: 44px;
  border-radius: 10px;
  border: 1px solid #c7d2fe;
  background: linear-gradient(90deg, #eef2ff 0%, #e0e7ff 40%, #eef2ff 80%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
}
.score-max { font-size: 14px; font-weight: 700; color: #94a3b8; }
.score-status { margin-top: 6px; font-size: 12px; font-weight: 600; }
.score-status.empty { color: #94a3b8; }
.score-status.pending { color: #d97706; }
.score-status.ok { color: #16a34a; }
.score-status.loading { color: #6366f1; }
.grade-actions { display: flex; gap: 8px; margin-bottom: 14px; flex-shrink: 0; }
.grade-actions button {
  flex: 1; height: 36px; border-radius: 8px; font-size: 13px; font-weight: 700; border: none;
}
.btn-ai { background: #4f46e5; color: #fff; }
.btn-ai:hover:not(:disabled) { background: #4338ca; }
.btn-done { background: #2563eb; color: #fff; }
.btn-done:hover:not(:disabled) { background: #1d4ed8; }
.grade-actions button:disabled { opacity: 0.45; cursor: default; }
.field-label { font-size: 12px; font-weight: 700; color: #475569; margin-bottom: 6px; flex-shrink: 0; }
.comment-wrap {
  position: relative;
  flex: 1;
  min-height: 280px;
  display: flex;
}
.comment-input {
  width: 100%;
  height: 100%;
  min-height: 0;
  resize: none;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  color: #1e293b;
  background: #fff;
}
.comment-input:focus { outline: 2px solid #c7d2fe; border-color: #818cf8; }
.comment-input:disabled { color: #64748b; }
.comment-loading {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #c7d2fe;
  border-radius: 10px;
  background: #fff;
  padding: 14px 16px 12px;
  overflow: hidden;
}
.sk-lines { display: flex; flex-direction: column; gap: 10px; flex: 1; }
.sk-bar {
  display: block;
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #eef2ff 0%, #e0e7ff 40%, #eef2ff 80%);
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
}
.sk-bar.w92 { width: 92%; }
.sk-bar.w100 { width: 100%; }
.sk-bar.w86 { width: 86%; }
.sk-bar.w96 { width: 96%; }
.sk-bar.w78 { width: 78%; }
.sk-bar.w88 { width: 88%; }
.sk-bar.w64 { width: 64%; }
.sk-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #6366f1;
}
.sk-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #c7d2fe;
  border-top-color: #4f46e5;
  border-radius: 50%;
  animation: sk-spin 0.8s linear infinite;
  flex-shrink: 0;
}
.review-error { margin-top: 8px; font-size: 12.5px; color: #dc2626; line-height: 1.6; flex-shrink: 0; }
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
@keyframes sk-spin { to { transform: rotate(360deg); } }
</style>
