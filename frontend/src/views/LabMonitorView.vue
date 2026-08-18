<template>
  <div class="monitor-page flex flex-col flex-1 min-h-0 w-full overflow-hidden">
    <!-- 顶部状态栏 -->
    <div class="monitor-topbar shrink-0 flex items-center justify-between px-5 py-2.5 border-b border-line-soft bg-white flex-wrap gap-2">
      <div class="flex items-center gap-2.5">
        <h1 class="text-[14px] font-bold text-ink-strong">实验台监控</h1>
        <span class="text-[11px] text-ink-faint">{{ experimentName }}</span>
      </div>
      <div class="flex items-center gap-3 flex-wrap">
        <div class="flex items-center gap-1.5">
          <span class="text-[11px] text-ink-muted">安全等级</span>
          <span class="text-[11px] font-bold px-2 py-0.5 rounded border" :class="levelClass">{{ envLevelLabel }}</span>
        </div>
        <button
          type="button"
          class="px-3 py-1 rounded-lg border border-line-soft bg-white text-[12px] font-semibold text-ink-base hover:text-brand-600 hover:border-brand-300 hover:bg-brand-50 transition-all disabled:opacity-50 flex items-center gap-1"
          :disabled="lab.envCheckRunning || !lab.envCheckAvailable || !hasSession"
          @click="manualCheck"
        >
          <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {{ lab.envCheckRunning ? '检查中…' : '立即检查' }}
        </button>
        <button
          type="button"
          role="switch"
          :aria-checked="lab.envCheckEnabled"
          :disabled="!lab.envCheckAvailable || !hasSession"
          class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg border border-line-soft bg-white text-[11px] font-semibold text-ink-muted hover:border-brand-300 transition-all disabled:opacity-50"
          @click="lab.toggleEnvCheck(!lab.envCheckEnabled)"
        >
          自动巡检
          <span class="inline-flex h-4 w-7 items-center rounded-full transition-colors" :style="{ backgroundColor: lab.envCheckEnabled ? '#4f46e5' : 'rgba(148,163,184,0.55)' }">
            <span class="h-3 w-3 rounded-full bg-white shadow-sm transition-all" :style="{ marginLeft: lab.envCheckEnabled ? '12px' : '2px' }" />
          </span>
        </button>
      </div>
    </div>

    <!-- Dify 服务不可用提示 -->
    <div
      v-if="!lab.envCheckAvailable && !bootstrapping"
      class="shrink-0 px-4 py-1.5 bg-amber-50 border-b border-amber-200 flex items-center gap-2"
    >
      <svg class="w-3.5 h-3.5 text-amber-500 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span class="text-[11px] text-amber-700">{{ difyUnavailableReason }}</span>
    </div>

    <!-- 主区域：左侧摄像头 + 右侧常驻日志 -->
    <div class="monitor-body flex-1 min-h-0 flex flex-row overflow-hidden">
      <!-- 左侧：摄像头占满 -->
      <div class="monitor-stage flex-1 min-h-0 relative bg-slate-950 overflow-hidden">
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

      <!-- 右侧：常驻巡检日志 -->
      <aside class="monitor-logs shrink-0 w-[280px] flex flex-col bg-white border-l border-line-soft overflow-hidden">
        <div class="shrink-0 px-4 py-2.5 border-b border-line-soft flex items-center justify-between">
          <div class="flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5 text-ink-muted" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
            </svg>
            <span class="text-[12px] font-bold text-ink-strong">巡检记录</span>
          </div>
          <span class="text-[10px] text-ink-faint">{{ lab.envLogs.length }} 条</span>
        </div>
        <div class="flex-1 min-h-0 overflow-y-auto custom-scroll px-3 py-2 space-y-1.5">
          <p v-if="!lab.envLogs.length" class="text-[11px] text-ink-faint py-6 text-center">
            暂无巡检记录<br />点击"立即检查"或开启自动巡检
          </p>
          <div
            v-for="(log, i) in lab.envLogs"
            :key="i"
            class="log-item rounded-lg border border-line-soft bg-surface-soft px-2.5 py-1.5"
          >
            <div class="flex items-center gap-1.5 mb-0.5">
              <span class="font-mono text-[10px] text-ink-faint">{{ log.time }}</span>
              <span class="text-[9px] font-bold px-1 py-0.5 rounded" :class="logLevelClass(log.level)">{{ log.level === 'NA' ? '不可用' : log.level }}</span>
            </div>
            <p class="text-[10px] text-ink-muted leading-tight line-clamp-3">{{ briefLogSummary(log.summary) }}</p>
          </div>
        </div>
        <div v-if="lab.envLogs.length" class="shrink-0 px-3 py-1.5 border-t border-line-soft">
          <p class="text-[10px] text-ink-faint text-center">
            安全等级 <span class="font-bold" :class="envLevelTextColor">{{ envLevelLabel }}</span>
          </p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref } from 'vue'
import { useLabStore } from '../stores/lab'
import { useAuthStore } from '../stores/auth'
import BenchCameraPanel from '../components/monitor/BenchCameraPanel.vue'

defineOptions({ name: 'LabMonitorView' })

const lab = useLabStore()
const auth = useAuthStore()
const benchCam = ref(null)
const bootstrapping = ref(false)
let didInit = false

const experimentName = computed(() => lab.experiment?.name || '实验台监控')

const hasSession = computed(() => !!lab.session?.id)

const envLevelLabel = computed(() => lab.envLevel === 'NA' ? '不可用' : lab.envLevel)

const envLevelTextColor = computed(() => {
  const map = {
    NA: 'text-slate-500',
    L0: 'text-emerald-600',
    L1: 'text-amber-600',
    L2: 'text-red-600',
    L3: 'text-red-600'
  }
  return map[lab.envLevel] || map.L0
})

const difyUnavailableReason = computed(() => {
  const reason = lab.envCheckDifyStatus?.reason
  if (!lab.difyStatus) return '正在检测安全监测服务状态…'
  if (reason) return `安全监测服务不可用：${reason}。请检查 Dify 服务是否正常运行。`
  return '安全监测服务不可用，请检查 Dify 服务是否正常运行。'
})

const levelClass = computed(() => {
  const map = {
    NA: 'bg-slate-100 text-slate-500 border-slate-200',
    L0: 'bg-emerald-50 text-emerald-600 border-emerald-200',
    L1: 'bg-amber-50 text-amber-600 border-amber-200',
    L2: 'bg-red-50 text-red-600 border-red-200',
    L3: 'bg-red-50 text-red-600 border-red-200'
  }
  return map[lab.envLevel] || map.L0
})

function logLevelClass(level) {
  if (level === 'NA') return 'text-slate-500 bg-slate-100'
  if (level === 'L3') return 'text-red-500 bg-red-50'
  if (level === 'L2') return 'text-red-500 bg-red-50'
  if (level === 'L1') return 'text-amber-500 bg-amber-50'
  return 'text-emerald-500 bg-emerald-50'
}

function briefLogSummary(text, maxLen = 60) {
  if (!text) return '暂无描述'
  const plain = String(text).replace(/[#*_>`[\]()]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length <= maxLen ? plain : plain.slice(0, maxLen) + '…'
}

async function manualCheck() {
  if (!lab.envCheckAvailable || !hasSession.value) return
  const ready = await benchCam.value?.ensureCameraReady?.()
  if (!ready) return
  const blob = await benchCam.value?.captureFrame?.()
  if (blob) await lab.runEnvCheck(blob)
}

async function ensureSession() {
  if (lab.session?.id) return
  bootstrapping.value = true
  try {
    await lab.loadExperiments()
    if (!lab.experiments.length) return
    const saved = localStorage.getItem('wxz_exp')
    const code = lab.experiments.some((e) => e.code === saved)
      ? saved
      : lab.experiments[0].code
    await lab.loadExperiment(code)
    const name = auth.displayName || localStorage.getItem('wxz_displayName') || '学生'
    const latest = await lab.getLatestActiveSession(code)
    if (latest) {
      await lab.resumeSession(latest)
    } else {
      await lab.startSession(code, name, auth.studentClass || '')
    }
  } catch {
    // 会话加载失败不阻断摄像头功能
  } finally {
    bootstrapping.value = false
  }
}

onMounted(async () => {
  lab.setEnvCaptureFn(() => benchCam.value?.captureFrame?.())
  lab.setEnvEnsureCamFn(() => benchCam.value?.ensureCameraReady?.())
  if (!lab.benchCamera) {
    lab.loadBenchCamera().catch(() => {})
  }
  if (!didInit) {
    didInit = true
    await ensureSession()
    await lab.loadEnvLogs()
    await lab.loadDifyStatus()
      .catch(() => null)
      .finally(() => {
        lab.startDifyStatusTimer()
        lab.applyEnvDifyStatus()
        lab.startEnvTimer()
      })
  }
})

onActivated(() => {
  lab.setEnvCaptureFn(() => benchCam.value?.captureFrame?.())
  lab.setEnvEnsureCamFn(() => benchCam.value?.ensureCameraReady?.())
  lab.loadDifyStatus({ silent: true })
    .catch(() => null)
    .finally(() => {
      lab.startDifyStatusTimer()
      lab.applyEnvDifyStatus()
      lab.startEnvTimer()
    })
})

onDeactivated(() => {
  lab.stopDifyStatusTimer()
  lab.stopEnvTimer()
})

onUnmounted(() => {
  lab.setEnvCaptureFn(null)
  lab.setEnvEnsureCamFn(null)
  lab.stopDifyStatusTimer()
  lab.stopEnvTimer()
})
</script>

<style scoped>
.monitor-page {
  background: #0f172a;
  position: relative;
}
.monitor-topbar {
  background: #fff;
}

/* ===== 让 BenchCameraPanel 占满整个监控区域 ===== */
.monitor-stage :deep(> div) {
  width: 100% !important;
  height: 100% !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  gap: 0 !important;
}

.monitor-stage :deep(> div > div:first-child) {
  flex: 1 !important;
  min-height: 0 !important;
  gap: 0 !important;
}

/* 摄像头预览区域占满 */
.monitor-stage :deep(.bench-camera-preview) {
  max-width: none !important;
  width: 100% !important;
  height: 100% !important;
  flex: 1 !important;
  aspect-ratio: auto !important;
  border-radius: 0 !important;
  border: none !important;
}

.monitor-stage :deep(.bench-camera-preview video) {
  object-fit: cover !important;
}

/* 放大"开启"按钮 */
.monitor-stage :deep(.bench-camera-preview .absolute.inset-0.flex.flex-col.items-center.justify-center.gap-2) {
  gap: 20px !important;
}
.monitor-stage :deep(.bench-camera-preview .absolute.inset-0 svg) {
  width: 56px !important;
  height: 56px !important;
}
.monitor-stage :deep(.bench-camera-preview .brand-gradient) {
  font-size: 15px !important;
  padding: 10px 28px !important;
  border-radius: 12px !important;
}

/* 连接中 spinner 放大 */
.monitor-stage :deep(.bench-camera-preview .w-6.h-6) {
  width: 36px !important;
  height: 36px !important;
}
.monitor-stage :deep(.bench-camera-preview .text-\[7px\]) {
  font-size: 13px !important;
}

/* REC / LIVE 指示器放大 */
.monitor-stage :deep(.bench-camera-preview .w-1\.5) {
  width: 8px !important;
  height: 8px !important;
}
.monitor-stage :deep(.bench-camera-preview .text-\[9px\]) {
  font-size: 13px !important;
}

/* 关闭按钮放大 */
.monitor-stage :deep(.bench-camera-preview .w-5.h-5) {
  width: 32px !important;
  height: 32px !important;
}

/* 隐藏 BenchCameraPanel 自带的安全监测区域（顶部已有） */
.monitor-stage :deep(> div > div > div.w-full.min-w-0) {
  display: none !important;
}

/* 隐藏 BenchCameraPanel 自带的巡检日志（右侧已有） */
.monitor-stage :deep(> div > div.mt-2) {
  display: none !important;
}

/* 隐藏展开时的遮罩按钮（全屏由页面控制） */
.monitor-stage :deep(.fixed.inset-0.z-40) {
  display: none !important;
}

/* 右侧日志面板 */
.monitor-logs {
  background: #f8fafc;
}

.log-item {
  transition: background 0.15s;
}
.log-item:hover {
  background: #f1f5f9;
}
</style>
