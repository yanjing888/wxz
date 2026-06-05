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

  <div v-else class="flex flex-col w-full h-full overflow-hidden">
    <!-- 顶栏 -->
    <LabHeader
      :experiments="lab.experiments"
      :experiment-code="lab.experiment?.code || ''"
      :student-name="lab.session?.studentName || '学生'"
      :student-class="lab.session?.studentClass || ''"
      :env-level="lab.envLevel"
      :switching="lab.switchingExperiment"
      @quick-stats="showQuickStats = true"
      @report="openReport"
      @experiment-change="onExperimentChange"
    />

    <!-- 主体：左辅助栏（步骤指导 + 图片 + 摄像头）+ 右 AI 主舞台 -->
    <div class="flex-1 flex flex-row overflow-hidden min-h-0 gap-4 px-4 pb-4 pt-4">
      <!-- 左辅助栏 -->
      <aside class="w-[400px] shrink-0 flex flex-col gap-3 min-h-0">
        <StepPanel
          :menu-labels="lab.experiment?.menuLabels || []"
          :active-step="lab.activeStep"
          :step="lab.stepConfig"
          @select="lab.selectStep"
          @tutorial="openTutorial"
        />
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
          v-else-if="lab.useManualDataCorrection"
          :fields="lab.currentDataFields"
          :step-title="lab.stepConfig?.title"
          :values="lab.currentStepDataValues"
          :last-saved="lab.currentStepDataSaved"
          :submitting="lab.submittingData"
          :validation-errors="lab.dataSubmitErrors"
          @submit="onSubmitData"
        />
        <ImageUploadZone
          v-else-if="lab.useVisionCorrection"
          ref="uploadZone"
          :image-preview="lab.imagePreview"
          :uploading-image="lab.uploadingImage"
          :upload-error="lab.uploadError"
          :marks="lab.marks"
          @upload="onUpload"
          @clear="lab.clearImage()"
        />
        <BenchCameraPanel
          :env-check-enabled="lab.envCheckEnabled"
          :env-level="lab.envLevel"
          :env-hint="lab.envHint"
          :env-logs="lab.envLogs"
          :env-check-running="lab.envCheckRunning"
          @toggle-env="lab.toggleEnvCheck"
          @env-check="lab.runEnvCheck"
        />
      </aside>

      <!-- 右：AI 智能助手主舞台 -->
      <section class="flex-1 min-w-0 flex flex-col">
        <RightPanel
          :messages="lab.messages"
          :loading-assist="lab.loadingAssist"
          :uploading-image="lab.uploadingImage"
          :image-preview="lab.composerImagePreview"
          :image-ready="!!lab.readyImageUrl"
          @send="onSendMessage"
          @upload-image="onUpload"
          @clear-image="lab.clearComposerImage()"
        />
      </section>
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
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useLabStore } from '../stores/lab'
import LabHeader from '../components/layout/LabHeader.vue'
import StepPanel from '../components/step/StepPanel.vue'
import InstrumentDataPanel from '../components/data/InstrumentDataPanel.vue'
import DataCollectionPanel from '../components/data/DataCollectionPanel.vue'
import ImageUploadZone from '../components/upload/ImageUploadZone.vue'
import BenchCameraPanel from '../components/monitor/BenchCameraPanel.vue'
import RightPanel from '../components/layout/RightPanel.vue'
import TutorialModal from '../components/modals/TutorialModal.vue'
import ReportModal from '../components/modals/ReportModal.vue'
import QuickStatsModal from '../components/modals/QuickStatsModal.vue'

const lab = useLabStore()

const uploadZone = ref(null)
const showTutorial = ref(false)
const showReport = ref(false)
const showQuickStats = ref(false)
const reportData = ref(null)
const downloadingDocx = ref(false)
const booting = ref(true)
const bootError = ref('')

async function bootstrap() {
  booting.value = true
  bootError.value = ''
  try {
    if (!lab.session?.id) {
      await lab.loadExperiments()
      const code = localStorage.getItem('wxz_exp') || 'tensile_steel'
      const name = localStorage.getItem('wxz_name') || '学生'
      const cls = localStorage.getItem('wxz_class') || ''
      await lab.loadExperiment(code)
      await lab.startSession(code, name, cls)
    }
    lab.startEnvTimer()
  } catch (e) {
    bootError.value = e.response?.data?.message || e.message || '无法连接后端，请先启动 backend（mvn spring-boot:run）'
    lab.stopEnvTimer()
  } finally {
    booting.value = false
  }
}

function retryBoot() {
  bootstrap()
}

onMounted(() => {
  bootstrap()
})

onUnmounted(() => {
  lab.stopEnvTimer()
  lab.teardownDevice()
})

async function onUpload(file) {
  try {
    await lab.uploadImage(file)
  } catch (e) {
    const msg = lab.uploadError || e.response?.data?.message || e.message || '图片上传失败'
    window.alert(msg)
  }
}

async function onSendMessage(text) {
  return lab.sendMessage(text)
}

async function onExperimentChange(code) {
  if (!code || code === lab.experiment?.code) return
  const target = lab.experiments.find((e) => e.code === code)
  const label = target?.name || code
  if (
    !window.confirm(
      `将切换到「${label}」并开始新的实验会话，当前步骤与对话记录不会保留。\n\n确定切换吗？`
    )
  ) {
    return
  }
  try {
    await lab.switchExperiment(code)
  } catch (e) {
    window.alert(e.response?.data?.message || e.message || '切换实验失败')
  }
}

async function onSubmitData(values) {
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
