<template>
  <div class="page flex-1 min-h-0 overflow-y-auto custom-scroll">
    <div class="inner">
      <header class="page-head">
        <div class="min-w-0">
          <h1 class="page-title">我的实验</h1>
          <p class="page-lead">
            每个实验都按「预习 → 操作 → 数据 → 报告」四步推进，AI 在每一步就近提供帮助。
          </p>
        </div>
        <router-link to="/files" class="btn-ghost px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0">
          查看实验资料
        </router-link>
      </header>

      <div v-if="loading" class="state-box">正在加载实验列表…</div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>
      <div v-else-if="!items.length" class="state-box">
        暂无已分配的实验。请联系任课教师为你分配实验后再进入。
      </div>

      <ul v-else class="exp-list">
        <li v-for="item in experimentItems" :key="item.experimentCode" class="exp-card">
          <div class="card-head">
            <div class="min-w-0">
              <h2 class="exp-name">{{ item.experimentName }}</h2>
              <p class="exp-meta">
                {{ statusSummary(item) }}
                <span v-if="item.fileCount" class="meta-dot">·</span>
                <span v-if="item.fileCount">{{ item.fileCount }} 份资料</span>
              </p>
            </div>
            <router-link :to="primaryRoute(item)" class="btn-brand px-5 py-2 rounded-xl text-[13px] font-semibold shrink-0">
              {{ primaryLabel(item) }}
            </router-link>
          </div>

          <div v-if="item.finishedSessionId" class="mb-4 flex flex-wrap items-center gap-3">
            <ProcessRecordDownload :session-id="item.finishedSessionId" />
            <router-link :to="{ name: 'after-center', params: { code: item.experimentCode } }" class="text-[13px] text-brand-600">实验资料与报告</router-link>
          </div>
          <ol class="stage-rail">
            <li v-for="(stage, index) in STAGES" :key="stage.key" class="rail-item">
              <router-link :to="stage.route(item.experimentCode)" class="rail-link" :class="`rail-link--${statusOf(item, stage.key)}`">
                <span class="rail-dot" :class="`rail-dot--${statusOf(item, stage.key)}`">
                  <template v-if="statusOf(item, stage.key) === 'done'">✓</template>
                  <template v-else>{{ index + 1 }}</template>
                </span>
                <span class="rail-text">
                  <span class="rail-label">{{ stage.label }}</span>
                  <span class="rail-lead">{{ stage.lead }}</span>
                </span>
              </router-link>
              <span v-if="index < STAGES.length - 1" class="rail-line" :class="{ 'rail-line--done': statusOf(item, stage.key) === 'done' }" />
            </li>
          </ol>
        </li>
      </ul>
      <ListPagination v-model:page="experimentPage" v-model:page-size="experimentSize" :total="experimentTotal" />
    </div>
  </div>
</template>

<script setup>
import ListPagination from '../../components/common/ListPagination.vue'
import ProcessRecordDownload from '../../components/experiment/ProcessRecordDownload.vue'
import { usePagination } from '../../composables/usePagination'
import { onMounted, ref } from 'vue'
import { studentExperimentApi, studentFileApi } from '../../api'
import { STAGES, stageStatus } from '../../utils/experimentFlow'

const items = ref([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const { data } = await studentExperimentApi.listProgress()
    items.value = (data || []).map((row) => ({ ...row, fileCount: 0 }))
  } catch (e) {
    error.value = e.response?.data?.message || '实验列表加载失败，请确认后端已启动'
  } finally {
    loading.value = false
  }

  try {
    const { data } = await studentFileApi.list()
    const counts = {}
    ;(data || []).forEach((file) => {
      if (file.experimentCode) counts[file.experimentCode] = (counts[file.experimentCode] || 0) + 1
    })
    items.value = items.value.map((row) => ({ ...row, fileCount: counts[row.experimentCode] || 0 }))
  } catch {
    // 资料统计失败不影响主流程
  }
})

function statusOf(item, stageKey) {
  return stageStatus(item, stageKey)
}

function statusSummary(item) {
  const done = STAGES.filter((s) => statusOf(item, s.key) === 'done').length
  if (done === 0) return '尚未开始'
  if (done >= STAGES.length) return '全部完成'
  return `已完成 ${done}/${STAGES.length} 个阶段`
}

function nextStage(item) {
  return STAGES.find((s) => statusOf(item, s.key) !== 'done') || STAGES[STAGES.length - 1]
}

function primaryRoute(item) {
  return nextStage(item).route(item.experimentCode)
}

function primaryLabel(item) {
  const stage = nextStage(item)
  const started = STAGES.some((s) => statusOf(item, s.key) === 'done')
  return started ? `继续：${stage.label}` : '开始预习'
}
const { page: experimentPage, pageSize: experimentSize, total: experimentTotal, items: experimentItems } = usePagination(items)
</script>

<style scoped>
.page { @apply bg-surface-soft/40; }
.inner { @apply max-w-[1000px] mx-auto w-full px-6 py-7 pb-12; }

.page-head { @apply flex flex-wrap items-end justify-between gap-4 mb-6; }
.page-title { @apply text-[22px] font-bold text-ink-strong; }
.page-lead { @apply text-[14px] text-ink-muted mt-1.5; }

.state-box { @apply rounded-2xl border border-line-soft bg-white px-5 py-8 text-center text-[14px] text-ink-muted; }
.state-box--error { @apply text-rose-600 border-rose-100 bg-rose-50/50; }

.exp-list { @apply space-y-4; }
.exp-card { @apply rounded-2xl border border-line-soft bg-white p-5; }
.card-head { @apply flex flex-wrap items-start justify-between gap-3 mb-5; }
.exp-name { @apply text-[17px] font-bold text-ink-strong; }
.exp-meta { @apply text-[12.5px] text-ink-muted mt-1; }
.meta-dot { @apply mx-1.5 text-ink-faint; }

.stage-rail { @apply grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2 relative; }
.rail-item { @apply relative flex items-center; }
.rail-link {
  @apply flex items-start gap-3 rounded-xl border border-line-soft bg-surface-soft/50 px-3 py-3 flex-1 min-w-0
    transition-all hover:border-brand-300 hover:bg-white;
}
.rail-link--done { @apply border-emerald-100 bg-emerald-50/40; }
.rail-link--in_progress { @apply border-brand-300 bg-brand-50/60; }
.rail-dot {
  @apply w-7 h-7 rounded-full flex items-center justify-center text-[12px] font-bold shrink-0
    bg-white border border-line-soft text-ink-muted;
}
.rail-dot--done { @apply bg-emerald-500 border-emerald-500 text-white; }
.rail-dot--in_progress { @apply bg-brand-600 border-brand-600 text-white; }
.rail-text { @apply min-w-0; }
.rail-label { @apply block text-[13.5px] font-bold text-ink-strong; }
.rail-lead { @apply block text-[11.5px] text-ink-muted leading-snug mt-0.5; }
.rail-line { @apply hidden lg:block absolute -right-1 top-1/2 w-2 h-px bg-line-soft; }
.rail-line--done { @apply bg-emerald-300; }
</style>
