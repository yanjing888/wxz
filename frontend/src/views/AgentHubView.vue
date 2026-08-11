<template>
  <div class="hub flex-1 min-h-0 flex flex-col bg-surface-soft/40">
    <header class="hub-head shrink-0">
      <div class="hub-head-inner">
        <div class="min-w-0">
          <p class="eyebrow">课余选用</p>
          <h1 class="title">更多实验能力</h1>
          <p class="sub">
            课上请回「实验台」直接问助教。这里放预习、溯源、原理答疑等课余能力，不必上课时打开。
          </p>
        </div>
        <div class="exp-wrap">
          <label class="exp-label">当前实验</label>
          <select v-model="experimentCode" class="exp-select" @change="persistExp">
            <option v-if="!options.length" value="">暂无已分配实验</option>
            <option v-for="opt in options" :key="opt.code" :value="opt.code">
              {{ opt.name }}
            </option>
          </select>
        </div>
      </div>
    </header>

    <div class="hub-body flex-1 min-h-0 overflow-y-auto custom-scroll">
      <div v-if="loading" class="state">加载智能体列表…</div>
      <div v-else-if="!experimentCode" class="state">请先联系教师分配实验。</div>
      <template v-else>
        <section v-for="group in STUDENT_AGENT_GROUPS" :key="group.key" class="group">
          <div class="group-head">
            <h2 class="group-title">{{ group.label }}</h2>
            <p class="group-lead">{{ group.lead }}</p>
          </div>
          <div class="agent-grid">
            <button
              v-for="agent in agentsByGroup(group.key)"
              :key="agent.code"
              type="button"
              class="agent-card"
              @click="openAgent(agent)"
            >
              <div class="agent-top">
                <span class="ai-badge">{{ agent.aiLabel }}</span>
                <span
                  v-if="availability[agent.code] && availability[agent.code].available === false"
                  class="warn-badge"
                >未配置专用工作流</span>
              </div>
              <h3 class="agent-name">{{ agent.name }}</h3>
              <p class="agent-blurb">{{ agent.blurb }}</p>
              <span class="agent-go">进入工作区 →</span>
            </button>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { aiToolApi, studentExperimentApi } from '../api'
import { lastExperiment, rememberVisit } from '../utils/experimentFlow'
import { STUDENT_AGENT_GROUPS, agentsByGroup } from '../utils/studentAgents'

const router = useRouter()
const loading = ref(true)
const options = ref([])
const experimentCode = ref('')
const availability = ref({})

onMounted(async () => {
  loading.value = true
  try {
    const [progRes, toolsRes] = await Promise.allSettled([
      studentExperimentApi.listProgress(),
      aiToolApi.tools()
    ])
    const rows = progRes.status === 'fulfilled' ? progRes.value.data || [] : []
    options.value = rows.map((r) => ({
      code: r.experimentCode,
      name: r.experimentName || r.experimentCode
    }))
    const remembered = lastExperiment()
    experimentCode.value = options.value.some((o) => o.code === remembered)
      ? remembered
      : (options.value[0]?.code || '')

    const tools = toolsRes.status === 'fulfilled' ? toolsRes.value.data || [] : []
    const map = {}
    tools.forEach((t) => {
      map[t.code] = t
    })
    // lab-assist 映射 session assist，不在 tools 列表时视为可用
    map['lab-assist'] = map['lab-assist'] || { available: true }
    availability.value = map
  } finally {
    loading.value = false
  }
})

function persistExp() {
  if (experimentCode.value) rememberVisit(experimentCode.value, 'task')
}

function openAgent(agent) {
  if (!experimentCode.value) return
  rememberVisit(experimentCode.value, 'task')

  if (agent.kind === 'lab') {
    const query = { exp: experimentCode.value, tab: agent.labTab || 'guide' }
    if (agent.labAction) query.agent = agent.labAction
    router.push({ name: 'lab', query })
    return
  }
  if (agent.kind === 'route') {
    router.push({ name: agent.routeName, params: { code: experimentCode.value } })
    return
  }
  router.push({
    name: 'agent-workspace',
    params: { code: experimentCode.value, agent: agent.code }
  })
}
</script>

<style scoped>
.hub-head { @apply bg-white border-b border-line-soft; }
.hub-head-inner {
  @apply max-w-6xl mx-auto px-5 md:px-8 py-6 flex flex-wrap items-end justify-between gap-5;
}
.eyebrow { @apply text-[11px] font-bold uppercase tracking-wide text-brand-600; }
.title { @apply text-[22px] font-bold text-ink-strong mt-1; }
.sub { @apply text-[13.5px] text-ink-muted mt-1.5 max-w-xl leading-relaxed; }
.exp-wrap { @apply shrink-0; }
.exp-label { @apply block text-[12px] font-semibold text-ink-muted mb-1.5; }
.exp-select {
  @apply min-w-[220px] rounded-xl border border-line-soft bg-white px-3 py-2.5 text-[14px] text-ink-strong;
}
.hub-body { @apply max-w-6xl w-full mx-auto px-5 md:px-8 py-6 space-y-8; }
.state { @apply py-20 text-center text-[14px] text-ink-muted; }
.group-head { @apply mb-3; }
.group-title { @apply text-[15px] font-bold text-ink-strong; }
.group-lead { @apply text-[12.5px] text-ink-muted mt-0.5; }
.agent-grid {
  @apply grid gap-3;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
}
.agent-card {
  @apply text-left rounded-2xl border border-line-soft bg-white p-4
    hover:border-brand-200 hover:shadow-card transition-all;
}
.agent-top { @apply flex flex-wrap items-center gap-2 mb-2; }
.ai-badge {
  @apply text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-md
    bg-brand-50 text-brand-700 border border-brand-100;
}
.warn-badge {
  @apply text-[10px] font-semibold px-2 py-0.5 rounded-md
    bg-amber-50 text-amber-700 border border-amber-100;
}
.agent-name { @apply text-[16px] font-bold text-ink-strong; }
.agent-blurb { @apply text-[13px] text-ink-muted mt-1.5 leading-relaxed min-h-[3rem]; }
.agent-go { @apply inline-block mt-3 text-[13px] font-semibold text-brand-700; }
</style>
