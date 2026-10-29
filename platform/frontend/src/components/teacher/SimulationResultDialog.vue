<template>
  <dialog ref="dialog" class="simulation-result" aria-labelledby="prep-report-title" @close="emit('close')" @click="onBackdrop">
    <header>
      <div class="report-heading"><h2 id="prep-report-title">仿真预习报告</h2><p>{{ row.studentName }} · {{ row.username }} · {{ row.studentClass }}</p></div>
      <div class="header-actions">
        <button class="download" :disabled="!downloadUrl" @click="downloadReport">下载 DOCX</button>
        <button autofocus @click="dialog.close()" aria-label="关闭预习报告">关闭</button>
      </div>
    </header>
    <div class="report-content" :aria-busy="loading">
      <p v-if="loading" class="report-state" role="status">正在加载预习报告…</p>
      <p v-else-if="error" class="report-state" role="alert">{{ error }} <button @click="load">重试</button></p>
      <p v-else-if="incomplete" class="report-state">{{ incomplete }}</p>
      <div ref="preview" v-show="!loading && !error && !incomplete" class="docx-preview" aria-label="预习报告 DOCX 预览" />
    </div>
  </dialog>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { teacherApi } from '../../api'
const props = defineProps({ row: { type: Object, required: true }, experimentCode: { type: String, required: true } })
const emit = defineEmits(['close'])
const dialog = ref(null), preview = ref(null), loading = ref(true), error = ref(''), incomplete = ref(''), downloadUrl = ref('')
let sequence = 0
function clearDownload() {
  if (downloadUrl.value) URL.revokeObjectURL(downloadUrl.value)
  downloadUrl.value = ''
}
function reportData(r, row) {
  return {
    student: `${row.studentName} / ${row.username || ''}`, studentClass: row.studentClass,
    startedAt: r.startedAt, finishedAt: r.syncedAt,
    installed: r.installed, guidePassed: r.guidePassed, answers: r.answers,
    knowledgeScore: r.knowledgeScore, totalScore: r.totalScore, passed: r.passed,
    challenge: { result: { ...r.challenge, score: r.challengeScore } }, actions: [],
  }
}
async function load() {
  const current = ++sequence, row = { ...props.row }
  clearDownload(); preview.value?.replaceChildren()
  loading.value = true; error.value = ''; incomplete.value = ''
  try {
    const { data } = await teacherApi.simulationResult(row.userId, props.experimentCode)
    if (current !== sequence) return
    if (!data?.quizConfirmed || !data?.challenge) {
      incomplete.value = '学生尚未最终确认预习题或提交成像挑战，预习报告暂未生成。'
      return
    }
    const [{ createReportDocx }, { renderAsync }] = await Promise.all([
      import(/* @vite-ignore */ `${import.meta.env.BASE_URL}newton-rings-simulation/student-report-docx.js`),
      import('docx-preview'),
    ])
    if (current !== sequence) return
    const blob = await createReportDocx(reportData(data, row))
    if (current !== sequence) return
    downloadUrl.value = URL.createObjectURL(blob)
    // Preview and download share the exact same DOCX bytes. Render off-screen
    // first so a late response cannot overwrite a different student's report.
    const host = document.createElement('div')
    await renderAsync(await blob.arrayBuffer(), host, host, {
      className: 'prep-docx', inWrapper: true, breakPages: true,
      ignoreLastRenderedPageBreak: false, useBase64URL: true,
      renderAltChunks: false, renderHeaders: true, renderFooters: true,
    })
    if (current === sequence) preview.value?.replaceChildren(host)
  } catch (e) {
    if (current === sequence) error.value = downloadUrl.value
      ? '报告预览失败，可重试或下载 DOCX 查看。'
      : e.response?.data?.message || '预习报告加载失败，请重试。'
  } finally { if (current === sequence) loading.value = false }
}
function downloadReport() {
  if (!downloadUrl.value) return
  const link = document.createElement('a')
  const name = String(props.row.username || props.row.userId).replace(/[\\/:*?"<>|]/g, '_')
  link.href = downloadUrl.value; link.download = `仿真预习报告-${name}.docx`; link.click()
}
function onBackdrop(event) {
  if (event.target !== dialog.value) return
  const r = dialog.value.getBoundingClientRect()
  if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.value.close()
}
watch(() => [props.row.userId, props.row.studentClass, props.experimentCode], load)
onMounted(() => { dialog.value.showModal(); load() })
onBeforeUnmount(() => { sequence++; clearDownload(); dialog.value?.close() })
</script>
<style scoped>
.simulation-result{width:min(1120px,96vw);height:92vh;max-height:92vh;margin:auto;padding:0;border:1px solid #d8dee8;border-radius:12px;background:#edf0f5;color:#334155;overflow:hidden;box-shadow:0 24px 80px #0f172a40}
.simulation-result[open]{display:flex;flex-direction:column}.simulation-result::backdrop{background:#0f172a80}
header{flex-shrink:0;display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 22px;background:white;border-bottom:1px solid #e2e8f0}
h2{font-size:18px;font-weight:650;margin:0}header p{font-size:12px;color:#64748b;margin:5px 0 0}.report-heading{min-width:0}.report-heading p{overflow-wrap:anywhere}.header-actions{display:flex;gap:10px;flex-shrink:0}
button{font-size:13px;padding:7px 12px;border:1px solid #cbd5e1;border-radius:6px;background:white;color:#475569}button.download{background:#4f46e5;border-color:#4f46e5;color:white}button:disabled{opacity:.45;cursor:not-allowed}button:focus-visible{outline:2px solid #6366f1;outline-offset:3px}
.report-content{flex:1;min-height:0;overflow:auto}.report-state{padding:64px 20px;text-align:center;font-size:14px;color:#475569}.report-state button{margin-left:10px}
.docx-preview{min-height:100%;width:max-content;min-width:100%}.docx-preview :deep(.prep-docx-wrapper){padding:24px;background:#edf0f5}.docx-preview :deep(section.prep-docx){margin:0 auto 24px;box-shadow:0 2px 12px #0f172a18}
@media(max-width:600px){header{padding:12px;gap:10px}.header-actions{gap:6px}h2{font-size:16px}button{padding:6px 8px}.docx-preview :deep(.prep-docx-wrapper){padding:12px}}
</style>
