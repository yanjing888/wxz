import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
const renderer = Vue.createRenderer({ createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }), insert: (child, parent) => parent.children.push(child), remove() {}, setElementText() {}, setText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const empty = { render: () => null }
function mount(path, deps, props) {
  const { descriptor } = parse(readFileSync(new URL(path, import.meta.url), 'utf8'))
  let code = compileScript(descriptor, { id: 'workstation-check' }).content
  code = code.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, source) => `const ${names} = __deps[${JSON.stringify(source)}]`).replace('export default', 'return')
  const component = new Function('__deps', code)(new Proxy({ vue: Vue, ...deps }, { get: (obj, key) => obj[key] || empty }))
  component.render = () => null
  const app = renderer.createApp(component, props); app.mount({ children: [] })
  return { app, state: app._instance.setupState, props: app._instance.props }
}
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await Vue.nextTick() } }
globalThis.localStorage = { getItem: () => null, setItem() {} }
globalThis.document = { hidden: false, addEventListener() {}, removeEventListener() {} }
globalThis.window = { confirm: () => true }
let resolveOld
const old = new Promise(resolve => { resolveOld = resolve })
let first = true
const data = { rooms: [{ id: 1, name: '空实验室' }, { id: 2, name: '物理楼302' }], stations: [
  { id: 10, roomId: 2, name: '一号', code: '01', enabled: true, streamConfigured: true, cameraType: 'NETWORK', streamRevision: 'v1', canView: true },
  { id: 20, roomId: 2, name: '二号', code: '02', enabled: true, streamConfigured: false, cameraType: 'NETWORK', streamRevision: 'v2', canView: true }
] }
const api = { overview: async () => { if (first) { first = false; return old }; return { data } } }
const monitor = mount('../src/components/teacher/TeacherWorkstationMonitor.vue', { '../../api/workstations': { workstationApi: api, workstationError: String } }, { active: true })
await monitor.state.load(); await settle()
resolveOld({ data: { rooms: [{ id: 99, name: '过期结果' }], stations: [] } }); await settle()
assert.equal(monitor.state.roomId, 2, 'open the room containing real registered workstations')
assert.equal(monitor.state.stations.length, 2, 'all registered workstations appear without students or a lesson')
assert.equal(monitor.state.roomOptions.length, 1, 'teachers see rooms containing workstations')
monitor.state.openLive(data.stations[0]); await settle()
assert.equal(monitor.state.dialog, 'live'); assert.equal(monitor.state.liveStation.id, 10)
monitor.props.active = false; await settle(); assert.equal(monitor.state.dialog, '')
monitor.app.unmount()

let bindingSaved, clearedRoom
const bindingData = { ...data, stations: data.stations.map((s, i) => ({ ...s, bindingCount: i ? 0 : 1, canBind: true, members: i ? [] : [{ id: 101, name: '张同学', className: '一班' }] })) }
const bindingMonitor = mount('../src/components/teacher/TeacherWorkstationMonitor.vue', {
  '../../api/workstations': { workstationError: String, workstationApi: {
    overview: async () => ({ data: bindingData }),
    bindingStudents: async () => ({ data: { classes: ['一班'], students: [{ id: 101, name: '张同学', username: '001', classes: ['一班'], stationId: 10 }, { id: 102, name: '李同学', username: '002', classes: ['一班'], stationId: 20 }, { id: 103, name: '王同学', username: '003', classes: ['二班'] }] } }),
    bindStudents: async (id, className, ids) => { bindingSaved = { id, className, ids } },
    clearBindings: async room => { clearedRoom = room }
  } }
}, { active: true })
await settle(); await bindingMonitor.state.openBinding(bindingData.stations[0]); await settle()
assert.equal(bindingMonitor.state.dialog, 'binding')
assert.deepEqual([...bindingMonitor.state.bindingIds], [101])
assert.equal(bindingMonitor.state.bindingCandidates.length, 2, 'only chosen class students are listed')
bindingMonitor.state.bindingSearch = '002'; await settle()
assert.equal(bindingMonitor.state.bindingCandidates[0].id, 102, 'search by student number')
bindingMonitor.state.bindingIds = [101, 102]; await bindingMonitor.state.saveBinding(); await settle()
assert.deepEqual(bindingSaved, { id: 10, className: '一班', ids: [101, 102] })
assert.equal(bindingMonitor.state.dialog, '')
await bindingMonitor.state.clearRoomBindings(); assert.equal(clearedRoom, 2)
bindingMonitor.app.unmount()

let saved
const management = mount('../src/components/teacher/TeacherWorkstationMonitor.vue', {
  '../../api/workstations': { workstationError: String, workstationApi: {
    overview: async () => ({ data }),
    saveSimple: async (roomId, body, id) => { saved = { roomId, body, id } }
  } }
}, { active: true, management: true })
await settle(); management.state.form = { code: ' 03 ', name: '', address: ' 192.168.10.20 ', enabled: true }
await management.state.save(); await settle()
assert.deepEqual(saved, { roomId: 2, body: { code: '03', name: '03 号工位', address: '192.168.10.20', enabled: true }, id: undefined })
management.app.unmount()

let starts = 0, stops = 0, playbackUrl
const preview = mount('../src/components/monitor/WorkstationPreview.vue', {
  '../../api/runtime': { mediaUrl: path => path },
  '../../composables/useFlvLivePlayer': { useFlvLivePlayer: () => ({ ready: Vue.ref(false), error: Vue.ref(''), start: async (video, url) => { starts++; playbackUrl = url; return false }, stop: () => { stops++ } }) }
}, { station: data.stations[0], live: true })
await settle(); assert.equal(starts, 1); assert.equal(playbackUrl, '/api/workstations/10/stream')
preview.props.station = { ...preview.props.station, frameFresh: false }; await settle()
assert.equal(starts, 1, 'metadata refresh does not interrupt the video')
preview.props.station = { ...preview.props.station, streamRevision: 'new-address' }; await settle(); assert.equal(starts, 2)
preview.props.station = { ...preview.props.station, streamConfigured: false }; await settle(); assert.equal(starts, 2, 'unconfigured cameras make no playback request')
preview.props.station = { ...preview.props.station, streamConfigured: true }; await settle(); assert.equal(starts, 3)
preview.props.live = false; await settle(); assert.equal(starts, 3); assert(stops >= 4)
preview.app.unmount()
console.log('Passed: lesson-free workstation grid, stale response protection, minimal IP configuration, authenticated playback, stable refresh and hidden-page cleanup.')
