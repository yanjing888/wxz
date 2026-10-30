import { usePagination } from '../src/composables/usePagination.js'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import { pendingLearning, recordRoute } from '../src/utils/learning.js'

const renderer = Vue.createRenderer({ createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }), insert: (child, parent) => parent.children.push(child), remove() {}, setElementText() {}, setText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const empty = { render: () => null }
globalThis.window = { addEventListener() {}, removeEventListener() {} }
function mount(path, deps = {}, props = {}) {
  const { descriptor } = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  let code = compileScript(descriptor, { id: 'learning-check' }).content
  code = code.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, source) => `const ${names} = __deps[${JSON.stringify(source)}]`).replace('export default', 'return')
  const component = new Function('__deps', code)(new Proxy({ vue: Vue, '../composables/usePagination': { usePagination }, '../../composables/usePagination': { usePagination }, ...deps }, { get: (obj, key) => obj[key] || empty }))
  component.render = () => null
  const app = renderer.createApp(component, props); app.mount({ children: [] })
  return { app, state: app._instance.setupState, props: app._instance.props }
}
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await Vue.nextTick() } }
const records = [
  { code: 'current', name: '当前实验', state: 'CURRENT', currentTask: true, points: 10 },
  { code: 'queued', name: '待开始', state: 'QUEUED', points: 0 },
  { code: 'report', name: '待交报告', state: 'REPORT_PENDING', finished: true, points: 20 },
  { code: 'feedback', name: '待订正', state: 'GRADED', finished: true, submitted: true, graded: true, reflectionCurrent: false, feedbackRevision: 'revision-1', points: 80 },
  { code: 'done', name: '已订正', state: 'GRADED', finished: true, submitted: true, graded: true, reflectionCurrent: true, points: 90 }
]
assert.deepEqual(pendingLearning(records).map(r => r.code), ['current', 'report', 'feedback'])
assert.equal(recordRoute(records[0]).name, 'lab'); assert.equal(recordRoute(records[2]).name, 'after-center')
const dashboard = { className: '一班', classes: ['一班'], summary: { records, dimensions: [], totalPoints: 200 }, leaderboard: [], selfRank: 1 }
let resolveOld, rejectSave = true, saved
const old = new Promise(resolve => { resolveOld = resolve })
let first = true
const utils = await import('../src/utils/learning.js')
const student = mount('../src/views/MyLearningView.vue', {
  'vue-router': { useRouter: () => ({ push() {} }) },
  '../utils/learning': utils,
  '../api/learning': { learningError: e => e.message, learningApi: {
    student: async () => { if (first) { first = false; return old }; return { data: dashboard } },
    reflect: async (...args) => { if (rejectSave) throw new Error('教师反馈已更新'); saved = args }
  } }
})
await student.state.load(); await settle()
resolveOld({ data: { ...dashboard, className: '过期班级' } }); await settle()
assert.equal(student.state.className, '一班', 'late responses must not overwrite the current dashboard')
assert.equal(student.state.visibleRecords.length, 3)
student.state.selectedTab = 'feedback'; await settle(); assert.equal(student.state.visibleRecords.length, 2)
await student.state.editReflection(records[3]); student.state.reflectionText = '补充误差分析'
await student.state.saveReflection(); assert.equal(student.state.reflectionRecord.code, 'feedback'); assert.equal(student.state.reflectionError, '教师反馈已更新')
rejectSave = false; await student.state.saveReflection(); assert.deepEqual(saved, ['feedback', '补充误差分析', 'revision-1']); assert.equal(student.state.reflectionRecord, null)
student.app.unmount()

let detailResolve, requestedClass, requestedExperiment, detailExperiment, requestedStudent
const teacher = mount('../src/components/teacher/TeacherLearningPanel.vue', {
  '../../utils/learning': utils,
  '../../api/learning': { learningError: e => e.message, learningApi: {
    teacher: async (className, experimentCode) => { requestedExperiment = experimentCode; return { data: { className: '一班', classes: ['一班'], leaderboard: [{ userId: 10, name: '张同学', username: '001' }, { userId: 20, name: '李同学', username: '002' }] } } },
    detail: async (id, className, experimentCode) => { requestedStudent = id; requestedClass = className; detailExperiment = experimentCode; return new Promise(resolve => { detailResolve = resolve }) }
  } }
}, { experimentCode: 'newton_rings' })
await settle(); teacher.state.search = '002'; await settle(); assert.equal(teacher.state.filtered.length, 1)
assert.equal(requestedExperiment, 'newton_rings')
const detailPromise = teacher.state.openDetail({ userId: 20 }); await settle(); assert.equal(requestedClass, '一班')
assert.equal(detailExperiment, 'newton_rings')
teacher.state.closeDetail(); detailResolve({ data: { name: '已关闭的记录' } }); await detailPromise; assert.equal(teacher.state.detail, null, 'closing detail invalidates an in-flight response')
const personalPromise = teacher.state.openDetail({ userId: 20, name: '李同学', rank: 2 }); await settle()
assert.equal(requestedStudent, 20, 'details use the clicked student ID')
detailResolve({ data: { userId: 20, name: '李同学', username: '002', totalPoints: 30, records: [{ code: 'newton_rings', prepared: true, finished: true, submitted: false, graded: false }] } }); await personalPromise
assert.equal(teacher.state.detail.userId, 20); assert.equal(teacher.state.detail.totalPoints, 30); assert.equal(teacher.state.selectedRanking.rank, 2)
assert.equal(teacher.state.recordMilestones(teacher.state.detail.records[0]).find(s => s.label === '实验报告').status, '未提交', 'personal detail does not infer submission from experiment completion')
assert.equal(teacher.state.recordMilestones({ submitted: true, graded: false }).find(s => s.label === '实验报告').status, '待批阅')
assert.equal(teacher.state.recordMilestones({ submitted: true, graded: true }).find(s => s.label === '实验报告').status, '已批阅')
teacher.props.experimentCode = 'air_wedge_thickness'; await settle(); assert.equal(requestedExperiment, 'air_wedge_thickness', 'switching experiments reloads statistics in the same scope')
assert.equal(teacher.state.detailOpen, false, 'changing experiment closes the previous student detail')
teacher.app.unmount()

const dimensions = ['准备', '实验', '数据', '报告', '订正'].map((label, i) => ({ key: String(i), label, value: null }))
const radar = mount('../src/components/learning/LearningRadar.vue', {}, { dimensions })
assert.equal(radar.state.hasData, false); assert.equal(radar.state.complete, false); assert.equal(radar.state.outlinePath, '')
radar.props.dimensions = dimensions.map((d, i) => ({ ...d, value: i < 2 ? 50 : null })); await settle()
assert.equal(radar.state.hasData, true); assert.equal(radar.state.complete, false); assert(radar.state.outlinePath.startsWith('M 160,136')); assert(!radar.state.description.includes('NaN'))
assert.equal(radar.state.hasArea, true, 'two adjacent measured dimensions form a filled triangle with the origin')
radar.props.dimensions = dimensions.map((d, i) => ({ ...d, value: [100, 100, 50, null, null][i] })); await settle()
assert.equal(radar.state.complete, false); assert.equal(radar.state.hasArea, true)
assert.equal(radar.state.sectors.length, 2, 'three consecutive measurements fill two triangles from the center')
assert(radar.state.sectors.every(sector => sector.points.startsWith('160,136 ') && sector.points.split(' ').length === 3))
assert(radar.state.outlinePath.endsWith('L 160,136 Z'), 'an incomplete measured range closes back to the center instead of skipping missing axes')
radar.props.dimensions = dimensions.map(d => ({ ...d, value: 50 })); await settle()
assert.equal(radar.state.sectors.length, 5, 'a complete radar fills every adjacent interval from the center')
radar.props.dimensions = dimensions.map((d, i) => ({ ...d, value: i === 0 || i === 2 ? 50 : null })); await settle()
assert.equal(radar.state.sectors.length, 0, 'missing axes do not get bridged by fabricated filled areas')
radar.props.dimensions = dimensions.map(d => ({ ...d, value: 0 })); await settle()
assert.equal(radar.state.hasArea, false, 'all-zero measurements must not be expanded to a fictitious area')
radar.app.unmount()

const tab = mount('../src/components/layout/StudentTabBar.vue', {
  'vue-router': { useRoute: () => ({ name: 'my-learning', query: {}, params: {} }) },
  '../../stores/tasks': { useTaskStore: () => ({ current: { experimentCode: 'newton_rings' } }) },
  '../../api': { studentExperimentApi: { getProgress: async () => ({ data: { labCompleted: false } }) } },
  '../../utils/experimentFlow': { lastExperiment: () => '' },
  '../../utils/prepSimulation': { requiresSimulation: () => true, simulationPrepRoute: code => ({ name: 'prep-simulation', params: { code } }) }
})
await settle(); assert.equal(tab.state.simulationLocked, true); assert(tab.state.tabs.some(t => t.key === 'learning')); assert(!tab.state.tabs.some(t => t.key === 'lab')); assert(tab.state.isActive({ key: 'learning' }))
tab.app.unmount()
console.log('Passed: learning tasks and routes, stale dashboard/detail protection, feedback revision handling, missing radar evidence, and learning navigation through simulation gating.')
