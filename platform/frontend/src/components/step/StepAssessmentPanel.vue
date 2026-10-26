<template>
  <section class="assessment" aria-label="本步过程考核">
    <header><h3>步骤检查</h3><button type="button" :disabled="loading || busy" @click="refresh">刷新</button></header>
    <p v-if="error || loadingError" ref="errorBox" class="error" role="alert" tabindex="-1">{{ error || loadingError }}</p>
    <template v-if="current">
      <div class="step-status"><strong :class="current.status">{{ current.record && !current.submitted && editable ? '待补充材料' : processStatus[current.status] }}</strong><span>{{ summary.completedCount }}/{{ summary.totalCount }} 步 · 本步 {{ current.maxScore }} 分</span></div>
      <ul><li v-for="item in current.criteria" :key="item">{{ item }}</li></ul>
      <div v-if="current.record?.reviewedAt" class="feedback"><strong>{{ current.record.teacherName || '教师' }} · {{ current.record.score }}/{{ current.maxScore }} 分{{ !current.submitted && editable ? '（上次评价）' : '' }}</strong><p>{{ current.record.comment || '操作符合要求' }}</p></div>
      <section v-if="submittedImages.length" class="submitted-images"><div class="field-heading"><span>已提交图片</span><span>{{ submittedImages.length }} 张</span></div><ProcessEvidenceGallery :images="submittedImages" compact /></section>
      <template v-if="!editable && submittedTable.length"><div class="field-heading"><span>实验数据</span></div><ProcessEvidenceTable :model-value="submittedTable" /></template>
      <template v-if="editable">
        <div class="field-heading"><span>{{ current.status === 'PENDING' && current.submitted ? '补充图片' : '检查图片' }}</span><span>{{ pendingImages.length ? `已选择 ${pendingImages.length} 张` : (current.status === 'PENDING' && submittedImages.length ? '' : '必填') }}</span></div>
        <ProcessEvidenceGallery v-if="pendingImages.length" :images="pendingImages" compact removable :disabled="busy || preparing" @remove="removeImage" />
        <div class="upload-actions"><button type="button" :disabled="busy || preparing" @click="fileInput?.click()">{{ preparing ? '读取中…' : '上传图片' }}</button><button type="button" :disabled="busy || preparing" @click="cameraOpen = true">拍照</button></div>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif,image/bmp" multiple class="file-input" aria-label="选择检查图片" @change="onFilesSelected" />
        <div class="field-heading"><span>实验数据</span><span>选填</span></div>
        <ProcessEvidenceTable :key="`${sessionId}:${stepId}`" v-model="table" editable :disabled="busy" />
        <label :for="`step-note-${stepId}`">{{ current.status === 'REVISION' ? '整改说明' : current.status === 'PENDING' ? '补充说明' : '完成说明' }}</label>
        <textarea :id="`step-note-${stepId}`" v-model="note" maxlength="2000" :disabled="busy" placeholder="选填" />
        <button type="button" class="primary" :disabled="busy || loading || preparing || (current.status === 'PENDING' && current.submitted && !pendingImages.length && !note.trim() && tableDraft === null)" @click="complete">{{ busy ? '提交中…' : current.status === 'PENDING' && current.submitted ? '补充材料' : current.status === 'REVISION' ? '提交复查' : '完成本步并提交检查' }}</button>
      </template>
      <p v-if="current.blocking && current.status === 'PENDING' && current.submitted" class="status-note">关键检查点，教师确认后可进入下一步。</p>
      <div v-if="revisions.length" class="revision-reminder"><span>待整改：</span><button v-for="step in revisions" :key="step.stepId" type="button" @click="emit('select-step', step.stepId)">步骤 {{ step.stepId }}</button></div>
      <p v-if="!current.accessible" class="status-note">前序步骤未完成</p>
      <details v-if="history.length"><summary>检查记录（{{ history.length }}）</summary><div v-for="(event, index) in [...history].reverse()" :key="index" class="event"><div><span>{{ event.action === 'SUPPLEMENTED' ? '补充材料' : processStatus[event.status] }}</span><time>{{ event.at?.replace('T', ' ').slice(0, 19) }}</time></div><p v-if="event.note">{{ event.note }}<span v-if="event.action === 'REVIEWED'"> · {{ event.score }} 分</span></p><ProcessEvidenceGallery v-if="historyImages(event).length" :images="historyImages(event)" compact label="当次图片" /><ProcessEvidenceTable v-if="event.action !== 'REVIEWED' && parseProcessJson(event.evidence, {}).table?.length" :model-value="parseProcessJson(event.evidence, {}).table" /></div></details>
    </template>
    <p v-else class="status-note">{{ loading ? '加载中…' : '检查状态加载失败，请重试' }}</p>
    <TabletCameraCapture :visible="cameraOpen" @close="cameraOpen = false" @captured="onCameraCaptured" />
  </section>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { sessionApi } from '../../api'
import { parseProcessJson, processImages, processStatus } from '../../utils/processAssessment'
import { normalizeImageFile } from '../../utils/imageFile'
import ProcessEvidenceGallery from '../experiment/ProcessEvidenceGallery.vue'
import ProcessEvidenceTable from '../experiment/ProcessEvidenceTable.vue'
import { validProcessTable } from '../../utils/processAssessment'
import TabletCameraCapture from '../camera/TabletCameraCapture.vue'
const props = defineProps({ sessionId: Number, stepId: Number, readOnly: Boolean })
const emit = defineEmits(['state', 'select-step'])
const summary = ref(null), loading = ref(false), busy = ref(false), error = ref(''), note = ref('')
const loadingError = ref(''), errorBox = ref(null)
const pendingImages = ref([]), fileInput = ref(null), cameraOpen = ref(false), preparing = ref(false)
const tableDraft = ref(null)
const current = computed(() => summary.value?.steps?.find(s => s.stepId === props.stepId))
const history = computed(() => parseProcessJson(current.value?.record?.historyJson))
const submittedImages = computed(() => processImages(parseProcessJson(current.value?.record?.evidenceJson, {}).images))
const submittedTable = computed(() => parseProcessJson(current.value?.record?.evidenceJson, {}).table || [])
const table = computed({ get: () => tableDraft.value ?? submittedTable.value, set: value => { tableDraft.value = value } })
const editable = computed(() => !props.readOnly && current.value?.canSubmit)
const revisions = computed(() => summary.value?.steps?.filter(step => step.status === 'REVISION' && step.stepId !== props.stepId) || [])
const drafts = new Map()
let generation = 0, activeDraftKey = ''
function historyImages(event) { return event.action === 'REVIEWED' ? [] : processImages(parseProcessJson(event.evidence, {}).images) }
function removeImage(index) { if (busy.value) return; URL.revokeObjectURL(pendingImages.value[index].url); pendingImages.value.splice(index, 1) }
function clearDraft(key) {
  const draft = key === activeDraftKey ? { images: pendingImages.value } : drafts.get(key)
  draft?.images.forEach(image => URL.revokeObjectURL(image.url))
  drafts.delete(key)
  if (key === activeDraftKey) { pendingImages.value = []; note.value = ''; tableDraft.value = null }
}
function clearAllDrafts() { const urls = new Set([...pendingImages.value, ...[...drafts.values()].flatMap(d => d.images)].map(image => image.url)); urls.forEach(url => URL.revokeObjectURL(url)); drafts.clear(); pendingImages.value = []; note.value = ''; tableDraft.value = null }
async function addImages(files) {
  const key = activeDraftKey
  if (files.reduce((sum, file) => sum + file.size, 0) + pendingImages.value.reduce((sum, image) => sum + image.file.size, 0) > 18 * 1024 * 1024) { error.value = '本次图片总大小不能超过 18MB'; return }
  preparing.value = true; error.value = ''
  const normalized = []
  try {
    for (const file of files) {
      if (!file.size || file.size > 10 * 1024 * 1024) throw new Error('单张图片不能超过 10MB，且不能为空')
      const image = await normalizeImageFile(file)
      if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp'].includes(image.type)) throw new Error('请选择 JPG、PNG、WebP、GIF 或 BMP 图片')
      normalized.push(image)
    }
    if (key === activeDraftKey) pendingImages.value.push(...normalized.map(file => ({ file, url: URL.createObjectURL(file), name: file.name })))
  } catch (e) { if (key === activeDraftKey) error.value = e.message || '图片读取失败' }
  finally { if (key === activeDraftKey) preparing.value = false }
}
function onFilesSelected(event) { const files = [...(event.target.files || [])]; event.target.value = ''; if (files.length) addImages(files) }
function onCameraCaptured(file) { cameraOpen.value = false; addImages([file]) }
async function refresh() {
  const id = props.sessionId, token = generation
  if (!id || loading.value || busy.value) return
  loading.value = true
  try { const { data } = await sessionApi.process(id); if (token !== generation) return; summary.value = data; loadingError.value = ''; emit('state', data) }
  catch (e) { if (token === generation) loadingError.value = e.response?.data?.message || '检查状态加载失败' }
  finally { if (token === generation) loading.value = false }
}
async function complete() {
  if (busy.value || preparing.value || !editable.value) return
  if (!pendingImages.value.length && !(current.value?.status === 'PENDING' && submittedImages.value.length)) { error.value = '请完成必填项：检查图片（至少上传或拍摄一张）'; await nextTick(); errorBox.value?.focus(); return }
  if (table.value.length && !validProcessTable(table.value)) { error.value = '实验数据表格填写不完整，请补全或删除表格后提交'; await nextTick(); errorBox.value?.focus(); return }
  const id = props.sessionId, step = props.stepId, token = generation, key = activeDraftKey
  busy.value = true; error.value = ''
  try {
    const { data } = await sessionApi.completeStep(id, step, { note: note.value, table: table.value }, pendingImages.value.map(image => image.file))
    if (token !== generation) return
    summary.value = data; clearDraft(key); emit('state', data)
  } catch (e) { if (token === generation) error.value = e.response?.data?.message || (e.response?.status === 413 ? '图片总大小超出限制，请减少图片后重试' : '提交失败，请重试') }
  finally { if (token === generation) busy.value = false }
}
watch(() => [props.sessionId, props.stepId], ([id, step], old) => {
  generation++; loading.value = false; busy.value = false
  if (!old || id !== old[0]) { clearAllDrafts(); summary.value = null; emit('state', null) }
  else if (activeDraftKey) drafts.set(activeDraftKey, { note: note.value, images: pendingImages.value, table: tableDraft.value })
  activeDraftKey = `${id}:${step}`
  const draft = drafts.get(activeDraftKey)
  note.value = draft?.note || ''; pendingImages.value = draft?.images || []; tableDraft.value = draft?.table ?? null; preparing.value = false; cameraOpen.value = false; error.value = ''; loadingError.value = ''
  refresh()
}, { immediate: true })
const timer = setInterval(() => { if (!document.hidden && !props.readOnly) refresh() }, 6000)
onUnmounted(() => { generation++; clearInterval(timer); clearAllDrafts() })
</script>

<style scoped>
.revision-reminder{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin-top:10px;color:#b45309;font-size:12px}.revision-reminder button{text-decoration:underline;text-underline-offset:3px}
.assessment{margin:12px 20px;padding:14px;border:1px solid #e4e9f3;border-radius:8px;background:#fff;font-size:12px;line-height:1.7;color:#475569}.assessment header{display:flex;justify-content:space-between;gap:12px;align-items:center}.assessment h3{font-size:13px;font-weight:600;color:#334155}.assessment header button{font-size:11px;color:#6366f1}.assessment button:disabled{opacity:.45;cursor:default}.step-status{display:flex;justify-content:space-between;gap:8px;margin:10px 0;font-size:11px}.step-status strong{font-weight:500;color:#64748b}.step-status>span{color:#94a3b8}.step-status .PASSED,.step-status .COMPLETED{color:#059669}.step-status .REVISION{color:#dc2626}.step-status .PENDING{color:#b45309}.assessment ul{list-style:disc;padding-left:16px;margin:12px 0}.assessment li{margin:5px 0}.feedback{padding:10px 0;border-top:1px solid #eef0f5;border-bottom:1px solid #eef0f5;margin:12px 0}.feedback strong{font-size:12px;font-weight:500}.feedback p{white-space:pre-wrap;margin-top:4px}.field-heading{display:flex;align-items:center;justify-content:space-between;margin:12px 0 8px;font-size:12px;color:#64748b}.field-heading span:last-child{font-size:11px;color:#94a3b8}.upload-actions{display:flex;gap:8px;margin:10px 0}.upload-actions button{border:1px solid #c7d2fe;border-radius:6px;background:#eef2ff;padding:6px 12px;min-height:34px;color:#4338ca;font-size:12px;font-weight:600;transition:background .15s,border-color .15s}.upload-actions button:hover:not(:disabled){background:#e0e7ff;border-color:#a5b4fc}.upload-actions button:focus-visible{outline:2px solid #818cf8;outline-offset:2px}.file-input{display:none}.assessment label{display:block;margin:12px 0 6px;font-size:12px;color:#64748b}.assessment textarea{width:100%;min-height:60px;padding:8px;border:1px solid #e2e8f0;border-radius:6px;font-size:12px;resize:vertical}.assessment textarea:focus{outline:2px solid #c7d2fe}.primary{width:100%;padding:9px;border-radius:6px;background:#4f46e5;color:#fff;font-size:12px;font-weight:600;margin-top:10px}.status-note{color:#94a3b8;margin:10px 0;font-size:11px}.assessment details{margin-top:12px;border-top:1px solid #eef0f5;padding-top:10px}.assessment summary{cursor:pointer;color:#64748b}.event{padding:10px 0;border-bottom:1px solid #f1f5f9}.event>div:first-child{display:flex;justify-content:space-between;gap:8px;font-size:11px;color:#94a3b8}.event p{white-space:pre-wrap;margin:6px 0}.error{color:#b45309;background:#fffbeb;padding:8px;border-radius:4px;margin:8px 0;font-size:12px}
</style>
