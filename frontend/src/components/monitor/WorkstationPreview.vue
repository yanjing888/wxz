<template>
  <div class="station-preview">
    <video v-if="station.cameraType === 'NETWORK'" ref="video" muted playsinline :aria-label="station.name + '摄像头'" />
    <img v-else-if="imageUrl" :src="imageUrl" :alt="station.name + '实时采集画面'" />
    <div v-if="hint" class="preview-hint" role="status"><span>{{ hint }}</span><button v-if="live && station.enabled && station.canView !== false" type="button" @click="restart">重新连接</button></div>
    <span v-if="live && !hint" class="preview-status">{{ station.cameraType === 'USB' ? 'USB · 约 2 帧/秒' : '网络摄像头' }}</span>
  </div>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { mediaUrl } from '../../api/runtime'
import { workstationApi } from '../../api/workstations'
import { useFlvLivePlayer } from '../../composables/useFlvLivePlayer'
const props = defineProps({ station: { type: Object, required: true }, live: Boolean })
const video = ref(null), imageUrl = ref(''), frameError = ref('')
const { ready, error, start, stop } = useFlvLivePlayer()
let timer, controller, sequence = 0
const hint = computed(() => !props.station.enabled ? '工位已停用' : props.station.canView === false ? '暂无查看权限' : !props.live ? '点击查看实时画面' : (props.station.cameraType === 'NETWORK' && props.station.streamConfigured === false) ? '摄像头播放地址未配置' : frameError.value || error.value || (props.station.cameraType === 'USB' ? (!imageUrl.value ? '等待 USB 采集器画面' : '') : (!ready.value ? '正在连接摄像头…' : '')))
function cleanup() {
  sequence++; clearTimeout(timer); controller?.abort(); stop(video.value)
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''; frameError.value = ''
}
async function pollFrame(run) {
  if (run !== sequence) return
  controller = new AbortController()
  try {
    const { data } = await workstationApi.frame(props.station.id, controller.signal)
    if (run !== sequence) return
    const previous = imageUrl.value; imageUrl.value = URL.createObjectURL(data)
    if (previous) URL.revokeObjectURL(previous)
    frameError.value = ''
  } catch (e) {
    if (run !== sequence) return
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''; frameError.value = '摄像头离线，请检查工位采集器'
  } finally {
    if (run === sequence) timer = setTimeout(() => pollFrame(run), frameError.value ? 2500 : 500)
  }
}
async function restart() {
  cleanup(); const run = sequence
  if (!props.live || !props.station.enabled || props.station.canView === false || (props.station.cameraType === 'NETWORK' && props.station.streamConfigured === false)) return
  await nextTick()
  if (run !== sequence) return
  if (props.station.cameraType === 'USB') pollFrame(run)
  else {
    const url = mediaUrl(`/api/workstations/${props.station.id}/stream`)
    const ok = await start(video.value, url, { Authorization: `Bearer ${localStorage.getItem('wxz_token') || ''}` })
    if (!ok || run !== sequence) return
    let lastTime = video.value.currentTime, progressedAt = Date.now()
    const check = () => {
      if (run !== sequence) return
      if (video.value.currentTime !== lastTime) { lastTime = video.value.currentTime; progressedAt = Date.now() }
      if (Date.now() - progressedAt > 10000) { frameError.value = '画面中断，请检查摄像头或网络'; stop(video.value); return }
      timer = setTimeout(check, 2000)
    }
    timer = setTimeout(check, 2000)
  }
}
// 只在设备地址、编号或可见性变化时重连，工位配置刷新不会中断同一视频。
watch([
  () => props.station.id, () => props.station.cameraType, () => props.station.streamUrl,
  () => props.station.enabled, () => props.station.canView, () => props.station.streamConfigured, () => props.station.streamRevision, () => props.live
], restart, { immediate: true })
onBeforeUnmount(cleanup)
</script>
<style scoped>
.station-preview{position:relative;background:#101827;aspect-ratio:16/10;border-radius:9px;overflow:hidden;min-height:150px}
video,img{width:100%;height:100%;object-fit:contain;position:absolute;inset:0}.preview-hint{position:absolute;inset:0;background:#101827e8;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;padding:20px;color:#cbd5e1;font-size:13px;text-align:center}.preview-hint button{border:1px solid #64748b;border-radius:6px;padding:5px 12px;color:#e2e8f0;background:transparent}.preview-status{position:absolute;bottom:9px;left:9px;color:white;background:#0009;border-radius:5px;padding:3px 7px;font-size:11px}
</style>
