<template>
  <span class="record-actions" :class="{ 'record-actions--compact': compact, 'record-actions--toolbar': toolbar }">
    <button v-if="!compact" :class="{ 'btn-ghost': toolbar }" type="button" :disabled="disabled || !sessionId || busy" :title="disabled || !sessionId ? '结束实验后可下载实验过程记录' : '下载可编辑的 Word 过程记录'" @click="load(false)">{{ busy ? '正在读取…' : '下载实验过程记录' }}</button>
    <button v-if="teacher" type="button" :disabled="disabled || !sessionId || busy" :title="disabled || !sessionId ? '实验尚未结束' : '预览并下载实验过程记录'" @click="load(true)">{{ busy ? '正在读取…' : compact ? '过程记录' : '预览过程记录' }}</button>
    <span v-if="error" role="alert" class="error">{{ error }}</span>
    <Teleport to="body"><dialog ref="dialog" @click="closeBackdrop">
      <header><strong>实验过程记录</strong><button @click="save">下载 DOCX</button><button @click="dialog.close()">关闭</button></header>
      <p v-if="previewError" role="alert">{{ previewError }}</p>
      <div ref="preview" class="preview" />
    </dialog></Teleport>
  </span>
</template>
<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { sessionApi, teacherApi } from '../../api'
const props = defineProps({ sessionId: [Number, String], teacher: Boolean, compact: Boolean, disabled: Boolean, toolbar: Boolean })
const busy = ref(false), error = ref(''), dialog = ref(null), preview = ref(null), previewError = ref('')
let url = '', alive = true
function save() { if (!url) return; const a = document.createElement('a'); a.href = url; a.download = `实验过程记录-${props.sessionId}.docx`; a.click() }
async function load(showPreview) {
  busy.value = true; error.value = ''; previewError.value = ''
  const id = props.sessionId
  try {
    const { data } = await (props.teacher ? teacherApi : sessionApi).processRecordDocx(id)
    if (!alive || id !== props.sessionId) return
    if (url) URL.revokeObjectURL(url)
    url = URL.createObjectURL(data)
    if (!showPreview) { save(); return }
    preview.value.replaceChildren(); dialog.value.showModal()
    try {
      const { renderAsync } = await import('docx-preview')
      const host = document.createElement('div')
      await renderAsync(await data.arrayBuffer(), host, host, { renderAltChunks: false, useBase64URL: true })
      if (alive && id === props.sessionId) preview.value.replaceChildren(host)
    } catch { previewError.value = '预览失败，请下载 DOCX 查看。' }
  } catch (e) {
    let message = e.response?.data?.message
    if (e.response?.data instanceof Blob) { try { message = JSON.parse(await e.response.data.text()).message } catch {} }
    if (alive) error.value = message || '过程记录下载失败，请重试。'
  } finally { if (alive) busy.value = false }
}
function closeBackdrop(e) { if (e.target === dialog.value) { const r = dialog.value.getBoundingClientRect(); if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom) dialog.value.close() } }
onBeforeUnmount(() => { alive = false; if (url) URL.revokeObjectURL(url); dialog.value?.close() })
</script>
<style scoped>
dialog{white-space:normal;text-align:left}dialog header button{border:1px solid #cbd5e1;border-radius:6px;padding:7px 12px;background:white;color:#475569;font-size:13px}dialog header button:hover{background:#f8fafc}

.record-actions.record-actions--compact>button{border:0;padding:0;background:transparent;color:#6366f1;font-size:12px;border-radius:0}.record-actions.record-actions--compact>button:hover:not(:disabled){background:transparent;text-decoration:underline}.preview :deep(.docx-wrapper){padding:24px;background:#eef2f6}.preview :deep(section.docx){margin:0 auto 24px;box-shadow:0 2px 10px #0f172a15}dialog p{padding:12px 20px;font-size:13px;color:#b91c1c}
.record-actions{display:inline-flex;align-items:center;flex-wrap:wrap;gap:8px}.record-actions button{border:1px solid #cbd5e1;border-radius:6px;padding:7px 10px;color:#334155;background:white;font-size:13px}.record-actions button:hover:not(:disabled){border-color:#64748b;background:#f8fafc}.record-actions button:disabled{opacity:.5;cursor:not-allowed}.error{color:#b91c1c;font-size:12px}dialog{margin:auto;width:min(1100px,96vw);height:90vh;border:1px solid #cbd5e1;border-radius:10px;padding:0;overflow:hidden}dialog[open]{display:flex;flex-direction:column}dialog::backdrop{background:#0f172a80}header{display:flex;align-items:center;gap:12px;padding:14px 20px}header strong{flex:1}.preview{overflow:auto;flex:1;min-height:0;background:#eef2f6}
</style>

<style scoped>
.record-actions.record-actions--toolbar>button {
  @apply flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold;
  height: 30px;
  background: var(--bg-surface);
  color: var(--text-muted);
  border-color: var(--border-soft);
  white-space: nowrap;
}
</style>
