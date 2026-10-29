import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFile } from 'node:fs/promises'
import { simulationSubmission } from '../public/newton-rings-simulation/student-sync.js'
const state = { quizDone: false }
assert.equal(simulationSubmission(state), null)
Object.assign(state, { quizDone: true, quizConfirmed: true, installed: ['sample'], answers: [{ id: 'orientation', selected: [1] }], challenge: { result: { optics: { focus: 50 }, edits: 2, durationSeconds: 10, snapshot: 'large-image' } } })
state.quizConfirmed = false
assert.equal(simulationSubmission(state), null, "unconfirmed quiz must stay local")
state.quizConfirmed = true
const payload = simulationSubmission(state, 123)
assert.equal(payload.revision, 123)
assert.equal(payload.challenge.snapshot, undefined)
assert.notEqual(payload.answers[0].selected, state.answers[0].selected)

const source = (await readFile(new URL('../src/views/experiment/SimulationPrepView.vue', import.meta.url), 'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const retryCallbacks = []
const calls = [], sourceWindow = {}, window = { location: { origin: 'http://local' }, dispatchEvent() {} }
const context = vm.createContext({
  setTimeout: (callback, delay) => { retryCallbacks.push({ callback, delay }); return 1 }, clearTimeout() {},
  ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), onMounted() {}, onUnmounted() {}, watch() {},
  useRoute: () => ({ params: { code: 'newton_rings' } }), useRouter: () => ({ push() {} }), window,
  CustomEvent: class {}, studentExperimentApi: { saveSimulationResult(code, result) { return new Promise((resolve, reject) => calls.push({ code, result, resolve, reject })) } },
  sourceWindow, assert,
})
vm.runInContext(source + '\nframeRef.value = { contentWindow: sourceWindow };', context)
const send = (revision, source = 'sourceWindow', origin = 'http://local') => vm.runInContext(`onFrameMessage({ origin: '${origin}', source: ${source}, data: { type: 'wxz-simulation-result', experiment: 'newton_rings', result: { version: 3, revision: ${revision} } } })`, context)
const settle = async () => { await Promise.resolve(); await Promise.resolve() }
send(1, '{}'); send(1, 'sourceWindow', 'http://other'); assert.equal(calls.length, 0)
send(1); calls[0].reject(new Error('offline')); await settle()
assert.equal(vm.runInContext('syncError.value', context), true)
assert.equal(vm.runInContext('pendingResult.revision', context), 1)
assert.equal(vm.runInContext('loadError.value', context), '', 'sync errors do not unmount the simulation')
assert.equal(retryCallbacks.length, 1)
assert.equal(retryCallbacks[0].delay, 10000)
retryCallbacks[0].callback()
send(2) // The next submitted result must survive an in-flight retry.
calls[1].resolve({ data: { passed: false } }); await settle()
assert.equal(calls.length, 3)
assert.equal(calls[2].result.revision, 2)
calls[2].resolve({ data: { passed: true } }); await settle()
assert.equal(vm.runInContext('pendingResult', context), null)
assert.equal(vm.runInContext('simulationCompleted.value', context), true)
console.log('PASS: bounded submission, trusted iframe messages, offline retry, queued latest result, server-confirmed completion')

// Run the actual confirmation UI handler: rendering a finished quiz must not upload it.
const studentSource = await readFile(new URL('../public/newton-rings-simulation/student.js', import.meta.url), 'utf8')
const quizFunction = studentSource.slice(studentSource.indexOf('function renderQuiz()'), studentSource.indexOf('function startChallenge()'))
const elements = { guideContent: {}, confirmQuiz: {} }
let uploads = 0
const quizState = { quizDone: true, quizConfirmed: false }
const quizContext = vm.createContext({ state: quizState, $: id => elements[id], quizScore: () => 40, log() {}, render() {}, publishResult() { uploads++ } })
vm.runInContext(quizFunction + '\nrenderQuiz()', quizContext)
assert.equal(uploads, 0)
elements.confirmQuiz.onclick()
assert.equal(quizState.quizConfirmed, true)
assert.equal(uploads, 1)
const allowedFunction = studentSource.slice(studentSource.indexOf('function allowed('), studentSource.indexOf('function completed('))
const gates = vm.createContext({ state: { quizConfirmed: false, challenge: { result: {} } }, allInstalled: () => true })
vm.runInContext(allowedFunction, gates)
assert.equal(vm.runInContext('allowed(3) || allowed(4)', gates), false)
vm.runInContext('state.quizConfirmed = true', gates)
assert.equal(vm.runInContext('allowed(4)', gates), true)
vm.runInContext('state.challenge = null', gates)
assert.equal(vm.runInContext('allowed(4)', gates), false)
const { reportHtml } = await import('../public/newton-rings-simulation/student-report.js')
const html = reportHtml({ student: '<script>bad</script>', startedAt: 1, finishedAt: 2, installed: [], answers: [], knowledgeScore: 0, totalScore: 0, challenge: { result: { score: 0, rubric: [] } }, actions: [] })
assert.ok(html.startsWith('<!doctype html>'))
assert.ok(!html.includes('<script>bad</script>'))
console.log('PASS: explicit quiz confirmation, challenge/report gating, downloadable escaped HTML report')
