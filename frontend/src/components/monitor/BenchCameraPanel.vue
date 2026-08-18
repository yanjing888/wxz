<template>
  <div class="shrink-0 p-3 flex flex-col gap-2">
    <!-- 主体一行：摄像头小窗 + 状态 + 操作 -->
    <div class="flex flex-col min-h-0 gap-2.5">
      <!-- 摄像头窗 -->
      <div
        ref="previewRef"
        class="bench-camera-preview aspect-video shrink-0 overflow-hidden border border-line-soft bg-gradient-to-br from-slate-900 to-slate-800"
        :class="isExpanded ? 'fixed left-1/2 top-1/2 z-50 w-[min(78vw,960px)] max-w-none -translate-x-1/2 -translate-y-1/2 rounded-2xl shadow-2xl' : 'relative w-full max-w-[280px] mx-auto rounded-xl shadow-card'"
      >
        <video
          v-show="camUiActive && camReady"
          ref="videoRef"
          class="absolute inset-0 w-full h-full object-cover"
          playsinline
          muted
        />
        <div v-if="camUiActive && !camReady" class="absolute inset-0 flex flex-col items-center justify-center gap-2">
          <div class="w-6 h-6 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          <p class="text-[7px] text-slate-400">连接中…</p>
        </div>
        <div v-if="camUiActive && camReady" class="absolute top-2 left-2 flex items-center gap-1 pointer-events-none">
          <span class="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
          <span class="text-[9px] text-red-400 font-mono font-bold">REC</span>
        </div>
        <div v-if="camUiActive && camReady" class="absolute bottom-2 left-2 pointer-events-none">
          <p class="text-[9px] text-emerald-400 font-mono">LIVE</p>
        </div>
        <button
          v-if="camUiActive && !isExpanded"
          type="button"
          class="absolute top-2 right-2 w-5 h-5 rounded-full bg-white/15 backdrop-blur text-white text-[11px] leading-none hover:bg-white/25 flex items-center justify-center z-10"
          title="关闭画面"
          @click="stopCamUi"
        >×</button>
        <button
          v-if="camUiActive && camReady && !isExpanded"
          type="button"
          class="absolute top-2 right-8 w-5 h-5 rounded-full bg-white/15 backdrop-blur text-white hover:bg-white/25 flex items-center justify-center z-10"
          title="放大观看"
          aria-label="放大观看"
          @click="openExpanded"
        >
          <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M8 3H3v5m13-5h5v5M8 21H3v-5m18 0v5h-5" />
          </svg>
        </button>
        <button
          v-if="isExpanded"
          type="button"
          class="bench-camera-exit absolute top-3 right-3 z-20 flex h-8 w-8 items-center justify-center rounded-full bg-black/25 font-bold leading-none text-white backdrop-blur hover:bg-black/40"
          title="退出放大"
          @click="closeExpanded"
        >
          退出
        </button>
        <div v-if="!camUiActive" class="absolute inset-0 flex flex-col items-center justify-center text-center px-3 gap-2">
          <svg class="w-8 h-8 text-slate-500" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <rect x="3" y="6" width="18" height="14" rx="2.5" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
          <button
            type="button"
            class="px-3 py-1.5 rounded-lg brand-gradient text-[11px] font-bold text-white shadow-brand btn-active-scale"
            @click="startCamUi"
          >
            开启
          </button>
        </div>
        <div v-if="flash" class="pointer-events-none absolute inset-0 bg-white opacity-40 transition-opacity duration-300 z-20" />
      </div>

      <!-- 状态区 -->
      <button
        v-if="isExpanded"
        type="button"
        class="fixed inset-0 z-40 bg-slate-950/45 backdrop-blur-[2px]"
        title="退出放大"
        aria-label="退出放大"
        @click="closeExpanded"
      />
      <div class="w-full min-w-0 flex flex-col gap-1.5">
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-1.5 min-w-0">
            <span class="text-[11px] font-bold text-ink-strong shrink-0">安全监测</span>
            <span class="text-[10px] font-bold px-1.5 py-0.5 rounded border shrink-0" :class="levelClass">{{ envLevelLabel }}</span>
          </div>
          <button
            type="button"
            role="switch"
            :aria-checked="envCheckEnabled"
            :title="envToggleTitle"
            :disabled="!envCheckAvailable"
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
            :disabled="envCheckRunning || !envCheckAvailable"
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
          <span class="font-bold shrink-0 px-1 rounded" :class="logLevelClass(log.level)">{{ log.level === 'NA' ? '不可用' : log.level }}</span>
          <span class="truncate">{{ log.summary }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import flvjs from 'flv.js'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const previewRef = ref(null)
const videoRef = ref(null)

const props = defineProps({
  envCheckEnabled: { type: Boolean, default: true },
  envLevel: { type: String, default: 'L0' },
  envHint: { type: String, default: '暂无异常' },
  envLogs: { type: Array, default: () => [] },
  envCheckRunning: { type: Boolean, default: false },
  envCheckAvailable: { type: Boolean, default: true },
  benchCamera: { type: Object, default: null }
})

const emit = defineEmits(['toggle-env', 'env-check'])

const camUiActive = ref(false)
const camReady = ref(false)
const camError = ref('')
const flash = ref(false)
const logsOpen = ref(false)
const isExpanded = ref(false)
let mediaStream = null
let flvPlayer = null
let liveBufferMonitorTimer = null

const configuredCamera = computed(() => {
  const cfg = props.benchCamera || {}
  if (!cfg.enabled) return null
  if (!cfg.browserStreamUrl && !cfg.rtspUrl) return null
  return cfg
})

const displayHint = computed(() => {
  if (!props.envCheckAvailable) return 'Dify 安全监测服务不可用'
  if (!props.envCheckEnabled) return '巡检已暂停，可手动立即检查'
  return briefSummary(props.envHint) || '暂无异常'
})

const envToggleTitle = computed(() => {
  if (!props.envCheckAvailable) return 'Dify 安全监测服务不可用，无法开启自动巡检'
  return props.envCheckEnabled ? '已开启自动巡检（约 1 分钟 / 次），点击关闭' : '已关闭自动巡检，点击开启'
})

const envLevelLabel = computed(() => (props.envLevel === 'NA' ? '不可用' : props.envLevel))

const levelClass = computed(() => {
  const map = {
    NA: 'bg-slate-100 text-slate-500 border-slate-200',
    L0: 'bg-emerald-50 text-emerald-600 border-emerald-200',
    L1: 'bg-amber-50 text-amber-600 border-amber-200',
    L2: 'bg-red-50 text-red-600 border-red-200',
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
  if (level === 'NA') return 'text-slate-500 bg-slate-100'
  if (level === 'L3') return 'text-red-500 bg-red-50'
  if (level === 'L2') return 'text-red-500 bg-red-50'
  if (level === 'L1') return 'text-amber-500 bg-amber-50'
  return 'text-emerald-500 bg-emerald-50'
}

function resolveBrowserStreamUrl(url) {
  if (url.startsWith('/ws/')) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}${url}`
  }
  return url
}

function stopMediaTracks() {
  stopLiveBufferMonitor()
  if (flvPlayer) {
    flvPlayer.pause()
    flvPlayer.unload()
    flvPlayer.detachMediaElement()
    flvPlayer.destroy()
    flvPlayer = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
    videoRef.value.removeAttribute('src')
    videoRef.value.load()
  }
}

function stopLiveBufferMonitor() {
  if (liveBufferMonitorTimer) {
    clearInterval(liveBufferMonitorTimer)
    liveBufferMonitorTimer = null
  }
}

function startLiveBufferMonitor(video) {
  stopLiveBufferMonitor()
  liveBufferMonitorTimer = setInterval(() => {
    if (!video?.buffered?.length || video.currentTime <= 0) return

    const bufferedEnd = video.buffered.end(video.buffered.length - 1)
    const bufferDuration = bufferedEnd - video.currentTime
    if (bufferDuration > 1) {
      // Drop accumulated MSE buffer to the live edge; playback remains at 1x.
      video.currentTime = Math.max(video.currentTime, bufferedEnd - 0.3)
    }
    video.playbackRate = 1
  }, 500)
}

async function startCamUi() {
  camError.value = ''
  camUiActive.value = true
  camReady.value = false

  if (configuredCamera.value) {
    await startConfiguredCamera(configuredCamera.value)
    return
  }

  await startLocalCamera()
}

async function startConfiguredCamera(camera) {
  stopMediaTracks()

  if (!camera.browserStreamUrl) {
    camUiActive.value = false
    return
    camError.value = '已配置 RTSP 地址，但浏览器不能直接播放 RTSP；请配置 browserStreamUrl 后再预览'
  }

  if (!flvjs.isSupported()) {
    camUiActive.value = false
    camError.value = '当前浏览器不支持 FLV 实时预览，请换用 Chrome/Edge 最新版或配置 HLS/WebRTC 播放地址'
    return
  }

  try {
    const video = videoRef.value
    if (!video) throw new Error('video element missing')
    video.muted = true
    flvPlayer = flvjs.createPlayer({
      type: 'flv',
      url: resolveBrowserStreamUrl(camera.browserStreamUrl),
      isLive: true,
      cors: true
    }, {
      enableWorker: false,
      enableStashBuffer: false,
      stashInitialSize: 32,
      stashMaxSize: 32,
      maxBufferLength: 0.3,
      maxBackoffMs: 2000,
      backoffMultiplier: 1.5,
      maxRetries: 3,
      liveBufferLatencyChasing: true,
      loadStatisticsInterval: 100,
      autoCleanupSourceBuffer: true,
      deferredBlob: false,
      fixAudioTimestampGap: true,
      acousticEchoCancellation: false,
      noiseSuppression: false,
      audioWorkletEnabled: false,
      autoplay: true,
      muted: true
    })
    flvPlayer.attachMediaElement(video)
    flvPlayer.load()
    video.playbackRate = 1
    await video.play()
    startLiveBufferMonitor(video)
    await waitForVideoFrame(video, 7000)
    camReady.value = true
  } catch (e) {
    stopMediaTracks()
    camUiActive.value = false
    camReady.value = false
    camError.value = '无法播放实验台摄像头视频流，请确认摄像头在线或检查配置地址'
  }
}

async function startLocalCamera() {

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
  closeExpanded()
  stopMediaTracks()
  camUiActive.value = false
  camReady.value = false
}

async function openExpanded() {
  if (!camReady.value) return
  isExpanded.value = true
  return
    camError.value = '当前浏览器不支持全屏放大观看'
    return
}

function closeExpanded() {
  isExpanded.value = false
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
  if (!props.envCheckAvailable) return
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
    if (enabled && props.envCheckAvailable) ensureCameraReady()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopMediaTracks()
})

defineExpose({ captureFrame, ensureCameraReady, camUiActive, camReady })
</script>

<style scoped>
.bench-camera-exit {
  font-size: 0;
}

.bench-camera-exit::before {
  content: "×";
  font-size: 1.25rem;
  line-height: 1;
}

.bench-camera-preview.fixed video {
  object-fit: contain;
}
</style>
