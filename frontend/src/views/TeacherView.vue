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
      <div class="head-right">
        <div class="user-avatar brand-gradient">{{ userInitial }}</div>
        <span class="user-name">{{ auth.displayName || auth.username }}</span>
        <span class="user-sep" aria-hidden="true" />
        <button type="button" class="logout-link" @click="logout">退出</button>
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
                <button type="button" @click="activeTab = 'camera'">查看摄像头</button>
                <button type="button" @click="activeTab = 'feedback'; selectedFeedbackUserId = selectedStudent.userId">查看问答反馈</button>
              </div>
            </aside>
          </transition>
        </div>
      </div>

      <!-- ========== Tab 2: 学生管理 ========== -->
      <div v-show="activeTab === 'students'" class="tab-panel student-manage-panel">
        <StudentManagePanel
          :experiment-code="selectedExpCode"
          :experiment-name="currentExperimentName"
          :students="experimentStudents"
          :all-students="students"
          @refresh="refresh"
        />
      </div>

      <!-- ========== Tab 3: 问答反馈 ========== -->
      <div v-show="activeTab === 'feedback'" class="tab-panel">
        <!-- 列表视图 -->
        <template v-if="!selectedFeedbackId">
          <div class="fb-tabs">
            <button type="button" class="fb-tab" :class="{ active: feedbackFilter === 'PENDING' }" @click="feedbackFilter = 'PENDING'">
              待审核 ({{ unprocessedCount }})
            </button>
            <button type="button" class="fb-tab" :class="{ active: feedbackFilter === 'HELPFUL' }" @click="feedbackFilter = 'HELPFUL'">
              有帮助 ({{ helpfulReviewedCount }})
            </button>
            <button type="button" class="fb-tab" :class="{ active: feedbackFilter === 'NOT_HELPFUL' }" @click="feedbackFilter = 'NOT_HELPFUL'">
              无帮助 ({{ notHelpfulReviewedCount }})
            </button>
          </div>

          <div v-if="!filteredFeedback.length" class="empty-state">{{ feedbackEmptyHint }}</div>
          <div v-else class="fb-list-wrap custom-scroll">
            <div class="fb-list-head">
              <span class="fb-col-student">学生</span>
              <span class="fb-col-step">步骤</span>
              <span class="fb-col-question">提问内容</span>
              <span class="fb-col-reply">回复内容</span>
              <span class="fb-col-time">时间</span>
              <span class="fb-col-status">状态</span>
            </div>
            <div
              v-for="item in filteredFeedback"
              :key="item.id"
              class="fb-list-row"
              :class="{ unprocessed: !isFeedbackProcessed(item) }"
              @click="selectedFeedbackId = item.id"
            >
              <span class="fb-col-student">
                <span class="fb-avatar">{{ (item.studentName || '?').charAt(0) }}</span>
                <strong>{{ item.studentName }}</strong>
              </span>
              <span class="fb-col-step">{{ item.stepId || '—' }}</span>
              <span class="fb-col-question" :title="item.userQuestion || ''">{{ item.userQuestion || '（学生未输入问题文本）' }}</span>
              <span class="fb-col-reply" :title="replyPreview(item.aiReply)">{{ replyPreview(item.aiReply) || '（暂无 AI 回复内容）' }}</span>
              <span class="fb-col-time">{{ formatTime(item.createdAt) }}</span>
              <span class="fb-col-status">
                <span v-if="!isFeedbackProcessed(item)" class="fb-badge fb-badge--pending">
                  学生：{{ ratingLabel(studentRatingOf(item)) }}
                </span>
                <span v-else class="fb-badge" :class="item.rating === 'HELPFUL' ? 'fb-badge--ok' : 'fb-badge--bad'">
                  {{ ratingLabel(item.rating) }}
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

            <div class="fb-student-mark">
              学生标记为「{{ ratingLabel(studentRatingOf(selectedFeedbackDetail)) }}」
              <span v-if="isFeedbackProcessed(selectedFeedbackDetail) && studentRatingOf(selectedFeedbackDetail) !== selectedFeedbackDetail.rating">
                ，教师已改判为「{{ ratingLabel(selectedFeedbackDetail.rating) }}」
              </span>
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
              <p class="fb-review-hint">请审核这条问答，判定应进入有帮助还是无帮助。</p>
              <div class="fb-review-actions">
                <button
                  type="button"
                  class="fb-process-btn fb-process-btn--ok"
                  :disabled="reviewingFeedback"
                  :class="{ current: isFeedbackProcessed(selectedFeedbackDetail) && selectedFeedbackDetail.rating === 'HELPFUL' }"
                  @click="reviewFeedback(selectedFeedbackDetail.id, 'HELPFUL')"
                >
                  {{ reviewingFeedback ? '提交中…' : '判为有帮助' }}
                </button>
                <button
                  type="button"
                  class="fb-process-btn fb-process-btn--bad"
                  :disabled="reviewingFeedback"
                  :class="{ current: isFeedbackProcessed(selectedFeedbackDetail) && selectedFeedbackDetail.rating === 'NOT_HELPFUL' }"
                  @click="reviewFeedback(selectedFeedbackDetail.id, 'NOT_HELPFUL')"
                >
                  {{ reviewingFeedback ? '提交中…' : '判为无帮助' }}
                </button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- ========== Tab 4: 摄像头监控 ========== -->
      <div v-show="activeTab === 'camera'" class="tab-panel camera-tab">
        <div v-if="!benchStations.length" class="empty-state">暂无工位学生。</div>
        <div v-else class="bench-grid custom-scroll">
          <TeacherBenchStationCard
            v-for="(station, index) in benchStations"
            :key="station.userId"
            :station="station"
            :bench-label="`工位 ${index + 1}`"
            :stream-live="activeTab === 'camera'"
            :stream-url="benchCameraStreamUrl"
            :bench-camera="benchCameraConfig"
            :camera-configured="benchCameraConfigured"
            :logs="benchEnvLogs[station.sessionId] || []"
            :env-check-enabled="!!station.envCheckEnabled"
            :checking="!!benchChecking[station.sessionId]"
            :env-check-available="envCheckAvailable"
            @toggle-env="(enabled) => toggleStationEnvCheck(station, enabled)"
            @manual-check="runStationEnvCheck(station)"
          />
        </div>
      </div>

      <!-- ========== Tab 5: 报告分析 ========== -->
      <div v-show="activeTab === 'report'" class="tab-panel">
        <div class="report-layout">
          <section class="report-list-section">
            <h2>实验报告 ({{ submittedReportCount }}/{{ cameraStudents.length }})</h2>
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
                    @click="stu.submitted && selectReportItem(stu.report?.sessionId || stu.sessionId)"
                  >
                    <div class="stu-info">
                      <strong>{{ stu.studentName }}</strong>
                      <span>{{ stu.submitted ? formatTime(stu.report?.endTime || stu.report?.startTime) : '未提交' }}</span>
                    </div>
                    <span v-if="stu.submitted" class="stu-badges">
                      <span v-if="stu.report?.gradingCompleted" class="badge-ok">已批改</span>
                      <span v-else-if="stu.report?.aiReviewScore != null" class="badge-help">已预评</span>
                      <span v-if="stu.report?.helpCount" class="badge-help">求助 {{ stu.report.helpCount }}</span>
                      <span v-if="stu.report?.errorPointCount" class="badge-err">纠错 {{ stu.report.errorPointCount }}</span>
                      <span v-if="!stu.report?.helpCount && !stu.report?.errorPointCount && !stu.report?.gradingCompleted && stu.report?.aiReviewScore == null" class="badge-ok">正常</span>
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
                  <button type="button" @click="downloadReport(selectedReportItem.sessionId, selectedReportItem.experimentName)">下载</button>
                </div>
              </header>

              <div class="rd-split">
                <div v-if="previewLoading" class="empty-state">正在读取报告数据…</div>
                <div v-else-if="selectedReportPreview" class="rd-body custom-scroll">
                  <StudentReportDocument :report="selectedReportPreview" compact />
                </div>
                <div v-else class="empty-state">暂无报告数据。</div>
                <ReportGradingPanel
                  :session-id="selectedReportItem.sessionId"
                  v-model:score="gradeScore"
                  v-model:comment="gradeComment"
                  :max-score="10"
                  :review-error="reviewError"
                  :reviewing="reviewingReport"
                  :saving="savingGrade"
                  :grading-completed="gradingCompleted"
                  :grade-band="gradeBand"
                  @ai-review="runAiReviewInPlace"
                  @finish="finishGrade"
                />
              </div>
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
      :review-score="gradeScore"
      :review-comment="aiReviewComment"
      @close="closeReport"
      @download-docx="downloadSelectedReport"
      @ai-review="runAiReview"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { experimentApi, teacherApi, systemApi } from '../api'
import { useAuthStore } from '../stores/auth'
import ReportModal from '../components/modals/ReportModal.vue'
import ExperimentSelect from '../components/layout/ExperimentSelect.vue'
import StudentReportDocument from '../components/teacher/StudentReportDocument.vue'
import ReportGradingPanel from '../components/teacher/ReportGradingPanel.vue'
import StudentManagePanel from '../components/teacher/StudentManagePanel.vue'
import TeacherBenchStationCard from '../components/teacher/TeacherBenchStationCard.vue'

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
const feedbackFilter = ref('PENDING')
const reviewingFeedback = ref(false)
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
const gradeScore = ref(null)
const gradeComment = ref('')
const aiReviewComment = ref('')
const gradeBand = ref('')
const reviewDimensions = ref([])
const gradingCompleted = ref(false)
const savingGrade = ref(false)

const benchEnvLogs = ref({})
const benchChecking = ref({})
const difyStatus = ref(null)
let cameraPollTimer = null

const benchCameraStreamUrl = computed(() => benchCameraConfig.value?.browserStreamUrl || '')
const benchCameraConfigured = computed(() => {
  const cfg = benchCameraConfig.value
  if (!cfg?.enabled) return false
  return !!(cfg.browserStreamUrl || cfg.browserStreamUrlDirect)
})

const envCheckAvailable = computed(() => difyStatus.value?.available !== false)

const benchStations = computed(() => cameraStudents.value)

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

const experimentStudents = computed(() => {
  if (!selectedExpCode.value) return students.value
  return students.value.filter((s) =>
    (s.assignedExperimentCodes || []).includes(selectedExpCode.value)
  )
})

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
      stepTitle: '',
      cameraActive: false
    }))
})

const selectedStudent = computed(() =>
  classroomStudents.value.find((s) => s.userId === selectedUserId.value) || null
)

const unprocessedCount = computed(() =>
  currentFeedback.value.filter((f) => !isFeedbackProcessed(f)).length
)
const helpfulReviewedCount = computed(() =>
  currentFeedback.value.filter((f) => isFeedbackProcessed(f) && f.rating === 'HELPFUL').length
)
const notHelpfulReviewedCount = computed(() =>
  currentFeedback.value.filter((f) => isFeedbackProcessed(f) && f.rating === 'NOT_HELPFUL').length
)

const feedbackEmptyHint = computed(() => {
  if (feedbackFilter.value === 'PENDING') return '暂无待审核的问答反馈。'
  if (feedbackFilter.value === 'HELPFUL') return '暂无教师判定为有帮助的问答。'
  return '暂无教师判定为无帮助的问答。'
})

const filteredFeedback = computed(() => {
  const list = currentFeedback.value.filter((f) => {
    if (feedbackFilter.value === 'PENDING') return !isFeedbackProcessed(f)
    return isFeedbackProcessed(f) && f.rating === feedbackFilter.value
  })
  return list.sort((a, b) => timeValue(b.createdAt) - timeValue(a.createdAt))
})

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
    const report = reportMap.get(s.userId)
    const submitted = !!(s.reportCompleted || report?.reportCompleted)
    group.students.push({
      ...s,
      report: report || null,
      submitted
    })
    group.total++
    if (submitted) group.submitted++
  })
  return [...classMap.values()].sort((a, b) => a.className.localeCompare(b.className))
})

const submittedReportCount = computed(() =>
  reportsByClass.value.reduce((n, g) => n + g.submitted, 0)
)

function toggleClassExpand(className) {
  expandedClasses.value = { ...expandedClasses.value, [className]: !expandedClasses.value[className] }
}

const selectedReportItem = computed(() => {
  const fromList = sortedReports.value.find((r) => r.sessionId === selectedReportId.value)
  if (fromList) return fromList
  for (const grp of reportsByClass.value) {
    for (const stu of grp.students) {
      const sid = stu.report?.sessionId || stu.sessionId
      if (sid && sid === selectedReportId.value) {
        return {
          ...stu.report,
          sessionId: sid,
          studentName: stu.studentName,
          studentClass: stu.studentClass,
          experimentName: stu.experimentName || stu.report?.experimentName
        }
      }
    }
  }
  return null
})

const navTabs = computed(() => [
  {
    key: 'overview', label: '学情总览', color: '#6366f1',
    icon: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
    iconFill: 'M3 13h8V3H3v10zm10 8h8V11h-8v10z',
    badge: ''
  },
  {
    key: 'students', label: '学生管理', color: '#0ea5e9',
    icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z',
    iconFill: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1z',
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
    badge: ''
  }
])

watch(activeTab, (tab) => {
  if (tab === 'report' && !selectedReportId.value && sortedReports.value.length) {
    selectedReportId.value = sortedReports.value[0].sessionId
  }
  if (tab === 'camera') {
    loadDifyStatus()
    loadAllBenchEnvLogs()
    startCameraPoll()
  } else {
    stopCameraPoll()
  }
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
  stopCameraPoll()
})

async function refreshClassroomForCamera() {
  if (!selectedExpCode.value) return
  try {
    const { data } = await teacherApi.classroom({ experimentCode: selectedExpCode.value })
    classroom.value = data
    if (activeTab.value === 'camera') {
      await loadAllBenchEnvLogs()
    }
  } catch {
    // 轮询失败不阻断界面
  }
}

function startCameraPoll() {
  stopCameraPoll()
  refreshClassroomForCamera()
  cameraPollTimer = setInterval(refreshClassroomForCamera, 5000)
}

function stopCameraPoll() {
  if (cameraPollTimer) {
    clearInterval(cameraPollTimer)
    cameraPollTimer = null
  }
}

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

async function loadDifyStatus() {
  try {
    const { data } = await systemApi.difyStatus()
    difyStatus.value = data
  } catch {
    difyStatus.value = { available: false }
  }
}

async function loadAllBenchEnvLogs() {
  const sessionIds = benchStations.value.map((s) => s.sessionId).filter(Boolean)
  if (!sessionIds.length) {
    benchEnvLogs.value = {}
    return
  }
  const results = await Promise.allSettled(
    sessionIds.map((sid) => teacherApi.sessionEnvLogs(sid))
  )
  const next = {}
  sessionIds.forEach((sid, i) => {
    const result = results[i]
    next[sid] = result.status === 'fulfilled' ? (result.value.data || []) : (benchEnvLogs.value[sid] || [])
  })
  benchEnvLogs.value = next
}

async function toggleStationEnvCheck(station, enabled) {
  if (!station?.sessionId) return
  try {
    await teacherApi.setEnvCheckEnabled(station.sessionId, { enabled })
    if (classroom.value?.students) {
      const target = classroom.value.students.find((s) => s.sessionId === station.sessionId)
      if (target) target.envCheckEnabled = enabled
    }
  } catch (e) {
    window.alert(e.response?.data?.message || e.message || '更新巡检开关失败')
  }
}

async function runStationEnvCheck(station) {
  const sessionId = station?.sessionId
  if (!sessionId || benchChecking.value[sessionId]) return
  benchChecking.value = { ...benchChecking.value, [sessionId]: true }
  try {
    await teacherApi.triggerEnvCheck(sessionId, {})
    const { data } = await teacherApi.sessionEnvLogs(sessionId)
    benchEnvLogs.value = { ...benchEnvLogs.value, [sessionId]: data || [] }
    await refreshClassroomForCamera()
  } catch (e) {
    window.alert(e.response?.data?.message || e.message || '巡检检查失败')
  } finally {
    benchChecking.value = { ...benchChecking.value, [sessionId]: false }
  }
}

async function loadPreviewReport(sessionId) {
  if (!sessionId) {
    selectedReportPreview.value = null
    resetGradePanel()
    return
  }
  previewLoading.value = true
  try {
    const { data } = await teacherApi.report(sessionId)
    selectedReportPreview.value = data
    applyGradeFromReport(data)
  } catch {
    selectedReportPreview.value = null
    resetGradePanel()
  } finally {
    previewLoading.value = false
  }
}

function resetGradePanel() {
  gradeScore.value = null
  gradeComment.value = ''
  aiReviewComment.value = ''
  gradeBand.value = ''
  reviewDimensions.value = []
  gradingCompleted.value = false
  reviewText.value = ''
  reviewError.value = ''
  reviewFromDify.value = true
}

function applyGradeFromReport(data) {
  if (!data) {
    resetGradePanel()
    return
  }
  const json = data.aiReviewJson && typeof data.aiReviewJson === 'object' ? data.aiReviewJson : {}
  const savedScore = data.teacherScore ?? data.aiReviewScore ?? json.score
  gradeScore.value = savedScore == null || savedScore === '' ? null : Number(savedScore)
  aiReviewComment.value = data.aiReviewComment || json.comment || ''
  gradeComment.value = data.teacherComment || aiReviewComment.value
  gradeBand.value = json.gradeBand || ''
  reviewDimensions.value = Array.isArray(json.dimensions) ? json.dimensions : []
  gradingCompleted.value = !!data.gradingCompleted
  reviewText.value = aiReviewComment.value
  reviewError.value = ''
  reviewFromDify.value = data.aiReviewScore != null || !!aiReviewComment.value
}

async function openReport(sessionId) {
  if (!sessionId) return
  const { data } = await teacherApi.report(sessionId)
  selectedReport.value = data
  selectedReportSessionId.value = sessionId
  reportVisible.value = true
}

function closeReport() {
  reportVisible.value = false
}

function applyReviewResponse(data) {
  reviewText.value = data?.text || '未返回预评内容'
  reviewFromDify.value = data?.fromDify !== false
  const payload = data?.data && typeof data.data === 'object' ? data.data : {}
  const score = data?.score ?? payload.score
  gradeScore.value = score == null || score === '' ? gradeScore.value : Number(score)
  aiReviewComment.value = data?.comment || payload.comment || ''
  if (!aiReviewComment.value) {
    const raw = String(data?.text || '').trim()
    if (raw && !raw.startsWith('{') && !raw.startsWith('```')) {
      aiReviewComment.value = raw
    }
  }
  if (aiReviewComment.value) {
    gradeComment.value = aiReviewComment.value
  }
  gradeBand.value = data?.gradeBand || payload.gradeBand || ''
  reviewDimensions.value = Array.isArray(payload.dimensions) ? payload.dimensions : []
  gradingCompleted.value = false
}

async function runAiReviewInPlace() {
  if (selectedReportItem.value?.sessionId) {
    selectedReportSessionId.value = selectedReportItem.value.sessionId
  }
  await runAiReview()
}

async function runAiReview() {
  const sessionId = selectedReportSessionId.value || selectedReportId.value
  if (!sessionId || reviewingReport.value) return
  selectedReportSessionId.value = sessionId
  reviewingReport.value = true
  reviewError.value = ''
  try {
    const { data } = await teacherApi.reviewReport(sessionId)
    applyReviewResponse(data)
  } catch (e) {
    reviewError.value = e.response?.data?.message || e.message || 'AI 预评失败'
  } finally {
    reviewingReport.value = false
  }
}

async function finishGrade() {
  const sessionId = selectedReportId.value || selectedReportSessionId.value
  if (!sessionId || savingGrade.value || gradeScore.value == null) return
  savingGrade.value = true
  reviewError.value = ''
  try {
    const { data } = await teacherApi.completeGrade(sessionId, {
      score: Number(gradeScore.value),
      comment: gradeComment.value || ''
    })
    applyGradeFromReport(data)
    gradingCompleted.value = true
    await loadExperimentData()
  } catch (e) {
    reviewError.value = e.response?.data?.message || e.message || '保存成绩失败'
  } finally {
    savingGrade.value = false
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

async function reviewFeedback(feedbackId, rating) {
  if (!feedbackId || reviewingFeedback.value) return
  reviewingFeedback.value = true
  try {
    await teacherApi.markFeedbackProcessed(feedbackId, { rating })
    selectedFeedbackId.value = null
    feedbackFilter.value = rating
    await loadExperimentData()
  } finally {
    reviewingFeedback.value = false
  }
}

async function loadBenchCameraConfig() {
  try {
    const { data } = await systemApi.benchCamera()
    benchCameraConfig.value = data
  } catch {
    benchCameraConfig.value = null
  }
}

function priorityLabel(p) {
  return { high: '高', medium: '中', normal: '低' }[p] || '低'
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

function isFeedbackProcessed(item) {
  return item?.processed === true || item?.processed === 1 || item?.processed === 'true'
}

function ratingLabel(rating) {
  if (rating === 'HELPFUL') return '有帮助'
  if (rating === 'NOT_HELPFUL') return '无帮助'
  return '—'
}

function studentRatingOf(item) {
  return item?.studentRating || item?.rating || ''
}

function plainText(text) {
  return String(text || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#>*`_\-]/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function replyPreview(text) {
  return plainText(text).replace(/\s+/g, ' ').trim()
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
  position: relative;
}
.head-left, .head-right { display: flex; align-items: center; gap: 10px; position: relative; z-index: 10; }
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
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
  display: flex; align-items: stretch; justify-content: center; gap: 4px;
  pointer-events: none;
  z-index: 5;
}
.terminal-nav > * {
  pointer-events: auto;
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
.tab-panel:has(.report-layout) { padding: 0; }
.student-manage-panel { padding: 0; }

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
.class-list { flex: 1; overflow-y: auto; padding: 10px 10px 16px; display: flex; flex-direction: column; gap: 8px; }

/* 班级分组（学情总览：不用 overflow:hidden，避免卡片底部边框/阴影被裁切） */
.overview-panel .class-group {
  border: 1px solid #e4e9f3;
  border-radius: 8px;
  background: #fff;
  overflow: visible;
}
.overview-panel .class-group:not(.expanded) .class-group-header {
  border-radius: 8px;
}
.overview-panel .class-group.expanded .class-group-header {
  border-radius: 8px 8px 0 0;
  border-bottom: 1px solid #eef2f7;
}
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
  padding: 10px 10px 14px;
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
  grid-template-columns: 132px 52px minmax(120px, 1fr) minmax(160px, 1.3fr) 108px 128px;
  gap: 0; padding: 0 16px; height: 36px; align-items: center;
  background: #f8f9fc; border-bottom: 1px solid #e4e9f3;
  position: sticky; top: 0; z-index: 1;
  font-size: 12px; font-weight: 600; color: #64748b;
}
.fb-list-row {
  display: grid;
  grid-template-columns: 132px 52px minmax(120px, 1fr) minmax(160px, 1.3fr) 108px 128px;
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
.fb-col-question,
.fb-col-reply {
  font-size: 13px; color: #334155; white-space: nowrap;
  overflow: hidden; text-overflow: ellipsis; padding-right: 12px;
}
.fb-col-reply { color: #475569; }
.fb-col-time { font-size: 12px; color: #94a3b8; }
.fb-col-status { text-align: left; }

.fb-badge {
  display: inline-flex; align-items: center; justify-content: center;
  padding: 2px 8px; font-size: 11px; font-weight: 600; border-radius: 4px;
}
.fb-badge--pending { background: #fef3c7; color: #d97706; }
.fb-badge--done { background: #f1f5f9; color: #94a3b8; }
.fb-badge--ok { background: #d1fae5; color: #047857; }
.fb-badge--bad { background: #fee2e2; color: #b91c1c; }

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
.fb-student-mark {
  margin-bottom: 14px; font-size: 13px; color: #64748b;
}
.fb-detail-footer { margin-top: 16px; padding-top: 12px; border-top: 1px solid #e4e9f3; }
.fb-review-hint { font-size: 12px; color: #64748b; margin-bottom: 10px; }
.fb-review-actions { display: flex; gap: 8px; }
.fb-process-btn {
  height: 36px; padding: 0 16px; border-radius: 8px;
  font-size: 13px; font-weight: 600; transition: all 0.15s;
}
.fb-process-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.fb-process-btn--ok { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
.fb-process-btn--ok:hover:not(:disabled) { background: #d1fae5; }
.fb-process-btn--ok.current { background: #059669; color: #fff; border-color: #059669; }
.fb-process-btn--bad { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; }
.fb-process-btn--bad:hover:not(:disabled) { background: #fee2e2; }
.fb-process-btn--bad.current { background: #dc2626; color: #fff; border-color: #dc2626; }
.processed-tag { color: #94a3b8; font-size: 12px; }

/* ====== 摄像头监控 ====== */
.camera-tab {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.bench-grid {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
  align-content: start;
}

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
  background: #fff; padding: 16px; overflow: hidden;
}

/* 班级分组（报告分析页） */
.report-layout .class-group { border: 1px solid #e4e9f3; border-radius: 10px; overflow: hidden; }
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

.rd-body { flex: 1; overflow-y: auto; background: #f8fafc; padding: 12px; border-radius: 10px; }
.rd-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 0;
  flex: 1;
  min-height: 0;
}
.rd-split .rd-body { border-radius: 10px 0 0 10px; }
.rd-split .empty-state { padding: 24px; }
.rd-section-title { color: #0f172a; font-size: 14px; font-weight: 700; margin-bottom: 10px; margin-top: 16px; }
.rd-section-title:first-child { margin-top: 0; }
.rd-student-report { display: flex; flex-direction: column; gap: 12px; }
.rd-student-section { padding: 12px 14px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.rd-student-section h4 { margin: 0 0 8px; font-size: 13px; font-weight: 700; color: #334155; }
.rd-student-html { font-size: 13px; line-height: 1.7; color: #1e293b; overflow: auto; }
.rd-student-html :deep(img) { max-width: 100%; height: auto; border-radius: 6px; }
.rd-student-html :deep(table) { width: 100%; border-collapse: collapse; }
.rd-student-html :deep(th), .rd-student-html :deep(td) { border: 1px solid #e2e8f0; padding: 4px 8px; }
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
  .rd-split { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .teacher-terminal { height: auto; min-height: 100vh; overflow-y: auto; }
  .terminal-head { flex-wrap: wrap; gap: 8px; padding: 12px; height: auto; }
  .terminal-nav { position: relative; left: auto; top: auto; bottom: auto; transform: none; flex-basis: 100%; order: 3; padding: 0 8px; overflow-x: auto; pointer-events: auto; }
  .nav-tab { padding: 3px 12px; }
  .tab-icon { width: 22px; height: 22px; }
  .tab-panel { height: auto; }
  .kpi-row { grid-template-columns: 1fr; }
  .rd-summary { flex-wrap: wrap; }
}
</style>
