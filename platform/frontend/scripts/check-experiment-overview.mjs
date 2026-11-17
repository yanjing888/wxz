import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import * as overview from '../src/utils/experimentOverview.js'

const rows = [
  { experimentCode: 'queued', experimentName: '油滴实验', currentTask: false },
  { experimentCode: 'current', experimentName: '霍尔效应实验', currentTask: true, activeSessionId: 12 },
  { experimentCode: 'report', experimentName: '薄透镜焦距测量', labCompleted: true },
  { experimentCode: 'submitted', experimentName: '声速测量实验', labCompleted: true, reportCompleted: true },
  { experimentCode: 'done', experimentName: '分光计实验', labCompleted: true, reportCompleted: true, gradingCompleted: true, teacherScore: 0 }
]
assert.deepEqual(overview.orderedExperiments(rows).map(x => x.experimentCode), ['current', 'report', 'queued', 'submitted', 'done'])
assert.equal(overview.experimentAction(rows[0]), null, 'Queued work must not offer a misleading entry link')
assert.equal(overview.experimentAction(rows[1]).to.query.exp, 'current')
assert.equal(overview.experimentAction({ ...rows[1], simulationRequired: true }).to.name, 'prep-simulation')
assert.equal(overview.experimentAction(rows[2]).to.params.code, 'report')
assert.equal(overview.experimentState(rows[3]), 'submitted', 'Submitted reports are not graded work')
assert.equal(overview.experimentState(rows[4]), 'done', 'Zero score is still a graded result')

const tasks = Vue.reactive({ items: rows, loaded: true, loading: false, error: '', current: rows[1], async load() {} })
const source = readFileSync(new URL('../src/views/experiment/MyExperimentsView.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
let code = compileScript(descriptor, { id: 'overview-check' }).content
code = code.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, path) => `const ${names} = __deps[${JSON.stringify(path)}]`).replace('export default', 'return')
const component = new Function('__deps', code)({ vue: Vue, '../../stores/tasks': { useTaskStore: () => tasks }, '../../utils/experimentOverview': overview })
component.render = () => null
const renderer = Vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert() {}, remove() {}, setElementText() {}, setText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const app = renderer.createApp(component); app.mount({})
const state = app._instance.setupState
assert.equal(state.counts.all, 5)
assert.equal(Object.entries(state.counts).filter(([key]) => key !== 'all').reduce((sum, [, count]) => sum + count, 0), 5)
state.selected = 'queued'; assert.deepEqual(state.visible.map(x => x.experimentCode), ['queued'])
state.selected = 'all'; state.search = '霍尔'; assert.deepEqual(state.visible.map(x => x.experimentCode), ['current'])
state.search = '不存在'; assert.equal(state.visible.length, 0)
state.search = ''; tasks.items = []; assert.equal(state.counts.all, 0); assert.equal(state.visible.length, 0)
app.unmount()
console.log('Experiment overview: status, ordering, actions, filters, search, counts and empty state passed')
