<template>
  <div class="prep-page flex-1 min-h-0 flex flex-col bg-surface-soft/40">
    <header class="prep-head shrink-0">
      <div class="prep-head-inner">
        <div class="min-w-0">
          <p class="eyebrow">进门就绪</p>
          <h1 class="title">{{ experimentName || code }}</h1>
          <p class="sub">
            先看要点、再做短自测。通过后标记就绪，再进实验台。
            <span v-if="preLabCompleted" class="ready-pill">已就绪</span>
          </p>
        </div>
        <div class="head-actions">
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold border border-line-soft" @click="goLab">
            {{ preLabCompleted ? '进入实验台' : '稍后自测，先去实验台' }}
          </button>
        </div>
      </div>
      <nav class="prep-tabs" aria-label="预习">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          class="prep-tab"
          :class="{ 'prep-tab--active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </header>

    <div class="prep-body flex-1 min-h-0 overflow-hidden bg-white">
      <BriefPanel
        v-show="activeTab === 'brief'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
        @next="activeTab = 'quiz'"
      />
      <QuizPanel
        v-show="activeTab === 'quiz'"
        class="h-full"
        :experiment-code="code"
        :experiment-name="experimentName"
        @ready="onQuizReady"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { experimentApi, studentExperimentApi } from '../../api'
import BriefPanel from '../../components/stage/BriefPanel.vue'
import QuizPanel from '../../components/stage/QuizPanel.vue'
import { rememberVisit } from '../../utils/experimentFlow'

const route = useRoute()
const router = useRouter()
const code = computed(() => String(route.params.code || '').trim())
const experimentName = ref('')
const preLabCompleted = ref(false)
const activeTab = ref('brief')

const tabs = [
  { key: 'brief', label: '预习要点' },
  { key: 'quiz', label: '预习自测' }
]

onMounted(load)
watch(code, load)

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
    preLabCompleted.value = !!prog?.preLabCompleted
  } catch {
    experimentName.value = code.value
  }
}

function onQuizReady() {
  preLabCompleted.value = true
}

function goLab() {
  router.push({ name: 'lab', query: { exp: code.value } })
}
</script>

<style scoped>
.prep-head { @apply bg-white border-b border-line-soft; }
.prep-head-inner {
  @apply max-w-5xl mx-auto px-5 md:px-8 pt-5 pb-3 flex flex-wrap items-start justify-between gap-4;
}
.eyebrow { @apply text-[11px] font-bold uppercase tracking-wide text-brand-600; }
.title { @apply text-[20px] font-bold text-ink-strong mt-0.5; }
.sub { @apply text-[13px] text-ink-muted mt-1 flex flex-wrap items-center gap-2; }
.ready-pill {
  @apply inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold
    bg-emerald-50 text-emerald-700 border border-emerald-100;
}
.head-actions { @apply shrink-0; }
.prep-tabs {
  @apply max-w-5xl mx-auto px-5 md:px-8 flex gap-1 pb-0;
}
.prep-tab {
  @apply px-4 py-2.5 text-[13px] font-semibold text-ink-muted border-b-2 border-transparent
    hover:text-ink-strong transition-colors;
}
.prep-tab--active { @apply text-brand-700 border-brand-600; }
.prep-body { @apply max-w-5xl w-full mx-auto; }
</style>
