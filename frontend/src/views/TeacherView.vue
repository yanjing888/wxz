<template>
  <div class="teacher-shell min-h-screen bg-bg-base flex flex-col">
    <AppTopBar :center="scopeLabel" @logout="logout">
      <nav class="teacher-nav">
        <div class="teacher-tabs">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            class="teacher-tab"
            :class="activeTab === item.key ? 'teacher-tab--active' : ''"
            @click="switchTab(item.key)"
          >
            {{ item.label }}
          </button>
        </div>
      </nav>
    </AppTopBar>

    <main class="flex-1 min-h-0 overflow-y-auto custom-scroll bg-white">
      <div class="teacher-page py-8 space-y-8">
        <div v-if="loading" class="py-16 text-sm text-ink-faint text-center">加载中…</div>

        <!-- 概览 -->
        <template v-else-if="activeTab === 'overview'">
          <section>
            <div class="section-head-row">
              <h2 class="section-head">数据概览</h2>
            </div>
            <div class="stat-grid">
              <div v-for="card in overviewCards" :key="card.label" class="stat-item">
                <p class="stat-label">{{ card.label }}</p>
                <p class="stat-value tabular-nums" :class="card.tone">{{ card.value }}</p>
              </div>
            </div>
          </section>

          <section>
            <div class="section-head-row">
              <h2 class="section-head">最近报告</h2>
              <button v-if="reports.length" type="button" class="link-btn" @click="switchTab('reports')">查看全部 {{ reports.length }} 份</button>
            </div>
            <div v-if="!recentReports.length" class="empty-block">还没有学生提交报告。</div>
            <table v-else class="teacher-table">
              <thead>
                <tr>
                  <th class="w-12">#</th>
                  <th>学生</th>
                  <th>班级</th>
                  <th>实验</th>
                  <th class="w-20 text-center">问答</th>
                  <th class="w-20 text-center">纠错</th>
                  <th>完成时间</th>
                  <th class="text-right w-28">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in recentReports" :key="item.sessionId">
                  <td class="text-ink-faint tabular-nums">{{ index + 1 }}</td>
                  <td class="font-semibold text-ink-strong">{{ item.studentName || '—' }}</td>
                  <td>{{ item.studentClass || '—' }}</td>
                  <td>{{ item.experimentName }}</td>
                  <td class="text-center tabular-nums">{{ item.helpCount ?? 0 }}</td>
                  <td class="text-center tabular-nums">{{ item.errorPointCount ?? 0 }}</td>
                  <td class="text-ink-muted tabular-nums">{{ formatTime(item.endTime || item.startTime) }}</td>
                  <td class="text-right whitespace-nowrap">
                    <button type="button" class="link-btn" @click="openReport(item.sessionId)">查看</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </section>

          <section>
            <div class="section-head-row">
              <h2 class="section-head">待处理反馈</h2>
              <button v-if="pendingFeedback.length" type="button" class="link-btn" @click="switchTab('feedback')">查看全部</button>
            </div>
            <div v-if="!recentPendingFeedback.length" class="empty-block">暂无待处理的学生评价。</div>
            <table v-else class="teacher-table">
              <thead>
                <tr>
                  <th class="w-36">时间</th>
                  <th class="w-24">学生</th>
                  <th class="w-32">实验</th>
                  <th class="w-16">步骤</th>
                  <th class="w-20">评价</th>
                  <th>问题摘要</th>
                  <th class="text-right w-28">操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="item in recentPendingFeedback" :key="item.id">
                  <tr>
                    <td class="text-ink-muted tabular-nums">{{ formatTime(item.createdAt) }}</td>
                    <td class="font-semibold text-ink-strong">{{ item.studentName }}</td>
                    <td>{{ item.experimentName }}</td>
                    <td class="tabular-nums">{{ item.stepId }}</td>
                    <td>
                      <span class="rating-tag" :class="item.rating === 'HELPFUL' ? 'rating-tag--ok' : 'rating-tag--bad'">
                        {{ item.rating === 'HELPFUL' ? '有帮助' : '无帮助' }}
                      </span>
                    </td>
                    <td><p class="line-clamp-2 leading-relaxed" :title="item.userQuestion">{{ item.userQuestion || '—' }}</p></td>
                    <td class="text-right whitespace-nowrap">
                      <button type="button" class="link-btn" @click="markProcessed(item.id)">已处理</button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </section>
        </template>

        <!-- 实验报告 -->
        <section v-else-if="activeTab === 'reports'" class="space-y-5">
          <p v-if="reports.length" class="text-[13px] text-ink-faint">共 {{ reports.length }} 份</p>
          <div v-if="!reports.length" class="empty-block">暂无报告。学生结束实验并生成报告后会出现在这里。</div>
          <table v-else class="teacher-table">
            <thead>
              <tr>
                <th class="w-12">#</th>
                <th>学生</th>
                <th>班级</th>
                <th>实验</th>
                <th class="w-20 text-center">问答</th>
                <th class="w-20 text-center">纠错</th>
                <th>开始时间</th>
                <th>完成时间</th>
                <th class="text-right w-36">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in reports" :key="item.sessionId">
                <td class="text-ink-faint tabular-nums">{{ index + 1 }}</td>
                <td class="font-semibold text-ink-strong">{{ item.studentName || '—' }}</td>
                <td>{{ item.studentClass || '—' }}</td>
                <td>{{ item.experimentName }}</td>
                <td class="text-center tabular-nums">{{ item.helpCount ?? 0 }}</td>
                <td class="text-center tabular-nums">{{ item.errorPointCount ?? 0 }}</td>
                <td class="text-ink-muted tabular-nums">{{ formatTime(item.startTime) }}</td>
                <td class="text-ink-muted tabular-nums">{{ formatTime(item.endTime || item.startTime) }}</td>
                <td class="text-right whitespace-nowrap">
                  <button type="button" class="link-btn" @click="openReport(item.sessionId)">查看</button>
                  <span class="text-line-strong mx-2">|</span>
                  <button type="button" class="link-btn" @click="downloadReport(item.sessionId, item.experimentName)">下载</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- 问答反馈 -->
        <section v-else-if="activeTab === 'feedback'" class="space-y-5">
          <div class="flex items-center justify-between gap-4 flex-wrap">
            <div class="flex items-center gap-2">
            <button
              v-for="filter in feedbackFilters"
              :key="filter.key"
              type="button"
              class="filter-chip"
              :class="feedbackFilter === filter.key ? 'filter-chip--active' : ''"
              @click="setFeedbackFilter(filter.key)"
            >
              {{ filter.label }}
            </button>
            </div>
            <span v-if="feedbackList.length" class="text-[13px] text-ink-faint">共 {{ feedbackList.length }} 条</span>
          </div>
          <div v-if="!feedbackList.length" class="empty-block">暂无学生评价。</div>
          <table v-else class="teacher-table">
            <thead>
              <tr>
                <th class="w-12">#</th>
                <th class="w-36">时间</th>
                <th class="w-24">学生</th>
                <th class="w-24">班级</th>
                <th>实验</th>
                <th class="w-16">步骤</th>
                <th class="w-20">评价</th>
                <th>问题</th>
                <th class="text-right w-36">操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="(item, index) in feedbackList" :key="item.id">
                <tr>
                  <td class="text-ink-faint tabular-nums">{{ index + 1 }}</td>
                  <td class="text-ink-muted tabular-nums">{{ formatTime(item.createdAt) }}</td>
                  <td class="font-semibold text-ink-strong">{{ item.studentName }}</td>
                  <td>{{ item.studentClass || '—' }}</td>
                  <td>{{ item.experimentName }}</td>
                  <td class="tabular-nums">{{ item.stepId }}</td>
                  <td>
                    <span class="rating-tag" :class="item.rating === 'HELPFUL' ? 'rating-tag--ok' : 'rating-tag--bad'">
                      {{ item.rating === 'HELPFUL' ? '有帮助' : '无帮助' }}
                    </span>
                  </td>
                  <td><p class="line-clamp-2 leading-relaxed" :title="item.userQuestion">{{ item.userQuestion || '—' }}</p></td>
                  <td class="text-right whitespace-nowrap">
                    <button type="button" class="link-btn" @click="toggleFeedbackDetail(item.id)">
                      {{ expandedFeedbackId === item.id ? '收起' : '详情' }}
                    </button>
                    <template v-if="!item.processed">
                      <span class="text-line-strong mx-2">|</span>
                      <button type="button" class="link-btn" @click="markProcessed(item.id)">已处理</button>
                    </template>
                    <span v-else class="ml-2 text-[12px] text-ink-faint">已处理</span>
                  </td>
                </tr>
                <tr v-if="expandedFeedbackId === item.id" class="detail-row">
                  <td colspan="9">
                    <div class="detail-block">
                      <p><span class="detail-label">学生问题</span>{{ item.userQuestion || '—' }}</p>
                      <p class="mt-3"><span class="detail-label">AI 回复</span><span class="whitespace-pre-wrap leading-relaxed">{{ plainText(item.aiReply) }}</span></p>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </section>

        <!-- 班级管理 -->
        <section v-else-if="activeTab === 'students'" class="space-y-6">
          <div class="import-bar">
            <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" @click="downloadImportTemplate">
              下载模板
            </button>
            <button
              type="button"
              class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold"
              :disabled="importing"
              @click="triggerImport"
            >
              {{ importing ? '导入中…' : '导入名单' }}
            </button>
            <input
              ref="importFileInput"
              type="file"
              accept=".csv,text/csv"
              class="hidden"
              @change="onImportFileChange"
            />
          </div>

          <div v-if="students.length" class="flex flex-wrap items-center gap-3">
            <p class="text-[13px] text-ink-faint flex-1">共 {{ students.length }} 人</p>
            <button
              v-if="selectedStudentIds.length"
              type="button"
              class="btn-ghost px-3 py-2 rounded-xl text-xs font-semibold"
              @click="openBulkAssign"
            >
              批量分配（{{ selectedStudentIds.length }}）
            </button>
          </div>
          <div v-if="!students.length" class="empty-block">暂无学生</div>
          <table v-else class="teacher-table">
            <thead>
              <tr>
                <th class="w-10">
                  <input type="checkbox" :checked="allStudentsSelected" @change="toggleSelectAll" />
                </th>
                <th class="w-12">#</th>
                <th class="w-32">姓名</th>
                <th>账号</th>
                <th class="w-36">班级</th>
                <th>已分配实验</th>
                <th class="text-right w-28">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(student, index) in students" :key="student.userId">
                <td>
                  <input type="checkbox" :value="student.userId" v-model="selectedStudentIds" />
                </td>
                <td class="text-ink-faint tabular-nums">{{ index + 1 }}</td>
                <td class="font-semibold text-ink-strong">{{ student.displayName }}</td>
                <td>{{ student.username }}</td>
                <td>{{ student.studentClass || '—' }}</td>
                <td>
                  <span v-if="!(student.assignedExperimentNames || []).length" class="text-ink-faint">未分配</span>
                  <span v-else>{{ (student.assignedExperimentNames || []).join('、') }}</span>
                </td>
                <td class="text-right whitespace-nowrap">
                  <button type="button" class="link-btn" @click="openAssign(student)">分配实验</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </main>

    <ReportModal
      :visible="reportVisible"
      :report="selectedReport"
      :downloading="downloadingReport"
      @close="reportVisible = false"
      @download-docx="downloadSelectedReport"
    />

    <div v-if="assignVisible" class="assign-overlay" @click.self="closeAssign">
      <div class="assign-modal">
        <h3 class="assign-title">{{ assignBulkMode ? `批量分配（${selectedStudentIds.length} 人）` : `为 ${assignTarget?.displayName || ''} 分配实验` }}</h3>
        <div v-if="!allExperiments.length" class="empty-block py-6">加载实验列表…</div>
        <div v-else class="assign-list custom-scroll">
          <label v-for="exp in allExperiments" :key="exp.code" class="assign-item">
            <input type="checkbox" :value="exp.code" v-model="assignSelectedCodes" />
            <span>{{ exp.name }}</span>
          </label>
        </div>
        <div class="assign-footer">
          <button type="button" class="btn-ghost px-4 py-2 rounded-xl text-sm font-semibold" @click="closeAssign">取消</button>
          <button type="button" class="btn-brand px-4 py-2 rounded-xl text-sm font-semibold" :disabled="assignSaving" @click="saveAssign">
            {{ assignSaving ? '保存中…' : '保存分配' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { teacherApi, experimentApi } from '../api'
import { useAuthStore } from '../stores/auth'
import AppTopBar from '../components/layout/AppTopBar.vue'
import ReportModal from '../components/modals/ReportModal.vue'

const auth = useAuthStore()
const router = useRouter()

const activeTab = ref('overview')
const loading = ref(false)
const overview = ref(null)
const reports = ref([])
const feedbackList = ref([])
const pendingFeedback = ref([])
const students = ref([])
const allExperiments = ref([])
const importFileInput = ref(null)
const importing = ref(false)
const selectedStudentIds = ref([])
const assignVisible = ref(false)
const assignTarget = ref(null)
const assignBulkMode = ref(false)
const assignSelectedCodes = ref([])
const assignSaving = ref(false)
const feedbackFilter = ref('all')
const expandedFeedbackId = ref(null)
const reportVisible = ref(false)
const selectedReport = ref(null)
const selectedReportSessionId = ref(null)
const downloadingReport = ref(false)

const navItems = [
  { key: 'overview', label: '概览' },
  { key: 'reports', label: '实验报告' },
  { key: 'feedback', label: '问答反馈' },
  { key: 'students', label: '班级管理' }
]

const feedbackFilters = [
  { key: 'all', label: '全部' },
  { key: 'NOT_HELPFUL', label: '无帮助' },
  { key: 'unprocessed', label: '未处理' }
]

const scopeLabel = computed(() => {
  const cls = overview.value?.managedClass
  return cls ? `当前班级：${cls}` : '全部班级'
})

const overviewCards = computed(() => {
  const d = overview.value || {}
  return [
    { label: '学生人数', value: d.studentCount ?? 0, tone: 'text-ink-strong' },
    { label: '已完成实验', value: d.finishedSessionCount ?? 0, tone: 'text-ink-strong' },
    { label: '实验报告', value: d.reportCount ?? 0, tone: 'text-ink-strong' },
    { label: '问答反馈', value: d.feedbackCount ?? 0, tone: 'text-ink-strong' },
    { label: '无帮助', value: d.notHelpfulCount ?? 0, tone: (d.notHelpfulCount ?? 0) > 0 ? 'text-red-500' : 'text-ink-strong' },
    { label: '待处理', value: d.unprocessedFeedbackCount ?? 0, tone: (d.unprocessedFeedbackCount ?? 0) > 0 ? 'text-amber-600' : 'text-ink-strong' }
  ]
})

const recentReports = computed(() => reports.value.slice(0, 8))
const recentPendingFeedback = computed(() => pendingFeedback.value.slice(0, 8))
const allStudentsSelected = computed(() =>
  students.value.length > 0 && selectedStudentIds.value.length === students.value.length
)

onMounted(() => {
  loadOverview()
})

async function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  expandedFeedbackId.value = null
  if (key === 'overview') await loadOverview()
  if (key === 'reports') await loadReports()
  if (key === 'feedback') await loadFeedback()
  if (key === 'students') await loadStudents()
}

async function loadOverview() {
  loading.value = true
  try {
    const [overviewRes, reportsRes, pendingRes] = await Promise.all([
      teacherApi.overview(),
      teacherApi.reports(),
      teacherApi.feedback({ processed: false })
    ])
    overview.value = overviewRes.data
    reports.value = reportsRes.data || []
    pendingFeedback.value = pendingRes.data || []
  } finally {
    loading.value = false
  }
}

async function loadReports() {
  loading.value = true
  try {
    const { data } = await teacherApi.reports()
    reports.value = data || []
  } finally {
    loading.value = false
  }
}

async function loadStudents() {
  loading.value = true
  try {
    const [studentsRes, expRes] = await Promise.all([
      teacherApi.students(),
      experimentApi.list()
    ])
    students.value = studentsRes.data || []
    allExperiments.value = expRes.data || []
    selectedStudentIds.value = selectedStudentIds.value.filter((id) =>
      students.value.some((s) => s.userId === id)
    )
  } finally {
    loading.value = false
  }
}

function downloadImportTemplate() {
  const content = '\uFEFF账号,姓名,密码,班级\n'
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '学生名单模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}

function triggerImport() {
  importFileInput.value?.click()
}

async function onImportFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  importing.value = true
  try {
    const csv = await file.text()
    await teacherApi.importStudents({ csv, defaultPassword: '123456' })
    await loadStudents()
  } catch (e) {
    window.alert(e.response?.data?.message || e.message || '导入失败')
  } finally {
    importing.value = false
    event.target.value = ''
  }
}

function toggleSelectAll(event) {
  selectedStudentIds.value = event.target.checked
    ? students.value.map((s) => s.userId)
    : []
}

function openAssign(student) {
  assignBulkMode.value = false
  assignTarget.value = student
  assignSelectedCodes.value = [...(student.assignedExperimentCodes || [])]
  assignVisible.value = true
}

function openBulkAssign() {
  if (!selectedStudentIds.value.length) return
  assignBulkMode.value = true
  assignTarget.value = null
  assignSelectedCodes.value = []
  assignVisible.value = true
}

function closeAssign() {
  assignVisible.value = false
  assignTarget.value = null
  assignBulkMode.value = false
}

async function saveAssign() {
  assignSaving.value = true
  try {
    if (assignBulkMode.value) {
      await teacherApi.bulkAssignExperiments({
        userIds: selectedStudentIds.value,
        experimentCodes: assignSelectedCodes.value
      })
    } else if (assignTarget.value) {
      await teacherApi.assignExperiments(assignTarget.value.userId, {
        experimentCodes: assignSelectedCodes.value
      })
    }
    closeAssign()
    await loadStudents()
  } finally {
    assignSaving.value = false
  }
}

async function loadFeedback() {
  loading.value = true
  try {
    const params = {}
    if (feedbackFilter.value === 'NOT_HELPFUL') params.rating = 'NOT_HELPFUL'
    if (feedbackFilter.value === 'unprocessed') params.processed = false
    const { data } = await teacherApi.feedback(params)
    feedbackList.value = data || []
    if (!feedbackList.value.some((item) => item.id === expandedFeedbackId.value)) {
      expandedFeedbackId.value = null
    }
  } finally {
    loading.value = false
  }
}

function setFeedbackFilter(key) {
  feedbackFilter.value = key
  loadFeedback()
}

function toggleFeedbackDetail(id) {
  expandedFeedbackId.value = expandedFeedbackId.value === id ? null : id
}

async function openReport(sessionId) {
  const { data } = await teacherApi.report(sessionId)
  selectedReport.value = data
  selectedReportSessionId.value = sessionId
  reportVisible.value = true
}

async function downloadReport(sessionId, experimentName) {
  const { data } = await teacherApi.reportDocx(sessionId)
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `实验总结报告-${experimentName || '实验'}.docx`
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadSelectedReport() {
  if (!selectedReportSessionId.value) return
  downloadingReport.value = true
  try {
    await downloadReport(selectedReportSessionId.value, selectedReport.value?.experimentName)
  } finally {
    downloadingReport.value = false
  }
}

async function markProcessed(feedbackId) {
  await teacherApi.markFeedbackProcessed(feedbackId)
  pendingFeedback.value = pendingFeedback.value.filter((item) => item.id !== feedbackId)
  if (activeTab.value === 'feedback') {
    await loadFeedback()
  }
  if (overview.value) {
    const { data } = await teacherApi.overview()
    overview.value = data
  }
}

function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function plainText(text) {
  return String(text || '').replace(/[#>*`_\-]/g, '').trim()
}

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.teacher-page {
  @apply w-full max-w-[1280px] mx-auto px-8 lg:px-12;
}
.section-head-row {
  @apply flex items-center justify-between gap-4 mb-4;
}
.section-head {
  @apply flex items-center gap-3 text-[16px] font-bold text-ink-strong;
}
.section-head::before {
  content: '';
  @apply w-1 h-5 rounded-full bg-brand-600 shrink-0;
}
.stat-grid {
  @apply grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-x-6 gap-y-5 pb-2;
}
.stat-item {
  @apply py-1;
}
.stat-label {
  @apply text-[13px] text-ink-faint;
}
.stat-value {
  @apply text-[26px] font-bold mt-1.5;
}
.teacher-nav {
  @apply -mx-6 px-6 border-t border-line-soft;
}
.teacher-tabs {
  @apply flex items-center justify-center gap-1 overflow-x-auto;
}
.teacher-tab {
  @apply relative px-5 py-3 text-[14px] font-medium text-ink-muted transition-colors;
}
.teacher-tab:hover {
  @apply text-ink-strong;
}
.teacher-tab--active {
  @apply text-brand-600 font-semibold;
}
.teacher-tab--active::after {
  content: '';
  @apply absolute bottom-0 left-4 right-4 h-0.5 bg-brand-600 rounded-full;
}
.teacher-table {
  @apply w-full text-[14px];
}
.teacher-table thead {
  @apply text-left border-b-2 border-line-strong;
}
.teacher-table th {
  @apply py-3 pr-6 text-[13px] font-bold text-ink-muted whitespace-nowrap;
}
.teacher-table th:last-child {
  @apply pr-0;
}
.teacher-table td {
  @apply py-4 pr-6 border-b border-line-soft align-top;
}
.teacher-table td:last-child {
  @apply pr-0;
}
.teacher-table tbody tr:hover td {
  @apply bg-surface-soft/60;
}
.detail-row td {
  @apply py-0 border-b border-line-soft bg-surface-soft/40;
}
.detail-block {
  @apply py-4 text-[14px] text-ink-base leading-relaxed;
}
.detail-label {
  @apply inline-block w-20 shrink-0 text-ink-faint font-semibold;
}
.filter-chip {
  @apply h-9 px-4 rounded-lg text-[13px] font-semibold text-ink-muted hover:text-ink-base hover:bg-surface-soft;
}
.filter-chip--active {
  @apply text-brand-700 bg-brand-50;
}
.link-btn {
  @apply text-[13px] font-semibold text-brand-700 hover:text-brand-800;
}
.rating-tag {
  @apply inline-block text-[12px] font-semibold;
}
.rating-tag--ok {
  @apply text-emerald-700;
}
.rating-tag--bad {
  @apply text-red-600;
}
.empty-block {
  @apply text-[14px] text-ink-faint py-10 pl-4;
}
.import-bar {
  @apply flex flex-wrap items-center gap-3;
}
.assign-overlay {
  @apply fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4;
}
.assign-modal {
  @apply w-full max-w-md rounded-2xl bg-white shadow-card p-6 space-y-4;
}
.assign-title {
  @apply text-[16px] font-bold text-ink-strong;
}
.assign-list {
  @apply max-h-64 overflow-y-auto space-y-2 py-1;
}
.assign-item {
  @apply flex items-center gap-3 text-[14px] text-ink-base cursor-pointer py-1.5;
}
.assign-footer {
  @apply flex justify-end gap-3 pt-2;
}
</style>
