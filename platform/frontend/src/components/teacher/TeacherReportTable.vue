<template>
  <section class="report-table-page">
    <div class="table-toolbar">
      <div class="status-tabs"><button v-for="item in statuses" :key="item.key" :class="{ active: status === item.key }" @click="status = item.key">{{ item.label }} <span>{{ item.count }}</span></button></div>
      <div class="batch-actions">
        <button class="primary" :disabled="loading || !submittedCount" @click="emit('review-all')"><svg viewBox="0 0 24 24"><path d="m15 5 4 4M4 20l4-1L20 7a2.8 2.8 0 0 0-4-4L4 15v5Z"/></svg>去批改</button>
        <button :disabled="loading || !!exporting || !rows.length" @click="emit('export-grades')"><svg viewBox="0 0 24 24"><path d="M14 3H5v18h14V8l-5-5Zm0 0v5h5M8 12l6 6m0-6-6 6"/></svg>{{ exporting === 'grades' ? '导出中…' : '导出成绩' }}</button>
        <button :disabled="loading || !!exporting || !submittedCount" @click="emit('export-reports')"><svg viewBox="0 0 24 24"><path d="M12 3v12m-4-4 4 4 4-4M4 15v6h16v-6"/></svg>{{ exporting === 'reports' ? '打包中…' : '导出全部实验报告' }}</button>
      </div>
    </div>
    <div class="filter-toolbar"><div class="filters"><div class="search-field"><svg viewBox="0 0 24 24"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg><input v-model.trim="search" type="search" placeholder="搜索姓名、学号" aria-label="搜索报告名单" /></div><select v-model="className" aria-label="报告列表班级"><option value="">全部班级</option><option v-for="name in classes" :key="name">{{ name }}</option></select></div><button class="refresh" @click="emit('refresh')" :disabled="loading"><svg viewBox="0 0 24 24"><path d="M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1"/></svg>{{ loading ? '刷新中…' : '刷新' }}</button></div>
    <p v-if="exportError" class="export-error" role="alert">{{ exportError }}</p>
    <p v-if="simulationError" class="export-error" role="status">{{ simulationError }}</p>
    <div class="table-scroll">
      <table><thead><tr><th>序号</th><th>姓名</th><th>学号</th><th>班级</th><th><button @click="sort('startTime')">开始时间 {{ arrow('startTime') }}</button></th><th><button @click="sort('reportSubmittedAt')">报告提交时间 {{ arrow('reportSubmittedAt') }}</button></th><th>实验用时</th>
        <th>预习题 / 40</th><th>仿真总分 / 100</th><th>预习分析</th>
        <th><button @click="sort('teacherScore')">实验报告得分 {{ arrow('teacherScore') }}</button></th><th><button @click="sort('aiReviewScore')">AI 得分 {{ arrow('aiReviewScore') }}</button></th><th><button @click="sort('processScore')">过程考核总分 {{ arrow('processScore') }}</button></th><th>实验报告状态</th><th class="actions">操作</th></tr></thead>
        <tbody><tr v-for="(row, index) in reportItems" :key="row.userId + '|' + row.studentClass">
          <td class="row-index">{{ String(reportOffset + index + 1).padStart(2, '0') }}</td><td class="name">{{ row.studentName }}</td><td class="student-id">{{ row.username || '—' }}</td><td><span class="class-tag">{{ row.studentClass || '未分班' }}</span></td>
          <td class="date-cell">{{ time(row.startTime) }}</td><td class="date-cell">{{ row.submitted ? time(row.report?.reportSubmittedAt) : '—' }}</td><td class="duration">{{ row.sessionId ? duration(row.minutesOnSession) : '—' }}</td>
          <td class="score-cell">{{ row.simulation?.knowledgeScore ?? '—' }}</td>
          <td class="score-cell">{{ row.simulation?.totalScore ?? '—' }}</td>
          <td><button v-if="row.simulation?.hasResult" class="prep-state" :class="row.simulation.passed ? 'passed' : row.simulation.totalScore != null ? 'failed' : 'answered'" @click="emit('simulation', row)">{{ row.simulation.passed ? '已通过 · 查看详情' : row.simulation.totalScore != null ? '未通过 · 查看详情' : '已答题 · 查看详情' }}</button><span v-else class="missing">{{ simulationError ? '暂时无法读取' : row.simulation?.completed ? '已完成 · 暂无明细' : '暂无结果' }}</span></td>
          <td class="score-cell" :class="{ missing: row.report?.teacherScore == null }">{{ row.report?.teacherScore ?? '—' }}</td><td class="score-cell ai-score" :class="{ missing: row.report?.aiReviewScore == null }">{{ row.report?.aiReviewScore ?? '—' }}</td><td class="score-cell"><button v-if="sessionId(row)" class="process-score" :class="{ ungraded: (row.report?.processScore ?? row.processScore) == null }" @click="emit('process', row)">{{ row.report?.processScore ?? row.processScore ?? '待评价' }}</button><span v-else class="missing">—</span></td>
          <td><span class="state" :class="row.report?.gradingCompleted ? 'graded' : row.submitted ? 'pending' : ''"><i />{{ row.report?.gradingCompleted ? '已批改' : row.submitted ? '待批改' : '未提交' }}</span></td>
          <td class="actions"><div class="action-links"><ProcessRecordDownload :disabled="row.report?.status !== 'FINISHED' && row.status !== 'FINISHED'" :session-id="sessionId(row)" teacher compact /><button class="row-review" :disabled="!row.submitted" @click="emit('review', row)">{{ row.report?.gradingCompleted ? '查看报告' : '去批改' }}</button><button class="text-link" :disabled="!sessionId(row)" @click="emit('process', row)">过程详情</button><button class="text-link download-link" :disabled="!row.submitted" @click="emit('download', row)">下载报告</button></div></td>
        </tr><tr v-if="!visibleRows.length"><td colspan="15" class="empty">{{ loading ? '加载中…' : '暂无符合条件的记录' }}</td></tr></tbody>
      </table>
    </div>
    <ListPagination v-model:page="reportPage" v-model:page-size="reportSize" :total="reportTotal" />
    <footer><span>共 <b>{{ visibleRows.length }}</b> 条记录 · {{ classes.length }} 个教学班</span><span>预习题满分 40，其余成绩满分 100 分</span></footer>
  </section>
</template>
<script setup>
import ProcessRecordDownload from '../experiment/ProcessRecordDownload.vue'
import ListPagination from '../common/ListPagination.vue'
import { usePagination } from '../../composables/usePagination'
import { computed, ref } from 'vue'
const props = defineProps({ rows: { type: Array, default: () => [] }, loading: Boolean, exporting: String, exportError: String, simulationError: String })
const emit = defineEmits(['review', 'review-all', 'export-grades', 'export-reports', 'process', 'download', 'refresh', 'simulation'])
const submittedCount = computed(() => props.rows.filter(row => row.submitted).length)
const search = ref(''), className = ref(''), status = ref('all'), sortField = ref('reportSubmittedAt'), descending = ref(true)
const classes = computed(() => [...new Set(props.rows.map(row => row.studentClass || '未分班'))].sort())
const filtered = computed(() => props.rows.filter(row => (!className.value || (row.studentClass || '未分班') === className.value) && (!search.value || [row.studentName, row.username].some(value => String(value || '').toLowerCase().includes(search.value.toLowerCase())))))
const statuses = computed(() => [{ key: 'all', label: '全部', count: filtered.value.length }, { key: 'pending', label: '待批改', count: filtered.value.filter(row => row.submitted && !row.report?.gradingCompleted).length }, { key: 'graded', label: '已批改', count: filtered.value.filter(row => row.report?.gradingCompleted).length }, { key: 'missing', label: '未提交', count: filtered.value.filter(row => !row.submitted).length }])
const visibleRows = computed(() => filtered.value.filter(row => status.value === 'all' || (status.value === 'pending' && row.submitted && !row.report?.gradingCompleted) || (status.value === 'graded' && row.report?.gradingCompleted) || (status.value === 'missing' && !row.submitted)).slice().sort((a, b) => {
  const value = row => { const raw = row.report?.[sortField.value] ?? row[sortField.value]; return sortField.value.endsWith('Time') || sortField.value.endsWith('At') ? (raw ? new Date(raw).getTime() : null) : raw }
  const av = value(a), bv = value(b)
  if (av == null) return bv == null ? 0 : 1
  if (bv == null) return -1
  return descending.value ? bv - av : av - bv
}))
const sessionId = row => row.report?.sessionId || row.sessionId
function sort(field) { descending.value = sortField.value === field ? !descending.value : true; sortField.value = field }
function arrow(field) { return sortField.value === field ? (descending.value ? '↓' : '↑') : '↕' }
function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—' }
function duration(minutes) { if (minutes == null) return '—'; return minutes < 60 ? `${minutes} 分钟` : `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分` }
const { page: reportPage, pageSize: reportSize, total: reportTotal, offset: reportOffset, items: reportItems } = usePagination(visibleRows, [search, className, status, sortField, descending])
</script>
<style scoped>
thead th { font-size:13px; font-weight:700; color:#334155; background:#f1f5f9; }
thead th button { font:inherit; color:inherit; }
.report-table-page td { font-size:13px; color:#334155; line-height:1.6; }
.report-table-page .name { font-size:14px; font-weight:600; color:#1e293b; }
.report-table-page .date-cell,.report-table-page .class-tag { font-size:12px; color:#475569; }
.report-table-page .score-cell { font-size:15px; font-weight:650; }
.report-table-page .missing { color:#64748b; }
.report-table-page .state { font-weight:600; border:1px solid #cbd5e1; }
.report-table-page .state.graded { border-color:#a7d7c1; color:#065f46; background:#ecfdf5; }
.report-table-page .state.pending { border-color:#efd398; color:#92400e; background:#fffbeb; }
.prep-state { font-size:12px; font-weight:600; padding:5px 8px; border:1px solid; border-radius:5px; }
.prep-state.passed { color:#065f46; background:#ecfdf5; border-color:#a7d7c1; }
.prep-state.failed { color:#b91c1c; background:#fef2f2; border-color:#fecaca; }
.prep-state.answered { color:#1d4ed8; background:#eff6ff; border-color:#bfdbfe; }
.prep-state:hover { text-decoration:underline; }
.prep-state:focus-visible { outline:2px solid #4f46e5; outline-offset:3px; }
.report-table-page{display:flex;flex:1;min-height:0;flex-direction:column;margin:16px 22px 20px;background:#fff;border:1px solid #e4e9f3;border-radius:10px;overflow:hidden;color: #475569;font-size: 14px;box-shadow:0 2px 8px #33415504}.table-toolbar{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:18px 20px;border-bottom:1px solid #edf0f5;flex-wrap:wrap}.status-tabs,.batch-actions,.filters{display:flex;gap:10px;align-items:center}.status-tabs{gap:4px}.status-tabs button{padding:8px 12px;border-radius:6px;color: #64748b}.status-tabs button.active{background:#eef2ff;color: #4f46e5;font-weight:600}.status-tabs span{margin-left:7px;color: #64748b;font-size: 12px}.status-tabs button.active span{color: #6366f1}.batch-actions button{height:36px;display:flex;align-items:center;justify-content:center;gap:7px;border:1px solid #d8d8fa;border-radius:6px;padding:0 13px;color: #5b50d1;background:#fff;font-size: 12px;font-weight:500;white-space:nowrap}.batch-actions button.primary{color:#fff;background:#6557df;border-color:#6557df}.batch-actions button:hover:not(:disabled){background:#f5f3ff}.batch-actions button.primary:hover:not(:disabled){background:#5547cc}svg{width:16px;height:16px;stroke:currentColor;stroke-width:1.7;fill:none;stroke-linecap:round;stroke-linejoin:round;flex-shrink:0}.filter-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 20px}.search-field{display:flex;align-items:center;height:34px;border:1px solid #e2e8f0;border-radius:6px;padding-left:10px;color: #64748b}.search-field input{width:180px;border:0;outline:none;padding:0 10px;background:transparent;color: #475569;font-size: 12px}.search-field:focus-within{border-color:#a5b4fc}.filters select{height:34px;min-width:140px;border:1px solid #e2e8f0;border-radius:6px;padding:0 10px;background:#fff;font-size: 12px}.refresh{display:flex;gap:6px;align-items:center;font-size: 12px;color: #64748b}.table-scroll{flex:1;overflow:auto;min-height:0;margin:0 20px 16px;border:1px solid #e2e8f0;border-radius:7px}table{width:100%;border-collapse:separate;border-spacing:0;white-space:nowrap}th{position:sticky;top:0;background:#f8f9fc;color: #64748b;font-weight:500;z-index:2;font-size: 12px;height:48px;}td,th{padding:14px 13px;border-bottom:1px solid #edf0f5;text-align:left}td:first-child,th:first-child{padding-left:24px}td{font-size: 12px;height:68px;background:#fff}tbody tr:hover td{background:#fbfbff}.name{font-weight:600;color: #334155;font-size: 13px}.student-id,.duration{font-variant-numeric:tabular-nums}.row-index{color: #64748b}.class-tag{display:inline-block;padding:3px 7px;border-radius:4px;background:#f3f5f9;color: #64748b;font-size: 11px}.date-cell{font-size: 11px;color: #64748b;font-variant-numeric:tabular-nums}.score-cell{text-align:center;font-weight:600;font-size:15px;font-variant-numeric:tabular-nums;color: #334155}.ai-score{color: #64748b;font-size:14px}.missing{color: #64748b;font-weight:400}.process-score{color: #6366f1}.process-score.ungraded{font-size: 11px;font-weight:400;color: #64748b}.actions{position:sticky;right:0;min-width:238px;box-shadow:-8px 0 12px -12px #64748b;z-index:1}th.actions{z-index:3;background:#f8f9fc}.actions button{margin-right:10px;font-size: 12px}.actions button:last-child{margin-right:0}.row-review{padding:5px 9px;border:1px solid #e0dcfb;border-radius:5px;color: #6557df;background:#faf9ff}.text-link{color: #6366f1}.download-link{color: #64748b}button:disabled{opacity:.45;cursor:default}.row-review:disabled{background:#fafbfc;border-color:#e2e8f0;color: #64748b}.state{display:inline-flex;align-items:center;gap:5px;padding:4px 7px;border-radius:4px;color: #64748b;font-size: 11px;background:#f5f6f8}.state i{width:5px;height:5px;border-radius:50%;background:currentColor}.state.graded{color: #047857;background:#eff9f4}.state.pending{color: #92400e;background:#fffaeb}.empty{text-align:center;height:180px;color: #64748b}footer{padding:13px 20px;color: #64748b;font-size: 12px;display:flex;justify-content:space-between;border-top:1px solid #edf0f5}footer b{font-weight:500;color: #64748b}.export-error{padding:8px 20px;color: #b45309;background:#fffbeb;font-size: 12px}@media(max-width:1100px){.table-toolbar{gap:12px}.batch-actions{flex-wrap:wrap}.report-table-page{margin:12px}}@media(max-width:650px){.table-scroll{margin:0 12px 12px}.filters{flex-wrap:wrap}.table-toolbar,.filter-toolbar{padding:12px}.status-tabs button{padding:6px 8px}.batch-actions{gap:6px}.batch-actions button{padding:0 9px;font-size: 11px}.actions{position:static}.report-table-page{margin:8px}.search-field input{width:140px}}

.actions .action-links{display:inline-flex;align-items:center;gap:16px;white-space:nowrap}
.actions .action-links :deep(button){margin:0;padding:0;border:0;border-radius:0;background:transparent;box-shadow:none;font-size:13px;font-weight:400;line-height:20px;color:#6366f1}
.actions .action-links :deep(button:hover:not(:disabled)){background:transparent;text-decoration:underline}
.actions .action-links :deep(button:disabled){color:#b0b8c6;background:transparent;border:0;opacity:1;cursor:not-allowed}
</style>
