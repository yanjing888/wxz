<template>
  <AgentWorkspace
    :agent="agent"
    :tool="tool"
    v-model:experiment="experimentCode"
    empty-title="上传照片后识别读数"
    empty-hint="选择仪器类型、拍摄或上传刻度照片，AI 会给出读数并逐步讲解读数过程。"
  >
    <template #input>
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
        <p class="field-hint">选择仪器后自动填入常见值，可按实际仪器修改。</p>
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

      <section>
        <label class="field-label">补充说明（可选）</label>
        <textarea v-model="note" class="field-textarea" rows="2" placeholder="如：测量的是小球直径，量程 25mm" />
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

      <section v-if="history.length" class="history">
        <div class="flex items-center justify-between mb-2">
          <h3 class="field-label mb-0">本次读数记录（{{ history.length }}）</h3>
          <button type="button" class="link-btn" @click="history = []">清空</button>
        </div>
        <ul class="history-list">
          <li v-for="(h, i) in history" :key="i">
            <span class="history-idx">{{ i + 1 }}</span>
            <span class="history-val">{{ h }}</span>
          </li>
        </ul>
        <button type="button" class="btn-ghost w-full py-2 rounded-lg text-[13px] mt-2" @click="sendToDoctor">
          把这组读数送去「数据体检」
        </button>
      </section>
    </template>

    <template v-if="answer" #result>
      <div class="result-head">
        <h3 class="result-title">识别结果</h3>
        <span v-if="!fromDify" class="result-tag">AI 服务未接入，以下为提示内容</span>
      </div>
      <div class="chat-md" v-html="renderMd(answer)" />
      <div class="save-row">
        <input v-model="manualReading" class="field-input flex-1" placeholder="确认后填入最终读数，如 12.34 mm" />
        <button type="button" class="btn-brand px-4 py-2 rounded-lg text-sm shrink-0" :disabled="!manualReading.trim()" @click="saveReading">
          记录
        </button>
      </div>
      <p class="field-hint">读数以你自己的判断为准，AI 识别仅作参考。记录后可一并送去数据体检。</p>
    </template>
  </AgentWorkspace>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AgentWorkspace from '../../components/ai/AgentWorkspace.vue'
import { useAgentTool } from '../../composables/useAgentTool'
import { uploadApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'

const props = defineProps({
  tool: { type: Object, required: true },
  agent: { type: Object, default: null }
})

const INSTRUMENTS = [
  { key: 'vernier', label: '游标卡尺', precision: '0.02 mm' },
  { key: 'micrometer', label: '螺旋测微器', precision: '0.01 mm' },
  { key: 'microscope', label: '读数显微镜', precision: '0.01 mm' },
  { key: 'ruler', label: '米尺 / 钢直尺', precision: '1 mm' },
  { key: 'spectrometer', label: '分光计', precision: "1'" },
  { key: 'meter', label: '电表 / 电桥', precision: '' }
]

const route = useRoute()
const router = useRouter()
const { experimentCode, loading, error, invoke } = useAgentTool(props.tool.code)
const stepNo = Number(route.query.step) || 0

const instrumentKey = ref('vernier')
const precision = ref('0.02 mm')
const note = ref('')
const fileInput = ref(null)
const previewUrl = ref('')
const imageUrl = ref('')
const answer = ref('')
const fromDify = ref(true)
const manualReading = ref('')
const history = ref([])

function selectInstrument(key) {
  instrumentKey.value = key
  const hit = INSTRUMENTS.find((i) => i.key === key)
  precision.value = hit?.precision || ''
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
  const label = INSTRUMENTS.find((i) => i.key === instrumentKey.value)?.label || '测量仪器'
  try {
    const data = await invoke(
      'reading',
      {
        instrumentKey: instrumentKey.value,
        instrumentLabel: label,
        precision: precision.value,
        note: note.value,
        stepNo
      },
      { imageUrl: imageUrl.value }
    )
    answer.value = data.text || ''
    fromDify.value = data.fromDify !== false
  } catch {
    // error 已由 composable 记录
  }
}

function saveReading() {
  const value = manualReading.value.trim()
  if (!value) return
  history.value.push(value)
  manualReading.value = ''
}

function sendToDoctor() {
  router.push({
    name: 'ai-tool',
    params: { code: 'data-doctor' },
    query: {
      exp: experimentCode.value || undefined,
      data: history.value.join(', '),
      precision: precision.value || undefined
    }
  })
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.field-label { @apply block text-[13px] font-bold text-ink-strong mb-2; }
.field-input { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.field-textarea { @apply w-full rounded-lg border border-line-soft px-3 py-2 text-[14px]; }
.field-hint { @apply text-[12px] text-ink-faint mt-1.5 leading-relaxed; }
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
    bg-surface-soft/60 min-h-[150px] p-3 cursor-pointer hover:border-brand-300 transition-colors text-center;
}
.upload-zone--filled { @apply border-solid p-1.5 bg-white; }
.upload-icon { @apply w-9 h-9 rounded-full bg-white border border-line-soft flex items-center justify-center text-lg text-ink-faint; }
.upload-text { @apply text-[13px] text-ink-base font-medium mt-1; }
.upload-sub { @apply text-[11.5px] text-ink-faint; }
.preview-img { @apply max-h-[220px] w-auto rounded-lg object-contain; }

.history-list { @apply flex flex-wrap gap-2; }
.history-list li { @apply flex items-center gap-1.5 px-2 py-1 rounded-lg bg-surface-soft border border-line-soft; }
.history-idx { @apply text-[10px] text-ink-faint; }
.history-val { @apply text-[13px] font-mono text-ink-strong; }

.result-head { @apply flex flex-wrap items-center gap-2 mb-3; }
.result-title { @apply text-[15px] font-bold text-ink-strong; }
.result-tag { @apply text-[11px] px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-100; }
.save-row { @apply flex gap-2 mt-5 pt-4 border-t border-line-soft; }
</style>
