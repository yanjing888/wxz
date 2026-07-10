<template>
  <div v-if="booting" class="w-full h-full flex flex-col items-center justify-center text-ink-muted text-sm gap-4">
    <div class="w-12 h-12 rounded-2xl brand-gradient flex items-center justify-center text-white font-bold shadow-brand chat-loading-avatar">智</div>
    <p>正在进入实验室<span class="chat-loading-dots ml-1"><span /><span /><span /></span></p>
  </div>

  <div v-else-if="bootError" class="w-full h-full flex flex-col items-center justify-center px-6 text-center gap-4">
    <div class="w-14 h-14 rounded-2xl bg-red-50 border border-red-100 flex items-center justify-center text-red-500 text-2xl">!</div>
    <p class="text-red-600 text-sm max-w-md leading-relaxed">{{ bootError }}</p>
    <button type="button" class="btn-brand px-7 py-2.5 rounded-xl text-sm font-bold" @click="retryBoot">重新连接</button>
  </div>

  <div v-else class="lab-page flex flex-col w-full overflow-hidden">
    <!-- 顶栏 -->
    <LabHeader
      :experiments="lab.experiments"
      :experiment-code="lab.experiment?.code || ''"
      :student-name="lab.session?.studentName || '学生'"
      :env-level="lab.envLevel"
      :dify-status="lab.difyStatus"
      :dify-status-loading="lab.difyStatusLoading"
      :switching="lab.switchingExperiment"
      @quick-stats="showQuickStats = true"
      @report="openReport"
      @experiment-change="onExperimentChange"
      @logout="logout"
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
        />
        <template v-if="lab.hasDataPanel">
          <div class="workzone-divider" />
          <DataCollectionSection
            :open="lab.dataPanelOpen"
            :mode="lab.useDeviceData ? 'device' : 'manual'"
            @toggle="lab.toggleDataPanel()"
          >
            <InstrumentDataPanel
              v-if="lab.useDeviceData"
              :step-title="lab.stepConfig?.title"
              :device-type="lab.deviceType"
              :device-name="lab.deviceName"
              :device-state="lab.deviceState"
              :device-connected="lab.deviceConnected"
              :device-busy="lab.deviceBusy"
              :sampling-hz="lab.deviceSamplingHz"
              :fields="lab.currentDataFields"
              :snapshot="lab.deviceSnapshot"
              :live="lab.deviceLive"
              :curve-points="lab.deviceCurve"
              :reading="lab.deviceReading"
              :submitting="lab.submittingData"
              :validation-errors="lab.dataSubmitErrors"
              :can-submit-data="lab.deviceHasSubmitData"
              :acquire-progress="lab.deviceAcquireProgress"
              @start-acquire="lab.startDeviceAcquisition()"
              @stop-acquire="lab.stopDeviceAcquisition()"
              @read-once="lab.readDeviceOnce()"
              @submit="lab.submitDeviceData()"
            />
            <DataCollectionPanel
              v-else
              :fields="lab.currentDataFields"
              :step-title="lab.stepConfig?.title"
              :values="lab.currentStepDataValues"
              :last-saved="lab.currentStepDataSaved"
              :submitting="lab.submittingData"
              :validation-errors="lab.dataSubmitErrors"
              @submit="onSubmitData"
            />
          </DataCollectionSection>
        </template>
        <div class="workzone-divider" />
        <ImageUploadZone
          ref="uploadZone"
          :image-preview="lab.imagePreview"
          :uploading-image="lab.uploadingImage"
          :upload-error="lab.uploadError"
          :marks="lab.marks"
          @upload="onZoneUpload"
          @capture="onZoneCapture"
          @clear="lab.clearImage()"
        />
        <div class="workzone-divider" />
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
      </aside>

      <div
        class="workspace-resizer"
        role="separator"
        aria-orientation="vertical"
        aria-label="调整左右区域宽度"
        title="拖动调整左右区域宽度"
        @pointerdown="startWorkspaceResize"
      />

      <!-- 右：AI 智能助手主舞台 -->
      <section class="flex-1 min-w-0 min-h-0 h-full flex flex-col overflow-hidden">
        <RightPanel
          :messages="lab.messages"
          :loading-assist="lab.loadingAssist"
          :uploading-image="lab.uploadingImage"
          :image-preview="lab.composerImagePreview"
          :image-ready="!!lab.readyImageUrl"
          :experiment-name="lab.experiment?.name || ''"
          :step-title="lab.stepConfig?.title || ''"
          :student-name="lab.session?.studentName || ''"
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
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLabStore } from '../stores/lab'
import { useAuthStore } from '../stores/auth'
import LabHeader from '../components/layout/LabHeader.vue'
import StepPanel from '../components/step/StepPanel.vue'
import InstrumentDataPanel from '../components/data/InstrumentDataPanel.vue'
import DataCollectionPanel from '../components/data/DataCollectionPanel.vue'
import DataCollectionSection from '../components/data/DataCollectionSection.vue'
import ImageUploadZone from '../components/upload/ImageUploadZone.vue'
import BenchCameraPanel from '../components/monitor/BenchCameraPanel.vue'
import RightPanel from '../components/layout/RightPanel.vue'
import TabletCameraCapture from '../components/camera/TabletCameraCapture.vue'
import TutorialModal from '../components/modals/TutorialModal.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import QuickStatsModal from '../components/modals/QuickStatsModal.vue'
import AppConfirmDialog from '../components/modals/AppConfirmDialog.vue'

const lab = useLabStore()
const auth = useAuthStore()
const router = useRouter()

const uploadZone = ref(null)
const benchCam = ref(null)
const showTutorial = ref(false)
const showReport = ref(false)
const showQuickStats = ref(false)
const reportData = ref(null)
const downloadingDocx = ref(false)
const booting = ref(true)
const bootError = ref('')
const tabletCameraOpen = ref(false)
const tabletCameraTarget = ref('composer')
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
let viewActive = true

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
      const code = localStorage.getItem('wxz_exp') || 'newton_rings'
      const name = auth.displayName || localStorage.getItem('wxz_displayName') || '学生'
      await lab.loadExperiment(code)
      const latest = await lab.getLatestActiveSession(code)
      if (latest) {
        await lab.resumeSession(latest)
      } else {
        await lab.startSession(code, name, '')
      }
    }
    lab.startEnvTimer()
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

function logout() {
  lab.stopDifyStatusTimer()
  lab.stopEnvTimer()
  lab.teardownDevice()
  lab.$reset()
  auth.logout()
  router.replace('/login')
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

async function uploadTo(file, target) {
  try {
    await lab.uploadImage(file, { target })
  } catch (e) {
    const msg = lab.uploadError || e.response?.data?.message || e.message || '图片上传失败'
    await showAppAlert('上传失败', msg)
  }
}

function onComposerUpload(file) {
  return uploadTo(file, 'composer')
}

function onComposerCapture() {
  return openTabletCamera('composer')
}

function onZoneUpload(file) {
  return uploadTo(file, 'zone')
}

function onZoneCapture() {
  return openTabletCamera('zone')
}

function openTabletCamera(target) {
  tabletCameraTarget.value = target
  tabletCameraOpen.value = true
}

async function onTabletCameraCaptured(file) {
  tabletCameraOpen.value = false
  return uploadTo(file, tabletCameraTarget.value)
}

async function onSendMessage(text) {
  return lab.sendMessage(text)
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
  const code = lab.experiment?.code || localStorage.getItem('wxz_exp') || 'newton_rings'
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
  await lab.startSession(code, name, '')
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
  } catch (e) {
    await showAppAlert('切换失败', e.response?.data?.message || e.message || '切换实验失败')
  }
}

async function onSubmitData(values) {
  if (currentSessionReadOnly.value) {
    await showAppAlert('历史会话仅供查看', '已完成的历史会话不能继续提交数据，请新建会话后再操作。')
    return
  }
  await lab.submitStepData(values)
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
