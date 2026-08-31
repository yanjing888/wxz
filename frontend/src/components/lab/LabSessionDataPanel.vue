<template>
  <div class="data-panel flex-1 min-h-0 flex flex-col overflow-hidden bg-white">
    <div class="panel-body flex-1 min-h-0 overflow-y-auto custom-scroll px-5 py-4">
      <div v-if="!sessionId" class="empty">请先开始实验会话。</div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <div v-else-if="!entries.length" class="empty-box">
        <p class="font-semibold text-ink-strong">还没有数据记录</p>
        <p class="text-[13px] text-ink-muted mt-2 leading-relaxed">
          在数据步骤左侧填写表单，确认后保存为实验数据。
        </p>
      </div>

      <div v-else class="tables-wrap">
        <section
          v-for="table in dataView.tables"
          :key="table.stepId"
          class="step-table-section"
        >
          <header class="step-table-head">
            <span class="step-badge">步骤 {{ table.stepId }}</span>
            <h4 class="step-title">{{ table.stepTitle }}</h4>
            <span class="step-count">{{ table.rows.length }} 行</span>
          </header>

          <div class="table-scroll">
            <table class="data-table">
              <thead>
                <tr>
                  <th class="col-idx">序号</th>
                  <th v-for="field in table.fields" :key="field.key" class="col-field">
                    {{ field.label }}
                  </th>
                  <th class="col-time">提交时间</th>
                  <th v-if="!readOnly" class="col-action">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in table.rows" :key="row.id || `${table.stepId}-${row.index}`">
                  <td class="col-idx">{{ row.index }}</td>
                  <td v-for="field in table.fields" :key="field.key" class="col-val">
                    {{ formatCell(row.values, field.key) }}
                  </td>
                  <td class="col-time">{{ formatTime(row.createdAt) }}</td>
                  <td v-if="!readOnly" class="col-action">
                    <button
                      type="button"
                      class="delete-btn"
                      :disabled="deletingId === row.id || !row.id"
                      @click="deleteRow(row)"
                    >
                      {{ deletingId === row.id ? '删除中…' : '删除' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { sessionApi } from '../../api'
import {
  buildSessionDataStepTables,
  formatSessionDataCell,
  parseSessionDataResponse
} from '../../utils/sessionReport'

const props = defineProps({
  sessionId: { type: Number, default: 0 },
  sessionDataRevision: { type: Number, default: 0 },
  readOnly: { type: Boolean, default: false }
})
const emit = defineEmits(['deleted'])

const loading = ref(false)
const deletingId = ref(0)
const sessionData = ref(null)

const entries = computed(() => parseSessionDataResponse(sessionData.value))

const dataView = computed(() =>
  buildSessionDataStepTables({
    experimentName: sessionData.value?.experimentName || '',
    entries: entries.value,
    stepSchemas: sessionData.value?.stepSchemas || null
  })
)

watch(
  () => [props.sessionId, props.sessionDataRevision],
  load,
  { immediate: true }
)

async function load() {
  if (!props.sessionId) {
    sessionData.value = null
    return
  }
  loading.value = true
  try {
    const { data } = await sessionApi.getData(props.sessionId)
    sessionData.value = data || null
  } catch {
    sessionData.value = null
  } finally {
    loading.value = false
  }
}

function formatCell(values, key) {
  return formatSessionDataCell(values, key)
}

function formatTime(raw) {
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function deleteRow(row) {
  if (!row?.id || deletingId.value) return
  const ok = window.confirm('确定删除这条实验数据记录吗？删除后实验报告将不再引用它。')
  if (!ok) return
  deletingId.value = row.id
  try {
    await sessionApi.deleteData(props.sessionId, row.id)
    await load()
    emit('deleted')
  } catch (e) {
    window.alert(e.response?.data?.message || e.message || '删除失败')
  } finally {
    deletingId.value = 0
  }
}

defineExpose({ reload: load })
</script>

<style scoped>
.empty { @apply py-12 text-center text-[13px] text-ink-muted; }
.empty-box { @apply rounded-2xl border border-dashed border-line-soft bg-surface-soft/50 px-5 py-8 text-center; }

.tables-wrap { @apply space-y-4; }

.step-table-section {
  @apply rounded-xl border border-line-soft bg-white overflow-hidden;
}
.step-table-head {
  @apply flex flex-wrap items-center gap-2 px-4 py-2.5 border-b border-line-soft bg-surface-soft/30;
}
.step-badge {
  @apply text-[11px] font-bold px-2 py-0.5 rounded-md bg-brand-50 text-brand-700 shrink-0;
}
.step-title { @apply text-[13px] font-semibold text-ink-strong flex-1 min-w-0; }
.step-count { @apply text-[11px] text-ink-faint tabular-nums; }

.table-scroll { @apply overflow-x-auto; }
.data-table {
  @apply w-full text-[12.5px] border-collapse;
  min-width: 100%;
}
.data-table thead { @apply bg-surface-soft/60; }
.data-table th {
  @apply px-3 py-2 text-left font-semibold text-ink-muted whitespace-nowrap
    border-b border-line-soft;
}
.data-table td {
  @apply px-3 py-2 text-ink-strong border-b border-line-soft/60 whitespace-nowrap;
}
.data-table tbody tr:last-child td { @apply border-b-0; }
.data-table tbody tr:hover { @apply bg-brand-50/30; }
.col-idx {
  @apply w-12 text-center text-ink-muted font-semibold tabular-nums sticky left-0 bg-inherit;
}
.col-val { @apply tabular-nums font-medium; }
.col-time { @apply text-ink-muted text-[11.5px] min-w-[5.5rem]; }
.col-action { @apply text-right w-16; }
.delete-btn {
  @apply px-2 py-1 rounded-md text-[11px] font-semibold text-rose-600 hover:bg-rose-50
    disabled:opacity-50 disabled:cursor-not-allowed transition-colors;
}
</style>
