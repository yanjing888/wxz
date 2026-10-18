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
const detail = id => ({ ...reports.find(r => r.sessionId === id), studentReportSections: { results: '测得曲率半径 1.02 m' } })
const teacherApi = {
  students: async () => ({ data: students }), overview: async () => ({ data: {} }),
  classroom: async () => ({ data: { students } }), reports: async () => ({ data: reports.map(r => ({ ...r })) }), feedback: async () => ({ data: [] }),
  report: async id => previewWait?.id === id ? previewWait.promise : ({ data: detail(id) }),
  reviewReport: async () => reviewWait.promise,
  completeGrade: async (id, body) => { saveCalls.push({ id, ...body }); Object.assign(reports.find(r => r.sessionId === id), { teacherScore: body.score, teacherComment: body.comment, gradingCompleted: true }); return { data: detail(id) } }
}
globalThis.localStorage = { getItem: () => null, setItem() {} }
const teacher = mountSfc('../src/views/TeacherView.vue', {
  '../api': { teacherApi, experimentApi: { list: async () => ({ data: [{ code: 'newton_rings', name: '牛顿环实验' }] }) }, systemApi: { benchCamera: async () => ({ data: {} }) } },
  'vue-router': { useRouter: () => ({}) }, '../stores/auth': { useAuthStore: () => ({}) },
  '../utils/sessionReport': { REPORT_SECTION_DEFS: [{ key: 'purpose', label: '1. 实验目的' }] }
})
const state = teacher.state
await settle()
assert.equal(state.navTabs.find(tab => tab.key === 'overview').label, '学情分析')
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
console.log('Passed: class/status filters, roster identity, draft navigation, AI/human separation, 100-point validation, saved selection, stale-response protection, explicit AI adoption and image-only sections.')
