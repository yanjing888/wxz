import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'

// Run the actual SFC setup functions with Vue's renderer, without a browser or live student data.
const renderer = Vue.createRenderer({
  createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }),
  insert: (child, parent) => parent.children.push(child), remove() {}, setElementText() {}, setText() {},
  parentNode: () => null, nextSibling: () => null, patchProp() {}
})
const emptyComponent = { render: () => null }
function mountSfc(path, dependencies = {}, props = {}) {
  const { descriptor } = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  let code = compileScript(descriptor, { id: 'report-review-check' }).content
  code = code.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, source) => {
    return `const ${names} = __deps[${JSON.stringify(source)}]`
  }).replace('export default', 'return')
  const deps = new Proxy({ vue: Vue, ...dependencies }, { get: (target, key) => target[key] || emptyComponent })
  const component = new Function('__deps', code)(deps)
  component.render = () => null
  const app = renderer.createApp(component, props)
  app.mount({ children: [] })
  return { app, state: app._instance.setupState }
}
const settle = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await Vue.nextTick() } }
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const students = [
  { userId: 1, username: '2026001', studentName: '张明', studentClass: '物理一班' },
  { userId: 2, username: '2026002', studentName: '李晨', studentClass: '物理一班' },
  { userId: 3, username: '2026003', studentName: '王欣', studentClass: '物理一班' },
  { userId: 1, username: '2026001', studentName: '张明', studentClass: '物理二班' }
].map((s, i) => ({ ...s, displayName: s.studentName, experimentCode: 'newton_rings', assignedExperimentCodes: ['newton_rings'], sessionId: 100 + i, reportCompleted: i !== 2 }))
const reports = students.filter(s => s.reportCompleted).map(s => ({ ...s, startTime: '2026-10-15T10:00:00', endTime: '2026-10-15T11:00:00', gradingCompleted: s.sessionId === 103, aiReviewScore: 75, aiReviewComment: '原 AI 建议', teacherScore: s.sessionId === 103 ? 80 : null }))
let reviewWait, previewWait, saveCalls = []
const classSelections = { newton_rings: '物理一班' }
const classOptions = {}
const scopedRequests = []
const detail = id => ({ ...reports.find(r => r.sessionId === id), studentReportSections: { results: '测得曲率半径 1.02 m' } })
const teacherApi = {
  simulationResults: async () => ({ data: [{ userId: 3, studentClass: '物理一班', hasResult: true, knowledgeScore: 35, totalScore: null }, { userId: 1, studentClass: '物理二班', hasResult: true, knowledgeScore: 40, totalScore: 100, passed: true }] }),
  teachingContext: async code => ({ data: { experimentCode: code, className: classSelections[code] || '', classes: classOptions[code] || ['物理一班', '物理二班'] } }),
  selectTeachingClass: async (code, name) => { classSelections[code] = name; return { data: {} } },
  students: async () => ({ data: students }), overview: async () => ({ data: {} }),
  classroom: async scope => { scopedRequests.push(scope); return { data: { students } } }, reports: async () => ({ data: reports.map(r => ({ ...r })) }), feedback: async () => ({ data: [] }),
  report: async id => previewWait?.id === id ? previewWait.promise : ({ data: detail(id) }),
  reviewReport: async () => reviewWait.promise,
  completeGrade: async (id, body) => { saveCalls.push({ id, ...body }); Object.assign(reports.find(r => r.sessionId === id), { teacherScore: body.score, teacherComment: body.comment, gradingCompleted: true }); return { data: detail(id) } }
}
globalThis.localStorage = { getItem: () => null, setItem() {} }
const teacher = mountSfc('../src/views/TeacherView.vue', {
  '../api': { teacherApi, experimentApi: { list: async () => ({ data: [{ code: 'newton_rings', name: '牛顿环实验' }] }) }, systemApi: { benchCamera: async () => ({ data: {} }) } },
  'vue-router': { useRouter: () => ({}), useRoute: () => ({ query: {} }) }, '../stores/auth': { useAuthStore: () => ({}) },
  '../utils/sessionReport': { REPORT_SECTION_DEFS: [{ key: 'purpose', label: '1. 实验目的' }] }
})
const state = teacher.state
await settle()
state.students = [...state.students, { userId: 99, studentClass: '未参加当前实验的班级' }]
assert.equal(state.studentsByClass.length, 2, 'Unrelated roster classes must not appear in the selected experiment')
const initialClassroom = state.classroom
state.classroom = { students: [] }
assert.equal(state.studentsByClass.length, 0, 'An experiment with no participating students must show no class groups')
state.classroom = initialClassroom
assert.equal(state.navTabs.find(tab => tab.key === 'overview').label, '学情分析')
assert.equal(state.reportPage, 'list', 'Report analysis must start with the summary table')
assert.equal(state.reportsByClass.flatMap(g => g.students).find(s => s.userId === 3).simulation.knowledgeScore, 35, 'Prep results are visible before a formal report is submitted')
assert.equal(state.reportsByClass.flatMap(g => g.students).find(s => s.userId === 1 && s.studentClass === '物理一班').simulation, null, 'Prep results must not leak across classes')
assert(!state.navTabs.some(tab => tab.key === 'learning'), 'statistics belongs to the shared analysis tab')
assert.equal(state.analysisPage, 'overview')
assert.equal(state.experimentContextVisible, true)
state.analysisPage = 'learning'; await settle()
assert.equal(state.experimentContextVisible, true, 'statistics and overview share the selected experiment')
state.activeTab = 'students'; await settle(); assert.equal(state.experimentContextVisible, true)
state.activeTab = 'overview'; await settle(); assert.equal(state.analysisPage, 'learning', 'returning to analysis preserves its selected menu')
state.analysisPage = 'overview'; await settle()
assert.equal(state.overviewReportStatus(students[0]).label, '待批阅')
assert.equal(state.overviewReportStatus(students[2]).label, '未提交')
assert.equal(state.overviewReportStatus({ ...students[2], status: 'FINISHED' }).label, '未提交', 'finishing the lab does not mean submitting a report')
assert.equal(state.overviewReportStatus(students[3]).label, '已批阅', 'same student in another class keeps its own report state')
assert.deepEqual(state.reportStatuses.map(s => [s.key, s.count]), [['all', 4], ['submitted', 2], ['graded', 1], ['pending', 1]])
assert.equal(state.filteredReportsByClass.length, 2)
state.reportClassFilter = '物理二班'
await settle()
assert.equal(state.selectedReportId, 103)
assert.equal(state.selectedReportItem.username, '2026001')
state.reportClassFilter = ''
state.selectReportItem(100)
await settle()
state.gradeScore = 88
state.gradeComment = '教师自行修改的评语'
await settle()
state.selectReportItem(101)
await settle()
state.selectReportItem(100)
await settle()
assert.equal(state.gradeScore, 88)
assert.equal(state.gradeComment, '教师自行修改的评语')

reviewWait = deferred()
const reviewing = state.runAiReviewInPlace()
state.selectReportItem(101)
assert.equal(state.selectedReportId, 100, 'AI request must lock student navigation')
reviewWait.resolve({ data: { score: 92, comment: '新的 AI 建议' } })
await reviewing
assert.equal(state.aiReviewScore, 92)
assert.equal(state.gradeScore, 88, 'Re-evaluation must preserve the teacher score')
assert.equal(state.gradeComment, '教师自行修改的评语', 'Re-evaluation must preserve teacher edits')

state.gradeScore = 101
await state.finishGrade()
assert.equal(saveCalls.length, 0)
state.gradeScore = 88
await state.finishGrade()
await settle()
assert.deepEqual(saveCalls[0], { id: 100, score: 88, comment: '教师自行修改的评语' })
assert.equal(state.selectedReportId, 100, 'Saving must preserve selection when still in the filtered list')
assert.equal(state.gradingCompleted, true)
assert.equal(state.gradeDraftPending, false)
assert.equal(state.gradeDrafts[100], undefined)
state.gradeScore = 90
await settle()
assert.equal(state.gradeDrafts[100].score, 90)
state.gradeScore = 88
await settle()
assert.equal(state.gradeDrafts[100], undefined, 'Reverting edits must clear the temporary draft')

state.reportStateFilter = 'pending'
await settle()
assert.equal(state.selectedReportId, null)
assert.equal(state.previewLoading, false)
assert.equal(state.gradeScore, null)
state.reportStateFilter = 'all'
await settle()
previewWait = { id: 101, ...deferred() }
state.selectReportItem(101)
await settle()
state.selectReportItem(103)
await settle()
previewWait.resolve({ data: detail(101) })
await settle()
assert.equal(state.selectedReportPreview.sessionId, 103, 'A late preview response must not replace the selected report')
state.reportSearch = '2026002'
await settle()
assert.equal(state.reportNavigation.length, 1)
assert.equal(state.selectedReportId, 101)
state.reportSearch = ''
state.reportSortField = 'name'
state.reportSortDirection = 'asc'
await settle()
assert.deepEqual(state.filteredReportsByClass[0].students.map(s => s.studentName), ['李晨', '王欣', '张明'])
state.reportStateFilter = 'submitted'
await settle()
assert.equal(state.selectedReportId, 101)
state.gradeScore = 0
await state.finishGrade()
await settle()
assert.equal(saveCalls.at(-1).score, 0, 'Zero is a valid grade')
assert.equal(state.selectedReportId, null, 'The pending queue must move on after grading')
assert.equal(state.navTabs.find(tab => tab.key === 'report').label, '实验评价')
const classroomBeforeScope = state.classroom
state.classroom = { students: students.map(s => ({ ...s, reportCompleted: true })) }
assert.equal(state.submittedReportCount, 3, 'A stale completion flag without a submitted report must not create a reviewable report')
assert.equal(state.reportsByClass.flatMap(group => group.students).find(s => s.userId === 3).submitted, false)
state.classroom = { students: [...students, { userId: 90, username: 'unassigned', studentName: '未分配学生', studentClass: '未分配班级', reportCompleted: false }] }
assert.equal(state.reportsByClass.length, 2, 'Evaluation must exclude classes without this experiment assignment')
state.classroom = classroomBeforeScope
await state.openReportReview(state.reportsByClass[0].students.find(row => row.submitted))
const singleId = state.reviewStudentId
assert.deepEqual(state.reportNavigation, [singleId], 'Row review must expose only the selected student')
state.selectReportItem(103)
assert.equal(state.selectedReportId, singleId, 'Single review cannot switch to another student')
state.reportPage = 'list'
await state.openAllReports()
assert.equal(state.reviewStudentId, null)
assert.equal(state.reportPage, 'review')
assert.equal(state.reportNavigation.length, 3, 'Top-level review must include submitted reports across all assigned classes')
assert.equal(state.reportClassFilter, '')
state.reportPage = 'list'
const beforeClassChange = { classroom: state.classroom, reports: state.reports, feedback: state.feedbackList }
await state.onTeachingClassChange('')
await settle()
assert.equal(state.selectedClass, '')
assert.equal(state.classroom, beforeClassChange.classroom, 'Clearing the process class must preserve learning data')
assert.equal(state.reports, beforeClassChange.reports, 'Clearing the process class must preserve reports')
assert.equal(state.feedbackList, beforeClassChange.feedback, 'Clearing the process class must preserve feedback')
for (const tab of ['overview', 'reports', 'feedback']) {
  state.activeTab = tab
  assert.equal(state.teachingTab, false, `${tab} must not be gated by the process class`)
}
state.activeTab = 'process'
assert.equal(state.teachingTab, true)
await state.loadExperimentData()
assert.equal(state.classroom.students.length, students.length, 'No process class must not prevent loading learning data')
assert.equal(state.reports.length, reports.length)
assert.ok(scopedRequests.every(scope => !('className' in scope)), 'Regular teacher data must use the experiment scope only')
await state.onTeachingClassChange('物理二班')
assert.equal(state.selectedClass, '物理二班')
state.selectedExpCode = 'tensile_steel'
await settle()
assert.equal(state.selectedClass, '', 'An experiment without a saved process class stays empty')
state.selectedExpCode = 'newton_rings'
await settle()
assert.equal(state.selectedClass, '物理二班', 'The process class is remembered per experiment')
state.activeTab = 'students'
await settle()
classOptions.newton_rings = ['物理一班']
classSelections.newton_rings = ''
state.activeTab = 'process'
await settle()
assert.deepEqual(state.teachingClasses, ['物理一班'], 'Re-entering process assessment must refresh the assigned class options')
assert.equal(state.selectedClass, '', 'A removed assignment must not leave a stale selected class')
// Optional prep failures (including an older running backend returning 404)
// must not erase the overview or the existing experiment evaluation roster.
const originalSimulationApi = teacherApi.simulationResults
teacherApi.simulationResults = async () => { throw Object.assign(new Error('Not Found'), { response: { status: 404 } }) }
state.clearTeachingData()
await state.loadExperimentData()
await settle()
assert.equal(state.classroom.students.length, students.length)
assert.equal(state.reports.length, reports.length)
assert.ok(state.reportsByClass.flatMap(g => g.students).length > 0)
assert.ok(state.simulationError)
assert.equal(state.loadError, '')
const slowSimulation = deferred()
teacherApi.simulationResults = () => slowSimulation.promise
state.clearTeachingData()
await state.loadExperimentData()
assert.equal(state.reports.length, reports.length, 'A hanging optional request must not delay reports')
teacherApi.simulationResults = originalSimulationApi
await state.loadExperimentData()
slowSimulation.resolve({ data: [{ userId: 999, hasResult: true }] })
await settle()
assert.ok(!state.simulationResults.some(row => row.userId === 999), 'Late supplemental responses must not replace newer data')
assert.equal(state.simulationError, '')
teacher.app.unmount()

const emitted = []
const grading = mountSfc('../src/components/teacher/ReportGradingPanel.vue', {}, {
  sessionId: 100, score: 88, comment: '手动评语', aiScore: 92, aiComment: 'AI 建议',
  'onUpdate:score': value => emitted.push(['score', value]), 'onUpdate:comment': value => emitted.push(['comment', value])
})
assert.equal(grading.state.canFinish, true)
grading.state.adoptReview()
assert.deepEqual(emitted, [['score', 92], ['comment', 'AI 建议']])
grading.state.scoreModel = ''
assert.deepEqual(emitted.at(-1), ['score', null])
grading.app.unmount()
for (const [score, valid] of [[0, true], [100, true], [101, false], [-1, false], [NaN, false], [null, false]]) {
  const check = mountSfc('../src/components/teacher/ReportGradingPanel.vue', {}, { sessionId: 100, score })
  assert.equal(check.state.canFinish, valid, `Grade validation: ${score}`)
  check.app.unmount()
}
const imageReport = mountSfc('../src/components/teacher/StudentReportDocument.vue', {
  '../../utils/sessionReport': { REPORT_SECTION_DEFS: [{ key: 'data', label: '数据' }] },
  '../../utils/markdown': { renderMathInMarkdown: text => text }
}, { report: { studentReportSections: { data: '<img src="data:image/png;base64,AAAA" alt="测量照片">' } } })
assert.equal(imageReport.state.hasAnySection, true, 'Image-only sections must be visible')
imageReport.app.unmount()

const tableRows = [
  { userId: 1, studentName: '甲', username: '001', studentClass: '一班', sessionId: 1, submitted: true, report: { sessionId: 1, teacherScore: 0, aiReviewScore: 88, processScore: 90, gradingCompleted: true, reportSubmittedAt: '2026-10-21T10:00:00' } },
  { userId: 2, studentName: '乙', username: '002', studentClass: '二班', sessionId: 2, submitted: true, report: { sessionId: 2, teacherScore: null, processScore: null, reportSubmittedAt: '2026-10-21T11:00:00' } },
  { userId: 3, studentName: '丙', username: '003', studentClass: '一班', submitted: false }
]
const tableView = mountSfc('../src/components/teacher/TeacherReportTable.vue', {}, { rows: tableRows })
assert.deepEqual(tableView.state.visibleRows.map(row => row.userId), [2, 1, 3], 'Sort by actual report submission time, with unsubmitted students last')
tableView.state.sort('teacherScore')
assert.equal(tableView.state.visibleRows[0].report.teacherScore, 0, 'Zero must remain a valid visible grade')
tableView.state.status = 'missing'
assert.deepEqual(tableView.state.visibleRows.map(row => row.userId), [3])
tableView.state.className = '二班'
assert.equal(tableView.state.visibleRows.length, 0)
tableView.app.unmount()

let uploaded, uploadResult, uploadFails = true
const upload = mountSfc('../src/components/stage/ReportUploadPanel.vue', { '../../api': { studentExperimentApi: {
  uploadReport: async (code, id, file) => { uploaded = { code, id, file }; if (uploadFails) throw new Error('offline'); return { data: { reportCompleted: true, reportFileName: file.name } } }
} } }, { experimentCode: 'newton_rings', sessionId: 7, progress: {}, onSubmitted: value => { uploadResult = value } })
upload.state.choose({ target: { files: [{ name: 'report.exe', size: 10 }], value: '' } })
assert.equal(upload.state.file, null)
const reportFile = new File(['%PDF-1.4'], '个人报告.pdf', { type: 'application/pdf' })
upload.state.choose({ target: { files: [reportFile], value: '' } })
await upload.state.submit()
assert.equal(upload.state.file, reportFile, 'Failed uploads must keep the chosen report for retry')
assert.equal(uploadResult, undefined)
uploadFails = false
await upload.state.submit()
assert.equal(uploaded.file, reportFile)
assert.equal(uploadResult.reportFileName, '个人报告.pdf')
assert.equal(upload.state.file, null)
upload.app.unmount()
// The score strip must reflect server progress and preserve zero as a real score.
for (const [assessment, submitted, score, processStatus, overallStatus] of [
  [{ requiredCount: 2, gradedCount: 0, score: null, steps: [{ required: true, status: 'NOT_STARTED' }] }, false, null, '待提交检查', '待提交报告'],
  [{ requiredCount: 2, gradedCount: 0, score: null, steps: [{ required: true, status: 'PENDING' }] }, true, null, '待教师评价', '待完成评价'],
  [{ requiredCount: 2, gradedCount: 1, score: null, steps: [] }, true, 80, '已评 1/2 项', '待过程评价'],
  [{ requiredCount: 2, gradedCount: 1, score: null, steps: [{ required: true, status: 'REVISION' }] }, true, 80, '待整改', '待过程评价'],
  [{ requiredCount: 2, gradedCount: 2, score: 0, steps: [] }, true, null, null, '待报告评分']
]) {
  const summary = mountSfc('../src/components/experiment/ProcessScoreSummary.vue', {}, { assessment, reportSubmitted: submitted, reportScore: score })
  if (processStatus) assert.equal(summary.state.processStatus, processStatus)
  assert.equal(summary.state.overallStatus, overallStatus)
  summary.app.unmount()
}
const progressSource = readFileSync(new URL('../src/composables/useReportProgress.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export function', 'function')
const focusEvents = new Map(), visibilityEvents = new Map()
const previousWindow = globalThis.window, previousDocument = globalThis.document
globalThis.window = { addEventListener: (name, fn) => focusEvents.set(name, fn), removeEventListener: name => focusEvents.delete(name) }
globalThis.document = { visibilityState: 'visible', addEventListener: (name, fn) => visibilityEvents.set(name, fn), removeEventListener: name => visibilityEvents.delete(name) }
let progressResponse = { data: { reportCompleted: false, processAssessment: { score: null } } }
const useProgress = new Function('Vue', 'studentExperimentApi', `const {ref,watch,onMounted,onBeforeUnmount}=Vue; ${progressSource}; return useReportProgress`)(Vue, { getProgress: async () => { if (progressResponse instanceof Error) throw progressResponse; return progressResponse } })
const progressCode = Vue.ref('first'), progressSession = Vue.ref(1)
let live
const progressApp = renderer.createApp({ setup() { live = useProgress(() => progressCode.value, () => progressSession.value); return () => null } })
progressApp.mount({ children: [] })
await live.refresh()
assert.equal(live.progress.value.reportCompleted, false)
progressResponse = { data: { reportCompleted: true, gradingCompleted: true, teacherScore: 0, processAssessment: { score: 80 }, overallScore: 32, processScoreWeight: 40 } }
focusEvents.get('focus')()
await settle()
assert.equal(live.progress.value.teacherScore, 0)
assert.equal(live.progress.value.overallScore, 32, 'Refresh uses the backend weighted score, including a zero report score')
progressResponse = new Error('offline')
await live.refresh()
assert.ok(live.error.value, 'A failed refresh must be visible instead of silently claiming current scores')
const lateProgress = deferred()
progressResponse = lateProgress.promise
const pendingProgress = live.refresh()
live.apply({ reportCompleted: true, teacherScore: 90 })
lateProgress.resolve({ data: { reportCompleted: false } })
await pendingProgress
assert.equal(live.progress.value.reportCompleted, true, 'A late refresh must not overwrite a successful submission')
const oldExperiment = deferred()
progressResponse = oldExperiment.promise
const staleRequest = live.refresh()
progressCode.value = 'second'; progressSession.value = 2
await settle()
oldExperiment.resolve({ data: { teacherScore: 99 } })
await staleRequest
assert.equal(live.progress.value, null, 'Never show another experiment’s scores')
progressApp.unmount()
assert.equal(focusEvents.size + visibilityEvents.size, 0)
globalThis.window = previousWindow; globalThis.document = previousDocument
console.log('Passed: class/status filters, roster identity, draft navigation, AI/human separation, 100-point validation, saved selection, stale-response protection, explicit AI adoption and image-only sections.')
