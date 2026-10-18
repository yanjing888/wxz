<template>
  <div class="loop flex-1 min-h-0 flex flex-col">
    <header class="loop-head shrink-0">
      <ExperimentSelect
        :experiments="experimentOptions"
        :experiment-code="code"
        @experiment-change="switchExperiment"
      />

      <nav class="loop-nav" aria-label="实验实训闭环">
        <router-link
          v-for="(node, index) in LOOP_NODES"
          :key="node.key"
          :to="node.route(code)"
          class="loop-tab"
          :class="tabClass(node.key, index)"
          :title="node.lead"
        >
          <span class="loop-index">{{ loopStatusOf(node.key) === 'done' ? '✓' : index + 1 }}</span>
          <span class="loop-name">{{ node.label }}</span>
        </router-link>
      </nav>

      <router-link :to="labRoute" class="btn-brand px-4 py-2 rounded-xl text-[13px] font-bold shrink-0">
        进入实验台
      </router-link>

      <router-link :to="{ name: 'files', query: { exp: code } }" class="files-link">
        全部资料
      </router-link>
    </header>

    <div class="loop-body flex-1 min-h-0 flex flex-col">
      <router-view v-slot="{ Component }">
        <component :is="Component" v-if="Component" class="node-root" />
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { aiToolApi, experimentApi, sessionApi, studentExperimentApi } from '../../api'
import ExperimentSelect from '../../components/layout/ExperimentSelect.vue'
import {
  LOOP_NODES,
  findLoopNode,
  lastLoopNode,
  loopStatus,
  rememberVisit
} from '../../utils/experimentFlow'

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

const activeNode = computed(() => {
  if (route.name === 'loop-documents') return 'documents'
  if (route.name === 'loop-review') return 'review'
  return 'task'
})

const labRoute = computed(() => ({ name: 'lab', query: { exp: code.value } }))

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
watch([code, activeNode], ([c, node]) => rememberVisit(c, node), { immediate: true })

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
  router.push(findLoopNode(lastLoopNode(next)).route(next))
}

function loopStatusOf(nodeKey) {
  return loopStatus(progress.value, nodeKey)
}

function tabClass(nodeKey, index) {
  const status = loopStatusOf(nodeKey)
  return {
    'loop-tab--active': activeNode.value === nodeKey,
    'loop-tab--done': status === 'done',
    'loop-tab--progress': status === 'in_progress' && activeNode.value !== nodeKey
  }
}
</script>

<style scoped>
.loop { @apply bg-surface-soft/40; }

.loop-head { @apply flex items-center gap-3 h-12 px-4 bg-white border-b border-line-soft relative z-20; }
.files-link {
  @apply text-[12.5px] font-medium text-ink-muted hover:text-brand-600 transition-colors shrink-0;
}

.loop-nav { @apply flex items-stretch gap-1 flex-1 min-w-0 h-full overflow-x-auto; }
.loop-tab {
  @apply relative flex items-center gap-2 px-3.5 text-[13.5px] text-ink-muted whitespace-nowrap
    transition-colors hover:text-ink-strong;
}
.loop-index {
  @apply w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold
    bg-surface-muted text-ink-faint;
}
.loop-tab--done .loop-index { @apply bg-emerald-500 text-white; }
.loop-tab--progress .loop-index { @apply bg-amber-400 text-white; }
.loop-tab--active { @apply text-brand-600 font-semibold; }
.loop-tab--active .loop-index { @apply bg-brand-600 text-white; }
.loop-tab--active::after {
  content: '';
  @apply absolute bottom-0 left-2.5 right-2.5 h-0.5 bg-brand-600 rounded-full;
}

:deep(.node-root) { @apply flex-1 min-h-0 flex flex-col; }
</style>
