<template>
  <div class="page-root flex flex-col flex-1 min-h-0 overflow-y-auto custom-scroll">
    <div class="home-page">
      <section class="home-intro">
        <h2 class="intro-title">我的实验</h2>
        <p class="intro-desc">
          教师分配的实验会出现在这里。建议流程：实验工作台完成操作 → 数据分析处理测量结果 → 报告助手撰写报告 → 实验复盘总结提升。
        </p>
      </section>

      <div v-if="loading" class="py-16 text-center text-sm text-ink-faint">加载中…</div>
      <div v-else-if="!items.length" class="empty-box">暂无分配的实验，请联系教师分配后再进入。</div>

      <div v-else class="exp-list">
        <article v-for="item in items" :key="item.experimentCode" class="exp-card">
          <div class="exp-head">
            <div>
              <h3 class="exp-name">{{ item.experimentName }}</h3>
              <p class="exp-status">{{ statusText(item) }}</p>
            </div>
            <router-link :to="stepRoute('lab', item.experimentCode)" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold">
              {{ item.activeSessionId ? '继续实验' : '开始实验' }}
            </router-link>
          </div>

          <div class="flow-row">
            <router-link
              v-for="step in item.steps"
              :key="step.key"
              :to="stepRoute(step.key, item.experimentCode)"
              class="flow-chip"
              :class="chipClass(step)"
            >
              {{ step.label }}
            </router-link>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { studentExperimentApi } from '../api'
import { stepRoute } from '../utils/studentFlow'

const items = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await studentExperimentApi.listProgress()
    items.value = data || []
  } finally {
    loading.value = false
  }
})

function statusText(item) {
  if (item.activeSessionId) return '实验进行中'
  if (item.finishedSessionId) return '已完成一次实验，可撰写报告与复盘'
  return '尚未开始'
}

function chipClass(step) {
  return {
    'flow-chip--done': step.status === 'done',
    'flow-chip--progress': step.status === 'in_progress'
  }
}
</script>

<style scoped>
.home-page { @apply max-w-[960px] mx-auto w-full px-6 py-6 pb-10; }
.home-intro { @apply mb-6; }
.intro-title { @apply text-[18px] font-bold text-ink-strong; }
.intro-desc { @apply mt-2 text-[14px] text-ink-muted leading-relaxed; }
.empty-box { @apply rounded-2xl border border-line-soft bg-white p-10 text-[14px] text-ink-faint text-center; }
.exp-list { @apply space-y-4; }
.exp-card { @apply rounded-2xl border border-line-soft bg-white p-5; }
.exp-head { @apply flex items-start justify-between gap-4 flex-wrap; }
.exp-name { @apply text-[17px] font-bold text-ink-strong; }
.exp-status { @apply mt-1 text-[13px] text-ink-muted; }
.flow-row { @apply flex flex-wrap gap-2 mt-4; }
.flow-chip {
  @apply inline-flex items-center px-3 py-1.5 rounded-lg text-[13px] font-medium
    border border-line-soft bg-white text-ink-muted hover:border-brand-200 hover:text-brand-700 transition-colors;
}
.flow-chip--done {
  @apply border-emerald-200 bg-emerald-50 text-emerald-800;
}
.flow-chip--progress {
  @apply border-amber-200 bg-amber-50 text-amber-900;
}
</style>
