<template>
  <div class="stage flex-1 min-h-0 flex flex-col">
    <StageTabs v-model="activeTab" :tabs="tabs" :unavailable="unavailableTools" />

    <ReportEditorPanel
      v-if="activeTab === 'write'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
    />
    <ThinkQuestionsPanel
      v-else-if="activeTab === 'think'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
    />
    <RecapPanel
      v-else
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
    />
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import StageTabs from '../../components/stage/StageTabs.vue'
import ReportEditorPanel from '../../components/stage/ReportEditorPanel.vue'
import ThinkQuestionsPanel from '../../components/stage/ThinkQuestionsPanel.vue'
import RecapPanel from '../../components/stage/RecapPanel.vue'
import { STAGE_TABS } from '../../utils/experimentFlow'

const ctx = inject('experimentContext')

const tabs = STAGE_TABS.report
const activeTab = ref('write')

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
.stage { @apply bg-white; }
</style>
