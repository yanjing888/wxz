import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as Vue from 'vue'
import { usePagination } from '../src/composables/usePagination.js'

const scope = Vue.effectScope()
scope.run(() => {
  const rows = Vue.ref(Array.from({ length: 35 }, (_, i) => ({ id: i + 1 })))
  const filter = Vue.ref('all')
  const paging = usePagination(rows, [filter])
  assert.equal(paging.items.value.length, 10)
  paging.page.value = 4
  assert.deepEqual(paging.items.value.map(r => r.id), [31, 32, 33, 34, 35])
  assert.equal(paging.total.value, 35)
  rows.value = rows.value.slice(0, 20)
  assert.equal(paging.page.value, 2, 'deleting the final page clamps to the last remaining page')
  filter.value = 'filtered'
  assert.equal(paging.page.value, 1)
  paging.page.value = 2; paging.pageSize.value = 20
  assert.equal(paging.page.value, 1)
  assert.equal(paging.items.value.length, 20)
  rows.value = []
  assert.equal(paging.page.value, 1); assert.deepEqual(paging.items.value, [])
})
scope.stop()

const renderer = Vue.createRenderer({ createElement: tag => ({ tag, children: [] }), createText: text => ({ text }), createComment: text => ({ text }), insert: (child, parent) => parent.children.push(child), remove() {}, setElementText() {}, setText() {}, parentNode: () => null, nextSibling: () => null, patchProp() {} })
const { descriptor } = parse(readFileSync(new URL('../src/components/teacher/StudentManagePanel.vue', import.meta.url), 'utf8'))
const code = compileScript(descriptor, { id: 'pagination-check' }).content.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, source) => `const ${names} = __deps[${JSON.stringify(source)}]`).replace('export default', 'return')
const component = new Function('__deps', code)(new Proxy({ vue: Vue, '../../composables/usePagination': { usePagination } }, { get: (o, k) => o[k] || {} }))
component.render = () => null
const rows = Array.from({ length: 23 }, (_, i) => ({ userId: i + 1, username: `${i + 1}`, displayName: `Student ${i + 1}`, studentClass: 'A', assignedExperimentCodes: [] }))
const app = renderer.createApp(component, { allStudents: rows, experimentCode: 'newton_rings' })
app.mount({ children: [] })
const state = app._instance.setupState
state.togglePageSelection(true)
assert.equal(state.selectedRows.length, 10)
state.studentPage = 2; state.togglePageSelection(true)
assert.equal(state.selectedRows.length, 20, 'selection is retained across pages')
state.studentPage = 1; state.togglePageSelection(false)
assert.equal(state.selectedRows.length, 10, 'unchecking a page preserves other pages')
assert.equal(state.selectedRows[0].userId, 11)
state.searchQuery = 'Student 23'
await Vue.nextTick()
assert.equal(state.studentPage, 1)
assert.equal(state.studentTotal, 1)
assert.equal(state.studentItems[0].userId, 23)
assert.equal(state.selectedRows.length, 0)
app.unmount()
const overview = readFileSync(new URL('../src/views/TeacherView.vue', import.meta.url), 'utf8')
assert.ok(overview.includes("'learn-card--active': selectedUserId === row.userId"))
assert.ok(!/learn-card--(?:done|warn|online)/.test(overview))
console.log('PASS: page boundaries, filter reset, page-size reset, empty data, cross-page student selection and selection-only card highlighting')
