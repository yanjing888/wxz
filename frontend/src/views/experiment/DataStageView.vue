<template>
  <div class="stage flex-1 min-h-0 flex flex-col">
    <StageTabs v-model="activeTab" :tabs="tabs" :unavailable="unavailableTools" />

    <DataEntryPanel
      v-if="activeTab === 'entry'"
      v-model="table"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
      @go="activeTab = $event"
    />
    <DataCalcPanel
      v-else-if="activeTab === 'calc'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
      :table="table"
    />
    <DataDoctorPanel
      v-else-if="activeTab === 'doctor'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :table="table"
      @go="activeTab = $event"
    />
    <ErrorTracePanel
      v-else
      :experiment-code="code"
      :experiment-name="experimentName"
      :sessions="sessions"
      :session-id="sessionId"
    />
  </div>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import StageTabs from '../../components/stage/StageTabs.vue'
import DataEntryPanel from '../../components/stage/DataEntryPanel.vue'
import DataCalcPanel from '../../components/stage/DataCalcPanel.vue'
import DataDoctorPanel from '../../components/stage/DataDoctorPanel.vue'
import ErrorTracePanel from '../../components/stage/ErrorTracePanel.vue'
import { STAGE_TABS } from '../../utils/experimentFlow'
import { emptyTable, normalizeTable } from '../../utils/dataTable'

const ctx = inject('experimentContext')

const tabs = STAGE_TABS.data
const activeTab = ref('entry')

const code = computed(() => ctx.code.value)
const experimentName = computed(() => ctx.experimentName.value)
const sessions = computed(() => ctx.sessions.value)
const sessionId = computed(() => ctx.primarySessionId.value)

const table = ref(emptyTable())

watch(code, restoreTable, { immediate: true })
watch(table, persistTable, { deep: true })

function storageKey() {
  return code.value ? `wxz_data_table_${code.value}` : ''
}

function restoreTable() {
  const key = storageKey()
  table.value = emptyTable()
  if (!key) return
  try {
    const saved = JSON.parse(localStorage.getItem(key) || 'null')
    if (saved?.headers?.length) table.value = normalizeTable(saved)
  } catch {
    // 忽略损坏的本地缓存
  }
}

function persistTable(value) {
  const key = storageKey()
  if (key && value) localStorage.setItem(key, JSON.stringify(value))
}

const unavailableTools = computed(() => {
  const available = new Set(ctx.tools.value.map((t) => t.code))
  if (!available.size) return []
  return tabs.map((t) => t.tool).filter((tool) => tool && !available.has(tool))
})
</script>

<style scoped>
.stage { @apply bg-white; }
</style>
