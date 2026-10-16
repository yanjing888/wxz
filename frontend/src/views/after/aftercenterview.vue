<template>
  <div class="after-center flex-1 min-h-0 flex flex-col bg-white">
    <div class="report-context"><router-link :to="{name:'student-reports'}">← 全部实验报告</router-link><span>{{ experimentName }} · {{ reportClass }}<template v-if="reportDueAt"> · 截止 {{ new Date(reportDueAt).toLocaleString('zh-CN') }}</template></span></div>
    <div v-if="loading" class="state">正在加载…</div>
    <div v-else-if="!sessionId" class="state">请先完成并结束此实验，再填写报告。</div>
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
const reportClass=ref(''),reportDueAt=ref(null)

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
    sessionId.value = prog?.finishedSessionId || null
    reportClass.value=prog?.className||'未分班'
    reportDueAt.value=prog?.reportDueAt||null
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.after-center { @apply bg-white; }
.report-context{display:flex;align-items:center;gap:20px;padding:10px 18px;border-bottom:1px solid #e3e9ed;font-size:12px;color:#758494;flex-shrink:0}.report-context a{color:#276d74}
.state { @apply flex-1 h-full flex items-center justify-center text-[14px] text-ink-muted; }
</style>
