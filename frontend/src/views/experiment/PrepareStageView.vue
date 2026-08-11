<template>
  <div class="stage flex-1 min-h-0 flex flex-col">
    <StageTabs v-model="activeTab" :tabs="tabs" :unavailable="unavailableTools" />

    <OverviewPanel v-if="activeTab === 'overview'" :config="ctx.config.value" @next="activeTab = $event" />
    <EquipmentPanel
      v-else-if="activeTab === 'equipment'"
      :experiment-code="code"
      :experiment-name="experimentName"
    />
    <BriefPanel
      v-else-if="activeTab === 'brief'"
      :experiment-code="code"
      :experiment-name="experimentName"
      :knowledge="knowledge"
      @next="activeTab = $event"
    />
    <QuizPanel
      v-else-if="activeTab === 'quiz'"
      :experiment-code="code"
      :experiment-name="experimentName"
    />
    <PrepReportPanel
      v-else-if="activeTab === 'prep-report'"
      :experiment-code="code"
      :experiment-name="experimentName"
    />
    <AskPanel
      v-else
      :experiment-code="code"
      :experiment-name="experimentName"
      :suggestions="askSuggestions"
    />
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import StageTabs from '../../components/stage/StageTabs.vue'
import OverviewPanel from '../../components/stage/OverviewPanel.vue'
import EquipmentPanel from '../../components/stage/EquipmentPanel.vue'
import BriefPanel from '../../components/stage/BriefPanel.vue'
import QuizPanel from '../../components/stage/QuizPanel.vue'
import PrepReportPanel from '../../components/stage/PrepReportPanel.vue'
import AskPanel from '../../components/stage/AskPanel.vue'
import { STAGE_TABS } from '../../utils/experimentFlow'

const ctx = inject('experimentContext')

const tabs = STAGE_TABS.prepare
const activeTab = ref('overview')

const code = computed(() => ctx.code.value)
const experimentName = computed(() => ctx.experimentName.value)
const knowledge = computed(() => ctx.config.value?.reportKnowledge || [])
const askSuggestions = computed(
  () => ctx.tools.value.find((t) => t.code === 'explore')?.suggestions || []
)

const unavailableTools = computed(() => {
  const available = new Set(ctx.tools.value.map((t) => t.code))
  if (!available.size) return []
  return tabs.map((t) => t.tool).filter((tool) => tool && !available.has(tool))
})
</script>

<style scoped>
.stage { @apply bg-white; }
</style>
