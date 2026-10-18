<template>
  <div class="page flex-1 min-h-0 overflow-y-auto custom-scroll">
    <div class="inner">
      <header class="page-head">
        <div class="min-w-0">
          <h1 class="page-title">实验资料</h1>
          <p class="page-lead">
            预习报告、原始数据、装置照片、图表与实验报告都存放在这里，按实验归档，随时可以取用。
          </p>
        </div>
        <button type="button" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold shrink-0" @click="fileInput?.click()">
          上传资料
        </button>
        <input ref="fileInput" type="file" class="hidden" @change="onFileChange" />
      </header>

      <div class="filter-bar">
        <select v-model="expFilter" class="filter-select">
          <option value="">全部实验</option>
          <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
        </select>
        <div class="chip-row">
          <button
            type="button"
            class="chip"
            :class="{ 'chip--active': !categoryFilter }"
            @click="categoryFilter = ''"
          >
            全部（{{ files.length }}）
          </button>
          <button
            v-for="cat in FILE_CATEGORIES"
            :key="cat.key"
            type="button"
            class="chip"
            :class="{ 'chip--active': categoryFilter === cat.key }"
            @click="categoryFilter = cat.key"
          >
            {{ cat.label }}（{{ countOf(cat.key) }}）
          </button>
        </div>
      </div>

      <p v-if="message" class="msg" :class="{ 'msg--error': isError }">{{ message }}</p>

      <div v-if="loading" class="state-box">正在加载资料…</div>
      <div v-else-if="!filtered.length" class="state-box">
        {{ files.length ? '当前筛选条件下没有资料。' : '还没有资料。在实验闭环中写文书或做实验时可「存入资料库」，或直接上传。' }}
      </div>

      <section v-for="group in groups" v-else :key="group.code" class="group-block">
        <h2 class="group-title">
          {{ group.name }}
          <span class="group-count">{{ group.items.length }} 份</span>
        </h2>
        <ul class="file-grid">
          <li v-for="file in group.items" :key="file.id" class="file-card">
            <a :href="file.url" target="_blank" rel="noopener" class="file-thumb">
              <img v-if="file.previewable" :src="file.url" :alt="file.fileName" />
              <span v-else class="file-ext">{{ (file.contentType || 'file').toUpperCase() }}</span>
            </a>
            <div class="file-body">
              <p class="file-name" :title="file.fileName">{{ file.fileName }}</p>
              <p class="file-meta">
                <span class="file-tag">{{ file.categoryLabel }}</span>
                {{ formatBytes(file.sizeBytes) }} · {{ formatSessionTime(file.createdAt) }}
              </p>
              <p v-if="file.note" class="file-note">{{ file.note }}</p>
            </div>
            <div class="file-actions">
              <a :href="file.url" :download="file.fileName" class="act-btn">下载</a>
              <button type="button" class="act-btn act-btn--danger" @click="remove(file)">删除</button>
            </div>
          </li>
        </ul>
      </section>
    </div>

    <div v-if="pendingFile" class="modal-mask" @click.self="cancelUpload">
      <div class="modal">
        <h3 class="modal-title">上传资料</h3>
        <p class="modal-file">{{ pendingFile.name }}（{{ formatBytes(pendingFile.size) }}）</p>
        <label class="modal-label">归属实验</label>
        <select v-model="uploadExp" class="modal-input">
          <option value="">未归类</option>
          <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
        </select>
        <label class="modal-label">资料类型</label>
        <select v-model="uploadCategory" class="modal-input">
          <option v-for="cat in FILE_CATEGORIES" :key="cat.key" :value="cat.key">{{ cat.label }}</option>
        </select>
        <label class="modal-label">备注（可选）</label>
        <input v-model="uploadNote" class="modal-input" placeholder="如：第 3 组重复测量数据" />
        <div class="modal-actions">
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" @click="cancelUpload">取消</button>
          <button type="button" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold" :disabled="uploading" @click="confirmUpload">
            {{ uploading ? '上传中…' : '确认上传' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { experimentApi, studentFileApi } from '../api'
import { FILE_CATEGORIES, formatBytes } from '../utils/experimentFlow'
import { formatSessionTime } from '../utils/studentFlow'

const route = useRoute()

const files = ref([])
const experiments = ref([])
const loading = ref(true)
const expFilter = ref(String(route.query.exp || ''))
const categoryFilter = ref('')
const message = ref('')
const isError = ref(false)

const fileInput = ref(null)
const pendingFile = ref(null)
const uploadExp = ref('')
const uploadCategory = ref('other')
const uploadNote = ref('')
const uploading = ref(false)

const filtered = computed(() =>
  files.value.filter(
    (f) =>
      (!expFilter.value || f.experimentCode === expFilter.value) &&
      (!categoryFilter.value || f.category === categoryFilter.value)
  )
)

const groups = computed(() => {
  const map = new Map()
  filtered.value.forEach((file) => {
    const key = file.experimentCode || ''
    if (!map.has(key)) map.set(key, { code: key, name: file.experimentName || '未归类', items: [] })
    map.get(key).items.push(file)
  })
  return [...map.values()]
})

watch(() => route.query.exp, (value) => {
  expFilter.value = String(value || '')
})

onMounted(async () => {
  const [expRes] = await Promise.allSettled([experimentApi.list()])
  if (expRes.status === 'fulfilled') experiments.value = expRes.value.data || []
  await reload()
})

async function reload() {
  loading.value = true
  try {
    const { data } = await studentFileApi.list()
    files.value = data || []
  } catch (e) {
    notify(e.response?.data?.message || '资料加载失败，请确认后端已启动', true)
  } finally {
    loading.value = false
  }
}

function countOf(category) {
  return files.value.filter(
    (f) => f.category === category && (!expFilter.value || f.experimentCode === expFilter.value)
  ).length
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  pendingFile.value = file
  uploadExp.value = expFilter.value
  uploadCategory.value = guessCategory(file)
  uploadNote.value = ''
}

function guessCategory(file) {
  if (file.type.startsWith('image/')) return 'photo'
  if (/\.(csv|xlsx?|txt)$/i.test(file.name)) return 'raw_data'
  if (/\.(docx?|pdf)$/i.test(file.name)) return 'report'
  return 'other'
}

function cancelUpload() {
  pendingFile.value = null
}

async function confirmUpload() {
  if (!pendingFile.value) return
  uploading.value = true
  try {
    const stage = FILE_CATEGORIES.find((c) => c.key === uploadCategory.value)?.stage || 'lab'
    await studentFileApi.upload(pendingFile.value, {
      experimentCode: uploadExp.value,
      category: uploadCategory.value,
      stage,
      note: uploadNote.value
    })
    pendingFile.value = null
    notify('上传成功')
    await reload()
  } catch (e) {
    notify(e.message || '上传失败', true)
  } finally {
    uploading.value = false
  }
}

async function remove(file) {
  if (!window.confirm(`确定删除「${file.fileName}」吗？删除后无法恢复。`)) return
  try {
    await studentFileApi.remove(file.id)
    files.value = files.value.filter((f) => f.id !== file.id)
    notify('已删除')
  } catch (e) {
    notify(e.response?.data?.message || '删除失败', true)
  }
}

function notify(text, error = false) {
  message.value = text
  isError.value = error
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 4000)
}
</script>

<style scoped>
.page { @apply bg-surface-soft/40; }
.inner { @apply max-w-[1080px] mx-auto w-full px-6 py-7 pb-12; }

.page-head { @apply flex flex-wrap items-end justify-between gap-4 mb-5; }
.page-title { @apply text-[22px] font-bold text-ink-strong; }
.page-lead { @apply text-[14px] text-ink-muted mt-1.5 max-w-2xl; }

.filter-bar { @apply flex flex-wrap items-center gap-3 mb-5; }
.filter-select { @apply rounded-xl border border-line-soft bg-white px-3 py-2 text-[13.5px] min-w-[160px]; }
.chip-row { @apply flex flex-wrap gap-2; }
.chip {
  @apply px-3 py-1.5 rounded-lg text-[12.5px] border border-line-soft bg-white
    hover:border-brand-200 transition-colors;
}
.chip--active { @apply border-brand-400 bg-brand-50 text-brand-700 font-semibold; }

.msg { @apply text-[13px] text-emerald-600 mb-3; }
.msg--error { @apply text-rose-600; }
.state-box { @apply rounded-2xl border border-line-soft bg-white px-5 py-10 text-center text-[14px] text-ink-muted; }

.group-block { @apply mb-7; }
.group-title { @apply flex items-baseline gap-2 text-[15px] font-bold text-ink-strong mb-3; }
.group-count { @apply text-[12px] font-normal text-ink-faint; }

.file-grid { @apply grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4; }
.file-card { @apply flex flex-col rounded-2xl border border-line-soft bg-white overflow-hidden; }
.file-thumb {
  @apply flex items-center justify-center h-32 bg-surface-soft/70 border-b border-line-soft overflow-hidden;
}
.file-thumb img { @apply w-full h-full object-cover; }
.file-ext { @apply text-[13px] font-bold text-ink-faint tracking-wide; }
.file-body { @apply p-3.5 flex-1 min-w-0; }
.file-name { @apply text-[13.5px] font-semibold text-ink-strong truncate; }
.file-meta { @apply text-[11.5px] text-ink-faint mt-1.5 flex items-center gap-1.5 flex-wrap; }
.file-tag { @apply px-1.5 py-0.5 rounded bg-brand-50 text-brand-600 border border-brand-100 font-medium; }
.file-note { @apply text-[12px] text-ink-muted mt-1.5 line-clamp-2; }
.file-actions { @apply flex border-t border-line-soft; }
.act-btn {
  @apply flex-1 text-center py-2 text-[12.5px] text-ink-muted hover:bg-surface-soft transition-colors
    border-r border-line-soft last:border-r-0;
}
.act-btn--danger { @apply text-rose-500 hover:bg-rose-50; }

.modal-mask { @apply fixed inset-0 z-50 bg-black/40 flex items-center justify-center px-4; }
.modal { @apply w-full max-w-sm rounded-2xl bg-white p-5 shadow-xl; }
.modal-title { @apply text-[16px] font-bold text-ink-strong; }
.modal-file { @apply text-[12.5px] text-ink-muted mt-1 mb-4 truncate; }
.modal-label { @apply block text-[12.5px] font-semibold text-ink-muted mt-3 mb-1.5; }
.modal-input { @apply w-full rounded-xl border border-line-soft px-3 py-2 text-[13.5px]; }
.modal-actions { @apply flex justify-end gap-2 mt-5; }
</style>
