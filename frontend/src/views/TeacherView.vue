<template>
  <div class="teacher-terminal">
    <!-- ====== 顶栏（含 Tab 导航） ====== -->
    <header class="terminal-head">
      <div class="head-left">
        <img src="/images/jyd-logo.png" alt="JYD" />
        <span class="head-sep" />
        <ExperimentSelect
          :experiments="experimentOptions"
          :experiment-code="selectedExpCode"
          @experiment-change="onExperimentChange"
        />
      </div>
      <nav class="terminal-nav">
        <button
          v-for="tab in navTabs"
          :key="tab.key"
          type="button"
          class="nav-tab"
          :class="{ active: activeTab === tab.key }"
          :style="{ '--icon-color': tab.color }"
          @click="activeTab = tab.key"
        >
          <svg class="tab-icon" fill="none" stroke="currentColor" stroke-width="1.8" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" :d="tab.icon" />
            <path v-if="tab.iconFill" :fill="tab.color" :d="tab.iconFill" stroke="none" opacity="0.12" />
          </svg>
          <span class="tab-label">{{ tab.label }}</span>
          <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
        </button>
      </nav>
      <div class="head-right">
        <div class="user-avatar brand-gradient">{{ userInitial }}</div>
        <span class="user-name">{{ auth.displayName || auth.username }}</span>
        <span class="user-sep" aria-hidden="true" />
        <button type="button" class="logout-link" @click="logout">退出</button>
      </div>
    </header>

    <!-- ====== 加载/空 ====== -->
    <div v-if="loading && !loadedOnce" class="terminal-state">加载中…</div>
    <div v-else-if="!selectedExpCode" class="terminal-state">暂无可查看的实验。</div>

    <!-- ====== 内容区 ====== -->
    <div v-else class="terminal-content">

      <!-- ========== Tab 1: 学情总览 ========== -->
      <div v-show="activeTab === 'overview'" class="tab-panel overview-panel">
        <div class="overview-toolbar">
          <h2>{{ classroom?.experimentName || currentExperimentName }}</h2>
          <div class="overview-summary">
            <span>共 {{ classroomStudents.length }} 人</span>
            <span class="sep">·</span>
            <span>{{ classroom?.activeCount ?? 0 }} 进行中</span>
            <span class="sep">·</span>
            <span>{{ finishedCount }} 已完成</span>
          </div>
        </div>

        <div class="overview-body">
          <!-- 按班级分组的学情列表 -->
          <section class="student-grid-section">
            <div v-if="!classroomStudents.length" class="empty-state">暂无学生进行此实验。</div>
            <div v-else class="class-list custom-scroll">
              <div
                v-for="grp in studentsByClass"
                :key="grp.name"
                class="class-group"
                :class="{ expanded: expandedClasses[grp.name] !== false }"
              >
                <button type="button" class="class-group-header" @click="toggleClassExpand(grp.name)">
                  <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <span class="class-name">{{ grp.name }}</span>
                  <span class="class-stats">
                    <span class="stat-chip">{{ grp.students.length }} 人</span>
                    <span v-if="grp.activeCount" class="stat-chip active-chip">{{ grp.activeCount }} 进行中</span>
                    <span v-if="grp.finishedCount" class="stat-chip done-chip">{{ grp.finishedCount }} 已完成</span>
                  </span>
                </button>
                <div v-if="expandedClasses[grp.name] !== false" class="class-group-body">
                  <!-- 学生学情卡片网格 -->
                  <div class="learn-card-grid">
                    <button
                      v-for="row in grp.students"
                      :key="row.userId"
                      type="button"
                      class="learn-card"
                      :class="{
                        'learn-card--active': selectedUserId === row.userId,
                        'learn-card--online': row.status === 'ACTIVE',
                        'learn-card--done': row.status === 'FINISHED',
                        'learn-card--warn': (row.helpCount > 0 || row.errorPointCount > 0) && row.status === 'ACTIVE'
                      }"
                      @click="selectedUserId = row.userId"
                    >
                      <div class="learn-card-top">
                        <span class="learn-name">{{ row.studentName }}</span>
                        <span class="learn-dot" :class="row.status === 'ACTIVE' ? 'dot-on' : row.status === 'FINISHED' ? 'dot-done' : 'dot-off'"></span>
                      </div>
                      <div class="learn-card-step">{{ row.stepTitle || '未开始' }}</div>
                      <div class="learn-card-bottom">
                        <span class="learn-stat">
                          <span class="learn-stat-label">用时</span>
                          <span class="learn-stat-val">{{ row.status === 'ACTIVE' || row.status === 'FINISHED' ? `${row.minutesOnSession}min` : '—' }}</span>
                        </span>
                        <span class="learn-stat" :class="{ warn: row.helpCount > 0 }">
                          <span class="learn-stat-label">求助</span>
                          <span class="learn-stat-val">{{ row.helpCount ?? 0 }}</span>
                        </span>
                        <span class="learn-stat" :class="{ warn: row.errorPointCount > 0 }">
                          <span class="learn-stat-label">纠错</span>
                          <span class="learn-stat-val">{{ row.errorPointCount ?? 0 }}</span>
                        </span>
                      </div>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <!-- 学生详情面板 -->
          <transition name="slide-fade">
            <aside v-if="selectedStudent" class="detail-panel">
              <div class="detail-header">
                <div>
                  <h2>{{ selectedStudent.studentName }}</h2>
                  <p class="detail-class">{{ selectedStudent.studentClass || '—' }}</p>
                </div>
                <span class="state-pill" :class="selectedStudent.status === 'ACTIVE' ? 'active' : selectedStudent.status === 'FINISHED' ? 'done' : 'idle'">
                  {{ selectedStudent.status === 'ACTIVE' ? '进行中' : selectedStudent.status === 'FINISHED' ? '已完成' : '未开始' }}
                </span>
              </div>

              <div class="detail-stats">
                <div>
                  <span>当前步骤</span>
                  <strong>{{ selectedStudent.stepTitle || '—' }}</strong>
                </div>
                <div>
                  <span>用时</span>
                  <strong>{{ (selectedStudent.status === 'ACTIVE' || selectedStudent.status === 'FINISHED') ? `${selectedStudent.minutesOnSession} 分钟` : '—' }}</strong>
                </div>
                <div>
                  <span>求助</span>
                  <strong>{{ selectedStudent.helpCount ?? 0 }}</strong>
                </div>
                <div>
                  <span>纠错</span>
                  <strong>{{ selectedStudent.errorPointCount ?? 0 }}</strong>
                </div>
                <div>
                  <span>预习完成</span>
                  <strong :class="selectedStudent.preLabCompleted ? 'ok-text' : 'bad-text'">
                    {{ selectedStudent.preLabCompleted ? '是' : '否' }}
                  </strong>
                </div>
                <div>
                  <span>数据校验</span>
                  <strong :class="selectedStudent.dataIssue ? 'bad-text' : 'ok-text'">
                    {{ selectedStudent.lastDataValidation || '—' }}
                  </strong>
                </div>
              </div>

              <div v-if="selectedStudent.recentCorrectionTypes?.length" class="detail-section">
                <h3>近期纠错类型</h3>
                <div class="tag-list">
                  <span v-for="t in selectedStudent.recentCorrectionTypes" :key="t" class="tag-item">{{ t }}</span>
                </div>
              </div>

              <div class="detail-quick-actions">
                <button type="button" @click="activeTab = 'camera'; openCameraModal(selectedStudent.userId)">查看摄像头</button>
                <button type="button" @click="activeTab = 'feedback'; selectedFeedbackUserId = selectedStudent.userId">查看问答反馈</button>
              </div>
            </aside>
          </transition>
        </div>
      </div>

      <!-- ========== Tab 2: 问答反馈 ========== -->
      <div v-show="activeTab === 'feedback'" class="tab-panel">
        <!-- 列表视图 -->
        <template v-if="!selectedFeedbackId">
          <div class="fb-tabs">
            <button type="button" class="fb-tab" :class="{ active: feedbackFilter === 'HELPFUL' }" @click="feedbackFilter = 'HELPFUL'">
              有帮助 ({{ helpfulCount }})
            </button>
            <button type="button" class="fb-tab" :class="{ active: feedbackFilter === 'NOT_HELPFUL' }" @click="feedbackFilter = 'NOT_HELPFUL'">
              无帮助 ({{ notHelpfulCount }})
            </button>
          </div>

          <div v-if="!filteredFeedback.length" class="empty-state">暂无{{ feedbackFilter === 'HELPFUL' ? '有帮助' : '无帮助' }}反馈。</div>
          <div v-else class="fb-list-wrap custom-scroll">
            <div class="fb-list-head">
              <span class="fb-col-student">学生</span>
              <span class="fb-col-step">步骤</span>
              <span class="fb-col-question">提问内容</span>
              <span class="fb-col-time">时间</span>
              <span class="fb-col-status">状态</span>
            </div>
            <div
              v-for="item in filteredFeedback"
              :key="item.id"
              class="fb-list-row"
              :class="{ unprocessed: !item.processed }"
              @click="selectedFeedbackId = item.id"
            >
              <span class="fb-col-student">
                <span class="fb-avatar">{{ (item.studentName || '?').charAt(0) }}</span>
                <strong>{{ item.studentName }}</strong>
              </span>
              <span class="fb-col-step">{{ item.stepId || '—' }}</span>
              <span class="fb-col-question">{{ item.userQuestion || '（学生未输入问题文本）' }}</span>
              <span class="fb-col-time">{{ formatTime(item.createdAt) }}</span>
              <span class="fb-col-status">
                <span class="fb-badge" :class="item.processed ? 'fb-badge--done' : 'fb-badge--pending'">
                  {{ item.processed ? '已处理' : '待处理' }}
                </span>
              </span>
            </div>
          </div>
        </template>

        <!-- 详情视图 -->
        <template v-else-if="selectedFeedbackDetail">
          <div class="fb-detail-top">
            <button type="button" class="back-btn" @click="selectedFeedbackId = null">
              <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" class="back-icon">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
              </svg>
              返回列表
            </button>
            <span class="fb-detail-time">{{ formatTime(selectedFeedbackDetail.createdAt) }}</span>
          </div>

          <div class="fb-detail-body custom-scroll">
            <div class="fb-detail-student">
              <strong>{{ selectedFeedbackDetail.studentName }}</strong>
              <span>步骤 {{ selectedFeedbackDetail.stepId || '—' }} · {{ selectedFeedbackDetail.experimentName || '—' }}</span>
            </div>

            <div class="fb-detail-block fb-detail-q">
              <span class="fb-detail-role">学生提问</span>
              <p>{{ selectedFeedbackDetail.userQuestion || '（学生未输入问题文本）' }}</p>
            </div>

            <div class="fb-detail-block fb-detail-a">
              <span class="fb-detail-role">AI 回复</span>
              <p>{{ plainText(selectedFeedbackDetail.aiReply) || '（暂无 AI 回复内容）' }}</p>
            </div>

            <div class="fb-detail-footer">
              <span v-if="selectedFeedbackDetail.processed" class="processed-tag">已处理</span>
              <button v-else type="button" class="fb-process-btn" @click="markProcessed(selectedFeedbackDetail.id)">标记已处理</button>
            </div>
          </div>
        </template>
      </div>

      <!-- ========== Tab 3: 摄像头监控 ========== -->
      <div v-show="activeTab === 'camera'" class="tab-panel camera-tab">
        <div class="grid-toolbar">
          <h2>实验台摄像头</h2>
          <span class="toolbar-exp">{{ benchCameraConfig?.enabled ? '摄像头已连接' : '摄像头未配置' }}</span>
        </div>
        <div v-if="!cameraStudents.length" class="empty-state">暂无学生。</div>
        <div v-else class="camera-grid custom-scroll">
          <button
            v-for="row in cameraStudents"
            :key="row.userId"
            type="button"
            class="camera-card"
            @click="openCameraModal(row.userId)"
          >
            <div class="cam-preview-mini">
              <span class="cam-badge" :class="benchCameraConfig?.enabled ? 'online' : 'offline'">
                {{ benchCameraConfig?.enabled ? '在线' : '离线' }}
              </span>
              <div class="cam-placeholder">
                <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
                  <rect x="3" y="6" width="18" height="14" rx="2.5" />
                  <circle cx="12" cy="13" r="3.5" />
                </svg>
              </div>
            </div>
            <div class="cam-info">
              <strong>{{ row.studentName }}</strong>
              <span>{{ row.status === 'ACTIVE' ? row.stepTitle || '实验中' : '未开始' }}</span>
            </div>
          </button>
        </div>

        <!-- 摄像头放大弹窗 -->
        <div v-if="cameraModalOpen" class="cam-modal-overlay" @click.self="closeCameraModal">
          <div class="cam-modal" :class="{ expanded: camExpanded }">
            <div ref="camLargeRef" class="cam-large-preview">
              <video v-show="camReady" ref="camVideoRef" class="cam-video" playsinline muted />
              <div v-if="!camReady" class="cam-loading">
                <div class="spinner" />
                <p>{{ camError || '连接中…' }}</p>
              </div>
              <div v-if="camReady" class="cam-overlay-tl">
                <span class="rec-dot" />
                <span class="rec-text">REC</span>
              </div>
              <div v-if="camReady" class="cam-overlay-bl">
                <span class="live-text">LIVE</span>
              </div>
              <div v-if="camReady" class="cam-overlay-tr">
                <button type="button" class="cam-expand-btn" title="全屏" @click="toggleExpand">
                  <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 3H3v5m13-5h5v5M8 21H3v-5m18 0v5h-5" />
                  </svg>
                </button>
              </div>
              <div class="cam-overlay-br">
                <strong>{{ cameraSelectedStudent?.studentName }}</strong>
                <span>{{ cameraSelectedStudent?.studentClass || '—' }} · {{ cameraSelectedStudent?.stepTitle || '—' }}</span>
              </div>
            </div>
            <div class="cam-modal-bar">
              <div class="cam-modal-actions">
                <button type="button" @click="startCameraStream" :disabled="camReady">开启画面</button>
                <button type="button" @click="stopCameraStream" :disabled="!camReady">关闭画面</button>
              </div>
              <button type="button" class="cam-modal-close" @click="closeCameraModal">关闭</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== Tab 4: 报告分析 ========== -->
      <div v-show="activeTab === 'report'" class="tab-panel">
        <div class="report-layout">
          <section class="report-list-section">
            <h2>实验报告 ({{ currentReports.length }}/{{ cameraStudents.length }})</h2>
            <div v-if="!reportsByClass.length" class="empty-state">暂无学生数据。</div>
            <div v-else class="report-class-list custom-scroll">
              <div
                v-for="grp in reportsByClass"
                :key="grp.className"
                class="class-group"
                :class="{ expanded: expandedClasses[grp.className] !== false }"
              >
                <button type="button" class="class-group-header" @click="toggleClassExpand(grp.className)">
                  <svg class="chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 5l7 7-7 7" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                  <span class="class-name">{{ grp.className }}</span>
                  <span class="class-stats">
                    <span class="stat-chip submitted">{{ grp.submitted }}/{{ grp.total }} 已提交</span>
                    <span v-if="grp.total - grp.submitted > 0" class="stat-chip pending">{{ grp.total - grp.submitted }} 未提交</span>
                  </span>
                </button>
                <div v-if="expandedClasses[grp.className] !== false" class="class-group-body">
                  <button
                    v-for="stu in grp.students"
                    :key="stu.userId"
                    type="button"
                    class="student-report-row"
                    :class="{ active: stu.report && selectedReportId === stu.report.sessionId }"
                    @click="stu.report && selectReportItem(stu.report.sessionId)"
                  >
                    <span class="stu-avatar" :class="stu.submitted ? 'submitted' : 'not-submitted'">
                      {{ (stu.studentName || '?').charAt(0) }}
                    </span>
                    <div class="stu-info">
                      <strong>{{ stu.studentName }}</strong>
                      <span>{{ stu.submitted ? formatTime(stu.report.endTime || stu.report.startTime) : '未提交' }}</span>
                    </div>
                    <span v-if="stu.submitted" class="stu-badges">
                      <span v-if="stu.report.helpCount" class="badge-help">求助 {{ stu.report.helpCount }}</span>
                      <span v-if="stu.report.errorPointCount" class="badge-err">纠错 {{ stu.report.errorPointCount }}</span>
                      <span v-if="!stu.report.helpCount && !stu.report.errorPointCount" class="badge-ok">正常</span>
                    </span>
                    <span v-else class="stu-status pending">未提交</span>
                  </button>
                </div>
              </div>
            </div>
          </section>

          <section class="report-detail-section">
            <div v-if="!selectedReportItem" class="empty-state">请选择左侧学生查看报告详情。</div>
            <template v-else>
              <header class="rd-head">
                <div>
                  <h2>{{ selectedReportItem.studentName }}</h2>
                  <p>{{ selectedReportItem.studentClass }} · {{ selectedReportItem.experimentName }} · {{ formatTime(selectedReportItem.endTime || selectedReportItem.startTime) }}</p>
                </div>
                <div class="rd-actions">
                  <button type="button" @click="openReport(selectedReportItem.sessionId)">查看完整报告</button>
                  <button type="button" class="ai-btn" @click="openReportWithReview(selectedReportItem.sessionId)">AI 预评</button>
                  <button type="button" @click="downloadReport(selectedReportItem.sessionId, selectedReportItem.experimentName)">下载</button>
                </div>
              </header>

              <div v-if="previewLoading" class="empty-state">正在读取报告数据…</div>
              <div v-else-if="selectedReportPreview" class="rd-body custom-scroll">
                <div class="rd-summary">
                  <span>求助 <strong>{{ selectedReportPreview.helpCount ?? 0 }}</strong></span>
                  <span>纠错 <strong class="bad-text">{{ selectedReportPreview.errorPointCount ?? 0 }}</strong></span>
                  <span>教程查阅 <strong>{{ selectedReportPreview.tutViewCount ?? 0 }}</strong></span>
                  <span>环境巡检 <strong>{{ selectedReportPreview.labL3Count ?? 0 }}</strong></span>
                </div>

                <h3 class="rd-section-title">实验数据记录</h3>
                <table v-if="previewDataRows.length" class="data-check-table">
                  <thead>
                    <tr><th>步骤</th><th>提交数据</th><th>校验结果</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="(item, i) in previewDataRows" :key="i">
                      <td>{{ item.stepTitle || '—' }}</td>
                      <td>{{ item.valuesSummary || '—' }}</td>
                      <td :class="isBadValidation(item.validationSummary) ? 'bad-text' : 'ok-text'">
                        {{ item.validationSummary || '—' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div v-else class="empty-state small">本次报告暂无结构化数据。</div>

                <div v-if="selectedReportPreview.corrections?.length" class="rd-corrections">
                  <h3 class="rd-section-title">操作纠错记录</h3>
                  <article v-for="(log, i) in selectedReportPreview.corrections" :key="i" class="correction-item">
                    <div class="corr-head">
                      <span>{{ log.stepTitle || '—' }}</span>
                      <span class="corr-type">{{ log.errorType || '—' }}</span>
                    </div>
                    <p>{{ log.detail || '—' }}</p>
                  </article>
                </div>
              </div>
              <div v-else class="empty-state">暂无报告数据。</div>
            </template>
          </section>
        </div>
      </div>
    </div>

    <ReportModal
      :visible="reportVisible"
      :report="selectedReport"
      :downloading="downloadingReport"
      :reviewing="reviewingReport"
      :review-text="reviewText"
      :review-from-dify="reviewFromDify"
      :review-error="reviewError"
      @close="closeReport"
      @download-docx="downloadSelectedReport"
      @ai-review="runAiReview"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import flvjs from 'flv.js'
import { experimentApi, teacherApi, systemApi } from '../api'
import { useAuthStore } from '../stores/auth'
import ReportModal from '../components/modals/ReportModal.vue'
import ExperimentSelect from '../components/layout/ExperimentSelect.vue'

const CURRENT_EXP_KEY = 'wxz_teacher_current_exp'
const auth = useAuthStore()
const router = useRouter()

const userInitial = computed(() => {
  const n = (auth.displayName || auth.username || '').trim()
  return n ? n.charAt(0) : '用'
})

const loading = ref(false)
const loadedOnce = ref(false)
const experiments = ref([])
const overview = ref(null)
const classroom = ref(null)
const reports = ref([])
const feedbackList = ref([])
const selectedFeedbackId = ref(null)
const students = ref([])
const benchCameraConfig = ref(null)
const selectedExpCode = ref('')
const activeTab = ref('overview')

const selectedUserId = ref(null)
const feedbackFilter = ref('NOT_HELPFUL')
const selectedReportId = ref(null)
const selectedReportPreview = ref(null)
const previewLoading = ref(false)
const expandedClasses = ref({})

const reportVisible = ref(false)
const selectedReport = ref(null)
const selectedReportSessionId = ref(null)
const downloadingReport = ref(false)
const reviewingReport = ref(false)
const reviewText = ref('')
const reviewFromDify = ref(true)
const reviewError = ref('')

const cameraSelectedId = ref(null)
const camReady = ref(false)
const camError = ref('')
const camExpanded = ref(false)
const camLargeRef = ref(null)
const camVideoRef = ref(null)
const cameraModalOpen = ref(false)
let flvPlayer = null

const experimentOptions = computed(() => {
  if (experiments.value.length) return experiments.value
  const codes = new Map()
  students.value.forEach((s) => {
    ;(s.assignedExperimentCodes || []).forEach((code, i) => {
      codes.set(code, s.assignedExperimentNames?.[i] || code)
    })
  })
  return [...codes.entries()].map(([code, name]) => ({ code, name }))
})

const currentExperimentName = computed(() =>
  experimentOptions.value.find((e) => e.code === selectedExpCode.value)?.name
    || classroom.value?.experimentName
    || selectedExpCode.value
    || '实验'
)

const classroomStudents = computed(() => classroom.value?.students || [])

const finishedCount = computed(() => classroomStudents.value.filter((s) => s.status === 'FINISHED').length)

const studentsByClass = computed(() => {
  const groups = {}
  for (const s of classroomStudents.value) {
    const cls = s.studentClass || '未分班'
    if (!groups[cls]) groups[cls] = []
    groups[cls].push(s)
  }
  return Object.entries(groups).map(([name, list]) => ({
    name,
    students: list,
    activeCount: list.filter((s) => s.status === 'ACTIVE').length,
    finishedCount: list.filter((s) => s.status === 'FINISHED').length,
    reportCount: 0,
    helpCount: list.reduce((sum, s) => sum + (s.helpCount || 0), 0),
    errorCount: list.reduce((sum, s) => sum + (s.errorPointCount || 0), 0)
  }))
})
const currentReports = computed(() =>
  reports.value.filter((r) => r.experimentCode === selectedExpCode.value)
)
const currentFeedback = computed(() =>
  feedbackList.value.filter((f) => f.experimentCode === selectedExpCode.value)
)

const cameraStudents = computed(() => {
  if (classroomStudents.value.length) return classroomStudents.value
  return students.value
    .filter((s) => !selectedExpCode.value || (s.assignedExperimentCodes || []).includes(selectedExpCode.value))
    .map((s) => ({
      userId: s.userId,
      studentName: s.displayName || s.username,
      studentClass: s.studentClass,
      status: 'NOT_STARTED',
      stepTitle: ''
    }))
})

const selectedStudent = computed(() =>
  classroomStudents.value.find((s) => s.userId === selectedUserId.value) || null
)

const cameraSelectedStudent = computed(() =>
  cameraStudents.value.find((s) => s.userId === cameraSelectedId.value) || null
)

const helpfulCount = computed(() => currentFeedback.value.filter((f) => f.rating === 'HELPFUL').length)
const notHelpfulCount = computed(() => currentFeedback.value.filter((f) => f.rating === 'NOT_HELPFUL').length)
const unprocessedCount = computed(() => currentFeedback.value.filter((f) => !f.processed).length)

const filteredFeedback = computed(() =>
  currentFeedback.value
    .filter((f) => f.rating === feedbackFilter.value)
    .sort((a, b) => timeValue(b.createdAt) - timeValue(a.createdAt))
)

const selectedFeedbackDetail = computed(() =>
  currentFeedback.value.find((f) => f.id === selectedFeedbackId.value) || null
)

const sortedReports = computed(() =>
  [...currentReports.value].sort((a, b) =>
    timeValue(b.endTime || b.startTime) - timeValue(a.endTime || a.startTime)
  )
)

const reportsByClass = computed(() => {
  const reportMap = new Map()
  currentReports.value.forEach((r) => {
    const key = r.userId || r.studentName
    reportMap.set(key, r)
  })
  const allStudents = cameraStudents.value
  const classMap = new Map()
  allStudents.forEach((s) => {
    const cls = s.studentClass || '未分班'
    if (!classMap.has(cls)) {
      classMap.set(cls, { className: cls, students: [], submitted: 0, total: 0 })
    }
    const group = classMap.get(cls)
    const report = reportMap.get(s.userId) || reportMap.get(s.studentName)
    group.students.push({
      ...s,
      report: report || null,
      submitted: !!report
    })
    group.total++
    if (report) group.submitted++
  })
  return [...classMap.values()].sort((a, b) => a.className.localeCompare(b.className))
})

function toggleClassExpand(className) {
  expandedClasses.value = { ...expandedClasses.value, [className]: !expandedClasses.value[className] }
}

const selectedReportItem = computed(() =>
  sortedReports.value.find((r) => r.sessionId === selectedReportId.value) || null
)

const previewDataRows = computed(() => selectedReportPreview.value?.dataLogEntries || [])

const navTabs = computed(() => [
  {
    key: 'overview', label: '学情总览', color: '#6366f1',
    icon: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
    iconFill: 'M3 13h8V3H3v10zm10 8h8V11h-8v10z',
    badge: ''
  },
  {
    key: 'feedback', label: '问答反馈', color: '#10b981',
    icon: 'M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4-.8L3 20l1.3-3.9A7.96 7.96 0 013 12c0-4.418 4.03-8 9-8s9 3.582 9 8z',
    iconFill: 'M21 12c0 4.418-4.03 8-9 8a9.86 9.86 0 01-4-.8L3 20l1.3-3.9A7.96 7.96 0 013 12c0-4.418 4.03-8 9-8s9 3.582 9 8z',
    badge: unprocessedCount.value ? String(unprocessedCount.value) : ''
  },
  {
    key: 'camera', label: '摄像头监控', color: '#f59e0b',
    icon: 'M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z',
    iconFill: 'M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z',
    badge: ''
  },
  {
    key: 'report', label: '报告分析', color: '#ec4899',
    icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4',
    iconFill: 'M7 3a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2',
    badge: currentReports.value.length ? String(currentReports.value.length) : ''
  }
])

watch(activeTab, (tab) => {
  if (tab === 'report' && !selectedReportId.value && sortedReports.value.length) {
    selectedReportId.value = sortedReports.value[0].sessionId
  }
  if (tab !== 'camera') stopCameraStream()
})

watch(
  () => selectedReportItem.value?.sessionId,
  (sid) => loadPreviewReport(sid),
  { immediate: false }
)

onMounted(() => {
  loadInitial()
  loadBenchCameraConfig()
})

onUnmounted(() => {
  stopCameraStream()
})

async function loadInitial() {
  loading.value = true
  try {
    const [expRes, studentRes] = await Promise.all([
      experimentApi.list(),
      teacherApi.students()
    ])
    experiments.value = expRes.data || []
    students.value = studentRes.data || []
    const saved = localStorage.getItem(CURRENT_EXP_KEY)
    selectedExpCode.value = experimentOptions.value.some((e) => e.code === saved)
      ? saved
      : experimentOptions.value[0]?.code || ''
    await loadExperimentData()
  } finally {
    loading.value = false
    loadedOnce.value = true
  }
}

async function loadExperimentData() {
  if (!selectedExpCode.value) return
  const [overviewRes, classroomRes, reportRes, feedbackRes] = await Promise.all([
    teacherApi.overview(),
    teacherApi.classroom({ experimentCode: selectedExpCode.value }),
    teacherApi.reports({ experimentCode: selectedExpCode.value }),
    teacherApi.feedback({ experimentCode: selectedExpCode.value })
  ])
  overview.value = overviewRes.data
  classroom.value = classroomRes.data
  reports.value = reportRes.data || []
  feedbackList.value = feedbackRes.data || []
  selectedUserId.value = classroomStudents.value[0]?.userId || null
  selectedReportId.value = sortedReports.value[0]?.sessionId || null
  await loadPreviewReport(selectedReportId.value)
}

async function refresh() {
  loading.value = true
  try {
    const { data } = await teacherApi.students()
    students.value = data || []
    await loadExperimentData()
  } finally {
    loading.value = false
  }
}

async function onExperimentChange(code) {
  if (code) selectedExpCode.value = code
  localStorage.setItem(CURRENT_EXP_KEY, selectedExpCode.value)
  selectedUserId.value = null
  selectedReportId.value = null
  selectedReportPreview.value = null
  loading.value = true
  try {
    await loadExperimentData()
  } finally {
    loading.value = false
  }
}

function selectReportItem(sessionId) {
  selectedReportId.value = sessionId
}

function openCameraModal(userId) {
  cameraSelectedId.value = userId
  cameraModalOpen.value = true
  camReady.value = false
  camError.value = ''
  nextTick(() => startCameraStream())
}

function closeCameraModal() {
  stopCameraStream()
  cameraModalOpen.value = false
  cameraSelectedId.value = null
}

async function loadPreviewReport(sessionId) {
  if (!sessionId) {
    selectedReportPreview.value = null
    return
  }
  previewLoading.value = true
  try {
    const { data } = await teacherApi.report(sessionId)
    selectedReportPreview.value = data
  } catch {
    selectedReportPreview.value = null
  } finally {
    previewLoading.value = false
  }
}

async function openReport(sessionId) {
  if (!sessionId) return
  const { data } = await teacherApi.report(sessionId)
  selectedReport.value = data
  selectedReportSessionId.value = sessionId
  reviewText.value = ''
  reviewError.value = ''
  reviewFromDify.value = true
  reportVisible.value = true
}

async function openReportWithReview(sessionId) {
  await openReport(sessionId)
  await runAiReview()
}

function closeReport() {
  reportVisible.value = false
  reviewingReport.value = false
}

async function runAiReview() {
  if (!selectedReportSessionId.value || reviewingReport.value) return
  reviewingReport.value = true
  reviewError.value = ''
  try {
    const { data } = await teacherApi.reviewReport(selectedReportSessionId.value)
    reviewText.value = data?.text || '未返回预评内容'
    reviewFromDify.value = data?.fromDify !== false
  } catch (e) {
    reviewError.value = e.response?.data?.message || e.message || 'AI 预评失败'
  } finally {
    reviewingReport.value = false
  }
}

async function downloadReport(sessionId, experimentName) {
  const { data } = await teacherApi.reportDocx(sessionId)
  const blob = new Blob([data], {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `实验报告-${experimentName || currentExperimentName.value || '实验'}.docx`
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
  await loadExperimentData()
}

async function loadBenchCameraConfig() {
  try {
    const { data } = await systemApi.benchCamera()
    benchCameraConfig.value = data
  } catch {
    benchCameraConfig.value = null
  }
}

function resolveStreamUrl(url) {
  if (url?.startsWith('/ws/')) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}${url}`
  }
  return url
}

async function startCameraStream() {
  stopCameraStream()
  camError.value = ''
  camReady.value = false
  const cfg = benchCameraConfig.value
  if (!cfg?.enabled || !cfg.browserStreamUrl) {
    camError.value = '摄像头未配置或不可用'
    return
  }
  if (!flvjs.isSupported()) {
    camError.value = '当前浏览器不支持 FLV 实时预览'
    return
  }
  await nextTick()
  const video = camVideoRef.value
  if (!video) return
  video.muted = true
  try {
    flvPlayer = flvjs.createPlayer({
      type: 'flv',
      url: resolveStreamUrl(cfg.browserStreamUrl),
      isLive: true,
      cors: true
    }, {
      enableWorker: false,
      enableStashBuffer: false,
      stashInitialSize: 32,
      maxBufferLength: 0.3,
      liveBufferLatencyChasing: true,
      autoCleanupSourceBuffer: true,
      autoplay: true,
      muted: true
    })
    flvPlayer.attachMediaElement(video)
    flvPlayer.load()
    await video.play()
    await waitForVideoFrame(video, 7000)
    camReady.value = true
  } catch {
    camError.value = '无法播放摄像头视频流，请确认摄像头在线'
    cleanupFlv()
  }
}

function waitForVideoFrame(video, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    if (video.readyState >= 2 && video.videoWidth > 0) { resolve(); return }
    const timer = setTimeout(() => { cleanup(); reject(new Error('timeout')) }, timeoutMs)
    const onReady = () => { if (video.videoWidth > 0) { cleanup(); resolve() } }
    const cleanup = () => {
      clearTimeout(timer)
      video.removeEventListener('loadeddata', onReady)
      video.removeEventListener('playing', onReady)
    }
    video.addEventListener('loadeddata', onReady)
    video.addEventListener('playing', onReady)
  })
}

function cleanupFlv() {
  if (flvPlayer) {
    try {
      flvPlayer.pause()
      flvPlayer.unload()
      flvPlayer.detachMediaElement()
      flvPlayer.destroy()
    } catch { /* noop */ }
    flvPlayer = null
  }
  const video = camVideoRef.value
  if (video) {
    video.srcObject = null
    video.removeAttribute('src')
    video.load()
  }
}

function stopCameraStream() {
  cleanupFlv()
  camReady.value = false
  camExpanded.value = false
}

function toggleExpand() {
  camExpanded.value = !camExpanded.value
}

function priorityLabel(p) {
  return { high: '高', medium: '中', normal: '低' }[p] || '低'
}

function isBadValidation(text) {
  return /异常|错误|不通过|fail|error/i.test(String(text || ''))
}

function timeValue(value) {
  const d = new Date(value || 0)
  return Number.isNaN(d.getTime()) ? 0 : d.getTime()
}

function formatTime(value) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  })
}

function plainText(text) {
  return String(text || '').replace(/[#>*`_\-]/g, '').replace(/\n{3,}/g, '\n\n').trim()
}

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.teacher-terminal {
  height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: #1e293b;
  background: #f2f3f7;
}

/* ====== 顶栏（含 Tab 导航） ====== */
.terminal-head {
  height: 56px;
  display: flex;
  align-items: stretch;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e4e9f3;
  flex-shrink: 0;
}
.head-left, .head-right { display: flex; align-items: center; gap: 10px; }
.head-left img { height: 32px; width: auto; object-fit: contain; }
.head-sep {
  width: 1px; height: 22px; background: #e4e9f3; flex-shrink: 0;
}
.terminal-head h1 { font-size: 17px; font-weight: 700; color: #0f172a; white-space: nowrap; }
.head-right { font-size: 13px; color: #64748b; margin-left: auto; }
.head-right .user-avatar {
  width: 28px; height: 28px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 11px; font-weight: 700; flex-shrink: 0;
}
.head-right .user-name {
  font-size: 13px; color: #1e293b; font-weight: 500;
  max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.head-right .user-sep {
  width: 1px; height: 14px; background: #e4e9f3; flex-shrink: 0;
}
.head-right .logout-link {
  font-size: 13px; color: #64748b; font-weight: 500;
  transition: color 0.15s;
}
.head-right .logout-link:hover { color: #4f46e5; }

/* ====== Tab 导航（嵌入顶栏） ====== */
.terminal-nav {
  display: flex; align-items: stretch; justify-content: center; gap: 4px;
  flex: 1; min-width: 0;
}
.nav-tab {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  position: relative; padding: 4px 18px; border: none; background: transparent;
  color: #94a3b8; transition: all 0.2s; white-space: nowrap;
  border-radius: 8px 8px 0 0;
}
.nav-tab:hover { color: #64748b; background: rgba(0, 0, 0, 0.03); }
.nav-tab:hover .tab-icon { color: #94a3b8; }
.nav-tab.active .tab-icon { color: var(--icon-color, #4f46e5); }
.nav-tab.active .tab-label { color: #0f172a; }
.nav-tab.active::after {
  content: ''; position: absolute; bottom: 0; left: 18px; right: 18px;
  height: 2px; background: var(--icon-color, #4f46e5); border-radius: 999px;
}
.tab-icon { width: 26px; height: 26px; color: #cbd5e1; transition: color 0.2s; }
.tab-label { font-size: 10px; font-weight: 500; letter-spacing: 0.02em; }
.nav-tab.active .tab-label { font-weight: 700; }
.tab-badge {
  position: absolute; top: 4px; right: 10px;
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px;
  background: #ef4444; color: #fff; font-size: 10px; font-weight: 700;
}

/* ====== 状态/加载 ====== */
.terminal-state {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: #94a3b8; font-size: 15px;
}
.terminal-content { flex: 1; overflow: hidden; display: flex; flex-direction: column; min-height: 0; }
.tab-panel { height: 100%; overflow: hidden; display: flex; flex-direction: column; padding: 14px 24px 20px; }

/* ====== 统计栏 ====== */
.stat-bar {
  display: flex; align-items: center; gap: 0; flex-shrink: 0;
  padding: 8px 16px; margin-bottom: 12px;
  background: #fff; border: 1px solid #e4e9f3;
  font-size: 13px; color: #64748b;
}
.stat-item { display: inline-flex; align-items: center; gap: 4px; }
.stat-item strong { color: #0f172a; font-size: 16px; font-weight: 700; margin: 0 2px; }
.stat-item strong.c-green { color: #059669; }
.stat-item strong.c-red { color: #ef4444; }
.stat-item em { font-size: 11px; color: #94a3b8; font-style: normal; }
.stat-sep { width: 1px; height: 16px; background: #e4e9f3; margin: 0 18px; flex-shrink: 0; }

/* ====== 学情总览 ====== */
.overview-panel { padding: 0; }
.overview-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; border-bottom: 1px solid #e4e9f3; flex-shrink: 0;
  background: #fff;
}
.overview-toolbar h2 { font-size: 14px; font-weight: 700; color: #0f172a; }
.overview-summary { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #64748b; }
.overview-summary .sep { color: #cbd5e1; }

.overview-body { display: grid; grid-template-columns: 1fr 300px; gap: 0; flex: 1; min-height: 0; }
.student-grid-section { display: flex; flex-direction: column; min-height: 0; border-right: 1px solid #e4e9f3; }
.class-list { flex: 1; overflow-y: auto; padding: 10px; display: flex; flex-direction: column; gap: 8px; }

/* 班级分组 */
.class-group { border: 1px solid #e4e9f3; border-radius: 8px; overflow: hidden; background: #fff; }
.class-group-header {
  display: flex; align-items: center; gap: 6px; width: 100%;
  padding: 9px 12px; background: #f8f9fc; border: none; cursor: pointer;
  font-size: 13px; font-weight: 600; color: #1e293b; transition: background 0.12s;
}
.class-group-header:hover { background: #f0f4ff; }
.class-group-header .chevron { width: 14px; height: 14px; color: #94a3b8; transition: transform 0.2s; flex-shrink: 0; }
.class-group.expanded .class-group-header .chevron { transform: rotate(90deg); }
.class-group-header .class-name { flex: 1; text-align: left; }
.class-group-header .class-stats { display: flex; gap: 4px; flex-shrink: 0; }
.stat-chip { padding: 1px 7px; border-radius: 4px; font-size: 10px; font-weight: 700; background: #f1f5f9; color: #64748b; }
.stat-chip.active-chip { background: #ecfdf5; color: #059669; }
.stat-chip.done-chip { background: #f0fdf4; color: #10b981; border: 1px solid #d1fae5; }
.class-group-body { display: flex; flex-direction: column; }

/* 学情卡片 */
.learn-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  padding: 10px;
}
.learn-card {
  display: flex; flex-direction: column; gap: 8px;
  padding: 12px 14px; border-radius: 10px;
  background: #fff; border: 1px solid #e8ecf2;
  cursor: pointer; transition: all 0.18s; text-align: left;
}
.learn-card:hover { border-color: #c7d2fe; box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08); }
.learn-card--active { border-color: #6366f1; box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.12); }
.learn-card--warn { border-color: #fcd34d; background: #fffbeb; }
.learn-card--warn.learn-card--active { border-color: #f59e0b; box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.12); }
.learn-card--done { border-color: #d1fae5; background: #f0fdf4; }
.learn-card--done.learn-card--active { border-color: #10b981; box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.12); }
.learn-card--done .learn-name { color: #059669; }
.learn-card--done .learn-card-step { background: #ecfdf5; color: #059669; }

.learn-card-top {
  display: flex; align-items: center; justify-content: space-between;
}
.learn-name {
  font-size: 14px; font-weight: 700; color: #1e293b;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.learn-dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.learn-dot.dot-on { background: #22c55e; box-shadow: 0 0 5px rgba(34, 197, 94, 0.4); }
.learn-dot.dot-off { background: #d1d5db; }
.learn-dot.dot-done { background: #10b981; }

.learn-card-step {
  font-size: 12px; color: #64748b;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  padding: 4px 8px; background: #f8f9fc; border-radius: 4px;
}

.learn-card-bottom {
  display: flex; gap: 0; margin-top: 2px;
  border-top: 1px solid #f1f5f9; padding-top: 8px;
}
.learn-stat {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px;
}
.learn-stat-label { font-size: 10px; color: #94a3b8; font-weight: 600; }
.learn-stat-val { font-size: 13px; font-weight: 700; color: #334155; font-variant-numeric: tabular-nums; }
.learn-stat.warn .learn-stat-val { color: #f59e0b; }

/* 状态标签 */
.state-pill {
  display: inline-flex; height: 22px; align-items: center; padding: 0 10px;
  border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap;
}
.state-pill.active { background: #ecfdf5; color: #059669; }
.state-pill.done { background: #f0fdf4; color: #10b981; border: 1px solid #d1fae5; }
.state-pill.idle { background: #f1f5f9; color: #64748b; }

/* 优先级 */
.priority-badge {
  display: inline-flex; height: 20px; align-items: center; padding: 0 8px;
  border-radius: 4px; font-size: 11px; font-weight: 700;
}
.priority-high { background: #fef2f2; color: #ef4444; }
.priority-medium { background: #fffbeb; color: #d97706; }
.priority-normal { background: #f1f5f9; color: #94a3b8; }

/* 详情面板 */
.detail-panel {
  display: flex; flex-direction: column; min-height: 0;
  border-left: 1px solid #e4e9f3; background: #fff;
  overflow: hidden;
}
.detail-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid #e4e9f3; flex-shrink: 0;
}
.detail-header h2 { color: #0f172a; font-size: 16px; font-weight: 700; }
.detail-class { color: #94a3b8; font-size: 12px; margin-top: 2px; }
.detail-stats {
  display: grid; grid-template-columns: repeat(2, 1fr);
  border-bottom: 1px solid #e4e9f3; flex-shrink: 0;
}
.detail-stats div { padding: 10px 14px; border-right: 1px solid #e4e9f3; border-bottom: 1px solid #e4e9f3; }
.detail-stats div:nth-child(2n) { border-right: 0; }
.detail-stats div:nth-last-child(-n+2) { border-bottom: 0; }
.detail-stats span { display: block; color: #94a3b8; font-size: 11px; font-weight: 600; }
.detail-stats strong { display: block; margin-top: 3px; color: #0f172a; font-size: 13px; font-weight: 700; }

.detail-alert {
  display: flex; align-items: flex-start; gap: 8px;
  margin: 12px 18px; padding: 10px 12px;
  border-radius: 8px; background: #fef2f2; border: 1px solid #fca5a5;
  color: #dc2626; font-size: 12px; font-weight: 600; line-height: 1.4;
}
.alert-icon { width: 16px; height: 16px; flex-shrink: 0; margin-top: 1px; }

.detail-section { padding: 12px 18px; border-bottom: 1px solid #e4e9f3; flex-shrink: 0; }
.detail-section h3 { color: #0f172a; font-size: 13px; font-weight: 700; margin-bottom: 8px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-item {
  padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 600;
  background: #f1f5f9; color: #475569;
}

.detail-quick-actions { display: flex; gap: 8px; padding: 12px 18px; flex-shrink: 0; }
.detail-quick-actions button {
  flex: 1; height: 32px; border-radius: 8px;
  border: 1px solid #e4e9f3; background: #f5f7fc; color: #64748b;
  font-size: 12px; font-weight: 600; transition: all 0.15s;
}
.detail-quick-actions button:hover { background: #eef2ff; color: #4f46e5; border-color: #a5b4fc; }

/* ====== 问答反馈 ====== */
.fb-tabs { display: flex; border-bottom: 1px solid #e4e9f3; flex-shrink: 0; }
.fb-tab {
  flex: 1; padding: 10px 0; font-size: 13px; font-weight: 600;
  color: #94a3b8; transition: color 0.15s; position: relative;
}
.fb-tab.active { color: #4f46e5; }
.fb-tab.active::after {
  content: ''; position: absolute; bottom: 0; left: 25%; right: 25%;
  height: 2px; background: #4f46e5; border-radius: 2px;
}

.fb-list-wrap { flex: 1; overflow-y: auto; }
.fb-list-head {
  display: grid;
  grid-template-columns: 140px 60px 1fr 140px 80px;
  gap: 0; padding: 0 16px; height: 36px; align-items: center;
  background: #f8f9fc; border-bottom: 1px solid #e4e9f3;
  position: sticky; top: 0; z-index: 1;
  font-size: 12px; font-weight: 600; color: #64748b;
}
.fb-list-row {
  display: grid;
  grid-template-columns: 140px 60px 1fr 140px 80px;
  gap: 0; padding: 0 16px; min-height: 48px; align-items: center;
  border-bottom: 1px solid #f1f5f9; cursor: pointer; transition: background 0.12s;
}
.fb-list-row:hover { background: #f5f7fc; }
.fb-list-row.unprocessed { background: #fffbeb; }
.fb-list-row.unprocessed:hover { background: #fef3c7; }

.fb-col-student { display: flex; align-items: center; gap: 8px; min-width: 0; }
.fb-avatar {
  width: 26px; height: 26px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: #fff;
  background: linear-gradient(135deg, #6366f1, #818cf8);
}
.fb-col-student strong { font-size: 13px; font-weight: 600; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fb-col-step { font-size: 13px; color: #64748b; }
.fb-col-question {
  font-size: 13px; color: #334155; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis; padding-right: 12px;
}
.fb-col-time { font-size: 12px; color: #94a3b8; }

.fb-badge {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 2px 8px; font-size: 11px; font-weight: 600; border-radius: 4px;
}
.fb-badge--pending { background: #fef3c7; color: #d97706; }
.fb-badge--done { background: #f1f5f9; color: #94a3b8; }

.fb-detail-top {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; border-bottom: 1px solid #e4e9f3; flex-shrink: 0;
}
.back-btn {
  display: flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 600;
  color: #64748b; transition: color 0.15s;
}
.back-btn:hover { color: #4f46e5; }
.back-icon { width: 16px; height: 16px; }
.fb-detail-time { color: #94a3b8; font-size: 11px; }
.fb-detail-body { flex: 1; overflow-y: auto; padding: 16px; }
.fb-detail-student {
  display: flex; align-items: center; gap: 8px; margin-bottom: 16px;
}
.fb-detail-student strong { color: #0f172a; font-size: 15px; }
.fb-detail-student span { color: #94a3b8; font-size: 12px; }
.fb-detail-block { border-radius: 12px; padding: 14px 16px; margin-bottom: 12px; }
.fb-detail-q { background: #f5f7fc; border: 1px solid #e4e9f3; }
.fb-detail-a { background: #eef2ff; border: 1px solid #c7d2fe; }
.fb-detail-role {
  display: block; font-size: 10px; font-weight: 700; text-transform: uppercase;
  letter-spacing: 0.05em; color: #94a3b8; margin-bottom: 6px;
}
.fb-detail-block p { font-size: 13px; line-height: 1.6; color: #334155; }
.fb-detail-q p { color: #0f172a; }
.fb-detail-footer { margin-top: 16px; padding-top: 12px; border-top: 1px solid #e4e9f3; }
.fb-process-btn {
  height: 32px; padding: 0 16px; border-radius: 8px;
  background: #4f46e5; color: #fff; font-size: 12px; font-weight: 600;
  transition: background 0.15s;
}
.fb-process-btn:hover { background: #4338ca; }
.processed-tag { color: #94a3b8; font-size: 12px; }

/* ====== 摄像头监控 ====== */
.camera-tab { display: flex; flex-direction: column; }
.camera-grid {
  flex: 1; overflow-y: auto; padding: 14px;
  display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 14px; align-content: start;
}
.camera-card {
  display: flex; flex-direction: column; gap: 8px; padding: 12px;
  border: 1px solid #e4e9f3; border-radius: 12px; background: #fff;
  color: inherit; text-align: left;
  transition: all 0.18s; cursor: pointer;
}
.camera-card:hover { border-color: #c7d2fe; box-shadow: 0 4px 14px rgba(99, 102, 241, 0.1); transform: translateY(-1px); }
.cam-preview-mini {
  position: relative; aspect-ratio: 16 / 10; border-radius: 8px; overflow: hidden;
  background: linear-gradient(135deg, #1e293b, #0f172a);
}
.cam-placeholder { display: flex; align-items: center; justify-content: center; height: 100%; }
.cam-placeholder svg { width: 36px; height: 36px; color: rgba(255, 255, 255, 0.2); }
.cam-badge {
  position: absolute; top: 6px; left: 6px; padding: 2px 8px;
  border-radius: 4px; font-size: 9px; font-weight: 700; letter-spacing: 0.05em;
}
.cam-badge.online { background: rgba(16, 185, 129, 0.85); color: #fff; }
.cam-badge.offline { background: rgba(100, 116, 139, 0.85); color: #cbd5e1; }
.cam-info strong { display: block; color: #0f172a; font-size: 14px; font-weight: 700; }
.cam-info span { display: block; color: #94a3b8; font-size: 12px; margin-top: 2px; }

/* 摄像头放大弹窗 */
.cam-modal-overlay {
  position: fixed; inset: 0; z-index: 200;
  display: flex; align-items: center; justify-content: center;
  background: rgba(15, 23, 42, 0.6); backdrop-filter: blur(3px);
}
.cam-modal {
  display: flex; flex-direction: column;
  width: 720px; max-width: 92vw; height: 480px; max-height: 80vh;
  background: #fff; border-radius: 14px; overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}
.cam-modal.expanded { width: 100vw; max-width: 100vw; height: 100vh; max-height: 100vh; border-radius: 0; }
.cam-large-preview { flex: 1; position: relative; background: #0a0e1a; overflow: hidden; }
.cam-video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.cam-loading {
  position: absolute; inset: 0; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  color: #64748b; font-size: 14px;
}
.spinner {
  width: 32px; height: 32px; border: 3px solid #e4e9f3;
  border-top-color: #4f46e5; border-radius: 50%; animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.cam-overlay-tl, .cam-overlay-tr, .cam-overlay-bl, .cam-overlay-br { position: absolute; z-index: 10; pointer-events: none; }
.cam-overlay-tl { top: 12px; left: 12px; }
.cam-overlay-tr { top: 12px; right: 12px; pointer-events: auto; }
.cam-overlay-bl { bottom: 12px; left: 12px; }
.cam-overlay-br { bottom: 12px; right: 12px; }
.rec-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #ef4444; animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: 0.4; } }
.rec-text { margin-left: 6px; color: #f87171; font-size: 11px; font-family: monospace; font-weight: 700; }
.live-text { color: #34d399; font-size: 11px; font-family: monospace; font-weight: 700; }
.cam-expand-btn {
  width: 32px; height: 32px; border-radius: 8px;
  background: rgba(0, 0, 0, 0.4); backdrop-filter: blur(4px);
  color: #fff; display: flex; align-items: center; justify-content: center; transition: background 0.15s;
}
.cam-expand-btn:hover { background: rgba(0, 0, 0, 0.6); }
.cam-expand-btn svg { width: 16px; height: 16px; }
.cam-overlay-br { background: rgba(0, 0, 0, 0.5); backdrop-filter: blur(6px); padding: 8px 14px; border-radius: 8px; }
.cam-overlay-br strong { display: block; color: #fff; font-size: 14px; font-weight: 700; }
.cam-overlay-br span { display: block; color: rgba(255, 255, 255, 0.7); font-size: 11px; margin-top: 2px; }
.cam-modal-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 16px; border-top: 1px solid #e4e9f3; flex-shrink: 0; background: #f8f9fc;
}
.cam-modal-actions { display: flex; gap: 8px; }
.cam-modal-actions button {
  height: 32px; padding: 0 16px; border-radius: 8px;
  border: 1px solid #e4e9f3; background: #fff; color: #64748b;
  font-size: 13px; font-weight: 600; transition: all 0.15s;
}
.cam-modal-actions button:hover:not(:disabled) { color: #4f46e5; border-color: #a5b4fc; background: #eef2ff; }
.cam-modal-actions button:disabled { opacity: 0.4; cursor: default; }
.cam-modal-close {
  height: 32px; padding: 0 18px; border-radius: 8px;
  background: #4f46e5; color: #fff; font-size: 13px; font-weight: 600; transition: background 0.15s;
}
.cam-modal-close:hover { background: #4338ca; }

/* ====== 报告分析 ====== */
.report-layout { display: grid; grid-template-columns: 320px 1fr; gap: 0; flex: 1; min-height: 0; }
.report-list-section {
  display: flex; flex-direction: column; min-height: 0;
  border-right: 1px solid #e4e9f3; background: #fff;
  padding: 12px;
}
.report-list-section h2 { color: #0f172a; font-size: 13px; font-weight: 700; flex-shrink: 0; margin-bottom: 10px; }
.report-class-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.report-detail-section {
  display: flex; flex-direction: column; min-height: 0;
  background: #fff; padding: 16px;
}

/* 班级分组 */
.class-group { border: 1px solid #e4e9f3; border-radius: 10px; overflow: hidden; }
.class-group-header {
  display: flex; align-items: center; gap: 6px; width: 100%;
  padding: 10px 12px; background: #f8f9fc; border: none; cursor: pointer;
  font-size: 13px; font-weight: 600; color: #1e293b; transition: background 0.12s;
}
.class-group-header:hover { background: #f0f4ff; }
.class-group-header .chevron { width: 14px; height: 14px; color: #94a3b8; transition: transform 0.2s; flex-shrink: 0; }
.class-group.expanded .class-group-header .chevron { transform: rotate(90deg); }
.class-group-header .class-name { flex: 1; text-align: left; }
.class-group-header .class-stats { display: flex; gap: 4px; flex-shrink: 0; }
.stat-chip { padding: 1px 7px; border-radius: 4px; font-size: 10px; font-weight: 700; }
.stat-chip.submitted { background: #dcfce7; color: #16a34a; }
.stat-chip.pending { background: #fef3c7; color: #d97706; }

/* 学生行 */
.class-group-body { display: flex; flex-direction: column; }
.student-report-row {
  display: flex; align-items: center; gap: 8px; width: 100%;
  padding: 8px 12px; border: none; border-bottom: 1px solid #f1f5f9;
  background: transparent; cursor: pointer; transition: background 0.12s; text-align: left;
}
.student-report-row:last-child { border-bottom: none; }
.student-report-row:hover { background: #f5f7fc; }
.student-report-row.active { background: #eef2ff; }
.stu-avatar {
  width: 28px; height: 28px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #fff;
}
.stu-avatar.submitted { background: linear-gradient(135deg, #6366f1, #818cf8); }
.stu-avatar.not-submitted { background: #cbd5e1; }
.stu-info { flex: 1; min-width: 0; }
.stu-info strong { display: block; font-size: 13px; font-weight: 600; color: #1e293b; }
.stu-info span { display: block; font-size: 11px; color: #94a3b8; margin-top: 1px; }
.stu-badges { display: flex; gap: 4px; flex-shrink: 0; }
.badge-help, .badge-err, .badge-ok {
  padding: 1px 6px; border-radius: 4px; font-size: 10px; font-weight: 600; white-space: nowrap;
}
.badge-help { background: #fef3c7; color: #d97706; }
.badge-err { background: #fee2e2; color: #dc2626; }
.badge-ok { background: #dcfce7; color: #16a34a; }
.stu-status.pending { font-size: 11px; color: #94a3b8; font-weight: 500; flex-shrink: 0; }

.rd-head {
  display: flex; align-items: center; justify-content: space-between;
  flex-shrink: 0; margin-bottom: 14px; padding-bottom: 14px; border-bottom: 1px solid #e4e9f3;
}
.rd-head h2 { color: #0f172a; font-size: 17px; font-weight: 700; }
.rd-head p { color: #94a3b8; font-size: 12px; font-weight: 600; margin-top: 2px; }
.rd-actions { display: flex; gap: 8px; }
.rd-actions button {
  height: 32px; border-radius: 8px; border: 1px solid #e4e9f3;
  background: #fff; color: #64748b; padding: 0 14px;
  font-size: 12px; font-weight: 600; transition: all 0.15s;
}
.rd-actions button:hover { color: #4f46e5; border-color: #a5b4fc; background: #eef2ff; }
.rd-actions .ai-btn {
  background: #4f46e5; color: #fff;
  border-color: transparent;
}
.rd-actions .ai-btn:hover { background: #4338ca; color: #fff; }

.rd-body { flex: 1; overflow-y: auto; }
.rd-section-title { color: #0f172a; font-size: 14px; font-weight: 700; margin-bottom: 10px; margin-top: 16px; }
.rd-section-title:first-child { margin-top: 0; }
.rd-summary {
  display: flex; align-items: center; gap: 0; margin-bottom: 14px;
  padding: 8px 0; border-bottom: 1px solid #f1f5f9;
}
.rd-summary span {
  font-size: 12px; color: #64748b; font-weight: 600;
  padding: 0 14px; border-right: 1px solid #e4e9f3;
}
.rd-summary span:first-child { padding-left: 0; }
.rd-summary span:last-child { border-right: 0; }
.rd-summary strong { font-size: 16px; font-weight: 700; color: #0f172a; margin-left: 4px; }

.data-check-table { width: 100%; border-collapse: collapse; overflow: hidden; border-radius: 10px; font-size: 12px; }
.data-check-table th { background: #f5f7fc; color: #475569; font-weight: 700; text-align: left; }
.data-check-table th, .data-check-table td { border: 1px solid #e4e9f3; padding: 10px; vertical-align: top; }

.rd-corrections { margin-top: 16px; }
.correction-item {
  margin-bottom: 8px; padding: 12px;
  border: 1px solid #fca5a5; border-radius: 10px; background: #fef2f2;
}
.corr-head { display: flex; align-items: center; justify-content: space-between; }
.corr-head span:first-child { color: #dc2626; font-size: 11px; font-weight: 600; }
.corr-type { padding: 2px 8px; border-radius: 4px; border: 1px solid #fca5a5; color: #dc2626; font-size: 10px; font-weight: 600; }
.correction-item p { margin-top: 6px; color: #64748b; font-size: 12px; line-height: 1.5; }

/* ====== 通用 ====== */
.empty-state {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: #94a3b8; font-size: 14px; text-align: center;
}
.empty-state.small { min-height: 80px; }
.bad-text { color: #ef4444 !important; font-weight: 700; }
.ok-text { color: #059669 !important; font-weight: 700; }

.custom-scroll::-webkit-scrollbar { width: 6px; height: 6px; }
.custom-scroll::-webkit-scrollbar-track { background: transparent; }
.custom-scroll::-webkit-scrollbar-thumb { background: rgba(99, 102, 241, 0.22); border-radius: 3px; }
.custom-scroll::-webkit-scrollbar-thumb:hover { background: rgba(99, 102, 241, 0.4); }

.slide-fade-enter-active { transition: all 0.25s ease; }
.slide-fade-enter-from { opacity: 0; transform: translateX(20px); }

@media (max-width: 1280px) {
  .overview-body { grid-template-columns: 1fr; }
  .detail-panel { max-height: 320px; }
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .report-layout { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .teacher-terminal { height: auto; min-height: 100vh; overflow-y: auto; }
  .terminal-head { flex-wrap: wrap; gap: 8px; padding: 12px; height: auto; }
  .terminal-nav { padding: 0 8px; overflow-x: auto; flex-basis: 100%; order: 3; }
  .nav-tab { padding: 3px 12px; }
  .tab-icon { width: 22px; height: 22px; }
  .tab-panel { height: auto; }
  .kpi-row { grid-template-columns: 1fr; }
  .rd-summary { flex-wrap: wrap; }
}
</style>
