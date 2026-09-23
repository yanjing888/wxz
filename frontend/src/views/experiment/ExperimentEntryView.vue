<template>
  <div class="entry flex-1 min-h-0 flex items-center justify-center px-6">
    <div class="entry-box">
      <p v-if="loading" class="entry-text">正在进入实验台…</p>
      <template v-else>
        <p class="entry-title">{{ error ? '实验加载失败' : '暂无已分配的实验' }}</p>
        <p class="entry-text mt-2">
          {{ error || '请联系任课教师为你分配实验后再进入。' }}
        </p>
        <button v-if="error" type="button" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold mt-4" @click="resolveEntry">
          重新加载
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { studentExperimentApi } from '../../api'
import { lastExperiment } from '../../utils/experimentFlow'
import { labEntryBlockedRoute } from '../../utils/prepSimulation'
import { syncSubmittedReportDrafts } from '../../utils/reportDraftSync'

const router = useRouter()
const loading = ref(true)
const error = ref('')

onMounted(resolveEntry)

/**
 * 默认进实验台助教。
 * 仅当：从未就绪、且还没有进行中会话 → 先过进门预习（可跳过）。
 */
async function resolveEntry() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await studentExperimentApi.listProgress()
    const rows = data || []
    if (!rows.length) return
    syncSubmittedReportDrafts()

    const remembered = lastExperiment()
    const row = rows.find((r) => r.experimentCode === remembered) || rows[0]
    const code = row.experimentCode

    const simBlock = labEntryBlockedRoute(row, code)
    if (simBlock) {
      await router.replace(simBlock)
      return
    }
    if (!row.preLabCompleted && !row.activeSessionId) {
      await router.replace({ name: 'prep-ready', params: { code } })
      return
    }
    await router.replace({ name: 'lab', query: { exp: code } })
  } catch (e) {
    error.value = e.response?.data?.message || '实验列表加载失败，请确认后端已启动'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.entry { @apply bg-surface-soft/40; }
.entry-box { @apply rounded-2xl border border-line-soft bg-white px-8 py-10 text-center max-w-md w-full; }
.entry-title { @apply text-[16px] font-bold text-ink-strong; }
.entry-text { @apply text-[13.5px] text-ink-muted; }
</style>
