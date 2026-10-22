<template>
  <div class="file-preview">
    <p v-if="loading" class="file-state">正在读取报告文件…</p>
    <p v-else-if="error" class="file-state" role="alert">{{ error }} <button @click="load">重试</button></p>
    <iframe v-else-if="url && fileType === 'application/pdf'" :src="url" title="学生上传的 PDF 实验报告" />
    <div v-else class="file-state"><strong>{{ fileName }}</strong><p>Word 报告请下载原文件查看。</p><button @click="download" :disabled="!url">下载原文件</button></div>
  </div>
</template>
<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { teacherApi } from '../../api'
const props = defineProps({ sessionId: [Number, String], fileName: String, fileType: String })
const url = ref(''), loading = ref(false), error = ref('')
let generation = 0
function cleanup() { if (url.value) URL.revokeObjectURL(url.value); url.value = '' }
async function load() {
  const run = ++generation; cleanup(); loading.value = true; error.value = ''
  try { const { data } = await teacherApi.reportFile(props.sessionId); if (run === generation) url.value = URL.createObjectURL(new Blob([data], { type: props.fileType })) }
  catch { if (run === generation) error.value = '报告文件读取失败' }
  finally { if (run === generation) loading.value = false }
}
function download() { const link = document.createElement('a'); link.href = url.value; link.download = props.fileName; link.click() }
watch(() => [props.sessionId, props.fileName], load, { immediate: true })
onUnmounted(() => { generation++; cleanup() })
</script>
<style scoped>
.file-preview{height:100%;min-height:400px;display:flex;flex-direction:column}iframe{border:0;width:100%;flex:1;min-height:600px}.file-state{text-align:center;padding:60px 20px;color:#94a3b8;font-size:13px}.file-state strong{color:#475569;word-break:break-all}.file-state p{margin:14px 0}.file-state button{color:#6366f1;border:1px solid #e2e8f0;border-radius:5px;padding:7px 12px}
</style>
