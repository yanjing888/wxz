<template>
  <main class="process-detail-page">
    <header class="page-header"><button @click="router.push({ name: 'teacher', query: { tab: 'report', exp: detail?.session.experimentCode } })">← 返回报告列表</button><strong>过程考核详情</strong><button @click="load" :disabled="loading">刷新</button></header>
    <p v-if="error" class="state" role="alert">{{ error }} <button @click="load">重试</button></p>
    <div v-else-if="loading" class="state">加载中…</div>
    <template v-else-if="detail">
      <section class="student-meta"><div><h1>{{ detail.session.studentName }}</h1><p>{{ detail.session.studentClass || '未分班' }} · {{ detail.session.experimentName }}</p></div><div class="total"><span>过程考核总分</span><strong>{{ detail.assessment.score ?? '待评价' }}</strong><span v-if="detail.assessment.score != null">/ 100</span></div></section>
      <div class="detail-body"><nav aria-label="实验步骤"><button v-for="step in detail.assessment.steps" :key="step.stepId" :class="{ active: selectedStep === step.stepId }" @click="selectedStep = step.stepId"><span>{{ step.stepId }}. {{ step.title }}</span><small>{{ processStatus[step.status] }} · {{ stepScore(step) }} / {{ step.maxScore }}</small></button></nav>
        <section v-if="current" class="step-content"><header><h2>{{ current.title }}</h2><span>{{ processStatus[current.status] }}</span><strong>{{ stepScore(current) }} / {{ current.maxScore }} 分</strong></header>
          <dl><div><dt>提交时间</dt><dd>{{ time(current.record?.submittedAt) }}</dd></div><div><dt>评分教师</dt><dd>{{ current.record?.teacherName || '—' }}</dd></div><div><dt>学生说明</dt><dd>{{ current.record?.studentNote || '—' }}</dd></div><div><dt>教师评语</dt><dd>{{ current.record?.comment || '—' }}</dd></div></dl>
          <h3>提交图片</h3><ProcessEvidenceGallery v-if="images.length" :images="images" /><p v-else class="empty">未提交图片</p>
          <h3>实验数据</h3><ProcessEvidenceTable v-if="table.length" :model-value="table" /><table v-for="record in evidence.data || []" :key="record.id" class="record-table"><tbody><tr v-for="(value, label) in parseProcessJson(record.valuesJson, {})" :key="label"><th>{{ label }}</th><td>{{ value }}</td></tr></tbody></table><p v-if="!table.length && !evidence.data?.length" class="empty">未提交实验数据</p>
        </section>
      </div>
    </template>
  </main>
</template>
<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { teacherApi } from '../api'
import { parseProcessJson, processImages, processStatus } from '../utils/processAssessment'
import ProcessEvidenceGallery from '../components/experiment/ProcessEvidenceGallery.vue'
import ProcessEvidenceTable from '../components/experiment/ProcessEvidenceTable.vue'
const route = useRoute(), router = useRouter(), detail = ref(null), loading = ref(false), error = ref(''), selectedStep = ref(null)
let generation = 0
const current = computed(() => detail.value?.assessment.steps.find(step => step.stepId === selectedStep.value))
const evidence = computed(() => parseProcessJson(current.value?.record?.evidenceJson, {}))
const images = computed(() => processImages(evidence.value.images))
const table = computed(() => Array.isArray(evidence.value.table) ? evidence.value.table : [])
function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' }
function stepScore(step) { return ['PASSED', 'REVISION'].includes(step.status) ? step.record?.score ?? '—' : '—' }
async function load() {
  const run = ++generation; loading.value = true; error.value = ''; detail.value = null
  try { const { data } = await teacherApi.processDetail(route.params.sessionId); if (run !== generation) return; detail.value = data; if (!data.assessment.steps.some(step => step.stepId === selectedStep.value)) selectedStep.value = data.assessment.steps[0]?.stepId }
  catch (e) { if (run === generation) error.value = e.response?.data?.message || '过程记录加载失败' }
  finally { if (run === generation) loading.value = false }
}
watch(() => route.params.sessionId, load, { immediate: true })
onUnmounted(() => generation++)
</script>
<style scoped>
.record-table{width:100%;border-collapse:collapse;margin:12px 0}.record-table th,.record-table td{padding:10px;border:1px solid #e2e8f0;text-align:left;white-space:pre-wrap}.record-table th{background:#f8fafc;font-weight:400;width:40%}
.process-detail-page{height:100dvh;display:flex;flex-direction:column;background:#f1f3f8;color:#334155;font-size:13px}.page-header{height:64px;display:flex;align-items:center;gap:24px;padding:0 28px;background:white;border-bottom:1px solid #e2e8f0}.page-header button{color:#6366f1}.page-header button:last-child{margin-left:auto}.student-meta{display:flex;justify-content:space-between;align-items:center;padding:22px 28px;background:#fff;border-bottom:1px solid #e2e8f0}.student-meta h1{font-size:18px;font-weight:600}.student-meta p{margin-top:6px;color:#94a3b8}.total{display:flex;gap:12px;align-items:baseline}.total strong{font-size:24px;color:#4f46e5}.total span{color:#94a3b8}.detail-body{display:flex;flex:1;min-height:0;margin:20px;background:white;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden}nav{width:250px;flex-shrink:0;overflow:auto;border-right:1px solid #e2e8f0}nav button{display:block;width:100%;text-align:left;padding:18px;border-bottom:1px solid #edf0f5;border-left:3px solid transparent}nav button.active{background:#f5f6ff;border-left-color:#6366f1}nav small{display:block;margin-top:8px;color:#94a3b8;font-size:11px}.step-content{flex:1;min-width:0;overflow:auto;padding:24px 30px}.step-content header{display:flex;align-items:center;gap:16px;border-bottom:1px solid #edf0f5;padding-bottom:20px}.step-content h2{font-size:16px;font-weight:600}.step-content header>span{font-size:12px;color:#94a3b8}.step-content header strong{margin-left:auto;color:#4f46e5}dl{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin:22px 0}dl>div{display:flex;gap:16px}dt{color:#94a3b8;white-space:nowrap}dd{white-space:pre-wrap}.step-content h3{font-weight:600;margin:24px 0 12px}.empty{padding:24px;background:#fafbfc;color:#94a3b8;text-align:center;border:1px dashed #e2e8f0}.state{padding:60px;text-align:center;color:#94a3b8}@media(max-width:800px){.detail-body{margin:8px}nav{width:180px}.step-content{padding:16px}dl{grid-template-columns:1fr}.total{flex-wrap:wrap}.student-meta{gap:12px}}
</style>
