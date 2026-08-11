<template>
  <div class="profile-page flex-1 min-h-0 overflow-y-auto custom-scroll">
    <div class="profile-inner">
      <header class="profile-header">
        <div class="avatar brand-gradient">{{ initial }}</div>
        <div class="min-w-0">
          <h1 class="profile-name">{{ auth.displayName || auth.username }}</h1>
          <p class="profile-meta">
            {{ auth.studentClass || '未分班' }} · 学号 {{ auth.username }}
          </p>
        </div>
      </header>

      <nav class="sub-tabs">
        <button
          v-for="t in TABS"
          :key="t.key"
          type="button"
          class="sub-tab"
          :class="{ 'sub-tab--active': activeTab === t.key }"
          @click="activeTab = t.key"
        >
          {{ t.label }}
        </button>
      </nav>

      <!-- 概览 -->
      <template v-if="activeTab === 'overview'">
        <section class="stat-grid">
          <div v-for="s in stats" :key="s.label" class="stat-card">
            <span class="stat-num">{{ s.value }}</span>
            <span class="stat-label">{{ s.label }}</span>
          </div>
        </section>

        <section class="two-col">
          <div class="panel">
            <h2 class="panel-title">能力画像</h2>
            <p class="panel-sub">依据你在实验中的纠错、校验与求助记录自动计算，仅供自我参考。</p>
            <div v-if="hasCapabilityData" class="radar-wrap">
              <svg :viewBox="`0 0 ${RADAR} ${RADAR}`" class="radar-svg">
                <polygon
                  v-for="ring in [1, 0.75, 0.5, 0.25]"
                  :key="ring"
                  :points="ringPoints(ring)"
                  class="radar-ring"
                />
                <line v-for="(p, i) in axisPoints" :key="`ax-${i}`" :x1="center" :y1="center" :x2="p.x" :y2="p.y" class="radar-axis" />
                <polygon :points="valuePoints" class="radar-area" />
                <circle v-for="(p, i) in valueDots" :key="`dot-${i}`" :cx="p.x" :cy="p.y" r="3" class="radar-dot" />
                <text
                  v-for="(p, i) in labelPoints"
                  :key="`lb-${i}`"
                  :x="p.x"
                  :y="p.y"
                  :text-anchor="p.anchor"
                  class="radar-label"
                >
                  {{ capabilities[i].label }}
                </text>
              </svg>
            </div>
            <p v-else class="empty-tip">完成一次实验后就会生成能力画像。</p>

            <ul v-if="hasCapabilityData" class="cap-list">
              <li v-for="c in capabilities" :key="c.key">
                <span class="cap-name">{{ c.label }}</span>
                <div class="cap-bar"><span :style="{ width: c.score + '%' }" /></div>
                <span class="cap-score">{{ c.score }}</span>
                <span class="cap-hint">{{ c.hint }}</span>
              </li>
            </ul>
          </div>

          <div class="panel">
            <h2 class="panel-title">近期实验</h2>
            <ol v-if="timeline.length" class="timeline">
              <li v-for="item in timeline" :key="item.sessionId">
                <span class="tl-dot" :class="item.status === 'FINISHED' ? 'tl-dot--done' : 'tl-dot--active'" />
                <div class="min-w-0">
                  <p class="tl-title">{{ item.experimentName }}</p>
                  <p class="tl-meta">
                    {{ item.startTime }} · {{ item.status === 'FINISHED' ? '已完成' : '进行中' }}
                    · 数据 {{ item.dataSubmitCount }} 次
                    <span v-if="item.correctionCount"> · 纠错 {{ item.correctionCount }} 次</span>
                  </p>
                </div>
              </li>
            </ol>
            <p v-else class="empty-tip">还没有实验记录。</p>
          </div>
        </section>
      </template>

      <!-- 我的实验 -->
      <template v-else-if="activeTab === 'experiments'">
        <section v-if="progressList.length" class="exp-grid">
          <article v-for="exp in progressList" :key="exp.experimentCode" class="panel">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <h2 class="panel-title mb-1">{{ exp.experimentName }}</h2>
                <p class="panel-sub">当前进度：{{ exp.currentStep || '未开始' }}</p>
              </div>
              <router-link
                :to="{ name: 'lab', query: { exp: exp.experimentCode } }"
                class="btn-brand px-4 py-2 rounded-xl text-[13px] font-semibold shrink-0"
              >
                {{ exp.activeSessionId ? '继续实验' : '开始实验' }}
              </router-link>
            </div>
            <ol v-if="exp.steps?.length" class="step-flow">
              <li v-for="step in exp.steps" :key="step.key" class="step-node" :class="`step-node--${step.status}`">
                <span class="step-mark">{{ step.status === 'done' ? '✓' : '' }}</span>
                <span class="step-text">{{ step.label }}</span>
              </li>
            </ol>
          </article>
        </section>
        <p v-else class="empty-panel">教师还没有给你分配实验。</p>
      </template>

      <!-- 报告存档 -->
      <template v-else-if="activeTab === 'reports'">
        <section v-if="finishedSessions.length" class="panel">
          <table class="archive-table">
            <thead>
              <tr>
                <th>实验</th>
                <th>完成时间</th>
                <th>求助</th>
                <th>纠错</th>
                <th class="text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in finishedSessions" :key="s.sessionId">
                <td class="font-medium text-ink-strong">{{ s.experimentName }}</td>
                <td>{{ formatSessionTime(s.endTime) }}</td>
                <td>{{ s.helpCount ?? 0 }}</td>
                <td>{{ s.errorPointCount ?? 0 }}</td>
                <td class="text-right whitespace-nowrap">
                  <button type="button" class="link-btn" :disabled="downloadingId === s.sessionId" @click="downloadReport(s)">
                    {{ downloadingId === s.sessionId ? '下载中…' : '下载 Word' }}
                  </button>
                  <router-link
                    :to="{ name: 'after-report', params: { code: s.experimentCode } }"
                    class="link-btn ml-3"
                  >
                    继续撰写
                  </router-link>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
        <p v-else class="empty-panel">完成并结束一次实验后，报告会归档到这里。</p>
      </template>

      <!-- 问答记录 -->
      <template v-else>
        <section v-if="conversations.length" class="panel">
          <ul class="conv-list">
            <li v-for="c in conversations" :key="c.id">
              <div class="conv-item">
                <span class="conv-title">{{ c.title || '未命名对话' }}</span>
                <span class="conv-time">{{ formatSessionTime(c.updatedAt) }}</span>
              </div>
            </li>
          </ul>
        </section>
        <p v-else class="empty-panel">还没有问答记录，去「原理答疑」问第一个问题吧。</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { aiToolApi, sessionApi, studentExperimentApi } from '../api'
import { useAuthStore } from '../stores/auth'
import { formatSessionTime } from '../utils/studentFlow'

const TABS = [
  { key: 'overview', label: '概览' },
  { key: 'experiments', label: '我的实验' },
  { key: 'reports', label: '报告存档' },
  { key: 'qa', label: '问答记录' }
]

const RADAR = 260
const center = RADAR / 2
const radius = 88

const auth = useAuthStore()
const activeTab = ref('overview')
const summary = ref(null)
const progressList = ref([])
const finishedSessions = ref([])
const conversations = ref([])
const downloadingId = ref(null)

const initial = computed(() => {
  const n = (auth.displayName || auth.username || '').trim()
  return n ? n.charAt(0) : '学'
})

const capabilities = computed(() => summary.value?.capabilities || [])
const timeline = computed(() => summary.value?.timeline || [])
const hasCapabilityData = computed(
  () => capabilities.value.length >= 3 && (summary.value?.startedCount || 0) > 0
)

const stats = computed(() => {
  const s = summary.value || {}
  return [
    { label: '分配实验', value: s.assignedCount ?? 0 },
    { label: '完成实验', value: s.finishedCount ?? 0 },
    { label: '提交数据', value: s.dataSubmitCount ?? 0 },
    { label: '实验时长', value: `${Math.round((s.labMinutes ?? 0) / 6) / 10} h` }
  ]
})

function pointAt(index, ratio) {
  const total = Math.max(1, capabilities.value.length)
  const angle = (Math.PI * 2 * index) / total - Math.PI / 2
  return {
    x: center + Math.cos(angle) * radius * ratio,
    y: center + Math.sin(angle) * radius * ratio
  }
}

const axisPoints = computed(() => capabilities.value.map((_, i) => pointAt(i, 1)))
const valueDots = computed(() =>
  capabilities.value.map((c, i) => pointAt(i, Math.max(0.04, c.score / 100)))
)
const valuePoints = computed(() => valueDots.value.map((p) => `${p.x},${p.y}`).join(' '))
const labelPoints = computed(() =>
  capabilities.value.map((_, i) => {
    const p = pointAt(i, 1.22)
    const anchor = p.x > center + 5 ? 'start' : p.x < center - 5 ? 'end' : 'middle'
    return { x: p.x, y: p.y + 4, anchor }
  })
)

function ringPoints(ratio) {
  return capabilities.value
    .map((_, i) => {
      const p = pointAt(i, ratio)
      return `${p.x},${p.y}`
    })
    .join(' ')
}

onMounted(async () => {
  const [summaryRes, progressRes, sessionRes, convRes] = await Promise.allSettled([
    studentExperimentApi.profileSummary(),
    studentExperimentApi.listProgress(),
    aiToolApi.recapSessions(),
    aiToolApi.conversations('explore')
  ])
  if (summaryRes.status === 'fulfilled') summary.value = summaryRes.value.data
  if (progressRes.status === 'fulfilled') progressList.value = progressRes.value.data || []
  if (sessionRes.status === 'fulfilled') finishedSessions.value = sessionRes.value.data || []
  if (convRes.status === 'fulfilled') conversations.value = convRes.value.data || []
})

async function downloadReport(session) {
  downloadingId.value = session.sessionId
  try {
    const { data } = await sessionApi.reportDocx(session.sessionId)
    const url = URL.createObjectURL(data)
    const a = document.createElement('a')
    a.href = url
    a.download = `${session.experimentName || '实验报告'}.docx`
    a.click()
    URL.revokeObjectURL(url)
  } finally {
    downloadingId.value = null
  }
}
</script>

<style scoped>
.profile-page { @apply bg-surface-soft/40; }
.profile-inner { @apply max-w-[1080px] mx-auto w-full px-6 py-7 pb-12; }

.profile-header { @apply flex items-center gap-4 mb-6; }
.avatar { @apply w-14 h-14 rounded-2xl flex items-center justify-center text-white text-xl font-bold shrink-0; }
.profile-name { @apply text-[20px] font-bold text-ink-strong; }
.profile-meta { @apply text-[13px] text-ink-muted mt-1; }

.sub-tabs { @apply flex gap-1 border-b border-line-soft mb-5; }
.sub-tab {
  @apply relative px-4 py-2.5 text-[14px] text-ink-muted hover:text-ink-strong transition-colors;
}
.sub-tab--active { @apply text-brand-600 font-semibold; }
.sub-tab--active::after {
  content: '';
  @apply absolute bottom-0 left-3 right-3 h-0.5 bg-brand-600 rounded-full;
}

.stat-grid { @apply grid grid-cols-2 md:grid-cols-4 gap-4 mb-5; }
.stat-card { @apply flex flex-col rounded-2xl border border-line-soft bg-white p-4; }
.stat-num { @apply text-[24px] font-bold text-ink-strong leading-none; }
.stat-label { @apply text-[12.5px] text-ink-muted mt-2; }

.two-col { @apply grid grid-cols-1 lg:grid-cols-2 gap-5; }
.panel { @apply rounded-2xl border border-line-soft bg-white p-5; }
.panel-title { @apply text-[15px] font-bold text-ink-strong; }
.panel-sub { @apply text-[12.5px] text-ink-muted mt-1 mb-3 leading-relaxed; }
.empty-tip { @apply text-[13px] text-ink-faint py-6 text-center; }
.empty-panel { @apply rounded-2xl border border-dashed border-line-soft bg-white p-10 text-center text-[14px] text-ink-muted; }

.radar-wrap { @apply flex justify-center; }
.radar-svg { @apply w-full max-w-[300px]; }
.radar-ring { @apply fill-none stroke-line-soft; stroke-width: 1; }
.radar-axis { @apply stroke-line-soft; stroke-width: 1; }
.radar-area { fill: rgba(59, 130, 246, 0.18); stroke: #3b82f6; stroke-width: 2; }
.radar-dot { fill: #3b82f6; }
.radar-label { @apply fill-ink-muted; font-size: 11px; }

.cap-list { @apply mt-4 space-y-2.5; }
.cap-list li { @apply grid grid-cols-[70px_1fr_28px] gap-2 items-center text-[12.5px]; }
.cap-name { @apply text-ink-base; }
.cap-bar { @apply h-1.5 rounded-full bg-surface-muted overflow-hidden; }
.cap-bar span { @apply block h-full bg-brand-500 rounded-full; }
.cap-score { @apply text-right font-semibold text-ink-strong font-mono; }
.cap-hint { @apply col-span-3 text-[11.5px] text-ink-faint -mt-1.5; }

.timeline { @apply space-y-3; }
.timeline li { @apply flex gap-3; }
.tl-dot { @apply w-2.5 h-2.5 rounded-full shrink-0 mt-1.5; }
.tl-dot--done { @apply bg-emerald-500; }
.tl-dot--active { @apply bg-amber-500; }
.tl-title { @apply text-[14px] font-semibold text-ink-strong; }
.tl-meta { @apply text-[12px] text-ink-muted mt-0.5; }

.exp-grid { @apply grid grid-cols-1 lg:grid-cols-2 gap-5; }
.step-flow { @apply flex flex-wrap gap-2 mt-4; }
.step-node {
  @apply flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[12px] border border-line-soft bg-surface-soft text-ink-muted;
}
.step-node--done { @apply border-emerald-200 bg-emerald-50 text-emerald-700; }
.step-node--active { @apply border-brand-300 bg-brand-50 text-brand-700 font-semibold; }
.step-mark { @apply text-[11px] font-bold; }

.archive-table { @apply w-full text-[13px] text-left; }
.archive-table th { @apply px-3 py-2 text-ink-muted font-semibold border-b border-line-soft; }
.archive-table td { @apply px-3 py-3 border-b border-line-soft/60 text-ink-base; }
.link-btn { @apply text-[13px] text-brand-600 hover:underline disabled:opacity-50; }

.conv-list { @apply divide-y divide-line-soft/60; }
.conv-item { @apply flex items-center justify-between gap-4 py-3 hover:text-brand-600 transition-colors; }
.conv-title { @apply text-[14px] text-ink-base truncate; }
.conv-time { @apply text-[12px] text-ink-faint shrink-0; }
</style>
