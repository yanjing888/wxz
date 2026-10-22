<template>
  <div v-if="assessment || loading || error" class="score-summary" aria-label="实验成绩构成" aria-live="polite">
    <template v-if="error"><span role="alert">{{ error }}</span><button type="button" :disabled="loading" @click="$emit('refresh')">{{ loading ? '同步中…' : '重试' }}</button></template>
    <span v-else-if="!assessment">正在读取成绩…</span>
    <template v-else>
      <span>过程成绩 <strong>{{ assessment.score ?? processStatus }}</strong><small v-if="assessment.score != null"> /100</small></span>
      <span>报告成绩 <strong>{{ reportScore ?? (reportSubmitted ? '待评分' : '未提交') }}</strong><small v-if="reportScore != null"> /100</small></span>
      <span>综合成绩 <strong>{{ overallScore ?? overallStatus }}</strong><small v-if="overallScore != null"> /100</small></span>
      <small v-if="processWeight != null">过程 {{ processWeight }}% · 报告 {{ 100 - processWeight }}%</small>
    </template>
  </div>
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ assessment: Object, reportSubmitted: { type: Boolean, default: true }, reportScore: { type: Number, default: null }, overallScore: { type: Number, default: null }, processWeight: { type: Number, default: null }, loading: Boolean, error: String })
defineEmits(['refresh'])
const processStatus = computed(() => {
  const assessment = props.assessment
  if (!assessment) return '待评价'
  if (assessment.requiredCount === 0) return '无需考核'
  const steps = (assessment.steps || []).filter(step => step.required)
  if (steps.some(step => step.status === 'REVISION')) return '待整改'
  if (assessment.gradedCount > 0) return `已评 ${assessment.gradedCount}/${assessment.requiredCount} 项`
  if (steps.some(step => step.status === 'PENDING')) return '待教师评价'
  return steps.length ? '待提交检查' : '待评价'
})
const overallStatus = computed(() => {
  if (!props.reportSubmitted && props.reportScore == null) return '待提交报告'
  if (props.assessment?.score == null && props.reportScore == null) return '待完成评价'
  if (props.assessment?.score == null) return '待过程评价'
  return '待报告评分'
})
</script>
<style scoped>
.score-summary{display:flex;flex-wrap:wrap;gap:10px 18px;align-items:center;padding:10px 14px;margin:8px 0;border:1px solid #e0e7ff;border-radius:8px;background:#f8faff;color:#475569;font-size:12px;line-height:1.7;flex-shrink:0}.score-summary strong{color:#4338ca;margin-left:4px}.score-summary small{color:#64748b;font-size:11px}
</style>
