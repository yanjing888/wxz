<template>
  <div class="after-page flex-1 min-h-0 flex flex-col bg-surface-soft/40">
    <AfterClassHeader
      :experiment-code="code"
      title="报告教练"
      subtitle="基于本次实验数据起稿、润色与查缺漏；思考题请自己作答，AI 不代写结论。"
    />
    <div class="after-body flex-1 min-h-0 flex flex-col">
      <ReportEditorPanel
        v-if="ready"
        :experiment-code="code"
        :experiment-name="experimentName"
        :session-id="sessionId"
      />
      <div v-else class="state">正在加载…</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { experimentApi, studentExperimentApi } from '../../api'
import AfterClassHeader from '../../components/layout/AfterClassHeader.vue'
import ReportEditorPanel from '../../components/stage/ReportEditorPanel.vue'

const route = useRoute()
const code = computed(() => String(route.params.code || ''))
const experimentName = ref('')
const sessionId = ref(null)
const ready = ref(false)

onMounted(async () => {
  if (!code.value) return
  try {
    const [cfgRes, progRes] = await Promise.allSettled([
      experimentApi.get(code.value),
      studentExperimentApi.getProgress(code.value)
    ])
    experimentName.value =
      (cfgRes.status === 'fulfilled' ? cfgRes.value.data?.name : '') || code.value
    const prog = progRes.status === 'fulfilled' ? progRes.value.data : null
    sessionId.value = prog?.finishedSessionId || prog?.activeSessionId || null
  } finally {
    ready.value = true
  }
})
</script>

<style scoped>
.after-body { @apply bg-white; }
.state { @apply flex-1 flex items-center justify-center text-[13px] text-ink-muted; }
</style>
