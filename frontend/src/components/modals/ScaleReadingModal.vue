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
          <h3 class="text-lg font-bold text-ink-strong">刻度识别</h3>
          <p class="text-[13px] text-ink-muted mt-1">
            {{ fieldLabel }}：让 0 刻度和当前读数基准线都进入画面，并把基准线对准绿色准线。
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
              <div v-if="previewUrl" class="preview-wrap">
                <img :src="previewUrl" alt="机械刻度照片" class="preview-img" />
                <span class="guide-line" :class="axis === 'horizontal' ? 'guide-line--vertical' : 'guide-line--horizontal'" />
              </div>
              <template v-else>
                <span class="upload-icon">+</span>
                <span class="upload-text">点击拍摄{{ fieldLabel }}</span>
                <span class="upload-sub">{{ captureHint }}</span>
              </template>
            </div>
            <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="onFileChange" />
            <div class="flex items-center justify-between gap-2">
              <p class="text-[11.5px] text-ink-faint leading-relaxed">
                识别后会先填入当前输入框，可先检查读数，确认无误后再保存为实验数据。
              </p>
              <button v-if="previewUrl" type="button" class="link-btn shrink-0" @click="retake">重拍</button>
            </div>
          </section>
          <p v-if="error" class="err-text">{{ error }}</p>
        </div>

        <div class="result-col">
          <div v-if="loading" class="result-empty">
            <p class="font-semibold text-ink-strong">正在识别刻度…</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">正在提取刻度线、估算小格间距并换算读数。</p>
          </div>

          <template v-else-if="result">
            <div class="flex flex-wrap items-center justify-between gap-2 mb-3">
              <h4 class="result-title">识别结果</h4>
              <span class="result-tag" :class="result.ok ? 'result-tag--ok' : 'result-tag--warn'">
                置信度 {{ Math.round((result.confidence || 0) * 100) }}%
              </span>
            </div>

            <div class="reading-card">
              <span class="reading-label">{{ fieldLabel }}</span>
              <strong>{{ result.display || '未能可靠识别' }}</strong>
            </div>

            <img v-if="debugUrl" :src="debugUrl" alt="刻度识别标注图" class="debug-img" />

            <ul v-if="result.steps?.length" class="info-list">
              <li v-for="(item, index) in result.steps" :key="`s-${index}`">{{ item }}</li>
            </ul>
            <ul v-if="result.warnings?.length" class="warn-list">
              <li v-for="(item, index) in result.warnings" :key="`w-${index}`">{{ item }}</li>
            </ul>

            <div class="save-row">
              <input v-model="manualReading" class="field-input flex-1" :placeholder="`确认后的${fieldLabel}`" />
              <button
                type="button"
                class="btn-brand px-4 py-2 rounded-lg text-sm shrink-0"
                :disabled="!manualReading.trim()"
                @click="applyReading"
              >
                填入
              </button>
            </div>
          </template>

          <div v-else class="result-empty">
            <p class="font-semibold text-ink-strong">结果会显示在这里</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">{{ captureHint }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { scaleReadingApi, uploadApi } from '../../api'
import { mediaUrl } from '../../api/runtime'

const props = defineProps({
  visible: Boolean,
  field: { type: Object, default: null }
})
const emit = defineEmits(['close', 'apply'])

const fileInput = ref(null)
const previewUrl = ref('')
const imageUrl = ref('')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const manualReading = ref('')

const fieldLabel = computed(() => props.field?.label || '机械刻度')
const axis = computed(() => props.field?.scaleAxis === 'horizontal' ? 'horizontal' : 'vertical')
const debugUrl = computed(() => result.value?.debugImageUrl ? mediaUrl(result.value.debugImageUrl) : '')
const captureHint = computed(() =>
  axis.value === 'horizontal'
    ? '横向刻度请让刻度线尽量水平，把当前读数基准线对准绿色竖线。'
    : '竖直刻度请让刻度线尽量竖直，把当前读数基准线对准绿色横线。'
)

watch(() => props.visible, (visible) => {
  if (visible) {
    clearImage()
    error.value = ''
    result.value = null
    manualReading.value = ''
  } else {
    clearImage()
  }
})

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
  result.value = null
  manualReading.value = ''
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file)
  try {
    const { data } = await uploadApi.image(file)
    imageUrl.value = data?.url || ''
    if (imageUrl.value) await recognize()
  } catch (e) {
    error.value = e.message || '图片上传失败'
    clearImage()
  }
}

async function recognize() {
  if (!imageUrl.value || !props.field?.key) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await scaleReadingApi.recognize({
      imageUrl: imageUrl.value,
      fieldKey: props.field.key,
      fieldLabel: fieldLabel.value,
      scaleAxis: props.field.scaleAxis || axis.value,
      scaleDirection: props.field.scaleDirection || '',
      scaleUnitPerTick: props.field.scaleUnitPerTick || 1,
      scaleZeroValue: props.field.scaleZeroValue || 0
    })
    result.value = data
    if (data?.value != null) {
      manualReading.value = String(data.value)
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '刻度识别失败'
  } finally {
    loading.value = false
  }
}

function retake() {
  clearImage()
  fileInput.value?.click()
}

function clearImage() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  imageUrl.value = ''
}

function applyReading() {
  const value = manualReading.value.trim()
  if (!value || !props.field?.key) return
  emit('apply', {
    value,
    instrumentKey: 'mechanical-scale',
    instrumentLabel: fieldLabel.value,
    precision: '1 mm',
    targetFieldKey: props.field.key
  })
  emit('close')
}
</script>

<style scoped>
.modal-body {
  @apply grid grid-cols-1 md:grid-cols-[300px_1fr] gap-px bg-line-soft flex-1 min-h-0 overflow-y-auto;
}
.input-col { @apply bg-white p-5 space-y-4; }
.result-col { @apply bg-white p-5 min-h-[240px]; }
.result-empty { @apply flex flex-col items-center justify-center text-center h-full py-10; }
.err-text { @apply text-[12px] text-rose-600; }
.link-btn { @apply text-[12px] text-brand-600 hover:underline mt-2; }
.upload-zone {
  @apply relative flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-line-soft
    bg-surface-soft/60 min-h-[300px] p-3 cursor-pointer hover:border-brand-300 transition-colors text-center overflow-hidden;
}
.upload-zone--filled { @apply border-solid p-1.5 bg-white; }
.upload-icon { @apply w-9 h-9 rounded-full bg-white border border-line-soft flex items-center justify-center text-lg text-ink-faint; }
.upload-text { @apply text-[13px] text-ink-base font-medium mt-1; }
.upload-sub { @apply text-[11.5px] text-ink-faint max-w-[220px]; }
.preview-wrap { @apply relative flex items-center justify-center w-full h-full min-h-[280px]; }
.preview-img { @apply max-h-[280px] w-auto rounded-lg object-contain; }
.guide-line { @apply absolute pointer-events-none bg-emerald-500 shadow-[0_0_0_1px_rgba(255,255,255,0.85),0_0_14px_rgba(16,185,129,0.55)]; }
.guide-line--horizontal { @apply left-2 right-2 top-1/2 h-0.5 -translate-y-1/2; }
.guide-line--vertical { @apply top-2 bottom-2 left-1/2 w-0.5 -translate-x-1/2; }
.result-title { @apply text-[15px] font-bold text-ink-strong; }
.result-tag { @apply text-[11px] px-2 py-0.5 rounded border; }
.result-tag--ok { @apply bg-emerald-50 text-emerald-700 border-emerald-100; }
.result-tag--warn { @apply bg-amber-50 text-amber-700 border-amber-100; }
.reading-card { @apply flex items-baseline justify-between gap-3 rounded-xl border border-line-soft bg-surface-soft/60 px-4 py-3; }
.reading-label { @apply text-[12px] text-ink-muted font-semibold; }
.reading-card strong { @apply text-xl text-ink-strong tabular-nums; }
.debug-img { @apply mt-3 max-h-[210px] w-full rounded-xl object-contain bg-slate-950; }
.info-list { @apply mt-3 space-y-1 text-[12px] text-ink-muted; }
.warn-list { @apply mt-3 space-y-1 text-[12px] text-amber-700 bg-amber-50 border border-amber-100 rounded-xl px-3 py-2; }
.save-row { @apply flex gap-2 mt-5 pt-4 border-t border-line-soft; }
.field-input { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
</style>
