<template>
  <div class="after-page flex-1 min-h-0 flex flex-col bg-surface-soft/40">
    <AfterClassHeader
      :experiment-code="code"
      title="评价复盘"
      subtitle="实验结束后再回顾；课上时间请留给操作与测量。"
    />
    <div v-if="ready" class="after-body flex-1 min-h-0 flex flex-col bg-white">
      <StageTabs v-model="activeTab" :tabs="tabs" :unavailable="unavailableTools" />
      <RecapPanel
        v-if="activeTab === 'recap'"
        :experiment-code="code"
        :experiment-name="experimentName"
        :session-id="sessionId"
      />
      <ThinkQuestionsPanel
        v-else
        :experiment-code="code"
        :experiment-name="experimentName"
        :session-id="sessionId"
      />
    </div>
    <div v-else class="state">正在加载…</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { aiToolApi, experimentApi, studentExperimentApi } from '../../api'
import AfterClassHeader from '../../components/layout/AfterClassHeader.vue'
import StageTabs from '../../components/stage/StageTabs.vue'
import RecapPanel from '../../components/stage/RecapPanel.vue'
import ThinkQuestionsPanel from '../../components/stage/ThinkQuestionsPanel.vue'

const route = useRoute()
const code = computed(() => String(route.params.code || ''))
const experimentName = ref('')
const sessionId = ref(null)
const ready = ref(false)
const tools = ref([])
const activeTab = ref('recap')

const tabs = [
  { key: 'recap', label: '实验复盘', tool: 'lab-recap' },
  { key: 'think', label: '思考题', tool: 'think-questions' }
]

const unavailableTools = computed(() => {
  const available = new Set(tools.value.map((t) => t.code))
  if (!available.size) return []
  return tabs.map((t) => t.tool).filter((tool) => tool && !available.has(tool))
})

onMounted(async () => {
  if (!code.value) return
  try {
    const [cfgRes, progRes, toolRes] = await Promise.allSettled([
      experimentApi.get(code.value),
      studentExperimentApi.getProgress(code.value),
      aiToolApi.tools()
    ])
    experimentName.value =
      (cfgRes.status === 'fulfilled' ? cfgRes.value.data?.name : '') || code.value
    const prog = progRes.status === 'fulfilled' ? progRes.value.data : null
    sessionId.value = prog?.finishedSessionId || prog?.activeSessionId || null
    tools.value = toolRes.status === 'fulfilled' ? toolRes.value.data || [] : []
  } finally {
    ready.value = true
  }
})
</script>

<style scoped>
.state { @apply flex-1 flex items-center justify-center text-[13px] text-ink-muted; }
</style>
