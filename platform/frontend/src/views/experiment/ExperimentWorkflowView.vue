<template>
  <div class="workflow flex-1 min-h-0 flex flex-col">
    <header class="flow-head shrink-0">
      <ExperimentSelect
        :experiments="experimentOptions"
        :experiment-code="code"
        @experiment-change="switchExperiment"
      />

      <nav class="stage-nav" aria-label="实验阶段">
        <router-link
          v-for="(stage, index) in STAGES"
          :key="stage.key"
          :to="stage.route(code)"
          class="stage-tab"
          :class="{ 'stage-tab--active': activeStage === stage.key, 'stage-tab--done': statusOf(stage.key) === 'done' }"
          :title="stage.lead"
        >
          <span class="stage-index">{{ statusOf(stage.key) === 'done' ? '✓' : index + 1 }}</span>
          <span class="stage-name">{{ stage.label }}</span>
        </router-link>
      </nav>

      <router-link :to="{ name: 'files', query: { exp: code } }" class="files-link">
        本实验资料
      </router-link>
    </header>

    <div class="flow-body flex-1 min-h-0 flex flex-col">
      <router-view v-slot="{ Component }">
        <component :is="Component" v-if="Component" class="stage-root" />
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiToolApi, experimentApi, sessionApi, studentExperimentApi } from '../../api'
import ExperimentSelect from '../../components/layout/ExperimentSelect.vue'
import { STAGES, findStage, lastStage, rememberVisit, stageStatus } from '../../utils/experimentFlow'

const route = useRoute()
const router = useRouter()

const code = computed(() => String(route.params.code || ''))
const config = ref(null)
const progress = ref(null)
const sessions = ref([])
const tools = ref([])
const assigned = ref([])

const experimentName = computed(
  () => config.value?.name || progress.value?.experimentName || code.value || '实验'
)

const experimentOptions = computed(() =>
  assigned.value.map((row) => ({ code: row.experimentCode, name: row.experimentName }))
)

const activeStage = computed(() => {
  if (route.name === 'stage-data') return 'data'
  if (route.name === 'stage-report') return 'report'
  return 'prepare'
})

/** 已完成的会话优先，用于数据处理与报告阶段取数 */
const primarySessionId = computed(
  () => progress.value?.finishedSessionId || progress.value?.activeSessionId || null
)

provide('experimentContext', {
  code,
  experimentName,
  config,
  progress,
  sessions,
  tools,
  primarySessionId,
  reload
})

onMounted(reload)
watch(code, reload)
watch([code, activeStage], ([c, stage]) => rememberVisit(c, stage), { immediate: true })

async function reload() {
  if (!code.value) return
  const [cfgRes, progressRes, sessionRes, toolRes, assignedRes] = await Promise.allSettled([
    experimentApi.get(code.value),
    studentExperimentApi.getProgress(code.value),
    sessionApi.list({ experimentCode: code.value }),
    aiToolApi.tools(),
    studentExperimentApi.listProgress()
  ])
  config.value = cfgRes.status === 'fulfilled' ? cfgRes.value.data : null
  progress.value = progressRes.status === 'fulfilled' ? progressRes.value.data : null
  sessions.value = sessionRes.status === 'fulfilled' ? sessionRes.value.data || [] : []
  tools.value = toolRes.status === 'fulfilled' ? toolRes.value.data || [] : []
  assigned.value = assignedRes.status === 'fulfilled' ? assignedRes.value.data || [] : []
}

function switchExperiment(next) {
  if (!next || next === code.value) return
  router.push(findStage(lastStage(next)).route(next))
}

function statusOf(stageKey) {
  return stageStatus(progress.value, stageKey)
}
</script>

<style scoped>
.workflow { @apply bg-surface-soft/40; }

.flow-head { @apply flex items-center gap-3 h-12 px-4 bg-white border-b border-line-soft relative z-20; }
.files-link {
  @apply text-[12.5px] font-medium text-ink-muted hover:text-brand-600 transition-colors shrink-0;
}

.stage-nav { @apply flex items-stretch gap-1 flex-1 min-w-0 h-full overflow-x-auto; }
.stage-tab {
  @apply relative flex items-center gap-2 px-3.5 text-[13.5px] text-ink-muted whitespace-nowrap
    transition-colors hover:text-ink-strong;
}
.stage-index {
  @apply w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold
    bg-surface-muted text-ink-faint;
}
.stage-tab--done .stage-index { @apply bg-emerald-500 text-white; }
.stage-tab--active { @apply text-brand-600 font-semibold; }
.stage-tab--active .stage-index { @apply bg-brand-600 text-white; }
.stage-tab--active::after {
  content: '';
  @apply absolute bottom-0 left-2.5 right-2.5 h-0.5 bg-brand-600 rounded-full;
}

:deep(.stage-root) { @apply flex-1 min-h-0 flex flex-col; }
</style>
