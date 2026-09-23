import flvjs from 'flv.js'
import { onBeforeUnmount, ref } from 'vue'
import { benchCameraStreamCandidates, resolveBenchStreamUrl } from '../utils/benchCamera'

export { resolveBenchStreamUrl }

function waitForVideoFrame(video, timeoutMs = 7000) {
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

export function useFlvLivePlayer() {
  const ready = ref(false)
  const error = ref('')
  let player = null

  function cleanup() {
    if (player) {
      try {
        player.pause()
        player.unload()
        player.detachMediaElement()
        player.destroy()
      } catch {
        /* noop */
      }
      player = null
    }
  }

  async function start(video, browserStreamUrl) {
    cleanup()
    ready.value = false
    error.value = ''

    if (!video || !browserStreamUrl) {
      error.value = '摄像头未配置'
      return false
    }
    if (!flvjs.isSupported()) {
      error.value = '浏览器不支持 FLV 预览'
      return false
    }

    video.muted = true
    try {
      player = flvjs.createPlayer({
        type: 'flv',
        url: resolveBenchStreamUrl(browserStreamUrl),
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
      player.attachMediaElement(video)
      player.load()
      await video.play()
      await waitForVideoFrame(video)
      ready.value = true
      return true
    } catch {
      error.value = '无法播放视频流'
      cleanup()
      if (video) {
        video.srcObject = null
        video.removeAttribute('src')
        video.load()
      }
      return false
    }
  }

  async function startWithCamera(video, camera) {
    const urls = benchCameraStreamCandidates(camera)
    if (!urls.length) {
      error.value = '摄像头未配置'
      return false
    }
    for (const url of urls) {
      const ok = await start(video, url)
      if (ok) return true
    }
    error.value = '无法播放视频流（已尝试实验室网与网线直连）'
    return false
  }

  function stop(video) {
    cleanup()
    ready.value = false
    if (video) {
      video.srcObject = null
      video.removeAttribute('src')
      video.load()
    }
  }

  onBeforeUnmount(() => cleanup())

  return { ready, error, start, startWithCamera, stop }
}
