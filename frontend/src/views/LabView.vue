<template>
  <div v-if="booting" class="w-full h-full flex items-center justify-center bg-[#eef2f7] text-slate-500 text-sm">
    正在进入实验室…
  </div>
  <div v-else-if="bootError" class="w-full h-full flex flex-col items-center justify-center bg-[#eef2f7] px-6 text-center gap-4">
    <p class="text-red-600 text-sm max-w-md">{{ bootError }}</p>
    <button type="button" class="px-6 py-2 bg-blue-600 text-white rounded-xl text-sm font-bold" @click="retryBoot">重试</button>
  </div>
  <div v-else class="flex flex-row w-full h-full overflow-hidden bg-[#eef2f7]">
    <SideNav @quick-stats="showQuickStats = true" />

    <main class="flex-1 flex flex-col border-r border-[#dbe3ee] bg-[#f7f9fc] overflow-hidden min-w-0">
      <LabHeader
        :experiment-name="lab.experiment?.name"
        :student-name="lab.session?.studentName || '学生'"
        :student-class="lab.session?.studentClass || ''"
      />
      <StepPanel
        :menu-labels="lab.experiment?.menuLabels || []"
        :active-step="lab.activeStep"
        :step="lab.stepConfig"
        @select="lab.selectStep"
        @tutorial="openTutorial"
      />
      <ImageUploadZone
        ref="uploadZone"
        :image-preview="lab.imagePreview"
        :marks="lab.marks"
        :show-analysis-label="lab.showAnalysisLabel"
        @upload="onUpload"
        @clear="lab.clearImage()"
      />
    </main>

    <RightPanel
      :messages="lab.messages"
      :loading-assist="lab.loadingAssist"
      :uploading-image="lab.uploadingImage"
      :image-preview="lab.imagePreview"
      :env-check-enabled="lab.envCheckEnabled"
      :env-level="lab.envLevel"
      :env-hint="lab.envHint"
      :env-logs="lab.envLogs"
      :env-check-running="lab.envCheckRunning"
      @send="lab.sendMessage"
      @upload-image="onUpload"
      @clear-image="lab.clearImage()"
      @report="openReport"
      @toggle-env="lab.toggleEnvCheck"
      @env-check="lab.runEnvCheck"
    />

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
import SideNav from '../components/layout/SideNav.vue'
import LabHeader from '../components/layout/LabHeader.vue'
import StepPanel from '../components/step/StepPanel.vue'
import ImageUploadZone from '../components/upload/ImageUploadZone.vue'
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
      const code = localStorage.getItem('wxz_exp') || lab.experiments[0]?.code || 'tensile_steel'
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

onUnmounted(() => lab.stopEnvTimer())

async function onUpload(file) {
  await lab.uploadImage(file)
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
