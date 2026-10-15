<template>
  <div class="hub-page flex-1 min-h-0 overflow-y-auto custom-scroll">
    <div class="hub-inner">
      <header class="hub-header">
        <div class="min-w-0">
          <h1 class="hub-title">AI 工具箱</h1>
          <p class="hub-lead">围绕实验全过程的 {{ agents.length }} 个智能体，按你所处的阶段选用。</p>
        </div>
        <div class="exp-picker">
          <label class="exp-label">当前实验</label>
          <select v-model="experimentCode" class="exp-select" @change="onExperimentChange">
            <option value="">未选择</option>
            <option v-for="exp in experiments" :key="exp.code" :value="exp.code">{{ exp.name }}</option>
          </select>
        </div>
      </header>

      <p v-if="!experimentCode" class="hub-tip">
        选择当前实验后，进入各智能体时会自动带上实验上下文，无需重复选择。
      </p>

      <section v-for="group in groups" :key="group.key" class="group-block">
        <div class="group-head">
          <h2 class="group-title">{{ group.label }}</h2>
          <span class="group-desc">{{ group.desc }}</span>
        </div>
        <div class="card-grid">
          <router-link
            v-for="agent in groupAgents(group.key)"
            :key="agent.code"
            :to="agentRoute(agent)"
            class="agent-card"
            :class="{ 'agent-card--dim': isUnavailable(agent) }"
          >
            <div class="card-top">
              <span class="card-icon" :class="`card-icon--${agent.tone}`">{{ agent.icon }}</span>
              <span v-if="agent.badge" class="card-badge">{{ agent.badge }}</span>
            </div>
            <h3 class="card-name">{{ agent.name }}</h3>
            <p class="card-tagline">{{ agent.tagline }}</p>
            <p class="card-desc">{{ agent.desc }}</p>
            <p v-if="statusText(agent)" class="card-status">{{ statusText(agent) }}</p>
          </router-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { aiToolApi, experimentApi } from '../../api'
import {
  AGENTS,
  AGENT_GROUPS,
  agentsByGroup,
  readAgentExperiment,
  saveAgentExperiment
} from '../../utils/agentCatalog'

const agents = AGENTS
const groups = AGENT_GROUPS

const experiments = ref([])
const experimentCode = ref(readAgentExperiment())
const backendTools = ref([])
const finishedSessionCount = ref(0)

onMounted(async () => {
  const [expRes, toolRes, sessionRes] = await Promise.allSettled([
    experimentApi.list(),
    aiToolApi.tools(),
    aiToolApi.recapSessions()
  ])
  if (expRes.status === 'fulfilled') experiments.value = expRes.value.data || []
  if (toolRes.status === 'fulfilled') backendTools.value = toolRes.value.data || []
  if (sessionRes.status === 'fulfilled') finishedSessionCount.value = (sessionRes.value.data || []).length

  if (experimentCode.value && !experiments.value.some((e) => e.code === experimentCode.value)) {
    experimentCode.value = ''
    saveAgentExperiment('')
  }
})

function groupAgents(key) {
  return agentsByGroup(key)
}

function agentRoute(agent) {
  const query = experimentCode.value ? { exp: experimentCode.value } : {}
  return { name: 'ai-tool', params: { code: agent.code }, query }
}

function onExperimentChange() {
  saveAgentExperiment(experimentCode.value)
}

function isUnavailable(agent) {
  const hit = backendTools.value.find((t) => t.code === agent.code)
  return backendTools.value.length > 0 && !hit
}

function statusText(agent) {
  if (isUnavailable(agent)) return '尚未启用'
  if (agent.needsSession && finishedSessionCount.value === 0) return '需先完成一次实验'
  return ''
}
</script>

<style scoped>
.hub-page { @apply bg-surface-soft/40; }
.hub-inner { @apply max-w-[1080px] mx-auto w-full px-6 py-7 pb-12; }

.hub-header { @apply flex flex-wrap items-end justify-between gap-4 mb-2; }
.hub-title { @apply text-[22px] font-bold text-ink-strong; }
.hub-lead { @apply text-[14px] text-ink-muted mt-1.5; }
.exp-picker { @apply flex items-center gap-2 shrink-0; }
.exp-label { @apply text-[13px] text-ink-muted font-medium; }
.exp-select { @apply rounded-xl border border-line-soft bg-white px-3 py-2 text-[14px] min-w-[180px]; }
.hub-tip { @apply text-[13px] text-ink-faint mb-6 mt-3; }

.group-block { @apply mt-7 first:mt-6; }
.group-head { @apply flex flex-wrap items-baseline gap-3 mb-3; }
.group-title { @apply text-[15px] font-bold text-ink-strong; }
.group-desc { @apply text-[12px] text-ink-faint; }

.card-grid { @apply grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4; }
.agent-card {
  @apply flex flex-col rounded-2xl border border-line-soft bg-white p-4
    transition-all hover:border-brand-300 hover:shadow-card hover:-translate-y-0.5;
}
.agent-card--dim { @apply opacity-55; }
.card-top { @apply flex items-start justify-between mb-3; }
.card-icon {
  @apply w-10 h-10 rounded-xl flex items-center justify-center text-[15px] font-bold text-white;
}
.card-icon--sky { @apply bg-sky-500; }
.card-icon--violet { @apply bg-violet-500; }
.card-icon--emerald { @apply bg-emerald-500; }
.card-icon--amber { @apply bg-amber-500; }
.card-icon--rose { @apply bg-rose-500; }
.card-badge {
  @apply text-[10px] font-bold px-1.5 py-0.5 rounded bg-brand-50 text-brand-600 border border-brand-100;
}
.card-name { @apply text-[15px] font-bold text-ink-strong; }
.card-tagline { @apply text-[12px] text-brand-600 font-medium mt-0.5; }
.card-desc { @apply text-[12.5px] text-ink-muted mt-2 leading-relaxed flex-1; }
.card-status { @apply text-[11px] text-amber-600 mt-2.5 font-medium; }
</style>
