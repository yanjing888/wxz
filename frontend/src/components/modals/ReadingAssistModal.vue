<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-slate-900/30 backdrop-blur-md"
    @click.self="$emit('close')"
  >
    <div class="relative w-full max-w-3xl max-h-[90vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full brand-gradient shrink-0" />
      <header class="p-5 border-b border-line-soft shrink-0 flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h3 class="text-lg font-bold text-ink-strong">读数助手</h3>
          <p class="text-[13px] text-ink-muted mt-1">
            拍下刻度，AI 识别读数并讲解读数过程。最终读数以你自己的判断为准。
          </p>
        </div>
        <button
          type="button"
          class="w-9 h-9 rounded-xl text-ink-faint hover:text-ink-strong hover:bg-surface-muted text-2xl leading-none flex items-center justify-center shrink-0"
          @click="$emit('close')"
        >
          ×
        </button>
      </header>

      <div class="modal-body custom-scroll">
        <div class="input-col">
          <section class="space-y-3">
            <div
              class="upload-zone"
              :class="{ 'upload-zone--filled': previewUrl }"
              @click="fileInput?.click()"
              @dragover.prevent
              @drop.prevent="onDrop"
            >
              <img v-if="previewUrl" :src="previewUrl" alt="刻度照片" class="preview-img" />
              <template v-else>
                <span class="upload-icon">+</span>
                <span class="upload-text">点击拍照识别</span>
                <span class="upload-sub">对准刻度或数显屏，拍完后右侧自动显示 OCR 结果</span>
              </template>
            </div>
            <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="onFileChange" />
            <div class="flex items-center justify-between gap-2">
              <p class="text-[11.5px] text-ink-faint leading-relaxed">
                {{ targetField ? `将回填：${targetField.label || targetField.key}${targetField.unit ? ` / ${targetField.unit}` : ''}` : '拍照后可复制识别结果或确认填入读数区' }}
              </p>
              <button v-if="previewUrl" type="button" class="link-btn shrink-0" @click="retake">
                重拍
              </button>
            </div>
          </section>
          <p v-if="error" class="err-text">{{ error }}</p>
        </div>

        <div class="result-col">
          <div v-if="loading" class="result-empty">
            <p class="font-semibold text-ink-strong">正在 OCR 识别…</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">请稍等，识别结果会显示在这里。</p>
          </div>
          <template v-else-if="answer">
            <div class="flex flex-wrap items-center gap-2 mb-3">
              <h4 class="result-title">OCR 识别结果</h4>
              <span v-if="!fromDify" class="result-tag">AI 服务未接入，以下为提示内容</span>
            </div>
            <div class="chat-md" v-html="renderMd(answer)" />
            <div class="save-row">
              <input v-model="manualReading" class="field-input flex-1" :placeholder="manualReadingPlaceholder" />
              <button
                type="button"
                class="btn-brand px-4 py-2 rounded-lg text-sm shrink-0"
                :disabled="!manualReading.trim()"
                @click="applyReading"
              >
                确认填入读数区
              </button>
              <button
                type="button"
                class="btn-ghost px-3 py-2 rounded-lg text-sm shrink-0 border border-line-soft"
                :disabled="!manualReading.trim()"
                @click="copyReading"
              >
                {{ copied ? '已复制' : '复制' }}
              </button>
            </div>
            <p class="text-[12px] text-ink-faint mt-2">
              AI 识别仅作参考。确认后会先填入左侧读数区，可先检查纠错，确认无误后再保存为实验数据。
            </p>
          </template>
          <div v-else class="result-empty">
            <p class="font-semibold text-ink-strong">OCR 识别结果显示在这里</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">
              左侧拍照后会自动识别读数；你确认后再填入读数区。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { aiToolApi, uploadApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'

const INSTRUMENTS = [
  { key: 'vernier', label: '游标卡尺', precision: '0.02 mm' },
  { key: 'micrometer', label: '螺旋测微器', precision: '0.01 mm' },
  { key: 'microscope', label: '读数显微镜', precision: '0.01 mm' },
  { key: 'ruler', label: '米尺 / 钢直尺', precision: '1 mm' },
  { key: 'spectrometer', label: '分光计', precision: "1'" },
  { key: 'meter', label: '电表 / 电桥', precision: '' }
]

const props = defineProps({
  visible: Boolean,
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  stepNo: { type: Number, default: 0 },
  stepTitle: { type: String, default: '' },
  deviceType: { type: String, default: '' },
  dataFields: { type: Array, default: () => [] },
  initialTargetFieldKey: { type: String, default: '' }
})
const emit = defineEmits(['close', 'apply'])

const instrumentKey = ref('vernier')
const precision = ref('0.02 mm')
const fileInput = ref(null)
const previewUrl = ref('')
const imageUrl = ref('')
const answer = ref('')
const error = ref('')
const loading = ref(false)
const fromDify = ref(true)
const manualReading = ref('')
const copied = ref(false)
const targetFieldKey = ref('')

const fillableFields = computed(() =>
  (props.dataFields || []).filter((field) =>
    field?.key && !field.computed && !field.readOnly && field.type !== 'computed'
  )
)

const targetField = computed(() =>
  fillableFields.value.find((field) => field.key === targetFieldKey.value) || null
)

const manualReadingPlaceholder = computed(() => {
  const label = targetField.value?.label || '最终读数'
  const unit = targetField.value?.unit ? ` ${targetField.value.unit}` : ''
  return `确认后的${label}，如 12.34${unit}`
})

watch(() => props.visible, (visible) => {
  if (visible) {
    resetDefaultsForStep()
    clearImage()
  } else {
    error.value = ''
    manualReading.value = ''
    clearImage()
  }
})

watch(
  () => [
    props.experimentCode,
    props.stepNo,
    props.deviceType,
    props.initialTargetFieldKey,
    fillableFields.value.map((f) => f.key).join(',')
  ],
  () => resetDefaultsForStep(),
  { immediate: true }
)

function resetDefaultsForStep() {
  applyInstrumentDefaults()
  if (props.initialTargetFieldKey && fillableFields.value.some((field) => field.key === props.initialTargetFieldKey)) {
    targetFieldKey.value = props.initialTargetFieldKey
    return
  }
  if (!targetFieldKey.value || !fillableFields.value.some((field) => field.key === targetFieldKey.value)) {
    targetFieldKey.value = defaultTargetFieldKey()
  }
}

function defaultTargetFieldKey() {
  const fields = fillableFields.value
  return fields.find((field) => /left|左侧读数|左读数/i.test(`${field.key} ${field.label}`))?.key
    || fields.find((field) => /读数|reading/i.test(`${field.key} ${field.label}`))?.key
    || fields.find((field) => field.required !== false)?.key
    || fields[0]?.key
    || ''
}

function applyInstrumentDefaults() {
  if (props.deviceType === 'reading_microscope' || props.experimentCode === 'newton_rings') {
    instrumentKey.value = 'microscope'
  } else {
    instrumentKey.value = inferInstrumentKey()
  }
  precision.value = INSTRUMENTS.find((i) => i.key === instrumentKey.value)?.precision || ''
}

function inferInstrumentKey() {
  const text = `${targetField.value?.label || ''} ${targetField.value?.key || ''} ${props.stepTitle || ''}`
  if (/游标|vernier|卡尺/i.test(text)) return 'vernier'
  if (/螺旋|micrometer|千分尺/i.test(text)) return 'micrometer'
  if (/显微镜|microscope|暗环|读数/i.test(text)) return 'microscope'
  if (/角|分光|spectrometer/i.test(text)) return 'spectrometer'
  if (/电压|电流|电阻|电表|meter/i.test(text)) return 'meter'
  return 'ruler'
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  if (file) uploadImage(file)
  event.target.value = ''
}

function onDrop(event) {
  const file = event.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) uploadImage(file)
}

async function uploadImage(file) {
  error.value = ''
  answer.value = ''
  manualReading.value = ''
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = URL.createObjectURL(file)
  try {
    const { data } = await uploadApi.image(file)
    imageUrl.value = data?.url || ''
    if (imageUrl.value) {
      await recognize()
    }
  } catch (e) {
    error.value = e.message || '图片上传失败'
    previewUrl.value = ''
    imageUrl.value = ''
  }
}

function clearImage() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  imageUrl.value = ''
  answer.value = ''
}

function retake() {
  clearImage()
  fileInput.value?.click()
}

async function recognize() {
  if (!imageUrl.value) return
  answer.value = ''
  error.value = ''
  loading.value = true
  const label = INSTRUMENTS.find((i) => i.key === instrumentKey.value)?.label || '测量仪器'
  try {
    const { data } = await aiToolApi.invoke('instrument-reading', {
      action: 'reading',
      imageUrl: imageUrl.value,
      inputs: {
        experimentCode: props.experimentCode,
        experimentName: props.experimentName,
        instrumentKey: instrumentKey.value,
        instrumentLabel: label,
        precision: precision.value,
        stepNo: props.stepNo,
        stepTitle: props.stepTitle,
        targetFieldKey: targetFieldKey.value,
        targetFieldLabel: fillableFields.value.find((field) => field.key === targetFieldKey.value)?.label || ''
      }
    })
    answer.value = data.text || ''
    fromDify.value = data.fromDify !== false
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '识别失败'
  } finally {
    loading.value = false
  }
}

function applyReading() {
  const value = manualReading.value.trim()
  if (!value) return
  const label = INSTRUMENTS.find((i) => i.key === instrumentKey.value)?.label || '测量仪器'
  emit('apply', {
    value,
    instrumentKey: instrumentKey.value,
    instrumentLabel: label,
    precision: precision.value,
    stepNo: props.stepNo,
    targetFieldKey: targetFieldKey.value
  })
  emit('close')
}

function copyReading() {
  const value = manualReading.value.trim()
  if (!value) return
  navigator.clipboard?.writeText(value).catch(() => {})
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.modal-body {
  @apply grid grid-cols-1 md:grid-cols-[300px_1fr] gap-px bg-line-soft flex-1 min-h-0 overflow-y-auto;
}
.input-col { @apply bg-white p-5 space-y-4; }
.result-col { @apply bg-white p-5 min-h-[240px]; }
.result-empty { @apply flex flex-col items-center justify-center text-center h-full py-10; }

.field-label { @apply block text-[13px] font-bold text-ink-strong mb-2; }
.field-input { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.err-text { @apply text-[12px] text-rose-600; }
.link-btn { @apply text-[12px] text-brand-600 hover:underline mt-2; }

.upload-zone {
  @apply flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-line-soft
    bg-surface-soft/60 min-h-[300px] p-3 cursor-pointer hover:border-brand-300 transition-colors text-center;
}
.upload-zone--filled { @apply border-solid p-1.5 bg-white; }
.upload-icon { @apply w-9 h-9 rounded-full bg-white border border-line-soft flex items-center justify-center text-lg text-ink-faint; }
.upload-text { @apply text-[13px] text-ink-base font-medium mt-1; }
.upload-sub { @apply text-[11.5px] text-ink-faint; }
.preview-img { @apply max-h-[200px] w-auto rounded-lg object-contain; }

.result-title { @apply text-[15px] font-bold text-ink-strong; }
.result-tag { @apply text-[11px] px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100; }
.save-row { @apply flex gap-2 mt-5 pt-4 border-t border-line-soft; }
</style>
