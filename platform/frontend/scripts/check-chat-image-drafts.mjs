import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createPinia, defineStore, setActivePinia } from 'pinia'
import { imageDataUrlToFile } from '../src/utils/imageFile.js'

globalThis.window = { dispatchEvent() {} }
globalThis.CustomEvent = class { constructor(type, options) { this.type = type; this.detail = options.detail } }
globalThis.localStorage = { getItem() { return null }, setItem() {}, removeItem() {} }
const image = new File([new Uint8Array([0xff, 0xd8, 0xff, 1, 2])], 'question.jpg', { type: 'image/jpeg' })
const preview = 'data:image/jpeg;base64,/9j/AQI='
let uploads, requests, archives, uploadImpl, captureImpl, statusImpl
const api = {
  uploadApi: { image: (...args) => { uploads.push(args); return uploadImpl(...args) } },
  sessionApi: {
    ccdCapture: (...args) => captureImpl(...args),
    get: async id => ({ data: { id, status: 'ACTIVE' } }),
    assistStream: async (id, request, handlers) => {
      requests.push({ id, request })
      handlers.onChunk?.('答复')
      await handlers.onDone?.({ feedback: '答复', aiMessageId: 2 })
    }
  }
}
const deps = {
  pinia: { defineStore }, '../api': api, '../api/runtime': { mediaUrl: url => url },
  '../utils/imageFile': {
    sniffImageMime: async file => file.type,
    readFileAsDataUrl: async file => 'data:' + file.type + ';base64,' + Buffer.from(await file.arrayBuffer()).toString('base64'),
    imageDataUrlToFile
  },
  '../utils/markedImage': {}, '../utils/composerData': {}, '../utils/sessionReport': {}
}
let code = readFileSync(new URL('../src/stores/lab.js', import.meta.url), 'utf8')
code = code.replace(/^import (.+?) from ['"](.+?)['"];?$/gm, (_, names, path) => `const ${names} = __deps[${JSON.stringify(path)}]`)
code = code.replace('export const useLabStore', 'const useLabStore')
const useStore = new Function('__deps', code + '\nreturn useLabStore')(deps)
function setup() {
  setActivePinia(createPinia())
  const store = useStore()
  uploads = []; requests = []; archives = []
  uploadImpl = async () => ({ data: { url: '/uploads/sent.jpg' } })
  captureImpl = async () => ({ data: { previewDataUrl: preview, fileName: 'capture.jpg' } })
  statusImpl = async () => {}
  store.session = { id: 1, status: 'ACTIVE' }
  store.experiment = { code: 'newton_rings', steps: { 1: { title: '调节' } } }
  store.refreshDifyStatusIfStale = () => statusImpl()
  store.archiveSessionFile = async entry => archives.push(entry)
  store.loadSessionHistory = async () => {}
  return store
}
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

let store = setup()
await store.uploadImage(image)
assert.equal(store.composerImageReady, true)
assert.equal(store.composerImagePreview, preview)
assert.equal(uploads.length, 0)
store.clearComposerImage()
assert.equal(store.composerImageFile, null)
assert.equal(store.composerImageReady, false)
assert.equal(uploads.length, 0)

store = setup()
await store.captureCcdImageIntoComposer()
assert.equal(uploads.length, 0)
assert.equal(store.composerImageReady, true)
assert.equal(store.composerImageFile.type, 'image/jpeg')
await store.sendMessage('看看这张图')
assert.equal(uploads.length, 1)
assert.equal(requests[0].request.imageUrl, '/uploads/sent.jpg')
assert.equal(archives.length, 1)
assert.equal(store.composerImageFile, null)

store = setup()
await store.uploadImage(image)
const gate = deferred()
statusImpl = () => gate.promise
const first = store.sendMessage('问题')
assert.equal(await store.sendMessage('重复点击'), false)
gate.resolve()
await first
assert.equal(uploads.length, 1)
assert.equal(requests.length, 1)

store = setup()
await store.uploadImage(image)
uploadImpl = async () => { throw new Error('网络中断') }
assert.equal(await store.sendMessage('问题'), false)
assert.equal(store.composerImageFile, image)
assert.equal(store.loadingAssist, false)
assert.equal(requests.length, 0)
uploadImpl = async () => ({ data: { url: '/uploads/retry.jpg' } })
await store.sendMessage('重试')
assert.equal(requests.length, 1)
assert.equal(requests[0].request.imageUrl, '/uploads/retry.jpg')

// A late acquisition response must not restore an explicitly cleared draft.
store = setup()
const capture = deferred()
captureImpl = () => capture.promise
const acquiring = store.captureCcdImageIntoComposer()
store.clearComposerImage()
capture.resolve({ data: { previewDataUrl: preview } })
assert.equal(await acquiring, false)
assert.equal(store.composerImageFile, null)
assert.equal(uploads.length, 0)
assert.equal(store.ccdCaptureBusy, false)

// Leaving/resetting while status checking must not trigger a background upload.
store = setup()
await store.uploadImage(image)
const status = deferred()
statusImpl = () => status.promise
const sending = store.sendMessage('问题')
store.stopAssist()
store.clearComposerImage()
status.resolve()
assert.equal(await sending, false)
assert.equal(uploads.length, 0)

store = setup()
await store.sendMessage('纯文字问题')
assert.equal(uploads.length, 0)
assert.equal(requests.length, 1)

// A capture can replace a file that is still being read. Its late result must
// neither overwrite the capture nor leave the send button stuck in preparation.
store = setup()
const slowRead = deferred()
const slowFile = new File([image], 'slow.jpg', { type: 'image/jpeg' })
slowFile.arrayBuffer = () => slowRead.promise
const selecting = store.uploadImage(slowFile)
await Promise.resolve()
await store.captureCcdImageIntoComposer()
assert.equal(store.uploadingImage, false)
assert.equal(store.composerImageReady, true)
slowRead.resolve(await image.arrayBuffer())
await selecting
assert.equal(store.composerImageFile.name, 'capture.jpg')
assert.equal(uploads.length, 0)
console.log('PASS: local/captured previews never upload; send saves once; clear, late capture, double send, upload failure/retry, leave and text-only flows')
