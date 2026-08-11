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
          <section>
            <label class="field-label">仪器类型</label>
            <div class="chip-row">
              <button
                v-for="item in INSTRUMENTS"
                :key="item.key"
                type="button"
                class="chip"
                :class="{ 'chip--active': instrumentKey === item.key }"
                @click="selectInstrument(item.key)"
              >
                {{ item.label }}
              </button>
            </div>
          </section>

          <section>
            <label class="field-label">分度值 / 精度</label>
            <input v-model="precision" class="field-input" placeholder="如 0.02 mm" />
          </section>

          <section>
            <label class="field-label">刻度照片</label>
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
                <span class="upload-text">点击拍照或上传</span>
                <span class="upload-sub">对准刻度、正对镜头，避免反光与倾斜</span>
              </template>
            </div>
            <input ref="fileInput" type="file" accept="image/*" capture="environment" class="hidden" @change="onFileChange" />
            <button v-if="previewUrl" type="button" class="link-btn" @click="clearImage">重新选择</button>
          </section>

          <button
            type="button"
            class="btn-brand w-full py-2.5 rounded-xl text-sm font-semibold"
            :disabled="loading || !imageUrl"
            @click="recognize"
          >
            {{ loading ? '识别中…' : '识别读数' }}
          </button>
          <p v-if="error" class="err-text">{{ error }}</p>
        </div>

        <div class="result-col">
          <template v-if="answer">
            <div class="flex flex-wrap items-center gap-2 mb-3">
              <h4 class="result-title">识别结果</h4>
              <span v-if="!fromDify" class="result-tag">AI 服务未接入，以下为提示内容</span>
            </div>
            <div class="chat-md" v-html="renderMd(answer)" />
            <div class="save-row">
              <input v-model="manualReading" class="field-input flex-1" placeholder="确认后的最终读数，如 12.34" />
              <button
                type="button"
                class="btn-brand px-4 py-2 rounded-lg text-sm shrink-0"
                :disabled="!manualReading.trim()"
                @click="applyReading"
              >
                确认填入对话
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
              AI 识别仅作参考。确认后会把读数放入右侧对话附件，由你发送入库，不会自动改原始数据。
            </p>
          </template>
          <div v-else class="result-empty">
            <p class="font-semibold text-ink-strong">识别结果显示在这里</p>
            <p class="text-[13px] text-ink-muted mt-1 max-w-xs">
              选择仪器类型并上传照片后点击「识别读数」。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
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
  stepNo: { type: Number, default: 0 }
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

watch(() => props.visible, (visible) => {
  if (!visible) {
    error.value = ''
    manualReading.value = ''
  }
})

function selectInstrument(key) {
  instrumentKey.value = key
  precision.value = INSTRUMENTS.find((i) => i.key === key)?.precision || ''
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
  previewUrl.value = URL.createObjectURL(file)
  try {
    const { data } = await uploadApi.image(file)
    imageUrl.value = data?.url || ''
  } catch (e) {
    error.value = e.message || '图片上传失败'
    previewUrl.value = ''
    imageUrl.value = ''
  }
}

function clearImage() {
  previewUrl.value = ''
  imageUrl.value = ''
  answer.value = ''
}

async function recognize() {
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
        stepNo: props.stepNo
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
    stepNo: props.stepNo
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

.chip-row { @apply flex flex-wrap gap-2; }
.chip {
  @apply px-3 py-1.5 rounded-lg text-[12.5px] border border-line-soft bg-surface-soft
    hover:border-brand-200 transition-colors;
}
.chip--active { @apply border-brand-400 bg-brand-50 text-brand-700 font-semibold; }

.upload-zone {
  @apply flex flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed border-line-soft
    bg-surface-soft/60 min-h-[140px] p-3 cursor-pointer hover:border-brand-300 transition-colors text-center;
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
