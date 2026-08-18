<template>
  <div class="after-center flex-1 min-h-0 flex flex-col bg-white">
    <div v-if="loading" class="state">正在加载…</div>
    <ReportEditorPanel
      v-else
      class="h-full"
      :experiment-code="code"
      :experiment-name="experimentName"
      :session-id="sessionId"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { experimentApi, studentExperimentApi } from '../../api'
import ReportEditorPanel from '../../components/stage/ReportEditorPanel.vue'
import { rememberVisit } from '../../utils/experimentFlow'

const route = useRoute()
const code = computed(() => String(route.params.code || '').trim())

const experimentName = ref('')
const sessionId = ref(null)
const loading = ref(true)

onMounted(load)
watch(code, load)

async function load() {
  if (!code.value) return
  loading.value = true
  rememberVisit(code.value, 'review')
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
    loading.value = false
  }
}
</script>

<style scoped>
.after-center { @apply bg-white; }
.state { @apply flex-1 h-full flex items-center justify-center text-[14px] text-ink-muted; }
</style>
