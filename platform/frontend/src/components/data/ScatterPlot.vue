<template>
  <div class="plot-wrap">
    <canvas ref="canvasEl" class="plot-canvas" />
    <div class="plot-actions">
      <button type="button" class="btn-brand px-4 py-2 rounded-lg text-[13px] font-semibold" @click="download">
        导出 PNG
      </button>
      <button type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px]" @click="copyImage">
        {{ copied ? '已复制' : '复制到剪贴板' }}
      </button>
      <button v-if="savable" type="button" class="btn-ghost px-4 py-2 rounded-lg text-[13px]" @click="emitSave">
        存入资料库
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'

const props = defineProps({
  xs: { type: Array, default: () => [] },
  ys: { type: Array, default: () => [] },
  fit: { type: Object, default: null },
  xLabel: { type: String, default: 'x' },
  yLabel: { type: String, default: 'y' },
  title: { type: String, default: '' },
  savable: { type: Boolean, default: false }
})

const emit = defineEmits(['save'])

const W = 720
const H = 460
const PAD = { top: 48, right: 28, bottom: 56, left: 74 }

const canvasEl = ref(null)
const copied = ref(false)

onMounted(draw)
watch(() => [props.xs, props.ys, props.fit, props.title], draw, { deep: true })

function niceStep(range, targetTicks) {
  const rough = range / targetTicks
  const mag = 10 ** Math.floor(Math.log10(rough))
  const norm = rough / mag
  const step = norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10
  return step * mag
}

function draw() {
  const canvas = canvasEl.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = W * dpr
  canvas.height = H * dpr
  canvas.style.width = '100%'
  canvas.style.aspectRatio = `${W} / ${H}`

  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, W, H)

  const n = Math.min(props.xs.length, props.ys.length)
  if (n < 2) {
    ctx.fillStyle = '#9ca3af'
    ctx.font = '15px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('至少需要 2 组数据才能作图', W / 2, H / 2)
    return
  }

  const xs = props.xs.slice(0, n)
  const ys = props.ys.slice(0, n)
  const xMin = Math.min(...xs)
  const xMax = Math.max(...xs)
  const yMin = Math.min(...ys)
  const yMax = Math.max(...ys)
  const xPadRange = (xMax - xMin) * 0.08 || Math.abs(xMax) * 0.1 || 1
  const yPadRange = (yMax - yMin) * 0.1 || Math.abs(yMax) * 0.1 || 1
  const x0 = xMin - xPadRange
  const x1 = xMax + xPadRange
  const y0 = yMin - yPadRange
  const y1 = yMax + yPadRange

  const plotW = W - PAD.left - PAD.right
  const plotH = H - PAD.top - PAD.bottom
  const toPx = (x) => PAD.left + ((x - x0) / (x1 - x0)) * plotW
  const toPy = (y) => PAD.top + plotH - ((y - y0) / (y1 - y0)) * plotH

  if (props.title) {
    ctx.fillStyle = '#111827'
    ctx.font = 'bold 16px system-ui, sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText(props.title, W / 2, 28)
  }

  ctx.strokeStyle = '#e5e7eb'
  ctx.fillStyle = '#6b7280'
  ctx.font = '12px system-ui, sans-serif'
  ctx.lineWidth = 1

  const xStep = niceStep(x1 - x0, 6)
  ctx.textAlign = 'center'
  for (let v = Math.ceil(x0 / xStep) * xStep; v <= x1; v += xStep) {
    const px = toPx(v)
    ctx.beginPath()
    ctx.moveTo(px, PAD.top)
    ctx.lineTo(px, PAD.top + plotH)
    ctx.stroke()
    ctx.fillText(formatTick(v, xStep), px, PAD.top + plotH + 20)
  }

  const yStep = niceStep(y1 - y0, 6)
  ctx.textAlign = 'right'
  for (let v = Math.ceil(y0 / yStep) * yStep; v <= y1; v += yStep) {
    const py = toPy(v)
    ctx.beginPath()
    ctx.moveTo(PAD.left, py)
    ctx.lineTo(PAD.left + plotW, py)
    ctx.stroke()
    ctx.fillText(formatTick(v, yStep), PAD.left - 10, py + 4)
  }

  ctx.strokeStyle = '#374151'
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(PAD.left, PAD.top)
  ctx.lineTo(PAD.left, PAD.top + plotH)
  ctx.lineTo(PAD.left + plotW, PAD.top + plotH)
  ctx.stroke()

  ctx.fillStyle = '#374151'
  ctx.font = '13px system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(props.xLabel, PAD.left + plotW / 2, H - 14)
  ctx.save()
  ctx.translate(20, PAD.top + plotH / 2)
  ctx.rotate(-Math.PI / 2)
  ctx.fillText(props.yLabel, 0, 0)
  ctx.restore()

  if (props.fit) {
    const { slope, intercept } = props.fit
    ctx.strokeStyle = '#ef4444'
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(toPx(x0), toPy(slope * x0 + intercept))
    ctx.lineTo(toPx(x1), toPy(slope * x1 + intercept))
    ctx.stroke()

    ctx.fillStyle = '#ef4444'
    ctx.font = '13px system-ui, sans-serif'
    ctx.textAlign = 'left'
    const eq = `y = ${slope.toFixed(4)}x ${intercept >= 0 ? '+' : '−'} ${Math.abs(intercept).toFixed(4)}`
    ctx.fillText(eq, PAD.left + 12, PAD.top + 20)
    ctx.fillText(`R² = ${props.fit.r2.toFixed(4)}`, PAD.left + 12, PAD.top + 38)
  }

  ctx.fillStyle = '#2563eb'
  for (let i = 0; i < n; i += 1) {
    ctx.beginPath()
    ctx.arc(toPx(xs[i]), toPy(ys[i]), 4, 0, Math.PI * 2)
    ctx.fill()
  }
}

function formatTick(v, step) {
  const decimals = Math.max(0, -Math.floor(Math.log10(step)))
  return v.toFixed(Math.min(6, decimals))
}

function download() {
  const canvas = canvasEl.value
  if (!canvas) return
  const a = document.createElement('a')
  a.href = canvas.toDataURL('image/png')
  a.download = `${props.title || '实验作图'}.png`
  a.click()
}

function emitSave() {
  const canvas = canvasEl.value
  if (!canvas) return
  const name = `${props.title || '实验作图'}.png`
  canvas.toBlob((blob) => {
    if (blob) emit('save', new File([blob], name, { type: 'image/png' }))
  }, 'image/png')
}

async function copyImage() {
  const canvas = canvasEl.value
  if (!canvas || !navigator.clipboard?.write) return
  canvas.toBlob(async (blob) => {
    try {
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
      copied.value = true
      setTimeout(() => { copied.value = false }, 2000)
    } catch {
      copied.value = false
    }
  })
}
</script>

<style scoped>
.plot-wrap { @apply rounded-xl border border-line-soft bg-white p-3; }
.plot-canvas { @apply w-full block rounded-lg; }
.plot-actions { @apply flex gap-2 mt-3; }
</style>
