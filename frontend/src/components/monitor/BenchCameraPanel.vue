<template>
  <div class="shrink-0 p-3">
    <!-- 主体一行：摄像头小窗 + 状态 + 操作 -->
    <div class="flex items-start gap-2.5">
      <!-- 摄像头窗 -->
      <div
        ref="previewRef"
        class="relative w-[100px] h-[68px] shrink-0 rounded-xl overflow-hidden border border-line-soft bg-gradient-to-br from-slate-900 to-slate-800 shadow-card"
      >
        <video
          v-show="camUiActive && camReady"
          ref="videoRef"
          class="absolute inset-0 w-full h-full object-cover"
          playsinline
          muted
        />
        <div v-if="camUiActive && !camReady" class="absolute inset-0 flex flex-col items-center justify-center gap-1">
          <div class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          <p class="text-[7px] text-slate-400">连接中…</p>
        </div>
        <div v-if="camUiActive && camReady" class="absolute top-1 left-1 flex items-center gap-0.5 pointer-events-none">
          <span class="w-1 h-1 rounded-full bg-red-500 animate-pulse" />
          <span class="text-[7px] text-red-400 font-mono font-bold">REC</span>
        </div>
        <div v-if="camUiActive && camReady" class="absolute bottom-1 left-1 pointer-events-none">
          <p class="text-[7px] text-emerald-400 font-mono">LIVE</p>
        </div>
        <button
          v-if="camUiActive"
          type="button"
          class="absolute top-1 right-1 w-3.5 h-3.5 rounded-full bg-white/15 backdrop-blur text-white text-[9px] leading-none hover:bg-white/25 flex items-center justify-center z-10"
          title="关闭画面"
          @click="stopCamUi"
        >×</button>
        <div v-if="!camUiActive" class="absolute inset-0 flex flex-col items-center justify-center text-center px-1 gap-1">
          <svg class="w-4 h-4 text-slate-500" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <rect x="3" y="6" width="18" height="14" rx="2.5" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
          <button
            type="button"
            class="px-1.5 py-0.5 rounded brand-gradient text-[8px] font-bold text-white shadow-brand btn-active-scale"
            @click="startCamUi"
          >
            开启
          </button>
        </div>
        <div v-if="flash" class="pointer-events-none absolute inset-0 bg-white opacity-40 transition-opacity duration-300 z-20" />
      </div>

      <!-- 状态区 -->
      <div class="flex-1 min-w-0 flex flex-col justify-between gap-1.5">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-1.5 min-w-0">
            <span class="text-[11px] font-bold text-ink-strong shrink-0">安全监测</span>
            <span class="text-[10px] font-bold px-1.5 py-0.5 rounded border shrink-0" :class="levelClass">{{ envLevel }}</span>
          </div>
          <button
            type="button"
            role="switch"
            :aria-checked="envCheckEnabled"
            :title="envCheckEnabled ? '已开启自动巡检（约 1 分钟 / 次），点击关闭' : '已关闭自动巡检，点击开启'"
            class="group flex items-center gap-1.5 px-1.5 py-0.5 rounded-full border border-line-soft bg-white hover:border-line-strong transition-colors btn-active-scale shrink-0"
            @click="$emit('toggle-env', !envCheckEnabled)"
          >
            <span class="text-[10px] font-semibold leading-none tracking-wide text-ink-muted">
              自动巡检 · {{ envCheckEnabled ? '开' : '关' }}
            </span>
            <span
              class="relative inline-flex h-3.5 w-7 items-center rounded-full transition-colors duration-200"
              :style="{ backgroundColor: envCheckEnabled ? '#4f46e5' : 'rgba(148, 163, 184, 0.55)' }"
            >
              <span
                class="absolute h-2.5 w-2.5 rounded-full bg-white shadow-sm transition-all duration-200"
                :style="{ left: envCheckEnabled ? '14px' : '2px' }"
              />
            </span>
          </button>
        </div>
        <p
          class="text-[10px] leading-tight env-hint-brief min-h-0"
          :class="envCheckEnabled ? 'text-ink-muted' : 'text-ink-faint italic'"
          :title="envCheckEnabled && envHint ? envHint : ''"
        >
          {{ displayHint }}
        </p>
        <p v-if="camError" class="text-[9px] text-amber-600 leading-tight">{{ camError }}</p>
        <div class="flex items-center gap-1.5">
          <button
            type="button"
            class="flex-1 px-2 py-1 rounded-lg border border-line-soft bg-white text-ink-base hover:text-brand-600 hover:border-brand-300 hover:bg-brand-50 btn-active-scale text-[10px] font-semibold transition-all disabled:opacity-50 flex items-center justify-center gap-1"
            :disabled="envCheckRunning"
            @click="manualCheck"
          >
            <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ envCheckRunning ? '检查中…' : '立即检查' }}
          </button>
          <button
            type="button"
            class="px-2 py-1 rounded-lg border border-line-soft bg-white text-ink-muted hover:text-brand-600 hover:border-brand-300 hover:bg-brand-50 text-[10px] font-medium transition-all flex items-center gap-0.5"
            :title="logsOpen ? '收起记录' : `查看 ${envLogs.length} 条记录`"
            @click="logsOpen = !logsOpen"
          >
            日志
            <svg class="w-2.5 h-2.5 transition-transform" :class="logsOpen ? 'rotate-180' : ''" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" /></svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 折叠的巡检日志 -->
    <div v-if="logsOpen" class="mt-2 rounded-lg overflow-hidden border border-line-soft bg-surface-soft fade-in-up">
      <div class="max-h-[80px] overflow-y-auto custom-scroll px-2 py-1.5 space-y-1">
        <p v-if="!envLogs.length" class="text-[10px] text-ink-faint py-2 text-center">暂无记录</p>
        <div v-for="(log, i) in envLogs" :key="i" class="text-[10px] text-ink-muted flex gap-1.5 items-baseline">
          <span class="font-mono text-ink-faint shrink-0">{{ log.time }}</span>
          <span class="font-bold shrink-0 px-1 rounded" :class="logLevelClass(log.level)">{{ log.level }}</span>
          <span class="truncate">{{ log.summary }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const previewRef = ref(null)
const videoRef = ref(null)

const props = defineProps({
  envCheckEnabled: { type: Boolean, default: true },
  envLevel: { type: String, default: 'L0' },
  envHint: { type: String, default: '暂无异常' },
  envLogs: { type: Array, default: () => [] },
  envCheckRunning: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle-env', 'env-check'])

const camUiActive = ref(false)
const camReady = ref(false)
const camError = ref('')
const flash = ref(false)
const logsOpen = ref(false)
let mediaStream = null

const displayHint = computed(() => {
  if (!props.envCheckEnabled) return '巡检已暂停，可手动立即检查'
  return briefSummary(props.envHint) || '暂无异常'
})

const levelClass = computed(() => {
  const map = {
    L0: 'bg-emerald-50 text-emerald-600 border-emerald-200',
    L1: 'bg-amber-50 text-amber-600 border-amber-200',
    L2: 'bg-orange-50 text-orange-600 border-orange-200',
    L3: 'bg-red-50 text-red-600 border-red-200'
  }
  return map[props.envLevel] || map.L0
})

function briefSummary(text, maxLen = 80) {
  if (!text) return ''
  const plain = String(text)
    .replace(/[#*_>`[\]()]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!plain) return ''
  return plain.length <= maxLen ? plain : `${plain.slice(0, maxLen)}…`
}

function logLevelClass(level) {
  if (level === 'L3') return 'text-red-500 bg-red-50'
  if (level === 'L2') return 'text-orange-500 bg-orange-50'
  if (level === 'L1') return 'text-amber-500 bg-amber-50'
  return 'text-emerald-500 bg-emerald-50'
}

function stopMediaTracks() {
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
}

async function startCamUi() {
  camError.value = ''
  camUiActive.value = true
  camReady.value = false

  if (!navigator.mediaDevices?.getUserMedia) {
    camError.value = '当前浏览器不支持摄像头，无法抽帧巡检'
    camUiActive.value = false
    return
  }

  stopMediaTracks()
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 640 },
        height: { ideal: 480 }
      },
      audio: false
    })
    const video = videoRef.value
    if (!video) throw new Error('video element missing')
    video.srcObject = mediaStream
    await video.play()
    await waitForVideoFrame(video)
    camReady.value = true
  } catch (e) {
    stopMediaTracks()
    camUiActive.value = false
    camReady.value = false
    camError.value = e?.name === 'NotAllowedError'
      ? '请允许摄像头权限后再开启监控'
      : '无法打开摄像头，请检查设备或权限'
  }
}

function stopCamUi() {
  stopMediaTracks()
  camUiActive.value = false
  camReady.value = false
}

function waitForVideoFrame(video, timeoutMs = 4000) {
  return new Promise((resolve, reject) => {
    if (video.readyState >= 2 && video.videoWidth > 0) {
      resolve()
      return
    }
    const timer = setTimeout(() => {
      cleanup()
      reject(new Error('camera timeout'))
    }, timeoutMs)
    const onReady = () => {
      if (video.videoWidth > 0) {
        cleanup()
        resolve()
      }
    }
    const cleanup = () => {
      clearTimeout(timer)
      video.removeEventListener('loadeddata', onReady)
      video.removeEventListener('playing', onReady)
    }
    video.addEventListener('loadeddata', onReady)
    video.addEventListener('playing', onReady)
  })
}

async function captureFrame() {
  const video = videoRef.value
  if (camUiActive.value && camReady.value && video?.videoWidth > 0) {
    const w = video.videoWidth
    const h = video.videoHeight
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return null
    ctx.drawImage(video, 0, 0, w, h)
    return new Promise((resolve) => {
      canvas.toBlob((blob) => resolve(blob), 'image/jpeg', 0.88)
    })
  }
  return null
}

async function ensureCameraReady() {
  if (camUiActive.value && camReady.value) return true
  await startCamUi()
  return camUiActive.value && camReady.value
}

async function manualCheck() {
  flash.value = true
  setTimeout(() => { flash.value = false }, 320)
  const ready = await ensureCameraReady()
  if (!ready) return
  const blob = await captureFrame()
  if (blob) emit('env-check', blob)
}

watch(
  () => props.envCheckEnabled,
  (enabled) => {
    if (enabled) ensureCameraReady()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopMediaTracks()
})

defineExpose({ captureFrame, ensureCameraReady })
</script>
