<template>
  <div class="page-root flex flex-col flex-1 min-h-0 w-full overflow-hidden">
    <div v-if="booting" class="flex flex-col flex-1 items-center justify-center text-ink-muted text-sm gap-4">
      <div class="w-12 h-12 rounded-2xl brand-gradient flex items-center justify-center text-white font-bold shadow-brand chat-loading-avatar">智</div>
      <p>正在进入实验室<span class="chat-loading-dots ml-1"><span /><span /><span /></span></p>
    </div>

    <div v-else-if="bootError" class="flex flex-col flex-1 items-center justify-center px-6 text-center gap-4">
      <div class="w-14 h-14 rounded-2xl bg-red-50 border border-red-100 flex items-center justify-center text-red-500 text-2xl">!</div>
      <p class="text-red-600 text-sm max-w-md leading-relaxed">{{ bootError }}</p>
      <button type="button" class="btn-brand px-7 py-2.5 rounded-xl text-sm font-bold" @click="retryBoot">重新连接</button>
    </div>

    <div v-else class="lab-page flex flex-col flex-1 min-h-0 w-full overflow-hidden">
    <!-- 顶栏 -->
    <LabHeader
      :experiments="lab.experiments"
      :experiment-code="lab.experiment?.code || ''"
      :env-level="lab.envLevel"
      :dify-status="lab.difyStatus"
      :dify-status-loading="lab.difyStatusLoading"
      :switching="lab.switchingExperiment"
      @quick-stats="showQuickStats = true"
      @report="openReport"
      @experiment-change="onExperimentChange"
    />

    <!-- 主体：嵌入式工作台 — 贴顶栏底、贴左右边、贴底，仅保留顶部圆角 -->
    <div class="flex-1 flex flex-row overflow-hidden min-h-0 h-full px-3">
      <div
        ref="workspaceFrame"
        class="flex-1 workspace-frame workspace-frame-resizable min-h-0 h-full overflow-hidden"
        :style="workspaceColumns"
      >
      <!-- 左：连续工作区 -->
      <aside class="min-w-0 min-h-0 h-full flex flex-col overflow-hidden">
        <StepPanel
          :menu-labels="lab.experiment?.menuLabels || []"
          :active-step="lab.activeStep"
          :step="lab.stepConfig"
          @select="lab.selectStep"
          @tutorial="openTutorial"
          @instrument-guide="showInstrumentGuide = true"
        />
        <div class="workzone-divider" />
        <DeviceReadBar
          :busy="lab.deviceReadBusy"
          :disabled="currentSessionReadOnly"
          :data-ready="lab.composerDataReady"
          @read="onReadDevice"
          @photo-read="showReadingAssist = true"
        />
        <div class="workzone-middle">
          <StepWorkPanel :step="lab.stepConfig" />
        </div>
        <div class="workzone-bottom-dock">
          <BenchCameraPanel
            ref="benchCam"
            :env-check-enabled="lab.envCheckEnabled"
            :env-level="lab.envLevel"
            :env-hint="lab.envHint"
            :env-logs="lab.envLogs"
            :env-check-running="lab.envCheckRunning"
            :env-check-available="lab.envCheckAvailable"
            :bench-camera="lab.benchCamera"
            @toggle-env="lab.toggleEnvCheck"
            @env-check="(blob) => lab.runEnvCheck(blob)"
          />
        </div>
      </aside>

      <div
        class="workspace-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整左右区域宽度"
        title="拖动调整左右区域宽度"
        @pointerdown="startWorkspaceResize"
      />

      <!-- 右：实验台工作台（指导 / 数据 / 资料 / 课后） -->
      <section class="flex-1 min-w-0 min-h-0 h-full flex flex-col overflow-hidden">
        <LabWorkbench
          v-model="workbenchTab"
          :data-count="sessionDataCount"
          :file-count="fileCount"
          :experiment-code="lab.experiment?.code || ''"
          :experiment-name="lab.experiment?.name || ''"
          :step-title="lab.stepConfig?.title || ''"
          :student-name="lab.session?.studentName || ''"
          :session-finished="lab.session?.status === 'FINISHED'"
          :messages="lab.messages"
          :loading-assist="lab.loadingAssist"
          :submitting-data="lab.submittingData"
          :uploading-image="lab.uploadingImage"
          :image-preview="lab.composerImagePreview"
          :image-ready="!!lab.readyImageUrl"
          :data-attachment="lab.composerDataAttachment"
          :suggestions="quickSuggestions"
          :read-only="currentSessionReadOnly"
          :session-history="lab.sessionHistory"
          :current-session-id="lab.session?.id || 0"
          :session-history-loading="lab.sessionHistoryLoading"
          @new-session="startNewSession"
          @select-session="onSelectSession"
          @send="onSendMessage"
          @stop="lab.stopAssist()"
          @upload-image="onComposerUpload"
          @capture-image="onComposerCapture"
          @clear-image="lab.clearComposerImage()"
          @clear-data="lab.clearComposerDataAttachment()"
          @open-summary="openReport"
        />
      </section>
      </div>
    </div>

    <TutorialModal
      :visible="showTutorial"
      :experiment-name="lab.experiment?.name"
      :step="lab.stepConfig"
      :step-no="lab.activeStep"
      @close="showTutorial = false"
    />
    <ReportModal
      :visible="showReport"
      :report="reportData"
      :downloading="downloadingDocx"
      @close="showReport = false"
      @download-docx="downloadDocx"
    />
    <QuickStatsModal
      :visible="showQuickStats"
      :step-title="lab.stepConfig?.title"
      :help-count="lab.session?.helpCount ?? 0"
      :error-point-count="lab.session?.errorPointCount ?? 0"
      :tut-view-count="lab.session?.tutViewCount ?? 0"
      :lab-l3-count="lab.session?.labL3Count ?? 0"
      @close="showQuickStats = false"
    />
    <PreLabBriefModal
      :visible="showPreLabBrief"
      :experiment-code="lab.experiment?.code || ''"
      :experiment-name="lab.experiment?.name || ''"
      @close="showPreLabBrief = false"
    />
    <InstrumentGuideModal
      :visible="showInstrumentGuide"
      :experiment-code="lab.experiment?.code || ''"
      :experiment-name="lab.experiment?.name || ''"
      :step-no="lab.activeStep"
      :step-title="lab.stepConfig?.title || ''"
      :step-desc="lab.stepConfig?.desc || ''"
      :device-type="lab.stepConfig?.deviceType || lab.deviceType || ''"
      :data-fields="lab.stepConfig?.dataFields || []"
      @close="showInstrumentGuide = false"
    />
    <ReadingAssistModal
      :visible="showReadingAssist"
      :experiment-code="lab.experiment?.code || ''"
      :experiment-name="lab.experiment?.name || ''"
      :step-no="lab.activeStep"
      @close="showReadingAssist = false"
    />
    <TabletCameraCapture
      :visible="tabletCameraOpen"
      @close="tabletCameraOpen = false"
      @captured="onTabletCameraCaptured"
    />
    </div>

    <AppConfirmDialog
      :visible="appDialog.visible"
      :mode="appDialog.mode"
      :title="appDialog.title"
      :message="appDialog.message"
      :detail="appDialog.detail"
      :confirm-text="appDialog.confirmText"
      :cancel-text="appDialog.cancelText"
      @confirm="resolveAppDialog(true)"
      @cancel="resolveAppDialog(false)"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useLabStore } from '../stores/lab'
import { useAuthStore } from '../stores/auth'
import LabHeader from '../components/layout/LabHeader.vue'
import LabWorkbench from '../components/layout/LabWorkbench.vue'
import StepPanel from '../components/step/StepPanel.vue'
import StepWorkPanel from '../components/step/StepWorkPanel.vue'
import DeviceReadBar from '../components/data/DeviceReadBar.vue'
import BenchCameraPanel from '../components/monitor/BenchCameraPanel.vue'
import TabletCameraCapture from '../components/camera/TabletCameraCapture.vue'
import TutorialModal from '../components/modals/TutorialModal.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import QuickStatsModal from '../components/modals/QuickStatsModal.vue'
import PreLabBriefModal from '../components/modals/PreLabBriefModal.vue'
import InstrumentGuideModal from '../components/modals/InstrumentGuideModal.vue'
import ReadingAssistModal from '../components/modals/ReadingAssistModal.vue'
import AppConfirmDialog from '../components/modals/AppConfirmDialog.vue'
import { rememberVisit } from '../utils/experimentFlow'
import { studentFileApi } from '../api'

const lab = useLabStore()
const auth = useAuthStore()
const route = useRoute()

const queryExperimentCode = computed(() => String(route.query.exp || '').trim())

const currentExperimentCode = computed(() => lab.experiment?.code || queryExperimentCode.value)

watch(currentExperimentCode, (code) => {
  if (code) rememberVisit(code, 'lab')
  refreshFileCount()
}, { immediate: true })

watch(() => lab.session?.id, () => {
  refreshFileCount()
})

watch(
  () => lab.submittingData,
  (busy, wasBusy) => {
    if (wasBusy && !busy && sessionDataCount.value > 0) {
      workbenchTab.value = 'data'
    }
  }
)

const benchCam = ref(null)
const showTutorial = ref(false)
const showReport = ref(false)
const showQuickStats = ref(false)
const showPreLabBrief = ref(false)
const showInstrumentGuide = ref(false)
const showReadingAssist = ref(false)
const reportData = ref(null)
const downloadingDocx = ref(false)
const booting = ref(true)
const bootError = ref('')
const tabletCameraOpen = ref(false)
const appDialog = ref({
  visible: false,
  mode: 'confirm',
  title: '',
  message: '',
  detail: '',
  confirmText: '确定',
  cancelText: '取消',
  resolve: null
})
const workspaceFrame = ref(null)
const WORKSPACE_WIDTH_KEY = 'wxz_workspace_left_width'
const DEFAULT_WORKSPACE_LEFT_WIDTH = 400
const MIN_WORKSPACE_LEFT_WIDTH = 320
const workspaceLeftWidth = ref(readStoredWorkspaceWidth())
const workbenchTab = ref('guide')
const fileCount = ref(0)
let viewActive = true

const sessionDataCount = computed(() => Object.keys(lab.sessionDataByStep || {}).length)

const workspaceColumns = computed(() => ({
  gridTemplateColumns: `${workspaceLeftWidth.value}px 10px minmax(0, 1fr)`
}))

const currentSessionReadOnly = computed(() => lab.session?.status === 'FINISHED')

const quickSuggestions = computed(() => {
  const stepTitle = lab.stepConfig?.title
  const expName = lab.experiment?.name
  const step = stepTitle ? `「${stepTitle}」` : '这一步'
  return [
    `${step}有哪些常见错误？`,
    `${step}的关键测量参数是什么？`,
    expName ? `介绍一下${expName}的原理？` : '介绍一下本次实验的原理？'
  ]
})

async function refreshFileCount() {
  const code = lab.experiment?.code || queryExperimentCode.value
  if (!code) {
    fileCount.value = 0
    return
  }
  try {
    const { data } = await studentFileApi.list(code)
    fileCount.value = (data || []).length
  } catch {
    fileCount.value = 0
  }
}

async function bootstrap() {
  booting.value = true
  bootError.value = ''
  try {
    lab.loadBenchCamera().catch(() => {})
    lab.loadDifyStatus()
      .catch(() => null)
      .finally(() => {
        if (viewActive) lab.startDifyStatusTimer()
      })
    if (!lab.session?.id) {
      await lab.loadExperiments()
      if (!lab.experiments.length) {
        bootError.value = '暂无分配的实验，请联系教师为您分配后再进入。'
        return
      }
      const queryExp = queryExperimentCode.value
      const saved = queryExp || localStorage.getItem('wxz_exp')
      const code = lab.experiments.some((e) => e.code === saved)
        ? saved
        : lab.experiments[0].code
      const name = auth.displayName || localStorage.getItem('wxz_displayName') || '学生'
      await lab.loadExperiment(code)
      const restart = route.query.restart === '1'
      const latest = restart ? null : await lab.getLatestActiveSession(code)
      if (latest) {
        await lab.resumeSession(latest)
      } else {
        await lab.startSession(code, name, auth.studentClass || '')
      }
    }
    lab.startEnvTimer()
    maybeShowPreLabBrief(lab.experiment?.code || queryExperimentCode.value)
    await refreshFileCount()
  } catch (e) {
    bootError.value = e.response?.data?.message || e.message || '无法连接后端，请先启动 backend（mvn spring-boot:run）'
    lab.stopDifyStatusTimer()
    lab.stopEnvTimer()
  } finally {
    booting.value = false
  }
}

function retryBoot() {
  bootstrap()
}

function maybeShowPreLabBrief(code) {
  if (!code || bootError.value) return
  const key = `wxz_brief_${code}`
  if (!localStorage.getItem(key)) {
    showPreLabBrief.value = true
  }
}

function openAppDialog(options = {}) {
  return new Promise((resolve) => {
    appDialog.value = {
      visible: true,
      mode: options.mode || 'confirm',
      title: options.title || '确认操作',
      message: options.message || '',
      detail: options.detail || '',
      confirmText: options.confirmText || '确定',
      cancelText: options.cancelText || '取消',
      resolve
    }
  })
}

function resolveAppDialog(result) {
  const done = appDialog.value.resolve
  appDialog.value = {
    visible: false,
    mode: 'confirm',
    title: '',
    message: '',
    detail: '',
    confirmText: '确定',
    cancelText: '取消',
    resolve: null
  }
  if (done) done(result)
}

function showAppAlert(title, message = '') {
  return openAppDialog({
    mode: 'alert',
    title,
    message,
    confirmText: '知道了'
  })
}

function onGlobalAppAlert(event) {
  const detail = event.detail || {}
  showAppAlert(detail.title || '提示', detail.message || '')
}

async function onVisibilityChange() {
  if (document.hidden) {
    lab.stopDifyStatusTimer()
    return
  }
  await lab.loadDifyStatus({ silent: true })
  lab.startDifyStatusTimer()
}

function readStoredWorkspaceWidth() {
  const raw = Number(localStorage.getItem(WORKSPACE_WIDTH_KEY))
  return Number.isFinite(raw) && raw > 0 ? raw : DEFAULT_WORKSPACE_LEFT_WIDTH
}

function clampWorkspaceWidth(width) {
  const frameWidth = workspaceFrame.value?.getBoundingClientRect().width || 0
  const maxWidth = frameWidth ? Math.max(MIN_WORKSPACE_LEFT_WIDTH, Math.floor(frameWidth * 0.58)) : 640
  return Math.min(Math.max(Math.round(width), MIN_WORKSPACE_LEFT_WIDTH), maxWidth)
}

function startWorkspaceResize(event) {
  if (!workspaceFrame.value) return
  event.preventDefault()
  event.currentTarget?.setPointerCapture?.(event.pointerId)
  document.body.classList.add('is-resizing-workspace')

  const frameLeft = workspaceFrame.value.getBoundingClientRect().left

  const onPointerMove = (moveEvent) => {
    workspaceLeftWidth.value = clampWorkspaceWidth(moveEvent.clientX - frameLeft)
  }

  const onPointerUp = () => {
    localStorage.setItem(WORKSPACE_WIDTH_KEY, String(workspaceLeftWidth.value))
    document.body.classList.remove('is-resizing-workspace')
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('pointercancel', onPointerUp)
  }

  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp, { once: true })
  window.addEventListener('pointercancel', onPointerUp, { once: true })
}

onMounted(() => {
  viewActive = true
  window.addEventListener('wxz-app-alert', onGlobalAppAlert)
  document.addEventListener('visibilitychange', onVisibilityChange)
  lab.setEnvCaptureFn(() => benchCam.value?.captureFrame?.())
  lab.setEnvEnsureCamFn(() => benchCam.value?.ensureCameraReady?.())
  bootstrap()
})

onUnmounted(() => {
  viewActive = false
  window.removeEventListener('wxz-app-alert', onGlobalAppAlert)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  lab.stopDifyStatusTimer()
  lab.stopEnvTimer()
  lab.teardownDevice()
})

async function uploadTo(file) {
  try {
    await lab.uploadImage(file)
  } catch (e) {
    const msg = lab.uploadError || e.response?.data?.message || e.message || '图片上传失败'
    await showAppAlert('上传失败', msg)
  }
}

function onComposerUpload(file) {
  return uploadTo(file)
}

function onComposerCapture() {
  tabletCameraOpen.value = true
}

async function onTabletCameraCaptured(file) {
  tabletCameraOpen.value = false
  return uploadTo(file)
}

async function onSendMessage(text) {
  const ok = await lab.sendMessage(text)
  refreshFileCount()
  return ok
}

async function onReadDevice() {
  if (currentSessionReadOnly.value) {
    await showAppAlert('历史会话仅供查看', '已完成的历史会话不能读取仪器数据，请新建会话后再操作。')
    return
  }
  await lab.loadDeviceDataIntoComposer()
}


async function onSelectSession(item) {
  await lab.resumeSession(item)
  if (item.status === 'ACTIVE') {
    lab.startEnvTimer()
  } else {
    lab.stopEnvTimer()
  }
}

async function startNewSession() {
  const code = lab.experiment?.code || lab.experiments[0]?.code || localStorage.getItem('wxz_exp') || ''
  const name = auth.displayName || localStorage.getItem('wxz_displayName') || lab.session?.studentName || '学生'
  if (lab.session?.status === 'ACTIVE') {
    const ok = await openAppDialog({
      title: '开始新的实验会话？',
      message: '当前实验会话会保留在历史记录中。',
      detail: '新会话会从第 1 步重新开始，右侧问答也会使用新的上下文。',
      confirmText: '新建会话',
      cancelText: '继续当前会话'
    })
    if (!ok) return
  }
  await lab.startSession(code, name, auth.studentClass || '')
  lab.startEnvTimer()
}

async function onExperimentChange(code) {
  if (!code || code === lab.experiment?.code) return
  const target = lab.experiments.find((e) => e.code === code)
  const label = target?.name || code
  const ok = await openAppDialog({
    title: `切换到「${label}」？`,
    message: '将为目标实验开始新的实验会话。',
    detail: '当前会话会保留在历史记录中，之后可以从“历史”入口查看或继续。',
    confirmText: '切换实验',
    cancelText: '取消'
  })
  if (!ok) {
    return
  }
  try {
    await lab.switchExperiment(code)
    maybeShowPreLabBrief(code)
  } catch (e) {
    await showAppAlert('切换失败', e.response?.data?.message || e.message || '切换实验失败')
  }
}

async function openTutorial() {
  await lab.openTutorial()
  showTutorial.value = true
}

async function openReport() {
  reportData.value = await lab.finishSession()
  showReport.value = true
}

async function downloadDocx() {
  downloadingDocx.value = true
  try {
    await lab.downloadReportDocx()
  } finally {
    downloadingDocx.value = false
  }
}
</script>
