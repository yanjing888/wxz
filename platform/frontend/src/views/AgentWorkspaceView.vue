<template>
  <div class="ws flex-1 min-h-0 flex flex-col bg-surface-soft/40">
    <header class="ws-head shrink-0">
      <div class="ws-head-inner">
        <button type="button" class="back-btn" @click="goHub">← 智能体大厅</button>
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="ai-badge">{{ agent?.aiLabel || 'AI' }}</span>
            <h1 class="title">{{ agent?.name || '智能体' }}</h1>
          </div>
          <p class="sub">{{ experimentName }} · {{ agent?.blurb }}</p>
        </div>
        <router-link
          :to="{ name: 'lab', query: { exp: code } }"
          class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold border border-line-soft shrink-0"
        >
          去实验台
        </router-link>
      </div>
    </header>

    <div class="ws-body flex-1 min-h-0 overflow-hidden bg-white">
      <div v-if="!agent" class="state">未找到该智能体</div>
      <BriefPanel
        v-else-if="agent.panel === 'brief'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
        @next="goAgent('practice-quiz')"
      />
      <QuizPanel
        v-else-if="agent.panel === 'quiz'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
      />
      <EquipmentPanel
        v-else-if="agent.panel === 'equipment'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
      />
      <ErrorTracePanel
        v-else-if="agent.panel === 'trace'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
        :session-id="sessionId"
      />
      <ThinkQuestionsPanel
        v-else-if="agent.panel === 'think'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
        :session-id="sessionId"
      />
      <AskPanel
        v-else-if="agent.panel === 'ask'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
      />
      <div v-else class="state">该智能体请从实验台或课后页面进入。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { experimentApi, studentExperimentApi } from '../api'
import BriefPanel from '../components/stage/BriefPanel.vue'
import QuizPanel from '../components/stage/QuizPanel.vue'
import EquipmentPanel from '../components/stage/EquipmentPanel.vue'
import ErrorTracePanel from '../components/stage/ErrorTracePanel.vue'
import ThinkQuestionsPanel from '../components/stage/ThinkQuestionsPanel.vue'
import AskPanel from '../components/stage/AskPanel.vue'
import { findStudentAgent } from '../utils/studentAgents'
import { rememberVisit } from '../utils/experimentFlow'

const route = useRoute()
const router = useRouter()

const code = computed(() => String(route.params.code || '').trim())
const agentCode = computed(() => String(route.params.agent || '').trim())
const agent = computed(() => findStudentAgent(agentCode.value))
const experimentName = ref('')
const sessionId = ref(null)

onMounted(load)
watch([code, agentCode], load)

async function load() {
  if (!code.value) return
  rememberVisit(code.value, 'task')
  try {
    const [cfgRes, progRes] = await Promise.allSettled([
      experimentApi.get(code.value),
      studentExperimentApi.getProgress(code.value)
    ])
    experimentName.value =
      (cfgRes.status === 'fulfilled' ? cfgRes.value.data?.name : '') || code.value
    const prog = progRes.status === 'fulfilled' ? progRes.value.data : null
    sessionId.value = prog?.finishedSessionId || prog?.activeSessionId || null
  } catch {
    experimentName.value = code.value
  }
}

function goHub() {
  router.push({ name: 'agents' })
}

function goAgent(nextCode) {
  router.push({ name: 'agent-workspace', params: { code: code.value, agent: nextCode } })
}
</script>

<style scoped>
.ws-head { @apply bg-white border-b border-line-soft; }
.ws-head-inner {
  @apply max-w-6xl mx-auto px-5 md:px-8 py-4 flex flex-wrap items-center gap-4;
}
.back-btn {
  @apply text-[13px] font-semibold text-ink-muted hover:text-brand-700 shrink-0;
}
.ai-badge {
  @apply text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-md
    bg-brand-50 text-brand-700 border border-brand-100;
}
.title { @apply text-[18px] font-bold text-ink-strong; }
.sub { @apply text-[12.5px] text-ink-muted mt-1; }
.ws-body { @apply max-w-6xl w-full mx-auto; }
.state { @apply flex-1 flex items-center justify-center text-[14px] text-ink-muted py-20; }
</style>
