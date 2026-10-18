<template>
  <div
    v-if="visible"
    class="fixed inset-0 z-50 flex items-center justify-center p-4 md:p-6 bg-slate-900/30 backdrop-blur-md"
    @click.self="$emit('close')"
  >
    <div class="relative w-full max-w-xl max-h-[88vh] rounded-3xl overflow-hidden flex flex-col bg-white border border-line-soft shadow-lift fade-in-up">
      <div class="h-1.5 w-full brand-gradient shrink-0" />
      <div class="p-5 md:p-6 border-b border-line-soft shrink-0">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex items-center gap-2 mb-1.5">
              <span class="chip text-brand-700 bg-brand-50 border-brand-100 font-mono text-[11px]">
                STEP {{ String(stepNo).padStart(2, '0') }}
              </span>
              <span v-if="deviceLabel" class="text-[11px] text-ink-faint">{{ deviceLabel }}</span>
            </div>
            <h3 class="text-lg font-bold text-ink-strong">{{ stepTitle || '仪器操作要点' }}</h3>
            <p v-if="stepDesc" class="text-[13px] text-ink-muted mt-1 line-clamp-2">{{ stepDesc }}</p>
          </div>
          <button
            type="button"
            class="w-9 h-9 rounded-xl text-ink-faint hover:text-ink-strong hover:bg-surface-muted text-2xl leading-none flex items-center justify-center shrink-0"
            @click="$emit('close')"
          >
            ×
          </button>
        </div>
      </div>

      <div class="p-5 md:p-6 overflow-y-auto custom-scroll flex-1 min-h-0 space-y-4">
        <section v-if="fieldLabels.length" class="rounded-xl border border-line-soft bg-surface-soft/60 p-4">
          <h4 class="text-[12px] font-bold text-ink-muted uppercase tracking-wide mb-2">本步数据字段</h4>
          <ul class="flex flex-wrap gap-2">
            <li
              v-for="(label, i) in fieldLabels"
              :key="i"
              class="px-2.5 py-1 rounded-lg bg-white border border-line-soft text-[12px] text-ink-base"
            >
              {{ label }}
            </li>
          </ul>
        </section>

        <div v-if="loading" class="flex flex-col items-center justify-center py-8 gap-3 text-ink-muted text-sm">
          <div class="w-10 h-10 rounded-xl brand-gradient flex items-center justify-center text-white font-bold shadow-brand">智</div>
          <p>正在整理操作要点…</p>
        </div>
        <div v-else-if="error" class="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
          {{ error }}
          <button type="button" class="block mt-3 text-brand-600 font-semibold hover:underline" @click="loadGuide">重试</button>
        </div>
        <div v-else class="chat-md guide-content" v-html="renderMd(content)" />
      </div>

      <div class="p-4 border-t border-line-soft bg-surface-soft flex justify-end shrink-0">
        <button type="button" class="btn-brand px-8 py-2.5 rounded-xl font-bold text-sm" @click="$emit('close')">
          知道了
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { aiToolApi } from '../../api'
import { renderChatMarkdown } from '../../utils/markdown'

const DEVICE_LABELS = {
  dimension_measure: '尺寸测量',
  universal_tester: '万能试验机',
  post_measure: '后测量',
  reading_microscope: '读数显微镜',
  newton_analyzer: '牛顿环分析仪'
}

const props = defineProps({
  visible: Boolean,
  experimentName: { type: String, default: '' },
  experimentCode: { type: String, default: '' },
  stepNo: { type: Number, default: 1 },
  stepTitle: { type: String, default: '' },
  stepDesc: { type: String, default: '' },
  deviceType: { type: String, default: '' },
  dataFields: { type: Array, default: () => [] }
})

defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const content = ref('')

const deviceLabel = computed(() => DEVICE_LABELS[props.deviceType] || (props.deviceType ? props.deviceType : ''))

const fieldLabels = computed(() =>
  (props.dataFields || [])
    .map((f) => f?.label || f?.name || f?.key)
    .filter(Boolean)
)

watch(
  () => [props.visible, props.stepNo, props.stepTitle],
  ([visible]) => {
    if (visible) loadGuide()
  }
)

async function loadGuide() {
  loading.value = true
  error.value = ''
  try {
    const fieldsText = fieldLabels.value.length ? fieldLabels.value.join('、') : '无'
    const { data } = await aiToolApi.invoke('instrument-guide', {
      action: 'instrument',
      inputs: {
        experimentCode: props.experimentCode,
        experimentName: props.experimentName,
        stepNo: props.stepNo,
        stepTitle: props.stepTitle,
        stepDesc: props.stepDesc,
        deviceType: props.deviceType,
        deviceLabel: deviceLabel.value,
        dataFields: fieldsText
      }
    })
    content.value = data?.text || '暂无仪器要点，请参考本步骤教程或询问右侧 AI 助手。'
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function renderMd(text) {
  return renderChatMarkdown(text, { normalize: true })
}
</script>

<style scoped>
.guide-content :deep(p) {
  @apply text-[14px] leading-relaxed text-ink-base mb-2 last:mb-0;
}
.guide-content :deep(ul),
.guide-content :deep(ol) {
  @apply pl-5 space-y-1.5 text-[14px] text-ink-base mb-3;
}
.guide-content :deep(ul) {
  @apply list-disc;
}
.guide-content :deep(ol) {
  @apply list-decimal;
}
</style>
