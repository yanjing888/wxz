import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import { parseProcessJson, processImages, processStatus, validProcessTable } from '../src/utils/processAssessment.js'

const renderer = Vue.createRenderer({
  createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }),
  insert: (child, parent) => parent.children.push(child), remove() {}, setElementText() {}, setText() {},
  parentNode: () => null, nextSibling: () => null, patchProp() {}
})
const emptyComponent = { render: () => null }
function mount(path, dependencies, props) {
  const { descriptor } = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  const code = compileScript(descriptor, { id: 'process-check' }).content
    .replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, source) => `const ${names} = __deps[${JSON.stringify(source)}]`)
    .replace('export default', 'return')
  const deps = new Proxy({ vue: Vue, '../../utils/processAssessment': { parseProcessJson, processImages, processStatus, validProcessTable }, ...dependencies }, { get: (target, key) => target[key] || emptyComponent })
  const component = new Function('__deps', code)(deps)
  component.render = () => null
  const app = renderer.createApp(component, props)
  app.mount({ children: [] })
  return { app, state: app._instance.setupState, props: app._instance.props }
}
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await Vue.nextTick() } }
const clone = value => structuredClone(value)
const dataTable = [['项目', '读数 / mm'], ['位置 1', '12.35']]
const assessment = () => ({ completedCount: 0, totalCount: 2, score: null, steps: [
  { stepId: 1, title: '光路装调', required: true, canSubmit: true, canReview: true, submitted: true, maxScore: 20, criteria: ['摆放稳定', '光路正确'], accessible: true, status: 'PENDING', record: { version: 1, score: null, comment: '', evidenceJson: JSON.stringify({ images: ['/uploads/photo.png'], table: dataTable }), historyJson: '[]' } },
  { stepId: 2, title: '测量', required: true, maxScore: 30, criteria: ['读数规范'], accessible: false, status: 'NOT_STARTED', record: null }
] })
let queue = [{ username: '2026001', session: { id: 7, userId: 1, experimentCode: 'newton_rings', studentName: '张同学', studentClass: '物理一班', status: 'ACTIVE' }, assessment: assessment() }]
let reviewed
const processScopes = []
const teacher = mount('../src/components/teacher/TeacherProcessAssessment.vue', { '../../api': { teacherApi: {
  processQueue: async scope => { processScopes.push(scope); return { data: clone(queue) } },
  reviewStep: async (id, step, body) => { reviewed = { id, step, body }; const data = assessment(); data.steps[0].status = 'PASSED'; data.steps[0].record = { ...data.steps[0].record, score: body.score, comment: body.comment, version: 3, checkedCriteriaJson: JSON.stringify(body.checkedCriteria) }; return { data } }
} } }, { active: true, experimentCode: 'newton_rings', className: '物理一班' })
await settle()
assert.equal(teacher.state.selected.session.userId, 1, 'The first waiting student must open automatically')
assert.equal(teacher.state.current.stepId, 1)
assert.equal(teacher.state.images.length, 1)
queue[0].assessment.steps[0].record.version = 2
queue[0].assessment.steps[0].record.evidenceJson = JSON.stringify({ images: ['/uploads/photo.png', '/uploads/second.png'] })
await teacher.state.load()
assert.equal(teacher.state.images.length, 2, 'Supplemented images must appear automatically when there is no grading draft')
teacher.state.score = 18
teacher.state.comment = '操作符合要求'
teacher.state.checked = ['摆放稳定', '光路正确']
queue[0].assessment.steps[0].record.version = 3
await teacher.state.load()
assert.equal(teacher.state.current.record.version, 2, 'Polling must preserve the grading draft and its evidence version')
assert.equal(teacher.state.remoteChanged, true)
assert.equal(teacher.state.score, 18)
await teacher.state.review(true)
assert.equal(reviewed.body.version, 2, 'The submitted review must retain its original evidence version')
assert.deepEqual(reviewed.body.checkedCriteria, ['摆放稳定', '光路正确'])
teacher.state.classFilter = '不存在的班级'
await settle()
assert.equal(teacher.state.selected, null)
assert.deepEqual(processScopes[0], { experimentCode: 'newton_rings', className: '物理一班' })
const callsBeforeClear = processScopes.length
teacher.props.className = ''
await settle()
await teacher.state.load()
assert.equal(processScopes.length, callsBeforeClear, 'No teaching class must not load a process queue')
assert.equal(teacher.state.rows.length, 0, 'Clearing the class must clear old process records')
teacher.props.className = '物理二班'
await settle()
assert.equal(processScopes.at(-1).className, '物理二班', 'Changing the class must reload the selected class only')
teacher.app.unmount()

let state = assessment(); state.steps[0].status = 'NOT_STARTED'; state.steps[0].record = null
let request, rejectSubmission = true
const student = mount('../src/components/step/StepAssessmentPanel.vue', {
  '../../api': { sessionApi: {
    process: async () => ({ data: clone(state) }),
    completeStep: async (id, step, body, images) => {
      request = { id, step, body, images }
      if (rejectSubmission) throw new Error('network failed')
      state = assessment(); return { data: clone(state) }
    }
  } },
  '../../utils/imageFile': { normalizeImageFile: async file => file }
}, { sessionId: 7, stepId: 1, readOnly: false })
await settle()
await student.state.complete()
assert.equal(request, undefined, 'Submission requires an image')
assert.match(student.state.error, /必填项.*检查图片/)
await student.state.refresh()
assert.match(student.state.error, /必填项/, 'Status polling must not erase the required-field message')
const image = new File([new Uint8Array([137, 80, 78, 71])], '光路.png', { type: 'image/png' })
await student.state.addImages([image])
await student.state.complete()
assert.deepEqual(request.body.table, [], 'An image can be submitted without experimental data')
student.state.table = clone(dataTable)
assert.equal(student.state.pendingImages.length, 1)
assert.match(student.state.pendingImages[0].url, /^blob:/)
student.state.note = '完成光路调整'
student.props.stepId = 2
await settle()
assert.equal(student.state.pendingImages.length, 0)
student.props.stepId = 1
await settle()
assert.equal(student.state.pendingImages.length, 1, 'Switching steps must preserve unsent images')
assert.equal(student.state.note, '完成光路调整')
assert.deepEqual(student.state.table, dataTable, 'Switching steps must preserve the table draft')
await student.state.complete()
assert.equal(student.state.pendingImages.length, 1, 'A failed submission must retain images for retry')
rejectSubmission = false
await student.state.complete()
assert.equal(request.images[0], image, 'Images must be attached to completion, independently of chat')
assert.deepEqual(request.body.table, dataTable, 'The complete table must accompany image evidence')
assert.equal(student.state.pendingImages.length, 0)
assert.equal(student.state.submittedImages[0].url, '/uploads/photo.png')
assert.equal(student.state.editable, true, 'A waiting student can supplement materials')
await student.state.addImages(Array.from({ length: 7 }, () => image))
assert.equal(student.state.pendingImages.length, 7, 'Image selection must have no fixed count limit, including supplements')
await student.state.complete()
assert.equal(request.images.length, 7, 'All selected images must be submitted')
student.app.unmount()

let insertedTable
const editor = mount('../src/components/experiment/ProcessEvidenceTable.vue', {}, { editable: true, modelValue: [], 'onUpdate:modelValue': value => { insertedTable = value } })
editor.state.insert(4, 3)
assert.equal(insertedTable.length, 4)
assert.ok(insertedTable.every(row => row.length === 3))
assert.equal(validProcessTable(insertedTable), false, 'An empty inserted table is not evidence')
editor.app.unmount()
assert.equal(validProcessTable([['表头'], [' ']]), false)
assert.equal(validProcessTable([[' '], ['12']]), false)
assert.equal(validProcessTable(dataTable), true)

assert.deepEqual(processImages(['', ' ', '/uploads/photo.png', '/uploads/photo.png', null, 'javascript:alert(1)']).map(image => image.url), ['/uploads/photo.png'])
console.log('Passed: automatic student selection, live evidence updates, preserved grading drafts, independent image submission, retry and per-step draft retention, supplementation and blank-image filtering.')
