const DEFAULT_MAX_DURATION_MS = 60_000
const TARGET_SAMPLE_RATE = 16_000
const MAX_NORMALIZE_GAIN = 12

function mergeBuffers(chunks, totalLength) {
  const merged = new Float32Array(totalLength)
  let offset = 0
  chunks.forEach((chunk) => {
    merged.set(chunk, offset)
    offset += chunk.length
  })
  return merged
}

function floatTo16BitPcm(view, offset, input) {
  for (let i = 0; i < input.length; i += 1, offset += 2) {
    const sample = Math.max(-1, Math.min(1, input[i]))
    view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true)
  }
}

function writeAscii(view, offset, value) {
  for (let i = 0; i < value.length; i += 1) {
    view.setUint8(offset + i, value.charCodeAt(i))
  }
}

function encodeWav(samples, sampleRate) {
  const bytesPerSample = 2
  const blockAlign = bytesPerSample
  const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample)
  const view = new DataView(buffer)

  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * bytesPerSample, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * blockAlign, true)
  view.setUint16(32, blockAlign, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, samples.length * bytesPerSample, true)
  floatTo16BitPcm(view, 44, samples)

  return new Blob([view], { type: 'audio/wav' })
}

function removeDcOffset(samples) {
  if (!samples.length) return samples
  let sum = 0
  for (let i = 0; i < samples.length; i += 1) sum += samples[i]
  const offset = sum / samples.length
  if (Math.abs(offset) < 0.00001) return samples
  const adjusted = new Float32Array(samples.length)
  for (let i = 0; i < samples.length; i += 1) adjusted[i] = samples[i] - offset
  return adjusted
}

function normalizeSpeech(samples) {
  if (!samples.length) return samples
  let peak = 0
  for (let i = 0; i < samples.length; i += 1) {
    peak = Math.max(peak, Math.abs(samples[i]))
  }
  if (peak <= 0.00001) return samples

  const gain = Math.min(MAX_NORMALIZE_GAIN, 0.92 / peak)
  if (gain <= 1.05) return samples

  const normalized = new Float32Array(samples.length)
  for (let i = 0; i < samples.length; i += 1) {
    normalized[i] = Math.max(-1, Math.min(1, samples[i] * gain))
  }
  return normalized
}

function resampleLinear(samples, fromRate, toRate) {
  if (!samples.length || fromRate === toRate) return samples
  const ratio = fromRate / toRate
  const newLength = Math.max(1, Math.round(samples.length / ratio))
  const result = new Float32Array(newLength)

  for (let i = 0; i < newLength; i += 1) {
    const sourceIndex = i * ratio
    const left = Math.floor(sourceIndex)
    const right = Math.min(left + 1, samples.length - 1)
    const weight = sourceIndex - left
    result[i] = samples[left] * (1 - weight) + samples[right] * weight
  }
  return result
}

function prepareSpeechSamples(samples, sampleRate) {
  const cleaned = removeDcOffset(samples)
  const normalized = normalizeSpeech(cleaned)
  return resampleLinear(normalized, sampleRate, TARGET_SAMPLE_RATE)
}

export async function createWavRecorder({ maxDurationMs = DEFAULT_MAX_DURATION_MS, onAutoStop } = {}) {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error('当前环境不支持麦克风录音，请换用 Chrome/Edge 或检查 APK WebView 权限')
  }

  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true,
      channelCount: { ideal: 1 },
      sampleRate: { ideal: TARGET_SAMPLE_RATE }
    }
  })
  const AudioContextClass = window.AudioContext || window.webkitAudioContext
  if (!AudioContextClass) {
    stream.getTracks().forEach((track) => track.stop())
    throw new Error('当前环境不支持音频采集')
  }

  const audioContext = new AudioContextClass()
  await audioContext.resume()

  const source = audioContext.createMediaStreamSource(stream)
  const processor = audioContext.createScriptProcessor(4096, 1, 1)
  const chunks = []
  let totalLength = 0
  let stopped = false

  processor.onaudioprocess = (event) => {
    if (stopped) return
    const input = event.inputBuffer.getChannelData(0)
    chunks.push(new Float32Array(input))
    totalLength += input.length
  }

  source.connect(processor)
  processor.connect(audioContext.destination)

  const timer = window.setTimeout(() => {
    if (!stopped && typeof onAutoStop === 'function') onAutoStop()
  }, maxDurationMs)

  return {
    async stop() {
      if (stopped) return null
      stopped = true
      window.clearTimeout(timer)
      processor.disconnect()
      source.disconnect()
      stream.getTracks().forEach((track) => track.stop())
      await audioContext.close()
      if (totalLength <= 0) return null
      const samples = prepareSpeechSamples(mergeBuffers(chunks, totalLength), audioContext.sampleRate)
      return encodeWav(samples, TARGET_SAMPLE_RATE)
    },
    cancel() {
      if (stopped) return
      stopped = true
      window.clearTimeout(timer)
      processor.disconnect()
      source.disconnect()
      stream.getTracks().forEach((track) => track.stop())
      audioContext.close().catch(() => {})
    }
  }
}
