<template>
  <section class="workbench flex-1 min-h-0 flex flex-col overflow-hidden">
    <nav class="wb-tabs shrink-0" aria-label="实验台侧栏">
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
      </button>
    </nav>

    <div class="wb-body flex-1 min-h-0 flex flex-col overflow-hidden">
      <RightPanel
        v-if="modelValue === 'guide'"
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
        @capture-image="$emit('capture-image')"
        @clear-image="$emit('clear-image')"
        @clear-data="$emit('clear-data')"
      />

      <LabSessionDataPanel
        v-else-if="modelValue === 'data'"
        :session-id="sessionId"
        :session-data-revision="sessionDataRevision"
      />

      <ChatRecordPanel
        v-else-if="modelValue === 'record'"
        :key="currentSessionId"
        :session-id="currentSessionId"
      />
    </div>
  </section>
</template>

<script setup>
import RightPanel from './RightPanel.vue'
import ChatRecordPanel from '../lab/ChatRecordPanel.vue'
import LabSessionDataPanel from '../lab/LabSessionDataPanel.vue'

defineProps({
  modelValue: { type: String, default: 'guide' },
  dataCount: { type: Number, default: 0 },
  sessionId: { type: Number, default: 0 },
  sessionDataRevision: { type: Number, default: 0 },
  experimentCode: { type: String, default: '' },
  experimentName: { type: String, default: '' },
  stepTitle: { type: String, default: '' },
  studentName: { type: String, default: '' },
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
  'clear-data'
])

const tabs = [
  { key: 'guide', label: '物小智' },
  { key: 'data', label: '我的数据' },
  { key: 'record', label: '问答记录' }
]
</script>

<style scoped>
.workbench { @apply bg-white; }
.wb-tabs {
  @apply flex items-center gap-1 px-3 pt-2 pb-0 border-b border-line-soft bg-white;
}
.wb-tab {
  @apply relative px-3.5 py-2.5 text-[13px] font-semibold text-ink-muted
    hover:text-ink-strong transition-colors;
}
.wb-tab--active { @apply text-brand-700; }
.wb-tab--active::after {
  content: '';
  @apply absolute left-3 right-3 bottom-0 h-0.5 bg-brand-600 rounded-full;
}
.wb-badge {
  @apply ml-1 inline-flex min-w-[1.1rem] h-[1.1rem] px-1 items-center justify-center
    rounded-full bg-brand-600 text-white text-[10px] tabular-nums;
}
.wb-body { @apply flex flex-col; }
</style>
