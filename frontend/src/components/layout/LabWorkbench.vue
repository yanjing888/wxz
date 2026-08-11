<template>
  <section class="workbench flex-1 min-h-0 flex flex-col overflow-hidden">
    <nav class="wb-tabs shrink-0" aria-label="实验台功能">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="wb-tab"
        :class="{ 'wb-tab--active': modelValue === tab.key }"
        @click="$emit('update:modelValue', tab.key)"
      >
        {{ tab.label }}
        <span v-if="tab.key === 'data' && dataCount" class="wb-badge">{{ dataCount }}</span>
        <span v-if="tab.key === 'files' && fileCount" class="wb-badge">{{ fileCount }}</span>
      </button>
    </nav>

    <div class="wb-body flex-1 min-h-0 flex flex-col overflow-hidden">
      <RightPanel
        v-show="modelValue === 'guide'"
        class="flex-1 min-h-0"
        :show-header="false"
        :messages="messages"
        :loading-assist="loadingAssist"
        :submitting-data="submittingData"
        :uploading-image="uploadingImage"
        :image-preview="imagePreview"
        :image-ready="imageReady"
        :data-attachment="dataAttachment"
        :experiment-name="experimentName"
        :step-title="stepTitle"
        :student-name="studentName"
        :suggestions="suggestions"
        :read-only="readOnly"
        :session-history="sessionHistory"
        :current-session-id="currentSessionId"
        :session-history-loading="sessionHistoryLoading"
        @new-session="$emit('new-session')"
        @select-session="$emit('select-session', $event)"
        @send="$emit('send', $event)"
        @stop="$emit('stop')"
        @upload-image="$emit('upload-image', $event)"
        @capture-image="$emit('capture-image', $event)"
        @clear-image="$emit('clear-image')"
        @clear-data="$emit('clear-data')"
      />

      <LabSessionDataPanel
        v-if="modelValue === 'data'"
        :key="`${currentSessionId}-${dataCount}`"
        :session-id="currentSessionId"
        :step-title="stepTitle"
        :read-only="readOnly"
      />

      <div v-else-if="modelValue === 'files'" class="wb-pane custom-scroll">
        <ExperimentMaterialsPanel :experiment-code="experimentCode" compact />
      </div>

      <LabAfterPanel
        v-else-if="modelValue === 'after'"
        :experiment-code="experimentCode"
        :session-finished="sessionFinished"
        @open-summary="$emit('open-summary')"
      />
    </div>
  </section>
</template>

<script setup>
import RightPanel from './RightPanel.vue'
import LabSessionDataPanel from '../lab/LabSessionDataPanel.vue'
import LabAfterPanel from '../lab/LabAfterPanel.vue'
import ExperimentMaterialsPanel from '../stage/ExperimentMaterialsPanel.vue'

defineProps({
  modelValue: { type: String, default: 'guide' },
  dataCount: { type: Number, default: 0 },
  fileCount: { type: Number, default: 0 },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  stepTitle: { type: String, default: '' },
  studentName: { type: String, default: '' },
  sessionFinished: { type: Boolean, default: false },
  messages: { type: Array, default: () => [] },
  loadingAssist: { type: Boolean, default: false },
  submittingData: { type: Boolean, default: false },
  uploadingImage: { type: Boolean, default: false },
  imagePreview: { type: String, default: '' },
  imageReady: { type: Boolean, default: false },
  dataAttachment: { type: Object, default: null },
  suggestions: { type: Array, default: () => [] },
  readOnly: { type: Boolean, default: false },
  sessionHistory: { type: Array, default: () => [] },
  currentSessionId: { type: Number, default: 0 },
  sessionHistoryLoading: { type: Boolean, default: false }
})

defineEmits([
  'update:modelValue',
  'new-session',
  'select-session',
  'send',
  'stop',
  'upload-image',
  'capture-image',
  'clear-image',
  'clear-data',
  'open-summary'
])

const tabs = [
  { key: 'guide', label: '指导纠错' },
  { key: 'data', label: '实验数据' },
  { key: 'files', label: '本实验资料' },
  { key: 'after', label: '课后整理' }
]
</script>

<style scoped>
.wb-tabs {
  @apply flex items-center gap-1 px-3 py-2 bg-white border-b border-line-soft overflow-x-auto shrink-0;
}
.wb-tab {
  @apply relative px-3.5 py-1.5 rounded-lg text-[13px] font-semibold text-ink-muted whitespace-nowrap
    transition-colors hover:bg-surface-soft hover:text-ink-strong;
}
.wb-tab--active { @apply bg-brand-50 text-brand-700; }
.wb-badge {
  @apply ml-1 inline-flex min-w-[1.1rem] h-[1.1rem] px-1 rounded-full bg-brand-600 text-white
    text-[10px] font-bold items-center justify-center;
}
.wb-pane { @apply flex-1 min-h-0 overflow-y-auto p-4; }
</style>
