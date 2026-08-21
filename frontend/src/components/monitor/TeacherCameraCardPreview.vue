<template>
  <div class="cam-preview-mini">
    <span class="cam-badge" :class="badgeClass">{{ badgeText }}</span>

    <video
      v-show="live"
      ref="videoRef"
      class="cam-mini-video"
      playsinline
      muted
    />

    <div v-if="live && !ready && !error" class="cam-mini-loading">
      <div class="spinner" />
    </div>

    <div v-if="!live" class="cam-placeholder">
      <svg fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
        <rect x="3" y="6" width="18" height="14" rx="2.5" />
        <circle cx="12" cy="13" r="3.5" />
      </svg>
      <p v-if="offlineHint" class="cam-offline-hint">{{ offlineHint }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useFlvLivePlayer } from '../../composables/useFlvLivePlayer'

const props = defineProps({
  live: { type: Boolean, default: false },
  browserStreamUrl: { type: String, default: '' },
  cameraConfigured: { type: Boolean, default: false }
})

const videoRef = ref(null)
const { ready, error, start, stop } = useFlvLivePlayer()

const badgeClass = computed(() => {
  if (props.live && ready.value) return 'online'
  if (props.cameraConfigured) return 'idle'
  return 'offline'
})

const badgeText = computed(() => {
  if (props.live && ready.value) return '直播中'
  if (props.live) return '连接中'
  if (props.cameraConfigured) return '未开启'
  return '离线'
})

const offlineHint = computed(() => {
  if (props.live) return ''
  if (!props.cameraConfigured) return ''
  return '学生未开摄像头'
})

async function syncStream(shouldPlay) {
  await nextTick()
  const video = videoRef.value
  if (shouldPlay && props.browserStreamUrl) {
    await start(video, props.browserStreamUrl)
  } else {
    stop(video)
  }
}

watch(
  () => [props.live, props.browserStreamUrl],
  ([live]) => {
    syncStream(!!live)
  },
  { immediate: true }
)
</script>

<style scoped>
.cam-preview-mini {
  position: relative;
  aspect-ratio: 16 / 10;
  border-radius: 8px;
  overflow: hidden;
  background: linear-gradient(135deg, #1e293b, #0f172a);
}
.cam-mini-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
  background: #000;
}
.cam-mini-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.55);
}
.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid rgba(255, 255, 255, 0.25);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.cam-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 6px;
  padding: 8px;
}
.cam-placeholder svg {
  width: 36px;
  height: 36px;
  color: rgba(255, 255, 255, 0.2);
}
.cam-offline-hint {
  margin: 0;
  font-size: 10px;
  color: rgba(148, 163, 184, 0.9);
  text-align: center;
}
.cam-badge {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 2;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.cam-badge.online { background: rgba(16, 185, 129, 0.85); color: #fff; }
.cam-badge.idle { background: rgba(100, 116, 139, 0.85); color: #e2e8f0; }
.cam-badge.offline { background: rgba(100, 116, 139, 0.85); color: #cbd5e1; }
</style>
