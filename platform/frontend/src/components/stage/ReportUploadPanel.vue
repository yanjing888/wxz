<template>
  <input ref="input" type="file" accept=".pdf,.docx" hidden @change="choose" />
  <section v-if="file || progress?.reportFileName || error" class="report-upload" aria-label="电子版实验报告">
    <div class="upload-row"><strong>电子版报告</strong>
      <template v-if="progress?.reportFileName"><span class="filename">{{ progress.reportFileName }}</span><span class="submitted">已提交</span><button @click="download" :disabled="busy">下载报告</button></template>
      <span v-else class="file-hint">PDF / Word（.docx），最大 20MB</span>
    </div>
    <div v-if="file" class="upload-row selected-file"><span class="filename">{{ file.name }}</span><span>{{ (file.size / 1024 / 1024).toFixed(2) }} MB</span><button @click="file = null" :disabled="busy">移除</button><button class="submit" @click="submit" :disabled="busy">{{ busy ? '提交中…' : '提交文件报告' }}</button></div>
    <p v-if="progress?.reportFileName && progress?.reportSubmittedAt" class="file-hint">提交时间：{{ String(progress.reportSubmittedAt).replace('T', ' ').slice(0, 19) }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </section>
</template>
<script setup>
import { ref, watch } from 'vue'
import { studentExperimentApi } from '../../api'
const props = defineProps({ experimentCode: String, sessionId: [Number, String], progress: Object })
const emit = defineEmits(['submitted', 'busy'])
const input = ref(null), file = ref(null), busy = ref(false), error = ref('')
function chooseFile() {
  if (!busy.value && !props.progress?.gradingCompleted) input.value?.click()
}
defineExpose({ chooseFile })
function choose(event) {
  const selected = event.target.files?.[0]; event.target.value = ''; error.value = ''
  if (!selected) return
  if (!/\.(pdf|docx)$/i.test(selected.name)) { error.value = '请选择 PDF 或 Word（.docx）文件'; return }
  if (selected.size === 0 || selected.size > 20 * 1024 * 1024) { error.value = '文件不能为空，且不能超过 20MB'; return }
  file.value = selected
}
async function submit() {
  if (busy.value || !file.value) return
  busy.value = true; emit('busy', true); error.value = ''
  try { const { data } = await studentExperimentApi.uploadReport(props.experimentCode, props.sessionId, file.value); file.value = null; emit('submitted', data) }
  catch (e) { error.value = e.response?.data?.message || '报告提交失败，请重试' }
  finally { busy.value = false; emit('busy', false) }
}
async function download() {
  busy.value = true; error.value = ''
  try { const { data } = await studentExperimentApi.reportFile(props.experimentCode, props.sessionId); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = props.progress.reportFileName; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
  catch { error.value = '报告下载失败，请重试' }
  finally { busy.value = false }
}
watch(() => props.sessionId, () => { file.value = null; error.value = '' })
</script>
<style scoped>
.report-upload{padding:12px 20px;border-bottom:1px solid #e4e9f3;background:#fff;font-size:12px;color:#64748b}.upload-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.upload-row strong{font-weight:600;color:#334155}.upload-row button{padding:6px 12px;border:1px solid #e2e8f0;border-radius:5px;color:#6366f1;background:#fff}.upload-row button:disabled{opacity:.5}.upload-row .submit{background:#4f46e5;color:white;border-color:#4f46e5}.filename{max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#475569}.file-hint{font-size:11px;color:#94a3b8}.selected-file{margin-top:10px;padding:10px;background:#f8fafc}.submitted{color:#059669}.error{margin-top:8px;color:#dc2626}
</style>
