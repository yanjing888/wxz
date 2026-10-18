<template>
  <div class="node flex-1 min-h-0 flex flex-col">
    <StageTabs v-model="activeTab" :tabs="tabs" :unavailable="unavailableTools" />

    <ReportEditorPanel
      v-if="activeTab === 'report'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
    />
    <PrepReportPanel
      v-else-if="activeTab === 'prep'"
      :experiment-code="code"
      :experiment-name="experimentName"
    />
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import StageTabs from '../../components/stage/StageTabs.vue'
import PrepReportPanel from '../../components/stage/PrepReportPanel.vue'
import ReportEditorPanel from '../../components/stage/ReportEditorPanel.vue'
import { LOOP_TABS } from '../../utils/experimentFlow'

const ctx = inject('experimentContext')

const tabs = LOOP_TABS.documents
const activeTab = ref('report')

const code = computed(() => ctx.code.value)
const experimentName = computed(() => ctx.experimentName.value)
const sessionId = computed(() => ctx.primarySessionId.value)

const unavailableTools = computed(() => {
  const available = new Set(ctx.tools.value.map((t) => t.code))
  if (!available.size) return []
  return tabs.map((t) => t.tool).filter((tool) => tool && !available.has(tool))
})
</script>

<style scoped>
.node { @apply bg-white; }
</style>
