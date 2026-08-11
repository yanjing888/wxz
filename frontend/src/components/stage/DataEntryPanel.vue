<template>
  <StagePanel
    layout="single"
    title="原始数据"
    desc="把纸上或实验台上的数据整理成一张表，后面的计算、体检与报告都从这里取数。"
  >
    <template #actions>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="!sessionTable" @click="importFromSession">
        导入实验台数据
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="empty" @click="exportCsv">
        导出 CSV
      </button>
      <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[13px]" :disabled="empty || saving" @click="saveToLibrary">
        {{ saving ? '存档中…' : '存入资料库' }}
      </button>
    </template>

    <section class="ocr-card">
      <div class="ocr-left">
        <h3 class="card-title">手写数据表拍照录入</h3>
        <p class="card-desc">
          课上记在纸上的数据表，拍一张照片就能转成下方的电子表格，省去逐个敲键盘。
          识别结果只做誊抄，不会修改你写下的数值，录入后务必自己核对一遍。
        </p>
        <div class="flex flex-wrap gap-2 mt-3">
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold" @click="fileInput?.click()">
            拍照 / 选择图片
          </button>
          <button
            v-if="imageUrl"
            type="button"
            class="btn-ghost px-4 py-2 rounded-xl text-[13px]"
            :disabled="loading"
            @click="recognize"
          >
            {{ loading ? '识别中…' : '识别成表格' }}
          </button>
          <button v-if="imageUrl" type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" @click="clearImage">
            清除图片
          </button>
        </div>
        <p v-if="error" class="err-text">{{ error }}</p>
        <ul v-if="warnings.length" class="warn-list">
          <li v-for="(w, i) in warnings" :key="i">{{ w }}</li>
        </ul>
      </div>
      <div class="ocr-right" @click="fileInput?.click()">
        <img v-if="previewUrl" :src="previewUrl" alt="数据表照片" class="preview-img" />
        <template v-else>
          <span class="upload-icon">+</span>
          <span class="upload-text">点击上传数据表照片</span>
          <span class="upload-sub">表格拍全、正对镜头，避免阴影</span>
        </template>
      </div>
      <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="onFileChange" />
    </section>

    <section class="table-card">
      <div class="table-head">
        <h3 class="card-title">数据表</h3>
        <div class="flex flex-wrap gap-2">
          <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[12.5px]" @click="onAddRow">加一行</button>
          <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[12.5px]" @click="onAddColumn">加一列</button>
          <button type="button" class="btn-ghost px-3 py-1.5 rounded-lg text-[12.5px]" @click="reset">清空</button>
        </div>
      </div>

      <div class="table-wrap custom-scroll">
        <table class="grid-table">
          <thead>
            <tr>
              <th class="idx-col">#</th>
              <th v-for="(header, ci) in table.headers" :key="ci">
                <div class="th-inner">
                  <input v-model="table.headers[ci]" class="th-input" @input="persist" />
                  <button type="button" class="del-btn" title="删除该列" @click="onRemoveColumn(ci)">×</button>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in table.rows" :key="ri">
              <td class="idx-col">
                <span>{{ ri + 1 }}</span>
                <button type="button" class="del-btn" title="删除该行" @click="onRemoveRow(ri)">×</button>
              </td>
              <td v-for="(_, ci) in table.headers" :key="ci">
                <input v-model="table.rows[ri][ci]" class="cell-input" @input="persist" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="notice" class="ok-text">{{ notice }}</p>
      <p class="table-hint">
        表格改动会自动保存。整理好后可以直接到「计算与作图」按列取数，或到「数据体检」检查合理性。
      </p>
      <div class="flex flex-wrap gap-2 mt-3">
        <button type="button" class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold" :disabled="empty" @click="$emit('go', 'calc')">
          去计算与作图
        </button>
        <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px]" :disabled="empty" @click="$emit('go', 'doctor')">
          去数据体检
        </button>
      </div>
    </section>
  </StagePanel>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import StagePanel from './StagePanel.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { sessionApi, studentFileApi, uploadApi } from '../../api'
import { extractTable, parseStructuredData } from '../../utils/aiTool'
import { parseSessionRows } from '../../utils/sessionReport'
import {
  addColumn,
  addRow,
  emptyTable,
  isTableEmpty,
  normalizeTable,
  removeColumn,
  removeRow,
  tableFromSessionRows,
  tableToCsv
} from '../../utils/dataTable'

const props = defineProps({
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  sessionId: { type: [Number, String], default: null },
  modelValue: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'go', 'saved'])

const { loading, error, invoke } = useAgentTool('data-entry', () => props.experimentCode)

const table = ref(normalizeTable(props.modelValue) || emptyTable())
const sessionTable = ref(null)
const fileInput = ref(null)
const previewUrl = ref('')
const imageUrl = ref('')
const warnings = ref([])
const saving = ref(false)
const notice = ref('')

const empty = computed(() => isTableEmpty(table.value))

watch(() => props.modelValue, (value) => {
  if (value) table.value = normalizeTable(value)
})

watch(() => props.sessionId, loadSessionTable, { immediate: true })

async function loadSessionTable() {
  sessionTable.value = null
  if (!props.sessionId) return
  try {
    const { data } = await sessionApi.getData(props.sessionId)
    sessionTable.value = tableFromSessionRows(parseSessionRows(data?.byStep))
  } catch {
    sessionTable.value = null
  }
}

function importFromSession() {
  if (!sessionTable.value) return
  table.value = normalizeTable(sessionTable.value)
  persist()
  notice.value = '已导入实验台提交的数据'
}

function persist() {
  emit('update:modelValue', { headers: [...table.value.headers], rows: table.value.rows.map((r) => [...r]) })
}

function onAddRow() {
  addRow(table.value)
  persist()
}

function onAddColumn() {
  addColumn(table.value)
  persist()
}

function onRemoveRow(index) {
  removeRow(table.value, index)
  persist()
}

function onRemoveColumn(index) {
  removeColumn(table.value, index)
  persist()
}

function reset() {
  table.value = emptyTable()
  notice.value = ''
  persist()
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (file) uploadImage(file)
  event.target.value = ''
}

async function uploadImage(file) {
  error.value = ''
  notice.value = ''
  previewUrl.value = URL.createObjectURL(file)
  try {
    const { data } = await uploadApi.image(file)
    imageUrl.value = data?.url || ''
  } catch (e) {
    error.value = e.message || '图片上传失败'
    previewUrl.value = ''
    imageUrl.value = ''
  }
}

function clearImage() {
  previewUrl.value = ''
  imageUrl.value = ''
  warnings.value = []
}

async function recognize() {
  warnings.value = []
  notice.value = ''
  try {
    const data = await invoke('ocr', { experimentName: props.experimentName }, { imageUrl: imageUrl.value })
    const parsed = extractTable(parseStructuredData(data))
    if (!parsed) {
      error.value = 'AI 未返回可用的表格结构，请换一张更清晰的照片，或手动录入'
      return
    }
    table.value = normalizeTable(parsed)
    warnings.value = parsed.warnings
    persist()
    notice.value = '识别完成，请逐格核对后再用于计算'
  } catch {
    // error 已由 composable 记录
  }
}

function exportCsv() {
  const name = `${props.experimentName || '实验'}-原始数据.csv`
  const blob = new Blob([`\uFEFF${tableToCsv(table.value)}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

async function saveToLibrary() {
  saving.value = true
  notice.value = ''
  try {
    const name = `${props.experimentName || '实验'}-原始数据.csv`
    const file = new File([`\uFEFF${tableToCsv(table.value)}`], name, { type: 'text/csv' })
    await studentFileApi.upload(file, {
      experimentCode: props.experimentCode,
      category: 'raw_data',
      stage: 'data',
      sessionId: props.sessionId || '',
      note: '原始数据表'
    })
    notice.value = '已存入实验资料库'
    emit('saved')
  } catch (e) {
    error.value = e.message || '存档失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.card-title { @apply text-[14px] font-bold text-ink-strong; }
.card-desc { @apply text-[13px] text-ink-muted mt-1.5 leading-relaxed; }
.err-text { @apply text-[12.5px] text-rose-600 mt-2; }
.ok-text { @apply text-[12.5px] text-emerald-600 mt-2; }

.ocr-card { @apply grid grid-cols-1 md:grid-cols-[1fr_240px] gap-4 rounded-2xl border border-line-soft bg-white p-5; }
.ocr-left { @apply min-w-0; }
.ocr-right {
  @apply flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-line-soft
    bg-surface-soft/60 min-h-[150px] p-2 cursor-pointer hover:border-brand-300 transition-colors text-center;
}
.upload-icon { @apply w-9 h-9 rounded-full bg-white border border-line-soft flex items-center justify-center text-lg text-ink-faint; }
.upload-text { @apply text-[13px] text-ink-base font-medium mt-1; }
.upload-sub { @apply text-[11.5px] text-ink-faint px-2; }
.preview-img { @apply max-h-[200px] w-auto rounded-lg object-contain; }
.warn-list { @apply mt-3 space-y-1 text-[12.5px] text-amber-700 list-disc pl-5; }

.table-card { @apply rounded-2xl border border-line-soft bg-white p-5; }
.table-head { @apply flex flex-wrap items-center justify-between gap-3 mb-3; }
.table-wrap { @apply overflow-x-auto border border-line-soft rounded-xl; }
.grid-table { @apply w-full text-[13px] border-collapse; }
.grid-table th, .grid-table td { @apply border-b border-r border-line-soft/70 p-0 last:border-r-0; }
.grid-table thead th { @apply bg-surface-soft; }
.idx-col {
  @apply w-12 text-center text-[11.5px] text-ink-faint bg-surface-soft/60 relative align-middle px-1 py-1.5;
}
.th-inner { @apply flex items-center; }
.th-input { @apply w-full min-w-[90px] px-2.5 py-2 text-[13px] font-semibold text-ink-strong bg-transparent outline-none; }
.cell-input { @apply w-full min-w-[90px] px-2.5 py-2 text-[13px] font-mono bg-transparent outline-none focus:bg-brand-50/60; }
.del-btn { @apply ml-1 mr-1 text-[13px] leading-none text-ink-faint hover:text-rose-500 shrink-0; }

.table-hint { @apply text-[12px] text-ink-faint mt-3 leading-relaxed; }
</style>
