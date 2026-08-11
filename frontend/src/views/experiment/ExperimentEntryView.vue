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

const router = useRouter()
const loading = ref(true)
const error = ref('')

onMounted(resolveEntry)

/** 我的实验 = 直接进入实验台，不再经过任务/闭环页 */
async function resolveEntry() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await studentExperimentApi.listProgress()
    const codes = (data || []).map((row) => row.experimentCode).filter(Boolean)
    if (!codes.length) return

    const remembered = lastExperiment()
    const code = codes.includes(remembered) ? remembered : codes[0]
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
