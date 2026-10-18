<template>
  <div class="node flex-1 min-h-0 flex flex-col overflow-y-auto custom-scroll">
    <TaskLandingPanel
      :config="ctx.config.value"
      :experiment-code="code"
      :experiment-name="experimentName"
      :knowledge="knowledge"
      :ask-suggestions="askSuggestions"
    />
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import TaskLandingPanel from '../../components/stage/TaskLandingPanel.vue'

const ctx = inject('experimentContext')

const code = computed(() => ctx.code.value)
const experimentName = computed(() => ctx.experimentName.value)
const knowledge = computed(() => ctx.config.value?.reportKnowledge || [])
const askSuggestions = computed(
  () => ctx.tools.value.find((t) => t.code === 'explore')?.suggestions || []
)
</script>

<style scoped>
.node { @apply bg-white; }
</style>
