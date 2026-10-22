import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { studentExperimentApi } from '../api'

export function useReportProgress(code, sessionId) {
  const progress = ref(null), loading = ref(false), error = ref('')
  let sequence = 0, timer
  function apply(data) {
    sequence++
    progress.value = data
    loading.value = false
    error.value = ''
  }
  async function refresh() {
    if (!code() || !sessionId()) return null
    const request = ++sequence, experiment = code(), session = sessionId()
    loading.value = true
    try {
      const { data } = await studentExperimentApi.getProgress(experiment)
      if (request !== sequence || code() !== experiment || sessionId() !== session) return null
      progress.value = data
      error.value = ''
      return data
    } catch {
      if (request === sequence) error.value = '成绩同步失败，请重试'
      return null
    } finally {
      if (request === sequence) loading.value = false
    }
  }
  function refreshVisible() {
    if (document.visibilityState === 'visible' && !loading.value) refresh()
  }
  watch([code, sessionId], () => { sequence++; progress.value = null; error.value = ''; loading.value = false })
  onMounted(() => {
    timer = setInterval(refreshVisible, 15000)
    window.addEventListener('focus', refreshVisible)
    document.addEventListener('visibilitychange', refreshVisible)
  })
  onBeforeUnmount(() => {
    sequence++
    clearInterval(timer)
    window.removeEventListener('focus', refreshVisible)
    document.removeEventListener('visibilitychange', refreshVisible)
  })
  return { progress, loading, error, refresh, apply }
}
