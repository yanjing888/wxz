<template>
  <section class="process-page">
    <div class="process-toolbar">
      <div class="status-tabs" role="group" aria-label="考核状态"><button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: status === tab.key }" :aria-pressed="status === tab.key" :disabled="saving" @click="status = tab.key">{{ tab.label }}<span>{{ tab.count }}</span></button></div>
      <div class="toolbar-actions"><button type="button" class="btn" :disabled="loading || saving" @click="load(true)">{{ loading ? '刷新中…' : '刷新' }}</button></div>
    </div>
    <p v-if="error" class="process-error" role="alert">{{ error }}</p>
    <div class="process-workspace">
      <aside class="student-list" aria-label="考核学生列表">
        <div class="student-list-header">
          <div class="list-heading"><h2>学生名单</h2><span>{{ filteredStudents.length }} 人</span></div>
          <input v-model.trim="search" type="search" aria-label="搜索学生" placeholder="搜索姓名或学号" :disabled="saving" />
          <select v-if="!className" v-model="classFilter" aria-label="筛选考核班级" :disabled="saving"><option value="">全部班级</option><option v-for="name in classes" :key="name">{{ name }}</option></select>
        </div>
        <div class="student-list-scroll custom-scroll">
        <p v-if="!filteredStudents.length" class="empty-state">{{ loading ? '加载中…' : '暂无符合条件的学生' }}</p>
        <template v-for="group in groups" :key="group.name">
          <div class="class-heading">{{ group.name }}</div>
          <button v-for="item in group.items" :key="studentKey(item)" type="button" class="student-row" :class="{ selected: selectedStudentKey === studentKey(item) }" :aria-pressed="selectedStudentKey === studentKey(item)" :disabled="saving" @click="selectStudent(item)">
            <div class="student-row-top"><strong>{{ item.session.studentName }}</strong><span class="state-label" :class="studentState(item)">{{ studentStateLabel(item) }}</span></div>
            <div class="student-number">{{ item.username || '—' }}</div>
            <div class="student-row-bottom"><span>{{ item.assessment.completedCount }}/{{ item.assessment.totalCount }} 步</span><span>{{ item.assessment.score == null ? '待评价' : `${item.assessment.score} 分` }}</span></div>
          </button>
        </template>
        </div>
        <ListPagination class="student-pagination" compact v-model:page="processPage" v-model:page-size="processSize" :total="processTotal" :disabled="saving" />
      </aside>

      <div v-if="selected" class="detail-workspace">
        <header class="student-header"><div><strong>{{ selected.session.studentName }}</strong><span>{{ selected.username || '' }}</span><span>{{ selected.session.studentClass || '未分班' }}</span><button v-if="remoteChanged" type="button" class="text-button" :disabled="saving" @click="load(true)">材料已更新</button></div><div class="student-total"><span>已完成 {{ selected.assessment.completedCount }}/{{ selected.assessment.totalCount }} 步</span><strong>{{ selected.assessment.score == null ? '过程成绩待评价' : `${selected.assessment.score} / 100 分` }}</strong></div></header>
        <nav class="step-list custom-scroll" aria-label="选择考核步骤"><button v-for="step in selected.assessment.steps" :key="step.stepId" type="button" :class="{ current: selectedStepId === step.stepId, passed: ['PASSED', 'COMPLETED'].includes(step.status), locked: !step.accessible }" :aria-current="selectedStepId === step.stepId ? 'step' : undefined" :disabled="saving" @click="selectStep(step.stepId)"><span class="step-number">{{ step.stepId }}</span><span class="step-title">{{ step.title }}<small>{{ step.accessible ? shortStatus[step.status] : '未解锁' }}</small></span></button></nav>

        <div class="detail-columns">
          <section class="materials-panel">
            <div class="detail-tabs"><button type="button" :class="{ active: detailTab === 'materials' }" @click="detailTab = 'materials'">提交材料<span>{{ images.length }}</span></button><button type="button" :class="{ active: detailTab === 'history' }" @click="detailTab = 'history'">检查记录<span>{{ history.length }}</span></button></div>
            <div class="materials-scroll custom-scroll">
              <template v-if="detailTab === 'materials'">
                <dl class="submission-meta"><div><dt>当前状态</dt><dd class="state-label" :class="current.status">{{ current.accessible ? processStatus[current.status] : '前序步骤未完成' }}</dd></div><div><dt>提交时间</dt><dd>{{ time(current.record?.submittedAt) || '未提交' }}</dd></div><div v-if="current.record?.studentNote" class="full"><dt>学生说明</dt><dd>{{ current.record.studentNote }}</dd></div></dl>
                <section class="material-section"><div class="section-heading"><h3>检查图片</h3><span>{{ images.length }} 张</span></div><ProcessEvidenceGallery v-if="images.length" :images="images" /><div v-else class="inline-empty">未提交检查图片</div></section>
                <section class="material-section"><div class="section-heading"><h3>实验数据</h3><span v-if="evidence.table?.length">{{ evidence.table.length - 1 }} 行数据</span></div><ProcessEvidenceTable v-if="evidence.table?.length" :model-value="evidence.table" /><div v-if="!evidence.table?.length && !evidence.data?.length" class="inline-empty">暂无本步骤数据</div><div v-for="data in evidence.data || []" :key="data.id" class="data-record"><div class="data-record-meta"><span>{{ data.officialData === false ? '检查记录' : '原始数据' }}</span><span>{{ time(data.createdAt) }}</span></div><table class="data-table"><tbody><tr v-for="(value, key) in parseProcessJson(data.valuesJson, {})" :key="key"><th>{{ fieldLabel(key) }}</th><td>{{ value }}</td></tr></tbody></table></div></section>
              </template>
              <template v-else><div v-if="!history.length" class="inline-empty">暂无检查记录</div><article v-for="(event, index) in historyItems" :key="index" class="history-record"><header><strong>{{ event.action === 'REVIEWED' ? (event.status === 'PASSED' ? '检查通过' : '退回整改') : event.action === 'SUPPLEMENTED' ? '补充材料' : '提交检查' }}</strong><span v-if="event.action === 'REVIEWED'">{{ event.score }} 分</span></header><div class="history-meta"><span>{{ event.teacherName || selected.session.studentName }}</span><time>{{ time(event.at) }}</time></div><p v-if="event.note">{{ event.note }}</p><ProcessEvidenceGallery v-if="historyImages(event).length" :images="historyImages(event)" compact :label="'当次图片'" /><ProcessEvidenceTable v-if="event.action !== 'REVIEWED' && parseProcessJson(event.evidence, {}).table?.length" :model-value="parseProcessJson(event.evidence, {}).table" /><div v-for="data in parseProcessJson(event.evidence, {}).data || []" :key="data.id" class="historical-data">{{ formatData(data.valuesJson) }}</div></article><ListPagination v-model:page="historyPage" v-model:page-size="historySize" :total="historyTotal" /></template>
            </div>
          </section>

          <aside class="grading-panel" aria-label="步骤评分">
            <div class="grading-heading"><h2>步骤评价</h2><span>{{ current.title }} · 满分 {{ current.maxScore }} 分</span></div>
            <div class="grading-scroll custom-scroll"><div class="section-heading"><h3>检查项目</h3><button v-if="canReview" type="button" class="text-button" :disabled="saving" @click="checked = checked.length === current.criteria.length ? [] : [...current.criteria]">{{ checked.length === current.criteria.length ? '取消全选' : '全部符合' }}</button></div><label v-for="criterion in current.criteria" :key="criterion" class="criterion"><input v-model="checked" type="checkbox" :value="criterion" :disabled="saving || !canReview" /><span>{{ criterion }}</span></label>
              <div class="grade-field"><label for="process-score">本步得分</label><div class="score-input"><input id="process-score" v-model.number="score" type="number" min="0" :max="current.maxScore" step="0.1" :disabled="saving || !canReview" :placeholder="`0–${current.maxScore}`" /><span>/ {{ current.maxScore }}</span><button v-if="canReview" type="button" class="text-button" :disabled="saving" @click="score = current.maxScore">满分</button></div></div>
              <div class="grade-field"><label for="process-comment">检查意见</label><textarea id="process-comment" v-model="comment" maxlength="2000" :disabled="saving || !canReview" :placeholder="canReview ? '填写检查意见，退回时必填' : '暂无检查意见'" /></div>
              <p v-if="current.record?.reviewedAt" class="review-time">检查时间：{{ time(current.record.reviewedAt) }}</p>
              <p v-if="!canReview" class="readonly-status">{{ !current.submitted && current.canSubmit ? '等待学生补充完整材料' : current.status === 'PASSED' ? '本步已通过' : current.status === 'REVISION' ? '等待学生提交复查' : !current.required ? '本步骤无需评分' : '等待学生提交完整材料' }}</p>
            </div>
            <footer v-if="canReview" class="grading-actions"><button type="button" class="btn return-button" :disabled="saving || !validScore || !comment.trim()" @click="review(false)">退回整改</button><button type="button" class="btn primary-button" :disabled="saving || !validScore || checked.length !== current.criteria.length" @click="review(true)">{{ saving ? '保存中…' : '通过并保存' }}</button></footer>
          </aside>
        </div>
      </div>
      <div v-else class="workspace-empty">{{ loading ? '加载中…' : '暂无考核记录' }}</div>
    </div>
  </section>
</template>

<script setup>
import ListPagination from '../common/ListPagination.vue'
import { usePagination } from '../../composables/usePagination'
import { computed, onUnmounted, ref, watch } from 'vue'
import { teacherApi } from '../../api'
import { parseProcessJson, processImages, processStatus } from '../../utils/processAssessment'
import ProcessEvidenceGallery from '../experiment/ProcessEvidenceGallery.vue'
import ProcessEvidenceTable from '../experiment/ProcessEvidenceTable.vue'
const props = defineProps({ active: Boolean, experimentCode: String, className: String })
const rows = ref([]), selected = ref(null), selectedStudentKey = ref(''), selectedStepId = ref(1)
const status = ref('ALL'), search = ref(''), classFilter = ref(''), detailTab = ref('materials'), error = ref('')
const loading = ref(false), saving = ref(false), score = ref(null), comment = ref(''), checked = ref([])
let generation = 0
const shortStatus = { NOT_STARTED: '未完成', PENDING: '待检查', REVISION: '待整改', PASSED: '已通过', COMPLETED: '已完成' }
const studentKey = item => `${item.session.userId}:${item.session.experimentCode}`
const classes = computed(() => [...new Set(rows.value.map(item => item.session.studentClass || '未分班'))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const searched = computed(() => rows.value.filter(item => (!classFilter.value || (item.session.studentClass || '未分班') === classFilter.value) && [item.username, item.session.studentName, item.session.studentClass].some(value => String(value || '').toLowerCase().includes(search.value.toLowerCase()))))
const matchesStatus = item => status.value === 'ALL' || item.assessment.steps.some(step => step.status === status.value)
const filteredStudents = computed(() => searched.value.filter(matchesStatus).sort((a, b) => priority(a) - priority(b) || a.session.studentName.localeCompare(b.session.studentName, 'zh-CN')))
const tabs = computed(() => [{ key: 'ALL', label: '全部学生', count: searched.value.length }, ...[['PENDING', '待检查'], ['REVISION', '待整改'], ['PASSED', '已通过']].map(([key, label]) => ({ key, label, count: searched.value.filter(item => item.assessment.steps.some(step => step.status === key)).length }))])
const groups = computed(() => {
  const grouped = new Map()
  processItems.value.forEach(item => { const name = item.session.studentClass || '未分班'; if (!grouped.has(name)) grouped.set(name, []); grouped.get(name).push(item) })
  return [...grouped].map(([name, items]) => ({ name, items }))
})
const current = computed(() => selected.value?.assessment.steps.find(step => step.stepId === selectedStepId.value))
const canReview = computed(() => !!current.value?.canReview)
const validScore = computed(() => score.value !== null && score.value !== '' && Number.isFinite(Number(score.value)) && score.value >= 0 && score.value <= current.value?.maxScore)
const evidence = computed(() => parseProcessJson(current.value?.record?.evidenceJson, {}))
const images = computed(() => processImages(evidence.value.images))
const history = computed(() => parseProcessJson(current.value?.record?.historyJson))
const hasDraft = computed(() => score.value !== (current.value?.record?.score ?? null) || comment.value !== (current.value?.record?.comment || '') || JSON.stringify([...checked.value].sort()) !== JSON.stringify([...parseProcessJson(current.value?.record?.checkedCriteriaJson)].sort()))
const remoteChanged = computed(() => {
  const latest = rows.value.find(item => studentKey(item) === selectedStudentKey.value)?.assessment.steps.find(step => step.stepId === selectedStepId.value)
  return !!latest && (latest.record?.version !== current.value?.record?.version || latest.status !== current.value?.status)
})
function priority(item) { return item.assessment.steps.some(s => s.status === 'PENDING') ? 0 : item.session.status === 'ACTIVE' ? 1 : 2 }
function studentState(item) { if (item.assessment.steps.some(s => s.status === 'PENDING')) return 'PENDING'; if (item.assessment.steps.some(s => s.status === 'REVISION')) return 'REVISION'; return item.session.status === 'FINISHED' ? 'COMPLETED' : 'NOT_STARTED' }
function studentStateLabel(item) { return { PENDING: '待检查', REVISION: '待整改', COMPLETED: '已结束', NOT_STARTED: '进行中' }[studentState(item)] }
function fieldLabel(key) { const field = current.value?.dataFields?.find(f => f.key === key); return field ? `${field.label}${field.unit ? `（${field.unit}）` : ''}` : key }
function formatData(json) { return Object.entries(parseProcessJson(json, {})).map(([key, value]) => `${fieldLabel(key)}：${value}`).join('；') }
function historyImages(event) { return event.action === 'REVIEWED' ? [] : processImages(parseProcessJson(event.evidence, {}).images) }
function time(value) { return value?.replace('T', ' ').slice(0, 19) || '' }
function resetDraft() { score.value = current.value?.record?.score ?? null; comment.value = current.value?.record?.comment || ''; checked.value = parseProcessJson(current.value?.record?.checkedCriteriaJson); error.value = '' }
function selectStep(id) { selectedStepId.value = id; detailTab.value = 'materials'; resetDraft() }
function selectStudent(item) {
  selected.value = item; selectedStudentKey.value = studentKey(item)
  const steps = item.assessment.steps
  const step = (status.value !== 'ALL' && steps.find(s => s.status === status.value)) || steps.find(s => s.status === 'PENDING') || steps.find(s => s.accessible && !['PASSED', 'COMPLETED'].includes(s.status)) || steps[0]
  selectStep(step?.stepId || 1)
}
function ensureSelection() {
  if (saving.value) return
  if (!filteredStudents.value.some(item => studentKey(item) === selectedStudentKey.value)) {
    const first = filteredStudents.value[0]
    if (first) selectStudent(first)
    else { selected.value = null; selectedStudentKey.value = '' }
  }
}
watch(status, () => { const item = filteredStudents.value.find(item => studentKey(item) === selectedStudentKey.value); if (item && !saving.value) selectStudent(item); else ensureSelection() })
watch([search, classFilter], ensureSelection)
async function load(manual = false) {
  if (!props.active || !props.className || !props.experimentCode || loading.value || saving.value) return
  const token = generation
  loading.value = true
  try {
    const { data } = await teacherApi.processQueue({ experimentCode: props.experimentCode, className: props.className })
    if (token !== generation) return
    rows.value = data; error.value = ''
    if (!selected.value) ensureSelection()
    else if (manual || !hasDraft.value) {
      const updated = rows.value.find(item => studentKey(item) === selectedStudentKey.value)
      if (updated) { selected.value = updated; resetDraft() }
      else { selected.value = null; ensureSelection() }
    }
  } catch (e) { if (token === generation) error.value = e.response?.data?.message || '考核记录加载失败' }
  finally { if (token === generation) loading.value = false }
}
async function review(passed) {
  if (!canReview.value || !validScore.value) return
  const item = selected.value, step = current.value, token = generation
  saving.value = true; error.value = ''
  try {
    const { data } = await teacherApi.reviewStep(item.session.id, step.stepId, { passed, score: Number(score.value), comment: comment.value, checkedCriteria: checked.value, version: step.record?.version ?? null })
    if (token !== generation) return
    const index = rows.value.findIndex(row => studentKey(row) === studentKey(item))
    const updated = { ...item, assessment: data }
    if (index >= 0) rows.value[index] = updated
    selected.value = updated; resetDraft()
  } catch (e) { if (token === generation) error.value = e.response?.data?.message || '保存失败，请刷新记录后重试' }
  finally { saving.value = false; if (token !== generation) load() }
}
watch(() => [props.active, props.experimentCode, props.className], () => { generation++; loading.value = false; selected.value = null; selectedStudentKey.value = ''; rows.value = []; classFilter.value = ''; load() }, { immediate: true })
const timer = setInterval(() => { if (!document.hidden) load() }, 10000)
onUnmounted(() => { generation++; clearInterval(timer) })
const { page: processPage, pageSize: processSize, total: processTotal, items: processItems } = usePagination(filteredStudents, [search, status, classFilter, () => props.experimentCode, () => props.className])
const { page: historyPage, pageSize: historySize, total: historyTotal, items: historyItems } = usePagination(() => [...history.value].reverse(), [selectedStudentKey, selectedStepId])
</script>

<style scoped>
.process-page{display:flex;flex-direction:column;height:100%;min-height:0;background:#fff;color:#334155;font-size:13px}
.process-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:0 20px;min-height:56px;border-bottom:1px solid #e2e8f0;flex-shrink:0}
.status-tabs{display:flex;align-items:stretch;gap:24px;overflow-x:auto}
.status-tabs button{display:flex;align-items:center;gap:7px;height:55px;border-bottom:2px solid transparent;color:#64748b;font-size:13px;white-space:nowrap}
.status-tabs button.active{color:#4338ca;border-bottom-color:#4f46e5;font-weight:600}
.status-tabs button span{font-size:12px;font-variant-numeric:tabular-nums;color:#64748b}
.toolbar-actions{flex-shrink:0}.btn{height:34px;border:1px solid #cbd5e1;border-radius:5px;padding:0 12px;background:#fff;color:#475569;font-size:12px;font-weight:500;white-space:nowrap}
.btn:hover:not(:disabled){background:#f8fafc}button:disabled{opacity:.45;cursor:not-allowed}
button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid #a5b4fc;outline-offset:2px}
.process-workspace{display:flex;flex:1;min-height:0;overflow:hidden}
.student-list{width:252px;flex-shrink:0;display:flex;flex-direction:column;min-height:0;overflow:hidden;border-right:1px solid #e2e8f0;background:#fff}
.student-list-header{padding:16px 14px 12px;flex-shrink:0;border-bottom:1px solid #eef2f6}
.list-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}
.list-heading h2{font-size:14px;font-weight:600;color:#1e293b}.list-heading>span{font-size:12px;color:#64748b}
.student-list-header input,.student-list-header select{width:100%;height:34px;border:1px solid #dce2ea;border-radius:5px;background:white;padding:0 10px;color:#334155;font-size:12px}.student-list-header select{margin-top:8px}
.student-list-scroll{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain}
.student-pagination{flex-shrink:0;margin-top:auto}
.class-heading{padding:9px 14px;font-size:12px;font-weight:500;color:#64748b;background:#f8fafc;border-bottom:1px solid #eef2f6}
.student-row{display:block;width:100%;text-align:left;padding:13px 14px;border-bottom:1px solid #eef2f6;border-left:3px solid transparent;background:white}
.student-row:hover{background:#f8fafc}.student-row.selected{background:#f0f3ff;border-left-color:#4f46e5}
.student-row-top,.student-row-bottom{display:flex;align-items:center;justify-content:space-between;gap:8px}.student-row-top strong{font-size:13px;font-weight:600;color:#1e293b}
.student-number{font-size:12px;color:#64748b;margin:4px 0 9px;font-variant-numeric:tabular-nums}.student-row-bottom{font-size:12px;color:#64748b}
.state-label{font-size:12px;color:#64748b;white-space:nowrap}.state-label.PENDING{color:#a16207}.state-label.REVISION{color:#b91c1c}.state-label.PASSED,.state-label.COMPLETED{color:#047857}
.detail-workspace{flex:1;display:flex;flex-direction:column;min-width:0;min-height:0}
.student-header{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 20px;border-bottom:1px solid #e2e8f0;flex-shrink:0}
.student-header>div{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.student-header strong{font-size:16px;font-weight:600;color:#1e293b}.student-header span{color:#64748b;font-size:12px}.student-total{justify-content:flex-end}.student-total strong{font-size:13px;color:#334155;font-variant-numeric:tabular-nums}
.step-list{display:flex;overflow-x:auto;flex-shrink:0;border-bottom:1px solid #e2e8f0;padding:0 16px;background:#fff}
.step-list button{display:flex;align-items:center;gap:8px;padding:13px 12px;border-bottom:2px solid transparent;flex:1;min-width:126px;text-align:left}
.step-list button.current{border-bottom-color:#4f46e5}.step-list button:hover{background:#f8fafc}
.step-number{display:flex;align-items:center;justify-content:center;width:24px;height:24px;flex-shrink:0;border-radius:50%;border:1px solid #dce2ea;color:#64748b;font-size:12px;background:white}
.current .step-number{background:#4f46e5;color:white;border-color:#4f46e5}
.step-title{font-size:13px;color:#475569;line-height:1.5}.current .step-title{color:#312e81;font-weight:600}.step-title small{display:block;font-size:11px;font-weight:400;color:#64748b;margin-top:2px}.locked .step-title{color:#64748b}
.detail-columns{display:grid;grid-template-columns:minmax(0,1fr) 308px;flex:1;min-height:0}
.materials-panel{display:flex;flex-direction:column;min-height:0;min-width:0;background:#fff}
.detail-tabs{display:flex;gap:22px;padding:0 20px;border-bottom:1px solid #e2e8f0;flex-shrink:0}
.detail-tabs button{padding:14px 0;color:#64748b;border-bottom:2px solid transparent;font-size:13px}.detail-tabs button.active{color:#4338ca;border-bottom-color:#4f46e5;font-weight:600}.detail-tabs button span{margin-left:7px;font-size:12px;color:#64748b;font-weight:400}
.materials-scroll{padding:20px;overflow:auto;min-height:0;flex:1;overscroll-behavior:contain}
.submission-meta{display:flex;flex-wrap:wrap;gap:12px 24px;margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #eef2f6}.submission-meta>div{display:flex;align-items:baseline;gap:10px;min-width:0}.submission-meta .full{width:100%}
.submission-meta dt{color:#64748b;white-space:nowrap;font-size:12px}.submission-meta dd{font-size:12px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
.material-section{margin-bottom:28px}.section-heading{display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:12px}.section-heading h3{font-size:13px;font-weight:600;color:#1e293b}.section-heading>span{font-size:12px;color:#64748b}
.inline-empty{padding:28px 12px;border:1px solid #eef2f6;border-radius:5px;color:#64748b;font-size:12px;text-align:center;background:#fafbfc}
.data-record{margin-bottom:14px}.data-record-meta{display:flex;justify-content:space-between;gap:10px;font-size:12px;color:#64748b;margin-bottom:8px}
.data-table{width:100%;font-size:12px;border-collapse:collapse}.data-table th,.data-table td{padding:9px 12px;border:1px solid #e2e8f0;text-align:left;overflow-wrap:anywhere}.data-table th{width:45%;background:#f8fafc;font-weight:500;color:#475569}.data-table td{color:#334155}
.grading-panel{display:flex;flex-direction:column;min-height:0;border-left:1px solid #e2e8f0;background:#fff}
.grading-heading{padding:15px 18px;border-bottom:1px solid #e2e8f0;flex-shrink:0}.grading-heading h2{font-size:14px;font-weight:600;color:#1e293b;margin-bottom:5px}.grading-heading>span{font-size:12px;color:#64748b;line-height:1.5}
.grading-scroll{overflow:auto;min-height:0;flex:1;padding:18px;overscroll-behavior:contain}.text-button{font-size:12px;color:#4f46e5}.text-button:hover{text-decoration:underline}
.criterion{display:flex;align-items:flex-start;gap:9px;padding:8px 0;font-size:13px;color:#334155;line-height:1.7}.criterion input{margin-top:5px;accent-color:#4f46e5;flex-shrink:0}
.grade-field{margin-top:22px;padding-top:18px;border-top:1px solid #eef2f6}.grade-field>label{font-size:13px;font-weight:500;color:#334155;display:block;margin-bottom:10px}
.score-input{display:flex;align-items:center;gap:8px}.score-input input{width:100px;height:38px;border:1px solid #cbd5e1;border-radius:5px;padding:0 10px;font-size:18px;font-weight:600;color:#1e293b}.score-input>span{color:#64748b;font-size:12px}.score-input .text-button{margin-left:auto}
.grade-field textarea{width:100%;min-height:120px;resize:vertical;border:1px solid #cbd5e1;border-radius:5px;padding:10px;color:#334155;font-size:13px;line-height:1.7}.grade-field input:disabled,.grade-field textarea:disabled{background:#f8fafc;color:#64748b}
.grading-actions{display:flex;gap:10px;padding:14px 18px;border-top:1px solid #e2e8f0;flex-shrink:0;background:#fff}.grading-actions .btn{flex:1;padding:0 8px;height:36px}.primary-button{color:white;background:#4f46e5;border-color:#4f46e5}.primary-button:hover:not(:disabled){background:#4338ca}.return-button{color:#b45309;border-color:#e2c9a9}
.review-time{border-top:1px solid #eef2f6;padding-top:14px;margin-top:20px;color:#64748b;font-size:12px}.readonly-status{font-size:12px;color:#64748b;margin-top:16px;line-height:1.7}
.history-record{border-bottom:1px solid #e2e8f0;padding:0 0 20px;margin-bottom:20px}.history-record header,.history-meta{display:flex;justify-content:space-between;gap:10px}.history-record header strong{font-size:13px;font-weight:600}.history-record header span{font-size:12px;color:#4338ca}.history-meta{font-size:12px;color:#64748b;margin:8px 0}.history-record p{font-size:13px;white-space:pre-wrap;line-height:1.7;margin-bottom:12px}.historical-data{font-size:12px;color:#64748b;margin-top:8px;line-height:1.7}
.empty-state,.workspace-empty{color:#64748b;text-align:center;font-size:13px;padding:48px 16px}.workspace-empty{flex:1;background:#fafbfc}.process-error{padding:10px 20px;color:#92400e;background:#fffbeb;border-bottom:1px solid #fde68a;font-size:12px;flex-shrink:0}
@media(max-width:1280px){.student-list{width:228px}.detail-columns{grid-template-columns:minmax(0,1fr) 280px}.student-header{padding:14px 16px}.materials-scroll{padding:16px}.grading-scroll{padding:16px}.student-total>span{display:none}}
@media(max-width:1080px){.student-list{width:220px}.detail-columns{display:block;overflow:auto}.materials-scroll,.grading-scroll{overflow:visible;flex:none}.grading-panel{border-left:0;border-top:1px solid #e2e8f0}.grading-actions{position:sticky;bottom:0}.student-header{align-items:flex-start}.student-header>div{gap:6px 10px}.step-list button{flex:0 0 132px}.grading-heading{padding:14px 16px}}
@media(max-width:640px){.process-toolbar{padding:0 12px;gap:12px}.status-tabs{gap:14px}.status-tabs button{font-size:12px}.process-workspace{flex-direction:column;overflow:auto}.student-list{width:100%;height:280px;min-height:280px;border-right:0;border-bottom:1px solid #e2e8f0}.student-list-header{padding:12px}.student-list-header .list-heading{margin-bottom:8px}.student-list-scroll{min-height:70px}.student-pagination{flex-shrink:0}.detail-workspace{min-height:650px;flex-shrink:0}.student-total{display:none!important}.student-header{padding:14px}.materials-scroll{padding:14px}.detail-tabs{padding:0 14px}}
</style>
