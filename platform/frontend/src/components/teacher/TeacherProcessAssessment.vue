<template>
  <section class="process-page">
    <div class="process-toolbar">
      <div class="status-tabs" role="group" aria-label="考核状态"><button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: status === tab.key }" :aria-pressed="status === tab.key" :disabled="saving" @click="status = tab.key">{{ tab.label }}<span>{{ tab.count }}</span></button></div>
      <div class="toolbar-actions"><input v-model.trim="search" type="search" aria-label="搜索学生" placeholder="搜索学号、姓名或班级" /><select v-if="!className" v-model="classFilter" aria-label="筛选考核班级"><option value="">全部班级</option><option v-for="name in classes" :key="name" :value="name">{{ name }}</option></select><button type="button" class="btn" :disabled="loading || saving" @click="load(true)">{{ loading ? '刷新中…' : '刷新' }}</button></div>
    </div>
    <p v-if="error" class="process-error" role="alert">{{ error }}</p>
    <div class="process-workspace">
      <aside class="student-list custom-scroll" aria-label="考核学生列表">
        <div class="list-heading"><span>学生</span><span>{{ filteredStudents.length }} 人</span></div>
        <p v-if="!filteredStudents.length" class="empty-state">{{ loading ? '加载中…' : '暂无符合条件的学生' }}</p>
        <template v-for="group in groups" :key="group.name">
          <div class="class-heading">{{ group.name }}<span>{{ group.items.length }} 人</span></div>
          <button v-for="item in group.items" :key="studentKey(item)" type="button" class="student-row" :class="{ selected: selectedStudentKey === studentKey(item) }" :disabled="saving" @click="selectStudent(item)">
            <div class="student-row-top"><strong>{{ item.session.studentName }}</strong><span class="state-label" :class="studentState(item)">{{ studentStateLabel(item) }}</span></div>
            <div class="student-number">{{ item.username || '—' }}</div>
            <div class="student-row-bottom"><span>{{ item.assessment.completedCount }}/{{ item.assessment.totalCount }} 步</span><span>{{ item.assessment.score == null ? '待评价' : `${item.assessment.score} 分` }}</span></div>
            <div class="progress-track"><span :style="{ width: `${item.assessment.completedCount / Math.max(item.assessment.totalCount, 1) * 100}%` }" /></div>
          </button>
        </template>
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
              <template v-else><div v-if="!history.length" class="inline-empty">暂无检查记录</div><article v-for="(event, index) in [...history].reverse()" :key="index" class="history-record"><header><strong>{{ event.action === 'REVIEWED' ? (event.status === 'PASSED' ? '检查通过' : '退回整改') : event.action === 'SUPPLEMENTED' ? '补充材料' : '提交检查' }}</strong><span v-if="event.action === 'REVIEWED'">{{ event.score }} 分</span></header><div class="history-meta"><span>{{ event.teacherName || selected.session.studentName }}</span><time>{{ time(event.at) }}</time></div><p v-if="event.note">{{ event.note }}</p><ProcessEvidenceGallery v-if="historyImages(event).length" :images="historyImages(event)" compact :label="'当次图片'" /><ProcessEvidenceTable v-if="event.action !== 'REVIEWED' && parseProcessJson(event.evidence, {}).table?.length" :model-value="parseProcessJson(event.evidence, {}).table" /><div v-for="data in parseProcessJson(event.evidence, {}).data || []" :key="data.id" class="historical-data">{{ formatData(data.valuesJson) }}</div></article></template>
            </div>
          </section>

          <aside class="grading-panel" aria-label="步骤评分">
            <div class="grading-scroll custom-scroll"><div class="section-heading"><h3>检查项目</h3><button v-if="canReview" type="button" class="text-button" :disabled="saving" @click="checked = checked.length === current.criteria.length ? [] : [...current.criteria]">{{ checked.length === current.criteria.length ? '取消全选' : '全部符合' }}</button></div><label v-for="criterion in current.criteria" :key="criterion" class="criterion"><input v-model="checked" type="checkbox" :value="criterion" :disabled="saving || !canReview" /><span>{{ criterion }}</span></label>
              <div class="grade-field"><label for="process-score">本步得分</label><div class="score-input"><input id="process-score" v-model.number="score" type="number" min="0" :max="current.maxScore" step="0.1" :disabled="saving || !canReview" :placeholder="`0–${current.maxScore}`" /><span>/ {{ current.maxScore }}</span><button v-if="canReview" type="button" class="text-button" :disabled="saving" @click="score = current.maxScore">满分</button></div></div>
              <div class="grade-field"><label for="process-comment">检查意见</label><textarea id="process-comment" v-model="comment" maxlength="2000" :disabled="saving || !canReview" :placeholder="canReview ? '填写检查意见，退回时必填' : '暂无检查意见'" /></div>
              <dl v-if="current.record?.reviewedAt" class="review-meta"><div><dt>检查教师</dt><dd>{{ current.record.teacherName || '—' }}</dd></div><div><dt>检查时间</dt><dd>{{ time(current.record.reviewedAt) }}</dd></div></dl>
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
  filteredStudents.value.forEach(item => { const name = item.session.studentClass || '未分班'; if (!grouped.has(name)) grouped.set(name, []); grouped.get(name).push(item) })
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
</script>

<style scoped>
.process-page{display:flex;flex-direction:column;height:100%;min-height:0;background:#fff;color:#1e293b;font-size:13px}.process-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 16px;border-bottom:1px solid #e4e9f3;flex-shrink:0}.status-tabs,.toolbar-actions{display:flex;align-items:center;gap:8px}.status-tabs button{display:flex;align-items:center;gap:6px;padding:7px 10px;border-radius:6px;color:#64748b;font-size:12px;white-space:nowrap}.status-tabs button.active{color:#4338ca;background:#eef2ff;font-weight:600}.status-tabs button span{font-size:11px;padding:1px 5px;background:#f1f5f9;border-radius:4px}.status-tabs button.active span{background:#e0e7ff}.toolbar-actions input,.toolbar-actions select{height:34px;border:1px solid #e2e8f0;border-radius:6px;padding:0 10px;font-size:12px;background:#fff;color:#475569}.toolbar-actions input{width:220px}.btn{height:34px;border:1px solid #cbd5e1;border-radius:6px;padding:0 12px;background:#fff;color:#475569;font-size:12px;font-weight:600;white-space:nowrap}.btn:hover:not(:disabled){background:#f8fafc}button:disabled{opacity:.45;cursor:default}input:focus,select:focus,textarea:focus{outline:2px solid #c7d2fe;outline-offset:0}.process-workspace{display:flex;flex:1;min-height:0;overflow:hidden}.student-list{width:248px;flex-shrink:0;overflow:auto;border-right:1px solid #e4e9f3;background:#fff}.list-heading{display:flex;justify-content:space-between;padding:12px 14px;color:#64748b;font-size:12px}.class-heading{display:flex;justify-content:space-between;padding:8px 14px;background:#f8f9fc;border-top:1px solid #eef0f5;border-bottom:1px solid #eef0f5;font-size:12px;font-weight:600;color:#475569}.class-heading span{font-weight:400;color:#94a3b8}.student-row{display:block;text-align:left;width:100%;padding:12px 14px;border-bottom:1px solid #f1f5f9;border-left:3px solid transparent}.student-row:hover{background:#fafbff}.student-row.selected{background:#f5f6ff;border-left-color:#6366f1}.student-row-top,.student-row-bottom{display:flex;align-items:center;justify-content:space-between;gap:8px}.student-row-top strong{font-size:13px;font-weight:600}.student-number{font-size:12px;color:#94a3b8;margin:4px 0 8px}.student-row-bottom{font-size:11px;color:#64748b}.progress-track{height:3px;background:#eef0f5;margin-top:8px;border-radius:2px;overflow:hidden}.progress-track span{display:block;height:100%;background:#a5b4fc}.state-label{font-size:11px;color:#64748b;white-space:nowrap}.state-label.PENDING{color:#b45309}.state-label.REVISION{color:#dc2626}.state-label.PASSED,.state-label.COMPLETED{color:#059669}.detail-workspace{flex:1;display:flex;flex-direction:column;min-width:0;min-height:0}.student-header{display:flex;justify-content:space-between;gap:16px;padding:16px 20px;border-bottom:1px solid #e4e9f3;flex-shrink:0}.student-header>div{display:flex;align-items:center;gap:12px}.student-header strong{font-size:15px;font-weight:600}.student-header span{color:#94a3b8;font-size:12px}.student-total strong{font-size:13px;color:#475569}.step-list{display:flex;overflow-x:auto;flex-shrink:0;border-bottom:1px solid #e4e9f3;padding:0 12px;background:#fafbfe}.step-list button{display:flex;align-items:center;gap:8px;padding:12px 10px;border-bottom:2px solid transparent;min-width:0;flex:1;text-align:left}.step-list button.current{border-color:#6366f1;background:#f1f3ff}.step-number{display:flex;align-items:center;justify-content:center;width:22px;height:22px;flex-shrink:0;border-radius:5px;border:1px solid #e2e8f0;background:#fff;color:#94a3b8;font-size:11px}.current .step-number{color:#4f46e5;border-color:#a5b4fc}.passed .step-number{color:#059669;border-color:#a7f3d0;background:#ecfdf5}.step-title{font-size:12px;color:#475569;line-height:1.5;min-width:65px}.step-title small{display:block;font-size:10px;color:#94a3b8;margin-top:3px}.locked .step-title{color:#94a3b8}.detail-columns{display:grid;grid-template-columns:minmax(0,1fr) 300px;flex:1;min-height:0}.materials-panel{display:flex;flex-direction:column;min-height:0;min-width:0}.detail-tabs{display:flex;gap:18px;padding:0 20px;border-bottom:1px solid #eef0f5;flex-shrink:0}.detail-tabs button{padding:12px 0;color:#64748b;border-bottom:2px solid transparent;font-size:12px}.detail-tabs button.active{color:#4f46e5;border-color:#6366f1;font-weight:600}.detail-tabs button span{margin-left:6px;color:#94a3b8;font-size:11px}.materials-scroll{padding:16px 20px;overflow:auto;min-height:0;flex:1}.submission-meta{display:grid;grid-template-columns:1fr 1.4fr;gap:12px;margin-bottom:18px}.submission-meta>div{display:flex;align-items:baseline;gap:12px;min-width:0}.submission-meta .full{grid-column:1/-1}dt{color:#94a3b8;white-space:nowrap;font-size:12px}dd{color:#475569;white-space:pre-wrap;line-height:1.7;font-size:12px}.material-section{margin-bottom:24px}.section-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.section-heading h3{font-size:13px;font-weight:600;color:#334155}.section-heading>span{font-size:11px;color:#94a3b8}.inline-empty{padding:18px 12px;border:1px dashed #e2e8f0;border-radius:6px;color:#94a3b8;font-size:12px;text-align:center;background:#fcfcfd}.data-record{margin-bottom:12px}.data-record-meta{display:flex;justify-content:space-between;font-size:11px;color:#94a3b8;margin-bottom:6px}.data-table{width:100%;font-size:12px;border-collapse:collapse}.data-table th,.data-table td{padding:8px 10px;border:1px solid #e4e9f3;text-align:left}.data-table th{width:50%;background:#f8f9fc;color:#64748b;font-weight:400}.data-table td{color:#334155}.grading-panel{display:flex;flex-direction:column;min-height:0;border-left:1px solid #e4e9f3;background:#fff}.grading-scroll{overflow:auto;min-height:0;flex:1;padding:18px 16px}.text-button{font-size:11px;color:#6366f1}.criterion{display:flex;align-items:flex-start;gap:8px;margin:12px 0;font-size:12px;color:#475569;line-height:1.7}.criterion input{margin-top:4px;accent-color:#6366f1;flex-shrink:0}.grade-field{margin-top:22px}.grade-field>label{font-size:12px;color:#64748b;display:block;margin-bottom:10px}.score-input{display:flex;align-items:center;gap:8px}.score-input input{width:104px;height:38px;border:1px solid #e2e8f0;border-radius:6px;padding:0 10px;font-size:18px;font-weight:600;color:#334155}.score-input>span{color:#94a3b8;font-size:12px}.score-input .text-button{margin-left:auto}.grade-field textarea{width:100%;min-height:110px;resize:vertical;border:1px solid #e2e8f0;border-radius:6px;padding:10px;color:#475569;font-size:12px;line-height:1.8}.grade-field input:disabled,.grade-field textarea:disabled{background:#fafbfe;color:#64748b}.grading-actions{display:flex;gap:8px;padding:12px 16px;border-top:1px solid #e4e9f3;flex-shrink:0}.grading-actions .btn{flex:1;padding:0 8px}.primary-button{color:white;background:#4f46e5;border-color:#4f46e5}.primary-button:hover:not(:disabled){background:#4338ca}.return-button{color:#b45309;border-color:#fcd9a8}.review-meta{border-top:1px solid #eef0f5;margin-top:20px;padding-top:12px}.review-meta>div{display:flex;gap:12px;margin:8px 0}.readonly-status{font-size:12px;color:#64748b;margin-top:16px}.history-record{border-bottom:1px solid #e4e9f3;padding:0 0 18px;margin-bottom:18px}.history-record header,.history-meta{display:flex;justify-content:space-between;gap:10px}.history-record header strong{font-size:13px;font-weight:600}.history-record header span{font-size:12px;color:#4f46e5}.history-meta{font-size:11px;color:#94a3b8;margin:8px 0}.history-record p{font-size:12px;color:#475569;white-space:pre-wrap;line-height:1.8;margin-bottom:12px}.historical-data{font-size:12px;color:#64748b;margin-top:8px;line-height:1.8}.empty-state,.workspace-empty{color:#94a3b8;text-align:center;font-size:13px;padding:40px 16px}.workspace-empty{flex:1}.process-error{padding:8px 16px;color:#b45309;background:#fffbeb;border-bottom:1px solid #fde68a;font-size:12px}@media(max-width:1200px){.student-list{width:210px}.detail-columns{grid-template-columns:minmax(0,1fr) 270px}.toolbar-actions input{width:170px}.student-header{padding:12px 16px}.student-header>div{gap:8px}.student-total>span{display:none}.materials-scroll{padding:14px}.step-title{font-size:11px}.process-toolbar{gap:8px;padding:10px 12px}.status-tabs{gap:2px}}@media(max-width:960px){.process-toolbar{flex-wrap:wrap}.toolbar-actions{width:100%}.toolbar-actions input{flex:1}.student-list{width:180px}.detail-columns{display:block;overflow:auto}.materials-panel,.grading-panel{overflow:visible}.grading-panel{border-left:0;border-top:1px solid #e4e9f3}.grading-scroll{overflow:visible}.materials-scroll{overflow:visible}.step-list button{flex:0 0 130px}.student-header{align-items:flex-start}.student-header>div{flex-wrap:wrap}.student-total strong{font-size:11px}.submission-meta{grid-template-columns:1fr}.submission-meta .full{grid-column:auto}}@media(max-width:600px){.student-list{width:140px}.student-header{padding:10px}.student-header>div>span{font-size:11px}.student-total{display:none!important}.status-tabs button{padding:6px 7px}.toolbar-actions select{max-width:120px}.materials-scroll{padding:12px}.student-row{padding:10px}.student-row-top{flex-wrap:wrap}}
</style>
