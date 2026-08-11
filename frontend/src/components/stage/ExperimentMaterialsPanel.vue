<template>
  <StagePanel
    layout="single"
    :title="compact ? '' : '本实验资料'"
    :desc="compact ? '' : '预习材料、原始数据、照片与报告会在这里沉淀，可随时上传或下载。'"
  >
    <div class="toolbar">
      <button type="button" class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold" @click="fileInput?.click()">
        上传资料
      </button>
      <router-link :to="{ name: 'files', query: { exp: experimentCode } }" class="btn-ghost px-4 py-2 rounded-xl text-[13px]">
        在资料库中查看
      </router-link>
      <input ref="fileInput" type="file" class="hidden" @change="onPick" />
    </div>

    <p v-if="message" class="msg" :class="{ 'msg--error': isError }">{{ message }}</p>
    <div v-if="loading" class="empty">正在加载…</div>
    <div v-else-if="!files.length" class="empty">
      {{ compact ? '还没有资料。课上发送的照片会自动归档到这里，也可手动上传。' : '还没有资料。写文书时可「存入资料库」，或在此直接上传。' }}
    </div>

    <ul v-else class="file-list">
      <li v-for="file in files" :key="file.id" class="file-row">
        <a :href="file.url" target="_blank" rel="noopener" class="file-link">
          <img v-if="file.previewable" :src="file.url" :alt="file.fileName" class="thumb" />
          <span v-else class="ext">{{ (file.contentType || 'file').toUpperCase() }}</span>
          <span class="min-w-0">
            <span class="name">{{ file.fileName }}</span>
            <span class="meta">{{ file.categoryLabel }} · {{ formatBytes(file.sizeBytes) }}</span>
          </span>
        </a>
        <button type="button" class="del" @click="remove(file)">删除</button>
      </li>
    </ul>
  </StagePanel>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { studentFileApi } from '../../api'
import { FILE_CATEGORIES, formatBytes } from '../../utils/experimentFlow'
import StagePanel from './StagePanel.vue'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})

const files = ref([])
const loading = ref(true)
const message = ref('')
const isError = ref(false)
const fileInput = ref(null)

onMounted(load)
watch(() => props.experimentCode, load)

async function load() {
  if (!props.experimentCode) {
    files.value = []
    loading.value = false
    return
  }
  loading.value = true
  try {
    const { data } = await studentFileApi.list(props.experimentCode)
    files.value = data || []
  } catch {
    files.value = []
  } finally {
    loading.value = false
  }
}

function onPick(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  upload(file)
}

async function upload(file) {
  message.value = ''
  isError.value = false
  try {
    await studentFileApi.upload(file, {
      experimentCode: props.experimentCode,
      category: guessCategory(file),
      stage: 'prepare'
    })
    message.value = '已上传'
    await load()
  } catch (err) {
    isError.value = true
    message.value = err.message || '上传失败'
  }
}

async function remove(file) {
  if (!confirm(`删除「${file.fileName}」？`)) return
  try {
    await studentFileApi.remove(file.id)
    await load()
  } catch (err) {
    isError.value = true
    message.value = err.message || '删除失败'
  }
}

function guessCategory(file) {
  const name = (file.name || '').toLowerCase()
  if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name)) return 'photo'
  if (/\.(csv|xls|xlsx|txt|json)$/i.test(name)) return 'raw_data'
  if (/\.(pdf|doc|docx)$/i.test(name)) return 'report'
  return 'other'
}
</script>

<style scoped>
.toolbar { @apply flex flex-wrap items-center gap-2 mb-4; }
.msg { @apply text-[13px] text-emerald-600 mb-3; }
.msg--error { @apply text-rose-600; }
.empty { @apply text-[13px] text-ink-muted py-8 text-center; }
.file-list { @apply space-y-2; }
.file-row { @apply flex items-center gap-2 rounded-xl border border-line-soft bg-white p-2; }
.file-link { @apply flex items-center gap-3 min-w-0 flex-1; }
.thumb { @apply w-12 h-12 rounded-lg object-cover shrink-0; }
.ext {
  @apply w-12 h-12 rounded-lg bg-surface-muted text-[10px] font-bold text-ink-muted
    flex items-center justify-center shrink-0;
}
.name { @apply block text-[13px] font-semibold text-ink-strong truncate; }
.meta { @apply block text-[11.5px] text-ink-muted mt-0.5; }
.del { @apply text-[12px] text-rose-600 px-2 py-1 rounded-lg hover:bg-rose-50 shrink-0; }
</style>
